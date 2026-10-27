// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.isDataEconomy
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kotlin.math.max
import kotlin.math.min

/** Internal drafting lanes; cards do not label them, the forecast row shows build fit truthfully. */
internal enum class OfferLane { ALIGNED, EXPERIMENTAL, VOLATILE }

internal const val PITY_OFFERS: Int = 6

/** The top resonance tier, where a tag's points count only the lane policy's saturated share toward fit. */
private const val TOP_RESONANCE_TIER: Int = 3

/** Items that may appear in an ordinary offer right now: dealt into the run's deck and offerable ([offerableOutsideDeck]). */
internal fun MutableGameState.offerableItems(): List<ItemDefinition> {
    val catalogLevel = offerCatalogLevel()
    return content.items.filter { item -> item.id in itemDeck && offerableOutsideDeck(item, catalogLevel) }
}

/**
 * One card-adding item per run: offers grow by one card however many such items the build holds, so once one is
 * owned another (or another copy) would add nothing, and it is no longer offered. The card says so.
 */
internal fun MutableGameState.offerCardsAlreadyAdded(item: ItemDefinition): Boolean =
    build.offerCards > 0 && item.rules.any { it is ItemRule.OfferCards }

/** Number of item cards in an offer (a card-adding item adds one, never above the choice bound). */
internal fun MutableGameState.itemOfferSize(): Int =
    min(MAX_GENERATED_REWARD_CHOICES, 3 + (if (build.offerCards > 0) 1 else 0) + (if (matrixWidensItemOffer()) 1 else 0))

/**
 * Points the build has in [item]'s tags and elements; reaction items count the reaction pseudo-tag. A tag counts only
 * with points the player chose (the character's own point is left out unless the lane policy lets the loadout fit) and
 * counts the policy's saturated share once it reached the top resonance tier. An item naming the equipped weapon's
 * family fits once [familyFits]: the loadout fits, or the build already took an item of that family.
 */
private fun MutableGameState.fit(item: ItemDefinition, familyFits: Boolean): Float {
    val lanes = content.build.policy.offerLanes
    val characterTag = coreShape.characterTag()
    var shared = 0f
    item.tags.forEach { tag ->
        val chosen = build.tagPoints(tag) - if (!lanes.loadoutFits && tag == characterTag) 1 else 0
        if (chosen > 0) shared += if (tagTier(tag) >= TOP_RESONANCE_TIER) lanes.saturatedTagFit else 1f
    }
    item.elements.forEach { if (build.affinity(it) > 0) shared += 0.75f }
    if (item.isReactionItem()) {
        val elementsInPlay = kinetickk.ball.content.api.StatusElement.entries.count { affinityTier(it) >= 1 }
        shared += max(0, elementsInPlay - 1).toFloat()
    }
    if (familyFits && item.namesWeaponFamily(currentWeaponDefinition.family)) shared += 1f
    return shared
}

/** True when family items fit the aligned lane: the loadout fits, or the build owns an item naming the equipped family. */
private fun MutableGameState.weaponFamilyFits(): Boolean {
    if (content.build.policy.offerLanes.loadoutFits) return true
    val family = currentWeaponDefinition.family
    return content.items.any { itemStacks[it.id] > 0 && it.namesWeaponFamily(family) }
}

private fun ItemDefinition.isReactionItem(): Boolean = rules.any {
    it is ItemRule.ReactionPower || it is ItemRule.ReactionResidue || it is ItemRule.ReactionRecoil ||
        it is ItemRule.ReactionKeystone || it is ItemRule.ReactionTuning || it is ItemRule.CatalysisRate
}

/** True when one of [this] item's conditions names weapon [family]. */
private fun ItemDefinition.namesWeaponFamily(family: kinetickk.ball.content.api.WeaponFamily): Boolean =
    clauses.any { clause -> clause.conditions.any { it is EffectCondition.WeaponFamilyIs && it.family == family } } ||
        rules.any { it is ItemRule.WhileModifier && it.condition.let { c -> c is EffectCondition.WeaponFamilyIs && c.family == family } }

