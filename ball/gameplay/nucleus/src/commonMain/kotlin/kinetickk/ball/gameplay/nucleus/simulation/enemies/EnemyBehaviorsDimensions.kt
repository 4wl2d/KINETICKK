// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * SERAPH: weaves 380-520 u out; every [SERAPH_PULSE_SECONDS] it mends the [SERAPH_MAX_LINKS] nearest allies within
 * [SERAPH_LINK_RANGE] (never a boss-class kind, another Seraph or a shifting enemy) by [SERAPH_HEAL_SHARE] of their
 * integrity and wraps them and itself in an impact-only shield for [SERAPH_SHIELD_SECONDS].
 */
internal fun MutableGameState.updateSeraph(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val radial = if (distance < SERAPH_HOLD_NEAR) -SERAPH_RETREAT_SPEED else if (distance > SERAPH_HOLD_FAR) SERAPH_CLOSE_SPEED else 0f
    val weave = sin(elapsed * SERAPH_WEAVE_RATE + enemy.id * SERAPH_WEAVE_PHASE) * SERAPH_WEAVE
    steerEnemy(
        enemy,
        dx / distance * radial - dy / distance * weave,
        dy / distance * radial + dx / distance * weave,
        SERAPH_AGILITY,
        delta,
    )
    enemy.actionTimer -= cadence
    if (enemy.actionTimer > 0f) return
    enemy.actionTimer = SERAPH_PULSE_SECONDS
    if (!canAttack(enemy)) return
    forNearestAllies(enemy, SERAPH_LINK_RANGE, SERAPH_MAX_LINKS, { it.type == EnemyType.SERAPH || isShifting(it) }) { mendAlly(it) }
    enemy.shieldUntil = max(enemy.shieldUntil, elapsed + SERAPH_SHIELD_SECONDS)
}

/** Restores [SERAPH_HEAL_SHARE] of [ally]'s integrity (not a damage path) and shields it against all but impacts. */
private fun MutableGameState.mendAlly(ally: Enemy) {
    ally.hp = min(ally.maxHp, ally.hp + ally.maxHp * SERAPH_HEAL_SHARE)
    ally.shieldUntil = max(ally.shieldUntil, elapsed + SERAPH_SHIELD_SECONDS)
    shockwave(ally.x, ally.y, 0.3f, ally.radius * 2f, 0)
}

/**
 * CHOIR: keeps 300-420 u out; every [CHOIR_PULSE_SECONDS] it sings for [CHOIR_TELEGRAPH_SECONDS], then fires a ring
 * of bolts and shoves a Core within [CHOIR_WAVE_RADIUS] away from it (no damage; a dash phase slips through).
 */
internal fun MutableGameState.updateChoir(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val desired = if (distance > CHOIR_HOLD_FAR) CHOIR_CLOSE_SPEED else if (distance < CHOIR_HOLD_NEAR) -CHOIR_RETREAT_SPEED else 0f
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    steerEnemy(
        enemy,
        dx / distance * desired - dy / distance * CHOIR_ORBIT_SPEED * tangent,
        dy / distance * desired + dx / distance * CHOIR_ORBIT_SPEED * tangent,
        CHOIR_AGILITY,
        delta,
    )
    if (enemy.mode == EnemyBehavior.TELEGRAPH && elapsed >= enemy.modeUntil) {
        if (canAttack(enemy)) {
            val volley = if (content.tempo.denseAttacks(threatElapsed)) CHOIR_DENSE_VOLLEY else CHOIR_VOLLEY
            fireRadial(enemy.x, enemy.y, volley, CHOIR_SHOT_SPEED, elapsed * CHOIR_VOLLEY_SPIN, shotDamage(enemy))
            choirShove(dx, dy, distance)
        }
        enemy.mode = EnemyBehavior.IDLE
    }
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= 0f) {
        enemy.mode = EnemyBehavior.TELEGRAPH
        enemy.modeUntil = elapsed + CHOIR_TELEGRAPH_SECONDS
        enemy.actionTimer = CHOIR_PULSE_SECONDS
    }
}

/** The Choir's wave: shoves a Core within [CHOIR_WAVE_RADIUS] of the singer away from it (a dash phase slips through). */
internal fun MutableGameState.choirShove(dx: Float, dy: Float, distance: Float) {
    if (distance <= CHOIR_WAVE_RADIUS + coreRadius && dashPhaseTime <= 0f) {
        velocityX += dx / distance * CHOIR_WAVE_PUSH
        velocityY += dy / distance * CHOIR_WAVE_PUSH
    }
}

