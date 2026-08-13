// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kinetickk.app.shared.KinetickkApp
import kinetickk.app.shared.enableKinetickkComposeRuntimeOptimizations
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    enableKinetickkComposeRuntimeOptimizations()
    ComposeViewport(document.body!!) {
        KinetickkApp()
    }
}
