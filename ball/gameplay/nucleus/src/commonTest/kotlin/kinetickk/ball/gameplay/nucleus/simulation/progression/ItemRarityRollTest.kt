// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRarityPolicy
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeded offers per statistical check, few enough for the browser runner's per-test limit. */
private const val ROLLS = 1_500

/**
 * The item card's rarity (decisions D4): Luck lifts the rarer tiers by more and saturates, every Epic and Legendary the
 * run owns makes the next one rarer whatever its source, the volatile card never lifts into Legendary, a reroll keeps
 * every card's rarity, directed rewards and floor offers roll like any card, and a run keeps its dealt Legendary hand.
 */
class ItemRarityRollTest {
    private val policy: ItemRarityPolicy = canonicalGameplayContent.build.policy.rarity

    @Test
    fun luckLiftsTheRarerTiersByMoreAndSaturates() {
        fun gain(rarity: ItemRarity, luck: Float) = policy.weight(rarity, luck, owned = 0) / policy.weight(rarity, 0f, owned = 0)
        val rare = gain(ItemRarity.RARE, 2f)
        val epic = gain(ItemRarity.EPIC, 2f)
        val legendary = gain(ItemRarity.LEGENDARY, 2f)
        assertTrue(rare > 1f && epic > rare && legendary > rare, "Luck 2 lifts Rare $rare, Epic $epic, Legendary $legendary")
        assertEquals(1f, gain(ItemRarity.UNCOMMON, 2f), "Luck leaves Uncommon alone; Common takes the rest")
        // Each later point adds less, and nothing past the ceiling.
        assertTrue(gain(ItemRarity.LEGENDARY, 1f) - 1f > gain(ItemRarity.LEGENDARY, 2f) - gain(ItemRarity.LEGENDARY, 1f))
        assertEquals(gain(ItemRarity.LEGENDARY, policy.luckCeiling), gain(ItemRarity.LEGENDARY, policy.luckCeiling + 3f))
        // At the strongest Luck the rarer tiers still leave Common most of the cards.
        val state = buildTestState(rebirth = 10).apply { luck = 10f }
        assertTrue(state.itemRarityOdds(ItemRarity.COMMON) > 0.3f)
    }

    @Test
    fun everyOwnedEpicAndLegendaryMakesTheNextOneRarer() {
        val state = buildTestState(rebirth = 10).dealWholeCatalog()
        val legendary = state.content.items.first { it.rarity == ItemRarity.LEGENDARY && !it.isActive }
        val epic = state.content.items.first { it.rarity == ItemRarity.EPIC && !it.isActive && !it.risk }
        val legendaryOdds = state.itemRarityOdds(ItemRarity.LEGENDARY)
        val epicOdds = state.itemRarityOdds(ItemRarity.EPIC)
        val commonOdds = state.itemRarityOdds(ItemRarity.COMMON)
        state.acquireItem(legendary.id)
        assertClose(legendaryOdds * policy.ownedDecay[ItemRarity.LEGENDARY.ordinal], state.itemRarityOdds(ItemRarity.LEGENDARY))
        assertClose(epicOdds, state.itemRarityOdds(ItemRarity.EPIC))
        state.acquireItem(epic.id)
        assertClose(epicOdds * policy.ownedDecay[ItemRarity.EPIC.ordinal], state.itemRarityOdds(ItemRarity.EPIC))
        assertTrue(state.itemRarityOdds(ItemRarity.COMMON) > commonOdds, "Common takes what the owned tiers lost")
        // A second stack of an owned item is not another item of its tier.
        val stackable = state.content.items.first { it.rarity == ItemRarity.EPIC && it.maxStacks > 1 }
        state.acquireItem(stackable.id)
        val afterOne = state.itemRarityOdds(ItemRarity.EPIC)
        state.acquireItem(stackable.id)
        assertEquals(afterOne, state.itemRarityOdds(ItemRarity.EPIC))
    }

