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

/**
 * Running totals of the text work behind [measureKkText] and the line-box metrics, for
 * performance probes and tests (draw thread only). Read differences: a steady frame whose strings
 * are all resident changes neither total.
 */
object KkTextStats {
    /** Layouts measured because no memoized layout matched (each one shapes text and allocates). */
    var layoutsMeasured: Long = 0L
        internal set

    /** Platform line-metric reads behind [kkBoxTop]/[kkBoxBottom] (each allocates on Skia). */
    var lineMetricReads: Long = 0L
        internal set
}

/**
 * Entries of each text cache. Sized well above the busiest frame's working set: up to 140 live
 * damage numbers (a face and a shadow layout each) plus the HUD, world labels, feed and an overlay.
 */
private const val KK_TEXT_CACHE_CAPACITY = 768

/** Hash buckets of each text cache (a power of two above twice the entries). */
private const val KK_TEXT_CACHE_BUCKETS = 2048

/** Spreads the high bits of [hash] over the bucket bits. */
private fun kkSpread(hash: Int): Int = hash xor (hash ushr 16)

/**
 * Slot bookkeeping of a bounded text cache: hash chains for lookup and an exact least-recently-used
 * order for eviction, in plain int arrays (allocation-free, draw thread only). A slot read in the
 * current frame is never evicted while the frame's working set stays below [capacity], so layouts
 * drawn every frame (HUD digits, every live damage number) stay resident however many new strings
 * pass through.
 */
private class KkLruSlots(private val capacity: Int) {
    private val heads = IntArray(KK_TEXT_CACHE_BUCKETS) { -1 }
    private val chain = IntArray(capacity) { -1 }
    private val hashes = IntArray(capacity)
    private val older = IntArray(capacity) { -1 }
    private val newer = IntArray(capacity) { -1 }
    private var newest = -1
    private var oldest = -1
    private var filled = 0

    /** First slot of [hash]'s chain, or -1; continue with [next]. */
    fun first(hash: Int): Int = heads[hash and (KK_TEXT_CACHE_BUCKETS - 1)]

    fun next(slot: Int): Int = chain[slot]

    fun hash(slot: Int): Int = hashes[slot]

    /** Marks [slot] as the most recently used. */
    fun touch(slot: Int) {
        if (slot == newest) return
        unlinkOrder(slot)
        linkNewest(slot)
    }

    /** A slot for a new entry with [hash]: a free one, else the least recently used (to overwrite). */
    fun claim(hash: Int): Int {
        val slot = if (filled < capacity) {
            filled++
        } else {
            val victim = oldest
            unlinkOrder(victim)
            unlinkChain(victim)
            victim
        }
        hashes[slot] = hash
        val bucket = hash and (KK_TEXT_CACHE_BUCKETS - 1)
        chain[slot] = heads[bucket]
        heads[bucket] = slot
        linkNewest(slot)
        return slot
    }

    private fun linkNewest(slot: Int) {
        older[slot] = newest
        newer[slot] = -1
        if (newest >= 0) newer[newest] = slot
        newest = slot
        if (oldest < 0) oldest = slot
    }

    private fun unlinkOrder(slot: Int) {
        val before = older[slot]
        val after = newer[slot]
        if (before >= 0) newer[before] = after else oldest = after
        if (after >= 0) older[after] = before else newest = before
    }

    private fun unlinkChain(slot: Int) {
        val bucket = hashes[slot] and (KK_TEXT_CACHE_BUCKETS - 1)
        var current = heads[bucket]
        if (current == slot) {
            heads[bucket] = chain[slot]
            return
        }
        while (current >= 0) {
            val following = chain[current]
            if (following == slot) {
                chain[current] = chain[slot]
                return
            }
            current = following
        }
    }
}

/**
 * Identity-keyed line-box metrics of recently drawn layouts, hashed by the layout's text and size
 * and evicted least recently used first, so layouts drawn every frame keep their metrics.
 */
private object KkBoxMetrics {
    private val slots = KkLruSlots(KK_TEXT_CACHE_CAPACITY)
    private val layouts = arrayOfNulls<TextLayoutResult>(KK_TEXT_CACHE_CAPACITY)
    private val tops = FloatArray(KK_TEXT_CACHE_CAPACITY)
    private val bottoms = FloatArray(KK_TEXT_CACHE_CAPACITY)

    fun top(layout: TextLayoutResult): Float = tops[slot(layout)]

    fun bottom(layout: TextLayoutResult): Float = bottoms[slot(layout)]

