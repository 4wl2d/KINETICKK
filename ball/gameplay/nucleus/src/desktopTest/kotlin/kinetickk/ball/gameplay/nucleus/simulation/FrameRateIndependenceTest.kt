// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The fixed step makes the simulation a function of executed steps and the inputs between them,
 * never of the presentation rate. Inputs change only at k/6 s, a frame boundary at 30, 60, 144
 * and 240 Hz, so each must land after exactly 20·k steps; states with equal step counts must then
 * be identical at every rate.
 */
class FrameRateIndependenceTest {
    @Test
    fun equalStepCountsGiveIdenticalStatesAtEveryFrameRate() {
        val traces = FRAME_RATES.associateWith { rate -> trace(rate) }
        val reference = traces.getValue(FRAME_RATES.first())
        assertTrue(reference.size >= SECONDS * 120 / COMPARE_EVERY_STEPS, "reference trace too short: ${reference.size}")
        traces.forEach { (rate, trace) ->
            assertEquals(reference.keys, trace.keys, "compared step counts at $rate Hz")
            reference.forEach { (steps, fingerprint) ->
                assertEquals(fingerprint, trace.getValue(steps), "state after $steps steps at $rate Hz")
            }
        }
    }

    @Test
    fun stepAdmissionToleratesExactlyTwoUlpsOfTheFixedStep() {
        // The portable (Wasm) definition equals the JVM ulp, so every target admits the same steps.
        assertEquals(Math.ulp(MutableGameState.FIXED_STEP) * 2f, FIXED_STEP_ROUNDING_TOLERANCE)
        val threshold = Float.fromBits(MutableGameState.FIXED_STEP.toRawBits() - 2)
        assertTrue(fixedStepIsDue(threshold))
        assertTrue(!fixedStepIsDue(Float.fromBits(threshold.toRawBits() - 1)))
    }

    @Test
    fun aSlowFrameOnlyDefersStepsWithinTheAccumulatorClamp() {
        // 10 fps stays real time: each 0.1 s frame runs exactly twelve steps.
        val reducer = GameReducer()
        var engine = EngineState(newRun())
        repeat(20) {
            engine = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(engine, GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)),
            ).state
            assertEquals(12, engine.model.lastTransitionSteps)
        }
        // Below 10 fps the 0.1 s clamp deliberately slows the simulation instead of spiralling.
        engine = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(engine, GameplayInteractionPulse.FrameElapsed.fromValidated(0.5f)),
        ).state
        assertEquals(12, engine.model.lastTransitionSteps)
    }

    private fun trace(rate: Int): Map<Int, List<Any>> {
        val reducer = GameReducer()
        var engine = EngineState(newRun())
        val delta = 1f / rate
        var steps = 0
        val fingerprints = linkedMapOf<Int, List<Any>>()
        fingerprints[0] = engine.model.fingerprint()
        val frames = SECONDS * rate
        for (frame in 1..frames) {
            engine = reduce(reducer, engine, GameplayInteractionPulse.FrameElapsed.fromValidated(delta))
            steps += engine.model.lastTransitionSteps
            assertEquals(GamePhase.RUNNING, engine.model.phase, "run ended at $rate Hz after $steps steps")
            if (steps % COMPARE_EVERY_STEPS == 0 && steps !in fingerprints) fingerprints[steps] = engine.model.fingerprint()
            if ((frame * INPUT_CHANGES_PER_SECOND) % rate == 0) {
                val change = frame * INPUT_CHANGES_PER_SECOND / rate
                assertEquals(change * STEPS_PER_INPUT_CHANGE, steps, "input change $change at $rate Hz")
                inputsAt(change).forEach { pulse -> engine = reduce(reducer, engine, pulse) }
            }
        }
        return fingerprints
    }

    /** A deterministic steering script: sweeping pointer, a Dash every second, Brake pulses. */
    private fun inputsAt(change: Int): List<GameplayInteractionPulse> = buildList {
        val angle = change * 0.9f
        add(
            GameplayInteractionPulse.PointerMoved.fromValidated(
                640f + kotlin.math.cos(angle) * (180f + (change % 4) * 60f),
                360f + kotlin.math.sin(angle) * (120f + (change % 3) * 40f),
            ),
        )
        if (change % 6 == 3) add(GameplayInteractionPulse.DashRequested)
        if (change % 5 == 1) add(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, active = true))
        if (change % 5 == 2) add(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, active = false))
    }

    private fun reduce(reducer: GameReducer, engine: EngineState, pulse: GameplayInteractionPulse): EngineState {
        val accepted = assertIs<GameReductionResult.Accepted>(reducer.reduce(engine, pulse))
        return accepted.state
    }

    private fun newRun(): MutableGameState = MutableGameState(canonicalGameplayContent, seed = 6_061).apply {
        applyPreferences(settings.copy(simulationSpeed = 1f))
        startRun()
        check(settings.simulationSpeed == 1f)
    }

    private fun MutableGameState.fingerprint(): List<Any> = buildList {
        add(elapsed.toRawBits())
        add(coreX.toRawBits()); add(coreY.toRawBits())
        add(velocityX.toRawBits()); add(velocityY.toRawBits())
        add(cameraX.toRawBits()); add(cameraY.toRawBits())
        add(hp.toRawBits()); add(heat.toRawBits()); add(data); add(kills); add(polarityStability.toRawBits())
        add(spawnClock.toRawBits()); add(gameplayRandom.copy().nextInt())
        enemies.forEach { enemy ->
            add("${enemy.id}:${enemy.type}:${enemy.x.toRawBits()}:${enemy.y.toRawBits()}:${enemy.hp.toRawBits()}")
        }
        projectiles.forEach { projectile -> add("p:${projectile.x.toRawBits()}:${projectile.y.toRawBits()}") }
        pickups.forEach { pickup -> add("d:${pickup.x.toRawBits()}:${pickup.y.toRawBits()}") }
    }

    private companion object {
        val FRAME_RATES = listOf(30, 60, 144, 240)
        const val SECONDS = 8
        const val INPUT_CHANGES_PER_SECOND = 6
        const val STEPS_PER_INPUT_CHANGE = 20
        const val COMPARE_EVERY_STEPS = 4
    }
}
