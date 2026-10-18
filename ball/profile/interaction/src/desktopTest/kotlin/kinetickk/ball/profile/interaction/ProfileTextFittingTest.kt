// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class ProfileTextFittingTest {
    @Test
    fun fittedTitlesKeepTheirWholeTextAtFractionalPixelBoundaries() = runSkikoComposeUiTest(Size(800f, 200f), Density(1f)) {
        lateinit var measurer: CanvasTextMeasurer
        setContent {
            val current = rememberKkCanvasMeasurer()
            SideEffect { measurer = current }
        }
        runOnIdle {
            val canvas = Canvas(ImageBitmap(800, 200))
            val scope = CanvasDrawScope()
            for (text in listOf("REINFORCED HULL", "УСИЛЕННЫЙ КОРПУС")) {
                for (size in listOf(12.55f, 18f, 30f, 42f)) {
                    val style = measurer.typography.wideStyle(size, lineHeightEm = 1.05f)
                    val width = measureKkText(measurer, text, style).multiParagraph.width
                    for (inset in listOf(0f, 0.5f, 1f, 2f)) {
                        val available = width - inset
                        val fitted = fitKkText(measurer, text, size, available, minFactor = 0.4f) {
                            measurer.typography.wideStyle(it, lineHeightEm = 1.05f)
                        }
                        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(800f, 200f)) {
                            drawKkText(fitted, 0f, 0f, Kk.Bone)
                        }
                        assertFalse(fitted.multiParagraph.intrinsics.maxIntrinsicWidth > fitted.size.width + 0.5f,
                            "$text at $size sp in $available px is ellipsized (overflow=${fitted.hasVisualOverflow})")
                    }
                }
            }
        }
    }
}
