// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.graphics.Color
import kinetickk.ball.profile.api.ColorVision
import kinetickk.foundation.design.Kk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSwatchContrastTest {
    @Test
    fun everyRoleChipLabelReadsAsSmallTextInEveryColorVisionPalette() {
        for (vision in ColorVision.entries) {
            settingsRoleSwatches(vision.previewPalette()).forEach { (role, fill) ->
                val label = settingsChipLabelColor(fill)
                val ratio = contrastRatio(label, fill)
                assertTrue(ratio >= SETTINGS_CHIP_LABEL_CONTRAST, "$vision $role: $label on $fill is $ratio:1")
            }
        }
    }

    @Test
    fun midToneChipsTakeInkWhereBoneFallsShort() {
        // Relative luminance about 0.25: bone gives about 3:1, ink about 5.7:1.
        assertEquals(Kk.Ink, settingsChipLabelColor(ColorVision.DEFAULT.previewPalette().threat))
        assertEquals(Kk.Ink, settingsChipLabelColor(ColorVision.PROTAN.previewPalette().threat))
        assertEquals(Kk.Ink, settingsChipLabelColor(ColorVision.MONO.previewPalette().shield))
        // Dark fills keep bone.
        assertEquals(Kk.Bone, settingsChipLabelColor(Kk.Ink3))
    }

    @Test
    fun anyGreyFillGetsALabelOfAtLeastTheMinimumContrast() {
        for (level in 0..255) {
            val fill = Color(level, level, level)
            val ratio = contrastRatio(settingsChipLabelColor(fill), fill)
            assertTrue(ratio >= SETTINGS_CHIP_LABEL_CONTRAST, "grey $level: $ratio:1")
        }
    }
}
