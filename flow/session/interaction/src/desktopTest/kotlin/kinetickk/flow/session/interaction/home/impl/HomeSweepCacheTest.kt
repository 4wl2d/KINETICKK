// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Codex sweep tints the menu once per distinct accent; later loops reuse those measurers. */
class HomeSweepCacheTest {
    @Test
    fun aSecondSweepReusesTheTintedAndMenuMeasurersOfTheFirst() {
        val base = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f)
        val motion = HomeMenuMotion()
        repeat(KkTime.PaletteShift / 16 + 2) { motion.advance(HomeLayoutTarget.CODEX, 1f / 60f) }
        val frames = (HOME_CODEX_SWEEP_SECONDS * 60f).toInt()
        val tinted = mutableMapOf<Color, CanvasTextMeasurer>()
        val menus = mutableMapOf<Color, CanvasTextMeasurer>()
        repeat(frames) {
            motion.advance(HomeLayoutTarget.CODEX, 1f / 60f)
            val tint = motion.tinted(base)
            assertEquals(motion.accent, tint.roles.you, "the menu wears the sweep's accent")
            tinted.getOrPut(motion.accent) { tint }
            menus.getOrPut(motion.accent) { motion.menuMeasurer(tint) }
        }
        repeat(frames) {
            motion.advance(HomeLayoutTarget.CODEX, 1f / 60f)
            val tint = motion.tinted(base)
            assertTrue(tinted[motion.accent] === tint, "a steady sweep tinted a new measurer for ${motion.accent}")
            assertTrue(menus[motion.accent] === motion.menuMeasurer(tint), "a steady sweep built a new menu measurer")
        }
    }
}
