// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OfferLanePolicy
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Active items reach a run only through item offers: their slots, ranks, offer rules and the slot replacement prompt. */
class ActiveItemOfferTest {
    @Test
    fun anActiveCardBindsTheLowestEmptySlotAndASecondStackRanksItReadyAtOnce() {
        val state = buildTestState().dealWholeCatalog().apply { level = 8 }
        val flare = state.active(SkillId.SOLAR_FLARE)
        state.offer(flare)
        val card = state.choices.single()
        assertEquals(ChoiceType.ITEM, card.type)
        assertEquals(SkillId.SOLAR_FLARE, card.skillId)
        assertEquals(1, card.skillRank, "the card names the rank it reaches")
        assertEquals(0, card.activeSlot, "it binds the lowest empty slot")
        assertFalse(card.replacesActive)
        state.choose(0)
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(SkillId.SOLAR_FLARE, state.skillLoadout.skill(0))
        assertEquals(1, state.skillLoadout.rank(0))
        assertEquals(state.elapsed, state.skillRuntime[0, SkillCell.READY_AT], "ready at once")
        assertTrue(state.castSkill(0))
        assertTrue(state.skillRuntime[0, SkillCell.READY_AT] > state.elapsed)

        state.offer(flare)
        val rankUp = state.choices.single()
        assertEquals(2, rankUp.skillRank)
        assertEquals(0, rankUp.activeSlot, "a rank-up card names the slot the effect holds")
        assertFalse(rankUp.replacesActive)
        state.choose(0)
        assertEquals(2, state.skillLoadout.rank(0))
        assertEquals(2, state.itemStack(flare.id), "stacks and rank never diverge")
        assertEquals(state.elapsed, state.skillRuntime[0, SkillCell.READY_AT], "a rank-up makes the effect ready")
        assertEquals(0f, state.skillRuntime[0, SkillCell.REFUNDED])
        assertNull(state.activeLoadoutMismatch())

        // A passive card carries no active fields.
        val passive = state.itemCard(state.itemNamed("Igniter"), "", reborn = false)
        assertNull(passive.skillId)
        assertNull(passive.skillRank)
        assertNull(passive.activeSlot)
        assertFalse(passive.replacesActive)
    }

    @Test
    fun activeRulesFilterAndWeightOffersWithoutAnExtraRewardDraw() {
        var shownActive = 0
        for (seed in 1..40) {
            val with = offerState(seed, canonicalGameplayContent)
            val without = offerState(seed, withoutActiveItems())
            assertEquals(with.rewardRandom.snapshot(), without.rewardRandom.snapshot(), "seed $seed: actives add no draw")
            with.openItemChoice()
            without.openItemChoice()
            assertEquals(with.rewardRandom.snapshot(), without.rewardRandom.snapshot(), "seed $seed: the offer draws the same")
            assertEquals(with.choices.size, without.choices.size)
            assertTrue(without.choices.none { it.skillId != null }, "seed $seed: no active without the active weight")
            val actives = with.choices.count { it.skillId != null }
            assertTrue(actives <= 1, "seed $seed: at most one active per offer")
            shownActive += actives
            with.rerollChoices()
            without.rerollChoices()
            assertEquals(with.rewardRandom.snapshot(), without.rewardRandom.snapshot(), "seed $seed: a reroll draws the same")
        }
        assertTrue(shownActive > 0, "some offer shows an active item")
    }

