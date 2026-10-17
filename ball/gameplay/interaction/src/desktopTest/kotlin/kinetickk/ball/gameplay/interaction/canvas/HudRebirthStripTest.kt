// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.localeList
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Rebirth strip: the tier plate and mutator sigils on the badge row, clear of the badge and the clock. */
class HudRebirthStripTest {
    @Test
    fun theStripShowsFromTierOneBesideTheBadgeAndClearOfTheClock() {
        for ((width, height) in listOf(1_440f to 810f, 844f to 390f, 390f to 844f)) for (textScale in listOf(1f, 1.75f)) {
            for (language in AppLanguage.entries) for (tier in listOf(0, 1, 6, 10)) {
                val model = hudTestModel(width, height).with("rebirthLevel" to tier, "keys" to 12, "runMatter" to 9_876_543L, "level" to 45)
                draw(width, height, language, textScale) { measurer -> drawHud(model, measurer, 1f) }
                val where = "${width}x$height x$textScale $language tier $tier"
                val strip = HudLayoutProbe.rect(HudBlock.REBIRTH)
                if (tier == 0) {
                    assertNull(strip, "$where: tier 0 shows no strip")
                    continue
                }
                assertNotNull(strip, "$where: no strip")
                val badge = assertNotNull(HudLayoutProbe.rect(HudBlock.BADGE))
                val clock = assertNotNull(HudLayoutProbe.rect(HudBlock.CLOCK))
                assertTrue(strip.left >= badge.right, "$where: strip $strip overlaps the badge $badge")
                assertFalse(strip.overlaps(clock), "$where: strip $strip overlaps the clock $clock")
                HudLayoutProbe.rect(HudBlock.MATTER_CHIP)?.let { chip ->
                    assertFalse(strip.overlaps(chip), "$where: strip $strip overlaps the matter chip $chip")
                }
                HudLayoutProbe.rect(HudBlock.KEY_CHIP)?.let { chip ->
                    assertFalse(strip.overlaps(chip), "$where: strip $strip overlaps the key chip $chip")
                }
            }
        }
    }

    private fun draw(
        width: Float,
        height: Float,
        language: AppLanguage,
        textScale: Float,
        block: androidx.compose.ui.graphics.drawscope.DrawScope.(CanvasTextMeasurer) -> Unit,
    ) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale,
            language, HudTestFonts.typography.copy(localeList = language.localeList()))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width, height)) { block(measurer) }
    }
}