/** True when the item would open a tag or element the build has fewer than two points in. */
private fun MutableGameState.experimental(item: ItemDefinition): Boolean =
    item.tags.any { build.tagPoints(it) < 2 } || item.elements.any { build.affinity(it) < 2 }

/**
 * Builds a build-aware item offer from [rewardRandom]: an aligned card, an experimental card and a volatile card
 * (Gambler's Ledger adds a second volatile card). Every card rolls a rarity and keeps it; a lane only chooses
 * inside that tier. The volatile card usually rolls one rarity higher and then sometimes prefers risk items when it
 * lands on their tier, and the pity card never rolls below Rare. A rolled tier with no available item falls to the
 * nearest lower tier (not below Rare for pity while any Rare or better is available), then to the nearest
 * higher one. Never guarantees a perfect synergy; suppressed items are excluded or down-weighted, recently taken
 * items (the Profile's history) are down-weighted in every lane, and the Matrix's Data offer weight raises Data-economy
 * items in every lane (weights only, no draw). Active items take the lane policy's active factors ([activeOfferWeight])
 * and at most one shows per offer: once a card is an active item, later cards leave actives out (a filter, never an
 * extra draw). Only items dealt into the run's deck appear (the deck is refilled first). Each card draws the rarity,
 * lane, pick and Reborn values from [rewardRandom] in that order.
 */
internal fun MutableGameState.buildItemChoices() {
    refillItemDeck()
    val lanePolicy = content.build.policy.offerLanes
    val familyFits = weaponFamilyFits()
    val pool = offerableItems()
    val selected = mutableListOf<ItemDefinition>()
    val reborns = mutableListOf<Boolean>()
    val lanes = OfferLane.entries
    val size = itemOfferSize()
    var rareOrBetter = false
    var activeShown = false
    val pityDue = offerPity >= pityOffers()
    for (cardIndex in 0 until size) {
        val lane = lanes[cardIndex.coerceAtMost(lanes.lastIndex)]
        val available = pool.filter { candidate ->
            selected.none { it.id == candidate.id } && !(activeShown && candidate.isActive)
        }
        if (available.isEmpty()) break
        var rarity = rollRarity()
        val laneRoll = rewardRandom.nextFloat()
        val volatileLift = lane == OfferLane.VOLATILE && laneRoll < lanePolicy.volatileLiftChance
        if (volatileLift) rarity = ItemRarity.entries.firstOrNull { it.rank == rarity.rank + 1 } ?: rarity
        val pityCard = lane == OfferLane.VOLATILE && pityDue && !rareOrBetter
        if (pityCard) rarity = maxOf(rarity, ItemRarity.RARE)
        val floor = if (pityCard && available.any { it.rarity.rank >= ItemRarity.RARE.rank }) ItemRarity.RARE else ItemRarity.COMMON
        val tier = available.nearestRarityTier(rarity, floor)
        val candidates = when (lane) {
            OfferLane.ALIGNED -> if (laneRoll < lanePolicy.alignedChance) {
                tier.filter { fit(it, familyFits) > 0f && itemSuppressedShare(it) == 0f }
            } else emptyList()
            OfferLane.EXPERIMENTAL -> if (laneRoll < 0.7f) tier.filter(::experimental) else emptyList()
            OfferLane.VOLATILE -> if (laneRoll < lanePolicy.volatileRiskChance) tier.filter { it.risk } else emptyList()
        }.ifEmpty { tier }
        val weights = FloatArray(candidates.size) { index ->
            val item = candidates[index]
            val base = if (lane == OfferLane.ALIGNED) 1f + lanePolicy.alignedFitWeight * min(fit(item, familyFits), lanePolicy.alignedFitCap) else 1f
            base * (1f - itemSuppressedShare(item)) * (1f - itemDormantShare(item)) * itemHistory.offerWeight(item.id) *
                (if (item.isDataEconomy) matrix.dataOfferWeight else 1f) * activeOfferWeight(item)
        }
        val choice = candidates[weightedIndex(weights)]
        selected += choice
        if (choice.isActive) activeShown = true
        if (choice.rarity.rank >= ItemRarity.RARE.rank) rareOrBetter = true
        reborns += rewardRandom.nextFloat() < rebornChance()
    }
    choices = selected.mapIndexed { index, item ->
        itemCard(item, item.rarity.displayLabel.uppercase(), reborns[index])
    }
}

