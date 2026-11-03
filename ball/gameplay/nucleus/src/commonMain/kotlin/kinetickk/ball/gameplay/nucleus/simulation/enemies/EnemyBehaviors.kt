// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Behavior of the 0.4.0 roster. Each step runs only while the enemy can act; every attack also checks
 * canAttack, so silence skips the attack and restarts its cadence. Modes use EnemyBehavior values and
 * absolute run times, so presentation reads the same fields.
 */

/**
 * Contact damage of an attack that hurts at any Core speed (strikes, lances, the Herald), or 0. A frozen,
 * stunned or silenced enemy strikes nothing (its contact is ordinary again); a strike ends with its window
 * even while a stun holds its mode.
 */
internal fun MutableGameState.strikeDamage(enemy: Enemy): Float {
    if (!canAttack(enemy) || isFrozen(enemy)) return 0f
    val striking = enemy.mode == EnemyBehavior.STRIKE && elapsed < enemy.modeUntil
    return when (enemy.type) {
        EnemyType.CHARGER -> if (striking) contactDamage(enemy) else 0f
        EnemyType.LANCER -> if (striking) hostileDamage(enemy, LANCER_DAMAGE) else 0f
        EnemyType.PHANTOM -> if (striking) hostileDamage(enemy, PHANTOM_STRIKE_DAMAGE) else 0f
        EnemyType.HERALD -> contactDamage(enemy)
        EnemyType.HOUND -> if (striking) contactDamage(enemy) else 0f
        EnemyType.PYRE_HERALD -> contactDamage(enemy)
        EnemyType.ANTECEDENT -> if (enemy.phase % 2 == 1) contactDamage(enemy) else 0f
        else -> 0f
    }
}

/** Ordinary contact damage, `7 + 0.18 r` through [hostileDamage]. */
internal fun MutableGameState.contactDamage(enemy: Enemy): Float = hostileDamage(enemy, 7f + enemy.radius * 0.18f)

/**
 * A hostile blast centered at ([x], [y]) reaches the Core: a dash phase or an open parry cancels it,
 * nothing else does, whatever the Core's speed.
 */
internal fun MutableGameState.hurtByHostileBlast(x: Float, y: Float, damage: Float, kind: IncomingDamage) {
    if (dashPhaseTime > 0f) return
    if (tryCharacterParry(x, y, coreX - x, coreY - y, coreX, coreY, damage = damage)) return
    takeDamage(damage, kind = kind)
    screenShake = max(screenShake, 7f)
}

internal fun turnToward(current: Float, target: Float, maximumStep: Float): Float {
    var difference = target - current
    while (difference > TAU * 0.5f) difference -= TAU
    while (difference < -TAU * 0.5f) difference += TAU
    return current + clamp(difference, -maximumStep, maximumStep)
}

internal fun angleBetween(a: Float, b: Float): Float {
    var difference = a - b
    while (difference > TAU * 0.5f) difference -= TAU
    while (difference < -TAU * 0.5f) difference += TAU
    return abs(difference)
}

internal fun Enemy.damp(rate: Float, delta: Float) {
    val factor = exp(-rate * delta)
    vx *= factor
    vy *= factor
}

internal fun MutableGameState.updateCharger(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.STRIKE && elapsed >= enemy.modeUntil) enemy.mode = EnemyBehavior.IDLE
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= -0.45f) {
        enemy.actionTimer = 2.15f
        if (canAttack(enemy)) {
            enemy.vx = dx / distance * 390f * rebirthProfile.enemySpeedMultiplier
            enemy.vy = dy / distance * 390f * rebirthProfile.enemySpeedMultiplier
            enemy.mode = EnemyBehavior.STRIKE
            enemy.modeUntil = elapsed + CHARGER_STRIKE_SECONDS
        }
    } else if (enemy.actionTimer < 0f) {
        enemy.damp(6f, delta)
    } else {
        steerEnemy(enemy, dx / distance * 48f, dy / distance * 48f, 0.7f, delta)
    }
}

internal fun MutableGameState.updateSwarmling(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    flock(enemy, dx, dy, distance, delta, SWARM_SPACING, 150f, 42f, 110f, 3.2f)
}

private const val SWARM_SPACING: Float = 34f

