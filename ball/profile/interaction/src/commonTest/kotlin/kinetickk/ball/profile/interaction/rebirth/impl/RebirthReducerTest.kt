// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.content.api.defaultEnemyDefinitions
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
import kinetickk.ball.profile.interaction.TestMatrixNodes
import kinetickk.ball.profile.interaction.TestWeapons
import kinetickk.ball.profile.interaction.rebirth.api.RebirthTierStatus
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
    fun projectionMapsTheSelectedAndFocusedTiersWithTheirStatus() {
        val policy = testRebirthPolicy()
        // Dropped back to tier 0 after clearing tier 4: the ladder opens on the earned tier 5.
        val lowered = rebirthProjection(RebirthProgress(level = 0, highestCleared = 4)).toRenderModel(policy, matter = 1_924L)
        assertEquals(0, lowered.current.tier)
        assertEquals(5, lowered.focus.tier)
        assertEquals(5, lowered.rank)
        assertEquals(1_924L, lowered.matter)
        assertEquals(policy.minimumLevel, lowered.minimumTier)
        assertEquals(policy.maximumLevel, lowered.maximumTier)
        assertEquals(RebirthTierStatus.CLEARED, lowered.status(4))
        assertEquals(RebirthTierStatus.EARNED, lowered.status(5))
        assertEquals(RebirthTierStatus.LOCKED, lowered.status(6))
        assertTrue(lowered.needsConfirmation)
        assertTrue(lowered.canSelect)
        assertFalse(lowered.ascendant)

        // A cleared tier in focus starts with one press; a locked one cannot start; the shell can close it.
        val cleared = rebirthProjection(RebirthProgress(0, 4)).toRenderModel(policy, focusTier = 2)
        assertEquals(2, cleared.focus.tier)
        assertFalse(cleared.needsConfirmation)
        assertTrue(cleared.canSelect)
        assertFalse(rebirthProjection(RebirthProgress(0, 4)).toRenderModel(policy, focusTier = 7).canSelect)
        assertFalse(rebirthProjection(RebirthProgress(0, 4)).toRenderModel(policy, eligible = false, focusTier = 2).canSelect)
        assertEquals(10, rebirthProjection(RebirthProgress(0, 4)).toRenderModel(policy, focusTier = 99).focus.tier)

        // Without a waiting earned tier the ladder opens on the selected tier.
        assertEquals(3, rebirthProjection(RebirthProgress(3, 2)).toRenderModel(policy).focus.tier)
        assertEquals(0, rebirthProjection(RebirthProgress()).toRenderModel(policy).focus.tier)
        assertEquals(RebirthTierStatus.EARNED, rebirthProjection(RebirthProgress()).toRenderModel(policy).focusStatus)
        val ascendant = rebirthProjection(RebirthProgress(10, 10)).toRenderModel(policy)
        assertEquals(10, ascendant.focus.tier)
        assertTrue(ascendant.ascendant)
        assertEquals(RebirthTierStatus.CLEARED, ascendant.focusStatus)
        assertFalse(ascendant.needsConfirmation)
    }

    @Test
    fun buildContentMapsEveryUnlockKindOntoItsTierAndTheFocusedOdds() {
        val policy = testRebirthPolicy()
        val gatedWeapons = TestWeapons.mapIndexed { index, w ->
            if (index != 3) w else kinetickk.ball.content.api.WeaponDefinition(w.id, w.name, w.description, w.family, w.element, w.tags,
                w.key, w.signature, w.scaling, w.cadence, w.permanentUnlockCost, 2, w.masteryForms, w.signatureText)
        }
        val content = RebirthBuildContent(TestRelics, TestItems, BuildPolicy(), gatedWeapons, defaultCoreShapeDefinitions(),
            TestMatrixNodes, defaultEnemyDefinitions())
        // Ladder order: by tier, then weapons, characters, Relics, items, Matrix nodes and enemies.
        val tierTwo = content.unlocks.filter { it.tier == 2 }
        assertEquals(gatedWeapons[3].id, assertIs<RebirthUnlock.Weapon>(tierTwo.first()).definition.id)
        assertEquals(listOf("Weapon", "Item", "Item", "MatrixNode", "MatrixNode", "Enemy", "Enemy"),
            tierTwo.map { it::class.simpleName })
        val characters = content.unlocks.filterIsInstance<RebirthUnlock.Character>()
        assertEquals(listOf(CoreShape.SPIRAL, CoreShape.PENTAGON, CoreShape.CRESCENT, CoreShape.HEXAGON), characters.map { it.definition.id })
        assertEquals(listOf(1, 3, 5, 7), characters.map { it.tier })
        assertEquals(listOf(RelicId.AGONY_SCEPTER, RelicId.HOLLOW_CROWN, RelicId.OUROBOROS_SEAL),
            content.unlocks.filterIsInstance<RebirthUnlock.Relic>().map { it.definition.id })
        assertTrue(content.unlocks.filterIsInstance<RebirthUnlock.Enemy>().all { it.definition.minimumRebirth > 0 })
        assertEquals(content.unlocks.map { it.tier }.sorted(), content.unlocks.map { it.tier })

        fun model(level: Int, cleared: Int, focus: Int, heirlooms: List<RelicId> = emptyList()) =
            rebirthProjection(RebirthProgress(level, cleared)).toRenderModel(policy, build = content, heirloomIds = heirlooms, focusTier = focus)

        val first = model(0, 0, 1)
        val firstBuild = assertNotNull(first.build)
        assertEquals(0f, firstBuild.currentRebornChance)
        assertEquals(0.05f, firstBuild.nextRebornChance, 0.0001f)
        assertEquals(listOf(CoreShape.SPIRAL), first.focusUnlocks.filterIsInstance<RebirthUnlock.Character>().map { it.definition.id })
        assertEquals(listOf(RelicId.AGONY_SCEPTER), first.focusUnlocks.filterIsInstance<RebirthUnlock.Relic>().map { it.definition.id })
        // The odds compare the selected tier with the focused one, whichever is higher.
        val back = assertNotNull(model(6, 8, 2).build)
        assertEquals(0.3f, back.currentRebornChance, 0.0001f)
        assertEquals(0.1f, back.nextRebornChance, 0.0001f)

        // Heirlooms keep the stored slot order; repeats and Relics the catalog lacks are dropped.
        val stored = listOf(RelicId.FURNACE_COVENANT, RelicId.GLASS_COMET, RelicId.PERPETUAL_VECTOR, RelicId.FURNACE_COVENANT)
        assertEquals(listOf(RelicId.FURNACE_COVENANT, RelicId.PERPETUAL_VECTOR),
            assertNotNull(model(3, 3, 3, stored).build).heirlooms.map { it.id })

        // Without catalog content the screen keeps its build-free model.
        assertNull(rebirthProjection(RebirthProgress(2, 2)).toRenderModel(policy).build)
    }

    @Test
    fun theFocusMovesWithinTheLadderAndAnEarnedTierTakesTwoPresses() {
        val policy = testRebirthPolicy()
        val model = rebirthProjection(RebirthProgress(level = 0, highestCleared = 0)).toRenderModel(policy)
        assertEquals(1, model.focus.tier)
        val first = RebirthReducer.reduce(RebirthState(model, confirmationArmed = false), RebirthAction.SelectRequested)
        assertEquals(RebirthOutput.ArmRequested(1), assertIs<RebirthEffect.Emit>(first.effects[0]).output)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<RebirthEffect.PlayAudio>(first.effects[1]).cue)
        val second = RebirthReducer.reduce(RebirthState(model, confirmationArmed = true), RebirthAction.SelectRequested)
        assertEquals(RebirthOutput.ConfirmRequested(1), assertIs<RebirthEffect.Emit>(second.effects.single()).output)

        // A cleared tier starts at once, armed or not.
        val cleared = rebirthProjection(RebirthProgress(1, 0)).toRenderModel(policy, focusTier = 0)
        assertEquals(RebirthOutput.ConfirmRequested(0),
            assertIs<RebirthEffect.Emit>(RebirthReducer.reduce(RebirthState(cleared, false), RebirthAction.SelectRequested).effects.single()).output)

        // Focus: a press, a step and the ladder's ends; an unchanged focus is silent.
        val focused = RebirthReducer.reduce(RebirthState(model, false), RebirthAction.FocusTier(4))
        assertEquals(RebirthEffect.Focus(4), focused.effects[0])
        assertEquals(RebirthEffect.Focus(2), RebirthReducer.reduce(RebirthState(model, false), RebirthAction.StepFocus(1)).effects[0])
        assertEquals(RebirthEffect.Focus(10), RebirthReducer.reduce(RebirthState(model, false), RebirthAction.FocusTier(42)).effects[0])
        assertTrue(RebirthReducer.reduce(RebirthState(model, false), RebirthAction.FocusTier(1)).effects.isEmpty())
        val atStart = rebirthProjection(RebirthProgress()).toRenderModel(policy)
        assertTrue(RebirthReducer.reduce(RebirthState(atStart, false), RebirthAction.StepFocus(-1)).effects.isEmpty())
    }

    @Test
    fun lockedTiersAndAClosedShellCannotStart() {
        val policy = testRebirthPolicy(maximumLevel = 3)
        val locked = rebirthProjection(RebirthProgress(level = 1, highestCleared = 0)).toRenderModel(policy, focusTier = 3)
        assertEquals(RebirthActionState.LOCKED, locked.actionState(confirmationArmed = true))
        assertTrue(RebirthReducer.reduce(RebirthState(locked, false), RebirthAction.SelectRequested).effects.isEmpty())
        val closed = rebirthProjection(RebirthProgress(1, 0)).toRenderModel(policy, eligible = false, focusTier = 1)
        assertEquals(RebirthActionState.UNAVAILABLE, closed.actionState(confirmationArmed = false))
        assertTrue(RebirthReducer.reduce(RebirthState(closed, false), RebirthAction.SelectRequested).effects.isEmpty())
        val ready = rebirthProjection(RebirthProgress(1, 0)).toRenderModel(policy, focusTier = 1)
        assertEquals(RebirthActionState.READY, ready.actionState(confirmationArmed = false))
        assertEquals(RebirthActionState.ARMED, ready.actionState(confirmationArmed = true))
        // The arm flag means nothing for a cleared tier.
        val clearedTier = rebirthProjection(RebirthProgress(1, 0)).toRenderModel(policy, focusTier = 0)
        assertEquals(RebirthActionState.READY, clearedTier.actionState(confirmationArmed = true))
    }

    @Test
    fun backLeavesControlledConfirmationUntouchedAndEmitsClickThenNavigation() {
        val model = rebirthProjection(RebirthProgress(0, 0)).toRenderModel(testRebirthPolicy())
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
            weapons = TestWeapons,
            coreShapes = defaultCoreShapeDefinitions(),
            matrixNodes = TestMatrixNodes,
            enemies = defaultEnemyDefinitions(),
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
): RebirthProgressProjection = RebirthProgressProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision.ZERO,
    snapshot = RebirthProfileSnapshot(progress),
    canAdvance = progress.highestCleared + 1 > progress.level,
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
