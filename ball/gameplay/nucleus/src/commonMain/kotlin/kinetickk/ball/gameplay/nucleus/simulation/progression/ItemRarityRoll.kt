// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*

/*
 * The item card's rarity (content: ItemRarityPolicy). Every source of item cards rolls here: ordinary and Elite Bounty
 * offers, rarity-floor offers (the floor raises the roll) and directed item rewards. The odds read the run's Luck and
 * the items of each tier it owns, so every Epic and Legendary a run takes, from any source, makes the next one rarer.
 */

/** Tiers above Common from the rarest down: the order a roll walks before Common takes the rest. */
private val RollOrder: List<ItemRarity> = ItemRarity.entries.drop(1).asReversed()

/** How many distinct items of each tier the run holds now, by [ItemRarity] ordinal. */
internal fun MutableGameState.ownedItemsByRarity(): IntArray {
    val owned = IntArray(ItemRarity.entries.size)
    for (item in content.items) if (itemStacks[item.id] > 0) owned[item.rarity.ordinal]++
    return owned
}

/** The odds of [rarity] on the next card: the policy's weight at the run's Luck and owned items, Common the rest. */
internal fun MutableGameState.itemRarityOdds(rarity: ItemRarity): Float {
    val policy = content.build.policy.rarity
    val luck = effectiveLuck()
    val owned = ownedItemsByRarity()
    if (rarity != ItemRarity.COMMON) return policy.weight(rarity, luck, owned[rarity.ordinal])
    return 1f - RollOrder.sumOf { policy.weight(it, luck, owned[it.ordinal]).toDouble() }.toFloat()
}

/** One card's rarity from one [rewardRandom] draw ([itemRarityOdds]). */
internal fun MutableGameState.rollRarity(): ItemRarity {
    val policy = content.build.policy.rarity
    val luck = effectiveLuck()
    val owned = ownedItemsByRarity()
    var roll = rewardRandom.nextFloat()
    for (rarity in RollOrder) {
        roll -= policy.weight(rarity, luck, owned[rarity.ordinal])
        if (roll < 0f) return rarity
    }
    return ItemRarity.COMMON
}

/**
 * The volatile card's lift of a rolled [rarity] by one rank when its [laneRoll] falls below the lane policy's lift
 * chance times the tier's lift share (never into Legendary, half as often into Epic). No draw.
 */
internal fun MutableGameState.volatileLift(rarity: ItemRarity, laneRoll: Float): ItemRarity {
    val policy = content.build.policy
    val chance = policy.offerLanes.volatileLiftChance * policy.rarity.liftShares[rarity.ordinal]
    return if (laneRoll < chance) ItemRarity.entries[rarity.ordinal + 1] else rarity
}

/**
 * The items of the rolled [rarity], else of the nearest lower tier down to [floor], else of the nearest higher tier,
 * else (a rarity floor no remaining item meets) of the nearest tier below [floor].
 */
internal fun List<ItemDefinition>.nearestRarityTier(rarity: ItemRarity, floor: ItemRarity): List<ItemDefinition> {
    val order = ItemRarity.entries.filter { it.rank in floor.rank..rarity.rank }.asReversed() +
        ItemRarity.entries.filter { it.rank > rarity.rank } +
        ItemRarity.entries.filter { it.rank < floor.rank }.asReversed()
    return order.firstNotNullOfOrNull { tier -> filter { it.rarity == tier }.takeIf { it.isNotEmpty() } } ?: this
}

/**
 * Rerolls the open item offer inside its cards' rarities: each card keeps its tier and draws a new item in it, so a
 * reroll improves the choice but never the odds. A floor offer keeps its floor the same way.
 */
internal fun MutableGameState.rerollItemChoices() {
    val locked = choices.mapNotNull { option -> option.itemId?.let { content.items[it].rarity } }
    buildItemChoices(lockedRarities = locked.ifEmpty { null })
}
