// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.ParticleDensity
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Separates accepted FX cue application and immutable publication from Canvas drawing. */
class ReactionFxPerformanceBenchmarkTest {
    @Test
    fun measureReactionHeavyFxApplyAndSnapshot() {
        if (System.getenv("KK_REACTION_FX_BENCH") != "1") return
        val warmups = System.getenv("KK_REACTION_FX_WARMUPS")?.toInt() ?: 480
        val samples = System.getenv("KK_REACTION_FX_SAMPLES")?.toInt() ?: 1_920
        require(warmups > 0 && samples > 0)
        // The same stream as the Canvas fixture; no list/cue construction enters the timer.
        val cues = List(warmups + samples) { reactionCues(it) }
        val reducer = InteractionFxReducer(731_991)
        repeat(warmups) { reducer.apply(cues[it]); reducer.snapshot() }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(threads.isThreadAllocatedMemorySupported)
        if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val elapsed = LongArray(samples)
        var snapshot = VisualFxProjection.EMPTY
        var witness = 1L
        val allocatedBefore = threads.getThreadAllocatedBytes(thread)
        for (sample in elapsed.indices) {
            val started = System.nanoTime()
            reducer.apply(cues[warmups + sample])
            snapshot = reducer.snapshot()
            elapsed[sample] = System.nanoTime() - started
            // Consume changing published values outside the timer, without allocating.
            witness = witness * 31L + snapshot.particles.first().x.toBits()
            witness = witness * 31L + snapshot.damageNumbers.last().amount
            witness = witness * 31L + snapshot.weaponArcs.size
        }
        val allocated = threads.getThreadAllocatedBytes(thread) - allocatedBefore
        assertEquals(InteractionFxLimits.MAX_DAMAGE_NUMBERS, snapshot.damageNumbers.size)
        assertTrue(snapshot.particles.size in 500..InteractionFxLimits.MAX_PARTICLES)
        elapsed.sort()
        fun percentile(fraction: Double): Double = elapsed[((samples - 1) * fraction).toInt()] / 1_000_000.0
        val label = (System.getenv("KK_REACTION_FX_LABEL") ?: "run").replace("\\", "\\\\").replace("\"", "\\\"")
        val result = "{\"suite\":\"reaction-fx-v1\",\"scenario\":\"reaction_fx_apply_snapshot\",\"label\":\"$label\"," +
            "\"warmups\":$warmups,\"samples\":$samples,\"witness\":$witness," +
            "\"particles\":${snapshot.particles.size},\"damageNumbers\":${snapshot.damageNumbers.size}," +
            "\"arcs\":${snapshot.weaponArcs.size},\"elementBursts\":${snapshot.elementBursts.size}," +
            "\"meanMs\":${elapsed.average() / 1_000_000.0},\"p50Ms\":${percentile(0.5)}," +
            "\"p95Ms\":${percentile(0.95)},\"p99Ms\":${percentile(0.99)}," +
            "\"allocatedBytesPerFrame\":${allocated.toDouble() / samples}}"
        println(result)
        File(System.getenv("KK_REACTION_FX_OUT") ?: "build/reaction-fx-benchmark.jsonl").apply {
            parentFile?.mkdirs()
            appendText(result + "\n")
        }
    }

    @Test
    fun particleAdvancementKeepsExactMotionAndPublishedSnapshots() {
        val reducer = InteractionFxReducer(731_991)
        reducer.apply(listOf(VisualFxCue.Burst(31f, -17f, 120, 2, ParticleDensity.NORMAL)))
        val before = reducer.snapshot()
        val frozen = before.particles.toList()
        val delta = 1f / 60f
        reducer.apply(listOf(VisualFxCue.EffectsAdvanced(delta)))
        val after = reducer.snapshot()
        assertEquals(before.particles.size, after.particles.size)
        for (index in frozen.indices) {
            val previous = frozen[index]
            val current = after.particles[index]
            assertEquals((previous.x + previous.vx * delta).toBits(), current.x.toBits())
            assertEquals((previous.y + previous.vy * delta).toBits(), current.y.toBits())
            assertEquals((previous.vx * exp(-2.2f * delta)).toBits(), current.vx.toBits())
            assertEquals((previous.vy * exp(-2.2f * delta)).toBits(), current.vy.toBits())
            assertEquals((previous.life - delta).toBits(), current.life.toBits())
            assertEquals(previous.maxLife, current.maxLife)
            assertEquals(previous.colorIndex, current.colorIndex)
            assertEquals(previous.size, current.size)
        }
        assertEquals(frozen, before.particles.toList())
    }
}
