// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Screen regions the running HUD occupies, in px, so world labels and edge markers stay clear of
 * it. The top-center column (clock, elite / Architect block), the chain, the trial panel and the
 * running controls come from the HUD's own geometry ([HudTopGeometry], [HudFrame],
 * [HudTrialPanelLayout], [forEachRunningControlBounds]) at the current text size, so the regions
 * follow the HUD; the remaining clusters use their board reserves. Recomputed only when the
 * viewport, text size, trial or boss state changes; draw-thread confined.
 */
internal class WorldHudKeepOut {
    var count = 0
        private set

    /** Incremented whenever the regions are recomputed. */
    var revision = 0
        private set
    private val rects = FloatArray(MAX_RECTS * 4)
    private val trialPanel = HudTrialPanelLayout()
    private val frame = HudFrame()
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextScale = Float.NaN
    private var keyTrial = false
    private var keyBoss = false

    fun update(width: Float, height: Float, density: Float, textScale: Float, trialActive: Boolean, bossPresent: Boolean): WorldHudKeepOut {
        if (width == keyWidth && height == keyHeight && density == keyDensity && textScale == keyTextScale &&
            trialActive == keyTrial && bossPresent == keyBoss
        ) return this
        keyWidth = width
        keyHeight = height
        keyDensity = density
        keyTextScale = textScale
        keyTrial = trialActive
        keyBoss = bossPresent
        revision++
        count = 0
        val scale = density.coerceAtLeast(1f)
        val frame = frame.update(width, height, scale)
        val mode = frame.mode
        val margin = frame.margin
        val unit = frame.unit
        val pad = 8f * unit
        val centerX = width * 0.5f
        var controlsLeft = Float.POSITIVE_INFINITY
        var controlsTop = Float.POSITIVE_INFINITY
        var headerBottom = 0f
        forEachRunningControlBounds(width, height, scale) { target, left, top, _, bottom ->
            if (target == RunningControlTarget.DASH || target == RunningControlTarget.BRAKE) {
                controlsLeft = min(controlsLeft, left)
                controlsTop = min(controlsTop, top)
            } else {
                headerBottom = max(headerBottom, bottom) // pause and performance in the top row
            }
        }
        val clockBottom = HudTopGeometry.clockTop(frame) + HudTopGeometry.clockHeight(frame)
        when (mode) {
            GameplayLayoutMode.REGULAR -> {
                // Level badge (a display numeral, 38 board px) and its Data bar; chips; clock; pause.
                val badgeBottom = 26f * unit + 38f * unit + 14f * unit
                add(0f, 0f, width, max(max(badgeBottom, clockBottom), max(60f * unit, headerBottom)) + pad)
                val chainTop = frame.chainTop
                val chainBottom = chainTop + 60f * frame.textFactor * COND_LINE_HEIGHT_EM * scale + 10f * unit
                add(width - margin - 200f * unit, chainTop - pad, width, chainBottom + pad)
                add(0f, frame.bottomClustersTop, margin + 360f * unit, height) // integrity, shield, dash, heat
                add(centerX - 220f * unit, frame.bottomClustersTop, centerX + 220f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_LANDSCAPE -> {
                // Badge, matter, clock, chain (a display numeral with its bar), performance and pause.
                val chainBottom = 30f * unit + 26f * COND_LINE_HEIGHT_EM * scale * 0.5f + 5f * unit
                add(0f, 0f, width, max(max(clockBottom, chainBottom), headerBottom) + 4f * unit)
                // Relics (with stacked synergy brackets) and the weapon slot with its level under it.
                val loadoutBottom = 62f * unit + 34f * unit + 4f * unit + 9f * scale * hudUiScale(textScale)
                add(width - margin - 180f * unit, 0f, width, loadoutBottom + pad)
                add(0f, frame.bottomClustersTop, margin + 230f * unit, height) // integrity cluster
                add(centerX - 150f * unit, frame.bottomClustersTop, centerX + 140f * unit, height) // speed
            }
            GameplayLayoutMode.COMPACT_PORTRAIT -> {
                // Status inset, badge, clock, chips and chain: the rows above the reserved boss row.
                add(0f, 0f, width, HudTopGeometry.bossTop(frame))
                add(0f, frame.bottomClustersTop, width, height) // loadout, integrity, speed, touch buttons
            }
        }
        if (bossPresent) {
            // The elite / Architect block under the clock (its own row on portrait phones), with the
            // name at the current text size; whichever boss it is, both shapes are covered.
            val half = max(HudTopGeometry.bossBarWidth(frame, architect = true), HudTopGeometry.bossBarWidth(frame, architect = false)) * 0.5f
            val bottom = max(HudTopGeometry.bossBottom(frame, true, textScale), HudTopGeometry.bossBottom(frame, false, textScale))
            add(max(0f, centerX - half - pad), 0f, min(width, centerX + half + pad), bottom + pad)
        }
        if (trialActive) {
            val panel = trialPanel.update(width, height, scale, textScale)
            add(panel.left - pad, panel.top - pad, panel.right + pad, panel.bottom + pad)
        }
        if (controlsLeft.isFinite()) add(controlsLeft - pad, controlsTop - pad, width, height)
        return this
    }

    private fun add(left: Float, top: Float, right: Float, bottom: Float) {
        if (count >= MAX_RECTS) return
        val base = count * 4
        rects[base] = left
        rects[base + 1] = top
        rects[base + 2] = right
        rects[base + 3] = bottom
        count++
    }

    /** Whether the box overlaps any HUD region. */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (index in 0 until count) {
            val base = index * 4
            if (left < rects[base + 2] && right > rects[base] && top < rects[base + 3] && bottom > rects[base + 1]) return true
        }
        return false
    }

    /** The regions as rects (tests). */
    fun rects(): List<Rect> = List(count) { Rect(rects[it * 4], rects[it * 4 + 1], rects[it * 4 + 2], rects[it * 4 + 3]) }

    private companion object {
        const val MAX_RECTS = 12
    }
}

/**
 * Places off-screen markers at the screen edge (`HUD-Elite.png`): on a ring inset from the edges,
 * pushed inward just past a HUD band lying along that edge (the phone's top row, a boss bar, the
 * bottom clusters) and then at most a tenth of the short side, so a marker never sits in the field
 * or on the HUD. The position is the marker's only direction cue, so of the clear positions the
 * one whose direction from the screen center is closest to the target's wins, with a small cost
 * for sitting deeper inside the screen. The table of clear positions is rebuilt only when the
 * keep-out, viewport or marker size changes.
 */
internal class EdgeMarkerPlanner {
    private val depths = FloatArray(MAX_SAMPLES)

