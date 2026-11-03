// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*

/**
 * Feed text layouts per slot, fitted once per change: the text wraps at word boundaries on its
 * plate and shrinks when a word would otherwise break or the lines would be cut, so it is never
 * ellipsized. Two memos per slot, keyed by (text, typography, measurer, scale, size, width, flags):
 * the size fraction the text needs alone ([need]) and the drawn layout under a size cap ([fit]).
 * One bank of slots per text step and column.
 */
internal object FeedTexts {
    private const val SLOTS = FEED_TEXT_SLOTS * 2 * SHRINK_LEVELS
    private val needKeys = FeedTextKeys(SLOTS)
    private val needSteps = IntArray(SLOTS)
    private val fitKeys = FeedTextKeys(SLOTS)
    private val layouts = arrayOfNulls<TextLayoutResult>(SLOTS)
    private val contentWidths = FloatArray(SLOTS)
    private val lastRights = FloatArray(SLOTS)
    private val lastCenters = FloatArray(SLOTS)
    private val wholes = BooleanArray(SLOTS)

    /** The size fraction [text] needs to show whole in [width] (the first fit step), or NaN when none does. */
    fun need(slot: Int, measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean): Float {
        val step = needStep(slot, measurer, text, mono, size, width, lines, uppercase)
        return if (step == NO_FIT) Float.NaN else FitFactors[step]
    }

    private fun needStep(slot: Int, measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean): Int {
        val flag = flags(mono, uppercase, lines)
        if (needKeys.matches(slot, measurer, text, size, width, flag, 1f)) return needSteps[slot]
        val step = fitStep(measurer, text, mono, size, width, lines, uppercase, cap = 1f)
        needKeys.store(slot, measurer, text, size, width, flag, 1f)
        needSteps[slot] = step
        return step
    }

    /**
     * [text] laid out at the first fit step whose size fraction is at most [cap]. When none shows it
     * whole, the smallest attempt is returned and [isWhole] is false ([FeedLines] does not show it).
     */
    fun fit(
        slot: Int,
        measurer: TextMeasurer,
        text: String,
        mono: Boolean,
        size: Float,
        width: Float,
        lines: Int,
        uppercase: Boolean,
        cap: Float = 1f,
    ): TextLayoutResult {
        val flag = flags(mono, uppercase, lines)
        val cached = layouts[slot]
        if (cached != null && fitKeys.matches(slot, measurer, text, size, width, flag, cap)) return cached
        // The step the text needs alone is the first that fits under any cap at or above its size.
        val need = needStep(slot, measurer, text, mono, size, width, lines, uppercase)
        val step = if (need != NO_FIT && FitFactors[need] <= cap + CAP_EPSILON) need else fitStep(measurer, text, mono, size, width, lines, uppercase, cap)
        val shown = if (step != NO_FIT) step else FitFactors.lastIndex
        val layout = measureFeedText(measurer, text, mono, size * FitFactors[shown], width, lines + FitExtraLines[shown], uppercase)
        fitKeys.store(slot, measurer, text, size, width, flag, cap)
        layouts[slot] = layout
        var content = 0f
        for (line in 0 until layout.lineCount) content = max(content, layout.getLineRight(line))
        contentWidths[slot] = min(content, layout.size.width.toFloat())
        val last = layout.lineCount - 1
        lastRights[slot] = min(layout.getLineRight(last), layout.size.width.toFloat())
        lastCenters[slot] = (layout.getLineTop(last) + layout.getLineBottom(last)) * 0.5f - layout.kkBoxTop
        wholes[slot] = step != NO_FIT && layout.fitsWithoutCuts()
        return layout
    }

    /** Width of the widest line of the slot's last fitted layout. */
    fun contentWidth(slot: Int): Float = contentWidths[slot]

    /** Right edge of the last line of the slot's last fitted layout. */
    fun lastLineRight(slot: Int): Float = lastRights[slot]

    /** Vertical center of that last line, below the layout's text box top. */
    fun lastLineCenter(slot: Int): Float = lastCenters[slot]

    /** Whether the slot's last fitted layout shows its whole text (see [fitsWithoutCuts]). */
    fun isWhole(slot: Int): Boolean = wholes[slot]

    private fun flags(mono: Boolean, uppercase: Boolean, lines: Int): Int = (if (mono) 1 else 0) or (if (uppercase) 2 else 0) or (lines shl 2)
}

/** Memo keys of one [FeedTexts] table (plain slots, no allocation per lookup). */
private class FeedTextKeys(slots: Int) {
    private val texts = arrayOfNulls<String>(slots)
    private val typographies = arrayOfNulls<Any>(slots)
    private val delegates = arrayOfNulls<Any>(slots)
    private val scales = FloatArray(slots)
    private val sizes = FloatArray(slots)
    private val widths = FloatArray(slots)
    private val caps = FloatArray(slots)
    private val flags = IntArray(slots)

