// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kinetickk.feature.game.impl.DefaultGameFeature
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val gameFeature = DefaultGameFeature()
    ComposeViewport(document.body!!) {
        gameFeature.Content()
    }
}
