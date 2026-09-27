// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.max

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

/** Minimum touch/click target of every running control, in dp. */
internal const val RUNNING_CONTROL_MIN_DP = 48f

/** Bottom inset of the REGULAR bottom clusters (core status, loadout), in reference dp. */
internal const val REGULAR_HUD_BOTTOM_DP = 30f

/** Height of the REGULAR bottom-right loadout block: relic row, gap, weapon slot, gap, mastery pips. */
internal const val REGULAR_LOADOUT_HEIGHT_DP = 36f + 12f + 54f + 5f + 4f

/**
 * REGULAR HUD unit in px per reference dp: the 1440×810 board layout at full size on ordinary
 * desktop windows, shrunk (down to 72 %) on narrow ones so the clusters never meet.
 */
internal fun regularHudUnit(width: Float, scale: Float): Float {
    val safeScale = scale.coerceAtLeast(1f)
    return safeScale * (width / safeScale / 1_180f).coerceIn(0.72f, 1f)
}

/** Horizontal factor of the phone layouts relative to the 844 × 390 and 390 × 844 boards. */
internal fun compactHudFactor(width: Float, scale: Float, portrait: Boolean): Float {
    val logicalWidth = width / scale.coerceAtLeast(1f)
    return if (portrait) ((logicalWidth - 32f) / 358f).coerceIn(0.8f, 1f) else (logicalWidth / 844f).coerceIn(0.75f, 1f)
}

/** Side margin of the running HUD in px (board safe margins on phones). */
internal fun runningHudMargin(width: Float, height: Float, scale: Float): Float {
    val safeScale = scale.coerceAtLeast(1f)
    return when (gameplayLayoutMode(width, height, safeScale)) {
        GameplayLayoutMode.REGULAR -> 32f * regularHudUnit(width, safeScale)
        GameplayLayoutMode.COMPACT_LANDSCAPE -> 44f * safeScale * compactHudFactor(width, safeScale, portrait = false)
        GameplayLayoutMode.COMPACT_PORTRAIT -> 16f * safeScale
    }
}

/** Top inset of the phone portrait HUD (status bar area of the Mobile-Portrait board). */
internal const val PORTRAIT_HUD_TOP_DP = 47f

/**
 * Running controls in canvas px. REGULAR: pause top-right, Dash and Brake as one row right-aligned
 * above the bottom-right loadout. Phones follow the Mobile boards: Dash and Brake at the thumb side,
 * performance and pause in the top-right row. Every target is at least [RUNNING_CONTROL_MIN_DP].
 */
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
    val minimum = RUNNING_CONTROL_MIN_DP * safeScale
    val half = minimum * 0.5f
    val margin = runningHudMargin(width, height, safeScale)
    when (gameplayLayoutMode(width, height, safeScale)) {
        GameplayLayoutMode.REGULAR -> {
            val unit = regularHudUnit(width, safeScale)
            val bottom = height - (REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP + 18f) * unit
            val top = bottom - max(52f * unit, minimum)
            val dashLeft = width - margin - max(124f * unit, minimum)
            action(RunningControlTarget.DASH, dashLeft, top, width - margin, bottom)
            val brakeRight = dashLeft - 8f * unit
            action(RunningControlTarget.BRAKE, brakeRight - max(108f * unit, minimum), top, brakeRight, bottom)
            val pauseX = width - margin - 20f * unit
            val pauseY = 24f * unit + 18f * unit
            action(RunningControlTarget.PAUSE, pauseX - half, pauseY - half, pauseX + half, pauseY + half)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val factor = compactHudFactor(width, safeScale, portrait = false)
            val boxTop = height - (18f + 150f) * safeScale
            action(
                RunningControlTarget.DASH,
                width - margin - max(104f * factor * safeScale, minimum),
                boxTop,
                width - margin,
                boxTop + 84f * safeScale,
            )
            val brakeLeft = width - margin - 190f * factor * safeScale
            action(
                RunningControlTarget.BRAKE,
                brakeLeft,
                height - (18f + 64f) * safeScale,
                brakeLeft + max(84f * factor * safeScale, minimum),
                height - 18f * safeScale,
            )
            val pauseX = width - margin - 22f * safeScale
            val rowY = 30f * safeScale
            val performanceX = pauseX - 52f * safeScale
            action(RunningControlTarget.PERFORMANCE, performanceX - half, rowY - half, performanceX + half, rowY + half)
            action(RunningControlTarget.PAUSE, pauseX - half, rowY - half, pauseX + half, rowY + half)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val factor = compactHudFactor(width, safeScale, portrait = true)
            val bottom = height - 36f * safeScale
            action(
                RunningControlTarget.BRAKE,
                margin,
                bottom - 70f * safeScale,
                margin + max(104f * factor * safeScale, minimum),
                bottom,
            )
            action(
                RunningControlTarget.DASH,
                width - margin - max(120f * factor * safeScale, minimum),
                bottom - 80f * safeScale,
                width - margin,
                bottom,
            )
            val pauseX = width - margin - 22f * safeScale
            val rowY = (PORTRAIT_HUD_TOP_DP + 11f + 20f) * safeScale
            val performanceX = pauseX - 52f * safeScale
            action(RunningControlTarget.PERFORMANCE, performanceX - half, rowY - half, performanceX + half, rowY + half)
            action(RunningControlTarget.PAUSE, pauseX - half, rowY - half, pauseX + half, rowY + half)
        }
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

/**
 * The running Build (Codex) button, a Compose control placed beside the canvas controls: left of
 * pause (REGULAR), left of performance (landscape phones), between Brake and Dash (portrait).
 */
internal fun runningBuildButtonBounds(width: Float, height: Float, scale: Float): Rect {
    val safeScale = scale.coerceAtLeast(1f)
    val half = RUNNING_CONTROL_MIN_DP * safeScale * 0.5f
    var pause = Rect.Zero
    var performance = Rect.Zero
    var dash = Rect.Zero
    forEachRunningControlBounds(width, height, safeScale) { target, left, top, right, bottom ->
        when (target) {
            RunningControlTarget.PAUSE -> pause = Rect(left, top, right, bottom)
            RunningControlTarget.PERFORMANCE -> performance = Rect(left, top, right, bottom)
            RunningControlTarget.DASH -> dash = Rect(left, top, right, bottom)
            RunningControlTarget.BRAKE -> Unit
        }
    }
    return when (gameplayLayoutMode(width, height, safeScale)) {
        GameplayLayoutMode.REGULAR -> {
            val x = pause.center.x - max(48f * regularHudUnit(width, safeScale), half * 2f)
            Rect(x - half, pause.center.y - half, x + half, pause.center.y + half)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val x = performance.center.x - 52f * safeScale
            Rect(x - half, performance.center.y - half, x + half, performance.center.y + half)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val x = width * 0.5f
            Rect(x - half, dash.center.y - half, x + half, dash.center.y + half)
        }
    }
}