    @Test
    fun atMostOneActiveShowsPerOfferAndAZeroActiveWeightOffersNoneAnywhere() {
        val eager = contentWithLanes { copy(activeWeight = 1_000f) }
        var single = 0
        for (seed in 1..120) {
            val state = offerState(seed, eager)
            state.openItemChoice()
            val actives = state.choices.count { it.skillId != null }
            assertTrue(actives <= 1, "seed $seed: ${state.choices.map { it.title }}")
            if (actives == 1) single++
            val reward = offerState(seed, eager).directedItemReward(RewardFocus.OFFENSE)
            assertTrue(reward.count { it.skillId != null } <= 1, "seed $seed: a directed reward shows at most one active")
        }
        assertTrue(single >= 60, "an eager active weight shows an active in most offers ($single of 120)")

        val none = withoutActiveItems()
        for (seed in 1..40) {
            val state = offerState(seed, none)
            assertTrue(state.offerableItems().none { it.isActive }, "seed $seed: no active is offerable")
            for (focus in listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY)) {
                assertTrue(offerState(seed, none).directedItemReward(focus).none { it.skillId != null }, "seed $seed $focus")
            }
        }
    }

    @Test
    fun activesUnlockByTheRunLevelWhateverTheLifetimeMatter() {
        val veteran = buildTestState().dealWholeCatalog().apply { lifetimeMatter = 4_000L }
        val resonator = veteran.active(SkillId.RESONATOR)
        assertEquals(ItemRarity.EPIC, resonator.rarity)
        veteran.level = resonator.unlockLevel - 1
        assertFalse(veteran.offerableOutsideDeck(resonator), "the catalog level never opens an active early")
        // A passive item of the same unlock level is open at the veteran's catalog level.
        val passive = veteran.content.items.first {
            !it.isActive && it.minimumRebirth == 0 && it.unlockLevel >= resonator.unlockLevel && veteran.hasUsefulItemEffect(it)
        }
        assertTrue(veteran.offerableOutsideDeck(passive))
        veteran.level = resonator.unlockLevel
        assertTrue(veteran.offerableOutsideDeck(resonator), "the run level opens it")

        // No active unlocks before level 5: an early directed reward shows none, a late one may.
        val earliest = canonicalGameplayContent.items.filter { it.isActive }.minOf { it.unlockLevel }
        var late = 0
        for (seed in 1..60) {
            for (focus in listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY)) {
                val early = offerState(seed, canonicalGameplayContent, level = earliest - 1).directedItemReward(focus)
                assertTrue(early.none { it.skillId != null }, "seed $seed $focus")
                late += offerState(seed, canonicalGameplayContent).directedItemReward(focus).count { it.skillId != null }
            }
        }
        assertTrue(late > 0, "directed rewards deal unlocked actives")
    }

    @Test
    fun anUnownedCatalyzeWaitsForTwoElementsAndAnOwnedOneStillRanks() {
        val state = buildTestState().dealWholeCatalog().apply { level = 20 }
        val catalyze = state.active(SkillId.CATALYZE)
        assertFalse(state.offerableOutsideDeck(catalyze), "no element in play")
        state.acquireActive(SkillId.SOLAR_FLARE)
        assertFalse(state.offerableOutsideDeck(catalyze), "one element in play")
        state.own("Rime Rounds")
        assertTrue(StatusElement.entries.count { state.build.affinity(it) >= 1 } >= CATALYZE_MIN_ELEMENTS)
        assertTrue(state.offerableOutsideDeck(catalyze), "two elements in play")

        // An owned Catalyze is offered its rank-ups with no element in play.
        val owner = buildTestState().dealWholeCatalog().apply { level = 20 }
        owner.acquireActive(SkillId.CATALYZE)
        assertTrue(StatusElement.entries.none { owner.build.affinity(it) >= 1 })
        assertTrue(owner.offerableOutsideDeck(catalyze))
    }

    @Test
    fun anActiveStaysOfferableAndRanksUnderAnyRelic() {
        val state = buildTestState(RelicId.ABSOLUTE_ZERO).dealWholeCatalog().apply { level = 20 }
        val flare = state.active(SkillId.SOLAR_FLARE)
        assertTrue(state.itemSuppressedShare(flare) in 0f..0.5f, "its Thermal passive may go, its effect never does")
        assertTrue(state.hasUsefulItemEffect(flare))
        assertTrue(state.offerableOutsideDeck(flare))
        state.acquireActive(SkillId.SOLAR_FLARE)
        assertTrue(state.offerableOutsideDeck(flare), "an owned active ranks under the Relic")
        state.acquireActive(SkillId.SOLAR_FLARE)
        assertEquals(2, state.skillLoadout.rank(state.skillLoadout.slotOf(SkillId.SOLAR_FLARE)))
        assertFalse(flare.id in state.buildProjection().suppressedItemIds)
        // Nor does an active ever sleep: with no other active owned, its own effect is in use.
        val fresh = buildTestState()
        canonicalGameplayContent.items.filter { it.isActive }.forEach { item ->
            assertTrue(fresh.itemDormantShare(item) < 1f, item.name)
            assertTrue(fresh.itemSuppressedShare(item) < 1f, item.name)
        }
    }

    @Test
    fun aMaxedActiveIsNotOfferedAndAnActiveTakenWithEverySlotFullOutsideAnOfferIsSalvaged() {
        val state = fullLoadout()
        val flare = state.active(SkillId.SOLAR_FLARE)
        state.acquireActive(SkillId.SOLAR_FLARE, 2)
        assertEquals(3, state.itemStack(flare.id))
        assertFalse(state.offerableOutsideDeck(flare), "a maxed active is not offered")

        val loadout = state.skillLoadout
        val incoming = state.active(SkillId.METEOR)
        val matter = state.matterHeld()
        state.acquireItem(incoming.id)
        assertEquals(0, state.itemStack(incoming.id), "never an inert owned active")
        assertEquals(loadout, state.skillLoadout)
        assertEquals(matter + incoming.rarity.rank * 2 * state.matterGain, state.matterHeld(), 0.001f)
        // A maxed active arriving anyway is salvaged too and ranks nothing further.
        state.acquireItem(flare.id)
        assertEquals(3, state.skillLoadout.rank(state.skillLoadout.slotOf(SkillId.SOLAR_FLARE)))
        assertNull(state.activeLoadoutMismatch())
    }

    @Test
    fun aFourthActiveAsksWhichSlotToReplaceAndTheKeepCardSalvagesIt() {
        val state = fullLoadout()
        val loadout = state.skillLoadout
        val incoming = state.active(SkillId.SONIC_BOOM)
        assertEquals(ItemRarity.UNCOMMON, incoming.rarity)
        state.offerPity = 2
        state.rerollsRemaining = 2
        state.offer(incoming, reborn = true)
        val card = state.choices.single()
        assertTrue(card.replacesActive, "the card says it will replace a slot")
        assertNull(card.activeSlot)
        assertEquals(1, card.skillRank)

        state.choose(0)
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(ChoiceType.ACTIVE_SLOT, state.choiceType)
        assertEquals(3, state.offerPity, "the item card resolved the offer's pity")
        assertEquals(incoming.id, state.pendingActiveItemId)
        assertTrue(state.pendingActiveReborn)
        assertEquals(0, state.itemStack(incoming.id), "nothing is taken until a slot is chosen")
        val cards = state.choices
        assertEquals(4, cards.size)
        for (slot in 0 until 3) {
            val held = checkNotNull(loadout.skill(slot))
            val prompt = cards[slot]
            assertEquals(ChoiceType.ACTIVE_SLOT, prompt.type)
            assertEquals(slot, prompt.activeSlot)
            assertEquals(state.active(held).id, prompt.itemId)
            assertEquals(held, prompt.skillId)
            assertEquals(loadout.rank(slot), prompt.skillRank)
            assertEquals(incoming.id, prompt.incomingItemId)
        }
        val keep = cards[3]
        assertEquals(ChoiceType.ACTIVE_SLOT, keep.type)
        assertNull(keep.activeSlot)
        assertNull(keep.itemId)
        assertEquals(incoming.id, keep.incomingItemId)

        assertFalse(state.choicesCanReroll, "the prompt is never rerolled")
        state.rerollChoices()
        assertEquals(cards, state.choices)
        assertEquals(2, state.rerollsRemaining)

        val matter = state.matterHeld()
        state.choose(3)
        assertEquals(GamePhase.RUNNING, state.phase)
        assertNull(state.pendingActiveItemId)
        assertFalse(state.pendingActiveReborn)
        assertEquals(loadout, state.skillLoadout, "the loadout stays")
        assertEquals(0, state.itemStack(incoming.id))
        assertEquals(matter + incoming.rarity.rank * 2 * state.matterGain, state.matterHeld(), 0.001f)
        assertEquals(matter + checkNotNull(keep.salvageMatter) * state.matterGain, state.matterHeld(), 0.001f,
            "the keep card names the Matter its salvage grants")
        assertEquals(3, state.offerPity, "pity resolves once")
    }

    @Test
    fun replacingASlotRemovesTheOldItemAndBindsTheIncomingThereAtRankOneOnce() {
        val state = fullLoadout()
        val line = state.active(SkillId.FROST_LINE)
        state.rebornItems[line.id] = 1
        state.recompileBuild()
        assertEquals(1, state.skillLoadout.slotOf(SkillId.FROST_LINE))
        assertTrue(state.build.affinity(StatusElement.CRYO) >= 1)
        val incoming = state.active(SkillId.VOID_STEP)
        state.directedReward = DirectedReward.ITEM_AND_REPAIR
        state.hp = state.maxHp - 60f
        val hp = state.hp
        state.offer(incoming)
        state.choose(0)
        assertEquals(ChoiceType.ACTIVE_SLOT, state.choiceType)
        assertEquals(hp, state.hp, "the directed repair waits for the prompt")
        assertEquals(DirectedReward.ITEM_AND_REPAIR, state.directedReward)

        state.choose(1)
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals(hp + 25f, state.hp, 0.001f, "the directed repair applies once, after the prompt")
        assertNull(state.directedReward)
        assertEquals(0, state.itemStack(line.id))
        assertEquals(0, state.rebornItems[line.id], "the Reborn flag goes with it")
        assertFalse(line.id in state.acquisitionOrder)
        assertEquals(0, state.build.affinity(StatusElement.CRYO), "its passive and points leave the build")
        assertTrue(state.isItemDiscovered(line.id), "discovery is not undone")
        assertEquals(SkillId.VOID_STEP, state.skillLoadout.skill(1), "the incoming item binds the same slot")
        assertEquals(1, state.skillLoadout.rank(1))
        assertEquals(state.elapsed, state.skillRuntime[1, SkillCell.READY_AT])
        assertEquals(1, state.itemStack(incoming.id))
        assertEquals(SkillId.SOLAR_FLARE, state.skillLoadout.skill(0))
        assertEquals(SkillId.ARC_HOOK, state.skillLoadout.skill(2))
        assertNull(state.activeLoadoutMismatch())
        assertTrue(state.offerableOutsideDeck(line), "the replaced item may be offered again")
    }

    @Test
    fun aReplacedActiveLeavesNothingRunning() {
        val state = buildTestState().dealWholeCatalog().apply { level = 20 }
        state.acquireActive(SkillId.MASS_DRIVER)
        state.acquireActive(SkillId.NULL_SHROUD)
        state.acquireActive(SkillId.DECAY_SPORES)
        // Mass Driver runs with its guard; its slot owns a field and holds a buffered request and pending timers.
        assertTrue(state.castSkill(0))
        assertTrue(state.skillMassMultiplier() > 1f)
        assertTrue(state.buffs.any { it.sourceKey == BuffSource.skill(SkillId.MASS_DRIVER) })
        state.addTestField(CombatFieldKind.GRAVITY_WELL, x = 0f, y = 0f, radius = 200f, owner = 0)
        assertEquals(FieldSource.SKILL, state.fieldRecords.single().source)
        state.pickups += Pickup(PickupType.DATA, 10f, 10f)
        state.skillRuntime[1, SkillCell.PENDING_AT] = state.elapsed + 1f
        state.skillRuntime[1, SkillCell.GHOST_AT] = state.elapsed + 1f
        state.skillBufferMask = 0b111
        state.skillBufferTime = 0.1f
        // A Null Shroud bolt flies back; a hostile bolt is not the shroud's.
        state.projectiles += Projectile(x = 0f, y = 0f, vx = 1f, vy = 0f, radius = 5f, life = 2f, hostile = false,
            damage = 10f, procKey = WeaponRuntime.PROC_SKILL)
        val hostile = state.hostileBolt(300f, 0f)
        // Decay Spores seeded one enemy; an item's mark on another is not the cast's.
        val seeded = state.dummy(x = 500f)
        val marked = state.dummy(x = -500f)
        state.setMark(seeded, MarkKind.SPORED, 5f, cast = true)
        state.setMark(marked, MarkKind.SPORED, 5f)
        state.shield = 1_000f

        state.replaceActiveItem(0, state.active(SkillId.SOLAR_FLARE).id, reborn = false)
        assertEquals(1f, state.skillMassMultiplier(), "the running effect ends")
        assertTrue(state.buffs.none { it.sourceKey == BuffSource.skill(SkillId.MASS_DRIVER) }, "its guard ends")
        assertTrue(state.fieldRecords.isEmpty(), "its field ends")
        assertTrue(state.pickups.none { it.attracted }, "without the field's closing latch")
        assertEquals(0b110, state.skillBufferMask, "its buffered request is dropped")
        assertTrue(state.shield <= state.shieldCapacity() * maxOf(1f, state.build.overhealOverfill), "the shield fits the new capacity")

        state.replaceActiveItem(1, state.active(SkillId.FROST_LINE).id, reborn = false)
        assertTrue(state.projectiles.none { !it.hostile && it.procKey == WeaponRuntime.PROC_SKILL }, "the shroud's bolts end")
        assertTrue(hostile in state.projectiles)
        assertEquals(-1f, state.skillRuntime[1, SkillCell.PENDING_AT], "no pending impact")
        assertEquals(-1f, state.skillRuntime[1, SkillCell.GHOST_AT], "no ghost repeat")

        state.replaceActiveItem(2, state.active(SkillId.ARC_HOOK).id, reborn = false)
        assertFalse(state.markIs(seeded, MarkKind.SPORED), "a seed would keep dropping Data")
        assertTrue(state.markIs(marked, MarkKind.SPORED), "an item's mark stays")
        assertEquals(0, state.skillBufferMask)
        assertEquals(0f, state.skillBufferTime)
        assertNull(state.activeLoadoutMismatch())
    }

    @Test
    fun theRunDamageOfAnActiveEffectOutlivesItsReplacement() {
        val state = fullLoadout()
        val slot = state.skillLoadout.slotOf(SkillId.SOLAR_FLARE)
        state.dummy(x = state.coreX + 20f, y = state.coreY)
        assertTrue(state.castSkill(slot))
        val dealt = state.skillRuntime.effectDamage(SkillId.SOLAR_FLARE)
        assertTrue(dealt > 0f)
        assertEquals(dealt, state.skillRuntime[slot, SkillCell.DAMAGE])
        state.replaceActiveItem(slot, state.active(SkillId.METEOR).id, reborn = false)
        assertEquals(0f, state.skillRuntime[slot, SkillCell.DAMAGE], "the slot starts over")
        assertEquals(dealt, state.skillRuntime.effectDamage(SkillId.SOLAR_FLARE), "the effect keeps its run damage")
        assertEquals(0f, state.skillRuntime.effectDamage(SkillId.METEOR))
        // A fork carries the counts and keeps them apart.
        val fork = state.skillRuntime.fork()
        fork.addEffectDamage(SkillId.METEOR, 5f)
        assertEquals(0f, state.skillRuntime.effectDamage(SkillId.METEOR))
        assertEquals(dealt, fork.effectDamage(SkillId.SOLAR_FLARE))
    }

    @Test
    fun previewsForecastAnActiveThatNeedsASlotAndTheReplacementWithoutTakingEither() {
        val state = fullLoadout()
        val loadout = state.skillLoadout
        val stacks = state.itemStacks.toList()
        val incoming = state.active(SkillId.GRAVITY_WELL)
        assertEquals(0, state.build.affinity(StatusElement.GRAVITIC))
        state.offer(incoming)
        val needsSlot = state.rewardPreviews().single()
        assertTrue(needsSlot.requiresSlot, "the same flag the card shows")
        assertTrue(needsSlot.forecasts.any { it.kind == ForecastKind.AFFINITY && it.element == StatusElement.GRAVITIC })
        state.choose(0)
        val previews = state.rewardPreviews()
        assertEquals(4, previews.size)
        val thermal = previews[state.skillLoadout.slotOf(SkillId.SOLAR_FLARE)]
        assertFalse(thermal.requiresSlot)
        assertTrue(thermal.forecasts.any { it.kind == ForecastKind.AFFINITY && it.element == StatusElement.GRAVITIC })
        assertTrue(previews.last().forecasts.isEmpty() && previews.last().changes.isEmpty(), "keeping changes nothing")
        assertEquals(loadout, state.skillLoadout, "previews never take a card")
        assertEquals(stacks, state.itemStacks.toList())
        assertEquals(incoming.id, state.pendingActiveItemId)
    }

    @Test
    fun theIncomingActiveIsCandidateState() {
        val state = fullLoadout()
        val incoming = state.active(SkillId.STORM_COIL)
        state.offer(incoming, reborn = true)
        state.choose(0)
        val loadout = state.skillLoadout
        val fork = state.copyForReduction()
        assertEquals(incoming.id, fork.pendingActiveItemId)
        assertTrue(fork.pendingActiveReborn)
        fork.choose(0)
        assertNull(fork.pendingActiveItemId)
        assertEquals(SkillId.STORM_COIL, fork.skillLoadout.skill(0))
        assertEquals(1, fork.rebornItems[incoming.id], "the Reborn roll arrives with the item")
        // The committed state still waits for its own choice.
        assertEquals(incoming.id, state.pendingActiveItemId)
        assertTrue(state.pendingActiveReborn)
        assertEquals(loadout, state.skillLoadout)
        assertEquals(0, state.itemStack(incoming.id))
        assertEquals(ChoiceType.ACTIVE_SLOT, state.choiceType)
    }

    @Test
    fun theDeckFloorsCountAndPromotePassiveItemsOnly() {
        val policy = canonicalGameplayContent.build.policy
        val floorless = canonicalGameplayContent.copy(
            build = canonicalGameplayContent.build.copy(
                policy = policy.copy(
                    draftDeck = policy.draftDeck.copy(
                        earlyOpenFloor = 0, earlyRareFloor = 0, dataEconomyFloor = 0, earlyDataFloor = 0,
                        characterTagFloor = 0, tagFloor = 0, elementFloor = 0, riskFloor = 0, skillFloor = 0,
                    ),
                ),
            ),
        )
        var promoted = 0
        for (seed in 1..40) for (shape in listOf(CoreShape.ORB, CoreShape.HEXAGON, CoreShape.RING)) {
            val floored = buildTestState(seed = seed, shape = shape)
            val bare = buildTestState(seed = seed, shape = shape, content = floorless)
            val actives = canonicalGameplayContent.items.filter { it.isActive }
            assertEquals(
                actives.filter { it.id in bare.itemDeck }.map { it.id },
                actives.filter { it.id in floored.itemDeck }.map { it.id },
                "seed $seed $shape: actives are dealt by their tier's share alone",
            )
            promoted += canonicalGameplayContent.items.count { it.id in floored.itemDeck && it.id !in bare.itemDeck }
        }
        assertTrue(promoted > 0, "the floors promote passive items")
    }

    @Test
    fun activeOfferWeightAppliesTheActiveTheCharacterAndTheOwnedFactors() {
        val lanes = canonicalGameplayContent.build.policy.offerLanes
        val state = buildTestState(shape = CoreShape.ORB)
        val lane = state.content.coreShape(CoreShape.ORB).activeLane
        assertTrue(SkillId.SLINGSHOT in lane && SkillId.FROST_LINE !in lane)
        assertEquals(lanes.activeWeight * lanes.characterActiveWeight, state.activeOfferWeight(state.active(SkillId.SLINGSHOT)))
        assertEquals(lanes.activeWeight, state.activeOfferWeight(state.active(SkillId.FROST_LINE)))
        state.acquireActive(SkillId.FROST_LINE)
        assertEquals(lanes.activeWeight * lanes.ownedActiveWeight, state.activeOfferWeight(state.active(SkillId.FROST_LINE)))
        assertEquals(1f, state.activeOfferWeight(state.itemNamed("Igniter")))
    }

    @Test
    fun everyRewardChoiceDropsBufferedRequestsAndABoundSlotDropsItsOwn() {
        val state = buildTestState().dealWholeCatalog().apply { level = 20 }
        state.skillBufferMask = 0b11
        state.skillBufferTime = 0.1f
        state.apexBufferTime = 0.1f
        state.dashBufferTime = 0.1f
        state.openItemChoice()
        assertEquals(GamePhase.CHOICE, state.phase)
        assertEquals(0, state.skillBufferMask)
        assertEquals(0f, state.skillBufferTime)
        assertEquals(0f, state.apexBufferTime)
        assertEquals(0f, state.dashBufferTime)

        val binder = buildTestState()
        binder.skillBufferMask = 0b011
        binder.skillBufferTime = 0.1f
        binder.acquireActive(SkillId.SOLAR_FLARE)
        assertEquals(0b010, binder.skillBufferMask, "a request for an empty slot never casts what binds there")
    }

    @Test
    fun theHeirloomStillOpensTheRunAndTheLevelsItemOfferFollowsIt() {
        val state = buildTestState()
        state.rebirthRank = 1
        state.heirloomCandidates = listOf(RelicId.GYRE_ENGINE)
        state.heirloomPending = true
        state.gainData((state.nextLevelData - state.data).toFloat())
        assertEquals(ChoiceType.RELIC, state.choiceType)
        assertTrue(state.choices.any { it.heirloom })
        state.choose(state.choices.indexOfFirst { it.relicId != null })
        assertEquals(ChoiceType.ITEM, state.choiceType, "no draft comes between")
        state.choose(0)
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    private fun MutableGameState.active(id: SkillId): ItemDefinition = checkNotNull(content.activeItem(id))

    /** An item offer showing exactly [items]. */
    private fun MutableGameState.offer(vararg items: ItemDefinition, reborn: Boolean = false) {
        activeChoiceType = ChoiceType.ITEM
        choices = items.map { itemCard(it, it.rarity.displayLabel.uppercase(), reborn) }
        phase = GamePhase.CHOICE
    }

    private fun MutableGameState.matterHeld(): Float = runMatter + matterFraction

    /** Every slot holds an active: Solar Flare (Z), Frost Line rank II (X) and Arc Hook (C), at level 20. */
    private fun fullLoadout(): MutableGameState = buildTestState().dealWholeCatalog().apply {
        level = 20
        acquireActive(SkillId.SOLAR_FLARE)
        acquireActive(SkillId.FROST_LINE, 2)
        acquireActive(SkillId.ARC_HOOK)
        takeVisualFxCues()
        takeSoundCues()
    }

    /** A level-[level] run of [content] with the whole catalog dealt and the reward stream at [seed]. */
    private fun offerState(seed: Int, content: GameplayContentSnapshot, level: Int = 20): MutableGameState =
        buildTestState(content = content).dealWholeCatalog().apply {
            this.level = level
            rewardRandom = CloneableXorWowRandom(seed)
        }

    /** The cards of an item-and-repair point of interest's [focus]. */
    private fun MutableGameState.directedItemReward(focus: RewardFocus): List<kinetickk.ball.gameplay.nucleus.render.ChoiceOption> {
        openDirectedReward(DirectedReward.ITEM_AND_REPAIR)
        selectRewardFocus(focus)
        assertNotNull(choices)
        return choices
    }

    private fun contentWithLanes(change: OfferLanePolicy.() -> OfferLanePolicy): GameplayContentSnapshot {
        val policy = canonicalGameplayContent.build.policy
        return canonicalGameplayContent.copy(
            build = canonicalGameplayContent.build.copy(policy = policy.copy(offerLanes = policy.offerLanes.change())),
        )
    }
}
