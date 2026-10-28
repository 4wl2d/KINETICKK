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
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.localeList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Off-screen markers drawn by the real world renderer next to the real HUD (bundled fonts): every
 * edge marker stays clear of every block the HUD actually draws (its probe rects and the running
 * controls), not only of the keep-out the planner reads, for targets in every direction, at every
 * text size, with and without a boss and a trial.
 */
class WorldEdgeMarkerHudTest {
    @Test
    fun edgeMarkersNeverTouchTheHudAsDrawn() {
        var markers = 0
        for ((dpWidth, dpHeight) in listOf(1_440f to 810f, 844f to 390f, 390f to 844f)) for (density in listOf(1f, 3f)) {
            val width = dpWidth * density
            val height = dpHeight * density
            for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr, cacheSize = 64),
                    textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
                for (boss in listOf(null, EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) {
                    val base = hudTestModel(dpWidth, dpHeight).with(
                        "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
                        "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f,
                        "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f, *levelOverrides(),
                        "enemies" to (if (boss == null) emptyList() else listOf(EnemyProjection(4, boss, 0f, -9_000f, 0f, 0f, 500f, 1_000f,
                            60f, 0f, 0f, 0f, 0f, 0f, 0f, false))).toImmutableList(),
                    )
                    for (step in 0 until DIRECTIONS) {
                        val angle = step * 2f * PI.toFloat() / DIRECTIONS
                        // The totem in this direction and, while a trial runs, its orbit on the opposite side.
                        val model = base.with(
                            "totem" to TotemProjection(cos(angle) * DISTANCE, sin(angle) * DISTANCE, 1f),
                            "sites" to (if (trial) listOf(orbit(-cos(angle) * DISTANCE, -sin(angle) * DISTANCE)) else emptyList())
                                .toImmutableList(),
                            "trial" to (if (trial) siteTrial(orbit(0f, 0f)) else null),
                        )
                        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(width, height)) {
                            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                            drawHud(model, measurer, 1f)
                        }
                        val drawn = drawnHud(width, height, density)
                        val where = "$language ${dpWidth}x$dpHeight @$density text=$textScale boss=$boss trial=$trial " +
                            "direction=${step * 360 / DIRECTIONS}"
                        val boxes = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                        assertTrue(boxes.size == (if (trial) 2 else 1), "$where: one marker per off-screen target ($boxes)")
                        boxes.forEach { box ->
                            markers++
                            assertTrue(box.left >= 0f && box.top >= 0f && box.right <= width && box.bottom <= height, "$where: marker $box on screen")
                            drawn.forEach { (name, block) ->
                                assertTrue(!box.overlaps(block), "$where: marker $box on the HUD's $name $block")
                            }
                        }
                    }
                }
            }
        }
        assertTrue(markers > 10_000, "the sweep draws its markers ($markers)")
    }

    /**
     * Two window offers, the sigil, the totem and a cache within its reveal distance: five markers, apart from each
     * other and clear of the HUD in every layout and language, collected in priority order (sites, totem, ambient).
     */
    @Test
    fun sitesOfEveryFamilyKeepTheirMarkersApartInPriorityOrder() {
        for ((dpWidth, dpHeight) in listOf(1_440f to 810f, 844f to 390f, 390f to 844f)) for (language in AppLanguage.entries)
        for (textScale in listOf(1f, 1.75f)) for (turn in 0 until 8) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
            val spin = turn * PI.toFloat() / 4f
            fun at(angle: Float, distance: Float = DISTANCE) = cos(spin + angle) * distance to sin(spin + angle) * distance
            val model = familyModel(dpWidth, dpHeight).with(
                "totem" to at(4.4f).let { (x, y) -> TotemProjection(x, y, 1f) },
                "sites" to immutableListOf(offer(SiteKind.GATE_RACE, at(0.2f)), offer(SiteKind.BOUNTY_ARENA, at(1.5f)),
                    offer(SiteKind.FAR_SIGIL, at(2.9f))),
                "ambientSites" to immutableListOf(cache(at(5.6f, 1_600f))),
            )
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(dpWidth, dpHeight)) {
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                drawHud(model, measurer, 1f)
            }
            val where = "$language ${dpWidth}x$dpHeight text=$textScale turn=$turn"
            val batch = WorldOverlayScratch.markers
            assertEquals(listOf(EdgeMarkerIcon.RACE, EdgeMarkerIcon.BOUNTY, EdgeMarkerIcon.SIGIL, EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.CACHE),
                (0 until batch.count).map { batch.icon[it] }, where)
            val boxes = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
            assertEquals(5, boxes.size, "$where: $boxes")
            val drawn = drawnHud(dpWidth, dpHeight, 1f)
            boxes.forEachIndexed { index, box ->
                assertTrue(box.left >= 0f && box.top >= 0f && box.right <= dpWidth && box.bottom <= dpHeight, "$where: marker $box on screen")
                drawn.forEach { (name, block) -> assertTrue(!box.overlaps(block), "$where: marker $box on the HUD's $name $block") }
                boxes.drop(index + 1).forEach { other -> assertTrue(!box.overlaps(other), "$where: markers $box and $other meet") }
            }
        }
    }

    /**
     * The batch keeps its first eight markers: the running site's target comes first (a race leads to its next gate),
     * window sites before the totem, and ambient sites go first when the batch is full.
     */
    @Test
    fun theRunningSitesTargetLeadsAndAFullBatchDropsAmbientSites() {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English, HudTestFonts.typography)
        fun collect(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel): List<EdgeMarkerIcon?> {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(1_440f, 810f)) {
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
            }
            val batch = WorldOverlayScratch.markers
            return (0 until batch.count).map { batch.icon[it] }
        }
        // A race whose next gate lies off screen, listed after an offer: its gate's marker comes first.
        val gate = kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection(2_600f, -1_400f)
        val race = SiteProjection(SiteKind.GATE_RACE, "Gate race", 200f, 100f, true, 9f, 1, 1f / 6f, immutableListOf(), 0f, 0f,
            course = immutableListOf(kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection(300f, 0f), gate))
        val running = familyModel(1_440f, 810f).withSites(offer(SiteKind.SEALED_ANOMALY, 0f to DISTANCE), race)
        assertEquals(listOf(EdgeMarkerIcon.RACE, EdgeMarkerIcon.SEALED_ANOMALY), collect(running))
        val batch = WorldOverlayScratch.markers
        val gateDistance = kotlin.math.hypot(race.x + gate.dx, race.y + gate.dy)
        assertEquals(gateDistance, batch.distance[0], 0.5f, "the race's marker leads to its next gate")
        // Seven offers and the totem fill the batch: the cache within its reveal distance is dropped.
        val crowded = familyModel(1_440f, 810f).with(
            "totem" to TotemProjection(-DISTANCE, 0f, 1f),
            "sites" to (0 until 7).map { offer(SiteKind.UNTOUCHED_TRIAL, cos(it * 0.8f) * DISTANCE to sin(it * 0.8f) * DISTANCE) }
                .toImmutableList(),
            "ambientSites" to immutableListOf(cache(0f to 1_600f)),
        )
        val icons = collect(crowded)
        assertEquals(EdgeMarkerBatch.CAPACITY, icons.size)
        assertEquals(List(7) { EdgeMarkerIcon.CHALLENGE } + EdgeMarkerIcon.TOTEM, icons)
        // With room left, the same cache gets its marker, last.
        assertEquals(EdgeMarkerIcon.CACHE, collect(crowded.with("sites" to immutableListOf<SiteProjection>())).last())
        // Past its reveal distance (or an egg, which reveals nothing) it gets none.
        val far = crowded.with("sites" to immutableListOf<SiteProjection>(),
            "ambientSites" to immutableListOf(cache(0f to 1_900f), egg(0f to -1_200f)))
        assertEquals(listOf(EdgeMarkerIcon.TOTEM), collect(far))
    }

    /**
     * Structure entrances off screen: the two nearest get markers (never the dungeon the Core is in, never an entrance on
     * screen), after the totem and before the ambient caches, apart from each other and clear of the HUD in every layout
     * and language.
     */
    @Test
    fun structureEntrancesMarkTheTwoNearestClearOfTheHud() {
        for ((dpWidth, dpHeight) in listOf(1_440f to 810f, 844f to 390f, 390f to 844f)) for (language in AppLanguage.entries)
        for (textScale in listOf(1f, 1.75f)) for (turn in 0 until 8) {
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
            val spin = turn * PI.toFloat() / 4f
            fun at(angle: Float, distance: Float) = cos(spin + angle) * distance to sin(spin + angle) * distance
            val base = familyModel(dpWidth, dpHeight)
            val templates = base.content.structures
            // The Core stands in a Spire (its entrance 1,200 below, nearer than any other); four entrances lie off screen
            // and one on screen.
            val held = StructureFixture(templates.template(StructureTemplateId.SPIRE), 0f, 0f, id = 1L shl 8)
            val entrances = listOf(1_600f, 2_400f, 3_200f, 4_000f).mapIndexed { index, distance ->
                entranceAt(templates.template(StructureTemplateId.VAULT), at(0.4f + index * 1.3f, distance), id = (index + 2L) shl 8)
            }
            val onScreen = entranceAt(templates.template(StructureTemplateId.LABYRINTH), 120f to 90f, id = 9L shl 8)
            val model = base.with(
                "totem" to at(5.6f, 3_000f).let { (x, y) -> TotemProjection(x, y, 1f) },
                "ambientSites" to immutableListOf(cache(at(3.1f, 1_500f))),
                "structures" to structureProjection(held, signals = listOf(held, onScreen) + entrances, dungeon = held),
            )
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(dpWidth, dpHeight)) {
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                drawHud(model, measurer, 1f)
            }
            val where = "$language ${dpWidth}x$dpHeight text=$textScale turn=$turn"
            val batch = WorldOverlayScratch.markers
            assertEquals(listOf(EdgeMarkerIcon.TOTEM, EdgeMarkerIcon.STRUCTURE, EdgeMarkerIcon.STRUCTURE, EdgeMarkerIcon.CACHE),
                (0 until batch.count).map { batch.icon[it] }, where)
            assertEquals(1_600f, batch.distance[1], 0.5f, "$where: the nearest entrance first")
            assertEquals(2_400f, batch.distance[2], 0.5f, "$where: then the second nearest")
            val boxes = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
            assertEquals(4, boxes.size, "$where: $boxes")
            val drawn = drawnHud(dpWidth, dpHeight, 1f)
            boxes.forEachIndexed { index, box ->
                assertTrue(box.left >= 0f && box.top >= 0f && box.right <= dpWidth && box.bottom <= dpHeight, "$where: marker $box on screen")
                drawn.forEach { (name, block) -> assertTrue(!box.overlaps(block), "$where: marker $box on the HUD's $name $block") }
                boxes.drop(index + 1).forEach { other -> assertTrue(!box.overlaps(other), "$where: markers $box and $other meet") }
            }
        }
    }

    /** A structure of [template] placed so its entrance (outer door centre) lies at [entrance]. */
    private fun entranceAt(template: kinetickk.ball.content.api.StructureTemplate, entrance: Pair<Float, Float>, id: Long): StructureFixture {
        val (localX, localY) = StructureFixture(template, 0f, 0f).entrance()
        return StructureFixture(template, entrance.first - localX, entrance.second - localY, id = id)
    }

    private fun familyModel(width: Float, height: Float) = hudTestModel(width, height).with(
        "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
        "enemies" to immutableListOf<EnemyProjection>(),
    )

    private fun offer(kind: SiteKind, at: Pair<Float, Float>) =
        SiteProjection(kind, kind.name, at.first, at.second, false, 20f, -1, 0f, immutableListOf(), 0f, 0f)

    private fun cache(at: Pair<Float, Float>) =
        SiteProjection(SiteKind.DRIFT_CACHE, "Drift cache", at.first, at.second, false, 0f, -1, 0f, immutableListOf(), 0f, 0f,
            reveal = 1_800f)

    private fun egg(at: Pair<Float, Float>) =
        SiteProjection(SiteKind.LOST_CURSOR, "Lost cursor", at.first, at.second, false, 0f, -1, 0f, immutableListOf(), 0f, 0f,
            reveal = 0f)

    /** What the HUD drew in the last frame: its probe blocks (not the world-anchored polarity label) and the running controls. */
    private fun drawnHud(width: Float, height: Float, density: Float): List<Pair<String, Rect>> = buildList {
        HudBlock.entries.forEach { block ->
            if (block == HudBlock.POLARITY_LABEL) return@forEach
            HudLayoutProbe.rect(block)?.let { add(block.name to it) }
        }
        forEachRunningControlBounds(width, height, density, RunningControlMask.ALL) { target, left, top, right, bottom ->
            add(target.name to Rect(left, top, right, bottom))
        }
    }

    private fun orbit(x: Float, y: Float) = SiteProjection(SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", x, y, true,
        12f, -1, 0.4f, immutableListOf(), 0f, 0f)

    // The size passed to draw sets the screen; a 1 px bitmap keeps the sweep fast.
    private val canvas = Canvas(ImageBitmap(1, 1))

    private companion object {
        const val DIRECTIONS = 48
        const val DISTANCE = 3_000f
    }
}
