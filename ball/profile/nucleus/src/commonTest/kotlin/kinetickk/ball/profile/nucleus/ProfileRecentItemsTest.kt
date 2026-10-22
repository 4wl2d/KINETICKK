// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileRecentItemsTest {
    @Test
    fun acquisitionsAppendInOrderWithRepeatsAndKeepOnlyTheNewestSixtyFour() {
        val first = applied(state(recent = listOf(5, 9)), GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(9, 2)))
        assertEquals(listOf(5, 9, 9, 2), first.profile.collection.recentItemIds)

        // 60 remembered + 6 acquired: the two oldest fall out.
        val remembered = (0 until 60).toList()
        val trimmed = applied(state(recent = remembered), GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(70, 71, 72, 73, 74, 75)))
        assertEquals(remembered.drop(2) + listOf(70, 71, 72, 73, 74, 75), trimmed.profile.collection.recentItemIds)
        assertEquals(MAX_RECENT_ITEM_IDS, trimmed.profile.collection.recentItemIds.size)

        // An update without acquisitions leaves the history as it was.
        val untouched = applied(state(recent = listOf(5, 9)), GameplayProgressUpdate(bankedMatter = 3L))
        assertEquals(listOf(5, 9), untouched.profile.collection.recentItemIds)
    }

    @Test
    fun sixtyFourAcquisitionsAreAcceptedAndSixtyFiveRejectWithoutChangingTheProfile() {
        val before = state(recent = listOf(1, 2, 3))
        val atBound = List(MAX_RECENT_ITEM_IDS) { it % TestProfilePolicy.itemCount }

        val accepted = applied(before, GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = atBound))
        assertEquals(atBound, accepted.profile.collection.recentItemIds)

        val overflow = GameplayProgressUpdate(bankedMatter = 5L, discoveredItemIds = emptySet(), acquiredItemIds = atBound + 7)
        assertEquals(
            ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.TooManyAcquiredItems),
            assertIs<ProfileDecision.Rejected>(decide(before, overflow)).reason,
        )
        assertEquals(listOf(1, 2, 3), before.profile.collection.recentItemIds)
        assertEquals(PlayerEconomy(), before.profile.economy)
    }

    @Test
    fun anAcquisitionOutsideTheCapturedCatalogRejectsTheWholeUpdate() {
        val before = state(recent = listOf(1))
        val itemCount = TestProfilePolicy.itemCount
        for (unknown in listOf(-1, itemCount)) {
            val update = GameplayProgressUpdate(
                bankedMatter = 5L,
                discoveredItemIds = setOf(0),
                acquiredItemIds = listOf(0, unknown),
            )
            assertEquals(
                ProfileRejection.InvalidGameplayProgress(ProfileGameplayProgressRejection.UnknownItem(unknown)),
                assertIs<ProfileDecision.Rejected>(decide(before, update)).reason,
            )
        }
        assertEquals(listOf(itemCount - 1), applied(state(), GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(itemCount - 1)))
            .profile.collection.recentItemIds)
    }

    @Test
    fun theRunBootstrapCarriesTheHistoryAndLoadingForgetsRetiredIds() {
        val settled = applied(state(), GameplayProgressUpdate(discoveredItemIds = emptySet(), acquiredItemIds = listOf(4, 8, 4)))
        val bootstrap = assertIs<ProfileRunBootstrapResult.Ready>(ProfileNucleus.query(settled, ProfileQuery.GetRunBootstrap).result)
        assertEquals(listOf(4, 8, 4), bootstrap.snapshot.recentItemIds)

        // The catalog shrank to 10 items: ids 12 and 40 are retired, every other entry keeps its place.
        val shrunk = TestProfilePolicy.copy(itemCount = 10)
        val profile = defaultPlayerProfile(shrunk).copy(
            economy = PlayerEconomy(7L, 7L),
            collection = PlayerCollection(setOf(1, 4), recentItemIds = listOf(4, 12, 1, 40, 4)),
        )
        val loaded = loaded(profile, shrunk)
        assertEquals(listOf(4, 1, 4), loaded.profile.collection.recentItemIds)
        assertEquals(PlayerEconomy(7L, 7L), loaded.profile.economy)
        assertEquals(ProfileRevision(4L), loaded.revision)

        // More history than any update could build is not a profile this version wrote.
        val oversized = defaultPlayerProfile(TestProfilePolicy).copy(
            economy = PlayerEconomy(7L, 7L),
            collection = PlayerCollection(emptySet(), recentItemIds = List(MAX_RECENT_ITEM_IDS + 1) { 0 }),
        )
        assertEquals(defaultPlayerProfile(TestProfilePolicy), loaded(oversized).profile)
        val atBound = oversized.copy(collection = PlayerCollection(emptySet(), recentItemIds = List(MAX_RECENT_ITEM_IDS) { 0 }))
        assertEquals(atBound, loaded(atBound).profile)
    }

    private fun state(recent: List<Int> = emptyList()): ProfileState {
        val initial = ProfileState.initial(TestProfilePolicy, ProfileSnapshotReadResult.Observed(null))
        return initial.copy(
            profile = defaultPlayerProfile(TestProfilePolicy).copy(
                collection = PlayerCollection(recentItemIds = recent.toImmutableList()),
            ),
        )
    }

    private fun loaded(profile: PlayerProfile, policy: kinetickk.ball.content.api.ProfilePolicySnapshot = TestProfilePolicy) =
        ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(ProfileSnapshot(ProfileRevision(3L), profile)))

    private fun decide(state: ProfileState, update: GameplayProgressUpdate): ProfileDecision =
        ProfileNucleus.decide(state, ProfileNucleusPulse.ApplyGameplayProgress(update))

    private fun applied(state: ProfileState, update: GameplayProgressUpdate): ProfileState {
        val frame = assertIs<ProfileDecision.Accepted>(decide(state, update)).frame
        return frame.nextState.copy(persistence = ProfilePersistenceStatus.Persisted(frame.nextState.revision))
    }
}