/**
 * The active factors of [item]'s offer weight (1 for a passive item): the lane policy's active weight, its character
 * active weight when the effect is in the run character's lane and its owned active weight when the card would rank up
 * an owned active ([kinetickk.ball.content.api.OfferLanePolicy]). Weights only; whether an active may be offered at all
 * is [activeOfferable].
 */
internal fun MutableGameState.activeOfferWeight(item: ItemDefinition): Float {
    val effect = item.active ?: return 1f
    val lanes = content.build.policy.offerLanes
    var weight = lanes.activeWeight
    if (effect in content.coreShape(coreShape).activeLane) weight *= lanes.characterActiveWeight
    if (itemStacks[item.id] > 0) weight *= lanes.ownedActiveWeight
    return weight
}

/**
 * The rules an active item meets on top of a passive item's to be offered (always true for a passive item): actives are
 * offered at all (a positive active weight), [item]'s unlock level is reached by the run level (not the lifetime-Matter
 * catalog level), and an unowned Catalyze waits for two elements in play ([catalyzeOfferable]); an owned one still ranks.
 */
internal fun MutableGameState.activeOfferable(item: ItemDefinition): Boolean {
    val effect = item.active ?: return true
    if (!(content.build.policy.offerLanes.activeWeight > 0f) || item.unlockLevel > level) return false
    return effect != SkillId.CATALYZE || itemStacks[item.id] > 0 || catalyzeOfferable()
}

/**
 * An item offer's card for [item]. An active item's card also names its effect, the rank it reaches (stacks + 1) and the
 * slot it binds to or ranks, or says it would replace a slot while every slot holds another active.
 */
internal fun MutableGameState.itemCard(item: ItemDefinition, tag: String, reborn: Boolean): ChoiceOption {
    val effect = item.active
    val owned = effect != null && skillLoadout.owns(effect)
    val replaces = effect != null && needsActiveSlot(item)
    return ChoiceOption(
        type = ChoiceType.ITEM,
        title = item.name,
        description = item.description,
        tag = tag,
        itemId = item.id,
        reborn = reborn,
        skillId = effect,
        skillRank = if (effect != null) itemStacks[item.id] + 1 else null,
        activeSlot = when {
            effect == null || replaces -> null
            owned -> skillLoadout.slotOf(effect)
            else -> skillLoadout.lowestEmptySlot()
        },
        replacesActive = replaces,
    )
}

/** The items of the rolled [rarity], else of the nearest lower tier down to [floor], else of the nearest higher tier. */
private fun List<ItemDefinition>.nearestRarityTier(rarity: ItemRarity, floor: ItemRarity): List<ItemDefinition> {
    val order = ItemRarity.entries.filter { it.rank in floor.rank..rarity.rank }.asReversed() +
        ItemRarity.entries.filter { it.rank > rarity.rank }
    return order.firstNotNullOfOrNull { tier -> filter { it.rarity == tier }.takeIf { it.isNotEmpty() } } ?: this
}

/** Called when an item offer is resolved (not rerolled): advances or resets the rarity pity counter. */
internal fun MutableGameState.resolveItemOfferPity(chosen: ItemDefinition?) {
    offerPity = if (chosen != null && chosen.rarity.rank >= ItemRarity.RARE.rank) 0 else offerPity + 1
}

