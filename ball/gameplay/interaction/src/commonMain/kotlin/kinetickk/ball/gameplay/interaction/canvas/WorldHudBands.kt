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
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
 * already taken, plus [EDGE_MARKER_SPACING_DP], are kept out for the rest, and the markers keep
 * their targets' order around the screen center. Placement remembers the previous frames, so a
 * marker (and a crowded run as a whole) moves only when its target really turned or another spot
 * is clearly better, two markers trade places only once their targets have clearly passed each
 * other, and a marker does not go back to a spot it just left: no flickering between near-equal
 * arrangements. That stickiness is bounded by a memoryless placement of the same targets, which
 * the kept arrangement gives way to when it stays clearly worse. Targets are given in the
 * unshaken view (markers are HUD overlays), so screen shake cannot move a marker. The table of
 * clear positions is rebuilt, and the memory dropped, when the keep-out, viewport or marker size
 * changes; the memory is also dropped by a frame without markers ([forget]).
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

    // Boxes of the markers [place] has placed so far this frame, and its ordering scratch: the
    // frame's markers in run order (around the screen center, from the widest gap between targets).
    private val placed = FloatArray(EdgeMarkerBatch.CAPACITY * 4)
    private var placedCount = 0
    private val preferred = IntArray(EdgeMarkerBatch.CAPACITY)
    private val runOrder = IntArray(EdgeMarkerBatch.CAPACITY)
    private val baseOrder = IntArray(EdgeMarkerBatch.CAPACITY) // the run in its targets' order, then as last frame kept it
    private val arrangedX = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedY = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedSample = IntArray(EdgeMarkerBatch.CAPACITY)
    private var arrangedError = 0f
    private var bestScore = 0f

    // The run orders a frame's arrangements are tried in ([MAX_ORDERS] of [EdgeMarkerBatch.CAPACITY]).
    private val orders = IntArray(MAX_ORDERS * EdgeMarkerBatch.CAPACITY)
    private var orderCount = 0

    // The best arrangement without memory (each marker's center, ring sample, run position) and
    // with it; how many frames in a row the one with memory pointed clearly worse.
    private val freshX = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val freshY = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val freshSample = IntArray(EdgeMarkerBatch.CAPACITY)
    private val freshRank = IntArray(EdgeMarkerBatch.CAPACITY)
    private val chosenSample = IntArray(EdgeMarkerBatch.CAPACITY)
    private val chosenRank = IntArray(EdgeMarkerBatch.CAPACITY)
    private var strandedFrames = 0
    private var strandedCheckIn = 0

    // Whether this frame's kept arrangement only holds or follows last frame's ([approaching]).
    private var frozen = false

    // Per ring sample, its direction from the screen center (degrees clockwise from the right).
    private val sampleAngle = FloatArray(MAX_SAMPLES)

    // Per marker of this frame: its target's direction, last frame's marker it continues, the
    // sample that one held (-1: none) and how that sample points for the target now (infinite:
    // none); [memory] / [memoryError] are what scoring uses (the held ones, or none for the
    // memoryless arrangement); per run position, the placed marker's direction counted from
    // [origin] (-1: not yet placed).
    private val targetAngle = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val targetUx = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val targetUy = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val continues = IntArray(EdgeMarkerBatch.CAPACITY)
    private val held = IntArray(EdgeMarkerBatch.CAPACITY)
    private val heldError = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val memory = IntArray(EdgeMarkerBatch.CAPACITY)
    private val memoryError = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val runAngle = FloatArray(EdgeMarkerBatch.CAPACITY)
    private var origin = 0f

    // The markers placed last frame: kind, target direction, ring sample and position in the run.
    private var lastCount = 0
    private val lastIcon = arrayOfNulls<EdgeMarkerIcon>(EdgeMarkerBatch.CAPACITY)
    private val lastTarget = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val lastSample = IntArray(EdgeMarkerBatch.CAPACITY)
    private val lastRank = IntArray(EdgeMarkerBatch.CAPACITY)

    // Each marker's centers over the last [RETURN_FADE_FRAMES] frames (oldest first), last frame's
    // and this frame's; and the spots it strayed from in that time, which it does not go back to
    // yet: each with how close counts as going back, and how many frames ago it was held.
    private val lastTrailX = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val lastTrailY = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val lastTrailLength = IntArray(EdgeMarkerBatch.CAPACITY)
    private val trailX = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val trailY = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val trailLength = IntArray(EdgeMarkerBatch.CAPACITY)
    private val leftX = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val leftY = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val leftRadius = FloatArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val leftAge = IntArray(EdgeMarkerBatch.CAPACITY * RETURN_FADE_FRAMES)
    private val leftCount = IntArray(EdgeMarkerBatch.CAPACITY)

    // Per marker, whether another target lies within [ORDER_HYSTERESIS_DEGREES] of its own: such
    // markers are interchangeable, so they neither count toward nor get reordered by stranding.
    private val coincident = BooleanArray(EdgeMarkerBatch.CAPACITY)
    private var returnRadius = 0f
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
        lastCount = 0 // the ring samples change meaning
        this.width = width
        this.height = height
        val unit = density.coerceAtLeast(1f)
        iconHalf = EDGE_ICON_DP * 0.5f * unit
        gap = EDGE_TEXT_GAP_DP * unit
        this.textWidth = textWidth
        halfHeight = EDGE_HALF_HEIGHT_DP * unit
        markerSpacing = EDGE_MARKER_SPACING_DP * unit
        returnRadius = RETURN_RADIUS_DP * unit
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
        return this
    }

    /**
     * Marker position for an off-screen [target]: of the clear positions, the one with the smallest
     * angle between its direction and the target's direction from the screen center, plus its
     * [DEPTH_COST_DEGREES] depth cost (so where the ray from the center meets the edge ring when
     * that spot is clear). Allocation-free and memoryless. [place] positions a frame's markers together.
     */
    fun position(target: Offset): Offset {
        val dx = target.x - width * 0.5f
        val dy = target.y - height * 0.5f
        val length = sqrt(dx * dx + dy * dy)
        val best = if (length > 0f) {
            bestIndex(NO_MARKER, dx / length, dy / length, avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0, span = samples)
        } else {
            -1
        }
        if (best >= 0) return Offset(markerX[best], markerY[best])
        val s0 = rayParameter(target.x, target.y)
        return Offset(ringX(s0), ringY(s0))
    }

    /**
     * Places every marker of [batch] (the frame's off-screen targets, in the unshaken view) at
     * once, writing each center to [EdgeMarkerBatch.markerX] / [EdgeMarkerBatch.markerY]. Each
     * marker takes the position [position] would choose, unless its box (icon and distance) would
     * come within [EDGE_MARKER_SPACING_DP] of a marker placed before it: then the best clear
     * position that keeps that spacing. The markers keep their targets' order around the screen
     * center (the run starts after the widest gap between targets) and are placed one after
     * another: from the middle of the run outward, from one end or from the other, and, when that
     * crowds a marker, from the middle with the middle marker stepped a little along the edge. The
     * arrangement pointing closest to the targets overall wins, so a crowded run shifts as a whole
     * where the edge has room.
     *
     * Continuity with last frame's markers (same kind, target within [TRACK_DEGREES]): two such
     * markers keep last frame's order until their targets have passed each other by
     * [ORDER_HYSTERESIS_DEGREES], so targets pointing almost the same way cannot swap their
     * markers back and forth (a new marker next to such a target may take either side of it);
     * a spot within [STICK_WINDOW_SAMPLES] of the marker's last one scores [STICK_NEAR_DEGREES]
     * better, and the last spot itself [STICK_EXACT_DEGREES] more; a spot pointing further from
     * the target than the last one scores that much worse again (up to [RETREAT_DEGREES]); going
     * back near a spot the marker strayed from in the last [RETURN_FRAMES] frames scores
     * [RETURN_DEGREES] worse (fading out by [RETURN_FADE_FRAMES]); the last arrangement, as it
     * was and followed a few samples either way, is tried first, and alone while two markers are
     * about to trade places ([APPROACH_DEGREES]). So a moving target's marker follows it sample
     * by sample, and a crowded run changes its arrangement only when another one is clearly
     * better, instead of flipping back and forth between near-equal ones.
     *
     * The same targets are also placed without memory; when the kept arrangement stays clearly
     * worse than that ([STRANDED_DEGREES], [STRANDED_POINTING_DEGREES], [STRANDED_FRAMES]), the
     * memoryless one replaces it, keeping the markers' order and the spots they just left. Markers
     * whose targets coincide within the order margin do not count toward that (they are
     * interchangeable), and it waits while two markers are about to trade places
     * ([TRADE_SOON_FRAMES]), so it is not followed by a swap. Allocation-free.
     */
    fun place(batch: EdgeMarkerBatch) {
        val count = batch.count
        if (count == 0) {
            forget()
            return
        }
        var remembered = false
        for (index in 0 until count) {
            val angle = angleOf(batch.targetX[index] - width * 0.5f, batch.targetY[index] - height * 0.5f)
            val last = rememberedMarker(batch.icon[index], angle)
            continues[index] = last
            val sample = if (last >= 0) lastSample[last] else -1
            held[index] = sample
            if (sample >= 0) remembered = true
            targetAngle[index] = angle
            targetUx[index] = cos(angle / DEGREES)
            targetUy[index] = sin(angle / DEGREES)
            heldError[index] = if (sample >= 0) errorAt(sample, targetUx[index], targetUy[index]) else Float.POSITIVE_INFINITY
            runOrder[index] = index
            collectLeftSpots(index, last)
        }
        // By the targets' directions first, to find the run (at most a few markers).
        sortRun(count, keepLastOrder = false)
        // Directions go round: start the run after the widest gap between neighbouring targets, so a
        // run across the right edge stays one run; directions count from that gap's middle. The gap
        // from the last direction round to the first is a full turn when they all coincide.
        var start = 0
        var widest = -1f
        for (index in 0 until count) {
            val previous = targetAngle[runOrder[(index + count - 1) % count]]
            val gap = targetAngle[runOrder[index]] - previous + if (index == 0) FULL_TURN else 0f
            if (gap > widest) {
                widest = gap
                start = index
            }
        }
        origin = (targetAngle[runOrder[start]] - widest * 0.5f + FULL_TURN) % FULL_TURN
        for (position in 0 until count) baseOrder[position] = runOrder[(start + position) % count]
        collectOrders(count)
        var useFresh = !remembered
        if (remembered) {
            for (index in 0 until count) {
                memory[index] = held[index]
                memoryError[index] = heldError[index]
            }
            frozen = approaching(count)
            val keptError = arrangeBest(batch, continuedPatterns = true, batch.markerX, batch.markerY, chosenSample, chosenRank)
            frozen = false
            // Stickiness is bounded: an arrangement kept for continuity that points clearly worse
            // than the memoryless one, overall or at one marker, for a while gives way to it. The
            // memoryless one is worked out only when the kept one could be that much worse, and
            // then every [STRANDED_CHECK_FRAMES] frames. Markers whose targets coincide with
            // another's are left out (which of them is off is a coin toss), and nothing gives way
            // while two markers are about to trade places.
            markCoincident(count)
            val keptOwn = keptError - coincidentError(count, chosenSample)
            var suspect = false
            for (index in 0 until count) {
                if (coincident[index] || chosenSample[index] < 0) continue
                if (pointingError(chosenSample[index], index) > STRANDED_POINTING_DEGREES) suspect = true
            }
            if (!suspect) suspect = keptOwn > errorAlone(count) + STRANDED_DEGREES
            if (!suspect) {
                strandedFrames = 0
                strandedCheckIn = 0
            } else if (strandedCheckIn > 0 || tradingSoon(count)) {
                if (strandedCheckIn > 0) strandedCheckIn--
            } else {
                strandedCheckIn = STRANDED_CHECK_FRAMES - 1
                forgetForNow(count)
                val freshError = arrangeBest(batch, continuedPatterns = false, freshX, freshY, freshSample, freshRank)
                var stranded = keptOwn > freshError - coincidentError(count, freshSample) + STRANDED_DEGREES
                for (index in 0 until count) {
                    if (coincident[index] || chosenSample[index] < 0 || freshSample[index] < 0) continue
                    if (pointingError(chosenSample[index], index) > STRANDED_POINTING_DEGREES &&
                        pointingError(freshSample[index], index) <= STRANDED_FRESH_DEGREES
                    ) stranded = true
                }
                strandedFrames = if (stranded) strandedFrames + STRANDED_CHECK_FRAMES else 0
                useFresh = strandedFrames > STRANDED_FRAMES
            }
        } else {
            forgetForNow(count)
            arrangeBest(batch, continuedPatterns = false, freshX, freshY, freshSample, freshRank)
        }
        if (useFresh) {
            strandedFrames = 0
            strandedCheckIn = 0
            for (index in 0 until count) {
                batch.markerX[index] = freshX[index]
                batch.markerY[index] = freshY[index]
                chosenSample[index] = freshSample[index]
                chosenRank[index] = freshRank[index]
            }
        }
        for (index in 0 until count) {
            lastIcon[index] = batch.icon[index]
            lastTarget[index] = targetAngle[index]
            lastSample[index] = chosenSample[index]
            lastRank[index] = chosenRank[index]
            // The trail, with this frame's center added (the oldest dropped when it is full).
            val base = index * RETURN_FADE_FRAMES
            val keep = min(trailLength[index], RETURN_FADE_FRAMES - 1)
            val skip = trailLength[index] - keep
            for (step in 0 until keep) {
                lastTrailX[base + step] = trailX[base + skip + step]
                lastTrailY[base + step] = trailY[base + skip + step]
            }
            lastTrailX[base + keep] = batch.markerX[index]
            lastTrailY[base + keep] = batch.markerY[index]
            lastTrailLength[index] = keep + 1
        }
        lastCount = count
    }

    /**
     * Copies last frame's trail of marker [last] (-1: none) as [marker]'s, and collects the spots
     * [marker] held two or more frames ago, has strayed more than [RETURN_LEAVE_PX] from since and
     * is not back at: going back to one of them soon (within a third of how far it strayed, at
     * most [RETURN_RADIUS_DP]) would flicker.
     */
    private fun collectLeftSpots(marker: Int, last: Int) {
        val base = marker * RETURN_FADE_FRAMES
        val length = if (last >= 0) lastTrailLength[last] else 0
        for (step in 0 until length) {
            trailX[base + step] = lastTrailX[last * RETURN_FADE_FRAMES + step]
            trailY[base + step] = lastTrailY[last * RETURN_FADE_FRAMES + step]
        }
        trailLength[marker] = length
        leftCount[marker] = 0
        if (length < 2) return
        val nowX = trailX[base + length - 1]
        val nowY = trailY[base + length - 1]
        for (step in 0 until length - 1) {
            val x = trailX[base + step]
            val y = trailY[base + step]
            var strayed = 0f
            for (later in step + 1 until length) {
                val dx = trailX[base + later] - x
                val dy = trailY[base + later] - y
                strayed = max(strayed, sqrt(dx * dx + dy * dy))
            }
            if (strayed <= RETURN_LEAVE_PX) continue
            val radius = min(returnRadius, strayed / 3f)
            val nowDx = nowX - x
            val nowDy = nowY - y
            if (nowDx * nowDx + nowDy * nowDy <= radius * radius) continue // it is back there already
            val spot = base + leftCount[marker]
            leftX[spot] = x
            leftY[spot] = y
            leftRadius[spot] = radius
            leftAge[spot] = length - step
            leftCount[marker]++
        }
    }

    /**
     * The arrangement of [batch]'s markers scoring lowest over the [orderCount] candidate run
     * orders and the placement patterns ([continuedPatterns]: also last frame's arrangement, held
     * and tracked), written as each marker's center, ring sample and position in its run to
     * [outX] / [outY] / [outSample] / [outRank]. Returns how far it points from the targets
     * overall (direction errors and depth costs, plus a large penalty per unspaced marker).
     */
    private fun arrangeBest(
        batch: EdgeMarkerBatch,
        continuedPatterns: Boolean,
        outX: FloatArray,
        outY: FloatArray,
        outSample: IntArray,
        outRank: IntArray,
    ): Float {
        val count = batch.count
        // Each marker's own best spot: no arrangement scores lower.
        var alone = 0f
        for (index in 0 until count) {
            val best = bestIndex(index, targetUx[index], targetUy[index], avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0,
                span = samples)
            alone += if (best >= 0) bestScore else UNSPACED_PENALTY
            preferred[index] = if (best >= 0) best else ringSample(rayParameter(batch.targetX[index], batch.targetY[index]))
        }
        var bestTotal = Float.POSITIVE_INFINITY
        var bestError = Float.POSITIVE_INFINITY
        // With markers from last frame: its arrangement as it was, then followed a few samples (from
        // the start, from the end), in every order first; then the patterns (middle out, from the
        // start, from the end), then the middle-out order with the middle marker shifted by -1, +1,
        // -2, +2, ... samples. While two markers' targets close in on each other ([approaching]),
        // only the continued arrangements are tried, if any fits.
        val continued = if (continuedPatterns && count > 1) CONTINUED_PATTERNS else 0
        val candidates = continued + if (count == 1) 1 else PLACEMENT_PATTERNS + 2 * MIDDLE_SHIFT_SAMPLES
        sweeps@ for (sweep in 0 until 2) {
            if (sweep == 1 && frozen && bestTotal < Float.POSITIVE_INFINITY) break
            for (order in 0 until orderCount) {
                for (position in 0 until count) runOrder[position] = orders[order * EdgeMarkerBatch.CAPACITY + position]
                for (candidate in (if (sweep == 0) 0 else continued) until (if (sweep == 0) continued else candidates)) {
                    val fresh = candidate - continued
                    val pattern = if (fresh < 0) HOLD + candidate else if (fresh < PLACEMENT_PATTERNS) fresh else MIDDLE_OUT
                    val step = fresh - PLACEMENT_PATTERNS
                    val shift = if (step < 0) 0 else if (step % 2 == 0) -(step / 2 + 1) else step / 2 + 1
                    val total = arrange(batch, pattern, shift)
                    if (total < bestTotal) {
                        bestTotal = total
                        bestError = arrangedError
                        for (index in 0 until count) {
                            outX[index] = arrangedX[index]
                            outY[index] = arrangedY[index]
                            outSample[index] = arrangedSample[index]
                        }
                        for (position in 0 until count) outRank[runOrder[position]] = position
                    }
                    if (bestTotal <= alone + 0.001f) break@sweeps // every marker has its own best spot
                }
            }
        }
        return bestError
    }

    /**
     * The run orders to try, into [orders]: the targets' order, where two markers continuing last
     * frame's keep last frame's order until their targets have passed each other by
     * [ORDER_HYSTERESIS_DEGREES]; then that order with each pair of neighbours swapped whose
     * targets lie within that margin and one of which is new, so a marker arriving next to another
     * may take the side that leaves the other in place.
     */
    private fun collectOrders(count: Int) {
        orderCount = 0
        for (position in 0 until count) runOrder[position] = baseOrder[position]
        sortRun(count, keepLastOrder = true)
        for (position in 0 until count) baseOrder[position] = runOrder[position]
        addOrder(count)
        for (position in 0 until count - 1) {
            val first = baseOrder[position]
            val second = baseOrder[position + 1]
            if (continues[first] >= 0 && continues[second] >= 0) continue // continuing markers keep their order
            if (abs(fromOriginDegrees(targetAngle[second]) - fromOriginDegrees(targetAngle[first])) >= ORDER_HYSTERESIS_DEGREES) continue
            for (index in 0 until count) runOrder[index] = baseOrder[index]
            runOrder[position] = second
            runOrder[position + 1] = first
            addOrder(count)
        }
    }

    /**
     * Whether two neighbours in the kept order (the first of [orders]), both continuing last
     * frame's markers, have targets within [APPROACH_DEGREES] of each other that are closing in
     * or already past each other (inside the order hysteresis): they are about to trade places,
     * and rearranging them now would only be undone when they do.
     */
    private fun approaching(count: Int): Boolean {
        for (position in 0 until count - 1) {
            val first = orders[position]
            val second = orders[position + 1]
            val lastFirst = continues[first]
            val lastSecond = continues[second]
            if (lastFirst < 0 || lastSecond < 0) continue
            val now = apartDegrees(targetAngle[first], targetAngle[second])
            if (now >= APPROACH_DEGREES) continue
            val crossed = fromOriginDegrees(targetAngle[second]) < fromOriginDegrees(targetAngle[first])
            if (crossed || now < apartDegrees(lastTarget[lastFirst], lastTarget[lastSecond])) return true
        }
        return false
    }

    /**
     * Whether two neighbours in the kept order, both continuing last frame's markers, are moving
     * so as to trade places (their targets passing each other by [ORDER_HYSTERESIS_DEGREES])
     * within [TRADE_SOON_FRAMES] frames: a stranding switch now would be followed by their
     * trading places, so it waits.
     */
    private fun tradingSoon(count: Int): Boolean {
        for (position in 0 until count - 1) {
            val first = orders[position]
            val second = orders[position + 1]
            val lastFirst = continues[first]
            val lastSecond = continues[second]
            if (lastFirst < 0 || lastSecond < 0) continue
            val ahead = fromOriginDegrees(targetAngle[second]) - fromOriginDegrees(targetAngle[first])
            val aheadBefore = fromOriginDegrees(lastTarget[lastSecond]) - fromOriginDegrees(lastTarget[lastFirst])
            if (ahead + (ahead - aheadBefore) * TRADE_SOON_FRAMES < -ORDER_HYSTERESIS_DEGREES) return true
        }
        return false
    }

    /** Adds [runOrder] to [orders] unless it is there already. */
    private fun addOrder(count: Int) {
        if (orderCount >= MAX_ORDERS) return
        for (order in 0 until orderCount) {
            var same = true
            for (position in 0 until count) if (orders[order * EdgeMarkerBatch.CAPACITY + position] != runOrder[position]) same = false
            if (same) return
        }
        for (position in 0 until count) orders[orderCount * EdgeMarkerBatch.CAPACITY + position] = runOrder[position]
        orderCount++
    }

    /** Scores this frame's markers without their memory (the memoryless arrangement). */
    private fun forgetForNow(count: Int) {
        for (index in 0 until count) {
            memory[index] = -1
            memoryError[index] = Float.POSITIVE_INFINITY
        }
    }

    /** Marks, in [coincident], the markers whose target lies within [ORDER_HYSTERESIS_DEGREES] of another marker's. */
    private fun markCoincident(count: Int) {
        for (index in 0 until count) {
            coincident[index] = false
            for (other in 0 until count) {
                if (other == index) continue
                if (apartDegrees(targetAngle[index], targetAngle[other]) < ORDER_HYSTERESIS_DEGREES) coincident[index] = true
            }
        }
    }

    /** Direction error and depth cost of the [coincident] markers on ring samples [samplesOf] (an arrangement's). */
    private fun coincidentError(count: Int, samplesOf: IntArray): Float {
        var total = 0f
        for (index in 0 until count) {
            if (coincident[index] && samplesOf[index] >= 0) total += errorAt(samplesOf[index], targetUx[index], targetUy[index])
        }
        return total
    }

    /**
     * The least the markers other than the [coincident] ones can point from their targets
     * overall, each on its own best clear spot (direction error and depth cost, with the unspaced
     * penalty for one that has none): no arrangement, memoryless or kept, points better.
     */
    private fun errorAlone(count: Int): Float {
        var total = 0f
        for (index in 0 until count) {
            if (coincident[index]) continue
            val best = bestIndex(NO_MARKER, targetUx[index], targetUy[index], avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0,
                span = samples)
            total += if (best >= 0) bestScore else UNSPACED_PENALTY
        }
        return total
    }

    /** Drops the memory of last frame's markers (a frame without any): the next ones are placed afresh. */
    fun forget() {
        lastCount = 0
        strandedFrames = 0
        strandedCheckIn = 0
    }

    /**
     * Insertion sort of the first [count] entries of [runOrder] by their targets' directions
     * ([keepLastOrder]: counted from [origin], with [comesBefore]'s memory of last frame's order).
     */
    private fun sortRun(count: Int, keepLastOrder: Boolean) {
        for (index in 1 until count) {
            val marker = runOrder[index]
            var slot = index
            while (slot > 0 && comesBefore(marker, runOrder[slot - 1], keepLastOrder)) {
                runOrder[slot] = runOrder[slot - 1]
                slot--
            }
            runOrder[slot] = marker
        }
    }

    /**
     * Whether marker [a] goes before marker [b] along the run: its target's direction is smaller.
     * With [keepLastOrder] (directions counted from [origin]), two markers that continue two of
     * last frame's markers keep those markers' order while their targets are within
     * [ORDER_HYSTERESIS_DEGREES] of each other, so they trade places only once the targets have
     * clearly passed each other.
     */
    private fun comesBefore(a: Int, b: Int, keepLastOrder: Boolean): Boolean {
        if (!keepLastOrder) return targetAngle[a] < targetAngle[b]
        val ahead = fromOriginDegrees(targetAngle[b]) - fromOriginDegrees(targetAngle[a])
        val lastA = continues[a]
        val lastB = continues[b]
        val remembered = lastA >= 0 && lastB >= 0 && lastA != lastB
        return if (remembered && abs(ahead) < ORDER_HYSTERESIS_DEGREES) lastRank[lastA] < lastRank[lastB] else ahead > 0f
    }

    /**
     * Last frame's marker of kind [icon] whose target's direction was within [TRACK_DEGREES] of
     * [direction] (the nearest such marker), as an index into the last-frame arrays; -1 when none.
     */
    private fun rememberedMarker(icon: EdgeMarkerIcon?, direction: Float): Int {
        var found = -1
        var nearest = TRACK_DEGREES
        for (index in 0 until lastCount) {
            if (lastIcon[index] != icon) continue
            val apart = apartDegrees(lastTarget[index], direction)
            if (apart <= nearest) {
                nearest = apart
                found = index
            }
        }
        return found
    }

    /**
     * Places [batch]'s markers in the order [pattern] picks along the run [runOrder] ([MIDDLE_OUT]:
     * middle first, then alternately one step toward each end; [FROM_START] / [TRACK_FROM_START]:
     * from the start; [FROM_END] / [TRACK_FROM_END]: from the end), into [arrangedX] /
     * [arrangedY] / [arrangedSample]. Each marker stays between its already placed neighbours in
     * the run. [HOLD] keeps every marker on its last spot; the tracking patterns look only within
     * [STICK_WINDOW_SAMPLES] of it. With a [shift], the first marker takes the clear ring sample
     * that many samples from its own best one. Returns the summed score (direction error and depth
     * cost, less continuity, plus retreat costs), with a large penalty per marker that found no
     * spaced spot; infinite when the shifted sample is not clear, or a held or tracked marker lost
     * its place.
     */
    private fun arrange(batch: EdgeMarkerBatch, pattern: Int, shift: Int): Float {
        val count = batch.count
        val middle = (count - 1) / 2
        placedCount = 0
        for (position in 0 until count) runAngle[position] = -1f
        var total = 0f
        arrangedError = 0f
        for (step in 0 until count) {
            val position = when (pattern) {
                MIDDLE_OUT -> {
                    val outward = if (step % 2 == 0) step / 2 else -(step + 1) / 2
                    (middle + outward + count) % count // an even run's last step wraps to its far end
                }
                FROM_START, HOLD, TRACK_FROM_START -> step
                else -> count - 1 - step
            }
            val marker = runOrder[position]
            val ux = targetUx[marker]
            val uy = targetUy[marker]
            // The nearest placed neighbours before and after it in the run bound its direction.
            var low = -1f
            var high = FULL_TURN
            for (other in 0 until count) {
                val angle = runAngle[other]
                if (angle < 0f) continue
                if (other < position) low = max(low, angle) else high = min(high, angle)
            }
            var best: Int
            if (step == 0 && shift != 0) {
                best = ((preferred[marker] + shift) % samples + samples) % samples
                if (depths[best].isNaN()) return Float.POSITIVE_INFINITY
                total += scoreAt(marker, best, ux, uy)
                arrangedError += errorAt(best, ux, uy)
            } else if (pattern == HOLD) {
                best = memory[marker]
                if (best < 0 || depths[best].isNaN()) return Float.POSITIVE_INFINITY
                val counted = fromOrigin(best)
                if (counted <= low || counted >= high || crowded(markerX[best], markerY[best])) return Float.POSITIVE_INFINITY
                total += scoreAt(marker, best, ux, uy)
                arrangedError += errorAt(best, ux, uy)
            } else {
                val last = memory[marker]
                val tracking = pattern >= TRACK_FROM_START && last >= 0
                best = if (tracking) {
                    bestIndex(marker, ux, uy, avoidPlaced = true, low = low, high = high, from = last - STICK_WINDOW_SAMPLES,
                        span = 2 * STICK_WINDOW_SAMPLES + 1)
                } else {
                    bestIndex(marker, ux, uy, avoidPlaced = true, low = low, high = high, from = 0, span = samples)
                }
                if (tracking && best < 0) return Float.POSITIVE_INFINITY // lost its place: a fresh pattern places it
                if (best >= 0) {
                    total += bestScore
                    arrangedError += errorAt(best, ux, uy)
                } else {
                    // No spaced spot: overlap rather than hide, in order where the ring allows.
                    best = bestIndex(marker, ux, uy, avoidPlaced = false, low = low, high = high, from = 0, span = samples)
                    if (best < 0) best = bestIndex(marker, ux, uy, avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0, span = samples)
                    total += UNSPACED_PENALTY
                    arrangedError += UNSPACED_PENALTY
                }
            }
            val x: Float
            val y: Float
            if (best >= 0) {
                x = markerX[best]
                y = markerY[best]
                runAngle[position] = fromOrigin(best)
            } else {
                val s0 = rayParameter(batch.targetX[marker], batch.targetY[marker])
                x = ringX(s0)
                y = ringY(s0)
            }
            arrangedX[marker] = x
            arrangedY[marker] = y
            arrangedSample[marker] = best
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
     * Of the [span] ring samples from [from] on, the clear one whose position points closest to the
     * target direction ([ux], [uy]) (a unit vector from the screen center; see [position]), less
     * [marker]'s continuity bonus and plus its retreat cost ([NO_MARKER]: neither); only samples
     * whose direction counted from [origin] lies strictly between [low] and [high] (degrees), and
     * with [avoidPlaced] only samples whose marker box keeps [markerSpacing] from every box placed
     * so far this frame. -1 when there is none. Leaves its score in [bestScore].
     */
    private fun bestIndex(
        marker: Int,
        ux: Float,
        uy: Float,
        avoidPlaced: Boolean,
        low: Float,
        high: Float,
        from: Int,
        span: Int,
    ): Int {
        bestScore = Float.POSITIVE_INFINITY
        if (samples == 0) return -1
        val first = (from % samples + samples) % samples
        val range = min(span, samples)
        val last = if (marker == NO_MARKER) -1 else memory[marker]
        var best = -1
        var score = Float.POSITIVE_INFINITY
        // The spots at and near the marker's last one first: their continuity bonus lowers the
        // score every other spot must beat.
        if (last >= 0) {
            for (k in -STICK_WINDOW_SAMPLES..STICK_WINDOW_SAMPLES) {
                val index = ((last + k) % samples + samples) % samples
                if ((index - first + samples) % samples >= range) continue
                val error = spotScore(index, ux, uy, avoidPlaced, low, high, -1f)
                val candidate = error - continuity(marker, index) + retreat(marker, error) + revisit(marker, index)
                if (candidate < score) {
                    score = candidate
                    best = index
                }
            }
        }
        // A position whose direction alone is already worse than the best score is skipped
        // without evaluating its angle.
        var cutoff = if (score < 180f) cos(score / DEGREES) else -1f
        for (k in 0 until range) {
            val index = (first + k) % samples
            if (last >= 0 && ringApart(index, last) <= STICK_WINDOW_SAMPLES) continue
            val error = spotScore(index, ux, uy, avoidPlaced, low, high, cutoff)
            if (error >= score) continue // its costs only add to it
            val candidate = error + retreat(marker, error) + revisit(marker, index)
            if (candidate < score) {
                score = candidate
                best = index
                cutoff = if (score < 180f) cos(score / DEGREES) else -1f
            }
        }
        bestScore = score
        return best
    }

    /**
     * Direction error plus depth cost of ring sample [index] for the target direction ([ux], [uy]);
     * infinite when the sample is not clear, its direction counted from [origin] is not strictly
     * between [low] and [high], it points no closer than [cutoff] (a cosine) or, with
     * [avoidPlaced], its box crowds a marker placed this frame.
     */
    private fun spotScore(index: Int, ux: Float, uy: Float, avoidPlaced: Boolean, low: Float, high: Float, cutoff: Float): Float {
        if (depths[index].isNaN()) return Float.POSITIVE_INFINITY
        val dot = directionX[index] * ux + directionY[index] * uy
        if (dot <= cutoff) return Float.POSITIVE_INFINITY
        val counted = fromOrigin(index)
        if (counted <= low || counted >= high) return Float.POSITIVE_INFINITY
        if (avoidPlaced && crowded(markerX[index], markerY[index])) return Float.POSITIVE_INFINITY
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
    }

    /** Score of ring sample [index] (a clear one) for [marker]'s target direction ([ux], [uy]), as [bestIndex] scores it. */
    private fun scoreAt(marker: Int, index: Int, ux: Float, uy: Float): Float {
        val error = errorAt(index, ux, uy)
        return error - continuity(marker, index) + retreat(marker, error) + revisit(marker, index)
    }

    /**
     * How much worse ring sample [index] scores for [marker] for going back to a spot it strayed
     * from a few frames ago: [RETURN_DEGREES] within [RETURN_FRAMES] frames, fading out by
     * [RETURN_FADE_FRAMES] (with or without the rest of its memory, so neither the kept nor the
     * memoryless arrangement sends it back).
     */
    private fun revisit(marker: Int, index: Int): Float {
        if (marker == NO_MARKER) return 0f
        val base = marker * RETURN_FADE_FRAMES
        var cost = 0f
        for (spot in base until base + leftCount[marker]) {
            val dx = markerX[index] - leftX[spot]
            val dy = markerY[index] - leftY[spot]
            if (dx * dx + dy * dy > leftRadius[spot] * leftRadius[spot]) continue
            val age = leftAge[spot]
            val fade = if (age <= RETURN_FRAMES) 1f else (RETURN_FADE_FRAMES - age).toFloat() / (RETURN_FADE_FRAMES - RETURN_FRAMES)
            cost = max(cost, RETURN_DEGREES * fade)
        }
        return cost
    }

    /** Degrees between ring sample [index]'s direction and marker [marker]'s target's. */
    private fun pointingError(index: Int, marker: Int): Float {
        val dot = directionX[index] * targetUx[marker] + directionY[index] * targetUy[marker]
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES
    }

    /** Direction error plus depth cost of ring sample [index] (a clear one) for the target direction ([ux], [uy]). */
    private fun errorAt(index: Int, ux: Float, uy: Float): Float {
        val dot = directionX[index] * ux + directionY[index] * uy
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES + depthCost[index]
    }

    /**
     * How much worse a spot scoring [error] (direction error and depth cost) scores for [marker]
     * for pointing further from its target than the marker's last spot does.
     */
    private fun retreat(marker: Int, error: Float): Float {
        if (marker == NO_MARKER) return 0f
        val worse = error - memoryError[marker]
        if (worse <= RETREAT_TOLERANCE_DEGREES) return 0f
        return min(RETREAT_DEGREES, worse)
    }

    /** How much better ring sample [index] scores for [marker] for being at or near its last spot. */
    private fun continuity(marker: Int, index: Int): Float {
        if (marker == NO_MARKER) return 0f
        val last = memory[marker]
        if (last < 0) return 0f
        val apart = ringApart(index, last)
        return when {
            apart == 0 -> STICK_NEAR_DEGREES + STICK_EXACT_DEGREES
            apart <= STICK_WINDOW_SAMPLES -> STICK_NEAR_DEGREES
            else -> 0f
        }
    }

    /** How many ring samples lie between samples [a] and [b], the short way round. */
    private fun ringApart(a: Int, b: Int): Int {
        val along = abs(a - b)
        return min(along, samples - along)
    }

    /** Direction of ring sample [index], counted from [origin] (the middle of the widest gap between this frame's targets). */
    private fun fromOrigin(index: Int): Float = fromOriginDegrees(sampleAngle[index])

    /** Direction [degrees] (in [0, 360)), counted from [origin]. */
    private fun fromOriginDegrees(degrees: Float): Float {
        val counted = degrees - origin
        return if (counted < 0f) counted + FULL_TURN else counted
    }

    /** Degrees between directions [a] and [b] (each in [0, 360)), the short way round. */
    private fun apartDegrees(a: Float, b: Float): Float {
        val along = abs(a - b)
        return min(along, FULL_TURN - along)
    }

    /** Direction of ([dx], [dy]) in degrees, clockwise from the right (screen y points down), in [0, 360). */
    private fun angleOf(dx: Float, dy: Float): Float = (atan2(dy, dx) * DEGREES + FULL_TURN) % FULL_TURN

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

    /** Width of a marker's distance text, as last [prepare]d (tests place markers with another planner). */
    val markerTextWidth: Float get() = textWidth

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

        /** Run orders [place] tries: the targets' (as last frame kept them), and one per new marker next to a nearly coincident target. */
        const val MAX_ORDERS = EdgeMarkerBatch.CAPACITY

        /**
         * The arrangement kept for continuity gives way to the memoryless one once, for more than
         * [STRANDED_FRAMES] frames in a row, it points [STRANDED_DEGREES] worse overall (direction
         * errors and depth costs summed over the markers), or one of its markers points more than
         * [STRANDED_POINTING_DEGREES] off its target while the memoryless one points it within
         * [STRANDED_FRESH_DEGREES]: continuity never strands a marker far from its target while
         * its neighbours could make room. While it might be, that is checked every
         * [STRANDED_CHECK_FRAMES] frames. Markers whose targets lie within
         * [ORDER_HYSTERESIS_DEGREES] of another's are left out of both measures.
         */
        const val STRANDED_DEGREES = 12f
        const val STRANDED_POINTING_DEGREES = 25f
        const val STRANDED_FRESH_DEGREES = 15f
        const val STRANDED_FRAMES = 20
        const val STRANDED_CHECK_FRAMES = 4

        /**
         * Two markers continuing last frame's whose targets are this close (degrees), and closing
         * in or already past each other, are about to trade places: while they are, the markers
         * only hold or follow their spots, so they are not rearranged just before trading places.
         */
        const val APPROACH_DEGREES = 5f

        /** How many frames ahead a stranding switch looks for two markers about to trade places ([tradingSoon]). */
        const val TRADE_SOON_FRAMES = 12

        /**
         * Cost, in degrees of direction error, of a spot a marker strayed from within the last
         * [RETURN_FRAMES] frames, fading out over the frames up to [RETURN_FADE_FRAMES]: once
         * pushed aside (or left by a neighbour), it does not snap back and forth, and no burst of
         * returns waits at the end of the window.
         */
        const val RETURN_DEGREES = 20f
        const val RETURN_FRAMES = 8
        const val RETURN_FADE_FRAMES = 16

        /**
         * A marker that moved more than [RETURN_LEAVE_PX] (px: a step that shows at any density)
         * from a spot left it; within a third of that, at most [RETURN_RADIUS_DP], is back there.
         */
        const val RETURN_LEAVE_PX = 12f
        const val RETURN_RADIUS_DP = 24f

        /**
         * Direction error, in degrees, that a marker pushed a full short side inward costs: markers
         * prefer the screen edge unless a deeper spot (below the phone's top row, above a bottom
         * cluster) points clearly better.
         */
        const val DEPTH_COST_DEGREES = 30f

        const val DEGREES = 57.29578f

        /** Placement orders [arrange] knows; [place] compares the first [PLACEMENT_PATTERNS], plus the tracking ones. */
        const val MIDDLE_OUT = 0
        const val FROM_START = 1
        const val FROM_END = 2
        const val HOLD = 3
        const val TRACK_FROM_START = 4
        const val TRACK_FROM_END = 5
        const val PLACEMENT_PATTERNS = 3
        const val CONTINUED_PATTERNS = 3

        /** How many ring samples (6 dp each) [place] steps the middle marker of a crowded run either way. */
        const val MIDDLE_SHIFT_SAMPLES = 8

        /** Score of a marker that found no spot keeping the spacing (worse than any direction error). */
        const val UNSPACED_PENALTY = 10_000f

        /** [bestIndex]'s marker for a lookup with no continuity ([position]). */
        const val NO_MARKER = -1

        /**
         * Continuity bonus, in degrees of direction error, for a spot within [STICK_WINDOW_SAMPLES]
         * of the marker's last one: another arrangement must point this much better per moved marker
         * to win. Larger than the error one ring sample adds (under 2 degrees on a phone's long side).
         */
        const val STICK_NEAR_DEGREES = 3f

        /**
         * Extra bonus for the marker's very last spot, so a target shaken or drifting across the
         * midpoint between two samples does not dither its marker; well under the error one sample
         * adds at a desktop corner (about 0.4 degrees), so markers still follow their targets.
         */
        const val STICK_EXACT_DEGREES = 0.25f

        /**
         * A spot pointing further from the marker's target than the marker's last spot does costs
         * that much more again, up to this many degrees: a marker makes room for a neighbour only
         * when that is clearly better, not for a hair of direction, and yields gradually as the
         * neighbour's need grows instead of holding out and then giving way at once.
         */
        const val RETREAT_DEGREES = 10f

        /** Direction error differences below this (degrees) are rounding, not a retreat. */
        const val RETREAT_TOLERANCE_DEGREES = 0.001f

        /** How many ring samples either way of its last spot a marker may follow its target per frame. */
        const val STICK_WINDOW_SAMPLES = 3

        /** A marker is last frame's marker of its kind when its target's direction moved at most this many degrees. */
        const val TRACK_DEGREES = 10f

        const val FULL_TURN = 360f

        /**
         * How far past each other (degrees, seen from the screen center) two targets must move
         * before their markers trade places: targets pointing almost the same way (the Core flying
         * along the line through them) cross and uncross by hairs, and their markers must not
         * swap with each hair. A new marker whose target is this close to another's may take
         * either side of it.
         */
        const val ORDER_HYSTERESIS_DEGREES = 1f
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

    /**
     * Adds an off-screen target at screen ([x], [y]) of the unshaken view (markers are HUD
     * overlays: screen shake must not move them), [worldDistance] from the Core.
     */
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
