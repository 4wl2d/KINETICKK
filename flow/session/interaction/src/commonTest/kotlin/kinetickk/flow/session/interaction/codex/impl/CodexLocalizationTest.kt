// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("ОТКРЫТО", russian.single().availability)
        assertTrue(russian.single().description.contains("Улучшения лаборатории"))
        assertEquals("UNLOCKED", english.single().availability)
    }

    @Test
    fun itemSearchUsesTranslatedNameAndRetainsDiscoveryFilter() {
        val item = ItemDefinition(0, "Cinder Ram", "Cinder Ram", ItemRarity.COMMON,
            ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.1f), ItemModifier(ItemEffect.REGEN, 0.1f), 3, 1, "Impact")
        val model = CodexRenderModel(immutableSetOf(0), CodexRunStacks(), immutableListOf(item))
        val russianEntry = codexItemEntry(item, model, AppLanguage.Russian)
        assertEquals(listOf(item), codexFilteredItems(model, russianEntry.title.lowercase(), CodexItemFilter.DISCOVERED, AppLanguage.Russian))
        assertTrue(codexFilteredItems(model, "Cinder", CodexItemFilter.ALL, AppLanguage.Russian).isEmpty())
        assertEquals(listOf(item), codexFilteredItems(model, "cInDeR", CodexItemFilter.ALL, AppLanguage.English))
        assertEquals("Обычный", russianEntry.kind)
        assertTrue(russianEntry.description.contains("Восстановление прочности: +0,1/с"))
        assertTrue(russianEntry.availability.contains("ОТКРЫТО"))
    }
}
