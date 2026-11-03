// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kinetickk.app.shared.KinetickkApp
import kinetickk.app.shared.WebDisplayHost
import kinetickk.app.shared.enableKinetickkComposeRuntimeOptimizations
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    enableKinetickkComposeRuntimeOptimizations()
    val display = WebDisplayHost()
    ComposeViewport(document.body!!) {
        LaunchedEffect(Unit) {
            // Leave the HTML splash in place until Compose has rendered its own loading screen.
            withFrameNanos { }
            withFrameNanos { }
            window.dispatchEvent(Event("kinetickk:canvas-ready"))
        }
        KinetickkApp(
            onLanguageChanged = { languageCode ->
                document.documentElement?.setAttribute("lang", languageCode)
                // The HTML boot splash reads this before the game loads.
                runCatching { window.localStorage.setItem("kinetickk_language", languageCode) }
            },
            displayHost = display,
        )
    }
}
