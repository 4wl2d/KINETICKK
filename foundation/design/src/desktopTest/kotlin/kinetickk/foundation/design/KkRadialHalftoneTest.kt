// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class KkRadialHalftoneTest {
    @Test
    fun compositePreservesLayeredFadeAtFractionalTranslationsScalesAndDensities() {
        val failures = mutableListOf<String>()
        for (density in listOf(1f, 1.5f, 2f, 2.625f)) {
            for (large in listOf(false, true)) {
                for (transform in 0..2) {
                    val rect = if (transform == 1) Rect(-103f, -67f, 103f, 67f) else Rect(-82f, -82f, 82f, 82f)
                    val color = if (transform == 0) Kk.Bone.copy(alpha = 0.12f) else Kk.EThermal.copy(alpha = 0.7f)
                    val innerStop = if (transform == 2) 0.55f else 0.2f
                    fun image(composite: Boolean) = kkRender(256, 256, density) {
                        translate(128.25f + transform * 0.17f, 127.625f) {
                            rotate(transform * 13.5f, Offset.Zero) {
                                scale(1f + transform * 0.17f, 1f + transform * 0.11f, Offset.Zero) {
                                    if (composite) drawKkRadialHalftone(rect, color, large, innerStop)
                                    else drawKkRadialFade(rect, innerStop) { drawKkHalftone(rect, color, large) }
                                }
                            }
                        }
                    }
                    val originalImage = image(false)
                    val combinedImage = image(true)
                    val original = originalImage.argb()
                    val combined = combinedImage.argb()
                    var maximumChannelDifference = 0
                    var totalChannelDifference = 0L
                    val radians = Math.toRadians(transform * 13.5)
                    val cosine = cos(radians).toFloat()
                    val sine = sin(radians).toFloat()
                    val scaleX = 1f + transform * 0.17f
                    val scaleY = 1f + transform * 0.11f
                    val edgePixel = 1f / minOf(scaleX, scaleY)
                    val radius = minOf(rect.width, rect.height) * 0.5f
                    val background = Kk.Ink.toArgb()
                    for (index in original.indices) {
                        // The layered reference leaves antialiased, incompletely masked dots
                        // along its rectangular clip. Locate only that one-device-pixel edge
                        // region in the original local coordinates; the new shader has no layer.
                        val dx = index % 256 + 0.5f - (128.25f + transform * 0.17f)
                        val dy = index / 256 + 0.5f - 127.625f
                        val localX = (dx * cosine + dy * sine) / scaleX
                        val localY = (-dx * sine + dy * cosine) / scaleY
                        val distanceToLayerEdge = minOf(
                            minOf(abs(localX - rect.left), abs(localX - rect.right)),
                            minOf(abs(localY - rect.top), abs(localY - rect.bottom)),
                        )
                        val onLayerEdge = distanceToLayerEdge <= edgePixel
                        val distanceFromCenter = sqrt(localX * localX + localY * localY)
                        if (distanceFromCenter > radius + edgePixel) {
                            assertTrue(combined[index] == background,
                                "Composite leaked outside radial fade: density=$density large=$large transform=$transform index=$index")
                        }
                        var shift = 0
                        while (shift <= 24) {
                            val difference = abs((original[index] ushr shift and 255) - (combined[index] ushr shift and 255))
                            if (!onLayerEdge) maximumChannelDifference = maxOf(maximumChannelDifference, difference)
                            totalChannelDifference += difference
                            shift += 8
                        }
                    }
                    // The old pass quantizes the masked offscreen image before compositing it;
                    // the combined shader rounds once. Permit only that small channel rounding.
                    val mean = totalChannelDifference.toDouble() / original.size / 4
                    val detail = "density=$density large=$large transform=$transform: max=$maximumChannelDifference mean=$mean"
                    println(detail)
                    if (maximumChannelDifference > 2 || mean >= 0.15) {
                        if (failures.isEmpty()) {
                            originalImage.writePng(File("build/radial-halftone-diagnostic/original.png"))
                            combinedImage.writePng(File("build/radial-halftone-diagnostic/combined.png"))
                        }
                        failures += detail
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun compositeMatchesProductionBackdropTransforms() {
        val failures = mutableListOf<String>()
        for (worldScale in listOf(1f, 1.5f, 2f, 2.625f)) for (variant in 0..2) {
            val rect = if (variant == 2) Rect(-332.5f, -341.25f, 367.5f, 358.75f) else Rect(-350f, -350f, 350f, 350f)
            val color = when (variant) {
                0 -> Kk.Bone.copy(alpha = 0.08f)
                1 -> Kk.EThermal.copy(alpha = 0.7f)
                else -> Kk.Bone
            }
            fun image(composite: Boolean) = kkRender(960, 960, density = 1f) {
                translate(480f + variant * 0.25f, 480.625f) {
                    scale(worldScale, worldScale, Offset.Zero) {
                        if (composite) drawKkRadialHalftone(rect, color)
                        else drawKkRadialFade(rect) { drawKkHalftone(rect, color) }
                    }
                }
            }
            val originalImage = image(false)
            val combinedImage = image(true)
            val original = originalImage.argb()
            val combined = combinedImage.argb()
            var maximum = 0
            var total = 0L
            for (index in original.indices) {
                var shift = 0
                while (shift <= 24) {
                    val difference = abs((original[index] ushr shift and 255) - (combined[index] ushr shift and 255))
                    maximum = maxOf(maximum, difference)
                    total += difference
                    shift += 8
                }
            }
            val mean = total.toDouble() / original.size / 4
            val detail = "worldScale=$worldScale variant=$variant: max=$maximum mean=$mean"
            println(detail)
            if (maximum > 2 || mean >= 0.15) {
                if (failures.isEmpty()) {
                    originalImage.writePng(File("build/radial-halftone-diagnostic/backdrop-original.png"))
                    combinedImage.writePng(File("build/radial-halftone-diagnostic/backdrop-combined.png"))
                }
                failures += detail
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun cacheDoesNotReuseOldPaletteGeometryOrDensityAfterCyclingBrushes() {
        fun pixels(density: Float, color: Color, rect: Rect) = kkRender(256, 256, density) {
            drawKkRadialHalftone(rect, color)
        }.argb()
        val rect = Rect(20.5f, 31.25f, 200.5f, 211.25f)
        val initial = pixels(1f, Kk.Bone, rect)
        repeat(24) { index ->
            val shifted = Rect(rect.left + index, rect.top, rect.right + index, rect.bottom)
            pixels(1f + index * 0.1f, Color(0xFF001122.toInt() + index * 127), shifted)
        }
        assertTrue(initial.contentEquals(pixels(1f, Kk.Bone, rect)))
        assertTrue(!initial.contentEquals(pixels(2f, Kk.Bone, rect)))
        assertTrue(!initial.contentEquals(pixels(1f, Kk.EThermal, rect)))
        assertTrue(!initial.contentEquals(pixels(1f, Kk.Bone, Rect(21.5f, 31.25f, 201.5f, 211.25f))))
    }
}
