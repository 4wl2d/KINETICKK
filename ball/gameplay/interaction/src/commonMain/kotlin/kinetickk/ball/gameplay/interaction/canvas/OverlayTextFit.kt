// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.text.TextLayoutResult
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/**
 * Widths (px) of a row of boxes sharing [available] px, and one factor for all their text. A box
 * needs its content ([contents], px at full size) plus [pads]. With room to spare, the boxes with
 * a weight share the rest by [weights] and never get less than they need (weight 0 keeps the
 * need). Without room, every content shrinks by the same factor.
 */
internal fun overlayRowWidths(contents: FloatArray, pads: FloatArray, weights: FloatArray, available: Float): Pair<FloatArray, Float> {
    val count = contents.size
    val widths = FloatArray(count)
    val need = FloatArray(count) { contents[it] + pads[it] }
    if (need.sum() > available) {
        val factor = ((available - pads.sum()) / contents.sum()).coerceIn(0f, 1f)
        for (index in 0 until count) widths[index] = contents[index] * factor + pads[index]
        return widths to factor
    }
    val fixed = BooleanArray(count) { weights[it] <= 0f }
    while (true) {
        var rest = available
        var weight = 0f
        for (index in 0 until count) if (fixed[index]) rest -= need[index] else weight += weights[index]
        if (weight <= 0f) {
            for (index in 0 until count) widths[index] = need[index]
            return widths to 1f
        }
        val share = rest / weight
        var changed = false
        for (index in 0 until count) if (!fixed[index] && weights[index] * share < need[index]) {
            fixed[index] = true
            changed = true
        }
        if (!changed) {
            for (index in 0 until count) widths[index] = if (fixed[index]) need[index] else weights[index] * share
            return widths to 1f
        }
    }
}

/**
 * A pause text layout that fits [maxWidth] in [maxLines] without breaking inside a word: the
 * [role] font shrinks (to [minScale] of [size]) and, only if it still does not fit, further until
 * it does. Nothing is ever cut with an ellipsis. Memoized per text and box, so the paused frame
 * redraws without measuring.
 */
internal fun fitOverlayText(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int = 1,
    uppercase: Boolean = false,
    minScale: Float = 0.7f,
    tabular: Boolean = false,
): TextLayoutResult = PauseFitMemo.find(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular)
    ?: measureFitted(measurer, text, role, size, maxWidth, maxLines, uppercase, minScale, tabular)
        .also { PauseFitMemo.put(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular, it) }

private fun measureFitted(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int,
    uppercase: Boolean,
    minScale: Float,
    tabular: Boolean,
): TextLayoutResult {
    val typography = measurer.typography
    val words = (if (uppercase) text.uppercase() else text).split(' ').filter(String::isNotEmpty)
    fun fitted(scale: Float): TextLayoutResult? {
        val style = typography.kkStyle(role, size * scale, tabular)
        if (maxLines == 1) {
            val layout = measureKkText(measurer, text, style, uppercase)
            return layout.takeIf { it.size.width <= maxWidth }
        }
        val widest = words.maxOfOrNull { measureKkText(measurer, it, style).size.width } ?: 0
        if (widest > maxWidth) return null
        return measureKkText(measurer, text, style, uppercase, maxWidth, maxLines).takeIf { !it.hasVisualOverflow }
    }
    var scale = 1f
    while (scale >= minScale - 0.001f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    // Below the preferred floor the text keeps shrinking rather than being cut.
    scale = minScale - 0.05f
    while (scale > 0.2f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    return measureKkText(measurer, text, typography.kkStyle(role, size * 0.2f, tabular), uppercase, maxWidth, maxLines)
}

/** Recently fitted pause texts (draw-thread confined, round robin). */
private object PauseFitMemo {
    private const val CAPACITY = 128
    private val texts = arrayOfNulls<String>(CAPACITY)
    private val roles = arrayOfNulls<KkTextRole>(CAPACITY)
    private val delegates = arrayOfNulls<Any>(CAPACITY)
    private val floats = FloatArray(CAPACITY * 3)
    private val ints = IntArray(CAPACITY * 3)
    private val results = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private var next = 0

    fun find(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
    ): TextLayoutResult? {
        for (index in 0 until CAPACITY) {
            if (roles[index] !== role || delegates[index] !== measurer.delegate) continue
            val f = index * 3
            val i = index * 3
            if (floats[f] == size && floats[f + 1] == maxWidth && floats[f + 2] == measurer.scale &&
                ints[i] == maxLines && ints[i + 1] == (if (uppercase) 1 else 0) && ints[i + 2] == (if (tabular) 1 else 0) &&
                texts[index] == text
            ) return results[index]
        }
        return null
    }

    fun put(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
        result: TextLayoutResult,
    ) {
        val index = next
        next = (next + 1) % CAPACITY
        texts[index] = text
        roles[index] = role
        delegates[index] = measurer.delegate
        floats[index * 3] = size
        floats[index * 3 + 1] = maxWidth
        floats[index * 3 + 2] = measurer.scale
        ints[index * 3] = maxLines
        ints[index * 3 + 1] = if (uppercase) 1 else 0
        ints[index * 3 + 2] = if (tabular) 1 else 0
        results[index] = result
    }
}
