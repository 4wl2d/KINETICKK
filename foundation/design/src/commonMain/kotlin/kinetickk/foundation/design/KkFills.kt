// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Repeating fills (`kk.css .hatch/.stripes/.halftone`). Every kind is one small tile rendered
 * once per color and density into an [ImageBitmap] and drawn through a repeated [ImageShader].
 * Diagonal kinds run bottom-left to top-right (`/`), like the boards.
 *
 * [periodDp] is the perpendicular pattern period, [markDp] the line/band thickness or dot radius,
 * [loopSeconds] one animation cycle for animated stripes (`kk-stripes`).
 */
enum class KkFillKind(val periodDp: Float, val markDp: Float, val loopSeconds: Float) {
    /** Locked/unavailable hatch: 2 px lines every 8 px (bone @8 %). */
    HATCH(8f, 2f, 0f),

    /** Denser hatch for the MONO threat marking. */
    THREAT_HATCH(7f, 2.5f, 0f),

    /** Hazard stripes: 10 px bands, 20 px period (`.stripes`, 0.7 s loop). */
    STRIPES(20f, 10f, 0.7f),

    /** Meter overheat/overdrive stripes: 6 px bands, 12 px period (0.5 s loop). */
    METER_STRIPES(12f, 6f, 0.5f),

    /** Level badge T4 echo stripes: 4 px bands, 8 px period. */
    BADGE_STRIPES(8f, 4f, 0f),

    /** Legendary card echo stripes: 8 px bands, 16 px period (0.9 s loop). */
    CARD_STRIPES(16f, 8f, 0.9f),

    /** Halftone dots: 9 px grid, ~2 px dots (`.halftone`). */
    HALFTONE(9f, 2f, 0f),

    /** Large halftone dots: 14 px grid, ~3.3 px dots (`.halftone-lg`). */
    HALFTONE_LARGE(14f, 3.3f, 0f),

    // Pattern fills that tell hues apart without color (color vision presets share one ramp). None
    // uses the 45° lines of the hatch and stripes, which mean locked and hazard.

    /** Horizontal sine shimmer: one wavy line every 6 px, a wave 12 px long (1.4 s drift loop). */
    SHIMMER(6f, 1.3f, 1.4f),

    /** Triangle lattice: horizontal and ±60° lines, triangles with 12 px sides. */
    LATTICE(12f, 0.9f, 0f),

    /** Vertical zigzag: one column every 7 px, a zig and a zag per 9 px of height. */
    ZIGZAG(7f, 1.2f, 0f),

    /** Concentric rings: two rings per 12 px cell. */
    RINGS(12f, 1.1f, 0f),

    /** Random pitting: irregular pits scattered over an 18 px tile (never the halftone grid). */
    PITTING(18f, 1.5f, 0f),

    // Card materials. The steep diagonals lean like the slash cut (about 27° from vertical), never the
    // 45° of the hatch and stripes.

    /** Brushed metal: horizontal hairlines of varying strength every 2 px, one in three broken. */
    HAIRLINE(6f, 0.6f, 0f),

    /** Fine steep diagonal lines every 4.5 px (metal grain). */
    SLASH(4.5f, 0.8f, 0f),

    /** Steep diagonal scanlines every 7 px. */
    SCANLINE(7f, 1.4f, 0f),
    ;

    internal val dots: Boolean get() = this == HALFTONE || this == HALFTONE_LARGE

    /** Diagonal line kinds (hatch and stripes), drawn as `/` lines on a square tile. */
    internal val diagonal: Boolean get() = ordinal <= CARD_STRIPES.ordinal

    /** Steep diagonal kinds: lines 2x + y = n * side on a square tile ([SLASH], [SCANLINE]). */
    internal val steep: Boolean get() = this == SLASH || this == SCANLINE
}

/**
 * Fills [path] with the repeating [kind] in [color] at [alpha] (`0..1`). Allocation-free once the
 * tile for this color and density exists; the pattern is anchored to the current transform's
 * origin, so it moves and turns with whatever the caller translated or rotated.
 */
fun DrawScope.drawKkPattern(path: Path, kind: KkFillKind, color: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawPath(path, kkFillBrush(kind, color), alpha = alpha.coerceAtMost(1f))
}

/** Rect overload of [drawKkPattern]. */
fun DrawScope.drawKkPattern(rect: Rect, kind: KkFillKind, color: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawRect(kkFillBrush(kind, color), rect.topLeft, rect.size, alpha = alpha.coerceAtMost(1f))
}

/** Default hatch color (bone @8 %). */
val KkHatchColor: Color = Color(0x14F1F0E8)

/**
 * Cached repeating brush for [kind] in [color] over [background] at the current density.
 * Allocation-free after the first request for the same arguments. The pattern is anchored to the
 * canvas origin of the current transform.
 */
