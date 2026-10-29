// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opt-in hot-path diagnostic benchmark. Uses an isolated temporary reporter, prebuilt input
 * descriptions and the real bounded event ring. Startup/teardown and disk checkpoints are outside
 * the event timer; checkpoint durations are reported separately. No player profile is opened.
 */
class DesktopCrashEventPerformanceBenchmarkTest {
    @Test
    fun measureHighFrequencyEventRecording() {
        if (System.getenv("KK_CRASH_EVENT_BENCH") != "1") return
        val warmups = System.getenv("KK_CRASH_EVENT_WARMUPS")?.toInt() ?: 8
        val samples = System.getenv("KK_CRASH_EVENT_SAMPLES")?.toInt() ?: 20
        val operations = 10_000
        require(warmups > 0 && samples > 0)
        val messages = List(operations) { "FrameElapsed(sequence=$it, realDeltaSeconds=0.001)" }
        val root = Files.createTempDirectory("kinetickk-crash-event-benchmark-")
        try {
            val reporter = DesktopCrashReporter(root, PrintStream(ByteArrayOutputStream()))
            try {
                fun recordBatch() {
                    for (message in messages) reporter.event("gameplay.input", message, highFrequency = true)
                }
                repeat(warmups) { recordBatch() }
                val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
                check(threads.isThreadAllocatedMemorySupported)
                if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
                val thread = Thread.currentThread().id
                val elapsed = LongArray(samples)
                val checkpoint = LongArray(samples)
                var allocated = 0L
                repeat(samples) { sample ->
                    val allocatedBefore = threads.getThreadAllocatedBytes(thread)
                    val started = System.nanoTime()
                    recordBatch()
                    elapsed[sample] = System.nanoTime() - started
                    allocated += threads.getThreadAllocatedBytes(thread) - allocatedBefore
                    val checkpointStarted = System.nanoTime()
                    reporter.checkpoint()
                    checkpoint[sample] = System.nanoTime() - checkpointStarted
                }
                val retained = Files.readAllLines(reporter.session.resolve("recent-inputs.txt"))
                assertEquals(messages.takeLast(512), retained.map { it.substringAfter(" [gameplay.input] ") })
                val label = (System.getenv("KK_CRASH_EVENT_LABEL") ?: "run")
                    .replace("\\", "\\\\").replace("\"", "\\\"")
                val nsPerEvent = elapsed.map { it.toDouble() / operations }
                val checkpointMs = checkpoint.map { it / 1_000_000.0 }
                val result = "{\"suite\":\"desktop-crash-event-v1\",\"label\":\"$label\"," +
                    "\"warmups\":$warmups,\"samples\":$samples,\"eventsPerSample\":$operations," +
                    "\"meanNsPerEvent\":${nsPerEvent.average()}," +
                    "\"allocatedBytesPerEvent\":${allocated.toDouble() / samples / operations}," +
                    "\"nsPerEvent\":[${nsPerEvent.joinToString()}]," +
                    "\"checkpointMs\":[${checkpointMs.joinToString()}]}"
                println(result)
                File(System.getenv("KK_CRASH_EVENT_OUT") ?: "build/crash-event-benchmark.jsonl").apply {
                    parentFile?.mkdirs()
                    appendText(result + "\n")
                }
            } finally {
                reporter.close()
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
