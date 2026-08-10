// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
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
        val model = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 2, highestCleared = 2),
        ).toRenderModel(rebirthPolicy, eligible = true)

        assertEquals(2, model.current.tier)
        assertEquals(3, model.next.tier)
        assertTrue(model.canAdvance)
        assertFalse(model.isMaximumTier)

        val shellBlocked = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 2, highestCleared = 2),
        ).toRenderModel(rebirthPolicy, eligible = false)
        assertFalse(shellBlocked.canAdvance)
    }

    @Test
    fun firstRequestArmsAndSecondRequestsExactlyOneCapabilityMutation() {
        val rebirthPolicy = testRebirthPolicy()
        val model = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 0, highestCleared = 0),
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
        val locked = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 1, highestCleared = 0),
        ).toRenderModel(rebirthPolicy)
        val lockedReduction = RebirthReducer.reduce(
            RebirthState(locked, armed = false),
            RebirthAction.AdvanceRequested,
        )
        assertFalse(lockedReduction.state.armed)
        assertTrue(lockedReduction.effects.isEmpty())

        val maximum = RebirthProfileSnapshot(
            progress = RebirthProgress(
                level = rebirthPolicy.maximumLevel,
                highestCleared = rebirthPolicy.maximumLevel,
            ),
        ).toRenderModel(rebirthPolicy)
        assertTrue(maximum.isMaximumTier)
        assertFalse(maximum.canAdvance)
    }

    @Test
    fun backDisarmsBeforeEmittingClickAndNavigation() {
        val model = RebirthProfileSnapshot(RebirthProgress(0, 0)).toRenderModel(testRebirthPolicy())
        val reduction = RebirthReducer.reduce(
            RebirthState(model, armed = true),
            RebirthAction.Back,
        )
        assertFalse(reduction.state.armed)
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<RebirthEffect.PlayAudio>(reduction.effects[0]).cue)
        assertEquals(RebirthOutput.Back, assertIs<RebirthEffect.Emit>(reduction.effects[1]).output)
    }
}
