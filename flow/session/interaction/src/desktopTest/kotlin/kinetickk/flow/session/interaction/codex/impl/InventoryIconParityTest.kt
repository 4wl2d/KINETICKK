// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.drawLayeredGlyph
import kinetickk.foundation.design.drawRuneMedallion
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The same raster contract runs independently in both Interaction owners. */
class InventoryIconParityTest {
    @Test
    fun allRelicsMatchSharedGeometryAtEqualParametersAndTime() {
        testRelics.forEach { definition ->
            val group = definition.aspect.ordinal
            listOf(1f, 1.25f, 1.75f).forEach { scale ->
                val actual = render(scale) {
                    drawRelicIcon(definition, RelicPolicy(6, 3), Center, 36f, rank = 2, time = 1.75f, alpha = 0.75f)
                }
                val expected = render(scale) {
                    drawRuneMedallion(RelicRunes.getValue(definition.id), Center, 36f,
                        Accents[group].copy(alpha = 0.75f), FrameSides[group],
                        frameRotation = -PI.toFloat() * 0.5f + if (definition.aspect == RelicAspect.SOVEREIGN) 1.75f * 0.08f else 0f,
                        markerCount = 3, filledMarkerCount = 2, time = 1.75f)
                }
                assertContentEquals(expected, actual, "${definition.id} at text scale $scale")
            }
        }
        assertEquals(RelicId.entries.toSet(), RelicRunes.keys)
        assertEquals(RelicRunes.size, RelicRunes.values.toSet().size, "Every relic keeps its own rune")
    }

    @Test
    fun itemGlyphsReadTheFirstElementThenTheFirstTag() {
        val items = buildList {
            // An element with a tag: the element leads, the tag follows.
            StatusElement.entries.forEach { element ->
                BuildTag.entries.forEach { tag -> add(item(listOf(tag), listOf(element)) to (ElementGlyphs[element]!! to TagGlyphs[tag]!!)) }
            }
            // Two tags without an element, a lone tag (its mark repeats), and elements without tags.
            BuildTag.entries.zipWithNext().forEach { (first, second) -> add(item(listOf(first, second)) to (TagGlyphs[first]!! to TagGlyphs[second]!!)) }
            BuildTag.entries.forEach { tag -> add(item(listOf(tag)) to (TagGlyphs[tag]!! to TagGlyphs[tag]!!)) }
            add(item(emptyList(), listOf(StatusElement.THERMAL, StatusElement.ENTROPY)) to
                (ElementGlyphs[StatusElement.THERMAL]!! to ElementGlyphs[StatusElement.ENTROPY]!!))
            add(item(emptyList(), listOf(StatusElement.CRYO)) to (ElementGlyphs[StatusElement.CRYO]!! to ElementGlyphs[StatusElement.CRYO]!!))
        }
        items.forEachIndexed { index, (item, glyphs) ->
            val rarity = ItemRarity.entries[index % ItemRarity.entries.size]
            val ranked = item.copy(rarity = rarity)
            listOf(false, true).forEach { obscured ->
                val actual = render(1.75f) { drawItemIcon(ranked, Center, 36f, Accents[0], stack = 1, obscured = obscured) }
                val expected = render(1.75f) {
                    drawLayeredGlyph(glyphs.first, glyphs.second,
                        Center, 36f, Accents[0], frameSides = if (obscured) 4 else rarity.rank + 3,
                        markerCount = if (obscured) 1 else rarity.rank, outerArcDegrees = 120f, crossedOut = obscured)
                }
                assertContentEquals(expected, actual, "${item.tags} ${item.elements}, obscured=$obscured")
            }
        }
        assertEquals(ElementGlyphs.size + TagGlyphs.size, (ElementGlyphs.values + TagGlyphs.values).toSet().size,
            "Elements and tags never share a glyph")
    }

    private fun item(tags: List<BuildTag>, elements: List<StatusElement> = emptyList()) = ItemDefinition(
        0, "Item", "Full description", ItemRarity.COMMON, tags.toImmutableList(), elements.toImmutableList(),
        stats = immutableListOf(StatDelta(ItemEffect.MASS, 0.1f)), maxStacks = 3, unlockLevel = 1,
    )

    private fun render(fontScale: Float, draw: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(96, 96)
        CanvasDrawScope().draw(Density(1f, fontScale), LayoutDirection.Ltr, Canvas(bitmap), Size(96f, 96f)) {
            drawRect(Color(0xFF050810))
            draw()
        }
        val map = bitmap.toPixelMap()
        return IntArray(96 * 96) { index -> map[index % 96, index / 96].toArgb() }
    }

