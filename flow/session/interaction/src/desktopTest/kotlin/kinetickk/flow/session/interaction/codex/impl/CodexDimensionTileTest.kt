// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldDiscoveryKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.SiteGlyph
import kinetickk.ball.gameplay.interaction.canvas.drawSiteGlyph
import kinetickk.ball.gameplay.interaction.canvas.drawWorldDiscoveryMark
import kinetickk.ball.gameplay.interaction.canvas.worldDiscoveryGlyph
import kinetickk.ball.gameplay.interaction.canvas.worldDiscoveryLayer
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three dimensions in the Codex World category: "???" over their hint until found, then their name and description,
 * each tile carrying its layer's own sigil (bone once found, muted over a hatch before), every other world entry its
 * site glyph as before.
 */
class CodexDimensionTileTest {
    private val catalog = codexTestCatalog()
    private val progress = codexTestProgress()
    private val roles = KkRolePalette.Default
    private val dimensions = listOf(WorldDiscovery.ZENITH, WorldDiscovery.NADIR, WorldDiscovery.ARCHIVE)

    @Test
    fun eachDimensionTileCarriesItsLayersSigilFoundOrHidden() {
        val hidden = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        val found = hidden.copy(discoveredWorldEntries = immutableSetOf(*dimensions.toTypedArray()))
        for (language in AppLanguage.entries) dimensions.forEach { id ->
            val definition = catalog.worldDiscoveries.single { it.id == id }
            val shut = entry(id, hidden, language)
            assertEquals(language.text(CodexText.UNKNOWN_TITLE), shut.title)
            assertEquals(definition.hint.localizedContent(language), shut.description)
            assertFalse(shut.discovered)
            val open = entry(id, found, language)
            assertEquals(definition.name.localizedContent(language), open.title)
            assertEquals(definition.description.localizedContent(language), open.description)
            assertEquals(language.text(CodexText.DIMENSION_KIND), open.kind)
            assertTrue(open.discovered)
        }
        // Found: three different sigils in bone. Hidden: the same marks muted (over the hatch), never in bone.
        val foundTiles = dimensions.map { tile(entry(it, found)) }
        val hiddenTiles = dimensions.map { tile(entry(it, hidden)) }
        for (a in 0..2) for (b in a + 1..2) {
            assertFalse(pixels(foundTiles[a]).contentEquals(pixels(foundTiles[b])), "${dimensions[a]} and ${dimensions[b]} share a mark")
            assertFalse(pixels(hiddenTiles[a]).contentEquals(pixels(hiddenTiles[b])), "hidden ${dimensions[a]} and ${dimensions[b]} share a mark")
        }
        foundTiles.forEachIndexed { index, map -> assertTrue(near(map, Kk.Bone) > 60, "${dimensions[index]}: its sigil in bone") }
        hiddenTiles.forEachIndexed { index, map ->
            assertEquals(0, near(map, Kk.Bone), "hidden ${dimensions[index]}: nothing in bone")
            assertTrue(near(map, Kk.Mute) > 30, "hidden ${dimensions[index]}: the muted sigil")
        }
    }

    @Test
    fun onlyTheDimensionsTradeTheirSiteGlyphForASigil() {
        assertEquals(listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE), dimensions.map(::worldDiscoveryLayer))
        WorldDiscovery.entries.filter { it.kind != WorldDiscoveryKind.DIMENSION }.forEach { id ->
            assertNull(worldDiscoveryLayer(id), "$id")
            assertContentEquals(pixels(mark { drawSiteGlyph(worldDiscoveryGlyph(id), it, SIDE * 0.5f, Kk.Bone) }),
                pixels(mark { drawWorldDiscoveryMark(id, it, SIDE * 0.5f, Kk.Bone) }), "$id keeps its site glyph")
        }
        val horizon = pixels(mark { drawSiteGlyph(SiteGlyph.DIMENSION, it, SIDE * 0.5f, Kk.Bone) })
        dimensions.forEach { id ->
            assertFalse(horizon.contentEquals(pixels(mark { drawWorldDiscoveryMark(id, it, SIDE * 0.5f, Kk.Bone) })), "$id draws its sigil")
        }
    }

    /** Each sigil spans about as much of its mark as the site glyphs beside it: never the smallest mark on the grid. */
    @Test
    fun eachSigilReadsAsLargeAsTheSiteGlyphsBesideIt() {
        fun span(map: PixelMap): Int {
            var left = SIDE
            var right = -1
            var top = SIDE
            var bottom = -1
            for (y in 0 until SIDE) for (x in 0 until SIDE) {
                val p = map[x, y]
                if (abs(p.red - Kk.Ink.red) + abs(p.green - Kk.Ink.green) + abs(p.blue - Kk.Ink.blue) < 0.2f) continue
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
            return maxOf(right - left, bottom - top) + 1
        }
        val size = SIDE * 0.5f
        val glyphs = WorldDiscovery.entries.filter { it.kind != WorldDiscoveryKind.DIMENSION }
            .map { id -> span(mark { drawWorldDiscoveryMark(id, it, size, Kk.Bone) }) }
        dimensions.forEach { id ->
            val sigil = span(mark { drawWorldDiscoveryMark(id, it, size, Kk.Bone) })
            assertTrue(sigil >= glyphs.average() * 0.95, "$id spans $sigil px against the site glyphs' ${glyphs.average()} px")
            assertTrue(sigil <= glyphs.max() * 1.05, "$id spans $sigil px, past the widest site glyph's ${glyphs.max()} px")
        }
    }

    private fun entry(id: WorldDiscovery, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry =
        codexCatalogEntries(CODEX_WORLD_CATEGORY, "", CodexItemFilter.ALL, model, catalog, progress, language).single { it.key == "world/$id" }

    private fun tile(entry: CodexEntry): PixelMap {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(DENSITY), LayoutDirection.Ltr), 1f, roles = roles)
        val bitmap = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(SIDE.toFloat(), SIDE.toFloat())) {
            drawCodexCell(entry, catalog, measurer, band = false, hovered = false, markBand = 0f)
        }
        return bitmap.toPixelMap()
    }

    private fun mark(block: DrawScope.(Offset) -> Unit): PixelMap {
        val bitmap = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(SIDE.toFloat(), SIDE.toFloat())) {
            drawRect(Kk.Ink)
            block(Offset(SIDE * 0.5f, SIDE * 0.5f))
        }
        return bitmap.toPixelMap()
    }

    private fun pixels(map: PixelMap): IntArray = IntArray(SIDE * SIDE) { map[it % SIDE, it / SIDE].toArgb() }

    private fun near(map: PixelMap, color: Color): Int {
        var count = 0
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val p = map[x, y]
            if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < 0.06f) count++
        }
        return count
    }

    private companion object {
        /** A board-size Codex tile: 96 dp at density 2. */
        const val DENSITY = 2f
        const val SIDE = 192
    }
}
