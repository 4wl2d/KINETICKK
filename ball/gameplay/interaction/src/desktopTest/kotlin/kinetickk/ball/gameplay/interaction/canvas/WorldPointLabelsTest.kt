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
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.localeList
import kinetickk.foundation.design.measureKkText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Point-of-interest marks, names and timers drawn by the real world renderer (bundled fonts) while
 * the point sweeps the screen: timers never land on the HUD, and a mark and its edge marker never
 * draw together (the drawn target beacon, place pips included, is whole on screen). Offers carry
 * their name above the timer; the collapsing orbit's label shows the seconds in the ring.
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

    @Test
    fun offersShowTheirNameAboveTheirTimer() {
        // Anomaly.png: the offer's name (`.t-label`, the kind's color) stacked above its timer
        // under the mark; the pair follows the timer's rules and fits in full at every text size.
        for ((width, height) in sizes) for (language in AppLanguage.entries) for (textScale in textScales) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
            for (kind in PointOfInterestKind.entries) {
                val base = model(width, height, emptyList())
                val name = base.content.pointsOfInterest.definition(kind).name
                val model = base.with("pointsOfInterest" to immutableListOf(
                    PointOfInterestProjection(kind, name, 0f, 0f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f)))
                val full = measureKkText(measurer, name.localizedContent(language), measurer.typography.labelStyle(worldLabelSp(15f)),
                    uppercase = true)
                assertFalse(full.isCut(), "$language $kind name at text=$textScale")
                var pairs = 0
                for (y in -20..(height + 20) step 29) for (x in -20..(width + 20) step 29) {
                    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                        drawWorld(model.lookingAt(width, height, x.toFloat(), y.toFloat()), VisualFxProjection.EMPTY, 0f, 0f, measurer)
                    }
                    val where = "$language $kind at $width x $height text=$textScale, offer at ($x, $y)"
                    val keepOut = WorldOverlayScratch.keepOut
                    val names = WorldDrawProbe.rects(WorldDrawn.POINT_NAME)
                    val timers = WorldDrawProbe.rects(WorldDrawn.POINT_TIMER)
                    val marks = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                    assertEquals(timers.size, names.size, "a name with every offer timer: $where")
                    if (names.isNotEmpty()) assertEquals(1, marks.size, "the name belongs to a drawn mark: $where")
                    names.zip(timers).forEach { (label, timer) ->
                        assertTrue(timer.top >= label.bottom && timer.top - label.bottom <= 6f, "name $label just above its timer $timer: $where")
                        assertEquals(label.center.x, timer.center.x, 0.5f, "name and timer share a center: $where")
                        assertTrue(label.onScreen(width, height) && timer.onScreen(width, height), "name $label on screen: $where")
                        assertFalse(keepOut.intersects(label.left, label.top, label.right, label.bottom), "name $label clear of the HUD: $where")
                        assertEquals(full.size.width.toFloat(), label.width, 0.5f, "the name in full at the board size: $where")
                        val mark = marks.single()
                        assertTrue(label.top >= mark.bottom || timer.bottom <= mark.top, "name and timer below or above the mark: $where")
                        pairs++
                    }
                }
                assertTrue(pairs > 50, "$language $kind at $width x $height text=$textScale draws its name in the open field ($pairs)")
            }
        }
        // The name is drawn in the kind's color: gravitic for the sealed anomaly, you for the circuit.
        listOf(PointOfInterestKind.SEALED_ANOMALY to Kk.AGravitic, PointOfInterestKind.RESONANT_CIRCUIT to KkRolePalette.Default.you)
            .forEach { (kind, color) ->
                val base = model(1_440, 810, emptyList())
                val model = base.with("pointsOfInterest" to immutableListOf(PointOfInterestProjection(kind,
                    base.content.pointsOfInterest.definition(kind).name, 0f, 0f, false, 31f, 0, 0f, immutableListOf(), 0f, 0f)))
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                    1.25f, AppLanguage.Russian, HudTestFonts.typography.copy(localeList = AppLanguage.Russian.localeList()))
                val bitmap = ImageBitmap(1_440, 810)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
                    drawRect(Kk.Ink)
                    drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                }
                val box = WorldDrawProbe.rects(WorldDrawn.POINT_NAME).single()
                val pixels = bitmap.toPixelMap(box.left.toInt(), box.top.toInt(), box.width.toInt(), box.height.toInt())
                var painted = 0
                for (py in 0 until pixels.height) for (px in 0 until pixels.width) if (pixels[px, py] == color) painted++
                assertTrue(painted > 20, "$kind name painted in its color ($painted px)")
            }
    }

    @Test
    fun orbitLabelShowsTheSecondsInTheRing() {
        // Anomaly.png: "5.2s" over the ring, the panel's "5.2 / 8.0 s" value, not the trial clock.
        val base = model(1_440, 810, emptyList())
        val required = base.content.pointsOfInterest.orbitRequiredSeconds
        listOf(0f, 0.3f, 0.65f, 0.99f, 1f, 1.3f).forEach { progress ->
            val tenths = orbitRingTenths(progress, required)
            val point = orbit(progress, remaining = 14.2f)
            assertTrue(trialProgressText(base, point, AppLanguage.English).startsWith("${tenths / 10}.${tenths % 10} / "), "EN at $progress")
            assertTrue(trialProgressText(base, point, AppLanguage.Russian).startsWith("${tenths / 10},${tenths % 10} / "), "RU at $progress")
        }
        assertEquals("S", WorldStrings.secondsSuffix(AppLanguage.English))
        assertEquals(" С", WorldStrings.secondsSuffix(AppLanguage.Russian))
        // Rendered: the label follows the time in the ring and ignores the trial clock.
        fun label(progress: Float, remaining: Float, box: Rect? = null): Pair<Rect, IntArray> {
            val model = base.with("pointsOfInterest" to immutableListOf(orbit(progress, remaining)))
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, AppLanguage.English, HudTestFonts.typography)
            val bitmap = ImageBitmap(1_440, 810)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
                drawRect(Kk.Ink)
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
            }
            val drawn = box ?: WorldDrawProbe.rects(WorldDrawn.ORBIT_LABEL).single()
            val pixels = IntArray(drawn.width.toInt() * drawn.height.toInt())
            bitmap.readPixels(pixels, drawn.left.toInt(), drawn.top.toInt(), drawn.width.toInt(), drawn.height.toInt())
            return drawn to pixels
        }
        val (box, early) = label(0.65f, remaining = 14.2f)
        assertTrue(early.any { it != Kk.Ink.toArgb() }, "the label is drawn")
        assertContentEquals(early, label(0.65f, remaining = 3f, box).second, "the trial clock does not change the label")
        assertFalse(early.contentEquals(label(0.3f, remaining = 14.2f, box).second), "the label shows the time in the ring")
    }

    private fun orbit(progress: Float, remaining: Float) = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT,
        "Collapsing orbit", 0f, 0f, true, remaining, 0, progress, immutableListOf(), 0f, 0f)

    private fun sweep(width: Int, height: Int, textScale: Float, model: GameplayRenderModel, step: Int, check: (Float, Float) -> Unit) {
        for (y in -20..(height + 20) step step) for (x in -20..(width + 20) step step) {
            drawAt(width, height, textScale, model, x.toFloat(), y.toFloat())
            check(x.toFloat(), y.toFloat())
        }
    }

    /** Draws the world with the point's anchor (world origin) at screen ([x], [y]), the camera moved there. */
    private fun drawAt(width: Int, height: Int, textScale: Float, model: GameplayRenderModel, x: Float, y: Float) {
        val measurer = measurers.getOrPut(textScale) {
            CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), textScale,
                AppLanguage.English, HudTestFonts.typography)
        }
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawWorld(model.lookingAt(width, height, x, y), VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
    }

    /**
     * [this] model with the camera placed so the world origin draws at screen ([x], [y]) (not via
     * the shake offset: marks and edge markers are decided in the unshaken view).
     */
    private fun GameplayRenderModel.lookingAt(width: Int, height: Int, x: Float, y: Float): GameplayRenderModel =
        with("cameraX" to width * 0.5f - x, "cameraY" to height * 0.5f - y)

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
