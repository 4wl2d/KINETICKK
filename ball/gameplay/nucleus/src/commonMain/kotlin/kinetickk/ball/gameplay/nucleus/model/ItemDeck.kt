// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemRarity

/**
 * A run's draft deck: which item ids may reach ordinary offers. Immutable: dealing an item returns a new value, so a
 * reduction candidate shares the deck with its source until it deals. [order] holds the rank-eligible ids tier by tier
 * (Common to Legendary, `tierStart[t] until tierStart[t + 1]`) in deal priority; a tier's reserve is its undealt ids in
 * that order. Membership is a bitset over at most [ContentBounds.MAX_ITEMS] ids.
 */
internal class ItemDeck private constructor(
    private val words: LongArray,
    private val order: IntArray,
    private val tierStart: IntArray,
    /** Every id of the catalog is dealt (no deck); [order] is empty then. */
    val dealsEverything: Boolean,
) {
    init {
        require(words.size <= WORDS && tierStart.size == TIERS + 1)
    }

    operator fun contains(itemId: Int): Boolean =
        dealsEverything || itemId >= 0 && itemId < words.size * Long.SIZE_BITS &&
            words[itemId ushr 6] and (1L shl (itemId and 63)) != 0L

    /** Dealt ids, ascending. */
    fun dealtIds(): List<Int> = buildList {
        for (word in words.indices) {
            var bits = words[word]
            while (bits != 0L) {
                val bit = bits.countTrailingZeroBits()
                add(word * Long.SIZE_BITS + bit)
                bits = bits and (bits - 1L)
            }
        }
    }

    /** Rank-eligible ids that were not dealt, ascending (the Codex's "Not in this run"). */
    fun reserveIds(): List<Int> = if (dealsEverything) emptyList() else order.filter { it !in this }.sorted()

    /** The undealt ids of [rarity]'s tier in deal priority. */
    fun reserveOf(rarity: ItemRarity): List<Int> = if (dealsEverything) emptyList() else
        (tierStart[rarity.ordinal] until tierStart[rarity.ordinal + 1]).map { order[it] }.filter { it !in this }

    /** Every rank-eligible undealt id in deal priority, tier by tier. */
    fun reserve(): List<Int> = ItemRarity.entries.flatMap(::reserveOf)

    /** This deck with [itemIds] dealt too; the same value when every one is already dealt. */
    fun withDealt(itemIds: Collection<Int>): ItemDeck {
        if (dealsEverything || itemIds.all { it in this }) return this
        val next = words.copyOf()
        itemIds.forEach { id ->
            require(id >= 0 && id < next.size * Long.SIZE_BITS) { "Item $id is outside the deck" }
            next[id ushr 6] = next[id ushr 6] or (1L shl (id and 63))
        }
        return ItemDeck(next, order, tierStart, dealsEverything = false)
    }

    companion object {
        private const val TIERS = 5
        private val WORDS = (ContentBounds.MAX_ITEMS + Long.SIZE_BITS - 1) / Long.SIZE_BITS

        /** No deck: every item is dealt. */
        val EVERYTHING: ItemDeck = ItemDeck(LongArray(0), IntArray(0), IntArray(TIERS + 1), dealsEverything = true)

        /**
         * A dealt deck: [order] lists the rank-eligible ids tier by tier in priority, [tierSizes] how many belong to each
         * tier, and [dealt] which of them are dealt.
         */
        fun dealt(itemCount: Int, order: IntArray, tierSizes: IntArray, dealt: Collection<Int>): ItemDeck {
            require(itemCount in 0..ContentBounds.MAX_ITEMS && tierSizes.size == TIERS && tierSizes.sum() == order.size)
            val starts = IntArray(TIERS + 1)
            for (tier in 0 until TIERS) starts[tier + 1] = starts[tier] + tierSizes[tier]
            val words = LongArray((itemCount + Long.SIZE_BITS - 1) / Long.SIZE_BITS)
            return ItemDeck(words, order.copyOf(), starts, dealsEverything = false).withDealt(dealt)
        }
    }
}

/**
 * Run-constant down-weights from the Profile's recent acquisitions (oldest first, at most [historyLength] entries).
 * An entry of age p (0 = newest) adds a recency of 1 − p / [historyLength] to its item's penalty; the deck deals the
 * item with weight max(floor, 1 / (1 + penalty factor × penalty)), and every offer lane picks it with the square root
 * of that. An empty history weighs every item 1.
 */
internal class ItemHistoryWeights private constructor(private val deck: FloatArray) {
    fun deckWeight(itemId: Int): Float = if (itemId in deck.indices) deck[itemId] else 1f

    fun offerWeight(itemId: Int): Float = if (itemId in deck.indices) kotlin.math.sqrt(deck[itemId]) else 1f

    val isEmpty: Boolean get() = deck.isEmpty()

    companion object {
        val NONE: ItemHistoryWeights = ItemHistoryWeights(FloatArray(0))

        fun of(
            recentItemIds: List<Int>,
            itemCount: Int,
            historyLength: Int,
            penaltyFactor: Float,
            floor: Float,
        ): ItemHistoryWeights {
            require(historyLength > 0)
            val recent = recentItemIds.takeLast(historyLength).filter { it in 0 until itemCount }
            if (recent.isEmpty()) return NONE
            val penalty = FloatArray(itemCount)
            recent.forEachIndexed { index, id ->
                val age = recent.size - 1 - index
                penalty[id] += 1f - age.toFloat() / historyLength
            }
            return ItemHistoryWeights(FloatArray(itemCount) { id ->
                if (penalty[id] <= 0f) 1f else kotlin.math.max(floor, 1f / (1f + penaltyFactor * penalty[id]))
            })
        }
    }
}
