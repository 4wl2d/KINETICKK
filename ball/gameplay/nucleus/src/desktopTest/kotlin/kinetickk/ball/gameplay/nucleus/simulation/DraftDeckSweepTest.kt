// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.isDataEconomy
import kinetickk.ball.content.api.usesSkills
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The draft deck's long sweeps (items.md §5.1, §5.2): every floor over 500 seeds at three Rebirth ranks, the Keynote's
 * floors (variety.md §8.2) over the same seeds, history's effect on the deal over 2,000 seeds and on offers over 4,000.
 * Too long for the browser runner's per-test limit; DraftDeckTest and KeynoteTest hold the deck's rules.
 */
class DraftDeckSweepTest {
    private val policy = canonicalGameplayContent.build.policy.draftDeck
    private val shapes = listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.STAR, CoreShape.HEXAGON, CoreShape.DIPOLE)

    @Test
    fun everyDeckDealsItsSharesAndHoldsEveryFloorOverFiveHundredSeeds() {
        // The deck's own floors, measured without the Keynote's (the next test measures those on the same seeds).
        val withoutKeynote = canonicalGameplayContent.withoutKeynote()
        for (rebirth in listOf(0, 5, 10)) {
            var growth = 0
            for (seed in 1..500) {
                val shape = shapes[seed % shapes.size]
                val state = buildTestState(seed = seed, rebirth = rebirth, shape = shape, content = withoutKeynote)
                val deck = state.itemDeck
                val universe = canonicalGameplayContent.items.filter { it.minimumRebirth <= state.rebirthRank }
                val dealt = universe.filter { it.id in deck }
                // Only rank-eligible items are dealt, and the reserve is exactly the rest of the universe.
                assertEquals(dealt.map { it.id }, deck.dealtIds())
                assertEquals(universe.map { it.id }.toSet(), (deck.dealtIds() + deck.reserve()).toSet())
                var base = 0
                ItemRarity.entries.forEach { rarity ->
                    val size = universe.count { it.rarity == rarity }
                    val share = (policy.shares[rarity.ordinal] * size).roundToInt()
                        .coerceIn(min(policy.minimums[rarity.ordinal], size), size)
                    base += share
                    assertTrue(dealt.count { it.rarity == rarity } >= share, "Reborn $rebirth seed $seed deals its $rarity share")
                }
                growth += dealt.size - base
                fun holds(floor: Int, label: String, qualifies: (ItemDefinition) -> Boolean) {
                    val wanted = min(floor, universe.count(qualifies))
                    assertTrue(dealt.count(qualifies) >= wanted, "Reborn $rebirth seed $seed holds $label")
                }
                fun ItemDefinition.atMostRare() = rarity.rank <= ItemRarity.RARE.rank
                holds(policy.earlyOpenFloor, "early choice") {
                    it.rarity.rank <= ItemRarity.UNCOMMON.rank && it.unlockLevel <= policy.earlyOpenUnlock
                }
                holds(policy.earlyRareFloor, "an early Rare") { it.rarity.rank >= ItemRarity.RARE.rank && it.unlockLevel <= policy.earlyRareUnlock }
                // The Data stratum the level-42 calibration needs: at least three rank-0 Data items, two of them early.
                val ladder = dealt.filter { it.isDataEconomy && it.atMostRare() && it.minimumRebirth == 0 }
                assertTrue(ladder.size >= 3, "Reborn $rebirth seed $seed deals ${ladder.size} rank-0 Data items")
                holds(policy.dataEconomyFloor, "the Data ladder") { it.isDataEconomy && it.atMostRare() && it.minimumRebirth == 0 }
                holds(policy.earlyDataFloor, "early Data") {
                    it.isDataEconomy && it.atMostRare() && it.minimumRebirth == 0 && it.unlockLevel <= policy.earlyDataUnlock
                }
                holds(policy.characterTagFloor, "the character's archetype") { it.atMostRare() && state.coreShape.characterTag() in it.tags }
                BuildTag.entries.forEach { tag -> holds(policy.tagFloor, "$tag") { it.atMostRare() && tag in it.tags } }
                StatusElement.entries.forEach { element -> holds(policy.elementFloor, "$element") { it.atMostRare() && element in it.elements } }
                holds(policy.riskFloor, "the wagers") { it.risk }
                holds(policy.skillFloor, "skill support") { it.usesSkills }
            }
            // The floors bind only on unlucky deals: they add a few items on average.
            assertTrue(growth / 500.0 <= 4.0, "Reborn $rebirth floors add ${growth / 500.0} items per deck")
        }
    }

    @Test
    fun everyKeynoteDeckHoldsItsSharesAndAddsAtMostNineItemsOverFiveHundredSeeds() {
        val keynote = canonicalGameplayContent.variety.keynote
        val withoutKeynote = canonicalGameplayContent.withoutKeynote()
        var added = 0
        for (rebirth in listOf(0, 5, 10)) {
            var addedAtRank = 0
            for (seed in 1..500) {
                val shape = shapes[seed % shapes.size]
                val state = buildTestState(seed = seed, rebirth = rebirth, shape = shape)
                val plain = buildTestState(seed = seed, rebirth = rebirth, shape = shape, content = withoutKeynote)
                val tag = requireNotNull(state.keynoteTag)
                val element = requireNotNull(state.keynoteElement)
                val universe = canonicalGameplayContent.items.filter { it.minimumRebirth <= state.rebirthRank }
                fun holds(share: Float, label: String, qualifies: (ItemDefinition) -> Boolean) {
                    val eligible = universe.filter(qualifies)
                    val dealt = eligible.count { it.id in state.itemDeck }
                    assertTrue(dealt.toFloat() >= share * eligible.size, "Reborn $rebirth seed $seed deals $dealt of ${eligible.size} $label")
                }
                holds(keynote.tagShare, "$tag") { it.featuresTag(tag) }
                holds(keynote.elementShare, "$element") { it.featuresElement(element) }
                addedAtRank += state.itemDeck.dealtIds().size - plain.itemDeck.dealtIds().size
            }
            println("KEYNOTE-DECK reborn $rebirth adds ${addedAtRank / 500.0} items per deck")
            added += addedAtRank
        }
        // The 0.8 shares grow the deck by a few items, about six of the tag and two or three of the element; more at
        // higher ranks, whose universe holds more of them.
        assertTrue(added / 1_500.0 <= 9.0, "Keynote floors add ${added / 1_500.0} items per deck")
    }


    @Test
    fun anItemTakenInTheLastRunIsDealtLessOftenOverTwoThousandSeeds() {
        val item = canonicalGameplayContent.items.single { it.name == "Ballast Core" }
        val others = (150 until 181).toList()
        // History weighs the deal itself; the Keynote's floors deal past it in runs that feature the item's tag.
        val withoutKeynote = canonicalGameplayContent.withoutKeynote()
        fun dealtCounts(content: GameplayContentSnapshot): Pair<Int, List<Int>> {
            val plain = (1..2_000).count { seed -> item.id in startedWithHistory(seed, emptyList(), content).itemDeck }
            // Taken newest, mid-run (the mean age of a run's 32 acquisitions) and first in the last run.
            val dealt = listOf(0, 16, 31).map { age ->
                val lastRun = others.take(31 - age) + item.id + others.drop(31 - age)
                (1..2_000).count { seed -> item.id in startedWithHistory(seed, lastRun, content).itemDeck }
            }
            return plain to dealt
        }
        val (plain, dealt) = dealtCounts(withoutKeynote)
        println("DECK-HISTORY plain $plain, with history (age 0, 16, 31) $dealt")
        assertTrue(dealt[1] <= 0.8 * plain, "Dealt ${dealt[1]} times taken mid-run against $plain without")
        assertTrue(dealt[0] < dealt[1] && dealt[1] < dealt[2] && dealt[2] < plain, "A newer acquisition is dealt less often")
        val (keynotePlain, keynoteDealt) = dealtCounts(canonicalGameplayContent)
        println("DECK-HISTORY with the Keynote: plain $keynotePlain, with history (age 0, 16, 31) $keynoteDealt")
        assertTrue(
            keynoteDealt[0] < keynoteDealt[1] && keynoteDealt[1] < keynoteDealt[2] && keynoteDealt[2] < keynotePlain,
            "With the Keynote a newer acquisition is still dealt less often",
        )
    }


    @Test
    fun recentItemsAreOfferedLessOftenInEveryLane() {
        val item = canonicalGameplayContent.items.single { it.name == "Ballast Core" }
        fun share(recent: List<Int>): Double {
            val state = startedWithHistory(5, recent).dealWholeCatalog().apply { lifetimeMatter = 4_000L; level = 20 }
            var cards = 0
            var hits = 0
            for (seed in 1..4_000) {
                state.rewardRandom = CloneableXorWowRandom(seed)
                state.buildItemChoices()
                cards += state.choices.size
                hits += state.choices.count { it.itemId == item.id }
            }
            return hits.toDouble() / cards
        }
        val fresh = share(emptyList())
        val recent = share(List(32) { item.id })
        assertTrue(recent < 0.8 * fresh, "Offer share $recent with history against $fresh without")
    }
}
