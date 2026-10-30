// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkMutatorSigil
import kinetickk.foundation.design.KkMutatorSigilPaths
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kotlin.math.min

/**
 * The Rebirth strip (`HudBlock.REBIRTH`): the tier plate (the tier numeral on a plate in the tier's
 * color) followed by the sigil of every active mutator, newest last in the tier color. It shares the
 * level badge's row in the top band (right of the badge, or of the inline economy on landscape phones)
 * and never reaches the clock. Portrait phones leave no room beside the badge, so there it takes the
 * chip row, right of the chips and clear of the chain at the row's end. Either way it lies inside the
 * world keep-out's top region. Sigils shrink (and wrap into up to three rows) to fit; below a legible
 * size only the plate is drawn. Tier 0 has no tier plate. Cached paths and number text: a steady frame
 * allocates nothing.
 *
 * The slot's lead plates: a dimension visit's plate takes the whole slot while it lasts; otherwise a
 * Baby mode run leads with the pacifier plate ([drawEasyStripPlate]), at tier 0 too, and the tier
 * plate and sigils follow it on the same row.
 */
internal fun DrawScope.drawRebirthStrip(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    // A dimension visit's plate takes the strip's slot while it lasts.
    if (drawDimensionPlate(engine, hostMeasurer, frame)) return
    val tier = engine.rebirthLevel
    val easy = engine.easyMode
    if (tier <= 0 && !easy) return
    val centerY: Float
    val height: Float
    val stripLeft: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(34f), HudScratch.badgeBottom - HudScratch.badgeTop)
            stripLeft = HudScratch.badgeRight + frame.u(18f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(28f), HudScratch.badgeBottom - HudScratch.badgeTop)
            stripLeft = HudScratch.economyRight + frame.u(12f)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            centerY = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP) + height * 0.5f
            stripLeft = HudScratch.economyRight + frame.u(10f)
        }
    }
    val right = if (frame.mode == GameplayLayoutMode.COMPACT_PORTRAIT) {
        // The chain ends the chip row: keep its usual width free (so the sigils do not resize as it
        // comes and goes) and never reach a wider one.
        min(frame.width - frame.margin - frame.u(PORTRAIT_CHAIN_RESERVE_DP), HudScratch.chainLeft - frame.u(10f))
    } else {
        // Keep clear of the clock at the top center.
        val clock = HudDrawCache.peekLayout(HudText.TIMER)?.size?.width?.toFloat() ?: frame.u(120f)
        frame.width * 0.5f - clock * 0.5f - frame.u(14f)
    }
    val top = centerY - height * 0.5f
    var left = stripLeft
    var drawnRight = stripLeft
    if (easy) {
        val plate = min(height, right - left)
        if (plate >= frame.u(14f)) {
            drawEasyStripPlate(hostMeasurer.roles, left + plate * 0.5f, centerY, plate * 0.5f, frame)
            drawnRight = left + plate
            left += plate + frame.u(6f)
        }
    }
    val plate = min(height, right - left)
    if (tier > 0 && plate >= frame.u(14f)) {
        drawnRight = max(drawnRight, drawTierPlateAndSigils(engine, hostMeasurer, frame, tier, left, plate, height, centerY, right))
    }
    if (drawnRight > stripLeft) HudLayoutProbe.record(HudBlock.REBIRTH, stripLeft, top, drawnRight, top + height)
}

/**
 * The tier plate of [plate] px at [left] (the tier numeral on the tier's color) and the mutator sigils after it up to
 * [right]; returns the drawn right edge.
 */
private fun DrawScope.drawTierPlateAndSigils(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    tier: Int,
    left: Float,
    plate: Float,
    height: Float,
    centerY: Float,
    right: Float,
): Float {
    val mutators = engine.content.rebirth.profile(tier).mutators
    val color = KkRebirthTiers.color(tier)
    val plateRadius = plate * 0.5f
    val plateCenterX = left + plateRadius
    translate(plateCenterX, centerY) { drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.TIER, plateRadius), color) }
    val numeral = HudDrawCache.layout(HudText.REBIRTH_TIER, HudMeasurers.display(hostMeasurer), HudScratch.rebirthTier.of(tier.toLong()),
        hostMeasurer.typography.wideStyle(plate * 0.42f / frame.density, tabular = true, lineHeightEm = 1f))
    drawKkText(numeral, plateCenterX, centerY, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    var drawnRight = left + plate
    val count = mutators.size
    if (count > 0) {
        val gap = frame.u(2f)
        val areaLeft = left + plate + frame.u(6f)
        val areaWidth = right - areaLeft
        var bestSize = 0f
        var bestRows = 1
        for (rows in 1..MAX_SIGIL_ROWS) {
            val columns = (count + rows - 1) / rows
            val size = min(min(frame.u(MAX_SIGIL_DP), (height - (rows - 1) * gap) / rows), (areaWidth - (columns - 1) * gap) / columns)
            if (size > bestSize + 0.01f) {
                bestSize = size
                bestRows = rows
            }
        }
        if (bestSize >= frame.u(MIN_SIGIL_DP)) {
            val columns = (count + bestRows - 1) / bestRows
            val blockHeight = bestRows * bestSize + (bestRows - 1) * gap
            val blockTop = centerY - blockHeight * 0.5f
            val radius = bestSize * 0.5f
            for (index in 0 until count) {
                val row = index / columns
                val column = index % columns
                val x = areaLeft + column * (bestSize + gap) + radius
                val y = blockTop + row * (bestSize + gap) + radius
                val sigil = mutators[index].ordinal
                val tint = if (index == count - 1) color else Kk.Bone.copy(alpha = 0.78f)
                translate(x, y) { drawPath(KkMutatorSigilPaths.of(sigil, radius), tint) }
                drawnRight = max(drawnRight, x + radius)
            }
        }
    }
    return drawnRight
}

/** Chip-row width (reference dp) the portrait strip leaves for the chain at the row's end. */
private const val PORTRAIT_CHAIN_RESERVE_DP = 72f

/** Largest mutator sigil (reference dp) and the smallest still drawn. */
private const val MAX_SIGIL_DP = 14f
private const val MIN_SIGIL_DP = 6f
private const val MAX_SIGIL_ROWS = 3
