// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import kinetickk.foundation.design.LocalCrashDiagnostics
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/** Install before creating Compose, AWT, profile storage, or audio workers. */
class DesktopCrashHandler private constructor(private val showDialogs: Boolean) {
    private val originalOut = System.out
    private val originalErr = System.err
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val fatalStarted = AtomicBoolean()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "kinetickk-crash-checkpoint").apply { isDaemon = true }
    }
    private val uncaughtHandler = Thread.UncaughtExceptionHandler { thread, error -> fatal(error, "uncaught-thread", thread) }
    internal lateinit var reporter: DesktopCrashReporter
        private set
    private val shutdownHook = Thread({
        // Only an explicit normal close marks the session clean. SIGTERM remains an interruption.
        runCatching { if (::reporter.isInitialized) reporter.checkpoint() }
    }, "kinetickk-crash-shutdown")

    fun verifyRuntime() {
        // Resolve the current command and persistence API classes before starting a run.
        criticalRuntimeClasses.forEach { javaClass.classLoader.loadClass(it) }
        reporter.event("runtime", "critical application classes resolved")
    }

    fun fatal(error: Throwable, source: String = "main", thread: Thread = Thread.currentThread(), propagating: Boolean = false) {
        if (!fatalStarted.compareAndSet(false, true)) {
            runCatching { if (::reporter.isInitialized) reporter.supplement(error) }
            return
        }
        // Preserve the original error even when disk, diagnostic formatting, or the UI also fails.
        error.printStackTrace(originalErr)
        val report = try {
            reporter.capture(error, source, thread)
        } catch (reportError: Throwable) {
            originalErr.println("KINETICKK primary crash report failed: $reportError")
            emergencyReport(error, reportError, source)
        }
        if (!showDialogs || GraphicsEnvironment.isHeadless()) {
            if (propagating) SwingUtilities.invokeLater { exitProcess(1) } else exitProcess(1)
            return
        }
        // A stuck EDT cannot leave a crashed simulation running indefinitely.
        val dialogOpened = AtomicBoolean()
        val terminator = Thread({
            Thread.sleep(30_000)
            if (!dialogOpened.get()) exitProcess(1)
        }, "kinetickk-crash-exit").apply { isDaemon = true }
        terminator.start()
        SwingUtilities.invokeLater {
            try {
                java.awt.Window.getWindows().forEach { it.isVisible = false }
                dialogOpened.set(true)
                showReportDialog(report, fatal = true)
            } finally { exitProcess(1) }
        }
    }

    fun showPreviousCrash() {
        if (!showDialogs || GraphicsEnvironment.isHeadless()) return
        val latestFile = reporter.root.resolve("latest.txt")
        val notifiedFile = reporter.root.resolve("last-notified.txt")
        runCatching {
            if (!Files.isRegularFile(latestFile)) return
            val latest = Files.readString(latestFile).trim()
            if (Files.isRegularFile(notifiedFile) && Files.readString(notifiedFile).trim() == latest) return
            SwingUtilities.invokeLater {
                showReportDialog(Path.of(latest), fatal = false)
                runCatching { atomicWrite(notifiedFile, latest) }
            }
        }.onFailure { originalErr.println("KINETICKK previous crash notification failed: $it") }
    }

    fun close() {
        scheduler.shutdown()
        runCatching { reporter.close() }.onFailure { originalErr.println("KINETICKK diagnostics close failed: $it") }
        System.setOut(originalOut)
        System.setErr(originalErr)
        if (Thread.getDefaultUncaughtExceptionHandler() === uncaughtHandler) Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }

    private fun showReportDialog(report: Path?, fatal: Boolean) {
        val message = if (report != null) {
            "${if (fatal) "Игра завершилась с ошибкой." else "Сохранён отчёт о предыдущем падении."}\n\n" +
                "Отчёт: $report\nСкопируй промпт и передай его агенту для исправления."
        } else "Игра завершилась с ошибкой. Записать файл не удалось; стек выведен в stderr."
        val options = if (report == null) arrayOf("Закрыть") else arrayOf(
            "Копировать промпт", "Открыть папку", if (fatal) "Закрыть игру" else "Продолжить",
        )
        while (true) {
            val pane = object : JOptionPane(message, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options.last()) {
                override fun getMaxCharactersPerLineCount(): Int = 88
            }
            val dialog = pane.createDialog(null, "KINETICKK — отчёт о краше")
            try { dialog.isVisible = true } finally { dialog.dispose() }
            val selected = options.indexOfFirst { it == pane.value }
            if (report == null || selected !in 0..1) return
            runCatching {
                if (selected == 0) Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(agentCrashPrompt(report)), null)
                else Desktop.getDesktop().open(report.parent.toFile())
            }.onFailure { originalErr.println("KINETICKK report action failed: $it") }
        }
    }

    private fun emergencyReport(error: Throwable, reportError: Throwable, source: String): Path? = try {
        val directory = Files.createTempDirectory("kinetickk-emergency-crash-")
        val report = directory.resolve("report.md")
        atomicWrite(report, "# KINETICKK emergency crash report\n\nsource=$source\n\n" +
            error.stackTraceToString() + "\n\nReport writer failure:\n" + reportError.stackTraceToString())
        atomicWrite(directory.resolve("agent-prompt.txt"), agentCrashPrompt(report))
        originalErr.println("KINETICKK emergency crash report: $report")
        report
    } catch (failure: Throwable) {
        originalErr.println("KINETICKK emergency report also failed: $failure")
        null
    }

    companion object {
        fun install(showDialogs: Boolean = true, selfTest: Boolean = false): DesktopCrashHandler {
            val handler = DesktopCrashHandler(showDialogs)
            Thread.setDefaultUncaughtExceptionHandler(handler.uncaughtHandler)
            val configured = Path.of(System.getProperty("kinetickk.crashDir", Path.of(System.getProperty("user.home"), ".kinetickk", "crashes").toString()))
            val root = if (selfTest) configured.resolve("self-tests") else configured
            handler.reporter = try { DesktopCrashReporter(root, handler.originalErr) } catch (error: Throwable) {
                handler.originalErr.println("KINETICKK crash directory unavailable ($root): $error")
                DesktopCrashReporter(Files.createTempDirectory("kinetickk-crashes-"), handler.originalErr)
            }
            System.setOut(tee(handler.originalOut, handler.reporter.log, handler.originalErr))
            System.setErr(tee(handler.originalErr, handler.reporter.log, handler.originalErr))
            Runtime.getRuntime().addShutdownHook(handler.shutdownHook)
            handler.scheduler.scheduleWithFixedDelay({
                runCatching { handler.reporter.checkpoint() }
                    .onFailure { handler.originalErr.println("KINETICKK crash checkpoint failed: $it") }
            }, 0, 1, TimeUnit.SECONDS)
            handler.originalOut.println("KINETICKK crash reports: ${handler.reporter.root.toAbsolutePath()}")
            return handler
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopCrashBoundary(handler: DesktopCrashHandler, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCrashDiagnostics provides handler.reporter,
        LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory {
            WindowExceptionHandler { error ->
                handler.fatal(error, "compose-window", propagating = true)
                throw error
            }
        },
        content = content,
    )
}

private fun tee(original: PrintStream, log: OutputStream, fallback: PrintStream): PrintStream = PrintStream(object : OutputStream() {
    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        original.write(bytes, offset, length)
        try { log.write(bytes, offset, length) } catch (error: Exception) { fallback.println("KINETICKK console capture failed: $error") }
    }
    override fun flush() { original.flush(); runCatching { log.flush() } }
}, true, Charsets.UTF_8)
