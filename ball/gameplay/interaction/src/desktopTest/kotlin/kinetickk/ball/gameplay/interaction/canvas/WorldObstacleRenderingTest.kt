// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Obstacles draw in the world layer: asteroids as ink rocks with bone rims, bumpers as polarity rings, unstable rocks hot,
 * gravity knots as Gravitic fields around an ink knot.
 */
class WorldObstacleRenderingTest {
    @Test
    fun outlinesAreCachedPerShapeAndHaveEightToTwelveCorners() {
        assertSame(ObstaclePaths.outline(17, 90f), ObstaclePaths.outline(17, 90f))
        val corners = (0 until 200).map { ObstaclePaths.corners(it * 7_919 - 300_000) }
        assertTrue(corners.all { it in 8..12 })
        assertEquals((8..12).toSet(), corners.toSet())
    }

    /**
     * Collisions use the full radius, so the drawn rim must lie on or just outside that circle: no edge may
     * sag inside it (a hidden shell would hit a Core that visibly clears the rock), and no corner may poke
     * far past it. Samples the drawn path itself.
     */
    @Test
    fun outlinesHugTheCollisionCircle() {
        val radius = 100f
        val measure = PathMeasure()
        var closest = Float.MAX_VALUE
        var farthest = 0f
        for (index in 0 until 600) {
            val seed = index * 104_729 - 31_000_000
            measure.setPath(ObstaclePaths.outline(seed, radius), forceClosed = true)
            val length = measure.length
            for (sample in 0 until 720) {
                val point = measure.getPosition(length * sample / 720f)
                val distance = point.getDistance()
                closest = minOf(closest, distance)
                farthest = maxOf(farthest, distance)
            }
        }
        assertTrue(closest >= radius * 0.97f, "an edge sags inside the collision circle: $closest")
        assertTrue(farthest <= radius * ObstaclePaths.OUTLINE_MAX_REACH, "a corner reaches too far past it: $farthest")
    }

