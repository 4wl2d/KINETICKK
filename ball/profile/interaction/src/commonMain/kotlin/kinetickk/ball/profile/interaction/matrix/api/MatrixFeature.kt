// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.foundation.collections.ImmutableList

/**
 * Why a node can or cannot take its next rank, in the order Profile checks it: a maxed node, then
 * the Rebirth gate, then the prerequisites, then Matter.
 */
enum class MatrixNodeStatus {
    MAXED,
    REBIRTH_LOCKED,
    PREREQUISITE_MISSING,
    SHORT_OF_MATTER,
    AVAILABLE,
}

data class MatrixNodeRenderModel(
    val id: MatrixNodeId,
    val name: String,
    val description: String,
    val branch: MatrixBranch,
    /** Prerequisite depth: the tile row inside its branch column. */
    val row: Int,
    /** Column inside the branch (0 or 1), chosen so prerequisite lines cross as little as possible. */
    val lane: Int,
    val rank: Int,
    val maxRanks: Int,
    /** Price of the next rank; 0 when maxed. */
    val nextCost: Long,
    val minimumRebirth: Int,
    val keystone: Boolean,
    /** Owning any one of these opens the node. */
    val prerequisites: ImmutableList<MatrixNodeId>,
    val status: MatrixNodeStatus,
) {
    val isMaxed: Boolean get() = status == MatrixNodeStatus.MAXED
    val canBuy: Boolean get() = status == MatrixNodeStatus.AVAILABLE
    val owned: Boolean get() = rank > 0
}

/** Small immutable payload rendered by the Matrix feature. */
data class MatrixRenderModel(
    val matter: Long,
    val rebirthRank: Int,
    val nodes: ImmutableList<MatrixNodeRenderModel>,
    /** Matter a reset returns: the price of every owned rank. */
    val refund: Long,
) {
    val ownedNodeCount: Int get() = nodes.count { it.owned }
    val nodeCount: Int get() = nodes.size

    fun node(id: MatrixNodeId): MatrixNodeRenderModel? = nodes.firstOrNull { it.id == id }
}

sealed interface MatrixOutput {
    data object Back : MatrixOutput
}

interface MatrixFeature {
    @Composable
    fun Content(
        routeToken: Long,
        onOutput: (MatrixOutput) -> Unit,
    )
}
