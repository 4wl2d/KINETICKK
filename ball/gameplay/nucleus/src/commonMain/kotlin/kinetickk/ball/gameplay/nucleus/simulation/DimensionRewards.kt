// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionReward
import kinetickk.ball.content.api.DimensionRewardKind
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RunBoonDefinition
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableListAppending

/*
 * Dimension rewards through the choice pipeline: a finished visit queues its grant, and the queue opens it after the
 * Relic choices. The Zenith deals Blessing cards, the Nadir Pact cards and a Refuse card, the Archive a trove of
 * Legendary items (its Matter is paid on the way out). Hands come from stateless hashes of the run and the layer, so
 * a reward never draws from a run stream. A dimension reward is never rerolled and leaves the item pity alone.
 */

/** Salt of the dimension reward hands (registered in the lane notes). */
internal const val BOON_SALT: Long = 0x0D13_E5A1_0000_0005L

/** Hash keys of the two hands a layer may deal. */
private const val BOON_HAND: Int = 0
private const val TROVE_HAND: Int = 1
/** Matter salvaged when a boon offer finds nothing to deal (every boon of its kind owned, or the boon bound reached). */
private const val BOON_SALVAGE_MATTER: Float = 20f
/** Matter salvaged when the Archive's trove finds no Legendary or Epic item to deal. */
private const val TROVE_SALVAGE_MATTER: Float = 40f
/** The Pact offer's decline card (English content keys). */
internal const val PACT_DECLINE_TITLE: String = "Refuse the pact"

/** The decline card's description, naming the [matter] the card salvages (localized by a content message template). */
internal fun pactDeclineDescription(matter: Int): String = "Keep the run as it is and salvage $matter Kinetic Matter."
/** Card tags (English content keys; uppercase variants localize). */
private const val BLESSING_TAG: String = "BLESSING"
private const val PACT_TAG: String = "PACT"
private const val DECLINE_TAG: String = "DECLINE"

internal fun MutableGameState.hasPendingDimensionReward(): Boolean = dimensionRun.pendingRewards.isNotEmpty()

/**
 * Opens the oldest waiting dimension reward (it leaves the queue first, like a directed reward). A reward that deals
 * no card (a failed Archive visit, already paid in Matter) or salvages instead leaves the run running.
 */
internal fun MutableGameState.openPendingDimensionReward() {
    val pending = dimensionRun.pendingRewards
    if (pending.isEmpty()) return
    val grant = pending[0]
    dimensionRun = dimensionRun.copy(pendingRewards = pending.subList(1, pending.size).toImmutableList())
    val reward = content.dimensions.definition(grant.layer).reward
    val cards = if (grant.completed) reward.cardsOnSuccess else reward.cardsOnFailure
    if (cards <= 0) return
    when (reward.kind) {
        DimensionRewardKind.NONE -> Unit
        DimensionRewardKind.BLESSING_OFFER -> openBoonOffer(grant, RunBoonKind.BLESSING, cards, reward)
        DimensionRewardKind.PACT_OFFER -> openBoonOffer(grant, RunBoonKind.PACT, cards, reward)
        DimensionRewardKind.ARCHIVE_TROVE -> openArchiveTrove(grant, cards)
    }
}

/**
 * Deals [count] boons of [kind] the run does not own, without replacement over the boons in id order, from a hash of
 * the run and the layer; a Pact offer adds the Refuse card (at most [MutableGameState.MAX_CHOICES] cards). With nothing
 * to deal, or the run's boons at [ContentBounds.MAX_RUN_BOONS], it salvages Matter instead.
 */
private fun MutableGameState.openBoonOffer(grant: DimensionRewardGrant, kind: RunBoonKind, count: Int, reward: DimensionReward) {
    val eligible = ArrayList<RunBoonDefinition>()
    if (runBoons.size < ContentBounds.MAX_RUN_BOONS) {
        for (boon in content.boons) if (boon.kind == kind && boon.id !in runBoons) eligible += boon
        eligible.sortBy { it.id }
    }
    if (eligible.isEmpty()) {
        grantMatter(BOON_SALVAGE_MATTER)
        return
    }
    val random = HashRandom(worldHash(runSeed, grant.layer, BOON_HAND, 0, BOON_SALT))
    val tag = when (kind) {
        RunBoonKind.BLESSING -> BLESSING_TAG
        RunBoonKind.PACT -> PACT_TAG
    }
    val cards = ArrayList<ChoiceOption>(count + 1)
    while (cards.size < count && eligible.isNotEmpty()) {
        val boon = eligible.removeAt(random.nextIndex(eligible.size))
        cards += ChoiceOption(type = ChoiceType.BOON, title = boon.name, description = boon.description, tag = tag, boonId = boon.id)
    }
    if (reward.kind == DimensionRewardKind.PACT_OFFER) {
        cards += ChoiceOption(
            type = ChoiceType.BOON,
            title = PACT_DECLINE_TITLE,
            description = pactDeclineDescription(reward.declineMatter),
            tag = DECLINE_TAG,
            salvageMatter = reward.declineMatter,
        )
    }
    openDimensionChoice(grant, ChoiceType.BOON, cards)
}

