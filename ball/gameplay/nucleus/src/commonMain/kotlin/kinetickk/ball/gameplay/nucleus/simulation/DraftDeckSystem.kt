// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.isDataEconomy
import kinetickk.ball.content.api.lifetimeMatterOfferLevel
import kinetickk.ball.content.api.usesSkills
import kinetickk.ball.gameplay.nucleus.model.ItemDeck
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deals the run's draft deck from its own stream (seed xor [MutableGameState.DECK_SEED_SALT]), so no reward draw moves.
 * Called once when the run starts. The universe is every item the run's Rebirth rank allows (unlock levels gate offers,
 * not the deal). Each rarity tier is ordered by weighted draws without replacement in id order, each weight the item's
 * history deck weight, raised for items the Profile has never discovered; the first share of each order is dealt, at
 * least the tier's minimum. The run's Keynote (a BuildTag and an element) is drawn from the same stream after every
 * dealing draw, so it never moves the dealt order; a full deck still draws it, from a fresh stream. Floors then grow the
 * deck, in order: early choice, an early Rare or better, the rank-0 Data ladder (the policy's floor plus the Matrix's
 * Data stratum, then its early part), the character's archetype, every archetype, every element, the Keynote's tag and
 * element shares, the wagers and skill support; a shortfall deals the best-priority qualifying reserve items, tiers
 * Common to Legendary, and nothing is ever removed. Active items are dealt by their tier's share like any item, but
 * every floor counts and promotes passive items only.
 * Float sums run in a fixed order with no transcendental functions, so every platform deals the same deck.
 */
internal fun MutableGameState.drawItemDeck() {
    val policy = content.build.policy.draftDeck
    val keynote = content.variety.keynote
    keynoteTag = null
    keynoteElement = null
    if (policy.dealsEverything) {
        itemDeck = ItemDeck.EVERYTHING
        if (keynote.enabled) rollKeynote(CloneableXorWowRandom(deckSeed))
        return
    }
    val random = CloneableXorWowRandom(deckSeed)
    val universe = content.items.filter { it.minimumRebirth <= rebirthRank }
    val order = IntArray(universe.size)
    val tierSizes = IntArray(ItemRarity.entries.size)
    val dealt = BooleanArray(content.items.size)
    var next = 0
    for (tier in ItemRarity.entries) {
        val ids = universe.filter { it.rarity == tier }.map { it.id }
        val weights = FloatArray(ids.size) { index ->
            val id = ids[index]
            itemHistory.deckWeight(id) * if (id in discoveredItemIds) 1f else policy.undiscoveredWeight
        }
        val taken = BooleanArray(ids.size)
        val start = next
        repeat(ids.size) {
            var total = 0f
            for (index in ids.indices) if (!taken[index]) total += weights[index]
            var roll = random.nextFloat() * total
            var pick = -1
            for (index in ids.indices) {
                if (taken[index]) continue
                pick = index
                roll -= weights[index]
                if (roll <= 0f) break
            }
            taken[pick] = true
            order[next++] = ids[pick]
        }
        tierSizes[tier.ordinal] = ids.size
        val share = (policy.shares[tier.ordinal] * ids.size).roundToInt()
        val dealtCount = share.coerceIn(min(policy.minimums[tier.ordinal], ids.size), ids.size)
        for (index in start until start + dealtCount) dealt[order[index]] = true
    }
    if (keynote.enabled) rollKeynote(random)

    // Floors guarantee passive coverage: an active item never counts toward one and is never promoted by one.
    fun promote(floor: Int, qualifies: (ItemDefinition) -> Boolean) {
        val passive: (ItemDefinition) -> Boolean = { !it.isActive && qualifies(it) }
        val target = min(floor, universe.count(passive))
        var missing = target - universe.count { dealt[it.id] && passive(it) }
        for (id in order) {
            if (missing <= 0) return
            if (dealt[id] || !passive(content.items[id])) continue
            dealt[id] = true
            missing--
        }
    }
    fun ItemDefinition.atMostRare(): Boolean = rarity.rank <= ItemRarity.RARE.rank
    fun ItemDefinition.dataLadder(): Boolean = isDataEconomy && atMostRare() && minimumRebirth == 0
    promote(policy.earlyOpenFloor) { it.rarity.rank <= ItemRarity.UNCOMMON.rank && it.unlockLevel <= policy.earlyOpenUnlock }
    promote(policy.earlyRareFloor) { it.rarity.rank >= ItemRarity.RARE.rank && it.unlockLevel <= policy.earlyRareUnlock }
    promote(policy.dataEconomyFloor + matrix.dataStratumItems) { it.dataLadder() }
    promote(policy.earlyDataFloor) { it.dataLadder() && it.unlockLevel <= policy.earlyDataUnlock }
    val characterTag = coreShape.characterTag()
    promote(policy.characterTagFloor) { it.atMostRare() && characterTag in it.tags }
    BuildTag.entries.forEach { tag -> promote(policy.tagFloor) { it.atMostRare() && tag in it.tags } }
    StatusElement.entries.forEach { element -> promote(policy.elementFloor) { it.atMostRare() && element in it.elements } }
    keynoteTag?.let { tag ->
        promote(keynoteFloor(keynote.tagShare, universe.count { it.isKeynotePassive(tag) })) { it.isKeynotePassive(tag) }
    }
    keynoteElement?.let { element ->
        promote(keynoteFloor(keynote.elementShare, universe.count { it.isKeynotePassive(element) })) { it.isKeynotePassive(element) }
    }
    promote(policy.riskFloor) { it.risk }
    promote(policy.skillFloor) { it.usesSkills }
    itemDeck = ItemDeck.dealt(content.items.size, order, tierSizes, content.items.indices.filter { dealt[it] })
}

