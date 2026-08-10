// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.ItemDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.profile.api.CollectionCapability
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks

internal const val CODEX_PAGE_SIZE = 10

internal sealed interface CodexAction {
    data object Back : CodexAction
    data object PreviousPage : CodexAction
    data object NextPage : CodexAction
}

internal data class CodexReduction(val page: Int, val close: Boolean = false)

internal class CodexReducer(
    private val capability: CollectionCapability,
    private val items: ImmutableList<ItemDefinition>,
) {
    val maxPage: Int
        get() = if (items.isEmpty()) 0 else (items.size - 1) / CODEX_PAGE_SIZE

    fun renderModel(runStacks: CodexRunStacks): CodexRenderModel = CodexRenderModel(
        discoveredItemIds = capability.collectionSnapshot().discoveredItemIds,
        runStacks = runStacks,
        items = items,
    )

    fun reduce(page: Int, action: CodexAction): CodexReduction = when (action) {
        CodexAction.Back -> CodexReduction(page.coerceIn(0, maxPage), close = true)
        CodexAction.PreviousPage -> CodexReduction((page.coerceIn(0, maxPage) - 1).coerceAtLeast(0))
        CodexAction.NextPage -> CodexReduction((page.coerceIn(0, maxPage) + 1).coerceAtMost(maxPage))
    }
}

internal data class CodexViewport(val width: Float, val height: Float, val density: Float)

internal fun resolveCodexPress(viewport: CodexViewport, x: Float, y: Float): CodexAction? {
    val d = viewport.density
    val width = minOf(900f * d, viewport.width - 30f * d)
    val height = minOf(650f * d, viewport.height - 30f * d)
    val left = (viewport.width - width) * 0.5f
    val top = (viewport.height - height) * 0.5f
    val right = left + width
    val bottom = top + height
    if (y <= bottom - 55f * d) return null
    return when {
        x < left + width * 0.45f -> CodexAction.Back
        x < right - 85f * d -> CodexAction.PreviousPage
        else -> CodexAction.NextPage
    }
}
