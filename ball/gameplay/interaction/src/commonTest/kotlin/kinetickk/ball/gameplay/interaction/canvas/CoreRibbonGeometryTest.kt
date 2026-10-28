// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fx.CoreRibbonProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreRibbonGeometryTest {
    @Test
    fun pointsAlongAnLPathFollowTheTurn() {
        // Head at (0, 0); the path runs 100 u back along −x, then turns and runs 100 u along +y.
        val ribbon = ribbon(listOf(-50f to 0f, -100f to 0f, -100f to 50f, -100f to 100f), startAt = 3)
        val out = FloatArray(2)
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 50f, out))
        assertPoint(-50f, 0f, out, "50 u back lies on the first leg")
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 150f, out))
        assertPoint(-100f, 50f, out, "150 u back lies on the turned leg, not on the line of the first")
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 75f, out))
        assertPoint(-75f, 0f, out, "75 u back")
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 200f, out))
        assertPoint(-100f, 100f, out, "the whole path's length reaches the oldest sample")
        assertFalse(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 200.5f, out), "nothing lies past the oldest sample")
        assertFalse(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, -1f, out), "no point lies ahead of the head")
    }

    @Test
    fun pointsStopAtAChainStart() {
        // The third sample starts a chain: the older samples 400 u away after it are not part of the path.
        val ribbon = ribbon(listOf(-10f to 0f, -20f to 0f, -30f to 0f, -430f to 0f, -440f to 0f), startAt = 2)
        val out = FloatArray(2)
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 30f, out))
        assertPoint(-30f, 0f, out, "the chain ends at its start sample")
        assertFalse(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 30.5f, out), "no point lies past a chain start")
        assertFalse(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 200f, out), "nor across the teleport")
    }

    @Test
    fun repeatedPositionsAddNoLength() {
        val ribbon = ribbon(listOf(0f to 0f, -40f to 0f, -40f to 0f, -80f to 0f), startAt = 3)
        val out = FloatArray(2)
        assertTrue(CoreRibbonGeometry.pointAlong(ribbon, 0f, 0f, 60f, out))
        assertPoint(-60f, 0f, out, "a sample on the head and a repeated sample count nothing")
        assertFalse(CoreRibbonGeometry.pointAlong(CoreRibbonProjection.EMPTY, 0f, 0f, 0f, out), "an empty ribbon has no path")
    }

    private fun ribbon(points: List<Pair<Float, Float>>, startAt: Int): CoreRibbonProjection = CoreRibbonProjection.of(
        FloatArray(points.size * 2) { if (it % 2 == 0) points[it / 2].first else points[it / 2].second },
        FloatArray(points.size),
        IntArray(points.size) { if (it == startAt) CoreRibbonProjection.START else 0 },
    )

    private fun assertPoint(x: Float, y: Float, out: FloatArray, message: String) =
        assertTrue(abs(out[0] - x) < 1e-3f && abs(out[1] - y) < 1e-3f, "$message: expected ($x, $y), was (${out[0]}, ${out[1]})")
}
