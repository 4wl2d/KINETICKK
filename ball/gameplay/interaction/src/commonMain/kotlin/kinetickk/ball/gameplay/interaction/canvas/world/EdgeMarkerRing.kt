// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.DEGREES
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.angleOf

/**
 * Where [EdgeMarkerPlanner] may put a marker: the table of clear marker positions on a ring inset
 * [EDGE_INSET_DP] from the screen edges, one per ring sample, each pushed inward just past a HUD
 * band lying along that edge and then at most [maxEdgeDepth] until the marker box (icon and
 * distance, text toward the screen center) is clear of the keep-out; plus the ring geometry and
 * the marker box. [rebuild] recomputes the table only when the keep-out, viewport or marker size
 * changed.
 */
internal class EdgeMarkerRing {
    /** Per ring sample, how far inward its clear position lies (NaN: none within reach). */
    val depths = FloatArray(MAX_SAMPLES)

    // Clear marker centers and their unit directions from the screen center, per ring sample.
    val markerX = FloatArray(MAX_SAMPLES)
    val markerY = FloatArray(MAX_SAMPLES)
    val directionX = FloatArray(MAX_SAMPLES)
    val directionY = FloatArray(MAX_SAMPLES)
    val depthCost = FloatArray(MAX_SAMPLES)

    // Per ring sample, its direction from the screen center (degrees clockwise from the right).
    val sampleAngle = FloatArray(MAX_SAMPLES)
    var samples = 0
        private set
    private var sampleStep = 1f
    private var ringLeft = 0f
    private var ringTop = 0f
    private var ringRight = 0f
    private var ringBottom = 0f
    var width = 0f
        private set
    var height = 0f
        private set
    private var iconHalf = 0f
    private var gap = 0f
    var textWidth = 0f
        private set
    var halfHeight = 0f
        private set
    private var markerSpacing = 0f
    private var keepOut: WorldHudKeepOut? = null

    // Boxes of the markers placed so far this frame ([take]); [crowded] keeps the rest apart from them.
    private val placed = FloatArray(EdgeMarkerBatch.CAPACITY * 4)
    private var placedCount = 0
    private var keyRevision = -1
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextWidth = Float.NaN

