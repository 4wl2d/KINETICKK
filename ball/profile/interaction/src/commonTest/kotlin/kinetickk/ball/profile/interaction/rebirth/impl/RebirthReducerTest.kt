// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.interaction.testRebirthPolicy
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
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
    fun firstRequestArmsAndSecondRequestsExactlyOneCapabilityMutation() {
        val rebirthPolicy = testRebirthPolicy()
        val model = rebirthProjection(
            progress = RebirthProgress(level = 0, highestCleared = 0),
            canAdvance = true,
        ).toRenderModel(rebirthPolicy)
        val first = RebirthReducer.reduce(
            RebirthState(model, armed = false),
            RebirthAction.AdvanceRequested,
        )
        assertTrue(first.state.armed)
        assertEquals(
            ProfileAudioCue.UI_CLICK,
            assertIs<RebirthEffect.PlayAudio>(first.effects.single()).cue,
        )

        val second = RebirthReducer.reduce(first.state, RebirthAction.AdvanceRequested)
        assertEquals(RebirthEffect.AdvanceCycle, second.effects.single())
    }

    @Test
    fun lockedAndMaximumCyclesCannotArm() {
        val rebirthPolicy = testRebirthPolicy(maximumLevel = 3)
        val locked = rebirthProjection(
            progress = RebirthProgress(level = 1, highestCleared = 0),
            canAdvance = false,
        ).toRenderModel(rebirthPolicy)
        val lockedReduction = RebirthReducer.reduce(
            RebirthState(locked, armed = false),
            RebirthAction.AdvanceRequested,
        )
        assertFalse(lockedReduction.state.armed)
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
    fun backDisarmsBeforeEmittingClickAndNavigation() {
        val model = rebirthProjection(
            progress = RebirthProgress(0, 0),
            canAdvance = true,
        ).toRenderModel(testRebirthPolicy())
        val reduction = RebirthReducer.reduce(
            RebirthState(model, armed = true),
            RebirthAction.Back,
        )
        assertFalse(reduction.state.armed)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<RebirthEffect.PlayAudio>(reduction.effects[0]).cue)
        assertEquals(RebirthOutput.Back, assertIs<RebirthEffect.Emit>(reduction.effects[1]).output)
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
