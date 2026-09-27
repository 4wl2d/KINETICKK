// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.measureKkText

/**
 * Value-keyed memo for the in-run HUD: text layouts, rects, dashed strokes and gradient brushes are
 * rebuilt only when their inputs change, so steady HUD frames allocate nothing. Pure caches without
 * presentation state; confined to the draw thread like the foundation memos.
 */
internal object HudDrawCache {
    val paths = KkPathCache(HudPath.entries.size)

    private val layoutTexts = arrayOfNulls<String>(HudText.entries.size)
    private val layoutStyles = arrayOfNulls<TextStyle>(HudText.entries.size)
    private val layoutDelegates = arrayOfNulls<Any>(HudText.entries.size)
    private val layoutScales = FloatArray(HudText.entries.size)
    private val layoutWidths = FloatArray(HudText.entries.size)
    private val layoutUpper = BooleanArray(HudText.entries.size)
    private val layouts = arrayOfNulls<TextLayoutResult>(HudText.entries.size)

    /** The layout of [text] for [slot]; measured again only when an input differs from the last call. */
    fun layout(
        slot: HudText,
        measurer: CanvasTextMeasurer,
        text: String,
        style: TextStyle,
        uppercase: Boolean = false,
        maxWidth: Float = Float.POSITIVE_INFINITY,
    ): TextLayoutResult {
        val index = slot.ordinal
        val cached = layouts[index]
        if (cached != null && layoutStyles[index] === style && layoutDelegates[index] === measurer.delegate &&
            layoutScales[index] == measurer.scale && layoutUpper[index] == uppercase &&
            layoutWidths[index] == maxWidth && layoutTexts[index] == text
        ) return cached
        val measured = measureKkText(measurer, text, style, uppercase, maxWidth)
        layoutTexts[index] = text
        layoutStyles[index] = style
        layoutDelegates[index] = measurer.delegate
        layoutScales[index] = measurer.scale
        layoutWidths[index] = maxWidth
        layoutUpper[index] = uppercase
        layouts[index] = measured
        return measured
    }

    private val rectKeys = FloatArray(HudRect.entries.size * 4) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(HudRect.entries.size)

    /** A [Rect] for [slot]; a new instance only when the coordinates changed. */
    fun rect(slot: HudRect, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val index = slot.ordinal
        val base = index * 4
        val cached = rects[index]
        if (cached != null && rectKeys[base] == left && rectKeys[base + 1] == top &&
            rectKeys[base + 2] == right && rectKeys[base + 3] == bottom
        ) return cached
        rectKeys[base] = left
        rectKeys[base + 1] = top
        rectKeys[base + 2] = right
        rectKeys[base + 3] = bottom
        return Rect(left, top, right, bottom).also { rects[index] = it }
    }

    private val dashKeys = FloatArray(HudDash.entries.size * 3) { Float.NaN }
    private val dashStrokes = arrayOfNulls<Stroke>(HudDash.entries.size)

    /** A dashed stroke of [width] px with [on]/[off] px intervals for [slot]. */
    fun dashed(slot: HudDash, width: Float, on: Float, off: Float): Stroke {
        val index = slot.ordinal
        val base = index * 3
        val cached = dashStrokes[index]
        if (cached != null && dashKeys[base] == width && dashKeys[base + 1] == on && dashKeys[base + 2] == off) return cached
        dashKeys[base] = width
        dashKeys[base + 1] = on
        dashKeys[base + 2] = off
        return Stroke(width, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off)))
            .also { dashStrokes[index] = it }
    }

    private val dashEffectKeys = FloatArray(2) { Float.NaN }
    private var dashEffect: PathEffect? = null

    /** A dash path effect for lines drawn with `drawLine` (tether). */
    fun lineDash(on: Float, off: Float): PathEffect {
        val cached = dashEffect
        if (cached != null && dashEffectKeys[0] == on && dashEffectKeys[1] == off) return cached
        dashEffectKeys[0] = on
        dashEffectKeys[1] = off
        return PathEffect.dashPathEffect(floatArrayOf(on, off)).also { dashEffect = it }
    }

    private val brushKeys = FloatArray(HudBrush.entries.size * 4) { Float.NaN }
    private val brushColors = LongArray(HudBrush.entries.size)
    private val brushes = arrayOfNulls<Brush>(HudBrush.entries.size)

    /**
     * A brush for [slot] built by [create] when (a, b, c, d, color) differ from the last request.
     * `create` runs only on a miss, so callers may allocate inside it.
     */
    inline fun brush(slot: HudBrush, a: Float, b: Float, c: Float, d: Float, color: Color, create: () -> Brush): Brush =
        cachedBrush(slot, a, b, c, d, color) ?: create().also { storeBrush(slot, a, b, c, d, color, it) }

    fun cachedBrush(slot: HudBrush, a: Float, b: Float, c: Float, d: Float, color: Color): Brush? {
        val index = slot.ordinal
        val base = index * 4
        val cached = brushes[index] ?: return null
        return if (brushKeys[base] == a && brushKeys[base + 1] == b && brushKeys[base + 2] == c &&
            brushKeys[base + 3] == d && brushColors[index] == color.value.toLong()
        ) cached else null
    }

    fun storeBrush(slot: HudBrush, a: Float, b: Float, c: Float, d: Float, color: Color, brush: Brush) {
        val index = slot.ordinal
        val base = index * 4
        brushKeys[base] = a
        brushKeys[base + 1] = b
        brushKeys[base + 2] = c
        brushKeys[base + 3] = d
        brushColors[index] = color.value.toLong()
        brushes[index] = brush
    }
}

