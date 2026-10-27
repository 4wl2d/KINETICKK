// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillRankLinesTest {
    private val skills = defaultSkillDefinitions()

    @Test
    fun rankLinesReadTheRanksNumbersInBothLanguages() {
        val flare = skills[SkillId.SOLAR_FLARE.ordinal]
        assertEquals(listOf("Rank 2", "Cooldown 7.65 s", "Radius 220", "Damage 56", "Stacks 2"), flare.rankLines(2, AppLanguage.English))
        assertEquals(listOf("Ранг 2", "Перезарядка 7,65 с", "Радиус 220", "Урон 56", "Слои 2"), flare.rankLines(2, AppLanguage.Russian))
        val well = skills[SkillId.GRAVITY_WELL.ordinal]
        assertEquals("Duration 4.9 s", well.rankLines(2, AppLanguage.English)[3])
        assertEquals("Длительность 4,9 с", well.rankLines(2, AppLanguage.Russian)[3])
    }

    @Test
    fun everySkillAndRankListsItsRankCooldownAndAtLeastOneValue() {
        for (skill in skills) for (rank in 1..ContentBounds.MAX_SKILL_RANK) {
            val lines = skill.rankLines(rank, AppLanguage.English)
            assertEquals("Rank $rank", lines[0])
            assertTrue(lines[1].startsWith("Cooldown "), "${skill.id} $rank")
            assertTrue(lines.size >= 3, "${skill.id} lists a value")
            assertEquals(lines.size, skill.rankLines(rank, AppLanguage.Russian).size)
        }
    }
}
