// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.ImmutableList

sealed interface GameplayQuery {
    data object GetRunStatus : GameplayQuery
    data object GetActiveWeapon : GameplayQuery
    data object GetBuildSummary : GameplayQuery
}

sealed interface GameplayProjection {
    val instanceId: GameplayInstanceId
    val revision: GameplayRevision
}

data class GameplayRunStatusProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val phase: GameplayRunPhase,
    val progressPending: Boolean,
) : GameplayProjection

data class GameplayActiveWeaponProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val weapon: WeaponId?,
) : GameplayProjection

data class GameplayBuildSummaryProjection(
    override val instanceId: GameplayInstanceId,
    override val revision: GameplayRevision,
    val itemStacks: ImmutableList<Int>,
    val character: CoreShape? = null,
    val characterName: String = "",
    val characterMechanic: String = "",
    val weapon: WeaponId? = null,
    val weaponName: String = "",
    val weaponLevel: Int = 0,
    val mastery: String = "",
    val relics: ImmutableList<EquippedRelic> = immutableListOf(),
    val stats: ImmutableList<BuildStatSummary> = immutableListOf(),
    val synergies: ImmutableList<BuildSynergySummary> = immutableListOf(),
    val eligibleItemIds: ImmutableList<Int> = immutableListOf(),
) : GameplayProjection

enum class BuildStatSource { CHARACTER, LAB, ITEMS, RELICS, SYNERGIES, MASTERY, TEMPORARY }
data class BuildStatContribution(val source: BuildStatSource, val amount: Float)
data class BuildStatSummary(
    val name: String,
    val value: Float,
    val unit: String,
    val contributions: ImmutableList<BuildStatContribution>,
)
data class BuildSynergySummary(
    val id: String,
    val name: String,
    val description: String,
    val active: Boolean,
    val missingComponents: ImmutableList<String>,
)

/** Lifecycle, settings and status capabilities of one bound GameplayRun. */
interface GameplayRunPort : GameplaySettings, GameplayLifecycle {
    val instanceId: GameplayInstanceId
    fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection
}

/** Query-only active-run view safe for presentation composition. */
interface GameplayPresentationPort {
    val instanceId: GameplayInstanceId

    fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection
    fun query(query: GameplayQuery.GetBuildSummary): GameplayBuildSummaryProjection
}
