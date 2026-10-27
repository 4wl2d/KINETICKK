// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OfferLanePolicy
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No rarity tier over-represents its items any more (items.md §5.3, §5.4): per-item card rates at Rebirth 0, run level
 * 20, an empty Orb build, against the Common rate, with the whole catalog dealt and with the draft deck. Desktop only:
 * 30,000 offers per policy exceed the browser runner's per-test limit.
 *
 * Restated for active items (R1.15): the tier buckets and the worst-item check cover passive items only, since the lane
 * policy weights active items on purpose. The same offers bound the active items instead: their cards are 0.04–0.10 of
 * all cards (ActiveItemFrequencyGateTest's share), and in every tier holding both, the Orb's own lane actives are shown
 * per item [OfferLanePolicy.characterActiveWeight] times as often as the other actives, within 0.80–1.25 of it.
 */
class OfferFrequencyFlatnessTest {
    @Test
    fun everyTierShowsItsItemsAboutAsOftenAsTheCommonsDo() {
        val measured = listOf(
            "whole catalog" to flatness(contentWith(DraftDeckPolicy.FULL), decks = 1),
            "draft deck" to flatness(canonicalGameplayContent, decks = 1_000),
        )
        // Both policies are reported before either is judged.
        measured.forEach { (label, flatness) -> describe(label, flatness) }
        measured.forEach { (label, flatness) -> judge(label, flatness) }
    }

    /**
     * The character's own archetype point no longer steers the aligned card: an Orb's (Drive) first card is no more
     * likely to carry Drive than a Prism's (Anchor) is, while the 0.4.0 lanes funnel the Orb to Drive from the first offer.
     * Passive cards only (R1.15): an offer whose first card is an active item is left out, since the Orb's lane actives
     * carry Drive and are weighted by the character on purpose (the flatness test bounds that factor).
     */
    @Test
    fun theCharactersOwnPointNoLongerFunnelsTheFirstOffer() {
        fun driveShare(shape: CoreShape, content: GameplayContentSnapshot): Double {
            val state = buildTestState(shape = shape, content = content).dealWholeCatalog().apply { level = 20 }
            var passive = 0
            var drive = 0
            repeat(SEEDS) { seed ->
                state.rewardRandom = CloneableXorWowRandom(seed)
                state.buildItemChoices()
                val first = state.content.items[requireNotNull(state.choices.first().itemId)]
                if (first.isActive) return@repeat
                passive++
                if (BuildTag.DRIVE in first.tags) drive++
            }
            return drive.toDouble() / passive
        }
        val orb = driveShare(CoreShape.ORB, canonicalGameplayContent)
        val prism = driveShare(CoreShape.PRISM, canonicalGameplayContent)
        println("LANES first-card Drive share Orb $orb Prism $prism")
        assertTrue(abs(orb - prism) <= 0.03, "Orb $orb against Prism $prism")
        val legacy = contentWith(DraftDeckPolicy.FULL, OfferLanePolicy.RELEASE_040)
        val legacyOrb = driveShare(CoreShape.ORB, legacy)
        val legacyPrism = driveShare(CoreShape.PRISM, legacy)
        println("LANES 0.4.0 first-card Drive share Orb $legacyOrb Prism $legacyPrism")
        assertTrue(legacyOrb - legacyPrism > 0.05, "The 0.4.0 lanes funnel: Orb $legacyOrb against Prism $legacyPrism")
    }

    /**
     * Per-tier mean card rate per offerable passive item against the Common mean, the largest passive item rate over its
     * tier's mean, the share of all cards that offer an active item, and per tier the mean card rate of the Orb's lane
     * actives over the other actives' (a tier holding both).
     */
    private class Flatness(
        val ratios: Map<String, Double>,
        val worst: Pair<String, Double>,
        val activeShare: Double,
        val laneFactors: Map<ItemRarity, Double>,
    )

    private fun flatness(content: GameplayContentSnapshot, decks: Int): Flatness {
        val shown = IntArray(content.items.size)
        val offerable = BooleanArray(content.items.size)
        var cards = 0
        var activeCards = 0
        val perDeck = OFFERS / decks
        for (deckSeed in 1..decks) {
            val state = buildTestState(seed = deckSeed, content = content).apply { level = 20 }
            content.items.forEach { if (state.offerableOutsideDeck(it)) offerable[it.id] = true }
            repeat(perDeck) { offer ->
                state.rewardRandom = CloneableXorWowRandom(deckSeed * 100_003 + offer)
                state.buildItemChoices()
                state.choices.forEach { choice ->
                    val id = requireNotNull(choice.itemId)
                    shown[id]++
                    if (content.items[id].isActive) activeCards++
                }
                cards += state.choices.size
            }
        }
        fun rate(item: ItemDefinition) = shown[item.id].toDouble() / cards
        fun List<ItemDefinition>.meanRate() = sumOf(::rate) / size
        val passive = content.items.filter { offerable[it.id] && !it.isActive }
        val buckets = linkedMapOf<String, List<ItemDefinition>>(
            "Common" to passive.filter { it.rarity == ItemRarity.COMMON },
            "Uncommon" to passive.filter { it.rarity == ItemRarity.UNCOMMON },
            "Rare" to passive.filter { it.rarity == ItemRarity.RARE },
            "Epic" to passive.filter { it.rarity == ItemRarity.EPIC && !it.risk && it.keystone == null },
            "risk Epic" to passive.filter { it.risk },
        )
        val means = buckets.mapValues { (_, items) -> items.meanRate() }
        val common = means.getValue("Common")
        val worst = buckets.flatMap { (bucket, items) -> items.map { it.name to rate(it) / means.getValue(bucket) } }.maxBy { it.second }
        val lane = content.coreShape(CoreShape.ORB).activeLane.toSet()
        val actives = content.items.filter { offerable[it.id] && it.isActive }
        val laneFactors = ItemRarity.entries.mapNotNull { tier ->
            val (inLane, outside) = actives.filter { it.rarity == tier }.partition { it.active in lane }
            if (inLane.isEmpty() || outside.isEmpty()) null else tier to inLane.meanRate() / outside.meanRate()
        }.toMap()
        return Flatness(means.mapValues { it.value / common }, worst, activeCards.toDouble() / cards, laneFactors)
    }

    private fun describe(label: String, flatness: Flatness) {
        println("FLATNESS $label ${flatness.ratios.entries.joinToString { "${it.key} ${"%.2f".format(it.value)}" }} " +
            "worst ${flatness.worst.first} ${"%.2f".format(flatness.worst.second)} actives ${"%.3f".format(flatness.activeShare)} " +
            "lane ${flatness.laneFactors.entries.joinToString { "${it.key} ${"%.2f".format(it.value)}" }}")
    }

    private fun judge(label: String, flatness: Flatness) {
        val laneWeight = canonicalGameplayContent.build.policy.offerLanes.characterActiveWeight.toDouble()
        assertTrue(flatness.activeShare in 0.04..0.10, "$label active cards ${flatness.activeShare} of all cards")
        assertTrue(flatness.laneFactors.isNotEmpty(), "$label holds lane actives and other actives in one tier")
        flatness.laneFactors.forEach { (tier, factor) ->
            assertTrue(factor in laneWeight * 0.80..laneWeight * 1.25, "$label $tier lane actives at $factor × the other actives")
        }
        val ratios = flatness.ratios
        assertTrue(ratios.getValue("Uncommon") in 0.85..1.15, "$label Uncommon ${ratios["Uncommon"]}")
        assertTrue(ratios.getValue("Rare") in 0.80..1.25, "$label Rare ${ratios["Rare"]}")
        assertTrue(ratios.getValue("Epic") in 0.50..1.00, "$label Epic ${ratios["Epic"]}")
        assertTrue(ratios.getValue("risk Epic") in 0.80..1.25, "$label risk Epic ${ratios["risk Epic"]}")
        assertTrue(flatness.worst.second <= 1.6, "$label ${flatness.worst.first} at ${flatness.worst.second} × its tier")
    }

    private fun contentWith(
        deck: DraftDeckPolicy,
        lanes: OfferLanePolicy = canonicalGameplayContent.build.policy.offerLanes,
    ): GameplayContentSnapshot =
        canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = deck, offerLanes = lanes),
        ))

    private companion object {
        const val OFFERS = 30_000
        const val SEEDS = 5_000
    }
}
