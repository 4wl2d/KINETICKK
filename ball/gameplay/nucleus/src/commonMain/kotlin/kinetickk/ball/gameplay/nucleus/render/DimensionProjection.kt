// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * A rift into [target]; a [glitch] rift leads to the Archive, a [locked] one cannot be entered yet. Flying within
 * [radius] of its centre crosses over.
 */
data class PortalProjection(
    val id: Int,
    val x: Float,
    val y: Float,
    val target: WorldLayer,
    val locked: Boolean,
    val glitch: Boolean,
    val remaining: Float,
    val radius: Float,
)

/** Why the rifts in realspace cannot be entered now; presentation names it beside a locked rift. */
enum class RiftLock {
    /** Every rift on screen is open. */
    NONE,

    /** Another engagement runs (a window trial, a sigil duel, a dungeon): one at a time. */
    ENGAGEMENT,

    /** A boss-class enemy lives or awaits its kill; crossing over would delete it and its reward. */
    BOSS,

    /** The Architect is here, or too little time is left before it for a visit. */
    ARCHITECT,
}

/** A Zenith halo ring; [courseIndex] is its place in the Ascent course, or -1 for an ambient halo. */
data class HaloProjection(val id: Int, val x: Float, val y: Float, val courseIndex: Int)

/**
 * A Nadir ember waiting to be collected, thrown from the burning kill at ([fromX], [fromY]); [age] is the seconds since
 * the throw. An ember without a throw has its origin at its position.
 */
data class EmberProjection(
    val x: Float,
    val y: Float,
    val remaining: Float,
    val fromX: Float,
    val fromY: Float,
    val age: Float,
)

/** The world layer the Core is in and its visit; [REALSPACE] outside every dimension. */
data class DimensionProjection(
    val layer: WorldLayer = WorldLayer.BASE,
    val objective: DimensionObjective? = null,
    val remaining: Float = 0f,
    val duration: Float = 0f,
    val progress: Int = 0,
    val goal: Int = 0,
    val completed: Boolean = false,
    /** The objective can no longer be completed (a broken Ascent): the exit rift is open and the visit pays its smaller reward. */
    val failed: Boolean = false,
    /** Speed the layer asks for; presentation compares the Core's speed with it (the too-slow look). */
    val pace: Float = 0f,
    /** 0..1 Nadir warning (1 while the scorch ticks). */
    val scorching: Float = 0f,
    /** Visit time scaled by the layer's clock (Archive motes freeze when the Core stops). */
    val localClock: Float = 0f,
    /** Hostile time over real time this step: the Archive's Desync (0.25..1.25 with the Core's speed), 1 elsewhere. */
    val timeScale: Float = 1f,
    /**
     * Seconds the hostile clock trails the run clock (the Archive's Desync; 0 elsewhere). Hostile fuses are timed on it,
     * so a volatile burst's telegraph reads the render time less this lag.
     */
    val hostileLag: Float = 0f,
    /** The catch radius of a halo ring and of an ember (0 outside a visit). */
    val haloRadius: Float = 0f,
    val emberRadius: Float = 0f,
    val nextHaloId: Int = -1,
    val exitPortal: PortalProjection? = null,
    val glitchPortal: PortalProjection? = null,
    val halos: ImmutableList<HaloProjection> = immutableListOf(),
    val embers: ImmutableList<EmberProjection> = immutableListOf(),
    /**
     * [WorldLayer.bit] of every dimension whose rift in realspace (a window rift or the glitch rift) the Core could not
     * enter now: another engagement runs or the entry rules refuse it (drawn locked).
     */
    val lockedRifts: Int = 0,
    /** Why the [lockedRifts] are locked; [RiftLock.NONE] when none is. */
    val riftLock: RiftLock = RiftLock.NONE,
    /** The layer whose reward choice is open (its Blessings, its Pacts or the Archive's trove); null otherwise. */
    val rewardLayer: WorldLayer? = null,
    /** Seconds left to pass the ring to fly through next ([nextHaloId]); 0 while no split runs. */
    val splitRemaining: Float = 0f,
    /** The running split's length; the next ring's dashed ring empties over it. 0 while no split runs. */
    val splitDuration: Float = 0f,
) {
    companion object {
        val REALSPACE: DimensionProjection = DimensionProjection()
    }
}
