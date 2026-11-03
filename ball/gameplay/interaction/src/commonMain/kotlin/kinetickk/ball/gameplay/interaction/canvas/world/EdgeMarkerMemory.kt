// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.DEGREES
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.FULL_TURN
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.angleOf
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.apartDegrees
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

/**
 * What [EdgeMarkerPlanner] remembers of the markers it placed in the last frames: each marker's
 * kind, target direction, ring sample and position in its run last frame, its centers over the last
 * [RETURN_FADE_FRAMES] frames, and the spots it strayed from in that time, which it does not go
 * back to yet. Allocation-free.
 */
internal class EdgeMarkerHistory {
    // The markers placed last frame: kind, target direction, ring sample and position in the run.
    var count = 0
        private set
    private val icon = arrayOfNulls<EdgeMarkerIcon>(EdgeMarkerBatch.CAPACITY)
    val target = FloatArray(EdgeMarkerBatch.CAPACITY)
    val sample = IntArray(EdgeMarkerBatch.CAPACITY)
    val rank = IntArray(EdgeMarkerBatch.CAPACITY)

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
    private var returnRadius = 0f

    /** Drops the memory after the ring was rebuilt for a viewport [density] (the ring samples change meaning). */
    fun restart(density: Float) {
        count = 0
        returnRadius = RETURN_RADIUS_DP * density.coerceAtLeast(1f)
    }

    /** Drops the memory of last frame's markers: the next ones are placed afresh. */
    fun forget() {
        count = 0
    }

    /**
     * Last frame's marker of kind [icon] whose target's direction was within [TRACK_DEGREES] of
     * [direction] (the nearest such marker), as an index into the last-frame arrays; -1 when none.
     */
    fun rememberedMarker(icon: EdgeMarkerIcon?, direction: Float): Int {
        var found = -1
        var nearest = TRACK_DEGREES
        for (index in 0 until count) {
            if (this.icon[index] != icon) continue
            val apart = apartDegrees(target[index], direction)
            if (apart <= nearest) {
                nearest = apart
                found = index
            }
        }
        return found
    }