    fun matches(slot: Int, measurer: TextMeasurer, text: String, size: Float, width: Float, flag: Int, cap: Float): Boolean =
        texts[slot] != null && typographies[slot] === measurer.typography && delegates[slot] === measurer.delegate &&
            scales[slot] == measurer.scale && sizes[slot] == size && widths[slot] == width && flags[slot] == flag && caps[slot] == cap &&
            texts[slot] == text

    fun store(slot: Int, measurer: TextMeasurer, text: String, size: Float, width: Float, flag: Int, cap: Float) {
        texts[slot] = text
        typographies[slot] = measurer.typography
        delegates[slot] = measurer.delegate
        scales[slot] = measurer.scale
        sizes[slot] = size
        widths[slot] = width
        caps[slot] = cap
        flags[slot] = flag
    }
}

/**
 * Fit steps in the order they are tried, as (size fraction, extra lines): long text first shrinks
 * a little on the requested line count, then wraps onto up to [EXTRA_LINES] more lines at the
 * largest size that fits.
 */
private val SameLinesSteps = floatArrayOf(1f, 0.92f, 0.85f)
private val MoreLinesSteps = floatArrayOf(1f, 0.92f, 0.85f, 0.78f, 0.72f, 0.66f, 0.6f)
private const val EXTRA_LINES = 2
private val FitFactors = FloatArray(SameLinesSteps.size + MoreLinesSteps.size * EXTRA_LINES) { index ->
    if (index < SameLinesSteps.size) SameLinesSteps[index] else MoreLinesSteps[(index - SameLinesSteps.size) / EXTRA_LINES]
}
private val FitExtraLines = IntArray(FitFactors.size) { index ->
    if (index < SameLinesSteps.size) 0 else 1 + (index - SameLinesSteps.size) % EXTRA_LINES
}
private const val NO_FIT = -1
private const val CAP_EPSILON = 0.0001f

/** The first fit step at or below [cap] that shows [text] whole, or [NO_FIT]. */
private fun fitStep(measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean, cap: Float): Int {
    for (step in FitFactors.indices) {
        if (FitFactors[step] > cap + CAP_EPSILON) continue
        val layout = measureFeedText(measurer, text, mono, size * FitFactors[step], width, lines + FitExtraLines[step], uppercase)
        if (layout.fitsWithoutCuts()) return step
    }
    return NO_FIT
}

private fun measureFeedText(measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean) =
    measureKkText(
        measurer, text,
        if (mono) measurer.typography.monoStyle(size) else measurer.typography.condStyle(size, lineHeightEm = 1f),
        uppercase, maxWidth = width, maxLines = lines,
    )

/**
 * Where the feed drew its plates and lines in the last frame (px, screen space), for layout tests:
 * plain slots rewritten every frame, no allocation. [begin] clears the frame's marks.
 */
internal object FeedProbe {
    private val lineLayouts = arrayOfNulls<TextLayoutResult>(FEED_TEXT_SLOTS)
    private val lineBoxes = FloatArray(FEED_TEXT_SLOTS * 4)
    private val linePlates = IntArray(FEED_TEXT_SLOTS)
    private val plateBoxes = FloatArray((1 + MAX_NOTICES) * 4)
    private val plateBanners = BooleanArray(1 + MAX_NOTICES)
    var lineCount = 0
        private set
    var plateCount = 0
        private set

    fun begin() {
        lineCount = 0
        plateCount = 0
    }

    fun plate(left: Float, top: Float, right: Float, bottom: Float, banner: Boolean) {
        if (plateCount >= plateBanners.size) return
        val base = plateCount * 4
        plateBoxes[base] = left
        plateBoxes[base + 1] = top
        plateBoxes[base + 2] = right
        plateBoxes[base + 3] = bottom
        plateBanners[plateCount] = banner
        plateCount++
    }

    fun line(layout: TextLayoutResult, left: Float, top: Float, right: Float, bottom: Float) {
        if (lineCount >= lineLayouts.size || plateCount == 0) return
        val base = lineCount * 4
        lineLayouts[lineCount] = layout
        lineBoxes[base] = left
        lineBoxes[base + 1] = top
        lineBoxes[base + 2] = right
        lineBoxes[base + 3] = bottom
        linePlates[lineCount] = plateCount - 1
        lineCount++
    }

    /** Test accessors. */
    fun lineLayout(index: Int): TextLayoutResult = lineLayouts[index]!!
    fun lineBox(index: Int): Rect = Rect(lineBoxes[index * 4], lineBoxes[index * 4 + 1], lineBoxes[index * 4 + 2], lineBoxes[index * 4 + 3])
    fun linePlate(index: Int): Int = linePlates[index]
    fun plateBox(index: Int): Rect = Rect(plateBoxes[index * 4], plateBoxes[index * 4 + 1], plateBoxes[index * 4 + 2], plateBoxes[index * 4 + 3])
    fun isBanner(index: Int): Boolean = plateBanners[index]
}
