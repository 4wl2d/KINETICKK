// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardPresentationTest {
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
