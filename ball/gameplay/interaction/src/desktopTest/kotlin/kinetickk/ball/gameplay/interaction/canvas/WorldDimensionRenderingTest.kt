// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.HaloProjection
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.RiftLock
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkLayerHues
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Dimensions drawn by the real backdrop and world renderers: realspace untouched by every dimension fact, each layer's
 * ground in its role and never hazard, black and white at the Event Horizon tier, motes that belong to the world, rifts
 * with their locked and glitch looks and blunt sigils, the ring to fly through next, and the markers beside the sites'.
 */
class WorldDimensionRenderingTest {
    private val width = 1_440
    private val height = 810
    private val windowLayers = listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)
    private val palettes = listOf(KkRolePalette.Default, KkRolePalette.Mono, KkRolePalette.Protan)

    @Test
    fun realspaceIgnoresEveryVisitFactAndNeverDrawsADimensionBackdrop() {
        // A realspace projection carrying everything a visit publishes (Gameplay never does): the backdrop and the world
        // must draw exactly what the shared realspace default draws, on every tier and palette.
        val visitFacts = dimensionVisit(WorldLayer.ZENITH, 0f, 0f, course = listOf(120f to 40f, -200f to 100f), ambient = listOf(300f to -90f),
            embers = listOf(60f to 60f), localClock = 9f, scorching = 1f).copy(
            layer = WorldLayer.BASE,
            exitPortal = PortalProjection(1, 200f, -100f, WorldLayer.BASE, locked = false, glitch = false, remaining = 20f, radius = 65f),
        )
        for (tier in listOf(0, 4, 10)) for (roles in palettes) {
            val realspace = base().with("rebirthLevel" to tier)
            val carried = realspace.withDimension(visitFacts)
            assertTrue(render(roles) { backdrop(realspace, roles) }.contentEquals(render(roles) { backdrop(carried, roles) }),
                "tier $tier: the realspace backdrop is untouched")
            assertTrue(render(roles) { world(realspace, roles) }.contentEquals(render(roles) { world(carried, roles) }),
                "tier $tier: realspace draws no ring, ember or exit rift")
        }
    }

    /**
     * Each layer's sky is its own and unmistakable (the Zenith lit warm from above, the Nadir crimson with magma below,
     * the Archive cold blue-green), yet dark enough under the play field for every role colour to read on it, and no
     * dimension's look ever shows the threat role.
     */
    @Test
    fun eachLayerHasItsOwnSkyDarkUnderThePlayAndNeverShowsHazard() {
        for (roles in palettes) {
            val means = windowLayers.associateWith { layer ->
                val pixels = render(roles) { backdrop(base().withDimension(dimensionVisit(layer, CORE, CORE)), roles) }
                // The middle of the screen, where the Core flies and the fight is, stays dark.
                val middle = pixels.indices.filter { index ->
                    val x = index % width
                    val y = index / width
                    x in width / 4 until width * 3 / 4 && y in height / 4 until height * 3 / 4
                }.map { pixels[it] }.toIntArray()
                assertTrue(middle.average(::luminance) < MIDDLE_LUMINANCE, "$layer ${roles.name()}: the middle averages ${middle.average(::luminance)}")
                assertTrue(pixels.average(::luminance) < FRAME_LUMINANCE, "$layer ${roles.name()}: the frame averages ${pixels.average(::luminance)}")
                Triple(pixels.average { channel(it, 16).toFloat() }, pixels.average { channel(it, 8).toFloat() }, pixels.average { channel(it, 0).toFloat() })
            }
            val (zr, zg, zb) = means.getValue(WorldLayer.ZENITH)
            val (nr, ng, nb) = means.getValue(WorldLayer.NADIR)
            val (ar, ag, ab) = means.getValue(WorldLayer.ARCHIVE)
            assertTrue(zr > zb + 8f && zr + zg + zb > nr + ng + nb && zr + zg + zb > ar + ag + ab, "${roles.name()}: the Zenith is the warm, bright one ($means)")
            assertTrue(nr > ng * 2f && nr > nb * 2f, "${roles.name()}: the Nadir is crimson ($means)")
            assertTrue(ab > ar && ag > ar, "${roles.name()}: the Archive is cold ($means)")
        }
        // Hazard never grounds a layer nor marks one: with every mark of each layer drawn there is no threat pixel.
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Protan)) for (layer in windowLayers) {
            val marks = base().withDimension(dimensionVisit(layer, CORE, CORE, course = listOf(200f to 0f, -260f to 120f), ambient = listOf(0f to -220f),
                embers = listOf(80f to 90f, -90f to 60f), pace = 1f)).with("sites" to immutableListOf(
                SiteProjection(SiteKind.NADIR_RIFT, "Nadir rift", CORE + 420f, CORE - 200f, false, 20f, 0, 0f, immutableListOf(), 0f, 0f)))
            val exit = base().withDimension(dimensionVisit(layer, CORE, CORE, exit = -380f to -150f))
            for (model in listOf(marks, exit)) {
                val pixels = render(roles) {
                    backdrop(model, roles)
                    drawDimensionWorld(model, 0f, 0f, roles)
                }
                val threat = roles.threat.toArgb()
                assertEquals(0, pixels.count { near(it, threat, 24) }, "$layer ${roles.name()}: no hazard in the dimension's look")
            }
        }
    }

    @Test
    fun theEventHorizonTierTurnsEveryLayerBlackAndWhite() {
        for (layer in windowLayers) for (roles in palettes) {
            val model = base().with("rebirthLevel" to 10).withDimension(dimensionVisit(layer, CORE, CORE))
            val pixels = render(roles) { backdrop(model, roles) }
            val worst = pixels.maxOf { spread(it) }
            assertTrue(worst <= 3, "$layer ${roles.name()} at tier 10: a pixel has $worst levels of colour")
            assertTrue(pixels.distinct().size > 3, "$layer at tier 10 still draws its grid and motes")
        }
    }

    @Test
    fun motesBelongToTheWorldRepeatOneCellOverAndFollowTheDensity() {
        fun motes(layer: WorldLayer, left: Float, top: Float, density: Float, crowded: Boolean = false, time: Float = 7.5f): List<Triple<Float, Float, Int>> =
            buildList { forEachDimensionMote(layer, left, top, left + width, top + height, time, density, crowded) { x, y, hash -> add(Triple(x, y, hash)) } }
        val cell = DIMENSION_MOTE_CELL
        for (layer in windowLayers) {
            val normal = dimensionMoteDensity(ParticleDensity.NORMAL)
            val here = motes(layer, 3_200f, -900f, normal)
            assertEquals(here, motes(layer, 3_200f, -900f, normal), "$layer: motes are a function of the world and the time")
            // One cell over, the motes in the shared cells are the same world points.
            val over = motes(layer, 3_200f + cell, -900f, normal)
            fun shared(list: List<Triple<Float, Float, Int>>) = list.filter { it.first >= 3_200f + cell && it.first < 3_200f + width }.toSet()
            assertEquals(shared(here), shared(over), "$layer: the pattern is anchored to the world")
            // Each mote stays inside its own cell, one mote per cell at most.
            here.forEach { (x, y, _) ->
                assertTrue(x >= floor(x / cell) * cell && y >= floor(y / cell) * cell, "$layer: a mote outside its cell")
            }
            val cells = (floor((3_200f + width) / cell) - floor(3_200f / cell) + 1) * (floor((-900f + height) / cell) - floor(-900f / cell) + 1)
            assertEquals(here.size, here.map { floor(it.first / cell) to floor(it.second / cell) }.toSet().size)
            assertTrue(here.size <= cells, "$layer: ${here.size} motes for $cells cells")
            assertTrue(here.isNotEmpty(), "$layer: a normal density shows motes")
            assertTrue(motes(layer, 3_200f, -900f, dimensionMoteDensity(ParticleDensity.HIGH)).size >= here.size)
            assertTrue(motes(layer, 3_200f, -900f, dimensionMoteDensity(ParticleDensity.LOW)).isEmpty(), "$layer: none at the low density")
            // A crowded screen keeps every other cell of the same pattern.
            val crowded = motes(layer, 3_200f, -900f, normal, crowded = true)
            assertTrue(crowded.size < here.size && here.containsAll(crowded), "$layer: a crowd halves the motes")
        }
        // The window layers' motes rise with time; the Archive's stay put (they blink on the local clock instead).
        assertTrue(motes(WorldLayer.ZENITH, 0f, 0f, 0.6f, time = 1f) != motes(WorldLayer.ZENITH, 0f, 0f, 0.6f, time = 2f))
        assertEquals(motes(WorldLayer.ARCHIVE, 0f, 0f, 0.6f, time = 1f).map { it.first to it.second },
            motes(WorldLayer.ARCHIVE, 0f, 0f, 0.6f, time = 9f).map { it.first to it.second })

        // The Nadir's cracks belong to the world too: two windows a crack cell apart hold the same cracks where they overlap.
        fun cracks(left: Float) = buildList {
            forEachNadirCrack(left, 400f, left + width, 400f + height) { x, y, variant, _ -> add(Triple(x, y, variant)) }
        }.filter { (x, y, _) -> x in 0f..width - 200f && y in 400f..400f + height }.toSet()
        val here = cracks(-200f)
        assertTrue(here.size >= 4, "a screen holds cracks: ${here.size}")
        assertEquals(here, cracks(-200f + CRACK_CELL), "the cracks stay where the world has them")
    }

    @Test
    fun riftsShowTheirTargetLockedHatchBoundedGlitchAndBluntSigils() {
        // The glitch's ghost rings stand off 40 % to all of 2.5 units, in quarter-second steps.
        var time = 0f
        while (time < 12f) {
            val offset = portalGlitchOffset(time)
            assertTrue(offset in PORTAL_GLITCH_SHIFT * 0.4f..PORTAL_GLITCH_SHIFT, "glitch offset $offset at $time")
            assertEquals(portalGlitchOffset(floor(time * 4f) / 4f), offset, "the offset holds for a quarter second")
            time += 0.01f
        }
        // Sigils: convex polygons with no corner sharper than a right angle (nothing reads as an arrowhead), inside their
        // circle, one distinct mark per layer.
        for (radius in listOf(14f, 30f)) {
            val shapes = WorldLayer.entries.map { layer ->
                val polygons = dimensionSigilPolygons(layer, radius)
                polygons.forEach { polygon ->
                    val vertices = polygon.size / 2
                    assertTrue(vertices >= 4, "$layer sigil has a triangle")
                    for (vertex in 0 until vertices) {
                        val x = polygon[vertex * 2]
                        val y = polygon[vertex * 2 + 1]
                        assertTrue(hypot(x, y) <= radius * 1.0001f, "$layer sigil leaves its circle")
                        val previous = (vertex + vertices - 1) % vertices
                        val next = (vertex + 1) % vertices
                        val ax = polygon[previous * 2] - x
                        val ay = polygon[previous * 2 + 1] - y
                        val bx = polygon[next * 2] - x
                        val by = polygon[next * 2 + 1] - y
                        assertTrue(ax * bx + ay * by <= 1e-3f * radius * radius, "$layer sigil has an acute corner")
                        // Convex: every turn goes the same way around.
                        val nx = polygon[((vertex + 2) % vertices) * 2] - polygon[next * 2]
                        val ny = polygon[((vertex + 2) % vertices) * 2 + 1] - polygon[next * 2 + 1]
                        val turn = (-ax) * ny - (-ay) * nx
                        assertTrue(turn * signedArea(polygon) >= -1e-3f, "$layer sigil polygon is not convex")
                    }
                }
                polygons.map { it.toList() }
            }
            assertEquals(WorldLayer.entries.size, shapes.toSet().size)
        }
        // The Nadir's cracks turn only at obtuse angles.
        NadirCrackLines.flatten().forEach { line ->
            for (vertex in 1 until line.size / 2 - 1) {
                val ax = line[vertex * 2 - 2] - line[vertex * 2]
                val ay = line[vertex * 2 - 1] - line[vertex * 2 + 1]
                val bx = line[vertex * 2 + 2] - line[vertex * 2]
                val by = line[vertex * 2 + 3] - line[vertex * 2 + 1]
                assertTrue(ax * bx + ay * by < 0f, "a crack turns at an acute angle")
            }
        }
        // Drawn: an open Zenith rift's tear burns in its target's gold round a lit heart; a locked one is grey, barred and
        // hatched with none of the gold; both keep where they lead at their centre.
        val center = Offset(width * 0.5f, height * 0.5f)
        val roles = KkRolePalette.Default
        val open = render { drawPortal(center, WorldLayer.ZENITH, locked = false, glitch = false, time = 2f, roles = roles, radius = 65f) }
        val locked = render { drawPortal(center, WorldLayer.ZENITH, locked = true, glitch = false, time = 2f, roles = roles, radius = 65f) }
        fun ring(pixels: IntArray, from: Float, to: Float, around: Offset = center) = pixels.indices.filter { index ->
            val distance = hypot(index % width + 0.5f - around.x, index / width + 0.5f - around.y)
            distance in from..to
        }.map { pixels[it] }
        val gold = roles.gold.base.toArgb()
        assertTrue(ring(open, 0f, 70f).count { near(it, gold, 40) } > 200, "an open rift burns in its target's accent")
        assertTrue(ring(open, 0f, 12f).count { luminance(it) > 120f } > 40, "an open rift's heart is lit")
        assertEquals(0, ring(locked, 0f, 90f).count { near(it, gold, 40) }, "a locked rift shows no accent")
        assertTrue(ring(locked, 20f, 70f).count { spread(it) <= 12 && luminance(it) > 60f } > 200, "a locked rift is grey and barred")
        assertTrue(ring(locked, 0f, 14f).count { spread(it) <= 12 && luminance(it) > 60f } > 20, "a locked rift still shows where it leads")
        // A window rift offered by the sites draws through the same tear and takes its lock and its reason from the projection.
        val offer = SiteProjection(SiteKind.ZENITH_RIFT, "Zenith rift", CORE + 300f, CORE - 120f, false, 20f, 0, 0f, immutableListOf(), 0f, 0f)
        val at = Offset(center.x + 300f, center.y - 120f)
        val offered = base().with("sites" to immutableListOf(offer))
        fun timerWidth(model: GameplayRenderModel): Float {
            render { drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer()) }
            return WorldDrawProbe.rects(WorldDrawn.POINT_TIMER).single().width
        }
        val openOffer = render { drawWorld(offered, VisualFxProjection.EMPTY, 0f, 0f, measurer()) }
        val lockedModel = offered.withDimension(DimensionProjection(lockedRifts = WorldLayer.ZENITH.bit, riftLock = RiftLock.BOSS))
        val lockedOffer = render { drawWorld(lockedModel, VisualFxProjection.EMPTY, 0f, 0f, measurer()) }
        assertTrue(ring(openOffer, 0f, 70f, at).count { near(it, gold, 40) } > 200, "an offered rift that can be entered shows its accent")
        assertEquals(0, ring(lockedOffer, 0f, 70f, at).count { near(it, gold, 40) }, "an offered rift that cannot be entered is grey")
        assertTrue(timerWidth(lockedModel) > timerWidth(offered) * 3f, "a locked rift names why in place of its timer")
        // Another layer's lock leaves it open.
        val otherLock = render {
            drawWorld(offered.withDimension(DimensionProjection(lockedRifts = WorldLayer.NADIR.bit, riftLock = RiftLock.BOSS)),
                VisualFxProjection.EMPTY, 0f, 0f, measurer())
        }
        assertTrue(ring(otherLock, 0f, 70f, at).count { near(it, gold, 40) } > 200, "a Nadir lock leaves the Zenith rift open")
        // A glitch rift splits its edge into cyan and magenta copies; a plain rift into the Archive has neither.
        val glitch = render { drawPortal(center, WorldLayer.ARCHIVE, locked = false, glitch = true, time = 3.3f, roles = roles, radius = 65f) }
        val plain = render { drawPortal(center, WorldLayer.ARCHIVE, locked = false, glitch = false, time = 3.3f, roles = roles, radius = 65f) }
        fun cyan(pixel: Int) = channel(pixel, 0) > channel(pixel, 16) + 80 && channel(pixel, 8) > channel(pixel, 16) + 80
        fun magenta(pixel: Int) = channel(pixel, 16) > channel(pixel, 8) + 80 && channel(pixel, 0) > channel(pixel, 8) + 60
        assertTrue(ring(glitch, 0f, 80f).count(::cyan) > 20 && ring(glitch, 0f, 80f).count(::magenta) > 20, "the glitch splits off its copies")
        assertEquals(0, ring(plain, 0f, 80f).count { cyan(it) || magenta(it) }, "a plain rift has no copies")
    }

    @Test
    fun theNextRingIsThePlayersColourAtPaceGreyAndHatchedBelowItAndTheOneAfterWaitsInGrey() {
        val roles = KkRolePalette.Default
        val you = roles.you.toArgb()
        val gold = roles.gold.base.toArgb()
        // The next ring on the Core's right, the one after on its left; the model's Core flies at its own speed.
        val speed = base().speed
        fun model(pace: Float) = base().withDimension(dimensionVisit(WorldLayer.ZENITH, CORE, CORE, course = listOf(300f to 0f, -300f to 0f), pace = pace))
        fun around(pixels: IntArray, dx: Float, from: Float, to: Float) = pixels.indices.filter { index ->
            val distance = hypot(index % width + 0.5f - (width * 0.5f + dx), index / width + 0.5f - height * 0.5f)
            distance in from..to
        }.map { pixels[it] }
        val fast = render { drawDimensionWorld(model(pace = speed * 0.5f), 0f, 0f, roles) }
        val slow = render { drawDimensionWorld(model(pace = speed * 2f + 100f), 0f, 0f, roles) }
        assertTrue(around(fast, 300f, 82f, 86f).count { it == you } > 60, "at pace the next ring's dashes are the player's colour")
        assertEquals(0, around(slow, 300f, 82f, 86f).count { near(it, you, 40) }, "below the pace they are not")
        assertTrue(around(slow, 300f, 80f, 88f).count { spread(it) <= 12 && luminance(it) > 50f } > 60, "below the pace they are grey")
        // Each hatched dash is a filled grey block under its stripes (14 on, 10 off round the ring): most of the band
        // between the dashes' edges is lit, where loose stripe fragments alone would light about a quarter of it.
        val band = around(slow, 300f, 81f, 87f)
        val lit = band.count { spread(it) <= 12 && luminance(it) > 40f }
        assertTrue(lit > band.size * 0.45f, "below the pace each dash is a solid hatched block ($lit of ${band.size} px lit)")
        assertTrue(around(fast, 300f, 68f, 72f).count { near(it, gold, 30) } > 100, "the next ring keeps its gold halo")
        // The ring after the next waits as a grey stroke: no gold and no player's colour.
        val following = around(fast, -300f, 64f, 90f)
        assertEquals(0, following.count { near(it, gold, 60) || near(it, you, 60) }, "the following ring is grey only")
        assertTrue(following.count { luminance(it) > 50f } > 100, "the following ring is drawn")

        // An ember fades over its last half second.
        fun ember(life: Float) = render {
            drawDimensionWorld(base().withDimension(dimensionVisit(WorldLayer.NADIR, CORE, CORE, embers = listOf(0f to 0f), emberLife = life)), 0f, 0f, roles)
        }
        fun brightness(pixels: IntArray) = around(pixels, 0f, 0f, 4f).maxOf { luminance(it) }
        assertTrue(brightness(ember(0.05f)) < brightness(ember(5f)), "a dying ember is dimmer")
        // A bright magma core in a heat glow, inside its catch ring: the ring's dashes lie on the projected radius.
        val live = ember(5f)
        assertTrue(around(live, 0f, 0f, 3f).any { near(it, KkLayerHues.MagmaCore.toArgb(), 12) }, "an ember's core is magma")
        assertTrue(around(live, 0f, 6f, 20f).count { leaning(it, roles.heat) > 0.9f } > 40, "in a heat glow")
        assertTrue(around(live, 0f, FIXTURE_CATCH_RADIUS - 2f, FIXTURE_CATCH_RADIUS + 2f).count { luminance(it) > 60f } > 60, "inside its catch ring")
    }

    @Test
    fun theNextRingEmptiesClockwiseFromTheTopOverItsSplitAndAThrownEmberStreaksInFromItsKill() {
        val roles = KkRolePalette.Default
        val you = roles.you.toArgb()
        val speed = base().speed
        // The next ring 300 u right of the Core, flown at pace; [left] of a [duration]-second split remains.
        fun ring(left: Float, duration: Float = 2f) = render {
            drawDimensionWorld(
                base().withDimension(dimensionVisit(WorldLayer.ZENITH, CORE, CORE, course = listOf(300f to 0f), pace = speed * 0.5f,
                    splitRemaining = left, splitDuration = duration)),
                0f, 0f, roles,
            )
        }
        val centerX = width * 0.5f + 300f
        val centerY = height * 0.5f
        // The player's-colour dashes of the next ring's dashed ring in one quadrant (right/left of, above/below its centre).
        fun dashes(pixels: IntArray, right: Boolean, above: Boolean) = pixels.indices.count { index ->
            val dx = index % width + 0.5f - centerX
            val dy = index / width + 0.5f - centerY
            hypot(dx, dy) in 82f..86f && (dx > 4f) == right && (dx < -4f) == !right && (dy < -4f) == above && (dy > 4f) == !above &&
                near(pixels[index], you, 40)
        }
        fun quadrants(pixels: IntArray) = listOf(true to true, true to false, false to false, false to true).map { (r, a) -> dashes(pixels, r, a) }
        val whole = quadrants(ring(left = 0f, duration = 0f))
        assertTrue(whole.all { it > 15 }, "with no split running the dashed ring is whole: $whole")
        val half = quadrants(ring(left = 1f))
        assertTrue(half[0] > 15 && half[1] > 15, "half the split left keeps the right half, from the top clockwise: $half")
        assertEquals(0, half[2] + half[3], "and the left half has run out: $half")
        val quarter = quadrants(ring(left = 0.5f))
        assertTrue(quarter[0] > 15 && quarter[1] + quarter[2] + quarter[3] == 0, "a quarter left is the top-right arc only: $quarter")
        assertEquals(0, quadrants(ring(left = 0f)).sum(), "a split run out leaves no arc")

        // A thrown ember: a heat streak from its kill 200 u to its left whose tail catches up with it, gone once landed.
        fun ember(age: Float, thrown: Boolean = true) = render {
            drawDimensionWorld(
                base().withDimension(dimensionVisit(WorldLayer.NADIR, CORE, CORE, embers = listOf(0f to 0f), emberLife = 1.25f - age,
                    thrownFrom = if (thrown) listOf(-200f to 0f) else emptyList(), emberAge = age)),
                0f, 0f, roles,
            )
        }
        fun streak(pixels: IntArray, fromX: Float, toX: Float) = pixels.indices.count { index ->
            val x = index % width + 0.5f - width * 0.5f
            val y = index / width + 0.5f - height * 0.5f
            x in fromX..toX && abs(y) <= 2f && channel(pixels[index], 16) > channel(Kk.Ink.toArgb(), 16) + 20 &&
                leaning(pixels[index], roles.heat) > 0.9f
        }
        assertTrue(streak(ember(age = 0.05f), -180f, -30f) > 100, "just thrown, the streak reaches back toward the kill")
        val later = ember(age = 0.17f)
        assertEquals(0, streak(later, -180f, -90f), "its tail has caught up past the kill's side")
        assertTrue(streak(later, -60f, -30f) > 20, "and still trails the ember")
        // (Beyond the ember's own glow.)
        assertEquals(0, streak(ember(age = 0.4f), -190f, -30f), "a landed ember trails nothing")
        assertEquals(0, streak(ember(age = 0.05f, thrown = false), -190f, -30f), "a placed ember never had a throw")
    }

    @Test
    fun theExitRiftTheGlitchRiftAndTheNextRingGetMarkersBesideTheSitesWithinTheBatch() {
        // Five site offers off screen, then the exit rift, the glitch rift and the next ring off screen: the collection
        // order is the priority order and all eight fit the batch.
        val offers = List(5) { index ->
            SiteProjection(SiteKind.GATE_RACE, "Race", CORE - 2_000f + index * 900f, CORE - 1_600f, false, 20f, -1, 0f, immutableListOf(), 0f, 0f)
        }
        val dimension = DimensionProjection(
            layer = WorldLayer.ZENITH,
            exitPortal = PortalProjection(1, CORE + 1_800f, CORE + 300f, WorldLayer.BASE, locked = false, glitch = false, remaining = 30f, radius = 65f),
            glitchPortal = PortalProjection(2, CORE - 1_900f, CORE + 600f, WorldLayer.ARCHIVE, locked = false, glitch = true, remaining = 30f, radius = 65f),
            nextHaloId = 4,
            halos = immutableListOf(HaloProjection(4, CORE + 400f, CORE + 1_300f, 0), HaloProjection(5, CORE + 100f, CORE + 50f, 1)),
        )
        val model = base().with("sites" to offers.toImmutableList()).withDimension(dimension)
        val pixels = render { drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer()) }
        val batch = WorldOverlayScratch.markers
        assertEquals(EdgeMarkerBatch.CAPACITY, batch.count)
        assertEquals(List(5) { EdgeMarkerIcon.RACE } + listOf(EdgeMarkerIcon.PORTAL, EdgeMarkerIcon.PORTAL, EdgeMarkerIcon.HALO),
            (0 until batch.count).map { batch.icon[it] })
        val markers = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
        assertEquals(EdgeMarkerBatch.CAPACITY, markers.size, "every marker is drawn")
        // The next ring's marker is the player's course, volt like the ring's dashes at pace; the rifts' markers are bone.
        val you = KkRolePalette.Default.you.toArgb()
        fun volt(box: Rect): Int {
            var count = 0
            for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(height)) {
                for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(width)) {
                    if (near(pixels[y * width + x], you, 24)) count++
                }
            }
            return count
        }
        assertTrue(volt(markers[7]) > 20, "the halo marker is volt (${volt(markers[7])} volt pixels)")
        assertEquals(0, volt(markers[5]) + volt(markers[6]), "the rift markers are not")
        // Seen on screen, a rift or ring needs no marker; a ring that is not next never gets one.
        val near = base().withDimension(dimension.copy(
            exitPortal = checkNotNull(dimension.exitPortal).copy(x = CORE + 300f, y = CORE),
            glitchPortal = null,
            nextHaloId = 5,
        ))
        render { drawWorld(near, VisualFxProjection.EMPTY, 0f, 0f, measurer()) }
        assertEquals(0, (0 until WorldOverlayScratch.markers.count).count { WorldOverlayScratch.markers.icon[it] == EdgeMarkerIcon.PORTAL ||
            WorldOverlayScratch.markers.icon[it] == EdgeMarkerIcon.HALO })
    }

    @Test
    fun theMinimapShowsHalosEmbersAndRifts() {
        val roles = KkRolePalette.Default
        fun minimap(model: GameplayRenderModel): List<Int> {
            val pixels = render(roles) { drawHud(model.with("minimapRange" to 1_200f), measurer(roles), 3f) }
            val rect = checkNotNull(HudLayoutProbe.rect(HudBlock.MINIMAP))
            return buildList {
                for (y in rect.top.toInt() until rect.bottom.toInt()) for (x in rect.left.toInt() until rect.right.toInt()) add(pixels[y * width + x])
            }
        }
        val plain = base().withDimension(dimensionVisit(WorldLayer.ZENITH, CORE, CORE))
        val marked = base().withDimension(dimensionVisit(WorldLayer.ZENITH, CORE, CORE, course = listOf(500f to 200f, -400f to -300f),
            ambient = listOf(300f to -500f), embers = listOf(200f to 150f, -350f to 250f), exit = 5_000f to 0f, completed = false))
        // Thin marks antialias, so they are read by hue: gold for the halo rings, the player's colour for the ring to fly
        // through next, pale magma for the embers.
        val you = roles.you.toArgb()
        fun gold(pixel: Int) = channel(pixel, 16) - channel(pixel, 0) > 70 && channel(pixel, 8) - channel(pixel, 0) > 40 && !near(pixel, you, 40)
        fun next(pixel: Int) = near(pixel, you, 40)
        val marks = minimap(marked)
        assertTrue(marks.count(::gold) > minimap(plain).count(::gold) + 10, "halos are gold rings on the minimap")
        assertTrue(marks.count(::next) > minimap(plain).count(::next) + 5, "the ring to fly through next stands out in the player's colour")
        assertTrue(marks.count { near(it, KkLayerHues.MagmaCore.toArgb(), 40) } > 3, "embers are magma dots on the minimap")
        fun boneLines(pixels: List<Int>) = pixels.count { spread(it) <= 20 && luminance(it) > 90f }
        assertTrue(boneLines(marks) > boneLines(minimap(plain)) + 3, "the exit rift is pinned to the rim in bone")
    }

    private fun base(): GameplayRenderModel = hudTestModel(width.toFloat(), height.toFloat()).with(
        "coreX" to CORE, "coreY" to CORE, "cameraX" to CORE, "cameraY" to CORE, "previousCoreX" to CORE, "previousCoreY" to CORE,
        "previousCameraX" to CORE, "previousCameraY" to CORE, "sites" to immutableListOf<SiteProjection>(), "trial" to null,
        "enemies" to immutableListOf<EnemyProjection>(), "obstacles" to immutableListOf<ObstacleProjection>(),
    )

    private fun DrawScope.backdrop(model: GameplayRenderModel, roles: KkRolePalette) = drawBackdrop(model, 0f, 0f, 3f, roles)

    private fun DrawScope.world(model: GameplayRenderModel, roles: KkRolePalette) {
        drawBackdrop(model, 0f, 0f, 3f, roles)
        drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer(roles))
    }

    private fun render(roles: KkRolePalette = KkRolePalette.Default, block: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            block()
        }
        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels, 0, 0, width, height)
        return pixels
    }

    private fun measurer(roles: KkRolePalette = KkRolePalette.Default) = CanvasTextMeasurer(
        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), 1f, AppLanguage.English,
        HudTestFonts.typography, roles,
    )

    private fun KkRolePalette.name(): String = when (this) {
        KkRolePalette.Mono -> "mono"
        KkRolePalette.Protan -> "protan"
        else -> "default"
    }

    private fun IntArray.mostCommon(): Int = asIterable().groupingBy { it }.eachCount().maxBy { it.value }.key

    private inline fun IntArray.average(value: (Int) -> Float): Float {
        var sum = 0.0
        for (pixel in this) sum += value(pixel)
        return (sum / size).toFloat()
    }

    private fun channel(pixel: Int, shift: Int) = (pixel shr shift) and 0xFF

    private fun luminance(pixel: Int): Float = 0.2126f * channel(pixel, 16) + 0.7152f * channel(pixel, 8) + 0.0722f * channel(pixel, 0)

    private fun spread(pixel: Int): Int {
        val r = channel(pixel, 16)
        val g = channel(pixel, 8)
        val b = channel(pixel, 0)
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    private fun near(pixel: Int, target: Int, tolerance: Int): Boolean =
        abs(channel(pixel, 16) - channel(target, 16)) <= tolerance && abs(channel(pixel, 8) - channel(target, 8)) <= tolerance &&
            abs(channel(pixel, 0) - channel(target, 0)) <= tolerance

    /** Cosine between the pixel's step away from ink and the role's step away from ink. */
    private fun leaning(pixel: Int, role: Color): Float {
        val ink = Kk.Ink.toArgb()
        val target = role.toArgb()
        var dot = 0f
        var a = 0f
        var b = 0f
        for (shift in intArrayOf(16, 8, 0)) {
            val step = (channel(pixel, shift) - channel(ink, shift)).toFloat()
            val toward = (channel(target, shift) - channel(ink, shift)).toFloat()
            dot += step * toward
            a += step * step
            b += toward * toward
        }
        return dot / sqrt(a * b)
    }

    private fun signedArea(polygon: FloatArray): Float {
        var area = 0f
        val vertices = polygon.size / 2
        for (vertex in 0 until vertices) {
            val next = (vertex + 1) % vertices
            area += polygon[vertex * 2] * polygon[next * 2 + 1] - polygon[next * 2] * polygon[vertex * 2 + 1]
        }
        return area
    }

    private companion object {
        const val CORE = 1_000f
        /**
         * The play field's middle and the whole frame stay below these mean luminances (0..255), so every role still reads
         * on them (enemies keep their ink bodies): the Zenith's lit sky is the brightest and sits near the bound.
         */
        const val MIDDLE_LUMINANCE = 90f
        const val FRAME_LUMINANCE = 90f
    }
}
