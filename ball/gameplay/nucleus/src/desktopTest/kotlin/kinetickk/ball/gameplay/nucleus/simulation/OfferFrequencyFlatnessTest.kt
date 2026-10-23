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
 */
class OfferFrequencyFlatnessTest {
    @Test
    fun everyTierShowsItsItemsAboutAsOftenAsTheCommonsDo() {
        report("whole catalog", flatness(contentWith(DraftDeckPolicy.FULL), decks = 1))
        report("draft deck", flatness(canonicalGameplayContent, decks = 1_000))
    }

    /**
     * The character's own archetype point no longer steers the aligned card: an Orb's (Drive) first card is no more
     * likely to carry Drive than a Prism's (Anchor) is, while the 0.4.0 lanes funnel the Orb to Drive from the first offer.
     */
    @Test
    fun theCharactersOwnPointNoLongerFunnelsTheFirstOffer() {
        fun driveShare(shape: CoreShape, content: GameplayContentSnapshot): Double {
            val state = buildTestState(shape = shape, content = content).dealWholeCatalog().apply { level = 20 }
            var drive = 0
            repeat(SEEDS) { seed ->
                state.rewardRandom = CloneableXorWowRandom(seed)
                state.buildItemChoices()
                if (BuildTag.DRIVE in state.content.items[requireNotNull(state.choices.first().itemId)].tags) drive++
            }
            return drive.toDouble() / SEEDS
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

    /** Per-tier mean card rate per offerable item against the Common mean, and the largest item rate over its tier's mean. */
    private class Flatness(val ratios: Map<String, Double>, val worst: Pair<String, Double>)

    private fun flatness(content: GameplayContentSnapshot, decks: Int): Flatness {
        val shown = IntArray(content.items.size)
        val offerable = BooleanArray(content.items.size)
        var cards = 0
        val perDeck = OFFERS / decks
        for (deckSeed in 1..decks) {
            val state = buildTestState(seed = deckSeed, content = content).apply { level = 20 }
            content.items.forEach { if (state.offerableOutsideDeck(it)) offerable[it.id] = true }
            repeat(perDeck) { offer ->
                state.rewardRandom = CloneableXorWowRandom(deckSeed * 100_003 + offer)
                state.buildItemChoices()
                state.choices.forEach { shown[requireNotNull(it.itemId)]++ }
                cards += state.choices.size
            }
        }
        fun rate(item: ItemDefinition) = shown[item.id].toDouble() / cards
        val buckets = linkedMapOf<String, List<ItemDefinition>>(
            "Common" to content.items.filter { offerable[it.id] && it.rarity == ItemRarity.COMMON },
            "Uncommon" to content.items.filter { offerable[it.id] && it.rarity == ItemRarity.UNCOMMON },
            "Rare" to content.items.filter { offerable[it.id] && it.rarity == ItemRarity.RARE },
            "Epic" to content.items.filter { offerable[it.id] && it.rarity == ItemRarity.EPIC && !it.risk && it.keystone == null },
            "risk Epic" to content.items.filter { offerable[it.id] && it.risk },
        )
        val means = buckets.mapValues { (_, items) -> items.sumOf(::rate) / items.size }
        val common = means.getValue("Common")
        val worst = buckets.flatMap { (bucket, items) -> items.map { it.name to rate(it) / means.getValue(bucket) } }.maxBy { it.second }
        return Flatness(means.mapValues { it.value / common }, worst)
    }

    private fun report(label: String, flatness: Flatness) {
        println("FLATNESS $label ${flatness.ratios.entries.joinToString { "${it.key} ${"%.2f".format(it.value)}" }} " +
            "worst ${flatness.worst.first} ${"%.2f".format(flatness.worst.second)}")
        val ratios = flatness.ratios
        assertTrue(ratios.getValue("Uncommon") in 0.85..1.15, "$label Uncommon ${ratios["Uncommon"]}")
        assertTrue(ratios.getValue("Rare") in 0.80..1.25, "$label Rare ${ratios["Rare"]}")
        assertTrue(ratios.getValue("Epic") in 0.50..1.00, "$label Epic ${ratios["Epic"]}")
        assertTrue(ratios.getValue("risk Epic") in 0.80..1.25, "$label risk Epic ${ratios["risk Epic"]}")
        assertTrue(flatness.worst.second <= 1.6, "$label ${flatness.worst.first} at ${flatness.worst.second} × its tier")
    }

    private fun contentWith(deck: DraftDeckPolicy, lanes: OfferLanePolicy = OfferLanePolicy()): GameplayContentSnapshot =
        canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = deck, offerLanes = lanes),
        ))

    private companion object {
        const val OFFERS = 30_000
        const val SEEDS = 5_000
    }
}
