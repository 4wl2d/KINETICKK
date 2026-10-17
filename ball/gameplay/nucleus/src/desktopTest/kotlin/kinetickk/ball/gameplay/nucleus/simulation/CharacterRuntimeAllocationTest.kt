// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import java.lang.management.ManagementFactory
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The echo ghost's ring buffer must not be copied per fixed step (SPEC 8.2). */
class CharacterRuntimeAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val reducer = GameReducer()
    private val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)

    /** A committed run of [shape] that has moved long enough for the Spiral's ghost to be active. */
    private fun committed(shape: CoreShape): EngineState {
        val state = MutableGameState(canonicalGameplayContent, seed = 3_131).apply {
            applyPreferences(settings.copy(simulationSpeed = 1f))
            resize(1_280f, 720f)
            coreShape = shape
            startRun()
            enemies.clear()
            projectiles.clear()
            pickups.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 60f
            updatePointer(screenWidth * 0.5f + 260f, screenHeight * 0.5f + 120f)
        }
        var engine = EngineState(state)
        repeat(CharacterRuntime.GHOST_SAMPLES + 30) { engine = reduce(engine) }
        engine.model.pointsOfInterest = emptyList()
        engine.model.spawnBudget = 0f
        return engine
    }

    private fun reduce(engine: EngineState): EngineState =
        assertIs<GameReductionResult.Accepted>(reducer.reduce(engine, frame)).state

    /** Average bytes one single-step reduction from the same committed state allocates. */
    private fun bytesPerOneStepReduction(engine: EngineState): Long {
        assertEquals(1, reduce(engine).model.lastTransitionSteps)
        repeat(WARMUP) { reduce(engine) }
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(ROUNDS) { reduce(engine) }
        return (threads.getThreadAllocatedBytes(thread) - before) / ROUNDS
    }

    @Test
    fun aOneStepSpiralReductionCopiesOnePageOfTheEchoRingNotTheRing() {
        val spiral = committed(CoreShape.SPIRAL)
        assertTrue(spiral.model.ghostActive)
        val orb = committed(CoreShape.ORB)
        val ringBytes = CharacterRuntime.GHOST_SAMPLES * CharacterRuntime.GHOST_STRIDE * Float.SIZE_BYTES
        val extra = bytesPerOneStepReduction(spiral) - bytesPerOneStepReduction(orb)
        println("ECHO-RING-ALLOCATION extra=$extra ring=$ringBytes")
        // A copied ring alone would cost more than ringBytes; one page plus the page table costs about 250.
        assertTrue(extra < ringBytes / 2, "a one-step Spiral reduction allocates $extra bytes more than the Orb's")
    }

    private companion object {
        const val WARMUP = 2_000
        const val ROUNDS = 4_000
    }
}
