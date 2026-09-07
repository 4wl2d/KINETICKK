// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SynergyDefinition
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.ImmutableList

internal const val CODEX_CATALOG_LIMIT = 400
internal const val CODEX_SEARCH_LIMIT = 128
internal enum class CodexItemFilter { ALL, DISCOVERED, IN_BUILD }

/** Representation bounds, kept local to this presentation feature. */
internal class CodexReducer(private val items: ImmutableList<ItemDefinition>) {
    init {
        require(items.size <= CODEX_CATALOG_LIMIT) { "Codex catalog exceeds $CODEX_CATALOG_LIMIT items" }
    }

    fun renderModel(projection: CollectionProjection, runStacks: CodexRunStacks): CodexRenderModel =
        CodexRenderModel(projection.collection.discoveredItemIds, runStacks, items)
}

internal fun codexSearchInput(input: String): String = input.take(CODEX_SEARCH_LIMIT)

internal fun codexFilteredItems(model: CodexRenderModel, search: String, filter: CodexItemFilter, language: AppLanguage = AppLanguage.English): List<ItemDefinition> =
    model.items.filter { item ->
        item.name.localizedContent(language).contains(search, ignoreCase = true) && when (filter) {
            CodexItemFilter.ALL -> true
            CodexItemFilter.DISCOVERED -> model.isDiscovered(item.id)
            CodexItemFilter.IN_BUILD -> model.runStacks.build != null && model.itemStack(item.id) > 0
        }
    }

internal fun codexUsesSidePanel(widthDp: Float, heightDp: Float): Boolean = widthDp >= 900f && heightDp >= 480f

/** Hover/focus are ephemeral; only pinnedKey and sheetOpen are saved by Compose. */
internal data class CodexSelection(
    val pinnedKey: String? = null,
    val hoverKey: String? = null,
    val focusKey: String? = null,
    val sheetOpen: Boolean = false,
) {
    val previewKey: String? get() = hoverKey ?: focusKey ?: pinnedKey
    fun activate(key: String): CodexSelection = copy(pinnedKey = key, sheetOpen = true)
    fun closeSheet(): CodexSelection = copy(sheetOpen = false, hoverKey = null, focusKey = null)
    fun retain(keys: Set<String>): CodexSelection = copy(
        pinnedKey = pinnedKey?.takeIf { it in keys },
        hoverKey = hoverKey?.takeIf { it in keys },
        focusKey = focusKey?.takeIf { it in keys },
        sheetOpen = sheetOpen && pinnedKey in keys,
    )
}

internal enum class CodexEmptyState { NONE, NO_RUN, EMPTY_INVENTORY, EMPTY_SEARCH }
internal fun codexEmptyState(tab: Int, hasRun: Boolean, search: String, count: Int): CodexEmptyState = when {
    tab == 0 && !hasRun -> CodexEmptyState.NO_RUN
    count != 0 -> CodexEmptyState.NONE
    search.isNotEmpty() -> CodexEmptyState.EMPTY_SEARCH
    else -> CodexEmptyState.EMPTY_INVENTORY
}

/** Aspect pairs are distinct identities; missing places stay visible, even when one relic has rank 3. */
internal fun codexSynergyComponents(
    definition: SynergyDefinition,
    catalog: UiCatalogSnapshot,
    owned: Set<RelicId>,
): List<RelicId?> = if (definition.requiredAspect == null) {
    definition.requiredRelics
} else {
    val present = catalog.relics.filter { it.aspect == definition.requiredAspect && it.id in owned }
        .map { it.id }.distinct().take(2)
    List(2) { present.getOrNull(it) }
}
