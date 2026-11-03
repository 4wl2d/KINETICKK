// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun MutableGameState.updateMinelayer(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    val radial = clamp((distance - 380f) * 0.5f, -70f, 70f)
    steerEnemy(
        enemy,
        dx / distance * radial - dy / distance * 85f * tangent,
        dy / distance * radial + dx / distance * 85f * tangent,
        1.6f,
        delta,
    )
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = MINE_DROP_SECONDS
        if (canAttack(enemy) && distance in MINE_MINIMUM_CORE_DISTANCE..900f && liveMines() < MAX_LIVE_MINES) {
            addProjectile(Projectile(
                x = enemy.x,
                y = enemy.y,
                vx = 0f,
                vy = 0f,
                radius = MINE_RADIUS,
                life = MINE_LIFE_SECONDS,
                damage = shotDamage(enemy),
                kind = ProjectileKind.MINE,
            ))
        }
    }
}

internal fun MutableGameState.liveMines(): Int {
    var count = 0
    for (index in projectiles.indices) if (projectiles[index].kind == ProjectileKind.MINE) count++
    return count
}

/** True once a mine has armed; it lives [MINE_LIFE_SECONDS] and arms after [MINE_ARM_SECONDS]. */
internal fun mineArmed(mine: Projectile): Boolean = mine.life <= MINE_LIFE_SECONDS - MINE_ARM_SECONDS

/**
 * Resolves a hostile mine against the Core this step; true when it detonated (the caller removes it).
 * An armed mine triggers within [MINE_TRIGGER_RADIUS]; a dash-phase contact sets any mine off harmlessly.
 */
internal fun MutableGameState.resolveMine(mine: Projectile): Boolean {
    val touched = segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, mine.x, mine.y, MINE_TRIGGER_RADIUS)
    if (!touched) return false
    val dashing = dashPhaseTime > 0f
    if (!dashing && !mineArmed(mine)) return false
    shockwave(mine.x, mine.y, 0.3f, MINE_BLAST_RADIUS, 4)
    burst(mine.x, mine.y, 8, 4)
    if (!dashing) {
        val reach = MINE_BLAST_RADIUS + coreRadius
        if (distanceSquared(mine.x, mine.y, coreX, coreY) <= reach * reach) {
            hurtByHostileBlast(mine.x, mine.y, mine.damage, IncomingDamage.PROJECTILE)
        }
    }
    return true
}

internal fun MutableGameState.updatePhantom(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    when (enemy.mode) {
        EnemyBehavior.PHASED -> {
            enemy.vx = 0f
            enemy.vy = 0f
            if (elapsed >= enemy.modeUntil) rematerializePhantom(enemy)
        }
        EnemyBehavior.TELEGRAPH -> {
            enemy.damp(8f, delta)
            enemy.facing = atan2(dy, dx)
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) {
                    val strikeSpeed = PHANTOM_STRIKE_SPEED * rebirthProfile.enemySpeedMultiplier
                    enemy.mode = EnemyBehavior.STRIKE
                    enemy.modeUntil = elapsed + PHANTOM_STRIKE_SECONDS
                    enemy.vx = cos(enemy.facing) * strikeSpeed
                    enemy.vy = sin(enemy.facing) * strikeSpeed
                } else {
                    enemy.mode = EnemyBehavior.IDLE
                    enemy.abilityAt = elapsed + 2f
                }
            }
        }
        EnemyBehavior.STRIKE -> if (elapsed >= enemy.modeUntil) {
            enemy.mode = EnemyBehavior.IDLE
            enemy.abilityAt = elapsed + 3.6f / hostileAttackRate()
        }
        else -> {
            val desired = if (distance > 380f) 70f else 0f
            val tangent = if (enemy.id % 2 == 0) 1f else -1f
            steerEnemy(
                enemy,
                dx / distance * desired - dy / distance * 60f * tangent,
                dy / distance * desired + dx / distance * 60f * tangent,
                1.8f,
                delta,
            )
            if (elapsed >= enemy.abilityAt && distance < 1_000f && canAttack(enemy)) {
                enemy.mode = EnemyBehavior.PHASED
                enemy.modeUntil = elapsed + PHANTOM_PHASE_SECONDS
                // Out of phase it is untargetable everywhere a shifting enemy is.
                enemy.shiftUntil = enemy.modeUntil
                enemy.vx = 0f
                enemy.vy = 0f
            }
        }
    }
}

