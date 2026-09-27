// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Type roles (SPEC section 4). Uppercase is applied by the caller or the draw helper with
 * `uppercase()` (Kotlin's locale-independent mapping keeps Russian correct); strings stay in
 * sentence case in the localization tables.
 */
enum class KkTextRole(val uppercase: Boolean) {
    /** Unbounded 900: uppercase, tracking 0, line height 0.9. */
    WIDE(true),

    /** Sofia Sans Extra Condensed 900 italic: uppercase, +0.5 % tracking, line height 0.86. */
    COND(true),

    /** Sofia Sans Extra Condensed 800: uppercase, +9 % tracking, 15 px, line height 1. */
    LABEL(true),

    /** Sofia Sans Semi Condensed 400/500: sentence case, 16 px, line height 1.4. */
    BODY(false),

    /** Martian Mono 500: uppercase, +5 % tracking, 11 px, line height 1.35. */
    MONO(true),
}

/**
 * CSS-like line boxes: the line height is exact and glyphs are centered in it (negative
 * half-leading for 0.86/0.9 line heights), so a text box can be centered on a slab.
 */
val KkLineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tabularFeature(tabular: Boolean): String? = if (tabular) "tnum" else null

private fun flags(italic: Boolean, tabular: Boolean): Int = (if (italic) 1 else 0) or (if (tabular) 2 else 0)

/**
 * Memo of role styles per [InterfaceTypography] instance so builders return the same [TextStyle]
 * for the same arguments (no per-frame allocation, stable identity for [measureKkText]'s memo).
 * Draw-thread confined, bounded, round-robin eviction.
 */
internal class KkStyleMemo {
    private val roles = IntArray(CAPACITY) { -1 }
    private val floats = FloatArray(CAPACITY * 3)
    private val ints = IntArray(CAPACITY * 2)
    private val colors = LongArray(CAPACITY)
    private val styles = arrayOfNulls<TextStyle>(CAPACITY)
    private var next = 0

    fun find(role: Int, size: Float, tracking: Float, lineHeight: Float, weight: Int, flags: Int, color: Color): TextStyle? {
        val colorKey = color.value.toLong()
        for (index in 0 until CAPACITY) {
            if (roles[index] != role) continue
            val f = index * 3
            val i = index * 2
            if (floats[f] == size && floats[f + 1] == tracking && floats[f + 2] == lineHeight &&
                ints[i] == weight && ints[i + 1] == flags && colors[index] == colorKey
            ) return styles[index]
        }
        return null
    }

    fun put(role: Int, size: Float, tracking: Float, lineHeight: Float, weight: Int, flags: Int, color: Color, style: TextStyle) {
        val index = next
        next = (next + 1) % CAPACITY
        roles[index] = role
        floats[index * 3] = size
        floats[index * 3 + 1] = tracking
        floats[index * 3 + 2] = lineHeight
        ints[index * 2] = weight
        ints[index * 2 + 1] = flags
        colors[index] = color.value.toLong()
        styles[index] = style
    }

    private companion object {
        const val CAPACITY = 64
    }
}

/** `.t-wide`: Unbounded 900 (700 via [weight]); tracking is never negative. */
fun InterfaceTypography.wideStyle(
    size: Float = 30f,
    weight: FontWeight = FontWeight.Black,
    tabular: Boolean = false,
    trackingEm: Float = 0f,
    lineHeightEm: Float = 0.9f,
    color: Color = Color.Unspecified,
): TextStyle {
    val tracking = trackingEm.coerceAtLeast(0f)
    val flags = flags(false, tabular)
    return styleMemo.find(0, size, tracking, lineHeightEm, weight.weight, flags, color) ?: TextStyle(
        color = color,
        fontFamily = wide,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = tracking.em,
        lineHeight = lineHeightEm.em,
        lineHeightStyle = KkLineHeightStyle,
        fontFeatureSettings = tabularFeature(tabular),
        localeList = localeList,
    ).also { styleMemo.put(0, size, tracking, lineHeightEm, weight.weight, flags, color, it) }
}

/** `.t-cond`: Sofia Sans Extra Condensed 900 italic (upright or 800 via parameters). */
fun InterfaceTypography.condStyle(
    size: Float = 26f,
    italic: Boolean = true,
    weight: FontWeight = FontWeight.Black,
    trackingEm: Float = 0.005f,
    tabular: Boolean = false,
    lineHeightEm: Float = 0.86f,
    color: Color = Color.Unspecified,
): TextStyle {
    val flags = flags(italic, tabular)
    return styleMemo.find(1, size, trackingEm, lineHeightEm, weight.weight, flags, color) ?: TextStyle(
        color = color,
        fontFamily = cond,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = size.sp,
        letterSpacing = trackingEm.em,
        lineHeight = lineHeightEm.em,
        lineHeightStyle = KkLineHeightStyle,
        fontFeatureSettings = tabularFeature(tabular),
        localeList = localeList,
    ).also { styleMemo.put(1, size, trackingEm, lineHeightEm, weight.weight, flags, color, it) }
}

/** `.t-label`: Sofia Sans Extra Condensed 800 upright, +9 % tracking, 15 px. */
fun InterfaceTypography.labelStyle(
    size: Float = 15f,
    trackingEm: Float = 0.09f,
    tabular: Boolean = false,
    lineHeightEm: Float = 1f,
    color: Color = Color.Unspecified,
): TextStyle {
    val flags = flags(false, tabular)
    return styleMemo.find(2, size, trackingEm, lineHeightEm, 800, flags, color) ?: TextStyle(
        color = color,
        fontFamily = label,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Normal,
        fontSize = size.sp,
        letterSpacing = trackingEm.em,
        lineHeight = lineHeightEm.em,
        lineHeightStyle = KkLineHeightStyle,
        fontFeatureSettings = tabularFeature(tabular),
        localeList = localeList,
    ).also { styleMemo.put(2, size, trackingEm, lineHeightEm, 800, flags, color, it) }
}

/** `.t-body`: Sofia Sans Semi Condensed 400 (500/700 via [weight]), 16 px, line height 1.4. */
fun InterfaceTypography.bodyStyle(
    size: Float = 16f,
    weight: FontWeight = FontWeight.Normal,
    lineHeightEm: Float = 1.4f,
    tabular: Boolean = false,
    color: Color = Color.Unspecified,
): TextStyle {
    val flags = flags(false, tabular)
    return styleMemo.find(3, size, 0f, lineHeightEm, weight.weight, flags, color) ?: TextStyle(
        color = color,
        fontFamily = body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeightEm.em,
        lineHeightStyle = KkLineHeightStyle,
        fontFeatureSettings = tabularFeature(tabular),
        localeList = localeList,
    ).also { styleMemo.put(3, size, 0f, lineHeightEm, weight.weight, flags, color, it) }
}

/** `.t-mono`: Martian Mono 500 (700 via [weight]), +5 % tracking, 11 px (14 px minimum on phones). */
fun InterfaceTypography.monoStyle(
    size: Float = 11f,
    weight: FontWeight = FontWeight.Medium,
    trackingEm: Float = 0.05f,
    lineHeightEm: Float = 1.35f,
    color: Color = Color.Unspecified,
): TextStyle = styleMemo.find(4, size, trackingEm, lineHeightEm, weight.weight, 2, color) ?: TextStyle(
    color = color,
    fontFamily = mono,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = trackingEm.em,
    lineHeight = lineHeightEm.em,
    lineHeightStyle = KkLineHeightStyle,
    fontFeatureSettings = "tnum",
    localeList = localeList,
).also { styleMemo.put(4, size, trackingEm, lineHeightEm, weight.weight, 2, color, it) }

/** Role dispatcher with each role's defaults; [size] NaN keeps the role's default size. */
fun InterfaceTypography.kkStyle(
    role: KkTextRole,
    size: Float = Float.NaN,
    tabular: Boolean = false,
    color: Color = Color.Unspecified,
): TextStyle = when (role) {
    KkTextRole.WIDE -> wideStyle(if (size.isNaN()) 30f else size, tabular = tabular, color = color)
    KkTextRole.COND -> condStyle(if (size.isNaN()) 26f else size, tabular = tabular, color = color)
    KkTextRole.LABEL -> labelStyle(if (size.isNaN()) 15f else size, tabular = tabular, color = color)
    KkTextRole.BODY -> bodyStyle(if (size.isNaN()) 16f else size, tabular = tabular, color = color)
    KkTextRole.MONO -> monoStyle(if (size.isNaN()) 11f else size, color = color)
}

/**
 * Top of the CSS line box of the first line (glyphs may overflow it with tight line heights).
 * Cached per layout: platform line metrics allocate, so they are read once per layout.
 */
val TextLayoutResult.kkBoxTop: Float get() = KkBoxMetrics.top(this)

/** Bottom of the CSS line box of the last line (cached per layout). */
val TextLayoutResult.kkBoxBottom: Float get() = KkBoxMetrics.bottom(this)

/** Identity-keyed cache of line-box metrics for recently drawn layouts (draw thread only). */
private object KkBoxMetrics {
    private const val CAPACITY = 128
    private val layouts = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private val tops = FloatArray(CAPACITY)
    private val bottoms = FloatArray(CAPACITY)
    private var next = 0

    fun top(layout: TextLayoutResult): Float = tops[index(layout)]

    fun bottom(layout: TextLayoutResult): Float = bottoms[index(layout)]

    private fun index(layout: TextLayoutResult): Int {
        for (index in 0 until CAPACITY) if (layouts[index] === layout) return index
        val index = next
        next = (next + 1) % CAPACITY
        layouts[index] = layout
        tops[index] = layout.getLineTop(0)
        bottoms[index] = layout.getLineBottom(layout.lineCount - 1)
        return index
    }
}

/** Height of the CSS line boxes (lines × line height), used for layout like CSS. */
val TextLayoutResult.kkBoxHeight: Float get() = kkBoxBottom - kkBoxTop

/**
 * Decimal string of [value] without allocating for small non-negative values (0..1023 are cached),
 * for per-frame numbers such as levels, counts and seconds.
 */
fun kkIntString(value: Int): String {
    if (value < 0 || value >= KkIntStrings.size) return value.toString()
    return KkIntStrings[value] ?: value.toString().also { KkIntStrings[value] = it }
}

private val KkIntStrings = arrayOfNulls<String>(1024)

/** Horizontal anchor of a text draw. */
enum class KkAlign { START, CENTER, END }

/** Vertical anchor of a text draw: line-box top, line-box center or first baseline. */
enum class KkVAlign { TOP, CENTER, BASELINE }

/**
 * Measures [text] with [style] scaled by the text-size setting (`measurer.scale`), optionally
 * uppercased and limited to [maxWidth] px with an ellipsis. Results are memoized (bounded) by
 * text, style identity and measurer, so with the memoized role builders ([condStyle] etc.) and an
 * unchanged string a repeated call per frame allocates nothing.
 */
fun measureKkText(
    measurer: CanvasTextMeasurer,
    text: String,
    style: TextStyle,
    uppercase: Boolean = false,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    maxLines: Int = 1,
): TextLayoutResult {
    KkMeasureMemo.find(measurer, text, style, uppercase, maxWidth, maxLines)?.let { return it }
    return measureUncached(measurer, text, style, uppercase, maxWidth, maxLines)
        .also { KkMeasureMemo.put(measurer, text, style, uppercase, maxWidth, maxLines, it) }
}

private fun measureUncached(
    measurer: CanvasTextMeasurer,
    text: String,
    style: TextStyle,
    uppercase: Boolean,
    maxWidth: Float,
    maxLines: Int,
): TextLayoutResult {
    val scaled = if (measurer.scale == 1f) style else style.copy(fontSize = style.fontSize * measurer.scale)
    val shown = if (uppercase) text.uppercase() else text
    return if (maxWidth.isFinite()) {
        measurer.delegate.measure(
            text = shown,
            style = scaled,
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
        )
    } else {
        measurer.delegate.measure(shown, scaled, softWrap = false, maxLines = maxLines)
    }
}

/**
 * Recently measured layouts keyed by (text, style identity, measurer delegate, text scale,
 * uppercase, width limit, line limit). With memoized role styles and unchanged strings, repeated
 * frames reuse layouts without measuring or allocating. Draw-thread confined, bounded.
 */
private object KkMeasureMemo {
    private const val CAPACITY = 128
    private val texts = arrayOfNulls<String>(CAPACITY)
    private val styles = arrayOfNulls<TextStyle>(CAPACITY)
    private val delegates = arrayOfNulls<Any>(CAPACITY)
    private val scales = FloatArray(CAPACITY)
    private val widths = FloatArray(CAPACITY)
    private val lines = IntArray(CAPACITY)
    private val upper = BooleanArray(CAPACITY)
    private val results = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private var next = 0

    fun find(measurer: CanvasTextMeasurer, text: String, style: TextStyle, uppercase: Boolean, maxWidth: Float, maxLines: Int): TextLayoutResult? {
        val delegate = measurer.delegate
        for (index in 0 until CAPACITY) {
            if (styles[index] !== style || delegates[index] !== delegate) continue
            if (scales[index] == measurer.scale && upper[index] == uppercase && lines[index] == maxLines &&
                widths[index] == maxWidth && texts[index] == text
            ) return results[index]
        }
        return null
    }

    fun put(measurer: CanvasTextMeasurer, text: String, style: TextStyle, uppercase: Boolean, maxWidth: Float, maxLines: Int, result: TextLayoutResult) {
        val index = next
        next = (next + 1) % CAPACITY
        texts[index] = text
        styles[index] = style
        delegates[index] = measurer.delegate
        scales[index] = measurer.scale
        widths[index] = maxWidth
        lines[index] = maxLines
        upper[index] = uppercase
        results[index] = result
    }
}

/**
 * Draws a measured [layout] anchored at ([x], [y]) per [align]/[valign] in [color] (or [brush],
 * e.g. the legendary foil). Allocation-free: this is the hot-path text call.
 */
fun DrawScope.drawKkText(
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    color: Color,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
    alpha: Float = 1f,
    brush: Brush? = null,
) {
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - layout.size.width * 0.5f
        KkAlign.END -> x - layout.size.width
    }
    val top = when (valign) {
        KkVAlign.TOP -> y - layout.kkBoxTop
        KkVAlign.CENTER -> y - (layout.kkBoxTop + layout.kkBoxBottom) * 0.5f
        KkVAlign.BASELINE -> y - layout.firstBaseline
    }
    if (brush != null) {
        drawText(layout, brush, Offset(left, top), alpha = alpha)
    } else {
        drawText(layout, color, Offset(left, top), alpha = alpha)
    }
}

/** Measures and draws [text] in one call (menus/overlays); returns the layout for hit rects. */
fun DrawScope.drawKkText(
    measurer: CanvasTextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    color: Color,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
    uppercase: Boolean = false,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    alpha: Float = 1f,
): TextLayoutResult {
    val layout = measureKkText(measurer, text, style, uppercase, maxWidth)
    drawKkText(layout, x, y, color, align, valign, alpha)
    return layout
}
