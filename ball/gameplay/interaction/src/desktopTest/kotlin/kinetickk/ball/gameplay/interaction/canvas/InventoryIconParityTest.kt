// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

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
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.drawLayeredGlyph
import kinetickk.foundation.design.drawRuneMedallion
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The same raster contract runs independently in both Interaction owners. */
class InventoryIconParityTest {
    @Test
    fun allRelicsMatchSharedGeometryAtEqualParametersAndTime() {
        RelicId.entries.forEachIndexed { index, id ->
            val group = (index / 6).coerceAtMost(6)
            val definition = RelicDefinition(id, id.name, RelicAspect.entries[group], "Full description", "Rank effect")
            listOf(1f, 1.25f, 1.75f).forEach { scale ->
                val actual = render(scale) {
                    drawRelicIcon(definition, RelicPolicy(6, 3), Center, 36f, rank = 2, time = 1.75f, alpha = 0.75f)
                }
                val expected = render(scale) {
                    drawRuneMedallion(CanvasRuneStyle.entries[index], Center, 36f,
                        Accents[group].copy(alpha = 0.75f), FrameSides[group],
                        frameRotation = -PI.toFloat() * 0.5f + if (group == 6) 1.75f * 0.08f else 0f,
                        markerCount = 3, filledMarkerCount = 2, time = 1.75f)
                }
                assertContentEquals(expected, actual, "$id at text scale $scale")
            }
        }
    }

    @Test
    fun everyItemEffectMatchesSharedLayeredGeometry() {
        ItemEffect.entries.forEachIndexed { index, effect ->
            val secondaryIndex = (index + 1) % ItemEffect.entries.size
            val rarity = ItemRarity.entries[index % ItemRarity.entries.size]
            val item = ItemDefinition(index, "Item $index", "Full description", rarity,
                ItemModifier(effect, 1f), ItemModifier(ItemEffect.entries[secondaryIndex], 1f), 6, 1, "Test")
            listOf(false, true).forEach { obscured ->
                val actual = render(1.75f) { drawItemIcon(item, Center, 36f, Accents[0], stack = 3, obscured = obscured) }
                val expected = render(1.75f) {
                    drawLayeredGlyph(CanvasGlyphStyle.entries[index], CanvasGlyphStyle.entries[secondaryIndex],
                        Center, 36f, Accents[0], frameSides = if (obscured) 4 else rarity.rank + 3,
                        markerCount = if (obscured) 1 else rarity.rank, outerArcDegrees = 180f, crossedOut = obscured)
                }
                assertContentEquals(expected, actual, "$effect, obscured=$obscured")
            }
        }
    }

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
        val Accents = listOf(Color(0xFF42F5E9), Color(0xFFA96CFF), Color(0xFF73A6FF), Color(0xFFFF4DC4),
            Color(0xFFB6FF5B), Color(0xFFFF714B), Color(0xFFFFD45B))
        val FrameSides = listOf(3, 6, 8, 4, 5, 7, 10)
    }
}
