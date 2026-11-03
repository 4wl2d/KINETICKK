// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.DEGREES
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerAngles.FULL_TURN
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerRun.Companion.NO_MARKER
import kinetickk.ball.gameplay.interaction.canvas.world.EdgeMarkerRun.Companion.STICK_WINDOW_SAMPLES

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
    // The clear positions ([EdgeMarkerRing]), last frames' markers ([EdgeMarkerHistory]) and this
    // frame's markers with their memory and run orders ([EdgeMarkerRun]).
    private val ring = EdgeMarkerRing()
    private val history = EdgeMarkerHistory()
    private val run = EdgeMarkerRun(ring, history)

    // [place]'s arrangement scratch: each marker's best spot alone, the arrangement just tried
    // (centers, ring samples, how far it points overall) and [bestIndex]'s last score.
    private val preferred = IntArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedX = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedY = FloatArray(EdgeMarkerBatch.CAPACITY)
    private val arrangedSample = IntArray(EdgeMarkerBatch.CAPACITY)
    private var arrangedError = 0f
    private var bestScore = 0f

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

    // Per run position, the placed marker's direction counted from the run's origin (-1: not yet placed).
    private val runAngle = FloatArray(EdgeMarkerBatch.CAPACITY)

    /** Rebuilds the clear-position table when [keepOut], the viewport or the marker width changed. */
    fun prepare(keepOut: WorldHudKeepOut, width: Float, height: Float, density: Float, textWidth: Float): EdgeMarkerPlanner {
        if (ring.rebuild(keepOut, width, height, density, textWidth)) history.restart(density) // the ring samples change meaning
        return this
    }

    /**
     * Marker position for an off-screen [target]: of the clear positions, the one with the smallest
     * angle between its direction and the target's direction from the screen center, plus its
     * [DEPTH_COST_DEGREES] depth cost (so where the ray from the center meets the edge ring when
     * that spot is clear). Allocation-free and memoryless. [place] positions a frame's markers together.
     */
    fun position(target: Offset): Offset {
        val dx = target.x - ring.width * 0.5f
        val dy = target.y - ring.height * 0.5f
        val length = sqrt(dx * dx + dy * dy)
        val best = if (length > 0f) {
            bestIndex(NO_MARKER, dx / length, dy / length, avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0, span = ring.samples)
        } else {
            -1
        }
        if (best >= 0) return Offset(ring.markerX[best], ring.markerY[best])
        val s0 = ring.rayParameter(target.x, target.y)
        return Offset(ring.ringX(s0), ring.ringY(s0))
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
        val remembered = run.load(batch)
        run.findRun(count)
        var useFresh = !remembered
        if (remembered) {
            run.remember(count)
            frozen = run.approaching(count)
            val keptError = arrangeBest(batch, continuedPatterns = true, batch.markerX, batch.markerY, chosenSample, chosenRank)
            frozen = false
            useFresh = strandedByMemory(batch, keptError)
        } else {
            run.forgetForNow(count)
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
            history.record(
                index, batch.icon[index], run.targetAngle[index], chosenSample[index], chosenRank[index],
                batch.markerX[index], batch.markerY[index],
            )
        }
        history.finish(count)
    }

    /**
     * Whether the memoryless arrangement replaces the one kept for continuity (pointing
     * [keptError] from the targets) this frame. Stickiness is bounded: an arrangement kept for
     * continuity that points clearly worse than the memoryless one, overall or at one marker, for
     * a while gives way to it. The memoryless one is worked out only when the kept one could be
     * that much worse, and then every [STRANDED_CHECK_FRAMES] frames. Markers whose targets
     * coincide with another's are left out (which of them is off is a coin toss), and nothing
     * gives way while two markers are about to trade places.
     */
    private fun strandedByMemory(batch: EdgeMarkerBatch, keptError: Float): Boolean {
        val count = batch.count
        run.markCoincident(count)
        val keptOwn = keptError - run.coincidentError(count, chosenSample)
        var suspect = false
        for (index in 0 until count) {
            if (run.coincident[index] || chosenSample[index] < 0) continue
            if (run.pointingError(chosenSample[index], index) > STRANDED_POINTING_DEGREES) suspect = true
        }
        if (!suspect) suspect = keptOwn > errorAlone(count) + STRANDED_DEGREES
        if (!suspect) {
            strandedFrames = 0
            strandedCheckIn = 0
            return false
        }
        if (strandedCheckIn > 0 || run.tradingSoon(count)) {
            if (strandedCheckIn > 0) strandedCheckIn--
            return false
        }
        strandedCheckIn = STRANDED_CHECK_FRAMES - 1
        run.forgetForNow(count)
        val freshError = arrangeBest(batch, continuedPatterns = false, freshX, freshY, freshSample, freshRank)
        var stranded = keptOwn > freshError - run.coincidentError(count, freshSample) + STRANDED_DEGREES
        for (index in 0 until count) {
            if (run.coincident[index] || chosenSample[index] < 0 || freshSample[index] < 0) continue
            if (run.pointingError(chosenSample[index], index) > STRANDED_POINTING_DEGREES &&
                run.pointingError(freshSample[index], index) <= STRANDED_FRESH_DEGREES
            ) stranded = true
        }
        strandedFrames = if (stranded) strandedFrames + STRANDED_CHECK_FRAMES else 0
        return strandedFrames > STRANDED_FRAMES
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
            val best = bestIndex(index, run.targetUx[index], run.targetUy[index], avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0,
                span = ring.samples)
            alone += if (best >= 0) bestScore else UNSPACED_PENALTY
            preferred[index] = if (best >= 0) best else ring.ringSample(ring.rayParameter(batch.targetX[index], batch.targetY[index]))
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
            for (order in 0 until run.orderCount) {
                run.loadOrder(order, count)
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
                        for (position in 0 until count) outRank[run.runOrder[position]] = position
                    }
                    if (bestTotal <= alone + 0.001f) break@sweeps // every marker has its own best spot
                }
            }
        }
        return bestError
    }

    /**
     * The least the markers other than the [coincident] ones can point from their targets
     * overall, each on its own best clear spot (direction error and depth cost, with the unspaced
     * penalty for one that has none): no arrangement, memoryless or kept, points better.
     */
    private fun errorAlone(count: Int): Float {
        var total = 0f
        for (index in 0 until count) {
            if (run.coincident[index]) continue
            val best = bestIndex(NO_MARKER, run.targetUx[index], run.targetUy[index], avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0,
                span = ring.samples)
            total += if (best >= 0) bestScore else UNSPACED_PENALTY
        }
        return total
    }

    /** Drops the memory of last frame's markers (a frame without any): the next ones are placed afresh. */
    fun forget() {
        history.forget()
        strandedFrames = 0
        strandedCheckIn = 0
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
        ring.clearTaken()
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
            val marker = run.runOrder[position]
            val ux = run.targetUx[marker]
            val uy = run.targetUy[marker]
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
                best = ((preferred[marker] + shift) % ring.samples + ring.samples) % ring.samples
                if (ring.depths[best].isNaN()) return Float.POSITIVE_INFINITY
                total += run.scoreAt(marker, best, ux, uy)
                arrangedError += ring.errorAt(best, ux, uy)
            } else if (pattern == HOLD) {
                best = run.memory[marker]
                if (best < 0 || ring.depths[best].isNaN()) return Float.POSITIVE_INFINITY
                val counted = fromOrigin(best)
                if (counted <= low || counted >= high || ring.crowded(ring.markerX[best], ring.markerY[best])) return Float.POSITIVE_INFINITY
                total += run.scoreAt(marker, best, ux, uy)
                arrangedError += ring.errorAt(best, ux, uy)
            } else {
                val last = run.memory[marker]
                val tracking = pattern >= TRACK_FROM_START && last >= 0
                best = if (tracking) {
                    bestIndex(marker, ux, uy, avoidPlaced = true, low = low, high = high, from = last - STICK_WINDOW_SAMPLES,
                        span = 2 * STICK_WINDOW_SAMPLES + 1)
                } else {
                    bestIndex(marker, ux, uy, avoidPlaced = true, low = low, high = high, from = 0, span = ring.samples)
                }
                if (tracking && best < 0) return Float.POSITIVE_INFINITY // lost its place: a fresh pattern places it
                if (best >= 0) {
                    total += bestScore
                    arrangedError += ring.errorAt(best, ux, uy)
                } else {
                    // No spaced spot: overlap rather than hide, in order where the ring allows.
                    best = bestIndex(marker, ux, uy, avoidPlaced = false, low = low, high = high, from = 0, span = ring.samples)
                    if (best < 0) best = bestIndex(marker, ux, uy, avoidPlaced = false, low = -1f, high = FULL_TURN, from = 0, span = ring.samples)
                    total += UNSPACED_PENALTY
                    arrangedError += UNSPACED_PENALTY
                }
            }
            val x: Float
            val y: Float
            if (best >= 0) {
                x = ring.markerX[best]
                y = ring.markerY[best]
                runAngle[position] = fromOrigin(best)
            } else {
                val s0 = ring.rayParameter(batch.targetX[marker], batch.targetY[marker])
                x = ring.ringX(s0)
                y = ring.ringY(s0)
            }
            arrangedX[marker] = x
            arrangedY[marker] = y
            arrangedSample[marker] = best
            ring.take(x, y)
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
        if (ring.samples == 0) return -1
        val first = (from % ring.samples + ring.samples) % ring.samples
        val range = min(span, ring.samples)
        val last = if (marker == NO_MARKER) -1 else run.memory[marker]
        var best = -1
        var score = Float.POSITIVE_INFINITY
        // The spots at and near the marker's last one first: their continuity bonus lowers the
        // score every other spot must beat.
        if (last >= 0) {
            for (k in -STICK_WINDOW_SAMPLES..STICK_WINDOW_SAMPLES) {
                val index = ((last + k) % ring.samples + ring.samples) % ring.samples
                if ((index - first + ring.samples) % ring.samples >= range) continue
                val error = spotScore(index, ux, uy, avoidPlaced, low, high, -1f)
                val candidate = error - run.continuity(marker, index) + run.retreat(marker, error) + run.revisit(marker, index)
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
            val index = (first + k) % ring.samples
            if (last >= 0 && ring.ringApart(index, last) <= STICK_WINDOW_SAMPLES) continue
            val error = spotScore(index, ux, uy, avoidPlaced, low, high, cutoff)
            if (error >= score) continue // its costs only add to it
            val candidate = error + run.retreat(marker, error) + run.revisit(marker, index)
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
        if (ring.depths[index].isNaN()) return Float.POSITIVE_INFINITY
        val dot = ring.directionX[index] * ux + ring.directionY[index] * uy
        if (dot <= cutoff) return Float.POSITIVE_INFINITY
        val counted = fromOrigin(index)
        if (counted <= low || counted >= high) return Float.POSITIVE_INFINITY
        if (avoidPlaced && ring.crowded(ring.markerX[index], ring.markerY[index])) return Float.POSITIVE_INFINITY
        return acos(dot.coerceIn(-1f, 1f)) * DEGREES + ring.depthCost[index]
    }

    /** Direction of ring sample [index], counted from the run's origin (the middle of the widest gap between this frame's targets). */
    private fun fromOrigin(index: Int): Float = run.fromOriginDegrees(ring.sampleAngle[index])

    /** Whether the marker (icon + distance, text toward the screen center) fits at ([x], [y]). */
    fun clearAt(x: Float, y: Float): Boolean = ring.clearAt(x, y)

    /** Width of a marker's distance text, as last [prepare]d (tests place markers with another planner). */
    val markerTextWidth: Float get() = ring.textWidth

    fun markerLeft(x: Float): Float = ring.markerLeft(x)
    fun markerRight(x: Float): Float = ring.markerRight(x)
    fun markerHalfHeight(): Float = ring.halfHeight

    private companion object {
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
    }
}
