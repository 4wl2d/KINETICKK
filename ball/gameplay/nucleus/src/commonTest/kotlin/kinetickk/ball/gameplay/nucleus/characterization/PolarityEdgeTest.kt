// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolarityEdgeTest {
    @Test
    fun defaultCursorSupportsSustainedAccelerationWithoutSpendingPolarity() {
        val engine = runningEngine()
        val initialSpeed = engine.speed
        repeat(1_200) {
            engine.updateCore(STEP)
            engine.updateCamera(STEP)
        }
        assertEquals(1f, engine.polarityStability)
        assertTrue(engine.speed > initialSpeed + 500f, "Normal steering must allow sustained acceleration")
    }

    @Test
    fun normalAreaRestoresPolarityAtAnySpeedWithoutBrakingOrTurning() {
        for (speed in listOf(0f, 200f, 700f, 5_000f)) {
            val engine = runningEngine().apply {
                polarityStability = 0.2f
                velocityX = speed
            }
            repeat(120) { engine.updatePolarityStability(STEP) }
            assertEquals(0.5f, engine.polarityStability, 0.0001f, "speed=$speed")
            repeat(600) { engine.updatePolarityStability(STEP) }
            assertEquals(1f, engine.polarityStability)
        }
    }

    @Test
    fun everyEdgeAndCornerDrainsAtTheSameRateAcrossViewportsAndStepRates() {
        val edgePositions = listOf(
            0f to 0.5f, 1f to 0.5f, 0.5f to 0f, 0.5f to 1f,
            0f to 0f, 0f to 1f, 1f to 0f, 1f to 1f,
        )
        for ((width, height) in listOf(640f to 360f, 1_280f to 720f, 2_560f to 1_080f, 720f to 1_280f)) {
            for (frequency in listOf(30, 60, 120, 240)) {
                for ((x, y) in edgePositions) {
                    val engine = runningEngine().apply {
                        resize(width, height)
                        updatePointer(width * x, height * y)
                    }
                    repeat(frequency) { engine.updatePolarityStability(1f / frequency) }
                    assertEquals(0.6f, engine.polarityStability, 0.0001f, "$width x $height @ $frequency ($x, $y)")
                    repeat(frequency * 2) { engine.updatePolarityStability(1f / frequency) }
                    assertEquals(0f, engine.polarityStability)
                }
            }
        }
    }

    @Test
    fun strainStartsOutsideTheNormalAreaAndIncreasesTowardTheEdge() {
        fun afterOneSecond(x: Float): Float {
            val engine = runningEngine().apply { updatePointer(screenWidth * x, screenHeight * 0.5f) }
            repeat(120) { engine.updatePolarityStability(STEP) }
            return engine.polarityStability
        }
        assertEquals(1f, afterOneSecond(0.1f))
        assertEquals(1f, afterOneSecond(0.9f))
        val justOutside = afterOneSecond(0.901f)
        val halfway = afterOneSecond(0.95f)
        val edge = afterOneSecond(1f)
        assertTrue(justOutside < 1f)
        assertTrue(justOutside > halfway && halfway > edge)
        assertEquals(0.8f, halfway, 0.0001f)
        assertEquals(0.6f, edge, 0.0001f)
    }

    @Test
    fun leavingTheEdgeRestoresPolarityWhileKeepingHighSpeed() {
        val engine = runningEngine().apply {
            velocityX = 5_000f
            updatePointer(screenWidth, screenHeight * 0.5f)
        }
        repeat(120) { engine.updatePolarityStability(STEP) }
        engine.updatePointer(engine.screenWidth * 0.76f, engine.screenHeight * 0.5f)
        repeat(120) { engine.updatePolarityStability(STEP) }
        assertEquals(0.9f, engine.polarityStability, 0.0001f)
        assertEquals(5_000f, engine.speed)
    }

    @Test
    fun cameraLagAndMagnetStrengthDoNotTurnNormalSteeringIntoEdgeStrain() {
        val engine = runningEngine().apply {
            polarityStability = 0.2f
            velocityX = 5_000f
            cameraX = coreX - 1_000f
            cameraY = coreY - 500f
            magnetStrength *= 4f
        }
        engine.updateCore(STEP)
        assertEquals(0.2025f, engine.polarityStability, 0.0001f)
    }

    @Test
    fun kineticImpactStillRewardsSpeedEvenWhenPolarityIsExhausted() {
        fun impactAt(speed: Float, polarity: Float): Float {
            val engine = runningEngine().apply {
                velocityX = speed
                polarityStability = polarity
                critChance = 0f
                addEnemyForTesting(x = 10f, y = 0f, hp = 10_000f, radius = 12f)
            }
            engine.resolveEnemyCoreCollisions()
            return engine.lastImpact
        }
        val slowImpact = impactAt(700f, 1f)
        val fastImpact = impactAt(5_000f, 1f)
        assertTrue(fastImpact > slowImpact)
        assertEquals(fastImpact, impactAt(5_000f, 0f))
    }

    @Test
    fun brakingAndRealTurnsCannotRechargeWhileTheCursorStaysAtTheEdge() {
        val braking = runningEngine().apply {
            braking = true
            velocityX = 100f
            updatePointer(screenWidth, screenHeight * 0.5f)
        }
        repeat(120) { braking.updatePolarityStability(STEP) }
        assertEquals(0.6f, braking.polarityStability, 0.0001f)

        val turning = runningEngine().apply {
            smoothedVelocityX = velocityX
            saturationHeadingX = 1f
            saturationHeadingY = 0f
            turnHeadingEstablished = true
            velocityX = 0f
            velocityY = 700f
            updatePointer(screenWidth, screenHeight * 0.5f)
        }
        repeat(60) { turning.updatePolarityStability(STEP) }
        assertTrue(turning.turnRecoveryCooldown > 0f, "A real turn must still be recognized for maneuver synergies")
        assertEquals(0.8f, turning.polarityStability, 0.0001f)
    }

    @Test
    fun resizingPreservesRelativeEdgeStrainAndReductionDoesNotMutateTheSource() {
        val source = runningEngine().apply { updatePointer(screenWidth * 0.95f, screenHeight * 0.5f) }
        val first = source.copyForReduction()
        val second = source.copyForReduction()
        second.resize(720f, 1_280f)
        repeat(120) {
            first.updatePolarityStability(STEP)
            second.updatePolarityStability(STEP)
        }
        assertEquals(1f, source.polarityStability)
        assertEquals(first.polarityStability, second.polarityStability, 0.0001f)
        assertEquals(0.8f, first.toRenderModel().polarityStability, 0.0001f)
    }

    private fun runningEngine(): GameScenario = gameScenario(initialMatter = 0).apply {
        startRun()
        runGrace = 100f
        velocityX = 700f
    }

    private companion object {
        const val STEP = 1f / 120f
    }
}
