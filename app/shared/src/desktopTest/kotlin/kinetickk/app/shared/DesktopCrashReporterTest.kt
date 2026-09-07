// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCrashReporterTest {
    @Test fun firstCrashRetainsCauseSuppressedThreadContextAndAgentEntryPoint() = withRoot { root ->
        val reporter = DesktopCrashReporter(root)
        val cause = ClassNotFoundException("kinetickk.ball.profile.api.ProfileSemanticHandle")
        val error = NoClassDefFoundError("kinetickk/ball/profile/api/ProfileSemanticHandle").apply {
            initCause(cause)
            addSuppressed(IllegalStateException("coroutine context"))
        }
        reporter.context("gameplay") { "speed=2.0 elapsed=55.0 seed=731991" }
        reporter.context("broken-formatter") { error("formatter failed") }
        reporter.context("profile.saved.json") { "{\"revision\":42}" }
        reporter.event("input", "DashRequested")
        val report = reporter.capture(error, "compose-window", Thread.currentThread())
        assertEquals(report, reporter.capture(IllegalArgumentException("secondary fault"), "uncaught-thread", Thread.currentThread()))
        assertEquals(report.toAbsolutePath().toString(), Files.readString(root.resolve("latest.txt")).trim())
        val stack = Files.readString(report.parent.resolve("exception.txt"))
        assertContains(stack, "Caused by: java.lang.ClassNotFoundException")
        assertContains(stack, "Suppressed: java.lang.IllegalStateException: coroutine context")
        assertContains(stack, "source=compose-window")
        assertFalse(stack.contains("secondary fault"))
        val context = Files.readString(report.parent.resolve("context.txt"))
        assertContains(context, "speed=2.0 elapsed=55.0 seed=731991")
        assertContains(context, "UNAVAILABLE:")
        assertEquals("{\"revision\":42}", Files.readString(report.parent.resolve("profile.saved.json")))
        assertContains(Files.readString(report.parent.resolve("events.txt")), "DashRequested")
        assertContains(Files.readString(report.parent.resolve("agent-prompt.txt")), report.toString())
        assertTrue(Files.size(report.parent.resolve("threads.txt")) > 0)
        error.addSuppressed(IllegalStateException("late coroutine context"))
        reporter.supplement(error)
        reporter.supplement(IllegalArgumentException("unrelated secondary error"))
        assertContains(Files.readString(report.parent.resolve("exception.txt")), "late coroutine context")
        assertFalse(Files.readString(report.parent.resolve("exception.txt")).contains("unrelated secondary error"))
        reporter.close()
    }

    @Test fun normalLaunchDoesNotReplaceLatestCrash() = withRoot { root ->
        val first = DesktopCrashReporter(root)
        val report = first.capture(IllegalStateException("first crash"), "main", Thread.currentThread())
        first.close()
        DesktopCrashReporter(root).close()
        assertEquals(report.toString(), Files.readString(root.resolve("latest.txt")).trim())
        assertContains(Files.readString(report), "first crash")
    }

    @Test fun interruptedLaunchProducesHonestReportWithLastFlushedEvidence() = withRoot { root ->
        val old = root.resolve("sessions/2000-interrupted")
        Files.createDirectories(old)
        Files.writeString(old.resolve("session.properties"), "pid=9223372036854775807\nstatus=open\n")
        Files.writeString(old.resolve("context.txt"), "speed=2.0 last checkpoint")
        Files.createDirectories(root.resolve("native"))
        Files.writeString(root.resolve("native/hs_err_pid9223372036854775807.log"), "fatal JVM diagnostic")
        val reporter = DesktopCrashReporter(root)
        assertEquals(old.resolve("report.md").toString(), Files.readString(root.resolve("latest.txt")).trim())
        assertContains(Files.readString(old.resolve("exception.txt")), "Cause is unknown")
        assertEquals("speed=2.0 last checkpoint", Files.readString(old.resolve("context.txt")))
        assertEquals("fatal JVM diagnostic", Files.readString(old.resolve("native-crash.log")))
        reporter.close()
    }

    @Test fun runningSecondInstanceIsNeverClassifiedAsCrashed() = withRoot { root ->
        val first = DesktopCrashReporter(root)
        val second = DesktopCrashReporter(root)
        assertFalse(Files.exists(root.resolve("latest.txt")))
        second.close()
        first.close()
    }

    @Test fun concurrentEventsAndOversizedLogsAreBoundedWithoutDroppingFirstFault() = withRoot { root ->
        val reporter = DesktopCrashReporter(root, PrintStream(ByteArrayOutputStream()))
        val workers = (1..4).map { worker -> thread {
            repeat(400) { reporter.event("worker", "$worker:$it") }
        } }
        workers.forEach { it.join() }
        repeat(1000) { reporter.event("frame", "$it", highFrequency = true) }
        reporter.context("long") { "x".repeat(MAX_CONTEXT_CHARS + 1) }
        reporter.log.write(ByteArray(MAX_LOG_BYTES * 3 + 17) { 'a'.code.toByte() })
        val report = reporter.capture(IllegalStateException("bounded"), "worker", Thread.currentThread())
        assertEquals(256, Files.readAllLines(report.parent.resolve("events.txt")).size)
        assertEquals(512, Files.readAllLines(report.parent.resolve("recent-inputs.txt")).size)
        assertTrue(Files.size(report.parent.resolve("console.log")) <= MAX_LOG_BYTES)
        assertTrue(Files.size(report.parent.resolve("console.previous.log")) <= MAX_LOG_BYTES)
        assertContains(Files.readString(report.parent.resolve("context.txt")), "[TRUNCATED at $MAX_CONTEXT_CHARS characters]")
        reporter.close()
    }

    @Test fun checkpointFailureDoesNotReplaceTheOriginalException() = withRoot { root ->
        val reporter = DesktopCrashReporter(root, PrintStream(ByteArrayOutputStream()))
        Files.createDirectory(reporter.session.resolve("context.txt"))
        val report = reporter.capture(IllegalStateException("original failure"), "main", Thread.currentThread())
        assertContains(Files.readString(report.parent.resolve("exception.txt")), "original failure")
        assertTrue(Files.isRegularFile(report))
        reporter.close()
    }

    @Test fun rotationKeepsTenReportsThreeCompletedSessionsAndLatestEntry() = withRoot { root ->
        repeat(MAX_CRASH_REPORTS + 2) { index ->
            val reporter = DesktopCrashReporter(root, PrintStream(ByteArrayOutputStream()))
            reporter.capture(IllegalStateException("crash $index"), "main", Thread.currentThread())
            reporter.close()
        }
        repeat(5) { DesktopCrashReporter(root).close() }
        Files.list(root.resolve("sessions")).use { sessions ->
            val directories = sessions.toList()
            assertEquals(MAX_CRASH_REPORTS, directories.count { Files.isRegularFile(it.resolve("report.md")) })
            assertEquals(3, directories.count { Files.readString(it.resolve("session.properties")).contains("status=closed") })
        }
        assertTrue(Files.isRegularFile(Path.of(Files.readString(root.resolve("latest.txt")).trim())))
    }

    private fun withRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("kinetickk-crash-test-")
        try { block(root) } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
