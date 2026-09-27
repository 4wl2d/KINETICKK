// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.tan

/**
 * Parallelogram/diamond geometry from `kk.css` shapes. Every builder rewinds the receiver and
 * returns it, so callers keep one [Path] per shape and rebuild it only when the geometry changes
 * (see [KkPathCache]). Builders never allocate after the path's first use.
 */
enum class KkSlabKind {
    /** `.slab`: both horizontal ends cut (top edge shifted right by the cut). */
    SLAB,

    /** `.slab-r`: square left edge, cut right end. */
    RIGHT,

    /** `.slab-l`: cut left end, square right edge. */
    LEFT,

    /** `.slab-rev`: leans the other way (top edge shifted left). */
    REVERSED,
}

/** `.slab` family: parallelogram with a [cut]-wide horizontal offset (see [KkSlabKind]). */
fun Path.kkSlab(rect: Rect, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path =
    kkSlab(rect.left, rect.top, rect.right, rect.bottom, cut, kind)

/** Float overload of [kkSlab] for allocation-free hot paths. */
fun Path.kkSlab(left: Float, top: Float, right: Float, bottom: Float, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path {
    rewind()
    val c = cut.coerceIn(0f, (right - left) * 0.5f)
    when (kind) {
        KkSlabKind.SLAB -> quad(left + c, top, right, top, right - c, bottom, left, bottom)
        KkSlabKind.RIGHT -> quad(left, top, right, top, right - c, bottom, left, bottom)
        KkSlabKind.LEFT -> quad(left + c, top, right, top, right, bottom, left, bottom)
        KkSlabKind.REVERSED -> quad(left, top, right - c, top, right, bottom, left + c, bottom)
    }
    return this
}

/** `.chamfer`: top-right and bottom-left corners cut by [cut]. */
fun Path.kkChamfer(rect: Rect, cut: Float): Path {
    rewind()
    val c = cut.coerceIn(0f, kotlin.math.min(rect.width, rect.height) * 0.5f)
    moveTo(rect.left, rect.top)
    lineTo(rect.right - c, rect.top)
    lineTo(rect.right, rect.top + c)
    lineTo(rect.right, rect.bottom)
    lineTo(rect.left + c, rect.bottom)
    lineTo(rect.left, rect.bottom - c)
    close()
    return this
}

/** `.chamfer-1` / tooltip slip: only the top-right corner cut by [cut]. */
fun Path.kkChamferTopRight(rect: Rect, cut: Float): Path {
    rewind()
    val c = cut.coerceIn(0f, kotlin.math.min(rect.width, rect.height) * 0.5f)
    moveTo(rect.left, rect.top)
    lineTo(rect.right - c, rect.top)
    lineTo(rect.right, rect.top + c)
    lineTo(rect.right, rect.bottom)
    lineTo(rect.left, rect.bottom)
    close()
    return this
}

/** `.diamond`: rhombus through the edge midpoints of [rect]. */
fun Path.kkDiamond(rect: Rect): Path = kkDiamond(rect.center.x, rect.center.y, rect.width * 0.5f, rect.height * 0.5f)

/** Diamond centered at ([cx], [cy]) with half extents [rx], [ry]. Allocation-free. */
fun Path.kkDiamond(cx: Float, cy: Float, rx: Float, ry: Float): Path {
    rewind()
    quad(cx, cy - ry, cx + rx, cy, cx, cy + ry, cx - rx, cy)
    return this
}

/**
 * [rect] sheared by [degrees] around its vertical center (`skewX(-12deg)`): the top edge moves
 * right and the bottom edge left for negative angles.
 */
fun Path.kkSheared(rect: Rect, degrees: Float = KkShape.Shear): Path =
    kkSheared(rect.left, rect.top, rect.right, rect.bottom, degrees)

/** Float overload of [kkSheared]. Allocation-free. */
fun Path.kkSheared(left: Float, top: Float, right: Float, bottom: Float, degrees: Float = KkShape.Shear): Path {
    rewind()
    val dx = kkShearOffset(bottom - top, degrees)
    quad(left + dx, top, right + dx, top, right - dx, bottom, left - dx, bottom)
    return this
}

/** Half of the horizontal travel of a [height]-tall edge sheared by [degrees] (positive = top right). */
fun kkShearOffset(height: Float, degrees: Float = KkShape.Shear): Float =
    if (degrees == KkShape.Shear) height * 0.5f * KkShape.ShearRatio
    else -height * 0.5f * tan(degrees * PI_F / 180f)

private fun Path.quad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
    lineTo(x4, y4)
    close()
}

/**
 * Caller-owned reusable paths for HUD/world renderers. Each slot keeps one [Path] and rebuilds it
 * only when the requested geometry differs from the previous call for that slot, so steady-state
 * frames perform no allocation and no path rebuild.
 *
 * Create one per renderer (or `remember` one per composable) and use a distinct slot index per
 * shape drawn in the same frame. Not thread-safe; use it from the draw thread only.
 */
class KkPathCache(slots: Int = 16) {
    private val paths = arrayOfNulls<Path>(slots)
    private val kinds = IntArray(slots) { -1 }
    private val keys = FloatArray(slots * 6)

    /** A reusable path for [slot] without memoization (build it yourself with the `kk*` builders). */
    fun path(slot: Int): Path = paths[slot] ?: Path().also { paths[slot] = it }

    fun slab(slot: Int, rect: Rect, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path =
        slab(slot, rect.left, rect.top, rect.right, rect.bottom, cut, kind)

    fun slab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path {
        val path = path(slot)
        if (!same(slot, kind.ordinal, left, top, right, bottom, cut, 0f)) path.kkSlab(left, top, right, bottom, cut, kind)
        return path
    }

    fun sheared(slot: Int, rect: Rect, degrees: Float = KkShape.Shear): Path =
        sheared(slot, rect.left, rect.top, rect.right, rect.bottom, degrees)

    fun sheared(slot: Int, left: Float, top: Float, right: Float, bottom: Float, degrees: Float = KkShape.Shear): Path {
        val path = path(slot)
        if (!same(slot, 10, left, top, right, bottom, degrees, 0f)) path.kkSheared(left, top, right, bottom, degrees)
        return path
    }

    fun diamond(slot: Int, cx: Float, cy: Float, rx: Float, ry: Float = rx): Path {
        val path = path(slot)
        if (!same(slot, 11, cx, cy, rx, ry, 0f, 0f)) path.kkDiamond(cx, cy, rx, ry)
        return path
    }

    fun chamfer(slot: Int, rect: Rect, cut: Float): Path {
        val path = path(slot)
        if (!same(slot, 12, rect.left, rect.top, rect.right, rect.bottom, cut, 0f)) path.kkChamfer(rect, cut)
        return path
    }

    fun chamferTopRight(slot: Int, rect: Rect, cut: Float): Path {
        val path = path(slot)
        if (!same(slot, 13, rect.left, rect.top, rect.right, rect.bottom, cut, 0f)) path.kkChamferTopRight(rect, cut)
        return path
    }

    private fun same(slot: Int, kind: Int, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Boolean {
        val base = slot * 6
        val hit = kinds[slot] == kind && keys[base] == a && keys[base + 1] == b && keys[base + 2] == c &&
            keys[base + 3] == d && keys[base + 4] == e && keys[base + 5] == f
        if (!hit) {
            kinds[slot] = kind
            keys[base] = a
            keys[base + 1] = b
            keys[base + 2] = c
            keys[base + 3] = d
            keys[base + 4] = e
            keys[base + 5] = f
        }
        return hit
    }
}

/**
 * Geometry-keyed path memo behind the component helpers: a bounded LRU of paths keyed by shape
 * kind and coordinates. Steady geometry (HUD frames) hits the memo, so helpers neither rebuild nor
 * allocate; changing geometry rebuilds the least recently used path (Skiko allocates a small
 * builder per rebuild on desktop/web; Android does not). Recorded draw commands keep their own
 * copy, so reusing a path after it was drawn is safe. Confined to the draw thread; created on
 * first draw so pure token reads never load the graphics backend.
 */
internal object KkPathMemo {
    private const val CAPACITY = 128
    private const val KEYS = 7
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private val kinds = IntArray(CAPACITY) { -1 }
    private val keys = FloatArray(CAPACITY * KEYS)
    private val stamps = LongArray(CAPACITY)
    private var clock = 0L

    const val SLAB = 0 // + KkSlabKind.ordinal (0..3)
    const val SHEARED = 10
    const val DIAMOND = 11
    const val CHAMFER = 12
    const val CHAMFER_TOP_RIGHT = 13
    const val GEM = 14
    const val BAND = 15
    const val BRACKET = 16

    fun slab(left: Float, top: Float, right: Float, bottom: Float, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path =
        get(SLAB + kind.ordinal, left, top, right, bottom, cut, 0f, 0f)

    fun slab(rect: Rect, cut: Float, kind: KkSlabKind = KkSlabKind.SLAB): Path =
        slab(rect.left, rect.top, rect.right, rect.bottom, cut, kind)

    fun sheared(left: Float, top: Float, right: Float, bottom: Float, degrees: Float = KkShape.Shear): Path =
        get(SHEARED, left, top, right, bottom, degrees, 0f, 0f)

    fun diamond(cx: Float, cy: Float, rx: Float, ry: Float = rx): Path = get(DIAMOND, cx, cy, rx, ry, 0f, 0f, 0f)

    fun chamfer(rect: Rect, cut: Float): Path = get(CHAMFER, rect.left, rect.top, rect.right, rect.bottom, cut, 0f, 0f)

    fun chamferTopRight(rect: Rect, cut: Float): Path =
        get(CHAMFER_TOP_RIGHT, rect.left, rect.top, rect.right, rect.bottom, cut, 0f, 0f)

    /** Vertical hexagon gem (`.gem`) of [size] centered at ([cx], [cy]). */
    fun gem(cx: Float, cy: Float, size: Float): Path = get(GEM, cx, cy, size, 0f, 0f, 0f, 0f)

    /** Card band quad: top edge inset [topInset] on the left, bottom edge inset [bottomInset] on the right. */
    fun band(left: Float, top: Float, right: Float, bottom: Float, topInset: Float, bottomInset: Float): Path =
        get(BAND, left, top, right, bottom, topInset, bottomInset, 0f)

    /** Open bracket: legs of [leg] down from a line at [y] between [left] and [right]. */
    fun bracket(left: Float, right: Float, y: Float, leg: Float): Path = get(BRACKET, left, right, y, leg, 0f, 0f, 0f)

    private fun get(kind: Int, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, g: Float): Path {
        clock++
        var oldest = 0
        for (index in 0 until CAPACITY) {
            if (kinds[index] == kind) {
                val base = index * KEYS
                if (keys[base] == a && keys[base + 1] == b && keys[base + 2] == c && keys[base + 3] == d &&
                    keys[base + 4] == e && keys[base + 5] == f && keys[base + 6] == g
                ) {
                    stamps[index] = clock
                    return paths[index]!!
                }
            }
            if (stamps[index] < stamps[oldest]) oldest = index
        }
        val path = paths[oldest] ?: Path().also { paths[oldest] = it }
        build(path, kind, a, b, c, d, e, f)
        kinds[oldest] = kind
        val base = oldest * KEYS
        keys[base] = a
        keys[base + 1] = b
        keys[base + 2] = c
        keys[base + 3] = d
        keys[base + 4] = e
        keys[base + 5] = f
        keys[base + 6] = g
        stamps[oldest] = clock
        return path
    }

    private fun build(path: Path, kind: Int, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        when (kind) {
            in SLAB..SLAB + 3 -> path.kkSlab(a, b, c, d, e, KkSlabKind.entries[kind - SLAB])
            SHEARED -> path.kkSheared(a, b, c, d, e)
            DIAMOND -> path.kkDiamond(a, b, c, d)
            CHAMFER -> path.kkChamfer(Rect(a, b, c, d), e)
            CHAMFER_TOP_RIGHT -> path.kkChamferTopRight(Rect(a, b, c, d), e)
            GEM -> {
                path.rewind()
                val l = a - c * 0.5f
                val t = b - c * 0.5f
                path.moveTo(l + c * 0.5f, t)
                path.lineTo(l + c, t + c * 0.3f)
                path.lineTo(l + c, t + c * 0.7f)
                path.lineTo(l + c * 0.5f, t + c)
                path.lineTo(l, t + c * 0.7f)
                path.lineTo(l, t + c * 0.3f)
                path.close()
            }
            BAND -> {
                path.rewind()
                path.moveTo(a + e, b)
                path.lineTo(c, b)
                path.lineTo(c - f, d)
                path.lineTo(a, d)
                path.close()
            }
            BRACKET -> {
                path.rewind()
                path.moveTo(a, c + d)
                path.lineTo(a, c)
                path.lineTo(b, c)
                path.lineTo(b, c + d)
            }
        }
    }
}

/**
 * Memo of recently used [Rect]s by coordinates: helpers that compute and return rects reuse the
 * same immutable instance while the geometry is unchanged (no per-frame allocation).
 */
internal object KkRects {
    private const val CAPACITY = 64
    private val keys = FloatArray(CAPACITY * 4) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(CAPACITY)
    private var next = 0

    fun of(left: Float, top: Float, right: Float, bottom: Float): Rect {
        for (index in 0 until CAPACITY) {
            val base = index * 4
            if (keys[base] == left && keys[base + 1] == top && keys[base + 2] == right && keys[base + 3] == bottom) {
                return rects[index]!!
            }
        }
        val rect = Rect(left, top, right, bottom)
        val base = next * 4
        keys[base] = left
        keys[base + 1] = top
        keys[base + 2] = right
        keys[base + 3] = bottom
        rects[next] = rect
        next = (next + 1) % CAPACITY
        return rect
    }
}

/** Fills a `.slab`-family parallelogram. Allocation-free for steady geometry (memoized path). */
fun DrawScope.drawKkSlab(
    rect: Rect,
    color: Color,
    cut: Float = 14f * density,
    kind: KkSlabKind = KkSlabKind.SLAB,
    style: DrawStyle = Fill,
) {
    drawPath(KkPathMemo.slab(rect, cut, kind), color, style = style)
}

/** Fills [rect] sheared by −12°. Allocation-free for steady geometry (memoized path). */
fun DrawScope.drawKkSheared(rect: Rect, color: Color, style: DrawStyle = Fill) {
    drawPath(KkPathMemo.sheared(rect.left, rect.top, rect.right, rect.bottom), color, style = style)
}

/**
 * Horizontal shear as rotate, scale, rotate (the SVD of `skewX`), so canvas shears need no
 * matrix object. [ratio] is x' = x + ratio * y.
 */
@PublishedApi
internal object KkShearSvd {
    private var cachedRatio = Float.NaN
    var phiDeg = 0f
        private set
    var sx = 1f
        private set
    var sy = 1f
        private set
    var thetaDeg = 0f
        private set

    fun prepare(ratio: Float) {
        if (ratio == cachedRatio) return
        cachedRatio = ratio
        val e = 1f
        val g = ratio * 0.5f
        val h = -ratio * 0.5f
        val q = kotlin.math.sqrt(e * e + h * h)
        val r = kotlin.math.abs(g)
        sx = q + r
        sy = q - r
        val a1 = kotlin.math.atan2(g, 0f)
        val a2 = kotlin.math.atan2(h, e)
        thetaDeg = (a2 - a1) * 0.5f * 180f / PI_F
        phiDeg = (a2 + a1) * 0.5f * 180f / PI_F
    }
}

/** x offset per unit of y for a lean of [degrees] (negative degrees lean the top edge right). */
@PublishedApi
internal fun kkShearRatio(degrees: Float): Float =
    if (degrees == KkShape.Shear) -KkShape.ShearRatio else kotlin.math.tan(degrees * PI_F / 180f)

/**
 * Applies a shear of [degrees] (default −12°, `skewX`) around the horizontal line y = [pivotY]
 * (meters, pips, sheared cells, shutter slabs). Allocation-free: rotate/scale/rotate only.
 */
fun DrawTransform.kkShear(pivotY: Float, degrees: Float = KkShape.Shear) {
    val ratio = kkShearRatio(degrees)
    if (ratio == 0f) return
    KkShearSvd.prepare(ratio)
    translate(0f, pivotY)
    rotate(KkShearSvd.phiDeg, Offset.Zero)
    scale(KkShearSvd.sx, KkShearSvd.sy, Offset.Zero)
    rotate(KkShearSvd.thetaDeg, Offset.Zero)
    translate(0f, -pivotY)
}

/** Expands [rect] to at least [minDp] × [minDp] around its center (touch targets, default 44). */
fun DrawScope.kkTouchRect(rect: Rect, minDp: Float = 44f): Rect {
    val min = minDp * density
    val w = kotlin.math.max(rect.width, min)
    val h = kotlin.math.max(rect.height, min)
    return Rect(Offset(rect.center.x - w * 0.5f, rect.center.y - h * 0.5f), Size(w, h))
}

/** Cached plain [Stroke]s by pixel width (butt caps, miter joins); allocation-free on reuse. */
internal object KkStrokes {
    private const val CAPACITY = 24
    private val widths = FloatArray(CAPACITY) { -1f }
    private val strokes = arrayOfNulls<Stroke>(CAPACITY)
    private var next = 0

    fun of(width: Float): Stroke {
        for (index in 0 until CAPACITY) {
            if (widths[index] == width) return strokes[index]!!
        }
        val created = Stroke(width)
        widths[next] = width
        strokes[next] = created
        next = (next + 1) % CAPACITY
        return created
    }
}

/** A cached plain stroke of [widthPx] (reuse across frames without allocation). */
fun kkStroke(widthPx: Float): Stroke = KkStrokes.of(widthPx)