fun DrawScope.kkFillBrush(kind: KkFillKind, color: Color, background: Color = Color.Transparent): Brush =
    KkTileCache.brush(kind, color, background, density)

/** Tile width in pixels of [kind] at the current density (one animation cycle of travel). */
fun DrawScope.kkFillTilePx(kind: KkFillKind): Float = KkTileCache.tilePx(kind, density).toFloat()

/** Hatch inside [rect] (`.hatch`). Allocation-free. */
fun DrawScope.drawKkHatch(rect: Rect, color: Color = KkHatchColor) {
    drawRect(kkFillBrush(KkFillKind.HATCH, color), rect.topLeft, rect.size)
}

/** Hatch inside [path]. Allocation-free. */
fun DrawScope.drawKkHatch(path: Path, color: Color = KkHatchColor) {
    drawPath(path, kkFillBrush(KkFillKind.HATCH, color))
}

/**
 * Two-color diagonal stripes inside [rect] (optionally clipped to [clip]). [time] in seconds
 * scrolls the pattern one tile per [KkFillKind.loopSeconds]; pass 0 for static stripes.
 * Allocation-free.
 */
fun DrawScope.drawKkStripes(
    rect: Rect,
    color: Color = Kk.Hazard,
    background: Color = Kk.Ink,
    time: Float = 0f,
    kind: KkFillKind = KkFillKind.STRIPES,
    clip: Path? = null,
) {
    if (clip != null) {
        clipPath(clip) { kkDrawStripes(rect.left, rect.top, rect.right, rect.bottom, color, background, time, kind) }
    } else {
        kkDrawStripes(rect.left, rect.top, rect.right, rect.bottom, color, background, time, kind)
    }
}

/** Float-bounds stripes without a [Rect] (hot paths). */
internal fun DrawScope.kkDrawStripes(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    background: Color,
    time: Float,
    kind: KkFillKind,
) {
    if (right <= left || bottom <= top) return
    val brush = kkFillBrush(kind, color, background)
    val shift = if (kind.loopSeconds > 0f && time != 0f) {
        positiveModulo(time / kind.loopSeconds, 1f) * kkFillTilePx(kind)
    } else {
        0f
    }
    clipRect(left, top, right, bottom) {
        translate(shift, 0f) {
            drawRect(brush, Offset(left - shift, top), Size(right - left, bottom - top))
        }
    }
}

/** Halftone dots inside [rect]; combine with [drawKkRadialFade] for `.halftone.fade-rad`. */
fun DrawScope.drawKkHalftone(rect: Rect, color: Color, large: Boolean = false) {
    val kind = if (large) KkFillKind.HALFTONE_LARGE else KkFillKind.HALFTONE
    drawRect(kkFillBrush(kind, color), rect.topLeft, rect.size)
}

/** Halftone dots inside [path]. */
fun DrawScope.drawKkHalftone(path: Path, color: Color, large: Boolean = false) {
    val kind = if (large) KkFillKind.HALFTONE_LARGE else KkFillKind.HALFTONE
    drawPath(path, kkFillBrush(kind, color))
}

/**
 * Background grid lines every [spacingDp] (default 40) inside [rect], aligned to [origin].
 * Allocation-free.
 */
fun DrawScope.drawKkGrid(
    rect: Rect,
    color: Color = Kk.Ink1,
    spacingDp: Float = 40f,
    origin: Offset = Offset.Zero,
    strokeDp: Float = 1f,
) {
    val step = spacingDp * density
    if (step < 2f) return
    val stroke = strokeDp * density
    var x = rect.left + positiveModulo(origin.x - rect.left, step)
    while (x <= rect.right) {
        drawLine(color, Offset(x, rect.top), Offset(x, rect.bottom), stroke)
        x += step
    }
    var y = rect.top + positiveModulo(origin.y - rect.top, step)
    while (y <= rect.bottom) {
        drawLine(color, Offset(rect.left, y), Offset(rect.right, y), stroke)
        y += step
    }
}

/**
 * Draws [content] into a layer masked by a radial fade (`mask-image: radial-gradient(closest-side,
 * #000 innerStop, transparent)`): opaque up to [innerStop] of the radius, transparent at the
 * closest side of [rect]. Allocation-free for the default [innerStop]; uses one offscreen layer, so
 * prefer it for backgrounds rather than many small elements per frame.
 */
inline fun DrawScope.drawKkRadialFade(
    rect: Rect,
    innerStop: Float = 0.2f,
    content: DrawScope.() -> Unit,
) {
    val canvas = drawContext.canvas
    canvas.saveLayer(rect, KkLayers.paint)
    content()
    kkApplyRadialMask(rect, innerStop)
    canvas.restore()
}

