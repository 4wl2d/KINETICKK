// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.immutableListOf
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileMutationRejection

/** Closed mutation cause for the first Profile Ball pilot. */
internal sealed interface ProfilePulse {
    data class PurchaseMetaUpgrade(val id: MetaUpgradeId) : ProfilePulse
}

internal sealed interface ProfileDecisionResult {
    data class Accepted(val decision: ProfileDecision) : ProfileDecisionResult
    data class Rejected(val reason: ProfileMutationRejection) : ProfileDecisionResult
}

internal data class ProfileDecision(
    val nextState: PlayerProfile,
    val outputs: ImmutableList<ProfileEffect>,
)

internal sealed interface ProfileEffect {
    data class PersistSnapshot(val profile: PlayerProfile) : ProfileEffect
}

/** Pure business-decision authority for the bounded Lab purchase pilot. */
internal object ProfileNucleus {
    fun decide(state: PlayerProfile, pulse: ProfilePulse): ProfileDecisionResult = when (pulse) {
        is ProfilePulse.PurchaseMetaUpgrade -> purchaseMetaUpgrade(state, pulse.id)
    }

    private fun purchaseMetaUpgrade(
        state: PlayerProfile,
        id: MetaUpgradeId,
    ): ProfileDecisionResult {
        val definition = MetaUpgradeCatalog.byId(id)
        val currentRank = state.labProgress.rank(id)
        if (currentRank >= definition.maxRanks) {
            return rejected(ProfileMutationRejection.MAX_RANK_REACHED)
        }
        val cost = definition.cost(currentRank).toLong()
        if (state.economy.matter < cost) {
            return rejected(ProfileMutationRejection.INSUFFICIENT_MATTER)
        }

        val ranks = state.labProgress.ranks.toMutableList()
        ranks[id.ordinal] = currentRank + 1
        return accepted(
            state.copy(
                economy = state.economy.copy(matter = state.economy.matter - cost),
                labProgress = LabProgress(ranks),
            ),
        )
    }

    private fun accepted(nextState: PlayerProfile): ProfileDecisionResult.Accepted =
        ProfileDecisionResult.Accepted(
            ProfileDecision(
                nextState = nextState,
                outputs = immutableListOf(ProfileEffect.PersistSnapshot(nextState)),
            ),
        )

    private fun rejected(reason: ProfileMutationRejection): ProfileDecisionResult.Rejected =
        ProfileDecisionResult.Rejected(reason)
}
