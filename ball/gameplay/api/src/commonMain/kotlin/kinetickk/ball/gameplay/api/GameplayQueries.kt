// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList

sealed interface GameplayQuery {
    data object GetRender : GameplayQuery
    data object GetRunStatus : GameplayQuery
    data object GetActiveWeapon : GameplayQuery
    data object GetCodexStacks : GameplayQuery
}

sealed interface GameplayProjection {
    val instanceId: GameplayInstanceId
    val revision: GameplayRevision
}

data class GameplayRenderProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val renderModel: GameplayRenderModel?,
) : GameplayProjection

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

interface GameplayPort {
    val instanceId: GameplayInstanceId

    fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance

    fun accept(
        command: GameplayCommand,
        admission: GameplayCommandAdmission,
    ): GameplayAcceptance

    fun query(query: GameplayQuery.GetRender): GameplayRenderProjection
    fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection
    fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection
    fun query(query: GameplayQuery.GetCodexStacks): GameplayCodexStacksProjection
}
