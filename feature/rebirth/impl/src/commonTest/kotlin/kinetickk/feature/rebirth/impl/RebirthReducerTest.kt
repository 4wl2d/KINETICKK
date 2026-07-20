// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.RebirthProgression
import kinetickk.core.profile.api.RebirthProfileSnapshot
import kinetickk.core.profile.api.RebirthProgress
import kinetickk.feature.rebirth.api.RebirthOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RebirthReducerTest {
    @Test
    fun eligibleSnapshotMapsCurrentAndNextCycle() {
        val model = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 2, highestCleared = 2),
        ).toRenderModel(eligible = true)

        assertEquals(2, model.current.tier)
        assertEquals(3, model.next.tier)
        assertTrue(model.canAdvance)
        assertFalse(model.isMaximumTier)

        val shellBlocked = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 2, highestCleared = 2),
        ).toRenderModel(eligible = false)
        assertFalse(shellBlocked.canAdvance)
    }

    @Test
    fun firstRequestArmsAndSecondRequestsExactlyOneCapabilityMutation() {
        val model = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 0, highestCleared = 0),
        ).toRenderModel()
        val first = RebirthReducer.reduce(
            RebirthState(model, armed = false),
            RebirthAction.AdvanceRequested,
        )
        assertTrue(first.state.armed)
        assertEquals(
            RebirthOutput.Cue(AudioCue.UI_CLICK),
            assertIs<RebirthEffect.Emit>(first.effects.single()).output,
        )

        val second = RebirthReducer.reduce(first.state, RebirthAction.AdvanceRequested)
        assertEquals(RebirthEffect.AdvanceCycle, second.effects.single())
    }

    @Test
    fun lockedAndMaximumCyclesCannotArm() {
        val locked = RebirthProfileSnapshot(
            progress = RebirthProgress(level = 1, highestCleared = 0),
        ).toRenderModel()
        val lockedReduction = RebirthReducer.reduce(
            RebirthState(locked, armed = false),
            RebirthAction.AdvanceRequested,
        )
        assertFalse(lockedReduction.state.armed)
        assertTrue(lockedReduction.effects.isEmpty())

        val maximum = RebirthProfileSnapshot(
            progress = RebirthProgress(
                level = RebirthProgression.MAX_LEVEL,
                highestCleared = RebirthProgression.MAX_LEVEL,
            ),
        ).toRenderModel()
        assertTrue(maximum.isMaximumTier)
        assertFalse(maximum.canAdvance)
    }

    @Test
    fun backDisarmsBeforeEmittingClickAndNavigation() {
        val model = RebirthProfileSnapshot(RebirthProgress(0, 0)).toRenderModel()
        val reduction = RebirthReducer.reduce(
            RebirthState(model, armed = true),
            RebirthAction.Back,
        )
        assertFalse(reduction.state.armed)
        assertEquals(RebirthOutput.Cue(AudioCue.UI_CLICK), assertIs<RebirthEffect.Emit>(reduction.effects[0]).output)
        assertEquals(RebirthOutput.Back, assertIs<RebirthEffect.Emit>(reduction.effects[1]).output)
    }
}