internal fun MutableGameState.rebornChance(): Float {
    val policy = content.build.policy
    var chance = policy.rebornChance(rebirthLevel)
    if (tagTier(BuildTag.FORTUNE) >= 3) chance += 0.1f
    chance += matrix.rebornChanceBonus
    return chance.coerceIn(0f, 0.5f)
}

/** Base card odds are Legendary 2%, Epic 5%, Rare 20%, Uncommon 34% and Common 39%; each luck point (up to 2) lowers the roll by 0.08. */
internal fun MutableGameState.rollRarity(): ItemRarity {
    val roll = rewardRandom.nextFloat() - effectiveLuck().coerceIn(0f, 2f) * 0.08f
    return when {
        roll < 0.02f -> ItemRarity.LEGENDARY
        roll < 0.07f -> ItemRarity.EPIC
        roll < 0.27f -> ItemRarity.RARE
        roll < 0.61f -> ItemRarity.UNCOMMON
        else -> ItemRarity.COMMON
    }
}

private fun MutableGameState.weightedIndex(weights: FloatArray): Int {
    var total = 0f
    for (weight in weights) total += max(0f, weight)
    if (total <= 0f) return rewardRandom.nextInt(weights.size)
    var roll = rewardRandom.nextFloat() * total
    for (index in weights.indices) {
        roll -= max(0f, weights[index])
        if (roll <= 0f) return index
    }
    return weights.lastIndex
}

// ---------------------------------------------------------------------------------------------
// Relic offers.

/** Relics that may appear in an offer: Rebirth-rank gated, not spent, not maxed, Crown never twice. */
internal fun MutableGameState.offerableRelics(): List<RelicDefinition> = content.relics.filter { relic ->
    relic.minimumRebirth <= rebirthRank &&
        !(relic.id == RelicId.OUROBOROS_SEAL && ouroborosSpent) &&
        relicRank(relic.id) < content.relicPolicy.maxRank &&
        !(relic.id == RelicId.HOLLOW_CROWN && relicRank(relic.id) > 0)
}

private fun MutableGameState.relicWeight(relic: RelicDefinition, concordLane: Boolean): Float {
    val bound = equippedRelics.map { it.id }
    var weight = 1f
    if (relicRank(relic.id) > 0) weight *= 0.5f
    val attuned = relic.attunedTags.sumOf { build.tagPoints(it) }
    weight *= 1f + 0.15f * min(attuned, 8)
    if (concordLane) {
        val concord = bound.any { id ->
            content.build.pairing(relic.id, id)?.kind == kinetickk.ball.content.api.RelicPairingKind.CONCORD
        }
        weight *= if (concord) 4f else 0.6f
    }
    return weight
}

internal fun MutableGameState.buildRelicChoices() {
    heirloomOfferActive = false
    val pool = offerableRelics()
    val selected = mutableListOf<RelicDefinition>()
    repeat(3) { slot ->
        val available = pool.filter { candidate -> selected.none { it.id == candidate.id } }
        if (available.isEmpty()) return@repeat
        val concordLane = slot == 0 && rewardRandom.nextFloat() < 0.55f
        val weights = FloatArray(available.size) { relicWeight(available[it], concordLane) }
        selected += available[weightedIndex(weights)]
    }
    choices = buildList {
        selected.forEach { relic ->
            val currentRank = relicRank(relic.id)
            add(ChoiceOption(
                type = ChoiceType.RELIC,
                title = relic.name,
                description = if (currentRank > 0) "Merge the duplicate resonance and advance rank $currentRank to ${currentRank + 1}." else relic.upside,
                tag = relic.aspect.displayLabel.uppercase(),
                relicId = relic.id,
                relicAction = RelicChoiceAction.ACQUIRE,
            ))
        }
        add(relicOfferTailCard())
    }
}

/**
 * The fourth card of every relic offer, so no offer forces a replacement: Meld (or a salvage when no bound
 * relic can rise) while all slots are full, otherwise leaving the slot hollow.
 */