internal fun MutableGameState.updateBomber(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.PRIMED) {
        enemy.damp(8f, delta)
        if (elapsed >= enemy.modeUntil) {
            // A silenced fuse fizzles; the Bomber has to close in and prime again.
            if (canAttack(enemy)) detonateBomber(enemy) else enemy.mode = EnemyBehavior.IDLE
        }
        return
    }
    val closing = 1f - clamp(distance / 700f, 0f, 1f)
    val approach = 90f + 260f * closing
    steerEnemy(enemy, dx / distance * approach, dy / distance * approach, 1.8f, delta)
    if (distance < BOMBER_PRIME_DISTANCE && canAttack(enemy)) {
        enemy.mode = EnemyBehavior.PRIMED
        enemy.modeUntil = elapsed + BOMBER_PRIME_SECONDS
    }
}

/** The blast removes the Bomber without a kill (no rewards) and hurts the Core within reach. */
internal fun MutableGameState.detonateBomber(enemy: Enemy) {
    enemy.dead = true
    shockwave(enemy.x, enemy.y, 0.35f, BOMBER_BLAST_RADIUS, 4)
    burst(enemy.x, enemy.y, 14, 4)
    val reach = BOMBER_BLAST_RADIUS + coreRadius
    if (distanceSquared(enemy.x, enemy.y, coreX, coreY) <= reach * reach) {
        hurtByHostileBlast(enemy.x, enemy.y, hostileDamage(enemy, BOMBER_BLAST_DAMAGE), IncomingDamage.HIT)
    }
}

internal fun MutableGameState.updateLancer(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    when (enemy.mode) {
        EnemyBehavior.TELEGRAPH -> {
            enemy.damp(7f, delta)
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) {
                    val lanceSpeed = LANCER_SPEED * rebirthProfile.enemySpeedMultiplier
                    enemy.mode = EnemyBehavior.STRIKE
                    enemy.modeUntil = elapsed + LANCER_DISTANCE / lanceSpeed
                    enemy.vx = cos(enemy.facing) * lanceSpeed
                    enemy.vy = sin(enemy.facing) * lanceSpeed
                } else {
                    enemy.mode = EnemyBehavior.RECOVERY
                    enemy.modeUntil = elapsed + LANCER_RECOVERY_SECONDS
                }
            }
        }
        EnemyBehavior.STRIKE -> if (elapsed >= enemy.modeUntil) {
            // The window can close during a stun or stop; the lance is over, so it never restarts the run.
            enemy.mode = EnemyBehavior.RECOVERY
            enemy.modeUntil = elapsed + LANCER_RECOVERY_SECONDS
        } else {
            val lanceSpeed = LANCER_SPEED * rebirthProfile.enemySpeedMultiplier
            enemy.vx = cos(enemy.facing) * lanceSpeed
            enemy.vy = sin(enemy.facing) * lanceSpeed
            val nextX = enemy.x + enemy.vx * delta
            val nextY = enemy.y + enemy.vy * delta
            if (obstacleBlocksSegment(enemy.x, enemy.y, nextX, nextY, enemy.radius)) {
                // Lancing into an obstacle stuns the Lancer.
                enemy.vx = 0f
                enemy.vy = 0f
                enemy.disabledUntil = max(enemy.disabledUntil, elapsed + LANCER_STUN_SECONDS)
                enemy.mode = EnemyBehavior.RECOVERY
                enemy.modeUntil = elapsed + LANCER_RECOVERY_SECONDS
                shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.4f, 3)
            }
        }
        EnemyBehavior.RECOVERY -> {
            enemy.damp(5f, delta)
            if (elapsed >= enemy.modeUntil) {
                enemy.mode = EnemyBehavior.IDLE
                enemy.abilityAt = elapsed + LANCER_COOLDOWN_SECONDS / hostileAttackRate()
            }
        }
        else -> {
            val desired = if (distance > 480f) 85f else if (distance < 340f) -70f else 0f
            val tangent = if (enemy.id % 2 == 0) 1f else -1f
            steerEnemy(
                enemy,
                dx / distance * desired - dy / distance * 45f * tangent,
                dy / distance * desired + dx / distance * 45f * tangent,
                1.6f,
                delta,
            )
            enemy.facing = atan2(dy, dx)
            if (elapsed >= enemy.abilityAt && distance < LANCER_ENGAGE_RANGE && canAttack(enemy)) {
                enemy.mode = EnemyBehavior.TELEGRAPH
                enemy.modeUntil = elapsed + LANCER_TELEGRAPH_SECONDS
            }
        }
    }
}

