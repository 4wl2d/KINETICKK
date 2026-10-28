// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A running gate race drawn by the real world renderer: its next gate and that gate's edge marker never draw together
 * (the drawn gate, order pips included, is whole on screen), and volt marks only the next gate.
 */
class RaceCourseRenderingTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun theNextGateAndItsEdgeMarkerNeverDrawTogether() {
        // Seven gates on a wide loop; the next one (the seventh: the most pips, the doubled finish posts) at the origin.
        val course = loop(7, 1_300f)
        val next = course.last()
        val race = race(-next.dx, -next.dy, course, step = course.lastIndex)
        for ((width, height) in sizes) for (textScale in listOf(1f, 1.75f)) {
            val model = model(width, height, race)
            var marks = 0
            var markers = 0
            val across = (-110..190 step 3).map { it.toFloat() }
            val alongX = (40..(width - 40) step 50).map { it.toFloat() }
            val alongY = (40..(height - 40) step 50).map { it.toFloat() }
            val positions = alongX.flatMap { x -> across.flatMap { d -> listOf(x to d, x to height - d) } } +
                alongY.flatMap { y -> across.flatMap { d -> listOf(d to y, width - d to y) } }
            positions.forEach { (x, y) ->
                drawAt(width, height, textScale, model, x, y)
                val where = "$width x $height text=$textScale, next gate at ($x, $y)"
                val mark = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                val marker = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                assertEquals(1, mark.size + marker.size, "exactly one of the next gate and its edge marker: $where")
                mark.forEach { box ->
                    assertTrue(box.onScreen(width, height), "whole gate mark on screen: $where")
                    marks++
                }
                val pips = WorldDrawProbe.rects(WorldDrawn.BEACON_PIPS)
                assertEquals(mark.size, pips.size, "the drawn gate's pips: $where")
                pips.forEach { row ->
                    assertTrue(row.onScreen(width, height), "gate pips $row on screen: $where")
                    assertTrue(mark.any { it.encloses(row) }, "gate mark $mark covers its pips $row: $where")
                }
                markers += marker.size
            }
            assertTrue(marks > 0 && markers > 0, "the sweep crosses the hand-over at $width x $height ($marks marks, $markers markers)")
        }
    }

    @Test
    fun voltMarksOnlyTheNextGateAndEveryOtherGateIsStillDrawn() {
        // Five gates around the screen center, the third next; the pulse at its peak draws the next posts in full volt.
        val course = loop(5, 300f)
        val race = race(0f, 0f, course, step = 2)
        val base = model(1_440, 810, race).with("elapsed" to 0.6f)
        val with = render(base)
        val without = render(base.with("sites" to immutableListOf<SiteProjection>(), "trial" to null))
        val volt = KkRolePalette.Default.you.toArgb()
        val gateReach = base.content.sites.race.gateRadius + 30f
        fun box(index: Int): Rect {
            val gate = course[index]
            return Rect(720f + gate.dx - gateReach, 405f + gate.dy - gateReach, 720f + gate.dx + gateReach, 405f + gate.dy + gateReach)
        }
        var voltInNext = 0
        val changed = IntArray(course.size)
        for (y in 0 until 810) for (x in 0 until 1_440) {
            val index = y * 1_440 + x
            if (with[index] == without[index]) continue
            val owner = course.indices.firstOrNull { box(it).contains(androidx.compose.ui.geometry.Offset(x + 0.5f, y + 0.5f)) }
            if (owner != null) changed[owner]++
            // Passed and later gates are bone and mute: over a neutral backdrop they stay neutral, never tinted volt.
            if (owner != null && owner != 2 && chroma(without[index]) < 12) {
                assertTrue(chroma(with[index]) < 20, "gate $owner pixel at ($x, $y) is neutral")
            }
            if (with[index] != volt) continue
            assertEquals(2, owner, "a volt course pixel at ($x, $y) belongs to the next gate")
            voltInNext++
        }
        assertTrue(voltInNext > 300, "the next gate's posts, bar and pips are volt ($voltInNext px)")
        for (index in course.indices) assertTrue(changed[index] > 150, "gate $index is drawn (${changed[index]} px)")
    }

    /** Spread between the strongest and weakest colour channel of an ARGB pixel (0 for greys). */
    private fun chroma(argb: Int): Int {
        val red = argb shr 16 and 0xFF
        val green = argb shr 8 and 0xFF
        val blue = argb and 0xFF
        return maxOf(red, green, blue) - minOf(red, green, blue)
    }

    private fun render(model: GameplayRenderModel): IntArray {
        val bitmap = ImageBitmap(1_440, 810)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
            drawRect(Kk.Ink)
            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer(1f))
        }
        val pixels = IntArray(1_440 * 810)
        bitmap.readPixels(pixels, 0, 0, 1_440, 810)
        return pixels
    }

    /** [count] marks spread over most of a loop of [radius] around the start, like a hashed course. */
    private fun loop(count: Int, radius: Float): ImmutableList<SiteMarkProjection> = List(count) { index ->
        val angle = index * 2f * kotlin.math.PI.toFloat() * 0.85f / count
        SiteMarkProjection(cos(angle) * radius, sin(angle) * radius)
    }.toImmutableList()

    private fun race(x: Float, y: Float, course: ImmutableList<SiteMarkProjection>, step: Int) = SiteProjection(
        SiteKind.GATE_RACE, "Gate race", x, y, true, 12f, step, step.toFloat() / course.size, immutableListOf(), 0f, 0f,
        course = course, clock = 3f, medal = 3,
    )

    /** Draws the world with the world origin at screen ([x], [y]), the camera moved there. */
    private fun drawAt(width: Int, height: Int, textScale: Float, model: GameplayRenderModel, x: Float, y: Float) {
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawWorld(model.with("cameraX" to width * 0.5f - x, "cameraY" to height * 0.5f - y), VisualFxProjection.EMPTY,
                0f, 0f, measurer(textScale))
        }
    }

    private fun measurer(textScale: Float): CanvasTextMeasurer = measurers.getOrPut(textScale) {
        CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), textScale,
            AppLanguage.English, HudTestFonts.typography)
    }

    private val measurers = HashMap<Float, CanvasTextMeasurer>()
    private val scope = CanvasDrawScope()

    // The size passed to draw sets the screen; a 1 px bitmap keeps the sweep fast.
    private val canvas = Canvas(ImageBitmap(1, 1))

    private fun model(width: Int, height: Int, race: SiteProjection): GameplayRenderModel =
        hudTestModel(width.toFloat(), height.toFloat()).with(
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
            "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf(race),
            "trial" to siteTrial(race),
        )

    private fun Rect.onScreen(width: Int, height: Int) = left >= 0f && top >= 0f && right <= width && bottom <= height

    private fun Rect.encloses(other: Rect) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}
