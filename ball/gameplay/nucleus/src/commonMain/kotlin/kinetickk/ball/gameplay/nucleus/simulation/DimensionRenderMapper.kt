// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionPhysics
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Ember
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EmberProjection
import kinetickk.ball.gameplay.nucleus.render.HaloProjection
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapToImmutableList
import kotlin.math.max
import kotlin.math.min

/**
 * The world layer and its visit as presentation draws them. Realspace with no open glitch rift and no locked window
 * rift publishes the shared [DimensionProjection.REALSPACE]. A visit publishes its layer, objective, clock, pace and
 * heat, the ring to fly through next, the exit rift, its halo rings and embers. An equal projection keeps [previous];
 * the halo list keeps its previous instance when the [identitySource] held the same rings, and otherwise each list
 * whose content matches (raw float bits) keeps its previous instance. A pure read: nothing here writes state.
 */
internal fun MutableGameState.dimensionProjection(
    previous: DimensionProjection?,
    identitySource: MutableGameState?,
): DimensionProjection {
    val run = dimensionRun
    val visit = run.visit
    val projection = if (visit == null) realspaceProjection(run, previous) else visitProjection(visit, previous, identitySource)
    return if (previous != null && projection == previous) previous else projection
}

private fun MutableGameState.realspaceProjection(run: DimensionRunState, previous: DimensionProjection?): DimensionProjection {
    val glitch = run.glitchPortal
    val locked = lockedRiftMask(glitch)
    if (glitch == null && locked == 0) return DimensionProjection.REALSPACE
    return DimensionProjection(
        glitchPortal = glitch?.let {
            portalProjection(it, locked = locked and WorldLayer.ARCHIVE.bit != 0, glitch = true, previous?.glitchPortal)
        },
        lockedRifts = locked,
    )
}

private fun MutableGameState.visitProjection(
    visit: DimensionVisit,
    previous: DimensionProjection?,
    identitySource: MutableGameState?,
): DimensionProjection {
    val physics = content.dimensions.definition(worldLayer).physics
    val sourceHalos = identitySource?.dimensionRun?.visit?.halos
    return DimensionProjection(
        layer = worldLayer,
        objective = visit.objective,
        remaining = max(0f, visit.endsAt - elapsed),
        duration = visit.endsAt - visit.enteredAt,
        progress = visit.progress,
        goal = visit.goal,
        completed = visit.completed,
        bossId = visit.bossId,
        pace = dimensionPace(),
        scorching = scorching(physics),
        localClock = dimensionLocalClock,
        nextHaloId = nextHaloId(visit),
        exitPortal = visit.exitPortal?.let { portalProjection(it, locked = false, glitch = false, previous?.exitPortal) },
        // The shared empty list proves nothing about where the previous rings came from, so only rings are matched by identity.
        halos = if (previous != null && visit.halos.isNotEmpty() && sourceHalos === visit.halos) {
            previous.halos
        } else {
            haloProjections(visit.halos, previous?.halos)
        },
        embers = emberProjections(visit.embers, previous?.embers),
    )
}

/**
 * [WorldLayer.bit] of every rift in realspace the Core could not enter now: a window rift or the glitch rift while
 * another engagement runs or the entry rules refuse its dimension (the HATCH look). The phase is not read, so a locked
 * rift keeps its look while the run is paused.
 */
private fun MutableGameState.lockedRiftMask(glitch: DimensionPortal?): Int {
    var mask = 0
    for (index in sites.indices) {
        val target = sites[index].kind.riftTarget() ?: continue
        if (riftRefused(target)) mask = mask or target.bit
    }
    if (glitch != null && riftRefused(WorldLayer.ARCHIVE)) mask = mask or WorldLayer.ARCHIVE.bit
    return mask
}

private fun MutableGameState.riftRefused(target: WorldLayer): Boolean = engagementActive() || !dimensionEntryOpen(target)

/** The Nadir's heat warning: the slow clock's share of the grace, 1 once the scorch ticks; 0 where nothing scorches. */
private fun MutableGameState.scorching(physics: DimensionPhysics): Float = when {
    physics.scorchSharePerSecond <= 0f || dimensionSlowSeconds <= 0f -> 0f
    physics.scorchGraceSeconds <= 0f -> 1f
    else -> min(1f, dimensionSlowSeconds / physics.scorchGraceSeconds)
}

/** The course ring to fly through next: the one whose place is the objective's progress, -1 once completed or none. */
private fun nextHaloId(visit: DimensionVisit): Int {
    if (visit.completed) return -1
    val halos = visit.halos
    for (index in halos.indices) if (halos[index].courseIndex == visit.progress) return halos[index].id
    return -1
}

private fun MutableGameState.portalProjection(
    portal: DimensionPortal,
    locked: Boolean,
    glitch: Boolean,
    previous: PortalProjection?,
): PortalProjection {
    val projection = PortalProjection(portal.id, portal.x, portal.y, portal.target, locked, glitch, max(0f, portal.expiresAt - elapsed))
    return if (projection == previous) previous else projection
}

private fun haloProjections(halos: ImmutableList<HaloRing>, reusable: ImmutableList<HaloProjection>?): ImmutableList<HaloProjection> {
    if (reusable != null && reusable.size == halos.size) {
        var same = true
        for (index in halos.indices) {
            val ring = halos[index]
            val projected = reusable[index]
            if (projected.id != ring.id || !projected.x.sameBitsAs(ring.x) || !projected.y.sameBitsAs(ring.y) ||
                projected.courseIndex != ring.courseIndex
            ) {
                same = false
                break
            }
        }
        if (same) return reusable
    }
    return halos.mapToImmutableList { HaloProjection(it.id, it.x, it.y, it.courseIndex) }
}

private fun MutableGameState.emberProjections(embers: ImmutableList<Ember>, reusable: ImmutableList<EmberProjection>?): ImmutableList<EmberProjection> {
    if (reusable != null && reusable.size == embers.size) {
        var same = true
        for (index in embers.indices) {
            val ember = embers[index]
            val projected = reusable[index]
            if (!projected.x.sameBitsAs(ember.x) || !projected.y.sameBitsAs(ember.y) ||
                !projected.remaining.sameBitsAs(emberRemaining(ember))
            ) {
                same = false
                break
            }
        }
        if (same) return reusable
    }
    return embers.mapToImmutableList { EmberProjection(it.x, it.y, emberRemaining(it)) }
}

private fun MutableGameState.emberRemaining(ember: Ember): Float = max(0f, ember.expiresAt - elapsed)