internal fun MutableGameState.updateSniper(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.TELEGRAPH) {
        enemy.damp(6f, delta)
        // The sight line tracks the Core until a short lock before the shot.
        if (enemy.modeUntil - elapsed > 0.22f) enemy.facing = atan2(dy, dx)
        if (elapsed >= enemy.modeUntil) {
            enemy.mode = EnemyBehavior.IDLE
            enemy.abilityAt = elapsed + 2.4f / hostileAttackRate()
            val startX = enemy.x + cos(enemy.facing) * enemy.radius
            val startY = enemy.y + sin(enemy.facing) * enemy.radius
            if (canAttack(enemy) && !obstacleBlocksSegment(startX, startY, coreX, coreY, 7f)) {
                val shotSpeed = SNIPER_SHOT_SPEED * rebirthProfile.enemySpeedMultiplier
                addProjectile(Projectile(
                    x = startX,
                    y = startY,
                    vx = cos(enemy.facing) * shotSpeed,
                    vy = sin(enemy.facing) * shotSpeed,
                    radius = 7f,
                    life = 1.6f,
                    damage = shotDamage(enemy),
                ))
            }
        }
        return
    }
    val desired = if (distance > SNIPER_MAX_RANGE) 105f else if (distance < SNIPER_MIN_RANGE) -125f else 0f
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    steerEnemy(
        enemy,
        dx / distance * desired - dy / distance * 38f * tangent,
        dy / distance * desired + dx / distance * 38f * tangent,
        1.4f,
        delta,
    )
    enemy.facing = atan2(dy, dx)
    if (
        elapsed >= enemy.abilityAt && distance < SNIPER_MAX_RANGE + 160f && canAttack(enemy) &&
        !obstacleBlocksSegment(enemy.x, enemy.y, coreX, coreY, 7f)
    ) {
        enemy.mode = EnemyBehavior.TELEGRAPH
        enemy.modeUntil = elapsed + SNIPER_AIM_SECONDS
    }
}

internal fun MutableGameState.updateAegis(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    val radial = when {
        distance < 560f -> -95f
        distance > 900f -> 60f
        else -> 0f
    }
    steerEnemy(
        enemy,
        dx / distance * radial - dy / distance * 38f * tangent,
        dy / distance * radial + dx / distance * 38f * tangent,
        1.5f,
        delta,
    )
    tetherNearestAllies(enemy, AEGIS_MAX_LINKS)
}

/**
 * Tethers the [links] (1..3) nearest live allies within [AEGIS_LINK_RANGE] of [enemy], nearest first, for
 * [AEGIS_LINK_HOLD_SECONDS]. The Aegis tethers three ([AEGIS_MAX_LINKS]); a Linked Elite tethers its affix's count.
 */
internal fun MutableGameState.tetherNearestAllies(enemy: Enemy, links: Int) {
    // Other Aegises are never linked.
    forNearestAllies(enemy, AEGIS_LINK_RANGE, links, { it.type == EnemyType.AEGIS }) { link(it, enemy) }
}

/**
 * Runs [action] on the [count] (1..3) nearest live allies within [range] of [enemy], nearest first, once all three
 * are chosen. Boss-class kinds and allies [skip] rejects are never chosen. Index locals only, so nothing is allocated.
 */
internal inline fun MutableGameState.forNearestAllies(
    enemy: Enemy,
    range: Float,
    count: Int,
    skip: (Enemy) -> Boolean,
    action: (Enemy) -> Unit,
) {
    var firstIndex = -1
    var secondIndex = -1
    var thirdIndex = -1
    var first = Float.MAX_VALUE
    var second = Float.MAX_VALUE
    var third = Float.MAX_VALUE
    val rangeSquared = range * range
    for (index in enemies.indices) {
        val ally = enemies[index]
        if (ally === enemy || !ally.alive || ally.isBossClass || skip(ally)) continue
        val squared = distanceSquared(enemy.x, enemy.y, ally.x, ally.y)
        if (squared > rangeSquared) continue
        if (squared < first) {
            thirdIndex = secondIndex; third = second
            secondIndex = firstIndex; second = first
            firstIndex = index; first = squared
        } else if (squared < second) {
            thirdIndex = secondIndex; third = second
            secondIndex = index; second = squared
        } else if (squared < third) {
            thirdIndex = index; third = squared
        }
    }
    if (firstIndex >= 0 && count >= 1) action(enemies[firstIndex])
    if (secondIndex >= 0 && count >= 2) action(enemies[secondIndex])
    if (thirdIndex >= 0 && count >= 3) action(enemies[thirdIndex])
}

