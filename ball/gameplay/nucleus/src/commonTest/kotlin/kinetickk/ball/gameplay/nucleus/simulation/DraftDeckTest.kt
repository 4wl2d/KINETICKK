// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.isDataEconomy
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.ItemHistoryWeights
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The per-run draft deck (items.md §5.1) and the history down-weights it deals with (§5.2). */
class DraftDeckTest {
    private val policy = canonicalGameplayContent.build.policy.draftDeck

    @Test
    fun theSameSeedAndProfileDealTheSameDeckAndOtherSeedsDealOthers() {
        val first = buildTestState(seed = 41).itemDeck
        val again = buildTestState(seed = 41).itemDeck
        assertEquals(first.dealtIds(), again.dealtIds())
        assertEquals(first.reserve(), again.reserve())
        val others = (42..61).map { buildTestState(seed = it).itemDeck.dealtIds() }
        assertTrue(others.all { it != first.dealtIds() }, "Twenty other seeds deal twenty other decks")
        assertFalse(first.dealsEverything)
    }

    @Test
    fun aDeckHoldsTheDataLadderAndTheCharactersArchetype() {
        for (seed in 1..20) {
            val state = buildTestState(seed = seed, shape = if (seed % 2 == 0) CoreShape.ORB else CoreShape.HEXAGON)
            val dealt = state.content.items.filter { it.id in state.itemDeck }
            fun ItemDefinition.atMostRare() = rarity.rank <= ItemRarity.RARE.rank
            assertTrue(dealt.count { it.isDataEconomy && it.atMostRare() && it.minimumRebirth == 0 } >= 3, "Seed $seed Data stratum")
            assertTrue(dealt.count { it.atMostRare() && state.coreShape.characterTag() in it.tags } >= policy.characterTagFloor)
        }
    }

    @Test
    fun theRankZeroDeckDealsAboutSixtyPercentOfEveryTier() {
        val deck = buildTestState(seed = 7).itemDeck
        val universe = canonicalGameplayContent.items.filter { it.minimumRebirth == 0 }
        assertEquals(221, universe.size)
        // Base deals 32 / 41 / 31 / 25 of 58 / 74 / 51 / 38 (the 18 actives among them, no rank-0 Legendary); floors,
        // which promote passive items only, may add a few.
        assertTrue(deck.dealtIds().size in 129..142, "Rank-0 deck size ${deck.dealtIds().size}")
    }

    @Test
    fun dealingDrawsFromItsOwnStreamSoRewardDrawsStayPut() {
        val deckContent = canonicalGameplayContent
        val noDeck = contentWith(DraftDeckPolicy.FULL)
        repeat(20) { seed ->
            val dealt = buildTestState(seed = seed, content = deckContent)
            val whole = buildTestState(seed = seed, content = noDeck)
            assertEquals(whole.rewardRandom.snapshot(), dealt.rewardRandom.snapshot(), "Seed $seed reward stream")
            assertEquals(whole.gameplayRandom.snapshot(), dealt.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(whole.spawnRandom.snapshot(), dealt.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertTrue(whole.itemDeck.dealsEverything)
            assertTrue(whole.content.items.all { it.id in whole.itemDeck })
        }
    }

    @Test
    fun offersShowOnlyDealtItemsAndARefillKeepsEveryTierOfferableWithoutADraw() {
        val state = buildTestState(seed = 11).apply {
            lifetimeMatter = 4_000L
            level = 20
        }
        repeat(200) { seed ->
            state.rewardRandom = CloneableXorWowRandom(seed)
            state.buildItemChoices()
            state.choices.forEach { assertTrue(requireNotNull(it.itemId) in state.itemDeck, "Card ${it.title} was dealt") }
        }

        // A deck holding one offerable Common: the refill deals the next offerable Commons up to the floor, no draw.
        val sparse = buildTestState(seed = 12).apply { lifetimeMatter = 4_000L; level = 20 }
        val commons = sparse.content.items.filter { it.rarity == ItemRarity.COMMON && it.minimumRebirth == 0 }
        val keep = commons.first()
        val others = sparse.content.items.filter { it.rarity != ItemRarity.COMMON && it.minimumRebirth == 0 }.map { it.id }
        sparse.itemDeck = ItemDeck.dealt(
            sparse.content.items.size,
            (commons.map { it.id } + others).toIntArray(),
            intArrayOf(commons.size, others.size, 0, 0, 0),
            listOf(keep.id) + others,
        )
        val before = sparse.itemDeck
        val stream = sparse.rewardRandom.snapshot()
        sparse.refillItemDeck()
        val newlyDealt = sparse.itemDeck.dealtIds() - before.dealtIds().toSet()
        assertEquals(policy.refillFloor - 1, newlyDealt.size)
        assertEquals(commons.drop(1).take(policy.refillFloor - 1).map { it.id }, newlyDealt.sorted())
        assertEquals(stream, sparse.rewardRandom.snapshot(), "A refill draws nothing")
        assertTrue(before.dealtIds().none { it in newlyDealt }, "The earlier deck value is unchanged")
        // A full tier needs nothing: the same deck value stays.
        val settled = sparse.itemDeck
        sparse.refillItemDeck()
        assertSame(settled, sparse.itemDeck)
    }

    @Test
    fun aDirectedItemRewardDrawsFromTheDeckAndTopsUpFromTheReserveInPriority() {
        val state = buildTestState(seed = 13)
        val eligible = state.content.items.filter { it.minimumRebirth == 0 }.take(10)
        val inDeck = eligible.filter { it.id in state.itemDeck }
        val pool = state.deckPool(eligible, 3)
        if (inDeck.size >= 3) assertEquals(inDeck, pool) else {
            assertEquals(3, pool.size)
            assertEquals(inDeck, pool.take(inDeck.size))
            val reserveOrder = state.itemDeck.reserve().filter { id -> eligible.any { it.id == id } }
            assertEquals(reserveOrder.take(3 - inDeck.size), pool.drop(inDeck.size).map { it.id })
        }
        // Items the deck left out come in deal priority.
        val outside = state.content.items.filter { it.minimumRebirth == 0 && it.id !in state.itemDeck }.take(2).map { it.id }.toSet()
        assertEquals(state.itemDeck.reserve().filter { it in outside }, state.deckPool(state.content.items.filter { it.id in outside }, 3).map { it.id })
    }

    @Test
    fun theBuildSummaryListsExactlyTheRankEligibleItemsTheDeckLeftOut() {
        val state = buildTestState(seed = 17, rebirth = 3)
        val summary = state.buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(1))
        val universe = state.content.items.filter { it.minimumRebirth <= state.rebirthRank }.map { it.id }
        assertEquals(universe.filter { it !in state.itemDeck }, summary.outOfDeckItemIds)
        assertTrue(summary.eligibleItemIds.none { it in summary.outOfDeckItemIds }, "The Codex never promises an undealt item")
        assertTrue(buildTestState(seed = 17, content = contentWith(DraftDeckPolicy.FULL))
            .buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(1)).outOfDeckItemIds.isEmpty())
    }

