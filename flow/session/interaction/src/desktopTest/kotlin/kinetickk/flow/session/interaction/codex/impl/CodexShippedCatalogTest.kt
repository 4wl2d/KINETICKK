// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.toImmutableSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real item catalog in the Codex: within its bound, every item in one slot of every sort. */
class CodexShippedCatalogTest {
    private val catalog = createContentCatalog().uiCatalog()

    @Test
    fun theShippedCatalogFitsTheCodexAndEveryItemHasOneSlotInEverySort() {
        val items = catalog.items
        assertEquals(221, items.size)
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
}