    @Test
    fun obstaclesDrawWhereTheWorldPutsThemAtEveryDensity() {
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f)
            val model = base.with(
                "viewportDensity" to density,
                "uiScale" to density,
                "obstacles" to immutableListOf(
                    ObstacleProjection(1L, ObstacleKind.ASTEROID, base.cameraX + 200f, base.cameraY, 70f, 9),
                    ObstacleProjection(2L, ObstacleKind.UNSTABLE, base.cameraX - 220f, base.cameraY + 120f, 80f, 21),
                    ObstacleProjection(3L, ObstacleKind.BUMPER, base.cameraX, base.cameraY - 200f, 34f, 0),
                ),
            )
            val pixels = renderWorld(model, density)
            val roles = KkRolePalette.Default
            val centerX = 480f * density
            val centerY = 300f * density
            // The asteroid's body is ink 3, its rim bone; its centre is 200 dp right of the camera.
            assertTrue(near(pixels.at(centerX + (200f - 30f) * density, centerY), Kk.Ink3), "asteroid body @$density")
            // The unstable rock's core pulses in heat.
            assertTrue(towards(pixels.at(centerX - 220f * density, centerY + 120f * density), roles.heat), "unstable core @$density")
            // The bumper's ring is polarity at its radius.
            assertTrue(towards(pixels.at(centerX, centerY + (-200f + 34f) * density), roles.pol), "bumper ring @$density")
        }
    }

    /**
     * A gravity knot: a Gravitic rim around an ink disc, a filled Gravitic core for a Mass Knot and an open ring around a
     * bone point for a Repulsor, at density 1 and 2; nothing it draws reaches past its field's edge, the edge itself is
     * drawn, and its rings contract onto a Mass Knot and spread from a Repulsor.
     */
    @Test
    fun gravityKnotsDrawGraviticInsideTheirFieldWithRingsDriftingByPolarity() {
        val gravitic = KkRolePalette.Default.element(3)
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val influence = base.content.obstacles.kinds.wellInfluence
            val body = base.content.obstacles.kinds.wellCoreRadius
            // 180 dp left of the camera, so the whole field fits the view and the Core sits east of the knot.
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            for (repels in listOf(false, true)) {
                val knot = ObstacleProjection(7L, ObstacleKind.GRAVITY_WELL, base.cameraX - 180f, base.cameraY, body, 5, repels = repels)
                fun frame(time: Float): Pair<PixelMap, PixelMap> {
                    val timed = base.with("elapsed" to time)
                    return renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density) to
                        renderWorld(timed.with("obstacles" to immutableListOf(knot)), density)
                }
                val label = "${if (repels) "Repulsor" else "Mass Knot"} @$density"
                for (time in listOf(0f, 1.3f, 2.9f)) {
                    val (empty, drawn) = frame(time)
                    var farthest = 0f
                    for (y in 0 until empty.height) {
                        for (x in 0 until empty.width) {
                            if (empty[x, y] == drawn[x, y]) continue
                            farthest = maxOf(farthest, kotlin.math.hypot(x + 0.5f - centerX, y + 0.5f - centerY))
                        }
                    }
                    assertTrue(farthest <= (influence + 2f) * density, "$label at $time reaches $farthest past its field")
                    assertTrue(farthest >= (influence - 2f) * density, "$label at $time draws its field's edge: $farthest")
                    assertTrue(towards(drawn.at(centerX + body * density, centerY), gravitic), "$label rim")
                    assertTrue(near(drawn.at(centerX - body * 0.7f * density, centerY), Kk.Ink2), "$label ink body")
                    if (repels) {
                        assertTrue(towards(drawn.at(centerX, centerY), Kk.Bone), "$label bone point")
                        assertTrue(near(drawn.at(centerX - body * 0.3f * density, centerY), Kk.Ink2), "$label open ring, no fill")
                    } else {
                        assertTrue(towards(drawn.at(centerX - body * 0.2f * density, centerY), gravitic), "$label filled core")
                    }
                }
                // A tenth of a second later every ring west of the knot sits further in (Mass Knot) or out (Repulsor).
                val (emptyBefore, before) = frame(0.4f)
                val (emptyAfter, after) = frame(0.5f)
                val earlier = ringsWest(emptyBefore, before, centerX, centerY, density, body + 4f, influence - 4f)
                val later = ringsWest(emptyAfter, after, centerX, centerY, density, body + 4f, influence - 4f)
                assertEquals(3, earlier.size, "$label rings at 0.4 s: $earlier")
                assertEquals(3, later.size, "$label rings at 0.5 s: $later")
                for (ring in earlier) {
                    val moved = later.minBy { kotlin.math.abs(it - ring) } - ring
                    assertTrue(if (repels) moved > 1f else moved < -1f, "$label ring at $ring moved by $moved")
                }
            }
        }
    }

    /** Midpoints (dp from the knot's centre) of the runs of pixels the knot changed along the ray west of it. */
    private fun ringsWest(empty: PixelMap, drawn: PixelMap, centerX: Float, centerY: Float, density: Float, from: Float, to: Float): List<Float> {
        val rings = mutableListOf<Float>()
        val y = centerY.toInt()
        var runStart = -1
        val first = kotlin.math.ceil(from * density).toInt()
        val last = (to * density).toInt()
        for (offset in first..last + 1) {
            val x = centerX.toInt() - offset
            val changed = offset <= last && empty[x, y] != drawn[x, y]
            if (changed && runStart < 0) runStart = offset
            if (!changed && runStart >= 0) {
                rings += (runStart + offset - 1) * 0.5f / density
                runStart = -1
            }
        }
        return rings
    }

    private fun renderWorld(model: GameplayRenderModel, density: Float): PixelMap {
        val width = (960 * density).toInt()
        val height = (600 * density).toInt()
        val bitmap = ImageBitmap(width, height)
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr), 1f,
            AppLanguage.English)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawWorld(model.with("screenWidth" to 960f, "screenHeight" to 600f), VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        return bitmap.toPixelMap()
    }

    private fun PixelMap.at(x: Float, y: Float): Color = this[x.toInt(), y.toInt()]

    private fun near(actual: Color, expected: Color): Boolean =
        kotlin.math.abs(actual.red - expected.red) < 0.03f && kotlin.math.abs(actual.green - expected.green) < 0.03f &&
            kotlin.math.abs(actual.blue - expected.blue) < 0.03f

    /** More than half way from the ink background toward [expected]. */
    private fun towards(actual: Color, expected: Color): Boolean {
        val base = Kk.Ink
        val ex = expected.red - base.red
        val ey = expected.green - base.green
        val ez = expected.blue - base.blue
        return ((actual.red - base.red) * ex + (actual.green - base.green) * ey + (actual.blue - base.blue) * ez) /
            (ex * ex + ey * ey + ez * ez) > 0.5f
    }
}
