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
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventEruptionEnd
import kinetickk.ball.content.api.ventSecondsToEruption
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
 * gravity knots as Gravitic fields around an ink knot, accelerator gates as two posts with volt bars between them and
 * thermal vents as dark heat discs whose plume flares while they erupt.
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

    /**
     * An accelerator gate: two ink posts with a bone rim around a volt point, a faint line between them and volt bars
     * across it, all inside its body circle; the look is the same mirrored across the gate's line and through its centre,
     * so nothing in it points one way (a gate works both ways). At density 1 and 2, for a gate facing east and one facing
     * south.
     */
    @Test
    fun acceleratorGatesDrawSymmetricVoltBarsBetweenTwoPostsInsideTheirBody() {
        val you = KkRolePalette.Default.you
        // Volt, not bone: bone also lies more than half way toward volt from the ink, but volt holds little blue.
        fun volt(color: Color) = towards(color, you) && color.green - color.blue > 0.3f
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val kinds = base.content.obstacles.kinds
            val radius = 100f
            val halfSpan = radius - kinds.gatePostRadius
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            // Seed 0 faces east (posts north and south of the centre), seed 16 384 faces south (posts west and east).
            for ((seed, postsAlongY) in listOf(0 to true, 16_384 to false)) {
                val gate = ObstacleProjection(9L, ObstacleKind.ACCEL_GATE, base.cameraX - 180f, base.cameraY, radius, seed)
                // At the top of the bars' pulse (its phase is offset by the seed's remainder of 5).
                val twoPi = 2f * kotlin.math.PI.toFloat()
                val peak = ((kotlin.math.PI.toFloat() / 2f - seed % 5) % twoPi + twoPi) % twoPi / 2.4f
                val timed = base.with("elapsed" to peak)
                val empty = renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density)
                val drawn = renderWorld(timed.with("obstacles" to immutableListOf(gate)), density)
                val label = "gate seed $seed @$density"
                var farthest = 0f
                for (y in 0 until empty.height) {
                    for (x in 0 until empty.width) {
                        if (empty[x, y] != drawn[x, y]) farthest = maxOf(farthest, kotlin.math.hypot(x + 0.5f - centerX, y + 0.5f - centerY))
                    }
                }
                assertTrue(farthest <= (radius + 2f) * density, "$label reaches $farthest past its body")
                fun post(sign: Float, offset: Float = 0f): Pair<Float, Float> =
                    if (postsAlongY) centerX + offset * density to centerY + sign * halfSpan * density
                    else centerX + sign * halfSpan * density to centerY + offset * density
                for (sign in listOf(-1f, 1f)) {
                    val (px, py) = post(sign)
                    assertTrue(volt(drawn.at(px, py)), "$label post point in volt")
                    val (ix, iy) = post(sign, 7f)
                    assertTrue(near(drawn.at(ix, iy), Kk.Ink2), "$label post body in ink")
                }
                // A bar 0.6 of the half-span out from the centre, across the line.
                val (barX, barY) = if (postsAlongY) centerX to centerY + 0.6f * halfSpan * density else centerX + 0.6f * halfSpan * density to centerY
                assertTrue(volt(drawn.at(barX, barY)), "$label bar in volt")
                // Between two bars and off the line: nothing.
                val (openX, openY) = if (postsAlongY) centerX + 10f * density to centerY + 0.4f * halfSpan * density
                else centerX + 0.4f * halfSpan * density to centerY + 10f * density
                assertEquals(empty.at(openX, openY), drawn.at(openX, openY), "$label open between the bars")
                // Mirrored across the gate's line and through its centre, every mark the gate makes has its counterpart.
                val span = ((radius + 2f) * density).toInt()
                val cx = centerX.toInt()
                val cy = centerY.toInt()
                for (dy in -span until span) {
                    for (dx in -span until span) {
                        if (change(empty[cx + dx, cy + dy], drawn[cx + dx, cy + dy]) < 0.25f) continue
                        val (ax, ay) = if (postsAlongY) cx - 1 - dx to cy + dy else cx + dx to cy - 1 - dy
                        assertTrue(markedNear(empty, drawn, ax, ay), "$label is mirrored across its line at ($dx, $dy)")
                        assertTrue(markedNear(empty, drawn, cx - 1 - dx, cy - 1 - dy), "$label is mirrored through its centre at ($dx, $dy)")
                    }
                }
            }
        }
    }

    /**
     * A thermal vent: a dark ink disc with a heat rim that stays within its body while quiet, swells a heat glow at its
     * centre before it erupts, and while it erupts flares a heat plume out to its plume's edge (beyond its body, which
     * stays a dark disc).
     */
    @Test
    fun thermalVentsGlowBeforeTheyEruptAndFlareTheirPlumeBeyondTheirBodyWhileErupting() {
        val heat = KkRolePalette.Default.heat
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val kinds = base.content.obstacles.kinds
            val radius = 80f
            val seed = 23
            val plume = radius + kinds.ventPlumeMargin
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            val vent = ObstacleProjection(11L, ObstacleKind.THERMAL_VENT, base.cameraX - 180f, base.cameraY, radius, seed)
            fun frames(time: Float): Pair<PixelMap, PixelMap> {
                val timed = base.with("elapsed" to time)
                return renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density) to
                    renderWorld(timed.with("obstacles" to immutableListOf(vent)), density)
            }
            fun farthest(empty: PixelMap, drawn: PixelMap): Float {
                var farthest = 0f
                for (y in 0 until empty.height) {
                    for (x in 0 until empty.width) {
                        if (empty[x, y] != drawn[x, y]) farthest = maxOf(farthest, kotlin.math.hypot(x + 0.5f - centerX, y + 0.5f - centerY))
                    }
                }
                return farthest
            }
            val label = "vent @$density"
            val (quietEmpty, quiet) = frames(ventTime(kinds, seed) { kinds.ventSecondsToEruption(it, seed) > kinds.ventTelegraphSeconds + 0.3f })
            assertTrue(farthest(quietEmpty, quiet) <= (radius + 2f) * density, "$label stays within its body while quiet")
            assertTrue(near(quiet.at(centerX - radius * 0.9f * density, centerY), Kk.Ink1, 0.006f), "$label ground disc in ink 1")
            assertTrue(leans(quiet.at(centerX - radius * density, centerY), heat, 0.3f), "$label heat rim")
            val (_, telegraph) = frames(ventTime(kinds, seed) { kinds.ventSecondsToEruption(it, seed) in 0.05f..0.2f })
            assertTrue(telegraph.at(centerX, centerY).red > quiet.at(centerX, centerY).red + 0.05f, "$label glows before it erupts")
            // Early, midway and late in an eruption: the plume and its rising rings stay within the plume's edge.
            for (left in listOf(0.8f, 0.5f, 0.1f)) {
                val (eruptingEmpty, erupting) = frames(ventTime(kinds, seed) {
                    kinds.ventErupting(it, seed) && kinds.ventEruptionEnd(it, seed) - it in (left - 0.05f)..(left + 0.05f)
                })
                val reach = farthest(eruptingEmpty, erupting)
                assertTrue(reach <= (plume + 2f) * density, "$label erupting ($left s left) reaches $reach past its plume")
                assertTrue(reach >= (plume - 2f) * density, "$label erupting ($left s left) draws out to its plume's edge: $reach")
            }
            val (_, erupting) = frames(ventTime(kinds, seed) { kinds.ventErupting(it, seed) && kinds.ventErupting(it - 0.2f, seed) })
            assertTrue(leans(erupting.at(centerX, centerY - (radius + 0.5f * kinds.ventPlumeMargin) * density), heat, 0.12f),
                "$label plume beyond its body")
            assertTrue(near(erupting.at(centerX - radius * 0.9f * density, centerY), Kk.Ink1, 0.006f), "$label body stays a dark disc")
            // A vent just off the left edge, its body out of view: its erupting plume still reaches into the view.
            val offscreen = vent.copy(x = base.cameraX - 480f - radius - 30f)
            val eruptingTime = ventTime(kinds, seed) { kinds.ventErupting(it, seed) && kinds.ventErupting(it - 0.2f, seed) }
            val edgeEmpty = renderWorld(base.with("elapsed" to eruptingTime, "obstacles" to immutableListOf<ObstacleProjection>()), density)
            val edge = renderWorld(base.with("elapsed" to eruptingTime, "obstacles" to immutableListOf(offscreen)), density)
            assertTrue(leans(edge.at(2f * density, centerY), heat, 0.12f) && edge.at(2f * density, centerY) != edgeEmpty.at(2f * density, centerY),
                "$label plume reaches in from off screen")
        }
    }

    /** The first run time in 0..8 s, on a 1/120 s grid, that [accept] takes. */
    private fun ventTime(kinds: ObstacleKindTuning, seed: Int, accept: (Float) -> Boolean): Float {
        for (step in 0 until 960) {
            val time = 0.5f + step / 120f
            if (accept(time)) return time
        }
        error("no vent time for seed $seed in ${kinds.ventPeriodSeconds} s")
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

    private fun near(actual: Color, expected: Color, tolerance: Float = 0.03f): Boolean =
        kotlin.math.abs(actual.red - expected.red) < tolerance && kotlin.math.abs(actual.green - expected.green) < tolerance &&
            kotlin.math.abs(actual.blue - expected.blue) < tolerance

    /** The largest channel change from [before] to [after]. */
    private fun change(before: Color, after: Color): Float = maxOf(
        kotlin.math.abs(after.red - before.red), kotlin.math.abs(after.green - before.green), kotlin.math.abs(after.blue - before.blue),
    )

    /**
     * Whether the look changed a pixel within one of ([x], [y]): antialiasing rasterises mirrored edges a little
     * differently, so a mirrored mark is looked for in the pixel's neighbourhood.
     */
    private fun markedNear(empty: PixelMap, drawn: PixelMap, x: Int, y: Int): Boolean =
        (-1..1).any { dy -> (-1..1).any { dx -> change(empty[x + dx, y + dy], drawn[x + dx, y + dy]) >= 0.1f } }

    /** At least [share] of the way from the ink background toward [expected]. */
    private fun leans(actual: Color, expected: Color, share: Float): Boolean {
        val base = Kk.Ink
        val ex = expected.red - base.red
        val ey = expected.green - base.green
        val ez = expected.blue - base.blue
        return ((actual.red - base.red) * ex + (actual.green - base.green) * ey + (actual.blue - base.blue) * ez) /
            (ex * ex + ey * ey + ez * ez) >= share
    }

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
