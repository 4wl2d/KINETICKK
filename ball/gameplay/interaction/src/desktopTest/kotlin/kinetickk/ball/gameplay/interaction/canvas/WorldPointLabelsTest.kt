// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Point-of-interest marks and timers drawn by the real world renderer (bundled fonts) while the
 * point sweeps the screen: timers never land on the HUD, and a mark and its edge marker never
 * draw together (the drawn target beacon, place pips included, is whole on screen).
 */
class WorldPointLabelsTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)
    private val textScales = listOf(1f, 1.25f, 1.75f)

    @Test
    fun pointTimersNeverTouchTheHud() {
        val scenes = listOf(
            // The circuit's target beacon (nextBeacon 1 leads to beacon order 2, right of center).
            "beacon" to listOf(point(PointOfInterestKind.RESONANT_CIRCUIT, -BEACON_DX, -BEACON_DY, active = true)),
            "vault" to listOf(point(PointOfInterestKind.SEALED_ANOMALY, 0f, 0f, active = true)),
            "offer" to listOf(point(PointOfInterestKind.RESONANT_CIRCUIT, 0f, 0f, active = false)),
            // An offer while another point's trial runs (its panel joins the HUD).
            "offer-trial" to listOf(
                point(PointOfInterestKind.SEALED_ANOMALY, 0f, 0f, active = false),
                point(PointOfInterestKind.COLLAPSING_ORBIT, -9_000f, -9_000f, active = true),
            ),
        )
        for ((width, height) in sizes) for (textScale in textScales) for ((name, points) in scenes) {
            val model = model(width, height, points)
            var drawn = 0
            sweep(width, height, textScale, model, step = 14) { x, y ->
                val keepOut = WorldOverlayScratch.keepOut
                val where = "$name at $width x $height text=$textScale, mark at ($x, $y)"
                WorldDrawProbe.rects(WorldDrawn.POINT_TIMER).forEach { box ->
                    drawn++
                    assertTrue(box.onScreen(width, height), "timer $box on screen: $where")
                    assertFalse(keepOut.intersects(box.left, box.top, box.right, box.bottom),
                        "timer $box clear of the HUD ${keepOut.rects()}: $where")
                }
                WorldDrawProbe.rects(WorldDrawn.POINT_MARK).forEach { box ->
                    assertTrue(box.onScreen(width, height), "mark $box on screen: $where")
                    val center = box.center
                    assertFalse(keepOut.intersects(center.x, center.y, center.x, center.y), "mark $box not under the HUD: $where")
                }
            }
            assertTrue(drawn > 50, "$name at $width x $height text=$textScale draws its timer in the open field ($drawn)")
        }
    }

    @Test
    fun targetBeaconAndItsEdgeMarkerNeverDrawTogether() {
        val points = listOf(point(PointOfInterestKind.RESONANT_CIRCUIT, -BEACON_DX, -BEACON_DY, active = true))
        for ((width, height) in sizes) for (textScale in listOf(1f, 1.75f)) {
            val model = model(width, height, points)
            var marks = 0
            var markers = 0
            // Every screen edge band: from well outside the edge to well inside it.
            val across = (-110..190 step 3).map { it.toFloat() }
            val alongX = (40..(width - 40) step 50).map { it.toFloat() }
            val alongY = (40..(height - 40) step 50).map { it.toFloat() }
            val positions = alongX.flatMap { x -> across.flatMap { d -> listOf(x to d, x to height - d) } } +
                alongY.flatMap { y -> across.flatMap { d -> listOf(d to y, width - d to y) } }
            positions.forEach { (x, y) ->
                drawAt(width, height, textScale, model, x, y)
                val where = "$width x $height text=$textScale, beacon at ($x, $y)"
                val mark = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                val marker = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                assertEquals(1, mark.size + marker.size, "exactly one of the beacon and its edge marker: $where (mark $mark, marker $marker)")
                mark.forEach { box ->
                    assertTrue(box.onScreen(width, height), "whole beacon on screen: $where")
                    marks++
                }
                // The place pips as drawn: on screen and inside the mark box the hand-over uses.
                val pips = WorldDrawProbe.rects(WorldDrawn.BEACON_PIPS)
                assertEquals(mark.size, pips.size, "the drawn beacon's pips: $where")
                pips.forEach { row ->
                    assertTrue(row.onScreen(width, height), "beacon pips $row on screen: $where")
                    assertTrue(mark.any { it.encloses(row) }, "beacon mark $mark covers its pips $row: $where")
                }
                markers += marker.size
            }
            assertTrue(marks > 0 && markers > 0, "the sweep crosses the hand-over at $width x $height ($marks marks, $markers markers)")
        }
    }

    private fun sweep(width: Int, height: Int, textScale: Float, model: GameplayRenderModel, step: Int, check: (Float, Float) -> Unit) {
        for (y in -20..(height + 20) step step) for (x in -20..(width + 20) step step) {
            drawAt(width, height, textScale, model, x.toFloat(), y.toFloat())
            check(x.toFloat(), y.toFloat())
        }
    }

    /** Draws the world with the point's anchor (world origin) at screen ([x], [y]) via the shake offset. */
    private fun drawAt(width: Int, height: Int, textScale: Float, model: GameplayRenderModel, x: Float, y: Float) {
        val measurer = measurers.getOrPut(textScale) {
            CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), textScale,
                AppLanguage.English, HudTestFonts.typography)
        }
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawWorld(model, VisualFxProjection.EMPTY, x - width * 0.5f, y - height * 0.5f, measurer)
        }
    }

    private val measurers = HashMap<Float, CanvasTextMeasurer>()
    private val scope = CanvasDrawScope()

    // The size passed to draw sets the screen; a 1 px bitmap keeps the sweep fast.
    private val canvas = Canvas(ImageBitmap(1, 1))

    private fun model(width: Int, height: Int, points: List<PointOfInterestProjection>): GameplayRenderModel =
        hudTestModel(width.toFloat(), height.toFloat()).with(
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
            "enemies" to immutableListOf<EnemyProjection>(), "pointsOfInterest" to points.toImmutableList(),
        )

    private fun point(kind: PointOfInterestKind, x: Float, y: Float, active: Boolean) =
        PointOfInterestProjection(kind, kind.name, x, y, active, 14f, 1, 0.4f, immutableListOf(), 0f, 0f)

    private fun Rect.onScreen(width: Int, height: Int) = left >= 0f && top >= 0f && right <= width && bottom <= height

    private fun Rect.encloses(other: Rect) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    private companion object {
        // PointOfInterestRenderer's beacon offsets: order 2 sits 230 right of and 200 below the center.
        const val BEACON_DX = 230f
        const val BEACON_DY = 200f
    }
}
