// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.ruleLines
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexLocalizationTest {
    @Test
    fun translatedCatalogSearchKeepsStableIdentitiesAndAvailability() {
        val catalog = codexTestCatalog()
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        val russian = codexCatalogEntries(3, "кРуГ", CodexItemFilter.ALL, model, catalog, codexTestProgress(), AppLanguage.Russian)
        val english = codexCatalogEntries(3, "cIrClE", CodexItemFilter.ALL, model, catalog, codexTestProgress(), AppLanguage.English)
        assertEquals(listOf("shape/${CoreShape.ORB}"), russian.map { it.key })
        assertEquals(english.map { it.key }, russian.map { it.key })
        assertEquals("Круг", russian.single().title)
        assertEquals("Circle", english.single().title)
        assertEquals("Открыто", russian.single().status)
        assertTrue(russian.single().help.orEmpty().contains("Матрица Ядра"))
        assertEquals("Unlocked", english.single().status)
    }

    @Test
    fun itemSearchUsesTranslatedNameAndRetainsDiscoveryFilter() {
        val item = ItemDefinition(0, "Heat Sink", "Copper fins drink the heat.", ItemRarity.COMMON, immutableListOf(BuildTag.OVERCLOCK),
            stats = immutableListOf(StatDelta(ItemEffect.REGEN, 0.1f)), maxStacks = 3, unlockLevel = 1)
        val model = CodexRenderModel(immutableSetOf(0), CodexRunStacks(), immutableListOf(item))
        val russianEntry = codexItemEntry(item, model, 0L, AppLanguage.Russian)
        assertEquals("Теплоотвод", russianEntry.title)
        assertEquals(listOf(item), codexFilteredItems(model, russianEntry.title.lowercase(), CodexItemFilter.DISCOVERED, AppLanguage.Russian))
        assertTrue(codexFilteredItems(model, "Heat", CodexItemFilter.ALL, AppLanguage.Russian).isEmpty())
        assertEquals(listOf(item), codexFilteredItems(model, "hEaT", CodexItemFilter.ALL, AppLanguage.English))
        assertEquals("Обычный", russianEntry.kind)
        // The body is the translated flavor line and the game's own generated rule text.
        assertTrue(russianEntry.description.startsWith("Медные рёбра пьют жар."), russianEntry.description)
        val rules = assertIsItem(russianEntry).lines
        assertEquals(item.ruleLines(AppLanguage.Russian).map { it.text }, rules.map { it.text })
        assertTrue(rules.single().text.contains("восстановлению прочности"), rules.single().text)
        assertEquals(CodexFact("Выпадает с", "Ур. 1", russianEntry.facts.single().info), russianEntry.facts.single())
    }

    @Test
    fun reactionAndBreakEntriesShowNamesInTheVisibleLanguageAndHideUndiscoveredReactions() {
        val catalog = codexTestCatalog()
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items, discoveredReactionIds = immutableSetOf(ReactionId.THERMAL_SHOCK))
        val russian = codexReactionEntries(model, catalog, AppLanguage.Russian).associateBy { it.key }
        assertEquals("Термошок", russian.getValue(codexReactionKey(ReactionId.THERMAL_SHOCK)).title)
        assertEquals("Воспламенение", russian.getValue("break/THERMAL").title)
        val hidden = russian.getValue(codexReactionKey(ReactionId.ROT_WELL))
        assertEquals("Неизвестная реакция", hidden.title)
        assertTrue(hidden.description.isEmpty())
        assertFalse(hidden.discovered)
        russian.values.forEach { entry ->
            listOf(entry.title, entry.description, entry.kind).forEach { text ->
                assertFalse(text.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in Russian ${entry.key}: $text")
            }
        }
    }

    private fun assertIsItem(entry: CodexEntry): CodexDetail.Item = entry.detail as CodexDetail.Item
}
