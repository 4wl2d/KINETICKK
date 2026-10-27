// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real item catalog in the Codex: within its bound, every item in one slot of every sort; every shipped active item
 * carries its effect's active part and the ACTIVE notch no passive cell carries, and the Active filter shows exactly them.
 */
@OptIn(ExperimentalTestApi::class)
class CodexShippedCatalogTest {
    private val catalog = createContentCatalog().uiCatalog()

    @Test
    fun theShippedCatalogFitsTheCodexAndEveryItemHasOneSlotInEverySort() {
        val items = catalog.items
        assertEquals(287, items.size)
        assertEquals(ContentBounds.MAX_ITEMS, CODEX_CATALOG_LIMIT)
        assertTrue(items.size <= CODEX_CATALOG_LIMIT)
        // The stand-in shape the common tests lay out is the real one.
        val shape = (0..BuildTag.entries.size).map { row ->
            ItemRarity.entries.map { rarity -> items.count { (it.tags.firstOrNull()?.ordinal ?: BuildTag.entries.size) == row && it.rarity == rarity } }
        }
        assertEquals(ShippedTagRarityCounts, shape)
        val model = CodexRenderModel(items.map { it.id }.toImmutableSet(), CodexRunStacks(), items)
        val progress = codexTestProgress(emptySet())
        CodexItemSort.entries.forEach { sort ->
            val rows = codexItemRows(sort, "", CodexItemFilter.ALL, model, catalog, progress)
            assertEquals(items.map { "item/${it.id}" }.sorted(), rows.entries.map { it.key }.sorted(), "$sort shows every item once")
        }
    }

    @Test
    fun everyShippedActiveItemShowsItsEffectsRanksAndTheActiveFilterKeepsExactlyThem() {
        val items = catalog.items
        val actives = items.filter { it.isActive }
        assertEquals(SkillId.entries.toSet(), actives.mapNotNull { it.active }.toSet())
        val model = CodexRenderModel(items.map { it.id }.toImmutableSet(), CodexRunStacks(), items)
        CodexItemSort.entries.forEach { sort ->
            val rows = codexItemRows(sort, "", CodexItemFilter.ACTIVE, model, catalog, codexTestProgress(emptySet()))
            assertEquals(actives.map { "item/${it.id}" }.sorted(), rows.entries.map { it.key }.sorted(), "$sort under the Active filter")
        }
        for (language in AppLanguage.entries) actives.forEach { item ->
            val skill = catalog.skill(requireNotNull(item.active))
            val part = assertNotNull((codexItemEntry(item, model, 0L, language, catalog).detail as CodexDetail.Item).active)
            assertEquals((1..ContentBounds.MAX_SKILL_RANK).map { skill.rankLines(it, language).drop(1).joinToString(", ") },
                part.ranks.map { it.numbers.replace('\u00A0', ' ') }, "${item.name} $language")
            assertEquals(skill.rankThree.localizedContent(language), part.rankThree)
        }
    }

    @Test
    fun theActiveFilterShowsEachActivesNotchAndItsSheetListsItsRanks() = runComposeUiTest {
        val items = catalog.items
        val actives = items.filter { it.isActive }
        val model = CodexRenderModel(items.map { it.id }.toImmutableSet(), CodexRunStacks(), items)
        val solarFlare = actives.single { it.active == SkillId.SOLAR_FLARE }
        val language = AppLanguage.Russian
        setContent {
            Box(Modifier.requiredSize(1440.dp, 810.dp)) {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    CodexContent(catalog, model, codexTestProgress(emptySet()), 1.25f) { }
                }
            }
        }
        // The Codex opens on All: passive cells sit among the actives and carry no notch; every active cell in view does.
        onNodeWithTag("codex-filter-0").assertIsSelected()
        val activeKeys = actives.map { "item/${it.id}" }.toSet()
        val passives = items.filter { !it.isActive }
        listOf(passives.first(), passives[passives.size / 2], passives.last(), solarFlare).forEach { item ->
            onNodeWithTag("codex-grid").performScrollToKey("item/${item.id}")
            onNodeWithTag("codex-slot-item/${item.id}").assertExists()
            if (!item.isActive) onNodeWithTag("codex-active-item/${item.id}", useUnmergedTree = true).assertDoesNotExist()
            val shown = onAllNodesWithTagPrefix("codex-slot-item/")
            assertTrue(shown.any { it !in activeKeys }, "passive cells are in view: $shown")
            assertEquals(shown.filter { it in activeKeys }.toSet(), onAllNodesWithTagPrefix("codex-active-item/").toSet(),
                "only the active cells in view carry the notch: $shown")
        }

        onNodeWithTag("codex-filter-3").performClick()
        actives.forEach { item ->
            onNodeWithTag("codex-grid").performScrollToKey("item/${item.id}")
            onNodeWithTag("codex-active-item/${item.id}", useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("codex-grid").performScrollToKey("item/${solarFlare.id}")
        onNodeWithTag("codex-slot-item/${solarFlare.id}").performClick()
        onNodeWithTag("codex-active-chip").assertExists()
        onNodeWithTag("codex-active").assertExists()
        val part = assertNotNull((codexItemEntry(solarFlare, model, 0L, language, catalog).detail as CodexDetail.Item).active)
        part.ranks.forEachIndexed { index, rank ->
            onNodeWithTag("codex-active-rank-${index + 1}").assertExists()
            onNode(hasText(codexKeepUnits(rank.numbers)), useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("codex-active-rank-three").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagPrefix(prefix: String): List<String> =
        onAllNodes(SemanticsMatcher("tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix(prefix.removeSuffix("item/")) }
}
