// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.upsideText
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class RewardPlaceholderPresentationTest {
    @Test
    fun indexedValuesFollowTemplateOrderAndLeaveLiteralBracesUnhighlighted() {
        val relic = rewardFixtureContent.relic(RelicId.BLINK_DRIVE).copy(
            upside = "{literal}: travel {1} u, then deal {0} % damage and {0} % again.",
        )
        for (language in AppLanguage.entries) {
            val text = relic.upsideText(language, rank = 2)
            val highlighted = relic.upsideValueRanges(language, rank = 2).map(text::substring)
            assertEquals(listOf("260", "140", "140"), highlighted)
        }
    }

    @Test
    fun plainLiteralBracesHaveNoRankHighlights() {
        val relic = rewardFixtureContent.relic(RelicId.BLINK_DRIVE).copy(
            upside = "Keep {literal} as ordinary text.",
        )
        assertEquals(emptyList(), relic.upsideValueRanges(AppLanguage.English, rank = 1))
    }
}