    /**
     * Copies last frame's trail of marker [last] (-1: none) as [marker]'s, and collects the spots
     * [marker] held two or more frames ago, has strayed more than [RETURN_LEAVE_PX] from since and
     * is not back at: going back to one of them soon (within a third of how far it strayed, at
     * most [RETURN_RADIUS_DP]) would flicker.
     */
    fun collectLeftSpots(marker: Int, last: Int) {
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
     * How much worse a spot centered at ([x], [y]) scores for [marker] for going back to a spot it
     * strayed from a few frames ago: [RETURN_DEGREES] within [RETURN_FRAMES] frames, fading out by
     * [RETURN_FADE_FRAMES] (with or without the rest of its memory, so neither the kept nor the
     * memoryless arrangement sends it back).
     */
    fun revisit(marker: Int, x: Float, y: Float): Float {
        val base = marker * RETURN_FADE_FRAMES
        var cost = 0f
        for (spot in base until base + leftCount[marker]) {
            val dx = x - leftX[spot]
            val dy = y - leftY[spot]
            if (dx * dx + dy * dy > leftRadius[spot] * leftRadius[spot]) continue
            val age = leftAge[spot]
            val fade = if (age <= RETURN_FRAMES) 1f else (RETURN_FADE_FRAMES - age).toFloat() / (RETURN_FADE_FRAMES - RETURN_FRAMES)
            cost = max(cost, RETURN_DEGREES * fade)
        }
        return cost
    }

    /**
     * Records this frame's marker [index] (kind [icon], target direction [target], ring sample
     * [sample], run position [rank], center ([x], [y])) as last frame's for the next frame; its
     * trail gets this frame's center (the oldest dropped when it is full). [finish] closes the frame.
     */
    fun record(index: Int, icon: EdgeMarkerIcon?, target: Float, sample: Int, rank: Int, x: Float, y: Float) {
        this.icon[index] = icon
        this.target[index] = target
        this.sample[index] = sample
        this.rank[index] = rank
        val base = index * RETURN_FADE_FRAMES
        val keep = min(trailLength[index], RETURN_FADE_FRAMES - 1)
        val skip = trailLength[index] - keep
        for (step in 0 until keep) {
            lastTrailX[base + step] = trailX[base + skip + step]
            lastTrailY[base + step] = trailY[base + skip + step]
        }
        lastTrailX[base + keep] = x
        lastTrailY[base + keep] = y
        lastTrailLength[index] = keep + 1
    }

    /** Closes a frame of [count] [record]ed markers. */
    fun finish(count: Int) {
        this.count = count
    }

    private companion object {
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

        /** A marker is last frame's marker of its kind when its target's direction moved at most this many degrees. */
        const val TRACK_DEGREES = 10f
    }
}

/**
 * This frame's markers for [EdgeMarkerPlanner]: each one's target direction, the marker of last
 * frame's ([history]) it continues and the ring sample that one held, how that memory scores a
 * spot ([scoreAt]), the run order around the screen center (from the middle of the widest gap
 * between targets, [origin]) and the candidate run orders a frame's arrangements are tried in.
 * Two continuing markers keep last frame's order until their targets have passed each other by
 * [ORDER_HYSTERESIS_DEGREES]. Allocation-free.
 */
internal class EdgeMarkerRun(private val ring: EdgeMarkerRing, private val history: EdgeMarkerHistory) {
    // Per marker of this frame: its target's direction (degrees and unit vector), last frame's
    // marker it continues, the sample that one held (-1: none) and how that sample points for the
    // target now (infinite: none); [memory] / [memoryError] are what scoring uses (the held ones,
    // or none for the memoryless arrangement).
    val targetAngle = FloatArray(EdgeMarkerBatch.CAPACITY)
    val targetUx = FloatArray(EdgeMarkerBatch.CAPACITY)
    val targetUy = FloatArray(EdgeMarkerBatch.CAPACITY)
    val continues = IntArray(EdgeMarkerBatch.CAPACITY)
    private val held = IntArray(EdgeMarkerBatch.CAPACITY)
    private val heldError = FloatArray(EdgeMarkerBatch.CAPACITY)
    val memory = IntArray(EdgeMarkerBatch.CAPACITY)
    private val memoryError = FloatArray(EdgeMarkerBatch.CAPACITY)

    // Per marker, whether another target lies within [ORDER_HYSTERESIS_DEGREES] of its own: such
    // markers are interchangeable, so they neither count toward nor get reordered by stranding.
    val coincident = BooleanArray(EdgeMarkerBatch.CAPACITY)

    // The frame's markers in run order (around the screen center, from the widest gap between
    // targets), and the run in its targets' order, then as last frame kept it.
    val runOrder = IntArray(EdgeMarkerBatch.CAPACITY)
    private val baseOrder = IntArray(EdgeMarkerBatch.CAPACITY)

    // The run orders a frame's arrangements are tried in ([MAX_ORDERS] of [EdgeMarkerBatch.CAPACITY]).
    private val orders = IntArray(MAX_ORDERS * EdgeMarkerBatch.CAPACITY)
    var orderCount = 0
        private set
    var origin = 0f
        private set

    /**
     * Loads [batch]'s targets: each one's direction, last frame's marker it continues and the
     * sample that one held, the spots it strayed from, and [runOrder] by index. Returns whether
     * any marker continues one of last frame's.
     */
    fun load(batch: EdgeMarkerBatch): Boolean {
        var remembered = false
        for (index in 0 until batch.count) {
            val angle = angleOf(batch.targetX[index] - ring.width * 0.5f, batch.targetY[index] - ring.height * 0.5f)
            val last = history.rememberedMarker(batch.icon[index], angle)
            continues[index] = last
            val sample = if (last >= 0) history.sample[last] else -1
            held[index] = sample
            if (sample >= 0) remembered = true
            targetAngle[index] = angle
            targetUx[index] = cos(angle / DEGREES)
            targetUy[index] = sin(angle / DEGREES)
            heldError[index] = if (sample >= 0) ring.errorAt(sample, targetUx[index], targetUy[index]) else Float.POSITIVE_INFINITY
            runOrder[index] = index
            history.collectLeftSpots(index, last)
        }
        return remembered
    }

