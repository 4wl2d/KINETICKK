// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.min

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
        val dashCenterX = width - 82f * safeScale
        val dashCenterY = height - 88f * safeScale
        val dashRadius = 48f * safeScale
        action(
            RunningControlTarget.DASH,
            dashCenterX - dashRadius,
            dashCenterY - dashRadius,
            dashCenterX + dashRadius,
            dashCenterY + dashRadius,
        )
        val brakeCenterX = width - 190f * safeScale
        val brakeCenterY = height - 67f * safeScale
        val brakeRadius = 38f * safeScale
        action(
            RunningControlTarget.BRAKE,
            brakeCenterX - brakeRadius,
            brakeCenterY - brakeRadius,
            brakeCenterX + brakeRadius,
            brakeCenterY + brakeRadius,
        )
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

internal enum class PauseTarget {
    RESUME,
    SETTINGS,
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
                PauseActionBounds(PauseTarget.RESUME, Rect(center - d(150f), height * 0.5f, center + d(150f), height * 0.5f + d(52f))),
                PauseActionBounds(PauseTarget.SETTINGS, Rect(center - d(150f), height * 0.62f, center + d(150f), height * 0.62f + d(52f))),
                PauseActionBounds(PauseTarget.EXIT, Rect(center - d(150f), height * 0.74f, center + d(150f), height * 0.74f + d(52f))),
            ),
        )
    }
    val buttonWidth = min(d(320f), width - d(24f))
    val buttonHeight = d(48f)
    val gap = d(8f)
    val totalHeight = buttonHeight * 4f + gap * 3f
    val start = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) {
        d(72f)
    } else {
        maxOf(d(180f), height * 0.34f)
    }
    val left = (width - buttonWidth) * 0.5f
    val targets = listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.PERFORMANCE, PauseTarget.EXIT)
    return PauseLayoutGeometry(
        mode = mode,
        titleY = if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) d(24f) else height * 0.18f,
        actions = targets.mapIndexed { index, target ->
            val top = min(start, height - d(12f) - totalHeight) + index * (buttonHeight + gap)
            PauseActionBounds(target, Rect(left, top, left + buttonWidth, top + buttonHeight))
        },
    )
}

internal class ChoiceLayoutGeometry(
    val mode: GameplayLayoutMode,
    val titleY: Float,
    val subtitleY: Float,
    val cards: List<Rect>,
    val reroll: Rect?,
    val compactCardContent: Boolean,
)

internal fun choiceLayoutGeometry(
    width: Float,
    height: Float,
    scale: Float,
    choiceCount: Int,
    canReroll: Boolean,
): ChoiceLayoutGeometry {
    val safeScale = scale.coerceAtLeast(1f)
    fun d(value: Float): Float = value * safeScale
    val count = choiceCount.coerceAtLeast(1)
    val mode = gameplayLayoutMode(width, height, safeScale)
    if (mode == GameplayLayoutMode.REGULAR) {
        val gap = d(if (count >= 4) 10f else 18f)
        val maxCardWidth = d(when {
            count >= 4 -> 190f
            count == 3 -> 250f
            else -> 300f
        })
        val availableCardWidth = (width - d(30f) - gap * (count - 1)) / count
        val cardWidth = min(maxCardWidth, availableCardWidth).coerceAtLeast(d(92f))
        val total = cardWidth * count + gap * (count - 1)
        val startX = (width - total) * 0.5f
        val top = height * if (count >= 4) 0.29f else 0.31f
        val bottomReserve = d(if (canReroll) 105f else 35f)
        val cardHeight = min(d(270f), height - bottomReserve - top).coerceAtLeast(d(170f))
        val rerollY = height - d(72f)
        return ChoiceLayoutGeometry(
            mode,
            titleY = height * 0.14f,
            subtitleY = height * 0.17f + d(36f),
            cards = List(count) { index ->
                val left = startX + index * (cardWidth + gap)
                Rect(left, top, left + cardWidth, top + cardHeight)
            },
            reroll = if (canReroll) Rect(width * 0.5f - d(90f), rerollY - d(22f), width * 0.5f + d(90f), rerollY + d(22f)) else null,
            compactCardContent = false,
        )
    }
    if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) {
        val margin = d(12f)
        val gap = d(10f)
        val top = d(128f)
        val rerollHeight = d(48f)
        val bottom = if (canReroll) height - d(68f) else height - d(12f)
        val cardWidth = (width - margin * 2f - gap * (count - 1)) / count
        val cardHeight = (bottom - top).coerceAtLeast(d(136f))
        return ChoiceLayoutGeometry(
            mode,
            titleY = d(14f),
            subtitleY = d(45f),
            cards = List(count) { index ->
                val left = margin + index * (cardWidth + gap)
                Rect(left, top, left + cardWidth, top + cardHeight)
            },
            reroll = if (canReroll) Rect(width * 0.5f - d(100f), height - d(58f), width * 0.5f + d(100f), height - d(58f) + rerollHeight) else null,
            compactCardContent = true,
        )
    }
    val margin = d(12f)
    val gap = d(10f)
    val columns = min(2, count)
    val rows = ceil(count / columns.toDouble()).toInt()
    val top = maxOf(d(118f), height * 0.16f)
    val bottom = if (canReroll) height - d(76f) else height - d(12f)
    val cardWidth = (width - margin * 2f - gap * (columns - 1)) / columns
    val cardHeight = min(d(260f), (bottom - top - gap * (rows - 1)) / rows)
    return ChoiceLayoutGeometry(
        mode,
        titleY = d(38f),
        subtitleY = d(76f),
        cards = List(count) { index ->
            val row = index / columns
            val column = index % columns
            val rowCount = min(columns, count - row * columns)
            val rowStart = (width - (cardWidth * rowCount + gap * (rowCount - 1))) * 0.5f
            val left = rowStart + column * (cardWidth + gap)
            val cardTop = top + row * (cardHeight + gap)
            Rect(left, cardTop, left + cardWidth, cardTop + cardHeight)
        },
        reroll = if (canReroll) Rect(width * 0.5f - d(100f), height - d(60f), width * 0.5f + d(100f), height - d(12f)) else null,
        compactCardContent = false,
    )
}

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
            exit = Rect(0f, exitTop, width, height),
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

internal fun containsInclusive(bounds: Rect, x: Float, y: Float): Boolean =
    x in bounds.left..bounds.right && y in bounds.top..bounds.bottom
