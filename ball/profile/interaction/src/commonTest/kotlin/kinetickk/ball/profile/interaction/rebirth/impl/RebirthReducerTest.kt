// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.interaction.testRebirthPolicy
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

        val shellBlocked = rebirthProjection(
            progress = RebirthProgress(level = 2, highestCleared = 2),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy, eligible = false)
        assertFalse(shellBlocked.canAdvance)
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

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance = error("unused")

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection = error("unused")
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
