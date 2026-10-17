// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.interaction.TestItems
import kinetickk.ball.profile.interaction.TestRelics
import kinetickk.ball.profile.interaction.testRebirthPolicy
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RebirthReducerTest {
    @Test
    fun eligibleSnapshotMapsCurrentAndNextCycle() {
        val rebirthPolicy = testRebirthPolicy(maximumLevel = 3)
        val model = rebirthProjection(
            progress = RebirthProgress(level = 2, highestCleared = 2),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy, eligible = true)

        assertEquals(2, model.current.tier)
        assertEquals(3, model.next.tier)
        assertTrue(model.canAdvance)
        assertFalse(model.isMaximumTier)

        val withMatter = rebirthProjection(
            progress = RebirthProgress(level = 2, highestCleared = 2),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy, eligible = true, matter = 1_924L)
        assertEquals(1_924L, withMatter.matter)
        assertEquals(rebirthPolicy.minimumLevel, withMatter.minimumTier)
        assertEquals(rebirthPolicy.maximumLevel, withMatter.maximumTier)
        assertEquals(3, withMatter.targetTier)

        val shellBlocked = rebirthProjection(
            progress = RebirthProgress(level = 2, highestCleared = 2),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy, eligible = false)
        assertFalse(shellBlocked.canAdvance)
    }

    @Test
    fun buildContentMapsTheTargetTiersUnlocksRebornOddsAndHeirlooms() {
        val rebirthPolicy = testRebirthPolicy()
        val content = RebirthBuildContent(TestRelics, TestItems, BuildPolicy())
        // Gated content in unlock order: by tier, Relics before items, then catalog order.
        assertEquals(listOf(1, 2, 2, 3, 4, 5), content.unlocks.map { it.tier })
        assertEquals(listOf(RelicId.AGONY_SCEPTER, RelicId.HOLLOW_CROWN, RelicId.OUROBOROS_SEAL),
            content.unlocks.filterIsInstance<RebirthUnlock.Relic>().map { it.definition.id })
        assertEquals(listOf(1, 3, 2), content.unlocks.filterIsInstance<RebirthUnlock.Item>().map { it.definition.id })

        fun model(level: Int, heirlooms: List<RelicId> = emptyList()) = rebirthProjection(
            progress = RebirthProgress(level = level, highestCleared = level),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy, build = content, heirloomIds = heirlooms)

        val first = model(0)
        val firstBuild = assertNotNull(first.build)
        assertEquals(0f, firstBuild.currentRebornChance)
        assertEquals(0.05f, firstBuild.nextRebornChance, 0.0001f)
        assertEquals(listOf(RelicId.AGONY_SCEPTER),
            first.targetUnlocks.map { assertIs<RebirthUnlock.Relic>(it).definition.id })
        // Both tier 2 legendaries; no relic joins at tier 2.
        assertEquals(listOf(1, 3), model(1).targetUnlocks.map { assertIs<RebirthUnlock.Item>(it).definition.id })
        assertTrue(model(5).targetUnlocks.isEmpty())

        // The odds cap at the policy's 40 %; the last tier shows no further unlocks.
        val capped = assertNotNull(model(8).build)
        assertEquals(0.4f, capped.currentRebornChance, 0.0001f)
        assertEquals(0.4f, capped.nextRebornChance, 0.0001f)
        val maximum = model(rebirthPolicy.maximumLevel)
        assertTrue(maximum.isMaximumTier)
        assertTrue(maximum.targetUnlocks.isEmpty())
        assertEquals(0.4f, assertNotNull(maximum.build).nextRebornChance, 0.0001f)

        // Heirlooms keep the stored slot order; repeats and Relics the catalog lacks are dropped.
        val stored = listOf(RelicId.FURNACE_COVENANT, RelicId.GLASS_COMET, RelicId.PERPETUAL_VECTOR, RelicId.FURNACE_COVENANT)
        assertEquals(listOf(RelicId.FURNACE_COVENANT, RelicId.PERPETUAL_VECTOR),
            assertNotNull(model(3, stored).build).heirlooms.map { it.id })
        assertTrue(assertNotNull(model(3).build).heirlooms.isEmpty())

        // Without catalog content the screen keeps its build-free model.
        assertNull(rebirthProjection(RebirthProgress(2, 2), canAdvance = true).toRenderModel(rebirthPolicy).build)
    }

    @Test
    fun controlledConfirmationEmitsDistinctArmAndConfirmRequests() {
        val rebirthPolicy = testRebirthPolicy()
        val model = rebirthProjection(
            progress = RebirthProgress(level = 0, highestCleared = 0),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy)
        val first = RebirthReducer.reduce(
            RebirthState(model, confirmationArmed = false),
            RebirthAction.AdvanceRequested,
        )
        assertFalse(first.state.confirmationArmed)
        assertEquals(
            RebirthOutput.ArmRequested,
            assertIs<RebirthEffect.Emit>(first.effects[0]).output,
        )
        assertEquals(
            ProfileAudioCue.UI_CLICK,
            assertIs<RebirthEffect.PlayAudio>(first.effects[1]).cue,
        )

        val second = RebirthReducer.reduce(
            first.state.copy(confirmationArmed = true),
            RebirthAction.AdvanceRequested,
        )
        assertEquals(
            RebirthOutput.ConfirmRequested,
            assertIs<RebirthEffect.Emit>(second.effects.single()).output,
        )
    }

    @Test
    fun lockedAndMaximumCyclesCannotArm() {
        val rebirthPolicy = testRebirthPolicy(maximumLevel = 3)
        val locked = rebirthProjection(
            progress = RebirthProgress(level = 1, highestCleared = 0),
            canAdvance = false,
        ).toRenderModel(rebirthPolicy)
        val lockedReduction = RebirthReducer.reduce(
            RebirthState(locked, confirmationArmed = false),
            RebirthAction.AdvanceRequested,
        )
        assertFalse(lockedReduction.state.confirmationArmed)
        assertTrue(lockedReduction.effects.isEmpty())

        val maximum = rebirthProjection(
            progress = RebirthProgress(
                level = rebirthPolicy.maximumLevel,
                highestCleared = rebirthPolicy.maximumLevel,
            ),
            canAdvance = false,
        ).toRenderModel(rebirthPolicy)
        assertTrue(maximum.isMaximumTier)
        assertFalse(maximum.canAdvance)
    }

    @Test
    fun backLeavesControlledConfirmationUntouchedAndEmitsClickThenNavigation() {
        val model = rebirthProjection(
            progress = RebirthProgress(0, 0),
            canAdvance = true,
        ).toRenderModel(testRebirthPolicy())
        val reduction = RebirthReducer.reduce(
            RebirthState(model, confirmationArmed = true),
            RebirthAction.Back,
        )
        assertTrue(reduction.state.confirmationArmed)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<RebirthEffect.PlayAudio>(reduction.effects[0]).cue)
        assertEquals(RebirthOutput.Back, assertIs<RebirthEffect.Emit>(reduction.effects[1]).output)
    }

    @Test
    fun acceptedFeedbackRetainsTheProfilePurchaseCue() {
        val audio = RecordingRebirthAudioService()
        val feature = DefaultRebirthFeature(
            profilePort = UnusedProfilePort,
            rebirthPolicy = testRebirthPolicy(),
            audioService = audio,
            relics = TestRelics,
            items = TestItems,
            buildPolicy = BuildPolicy(),
        )

        feature.playAcceptedFeedback()

        assertEquals(
            listOf(ToneRequest(490f, 0.1f, 0.16f, ToneWave.SINE)),
            audio.requests,
        )
    }
}

private fun rebirthProjection(
    progress: RebirthProgress,
    canAdvance: Boolean,
): RebirthProgressProjection = RebirthProgressProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision.ZERO,
    snapshot = RebirthProfileSnapshot(progress),
    canAdvance = canAdvance,
)

private object UnusedProfilePort : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance = error("unused")

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetHangar): kinetickk.ball.profile.api.HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

private class RecordingRebirthAudioService : AudioService {
    var requests = emptyList<ToneRequest>()

    override fun updatePreferences(preferences: AudioPreferences) = Unit

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        assertEquals(0f, realDeltaSeconds)
        this.requests = requests
    }

    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
