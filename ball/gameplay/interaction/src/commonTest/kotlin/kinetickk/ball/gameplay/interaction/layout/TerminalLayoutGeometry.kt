// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.min

internal class TerminalLayoutGeometry(
    val mode: GameplayLayoutMode,
    val titleY: Float,
    val subtitleY: Float,
    val statsY: Float,
    val restart: Rect,
    val rebirth: Rect?,
    val exit: Rect,
)

internal fun terminalLayoutGeometry(width: Float, height: Float, scale: Float, victory: Boolean): TerminalLayoutGeometry {
    val safeScale = scale.coerceAtLeast(1f)
    fun d(value: Float): Float = value * safeScale
    val mode = gameplayLayoutMode(width, height, safeScale)
    if (mode == GameplayLayoutMode.REGULAR) {
        val center = width * 0.5f
        val buttonY = height * 0.72f
        val rebirth = if (victory) Rect(center - d(120f), buttonY + d(50f), center + d(120f), buttonY + d(90f)) else null
        val exitTop = buttonY + d(if (victory) 100f else 50f)
        return TerminalLayoutGeometry(
            mode,
            titleY = height * 0.25f,
            subtitleY = height * 0.36f,
            statsY = height * 0.47f,
            restart = Rect(center - d(155f), buttonY - d(38f), center + d(155f), buttonY + d(38f)),
            rebirth = rebirth,
            exit = Rect(center - d(120f), exitTop, center + d(120f), min(height - d(12f), exitTop + d(40f))),
        )
    }
    val margin = d(12f)
    val buttonWidth = min(d(320f), width - margin * 2f)
    val buttonHeight = d(48f)
    val gap = d(8f)
    val buttonCount = if (victory) 3 else 2
    val total = buttonHeight * buttonCount + gap * (buttonCount - 1)
    val start = height - margin - total
    val left = (width - buttonWidth) * 0.5f
    val restart = Rect(left, start, left + buttonWidth, start + buttonHeight)
    val rebirth = if (victory) Rect(left, start + buttonHeight + gap, left + buttonWidth, start + buttonHeight * 2f + gap) else null
    val exitTop = start + (buttonHeight + gap) * (buttonCount - 1)
    return TerminalLayoutGeometry(
        mode,
        titleY = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) d(28f) else height * 0.13f,
        subtitleY = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) d(66f) else height * 0.21f,
        statsY = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) d(102f) else height * 0.30f,
        restart = restart,
        rebirth = rebirth,
        exit = Rect(left, exitTop, left + buttonWidth, exitTop + buttonHeight),
    )
}
