// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList

/*
 * The run's dimension facts. Every type is a value: a change replaces the value, so a reduction candidate never
 * writes into a committed one. Positions are run-local and shift with the world rebase.
 */

/** A rift the dimension system owns: the Archive's glitch rift or a visit's exit rift. */
internal data class DimensionPortal(val id: Int, val x: Float, val y: Float, val target: WorldLayer, val expiresAt: Float) {
    fun shifted(dx: Float, dy: Float): DimensionPortal = copy(x = x - dx, y = y - dy)
}

/** A Zenith halo ring; [courseIndex] is its place in the Ascent course, or -1 for an ambient ring. Passed rings leave. */
internal data class HaloRing(val id: Int, val x: Float, val y: Float, val courseIndex: Int) {
    fun shifted(dx: Float, dy: Float): HaloRing = copy(x = x - dx, y = y - dy)
}

/** A Nadir ember waiting to be collected until [expiresAt]. */
internal data class Ember(val x: Float, val y: Float, val expiresAt: Float) {
    fun shifted(dx: Float, dy: Float): Ember = copy(x = x - dx, y = y - dy)
}

/** The visit in progress: its layer, objective, clock and the objective's world marks. */
internal data class DimensionVisit(
    val layer: WorldLayer,
    val objective: DimensionObjective,
    /** Visits entered earlier this run (0..2); it keys the visit's hashes. */
    val serial: Int,
    val enteredAt: Float,
    /** The run second the timer pulls the Core back; never later than the boss time less the exit margin. */
    val endsAt: Float,
    val progress: Int = 0,
    val goal: Int,
    val completed: Boolean = false,
    /** The objective boss once it spawned (-1: none yet, or the objective has none). */
    val bossId: Int = -1,
    /** The run second the objective boss arrives; +∞ when the objective has none. */
    val bossSpawnAt: Float,
    val exitPortal: DimensionPortal? = null,
    /** At most ContentBounds.MAX_HALO_RINGS. */
    val halos: ImmutableList<HaloRing> = immutableListOf(),
    val haloSerial: Int = 0,
    /** The anchor and heading the next Ascent ring is placed from (the entry point and heading at first). */
    val courseX: Float,
    val courseY: Float,
    val courseHeading: Float,
    /** At most ContentBounds.MAX_EMBERS. */
    val embers: ImmutableList<Ember> = immutableListOf(),
) {
    fun shifted(dx: Float, dy: Float): DimensionVisit = copy(
        exitPortal = exitPortal?.shifted(dx, dy),
        halos = if (halos.isEmpty()) halos else halos.mapToImmutableList { it.shifted(dx, dy) },
        courseX = courseX - dx,
        courseY = courseY - dy,
        embers = if (embers.isEmpty()) embers else embers.mapToImmutableList { it.shifted(dx, dy) },
    )
}

/** A finished visit's reward waiting for the choice queue; a failed objective pays the smaller reward. */
internal data class DimensionRewardGrant(val layer: WorldLayer, val completed: Boolean)

/** What the run did with dimensions: rift offers, entered and completed layers, the open rift and the visit. */
internal data class DimensionRunState(
    /** Window rifts claimed this run, at most the policy's maxPortalOffersPerRun. */
    val portalOffers: Int = 0,
    /** [WorldLayer.bit] of every layer entered this run; each layer is entered at most once. */
    val visitedMask: Int = 0,
    /** [WorldLayer.bit] of every layer whose objective was completed this run. */
    val completedMask: Int = 0,
    /** The glitch rift into the Archive while it is open (realspace only). */
    val glitchPortal: DimensionPortal? = null,
    val visit: DimensionVisit? = null,
    /** At most ContentBounds.MAX_PENDING_DIMENSION_REWARDS, oldest first. */
    val pendingRewards: ImmutableList<DimensionRewardGrant> = immutableListOf(),
    val nextPortalId: Int = 1,
) {
    /** This value with every position moved by the rebase shift; itself when nothing positional exists. */
    fun shifted(dx: Float, dy: Float): DimensionRunState =
        if (glitchPortal == null && visit == null) this
        else copy(glitchPortal = glitchPortal?.shifted(dx, dy), visit = visit?.shifted(dx, dy))

    companion object {
        val EMPTY: DimensionRunState = DimensionRunState()
    }
}