private fun MutableGameState.link(ally: Enemy, aegis: Enemy) {
    ally.linkedBy = aegis.id
    ally.linkedUntil = elapsed + AEGIS_LINK_HOLD_SECONDS
}

/** A link lapses this long after its Aegis stops renewing it (destroyed, stunned, out of range). */
private const val AEGIS_LINK_HOLD_SECONDS: Float = 0.2f

/** True while an Aegis tethers [enemy]. */
internal fun MutableGameState.isLinked(enemy: Enemy): Boolean = enemy.linkedBy >= 0 && enemy.linkedUntil > elapsed

internal fun MutableGameState.updateHerald(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    val radial = clamp((distance - 320f) * 0.4f, -60f, 60f)
    steerEnemy(
        enemy,
        dx / distance * radial - dy / distance * 50f * tangent,
        dy / distance * radial + dx / distance * 50f * tangent,
        1.1f,
        delta,
    )
    enemy.phase = when {
        enemy.hp < enemy.maxHp / 3f -> 2
        enemy.hp < enemy.maxHp * 2f / 3f -> 1
        else -> 0
    }
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = 1f
        if (canAttack(enemy)) {
            fireRadial(enemy.x, enemy.y, 6 + 2 * enemy.phase, 150f, elapsed * 1.3f, shotDamage(enemy), life = 6f)
        }
    }
    if (elapsed >= enemy.abilityAt && enemy.shieldUntil <= elapsed) {
        enemy.shieldUntil = elapsed + HERALD_SHIELD_SECONDS
        enemy.abilityAt = elapsed + HERALD_SHIELD_INTERVAL_SECONDS
        // Its arrival escorts belong to the encounter; repeat summons, like a brood, only fill free ordinary room.
        enemy.escortsPending += heraldSummonsWithinCap()
        shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * 2.6f, 3)
    }
}

/**
 * Escorts one Herald shield cycle summons: [HERALD_ESCORTS], cut to the whole ordinary cap slots still free
 * (escorts share the Herald's aspect, so none is a Swarmling and each fills a full slot).
 */
internal fun MutableGameState.heraldSummonsWithinCap(): Int {
    val room = ordinaryEnemyCap() - ordinaryCapLoad()
    return min(HERALD_ESCORTS, max(0, (room + 1e-4f).toInt()))
}

/**
 * Applies the receiver's own guards to [amount]: a Herald shield stops everything but impacts (an impact
 * breaks it) and an Aegis tether removes [AEGIS_LINK_REDUCTION] of the rest, except from an [execute].
 */
internal fun MutableGameState.guardedDamage(enemy: Enemy, amount: Float, source: HitSource?, execute: Boolean = false): Float {
    var guarded = amount
    if (enemy.shieldUntil > elapsed) {
        if (source != HitSource.IMPACT && source != HitSource.DASH) return 0f
        enemy.shieldUntil = elapsed
        shockwave(enemy.x, enemy.y, 0.4f, enemy.radius * 2.4f, 3)
    }
    if (!execute && isLinked(enemy)) guarded *= 1f - AEGIS_LINK_REDUCTION
    return guarded
}

/** Seconds left in the enemy's current behavior step, for telegraphs. */
internal fun MutableGameState.behaviorRemaining(enemy: Enemy): Float =
    if (enemy.mode == EnemyBehavior.IDLE) 0f else max(0f, enemy.modeUntil - elapsed)

internal fun MutableGameState.linkedTo(enemy: Enemy): Int = if (isLinked(enemy)) enemy.linkedBy else -1

internal fun MutableGameState.shielded(enemy: Enemy): Boolean = enemy.shieldUntil > elapsed

/** Initial facing and timers a new enemy of [type] starts with. */
internal fun MutableGameState.initializeBehavior(enemy: Enemy) {
    enemy.facing = atan2(coreY - enemy.y, coreX - enemy.x)
    when (enemy.type) {
        EnemyType.LANCER -> enemy.abilityAt = elapsed + 2f
        EnemyType.SNIPER -> enemy.abilityAt = elapsed + 1.5f
        EnemyType.PHANTOM -> enemy.abilityAt = elapsed + 2f
        EnemyType.HERALD -> enemy.abilityAt = elapsed + HERALD_FIRST_SHIELD_SECONDS
        EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CIPHER -> initializeWorldsBehavior(enemy)
        EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD -> initializeMiniBoss(enemy)
        else -> Unit
    }
    // A mini or secret boss runs its first phase start on its first step.
    if (!enemy.isBoss && encounterSpec(enemy) != null) enemy.phaseStartPending = true
}
