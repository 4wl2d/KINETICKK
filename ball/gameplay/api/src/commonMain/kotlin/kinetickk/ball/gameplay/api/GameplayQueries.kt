// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
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
    val eligibleItemIds: ImmutableList<Int> = immutableListOf(),
    val tags: ImmutableList<BuildTagSummary> = immutableListOf(),
    val elements: ImmutableList<BuildElementSummary> = immutableListOf(),
    val relicLinks: ImmutableList<BuildRelicLinkSummary> = immutableListOf(),
    val reachableReactions: ImmutableList<ReactionId> = immutableListOf(),
    val suppressedItemIds: ImmutableList<Int> = immutableListOf(),
    val rebornItemIds: ImmutableList<Int> = immutableListOf(),
    val severedRelicIds: ImmutableList<RelicId> = immutableListOf(),
    /** The run's permanent upgrades, "Refit {character level} · Honing {current weapon level}" (content-localized). */
    val upgrades: String = "",
) : GameplayProjection

enum class BuildStatSource { CHARACTER, LAB, ITEMS, RELICS, SYNERGIES, MASTERY, TEMPORARY }
data class BuildStatContribution(val source: BuildStatSource, val amount: Float)
data class BuildStatSummary(
    val name: String,
    val value: Float,
    val unit: String,
    val contributions: ImmutableList<BuildStatContribution>,
)
/** Archetype tag points and the reached resonance tier (0..3). */
data class BuildTagSummary(val tag: BuildTag, val points: Int, val tier: Int)

/** Element affinity, its tier (0..3) and whether it is the build's dominant element. */
data class BuildElementSummary(val element: StatusElement, val affinity: Int, val tier: Int, val dominant: Boolean)

enum class BuildRelicLinkKind { CONCORD, DISCORD, HARMONY, DISSONANCE }

/** How two bound relics interact; a named [pairing] stays hidden in presentation until [discovered]. */
data class BuildRelicLinkSummary(
    val first: RelicId,
    val second: RelicId,
    val kind: BuildRelicLinkKind,
    val pairing: RelicPairingId?,
    val discovered: Boolean,
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
