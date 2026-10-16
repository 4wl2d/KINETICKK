// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Largest share of the Architect's integrity one damage instance may remove. */
internal const val ARCHITECT_HIT_CAP: Float = 0.015f
internal const val ARCHITECT_FINAL_PHASE: Int = 2
internal const val ARCHITECT_SHIFT_SECONDS: Float = 8f
internal const val ARCHITECT_SHIFT_RING: Int = 16
internal const val ARCHITECT_SHIFT_REPAIRS: Int = 4
/** The Architect skips a volley while this many projectiles are live, so player shots never starve. */
internal const val ARCHITECT_VOLLEY_PROJECTILE_BUDGET: Int = 450
/** Ordinary enemies keep arriving during the fight at this share of the cap and a slower rate. */
internal const val ARCHITECT_ADD_CAP_SHARE: Float = 0.6f
internal const val ARCHITECT_ADD_INTERVAL_SCALE: Float = 1.4f
/** Adds arrive only while the Architect shifts or took damage this recently, so stalling farms nothing. */
internal const val ARCHITECT_ADD_ENGAGEMENT_SECONDS: Float = 10f

private const val ARCHITECT_RADIAL_SPEED: Float = 135f
private const val ARCHITECT_AIMED_SPEED: Float = 250f
private const val ARCHITECT_AIMED_SPACING: Float = 0.18f
private const val ARCHITECT_LATE_RADIAL_LIFE: Float = 5f
private const val ARCHITECT_SPIRAL_TURN_RATE: Float = 0.2f
private const val ARCHITECT_GATE_TOLERANCE: Float = 1e-5f

internal fun MutableGameState.isShifting(enemy: Enemy): Boolean = enemy.shiftUntil > elapsed

/** Integrity at which the current phase ends: the boss bar's thirds. */
private fun architectGate(enemy: Enemy): Float = enemy.maxHp * (ARCHITECT_FINAL_PHASE - enemy.phase) / 3f

/**
 * Applies the Architect's gating to a requested amount: nothing while shifting, at most
 * [ARCHITECT_HIT_CAP] of its integrity, and never past the current phase's gate. Never raises integrity.
 */
internal fun MutableGameState.gateArchitectDamage(enemy: Enemy, amount: Float): Float {
    if (isShifting(enemy)) return 0f
    var gated = min(amount, ARCHITECT_HIT_CAP * enemy.maxHp)
    if (enemy.phase < ARCHITECT_FINAL_PHASE) {
        val gate = architectGate(enemy)
        if (enemy.hp > gate) gated = min(gated, enemy.hp - gate)
    }
    return gated
}

/** Called after every integrity write on the Architect: reaching a gate starts the next phase. */
internal fun MutableGameState.onArchitectDamaged(enemy: Enemy) {
    if (enemy.phase >= ARCHITECT_FINAL_PHASE || enemy.hp <= 0f) return
    if (enemy.hp <= architectGate(enemy) + enemy.maxHp * ARCHITECT_GATE_TOLERANCE) startArchitectPhase(enemy)
}

/**
 * Enters the next phase: a shift without damage, an aspect hardened against the build and escorts.
 * Only Architect state changes here; [updateBossEncounter] spawns outside the damage pipeline.
 */
private fun MutableGameState.startArchitectPhase(enemy: Enemy) {
    enemy.phase++
    enemy.shiftUntil = elapsed + ARCHITECT_SHIFT_SECONDS
    assignAspect(enemy, adaptiveArchitectAspect(enemy.phase), immune = enemy.type.profile.aspectImmune)
    enemy.escortsPending = architectEscortCount(enemy.aspect)
}

/** "It hardens against what you rely on": the dominant, then the secondary element, once they reach a tier. */
private fun MutableGameState.adaptiveArchitectAspect(phase: Int): Int {
    if (phase <= 0) return -1
    val dominant = build.dominant?.takeIf { affinityTier(it) >= 1 }
    if (phase == 1) return dominant?.ordinal ?: -1
    val secondary = build.secondary?.takeIf { affinityTier(it) >= 1 }
    return (secondary ?: dominant)?.ordinal ?: -1
}

private fun architectEscortCount(aspect: Int): Int = when (aspect) {
    StatusElement.CRYO.ordinal, StatusElement.ION.ordinal -> 3
    StatusElement.ENTROPY.ordinal -> 2
    else -> 4
}

private fun architectEscort(aspect: Int, index: Int): EnemyType = when (aspect) {
    StatusElement.THERMAL.ordinal -> if (index < 2) EnemyType.CHARGER else EnemyType.INTERCEPTOR
    StatusElement.CRYO.ordinal -> EnemyType.WEAVER
    StatusElement.ION.ordinal -> EnemyType.SHOOTER
    StatusElement.GRAVITIC.ordinal -> if (index == 0) EnemyType.WARDEN else EnemyType.DRIFTER
    StatusElement.ENTROPY.ordinal -> EnemyType.SPLITTER
    else -> EnemyType.DRIFTER
}

