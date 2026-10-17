// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.matrix.api.MatrixNodeRenderModel
import kinetickk.ball.profile.interaction.matrix.api.MatrixNodeStatus
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kinetickk.ball.profile.interaction.matrix.api.MatrixRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal sealed interface MatrixAction {
    /** The detail panel's Buy rank button for [id]. */
    data class PurchaseRequested(val id: MatrixNodeId) : MatrixAction

    /** Keyboard focus moves the selection (hover only highlights a tile). */
    data class Select(val id: MatrixNodeId) : MatrixAction

    /** Tile activation (click, tap, Enter): a press on the selected tile buys its next rank, otherwise it selects. */
    data class Activate(val id: MatrixNodeId) : MatrixAction

    /** The header Reset: the first press arms it, the second refunds every rank. */
    data object ResetRequested : MatrixAction

    data object Back : MatrixAction
}

/**
 * Matrix presentation state: the Profile-backed [model], the selected node ([selected] null = the
 * first node), whether Reset waits for its confirming press, and the purchase feedback [flash].
 */
internal data class MatrixState(
    val model: MatrixRenderModel,
    val selected: MatrixNodeId? = null,
    val resetArmed: Boolean = false,
    val flash: MatrixPurchaseFlash? = null,
) {
    val selectedNode: MatrixNodeRenderModel?
        get() = model.nodes.firstOrNull { it.id == selected } ?: model.nodes.firstOrNull()
}

/** Purchase feedback: the tile inverts for one frame and nudges (Pull); [sequence] restarts it. */
internal data class MatrixPurchaseFlash(val id: MatrixNodeId, val sequence: Int)

internal const val MATRIX_PURCHASE_FLASH_MS = 500

internal sealed interface MatrixUiEffect {
    data class Purchase(val id: MatrixNodeId) : MatrixUiEffect
    data object Reset : MatrixUiEffect
    data class PlayAudio(val cue: ProfileAudioCue) : MatrixUiEffect
    data class Emit(val output: MatrixOutput) : MatrixUiEffect
}

internal data class MatrixReduction(
    val state: MatrixState,
    val effects: List<MatrixUiEffect> = emptyList(),
)

internal object MatrixReducer {
    fun reduce(state: MatrixState, action: MatrixAction): MatrixReduction = when (action) {
        is MatrixAction.PurchaseRequested -> MatrixReduction(
            state = state.copy(selected = action.id, resetArmed = false),
            // Prices, gates and prerequisites depend on canonical Profile state and belong to its Nucleus.
            effects = listOf(MatrixUiEffect.Purchase(action.id)),
        )
        is MatrixAction.Select -> MatrixReduction(
            if (state.selected == action.id) state else state.copy(selected = action.id, resetArmed = false),
        )
        is MatrixAction.Activate -> if (state.selectedNode?.id == action.id) {
            reduce(state, MatrixAction.PurchaseRequested(action.id))
        } else {
            MatrixReduction(state.copy(selected = action.id, resetArmed = false))
        }
        MatrixAction.ResetRequested -> when {
            state.model.ownedNodeCount == 0 -> MatrixReduction(state.copy(resetArmed = false))
            !state.resetArmed -> MatrixReduction(
                state = state.copy(resetArmed = true),
                effects = listOf(MatrixUiEffect.PlayAudio(ProfileAudioCue.UI_CLICK)),
            )
            else -> MatrixReduction(state.copy(resetArmed = false), listOf(MatrixUiEffect.Reset))
        }
        MatrixAction.Back -> MatrixReduction(
            state = state.copy(resetArmed = false),
            effects = listOf(
                MatrixUiEffect.PlayAudio(ProfileAudioCue.UI_CLICK),
                MatrixUiEffect.Emit(MatrixOutput.Back),
            ),
        )
    }

    /** Applies the Profile's answer to a purchase: a new model, and the flash when accepted. */
    fun purchased(state: MatrixState, id: MatrixNodeId, model: MatrixRenderModel, accepted: Boolean): MatrixState = state.copy(
        model = model,
        flash = if (accepted) MatrixPurchaseFlash(id, (state.flash?.sequence ?: 0) + 1) else state.flash,
    )

    /** Applies the Profile's answer to a reset: the refreshed model, disarmed. */
    fun reset(state: MatrixState, model: MatrixRenderModel): MatrixState = state.copy(model = model, resetArmed = false)
}

internal fun MatrixProfileSnapshot.toRenderModel(
    nodes: ImmutableList<MatrixNodeDefinition>,
): MatrixRenderModel {
    val ranks = nodes.associate { it.id to progress.rank(it.id).coerceIn(0, it.maxRanks) }
    val lanes = matrixLanes(nodes)
    val matter = economy.matter
    return MatrixRenderModel(
        matter = matter,
        rebirthRank = rebirthRank,
        nodes = nodes.map { definition ->
            val rank = ranks.getValue(definition.id)
            val maxed = rank >= definition.maxRanks
            val cost = if (maxed) 0L else definition.cost(rank).toLong()
            val status = when {
                maxed -> MatrixNodeStatus.MAXED
                rebirthRank < definition.minimumRebirth -> MatrixNodeStatus.REBIRTH_LOCKED
                definition.prerequisites.isNotEmpty() && definition.prerequisites.none { (ranks[it] ?: 0) > 0 } ->
                    MatrixNodeStatus.PREREQUISITE_MISSING
                matter < cost -> MatrixNodeStatus.SHORT_OF_MATTER
                else -> MatrixNodeStatus.AVAILABLE
            }
            MatrixNodeRenderModel(
                id = definition.id,
                name = definition.name,
                description = definition.description,
                branch = definition.branch,
                row = definition.row,
                lane = lanes.getValue(definition.id),
                rank = rank,
                maxRanks = definition.maxRanks,
                nextCost = cost,
                minimumRebirth = definition.minimumRebirth,
                keystone = definition.keystone,
                prerequisites = definition.prerequisites,
                status = status,
            )
        }.toImmutableList(),
        refund = nodes.sumOf { it.spent(ranks.getValue(it.id)) },
    )
}

/**
 * Lane of every node inside its branch column. The first row keeps catalog order; every later row
 * orders its nodes by the mean lane of their prerequisites (catalog order breaks ties), so lines
 * from one row to the next cross as little as possible.
 */
internal fun matrixLanes(nodes: List<MatrixNodeDefinition>): Map<MatrixNodeId, Int> {
    val lanes = HashMap<MatrixNodeId, Int>(nodes.size)
    MatrixBranch.entries.forEach { branch ->
        val inBranch = nodes.filter { it.branch == branch }
        inBranch.map { it.row }.distinct().sorted().forEach { row ->
            val inRow = inBranch.filter { it.row == row }
            val ordered = inRow.withIndex().sortedWith(
                compareBy<IndexedValue<MatrixNodeDefinition>>(
                    { (_, node) ->
                        val placed = node.prerequisites.mapNotNull { lanes[it] }
                        if (placed.isEmpty()) Float.MAX_VALUE else placed.sum().toFloat() / placed.size
                    },
                    { it.index },
                ),
            )
            ordered.forEachIndexed { lane, (_, node) -> lanes[node.id] = lane }
        }
    }
    return lanes
}

/** Owned nodes over all nodes (Home and the header show it as 7/32). */
internal fun MatrixRenderModel.nodeTotals(): Pair<Int, Int> = ownedNodeCount to nodeCount
