// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Calibrates the collision bands against real flight (dp units): holding the cursor at reach 0.35 stays
 * below the damage-start speed, 0.6 lands in the damage band and 0.79 (just inside the edge strain)
 * is lethal, on a 1280×800 desktop and a 412×915 phone, along both screen axes.
 */
class ObstacleCalibrationTest {
    private val step = MutableGameState.FIXED_STEP
    private val emptyWorld = canonicalGameplayContent.copy(
        obstacles = canonicalGameplayContent.obstacles.copy(meanPerChunk = 0f),
    )

    private class Measured(val speed: Float, val start: Float, val lethal: Float)

    /** Terminal speed with the cursor held at [reach] toward +x ([horizontal]) or +y, after 20 s. */
    private fun measure(width: Float, height: Float, reach: Float, horizontal: Boolean, tier: Int): Measured {
        val state = buildTestState(content = emptyWorld, rebirth = tier).apply {
            resize(width, height)
            startRun()
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 0f
            // Overdrive would add thrust; the bands are measured on plain flight.
            overdriveBurnoutUntil = Float.MAX_VALUE
            if (horizontal) updatePointer(width * 0.5f * (1f + reach), height * 0.5f)
            else updatePointer(width * 0.5f, height * 0.5f * (1f + reach))
        }
        repeat((20f / step).toInt()) {
            // At a gentle reach the Core rides close to the singularity; the grace keeps it measuring.
            state.runGrace = 1f
            state.simulateStep(step)
            state.enemies.clear()
            state.projectiles.clear()
        }
        assertEquals(GamePhase.RUNNING, state.phase)
        val heading = atan2(state.velocityY, state.velocityX)
        return Measured(state.speed, state.obstacleDamageStartSpeed(heading), state.obstacleLethalSpeed(heading))
    }

    @Test
    fun cursorReachDecidesTheCollisionBandOnDesktopAndPhone() {
        val viewports = listOf(1_280f to 800f, 412f to 915f)
        for ((width, height) in viewports) for (horizontal in listOf(true, false)) for (tier in listOf(0, 3)) {
            val gentle = measure(width, height, 0.35f, horizontal, tier)
            val brisk = measure(width, height, 0.6f, horizontal, tier)
            val edge = measure(width, height, 0.79f, horizontal, tier)
            val label = "${width.toInt()}x${height.toInt()} ${if (horizontal) "x" else "y"} tier $tier"
            println(
                "OBSTACLE CALIBRATION $label: reach 0.35=${gentle.speed} 0.6=${brisk.speed} 0.79=${edge.speed} " +
                    "start=${brisk.start} lethal=${brisk.lethal}",
            )
            assertTrue(gentle.speed < gentle.start, "$label: reach 0.35 (${gentle.speed}) below ${gentle.start}")
            assertTrue(brisk.speed >= brisk.start && brisk.speed < brisk.lethal,
                "$label: reach 0.6 (${brisk.speed}) inside ${brisk.start}..${brisk.lethal}")
            assertTrue(edge.speed >= edge.lethal, "$label: reach 0.79 (${edge.speed}) at least ${edge.lethal}")
        }
    }

    @Test
    fun theSteadySpeedModelMatchesMeasuredFlight() {
        for ((width, height) in listOf(1_280f to 800f, 412f to 915f)) for (reach in listOf(0.35f, 0.6f, 0.79f)) {
            val measured = measure(width, height, reach, horizontal = true, tier = 3)
            val model = buildTestState(content = emptyWorld).apply { resize(width, height) }.steadyCursorSpeed(reach, 0f)
            assertEquals(model, measured.speed, model * 0.03f, "steady model at ${width.toInt()} reach $reach")
        }
    }

    @Test
    fun prismFlyingIntoAnAsteroidAtLethalSpeedWithoutSecondWindDies() {
        val state = buildTestState(shape = CoreShape.PRISM)
        state.placeObstaclesForTesting(testObstacle(400f, 0f, radius = 120f))
        state.setVelocityForTesting(state.obstacleLethalSpeed(0f) + 100f, 0f)
        state.updatePointer(state.screenWidth, state.screenHeight * 0.5f)
        repeat(60) { if (state.phase == GamePhase.RUNNING) state.simulateStep(step) }
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
    }
}
