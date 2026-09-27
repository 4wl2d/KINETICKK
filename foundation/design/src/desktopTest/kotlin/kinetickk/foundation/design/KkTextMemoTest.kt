// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kotlin.test.Test
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
}