    /** Rebuilds the clear-position table when [keepOut], the viewport or the marker width changed; true when it did. */
    fun rebuild(keepOut: WorldHudKeepOut, width: Float, height: Float, density: Float, textWidth: Float): Boolean {
        if (this.keepOut === keepOut && keyRevision == keepOut.revision && keyWidth == width &&
            keyHeight == height && keyDensity == density && keyTextWidth == textWidth
        ) return false
        this.keepOut = keepOut
        keyRevision = keepOut.revision
        keyWidth = width
        keyHeight = height
        keyDensity = density
        keyTextWidth = textWidth
        this.width = width
        this.height = height
        val unit = density.coerceAtLeast(1f)
        iconHalf = EDGE_ICON_DP * 0.5f * unit
        gap = EDGE_TEXT_GAP_DP * unit
        this.textWidth = textWidth
        halfHeight = EDGE_HALF_HEIGHT_DP * unit
        markerSpacing = EDGE_MARKER_SPACING_DP * unit
        val inset = EDGE_INSET_DP * unit
        ringLeft = inset
        ringTop = inset
        ringRight = width - inset
        ringBottom = height - inset
        val ringWidth = max(0f, ringRight - ringLeft)
        val ringHeight = max(0f, ringBottom - ringTop)
        val perimeter = 2f * (ringWidth + ringHeight)
        sampleStep = max(EDGE_SAMPLE_DP * unit, perimeter / MAX_SAMPLES)
        samples = min(MAX_SAMPLES, (perimeter / sampleStep).toInt().coerceAtLeast(1))
        val maxDepth = maxEdgeDepth(width, height)
        val step = 3f * unit
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val bands = keepOut.rects() // rebuilt only when the regions change
        for (index in 0 until samples) {
            val s = index * sampleStep
            val px = ringX(s)
            val py = ringY(s)
            val nx = normalX(s)
            val ny = normalY(s)
            val limit = edgeBandDepth(bands, px, py, nx, ny) + maxDepth
            var depth = Float.NaN
            var k = 0f
            while (k <= limit) {
                if (clearAt(px + nx * k, py + ny * k)) {
                    depth = k
                    break
                }
                k += step
            }
            depths[index] = depth
            if (depth.isNaN()) continue
            val x = px + nx * depth
            val y = py + ny * depth
            markerX[index] = x
            markerY[index] = y
            val length = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).coerceAtLeast(1e-3f)
            directionX[index] = (x - centerX) / length
            directionY[index] = (y - centerY) / length
            sampleAngle[index] = angleOf(x - centerX, y - centerY)
            depthCost[index] = DEPTH_COST_DEGREES * depth / max(1f, min(width, height))
        }
        return true
    }

    /** Starts a frame's arrangement: no marker box is taken yet. */
    fun clearTaken() {
        placedCount = 0
    }

    /** Takes the box of a marker centered at ([x], [y]) for the rest of this arrangement. */
    fun take(x: Float, y: Float) {
        val base = placedCount * 4
        placed[base] = markerLeft(x)
        placed[base + 1] = y - halfHeight
        placed[base + 2] = markerRight(x)
        placed[base + 3] = y + halfHeight
        placedCount++
    }

    /** Whether a marker centered at ([x], [y]) comes within [markerSpacing] of a marker box [take]n this arrangement. */
    fun crowded(x: Float, y: Float): Boolean {
        val left = markerLeft(x) - markerSpacing
        val right = markerRight(x) + markerSpacing
        val top = y - halfHeight - markerSpacing
        val bottom = y + halfHeight + markerSpacing
        for (index in 0 until placedCount) {
            val base = index * 4
            if (left < placed[base + 2] && right > placed[base] && top < placed[base + 3] && bottom > placed[base + 1]) return true
        }
        return false
    }

    /** Degrees between ring sample [index]'s direction and the target direction ([ux], [uy]). */
    fun pointingError(index: Int, ux: Float, uy: Float): Float {
        val dot = directionX[index] * ux + directionY[index] * uy
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES
    }

    /** Direction error plus depth cost of ring sample [index] (a clear one) for the target direction ([ux], [uy]). */
    fun errorAt(index: Int, ux: Float, uy: Float): Float {
        val dot = directionX[index] * ux + directionY[index] * uy
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
    }

    /** How many ring samples lie between samples [a] and [b], the short way round. */
    fun ringApart(a: Int, b: Int): Int {
        val along = abs(a - b)
        return min(along, samples - along)
    }

    /** The ring sample at or before ring parameter [s]. */
    fun ringSample(s: Float): Int = (s / sampleStep).toInt().coerceIn(0, max(0, samples - 1))

    /**
     * How far past the ring position at ([px], [py]) (inward normal [nx], [ny]) a marker must move to
     * clear the HUD band lying along that edge: the regions flatter along the edge than deep that
     * overlap the marker's footprint and start within a marker's extent of the edge or of such a
     * region (the phone's top row, a boss row and a trial panel below it, a bottom cluster): a gap
     * narrower than the marker cannot hold it. Zero when no band lies there.
     */
    private fun edgeBandDepth(bands: List<Rect>, px: Float, py: Float, nx: Float, ny: Float): Float {
        val left = markerLeft(px)
        val right = markerRight(px)
        val horizontal = ny != 0f
        // The marker's extent across the edge.
        val across = if (horizontal) 2f * halfHeight else right - left
        // Covered distance from the screen edge, grown while another region joins the band.
        var covered = 0f
        var grown = true
        var rounds = 0
        while (grown && rounds++ < bands.size) {
            grown = false
            for (index in bands.indices) {
                val band = bands[index]
                val along = if (horizontal) band.width else band.height
                val deep = if (horizontal) band.height else band.width
                if (deep > along) continue
                val overlaps = if (horizontal) band.left < right && band.right > left else band.top < py + halfHeight && band.bottom > py - halfHeight
                if (!overlaps) continue
                val near: Float
                val far: Float
                when {
                    ny > 0f -> { near = band.top; far = band.bottom }
                    ny < 0f -> { near = height - band.bottom; far = height - band.top }
                    nx > 0f -> { near = band.left; far = band.right }
                    else -> { near = width - band.right; far = width - band.left }
                }
                if (near < covered + across && far > covered) {
                    covered = far
                    grown = true
                }
            }
        }
        if (covered <= 0f) return 0f
        // The marker's center clears the band by its half extent across the edge.
        val half = if (ny != 0f) halfHeight else iconHalf
        val inset = if (ny > 0f) ringTop else if (ny < 0f) height - ringBottom else if (nx > 0f) ringLeft else width - ringRight
        return max(0f, covered + half - inset)
    }

    /** Whether the marker (icon + distance, text toward the screen center) fits at ([x], [y]). */
    fun clearAt(x: Float, y: Float): Boolean {
        val left = markerLeft(x)
        val right = markerRight(x)
        val top = y - halfHeight
        val bottom = y + halfHeight
        if (left < 0f || right > width || top < 0f || bottom > height) return false
        return keepOut?.intersects(left, top, right, bottom) != true
    }

    fun markerLeft(x: Float): Float = if (x > width * 0.5f) x - iconHalf - gap - textWidth else x - iconHalf
    fun markerRight(x: Float): Float = if (x > width * 0.5f) x + iconHalf else x + iconHalf + gap + textWidth

    private val ringWidth get() = max(0f, ringRight - ringLeft)
    private val ringHeight get() = max(0f, ringBottom - ringTop)

    // The ring runs clockwise from the top-left corner: top, right, bottom, left.
    fun ringX(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> ringLeft + s
            s < w + h -> ringRight
            s < 2f * w + h -> ringRight - (s - w - h)
            else -> ringLeft
        }
    }

    fun ringY(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> ringTop
            s < w + h -> ringTop + (s - w)
            s < 2f * w + h -> ringBottom
            else -> ringBottom - (s - 2f * w - h)
        }
    }

    private fun normalX(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> 0f
            s < w + h -> -1f
            s < 2f * w + h -> 0f
            else -> 1f
        }
    }

    private fun normalY(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> 1f
            s < w + h -> 0f
            s < 2f * w + h -> -1f
            else -> 0f
        }
    }

    /** Ring parameter where the ray from the screen center toward ([x], [y]) meets the ring. */
    fun rayParameter(x: Float, y: Float): Float {
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val dx = x - centerX
        val dy = y - centerY
        val tx = when {
            dx > 0f -> (ringRight - centerX) / dx
            dx < 0f -> (ringLeft - centerX) / dx
            else -> Float.POSITIVE_INFINITY
        }
        val ty = when {
            dy > 0f -> (ringBottom - centerY) / dy
            dy < 0f -> (ringTop - centerY) / dy
            else -> Float.POSITIVE_INFINITY
        }
        val nearest = min(tx, ty)
        val t = if (nearest.isFinite()) nearest else 0f
        val hitX = (centerX + dx * t).coerceIn(ringLeft, ringRight)
        val hitY = (centerY + dy * t).coerceIn(ringTop, ringBottom)
        val w = ringWidth
        val h = ringHeight
        return when {
            tx <= ty && dx > 0f -> w + (hitY - ringTop)
            tx <= ty && dx < 0f -> 2f * w + h + (ringBottom - hitY)
            dy < 0f -> hitX - ringLeft
            else -> w + h + (ringRight - hitX)
        }
    }

    private companion object {
        const val MAX_SAMPLES = 1_024

        /**
         * Direction error, in degrees, that a marker pushed a full short side inward costs: markers
         * prefer the screen edge unless a deeper spot (below the phone's top row, above a bottom
         * cluster) points clearly better.
         */
        const val DEPTH_COST_DEGREES = 30f
    }
}

