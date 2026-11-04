// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.max

/**
 * Silence stops [target]'s attacks. A boss falls silent for [SILENCE_BOSS_SECONDS] and is then out of reach
 * for [SILENCE_BOSS_GRACE_SECONDS]: per-enemy reaction cooldowns cannot stop several reactors from chaining
 * Silences, so the lockout belongs to the boss.
 */
internal fun MutableGameState.silence(target: Enemy, seconds: Float = SILENCE_SECONDS) {
    if (!target.isBossClass) {
        target.silencedUntil = max(target.silencedUntil, elapsed + seconds)
        return
    }
    if (target.silencedUntil > 0f && elapsed < target.silencedUntil + SILENCE_BOSS_GRACE_SECONDS) return
    target.silencedUntil = elapsed + SILENCE_BOSS_SECONDS
}

/**
 * Knockback to Damage: a [speed] throw, push or pull on [target], which takes at most [IMMOVABLE_KNOCKBACK] of
 * knockback, deals proc damage instead (through the Architect's gate), at most once per enemy per
 * [KNOCKBACK_DAMAGE_GATE_SECONDS] (the slam gate). True when it did; a gated throw moves the enemy as usual.
 */
internal fun MutableGameState.knockbackAsDamage(target: Enemy, knockback: Float, speed: Float): Boolean {
    val amount = build.knockbackToDamage
    if (amount <= 0f || knockback > IMMOVABLE_KNOCKBACK || !(speed > 0f) || !target.alive || isShifting(target)) return false
    if (target.slamReadyAt > elapsed) return false
    target.slamReadyAt = elapsed + KNOCKBACK_DAMAGE_GATE_SECONDS
    dealProcDamage(target, amount * speed * KNOCKBACK_DAMAGE_SCALE * runScale(), rescale = false)
    return true
}

internal const val IMMOVABLE_KNOCKBACK: Float = 0.35f
internal const val KNOCKBACK_DAMAGE_SCALE: Float = 0.15f
internal const val KNOCKBACK_DAMAGE_GATE_SECONDS: Float = 0.5f

/** Pushes [target] away from ([x], [y]) at [speed], scaled by the knockback it takes. */
internal fun MutableGameState.hurl(x: Float, y: Float, target: Enemy, speed: Float) {
    val knockback = knockbackTaken(target)
    if (knockbackAsDamage(target, knockback, speed)) return
    if (knockback <= 0f) return
    var dx = target.x - x
    var dy = target.y - y
    if (length(dx, dy) < 1f) {
        dx = target.x - coreX
        dy = target.y - coreY
    }
    val distance = max(1f, length(dx, dy))
    target.vx += dx / distance * speed * knockback
    target.vy += dy / distance * speed * knockback
    markThrown(target)
}

/** The other living enemy with the most integrity within [range] of [origin] (ties by id), or null. */
internal fun MutableGameState.toughestOtherEnemy(origin: Enemy, range: Float): Enemy? {
    var best: Enemy? = null
    val rangeSquared = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (candidate.id == origin.id || !candidate.alive || isShifting(candidate)) continue
        if (distanceSquared(origin.x, origin.y, candidate.x, candidate.y) > rangeSquared) continue
        if (best == null || candidate.hp > best.hp) best = candidate
    }
    return best
}
