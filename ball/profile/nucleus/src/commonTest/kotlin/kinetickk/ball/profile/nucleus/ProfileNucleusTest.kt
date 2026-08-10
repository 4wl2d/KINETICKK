// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MetaUpgradeCatalog
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileMutationRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileNucleusTest {
    @Test
    fun labPurchaseIsDeterministicAndEmitsTheAcceptedSnapshotForPersistence() {
        val id = MetaUpgradeId.CORE_INTEGRITY
        val initial = PlayerProfile(economy = PlayerEconomy(matter = 1_000L, lifetimeMatter = 1_000L))
        val pulse = ProfilePulse.PurchaseMetaUpgrade(id)

        val first = ProfileNucleus.decide(initial, pulse)
        val second = ProfileNucleus.decide(initial, pulse)

        assertEquals(first, second)
        val decision = assertIs<ProfileDecisionResult.Accepted>(first).decision
        val cost = MetaUpgradeCatalog.byId(id).cost(0).toLong()
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
            ProfilePulse.PurchaseMetaUpgrade(id),
        )
        assertEquals(
            ProfileMutationRejection.INSUFFICIENT_MATTER,
            assertIs<ProfileDecisionResult.Rejected>(insufficient).reason,
        )

        val maxRank = MetaUpgradeCatalog.byId(id).maxRanks
        val maxed = ProfileNucleus.decide(
            PlayerProfile(
                economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE),
                labProgress = LabProgress(List(MetaUpgradeId.entries.size) { candidate ->
                    if (candidate == id.ordinal) maxRank else 0
                }),
            ),
            ProfilePulse.PurchaseMetaUpgrade(id),
        )
        assertEquals(
            ProfileMutationRejection.MAX_RANK_REACHED,
            assertIs<ProfileDecisionResult.Rejected>(maxed).reason,
        )
    }
}