    @Test
    fun historyWeightsFollowRecencyAndNeverFallBelowTheFloor() {
        val count = canonicalGameplayContent.items.size
        fun weights(recent: List<Int>) =
            ItemHistoryWeights.of(recent, count, MAX_RECENT_ITEM_IDS, policy.historyPenalty, policy.historyFloor)
        assertTrue(weights(emptyList()).isEmpty)
        assertEquals(1f, weights(emptyList()).deckWeight(3))
        // Taken once in the last run (about 32 entries per run): age p weighs 1 / (1 + 0.8 × (1 − p / 64)) on the deck,
        // 0.56 (newest) to 0.71 (the run's first), and its square root, 0.75 to 0.84, on offers.
        val lastRun = (100 until 132).toList()
        val once = weights(lastRun)
        assertEquals(1f / (1f + 0.8f), once.deckWeight(131), 1e-6f)
        assertEquals(1f / (1f + 0.8f * (1f - 31f / 64f)), once.deckWeight(100), 1e-6f)
        listOf(100, 131).forEach { id ->
            assertTrue(once.deckWeight(id) in 0.55f..0.71f, "deck weight ${once.deckWeight(id)}")
            assertTrue(once.offerWeight(id) in 0.74f..0.85f, "offer weight ${once.offerWeight(id)}")
            assertEquals(kotlin.math.sqrt(once.deckWeight(id)), once.offerWeight(id))
        }
        assertTrue(once.deckWeight(131) < once.deckWeight(100), "A newer entry weighs more")
        assertEquals(1f, once.deckWeight(5))
        // Taken in each of the last two runs: about 0.53 on the deck.
        val twice = weights((100 until 132).toList() + (100 until 132).toList())
        assertTrue(twice.deckWeight(120) in 0.45f..0.56f, "twice ${twice.deckWeight(120)}")
        // Sixty-four entries of one item hit the floor; only the newest 64 entries count.
        val spam = weights(List(80) { 7 })
        assertEquals(policy.historyFloor, spam.deckWeight(7))
        assertEquals(spam.deckWeight(7), weights(List(64) { 7 }).deckWeight(7))
    }

    private fun contentWith(deck: DraftDeckPolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = deck),
        ))

    @Test
    fun theFullPolicyDealsEverythingAndTheDefaultDoesNot() {
        assertTrue(DraftDeckPolicy.FULL.dealsEverything)
        assertFalse(DraftDeckPolicy().dealsEverything)
        assertNotEquals(DraftDeckPolicy.FULL, policy)
    }
}
