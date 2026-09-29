// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KkTextMemoTest {
    @Test
    fun layoutsReadEveryFrameSurviveChurnFromOneOffStrings() {
        val measurer = kkTestMeasurer()
        val style = measurer.typography.wideStyle(44f, tabular = true)
        val oneOff = measurer.typography.condStyle(20f)
        val digit = measureKkText(measurer, "7", style)
        // Damage numbers measure a new string almost every frame; the HUD reads its digits each frame.
        repeat(1_000) { frame ->
            measureKkText(measurer, "one-off $frame", oneOff)
            assertSame(digit, measureKkText(measurer, "7", style), "digit layout evicted at frame $frame")
        }
    }

    @Test
    fun aDenseCombatFrameStaysResidentWhileNewNumbersArrive() {
        // The reducer's cap of 140 live damage numbers, each drawn every frame of its 36-frame life
        // as a face and a shadow layout, plus HUD digits in several styles drawn after them. Four
        // new numbers arrive per frame for far more than a full pass through the caches.
        val measurer = kkTestMeasurer()
        val t = measurer.typography
        val face = t.condStyle(22.5f, tabular = true, lineHeightEm = 1f)
        val shadow = t.condStyle(22.5f, tabular = true, lineHeightEm = 1.001f)
        val hudStyles = listOf(
            t.wideStyle(44f, tabular = true), t.wideStyle(56f, tabular = true), t.condStyle(60f, tabular = true),
            t.condStyle(26f, tabular = true), t.monoStyle(11f, weight = androidx.compose.ui.text.font.FontWeight.Bold),
        )
        val live = Array(LIVE_NUMBERS) { index -> "${1_000 + index * 37}" }
        var serial = 0
        val scope = CanvasDrawScope()
        val canvas = Canvas(ImageBitmap(64, 64))
        fun frame(block: DrawScope.() -> Unit) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(64f, 64f), block)
        fun drawFrame() = frame {
            for (text in live) {
                drawKkText(measureKkText(measurer, text, shadow), 2f, 2f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
                drawKkText(measureKkText(measurer, text, face), 0f, 0f, Kk.Bone, KkAlign.CENTER, KkVAlign.CENTER)
            }
            for (style in hudStyles) for (digit in 0..9) {
                drawKkText(measureKkText(measurer, kkIntString(digit), style), 0f, 0f, Kk.Bone)
            }
        }
        repeat(3) { drawFrame() }
        val hud = hudStyles.map { style -> (0..9).map { measureKkText(measurer, kkIntString(it), style) } }
        val measured = KkTextStats.layoutsMeasured
        val reads = KkTextStats.lineMetricReads
        repeat(36) { drawFrame() }
        assertEquals(measured, KkTextStats.layoutsMeasured, "a frame that adds no number measures no text")
        assertEquals(reads, KkTextStats.lineMetricReads, "a frame that adds no number reads no line metrics")

        repeat(400) { churn ->
            repeat(NEW_PER_FRAME) { live[(churn * NEW_PER_FRAME + it) % LIVE_NUMBERS] = "${2_000_000 + serial++}" }
            val before = KkTextStats.layoutsMeasured
            val readsBefore = KkTextStats.lineMetricReads
            drawFrame()
            assertEquals(2L * NEW_PER_FRAME, KkTextStats.layoutsMeasured - before, "frame $churn measures only its new numbers")
            assertEquals(2L * NEW_PER_FRAME, KkTextStats.lineMetricReads - readsBefore, "frame $churn reads metrics only for new layouts")
        }
        hudStyles.forEachIndexed { index, style ->
            (0..9).forEach { digit ->
                assertSame(hud[index][digit], measureKkText(measurer, kkIntString(digit), style), "HUD digit $digit kept its layout")
            }
        }
    }

    private companion object {
        const val LIVE_NUMBERS = 140
        const val NEW_PER_FRAME = 4
    }
}