/**
 * IMP: jinks in at the Core (the Bomber's closing speed with a lateral weave) and primes within [IMP_PRIME_DISTANCE];
 * after [IMP_PRIME_SECONDS] it bursts (a silenced fuse fizzles).
 */
internal fun MutableGameState.updateImp(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.PRIMED) {
        enemy.damp(8f, delta)
        if (elapsed >= enemy.modeUntil) {
            if (canAttack(enemy)) detonateImp(enemy, dx, dy, distance) else enemy.mode = EnemyBehavior.IDLE
        }
        return
    }
    val closing = 1f - clamp(distance / 700f, 0f, 1f)
    val approach = 90f + 260f * closing
    val lateral = sin(elapsed * IMP_WEAVE_RATE + enemy.id) * IMP_WEAVE
    steerEnemy(
        enemy,
        dx / distance * approach - dy / distance * lateral,
        dy / distance * approach + dx / distance * lateral,
        IMP_AGILITY,
        delta,
    )
    if (distance < IMP_PRIME_DISTANCE && canAttack(enemy)) {
        enemy.mode = EnemyBehavior.PRIMED
        enemy.modeUntil = elapsed + IMP_PRIME_SECONDS
    }
}

/**
 * The burst removes the Imp without a kill (no rewards, as a Bomber), hurts a Core within [IMP_BLAST_RADIUS] and leaves
 * three embers across its heading toward the Core.
 */
private fun MutableGameState.detonateImp(enemy: Enemy, dx: Float, dy: Float, distance: Float) {
    enemy.dead = true
    shockwave(enemy.x, enemy.y, 0.35f, IMP_BLAST_RADIUS, 4)
    burst(enemy.x, enemy.y, 12, 4)
    val reach = IMP_BLAST_RADIUS + coreRadius
    if (distanceSquared(enemy.x, enemy.y, coreX, coreY) <= reach * reach) {
        hurtByHostileBlast(enemy.x, enemy.y, hostileDamage(enemy, IMP_BLAST_DAMAGE), IncomingDamage.HIT)
    }
    val acrossX = -dy / distance * IMP_EMBER_SPREAD
    val acrossY = dx / distance * IMP_EMBER_SPREAD
    queueEmber(enemy.x, enemy.y, IMP_EMBER_SECONDS)
    queueEmber(enemy.x + acrossX, enemy.y + acrossY, IMP_LEFT_EMBER_SECONDS)
    queueEmber(enemy.x - acrossX, enemy.y - acrossY, IMP_RIGHT_EMBER_SECONDS)
}

/**
 * HOUND: the Charger's rhythm with pack flocking instead of a creep: it flocks with other Hounds, winds up for
 * [HOUND_WINDUP] and dashes at the Core at [HOUND_DASH_SPEED]; its contact hurts at any speed while it strikes.
 */
internal fun MutableGameState.updateHound(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.STRIKE && elapsed >= enemy.modeUntil) enemy.mode = EnemyBehavior.IDLE
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= -HOUND_WINDUP) {
        enemy.actionTimer = HOUND_DASH_CADENCE
        if (canAttack(enemy)) {
            val dash = HOUND_DASH_SPEED * rebirthProfile.enemySpeedMultiplier
            enemy.vx = dx / distance * dash
            enemy.vy = dy / distance * dash
            enemy.mode = EnemyBehavior.STRIKE
            enemy.modeUntil = elapsed + HOUND_STRIKE_SECONDS
        }
    } else if (enemy.actionTimer < 0f) {
        enemy.damp(6f, delta)
    } else {
        flock(enemy, dx, dy, distance, delta, HOUND_SPACING, HOUND_APPROACH, HOUND_SWIRL, HOUND_SEPARATION, HOUND_AGILITY)
    }
    enemy.facing = if (enemy.vx * enemy.vx + enemy.vy * enemy.vy > HOUND_FACING_SPEED * HOUND_FACING_SPEED) {
        atan2(enemy.vy, enemy.vx)
    } else {
        atan2(dy, dx)
    }
}

/**
 * CIPHER: holds 300-420 u out like a Shooter; then glitches out of phase for [CIPHER_PHASE_SECONDS] (intangible, its
 * profile phases out), reappears around the Core at a hashed turn, aims for [CIPHER_AIM_SECONDS] and fires a fan.
 * The turn is a stateless hash of the run seed, its id and the run clock: no stream is drawn.
 */
