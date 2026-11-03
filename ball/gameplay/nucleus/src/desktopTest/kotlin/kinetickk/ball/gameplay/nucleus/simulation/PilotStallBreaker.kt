// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kotlin.math.abs
import kotlin.math.exp

/**
 * A pilot's unstick rule (test-only, input-only), as a player reads a stall off the screen: the Core has crawled for
 * [STALL_SECONDS] (its speed averaged over [AVERAGE_SECONDS] below [STALL_SPEED]). Steering terms that cancel pin a
 * pilot that way: a wall between the Core and its target, an orbit pressed into a wall, a duel's return leash against
 * a boss that holds its range. The pilot then breaks out, by default with a side step ([sideStep]): [ESCAPE_SECONDS]
 * at [ESCAPE_SPEED] at right angles to the way to what it chases, off a wall within [WALL_SIDE_REACH] when that points
 * off it, else on the other side from last time (a player tries the other way round when the first did not get
 * clear). The rule reads the Core's motion only, never the Core Stasis clock, and does nothing while the Core moves.
 */
internal class PilotStallBreaker {
    /** The side step's velocity while it runs. */
    var escapeX = 0f
        private set
    var escapeY = 0f
        private set

    private var speedAverage = 0f
    private var slowSeconds = 0f
    private var lastElapsed = Float.NaN
    private var escapeUntil = Float.NEGATIVE_INFINITY
    private var side = 1f

    /**
     * Reads the Core once per input frame the pilot steers; true on the frame a stall is read (never while a way out
     * runs), after which the reading starts over. A gap in the readings (the pilot did not steer) restarts them.
     */
    fun stalled(s: MutableGameState): Boolean {
        val delta = if (lastElapsed.isNaN()) Float.POSITIVE_INFINITY else s.elapsed - lastElapsed
        lastElapsed = s.elapsed
        if (delta > MAX_READING_GAP || delta < 0f) {
            speedAverage = s.speed
            slowSeconds = 0f
            escapeUntil = Float.NEGATIVE_INFINITY
            return false
        }
        speedAverage += (s.speed - speedAverage) * (1f - exp(-delta / AVERAGE_SECONDS))
        if (escaping(s)) return false
        slowSeconds = if (speedAverage < STALL_SPEED) slowSeconds + delta else 0f
        if (slowSeconds < STALL_SECONDS) return false
        slowSeconds = 0f
        return true
    }

    /** True while a way out runs ([sideStep], or one the pilot steers itself after [hold]). */
    fun escaping(s: MutableGameState): Boolean = s.elapsed < escapeUntil

    /** Starts a way out the pilot steers itself for at most [seconds]. */
    fun hold(s: MutableGameState, seconds: Float) {
        escapeUntil = s.elapsed + seconds
    }

    /** Ends the running way out. */
    fun stop() {
        escapeUntil = Float.NEGATIVE_INFINITY
    }

    /** Starts a side step away from the way to ([chaseX], [chaseY]); its velocity is [escapeX], [escapeY]. */
    fun sideStep(s: MutableGameState, chaseX: Float, chaseY: Float) {
        escapeUntil = s.elapsed + ESCAPE_SECONDS
        val toX = chaseX - s.coreX
        val toY = chaseY - s.coreY
        val distance = length(toX, toY)
        val unitX = if (distance > 1f) toX / distance else 1f
        val unitY = if (distance > 1f) toY / distance else 0f
        var sign = side
        side = -side
        s.nearestWall(s.coreX, s.coreY, WALL_SIDE_REACH) { nx, ny, _ ->
            val off = -unitY * nx + unitX * ny
            if (abs(off) >= WALL_SIDE_SHARE) sign = if (off > 0f) 1f else -1f
        }
        escapeX = -unitY * sign * ESCAPE_SPEED
        escapeY = unitX * sign * ESCAPE_SPEED
    }

    companion object {
        /** The Core counts as crawling below this averaged speed (u/s)... */
        const val STALL_SPEED = 150f

        /** ...averaged over this time constant (s)... */
        const val AVERAGE_SECONDS = 1f

        /** ...and the pilot breaks out after crawling this long (s). */
        const val STALL_SECONDS = 3f

        /** The side step: this long (s) at this speed (u/s). */
        const val ESCAPE_SECONDS = 1.5f
        const val ESCAPE_SPEED = 600f

        /** A wall this near (u) decides the side when the side step points at least this much off it. */
        const val WALL_SIDE_REACH = 200f
        const val WALL_SIDE_SHARE = 0.3f

        /** Readings farther apart than this (s) restart the stall reading. */
        const val MAX_READING_GAP = 0.1f
    }
}
