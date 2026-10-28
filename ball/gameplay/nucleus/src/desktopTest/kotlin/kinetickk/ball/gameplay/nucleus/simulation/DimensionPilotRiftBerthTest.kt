// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dimension gates' pilot flies through the rift it was sent to (DimensionPilotGateTest: a rift is never left to
 * close unentered) while it keeps its berth from every other skipped optional site. A rift that opens inside such a
 * berth (in the gate a glitch rift beside a structure's 1,300 u mark) must not hold the Core where the berth balances
 * the approach.
 */
class DimensionPilotRiftBerthTest {
    @Test fun thePilotReachesARiftThatOpenedInsideAnotherSkippedSitesBerth() {
        val state = quietArena()
        val riftX = state.coreX - RIFT_DISTANCE
        val riftY = state.coreY
        state.dimensionRun = state.dimensionRun.copy(
            glitchPortal = DimensionPortal(state.dimensionRun.nextPortalId, riftX, riftY, WorldLayer.ARCHIVE, state.elapsed + 30f),
            nextPortalId = state.dimensionRun.nextPortalId + 1,
        )
        // A skipped point beyond the rift, near enough that the rift lies inside its berth (its radius plus 295 u).
        val site = SiteState(0L, SiteKind.RESONANT_CIRCUIT, riftX - SITE_BEYOND_RIFT, riftY, state.elapsed + 60f)
        state.sites = listOf(site)
        assertTrue(SITE_BEYOND_RIFT < state.content.sites.definition(site.kind).activationRadius + 295f)
        val pilot = DimensionPilot(WorldLayer.ARCHIVE, NoPoiBot()).apply { riftDue = true }
        val start = state.elapsed
        while (state.elapsed - start < FLIGHT_SECONDS && state.worldLayer == WorldLayer.BASE && state.phase == GamePhase.RUNNING) {
            pilot.control(state)
            state.update(INPUT_FRAME)
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        assertEquals(WorldLayer.ARCHIVE, state.worldLayer, "the pilot did not reach its rift in $FLIGHT_SECONDS s")
        assertTrue(state.sites.none { it.active } && state.openedSiteIds.isEmpty(), "the pilot entered the skipped point")
    }

    /** A fresh run at rest with nothing else in reach: no enemies, shots, pickups, spawns, sites, events or obstacles. */
    private fun quietArena(): MutableGameState = MutableGameState(canonicalGameplayContent, seed = SEED).apply {
        unlockWeaponsForTesting(ReferenceArmory)
        applyPreferences(settings.copy(simulationSpeed = 1f))
        startRun()
        val opener = NoPoiBot()
        while (phase == GamePhase.CHOICE) opener.choose(this)
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        spawnClock = Float.MAX_VALUE
        nextEliteAt = Float.MAX_VALUE
        quietArenaForTesting()
        clearObstaclesForTesting()
        setVelocityForTesting(0f, 0f)
    }

    private companion object {
        const val SEED = 2026
        const val INPUT_FRAME = 0.02f
        const val FLIGHT_SECONDS = 8f
        const val RIFT_DISTANCE = 500f
        const val SITE_BEYOND_RIFT = 200f
    }
}
