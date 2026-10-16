// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatusElement
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkRarityTile
import kinetickk.foundation.design.kkContrast
import kinetickk.foundation.design.kkLuminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How far (px at density 1) a Codex cell's rarity frame reaches into the test catalog's common cells,
 * with its antialiasing: the iron border and its seam end under 3 dp in, and iron draws no glow.
 */
internal const val CODEX_FRAME_PX = 6f

/**
 * Rarity and type never mix on a Codex tile: its frame is the rarity's material, drawn as the
 * foundation draws that material on any tile (the reward card's ladder), pixel for pixel the same
 * whatever the item's elements, Kinetic or not, and the five rarities wear five different frames.
 * The frame is read from the outer ring of its border, which nothing of the type channel reaches.
 */
class CodexRarityTileTest {
    @Test
    fun anItemTilesFrameIsItsRarityMaterialWhateverItsType() {
        // One item of every type: Kinetic (no element), each element alone and each pair of neighbouring elements.
        val base = codexTestCatalog().items.first()
        val elements = listOf(emptyList<StatusElement>()) + StatusElement.entries.map { listOf(it) } + StatusElement.entries.zipWithNext { a, b -> listOf(a, b) }
        val types = elements.map { base.copy(elements = it.toImmutableList()) }
        for ((name, roles) in Palettes) {
            val frames = ItemRarity.entries.map { rarity ->
                val material = KkCardMaterial.ofRank(rarity.rank)
                val expected = frameRing { drawKkRarityTile(roles.frame(material)) }
                types.forEach { type ->
                    for (lit in listOf(false, true)) {
                        val icon = CodexIcon.Item(type.copy(rarity = rarity), stack = 1, reborn = lit)
                        val actual = frameRing { drawCodexItemTile(icon, roles, lit, Center, Radius) }
                        assertSameRing(expected, actual, "$name: a $rarity ${type.elements.ifEmpty { "Kinetic" }} tile wears $material")
                    }
                }
                expected.toList()
            }
            assertEquals(ItemRarity.entries.size, frames.toSet().size, "$name: every rarity wears its own frame")
        }
    }

    @Test
    fun aRelicTileWearsTheRelicCardsChromeWhateverItsAspect() {
        assertEquals(RelicAspect.entries.toSet(), testRelics.map { it.aspect }.toSet(), "the relics cover every aspect")
        for ((name, roles) in Palettes) {
            // A relic has no rarity: its reward card wears chrome, and its tile does too.
            val expected = frameRing { drawKkRarityTile(roles.frame(KkCardMaterial.CHROME)) }
            testRelics.forEach { relic ->
                val actual = frameRing { drawCodexRelicTile(CodexIcon.Relic(relic, rank = 1), RelicPolicy(4, 3), roles, false, Center, Radius) }
                assertSameRing(expected, actual, "$name: the ${relic.aspect} relic ${relic.id} wears chrome")
            }
        }
    }

