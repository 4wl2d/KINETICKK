// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeds per long pity sweep. */
private const val SWEEP_SEEDS = 20_000

/** An unlock level no run reaches, which keeps an item out of every ordinary offer. */
private const val NEVER_UNLOCKED = Int.MAX_VALUE

/**
 * Long seeded sweeps of the offer pity guarantee. OfferSystemTest keeps short versions of both checks (they also run
 * on Wasm); these repeat them over 20,000 reward streams each, which is too slow for the browser test runner.
 */
class OfferPitySweepTest {
    private val rareOrBetter = { item: ItemDefinition -> item.rarity.rank >= ItemRarity.RARE.rank }

    @Test
    fun pityHoldsOverTwentyThousandSeedsEvenWhenTheRolledRarityHasNothingToOffer() {
        // No Legendary is offerable before Rebirth 2, so a rolled Legendary must still end on a Rare or better card.
        val offers = veteran().sampleOffers(pity = PITY_OFFERS)
        assertEquals(0, offers.count { offer -> offer.none(rareOrBetter) }, "Pity offers without a Rare or better")
    }

    @Test
    fun pityFindsARareOrBetterCardWhereverTheOfferableOnesSitOverTwentyThousandSeeds() {
        val singleRare = canonicalGameplayContent.items.first { it.rarity == ItemRarity.RARE && it.minimumRebirth == 0 }
        val scenarios = mapOf(
            "Rebirth 2" to veteran(rebirth = 2),
            // The whole catalog dealt: the one Rare is offerable whichever items a deal of this catalog holds.
            "a single Rare or better" to veteran(contentOffering { !rareOrBetter(it) || it.id == singleRare.id }).dealWholeCatalog(),
            "no Rare" to veteran(contentOffering { it.rarity != ItemRarity.RARE }),
            "no Rare or Epic at Rebirth 2" to veteran(
                contentOffering { it.rarity != ItemRarity.RARE && it.rarity != ItemRarity.EPIC },
                rebirth = 2,
            ),
        )
        assertEquals(1, scenarios.getValue("a single Rare or better").offerableItems().count(rareOrBetter))
        scenarios.forEach { (name, state) ->
            val offers = state.sampleOffers(pity = PITY_OFFERS)
            assertEquals(0, offers.count { offer -> offer.none(rareOrBetter) }, "Offers without a Rare or better ($name)")
            assertTrue(offers.all { offer -> offer.map { it.id }.toSet().size == offer.size }, "An offer never repeats a card ($name)")
        }
    }

    private fun veteran(content: GameplayContentSnapshot = canonicalGameplayContent, rebirth: Int = 0) =
        buildTestState(rebirth = rebirth, content = content).apply {
            level = 20
            lifetimeMatter = 4_000L
        }

    /** The canonical catalog in which only the items matching [offerable] ever unlock for ordinary offers. */
    private fun contentOffering(offerable: (ItemDefinition) -> Boolean): GameplayContentSnapshot =
        canonicalGameplayContent.copy(
            items = canonicalGameplayContent.items
                .map { item -> if (offerable(item)) item else item.copy(unlockLevel = NEVER_UNLOCKED) }
                .toImmutableList(),
        )

    /** Builds one offer per seed from a fresh reward stream with the given pity counter and returns its cards. */
    private fun MutableGameState.sampleOffers(pity: Int): List<List<ItemDefinition>> =
        (1..SWEEP_SEEDS).map { seed ->
            rewardRandom = CloneableXorWowRandom(seed)
            offerPity = pity
            buildItemChoices()
            choices.map { content.items[requireNotNull(it.itemId)] }
        }
}
