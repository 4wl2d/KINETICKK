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
 * for sitting deeper inside the screen. A frame's markers are placed together ([place]): positions
 * already taken, plus [EDGE_MARKER_SPACING_DP], are kept out for the rest. The table of clear
 * positions is rebuilt only when the keep-out, viewport or marker size changes.
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
    private var sampleStep = 1f
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
    private var markerSpacing = 0f

    // Boxes of the markers [place] has placed so far this frame, and its ordering scratch.
    private val placed = FloatArray(EdgeMarkerBatch.CAPACITY * 4)
    private var placedCount = 0
    private val preferred = IntArray(EdgeMarkerBatch.CAPACITY)
    private val ringOrder = IntArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedX = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedY = FloatArray(EdgeMarkerBatch.CAPACITY)
    private var bestScore = 0f
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
            depthCost[index] = DEPTH_COST_DEGREES * depth / max(1f, min(width, height))
        }
        return this
    }

    /**
     * Marker position for an off-screen [target]: of the clear positions, the one with the smallest
     * angle between its direction and the target's direction from the screen center, plus its
     * [DEPTH_COST_DEGREES] depth cost (so where the ray from the center meets the edge ring when
     * that spot is clear). Allocation-free. [place] positions a frame's markers together.
     */
    fun position(target: Offset): Offset {
        val best = bestIndex(target.x, target.y, avoidPlaced = false)
        if (best >= 0) return Offset(markerX[best], markerY[best])
        val s0 = rayParameter(target.x, target.y)
        return Offset(ringX(s0), ringY(s0))
    }

    /**
     * Places every marker of [batch] (the frame's off-screen targets) at once, writing each center
     * to [EdgeMarkerBatch.markerX] / [EdgeMarkerBatch.markerY]. Each marker takes the position
     * [position] would choose, unless its box (icon and distance) would come within
     * [EDGE_MARKER_SPACING_DP] of a marker placed before it: then the best clear position that
     * keeps that spacing. Markers are placed one after another along the edge (in the ring order of
     * their own best positions): from the middle of the run outward, from one end or from the
     * other, and, when that crowds a marker, from the middle with the middle marker stepped a
     * little along the edge. The arrangement pointing closest to the targets overall wins, so a
     * crowded run shifts as a whole where the edge has room and neighbours keep their order.
     * Allocation-free.
     */
    fun place(batch: EdgeMarkerBatch) {
        val count = batch.count
        if (count == 0) return
        // Each marker's own best ring sample (the ring runs clockwise from the top-left corner).
        var alone = 0f
        for (index in 0 until count) {
            val best = bestIndex(batch.targetX[index], batch.targetY[index], avoidPlaced = false)
            alone += if (best >= 0) bestScore else UNSPACED_PENALTY
            preferred[index] = if (best >= 0) best else ringSample(rayParameter(batch.targetX[index], batch.targetY[index]))
            ringOrder[index] = index
        }
        // Insertion sort by ring position (at most a few markers).
        for (index in 1 until count) {
            val marker = ringOrder[index]
            var slot = index
            while (slot > 0 && preferred[ringOrder[slot - 1]] > preferred[marker]) {
                ringOrder[slot] = ringOrder[slot - 1]
                slot--
            }
            ringOrder[slot] = marker
        }
        // The ring is closed: start the order after the widest gap between neighbours, so a run
        // across the top-left corner stays one run.
        var start = 0
        var widest = -1
        for (index in 0 until count) {
            val previous = preferred[ringOrder[(index + count - 1) % count]]
            val gap = (preferred[ringOrder[index]] - previous + samples) % samples.coerceAtLeast(1)
            if (gap > widest) {
                widest = gap
                start = index
            }
        }
        var bestTotal = Float.POSITIVE_INFINITY
        val candidates = if (count == 1) 1 else PLACEMENT_PATTERNS + 2 * MIDDLE_SHIFT_SAMPLES
        for (candidate in 0 until candidates) {
            // Patterns first (middle out, from the start, from the end), then the middle-out order
            // with the middle marker shifted by -1, +1, -2, +2, ... ring samples.
            val pattern = if (candidate < PLACEMENT_PATTERNS) candidate else 0
            val step = candidate - PLACEMENT_PATTERNS
            val shift = if (step < 0) 0 else if (step % 2 == 0) -(step / 2 + 1) else step / 2 + 1
            val total = arrange(batch, start, pattern, shift)
            if (total < bestTotal) {
                bestTotal = total
                for (index in 0 until count) {
                    batch.markerX[index] = arrangedX[index]
                    batch.markerY[index] = arrangedY[index]
                }
            }
            if (bestTotal <= alone + 0.001f) break // every marker has its own best spot
        }
    }

    /**
     * Places [batch]'s markers in the order [pattern] picks along the run that starts at
     * [ringOrder] index [start] (0: middle first, then alternately one step toward each end; 1:
     * from the start; 2: from the end), into [arrangedX] / [arrangedY]. With a [shift], the first
     * marker takes the clear ring sample that many samples from its own best one. Returns the
     * summed score (direction error and depth cost), with a large penalty per marker that found no
     * spaced spot; infinite when the shifted sample is not clear.
     */
    private fun arrange(batch: EdgeMarkerBatch, start: Int, pattern: Int, shift: Int): Float {
        val count = batch.count
        val middle = (count - 1) / 2
        placedCount = 0
        var total = 0f
        for (step in 0 until count) {
            val position = when (pattern) {
                0 -> middle + if (step % 2 == 0) step / 2 else -(step + 1) / 2
                1 -> step
                else -> count - 1 - step
            }
            val marker = ringOrder[(start + position + count) % count]
            val targetX = batch.targetX[marker]
            val targetY = batch.targetY[marker]
            var best: Int
            if (step == 0 && shift != 0) {
                best = (preferred[marker] + shift + samples) % samples
                if (depths[best].isNaN()) return Float.POSITIVE_INFINITY
                total += scoreAt(best, targetX, targetY)
            } else {
                best = bestIndex(targetX, targetY, avoidPlaced = true)
            }
            if (best >= 0) {
                if (step > 0 || shift == 0) total += bestScore
            } else {
                best = bestIndex(targetX, targetY, avoidPlaced = false) // no spaced spot: overlap rather than hide
                total += UNSPACED_PENALTY
            }
            val x: Float
            val y: Float
            if (best >= 0) {
                x = markerX[best]
                y = markerY[best]
            } else {
                val s0 = rayParameter(targetX, targetY)
                x = ringX(s0)
                y = ringY(s0)
            }
            arrangedX[marker] = x
            arrangedY[marker] = y
            val base = placedCount * 4
            placed[base] = markerLeft(x)
            placed[base + 1] = y - halfHeight
            placed[base + 2] = markerRight(x)
            placed[base + 3] = y + halfHeight
            placedCount++
        }
        return total
    }

    /**
     * Ring sample whose position points closest to the target at ([targetX], [targetY]) (see
     * [position]); with [avoidPlaced], only samples whose marker box keeps [markerSpacing] from every box
     * placed so far this frame. -1 when there is none. Leaves its score in [bestScore].
     */
    private fun bestIndex(targetX: Float, targetY: Float, avoidPlaced: Boolean): Int {
        val dx = targetX - width * 0.5f
        val dy = targetY - height * 0.5f
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0f) return -1
        val ux = dx / length
        val uy = dy / length
        var best = -1
        var score = Float.POSITIVE_INFINITY
        // A position whose direction alone is already worse than the best score is skipped
        // without evaluating its angle.
        var cutoff = -1f
        for (index in 0 until samples) {
            if (depths[index].isNaN()) continue
            val dot = directionX[index] * ux + directionY[index] * uy
            if (dot <= cutoff) continue
            if (avoidPlaced && crowded(markerX[index], markerY[index])) continue
            val candidate = acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
            if (candidate < score) {
                score = candidate
                best = index
                cutoff = if (score < 180f) cos(score / DEGREES) else -1f
            }
        }
        bestScore = score
        return best
    }

    /** Score of ring sample [index] (a clear one) for the target at ([targetX], [targetY]), as [bestIndex] scores it. */
    private fun scoreAt(index: Int, targetX: Float, targetY: Float): Float {
        val dx = targetX - width * 0.5f
        val dy = targetY - height * 0.5f
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val dot = directionX[index] * dx / length + directionY[index] * dy / length
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
    }

    /** Whether a marker centered at ([x], [y]) comes within [markerSpacing] of a marker placed this frame. */
    private fun crowded(x: Float, y: Float): Boolean {
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

    /** The ring sample at or before ring parameter [s]. */
    private fun ringSample(s: Float): Int = (s / sampleStep).toInt().coerceIn(0, max(0, samples - 1))

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

        /**
         * Direction error, in degrees, that a marker pushed a full short side inward costs: markers
         * prefer the screen edge unless a deeper spot (below the phone's top row, above a bottom
         * cluster) points clearly better.
         */
        const val DEPTH_COST_DEGREES = 30f

        const val DEGREES = 57.29578f

        /** Placement orders [place] compares: middle out, from the start, from the end. */
        const val PLACEMENT_PATTERNS = 3

        /** How many ring samples (6 dp each) [place] steps the middle marker of a crowded run either way. */
        const val MIDDLE_SHIFT_SAMPLES = 8

        /** Score of a marker that found no spot keeping the spacing (worse than any direction error). */
        const val UNSPACED_PENALTY = 10_000f
    }
}

/**
 * The off-screen targets of one frame (points of interest, the totem), collected before any is
 * drawn so [EdgeMarkerPlanner.place] can keep their markers apart. Fixed arrays; draw-thread
 * confined. The game offers at most two points at once plus the totem, well under [CAPACITY].
 */
internal class EdgeMarkerBatch {
    var count = 0
        private set
    val targetX = FloatArray(CAPACITY)
    val targetY = FloatArray(CAPACITY)
    val distance = FloatArray(CAPACITY)
    val icon = arrayOfNulls<EdgeMarkerIcon>(CAPACITY)

    /** Marker centers written by [EdgeMarkerPlanner.place]. */
    val markerX = FloatArray(CAPACITY)
    val markerY = FloatArray(CAPACITY)

    fun clear(): EdgeMarkerBatch {
        count = 0
        return this
    }

    /** Adds an off-screen target at screen ([x], [y]), [worldDistance] from the Core. */
    fun add(x: Float, y: Float, worldDistance: Float, kind: EdgeMarkerIcon) {
        if (count >= CAPACITY) return
        targetX[count] = x
        targetY[count] = y
        distance[count] = worldDistance
        icon[count] = kind
        count++
    }

    companion object {
        const val CAPACITY = 8
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