/** Directions as the edge-marker planner counts them: degrees clockwise from the right, in [0, 360). */
internal object EdgeMarkerAngles {
    const val DEGREES = CanvasMath.RAD_TO_DEG

    const val FULL_TURN = 360f

    /** Direction of ([dx], [dy]) in degrees, clockwise from the right (screen y points down), in [0, 360). */
    fun angleOf(dx: Float, dy: Float): Float = (atan2(dy, dx) * DEGREES + FULL_TURN) % FULL_TURN

    /** Degrees between directions [a] and [b] (each in [0, 360)), the short way round. */
    fun apartDegrees(a: Float, b: Float): Float {
        val along = abs(a - b)
        return min(along, FULL_TURN - along)
    }
}

/** Edge markers sit this far from the screen edge (icon center) before any inward push. */
internal const val EDGE_INSET_DP = 24f
internal const val EDGE_ICON_DP = 18f
internal const val EDGE_TEXT_GAP_DP = 8f
internal const val EDGE_HALF_HEIGHT_DP = 11f

/**
 * Least clear space between two edge markers drawn in one frame (icon and distance boxes): half as
 * wide again as the icon-to-distance gap, so two distances that face each other read as two markers.
 */
internal const val EDGE_MARKER_SPACING_DP = 1.5f * EDGE_TEXT_GAP_DP
private const val EDGE_SAMPLE_DP = 6f

/**
 * How far inward an edge marker may move to clear the HUD, past any HUD band along that edge: a
 * tenth of the short side.
 */
internal fun maxEdgeDepth(width: Float, height: Float): Float = 0.1f * min(width, height)
