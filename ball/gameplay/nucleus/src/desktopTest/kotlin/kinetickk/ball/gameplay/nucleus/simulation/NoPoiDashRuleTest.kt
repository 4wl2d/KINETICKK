// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reference pilot's Dash (decisions Addendum B1): a human cadence and a look at the rocks before a dodge. Both
 * cases fly a quiet arena with a hostile shot always 0.1 s from the Core, so the bot wants a threat-dodge Dash on
 * every input frame; the old bot ([DashRule.LEGACY]) shows what each rule changes.
 */
class NoPoiDashRuleTest {
    /**
     * Under a shot that keeps closing, the human pilot presses at most once per Dash phase: its Dashes start at least
     * the 0.24 s phase apart. The old bot pressed again on the next 0.02 s frame and stacked the impulses.
     */
    @Test fun theHumanPilotDashesAtMostOncePerDashPhase() {
        val human = dashStarts(DashRule.HUMAN)
        val legacy = dashStarts(DashRule.LEGACY)
        assertTrue(human.size >= 3, "the human pilot dodged only ${human.size} times in $FLIGHT_SECONDS s")
        val humanGap = human.zipWithNext { first, next -> next - first }.min()
        assertTrue(humanGap >= DASH_PHASE_SECONDS, "two human Dashes started $humanGap s apart: $human")
        val legacyGap = legacy.zipWithNext { first, next -> next - first }.min()
        assertTrue(legacyGap < 2.5f * INPUT_FRAME, "the old bot is expected to chain Dashes on consecutive frames: $legacy")
    }

    /**
     * Cruising toward a row of rocks below its lethal obstacle speed, a Dash would carry the Core past that speed into
     * them: the human pilot does not press, the old bot does, and the human pilot presses the same dodge in open space.
     */
    @Test fun theHumanPilotSkipsADodgeThatWouldCarryItIntoRocksAtItsLethalSpeed() {
        assertFalse(pressesDash(DashRule.HUMAN, rocksAhead = true), "the human pilot dashed into the rocks")
        assertTrue(pressesDash(DashRule.LEGACY, rocksAhead = true), "the old bot is expected to dash into the rocks")
        assertTrue(pressesDash(DashRule.HUMAN, rocksAhead = false), "the human pilot did not dodge in open space")
    }

    /** The elapsed times at which the Core's Dashes started over [FLIGHT_SECONDS] under a constant threat. */
    private fun dashStarts(rule: DashRule): List<Float> {
        val state = quietArena()
        val bot = NoPoiBot(dashRule = rule)
        val starts = mutableListOf<Float>()
        var dashes = state.characterRuntime.dashSequence
        while (state.elapsed < FLIGHT_SECONDS && state.phase == GamePhase.RUNNING) {
            state.hp = state.maxHp
            closingShot(state)
            bot.control(state)
            state.update(INPUT_FRAME)
            state.takeSoundCues()
            state.takeVisualFxCues()
            if (state.characterRuntime.dashSequence != dashes) starts += state.elapsed
            dashes = state.characterRuntime.dashSequence
        }
        return starts
    }

    /** Whether the bot presses Dash on its first frame, cruising along +x just below the speed a Dash makes lethal. */
    private fun pressesDash(rule: DashRule, rocksAhead: Boolean): Boolean {
        val state = quietArena()
        // A Dash's impulse at the bot's first cursor (within 0.45 rad of +x) adds more than half the impulse.
        state.setVelocityForTesting(state.obstacleLethalSpeed() - 0.5f * state.effectiveDashImpulse(), 0f)
        if (rocksAhead) {
            state.placeObstaclesForTesting(
                testObstacle(state.coreX + ROCK_AHEAD, state.coreY - 110f, id = 1L),
                testObstacle(state.coreX + ROCK_AHEAD, state.coreY, id = 2L),
                testObstacle(state.coreX + ROCK_AHEAD, state.coreY + 110f, id = 3L),
            )
        }
        closingShot(state)
        NoPoiBot(dashRule = rule).control(state)
        return state.dashBufferTime > 0f
    }

    /** A fresh run with nothing else in reach: no enemies, shots, pickups, spawns, sites, events or obstacles. */
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
    }

    /** Replaces every hostile shot with one 60 u to the Core's side, closing at 600 u/s: it would hit in 0.1 s. */
    private fun closingShot(state: MutableGameState) {
        state.projectiles.removeAll { it.hostile }
        state.projectiles += Projectile(
            x = state.coreX, y = state.coreY + 60f, vx = state.velocityX, vy = state.velocityY - 600f,
            radius = 5f, life = 1f, hostile = true,
        )
    }

    private companion object {
        const val SEED = 2026
        const val INPUT_FRAME = 0.02f
        const val FLIGHT_SECONDS = 3f
        /** Where the row of rocks stands ahead of the Core: inside 0.3 s of post-Dash travel, beyond the first frame's. */
        const val ROCK_AHEAD = 450f
    }
}
