// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList

sealed interface GameplayQuery {
    data object GetRunStatus : GameplayQuery
    data object GetActiveWeapon : GameplayQuery
    data object GetCodexStacks : GameplayQuery
}

sealed interface GameplayProjection {
    val instanceId: GameplayInstanceId
    val revision: GameplayRevision
}

data class GameplayRunStatusProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val phase: GameplayRunPhase,
    val profileCommandPending: Boolean,
) : GameplayProjection

data class GameplayActiveWeaponProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val weapon: WeaponId?,
) : GameplayProjection

data class GameplayCodexStacksProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val itemStacks: ImmutableList<Int>,
) : GameplayProjection

/** AppSession's source-bound command route and sole workflow read. */
interface GameplaySessionRunPort {
    val instanceId: GameplayInstanceId

    fun acceptFromSession(
        request: GameplayModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): GameplayCommandIngressResult

    fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection
}

/** Query-only active-run view safe for presentation composition. */
interface GameplayPresentationPort {
    val instanceId: GameplayInstanceId

    fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection
    fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection
}
