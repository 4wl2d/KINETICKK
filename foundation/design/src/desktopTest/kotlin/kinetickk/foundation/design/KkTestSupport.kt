// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.common.localization.AppLanguage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Bundled fonts loaded straight from the module's compose resources (no composition needed). */
internal object KkTestFonts {
    private val directory: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/commonMain/composeResources/font"), File(it, "foundation/design/src/commonMain/composeResources/font")) }
            .first { File(it, "kk_wide_black.ttf").isFile }
    }

    private fun font(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
        Font(File(directory, "$name.ttf"), weight, style)

    val typography: InterfaceTypography by lazy {
        val cond = FontFamily(
            font("kk_cond_black_italic", FontWeight.Black, FontStyle.Italic),
            font("kk_cond_black", FontWeight.Black),
            font("kk_cond_extrabold", FontWeight.ExtraBold),
        )
        InterfaceTypography(
            body = FontFamily(font("kk_body_regular", FontWeight.Normal), font("kk_body_medium", FontWeight.Medium), font("kk_body_bold", FontWeight.Bold)),
            display = FontFamily(font("kk_cond_black_italic", FontWeight.Bold), font("kk_cond_extrabold", FontWeight.Normal)),
            wide = FontFamily(font("kk_wide_black", FontWeight.Black), font("kk_wide_bold", FontWeight.Bold)),
            cond = cond,
            label = cond,
            mono = FontFamily(font("kk_mono_medium", FontWeight.Medium), font("kk_mono_bold", FontWeight.Bold)),
        )
    }
}

internal fun kkTestMeasurer(
    roles: KkRolePalette = KkRolePalette.Default,
    density: Float = 1f,
    typography: InterfaceTypography = KkTestFonts.typography,
    scale: Float = 1f,
): CanvasTextMeasurer = CanvasTextMeasurer(
    TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr),
    scale,
    AppLanguage.English,
    typography,
    roles,
)

internal fun kkRender(width: Int, height: Int, density: Float = 1f, background: Color = Kk.Ink, draw: DrawScope.() -> Unit): ImageBitmap =
    ImageBitmap(width, height).also { bitmap ->
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(background)
            draw()
        }
    }

internal fun ImageBitmap.argb(): IntArray {
    val map = toPixelMap()
    return IntArray(width * height) { map[it % width, it / width].toArgb() }
}

internal fun ImageBitmap.at(x: Int, y: Int): Int = toPixelMap(x, y, 1, 1)[0, 0].toArgb()

internal fun ImageBitmap.writePng(file: File) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val pixels = argb()
    image.setRGB(0, 0, width, height, pixels, 0, width)
    file.parentFile.mkdirs()
    ImageIO.write(image, "png", file)
}

/** Distance between two ARGB colors (max channel difference). */
internal fun colorDistance(a: Int, b: Int): Int {
    var max = 0
    for (shift in intArrayOf(0, 8, 16, 24)) {
        val d = kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        if (d > max) max = d
    }
    return max
}

internal fun IntArray.countNear(color: Color, tolerance: Int = 12): Int {
    val target = color.toArgb()
    return count { colorDistance(it, target) <= tolerance }
}

internal fun ImageBitmap.countNear(color: Color, tolerance: Int = 12): Int = argb().countNear(color, tolerance)

internal fun ImageBitmap.regionCountNear(color: Color, left: Int, top: Int, right: Int, bottom: Int, tolerance: Int = 12): Int {
    val target = color.toArgb()
    val map = toPixelMap(left, top, right - left, bottom - top)
    var count = 0
    for (y in 0 until bottom - top) for (x in 0 until right - left) {
        if (colorDistance(map[x, y].toArgb(), target) <= tolerance) count++
    }
    return count
}
