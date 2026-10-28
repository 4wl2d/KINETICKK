// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** A rift into [target]; a [glitch] rift leads to the Archive, a [locked] one cannot be entered yet. */
data class PortalProjection(
    val id: Int,
    val x: Float,
    val y: Float,
    val target: WorldLayer,
    val locked: Boolean,
    val glitch: Boolean,
    val remaining: Float,
)

/** A Zenith halo ring; [courseIndex] is its place in the Ascent course, or -1 for an ambient halo. */
data class HaloProjection(val id: Int, val x: Float, val y: Float, val courseIndex: Int)

/** A Nadir ember waiting to be collected. */
data class EmberProjection(val x: Float, val y: Float, val remaining: Float)

/** The world layer the Core is in and its visit; [REALSPACE] outside every dimension. */
data class DimensionProjection(
    val layer: WorldLayer = WorldLayer.BASE,
    val objective: DimensionObjective? = null,
    val remaining: Float = 0f,
    val duration: Float = 0f,
    val progress: Int = 0,
    val goal: Int = 0,
    val completed: Boolean = false,
    val bossId: Int = -1,
    /** Speed the layer asks for; presentation compares the Core's speed with it (the too-slow look). */
    val pace: Float = 0f,
    /** 0..1 Nadir warning (1 while the scorch ticks). */
    val scorching: Float = 0f,
    /** Visit time scaled by the layer's clock (Archive motes freeze when the Core stops). */
    val localClock: Float = 0f,
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
    /** The layer whose reward choice is open (its Blessings, its Pacts or the Archive's trove); null otherwise. */
    val rewardLayer: WorldLayer? = null,
) {
    companion object {
        val REALSPACE: DimensionProjection = DimensionProjection()
    }
}
