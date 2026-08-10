// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileMutationRejection
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileNucleusTest {
    @Test
    fun labPurchaseIsDeterministicAndEmitsTheAcceptedSnapshotForPersistence() {
        val id = MetaUpgradeId.CORE_INTEGRITY
        val initial = PlayerProfile(economy = PlayerEconomy(matter = 1_000L, lifetimeMatter = 1_000L))
        val pulse = ProfilePulse.PurchaseMetaUpgrade(id)

        val first = ProfileNucleus.decide(initial, TestProfilePolicy, pulse)
        val second = ProfileNucleus.decide(initial, TestProfilePolicy, pulse)

        assertEquals(first, second)
        val decision = assertIs<ProfileDecisionResult.Accepted>(first).decision
        val cost = TestProfilePolicy.metaUpgrade(id).cost(0).toLong()
        assertEquals(1_000L - cost, decision.nextState.economy.matter)
        assertEquals(1, decision.nextState.labProgress.rank(id))
        assertEquals(1_000L, initial.economy.matter)
        assertEquals(0, initial.labProgress.rank(id))
        assertEquals(initial.preferences, decision.nextState.preferences)
        assertEquals(initial.loadout, decision.nextState.loadout)
        assertEquals(initial.collection, decision.nextState.collection)
        assertEquals(initial.rebirthProgress, decision.nextState.rebirthProgress)

        val effect = assertIs<ProfileEffect.PersistSnapshot>(decision.outputs.single())
        assertEquals(decision.nextState, effect.profile)
    }

    @Test
    fun labBusinessRejectionsProduceNoAcceptedDecision() {
        val id = MetaUpgradeId.CORE_INTEGRITY
        val insufficient = ProfileNucleus.decide(
            PlayerProfile(economy = PlayerEconomy()),
            TestProfilePolicy,
            ProfilePulse.PurchaseMetaUpgrade(id),
        )
        assertEquals(
            ProfileMutationRejection.INSUFFICIENT_MATTER,
            assertIs<ProfileDecisionResult.Rejected>(insufficient).reason,
        )

        val maxRank = TestProfilePolicy.metaUpgrade(id).maxRanks
        val maxed = ProfileNucleus.decide(
            PlayerProfile(
                economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE),
                labProgress = LabProgress(List(MetaUpgradeId.entries.size) { candidate ->
                    if (candidate == id.ordinal) maxRank else 0
                }),
            ),
            TestProfilePolicy,
            ProfilePulse.PurchaseMetaUpgrade(id),
        )
        assertEquals(
            ProfileMutationRejection.MAX_RANK_REACHED,
            assertIs<ProfileDecisionResult.Rejected>(maxed).reason,
        )
    }

    @Test
    fun labDecisionUsesTheCapturedPolicyRatherThanCanonicalConstants() {
        val id = MetaUpgradeId.CORE_INTEGRITY
        val customPolicy = TestProfilePolicy.copy(
            metaUpgrades = TestProfilePolicy.metaUpgrades.map { definition ->
                if (definition.id == id) definition.copy(maxRanks = 1, baseCost = 7) else definition
            }.toImmutableList(),
        )
        val initial = PlayerProfile(economy = PlayerEconomy(10L, 10L))

        val accepted = assertIs<ProfileDecisionResult.Accepted>(
            ProfileNucleus.decide(initial, customPolicy, ProfilePulse.PurchaseMetaUpgrade(id)),
        )
        assertEquals(3L, accepted.decision.nextState.economy.matter)

        val maxed = ProfileNucleus.decide(
            accepted.decision.nextState,
            customPolicy,
            ProfilePulse.PurchaseMetaUpgrade(id),
        )
        assertEquals(
            ProfileMutationRejection.MAX_RANK_REACHED,
            assertIs<ProfileDecisionResult.Rejected>(maxed).reason,
        )
    }
}