    @Test
    fun cardRaritiesFollowTheOdds() {
        // Rebirth 10 with the whole catalog: every tier holds items, so no card falls to another tier. Cards 0 and 1 roll
        // the plain odds (the volatile card lifts).
        val state = buildTestState(rebirth = 10).dealWholeCatalog().apply {
            level = 30
            lifetimeMatter = 4_000L
        }
        val counts = IntArray(ItemRarity.entries.size)
        var cards = 0
        repeat(ROLLS) { seed ->
            state.rewardRandom = CloneableXorWowRandom(seed)
            state.buildItemChoices()
            state.choices.take(2).forEach { counts[state.content.items[requireNotNull(it.itemId)].rarity.ordinal]++ }
            cards += 2
        }
        for (rarity in ItemRarity.entries) {
            val odds = state.itemRarityOdds(rarity).toDouble()
            val share = counts[rarity.ordinal].toDouble() / cards
            val tolerance = 4.5 * sqrt(odds * (1 - odds) / cards) + 0.002
            assertTrue(abs(share - odds) <= tolerance, "$rarity cards $share against the odds $odds")
        }
    }

    @Test
    fun theVolatileCardNeverLiftsIntoLegendaryAndLiftsRareIntoEpicLessOften() {
        val state = buildTestState(rebirth = 10)
        val chance = state.content.build.policy.offerLanes.volatileLiftChance
        assertEquals(ItemRarity.EPIC, state.volatileLift(ItemRarity.EPIC, 0f), "An Epic never lifts into Legendary")
        assertEquals(ItemRarity.LEGENDARY, state.volatileLift(ItemRarity.LEGENDARY, 0f))
        assertEquals(ItemRarity.UNCOMMON, state.volatileLift(ItemRarity.COMMON, chance * 0.99f))
        assertEquals(ItemRarity.RARE, state.volatileLift(ItemRarity.UNCOMMON, chance * 0.99f))
        val rareShare = policy.liftShares[ItemRarity.RARE.ordinal]
        assertTrue(rareShare < 1f, "Rare lifts into Epic less often than the lower tiers lift")
        assertEquals(ItemRarity.EPIC, state.volatileLift(ItemRarity.RARE, chance * rareShare * 0.99f))
        assertEquals(ItemRarity.RARE, state.volatileLift(ItemRarity.RARE, chance * rareShare * 1.01f))
    }

    @Test
    fun aRerollKeepsEveryCardsRarityAndCannotFishForALegendary() {
        var changedItems = 0
        val state = buildTestState(rebirth = 10).apply {
            level = 30
            lifetimeMatter = 4_000L
        }
        repeat(80) { seed ->
            state.rewardRandom = CloneableXorWowRandom(seed)
            state.rerollsRemaining = 6
            state.openItemChoice()
            val rarities = state.cardRarities()
            val items = state.choices.map { it.itemId }
            repeat(6) {
                state.rerollChoices()
                assertEquals(rarities, state.cardRarities(), "A reroll keeps the rarity of every card (seed $seed)")
            }
            if (state.choices.map { it.itemId } != items) changedItems++
        }
        assertTrue(changedItems > 60, "A reroll still draws new items inside the rarities: $changedItems of 80 changed")
    }

    @Test
    fun aFloorOfferKeepsItsFloorThroughRerollsAndItsLegendaryCountsTowardTheDecay() {
        val state = buildTestState(rebirth = 10).apply {
            level = 30
            lifetimeMatter = 4_000L
            rerollsRemaining = 3
        }
        state.openItemOfferWithFloor(ItemRarity.EPIC)
        assertTrue(state.cardRarities().all { it.rank >= ItemRarity.EPIC.rank })
        val rarities = state.cardRarities()
        repeat(3) {
            state.rerollChoices()
            assertEquals(rarities, state.cardRarities())
        }
        state.choose(0)
        val legendaryOdds = state.itemRarityOdds(ItemRarity.LEGENDARY)
        state.openItemOfferWithFloor(ItemRarity.LEGENDARY)
        assertTrue(state.cardRarities().all { it == ItemRarity.LEGENDARY }, "A floor stays a floor")
        state.choose(0)
        assertClose(legendaryOdds * policy.ownedDecay[ItemRarity.LEGENDARY.ordinal], state.itemRarityOdds(ItemRarity.LEGENDARY))
    }

