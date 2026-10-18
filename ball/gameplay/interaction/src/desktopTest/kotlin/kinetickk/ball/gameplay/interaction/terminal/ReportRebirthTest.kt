// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import kinetickk.ball.gameplay.interaction.canvas.hudTestModel
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Victory report names the Rebirth rank a clear earns and what it opens, or the Ascendant clear. */
class ReportRebirthTest {
    @Test
    fun clearingTheEarnedTierNamesTheNextRankAndItsUnlocks() {
        val victory = hudTestModel().with("phase" to GamePhase.VICTORY, "rebirthLevel" to 3, "rebirthRank" to 3)
        val english = assertNotNull(victory.reportRebirthLines(AppLanguage.English))
        assertEquals("Reborn rank 4 earned", english.headline)
        val unlocks = assertNotNull(english.unlocks)
        assertTrue(unlocks.startsWith("Unlocked: "), unlocks)
        assertTrue("2 Matrix nodes" in unlocks, unlocks)
        assertTrue("new enemies Reflector" in unlocks, unlocks)
        val russian = assertNotNull(victory.reportRebirthLines(AppLanguage.Russian))
        assertEquals("Получен ранг перерождения 4", russian.headline)
        assertTrue(assertNotNull(russian.unlocks).none { it in 'A'..'Z' || it in 'a'..'z' }, russian.unlocks)
        assertEquals(victory.terminalPresentation(AppLanguage.English).rebirthEarned, english)
        // The Spiral opens at rank 1.
        val first = hudTestModel().with("phase" to GamePhase.VICTORY, "rebirthLevel" to 0, "rebirthRank" to 0)
        assertTrue("Spiral" in assertNotNull(assertNotNull(first.reportRebirthLines(AppLanguage.English)).unlocks))
    }

    @Test
    fun aReplayedTierADefeatAndTheFinalTierReadDifferently() {
        assertNull(hudTestModel().with("phase" to GamePhase.VICTORY, "rebirthLevel" to 2, "rebirthRank" to 5)
            .reportRebirthLines(AppLanguage.English))
        assertNull(hudTestModel().with("phase" to GamePhase.GAME_OVER, "rebirthLevel" to 5, "rebirthRank" to 5)
            .reportRebirthLines(AppLanguage.English))
        val ascendant = assertNotNull(hudTestModel().with("phase" to GamePhase.VICTORY, "rebirthLevel" to 10, "rebirthRank" to 10)
            .reportRebirthLines(AppLanguage.English))
        assertTrue(ascendant.ascendant)
        assertEquals("Tier 10 cleared, Ascendant", ascendant.headline)
        assertNull(ascendant.unlocks)
    }
}
