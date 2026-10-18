// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.common.localization.AppLanguage

class RewardPresentationTest {
    @Test
    fun smallBonusesAreRoundedAndLocalizedWithoutTruncatingToZero() {
        assertEquals("2,84 с", rewardNumber(2.835f, "s", AppLanguage.Russian))
        assertEquals("0,04 с", rewardNumber(0.04f, "s", AppLanguage.Russian))
        assertEquals("1.02×", rewardNumber(1.0175f, "×", AppLanguage.English))
        val heat = RewardStatChange("Dash heat", 36f, 33.25f, "", lowerIsBetter = true).presentation(AppLanguage.Russian)
        assertEquals("Нагрев рывка", heat.name)
        assertEquals("36", heat.before)
        assertEquals("33,25", heat.after)
        assertTrue(heat.improved)
        assertFalse(RewardStatChange("Shield", 12f, 0f, "").presentation(AppLanguage.English).improved)
        val nearCap = RewardStatChange("Dash heat", 12.0001f, 12f, "", lowerIsBetter = true).presentation(AppLanguage.English)
        assertTrue(nearCap.before != nearCap.after, "A real change must not display identical rounded values")
    }
    @Test
    fun unitsStayWithTheirNumbersAndUnitSlashesNeverBreak() {
        assertEquals("All your damage +40\u00A0%.", rewardKeepUnits("All your damage +40 %."))
        assertEquals("above 900\u00A0u\u2060/\u2060s grant", rewardKeepUnits("above 900 u/s grant"))
        assertEquals("1,5\u00A0ед\u2060/\u2060с", rewardKeepUnits("1,5 ед/с"))
        assertEquals("Цена держится 45\u00A0с", rewardKeepUnits("Цена держится 45 с"))
        // Words after a number and spaced slashes stay as they are.
        assertEquals("3 stacks of 2 elite / boss", rewardKeepUnits("3 stacks of 2 elite / boss"))
        // Rich text keeps the emphasized rank value on the same characters after the joiners.
        val text = "Above 700 u/s it adds 40 % damage."
        val value = text.indexOf("40")
        val rich = rewardRichText(text, androidx.compose.ui.graphics.Color.White, listOf(value..value + 1), androidx.compose.ui.graphics.Color.Red)
        val emphasized = rich.spanStyles.filter { it.item.color == androidx.compose.ui.graphics.Color.Red }.single()
        assertEquals("40", rich.text.substring(emphasized.start, emphasized.end))
        assertTrue(rich.spanStyles.any { rich.text.substring(it.start, it.end) == "40\u00A0%" }, "The percent mark is part of the number")
    }

    @Test
    fun stripPairingTagsShareARowWithoutOverlapAndStayLeftOfTheCount() {
        // Two pairings whose brackets overlap (Lightning Rod over slots 0 to 2, Faraday Cage over 1 to 2).
        val widths = floatArrayOf(110f, 104f)
        val middles = floatArrayOf(181f, 205f)
        val lefts = relicStripTagLefts(widths, middles, 6f, right = 170f)
        assertTrue(lefts[0] >= 0f && lefts[1] >= lefts[0] + widths[0] + 6f - 0.01f, "Packed without overlap: ${lefts.toList()}")
        assertTrue(lefts[1] + widths[1] <= 170f + 0.01f || lefts[0] == 0f, "Kept left of the relic count: ${lefts.toList()}")
        // A lone tag stays centered on its bracket when there is room.
        assertEquals(181f - 55f, relicStripTagLefts(floatArrayOf(110f), floatArrayOf(181f), 6f, right = 300f).single())
    }

    @Test
    fun compactContentUsesActualCardDimensionsWithStrictBoundaries() {
        assertTrue(rewardCardIsCompact(179.99f, 300f))
        assertTrue(rewardCardIsCompact(200f, 239.99f))
        assertFalse(rewardCardIsCompact(180f, 240f))
        assertFalse(rewardCardIsCompact(250f, 270f))
    }
}
