// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TetherSaturationTest {
    @Test
    fun cursorOscillationAtTheEdgeCannotRecoverStabilityDuringStraightFlight() {
        for (width in listOf(640f, 1_280f, 2_560f)) {
            for (frequency in listOf(30, 60, 120, 240)) {
                val engine = runningEngine().apply {
                    resize(width, width * 0.5625f)
                    polarityStability = 1f
                }
                val delta = 1f / frequency
                repeat(frequency * 3) { index ->
                    engine.updatePointer(width, engine.screenHeight * if (index % 2 == 0) 0.1f else 0.9f)
                    engine.velocityX = 700f
                    engine.velocityY = 0f
                    engine.updatePolarityStability(delta)
                }
                assertEquals(0f, engine.polarityStability, "width=$width frequency=$frequency")
                // Moving in and out only earns time-based recovery, never a turn bonus.
                engine.polarityStability = 0.2f
                repeat(frequency) { index ->
                    engine.updatePointer(width * if (index % 2 == 0) 0.51f else 1f, engine.screenHeight * 0.5f)
                    engine.updatePolarityStability(delta)
                }
                assertEquals(0.15f, engine.polarityStability, 0.0001f)
                assertEquals(0f, engine.turnRecoveryCooldown)
            }
        }
    }

    @Test
    fun realTurnRequiresSmoothingAndHoldThenAwardsFortyPercentOnce() {
        val engine = runningEngine()
        repeat(120) { engine.updatePolarityStability(STEP) }
        engine.polarityStability = 0.2f
        engine.velocityX = 0f
        engine.velocityY = 700f
        repeat(24) { engine.updatePolarityStability(STEP) }
        assertEquals(0.26f, engine.polarityStability, 0.0001f, "Only normal recovery before the turn is sustained")
        repeat(36) { engine.updatePolarityStability(STEP) }
        assertEquals(0.75f, engine.polarityStability, 0.0001f)
        engine.polarityStability = 0.1f
        repeat(300) { engine.updatePolarityStability(STEP) }
        assertEquals(0.85f, engine.polarityStability, 0.0001f, "One changed heading must not repeatedly award a turn bonus")
    }

    @Test
    fun lowSpeedTurnsAndShortActualOscillationDoNotCount() {
        val engine = runningEngine().apply { velocityX = 100f }
        repeat(120) { index ->
            engine.velocityX = if (index % 2 == 0) 100f else -100f
            engine.updatePolarityStability(STEP)
        }
        assertEquals(0.5f, engine.polarityStability, 0.0001f)
        assertEquals(0f, engine.turnRecoveryCooldown)
        engine.velocityX = 700f
        repeat(120) { engine.updatePolarityStability(STEP) }
        engine.polarityStability = 0.2f
        repeat(120) { index ->
            engine.velocityY = if (index % 2 == 0) 700f else -700f
            engine.updatePolarityStability(STEP)
        }
        assertEquals(0.5f, engine.polarityStability, 0.0001f)
        assertEquals(0f, engine.turnRecoveryCooldown)
    }

    @Test
    fun recoveryCooldownPreventsRepeatedFastTurns() {
        val engine = runningEngine()
        repeat(120) { engine.updatePolarityStability(STEP) }
        engine.polarityStability = 0.2f
        engine.velocityX = 0f
        engine.velocityY = 700f
        repeat(60) { engine.updatePolarityStability(STEP) }
        val recovered = engine.polarityStability
        engine.velocityX = -700f
        engine.velocityY = 0f
        repeat(60) { engine.updatePolarityStability(STEP) }
        assertEquals(recovered + 0.15f, engine.polarityStability, 0.0001f)
    }

    @Test
    fun brakingAcceleratesNormalRecoveryAtLowActualSpeedOnly() {
        val engine = runningEngine().apply { braking = true }
        repeat(30) { engine.updatePolarityStability(STEP) }
        assertEquals(0.275f, engine.polarityStability, 0.0001f)
        engine.velocityX = 250f
        repeat(60) { engine.updatePolarityStability(STEP) }
        assertEquals(0.675f, engine.polarityStability, 0.0001f)
        engine.braking = false
        engine.velocityX = 0f
        repeat(60) { engine.updatePolarityStability(STEP) }
        assertEquals(0.825f, engine.polarityStability, 0.0001f)
    }

    @Test
    fun exhaustionPreservesLateralControlCounterThrustDashAndUncappedMomentum() {
        val lateral = runningEngine().apply {
            polarityStability = 0f
            updatePointer(screenWidth * 0.5f, screenHeight)
        }
        lateral.updateCore(STEP)
        assertTrue(lateral.velocityY > 10f, "Zero stability must retain lateral steering")
        val reverse = runningEngine().apply {
            polarityStability = 0f
            updatePointer(0f, screenHeight * 0.5f)
        }
        reverse.updateCore(STEP)
        assertTrue(reverse.velocityX < 680f, "Zero stability must retain counter-thrust")
        val momentum = runningEngine().apply {
            polarityStability = 0f
            velocityX = 5_000f
            updatePointer(screenWidth, screenHeight * 0.5f)
        }
        momentum.updateCore(STEP)
        assertTrue(momentum.speed > 4_900f, "No speed ceiling or extra fatigue drag")
        val beforeDash = momentum.speed
        momentum.performDash()
        assertTrue(momentum.speed > beforeDash + 580f)
    }

    @Test
    fun exhaustedCoreCanRecenterAndPerformRealManeuverWithoutDash() {
        val engine = runningEngine().apply {
            polarityStability = 0f
            smoothedVelocityX = 700f
            turnHeadingEstablished = true
            updatePointer(screenWidth * 0.5f, screenHeight * 0.8f)
        }
        repeat(90) {
            engine.cameraX = engine.coreX
            engine.cameraY = engine.coreY
            engine.updateCore(STEP)
        }
        assertTrue(engine.velocityY > 300f)
        assertTrue(engine.polarityStability > 0.05f, "Actual maneuver must recover from total exhaustion")
    }

    @Test
    fun movementRecoveryStateIsIsolatedInReductionCopiesAndResetForNextRun() {
        val engine = runningEngine().apply {
            smoothedVelocityX = 300f
            smoothedVelocityY = 500f
            turnHoldTime = 0.13f
            turnDirection = -1
            turnRecoveryCooldown = 0.45f
            turnHeadingEstablished = true
        }
        val fork = engine.copyForReduction()
        assertEquals(engine.smoothedVelocityX, fork.smoothedVelocityX)
        assertEquals(engine.smoothedVelocityY, fork.smoothedVelocityY)
        assertEquals(engine.turnHoldTime, fork.turnHoldTime)
        assertEquals(engine.turnDirection, fork.turnDirection)
        assertEquals(engine.turnRecoveryCooldown, fork.turnRecoveryCooldown)
        fork.updatePolarityStability(STEP)
        assertEquals(0.45f, engine.turnRecoveryCooldown)
        fork.startRun()
        assertEquals(0f, fork.turnRecoveryCooldown)
        assertEquals(0f, fork.smoothedVelocityX)
        assertTrue(!fork.turnHeadingEstablished)
    }

    private fun runningEngine(): GameScenario = gameScenario(initialMatter = 0).apply {
        startRun()
        runGrace = 100f
        polarityStability = 0.2f
        velocityX = 700f
    }

    private companion object {
        const val STEP = 1f / 120f
    }
}