/** True while the fight is live: the Architect is shifting or took damage within [ARCHITECT_ADD_ENGAGEMENT_SECONDS]. */
internal fun MutableGameState.architectEngaged(enemy: Enemy): Boolean =
    isShifting(enemy) || elapsed < enemy.lastDamagedAt + ARCHITECT_ADD_ENGAGEMENT_SECONDS

internal fun MutableGameState.liveArchitect(): Enemy? {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.isBoss && enemy.alive) return enemy
    }
    return null
}

/**
 * Resolves a shift the damage pipeline started: one ring, repair drops and the escorts. Runs once per
 * step after enemy movement and outside every enemy loop, so it also runs while the Architect is Frozen.
 */
internal fun MutableGameState.updateBossEncounter() {
    if (!bossSpawned) return
    val boss = liveArchitect() ?: return
    val escorts = boss.escortsPending
    if (escorts <= 0) return
    boss.escortsPending = 0
    fireRadial(boss.x, boss.y, ARCHITECT_SHIFT_RING, ARCHITECT_RADIAL_SPEED, elapsed, ARCHITECT_LATE_RADIAL_LIFE)
    for (index in 0 until ARCHITECT_SHIFT_REPAIRS) {
        val side = if (index % 2 == 0) 1f else -1f
        addPickup(Pickup(PickupType.REPAIR, boss.x + side * 22f, boss.y - 10f * index))
    }
    for (index in 0 until escorts) spawnEnemy(architectEscort(boss.aspect, index))
}

internal fun MutableGameState.updateArchitect(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    val orbit = elapsed * 0.23f
    val orbitRadius = if (enemy.phase >= ARCHITECT_FINAL_PHASE) 340f else 380f
    val targetX = coreX + cos(orbit) * orbitRadius
    val targetY = coreY + sin(orbit) * orbitRadius
    steerEnemy(enemy, (targetX - enemy.x) * 0.6f, (targetY - enemy.y) * 0.6f, 1.2f, delta)
    val cadence = when (enemy.phase) {
        0 -> 1.3f
        1 -> 1.15f
        else -> 1.0f
    }
    if (isShifting(enemy)) {
        // No attacks while shifting; the next volley comes a full cadence after the shift.
        enemy.actionTimer = cadence
    } else {
        enemy.actionTimer -= delta * content.tempo.attackRateMultiplier(threatElapsed)
        if (enemy.actionTimer <= 0f) {
            enemy.actionTimer = cadence
            if (projectiles.size < ARCHITECT_VOLLEY_PROJECTILE_BUDGET) fireArchitectVolley(enemy, atan2(dy, dx))
        }
    }
    if (distance < 180f) {
        enemy.vx -= dx / distance * 80f * delta
        enemy.vy -= dy / distance * 80f * delta
    }
}

private fun MutableGameState.fireArchitectVolley(enemy: Enemy, aim: Float) {
    when (enemy.phase) {
        0 -> {
            fireSpread(enemy.x, enemy.y, aim, 3, ARCHITECT_AIMED_SPACING, ARCHITECT_AIMED_SPEED)
            fireRadial(enemy.x, enemy.y, 8, ARCHITECT_RADIAL_SPEED, elapsed)
        }
        1 -> {
            fireSpread(enemy.x, enemy.y, aim, 4, ARCHITECT_AIMED_SPACING, ARCHITECT_AIMED_SPEED)
            fireRadial(enemy.x, enemy.y, 10, ARCHITECT_RADIAL_SPEED, elapsed * ARCHITECT_SPIRAL_TURN_RATE,
                ARCHITECT_LATE_RADIAL_LIFE)
        }
        else -> {
            fireSpread(enemy.x, enemy.y, aim, 5, ARCHITECT_AIMED_SPACING, ARCHITECT_AIMED_SPEED)
            fireRadial(enemy.x, enemy.y, 12, ARCHITECT_RADIAL_SPEED, elapsed, ARCHITECT_LATE_RADIAL_LIFE)
        }
    }
}

/**
 * Executioner's "below the line" test. Against the Architect it reads the integrity within the current
 * third, so a whole phase never auto-crits.
 */
internal fun belowExecutionLine(enemy: Enemy, fraction: Float): Boolean {
    if (!enemy.isBoss) return enemy.hp < enemy.maxHp * fraction
    val third = enemy.maxHp / 3f
    val floor = third * (ARCHITECT_FINAL_PHASE - enemy.phase.coerceIn(0, ARCHITECT_FINAL_PHASE))
    return enemy.hp - floor < third * fraction
}