    /** Scores this frame's first [count] markers with their memory (the samples they held). */
    fun remember(count: Int) {
        for (index in 0 until count) {
            memory[index] = held[index]
            memoryError[index] = heldError[index]
        }
    }

    /** Scores this frame's markers without their memory (the memoryless arrangement). */
    fun forgetForNow(count: Int) {
        for (index in 0 until count) {
            memory[index] = -1
            memoryError[index] = Float.POSITIVE_INFINITY
        }
    }

    /** Marks, in [coincident], the markers whose target lies within [ORDER_HYSTERESIS_DEGREES] of another marker's. */
    fun markCoincident(count: Int) {
        for (index in 0 until count) {
            coincident[index] = false
            for (other in 0 until count) {
                if (other == index) continue
                if (apartDegrees(targetAngle[index], targetAngle[other]) < ORDER_HYSTERESIS_DEGREES) coincident[index] = true
            }
        }
    }

    /** Direction error and depth cost of the [coincident] markers on ring samples [samplesOf] (an arrangement's). */
    fun coincidentError(count: Int, samplesOf: IntArray): Float {
        var total = 0f
        for (index in 0 until count) {
            if (coincident[index] && samplesOf[index] >= 0) total += ring.errorAt(samplesOf[index], targetUx[index], targetUy[index])
        }
        return total
    }

    /** Score of ring sample [index] (a clear one) for [marker]'s target direction ([ux], [uy]), as [EdgeMarkerPlanner] scores it. */
    fun scoreAt(marker: Int, index: Int, ux: Float, uy: Float): Float {
        val error = ring.errorAt(index, ux, uy)
        return error - continuity(marker, index) + retreat(marker, error) + revisit(marker, index)
    }

    /** How much worse ring sample [index] scores for [marker] for going back to a spot it strayed from ([EdgeMarkerHistory.revisit]). */
    fun revisit(marker: Int, index: Int): Float =
        if (marker == NO_MARKER) 0f else history.revisit(marker, ring.markerX[index], ring.markerY[index])

    /** Degrees between ring sample [index]'s direction and marker [marker]'s target's. */
    fun pointingError(index: Int, marker: Int): Float = ring.pointingError(index, targetUx[marker], targetUy[marker])

    /**
     * How much worse a spot scoring [error] (direction error and depth cost) scores for [marker]
     * for pointing further from its target than the marker's last spot does.
     */
    fun retreat(marker: Int, error: Float): Float {
        if (marker == NO_MARKER) return 0f
        val worse = error - memoryError[marker]
        if (worse <= RETREAT_TOLERANCE_DEGREES) return 0f
        return min(RETREAT_DEGREES, worse)
    }

    /** How much better ring sample [index] scores for [marker] for being at or near its last spot. */
    fun continuity(marker: Int, index: Int): Float {
        if (marker == NO_MARKER) return 0f
        val last = memory[marker]
        if (last < 0) return 0f
        val apart = ring.ringApart(index, last)
        return when {
            apart == 0 -> STICK_NEAR_DEGREES + STICK_EXACT_DEGREES
            apart <= STICK_WINDOW_SAMPLES -> STICK_NEAR_DEGREES
            else -> 0f
        }
    }

    /**
     * Finds the run of the first [count] markers (whose [runOrder] holds them by index): sorted by
     * their targets' directions, starting after the widest gap between neighbouring targets, so a
     * run across the right edge stays one run; directions count from that gap's middle
     * ([origin]). The gap from the last direction round to the first is a full turn when they all
     * coincide. Then collects the run orders to try ([collectOrders]).
     */
    fun findRun(count: Int) {
        // By the targets' directions first, to find the run (at most a few markers).
        sortRun(count, keepLastOrder = false)
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
    }

