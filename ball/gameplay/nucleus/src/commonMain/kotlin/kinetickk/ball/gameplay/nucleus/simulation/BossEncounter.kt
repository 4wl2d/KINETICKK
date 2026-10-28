// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BossEncounterSpec
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.min

/*
 * The phase gate every boss-class kind with a content encounter spec shares: equal integrity parts, a per-instance hit
 * cap on the tier-0 integrity, a shift without damage at each gate and an optional enrage. The Architect's numbers are
 * its spec, and these functions run the same float operations in the same order as its own gate always did.
 */

/** The content encounter spec of [enemy]'s kind; null for every kind without a phase gate (the Herald among them). */
internal fun MutableGameState.encounterSpec(enemy: Enemy): BossEncounterSpec? = content.enemy(enemy.type).encounter

/** The Architect's spec; the content contract makes every boss carry one. */
internal fun MutableGameState.architectSpec(): BossEncounterSpec =
    checkNotNull(content.enemy(EnemyType.ARCHITECT).encounter)

/** True while the mutator that adds [spec]'s enraged phases runs this run (the Rebirth tier's or a run modifier's). */
internal fun MutableGameState.enrageMutatorActive(spec: BossEncounterSpec): Boolean {
    val mutator = spec.enrageMutator ?: return false
    return runMutatorActive(mutator)
}

/** Last phase index: phases − 1, plus the enraged phases while their mutator runs. */
internal fun MutableGameState.bossFinalPhase(spec: BossEncounterSpec): Int =
    spec.phases - 1 + if (enrageMutatorActive(spec)) spec.enragePhases else 0

/** The last phase index of [enemy]: its spec's, or the ordinary Architect's for a kind without one. */
internal fun MutableGameState.bossFinalPhaseOf(enemy: Enemy): Int =
    encounterSpec(enemy)?.let { spec -> bossFinalPhase(spec) } ?: ARCHITECT_FINAL_PHASE

/** Integrity at which [enemy]'s current phase ends: the equal parts of its bar. */
internal fun MutableGameState.bossGate(enemy: Enemy, spec: BossEncounterSpec): Float {
    val finalPhase = bossFinalPhase(spec)
    return enemy.maxHp * (finalPhase - enemy.phase) / (finalPhase + 1f)
}

/**
 * Largest amount one damage instance may remove: [BossEncounterSpec.hitCapShare] of the integrity [enemy] would have
 * without the tier's integrity multiplier, shrunk by [BossEncounterSpec.hitCapTierShrink] per selected tier but never
 * below [BossEncounterSpec.hitCapMinimumScale] of it. A kind without a spec (the Herald) takes the Architect's share.
 */
internal fun MutableGameState.bossHitCap(
    enemy: Enemy,
    spec: BossEncounterSpec = encounterSpec(enemy) ?: architectSpec(),
): Float =
    spec.hitCapShare * enemy.maxHp / rebirthProfile.enemyHealthMultiplier.coerceAtLeast(1f) *
        (1f - spec.hitCapTierShrink * rebirthProfile.tier).coerceIn(spec.hitCapMinimumScale, 1f)

/**
 * Applies the phase gate to a requested amount: nothing while shifting, at most [bossHitCap], and never past the
 * current phase's gate. Never raises integrity.
 */
internal fun MutableGameState.gateBossDamage(enemy: Enemy, spec: BossEncounterSpec, amount: Float): Float {
    if (isShifting(enemy)) return 0f
    var gated = min(amount, bossHitCap(enemy, spec))
    if (enemy.phase < bossFinalPhase(spec)) {
        val gate = bossGate(enemy, spec)
        if (enemy.hp > gate) gated = min(gated, enemy.hp - gate)
    }
    return gated
}

/** Called after every integrity write on a gated boss: reaching a gate starts the next phase. */
internal fun MutableGameState.onBossDamaged(enemy: Enemy, spec: BossEncounterSpec) {
    if (enemy.phase >= bossFinalPhase(spec) || enemy.hp <= 0f) return
    if (enemy.hp <= bossGate(enemy, spec) + enemy.maxHp * ARCHITECT_GATE_TOLERANCE) startBossPhase(enemy, spec)
}

/**
 * Enters the next phase: a shift without damage. Only boss state changes inside the damage pipeline; the Architect's
 * aspect and escorts are set here as always, every other kind's phase start runs from [updateBossPhaseStarts].
 */
private fun MutableGameState.startBossPhase(enemy: Enemy, spec: BossEncounterSpec) {
    enemy.phase++
    enemy.shiftUntil = elapsed + spec.shiftSeconds
    if (enemy.isBoss) startArchitectPhaseEffects(enemy) else enemy.phaseStartPending = true
}

/**
 * True while [enemy] is enraged: in a phase its enrage mutator adds, or [BossEncounterSpec.enrageAfterSeconds] after
 * its encounter started (an untagged boss has no encounter clock).
 */
internal fun MutableGameState.bossEnraged(enemy: Enemy, spec: BossEncounterSpec): Boolean {
    if (enrageMutatorActive(spec) && enemy.phase > spec.phases - 1) return true
    if (spec.enrageAfterSeconds <= 0f) return false
    val start = encounterStartedAt(enemy.ownerTag)
    return !start.isNaN() && elapsed - start >= spec.enrageAfterSeconds
}

/** Attack cadence factor of a boss-class kind: its spec's enrage rate while enraged, else 1. */
internal fun MutableGameState.bossAttackScale(enemy: Enemy): Float {
    val spec = encounterSpec(enemy) ?: return 1f
    return if (bossEnraged(enemy, spec)) spec.enrageAttackRate else 1f
}

/**
 * Runs the phase starts the damage pipeline left pending, once per step after enemy movement and outside every enemy
 * loop, so a Frozen boss still starts its phase and nothing spawns inside the damage pipeline.
 */
internal fun MutableGameState.updateBossPhaseStarts() {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.phaseStartPending || !enemy.alive) continue
        enemy.phaseStartPending = false
        startMiniOrSecretPhase(enemy)
    }
}

/** The phase-start effects of the mini and secret bosses; each kind adds its branch with its behaviour. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
private fun MutableGameState.startMiniOrSecretPhase(enemy: Enemy) = Unit
