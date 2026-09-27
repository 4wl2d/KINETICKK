// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect

internal enum class GameplayLayoutMode {
    REGULAR,
    COMPACT_PORTRAIT,
    COMPACT_LANDSCAPE,
}

internal fun gameplayLayoutMode(width: Float, height: Float, scale: Float): GameplayLayoutMode {
    val safeScale = scale.coerceAtLeast(1f)
    val logicalWidth = width / safeScale
    val logicalHeight = height / safeScale
    val compactPhone = logicalWidth <= 480f || (logicalHeight <= 480f && logicalWidth <= 1_000f)
    return when {
        !compactPhone -> GameplayLayoutMode.REGULAR
        logicalWidth <= logicalHeight -> GameplayLayoutMode.COMPACT_PORTRAIT
        else -> GameplayLayoutMode.COMPACT_LANDSCAPE
    }
}

internal fun containsInclusive(bounds: Rect, x: Float, y: Float): Boolean =
    x in bounds.left..bounds.right && y in bounds.top..bounds.bottom
