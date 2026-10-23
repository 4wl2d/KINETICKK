// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max
import kotlin.math.min

/** Running HUD controls. Append-only: the touch tracker and the press log index by ordinal. */
internal enum class RunningControlTarget {
    BRAKE,
    DASH,
    PERFORMANCE,
    PAUSE,
    SKILL_1,
    SKILL_2,
    SKILL_3,
    APEX,
}

/** The skill slot (0..2) of a SKILL target, −1 for every other target. */
internal val RunningControlTarget.skillSlot: Int
    get() = when (this) {
        RunningControlTarget.SKILL_1 -> 0
        RunningControlTarget.SKILL_2 -> 1
        RunningControlTarget.SKILL_3 -> 2
        else -> -1
    }

/**
 * Which optional running controls are placed. Controls that act on them (drawing, hit testing,
 * semantics) pass the live mask; layouts that only keep clear of them pass [ALL], so nothing else
 * moves when the first skill is drafted or the Apex unlocks.
 */
internal object RunningControlMask {
    const val NONE = 0

    /** The Z/X/C row: placed once any slot holds a skill (the first draft). */
    const val SKILLS = 1

    /** The level badge as the Apex control: placed once the Apex unlocks. */
    const val APEX = 2

    /**
     * The FPS counter in the PERFORMANCE slot (a tap toggles the performance panel): placed while Show
     * FPS is on, on desktop and landscape phones (portrait phones have no Performance slot).
     */
    const val FPS = 4
    const val ALL = SKILLS or APEX or FPS

    fun of(engine: GameplayRenderModel): Int {
        var mask = NONE
        val slots = engine.skillBar.slots
        for (index in 0 until slots.size) {
            if (slots[index].skill >= 0) {
                mask = mask or SKILLS
                break
            }
        }
        if (engine.apex.unlocked) mask = mask or APEX
        if (engine.settings.showFps) mask = mask or FPS
        return mask
    }
}

internal data class RunningControlBounds(
    val target: RunningControlTarget,
    val bounds: Rect,
)

/** Minimum touch/click target of every running control, in dp. */
internal const val RUNNING_CONTROL_MIN_DP = 48f

/** Width of the REGULAR Dash and Brake slabs, in reference dp. */
internal const val REGULAR_TOUCH_BUTTON_DP = 124f

/** Bottom inset of the REGULAR bottom clusters (core status, loadout), in reference dp. */
internal const val REGULAR_HUD_BOTTOM_DP = 30f

/**
 * Height of the REGULAR bottom-right loadout block: synergy bracket headroom (three stacked
 * levels), relic row, gap, weapon slot, gap, mastery pips.
 */
internal const val REGULAR_LOADOUT_HEIGHT_DP = 4f + 36f + 12f + 54f + 5f + 4f

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

/** Portrait rows below [PORTRAIT_HUD_TOP_DP], in dp: chips + chain, then the boss (elite/Architect) row. */
internal const val PORTRAIT_CHIP_ROW_DP = 61f
internal const val PORTRAIT_CHIP_ROW_HEIGHT_DP = 26f
internal const val PORTRAIT_BOSS_ROW_DP = PORTRAIT_CHIP_ROW_DP + PORTRAIT_CHIP_ROW_HEIGHT_DP + 10f
internal const val PORTRAIT_BOSS_ROW_HEIGHT_DP = 34f

/** First free portrait row below the boss row (trial panel, feed), in dp below the HUD top. */
internal const val PORTRAIT_PANEL_ROW_DP = PORTRAIT_BOSS_ROW_DP + PORTRAIT_BOSS_ROW_HEIGHT_DP + 10f

/** Side of a phone skill square and the REGULAR minimum, in dp. */
internal const val SKILL_SQUARE_DP = 48f

/** REGULAR skill square in reference dp (the Dash row's height). */
internal const val REGULAR_SKILL_SQUARE_DP = 52f

/** Gap between the REGULAR skill squares and between C and Brake, in reference dp. */
internal const val REGULAR_SKILL_GAP_DP = 8f
internal const val REGULAR_SKILL_BRAKE_GAP_DP = 12f

/**
 * REGULAR skill key caps (keyboard in use): a plate this tall, this far below the square, so the
 * caps reach [SKILL_KEY_CAP_REACH_DP] below the row (reference dp).
 */
internal const val SKILL_KEY_CAP_GAP_DP = 3f
internal const val SKILL_KEY_CAP_HEIGHT_DP = 12f
internal const val SKILL_KEY_CAP_REACH_DP = SKILL_KEY_CAP_GAP_DP + SKILL_KEY_CAP_HEIGHT_DP

/** Key caps show only on windows at least this wide (dp). */
internal const val KEY_CAP_MIN_WIDTH_DP = 600f

/** Phone gaps: between landscape Z and X, below Dash, and between the portrait squares, in dp. */
internal const val COMPACT_SKILL_GAP_DP = 6f
internal const val LANDSCAPE_SKILL_DASH_GAP_DP = 8f

