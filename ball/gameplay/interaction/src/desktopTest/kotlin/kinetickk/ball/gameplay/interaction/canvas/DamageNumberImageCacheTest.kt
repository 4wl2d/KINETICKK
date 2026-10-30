// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.localeList
import kinetickk.foundation.design.measureKkText
import java.lang.management.ManagementFactory
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DamageNumberImageCacheTest {
    @Test
    fun measureDamageImageConstruction() {
        if (System.getenv("KK_DAMAGE_CROP_BENCH") != "1") return
        val case = LabelCase("123456", 36f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you)
        val labels = (0 until 64).map { label(case.copy(text = (123456 + it).toString())) }
        val cache = DamageNumberImageCache(capacity = 1)
        fun construct(index: Int) {
            cache.beginFrame()
            val label = labels[index % labels.size]
            checkNotNull(cache.image(label.face, label.shadow, case.color))
        }
        repeat(512) { construct(it) }
        val samples = LongArray(1_024)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        for (index in samples.indices) {
            val started = System.nanoTime()
            construct(index)
            samples[index] = System.nanoTime() - started
        }
        val allocated = threads.getThreadAllocatedBytes(thread) - before
        samples.sort()
        println("Damage image construction medianUs=${samples[samples.size / 2] / 1_000.0} " +
            "p95Us=${samples[samples.size * 95 / 100] / 1_000.0} meanUs=${samples.average() / 1_000.0} " +
            "allocatedBytesPerImage=${allocated.toDouble() / samples.size}")
    }

    @Test
    fun tightDrawBoundsPreserveFullBitmapSamplingAcrossAnimatedTransforms() {
        val cases = listOf(
            LabelCase("742", 25f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you),
            LabelCase("12.5 M", 45f, 1.5f, 1.25f, AppLanguage.English, KkRolePalette.Protan.threat),
            LabelCase("1,25 квинтлн", 45f, 2f, 1.25f, AppLanguage.Russian, KkRolePalette.Default.element(5)),
            LabelCase("1,25 квинтлн", 25f, 3f, 1.25f, AppLanguage.Russian, KkRolePalette.Mono.you, fontScale = 1.5f),
            LabelCase("", 25f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you),
        )
        for (case in cases) {
            val label = label(case)
            val image = checkNotNull(DamageNumberImageCache().image(label.face, label.shadow, case.color))
            val brush = damageNumberImageBrush(image.bitmap)
            if (case.text.isEmpty()) assertTrue(image.drawBounds.isEmpty)
            else assertTrue(image.drawBounds.width * image.drawBounds.height < image.bitmap.width * image.bitmap.height,
                "The test must eliminate transparent padding: $case")
            for ((pop, tilt) in listOf(1f to 0f, 1.25f to 4.4f, 0.75f to 2.2f, 0.51f to -4.4f, 0.5f to -4.4f, 0.4f to -4.4f, 0.021f to 2.2f)) {
                for (shift in listOf(0f, 0.37f, -0.625f)) {
                    val width = (label.face.size.width * 1.7f + 240f).toInt()
                    val height = (label.face.size.height * 1.7f + 240f).toInt()
                    fun sampled(crop: Boolean): IntArray {
                        val bitmap = ImageBitmap(width, height)
                        CanvasDrawScope().draw(label.face.layoutInput.density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                            withTransform({
                                translate(width / 2f + shift, height / 2f - shift)
                                rotate(tilt, Offset.Zero)
                                scale(pop, pop, Offset.Zero)
                            }) {
                                if (crop) image.draw(this, pop) else translate(image.left, image.top) {
                                    drawRect(brush, Offset.Zero, Size(image.bitmap.width.toFloat(), image.bitmap.height.toFloat()))
                                }
                            }
                        }
                        val pixels = bitmap.toPixelMap()
                        return IntArray(width * height) { pixels[it % width, it / width].toArgb() }
                    }
                    assertTrue(sampled(false).contentEquals(sampled(true)),
                        "Cropped sampling changed pixels: $case, pop=$pop, tilt=$tilt, shift=$shift")
                }
            }
        }
    }

    @Test
    fun cachedFaceAndShadowsPreserveGlyphsAcrossDensityLanguageAndPalette() {
        val cases = listOf(
            LabelCase("742", 25f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you),
            LabelCase("9,223,372,036,854,775,807", 36f, 1f, 1.75f, AppLanguage.English, KkRolePalette.Protan.threat),
            LabelCase("1,25 квинтлн", 45f, 2f, 1.25f, AppLanguage.Russian, KkRolePalette.Default.element(5)),
            LabelCase("1,25 квинтлн", 20f, 3f, 1f, AppLanguage.Russian, KkRolePalette.Mono.you),
            LabelCase("1,25 квинтлн", 25f, 2f, 1.25f, AppLanguage.Russian, KkRolePalette.Default.you, fontScale = 0.85f),
            LabelCase("742", 25f, 2f, 1.25f, AppLanguage.English, KkRolePalette.Default.you, fontScale = 1.5f),
            LabelCase("1,25 квинтлн", 25f, 2f, 1.25f, AppLanguage.Russian, KkRolePalette.Mono.you, fontScale = 2f),
        )
        for ((caseIndex, case) in cases.withIndex()) {
            val label = label(case)
            val cache = DamageNumberImageCache()
            cache.beginFrame()
            val image = checkNotNull(cache.image(label.face, label.shadow, case.color))
            val imagePixels = image.bitmap.toPixelMap()
            // Transparent border proves the italic glyphs, suffixes and offset shadow were not cut.
            for (x in 0 until image.bitmap.width) {
                assertEquals(0f, imagePixels[x, 0].alpha)
                assertEquals(0f, imagePixels[x, image.bitmap.height - 1].alpha)
            }
            for (y in 0 until image.bitmap.height) {
                assertEquals(0f, imagePixels[0, y].alpha)
                assertEquals(0f, imagePixels[image.bitmap.width - 1, y].alpha)
            }
            for ((pop, tilt) in listOf(1f to 0f, 1.25f to 4.4f, 0.4f to -4.4f)) {
                val direct = render(label, case.color, null, pop, tilt)
                val cached = render(label, case.color, image, pop, tilt)
                captureComparison(caseIndex, case, label, direct, cached, pop, tilt)
                var directAlpha = 0L
                var alphaError = 0L
                var colorError = 0L
                var coloredPixels = 0
                for (index in direct.indices) {
                    val a = (direct[index] ushr 24) and 255
                    val b = (cached[index] ushr 24) and 255
                    directAlpha += a
                    alphaError += abs(a - b)
                    if (a >= 240 && b >= 240) {
                        coloredPixels++
                        colorError += abs((direct[index] and 255) - (cached[index] and 255))
                        colorError += abs(((direct[index] ushr 8) and 255) - ((cached[index] ushr 8) and 255))
                        colorError += abs(((direct[index] ushr 16) and 255) - ((cached[index] ushr 16) and 255))
                    }
                }
                assertTrue(directAlpha > 10_000, "The reference label is visible: $case")
                println("Damage image case=$caseIndex pop=$pop tilt=$tilt alphaError=${alphaError.toDouble() / directAlpha} " +
                    "colorError=${colorError.toDouble() / (coloredPixels.coerceAtLeast(1) * 3)}")
                // Raster sampling can change antialiased edge pixels under the existing pop/tilt.
                // The filled silhouette and paint must remain within these bounds, including 3x DPI.
                assertTrue(alphaError.toDouble() / directAlpha < 0.14, "Alpha silhouette changed: $case pop=$pop tilt=$tilt")
                assertTrue(colorError.toDouble() / (coloredPixels.coerceAtLeast(1) * 3) < 18.0,
                    "Face or shadow paint changed: $case pop=$pop tilt=$tilt")
            }
        }
    }

    @Test
    fun cacheTracksTextFontScaleDensityLocaleAndPaintAndKeepsWarmReadsAllocationFree() {
        val cache = DamageNumberImageCache()
        val base = LabelCase("124", 25f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you)
        val original = label(base)
        cache.beginFrame()
        val first = checkNotNull(cache.image(original.face, original.shadow, base.color))
        assertSame(first, cache.image(original.face, original.shadow, base.color))
        for (variant in listOf(
            base.copy(text = "125"), base.copy(fontSize = 45f), base.copy(density = 2f),
            base.copy(textScale = 1.75f), base.copy(language = AppLanguage.Russian),
            base.copy(fontScale = 1.5f),
        )) {
            val changed = label(variant)
            assertNotSame(first, cache.image(changed.face, changed.shadow, variant.color))
        }
        assertNotSame(first, cache.image(original.face, original.shadow, KkRolePalette.Protan.threat))
        val alternateFont = label(base, HudTestFonts.typography.copy(cond = HudTestFonts.typography.mono))
        assertNotSame(first, cache.image(alternateFont.face, alternateFont.shadow, base.color))

        val canvas = Canvas(ImageBitmap(320, 180))
        val scope = CanvasDrawScope()
        val density = Density(1f)
        val size = Size(320f, 180f)
        fun draw() = scope.draw(density, LayoutDirection.Ltr, canvas, size) {
            cache.beginFrame()
            withTransform({ translate(160f, 90f) }) {
                checkNotNull(cache.image(original.face, original.shadow, base.color)).draw(this)
            }
        }
        fun empty() = scope.draw(density, LayoutDirection.Ltr, canvas, size) { }
        repeat(300) { draw(); empty() }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        var before = threads.getThreadAllocatedBytes(thread)
        repeat(300) { empty() }
        val baseline = threads.getThreadAllocatedBytes(thread) - before
        before = threads.getThreadAllocatedBytes(thread)
        repeat(300) { draw() }
        val extra = threads.getThreadAllocatedBytes(thread) - before - baseline
        assertTrue(extra / 300 <= 96, "A warm cached label allocates ${extra / 300} bytes above the empty draw")
    }

    @Test
    fun fullAndOversizedCachesFallBackWithoutEvictingThisFramesLabels() {
        val base = LabelCase("124", 25f, 1f, 1.25f, AppLanguage.English, KkRolePalette.Default.you)
        val labels = listOf(base, base.copy(text = "125"), base.copy(text = "126")).map { label(it) }
        val cache = DamageNumberImageCache(capacity = 2, maximumPixels = 100_000)
        cache.beginFrame()
        val first = checkNotNull(cache.image(labels[0].face, labels[0].shadow, base.color))
        val second = checkNotNull(cache.image(labels[1].face, labels[1].shadow, base.color))
        assertNull(cache.image(labels[2].face, labels[2].shadow, base.color))
        assertSame(first, cache.image(labels[0].face, labels[0].shadow, base.color))
        assertSame(second, cache.image(labels[1].face, labels[1].shadow, base.color))
        cache.beginFrame()
        assertSame(second, cache.image(labels[1].face, labels[1].shadow, base.color))
        assertTrue(cache.image(labels[2].face, labels[2].shadow, base.color) != null)
        assertTrue(cache.retainedPixels <= 100_000)

        val oneImagePixels = first.bitmap.width * first.bitmap.height
        val byteLimited = DamageNumberImageCache(capacity = 3, maximumPixels = oneImagePixels)
        byteLimited.beginFrame()
        assertTrue(byteLimited.image(labels[0].face, labels[0].shadow, base.color) != null)
        assertNull(byteLimited.image(labels[1].face, labels[1].shadow, base.color))
        byteLimited.beginFrame()
        assertTrue(byteLimited.image(labels[1].face, labels[1].shadow, base.color) != null)
        assertTrue(byteLimited.retainedPixels <= oneImagePixels)

        val oversized = DamageNumberImageCache(maximumPixels = 1, maximumImagePixels = 1)
        assertNull(oversized.image(labels[0].face, labels[0].shadow, base.color))
        assertEquals(0, oversized.retainedPixels)
        // Null uses the original five paints; even an intentionally tiny budget preserves pixels.
        assertTrue(render(labels[0], base.color, null, 1f, 0f).contentEquals(
            render(labels[0], base.color, oversized.image(labels[0].face, labels[0].shadow, base.color), 1f, 0f)))
    }
}

