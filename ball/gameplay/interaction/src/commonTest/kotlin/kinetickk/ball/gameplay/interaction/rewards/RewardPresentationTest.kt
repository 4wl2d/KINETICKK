// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
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
    fun compactContentUsesActualCardDimensionsWithStrictBoundaries() {
        assertTrue(rewardCardIsCompact(179.99f, 300f))
        assertTrue(rewardCardIsCompact(200f, 239.99f))
        assertFalse(rewardCardIsCompact(180f, 240f))
        assertFalse(rewardCardIsCompact(250f, 270f))
    }

    @Test
    fun relicOperationsRetainAcquisitionRankReplacementMeldAndSalvage() {
        assertEquals("BIND TO MATRIX", operation(RelicChoiceAction.ACQUIRE))
        assertEquals("MELD // R2 → R3", operation(RelicChoiceAction.ACQUIRE, ownedRank = 2))
        assertEquals("SALVAGE RESONANCE", operation(RelicChoiceAction.ACQUIRE, ownedRank = 5))
        assertEquals("REPLACE SLOT 4", operation(RelicChoiceAction.REPLACE, slotIndex = 3))
        assertEquals("MELD SIGNAL INTO A SLOT", operation(RelicChoiceAction.MELD))
        assertEquals("MELD // R2 → R3", operation(RelicChoiceAction.MELD_TARGET, slotRank = 2))
        assertEquals("SALVAGE EXCESS", operation(RelicChoiceAction.MELD_TARGET, slotRank = 5))
    }

    private fun operation(
        action: RelicChoiceAction,
        ownedRank: Int = 0,
        slotRank: Int? = null,
        slotIndex: Int = 0,
    ): String = relicRewardOperation(action, ownedRank, slotRank, slotIndex, maxRank = 5)
}
