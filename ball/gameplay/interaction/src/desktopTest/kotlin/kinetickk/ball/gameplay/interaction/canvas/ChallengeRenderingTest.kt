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
import kinetickk.ball.gameplay.nucleus.render.EnemyType
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
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Running challenges drawn by the real world renderer: a slalom's next pole and its edge marker never draw together,
 * volt marks only the next pole and its flag points to the side it is passed on; ring challenges draw their progress in
 * volt on their own ring, the untouched volley's lanes run from the centre along the warned aim, and the Ram rite's
 * marked foes carry threat brackets.
 */
class ChallengeRenderingTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)
    private val volt = KkRolePalette.Default.you.toArgb()

    @Test
    fun theNextPoleAndItsEdgeMarkerNeverDrawTogether() {
        // The fourth pole is next and sits at the world origin.
        val course = slalomCourse(along = 360f, spacing = 340f)
        val next = course[3]
        val slalom = slalom(-next.dx, -next.dy, course, step = 3)
        for ((width, height) in sizes) for (textScale in listOf(1f, 1.75f)) {
            val model = model(width, height, slalom)
            var marks = 0
            var markers = 0
            val across = (-60..120 step 4).map { it.toFloat() }
            val alongX = (40..(width - 40) step 60).map { it.toFloat() }
            val alongY = (40..(height - 40) step 60).map { it.toFloat() }
            val positions = alongX.flatMap { x -> across.flatMap { d -> listOf(x to d, x to height - d) } } +
                alongY.flatMap { y -> across.flatMap { d -> listOf(d to y, width - d to y) } }
            positions.forEach { (x, y) ->
                drawAt(width, height, textScale, model, x, y)
                val where = "$width x $height text=$textScale, next pole at ($x, $y)"
                val mark = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                val marker = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                assertEquals(1, mark.size + marker.size, "exactly one of the next pole and its edge marker: $where")
                mark.forEach { box ->
                    assertTrue(box.onScreen(width, height), "whole pole mark on screen: $where")
                    marks++
                }
                markers += marker.size
            }
            assertTrue(marks > 0 && markers > 0, "the sweep crosses the hand-over at $width x $height ($marks marks, $markers markers)")
        }
    }

    @Test
    fun voltMarksOnlyTheNextPoleAndItsFlagPointsToItsPassSide() {
        // A tight slalom along x through the screen, the third pole next. Bit set = passed on the left of the axis,
        // which is +y on screen for a course running along +x.
        val course = slalomCourse(along = 160f, spacing = 220f)
        for ((sides, passDown) in listOf(0b010101 to true, 0b101010 to false)) {
            val slalom = slalom(-620f, -100f, course, step = 2, sides = sides)
            val pixels = siteDrawn(model(1_440, 810, slalom))
            val poleX = 720f - 620f + course[2].dx
            val poleY = 405f - 100f + course[2].dy
            var above = 0
            var below = 0
            for (y in 0 until 810) for (x in 0 until 1_440) {
                if (pixels[y * 1_440 + x] != volt) continue
                val dx = x + 0.5f - poleX
                val dy = y + 0.5f - poleY
                // The pole (26 u tall), its lane bar and its flag 110 u out from the plate (16 u tall) on one side.
                assertTrue(kotlin.math.abs(dx) <= 12f && kotlin.math.abs(dy) <= 13f + 110f + 9f,
                    "a volt pixel at ($x, $y) belongs to the next pole or its lane")
                if (dy < -15f) above++
                if (dy > 15f) below++
            }
            if (passDown) {
                assertTrue(below > 60 && above == 0, "the lane and flag lie below, on the pass side ($below below, $above above)")
            } else {
                assertTrue(above > 60 && below == 0, "the lane and flag lie above, on the pass side ($above above, $below below)")
            }
        }
    }

    @Test
    fun ringChallengesDrawTheirProgressInVoltOnTheirOwnRing() {
        fun ring(kind: SiteKind, radius: Float, progress: Float) =
            SiteProjection(kind, kind.name, 0f, 0f, true, 12f, 0, progress, immutableListOf(), 0f, 0f, radius = radius)
        // A quarter of the brakeless ring (300 u) and of the untouched ring, clockwise from the top.
        for ((kind, arcRadius) in listOf(SiteKind.BRAKELESS_TRIAL to 300f, SiteKind.UNTOUCHED_TRIAL to 300f, SiteKind.VELOCITY_TRIAL to 120f)) {
            val pixels = siteDrawn(model(1_440, 810, ring(kind, radius = if (kind == SiteKind.VELOCITY_TRIAL) 1_800f else 300f, progress = 0.25f)))
            var count = 0
            for (y in 0 until 810) for (x in 0 until 1_440) {
                if (pixels[y * 1_440 + x] != volt) continue
                val dx = x + 0.5f - 720f
                val dy = y + 0.5f - 405f
                val distance = sqrt(dx * dx + dy * dy)
                assertTrue(kotlin.math.abs(distance - arcRadius) <= 4f, "$kind: a volt pixel at ($x, $y) lies on its progress ring")
                assertTrue(dx >= -1f && dy <= 1f, "$kind: the first quarter runs clockwise from the top ($x, $y)")
                count++
            }
            assertTrue(count > 100, "$kind draws its progress ($count px)")
        }
        // The Ram rite shows its count on the panel, never a volt arc.
        val ram = siteDrawn(model(1_440, 810, ring(SiteKind.RAM_TRIAL, radius = 650f, progress = 0.5f)))
        assertTrue(ram.none { it == volt })
        assertTrue(ram.count { it != NOT_DRAWN } > 100, "the Ram rite draws its ring")
    }

    @Test
    fun theUntouchedVolleyLanesRunFromTheCentreAlongTheWarnedAim() {
        // Aim 200 u to the right of the centre: the shots fly 600 u (200 past the aim point plus the overshoot).
        fun untouched(warning: Float) = SiteProjection(SiteKind.UNTOUCHED_TRIAL, "Untouched", -300f, 0f, true, 12f, 0, 0f,
            immutableListOf(), warning, 0f, radius = 650f, aimDx = 200f, aimDy = 0f)
        val with = render(model(1_440, 810, untouched(0.5f)))
        val without = render(model(1_440, 810, untouched(0f)))
        val centerX = 720f - 300f
        var changed = 0
        var farthest = 0f
        for (y in 0 until 810) for (x in 0 until 1_440) {
            val index = y * 1_440 + x
            if (with[index] == without[index]) continue
            val dx = x + 0.5f - centerX
            val dy = y + 0.5f - 405f
            assertTrue(dx >= -2f && kotlin.math.abs(dy) <= 63f, "a telegraph pixel at ($x, $y) lies ahead of the centre on a lane")
            farthest = maxOf(farthest, dx)
            changed++
        }
        assertTrue(changed > 300, "the three lanes are drawn ($changed px)")
        // The dashes end within one dash period of the shots' reach.
        assertTrue(farthest in 584f..602f, "the lanes reach as far as the shots fly ($farthest)")
    }

    @Test
    fun theRamRitesMarkedFoesCarryThreatBrackets() {
        val foe = enemy(1, -200f, 120f)
        val stranger = enemy(2, 260f, -140f)
        fun rite(foes: ImmutableList<Int>) = SiteProjection(SiteKind.RAM_TRIAL, "Ram rite", 0f, 0f, true, 12f, 0, 0f, foes, 0f, 0f,
            radius = 650f)
        val base = model(1_440, 810, rite(immutableListOf())).with("enemies" to immutableListOf(foe, stranger))
        val marked = render(base.with("sites" to immutableListOf(rite(immutableListOf(1)))))
        val unmarked = render(base)
        var nearFoe = 0
        for (y in 0 until 810) for (x in 0 until 1_440) {
            val index = y * 1_440 + x
            if (marked[index] == unmarked[index]) continue
            val dx = x + 0.5f - (720f - 200f)
            val dy = y + 0.5f - (405f + 120f)
            assertTrue(kotlin.math.abs(dx) <= foe.radius + 14f && kotlin.math.abs(dy) <= foe.radius + 14f,
                "a bracket pixel at ($x, $y) surrounds the marked foe")
            nearFoe++
        }
        assertTrue(nearFoe > 40, "the marked foe is bracketed ($nearFoe px)")
    }

    private fun enemy(id: Int, x: Float, y: Float) = EnemyProjection(id, EnemyType.DRIFTER, x, y, 0f, 0f, 30f, 30f, 17f, 0f, 0f,
        0f, 0f, x, y, false)

    /** Six alternating poles along +x from the site: pole k [along] + [spacing]·k ahead, 150 u to its side. */
    private fun slalomCourse(along: Float, spacing: Float): ImmutableList<SiteMarkProjection> = List(6) { index ->
        SiteMarkProjection(along + spacing * index, if (index % 2 == 0) 150f else -150f)
    }.toImmutableList()

    private fun slalom(x: Float, y: Float, course: ImmutableList<SiteMarkProjection>, step: Int, sides: Int = 0b010101) = SiteProjection(
        SiteKind.SLALOM_TRIAL, "Slalom", x, y, true, 12f, step, step / 6f, immutableListOf(), 0f, 0f, course = course, sides = sides,
    )

    /**
     * The pixels the site of [model] adds to its world (the Core, its trail and the rest of the frame drawn alike
     * without it); every other pixel is [NOT_DRAWN].
     */
    private fun siteDrawn(model: GameplayRenderModel): IntArray {
        val with = render(model)
        val without = render(model.with("sites" to immutableListOf<SiteProjection>(), "trial" to null))
        return IntArray(with.size) { if (with[it] == without[it]) NOT_DRAWN else with[it] }
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

    private fun model(width: Int, height: Int, site: SiteProjection): GameplayRenderModel =
        hudTestModel(width.toFloat(), height.toFloat()).with(
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
            "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf(site),
            "trial" to siteTrial(site),
        )

    private fun Rect.onScreen(width: Int, height: Int) = left >= 0f && top >= 0f && right <= width && bottom <= height

    private companion object {
        /** Fully transparent: no frame pixel is, since every frame starts on the opaque ink backdrop. */
        const val NOT_DRAWN = 0
    }
}
