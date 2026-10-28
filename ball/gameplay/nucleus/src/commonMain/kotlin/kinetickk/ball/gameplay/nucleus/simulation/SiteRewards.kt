// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.cos
import kotlin.math.sin

/** Grants every part of [reward] at ([x], [y]); [scatterSeed] places Data and repair pickups without any stream draw. */
internal fun MutableGameState.grantSiteReward(reward: SiteReward, x: Float, y: Float, scatterSeed: Long) {
    reward.directed?.let { if (!enqueueDirectedReward(it)) salvageReward() }
    repeat(reward.itemOffers) { pendingLevelChoices++ }
    reward.itemOfferFloor?.let { if (!enqueueFloorOffer(it)) salvageReward() }
    repeat(reward.keys) { k -> addPickup(Pickup(PickupType.KEY, x + 18f * k, y)) }
    repeat(reward.relics) { addPickup(Pickup(PickupType.RELIC, x + 15f, y - 8f)) }
    scatterPickups(PickupType.REPAIR, reward.repairs, x, y, scatterSeed)
    scatterPickups(PickupType.DATA, reward.data, x, y, scatterSeed xor 0x5A)
    if (reward.matter > 0) grantMatter(reward.matter.toFloat())
    if (reward.heal > 0f) heal(maxHp * reward.heal, HealSource.REPAIR)
    if (reward.overdrive > 0f) gainOverdrive(reward.overdrive)
    rerollsRemaining += reward.rerolls
}

/**
 * The only writer of pendingDirectedRewards. Until the window sites draw kinds by weight the queue keeps its 0.5.0
 * contract: at most six scheduled rewards per run, so a seventh is a broken invariant, never a salvage.
 */
internal fun MutableGameState.enqueueDirectedReward(reward: DirectedReward): Boolean {
    check(pendingDirectedRewards.size < 6) { "At most six scheduled POI rewards per run" }
    pendingDirectedRewards = pendingDirectedRewards + reward
    return true
}

/** Queues a rarity-floor item offer; false (nothing queued) when the queue already holds its bound. */
internal fun MutableGameState.enqueueFloorOffer(floor: ItemRarity): Boolean {
    if (pendingFloorOffers.size >= content.sites.maxPendingFloorOffers) return false
    pendingFloorOffers = pendingFloorOffers + floor
    return true
}

/** A refused queue entry: 8 Matter and the REWARD SALVAGED notice (never a crash). */
private fun MutableGameState.salvageReward() {
    grantMatter(8f)
    message = SiteNotice.REWARD_SALVAGED.english
    messageTime = 1.6f
}

/** Data or repair pickups on a ring: angle i·TAU/n plus hashed jitter, speed 60..140, from [scatterSeed]. */
private fun MutableGameState.scatterPickups(type: PickupType, count: Int, x: Float, y: Float, scatterSeed: Long) {
    if (count <= 0) return
    val random = HashRandom(scatterSeed)
    val spacing = TAU / count
    for (index in 0 until count) {
        val angle = index * spacing + (random.nextFloat() - 0.5f) * spacing * 0.5f
        val pickupSpeed = 60f + random.nextFloat() * 80f
        addPickup(Pickup(type, x, y, cos(angle) * pickupSpeed, sin(angle) * pickupSpeed))
    }
}

/** Records a world discovery once; the pending mask publishes it with the next progress update. */
internal fun MutableGameState.discoverWorld(id: WorldDiscovery) {
    if (worldDiscoveryMask and id.bit != 0) return
    worldDiscoveryMask = worldDiscoveryMask or id.bit
    pendingWorldDiscoveryMask = pendingWorldDiscoveryMask or id.bit
}

internal fun MutableGameState.hasSecret(flag: RunSecretFlag): Boolean = runSecretFlags and (1 shl flag.ordinal) != 0

internal fun MutableGameState.setSecret(flag: RunSecretFlag) {
    runSecretFlags = runSecretFlags or (1 shl flag.ordinal)
}

/**
 * Opens an item offer whose cards roll at least [floor] (rerolls keep it; [finishChoice] clears it). An exhausted
 * catalog salvages 8 Matter instead.
 */
internal fun MutableGameState.openItemOfferWithFloor(floor: ItemRarity) {
    offerRarityFloor = floor
    activeChoiceType = ChoiceType.ITEM
    itemOffersOpened++
    buildItemChoices()
    if (choices.isEmpty()) {
        grantMatter(8f)
        message = "CATALOG COMPLETE // MATTER SALVAGED"
        messageTime = 1.5f
        offerRarityFloor = null
        finishChoice(GameplayAudioCue.UI_CLICK)
        return
    }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}
