// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.characters

import java.lang.management.ManagementFactory
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.GameplayTickOutputs
import kinetickk.ball.gameplay.nucleus.GameplayTickResult
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.performance.authorityState
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The echo ghost's ring buffer must not be copied per fixed step (SPEC 8.2). */
class CharacterRuntimeAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)
    private val outputs = GameplayTickOutputs()

    /** A committed run of [shape] that has moved long enough for the Spiral's ghost to be active. */
    private fun committed(shape: CoreShape): GameplayState {
        val model = MutableGameState(canonicalGameplayContent, seed = 3_131).apply {
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
        var state = authorityState(model)
        repeat(CharacterRuntime.GHOST_SAMPLES + 30) { state = tick(state) }
        model.sites = emptyList()
        model.spawnBudget = 0f
        return state
    }

    private fun tick(state: GameplayState): GameplayState =
        assertIs<GameplayTickResult.Accepted>(GameplayNucleus.tick(state, frame, outputs)).nextState

    /**
     * Median bytes one single-step in-place tick allocates for [first] and for [second], ticked alternately so both
     * runs meet the same compiled code. The runs advance, so the median keeps an occasional step that regenerates the
     * world window from counting as per-step cost.
     */
    private fun medianBytesPerOneStepTick(first: GameplayState, second: GameplayState): Pair<Long, Long> {
        var a = tick(first)
        var b = tick(second)
        assertEquals(1, checkNotNull(a.engine).model.lastTransitionSteps)
        assertEquals(1, checkNotNull(b.engine).model.lastTransitionSteps)
        repeat(WARMUP) {
            a = tick(a)
            b = tick(b)
        }
        val thread = Thread.currentThread().id
        val firstSamples = LongArray(ROUNDS)
        val secondSamples = LongArray(ROUNDS)
        for (round in 0 until ROUNDS) {
            var before = threads.getThreadAllocatedBytes(thread)
            a = tick(a)
            firstSamples[round] = threads.getThreadAllocatedBytes(thread) - before
            before = threads.getThreadAllocatedBytes(thread)
            b = tick(b)
            secondSamples[round] = threads.getThreadAllocatedBytes(thread) - before
        }
        assertEquals(1, checkNotNull(a.engine).model.lastTransitionSteps)
        assertEquals(1, checkNotNull(b.engine).model.lastTransitionSteps)
        firstSamples.sort()
        secondSamples.sort()
        return firstSamples[ROUNDS / 2] to secondSamples[ROUNDS / 2]
    }

    @Test
    fun aOneStepSpiralTickWritesTheEchoRingInPlace() {
        val spiral = committed(CoreShape.SPIRAL)
        assertTrue(checkNotNull(spiral.engine).model.ghostActive)
        val orb = committed(CoreShape.ORB)
        val ringBytes = CharacterRuntime.GHOST_SAMPLES * CharacterRuntime.GHOST_STRIDE * Float.SIZE_BYTES
        val (spiralBytes, orbBytes) = medianBytesPerOneStepTick(spiral, orb)
        val extra = spiralBytes - orbBytes
        println("ECHO-RING-TICK-ALLOCATION extra=$extra ring=$ringBytes")
        // A copied page alone would cost about 100 bytes and a copied ring more than ringBytes.
        assertTrue(extra <= 64, "a one-step Spiral tick allocates $extra bytes more than the Orb's")
    }

    private companion object {
        const val WARMUP = 4_000
        const val ROUNDS = 1_501
    }
}
