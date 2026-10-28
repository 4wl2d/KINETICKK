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
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ambient sites drawn by the real world renderer: a drift cache is a bone crate where it lies, a cache within its
 * reveal distance and its edge marker never draw together, a cache beyond it never shows a marker, and the Golden
 * Drifter's trigger is never drawn.
 */
class AmbientSiteRenderingTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun aRevealedCacheAndItsEdgeMarkerNeverDrawTogether() {
        // The cache at the world origin, the Core 300 u from it (well inside the 1,800 u reveal distance).
        for ((width, height) in sizes) for (textScale in listOf(1f, 1.75f)) {
            val model = model(width, height, coreX = 300f, cache(SiteKind.DRIFT_CACHE, reveal = 1_800f))
            var marks = 0
            var markers = 0
            sweep(width, height).forEach { (x, y) ->
                drawAt(width, height, textScale, model, x, y)
                val where = "$width x $height text=$textScale, cache at ($x, $y)"
                val mark = WorldDrawProbe.rects(WorldDrawn.POINT_MARK)
                val marker = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                assertEquals(1, mark.size + marker.size, "exactly one of the cache and its edge marker: $where")
                mark.forEach { box -> assertTrue(box.onScreen(width, height), "whole cache mark on screen: $where") }
                marks += mark.size
                markers += marker.size
            }
            assertTrue(marks > 0 && markers > 0, "the sweep crosses the hand-over at $width x $height ($marks marks, $markers markers)")
        }
    }

    @Test
    fun aCacheBeyondItsRevealDistanceDrawsOnlyWhereItLies() {
        // The Core 2,500 u away: past the reveal distance, so no marker, and the crate still draws while in view.
        val (width, height) = sizes.first()
        val model = model(width, height, coreX = 2_500f, cache(SiteKind.DRIFT_CACHE, reveal = 1_800f))
        var marks = 0
        sweep(width, height).forEach { (x, y) ->
            drawAt(width, height, 1f, model, x, y)
            assertEquals(0, WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER).size, "no marker past the reveal distance at ($x, $y)")
            marks += WorldDrawProbe.rects(WorldDrawn.POINT_MARK).size
        }
        assertTrue(marks > 0)
    }

    @Test
    fun aCacheIsABoneCrateWhereItLiesAndTheGoldenTriggerIsNeverDrawn() {
        val empty = render(model(1_440, 810, coreX = 600f))
        val withCache = render(model(1_440, 810, coreX = 600f, cache(SiteKind.DRIFT_CACHE, reveal = 1_800f)))
        val bone = Kk.Bone.toArgb()
        var changed = 0
        var bonePixels = 0
        for (y in 0 until 810) for (x in 0 until 1_440) {
            val index = y * 1_440 + x
            if (withCache[index] == empty[index]) continue
            changed++
            // The crate (44 u across, bobbing 3 u) stays inside its 40 u mark around the screen centre.
            assertTrue(abs(x + 0.5f - 720f) <= AMBIENT_MARK_HALF && abs(y + 0.5f - 405f) <= AMBIENT_MARK_HALF,
                "a cache pixel at ($x, $y) lies inside its mark")
            if (withCache[index] == bone) bonePixels++
        }
        assertTrue(changed > 120, "the crate is drawn ($changed px)")
        assertTrue(bonePixels > 40, "the crate is bone ($bonePixels px)")

        val golden = render(model(1_440, 810, coreX = 600f, cache(SiteKind.GOLDEN_DRIFTER, reveal = 0f)))
        assertTrue(golden.contentEquals(empty), "the Golden Drifter's trigger is never drawn")
    }

    private fun cache(kind: SiteKind, reveal: Float) =
        SiteProjection(kind, kind.name, 0f, 0f, false, 0f, -1, 0f, immutableListOf(), 0f, 0f, activationRadius = 70f, reveal = reveal)

    /** Screen points from just past each edge to well inside it, so a mark crosses every edge and the HUD. */
    private fun sweep(width: Int, height: Int): List<Pair<Float, Float>> {
        val across = (-110..190 step 3).map { it.toFloat() }
        val alongX = (40..(width - 40) step 50).map { it.toFloat() }
        val alongY = (40..(height - 40) step 50).map { it.toFloat() }
        return alongX.flatMap { x -> across.flatMap { d -> listOf(x to d, x to height - d) } } +
            alongY.flatMap { y -> across.flatMap { d -> listOf(d to y, width - d to y) } }
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

    /** A quiet world (no enemies, totem or sites) with the Core at ([coreX], 0) and [ambient] sites. */
    private fun model(width: Int, height: Int, coreX: Float, vararg ambient: SiteProjection): GameplayRenderModel =
        hudTestModel(width.toFloat(), height.toFloat()).with(
            "coreX" to coreX, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
            "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf<SiteProjection>(), "trial" to null,
            "ambientSites" to immutableListOf(*ambient),
        )

    private fun Rect.onScreen(width: Int, height: Int) = left >= 0f && top >= 0f && right <= width && bottom <= height
}