private fun captureComparison(
    caseIndex: Int,
    case: LabelCase,
    label: LabelLayouts,
    direct: IntArray,
    cached: IntArray,
    pop: Float,
    tilt: Float,
) {
    val path = System.getenv("KK_DAMAGE_CACHE_CAPTURE_DIR") ?: return
    val width = (label.face.size.width * 1.7f + 240f).toInt()
    val height = direct.size / width
    var left = width
    var right = 0
    var top = height
    var bottom = 0
    for (index in direct.indices) {
        if ((direct[index] ushr 24) != 0 || (cached[index] ushr 24) != 0) {
            left = minOf(left, index % width)
            right = maxOf(right, index % width)
            top = minOf(top, index / width)
            bottom = maxOf(bottom, index / width)
        }
    }
    left = (left - 8).coerceAtLeast(0)
    top = (top - 8).coerceAtLeast(0)
    val cropWidth = (right + 9).coerceAtMost(width) - left
    val cropHeight = (bottom + 9).coerceAtMost(height) - top
    val columnWidth = maxOf(270, cropWidth * 4 + 20)
    val output = BufferedImage(columnWidth * 2, 75 + cropHeight * 5, BufferedImage.TYPE_INT_ARGB)
    val graphics = output.createGraphics()
    graphics.color = java.awt.Color(38, 38, 38)
    graphics.fillRect(0, 0, output.width, output.height)
    graphics.color = java.awt.Color.WHITE
    graphics.drawString("Direct: ${case.text} | pop=$pop tilt=$tilt", 10, 18)
    graphics.drawString("Cached | density=${case.density} textScale=${case.textScale}", columnWidth + 10, 18)
    listOf(direct, cached).forEachIndexed { column, pixels ->
        val source = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, width, height, pixels, 0, width)
        val cropped = source.getSubimage(left, top, cropWidth, cropHeight)
        graphics.drawImage(cropped, column * columnWidth + 10, 30, null)
        graphics.drawImage(cropped, column * columnWidth + 10, 55 + cropHeight, cropWidth * 4, cropHeight * 4, null)
    }
    graphics.dispose()
    val target = File(path, "case-$caseIndex-pop-$pop-tilt-$tilt.png")
    target.parentFile.mkdirs()
    ImageIO.write(output, "png", target)
}

