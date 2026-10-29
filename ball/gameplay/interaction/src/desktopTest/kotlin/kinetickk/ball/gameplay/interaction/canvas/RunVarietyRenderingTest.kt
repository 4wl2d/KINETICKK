// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Run variety as drawn by the real renderers: the world event's edge band ramps over its telegraph, holds and fades at its
 * end, the gravity storm tinting only its downwind edges; strikes fill toward their landing in their kind's colour (the
 * meteor in threat, the ion zone in the Ion hue with a threat ring where it hurts the Core); the storms' motes stay within
 * their cell cap and thin out at a low density or in a crowd; the flare's band crosses only during a sweep; a live signal
 * shows its mark and name or its edge marker, never both; an affixed Elite wears its glyph on its halo and a champion its
 * aura and a heavier outline.
 */
class RunVarietyRenderingTest {
    private val roles = KkRolePalette.Default

    @Test
    fun theEventEdgeRampsOverTheTelegraphHoldsWhileItRunsAndFadesAtItsEnd() {
        val event = WorldEventProjection(WorldEventKind.METEOR_SHOWER, telegraphAt = 100f, startsAt = 103f, endsAt = 133f)
        assertEquals(0f, worldEventEdgeIntensity(event, 99.9f), "nothing before the telegraph")
        assertEquals(0f, worldEventEdgeIntensity(event, 100f), 1e-6f)
        assertEquals(0.225f, worldEventEdgeIntensity(event, 101.5f), 1e-5f, "half way up the telegraph ramp")
        assertEquals(0.45f, worldEventEdgeIntensity(event, 102.999f), 1e-3f, "the ramp tops out at 0.45")
        assertEquals(0.30f, worldEventEdgeIntensity(event, 103f), 1e-6f, "steady 0.30 while it runs")
        assertEquals(0.30f, worldEventEdgeIntensity(event, 130f), 1e-6f)
        assertEquals(0.15f, worldEventEdgeIntensity(event, 131.5f), 1e-5f, "fading over the last 3 s")
        assertEquals(0f, worldEventEdgeIntensity(event, 133f), "gone at the end")
        // The ground look follows the same clock: half strength at the end of the telegraph, whole while it runs.
        assertEquals(0.5f, worldEventAmbientShare(event, 102.999f), 1e-3f)
        assertEquals(1f, worldEventAmbientShare(event, 120f))
        assertEquals(0.5f, worldEventAmbientShare(event, 131.5f), 1e-5f)
        assertEquals(0f, worldEventAmbientShare(event, 133f))
    }

    @Test
    fun theGravityStormTintsOnlyItsDownwindEdgesAndOtherEventsEveryEdge() {
        fun edges(kind: WorldEventKind, wind: Float): Map<String, Color> {
            val pixels = draw { model -> drawWorldEventEdge(model, roles, 0f) }(
                running(WorldEventProjection(kind, 0f, 3f, 33f, windAngle = wind, windStrength = 1f)),
            )
            return mapOf("left" to pixels[1, H / 2], "right" to pixels[W - 2, H / 2], "top" to pixels[W / 2, 1], "bottom" to pixels[W / 2, H - 2])
        }
        val east = edges(WorldEventKind.GRAVITY_STORM, 0f)
        assertTrue(sameHue(east.getValue("right"), roles.pol), "a wind blowing right tints the right edge in polarity")
        listOf("left", "top", "bottom").forEach { assertEquals(Kk.Ink, east.getValue(it), "the wind leaves the $it edge alone") }
        val south = edges(WorldEventKind.GRAVITY_STORM, (PI / 2).toFloat())
        assertTrue(sameHue(south.getValue("bottom"), roles.pol), "a wind blowing down tints the bottom edge")
        listOf("left", "right", "top").forEach { assertEquals(Kk.Ink, south.getValue(it), "the wind leaves the $it edge alone") }
        // A diagonal wind shares out over its two downwind edges.
        val northWest = edges(WorldEventKind.GRAVITY_STORM, (PI * 1.25).toFloat())
        assertTrue(sameHue(northWest.getValue("left"), roles.pol) && sameHue(northWest.getValue("top"), roles.pol))
        assertEquals(Kk.Ink, northWest.getValue("right"))
        assertEquals(Kk.Ink, northWest.getValue("bottom"))
        for ((kind, color) in listOf(WorldEventKind.METEOR_SHOWER to roles.heat, WorldEventKind.DATA_STORM to Kk.Bone,
            WorldEventKind.ION_STORM to roles.element(StatusElement.ION.ordinal), WorldEventKind.SOLAR_FLARE to roles.heat)) {
            edges(kind, 0f).forEach { (side, pixel) -> assertTrue(sameHue(pixel, color), "$kind tints the $side edge in its colour") }
        }
    }

