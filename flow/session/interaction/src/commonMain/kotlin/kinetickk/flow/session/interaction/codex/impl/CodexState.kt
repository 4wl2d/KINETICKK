// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.common.localization.AppLanguage

/** The Codex never lists more items than the content bound allows. */
internal const val CODEX_CATALOG_LIMIT = ContentBounds.MAX_ITEMS
internal const val CODEX_SEARCH_LIMIT = 128
/**
 * The Items filters; saved state and test tags (`codex-filter-N`) use the ordinal. [DISCOVERED] is a legacy saved value
 * read as [ALL]; [ACTIVE] keeps the active items.
 */
internal enum class CodexItemFilter { ALL, DISCOVERED, IN_BUILD, ACTIVE }

/**
 * How the Items matrix groups its rows: by first tag (the default board rows), by first element or by
 * rarity. Saved state and test tags (`codex-sort-N`) use the ordinal.
 */
internal enum class CodexItemSort { TAG, ELEMENT, RARITY }

/** Codex tabs in display order; saved state and test tags (`codex-tab-N`) use the ordinal. */
internal enum class CodexTab { BUILD, CATALOG, REACTIONS, PAIRINGS, TAGS }

/** Representation bounds, kept local to this presentation feature. */
internal class CodexReducer(private val items: ImmutableList<ItemDefinition>) {
    init {
        require(items.size <= CODEX_CATALOG_LIMIT) { "Codex catalog exceeds $CODEX_CATALOG_LIMIT items" }
    }

    fun renderModel(projection: CollectionProjection, runStacks: CodexRunStacks): CodexRenderModel = with(projection.collection) {
        CodexRenderModel(discoveredItemIds, runStacks, items, newItemIds, discoveredRelicIds, newRelicIds,
            discoveredReactionIds, discoveredPairingIds, discoveredWorldEntries)
    }
}

internal fun codexSearchInput(input: String): String = input.take(CODEX_SEARCH_LIMIT)

internal fun codexFilteredItems(model: CodexRenderModel, search: String, filter: CodexItemFilter, language: AppLanguage = AppLanguage.English): List<ItemDefinition> =
    model.items.filter { item ->
        model.isDiscovered(item.id) && item.name.localizedContent(language).contains(search, ignoreCase = true) && when (filter) {
            CodexItemFilter.ALL -> true
            CodexItemFilter.DISCOVERED -> model.isDiscovered(item.id)
            CodexItemFilter.IN_BUILD -> model.runStacks.build != null && model.itemStack(item.id) > 0
            CodexItemFilter.ACTIVE -> item.isActive
        }
    }

internal fun codexUsesSidePanel(widthDp: Float, heightDp: Float): Boolean = widthDp >= 900f && heightDp >= 480f

/** Below this width tabs stack a glyph over a short label. */
internal const val CODEX_COMPACT_TABS_DP = 600f

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

internal enum class CodexEmptyState { NONE, NO_RUN, EMPTY_INVENTORY, EMPTY_SEARCH, NO_PAIRINGS }
internal fun codexEmptyState(tab: CodexTab, hasRun: Boolean, search: String, count: Int): CodexEmptyState = when {
    tab == CodexTab.BUILD && !hasRun -> CodexEmptyState.NO_RUN
    count != 0 -> CodexEmptyState.NONE
    tab == CodexTab.PAIRINGS -> CodexEmptyState.NO_PAIRINGS
    search.isNotEmpty() -> CodexEmptyState.EMPTY_SEARCH
    else -> CodexEmptyState.EMPTY_INVENTORY
}

/**
 * One cell of the triangular reaction matrix: row [row], column [column] (never right of the
 * diagonal). The diagonal holds the row element's Break, every other cell the pair's reaction.
 */
internal data class CodexMatrixCell(val row: StatusElement, val column: StatusElement) {
    val isBreak: Boolean get() = row == column
    val reaction: ReactionId? get() = if (isBreak) null else ReactionId.between(row, column)
    val key: String get() = reaction?.let(::codexReactionKey) ?: codexBreakKey(row)
}

/** Row r holds the reactions of element r with every earlier element, then its own Break. */
internal val CodexMatrix: List<List<CodexMatrixCell>> = StatusElement.entries.map { row ->
    StatusElement.entries.take(row.ordinal + 1).map { column -> CodexMatrixCell(row, column) }
}

/** Reactions a residue of [element] can feed next: every reaction that takes [element] in. */
internal fun codexResidueFeeds(element: StatusElement): Set<ReactionId> = ReactionId.entries.filter { it.involves(element) }.toSet()

internal fun codexReactionKey(id: ReactionId): String = "reaction/$id"
internal fun codexBreakKey(element: StatusElement): String = "break/$element"

/**
 * Opposed relic keys as chains (each key next to the keys it opposes), then the keys that oppose
 * nothing, each on its own. A key opposing two others sits between them, so no key repeats.
 */
internal fun codexKeyGroups(): List<List<RelicKey>> {
    val neighbours = RelicKey.entries.associateWith { key -> RelicKey.entries.filter { it != key && RelicKey.opposed(key, it) } }
    val seen = mutableSetOf<RelicKey>()
    val groups = mutableListOf<List<RelicKey>>()
    RelicKey.entries.filter { neighbours.getValue(it).isNotEmpty() }.forEach { start ->
        if (start in seen) return@forEach
        // Walk the component from one of its ends so a chain reads in order.
        val component = mutableSetOf(start)
        val frontier = ArrayDeque(listOf(start))
        while (frontier.isNotEmpty()) neighbours.getValue(frontier.removeFirst()).forEach { if (component.add(it)) frontier.add(it) }
        val end = component.firstOrNull { neighbours.getValue(it).size == 1 } ?: start
        val chain = mutableListOf(end)
        while (true) {
            val next = neighbours.getValue(chain.last()).firstOrNull { it !in chain } ?: break
            chain += next
        }
        // A component that is not a simple path keeps its remaining keys at the end.
        chain += component.filter { it !in chain }.sortedBy { it.ordinal }
        seen += chain
        groups += chain
    }
    RelicKey.entries.filter { it !in seen }.forEach { groups += listOf(it) }
    return groups
}