    // Clear marker centers and their unit directions from the screen center, per ring sample.
    private val markerX = FloatArray(MAX_SAMPLES)
    private val markerY = FloatArray(MAX_SAMPLES)
    private val directionX = FloatArray(MAX_SAMPLES)
    private val directionY = FloatArray(MAX_SAMPLES)
    private val depthCost = FloatArray(MAX_SAMPLES)
    private var samples = 0
    private var spacing = 1f
    private var ringLeft = 0f
    private var ringTop = 0f
    private var ringRight = 0f
    private var ringBottom = 0f
    private var width = 0f
    private var height = 0f
    private var iconHalf = 0f
    private var gap = 0f
    private var textWidth = 0f
    private var halfHeight = 0f
    private var keepOut: WorldHudKeepOut? = null
    private var keyRevision = -1
    private var keyWidth = Float.NaN
    private var keyHeight = Float.NaN
    private var keyDensity = Float.NaN
    private var keyTextWidth = Float.NaN

    /** Rebuilds the clear-position table when [keepOut], the viewport or the marker width changed. */
    fun prepare(keepOut: WorldHudKeepOut, width: Float, height: Float, density: Float, textWidth: Float): EdgeMarkerPlanner {
        if (this.keepOut === keepOut && keyRevision == keepOut.revision && keyWidth == width &&
            keyHeight == height && keyDensity == density && keyTextWidth == textWidth
        ) return this
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
        val inset = EDGE_INSET_DP * unit
        ringLeft = inset
        ringTop = inset
        ringRight = width - inset
        ringBottom = height - inset
        val ringWidth = max(0f, ringRight - ringLeft)
        val ringHeight = max(0f, ringBottom - ringTop)
        val perimeter = 2f * (ringWidth + ringHeight)
        spacing = max(EDGE_SAMPLE_DP * unit, perimeter / MAX_SAMPLES)
        samples = min(MAX_SAMPLES, (perimeter / spacing).toInt().coerceAtLeast(1))
        val maxDepth = maxEdgeDepth(width, height)
        val step = 3f * unit
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val bands = keepOut.rects() // rebuilt only when the regions change
        for (index in 0 until samples) {
            val s = index * spacing
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
            depthCost[index] = DEPTH_COST_DEGREES * depth / max(1f, min(width, height))
        }
        return this
    }