/** Teleports the Phantom about [PHANTOM_REAPPEAR_DISTANCE] from the Core and starts its wind-up. */
private fun MutableGameState.rematerializePhantom(enemy: Enemy) {
    val angle = movementAngle() + (spawnRandom.nextFloat() * 2f - 1f) * 1.4f
    val directionX = cos(angle)
    val directionY = sin(angle)
    placeClear(enemy, coreX + directionX * PHANTOM_REAPPEAR_DISTANCE, coreY + directionY * PHANTOM_REAPPEAR_DISTANCE, directionX, directionY)
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + PHANTOM_WINDUP_SECONDS
    enemy.facing = atan2(coreY - enemy.y, coreX - enemy.x)
    burst(enemy.x, enemy.y, 8, 2)
}

/**
 * Teleports [enemy] to ([startX], [startY]), first nudged up to four times 50 u along ([dirX], [dirY]) while obstacles
 * block the point, with no interpolation from where it was (the Phantom, the Cipher and the Cartographer's open jump).
 */
internal fun MutableGameState.placeClear(enemy: Enemy, startX: Float, startY: Float, dirX: Float, dirY: Float) {
    var x = startX
    var y = startY
    var attempts = 0
    while (attempts < 4 && isSpawnPointBlocked(x, y, enemy.radius)) {
        x += dirX * 50f
        y += dirY * 50f
        attempts++
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
}

internal fun MutableGameState.updateBroodmother(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val desired = if (distance > 260f) 40f else 0f
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    steerEnemy(
        enemy,
        dx / distance * desired - dy / distance * 22f * tangent,
        dy / distance * desired + dx / distance * 22f * tangent,
        0.9f,
        delta,
    )
    enemy.actionTimer -= cadence
    if (enemy.actionTimer > 0f) return
    enemy.actionTimer = BROOD_SECONDS
    if (!canAttack(enemy)) return
    val room = ordinaryEnemyCap() - ordinaryCapLoad()
    val brood = min(BROOD_SIZE, (room / SWARMLING_CAP_COST + 1e-4f).toInt())
    for (index in 0 until brood) {
        if (enemies.size >= content.rebirth.maxActiveEnemies) return
        val angle = index * TAU / BROOD_SIZE + elapsed
        val offset = enemy.radius + 14f
        val child = spawnedEnemy(EnemyType.SWARMLING, enemy.x + cos(angle) * offset, enemy.y + sin(angle) * offset)
        child.vx = cos(angle) * 120f
        child.vy = sin(angle) * 120f
        enemies += child
    }
}

internal fun MutableGameState.updateReflector(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    val toCore = atan2(dy, dx)
    enemy.facing = turnToward(enemy.facing, toCore, REFLECTOR_TURN_RATE * delta)
    enemy.guarded = angleBetween(enemy.facing, toCore) <= REFLECTOR_ARC
    val desired = if (distance > 170f) 55f else 0f
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    steerEnemy(
        enemy,
        dx / distance * desired - dy / distance * 20f * tangent,
        dy / distance * desired + dx / distance * 20f * tangent,
        1.2f,
        delta,
    )
}

/**
 * True when a hit arriving from ([fromX], [fromY]) meets a directional guard: the Reflector's front shield, or one of
 * the shield arcs of the Throne or the Antecedent.
 */
internal fun shieldFaces(enemy: Enemy, fromX: Float, fromY: Float): Boolean {
    val theta = atan2(fromY - enemy.y, fromX - enemy.x)
    return when (enemy.type) {
        EnemyType.REFLECTOR -> angleBetween(enemy.facing, theta) <= REFLECTOR_ARC
        EnemyType.THRONE, EnemyType.ANTECEDENT -> arcGuards(enemy, theta)
        else -> false
    }
}

/** Sends a blocked shot back at the Core with the Reflector's own projectile damage, at most once per 0.3 s. */
internal fun MutableGameState.reflectShot(enemy: Enemy) {
    if (elapsed < enemy.abilityAt || !canAttack(enemy)) return
    enemy.abilityAt = elapsed + REFLECTOR_REFLECT_SECONDS
    val angle = atan2(coreY - enemy.y, coreX - enemy.x)
    val shotSpeed = REFLECTOR_SHOT_SPEED * rebirthProfile.enemySpeedMultiplier
    addProjectile(Projectile(
        x = enemy.x + cos(angle) * (enemy.radius + 6f),
        y = enemy.y + sin(angle) * (enemy.radius + 6f),
        vx = cos(angle) * shotSpeed,
        vy = sin(angle) * shotSpeed,
        radius = 5f,
        life = 3f,
        damage = shotDamage(enemy),
    ))
}
