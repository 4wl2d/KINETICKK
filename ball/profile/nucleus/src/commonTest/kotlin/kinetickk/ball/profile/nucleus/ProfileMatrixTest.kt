// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ProfileMatrixTest {
    @Test
    fun purchaseChargesTheListedRankPriceAtTheExactBalanceAndRejectsOneMatterShort() {
        val cost = TestProfilePolicy.matrixNode(MatrixNodeId.IMPACT_DRIVER).cost(0).toLong()

        val exact = purchase(state(matter = cost), MatrixNodeId.IMPACT_DRIVER).acceptedProfile()
        assertEquals(1, exact.matrix.rank(MatrixNodeId.IMPACT_DRIVER))
        assertEquals(0L, exact.economy.matter)
        assertEquals(cost, exact.economy.lifetimeMatter)

        val short = state(matter = cost - 1L)
        assertEquals(ProfileRejection.InsufficientMatter, purchase(short, MatrixNodeId.IMPACT_DRIVER).rejection())
    }

    @Test
    fun theLastRankIsAcceptedAndTheNextOneIsRejectedAtMaximumRank() {
        val node = TestProfilePolicy.matrixNode(MatrixNodeId.REINFORCED_HULL)
        val nearlyFull = state(matter = 10_000L, ranks = mapOf(MatrixNodeId.REINFORCED_HULL to node.maxRanks - 1))

        val full = purchase(nearlyFull, MatrixNodeId.REINFORCED_HULL).acceptedProfile()
        assertEquals(node.maxRanks, full.matrix.rank(MatrixNodeId.REINFORCED_HULL))
        assertEquals(10_000L - node.costs.last(), full.economy.matter)

        val maxed = state(matter = 10_000L, ranks = mapOf(MatrixNodeId.REINFORCED_HULL to node.maxRanks))
        assertEquals(ProfileRejection.MatrixNodeMaxRank, purchase(maxed, MatrixNodeId.REINFORCED_HULL).rejection())
    }

    @Test
    fun anyOneOwnedPrerequisiteOpensANodeAndNoneRejectsIt() {
        val rich = state(matter = 10_000L)
        assertEquals(ProfileRejection.MatrixPrerequisiteMissing, purchase(rich, MatrixNodeId.CRITICAL_LATTICE).rejection())
        assertEquals(ProfileRejection.MatrixPrerequisiteMissing, purchase(rich, MatrixNodeId.CATALYST_CORE).rejection())

        // Catalyst Core needs Critical Lattice or Armed Start: either one alone is enough.
        val viaArmedStart = state(matter = 10_000L, ranks = mapOf(MatrixNodeId.ARMED_START to 1))
        assertEquals(1, purchase(viaArmedStart, MatrixNodeId.CATALYST_CORE).acceptedProfile().matrix.rank(MatrixNodeId.CATALYST_CORE))
        val viaLattice = state(matter = 10_000L, ranks = mapOf(MatrixNodeId.CRITICAL_LATTICE to 1))
        assertEquals(1, purchase(viaLattice, MatrixNodeId.CATALYST_CORE).acceptedProfile().matrix.rank(MatrixNodeId.CATALYST_CORE))
    }

    @Test
    fun aRebirthGateOpensAtExactlyItsRankAndRejectsTheRankBelow() {
        val node = TestProfilePolicy.matrixNode(MatrixNodeId.OVERDRIVE_COIL)
        assertEquals(1, node.minimumRebirth)
        val ranks = mapOf(MatrixNodeId.ARMED_START to 1)

        // Rank = highest cleared tier + 1: nothing cleared is rank 0, one clear is rank 1.
        val rankZero = state(matter = 10_000L, ranks = ranks, rebirth = RebirthProgress(level = 0, highestCleared = -1))
        assertEquals(ProfileRejection.MatrixRebirthLocked, purchase(rankZero, MatrixNodeId.OVERDRIVE_COIL).rejection())

        val rankOne = state(matter = 10_000L, ranks = ranks, rebirth = RebirthProgress(level = 0, highestCleared = 0))
        assertEquals(1, purchase(rankOne, MatrixNodeId.OVERDRIVE_COIL).acceptedProfile().matrix.rank(MatrixNodeId.OVERDRIVE_COIL))
    }

    @Test
    fun aRejectedPurchaseLeavesStateRevisionAndOutputsUntouched() {
        val state = state(matter = 10L)
        val decision = purchase(state, MatrixNodeId.PROSPECTOR)

        assertIs<ProfileDecision.Rejected>(decision)
        assertEquals(TestProfilePolicy.matrixNode(MatrixNodeId.PROSPECTOR).id, MatrixNodeId.PROSPECTOR)
        assertEquals(ProfileRevision(1L), state.revision)
        assertEquals(10L, state.profile.economy.matter)
    }

    @Test
    fun resetRefundsEveryOwnedRankAtFullPriceAndClearsTheMatrix() {
        val owned = mapOf(
            MatrixNodeId.IMPACT_DRIVER to 3,
            MatrixNodeId.CRITICAL_LATTICE to 1,
            MatrixNodeId.SALVAGE_PROTOCOL to 2,
        )
        val spent = 150L + 400L + 900L + 600L + 150L + 400L
        val state = state(matter = 7L, lifetime = 7L, ranks = owned)

        val frame = ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.ResetMatrix)).acceptedFrame()
        val reset = frame.nextState.profile
        assertEquals(MatrixProgress(), reset.matrix)
        assertEquals(7L + spent, reset.economy.matter)
        assertEquals(7L + spent, reset.economy.lifetimeMatter)
        assertIs<ProfilePersistenceStatus.Pending>(frame.nextState.persistence)
        assertEquals(1, frame.outputs.size)

        assertEquals(ProfileRejection.NoChange, ProfileNucleus.decide(
            state(matter = 7L), ProfileNucleusPulse.Intent(ProfilePulse.ResetMatrix),
        ).rejection())
    }

    @Test
    fun loadingKeepsOwnedNodesWhoseGatesOrPrerequisitesAreNoLongerMet() {
        // Glass Engine without Catalyst Core and at Rebirth rank 0: a rebalance must never wipe it.
        val profile = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(matter = 5L, lifetimeMatter = 9_000L),
            matrix = matrix(mapOf(MatrixNodeId.GLASS_ENGINE to 1, MatrixNodeId.GRAVITY_SURFER to 1)),
        )

        val loaded = loaded(profile)

        assertEquals(profile, loaded.profile)
        assertEquals(ProfileRevision(4L), loaded.revision)
    }

    @Test
    fun loadingClampsRanksAboveALoweredMaximumAndRefundsTheExcessAtTheLastPrice() {
        val lowered = TestProfilePolicy.copy(
            matrixNodes = TestProfilePolicy.matrixNodes.map { node ->
                if (node.id != MatrixNodeId.IMPACT_DRIVER) node
                else node.copy(maxRanks = 1, costs = node.costs.take(1).toImmutableList())
            }.toImmutableList(),
        )
        val profile = defaultPlayerProfile(lowered).copy(
            economy = PlayerEconomy(matter = 100L, lifetimeMatter = 100L),
            matrix = matrix(mapOf(MatrixNodeId.IMPACT_DRIVER to 3, MatrixNodeId.DATA_ARCHIVE to 2)),
        )

        val loaded = loaded(profile, lowered)

        assertEquals(1, loaded.profile.matrix.rank(MatrixNodeId.IMPACT_DRIVER))
        assertEquals(2, loaded.profile.matrix.rank(MatrixNodeId.DATA_ARCHIVE))
        // Two excess ranks at the one remaining price of 150.
        assertEquals(100L + 2L * 150L, loaded.profile.economy.matter)
        assertEquals(400L, loaded.profile.economy.lifetimeMatter)
    }

    @Test
    fun achievementCountersAccumulateAcrossRuns() {
        var state = state(matter = 0L)
        repeat(2) {
            val frame = ProfileNucleus.decide(
                state,
                ProfileNucleusPulse.ApplyGameplayProgress(GameplayProgressUpdate(totalKills = 40, ricochets = 3, dataPickups = 120)),
            ).acceptedFrame()
            state = frame.nextState.copy(persistence = ProfilePersistenceStatus.Persisted(frame.nextState.revision))
        }
        val achievements = state.profile.characterAchievements
        assertEquals(80L, achievements.totalKills)
        assertEquals(6L, achievements.ricochets)
        assertEquals(240L, achievements.dataPickups)
        assertSame(state.profile.matrix, state.profile.matrix)
    }

    private fun matrix(ranks: Map<MatrixNodeId, Int>): MatrixProgress =
        MatrixProgress(MatrixNodeId.entries.map { ranks[it] ?: 0 })

    private fun state(
        matter: Long,
        lifetime: Long = matter,
        ranks: Map<MatrixNodeId, Int> = emptyMap(),
        rebirth: RebirthProgress = RebirthProgress(),
    ): ProfileState {
        val initial = ProfileState.initial(TestProfilePolicy, ProfileSnapshotReadResult.Observed(null))
        return initial.copy(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                economy = PlayerEconomy(matter = matter, lifetimeMatter = lifetime),
                matrix = matrix(ranks),
                rebirthProgress = rebirth,
            ),
        )
    }

    private fun loaded(profile: PlayerProfile, policy: ProfilePolicySnapshot = TestProfilePolicy): ProfileState =
        ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(ProfileSnapshot(ProfileRevision(3L), profile)))

    private fun purchase(state: ProfileState, id: MatrixNodeId): ProfileDecision =
        ProfileNucleus.decide(state, ProfileNucleusPulse.Intent(ProfilePulse.PurchaseMatrixNode(id)))

    private fun ProfileDecision.acceptedFrame(): ProfileAcceptedFrame = assertIs<ProfileDecision.Accepted>(this).frame

    private fun ProfileDecision.acceptedProfile(): PlayerProfile = acceptedFrame().nextState.profile

    private fun ProfileDecision.rejection(): ProfileRejection = assertIs<ProfileDecision.Rejected>(this).reason
}
