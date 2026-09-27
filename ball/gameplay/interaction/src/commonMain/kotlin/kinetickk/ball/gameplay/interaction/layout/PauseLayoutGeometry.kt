// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.min

internal enum class PauseTarget {
    RESUME,
    SETTINGS,
    CODEX,
    PERFORMANCE,
    EXIT,
}

internal class PauseActionBounds(
    val target: PauseTarget,
    val bounds: Rect,
)

internal class PauseLayoutGeometry(
    val mode: GameplayLayoutMode,
    val titleY: Float,
    val actions: List<PauseActionBounds>,
)

internal fun pauseLayoutGeometry(width: Float, height: Float, scale: Float): PauseLayoutGeometry {
    val safeScale = scale.coerceAtLeast(1f)
    fun d(value: Float): Float = value * safeScale
    val mode = gameplayLayoutMode(width, height, safeScale)
    if (mode == GameplayLayoutMode.REGULAR) {
        val center = width * 0.5f
        return PauseLayoutGeometry(
            mode,
            titleY = height * 0.30f,
            actions = listOf(
                PauseActionBounds(PauseTarget.RESUME, Rect(center - d(150f), height * 0.44f, center + d(150f), height * 0.44f + d(52f))),
                PauseActionBounds(PauseTarget.SETTINGS, Rect(center - d(150f), height * 0.54f, center + d(150f), height * 0.54f + d(52f))),
                PauseActionBounds(PauseTarget.CODEX, Rect(center - d(150f), height * 0.64f, center + d(150f), height * 0.64f + d(52f))),
                PauseActionBounds(PauseTarget.EXIT, Rect(center - d(150f), height * 0.74f, center + d(150f), height * 0.74f + d(52f))),
            ),
        )
    }
    val buttonWidth = min(d(320f), width - d(24f))
    val buttonHeight = d(48f)
    val gap = d(8f)
    val targets = listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.CODEX, PauseTarget.PERFORMANCE, PauseTarget.EXIT)
    val totalHeight = buttonHeight * targets.size + gap * (targets.size - 1)
    val start = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) {
        d(72f)
    } else {
        maxOf(d(180f), height * 0.34f)
    }
    val left = (width - buttonWidth) * 0.5f
    return PauseLayoutGeometry(
        mode = mode,
        titleY = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) d(24f) else height * 0.18f,
        actions = targets.mapIndexed { index, target ->
            val top = min(start, height - d(12f) - totalHeight) + index * (buttonHeight + gap)
            PauseActionBounds(target, Rect(left, top, left + buttonWidth, top + buttonHeight))
        },
    )
}
