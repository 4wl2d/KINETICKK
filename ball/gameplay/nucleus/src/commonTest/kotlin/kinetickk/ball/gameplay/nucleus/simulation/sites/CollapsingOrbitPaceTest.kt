// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.sites

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The collapsing orbit earns only while the Core circles its ring at the minimum pace, which lies above the speed at
 * which Core Freeze thaws: no orbit pace the site rewards can freeze the Core.
 */
class CollapsingOrbitPaceTest {
    @Test
    fun theOrbitEarnsOnlyAtAPaceThatThawsTheCore() {
        val minimum = buildTestState().content.sites.windows.orbitMinimumSpeed
        assertTrue(minimum > buildTestState().content.coreStasis.thawSpeed)
        for ((speed, earns) in listOf(100f to false, minimum - 5f to false, minimum + 10f to true)) {
            val game = buildTestState().apply { quietArenaForTesting() }
            val centerX = game.coreX - RING
            val centerY = game.coreY
            game.sites = listOf(SiteState(1L, SiteKind.COLLAPSING_ORBIT, centerX, centerY, expiresAt = 260f, active = true))
            var angle = 0f
            repeat(120) {
                game.previousCoreX = game.coreX
                game.previousCoreY = game.coreY
                angle += speed / RING * STEP
                game.coreX = centerX + cos(angle) * RING
                game.coreY = centerY + sin(angle) * RING
                game.updateSites(STEP)
            }
            val meter = game.sites.single().meter
            if (earns) assertEquals(120 * STEP, meter, 1e-3f, "$speed u/s earns") else assertEquals(0f, meter, "$speed u/s earns nothing")
        }
    }
}

private const val STEP = MutableGameState.FIXED_STEP

/** The orbit's ring is 120..190 u; the pilot flies 155. */
private const val RING = 155f
