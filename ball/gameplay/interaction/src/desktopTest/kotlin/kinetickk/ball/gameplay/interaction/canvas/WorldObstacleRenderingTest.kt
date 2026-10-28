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
import kinetickk.ball.content.api.phaseShardSolid
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

    /** A crystal's facet hexagon lies on or just inside its collision circle, like an asteroid's outline, at any rotation. */
    @Test
    fun crystalFacetsHugTheCollisionCircle() {
        val radius = 100f
        val measure = PathMeasure()
        var closest = Float.MAX_VALUE
        var farthest = 0f
        for (index in 0 until 300) {
            measure.setPath(ObstaclePaths.crystalFacets(index * 104_729 - 31_000_000, radius), forceClosed = true)
            val length = measure.length
            for (sample in 0 until 360) {
                val distance = measure.getPosition(length * sample / 360f).getDistance()
                closest = minOf(closest, distance)
                farthest = maxOf(farthest, distance)
            }
        }
        assertTrue(closest >= radius * 0.97f, "an edge sags inside the collision circle: $closest")
        assertTrue(farthest <= radius * ObstaclePaths.OUTLINE_MAX_REACH, "a corner reaches too far past it: $farthest")
    }

    /**
     * A crystal: an ink hexagon with a Cryo rim at its edges, an inner facet and Cryo spokes out to alternate corners only,
     * all within its outline reach; at density 1 and 2.
     */
    @Test
    fun crystalsDrawAnInkHexagonWithACryoRimAndSpokesToAlternateCorners() {
        val cryo = KkRolePalette.Default.element(1)
        // Cryo, not bone: bone also lies more than half way toward the pale Cryo from the ink, but Cryo is blue.
        fun cryoLike(color: Color, share: Float, blue: Float) = leans(color, cryo, share) && color.blue - color.red > blue
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val radius = 90f
            val seed = 21
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            val crystal = ObstacleProjection(12L, ObstacleKind.CRYSTAL, base.cameraX - 180f, base.cameraY, radius, seed)
            val empty = renderWorld(base.with("obstacles" to immutableListOf<ObstacleProjection>()), density)
            val drawn = renderWorld(base.with("obstacles" to immutableListOf(crystal)), density)
            val label = "crystal @$density"
            var farthest = 0f
            for (y in 0 until empty.height) {
                for (x in 0 until empty.width) {
                    if (empty[x, y] != drawn[x, y]) farthest = maxOf(farthest, kotlin.math.hypot(x + 0.5f - centerX, y + 0.5f - centerY))
                }
            }
            assertTrue(farthest <= (radius * ObstaclePaths.OUTLINE_MAX_REACH + 2f) * density, "$label reaches $farthest")
            assertTrue(near(drawn.at(centerX, centerY), Kk.Ink2), "$label ink body")
            val angle = crystalFacetAngle(seed)
            fun pixel(direction: Float, reach: Float): Pair<Int, Int> =
                (centerX + kotlin.math.cos(direction) * reach * density).toInt() to (centerY + kotlin.math.sin(direction) * reach * density).toInt()
            /** The pixels within one of the point [reach] from the centre along [direction]. */
            fun around(map: PixelMap, direction: Float, reach: Float): List<Color> {
                val (x, y) = pixel(direction, reach)
                return (-1..1).flatMap { dy -> (-1..1).map { dx -> map[x + dx, y + dy] } }
            }
            val step = 2f * kotlin.math.PI.toFloat() / CRYSTAL_CORNERS
            val edge = angle + step * 0.5f
            assertTrue(around(drawn, edge, radius * CRYSTAL_APOTHEM).any { cryoLike(it, 0.5f, 0.1f) }, "$label Cryo rim at an edge")
            assertTrue(around(drawn, edge, radius * CRYSTAL_APOTHEM - 6f).all { near(it, Kk.Ink2) }, "$label filled up to its rim")
            assertEquals(around(empty, edge, radius * CRYSTAL_APOTHEM + 4f), around(drawn, edge, radius * CRYSTAL_APOTHEM + 4f),
                "$label open just past an edge")
            // Spokes run to the first, third and fifth corners; the others stay plain ink.
            val spokeReach = radius * 0.72f * 1.1547005f
            for (corner in 0 until CRYSTAL_CORNERS) {
                val pixels = around(drawn, angle + corner * step, spokeReach)
                if (corner % 2 == 0) {
                    assertTrue(pixels.any { cryoLike(it, 0.25f, 0.04f) }, "$label spoke to corner $corner")
                } else {
                    assertTrue(pixels.all { near(it, Kk.Ink2) }, "$label no spoke to corner $corner")
                }
            }
        }
    }

    /**
     * A derelict mine: three ink pips with threat stubs and lights and no body (its middle stays open), within its body
     * circle while dormant; armed, a threat ring at its blast's edge, reaching in from off screen too, and its look moves
     * from frame to frame as its lights blink.
     */
    @Test
    fun derelictMinesDrawThreatPipsWithoutABodyAndRingTheirBlastOnceArmed() {
        val threat = KkRolePalette.Default.threat
        // Threat, not bone: bone also projects onto the threat hue from the ink, but threat is red over green.
        fun threatLike(color: Color, share: Float, red: Float) = leans(color, threat, share) && color.red - color.green > red
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val kinds = base.content.obstacles.kinds
            val radius = 60f
            val seed = 22
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            val dormant = ObstacleProjection(13L, ObstacleKind.DERELICT_MINE, base.cameraX - 180f, base.cameraY, radius, seed)
            fun frames(mine: ObstacleProjection, time: Float): Pair<PixelMap, PixelMap> {
                val timed = base.with("elapsed" to time)
                return renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density) to
                    renderWorld(timed.with("obstacles" to immutableListOf(mine)), density)
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
            val label = "mine @$density"
            // At the top of the dormant lights' slow breath.
            val (empty, quiet) = frames(dormant, kotlin.math.PI.toFloat() / 4f)
            assertTrue(farthest(empty, quiet) <= (radius + 2f) * density, "$label stays within its body while dormant")
            assertEquals(empty.at(centerX, centerY), quiet.at(centerX, centerY), "$label has no body")
            forEachMinePip(seed, radius) { x, y, angle ->
                val px = centerX + x * density
                val py = centerY + y * density
                assertTrue(threatLike(quiet.at(px, py), 0.4f, 0.3f), "$label threat light at a pip")
                assertTrue(near(quiet.at(px + kotlin.math.cos(angle) * 5f * density, py + kotlin.math.sin(angle) * 5f * density), Kk.Ink2),
                    "$label ink pip")
                val stub = angle + kotlin.math.PI.toFloat() / 4f
                val reach = (MINE_STUB_TO + MINE_PIP_RADIUS) * 0.5f * density
                val stubX = (px + kotlin.math.cos(stub) * reach).toInt()
                val stubY = (py + kotlin.math.sin(stub) * reach).toInt()
                assertTrue((-1..1).any { dy -> (-1..1).any { dx -> threatLike(quiet[stubX + dx, stubY + dy], 0.3f, 0.2f) } },
                    "$label threat stub")
            }
            // Armed half way through its fuse: a ring at the blast's edge.
            val armedAt = 2f
            val armed = dormant.copy(fuseAt = armedAt + kinds.mineFuseSeconds * 0.5f)
            val (armedEmpty, ringing) = frames(armed, armedAt)
            val reach = farthest(armedEmpty, ringing)
            assertTrue(reach in (kinds.mineBlastRadius - 2f) * density..(kinds.mineBlastRadius + 2f) * density,
                "$label armed rings its blast: $reach")
            val ringY = (centerY - kinds.mineBlastRadius * density).toInt()
            assertTrue((-1..1).any { dy -> threatLike(ringing[centerX.toInt(), ringY + dy], 0.25f, 0.15f) }, "$label threat ring")
            val (_, later) = frames(armed, armedAt + 1f / 48f)
            assertTrue((0 until ringing.height).any { y -> (0 until ringing.width).any { x -> ringing[x, y] != later[x, y] } },
                "$label an armed mine's frames differ")
            // An armed mine just off the left edge, its pips out of view: its ring still reaches in.
            val offscreen = armed.copy(x = base.cameraX - 480f - radius - 30f)
            val (edgeEmpty, edge) = frames(offscreen, armedAt)
            assertTrue((0 until edge.height).any { y -> edge[(2f * density).toInt(), y] != edgeEmpty[(2f * density).toInt(), y] },
                "$label its ring reaches in from off screen")
        }
    }

    /**
     * A phase shard is drawn filled exactly while its cycle has it solid, and the collisions read the same cycle. Solid,
     * it has a full ink fill and an unbroken Void edge. As a ghost it has only a dashed outline and its middle stays
     * open. In its telegraph a fill fades in, held below full, under dashes that brighten. Sampled across a cycle and
     * one 480th of a second either side of both turns, at density 1 and 2.
     */
    @Test
    fun phaseShardsDrawFilledExactlyWhileTheirCycleIsSolid() {
        val void = KkRolePalette.Default.element(6)
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val kinds = base.content.obstacles.kinds
            val radius = 80f
            val seed = 31
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            val shard = ObstacleProjection(15L, ObstacleKind.PHASE_SHARD, base.cameraX - 180f, base.cameraY, radius, seed)
            val (materialises, fades) = phaseTurns(kinds, seed)
            val telegraph = kinds.phaseTelegraphSeconds
            val nudge = 1f / 480f
            val ghost = materialises - telegraph - 0.2f
            val early = materialises - telegraph * 0.9f
            val midway = materialises - telegraph * 0.5f
            val lastGhost = materialises - nudge
            val firstSolid = materialises + nudge
            val samples = listOf(
                ghost to "a ghost",
                early to "early in its telegraph",
                midway to "midway through its telegraph",
                lastGhost to "just before it materialises",
                firstSolid to "just materialised",
                (materialises + fades) * 0.5f to "solid",
                fades - nudge to "just before it fades",
                fades + nudge to "just faded",
            )
            val looks = samples.associate { (time, label) ->
                val timed = base.with("elapsed" to time)
                val empty = renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density)
                val drawn = renderWorld(timed.with("obstacles" to immutableListOf(shard)), density)
                val look = PhaseLook(
                    fill = fillShare(empty, drawn, centerX.toInt(), centerY.toInt()),
                    edge = voidEdge(empty, drawn, ObstaclePaths.outline(seed, radius), centerX, centerY, density),
                    centerUntouched = empty.at(centerX, centerY) == drawn.at(centerX, centerY),
                )
                val solid = kinds.phaseShardSolid(time, seed)
                val name = "shard $label @$density (solid $solid): $look"
                assertEquals(solid, look.fill >= 0.92f, "$name is drawn filled exactly while it collides")
                if (solid) assertEquals(0, look.gaps, "$name has an unbroken edge")
                else assertTrue(look.gaps >= look.samples * 0.15f, "$name has a dashed outline")
                time to look
            }
            for ((time, label) in samples) assertEquals(label.startsWith("just materialised") || label == "solid" ||
                label == "just before it fades", kinds.phaseShardSolid(time, seed), "the sample $label @$density")
            val quiet = looks.getValue(ghost)
            assertTrue(quiet.centerUntouched, "a ghost shard has no fill @$density")
            assertTrue(looks.getValue(fades + nudge).centerUntouched, "a shard that just faded has no fill @$density")
            val start = looks.getValue(early)
            val half = looks.getValue(midway)
            val late = looks.getValue(lastGhost)
            assertTrue(start.fill in 0.02f..0.2f, "early in its telegraph the fill has only begun: $start @$density")
            assertTrue(half.fill in 0.2f..0.6f && half.fill > start.fill + 0.15f, "half way through its telegraph the fill is fading in: $half @$density")
            assertTrue(late.fill in 0.65f..0.88f && late.fill > half.fill + 0.15f,
                "about to materialise, the fill is near full but held below it: $late @$density")
            assertTrue(late.brightest > quiet.brightest + 0.2f, "its dashes brighten before it materialises: $quiet, then $late @$density")
            assertTrue(void.blue - void.green > 0.2f, "Void rises more in blue than in green over ink, as the edge check reads it")
        }
    }

    /**
     * A ghost phase shard draws only its dashed outline, on its asteroid outline around the collision circle (nothing
     * inside 0.97 radii or past the outline's reach), for shapes of several corner counts at density 1 and 2.
     */
    @Test
    fun aGhostPhaseShardDrawsOnlyADashedOutlineOnItsCollisionCircle() {
        for (density in listOf(1f, 2f)) {
            val base = hudTestModel(960f, 600f).with("viewportDensity" to density, "uiScale" to density)
            val kinds = base.content.obstacles.kinds
            val centerX = (480f - 180f) * density
            val centerY = 300f * density
            for ((seed, radius) in listOf(31 to 80f, 32 to 46f, 33 to 140f)) {
                val (materialises, _) = phaseTurns(kinds, seed)
                val timed = base.with("elapsed" to materialises - kinds.phaseTelegraphSeconds - 0.2f)
                val shard = ObstacleProjection(16L, ObstacleKind.PHASE_SHARD, base.cameraX - 180f, base.cameraY, radius, seed)
                val empty = renderWorld(timed.with("obstacles" to immutableListOf<ObstacleProjection>()), density)
                val drawn = renderWorld(timed.with("obstacles" to immutableListOf(shard)), density)
                val label = "ghost seed $seed r $radius @$density"
                var closest = Float.MAX_VALUE
                var farthest = 0f
                for (y in 0 until empty.height) {
                    for (x in 0 until empty.width) {
                        if (empty[x, y] == drawn[x, y]) continue
                        val distance = kotlin.math.hypot(x + 0.5f - centerX, y + 0.5f - centerY)
                        closest = minOf(closest, distance)
                        farthest = maxOf(farthest, distance)
                    }
                }
                assertTrue(closest >= (radius * 0.97f - 2f) * density, "$label draws inside its outline at $closest")
                assertTrue(farthest <= (radius * ObstaclePaths.OUTLINE_MAX_REACH + 2f) * density, "$label reaches $farthest")
                val edge = voidEdge(empty, drawn, ObstaclePaths.outline(seed, radius), centerX, centerY, density)
                assertTrue(edge.gaps >= edge.samples * 0.15f && edge.marked >= edge.samples * 0.35f, "$label is dashed in Void: $edge")
            }
        }
    }

    /** What a phase shard's look shows: [fill] at its centre (see [fillShare]) and its [edge]. */
    private data class PhaseLook(val fill: Float, val edge: VoidEdge, val centerUntouched: Boolean) {
        val gaps: Int get() = edge.gaps
        val samples: Int get() = edge.samples
        val brightest: Float get() = edge.brightest
    }

    /**
     * Void marks along an outline: of [samples] points spaced evenly along it, [marked] have a Void mark within one pixel
     * (a rise in blue of at least 0.08 that outruns the rise in green by 0.03, as Void over ink does and bone and the ink
     * rim do not) and [gaps] have none; [brightest] is the largest such rise in blue.
     */
    private data class VoidEdge(val samples: Int, val marked: Int, val brightest: Float) {
        val gaps: Int get() = samples - marked
    }

    private fun voidEdge(empty: PixelMap, drawn: PixelMap, outline: androidx.compose.ui.graphics.Path, centerX: Float, centerY: Float, density: Float): VoidEdge {
        val measure = PathMeasure()
        measure.setPath(outline, forceClosed = true)
        val samples = 240
        var marked = 0
        var brightest = 0f
        for (sample in 0 until samples) {
            val point = measure.getPosition(measure.length * sample / samples)
            val x = (centerX + point.x * density).toInt()
            val y = (centerY + point.y * density).toInt()
            var mark = false
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val before = empty[x + dx, y + dy]
                    val after = drawn[x + dx, y + dy]
                    val blue = after.blue - before.blue
                    if (blue >= 0.08f && blue - (after.green - before.green) >= 0.03f) {
                        mark = true
                        brightest = maxOf(brightest, blue)
                    }
                }
            }
            if (mark) marked++
        }
        return VoidEdge(samples, marked, brightest)
    }

    /**
     * How far the look moved the pixels within two of ([x], [y]) from the backdrop toward ink 3, the shard's fill: 0 for
     * untouched, 1 for a full fill.
     */
    private fun fillShare(empty: PixelMap, drawn: PixelMap, x: Int, y: Int): Float {
        var sum = 0f
        var count = 0
        for (dy in -2..2) {
            for (dx in -2..2) {
                val before = empty[x + dx, y + dy]
                val after = drawn[x + dx, y + dy]
                for ((from, to, fill) in listOf(
                    Triple(before.red, after.red, Kk.Ink3.red),
                    Triple(before.green, after.green, Kk.Ink3.green),
                    Triple(before.blue, after.blue, Kk.Ink3.blue),
                )) {
                    if (fill - from < 0.03f) continue
                    sum += (to - from) / (fill - from)
                    count++
                }
            }
        }
        check(count > 0) { "the backdrop at ($x, $y) is as light as the fill" }
        return sum / count
    }

    /**
     * The first time from 2 s on, on a 1/960 s grid, at which the phase shard with [seed] reads solid after reading a
     * ghost, and the first time after that at which it reads a ghost again.
     */
    private fun phaseTurns(kinds: ObstacleKindTuning, seed: Int): Pair<Float, Float> {
        var materialises = -1f
        for (step in 0 until 8_000) {
            val time = 2f + step / 960f
            val next = 2f + (step + 1) / 960f
            if (materialises < 0f) {
                if (!kinds.phaseShardSolid(time, seed) && kinds.phaseShardSolid(next, seed)) materialises = next
            } else if (kinds.phaseShardSolid(time, seed) && !kinds.phaseShardSolid(next, seed)) {
                return materialises to next
            }
        }
        error("no phase turns for seed $seed")
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
