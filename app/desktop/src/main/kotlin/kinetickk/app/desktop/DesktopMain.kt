// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.desktop

import kinetickk.app.shared.enableKinetickkComposeRuntimeOptimizations
import kinetickk.app.shared.DesktopCrashHandler
import kinetickk.app.shared.runKinetickkDesktop
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

fun main(args: Array<String>) {
    val crashTest = args.firstOrNull { it.startsWith("--crash-test=") }?.substringAfter('=')
    val crashes = DesktopCrashHandler.install(
        showDialogs = "--no-crash-dialog" !in args,
        selfTest = crashTest != null,
    )
    try {
        require(crashTest == null || crashTest in setOf("main", "worker", "compose", "awt", "halt")) {
            "Unknown crash self-test: $crashTest"
        }
        crashes.verifyRuntime()
        if ("--verify-runtime" in args) {
            println("KINETICKK runtime verified")
            crashes.close()
            return
        }
        if (crashTest == "main") error("KINETICKK deliberate main crash")
        if (crashTest == "worker") Thread({ error("KINETICKK deliberate worker crash") }, "crash-test-worker").start()
        enableKinetickkComposeRuntimeOptimizations()
        // The owner lives above the window, so a window opened again for a display change keeps the run.
        runKinetickkDesktop(crashes) {
            LaunchedEffect(Unit) {
                if (crashTest == null) crashes.showPreviousCrash()
                if (crashTest == "compose") {
                    delay(500)
                    error("KINETICKK deliberate Compose coroutine crash")
                }
                if (crashTest == "awt") java.awt.EventQueue.invokeLater {
                    error("KINETICKK deliberate AWT crash")
                }
                if (crashTest == "halt") {
                    delay(1500)
                    Runtime.getRuntime().halt(137)
                }
                if ("--smoke-test" in args) { delay(1500); exitApplication() }
            }
        }
        crashes.close()
    } catch (error: Throwable) {
        crashes.fatal(error)
    }
}