    private fun slot(layout: TextLayoutResult): Int {
        val size = layout.size
        val hash = kkSpread(31 * (31 * layout.layoutInput.text.text.hashCode() + size.width) + size.height)
        var slot = slots.first(hash)
        while (slot >= 0) {
            if (layouts[slot] === layout) {
                slots.touch(slot)
                return slot
            }
            slot = slots.next(slot)
        }
        KkTextStats.lineMetricReads++
        slot = slots.claim(hash)
        layouts[slot] = layout
        tops[slot] = layout.getLineTop(0)
        bottoms[slot] = layout.getLineBottom(layout.lineCount - 1)
        return slot
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

private val KkDigitStrings = Array(10) { it.toString() }

private fun kkDigitCount(value: Long): Int {
    var count = 1
    var rest = value / 10L
    while (rest > 0L) {
        count++
        rest /= 10L
    }
    return count
}

/**
 * Width of [value] as [drawKkTabularNumber] draws it (with optional constant [prefix]/[suffix]).
 * Allocation-free once each glyph has been measured.
 */
fun kkTabularNumberWidth(
    measurer: CanvasTextMeasurer,
    value: Long,
    style: TextStyle,
    prefix: String? = null,
    suffix: String? = null,
): Float {
    val digits = kkDigitCount(value.coerceAtLeast(0L))
    var width = measureKkText(measurer, KkDigitStrings[0], style).size.width.toFloat() * digits
    if (prefix != null) width += measureKkText(measurer, prefix, style).size.width
    if (suffix != null) width += measureKkText(measurer, suffix, style).size.width
    return width
}

/**
 * Draws a non-negative number that changes every frame (speed, drain percentages, distances)
 * from cached per-digit layouts, so a new value never measures text: allocation-free once each
 * digit, [prefix] and [suffix] have been measured. [style] should use tabular figures (every digit
 * advances by the width of "0"). Returns the drawn width.
 *
 * The drawn layouts are kept per [color], so numbers that share a style but are drawn in different
 * colors never repaint one paragraph in alternating colors. Pick [color] from a small fixed set
 * (role or state colors): a color animated per frame would measure new layouts every frame. A
 * changing [alpha] still repaints the paragraph every frame: keep text alpha steady and animate
 * plates and halos instead.
 */
fun DrawScope.drawKkTabularNumber(
    measurer: CanvasTextMeasurer,
    value: Long,
    style: TextStyle,
    x: Float,
    y: Float,
    color: Color,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
    prefix: String? = null,
    suffix: String? = null,
    alpha: Float = 1f,
): Float {
    val shown = value.coerceAtLeast(0L)
    val width = kkTabularNumberWidth(measurer, shown, style, prefix, suffix)
    var left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - width * 0.5f
        KkAlign.END -> x - width
    }
    if (prefix != null) {
        val layout = measureKkTextPainted(measurer, prefix, style, color)
        drawKkText(layout, left, y, color, KkAlign.START, valign, alpha)
        left += layout.size.width
    }
    val digitWidth = measureKkText(measurer, KkDigitStrings[0], style).size.width
    val digits = kkDigitCount(shown)
    var divisor = 1L
    repeat(digits - 1) { divisor *= 10L }
    var rest = shown
    repeat(digits) {
        val digit = (rest / divisor).toInt()
        rest %= divisor
        divisor /= 10L
        drawKkText(measureKkTextPainted(measurer, KkDigitStrings[digit], style, color), left, y, color, KkAlign.START, valign, alpha)
        left += digitWidth
    }
    if (suffix != null) drawKkText(measureKkTextPainted(measurer, suffix, style, color), left, y, color, KkAlign.START, valign, alpha)
    return width
}

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
): TextLayoutResult = measureMemoized(measurer, text, style, uppercase, maxWidth, maxLines, KK_ANY_PAINT)

/** Paint key of layouts that are not tied to one paint color ([measureKkText]). */
private val KK_ANY_PAINT = Color.Unspecified.value.toLong()

/**
 * [measureKkText] for a layout that is always painted in [paint]: each paint color gets its own
 * layout (and paragraph), so numbers that share a style but differ in color never repaint a shared
 * paragraph in alternating colors.
 */
private fun measureKkTextPainted(measurer: CanvasTextMeasurer, text: String, style: TextStyle, paint: Color): TextLayoutResult =
    measureMemoized(measurer, text, style, false, Float.POSITIVE_INFINITY, 1, paint.value.toLong())

private fun measureMemoized(
    measurer: CanvasTextMeasurer,
    text: String,
    style: TextStyle,
    uppercase: Boolean,
    maxWidth: Float,
    maxLines: Int,
    paint: Long,
): TextLayoutResult {
    val hash = KkMeasureMemo.hash(text, measurer.scale, uppercase, maxWidth, maxLines, paint)
    KkMeasureMemo.find(hash, measurer, text, style, uppercase, maxWidth, maxLines, paint)?.let { return it }
    return measureUncached(measurer, text, style, uppercase, maxWidth, maxLines)
        .also { KkMeasureMemo.put(hash, measurer, text, style, uppercase, maxWidth, maxLines, paint, it) }
}