/**
 * The Archive's trove: [count] Legendary item cards the run could take (dealt or in the deck's reserve, offerable now,
 * not at max stacks, at most one active), topped up with Epic ones, from a hash of the run; with none of either it
 * salvages Matter instead. The cards keep the ordinary item card (no Reborn roll, so no draw).
 */
private fun MutableGameState.openArchiveTrove(grant: DimensionRewardGrant, count: Int) {
    refillItemDeck()
    val random = HashRandom(worldHash(runSeed, grant.layer, TROVE_HAND, 0, BOON_SALT))
    val picks = ArrayList<ItemDefinition>(count)
    pickTroveItems(ItemRarity.LEGENDARY, count, random, picks)
    if (picks.size < count) pickTroveItems(ItemRarity.EPIC, count, random, picks)
    if (picks.isEmpty()) {
        grantMatter(TROVE_SALVAGE_MATTER)
        message = "CATALOG COMPLETE // MATTER SALVAGED"
        messageTime = 1.5f
        return
    }
    openDimensionChoice(grant, ChoiceType.ITEM, picks.map { itemCard(it, it.rarity.displayLabel.uppercase(), reborn = false) })
}

/** Adds items of [rarity] to [picks] until it holds [count]: drawn from [random], an active only while none is picked. */
private fun MutableGameState.pickTroveItems(rarity: ItemRarity, count: Int, random: HashRandom, picks: MutableList<ItemDefinition>) {
    val eligible = content.items.filter { item ->
        item.rarity == rarity && itemStacks[item.id] < item.maxStacks && offerableOutsideDeck(item) && picks.none { it.id == item.id }
    }
    val pool = deckPool(eligible, count).toMutableList()
    while (picks.size < count && pool.isNotEmpty()) {
        val item = pool.removeAt(random.nextIndex(pool.size))
        if (item.isActive && picks.any { it.isActive }) continue
        picks += item
    }
}

private fun MutableGameState.openDimensionChoice(grant: DimensionRewardGrant, type: ChoiceType, cards: List<ChoiceOption>) {
    activeChoiceType = type
    choices = cards
    dimensionRewardLayer = grant.layer
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/**
 * A taken boon card: the boon for the rest of the run, or the Refuse card's salvage. Returns the card's sound
 * (choose finishes the choice).
 */
internal fun MutableGameState.takeBoonCard(option: ChoiceOption): GameplayAudioCue {
    val boonId = option.boonId
    if (boonId == null) {
        grantMatter((option.salvageMatter ?: 0).toFloat())
        return GameplayAudioCue.UI_CLICK
    }
    applyRunBoon(boonId)
    return GameplayAudioCue.WEAPON_ACQUIRED
}

/**
 * Takes boon [id] for the rest of the run: its gains, then its costs, through [applyRunModifier], and it joins the
 * run's boons. Refused before anything changes once the run holds [ContentBounds.MAX_RUN_BOONS] or for an unknown id.
 * [preview] (reward previews, on a candidate copy) rebuilds no world.
 */
internal fun MutableGameState.applyRunBoon(id: Int, preview: Boolean = false) {
    check(runBoons.size < ContentBounds.MAX_RUN_BOONS) { "A run holds at most ${ContentBounds.MAX_RUN_BOONS} boons" }
    val boon = checkNotNull(content.boon(id)) { "Unknown boon $id" }
    for (index in boon.gains.indices) applyRunModifier(boon.gains[index], preview)
    for (index in boon.costs.indices) applyRunModifier(boon.costs[index], preview)
    runBoons = runBoons.toImmutableListAppending(id)
}