    @Test
    fun strikesFillTowardTheirLandingInTheirKindsColour() {
        val threat = roles.threat
        val ion = roles.element(StatusElement.ION.ordinal)
        fun strikes(kind: WorldEventKind, progress: Float): PixelMap {
            // One strike 95 u around the screen centre, armed 1.4 s before it lands.
            val strike = EventStrikeProjection(0f, 0f, 95f, armedAt = 10f, strikesAt = 11.4f)
            val model = running(WorldEventProjection(kind, 0f, 3f, 33f, strikes = immutableListOf(strike)), elapsed = 10f + 1.4f * progress)
            return draw { drawWorldEventStrikes(it, 0f, 0f, roles) }(model)
        }
        val meteor = strikes(WorldEventKind.METEOR_SHOWER, 0.9f)
        assertTrue(meteor.count(CENTER, 92f, 98f) { sameHue(it, threat) } > 120, "the meteor's landing ring is danger (threat)")
        assertEquals(0, meteor.count(CENTER, 0f, 140f) { sameHue(it, ion) }, "no Ion hue in a meteor strike")
        assertTrue(meteor.count(CENTER, 100f, 140f) { it != Kk.Ink } == 0, "nothing outside the ring")
        // The fill grows toward the ring as the strike nears.
        val early = strikes(WorldEventKind.METEOR_SHOWER, 0.25f).count(CENTER, 40f, 80f) { sameHue(it, threat, 0.04f) }
        val late = meteor.count(CENTER, 40f, 80f) { sameHue(it, threat, 0.04f) }
        assertTrue(early == 0 && late > 500, "the fill reaches outward as the meteor falls ($early early, $late late)")
        val zone = strikes(WorldEventKind.ION_STORM, 0.9f)
        assertTrue(zone.count(CENTER, 92f, 98f) { sameHue(it, ion) } > 120, "an ion zone's ring takes the Ion hue")
        val coreReach = running(WorldEventProjection(WorldEventKind.ION_STORM, 0f, 3f, 33f)).content.variety.worldEvents.ion.coreRadius
        assertTrue(zone.count(CENTER, coreReach - 2f, coreReach + 2f) { sameHue(it, threat) } > 60, "where it hurts the Core is threat")
        // Kinds without strikes draw none, even with strikes in the projection.
        assertTrue(strikes(WorldEventKind.DATA_STORM, 0.9f).count(CENTER, 0f, 140f) { it != Kk.Ink } == 0)
    }

    @Test
    fun stormMotesStayInsideTheirCellCapAndThinOutWhenTheFrameIsBusy() {
        // The cap holds on every screen: phones, the boards and a 4K desktop at density 1.
        for ((width, height) in listOf(390f to 844f, 844f to 390f, 1_440f to 810f, 2_560f to 1_440f, 3_840f to 2_160f)) {
            val cell = eventCellSize(width, height)
            assertTrue(eventCellCount(width, height, cell) <= MAX_EVENT_CELLS, "$width x $height draws at most $MAX_EVENT_CELLS cells")
            assertTrue(cell >= EVENT_CELL, "cells never shrink below $EVENT_CELL")
        }
        assertEquals(EVENT_CELL, eventCellSize(390f, 844f), "a phone keeps the 160 u cells")
        fun motes(kind: WorldEventKind, color: Color, density: ParticleDensity = ParticleDensity.HIGH, crowd: Int = 0): Int {
            val model = running(WorldEventProjection(kind, 0f, 3f, 33f, windAngle = 0.6f, windStrength = 1f), elapsed = 12f)
                .with("settings" to PlayerPreferences(particleDensity = density))
            return draw {
                FxFrame.begin(crowd, ReactionEffectsLevel.FULL)
                drawWorldEventAmbient(it, 0f, 0f, roles)
                FxFrame.begin(0, ReactionEffectsLevel.FULL)
            }(model).count(CENTER, 0f, 1_000f) { sameHue(it, color, 0.04f) }
        }
        for ((kind, color) in listOf(WorldEventKind.GRAVITY_STORM to roles.pol, WorldEventKind.DATA_STORM to Kk.Bone)) {
            val full = motes(kind, color)
            val low = motes(kind, color, ParticleDensity.LOW)
            val crowded = motes(kind, color, crowd = FxFrame.CROWD_SECONDARY_OFF + 1)
            assertTrue(full > 0, "$kind draws its motes")
            assertTrue(low in 1 until full, "$kind keeps some motes at a low density ($low of $full px)")
            assertTrue(crowded in 1 until full, "$kind thins out in a crowd ($crowded of $full px)")
            assertEquals(0, motes(kind, color, crowd = FxFrame.CROWD_SIG_ONLY + 1), "$kind gives way to a packed screen")
        }
    }

