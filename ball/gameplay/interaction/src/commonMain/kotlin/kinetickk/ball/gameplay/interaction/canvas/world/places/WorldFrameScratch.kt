// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.interaction.canvas.world.*

/** HUD regions, the edge-marker table and the frame's off-screen targets (draw-thread confined). */
internal object WorldOverlayScratch {
    val keepOut = WorldHudKeepOut()
    val planner = EdgeMarkerPlanner()
    val markers = EdgeMarkerBatch()
}

/**
 * Boxes of the point marks, names and timers, the orbit label and the edge markers drawn in the
 * current world frame, for tests (fixed arrays; recording allocates nothing). Draw-thread confined.
 */
internal object WorldDrawProbe {
    private const val CAPACITY = 32
    private val kinds = IntArray(CAPACITY)
    private val values = FloatArray(CAPACITY * 4)
    private var count = 0

    fun begin() {
        count = 0
    }

    fun record(kind: WorldDrawn, left: Float, top: Float, right: Float, bottom: Float) {
        if (count >= CAPACITY) return
        kinds[count] = kind.ordinal
        val base = count * 4
        values[base] = left
        values[base + 1] = top
        values[base + 2] = right
        values[base + 3] = bottom
        count++
    }

    /** The boxes of [kind] drawn since [begin] (tests only). */
    fun rects(kind: WorldDrawn): List<Rect> = (0 until count).filter { kinds[it] == kind.ordinal }.map {
        Rect(values[it * 4], values[it * 4 + 1], values[it * 4 + 2], values[it * 4 + 3])
    }
}

/**
 * What [WorldDrawProbe] records: a point's mark (offer, vault, target beacon, a race's next gate), a
 * point timer, an edge marker, the place pips of the target beacon or the next gate as drawn, an
 * offer's name, the collapsing orbit's seconds-in-ring label.
 */
internal enum class WorldDrawn { POINT_MARK, POINT_TIMER, EDGE_MARKER, BEACON_PIPS, POINT_NAME, ORBIT_LABEL }