    /** Loads candidate run order [order] into [runOrder]. */
    fun loadOrder(order: Int, count: Int) {
        for (position in 0 until count) runOrder[position] = orders[order * EdgeMarkerBatch.CAPACITY + position]
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
    fun approaching(count: Int): Boolean {
        for (position in 0 until count - 1) {
            val first = orders[position]
            val second = orders[position + 1]
            val lastFirst = continues[first]
            val lastSecond = continues[second]
            if (lastFirst < 0 || lastSecond < 0) continue
            val now = apartDegrees(targetAngle[first], targetAngle[second])
            if (now >= APPROACH_DEGREES) continue
            val crossed = fromOriginDegrees(targetAngle[second]) < fromOriginDegrees(targetAngle[first])
            if (crossed || now < apartDegrees(history.target[lastFirst], history.target[lastSecond])) return true
        }
        return false
    }

    /**
     * Whether two neighbours in the kept order, both continuing last frame's markers, are moving
     * so as to trade places (their targets passing each other by [ORDER_HYSTERESIS_DEGREES])
     * within [TRADE_SOON_FRAMES] frames: a stranding switch now would be followed by their
     * trading places, so it waits.
     */
    fun tradingSoon(count: Int): Boolean {
        for (position in 0 until count - 1) {
            val first = orders[position]
            val second = orders[position + 1]
            val lastFirst = continues[first]
            val lastSecond = continues[second]
            if (lastFirst < 0 || lastSecond < 0) continue
            val ahead = fromOriginDegrees(targetAngle[second]) - fromOriginDegrees(targetAngle[first])
            val aheadBefore = fromOriginDegrees(history.target[lastSecond]) - fromOriginDegrees(history.target[lastFirst])
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
        return if (remembered && abs(ahead) < ORDER_HYSTERESIS_DEGREES) history.rank[lastA] < history.rank[lastB] else ahead > 0f
    }

    /** Direction [degrees] (in [0, 360)), counted from [origin]. */
    fun fromOriginDegrees(degrees: Float): Float {
        val counted = degrees - origin
        return if (counted < 0f) counted + FULL_TURN else counted
    }

    companion object {
        /** The marker of a lookup with no continuity ([EdgeMarkerPlanner.position]). */
        const val NO_MARKER = -1

        /**
         * Continuity bonus, in degrees of direction error, for a spot within [STICK_WINDOW_SAMPLES]
         * of the marker's last one: another arrangement must point this much better per moved marker
         * to win. Larger than the error one ring sample adds (under 2 degrees on a phone's long side).
         */
        private const val STICK_NEAR_DEGREES = 3f

        /**
         * Extra bonus for the marker's very last spot, so a target shaken or drifting across the
         * midpoint between two samples does not dither its marker; well under the error one sample
         * adds at a desktop corner (about 0.4 degrees), so markers still follow their targets.
         */
        private const val STICK_EXACT_DEGREES = 0.25f

        /**
         * A spot pointing further from the marker's target than the marker's last spot does costs
         * that much more again, up to this many degrees: a marker makes room for a neighbour only
         * when that is clearly better, not for a hair of direction, and yields gradually as the
         * neighbour's need grows instead of holding out and then giving way at once.
         */
        private const val RETREAT_DEGREES = 10f

        /** Direction error differences below this (degrees) are rounding, not a retreat. */
        private const val RETREAT_TOLERANCE_DEGREES = 0.001f

        /** How many ring samples either way of its last spot a marker may follow its target per frame. */
        const val STICK_WINDOW_SAMPLES = 3

        /** Run orders a frame tries: the targets' (as last frame kept them), and one per new marker next to a nearly coincident target. */
        private const val MAX_ORDERS = EdgeMarkerBatch.CAPACITY

        /**
         * Two markers continuing last frame's whose targets are this close (degrees), and closing
         * in or already past each other, are about to trade places: while they are, the markers
         * only hold or follow their spots, so they are not rearranged just before trading places.
         */
        private const val APPROACH_DEGREES = 5f

        /** How many frames ahead a stranding switch looks for two markers about to trade places ([tradingSoon]). */
        private const val TRADE_SOON_FRAMES = 12

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
