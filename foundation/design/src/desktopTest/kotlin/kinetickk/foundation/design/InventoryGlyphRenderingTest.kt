// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

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
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InventoryGlyphRenderingTest {
    @Test
    fun everyRuneIsDistinctAndDeterministicAtEqualParametersAndAnimationTime() {
        assertEquals(40, CanvasRuneStyle.entries.size)
        val distinct = mutableSetOf<List<Int>>()
        CanvasRuneStyle.entries.forEach { style ->
            val expected = runePixels(style, time = 1.75f)
            assertTrue(expected.count { it != Background.toArgb() } > 400, "$style is visible")
            assertContentEquals(expected, runePixels(style, time = 1.75f), "$style is deterministic")
            listOf(1f, 1.25f, 1.75f).forEach { fontScale ->
                assertContentEquals(expected, runePixels(style, time = 1.75f, fontScale = fontScale),
                    "Identical geometry for $style at text scale $fontScale")
            }
            assertTrue(distinct.add(expected.toList()), "$style has a distinct silhouette")
        }
    }

    @Test
    fun animatedRuneUsesOnlyTheSuppliedTime() {
        assertNotEquals(
            runePixels(CanvasRuneStyle.COUNTER_ROTATING_ARCS, time = 0f).toList(),
            runePixels(CanvasRuneStyle.COUNTER_ROTATING_ARCS, time = 4f).toList(),
        )
    }

    @Test
    fun layeredGlyphsRetainTheirCompositionAcrossTextScales() {
        CanvasGlyphStyle.entries.forEachIndexed { index, primary ->
            val secondary = CanvasGlyphStyle.entries[(index + 1) % CanvasGlyphStyle.entries.size]
            val draw: DrawScope.() -> Unit = {
                drawLayeredGlyph(primary, secondary, Center, 42f, Accent, 8, 5, 240f)
            }
            val expected = pixels(render(draw = draw))
            assertTrue(expected.count { it != Background.toArgb() } > 300)
            listOf(1f, 1.25f, 1.75f).forEach { scale ->
                assertContentEquals(expected, pixels(render(fontScale = scale, draw = draw)))
            }
        }
    }

    @Test
    fun allRunesProduceReviewableAtlasesAtThreeDisplayScales() {
        listOf(1f, 1.25f, 1.75f).forEach { displayScale ->
            val cell = (144 * displayScale).toInt()
            val atlas = ImageBitmap(cell * 8, cell * 5)
            CanvasDrawScope().draw(Density(displayScale, displayScale), LayoutDirection.Ltr,
                Canvas(atlas), Size(atlas.width.toFloat(), atlas.height.toFloat())) {
                drawRect(Background)
                CanvasRuneStyle.entries.forEachIndexed { index, style ->
                    val center = Offset((index % 8 + 0.5f) * cell, (index / 8 + 0.5f) * cell)
                    val group = (index / 6).coerceAtMost(6)
                    drawRuneMedallion(style, center, 42f * displayScale, Accents[group],
                        FrameSides[group], markerCount = 3, filledMarkerCount = 2, time = 1.75f)
                }
            }
            val image = BufferedImage(atlas.width, atlas.height, BufferedImage.TYPE_INT_ARGB)
            val map = atlas.toPixelMap()
            for (y in 0 until atlas.height) for (x in 0 until atlas.width) {
                image.setRGB(x, y, map[x, y].toArgb())
            }
            val directory = File("build/reports/icon-atlas").apply { mkdirs() }
            ImageIO.write(image, "png", File(directory, "rune-glyphs-scale-$displayScale.png"))
        }
    }

    private fun runePixels(style: CanvasRuneStyle, time: Float, fontScale: Float = 1f): IntArray =
        pixels(render(fontScale = fontScale) {
            drawRuneMedallion(style, Center, 42f, Accent, 6, markerCount = 3,
                filledMarkerCount = 2, time = time)
        })

    private fun render(fontScale: Float = 1f, draw: DrawScope.() -> Unit): ImageBitmap =
        ImageBitmap(128, 128).also { bitmap ->
            CanvasDrawScope().draw(Density(1f, fontScale), LayoutDirection.Ltr,
                Canvas(bitmap), Size(128f, 128f)) {
                drawRect(Background)
                draw()
            }
        }

    private fun pixels(bitmap: ImageBitmap): IntArray {
        val map = bitmap.toPixelMap()
        return IntArray(bitmap.width * bitmap.height) { index ->
            map[index % bitmap.width, index / bitmap.width].toArgb()
        }
    }

    private companion object {
        val Center = Offset(64f, 64f)
        val Background = Color(0xFF050810)
        val Accent = Color(0xFF42F5E9)
        val Accents = listOf(Accent, Color(0xFFA96CFF), Color(0xFF73A6FF), Color(0xFFFF4DC4),
            Color(0xFFB6FF5B), Color(0xFFFF714B), Color(0xFFFFD45B))
        val FrameSides = listOf(3, 6, 8, 4, 5, 7, 10)
    }
}
