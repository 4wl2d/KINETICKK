// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.ball.content.api.RelicId

data class CodexRunStacks(
    val itemStacks: ImmutableList<Int> = immutableListOf(),
    val build: kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection? = null,
)

data class CodexRenderModel(
    val discoveredItemIds: ImmutableSet<Int>,
    val runStacks: CodexRunStacks,
    val items: ImmutableList<ItemDefinition>,
    val newItemIds: ImmutableSet<Int> = immutableSetOf(),
    val discoveredRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
    val newRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
) {
    fun isDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds || itemStack(itemId) > 0
    fun isRelicDiscovered(id: RelicId): Boolean = id in discoveredRelicIds || runStacks.build?.relics?.any { it.id == id } == true
    fun itemStack(itemId: Int): Int = runStacks.itemStacks.getOrElse(itemId) { 0 }
}

sealed interface CodexOutput {
    data object Back : CodexOutput
}

interface CodexFeature {
    @Composable
    fun Content(
        runStacks: CodexRunStacks,
        onOutput: (CodexOutput) -> Unit,
    )
}
