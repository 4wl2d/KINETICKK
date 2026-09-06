// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunFeatureBoundsTest {
    private fun game() = MutableGameState(canonicalGameplayContent).apply {
        startRun()
        enemies.clear()
    }

    @Test
    fun activationClosesAlternativeAndNeverRetainsTwoActiveTrials() {
        val state = game()
        state.elapsed = 45f
        state.updatePointsOfInterest(0.01f)
        val offered = state.pointsOfInterest
        assertEquals(2, offered.size)
        state.activatePointOfInterest(offered.first())
        assertEquals(1, state.pointsOfInterest.size)
        assertTrue(state.pointsOfInterest.single().active)
        assertEquals(offered.first().kind, state.pointsOfInterest.single().kind)
    }

    @Test
    fun sixthRewardFitsAndSeventhCannotPublishIntoSourceSnapshot() {
        val source = game()
        source.pendingDirectedRewards = List(5) { DirectedReward.WEAPON }
        source.pointsOfInterest = listOf(PointOfInterestState(
            0, PointOfInterestKind.RESONANT_CIRCUIT, source.coreX, source.coreY, 85f,
            active = true, nextBeacon = 3,
        ))
        source.previousCoreX = source.coreX
        source.previousCoreY = source.coreY
        source.updatePointsOfInterest(0.01f)
        assertEquals(6, source.pendingDirectedRewards.size)
        val candidate = source.copyForReduction()
        candidate.pointsOfInterest = listOf(PointOfInterestState(
            0, PointOfInterestKind.RESONANT_CIRCUIT, source.coreX, source.coreY, 85f,
            active = true, nextBeacon = 3,
        ))
        assertFailsWith<IllegalStateException> { candidate.updatePointsOfInterest(0.01f) }
        assertEquals(6, source.pendingDirectedRewards.size)
        assertTrue(source.pointsOfInterest.isEmpty())
    }

    @Test
    fun fourthSeparatedDashCollapsesInsteadOfGrowingRetainedLattice() {
        val state = game()
        state.coreShape = CoreShape.TESSERACT
        repeat(4) { index ->
            state.coreX = index * 400f
            state.beginCharacterDash()
            state.previousCoreX = state.coreX
            state.coreX += 150f
            state.velocityX = 600f
            state.dashPhaseTime = 0.24f
            state.updateCharacterRuntime(0.01f)
            assertEquals(if (index == 3) 0 else index + 1, state.characterRuntime.lattice.size)
        }
        assertTrue(state.characterRuntime.lattice.isEmpty())
    }
}
