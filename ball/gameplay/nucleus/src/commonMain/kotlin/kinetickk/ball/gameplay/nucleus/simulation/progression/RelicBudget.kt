// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.*

/*
 * The run's Relic budget ([kinetickk.ball.content.api.RelicPolicy.acquisitionsPerRun]) and the Meld. Every Relic offer
 * is one opportunity: opening it claims it, and each of its cards (a bind, the rank-up of a duplicate, a Meld) consumes
 * it; a taken Heirloom claims one as well. Rerolls redraw the open offer and claim nothing. An Elite-class kill drops a
 * Relic only for an opportunity the schedule has unlocked; the opt-in sources (directed Relic sites, drift caches,
 * shrine and idol signals) may claim ahead of it while the budget lasts, and once it is spent they are not offered or
 * pay Matter instead. Slots stay as many as the budget, so a bind always finds an open slot.
 */

/**
 * Relic opportunities the run has claimed or still holds: claimed offers and Heirlooms, Relic offers waiting in the
 * queue, directed Relic rewards waiting in the queue and Relic resonances lying on the field.
 */
internal fun MutableGameState.relicOpportunitiesHeld(): Int {
    var held = relicRun.relicClaims + pendingChoice.pendingRelicChoices
    val directed = pendingDirectedRewards
    for (index in directed.indices) if (directed[index] == DirectedReward.RELIC) held++
    for (index in pickups.indices) if (pickups[index].type == PickupType.RELIC) held++
    return held
}

/** Relic opportunities the run may still gain: the budget less every one held, never negative. */
internal fun MutableGameState.relicBudgetLeft(): Int =
    (content.relicPolicy.acquisitionsPerRun - relicOpportunitiesHeld()).coerceAtLeast(0)

/** Relic slots a Relic may still bind into: neither bound nor sealed by a Meld. */
internal fun MutableGameState.openRelicSlots(): Int =
    (content.relicPolicy.maxSlots - equippedRelics.size - relicRun.meldedSlots).coerceAtLeast(0)

/**
 * A Relic resonance at ([x], [y]) while the run may hold one more opportunity, for an Elite-class kill ([paced]) one the
 * schedule has unlocked; otherwise the policy's salvage Matter. It draws from no stream.
 */
internal fun MutableGameState.dropRelicResonance(x: Float, y: Float, paced: Boolean) {
    val policy = content.relicPolicy
    val limit = if (paced) policy.unlockedOpportunities(elapsed) else policy.acquisitionsPerRun
    if (relicOpportunitiesHeld() < limit) addPickup(Pickup(PickupType.RELIC, x, y)) else grantMatter(policy.salvageMatter.toFloat())
}

/**
 * Claims the opportunity of a Relic offer about to open. With the budget already claimed it salvages instead and returns
 * false; every source checks the budget before it grants, so only a stray queued offer gets here.
 */
internal fun MutableGameState.claimRelicOpportunity(): Boolean {
    if (relicRun.relicClaims >= content.relicPolicy.acquisitionsPerRun) {
        salvageRelicResonance()
        return false
    }
    relicRun.relicClaims++
    return true
}

/** The salvage of a Relic opportunity that cannot be one: the policy's Matter and the RESONANCE SALVAGED notice. */
internal fun MutableGameState.salvageRelicResonance() {
    grantMatter(content.relicPolicy.salvageMatter.toFloat())
    message = RESONANCE_SALVAGED
    messageTime = 1.6f
}

/**
 * The Meld card: seals an open slot for the run and takes its lasting bonus. All damage dealt is multiplied by the
 * policy's Meld multiplier (beside the Matrix's, [kinetickk.ball.gameplay.nucleus.simulation.combat.runDamageMultiplier]),
 * and maximum integrity rises by the policy's share of its current value, healed as any integrity gain is. Without an
 * open slot it salvages.
 */
internal fun MutableGameState.meldRelicSlot() {
    if (openRelicSlots() <= 0) {
        salvageRelicResonance()
        return
    }
    val policy = content.relicPolicy
    relicRun.meldedSlots++
    relicRun.meldDamageMultiplier = policy.meldDamageMultiplier(relicRun.meldedSlots)
    applyStatDelta(ItemEffect.MAX_INTEGRITY, maxHp * policy.meldIntegrity)
    message = SLOT_SEALED
    messageTime = 1.7f
}

/** The notice of a salvaged Relic opportunity. */
internal const val RESONANCE_SALVAGED: String = "RESONANCE SALVAGED"

/** The notice of a Meld. */
internal const val SLOT_SEALED: String = "SLOT SEALED // MELD"
