// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.min

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
        val cardHeight = min(d(405f), height - bottomReserve - top).coerceAtLeast(d(170f))
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
        val top = d(152f)
        val rerollHeight = d(48f)
        val bottom = if (canReroll) height - d(68f) else height - d(12f)
        val cardWidth = (width - margin * 2f - gap * (count - 1)) / count
        val cardHeight = (bottom - top).coerceAtLeast(d(136f))
        return ChoiceLayoutGeometry(
            mode,
            titleY = d(58f),
            subtitleY = d(94f),
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
    val top = maxOf(d(154f), height * 0.19f)
    val bottom = if (canReroll) height - d(76f) else height - d(12f)
    val cardWidth = (width - margin * 2f - gap * (columns - 1)) / columns
    val cardHeight = min(d(390f), (bottom - top - gap * (rows - 1)) / rows)
    return ChoiceLayoutGeometry(
        mode,
        titleY = d(62f),
        subtitleY = d(108f),
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