    /**
     * Marker position for an off-screen [target]: of the clear positions, the one with the smallest
     * angle between its direction and the target's direction from the screen center, plus its
     * [DEPTH_COST_DEGREES] depth cost (so where the ray from the center meets the edge ring when
     * that spot is clear). Allocation-free.
     */
    fun position(target: Offset): Offset {
        val dx = target.x - width * 0.5f
        val dy = target.y - height * 0.5f
        val length = sqrt(dx * dx + dy * dy)
        var best = -1
        if (length > 0f) {
            val ux = dx / length
            val uy = dy / length
            var bestScore = Float.POSITIVE_INFINITY
            // A position whose direction alone is already worse than the best score is skipped
            // without evaluating its angle.
            var cutoff = -1f
            for (index in 0 until samples) {
                if (depths[index].isNaN()) continue
                val dot = directionX[index] * ux + directionY[index] * uy
                if (dot <= cutoff) continue
                val score = acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
                if (score < bestScore) {
                    bestScore = score
                    best = index
                    cutoff = if (bestScore < 180f) cos(bestScore / DEGREES) else -1f
                }
            }
        }
        if (best >= 0) return Offset(markerX[best], markerY[best])
        val s0 = rayParameter(target.x, target.y)
        return Offset(ringX(s0), ringY(s0))
    }

    /**
     * How far past the ring position at ([px], [py]) (inward normal [nx], [ny]) a marker must move to
     * clear the HUD band lying along that edge: the regions flatter along the edge than deep that
     * overlap the marker's footprint and touch the edge, or touch such a region (the phone's top
     * row and a boss bar below it, a bottom cluster). Zero when no band lies there.
     */
    private fun edgeBandDepth(bands: List<Rect>, px: Float, py: Float, nx: Float, ny: Float): Float {
        val left = markerLeft(px)
        val right = markerRight(px)
        // Covered distance from the screen edge, grown while another region touches the band.
        var covered = 0f
        var grown = true
        var rounds = 0
        while (grown && rounds++ < bands.size) {
            grown = false
            for (index in bands.indices) {
                val band = bands[index]
                val horizontal = ny != 0f
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
                if (near <= covered + BAND_TOUCH && far > covered) {
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
    fun markerHalfHeight(): Float = halfHeight

    private val ringWidth get() = max(0f, ringRight - ringLeft)
    private val ringHeight get() = max(0f, ringBottom - ringTop)

    // The ring runs clockwise from the top-left corner: top, right, bottom, left.
    private fun ringX(s: Float): Float {
        val w = ringWidth
        val h = ringHeight
        return when {
            s < w -> ringLeft + s
            s < w + h -> ringRight
            s < 2f * w + h -> ringRight - (s - w - h)
            else -> ringLeft
        }
    }

    private fun ringY(s: Float): Float {
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
    private fun rayParameter(x: Float, y: Float): Float {
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

        /** A region within this many px of a screen edge lies along it. */
        const val BAND_TOUCH = 1f

        /**
         * Direction error, in degrees, that a marker pushed a full short side inward costs: markers
         * prefer the screen edge unless a deeper spot (below the phone's top row, above a bottom
         * cluster) points clearly better.
         */
        const val DEPTH_COST_DEGREES = 30f

        const val DEGREES = 57.29578f
    }
}

/** Edge markers sit this far from the screen edge (icon center) before any inward push. */
internal const val EDGE_INSET_DP = 24f
internal const val EDGE_ICON_DP = 18f
internal const val EDGE_TEXT_GAP_DP = 8f
internal const val EDGE_HALF_HEIGHT_DP = 11f
private const val EDGE_SAMPLE_DP = 6f

/**
 * How far inward an edge marker may move to clear the HUD, past any HUD band along that edge: a
 * tenth of the short side.
 */
internal fun maxEdgeDepth(width: Float, height: Float): Float = 0.1f * min(width, height)
