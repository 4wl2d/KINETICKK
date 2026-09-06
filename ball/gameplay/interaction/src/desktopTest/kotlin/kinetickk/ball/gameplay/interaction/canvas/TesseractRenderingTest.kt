// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class TesseractRenderingTest {
    @Test
    fun actualCanvasProjectionKeepsVisibleCenterAndRotatesAcrossFourPoses() {
        val bitmap = ImageBitmap(800, 200)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(800f, 200f)) {
            drawRect(Color(0xFF050810))
            repeat(4) { index ->
                val center = Offset(index * 200f + 100f, 100f)
                scale(3f, center) {
                    drawTesseractCore(center, index * 1.4f, Color(0xFFB7A7FF))
                }
            }
        }
        val pixels = bitmap.toPixelMap()
        val hashes = mutableSetOf<Long>()
        repeat(4) { index ->
            assertTrue(pixels[index * 200 + 100, 100].red > 0.9f, "Physical center remains visible")
            var hash = 1L
            var litPixels = 0
            for (y in 0 until 200) for (x in index * 200 until (index + 1) * 200) {
                val color = pixels[x, y]
                hash = hash * 31 + color.toArgb()
                if (color.red > 0.15f) litPixels++
            }
            assertTrue(litPixels > 300, "Wireframe must be visibly drawn")
            hashes += hash
        }
        assertTrue(hashes.size == 4, "Rotation changes the actual rendered geometry")
        System.getenv("KINETICKK_RENDER_CAPTURE_DIR")?.let { directory ->
            val image = BufferedImage(800, 200, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 200) for (x in 0 until 800) image.setRGB(x, y, pixels[x, y].toArgb())
            File(directory).mkdirs()
            ImageIO.write(image, "png", File(directory, "tesseract-four-poses.png"))
        }
    }
}
