// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dimension gates' pilot flies through the rift it was sent to (DimensionPilotGateTest: a rift is never left to
 * close unentered) while it keeps its berth from every other skipped optional site. A rift that opens inside such a
 * berth (in the gate a glitch rift beside a structure's 1,300 u mark) must not hold the Core where the berth balances
 * the approach, and a threat Dash flown along the pilot's pointer must not carry the Core onto such a site, read along
 * the path the Core flies after the Dash.
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

    /**
     * Beside its rift the pilot flies inside another skipped site's usual berth, so the threat Dash the bot asks for is
     * dropped when its pushed line passes the site's guard: in the A3 gate such a Dash at about 1,000 u/s carried the
     * Core into a Nadir window rift (232 u from the glitch rift) or a bounty arena (287 u from it), and the glitch rift
     * was lost. With the site gone the same Dash is kept, so the bot did ask for it.
     */
    @Test fun thePilotDropsAThreatDashThatWouldCarryItOntoASkippedSite() {
        assertTrue(keepsThreatDash(site = false), "the bot asked for no threat Dash")
        assertFalse(keepsThreatDash(site = true), "the pilot kept a threat Dash onto the skipped site beside its rift")
    }

    /**
     * A Dash's impulse adds to the velocity the Core already has, so the pilot reads a pushed Dash along the path the Core
     * then flies, not along its pointer: cruising away from its rift, the pilot turns back toward it, and the threat Dash
     * the bot asks for would carry the Core past a skipped site that the pointer's own line clears. In the A3 gate such a
     * Dash flew into a Nadir window rift that its pointer's line passed 238 u from, and the glitch rift was lost.
     */
    @Test fun thePilotReadsAPushedDashAlongThePathTheCoreFlies() {
        assertTrue(keepsDashFromItsCruise(site = false), "the bot asked for no threat Dash")
        assertFalse(keepsDashFromItsCruise(site = true), "the pilot kept a Dash whose path runs onto the skipped site")
    }

    /**
     * The Core cruises along +x at [CRUISE] u/s with its rift behind it ([RIFT_BEHIND], [RIFT_ASIDE]) and, with [site], a
     * skipped Nadir rift on the post-Dash path; once the pilot's cursor turned back toward its rift a hostile shot closes
     * on the Core: true when the threat Dash the bot asks for survives the pilot's input. The site lies well outside the
     * pilot's berth, so both runs steer alike.
     */
    private fun keepsDashFromItsCruise(site: Boolean): Boolean {
        val state = quietArena()
        state.dashBufferTime = 0f
        state.setVelocityForTesting(CRUISE, 0f)
        state.dimensionRun = state.dimensionRun.copy(
            glitchPortal = DimensionPortal(
                state.dimensionRun.nextPortalId, state.coreX - RIFT_BEHIND, state.coreY - RIFT_ASIDE, WorldLayer.ARCHIVE, state.elapsed + 30f,
            ),
            nextPortalId = state.dimensionRun.nextPortalId + 1,
        )
        if (site) {
            state.sites = listOf(SiteState(0L, SiteKind.NADIR_RIFT, state.coreX - PATH_SITE_BEHIND, state.coreY - PATH_SITE_ASIDE, state.elapsed + 60f))
        }
        val pilot = DimensionPilot(WorldLayer.ARCHIVE, NoPoiBot()).apply { riftDue = true }
        repeat(TURN_INPUTS) { pilot.control(state) }
        assertEquals(0f, state.dashBufferTime, "a Dash was asked for before the shot")
        // The shot keeps the Core's cruise along x and closes on it along +y.
        state.projectiles += Projectile(state.coreX, state.coreY - SHOT_DISTANCE, CRUISE, SHOT_SPEED, radius = 6f, life = 2f, hostile = true)
        pilot.control(state)
        return state.dashBufferTime > 0f
    }

    /**
     * Turns the pilot onto its rift [FAR_RIFT_DISTANCE] ahead with a skipped point ([site]) beside its course, then fires
     * a hostile shot at the Core: true when the threat Dash the bot asks for survives the pilot's input.
     */
    private fun keepsThreatDash(site: Boolean): Boolean {
        val state = quietArena()
        state.dashBufferTime = 0f
        state.dimensionRun = state.dimensionRun.copy(
            glitchPortal = DimensionPortal(
                state.dimensionRun.nextPortalId, state.coreX - FAR_RIFT_DISTANCE, state.coreY, WorldLayer.ARCHIVE, state.elapsed + 30f,
            ),
            nextPortalId = state.dimensionRun.nextPortalId + 1,
        )
        if (site) {
            val point = SiteState(0L, SiteKind.RESONANT_CIRCUIT, state.coreX - SITE_AHEAD, state.coreY + SITE_ASIDE, state.elapsed + 60f)
            state.sites = listOf(point)
        }
        val pilot = DimensionPilot(WorldLayer.ARCHIVE, NoPoiBot()).apply { riftDue = true }
        // The cursor turns onto the rift at its turn rate before the shot comes.
        repeat(TURN_INPUTS) { pilot.control(state) }
        assertEquals(0f, state.dashBufferTime, "a Dash was asked for before the shot")
        state.projectiles += Projectile(state.coreX, state.coreY - SHOT_DISTANCE, 0f, SHOT_SPEED, radius = 6f, life = 2f, hostile = true)
        pilot.control(state)
        return state.dashBufferTime > 0f
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
        const val FAR_RIFT_DISTANCE = 700f
        const val SITE_AHEAD = 300f
        const val SITE_ASIDE = 30f
        const val TURN_INPUTS = 10
        const val SHOT_DISTANCE = 100f
        const val SHOT_SPEED = 800f
        const val CRUISE = 700f
        const val RIFT_BEHIND = 610f
        const val RIFT_ASIDE = 350f

        /**
         * About 100 u beyond the end of the post-Dash path's 0.3 s line and about 185 u from the end of the pointer's line
         * at the speed the pointer alone would give, against the site's 65 u radius and the 85 u guard.
         */
        const val PATH_SITE_BEHIND = 200f
        const val PATH_SITE_ASIDE = 100f
    }
}