/** Cached strings for changing HUD numbers: the string is rebuilt only when the value changes. */
internal class HudNumberText(private val format: (Long) -> String) {
    private var value = Long.MIN_VALUE
    private var text = ""

    fun of(next: Long): String {
        if (next != value) {
            value = next
            text = format(next)
        }
        return text
    }
}

/** Cached strings keyed by a value and a language-dependent template owner. */
internal class HudKeyedText {
    private var key: Any? = null
    private var value = Long.MIN_VALUE
    private var text = ""

    inline fun of(owner: Any, next: Long, build: () -> String): String {
        if (owner !== keyOwner() || next != valueOf()) {
            store(owner, next, build())
        }
        return current()
    }

    fun keyOwner(): Any? = key
    fun valueOf(): Long = value
    fun current(): String = text
    fun store(owner: Any, next: Long, built: String) {
        key = owner
        value = next
        text = built
    }
}

internal enum class HudText {
    TIMER, LEVEL_LABEL, INTEGRITY, INTEGRITY_SPLIT_THREAT, INTEGRITY_SPLIT_SHIELD, SPEED, CHAIN, MATTER, KEYS, WEAPON_LEVEL, OVERHEAT, POLARITY,
    BOSS_NAME, TRIAL_LABEL, TRIAL_NAME, TRIAL_NAME_FIT, TRIAL_CLOCK, TRIAL_PROGRESS, TRIAL_REWARD,
    MESSAGE_TITLE, MESSAGE_DETAIL,
    NOTICE_TITLE_0, NOTICE_TITLE_1, NOTICE_TITLE_2,
    NOTICE_DETAIL_0, NOTICE_DETAIL_1, NOTICE_DETAIL_2, NOTICE_DETAIL_3,
    NOTICE_DETAIL_4, NOTICE_DETAIL_5, NOTICE_DETAIL_6, NOTICE_DETAIL_7,
    NOTICE_DETAIL_8, NOTICE_DETAIL_9, NOTICE_DETAIL_10, NOTICE_DETAIL_11,
    DASH_LABEL, BRAKE_LABEL, TOOLTIP,
    PERF_TITLE, PERF_0, PERF_1, PERF_2, PERF_3, PERF_4,
}

internal enum class HudRect {
    DATA_BAR, INTEGRITY, HEAT, ABILITY, LADDER, OVERDRIVE, CHAIN, BOSS_0, BOSS_1, BOSS_2,
    WEAPON, TRIAL_METER, TRIAL_INFO, TRIAL_ANCHOR, PERF_PANEL,
}

internal enum class HudPath {
    MATTER_CHIP, KEY_CHIP, PAUSE, PERFORMANCE, DASH, BRAKE, DASH_OUTLINE, BRAKE_OUTLINE, DASH_SPEND,
    TRIAL_PANEL, MESSAGE, NOTICE_0, NOTICE_1, NOTICE_2, PERF_PANEL, STAMP,
}

internal enum class HudDash { SHIELD_RING, HALO_TRACK }

internal enum class HudBrush { VIGNETTE, EDGE_LEFT, EDGE_RIGHT, EDGE_TOP, EDGE_BOTTOM, OVERDRIVE_GLOW, CORE_GLOW }

internal fun Offset.isFiniteOffset(): Boolean = x.isFinite() && y.isFinite()
