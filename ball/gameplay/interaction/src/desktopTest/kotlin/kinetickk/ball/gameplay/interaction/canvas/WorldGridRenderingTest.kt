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
import kinetickk.foundation.design.Kk
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldGridRenderingTest {
    @Test
    fun batchedGridKeepsLineCoverageAndIntersectionAlphaThroughResizeAndCameraMovement() {
        for ((width, height) in listOf(320 to 180, 373 to 221, 1_440 to 810, 320 to 180)) {
            for (density in listOf(1f, 1.5f, 2f)) {
                for (camera in listOf(0f, -281.25f, 43.75f, 39.875f, 33.77687454223633f, -63.14159f, 12_345.678f, -98_765.43f)) {
                    val label = "$width x $height at $density, camera $camera"
                    // Camera movement switches between the two buffer lengths as the last line
                    // enters or leaves the viewport.
                    val expected = render(width, height, density, camera) { drawReferenceGrid(it) }
                    val actual = render(width, height, density, camera) { drawWorldGrid(it, GRID_COLOR) }
                    assertPixelsEqual(expected, actual, label)
                }
            }
        }
    }

    private fun DrawScope.drawReferenceGrid(origin: Offset) {
        var x = origin.x - WORLD_GRID_SPACING
        while (x <= size.width) {
            if (x >= 0f) drawLine(GRID_COLOR, Offset(x, 0f), Offset(x, size.height), 1f)
            x += WORLD_GRID_SPACING
        }
        var y = origin.y - WORLD_GRID_SPACING
        while (y <= size.height) {
            if (y >= 0f) drawLine(GRID_COLOR, Offset(0f, y), Offset(size.width, y), 1f)
            y += WORLD_GRID_SPACING
        }
    }

    private fun render(width: Int, height: Int, density: Float, camera: Float, grid: DrawScope.(Offset) -> Unit): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            withWorldUnits(density) {
                grid(Offset(worldGridOffset(camera, size.width, 0.25f), worldGridOffset(-camera, size.height, -1.5f)))
            }
        }
        val pixels = bitmap.toPixelMap()
        return IntArray(width * height) { pixels[it % width, it / width].toArgb() }
    }

    private fun assertPixelsEqual(expected: IntArray, actual: IntArray, label: String) {
        val firstMismatch = expected.indices.firstOrNull { expected[it] != actual[it] }
        assertEquals(null, firstMismatch, "$label, first differing pixel $firstMismatch")
    }

    private companion object {
        val GRID_COLOR = Color(0xFFB9C3C8).copy(alpha = 0.28f)
    }
}