/**
 * The catalog level ordinary offers open for passive items: the run level or the lifetime-Matter level, whichever is
 * higher. Active items unlock by the run level alone ([activeOfferable]).
 */
internal fun MutableGameState.offerCatalogLevel(): Int = max(level, lifetimeMatterOfferLevel(lifetimeMatter))

/**
 * True when [item] could be offered now if the deck had dealt it: unlocked (a passive item by [catalogLevel], an active
 * item by the run level and the other active rules of [activeOfferable]), Rebirth-eligible and useful.
 */
internal fun MutableGameState.offerableOutsideDeck(item: ItemDefinition, catalogLevel: Int = offerCatalogLevel()): Boolean =
    (if (item.isActive) activeOfferable(item) else item.unlockLevel <= catalogLevel) &&
        item.minimumRebirth <= rebirthRank && hasUsefulItemEffect(item)

/**
 * Keeps a tier from running dry: a tier holding fewer than the policy's refill floor of dealt items the run can still
 * take (rank-eligible and useful, at any unlock level) deals its next reserve items that are offerable now, in priority
 * (reserve items not offerable yet stay in reserve). Unlock levels are not a shortage: early in a run a tier holds few
 * unlocked items with or without a deck, and the rolled rarity falls to the nearest tier as it always has. No draw; the
 * deck is replaced only when something is dealt.
 */
internal fun MutableGameState.refillItemDeck() {
    val deck = itemDeck
    if (deck.dealsEverything) return
    val floor = content.build.policy.draftDeck.refillFloor
    val catalogLevel = offerCatalogLevel()
    val newlyDealt = ArrayList<Int>()
    for (tier in ItemRarity.entries) {
        var takeable = content.items.count {
            it.rarity == tier && it.id in deck && it.minimumRebirth <= rebirthRank && hasUsefulItemEffect(it)
        }
        if (takeable >= floor) continue
        for (id in deck.reserveOf(tier)) {
            if (!offerableOutsideDeck(content.items[id], catalogLevel)) continue
            newlyDealt += id
            if (++takeable >= floor) break
        }
    }
    if (newlyDealt.isNotEmpty()) itemDeck = deck.withDealt(newlyDealt)
}

/**
 * A directed reward's item pool: the dealt items among [eligible], topped up to [size] with eligible reserve items in
 * deal priority when the deck holds too few (no draw).
 */
internal fun MutableGameState.deckPool(eligible: List<ItemDefinition>, size: Int): List<ItemDefinition> {
    val dealt = eligible.filter { it.id in itemDeck }
    if (dealt.size >= size) return dealt
    val eligibleIds = eligible.mapTo(HashSet()) { it.id }
    return dealt + itemDeck.reserve().filter { it in eligibleIds }.take(size - dealt.size).map { content.items[it] }
}