/** Portrait Dash and Brake: their widest shares of the row and the room the skill squares need, in dp. */
private const val PORTRAIT_BRAKE_DP = 80f
private const val PORTRAIT_DASH_DP = 92f
private const val PORTRAIT_SKILL_ROW_DP = 3f * SKILL_SQUARE_DP + 2f * COMPACT_SKILL_GAP_DP + 2f * COMPACT_SKILL_GAP_DP

/**
 * Running controls in canvas px. REGULAR: pause top-right with the FPS counter one slot left of Build,
 * the skill squares, Brake and Dash as one row right-aligned above the bottom-right loadout. Landscape
 * phones: Dash and Brake at the right thumb, Z and X left of Dash, C below it; the FPS counter and pause
 * in the top-right row. Portrait phones: Brake, Z, X, C and Dash share the bottom row; pause top-right
 * (Build holds the Performance slot, so no counter is placed). The Apex control is the level badge.
 * [mask] ([RunningControlMask]) places the skill row, the Apex and the FPS counter (PERFORMANCE); every
 * target is at least [RUNNING_CONTROL_MIN_DP] and they never overlap.
 */
internal inline fun forEachRunningControlBounds(
    width: Float,
    height: Float,
    scale: Float,
    mask: Int,
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
    val skills = mask and RunningControlMask.SKILLS != 0
    val apex = mask and RunningControlMask.APEX != 0
    val fps = mask and RunningControlMask.FPS != 0
    when (gameplayLayoutMode(width, height, safeScale)) {
        GameplayLayoutMode.REGULAR -> {
            val unit = regularHudUnit(width, safeScale)
            val bottom = height - (REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP + 18f) * unit
            val top = bottom - max(52f * unit, minimum)
            // Equal slabs: each fits its icon and its label at the board size in either language.
            val dashLeft = width - margin - max(REGULAR_TOUCH_BUTTON_DP * unit, minimum)
            action(RunningControlTarget.DASH, dashLeft, top, width - margin, bottom)
            val brakeRight = dashLeft - 8f * unit
            val brakeLeft = brakeRight - max(REGULAR_TOUCH_BUTTON_DP * unit, minimum)
            action(RunningControlTarget.BRAKE, brakeLeft, top, brakeRight, bottom)
            if (skills) {
                val side = max(REGULAR_SKILL_SQUARE_DP * unit, minimum)
                val gap = REGULAR_SKILL_GAP_DP * unit
                val cRight = brakeLeft - REGULAR_SKILL_BRAKE_GAP_DP * unit
                val xRight = cRight - side - gap
                val zRight = xRight - side - gap
                action(RunningControlTarget.SKILL_1, zRight - side, top, zRight, bottom)
                action(RunningControlTarget.SKILL_2, xRight - side, top, xRight, bottom)
                action(RunningControlTarget.SKILL_3, cRight - side, top, cRight, bottom)
            }
            val pauseX = width - margin - 20f * unit
            val pauseY = 24f * unit + 18f * unit
            action(RunningControlTarget.PAUSE, pauseX - half, pauseY - half, pauseX + half, pauseY + half)
            if (fps) {
                // One header step left of Build ([runningBuildButtonBounds]), which sits one step left of pause.
                val fpsX = pauseX - 2f * regularHeaderStep(width, safeScale)
                action(RunningControlTarget.PERFORMANCE, fpsX - half, pauseY - half, fpsX + half, pauseY + half)
            }
            if (apex) {
                // The level badge with its Data bar: from 6 u left of the badge to 134 u, around its row.
                val left = margin - 6f * unit
                val halfHeight = max(33f * unit, half)
                action(RunningControlTarget.APEX, left, 51f * unit - halfHeight, left + max(134f * unit, minimum), 51f * unit + halfHeight)
            }
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val factor = compactHudFactor(width, safeScale, portrait = false)
            val boxTop = height - (18f + 150f) * safeScale
            val dashLeft = width - margin - max(104f * factor * safeScale, minimum)
            val dashBottom = boxTop + 84f * safeScale
            action(RunningControlTarget.DASH, dashLeft, boxTop, width - margin, dashBottom)
            val brakeLeft = width - margin - 190f * factor * safeScale
            action(
                RunningControlTarget.BRAKE,
                brakeLeft,
                height - (18f + 64f) * safeScale,
                brakeLeft + max(84f * factor * safeScale, minimum),
                height - 18f * safeScale,
            )
            if (skills) {
                // A 2 + 1 cluster around Dash for the right thumb: Z and X left of it, C in the free cell below.
                val side = SKILL_SQUARE_DP * safeScale
                val xRight = dashLeft - LANDSCAPE_SKILL_DASH_GAP_DP * safeScale
                val zRight = xRight - side - COMPACT_SKILL_GAP_DP * safeScale
                action(RunningControlTarget.SKILL_1, zRight - side, boxTop, zRight, boxTop + side)
                action(RunningControlTarget.SKILL_2, xRight - side, boxTop, xRight, boxTop + side)
                val cTop = dashBottom + COMPACT_SKILL_GAP_DP * safeScale
                action(RunningControlTarget.SKILL_3, width - margin - side, cTop, width - margin, cTop + side)
            }
            val pauseX = width - margin - 22f * safeScale
            val rowY = 30f * safeScale
            val performanceX = pauseX - 52f * safeScale
            if (fps) action(RunningControlTarget.PERFORMANCE, performanceX - half, rowY - half, performanceX + half, rowY + half)
            action(RunningControlTarget.PAUSE, pauseX - half, rowY - half, pauseX + half, rowY + half)
            if (apex) {
                // The badge row; the box ends where the trial panel and the minimap begin (48 dp).
                action(RunningControlTarget.APEX, margin - 6f * safeScale, 0f, margin + 84f * safeScale, minimum)
            }
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val factor = compactHudFactor(width, safeScale, portrait = true)
            val bottom = height - 36f * safeScale
            // Brake and Dash give up width to the skill squares between them, never below 48 dp.
            val rest = width - margin * 2f - PORTRAIT_SKILL_ROW_DP * safeScale
            val brakeWidth = max(minimum, min(PORTRAIT_BRAKE_DP * factor * safeScale, rest * PORTRAIT_BRAKE_DP / (PORTRAIT_BRAKE_DP + PORTRAIT_DASH_DP)))
            val dashWidth = max(minimum, min(PORTRAIT_DASH_DP * factor * safeScale, rest * PORTRAIT_DASH_DP / (PORTRAIT_BRAKE_DP + PORTRAIT_DASH_DP)))
            val brakeRight = margin + brakeWidth
            val dashLeft = width - margin - dashWidth
            action(RunningControlTarget.BRAKE, margin, bottom - 70f * safeScale, brakeRight, bottom)
            if (skills) {
                val side = SKILL_SQUARE_DP * safeScale
                val gap = COMPACT_SKILL_GAP_DP * safeScale
                var left = (brakeRight + dashLeft) * 0.5f - (side * 3f + gap * 2f) * 0.5f
                action(RunningControlTarget.SKILL_1, left, bottom - side, left + side, bottom)
                left += side + gap
                action(RunningControlTarget.SKILL_2, left, bottom - side, left + side, bottom)
                left += side + gap
                action(RunningControlTarget.SKILL_3, left, bottom - side, left + side, bottom)
            }
            action(RunningControlTarget.DASH, dashLeft, bottom - 80f * safeScale, width - margin, bottom)
            val pauseX = width - margin - 22f * safeScale
            val rowY = (PORTRAIT_HUD_TOP_DP + 11f + 20f) * safeScale
            action(RunningControlTarget.PAUSE, pauseX - half, rowY - half, pauseX + half, rowY + half)
            if (apex) {
                action(RunningControlTarget.APEX, margin - 6f * safeScale, rowY - half, margin + 84f * safeScale, rowY + half)
            }
        }
    }
}

