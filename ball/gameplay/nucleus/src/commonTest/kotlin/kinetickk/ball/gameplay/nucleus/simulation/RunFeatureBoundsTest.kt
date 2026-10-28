// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunFeatureBoundsTest {
    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent) = MutableGameState(content).apply {
        startRun()
        enemies.clear()
    }

    @Test
    fun activationClosesAlternativeAndNeverRetainsTwoActiveTrials() {
        // Anomaly windows only, so whichever kind the site stream draws can start its trial.
        val state = game(canonicalGameplayContent.withAnomalyWindowsOnly())
        state.elapsed = 45f
        state.updateSites(0.01f)
        val offered = state.sites
        assertEquals(2, offered.size)
        state.activateWindowSite(offered.first())
        assertEquals(1, state.sites.size)
        assertTrue(state.sites.single().active)
        assertEquals(offered.first().kind, state.sites.single().kind)
    }

    @Test
    fun sixthRewardFitsAndSeventhCannotPublishIntoSourceSnapshot() {
        val source = game()
        source.pendingDirectedRewards = List(5) { DirectedReward.WEAPON }
        source.sites = listOf(SiteState(
            0L, SiteKind.RESONANT_CIRCUIT, source.coreX, source.coreY, 85f,
            active = true, step = 2,
        ))
        source.previousCoreX = source.coreX
        source.previousCoreY = source.coreY
        source.updateSites(0.01f)
        assertEquals(6, source.pendingDirectedRewards.size)
        val candidate = source.copyForReduction()
        candidate.sites = listOf(SiteState(
            0L, SiteKind.RESONANT_CIRCUIT, source.coreX, source.coreY, 85f,
            active = true, step = 2,
        ))
        assertFailsWith<IllegalStateException> { candidate.updateSites(0.01f) }
        assertEquals(6, source.pendingDirectedRewards.size)
        assertTrue(source.sites.isEmpty())
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
            assertEquals(if (index == 3) 0 else index + 1, state.characterRuntime.latticeCount)
        }
        assertEquals(0, state.latticeNodeCount)
    }
}
