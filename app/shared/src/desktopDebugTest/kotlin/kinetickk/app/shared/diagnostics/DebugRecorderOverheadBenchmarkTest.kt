// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kinetickk.foundation.diagnostics.DiagnosticPhase

/**
 * Opt-in measurement of real recorder callbacks, with prebuilt event text and no UI or export in
 * the timer. Reversed case order counterbalances JVM warmup/drift. This is CPU/allocation overhead
 * per simulated frame, not a measurement of the game's FPS, GPU, startup or Release renderer.
 */
class DebugRecorderOverheadBenchmarkTest {
    @Test
    fun measureRecorderCallbackOverhead() {
        if (System.getenv("KK_DEBUG_RECORDER_BENCH") != "1") return
        val warmups = System.getenv("KK_DEBUG_RECORDER_WARMUPS")?.toInt() ?: 8
        val samples = System.getenv("KK_DEBUG_RECORDER_SAMPLES")?.toInt() ?: 12
        val frames = System.getenv("KK_DEBUG_RECORDER_FRAMES")?.toInt() ?: 100_000
        require(warmups > 0 && samples > 0 && frames >= 100)
        val stopped = Probe("stopped", DebugSession())
        val visible = Probe("live", DebugSession().apply { panelVisible = true })
        val recording = Probe("recording", DebugSession().apply { start() })
        val probes = listOf(stopped, visible, recording)
        val order = listOf(stopped, visible, recording, recording, visible, stopped)
        repeat(warmups) { order.forEach { it.run(frames) } }
        val allocationBean = (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
            ?.apply { if (!isThreadAllocatedMemoryEnabled) isThreadAllocatedMemoryEnabled = true }
        val thread = Thread.currentThread().id
        val rows = mutableListOf<String>()
        repeat(samples) { sample ->
            order.forEach { probe ->
                val beforeBytes = allocationBean?.getThreadAllocatedBytes(thread)
                val before = System.nanoTime()
                probe.run(frames)
                val elapsed = System.nanoTime() - before
                val bytes = beforeBytes?.let { allocationBean?.getThreadAllocatedBytes(thread)?.minus(it) }
                rows += "{\"round\":$sample,\"mode\":\"${probe.name}\",\"nsPerFrame\":${elapsed.toDouble() / frames}," +
                    "\"allocatedBytesPerFrame\":${bytes?.toDouble()?.div(frames)}}"
            }
        }
        recording.session.stop()
        assertEquals(0L, stopped.session.recordedFrames)
        assertEquals(0L, visible.session.recordedFrames)
        assertEquals((warmups + samples).toLong() * frames * 2L - 1L, recording.session.recordedFrames)
        assertTrue(recording.session.overwrittenFrames > 0L)
        assertTrue(recording.session.export().contains("KINETICKK_DEBUG_DIAGNOSTICS_V1"))
        val metadata = listOf(
            "suite" to "debug-recorder-callbacks-v1",
            "javaVersion" to System.getProperty("java.version"),
            "vmName" to System.getProperty("java.vm.name"),
            "osName" to System.getProperty("os.name"),
            "architecture" to System.getProperty("os.arch"),
            "label" to (System.getenv("KK_DEBUG_RECORDER_LABEL") ?: "current"),
        ).joinToString { (key, value) -> "\"$key\":${jsonString(value)}" }
        val result = "{$metadata,\"warmups\":$warmups,\"rounds\":$samples,\"framesPerBatch\":$frames," +
            "\"sequence\":\"stopped,live,recording,recording,live,stopped\"," +
            "\"operationsPerFrame\":\"frame + dispatch/simulation/projection/audio/draw + workload; event every 100 frames\"," +
            "\"limits\":\"Synthetic callback overhead only. No UI refresh, export, downstream crash logging, game logic or GPU. Stopped is a Debug recorder with collection disabled, not a Release build.\"," +
            "\"samples\":[${rows.joinToString()}]}"
        File(System.getenv("KK_DEBUG_RECORDER_OUT") ?: "build/debug-recorder-benchmark.jsonl").apply {
            parentFile?.mkdirs()
            appendText(result + "\n")
        }
        println(result)
        // Keep the collector states observable after all timed batches.
        assertTrue(probes.sumOf { it.session.snapshot().frames } > 0L)
    }

    private class Probe(val name: String, val session: DebugSession) {
        private var frame = 0L
        fun run(count: Int) {
            repeat(count) { index ->
                session.frame(++frame * 8_333_333L, 8_333_333L)
                session.phase(DiagnosticPhase.Dispatch, 1_200_000L)
                session.phase(DiagnosticPhase.Simulation, 900_000L)
                session.phase(DiagnosticPhase.Projection, 200_000L)
                session.phase(DiagnosticPhase.Audio, 100_000L)
                session.phase(DiagnosticPhase.CanvasDraw, 750_000L)
                session.workload(120, 80, 20, 12)
                if (index % 100 == 0) session.event("player.action", "Dash", highFrequency = true)
            }
        }
    }
}

private fun jsonString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""
