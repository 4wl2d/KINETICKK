// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.impl.ProfileComponent
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The recent-item history across the Ball boundary (items.md §5.2): Gameplay reports first acquisitions, the Profile
 * appends them and keeps the newest 64, and the next run's bootstrap snapshot carries them into a draft deck dealt with
 * those items down-weighted.
 */
class RecentItemHistoryRunBootstrapTest {
    @Test
    fun acquisitionsReportedByRunsReachTheNextRunsDeck() {
        val catalog = createContentCatalog()
        val profile = createProfileComponent(HistoryPersistence(), catalog.profilePolicy())
        val content = catalog.gameplayContent()
        val fresh = snapshot(profile)
        assertTrue(fresh.recentItemIds.isEmpty())

        // Two runs' first acquisitions, 40 each: the Profile keeps the newest 64 in order.
        val first = (0 until 40).toList()
        val second = (100 until 140).toList()
        apply(profile, first)
        apply(profile, second)
        val remembered = snapshot(profile)
        assertEquals((first + second).takeLast(MAX_RECENT_ITEM_IDS), remembered.recentItemIds.toList())

        // The run starts with that history (Gameplay validates it) and deals another deck than a fresh profile does.
        val withHistory = start(content, remembered)
        val withoutHistory = start(content, remembered.copy(collection = remembered.collection.copy(recentItemIds = immutableListOf())))
        val dealtWith = GameplayNucleus.query(withHistory, GameplayQuery.GetBuildSummary).outOfDeckItemIds
        val dealtWithout = GameplayNucleus.query(withoutHistory, GameplayQuery.GetBuildSummary).outOfDeckItemIds
        assertTrue(dealtWith.isNotEmpty() && dealtWithout.isNotEmpty(), "Both runs dealt a deck")
        assertNotEquals(dealtWithout, dealtWith, "The history reweighs the deal")
        // Remembered items are left out of the deck more often than the rest.
        val rememberedIds = remembered.recentItemIds.toSet()
        val rank0 = content.items.filter { it.minimumRebirth == 0 }.map { it.id }
        fun outShare(ids: Collection<Int>, out: Collection<Int>) = ids.count { it in out }.toDouble() / ids.size
        val leftOutRemembered = (1..40).sumOf { seed ->
            outShare(rank0.filter { it in rememberedIds }, GameplayNucleus.query(start(content, remembered, seed), GameplayQuery.GetBuildSummary).outOfDeckItemIds)
        }
        val leftOutOthers = (1..40).sumOf { seed ->
            outShare(rank0.filter { it !in rememberedIds }, GameplayNucleus.query(start(content, remembered, seed), GameplayQuery.GetBuildSummary).outOfDeckItemIds)
        }
        assertTrue(leftOutRemembered > leftOutOthers, "Remembered items left out $leftOutRemembered against $leftOutOthers")
    }

    private fun snapshot(profile: ProfileComponent): GameplayProfileSnapshot =
        assertIs<ProfileRunBootstrapResult.Ready>(profile.query(ProfileQuery.GetRunBootstrap).result).snapshot

    private fun start(
        content: kinetickk.ball.content.api.GameplayContentSnapshot,
        profile: GameplayProfileSnapshot,
        seed: Int = 11,
    ): GameplayState = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(
        GameplayState.initial(RunId(1), content),
        GameplayNucleusPulse.StartRun,
        GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(content, profile, seed = seed))),
    )).frame.nextState

    /** Applies a run's report of [acquired] items through the Profile's accepted gameplay-progress path. */
    private fun apply(profile: ProfileComponent, acquired: List<Int>) {
        val call = InlineAcceptance(BoundedCompletionDeque<Boolean>(1))
        var applied = false
        call.dispatch {
            call.acceptAndDrain(rootItem = false, rootFrame = immutableListOf(Unit),
                outputs = { it }, acceptFrame = { _, _ -> },
                decideCompletion = { accepted -> applied = accepted; immutableListOf<Unit>() },
                execute = { _, _ -> call.call(
                    invoke = { reply: InlineReply<ProfileProgressApplied, ProfileRefusal> ->
                        profile.applyGameplayProgress(GameplayProgressUpdate(acquiredItemIds = acquired.toImmutableList()), reply)
                    },
                    acceptedInput = { true }, refusedInput = { false },
                ) })
        }
        assertTrue(applied, "Profile appended the acquisitions")
    }
}

private class HistoryPersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}
