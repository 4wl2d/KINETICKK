// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.ball.gameplay.interaction.canvas.world.places.*

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
