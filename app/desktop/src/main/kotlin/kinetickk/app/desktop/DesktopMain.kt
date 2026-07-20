// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import kinetickk.feature.game.impl.DefaultGameFeature

fun main() = application {
    val gameFeature = DefaultGameFeature()
    Window(
        onCloseRequest = ::exitApplication,
        title = "KINETICKK",
        state = WindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(720, 540)
        gameFeature.Content()
    }
}
