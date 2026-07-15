// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.model

import kotlin.test.Test
import kotlin.test.assertTrue

class TetherSaturationTest {
    @Test
    fun heldEdgeExhaustsPullAndThenSlowsTheCore() {
        val engine = runningEngine(seed = 60)
        engine.updatePointer(SCREEN_WIDTH, SCREEN_HEIGHT * 0.5f)

        val saturationStartedAt = engine.elapsed
        advanceUntil(engine, timeoutSeconds = 4f) { engine.polarityStability <= 0.001f }
        val saturationTime = engine.elapsed - saturationStartedAt
        val speedAtExhaustion = engine.speed

        assertTrue(
            saturationTime in 2.45f..2.60f,
            "Held edge should exhaust polarity in about 2.5 simulated seconds, took $saturationTime",
        )
        assertTrue(engine.tetherAuthority < 0.0001f, "Tether retained authority: ${engine.tetherAuthority}")
        assertTrue(speedAtExhaustion > 500f, "The Core never built meaningful momentum: $speedAtExhaustion")

        advanceBy(engine, seconds = 3f)

        assertTrue(engine.phase == GamePhase.RUNNING)
        assertTrue(engine.polarityStability <= 0.001f)
        assertTrue(
            engine.speed < speedAtExhaustion * 0.5f,
            "A saturated tether sustained edge-flight: $speedAtExhaustion -> ${engine.speed}",
        )
    }

    @Test
    fun ninetyDegreeTurnRelievesSaturation() {
        val engine = saturatedEngine(seed = 61)
        val stabilityBeforeTurn = engine.polarityStability

        engine.updatePointer(SCREEN_WIDTH * 0.5f, SCREEN_HEIGHT)
        step(engine)

        assertTrue(engine.phase == GamePhase.RUNNING)
        assertTrue(
            engine.polarityStability > stabilityBeforeTurn + 0.65f,
            "A deliberate turn did not restore polarity: $stabilityBeforeTurn -> ${engine.polarityStability}",
        )
        assertTrue(engine.tetherAuthority > 0.4f, "Turn left too little tether authority: ${engine.tetherAuthority}")
    }

    @Test
    fun bringingAimInwardRecoversSaturation() {
        val engine = saturatedEngine(seed = 62)

        // Let ordinary drag reduce the edge-flight camera lag before moving the lethal
        // singularity inward, then brake while the polarity field recovers.
        advanceBy(engine, seconds = 3f)
        engine.setBrake(true)
        engine.updatePointer(SCREEN_WIDTH * 0.61f, SCREEN_HEIGHT * 0.5f)

        advanceBy(engine, seconds = 1.75f)

        assertTrue(engine.phase == GamePhase.RUNNING)
        assertTrue(
            engine.polarityStability > 0.9f,
            "Inward aim did not restore polarity: ${engine.polarityStability}",
        )
        assertTrue(engine.tetherAuthority > 0.8f)
    }

    @Test
    fun dashKeepsItsAuthorityWhileTetherIsSaturated() {
        val engine = saturatedEngine(seed = 63)
        engine.setVelocityForTesting(0f, 0f)

        engine.requestDash()
        step(engine)

        assertTrue(engine.phase == GamePhase.RUNNING)
        assertTrue(engine.polarityStability <= 0.001f)
        assertTrue(engine.speed > 570f, "Saturation incorrectly weakened Dash: ${engine.speed}")
        assertTrue(engine.heat > 30f)
    }

    @Test
    fun saturatedTetherDoesNotCapExistingVelocity() {
        val engine = saturatedEngine(seed = 64)
        engine.setVelocityForTesting(5_000f, 320f)
        val speedBeforeStep = engine.speed

        step(engine)

        assertTrue(engine.polarityStability <= 0.001f)
        assertTrue(engine.speed > 4_000f, "Physical velocity was capped at ${engine.speed}")
        assertTrue(
            engine.speed > speedBeforeStep * 0.98f,
            "Saturation added momentum-killing drag: $speedBeforeStep -> ${engine.speed}",
        )
        assertTrue(engine.velocityX.isFinite())
        assertTrue(engine.velocityY.isFinite())
    }

    private fun saturatedEngine(seed: Int): GameEngine = runningEngine(seed).apply {
        updatePointer(SCREEN_WIDTH, SCREEN_HEIGHT * 0.5f)
        advanceUntil(this, timeoutSeconds = 4f) { polarityStability <= 0.001f }
        assertTrue(polarityStability <= 0.001f, "Test setup failed to saturate tether: $polarityStability")
    }

    private fun runningEngine(seed: Int): GameEngine = GameEngine(seed = seed, initialMatter = 0).apply {
        resize(SCREEN_WIDTH, SCREEN_HEIGHT)
        startRun()
        clearHazards(this)
    }

    private fun advanceUntil(
        engine: GameEngine,
        timeoutSeconds: Float,
        condition: () -> Boolean,
    ) {
        val deadline = engine.elapsed + timeoutSeconds
        while (!condition() && engine.phase == GamePhase.RUNNING && engine.elapsed < deadline) {
            step(engine)
        }
        assertTrue(condition(), "Condition was not reached within $timeoutSeconds simulated seconds")
    }

    private fun advanceBy(engine: GameEngine, seconds: Float) {
        val targetElapsed = engine.elapsed + seconds
        while (engine.phase == GamePhase.RUNNING && engine.elapsed < targetElapsed) {
            step(engine)
        }
    }

    private fun step(engine: GameEngine) {
        clearHazards(engine)
        engine.update(GameEngine.FIXED_STEP)
        clearHazards(engine)
    }

    private fun clearHazards(engine: GameEngine) {
        engine.enemies.clear()
        engine.projectiles.clear()
    }

    private companion object {
        const val SCREEN_WIDTH = 1_280f
        const val SCREEN_HEIGHT = 720f
    }
}