private fun measureUncached(
    measurer: CanvasTextMeasurer,
    text: String,
    style: TextStyle,
    uppercase: Boolean,
    maxWidth: Float,
    maxLines: Int,
): TextLayoutResult {
    KkTextStats.layoutsMeasured++
    val scaled = if (measurer.scale == 1f) style else style.copy(fontSize = style.fontSize * measurer.scale)
    val shown = if (uppercase) text.uppercase() else text
    // The memo is the cache: Compose's own layout cache would hand back a paragraph shared with a
    // layout whose style differs only in color, and painting one paragraph in two colors re-lays
    // it out on every paint (Skia).
    return if (maxWidth.isFinite()) {
        measurer.delegate.measure(
            text = shown,
            style = scaled,
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
            skipCache = true,
        )
    } else {
        measurer.delegate.measure(shown, scaled, softWrap = false, maxLines = maxLines, skipCache = true)
    }
}

/**
 * Recently measured layouts keyed by (text, style identity, measurer delegate, text scale,
 * uppercase, width limit, line limit, paint key), hashed by the text and scalar keys. With memoized
 * role styles and unchanged strings, repeated frames reuse layouts without measuring or allocating.
 *
 * Evicts the least recently used layout. Every layout drawn in a frame is read in that frame (each
 * live damage number reads its face and shadow on every frame of its life), so a frame stays
 * resident while its working set is below [KK_TEXT_CACHE_CAPACITY]; new strings only displace
 * layouts that were not drawn recently. Draw-thread confined, bounded.
 */
private object KkMeasureMemo {
    private val slots = KkLruSlots(KK_TEXT_CACHE_CAPACITY)
    private val texts = arrayOfNulls<String>(KK_TEXT_CACHE_CAPACITY)
    private val styles = arrayOfNulls<TextStyle>(KK_TEXT_CACHE_CAPACITY)
    private val delegates = arrayOfNulls<Any>(KK_TEXT_CACHE_CAPACITY)
    private val scales = FloatArray(KK_TEXT_CACHE_CAPACITY)
    private val widths = FloatArray(KK_TEXT_CACHE_CAPACITY)
    private val lines = IntArray(KK_TEXT_CACHE_CAPACITY)
    private val upper = BooleanArray(KK_TEXT_CACHE_CAPACITY)
    private val paints = LongArray(KK_TEXT_CACHE_CAPACITY)
    private val results = arrayOfNulls<TextLayoutResult>(KK_TEXT_CACHE_CAPACITY)

    fun hash(text: String, scale: Float, uppercase: Boolean, maxWidth: Float, maxLines: Int, paint: Long): Int {
        var hash = text.hashCode()
        hash = 31 * hash + scale.toRawBits()
        hash = 31 * hash + maxWidth.toRawBits()
        hash = 31 * hash + maxLines
        hash = 31 * hash + (paint xor (paint ushr 32)).toInt()
        hash = 31 * hash + if (uppercase) 1 else 0
        return kkSpread(hash)
    }

    fun find(
        hash: Int,
        measurer: CanvasTextMeasurer,
        text: String,
        style: TextStyle,
        uppercase: Boolean,
        maxWidth: Float,
        maxLines: Int,
        paint: Long,
    ): TextLayoutResult? {
        val delegate = measurer.delegate
        var slot = slots.first(hash)
        while (slot >= 0) {
            if (slots.hash(slot) == hash && styles[slot] === style && delegates[slot] === delegate &&
                scales[slot] == measurer.scale && upper[slot] == uppercase && lines[slot] == maxLines &&
                widths[slot] == maxWidth && paints[slot] == paint && texts[slot] == text
            ) {
                slots.touch(slot)
                return results[slot]
            }
            slot = slots.next(slot)
        }
        return null
    }

    fun put(
        hash: Int,
        measurer: CanvasTextMeasurer,
        text: String,
        style: TextStyle,
        uppercase: Boolean,
        maxWidth: Float,
        maxLines: Int,
        paint: Long,
        result: TextLayoutResult,
    ) {
        val slot = slots.claim(hash)
        texts[slot] = text
        styles[slot] = style
        delegates[slot] = measurer.delegate
        scales[slot] = measurer.scale
        widths[slot] = maxWidth
        lines[slot] = maxLines
        upper[slot] = uppercase
        paints[slot] = paint
        results[slot] = result
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
