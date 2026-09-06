// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.ball.gameplay.interaction.canvas.toPerformanceHudProjection
import kinetickk.ball.gameplay.interaction.gameplayBrakeStateDescription
import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceSnapshot
import kinetickk.ball.gameplay.interaction.rewards.relicRewardOperation
import kinetickk.ball.gameplay.interaction.rewards.rewardHeading
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameplayLocalizationTest {
    @Test
    fun everyGameplayResourceHasBothTranslationsAndPreservesAllArguments() {
        val arguments = Regex("\\{\\d+}")
        GameplayText.entries.forEach { resource ->
            assertTrue(resource.english.isNotBlank(), "Missing English: $resource")
            assertTrue(resource.russian.isNotBlank(), "Missing Russian: $resource")
            assertNotEquals(resource.english, resource.russian, "Untranslated resource: $resource")
            assertEquals(
                arguments.findAll(resource.english).map { it.value }.sorted().toList(),
                arguments.findAll(resource.russian).map { it.value }.sorted().toList(),
                "Translation changed formatting arguments: $resource",
            )
        }
    }

    @Test
    fun changingLanguageChangesRewardsAndAccessibilityWithoutChangingTheirValues() {
        assertEquals("RELIC MELD", rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.English))
        assertEquals("СЛИЯНИЕ РЕЛИКВИЙ", rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.Russian))
        assertEquals("СЛИЯНИЕ // Р2 → Р3", relicRewardOperation(RelicChoiceAction.MELD_TARGET, 0, 2, 0, 5, AppLanguage.Russian))
        assertEquals("REPLACE SLOT 4", relicRewardOperation(RelicChoiceAction.REPLACE, 0, 2, 3, 5, AppLanguage.English))
        assertEquals("ЗАМЕНИТЬ ЯЧЕЙКУ 4", relicRewardOperation(RelicChoiceAction.REPLACE, 0, 2, 3, 5, AppLanguage.Russian))
        assertEquals("pressed", gameplayBrakeStateDescription(true, AppLanguage.English))
        assertEquals("нажат", gameplayBrakeStateDescription(true, AppLanguage.Russian))
        assertEquals("отпущен", gameplayBrakeStateDescription(false, AppLanguage.Russian))
        assertEquals("УР. 7   ДАННЫЕ 12/20", AppLanguage.Russian.text(GameplayText.LevelData, 7, 12, 20))
    }

    @Test
    fun diagnosticProjectionTranslatesLabelsWhileRetainingMeasurements() {
        val english = GameplayPerformanceSnapshot.Empty.toPerformanceHudProjection(AppLanguage.English)
        val russian = GameplayPerformanceSnapshot.Empty.toPerformanceHudProjection(AppLanguage.Russian)
        assertTrue(english.frameLine.startsWith("FRAME ms"))
        assertTrue(russian.frameLine.startsWith("КАДР мс"))
        assertTrue(english.entitiesLine.startsWith("ENTITY CUR/PEAK"))
        assertTrue(russian.entitiesLine.startsWith("ОБЪЕКТЫ ТЕК/ПИК"))
        val values = Regex("\\d+(?:\\.\\d+)?")
        assertEquals(
            values.findAll(english.compactLines.joinToString()).map { it.value }.toList(),
            values.findAll(russian.compactLines.joinToString().replace(',', '.')).map { it.value }.toList(),
        )
        assertEquals(english.hasSlowFrames, russian.hasSlowFrames)
    }
}