@PublishedApi
internal fun DrawScope.kkApplyRadialMask(rect: Rect, innerStop: Float) {
    val radius = min(rect.width, rect.height) * 0.5f
    kkApplyEllipseMask(rect, rect.center.x, rect.center.y, radius, radius, innerStop)
}

/** Multiplies the current layer inside [rect] by an elliptical fade centered at ([cx], [cy]). */
internal fun DrawScope.kkApplyEllipseMask(rect: Rect, cx: Float, cy: Float, rx: Float, ry: Float, innerStop: Float) {
    if (rx <= 0f || ry <= 0f) return
    translate(cx, cy) {
        scale(rx, ry, Offset.Zero) {
            drawRect(
                KkLayers.radialMask(innerStop),
                Offset((rect.left - cx) / rx, (rect.top - cy) / ry),
                Size(rect.width / rx, rect.height / ry),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/** Native-backed layer helpers, created on first draw only. */
@PublishedApi
internal object KkLayers {
    val paint: Paint by lazy { Paint() }
    private var defaultMask: Brush? = null
    private val customStops = FloatArray(4) { Float.NaN }
    private val customMasks = arrayOfNulls<Brush>(4)
    private var customNext = 0

    fun radialMask(innerStop: Float): Brush {
        if (innerStop == 0.2f) {
            return defaultMask ?: createMask(0.2f).also { defaultMask = it }
        }
        for (index in 0 until 4) if (customStops[index] == innerStop) return customMasks[index]!!
        val created = createMask(innerStop)
        customStops[customNext] = innerStop
        customMasks[customNext] = created
        customNext = (customNext + 1) % 4
        return created
    }

    private fun createMask(innerStop: Float): Brush = Brush.radialGradient(
        0f to Color.Black,
        innerStop.coerceIn(0f, 0.99f) to Color.Black,
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f,
    )
}

/** Small bounded cache of fill tiles; drawing is confined to the UI/draw thread. */
private object KkTileCache {
    // Hatch, stripes and halftones in a few colors, the five patterns in each status hue and the
    // card materials and faces (hairlines, halftones and patterns) of a dealt hand.
    private const val CAPACITY = 64
    private val kindKeys = LongArray(CAPACITY)
    private val colorKeys = LongArray(CAPACITY)
    private val brushes = arrayOfNulls<Brush>(CAPACITY)
    private var next = 0

    /** Tile width in px: one animation cycle of horizontal travel. */
    fun tilePx(kind: KkFillKind, density: Float): Int {
        val side = when {
            kind.dots -> kind.periodDp
            kind.diagonal -> kind.periodDp * sqrt(2f)
            kind.steep -> kind.periodDp * sqrt(5f)
            kind == KkFillKind.SHIMMER -> kind.periodDp * 2f
            kind == KkFillKind.HAIRLINE -> 24f
            else -> kind.periodDp
        }
        return max(2, (side * density).roundToInt())
    }

    /** Tile height in px (square tiles match [tilePx]). */
    fun tileHeightPx(kind: KkFillKind, density: Float): Int {
        val side = when (kind) {
            KkFillKind.SHIMMER -> kind.periodDp
            KkFillKind.LATTICE -> kind.periodDp * sqrt(3f)
            KkFillKind.ZIGZAG -> kind.periodDp * 9f / 7f
            KkFillKind.HAIRLINE -> kind.periodDp
            else -> return tilePx(kind, density)
        }
        return max(2, (side * density).roundToInt())
    }

    fun brush(kind: KkFillKind, color: Color, background: Color, density: Float): Brush {
        val kindKey = (kind.ordinal.toLong() shl 40) or ((density * 1000f).roundToInt().toLong() and 0xFFFFFFFFFFL)
        val colorKey = (color.toArgb().toLong() shl 32) or (background.toArgb().toLong() and 0xFFFFFFFFL)
        for (index in 0 until CAPACITY) {
            val cached = brushes[index] ?: continue
            if (kindKeys[index] == kindKey && colorKeys[index] == colorKey) return cached
        }
        val created = ShaderBrush(ImageShader(tile(kind, color, background, density), TileMode.Repeated, TileMode.Repeated))
        kindKeys[next] = kindKey
        colorKeys[next] = colorKey
        brushes[next] = created
        next = (next + 1) % CAPACITY
        return created
    }

    private fun tile(kind: KkFillKind, color: Color, background: Color, density: Float): ImageBitmap {
        val side = tilePx(kind, density)
        val height = tileHeightPx(kind, density)
        val bitmap = ImageBitmap(side, height)
        val s = side.toFloat()
        val h = height.toFloat()
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(s, h)) {
            if (background.alpha > 0f) drawRect(background)
            val width = kind.markDp * density
            when {
                kind.dots -> drawCircle(color, kind.markDp * density, Offset(s * 0.5f, s * 0.5f))
                kind.diagonal -> {
                    // Lines x + y = c are "/" in y-down coordinates; three copies keep the tile seamless.
                    for (copy in 0..2) {
                        val c = copy * s
                        drawLine(color, Offset(-s, c + s), Offset(c + s, -s), width, StrokeCap.Butt)
                    }
                }
                kind == KkFillKind.SHIMMER -> drawPath(shimmerWave(s, h), color, style = Stroke(width, cap = StrokeCap.Butt))
                kind == KkFillKind.LATTICE -> {
                    // Horizontal rows at 0, h/2 and h; ±60° lines through the row vertices (x = k * s at
                    // y = 0, offset by s/2 on the middle row). Copies past the edges keep it seamless.
                    for (row in 0..2) drawLine(color, Offset(-1f, h * row * 0.5f), Offset(s + 1f, h * row * 0.5f), width)
                    for (k in -1..2) {
                        drawLine(color, Offset(k * s, 0f), Offset(k * s + s, h), width)
                        drawLine(color, Offset(k * s, 0f), Offset(k * s - s, h), width)
                    }
                }
                kind == KkFillKind.ZIGZAG -> drawPath(zigzagColumn(s, h), color, style = Stroke(width, join = StrokeJoin.Miter))
                kind.steep -> {
                    // Lines 2x + y = c, one every side length of c; the tile repeats along both axes.
                    for (copy in 0..3) {
                        val c = copy * s
                        drawLine(color, Offset((c + s) * 0.5f, -s), Offset((c - 2f * s) * 0.5f, 2f * s), width, StrokeCap.Butt)
                    }
                }
                kind == KkFillKind.HAIRLINE -> {
                    // Each hairline centred on a pixel row, so it stays one crisp line at any density.
                    val row = h / 3f
                    fun y(index: Int): Float = kotlin.math.floor(row * (index + 0.5f)) + 0.5f
                    drawLine(color, Offset(0f, y(0)), Offset(s, y(0)), width)
                    drawLine(color.copy(alpha = color.alpha * 0.55f), Offset(0f, y(1)), Offset(s * 0.62f, y(1)), width)
                    drawLine(color.copy(alpha = color.alpha * 0.8f), Offset(s * 0.7f, y(1)), Offset(s, y(1)), width)
                    drawLine(color.copy(alpha = color.alpha * 0.75f), Offset(0f, y(2)), Offset(s, y(2)), width)
                }
                kind == KkFillKind.RINGS -> {
                    val center = Offset(s * 0.5f, s * 0.5f)
                    drawCircle(color, s * 0.36f, center, style = Stroke(width))
                    drawCircle(color, s * 0.17f, center, style = Stroke(width))
                    drawCircle(color, width * 0.6f, center)
                }
                kind == KkFillKind.PITTING -> {
                    // A fixed scatter (not random per run), wrapped across the edges.
                    for (pit in Pits.indices step 3) {
                        val radius = Pits[pit + 2] * kind.markDp * density
                        for (dx in -1..1) for (dy in -1..1) {
                            drawCircle(color, radius, Offset(Pits[pit] * s + dx * s, Pits[pit + 1] * s + dy * s))
                        }
                    }
                }
            }
        }
        return bitmap
    }

    /** One sine wavelength across the tile's width, centered vertically, a little past both edges. */
    private fun shimmerWave(width: Float, height: Float): Path = Path().apply {
        val amplitude = height * 0.22f
        val steps = 24
        for (step in -1..steps + 1) {
            val x = width * step / steps
            val y = height * 0.5f + amplitude * kotlin.math.sin(x / width * 2f * kotlin.math.PI.toFloat())
            if (step == -1) moveTo(x, y) else lineTo(x, y)
        }
    }

    /** A zig and a zag down the tile's height, continuing past the top and bottom edges. */
    private fun zigzagColumn(width: Float, height: Float): Path = Path().apply {
        val left = width * 0.5f - width * 0.24f
        val right = width * 0.5f + width * 0.24f
        moveTo(right, -height * 0.5f)
        lineTo(left, 0f)
        lineTo(right, height * 0.5f)
        lineTo(left, height)
        lineTo(right, height * 1.5f)
    }

    /** Pits as (x, y, size) in tile fractions and multiples of the mark radius. */
    private val Pits = floatArrayOf(
        0.12f, 0.18f, 1.0f,
        0.47f, 0.09f, 0.55f,
        0.78f, 0.27f, 1.25f,
        0.33f, 0.45f, 0.7f,
        0.66f, 0.58f, 0.9f,
        0.09f, 0.71f, 0.6f,
        0.9f, 0.83f, 0.75f,
        0.43f, 0.86f, 1.15f,
        0.24f, 0.97f, 0.5f,
    )
}
