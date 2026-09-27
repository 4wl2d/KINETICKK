// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect

internal enum class RunningControlTarget {
    BRAKE,
    DASH,
    PERFORMANCE,
    PAUSE,
}

internal data class RunningControlBounds(
    val target: RunningControlTarget,
    val bounds: Rect,
)

internal inline fun forEachRunningControlBounds(
    width: Float,
    height: Float,
    scale: Float,
    action: (
        target: RunningControlTarget,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) -> Unit,
) {
    val safeScale = scale.coerceAtLeast(1f)
    if (gameplayLayoutMode(width, height, safeScale) == GameplayLayoutMode.REGULAR) {
        val bottom = height - 20f * safeScale
        val top = bottom - 64f * safeScale
        action(RunningControlTarget.DASH, width - 180f * safeScale, top, width - 20f * safeScale, bottom)
        action(RunningControlTarget.BRAKE, width - 352f * safeScale, top, width - 192f * safeScale, bottom)
    } else {
        val margin = 12f * safeScale
        val controlSize = 64f * safeScale
        val utilityWidth = 52f * safeScale
        val utilityHeight = 48f * safeScale
        val utilityTop = 10f * safeScale
        val pauseRight = width - 10f * safeScale
        action(
            RunningControlTarget.BRAKE,
            margin,
            height - margin - controlSize,
            margin + controlSize,
            height - margin,
        )
        action(
            RunningControlTarget.DASH,
            width - margin - controlSize,
            height - margin - controlSize,
            width - margin,
            height - margin,
        )
        action(
            RunningControlTarget.PERFORMANCE,
            pauseRight - utilityWidth * 2f - 8f * safeScale,
            utilityTop,
            pauseRight - utilityWidth - 8f * safeScale,
            utilityTop + utilityHeight,
        )
        action(
            RunningControlTarget.PAUSE,
            pauseRight - utilityWidth,
            utilityTop,
            pauseRight,
            utilityTop + utilityHeight,
        )
    }
}

internal fun runningControlBounds(
    width: Float,
    height: Float,
    scale: Float,
): List<RunningControlBounds> {
    val controls = ArrayList<RunningControlBounds>(4)
    forEachRunningControlBounds(width, height, scale) { target, left, top, right, bottom ->
        controls += RunningControlBounds(target, Rect(left, top, right, bottom))
    }
    return controls
}