internal fun MutableGameState.relicOfferTailCard(): ChoiceOption = when {
    equippedRelics.size < content.relicPolicy.maxSlots -> ChoiceOption(
        type = ChoiceType.RELIC,
        title = "Leave hollow",
        description = "Keep the slot empty and salvage the resonance for Kinetic Matter.",
        tag = "DECLINE",
        relicAction = RelicChoiceAction.DECLINE,
    )
    equippedRelics.any { canMeld(it.id) } -> ChoiceOption(
        type = ChoiceType.RELIC,
        title = "Meld resonance",
        description = "Collapse this offering into a bound Relic and raise it to rank 2.",
        tag = "MELD",
        relicAction = RelicChoiceAction.MELD,
    )
    else -> ChoiceOption(
        type = ChoiceType.RELIC,
        title = "Salvage resonance",
        description = "Collapse this offering into a bound Relic for Kinetic Matter.",
        tag = "MELD",
        relicAction = RelicChoiceAction.MELD,
    )
}

/** Generic Meld raises a relic to rank 2 only; rank 3 needs the relic itself to be offered again. */
internal fun MutableGameState.canMeld(id: RelicId): Boolean =
    content.relic(id).meldable && relicRank(id) < min(2, content.relicPolicy.maxRank) &&
        !(id == RelicId.OUROBOROS_SEAL && ouroborosSpent)

/** Carried Relics the Heirloom offer may still show: known to content and not bound this run. */
private fun MutableGameState.openHeirloomCandidates(): List<RelicId> =
    heirloomCandidates.filter { id -> content.relics.any { it.id == id } && relicRank(id) == 0 }

/**
 * Heirloom cards for [candidates] carried Relics: the policy count plus [extra], never more than the
 * carried Relics or the choice bound, where the decline card keeps one slot.
 */
private fun MutableGameState.heirloomCardCount(candidates: Int, extra: Int): Int =
    min(content.relicPolicy.heirloomOffers(rebirthRank) + extra, min(candidates, MutableGameState.MAX_CHOICES - 1))

/**
 * Matrix extra Heirloom cards the open offer cannot show (it already shows every carried Relic or fills
 * the bound). Each one raises the taken Heirloom a rank instead, so the extra is never lost.
 */
internal fun MutableGameState.heirloomRankBonus(): Int {
    val candidates = openHeirloomCandidates().size
    val extra = matrix.extraHeirloomOffers
    return extra - (heirloomCardCount(candidates, extra) - heirloomCardCount(candidates, 0))
}

/**
 * The Heirloom offer opens a run with Relics carried over from the last victory. It reads the rank, so a
 * player who drops back to a lower tier keeps the offer instead of losing the stored Relics.
 */
internal fun MutableGameState.openHeirloomChoice() {
    val candidates = openHeirloomCandidates()
    heirloomPending = false
    if (candidates.isEmpty() || rebirthRank < 1) {
        if (heirloomCandidates.isNotEmpty()) pendingHeirloomResolved = true
        return
    }
    val count = heirloomCardCount(candidates.size, matrix.extraHeirloomOffers)
    val offered = candidates.shuffled(rewardRandom).take(count)
    activeChoiceType = ChoiceType.RELIC
    heirloomOfferActive = true
    choices = offered.map { id ->
        val relic = content.relic(id)
        ChoiceOption(
            type = ChoiceType.RELIC,
            title = relic.name,
            description = relic.upside,
            tag = "HEIRLOOM",
            relicId = id,
            relicAction = RelicChoiceAction.ACQUIRE,
            heirloom = true,
        )
    } + ChoiceOption(
        type = ChoiceType.RELIC,
        title = "Leave the heirloom",
        description = "Start without it and gain two rerolls.",
        tag = "DECLINE",
        relicAction = RelicChoiceAction.DECLINE,
        heirloom = true,
    )
    clearInputBuffers()
    phase = kinetickk.ball.gameplay.nucleus.render.GamePhase.CHOICE
}