    @Test
    fun theFlaresBandCrossesTheScreenOnlyDuringASweep() {
        fun heat(since: Float): Int {
            val model = running(WorldEventProjection(WorldEventKind.SOLAR_FLARE, 0f, 3f, 33f, sweepStartedAt = 10f, sweepAngle = 0.3f),
                elapsed = 10f + since)
            return draw { drawWorldEventAmbient(it, 0f, 0f, roles) }(model).count(CENTER, 0f, 1_000f) { sameHue(it, roles.heat, 0.04f) }
        }
        assertTrue(heat(SOLAR_SWEEP_CROSS_SECONDS * 0.5f) > 4_000, "half way through the sweep the band crosses the centre")
        assertEquals(0, heat(-0.1f), "no band before the sweep")
        assertEquals(0, heat(SOLAR_SWEEP_CROSS_SECONDS + 0.05f), "no band once it has crossed")
    }

    @Test
    fun aSignalShowsItsMarkAndNameOrItsEdgeMarkerNeverBoth() {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), 1.25f,
            AppLanguage.Russian, HudTestFonts.typography)
        val scope = CanvasDrawScope()
        val canvas = Canvas(ImageBitmap(1, 1))
        for ((width, height) in listOf(1_440 to 810, 390 to 844)) {
            var marks = 0
            var markers = 0
            val positions = (-200..(width + 200) step 40).map { it.toFloat() to height * 0.5f } +
                (-200..(height + 200) step 40).map { width * 0.5f + 120f to it.toFloat() }
            for ((x, y) in positions) {
                // The Core and camera at the origin; the signal at screen (x, y).
                val signal = EventSignalProjection(7, SignalKind.MERCHANT, x - width * 0.5f, y - height * 0.5f, expiresAt = 20f)
                val model = hudTestModel(width.toFloat(), height.toFloat()).with(
                    "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null, "elapsed" to 10f,
                    "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf<SiteProjection>(),
                    "ambientSites" to immutableListOf<SiteProjection>(), "trial" to null,
                    "eventSignals" to immutableListOf(signal),
                )
                scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                    drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                }
                val where = "$width x $height, signal at ($x, $y)"
                val mark = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                val marker = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                assertEquals(1, mark.size + marker.size, "exactly one of the signal's mark and its edge marker: $where")
                if (mark.isNotEmpty()) {
                    assertTrue(mark.single().inside(width, height), "the whole mark is on screen: $where")
                    WorldDrawProbe.rects(WorldDrawn.POINT_NAME).forEach { assertTrue(it.inside(width, height), "the name stays on screen: $where") }
                    marks++
                } else {
                    val batch = WorldOverlayScratch.markers
                    assertEquals(EdgeMarkerIcon.SIGNAL, batch.icon[0], "the marker is the signal's: $where")
                    markers++
                }
            }
            assertTrue(marks > 4 && markers > 4, "the sweep crosses the hand-over at $width x $height ($marks marks, $markers markers)")
        }
        // The remaining-life arc runs down over the signal's lifetime.
        val signal = EventSignalProjection(1, SignalKind.SHRINE, 0f, 0f, expiresAt = 130f)
        assertEquals(1f, signalLifeShare(signal, 30f, 100f))
        assertEquals(0.5f, signalLifeShare(signal, 30f, 115f), 1e-6f)
        assertEquals(0f, signalLifeShare(signal, 30f, 131f))
    }

    /**
     * On a landscape phone a signal up and to the right of the Core has the HUD both under and over its mark (the action
     * buttons, the relic row): its name goes beside the mark, on screen and clear of the HUD, in both languages and at
     * the largest text size.
     */
    @Test
    fun aSignalBetweenHudBlocksStillShowsItsNameBesideItsMark() {
        val scope = CanvasDrawScope()
        val canvas = Canvas(ImageBitmap(1, 1))
        val (width, height) = 844 to 390
        for (language in AppLanguage.entries) for (textScale in listOf(1.25f, 1.75f)) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography)
            val signal = EventSignalProjection(5, SignalKind.MERCHANT, width * 0.22f, -height * 0.12f, expiresAt = 21f)
            val model = hudTestModel(width.toFloat(), height.toFloat()).with(
                "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null, "elapsed" to 3f,
                "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf<SiteProjection>(),
                "ambientSites" to immutableListOf<SiteProjection>(), "trial" to null, "eventSignals" to immutableListOf(signal),
            )
            var keepOut: List<Rect> = emptyList()
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                keepOut = worldHudKeepOut(model, measurer).rects()
            }
            val where = "${language.name} x$textScale"
            assertEquals(1, WorldDrawProbe.rects(WorldDrawn.POINT_MARK).size, "the mark shows: $where")
            val name = WorldDrawProbe.rects(WorldDrawn.POINT_NAME).singleOrNull()
            assertNotNull(name, "the signal's name shows: $where")
            assertTrue(name.inside(width, height), "the name stays on screen: $where")
            assertTrue(keepOut.none { it.overlaps(name) }, "the name keeps clear of the HUD: $where ($name)")
        }
    }

    @Test
    fun affixGlyphsAreSixDistinctShapesInsideTheirBox() {
        val drawn = EliteAffix.entries.map { affix ->
            val bounds = AffixGlyphs.of(affix).getBounds()
            val half = AFFIX_GLYPH_SIZE * 0.5f + 0.01f
            assertTrue(bounds.left >= -half && bounds.top >= -half && bounds.right <= half && bounds.bottom <= half,
                "$affix stays inside its ${AFFIX_GLYPH_SIZE} u box ($bounds)")
            val pixels = drawn(48, 48) {
                translate(24f, 24f) { scale(4.5f, 4.5f, Offset.Zero) { drawPath(AffixGlyphs.of(affix), Kk.Bone) } }
            }
            val set = (0 until 48 * 48).filter { pixels[it % 48, it / 48] != Kk.Ink }.toSet()
            assertTrue(set.size > 150, "$affix fills a readable glyph (${set.size} px)")
            affix to set
        }
        drawn.forEach { (affix, set) ->
            drawn.forEach { (other, otherSet) ->
                if (other != affix) {
                    val shared = set.intersect(otherSet).size
                    assertTrue(shared < 0.8f * maxOf(set.size, otherSet.size), "$affix and $other read differently")
                }
            }
        }
    }

    @Test
    fun anAffixedEliteWearsItsGlyphOnItsHaloAndAChampionItsAuraAndAHeavierOutline() {
        val glyphAt = Offset(CENTER.x, CENTER.y - RADIUS * AFFIX_GLYPH_LIFT)
        val plainElite = enemyDrawn(EnemyType.ELITE, affinity(), detail = true)
        assertEquals(0, plainElite.count(glyphAt, 0f, 3f) { sameHue(it, Kk.Bone) }, "no affix, no glyph")
        for (affix in EliteAffix.entries) {
            val elite = enemyDrawn(EnemyType.ELITE, affinity(affix = affix.ordinal), detail = true)
            assertTrue(elite.count(glyphAt, 0f, AFFIX_GLYPH_SIZE * 0.5f) { sameHue(it, Kk.Bone) } > 4, "$affix shows on the halo's top")
            // Elites keep their detail in a crowd, like their status patterns.
            val crowded = enemyDrawn(EnemyType.ELITE, affinity(affix = affix.ordinal), detail = false)
            assertTrue(crowded.count(glyphAt, 0f, AFFIX_GLYPH_SIZE * 0.5f) { sameHue(it, Kk.Bone) } > 4, "$affix stays in a crowd")
        }
        val aura = RADIUS * CHAMPION_AURA
        val plain = enemyDrawn(EnemyType.DRIFTER, affinity(), detail = true)
        val champion = enemyDrawn(EnemyType.DRIFTER, affinity(champion = true), detail = true)
        assertEquals(0, plain.count(CENTER, aura - 2f, aura + 2f) { sameHue(it, Kk.Bone) }, "an ordinary enemy has no aura")
        assertTrue(champion.count(CENTER, aura - 2f, aura + 2f) { sameHue(it, Kk.Bone) } > 60, "a champion wears a dashed bone aura")
        assertEquals(0, enemyDrawn(EnemyType.DRIFTER, affinity(champion = true), detail = false)
            .count(CENTER, aura - 2f, aura + 2f) { sameHue(it, Kk.Bone) }, "a crowd drops the aura like status patterns")
        val plainOutline = plain.count(CENTER, 0f, RADIUS + 3f) { sameHue(it, roles.threat, 0.6f) }
        val championOutline = champion.count(CENTER, 0f, RADIUS + 3f) { sameHue(it, roles.threat, 0.6f) }
        assertTrue(championOutline > plainOutline, "the champion's threat outline is heavier ($championOutline vs $plainOutline px)")
    }

    private fun affinity(affix: Int = EnemyAffinity.NONE, champion: Boolean = false): Int = EnemyAffinity.encode(
        aspect = EnemyAffinity.NONE, immune = false, weakness = EnemyAffinity.NONE, armored = false, fragile = false, shifting = false,
        phase = 0, affix = affix, champion = champion,
    )

    private fun enemyDrawn(type: EnemyType, affinity: Int, detail: Boolean): PixelMap {
        val enemy = EnemyProjection(1, type, 0f, 0f, 0f, 0f, 50f, 100f, RADIUS, 1f, 0f, 0f, 0f, 0f, 0f, false, affinityBits = affinity)
        val model = base().with("enemies" to immutableListOf(enemy))
        return draw { drawEnemy(it, it.enemies.single(), 0f, 0f, roles, statusDetail = detail) }(model)
    }

    /** A running model at the screen size with the Core and camera at the origin, [event] in progress at [elapsed]. */
    private fun running(event: WorldEventProjection, elapsed: Float = 12f): GameplayRenderModel =
        base().with("worldEvent" to event, "elapsed" to elapsed)

    private fun base(): GameplayRenderModel = hudTestModel(W.toFloat(), H.toFloat()).with(
        "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "elapsed" to 0f, "enemies" to immutableListOf<EnemyProjection>(),
    )

    /** Draws [block] over ink at the test size. */
    private fun draw(block: DrawScope.(GameplayRenderModel) -> Unit): (GameplayRenderModel) -> PixelMap = { model ->
        drawn(W, H) { block(model) }
    }

    private fun drawn(width: Int, height: Int, block: DrawScope.() -> Unit): PixelMap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            block()
        }
        return bitmap.toPixelMap()
    }

    private inline fun PixelMap.count(center: Offset, inner: Float, outer: Float, predicate: (Color) -> Boolean): Int {
        var count = 0
        val left = (center.x - outer).toInt().coerceAtLeast(0)
        val right = (center.x + outer).toInt().coerceAtMost(width - 1)
        val top = (center.y - outer).toInt().coerceAtLeast(0)
        val bottom = (center.y + outer).toInt().coerceAtMost(height - 1)
        for (y in top..bottom) for (x in left..right) {
            val distance = hypot(x + 0.5f - center.x, y + 0.5f - center.y)
            if (distance < inner || distance > outer) continue
            if (predicate(this[x, y])) count++
        }
        return count
    }

    /** A pixel blended from ink toward [color]: its channels keep [color]'s proportions. */
    private fun sameHue(pixel: Color, color: Color, minStrength: Float = 0.12f): Boolean {
        val ink = Kk.Ink
        val dr = pixel.red - ink.red
        val dg = pixel.green - ink.green
        val db = pixel.blue - ink.blue
        val tr = color.red - ink.red
        val tg = color.green - ink.green
        val tb = color.blue - ink.blue
        val strength = (dr * tr + dg * tg + db * tb) / (tr * tr + tg * tg + tb * tb)
        if (strength < minStrength) return false
        return abs(dr - tr * strength) < 0.06f && abs(dg - tg * strength) < 0.06f && abs(db - tb * strength) < 0.06f
    }

    private fun Rect.inside(width: Int, height: Int) = left >= -0.5f && top >= -0.5f && right <= width + 0.5f && bottom <= height + 0.5f

    private companion object {
        const val W = 960
        const val H = 600
        const val RADIUS = 30f
        val CENTER = Offset(W * 0.5f, H * 0.5f)
    }
}