    @Test
    fun directedItemRewardsRollTheirRarityLikeAnyCard() {
        // The old uniform shuffle showed a Legendary in proportion to the focus pool (about 1 card in 20 at Rebirth 10);
        // a rolled card shows one at the Legendary odds.
        val state = buildTestState(rebirth = 10).apply {
            level = 30
            lifetimeMatter = 4_000L
        }
        fun legendaryShare(run: MutableGameState): Double {
            var cards = 0
            var legendaries = 0
            repeat(ROLLS / 3) { seed ->
                val fork = run.deepCopy()
                fork.rewardRandom = CloneableXorWowRandom(seed)
                fork.openDirectedReward(DirectedReward.ITEM_AND_REPAIR)
                fork.selectRewardFocus(RewardFocus.OFFENSE)
                if (fork.choiceType != ChoiceType.ITEM) return@repeat
                fork.choices.forEach { option ->
                    cards++
                    if (fork.content.items[requireNotNull(option.itemId)].rarity == ItemRarity.LEGENDARY) legendaries++
                }
            }
            return legendaries.toDouble() / cards
        }
        val odds = state.itemRarityOdds(ItemRarity.LEGENDARY).toDouble()
        val fresh = legendaryShare(state)
        assertTrue(fresh <= 2.0 * odds + 0.004, "Directed Legendary cards $fresh against the odds $odds")
        // Owning Legendaries makes a directed Legendary rarer too.
        state.content.items.filter { it.rarity == ItemRarity.LEGENDARY && it.id in state.itemDeck }.take(3).forEach { state.acquireItem(it.id) }
        assertTrue(legendaryShare(state) < fresh, "Owned Legendaries lower directed Legendary cards")
    }

    @Test
    fun aRunKeepsTheLegendariesItsDeckDealt() {
        // Rebirth 2 holds three Legendaries and the deck deals two: with both taken, neither the refill nor a directed
        // pool deals the third from the reserve, so no run collects the whole tier.
        val state = buildTestState(rebirth = 2).apply {
            level = 30
            lifetimeMatter = 4_000L
        }
        val legendaries = state.content.items.filter { it.rarity == ItemRarity.LEGENDARY && it.minimumRebirth <= 2 }
        val dealt = legendaries.filter { it.id in state.itemDeck }
        assertEquals(3, legendaries.size)
        assertEquals(2, dealt.size)
        dealt.forEach { state.acquireItem(it.id) }
        state.refillItemDeck()
        assertEquals(dealt.map { it.id }, legendaries.filter { it.id in state.itemDeck }.map { it.id })
        val reserve = legendaries.single { it !in dealt }
        assertTrue(reserve !in state.deckPool(listOf(reserve), size = 3), "A directed pool never tops up a Legendary")
        // A lower tier still refills from its reserve.
        val epics = state.content.items.filter { it.rarity == ItemRarity.EPIC && it.minimumRebirth <= 2 }
        assertTrue(epics.any { it.id !in state.itemDeck })
        val reserveEpic = epics.first { it.id !in state.itemDeck && state.offerableOutsideDeck(it) }
        assertTrue(reserveEpic in state.deckPool(listOf(reserveEpic), size = 1))
    }

    private fun MutableGameState.cardRarities(): List<ItemRarity> = choices.map { content.items[requireNotNull(it.itemId)].rarity }

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) <= 1e-6f, "$actual is not $expected")
}
