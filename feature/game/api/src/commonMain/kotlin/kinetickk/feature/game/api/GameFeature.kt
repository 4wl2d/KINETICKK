// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.api

import androidx.compose.runtime.Composable

/** Consumer-facing entry point for the game feature. */
interface GameFeature {
    @Composable
    fun Content()
}
