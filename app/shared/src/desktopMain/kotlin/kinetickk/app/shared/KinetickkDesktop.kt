// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Dimension

/**
 * The desktop application. The composition owner (Profile, Gameplay, Session and audio) lives above
 * the window, so a window opened again for a decoration or V-Sync change keeps the session and the
 * run; only the window's own composition is rebuilt. [hostEffects] compose once, outside the window.
 */
fun runKinetickkDesktop(
    crashes: DesktopCrashHandler,
    hostEffects: @Composable ApplicationScope.() -> Unit = {},
) {
    val host = DesktopDisplayHost()
    // Profile loads synchronously here, so the stored display preferences shape the first window.
    val owner = AppCompositionOwner(diagnostics = crashes.reporter, displayHost = host, quitAvailable = true)
    try {
        val initial = owner.preferences()
        // Before the first window: Skiko reads the V-Sync setting when it creates a window's redrawer.
        val pacing = DesktopFramePacing.apply(preferenceVsync = initial.vsync)
        if (!pacing.vsync) println("KINETICKK uncapped frame rate (V-Sync off)")
        host.start(initial.toHostDisplayRequest(), pacing)
        application(exitProcessOnExit = false) {
            DesktopCrashBoundary(crashes) {
                key(host.generation) {
                    Window(
                        onCloseRequest = ::exitApplication,
                        state = host.windowState,
                        title = "KINETICKK",
                        undecorated = host.undecorated,
                        resizable = host.resizable,
                    ) {
                        window.minimumSize = Dimension(MINIMUM_WIDTH_DP, MINIMUM_HEIGHT_DP)
                        DisposableEffect(window) {
                            host.attach(window)
                            onDispose { host.detach(window) }
                        }
                        // Leaving fullscreen: the windowed preset follows once the window has left it.
                        LaunchedEffect(host.leavingFullscreen) {
                            while (host.leavingFullscreen) withFrameNanos { host.settle() }
                        }
                        // Home's Quit takes the window's close path: the owner closes after the application ends.
                        owner.Content(onQuit = ::exitApplication)
                    }
                }
                hostEffects()
            }
        }
    } finally {
        owner.close()
    }
}

private const val MINIMUM_WIDTH_DP = 720
private const val MINIMUM_HEIGHT_DP = 540
