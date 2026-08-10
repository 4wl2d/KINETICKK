// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileMutationRejection

/** Closed mutation cause for the first Profile Ball pilot. */
sealed interface ProfilePulse {
    data class PurchaseMetaUpgrade(val id: MetaUpgradeId) : ProfilePulse
}

sealed interface ProfileDecisionResult {
    data class Accepted(val decision: ProfileDecision) : ProfileDecisionResult
    data class Rejected(val reason: ProfileMutationRejection) : ProfileDecisionResult
}

data class ProfileDecision(
    val nextState: PlayerProfile,
    val outputs: ImmutableList<ProfileEffect>,
)

sealed interface ProfileEffect {
    data class PersistSnapshot(val profile: PlayerProfile) : ProfileEffect
}

/** Pure business-decision authority for the bounded Lab purchase pilot. */
object ProfileNucleus {
    fun decide(
        state: PlayerProfile,
        policy: ProfilePolicySnapshot,
        pulse: ProfilePulse,
    ): ProfileDecisionResult = when (pulse) {
        is ProfilePulse.PurchaseMetaUpgrade -> purchaseMetaUpgrade(state, policy, pulse.id)
    }

    private fun purchaseMetaUpgrade(
        state: PlayerProfile,
        policy: ProfilePolicySnapshot,
        id: MetaUpgradeId,
    ): ProfileDecisionResult {
        val definition = policy.metaUpgrade(id)
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
