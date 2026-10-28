// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max

/*
 * Run-long modifiers (the run variety lane owns this file): the run-mutator mask on top of the Rebirth tier's
 * mutators and the one fold every omen, Blessing and Pact effect goes through. No run applies one yet.
 */

/** True when [mutator] runs this run: the Rebirth tier's own mutators or one a run modifier added. */
internal fun MutableGameState.runMutatorActive(mutator: RebirthMutator): Boolean =
    rebirthProfile.has(mutator) || runMutatorMask and (1 shl mutator.ordinal) != 0

/**
 * Applies one run modifier through the existing seams. [preview] (reward previews) skips what would rebuild the world.
 */
internal fun MutableGameState.applyRunModifier(effect: RunModifierEffect, preview: Boolean = false) {
    when (effect) {
        is RunModifierEffect.Stat -> applyStatDelta(effect.effect, effect.amount)
        is RunModifierEffect.MatterGainScale -> matterGain = max(0.1f, matterGain * effect.factor)
        is RunModifierEffect.Rerolls -> rerollsRemaining = max(0, rerollsRemaining + effect.delta)
        is RunModifierEffect.HostileScale -> activeRebirthProfile = activeRebirthProfile.copy(
            enemyHealthMultiplier = activeRebirthProfile.enemyHealthMultiplier * effect.health,
            enemySpeedMultiplier = activeRebirthProfile.enemySpeedMultiplier * effect.speed,
            incomingDamageMultiplier = activeRebirthProfile.incomingDamageMultiplier * effect.incomingDamage,
            spawnRateMultiplier = activeRebirthProfile.spawnRateMultiplier * effect.spawnRate,
            eliteRateMultiplier = activeRebirthProfile.eliteRateMultiplier * effect.eliteRate,
        )
        is RunModifierEffect.Mutator -> {
            runMutatorMask = runMutatorMask or (1 shl effect.mutator.ordinal)
            if (!preview && effect.mutator == RebirthMutator.FRACTURED_SPACE) resetObstacles()
        }
        is RunModifierEffect.KeynoteFloor -> promoteKeynoteFloors(effect.share)
    }
}

/**
 * A new run: no omen, no added mutators, no world event, signal or rarity floor. The rolls made at run start
 * ([startRunVariety]), the Keynote drawn with the deal ([drawItemDeck]) and the omen flag set beside the Heirloom are
 * not touched.
 */
internal fun MutableGameState.resetRunVariety() {
    runOmen = null
    runMutatorMask = 0
    surgeIndex = 0
    surgeThemeType = -1
    worldEvent = null
    worldEventPulse = 0
    worldEventNextPulseAt = 0f
    eventSignals = immutableListOf()
    nextSignalSlot = 0
    signalsSpawned = 0
    signalsEngaged = 0
    pendingSignalPrompts = immutableListOf()
    offerRarityFloor = null
}

/**
 * Run-start rolls, after the deal drew the Keynote and before the director's reset reads the opening calm's length: the
 * director personality, the featured aspect (the one weak to the Keynote element, or the Elemental Storm's) and the world
 * event plan.
 */
internal fun MutableGameState.startRunVariety() {
    applyDirectorPersonality(directorPersonalityFor(runSeed, content.variety.director))
    varietyAnnouncement = 0
    worldEventPlans = immutableListOf()
    nextWorldEvent = 0
}

/** Positions of the event in progress (its strikes) and of live signals follow a rebase. */
internal fun MutableGameState.rebaseRunVariety(dx: Float, dy: Float) {
    worldEvent?.let { event ->
        if (event.strikes.isNotEmpty()) {
            worldEvent = event.copy(strikes = event.strikes.map { it.copy(x = it.x - dx, y = it.y - dy) }.toImmutableList())
        }
    }
    if (eventSignals.isNotEmpty()) {
        eventSignals = eventSignals.map { it.copy(x = it.x - dx, y = it.y - dy) }.toImmutableList()
    }
}