    private companion object {
        val Center = Offset(48f, 48f)
        /** Relic aspect accents are the element tokens (`tokens.json`), in aspect order. */
        val Accents = listOf(Color(0xFFD8FF3E), Color(0xFFFF8A1F), Color(0xFFBDEBFF), Color(0xFF4F7DFF),
            Color(0xFFD24CFF), Color(0xFF46D96A), Color(0xFFFFC93C))
        val FrameSides = listOf(3, 6, 8, 4, 5, 7, 10)

        /** The rune of each relic; the in-run renderer keeps the same table. */
        val RelicRunes = mapOf(
            RelicId.PERPETUAL_VECTOR to CanvasRuneStyle.RADIAL_WHEEL,
            RelicId.BLINK_DRIVE to CanvasRuneStyle.OFFSET_CHEVRONS,
            RelicId.GYRE_ENGINE to CanvasRuneStyle.FOUR_SWIRL_ARMS,
            RelicId.GLASS_COMET to CanvasRuneStyle.CRACKED_RING,
            RelicId.EXECUTIONERS_PRISM to CanvasRuneStyle.SLASH_BLADE,
            RelicId.ECHO_CHAMBER to CanvasRuneStyle.NESTED_DIAMONDS,
            RelicId.FURNACE_COVENANT to CanvasRuneStyle.DIAMOND_FLAME,
            RelicId.CINDER_CROWN to CanvasRuneStyle.FOUR_DOT_CROWN,
            RelicId.OVERDRIVE_CASCADE to CanvasRuneStyle.COUNTER_ROTATING_ARCS,
            RelicId.ABSOLUTE_ZERO to CanvasRuneStyle.SIX_PETAL_ROSETTE,
            RelicId.STASIS_ANCHOR to CanvasRuneStyle.RINGED_ANCHOR,
            RelicId.STORMHEART_CAPACITOR to CanvasRuneStyle.BOLT_AND_DOT,
            RelicId.AEGIS_INVERSION to CanvasRuneStyle.CROSSED_DIAMOND,
            RelicId.NEUTRON_HEART to CanvasRuneStyle.OFFSET_RINGS,
            RelicId.EVENT_HORIZON to CanvasRuneStyle.INWARD_CHEVRONS,
            RelicId.VERDANT_ROT to CanvasRuneStyle.STITCHED_DIAGONAL,
            RelicId.PANDEMIC_CODEX to CanvasRuneStyle.FIVE_RAY_FAN,
            RelicId.AGONY_SCEPTER to CanvasRuneStyle.RADIATING_STAFF,
            RelicId.HOLLOW_CROWN to CanvasRuneStyle.OPEN_DIAL,
            RelicId.OUROBOROS_SEAL to CanvasRuneStyle.RETURN_ARROW,
        )

        /** Item glyphs: the element when the item has one, its archetype tag otherwise. */
        val ElementGlyphs = mapOf(
            StatusElement.THERMAL to CanvasGlyphStyle.SUNBURST,
            StatusElement.CRYO to CanvasGlyphStyle.SNOWFLAKE,
            StatusElement.ION to CanvasGlyphStyle.BOLT,
            StatusElement.GRAVITIC to CanvasGlyphStyle.CONCENTRIC_ORB,
            StatusElement.ENTROPY to CanvasGlyphStyle.LEAF,
        )
        val TagGlyphs = mapOf(
            BuildTag.RAM to CanvasGlyphStyle.TRIPLE_ARROW,
            BuildTag.DRIVE to CanvasGlyphStyle.DOUBLE_CHEVRON,
            BuildTag.ANCHOR to CanvasGlyphStyle.HORSESHOE,
            BuildTag.ARSENAL to CanvasGlyphStyle.SLASH_BARS,
            BuildTag.PRECISION to CanvasGlyphStyle.RETICLE,
            BuildTag.BULWARK to CanvasGlyphStyle.SHIELD,
            BuildTag.OVERCLOCK to CanvasGlyphStyle.THREE_BLADE,
            BuildTag.CHAIN to CanvasGlyphStyle.INTERLOCKING_RINGS,
            BuildTag.FORTUNE to CanvasGlyphStyle.FOUR_LEAF,
        )
    }
}
