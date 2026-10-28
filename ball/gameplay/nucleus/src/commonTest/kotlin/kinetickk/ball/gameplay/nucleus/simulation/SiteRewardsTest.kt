// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteRewardsTest {
    private fun game() = MutableGameState(canonicalGameplayContent).apply { startRun(); enemies.clear() }

    @Test
    fun theFloorQueueAcceptsItsBoundAndRefusesTheNext() {
        val game = game()
        val bound = game.content.sites.maxPendingFloorOffers
        repeat(bound) { assertTrue(game.enqueueFloorOffer(ItemRarity.RARE)) }
        assertFalse(game.enqueueFloorOffer(ItemRarity.EPIC))
        assertEquals(List(bound) { ItemRarity.RARE }, game.pendingFloorOffers)
    }

    @Test
    fun aQueuedFloorOfferDealsCardsAtTheFloorWithTheOrdinaryDrawsAndFinishingClearsIt() {
        // Every item dealt and unlocked, so each card's tier holds Rare or better items.
        val plain = game().apply {
            itemDeck = ItemDeck.EVERYTHING
            level = 60
        }
        val floored = plain.copyForReduction()
        plain.openItemChoice()
        assertTrue(floored.enqueueFloorOffer(ItemRarity.RARE))
        floored.openNextPendingChoice()
        assertEquals(GamePhase.CHOICE, floored.phase)
        assertEquals(ChoiceType.ITEM, floored.choiceType)
        assertEquals(ItemRarity.RARE, floored.offerRarityFloor)
        assertTrue(floored.pendingFloorOffers.isEmpty())
        assertEquals(plain.choices.size, floored.choices.size)
        floored.choices.forEach { card ->
            assertTrue(floored.content.items[card.itemId!!].rarity.rank >= ItemRarity.RARE.rank, "${card.title} is Rare or better")
        }
        assertEquals(plain.rewardRandom.snapshot(), floored.rewardRandom.snapshot(), "a floor draws exactly what an offer draws")
        floored.choose(0)
        assertNull(floored.offerRarityFloor)
        assertEquals(GamePhase.RUNNING, floored.phase)
    }

    @Test
    fun directedRewardsKeepTheirSixPerRunContract() {
        val game = game()
        repeat(6) { assertTrue(game.enqueueDirectedReward(DirectedReward.RELIC)) }
        assertFailsWith<IllegalStateException> { game.enqueueDirectedReward(DirectedReward.RELIC) }
        assertEquals(6, game.pendingDirectedRewards.size)
    }

    @Test
    fun aDiscoveryIsPublishedOnceAndSecretsAreRunFlags() {
        val game = game()
        game.discoverWorld(WorldDiscovery.entries.first())
        val bit = WorldDiscovery.entries.first().bit
        assertEquals(bit, game.pendingWorldDiscoveryMask)
        game.pendingWorldDiscoveryMask = 0
        game.discoverWorld(WorldDiscovery.entries.first())
        assertEquals(0, game.pendingWorldDiscoveryMask, "a known discovery is not published again")
        assertEquals(bit, game.worldDiscoveryMask)

        assertFalse(game.hasSecret(RunSecretFlag.SPIN_CYCLE))
        game.setSecret(RunSecretFlag.SPIN_CYCLE)
        assertTrue(game.hasSecret(RunSecretFlag.SPIN_CYCLE))
        assertTrue(game.optionalSitesTouched(), "an opted-in secret counts as engagement")
        val far = game()
        far.setSecret(RunSecretFlag.FAR_TRAVEL)
        assertFalse(far.optionalSitesTouched(), "far travel alone is not an opt-in")
        game.startRun()
        assertEquals(0, game.runSecretFlags)
        assertEquals(bit, game.worldDiscoveryMask, "a new run keeps what the Profile knows")
    }
}
