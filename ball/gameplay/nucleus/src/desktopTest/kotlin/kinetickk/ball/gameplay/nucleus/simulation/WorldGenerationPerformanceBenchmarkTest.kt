// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test

/** Chunk generation is separate from the fixed-step terrain benchmark, which starts with a built window. */
class WorldGenerationPerformanceBenchmarkTest {
    @Test
    fun measureWorldGeneration() {
        if (System.getenv("KK_WORLD_GENERATION_BENCH") != "1") return
        val state = MutableGameState(canonicalGameplayContent, seed = 731_991).apply { startRun() }
        val chunk = state.content.obstacles.chunkSize
        measure("adjacent_chunk_window", { index ->
            state.coreX = (40 + index % 128) * chunk + chunk * 0.25f
            state.coreY = -27f * chunk + chunk * 0.25f
            state.refreshObstacleWindow()
        }) { state.worldWitness() }
        measure("distant_chunk_window", { index ->
            state.coreX = (40 + (index % 128) * 7) * chunk + chunk * 0.25f
            state.coreY = (-27 - (index % 13) * 7) * chunk + chunk * 0.25f
            state.refreshObstacleWindow()
        }) { state.worldWitness() }
        var placementWitness = 0L
        measure("structure_placement_queries", { index ->
            var value = 1L
            repeat(64) { offset ->
                val placement = state.structurePlacement(10 + (index + offset) % 128, -20 + offset % 17)
                value = value * 31L + (placement?.id ?: 0L)
                value = value * 31L + (placement?.absoluteX?.toRawBits() ?: 0L)
                value = value * 31L + (placement?.absoluteY?.toRawBits() ?: 0L)
            }
            placementWitness = value
        }) { placementWitness }
    }

    private fun measure(scenario: String, operation: (Int) -> Unit, witness: () -> Long) {
        val warmups = System.getenv("KK_WORLD_GENERATION_WARMUPS")?.toInt() ?: 1_024
        val samples = System.getenv("KK_WORLD_GENERATION_SAMPLES")?.toInt() ?: 4_096
        require(warmups > 0 && samples > 0)
        repeat(warmups, operation)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(threads.isThreadAllocatedMemorySupported)
        if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val times = LongArray(samples)
        var allocated = 0L
        var outcome = 1L
        repeat(samples) { index ->
            val beforeBytes = threads.getThreadAllocatedBytes(thread)
            val before = System.nanoTime()
            operation(index)
            times[index] = System.nanoTime() - before
            allocated += threads.getThreadAllocatedBytes(thread) - beforeBytes
            outcome = outcome * 31L + witness()
        }
        times.sort()
        fun percentile(fraction: Double) = times[((samples - 1) * fraction).toInt()] / 1_000_000.0
        val label = (System.getenv("KK_WORLD_GENERATION_LABEL") ?: "run")
            .replace("\\", "\\\\").replace("\"", "\\\"")
        val result = "{\"suite\":\"world-generation-v1\",\"scenario\":\"$scenario\",\"label\":\"$label\"," +
            "\"warmups\":$warmups,\"samples\":$samples,\"witness\":$outcome," +
            "\"meanMs\":${times.average() / 1_000_000.0},\"p50Ms\":${percentile(0.5)}," +
            "\"p95Ms\":${percentile(0.95)},\"p99Ms\":${percentile(0.99)}," +
            "\"allocatedBytesPerOperation\":${allocated.toDouble() / samples}}"
        println(result)
        File(System.getenv("KK_WORLD_GENERATION_OUT") ?: "build/world-generation-benchmark.jsonl").apply {
            parentFile?.mkdirs()
            appendText(result + "\n")
        }
    }

    private fun MutableGameState.worldWitness(): Long {
        var value = 1L
        for (obstacle in obstacleField.obstacles) {
            value = value * 31L + obstacle.id
            value = value * 31L + obstacle.kind.ordinal
            value = value * 31L + obstacle.x.toRawBits()
            value = value * 31L + obstacle.y.toRawBits()
            value = value * 31L + obstacle.radius.toRawBits()
            value = value * 31L + obstacle.shapeSeed
        }
        for (structure in structureField.structures) {
            value = value * 31L + structure.id
            value = value * 31L + structure.template.ordinal
            value = value * 31L + structure.rotation
            value = value * 31L + structure.x.toRawBits()
            value = value * 31L + structure.y.toRawBits()
        }
        for (wall in structureField.walls) {
            value = value * 31L + wall.id
            value = value * 31L + wall.kind.ordinal
            value = value * 31L + wall.ax.toRawBits()
            value = value * 31L + wall.ay.toRawBits()
            value = value * 31L + wall.bx.toRawBits()
            value = value * 31L + wall.by.toRawBits()
        }
        return value
    }
}