private data class LabelCase(
    val text: String,
    val fontSize: Float,
    val density: Float,
    val textScale: Float,
    val language: AppLanguage,
    val color: Color,
    val fontScale: Float = 1f,
)

private data class LabelLayouts(val face: TextLayoutResult, val shadow: TextLayoutResult)

private fun label(case: LabelCase, typography: InterfaceTypography = HudTestFonts.typography): LabelLayouts {
    val localized = typography.copy(localeList = case.language.localeList())
    val measurer = CanvasTextMeasurer(
        ComposeTextMeasurer(createFontFamilyResolver(), Density(case.density, case.fontScale), LayoutDirection.Ltr),
        case.textScale, case.language, localized,
    )
    return LabelLayouts(
        measureKkText(measurer, case.text, localized.condStyle(case.fontSize, tabular = true, lineHeightEm = 1f, color = case.color)),
        measureKkText(measurer, case.text, localized.condStyle(case.fontSize, tabular = true, lineHeightEm = 1.001f, color = Kk.Ink)),
    )
}

private fun render(label: LabelLayouts, color: Color, image: DamageNumberImage?, pop: Float, tilt: Float): IntArray {
    val width = (label.face.size.width * 1.7f + 240f).toInt()
    val height = (label.face.size.height * 1.7f + 240f).toInt()
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(label.face.layoutInput.density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        withTransform({
            translate(width / 2f, height / 2f)
            rotate(tilt, Offset.Zero)
            scale(pop, pop, Offset.Zero)
        }) {
            if (image == null) drawDamageNumberInk(label.face, label.shadow, color) else image.draw(this, pop)
        }
    }
    val pixels = bitmap.toPixelMap()
    return IntArray(width * height) { pixels[it % width, it / width].toArgb() }
}
