// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf

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
    val discoveredReactionIds: ImmutableSet<ReactionId> = immutableSetOf(),
    val discoveredPairingIds: ImmutableSet<RelicPairingId> = immutableSetOf(),
    /** World places and secrets the Profile recorded; the World category names only these. */
    val discoveredWorldEntries: ImmutableSet<WorldDiscovery> = immutableSetOf(),
) {
    fun isDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds || itemStack(itemId) > 0
    fun isRelicDiscovered(id: RelicId): Boolean = id in discoveredRelicIds || runStacks.build?.relics?.any { it.id == id } == true
    fun itemStack(itemId: Int): Int = runStacks.itemStacks.getOrElse(itemId) { 0 }

    /** Reactions are revealed only once triggered (recorded by the Profile). */
    fun isReactionDiscovered(id: ReactionId): Boolean = id in discoveredReactionIds

    /** A named pairing is known once recorded, or while the current run has both relics bound together. */
    fun isPairingDiscovered(id: RelicPairingId): Boolean =
        id in discoveredPairingIds || runStacks.build?.relicLinks?.any { it.pairing == id && it.discovered } == true

    /** A world discovery is known once the Profile recorded it (found in an earlier run or published by this one). */
    fun isWorldDiscovered(id: WorldDiscovery): Boolean = id in discoveredWorldEntries
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