    /**
     * The Items matrix fills a cell with its rarity's material instead of framing it: the fill under the
     * glyph's left (a 3 × 3 dp patch at inset + 1 dp, 40 % down) is the same for every type of a rarity,
     * the five fills differ, Common and Uncommon are a large lightness step apart and so are Uncommon and
     * Rare over the whole cell, and every fill stands off the hatch of an undiscovered cell. The stack bar
     * shows only for an item held in the build.
     */
    @Test
    fun aMatrixCellFillIsItsRarityMaterialWhateverItsType() {
        val base = codexTestCatalog().items.first()
        val elements = listOf(emptyList<StatusElement>(), listOf(StatusElement.CRYO), listOf(StatusElement.THERMAL, StatusElement.ENTROPY))
        val types = elements.map { base.copy(elements = it.toImmutableList(), maxStacks = 3) }
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(DENSITY), LayoutDirection.Ltr), 1f)
        val marks = codexItemCellMarks(MATRIX.toFloat(), DENSITY)
        val patchLeft = (marks.inset + DENSITY).toInt()
        val patchTop = (MATRIX * 0.4f).toInt()
        val patchSide = (3f * DENSITY).toInt()
        // Two points of the fill clear of every mark: the patch's centre and low in the middle, under the glyph.
        val points = listOf(patchLeft + patchSide / 2 to patchTop + patchSide / 2, MATRIX / 2 to (MATRIX * 0.9f).toInt())
        val barX = MATRIX / 2
        val barY = marks.bar.center.y.toInt()
        for ((name, roles) in Palettes) {
            val painter = CanvasTextMeasurer(measurer.delegate, 1f, roles = roles)
            val fills = ItemRarity.entries.map { rarity ->
                val fill = codexRarityFill(rarity.rank, roles)
                // The fill alone, with no mark of any cell drawn over it.
                val bare = cell { drawRect(fill.brush(Rect(Offset.Zero, size))) }
                val patches = types.map { type ->
                    val entry = codexItemEntry(type.copy(rarity = rarity), CodexRenderModel(immutableSetOf(type.id), CodexRunStacks(), immutableListOf(type)), 0L)
                    val map = cell { drawCodexItemRowCell(entry, fill, fill.brush(Rect(Offset.Zero, size)), marks, painter, Path()) }
                    // Iron and steel are solid: their patch is one colour (the glyph-box measures rely on it).
                    if (rarity.rank <= 2) for (y in patchTop until patchTop + patchSide) for (x in patchLeft until patchLeft + patchSide) {
                        assertEquals(map[patchLeft, patchTop], map[x, y], "$name: the $rarity fill is solid at $x,$y")
                    }
                    // Not in the build: no bar along the bottom, only the fill.
                    assertEquals(bare[barX, barY], map[barX, barY], "$name: a $rarity item outside the build draws no bar")
                    map
                }
                patches.forEach { map ->
                    for (y in patchTop until patchTop + patchSide) for (x in patchLeft until patchLeft + patchSide) {
                        assertEquals(patches.first()[x, y], map[x, y], "$name: every $rarity type shares the fill at $x,$y")
                    }
                }
                // Held once in the build: the bar covers the fill there.
                val type = types.first().copy(rarity = rarity)
                val held = codexItemEntry(type, CodexRenderModel(immutableSetOf(type.id), CodexRunStacks(List(type.id + 1) { 1 }.toImmutableList()),
                    immutableListOf(type)), 0L)
                val heldMap = cell { drawCodexItemRowCell(held, fill, fill.brush(Rect(Offset.Zero, size)), marks, painter, Path()) }
                assertTrue(channelDelta(bare[barX, barY], heldMap[barX, barY]) > 0.08f, "$name: a held $rarity item draws its stack bar")
                points.map { (x, y) -> patches.first()[x, y] }
            }
            for (a in fills.indices) for (b in a + 1 until fills.size) {
                val difference = fills[a].indices.maxOf { point -> channelDelta(fills[a][point], fills[b][point]) }
                assertTrue(difference >= 0.08f, "$name: ${ItemRarity.entries[a]} and ${ItemRarity.entries[b]} fills differ by $difference")
            }
            val common = fills[ItemRarity.COMMON.ordinal].first()
            val uncommon = fills[ItemRarity.UNCOMMON.ordinal].first()
            assertTrue(kkContrast(common, uncommon) >= 1.8f, "$name: Common and Uncommon are ${kkContrast(common, uncommon)}:1 apart")
            // Steel and chrome: over the whole cell, the polished chrome stands well above the steel in lightness.
            val steel = meanLuminance(codexRarityFill(ItemRarity.UNCOMMON.rank, roles))
            val chrome = meanLuminance(codexRarityFill(ItemRarity.RARE.rank, roles))
            val ratio = (max(steel, chrome) + 0.05f) / (min(steel, chrome) + 0.05f)
            assertTrue(ratio >= 1.5f, "$name: Uncommon (mean luminance $steel) and Rare ($chrome) are $ratio:1 apart")
            fills.forEachIndexed { rarity, colors ->
                colors.forEach { color -> assertTrue(kkContrast(color, Kk.Ink3) >= 1.5f, "$name: ${ItemRarity.entries[rarity]} against the hatch face is ${kkContrast(color, Kk.Ink3)}:1") }
            }
        }
    }

    /**
     * A NEW item's notch is a `you` triangle edged in Ink on every fill, so it stands off the light Epic
     * spectrum and the Legendary gold as clearly as off iron: its core is `you`, its legs' edge Ink, and the
     * fill just beyond it contrasts at least 3:1 with its core or its edge.
     */
    @Test
    fun aNewNotchStandsOffEveryRarityFill() {
        val base = codexTestCatalog().items.first()
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(DENSITY), LayoutDirection.Ltr), 1f)
        val marks = codexItemCellMarks(MATRIX.toFloat(), DENSITY)
        val notch = marks.notch
        // Inside the triangle, on its left leg's Ink edge (in the inset), and just beyond its hypotenuse.
        val core = (notch.left + notch.width / 4f).toInt() to (notch.top + notch.width / 4f).toInt()
        val edge = (notch.left - marks.hairline / 2f).toInt() to notch.center.y.toInt()
        val beyond = (notch.center.x + 2f * DENSITY).toInt() to (notch.center.y + 2f * DENSITY).toInt()
        for ((name, roles) in Palettes) {
            val painter = CanvasTextMeasurer(measurer.delegate, 1f, roles = roles)
            ItemRarity.entries.forEach { rarity ->
                val item = base.copy(rarity = rarity)
                val entry = codexItemEntry(item, CodexRenderModel(immutableSetOf(item.id), CodexRunStacks(), immutableListOf(item),
                    newItemIds = immutableSetOf(item.id)), 0L)
                assertTrue(entry.isNew)
                val fill = codexRarityFill(rarity.rank, roles)
                val map = cell { drawCodexItemRowCell(entry, fill, fill.brush(Rect(Offset.Zero, size)), marks, painter, Path()) }
                val where = "$name: the $rarity notch"
                assertTrue(channelDelta(map[core.first, core.second], roles.you) < 0.02f, "$where is `you` inside: ${map[core.first, core.second]}")
                assertTrue(channelDelta(map[edge.first, edge.second], Kk.Ink) < 0.02f, "$where is edged in Ink: ${map[edge.first, edge.second]}")
                val outside = map[beyond.first, beyond.second]
                val standoff = max(kkContrast(outside, map[core.first, core.second]), kkContrast(outside, Kk.Ink))
                assertTrue(standoff >= 3f, "$where stands off the fill $outside by $standoff:1")
            }
        }
    }

    private fun channelDelta(a: Color, b: Color): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    /** The mean relative luminance of a [MATRIX] px cell painted with [fill] alone. */
    private fun meanLuminance(fill: CodexRarityFill): Float {
        val map = cell { drawRect(fill.brush(Rect(Offset.Zero, size))) }
        var sum = 0f
        for (y in 0 until MATRIX) for (x in 0 until MATRIX) sum += kkLuminance(map[x, y])
        return sum / (MATRIX * MATRIX)
    }

    /** A [MATRIX] px matrix cell drawn by [draw]. */
    private fun cell(draw: DrawScope.() -> Unit): PixelMap {
        val bitmap = ImageBitmap(MATRIX, MATRIX)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(MATRIX.toFloat(), MATRIX.toFloat()), draw)
        return bitmap.toPixelMap()
    }

    private fun assertSameRing(expected: IntArray, actual: IntArray, message: String) {
        assertEquals(expected.size, actual.size, message)
        val differing = expected.indices.count { expected[it] != actual[it] }
        assertEquals(0, differing, "$message: $differing of ${expected.size} frame pixels differ from the material's")
    }

    /** The outer [RING] px of a [SIDE] px tile drawn by [draw] (the border is wider on every material). */
    private fun frameRing(draw: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(SIDE.toFloat(), SIDE.toFloat()), draw)
        val map = bitmap.toPixelMap()
        val ring = ArrayList<Int>()
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            if (min(min(x, y), min(SIDE - 1 - x, SIDE - 1 - y)) < RING) ring += map[x, y].toArgb()
        }
        return ring.toIntArray()
    }

    private companion object {
        /** A board-size Codex tile: 96 dp at density 2. */
        const val DENSITY = 2f
        const val SIDE = 192
        const val RING = 2
        val Center = Offset(SIDE * 0.5f, SIDE * 0.5f)
        const val Radius = SIDE * 0.3f
        /** A 36 dp matrix cell at density 2. */
        const val MATRIX = 72
        val Palettes = listOf("Default" to KkRolePalette.Default, "Protan" to KkRolePalette.Protan, "Mono" to KkRolePalette.Mono)
    }
}
