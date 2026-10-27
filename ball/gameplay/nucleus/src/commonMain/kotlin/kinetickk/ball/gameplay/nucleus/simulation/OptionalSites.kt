// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kotlin.math.min

/** Most opt-in site marks [optionalSiteMarks] writes in one call. */
internal const val MAX_OPTIONAL_SITE_MARKS: Int = 64

/** Activation radius of a point-of-interest window offer (the circle `updatePointsOfInterest` tests). */
internal const val POI_ACTIVATION_RADIUS: Float = 65f

/**
 * Writes `(x, y, radius)` triples of every opt-in site, in list order, into [out] for pilots that must
 * not opt in, and returns how many marks it wrote (at most [MAX_OPTIONAL_SITE_MARKS]). Allocation-free.
 */
internal fun MutableGameState.optionalSiteMarks(out: FloatArray): Int {
    require(out.size >= MAX_OPTIONAL_SITE_MARKS * 3) { "An optional-site buffer holds $MAX_OPTIONAL_SITE_MARKS marks" }
    val points = pointsOfInterest
    val count = min(points.size, MAX_OPTIONAL_SITE_MARKS)
    for (index in 0 until count) {
        val point = points[index]
        val base = index * 3
        out[base] = point.x
        out[base + 1] = point.y
        out[base + 2] = POI_ACTIVATION_RADIUS
    }
    return count
}

/** True while the run is engaged with optional content (callers keep it sticky). */
internal fun MutableGameState.optionalSitesTouched(): Boolean =
    pointsOfInterest.any { it.active } || directedReward != null || pendingDirectedRewards.isNotEmpty()
