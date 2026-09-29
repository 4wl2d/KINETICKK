// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.min

/**
 * Reward overlay geometry in px. [cards] are the dealt choice cards (their resting, unrotated
 * rects), [reroll]/[take]/[build] the footer buttons and [titleY] the top of the level badge or
 * heading stamp. [subtitleY] is the lowest edge the heading area may use.
 */
internal class ChoiceLayoutGeometry(
    val mode: GameplayLayoutMode,
    val titleY: Float,
    val subtitleY: Float,
    val cards: List<Rect>,
    val reroll: Rect?,
    val compactCardContent: Boolean,
    val take: Rect = Rect.Zero,
    val build: Rect = Rect.Zero,
    val headerLeft: Float = 0f,
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
        // LevelUp board at 1440 x 810: 300 x 450 cards from y 170, footer at y 724.
        val footerHeight = d(38f)
        val footerTop = height - d(86f)
        val footer = footerRow(width * 0.5f, footerTop, footerHeight, d(16f), canReroll, d(180f), d(200f), d(140f))
        val margin = d(24f)
        val gap = d(if (count >= 4) 24f else 34f)
        val top = maxOf(d(150f), height * 0.21f)
        // Room under the cards for the lifted card's interaction strip.
        val cardHeight = min(d(450f), footerTop - d(64f) - top).coerceAtLeast(d(170f))
        val available = (width - margin * 2f - gap * (count - 1)) / count
        val cardWidth = min(d(300f), available).coerceAtLeast(d(92f))
        val total = cardWidth * count + gap * (count - 1)
        val startX = (width - total) * 0.5f
        return ChoiceLayoutGeometry(
            mode,
            titleY = d(42f),
            subtitleY = top - d(8f),
            cards = List(count) { index ->
                val left = startX + index * (cardWidth + gap)
                Rect(left, top, left + cardWidth, top + cardHeight)
            },
            reroll = footer.reroll,
            compactCardContent = false,
            take = footer.take,
            build = footer.build,
            headerLeft = d(56f),
        )
    }
    if (mode == GameplayLayoutMode.COMPACT_LANDSCAPE) {
        // Mobile-LevelUp board at 844 x 390: 220 x 256 cards from y 70, 44-48 dp footer.
        val margin = d(16f)
        val gap = d(10f)
        val footerHeight = d(48f)
        val footerTop = height - d(8f) - footerHeight
        val footer = footerRow(width * 0.5f, footerTop, footerHeight, d(10f), canReroll, d(150f), d(150f), d(110f))
        val top = d(70f)
        val cardHeight = min(d(256f), footerTop - d(10f) - top).coerceAtLeast(d(136f))
        val available = (width - margin * 2f - gap * (count - 1)) / count
        val cardWidth = min(d(220f), available)
        val total = cardWidth * count + gap * (count - 1)
        val startX = (width - total) * 0.5f
        return ChoiceLayoutGeometry(
            mode,
            titleY = d(14f),
            subtitleY = top - d(6f),
            cards = List(count) { index ->
                val left = startX + index * (cardWidth + gap)
                Rect(left, top, left + cardWidth, top + cardHeight)
            },
            reroll = footer.reroll,
            compactCardContent = true,
            take = footer.take,
            build = footer.build,
            headerLeft = d(44f),
        )
    }
    // Portrait: the same card language in two columns, footer in two rows (Take full width).
    val margin = d(12f)
    val gap = d(10f)
    val buttonHeight = d(48f)
    val take = Rect(margin, height - margin - buttonHeight, width - margin, height - margin)
    val secondTop = take.top - d(8f) - buttonHeight
    val halfWidth = (width - margin * 2f - gap) * 0.5f
    val reroll = if (canReroll) Rect(margin, secondTop, margin + halfWidth, secondTop + buttonHeight) else null
    val build = if (canReroll) {
        Rect(width - margin - halfWidth, secondTop, width - margin, secondTop + buttonHeight)
    } else {
        Rect((width - halfWidth) * 0.5f, secondTop, (width + halfWidth) * 0.5f, secondTop + buttonHeight)
    }
    val columns = min(2, count)
    val rows = ceil(count / columns.toDouble()).toInt()
    val top = d(96f)
    val bottom = secondTop - d(12f)
    val cardWidth = (width - margin * 2f - gap * (columns - 1)) / columns
    val cardHeight = min(d(390f), (bottom - top - gap * (rows - 1)) / rows)
    return ChoiceLayoutGeometry(
        mode,
        titleY = d(16f),
        subtitleY = top - d(6f),
        cards = List(count) { index ->
            val row = index / columns
            val column = index % columns
            val rowCount = min(columns, count - row * columns)
            val rowStart = (width - (cardWidth * rowCount + gap * (rowCount - 1))) * 0.5f
            val left = rowStart + column * (cardWidth + gap)
            val cardTop = top + row * (cardHeight + gap)
            Rect(left, cardTop, left + cardWidth, cardTop + cardHeight)
        },
        reroll = reroll,
        compactCardContent = false,
        take = take,
        build = build,
        headerLeft = d(16f),
    )
}

private class FooterRow(val reroll: Rect?, val take: Rect, val build: Rect)

/** Reroll (when offered), Take and Build centered on [centerX] in one row. */
private fun footerRow(
    centerX: Float,
    top: Float,
    height: Float,
    gap: Float,
    canReroll: Boolean,
    rerollWidth: Float,
    takeWidth: Float,
    buildWidth: Float,
): FooterRow {
    val total = (if (canReroll) rerollWidth + gap else 0f) + takeWidth + gap + buildWidth
    var x = centerX - total * 0.5f
    val reroll = if (canReroll) Rect(x, top, x + rerollWidth, top + height).also { x += rerollWidth + gap } else null
    val take = Rect(x, top, x + takeWidth, top + height)
    x += takeWidth + gap
    return FooterRow(reroll, take, Rect(x, top, x + buildWidth, top + height))
}