internal fun MutableGameState.updateCipher(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    when (enemy.mode) {
        EnemyBehavior.PHASED -> {
            enemy.vx = 0f
            enemy.vy = 0f
            if (elapsed >= enemy.modeUntil) rematerializeCipher(enemy, dx, dy, distance)
        }
        EnemyBehavior.TELEGRAPH -> {
            enemy.damp(8f, delta)
            enemy.facing = atan2(dy, dx)
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) {
                    fireSpread(enemy.x, enemy.y, enemy.facing, CIPHER_VOLLEY, CIPHER_VOLLEY_SPACING, CIPHER_SHOT_SPEED, shotDamage(enemy))
                }
                enemy.mode = EnemyBehavior.IDLE
                enemy.abilityAt = elapsed + CIPHER_GLITCH_SECONDS / hostileAttackRate()
            }
        }
        else -> {
            val desired = if (distance > CIPHER_HOLD_FAR) CIPHER_CLOSE_SPEED else if (distance < CIPHER_HOLD_NEAR) -CIPHER_RETREAT_SPEED else 0f
            val tangent = if (enemy.id % 2 == 0) 1f else -1f
            steerEnemy(
                enemy,
                dx / distance * desired - dy / distance * CIPHER_ORBIT_SPEED * tangent,
                dy / distance * desired + dx / distance * CIPHER_ORBIT_SPEED * tangent,
                CIPHER_AGILITY,
                delta,
            )
            if (elapsed >= enemy.abilityAt && distance < CIPHER_ENGAGE_RANGE && canAttack(enemy)) {
                enemy.mode = EnemyBehavior.PHASED
                enemy.modeUntil = elapsed + CIPHER_PHASE_SECONDS
                // Out of phase it is untargetable everywhere a shifting enemy is.
                enemy.shiftUntil = enemy.modeUntil
                enemy.vx = 0f
                enemy.vy = 0f
            }
        }
    }
}

/**
 * Puts the Cipher back [CIPHER_NEAREST]-[CIPHER_FARTHEST] from the Core, turned around it by the glitch hash, nudged
 * outward clear of obstacles, and starts its aim.
 */
private fun MutableGameState.rematerializeCipher(enemy: Enemy, dx: Float, dy: Float, distance: Float) {
    val hash = runHash(runSeed, CIPHER_SALT, (enemy.id.toLong() shl 20) xor (elapsed * CIPHER_HASH_TICKS).toLong())
    val side = if (hash and 1L == 1L) 1f else -1f
    val turn = (CIPHER_TURN + CIPHER_TURN_SPREAD * hashUnit(hash)) * side
    val reach = clamp(distance, CIPHER_NEAREST, CIPHER_FARTHEST)
    val outX = -dx / distance
    val outY = -dy / distance
    val turnCos = cos(turn)
    val turnSin = sin(turn)
    val directionX = outX * turnCos - outY * turnSin
    val directionY = outX * turnSin + outY * turnCos
    placeClear(enemy, coreX + directionX * reach, coreY + directionY * reach, directionX, directionY)
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + CIPHER_AIM_SECONDS
    enemy.facing = atan2(coreY - enemy.y, coreX - enemy.x)
}

/**
 * MIRROR: steers for the Core's mirror image through the singularity (2S − core), so it waits beyond the cursor and
 * meets the Core there; every [MIRROR_SHOT_SECONDS] it fires a two-bolt fan at the Core.
 */
internal fun MutableGameState.updateMirror(enemy: Enemy, dx: Float, dy: Float, cadence: Float, delta: Float) {
    val singularityX = cameraX + pointerX - screenWidth * 0.5f
    val singularityY = cameraY + pointerY - screenHeight * 0.5f
    var desiredX = (2f * singularityX - coreX - enemy.x) * MIRROR_GAIN
    var desiredY = (2f * singularityY - coreY - enemy.y) * MIRROR_GAIN
    val desired = length(desiredX, desiredY)
    if (desired > MIRROR_MAX_SPEED) {
        desiredX *= MIRROR_MAX_SPEED / desired
        desiredY *= MIRROR_MAX_SPEED / desired
    }
    steerEnemy(enemy, desiredX, desiredY, MIRROR_AGILITY, delta)
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = MIRROR_SHOT_SECONDS
        if (canAttack(enemy)) {
            fireSpread(enemy.x, enemy.y, atan2(dy, dx), MIRROR_VOLLEY, MIRROR_VOLLEY_SPACING, MIRROR_SHOT_SPEED, shotDamage(enemy))
        }
    }
}