/** The controls [mask] places, as a list (tests and one-off layout checks; never per frame). */
internal fun runningControlBounds(
    width: Float,
    height: Float,
    scale: Float,
    mask: Int = RunningControlMask.ALL,
): List<RunningControlBounds> {
    val controls = ArrayList<RunningControlBounds>(RunningControlTarget.entries.size)
    forEachRunningControlBounds(width, height, scale, mask) { target, left, top, right, bottom ->
        controls += RunningControlBounds(target, Rect(left, top, right, bottom))
    }
    return controls
}

/**
 * The running Build (Codex) button, a Compose control placed beside the canvas controls: left of
 * pause (REGULAR), left of performance (landscape phones), in the top row where performance sits on
 * the other layouts (portrait phones: their bottom row holds the skills).
 */
internal fun runningBuildButtonBounds(width: Float, height: Float, scale: Float): Rect {
    val safeScale = scale.coerceAtLeast(1f)
    val half = RUNNING_CONTROL_MIN_DP * safeScale * 0.5f
    var pause = Rect.Zero
    var performance = Rect.Zero
    forEachRunningControlBounds(width, height, safeScale, RunningControlMask.ALL) { target, left, top, right, bottom ->
        when (target) {
            RunningControlTarget.PAUSE -> pause = Rect(left, top, right, bottom)
            RunningControlTarget.PERFORMANCE -> performance = Rect(left, top, right, bottom)
            else -> Unit
        }
    }
    return when (gameplayLayoutMode(width, height, safeScale)) {
        GameplayLayoutMode.REGULAR -> {
            val x = pause.center.x - regularHeaderStep(width, safeScale)
            Rect(x - half, pause.center.y - half, x + half, pause.center.y + half)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val x = performance.center.x - 52f * safeScale
            Rect(x - half, performance.center.y - half, x + half, performance.center.y + half)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val x = pause.center.x - 52f * safeScale
            Rect(x - half, pause.center.y - half, x + half, pause.center.y + half)
        }
    }
}

/** REGULAR header step between the centres of pause, Build and the FPS counter, in px. */
internal fun regularHeaderStep(width: Float, scale: Float): Float {
    val safeScale = scale.coerceAtLeast(1f)
    return max(48f * regularHudUnit(width, safeScale), RUNNING_CONTROL_MIN_DP * safeScale)
}
