// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
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

internal const val CHARGER_STRIKE_SECONDS: Float = 0.55f
internal const val BOMBER_PRIME_DISTANCE: Float = 110f
internal const val BOMBER_PRIME_SECONDS: Float = 0.6f
internal const val BOMBER_BLAST_RADIUS: Float = 120f
internal const val BOMBER_BLAST_DAMAGE: Float = 18f
/** A Core impact at least this fast destroys an unprimed Bomber outright, without a blast. */
internal const val BOMBER_DEFUSE_SPEED: Float = 450f
internal const val LANCER_TELEGRAPH_SECONDS: Float = 0.7f
internal const val LANCER_DISTANCE: Float = 900f
internal const val LANCER_SPEED: Float = 1_100f
internal const val LANCER_DAMAGE: Float = 16f
internal const val LANCER_RECOVERY_SECONDS: Float = 1.2f
internal const val LANCER_STUN_SECONDS: Float = 1f
/** Pause after a recovery before the next telegraph, at the base attack rate. */
internal const val LANCER_COOLDOWN_SECONDS: Float = 2.6f
internal const val LANCER_ENGAGE_RANGE: Float = 650f
internal const val SNIPER_MIN_RANGE: Float = 600f
internal const val SNIPER_MAX_RANGE: Float = 820f
internal const val SNIPER_AIM_SECONDS: Float = 1.1f
internal const val SNIPER_SHOT_SPEED: Float = 900f
internal const val AEGIS_LINK_RANGE: Float = 220f
internal const val AEGIS_MAX_LINKS: Int = 3
/** Linked allies take this share less damage. */
internal const val AEGIS_LINK_REDUCTION: Float = 0.6f
internal const val MINE_ARM_SECONDS: Float = 0.8f
internal const val MINE_LIFE_SECONDS: Float = 8f
internal const val MINE_TRIGGER_RADIUS: Float = 60f
internal const val MINE_BLAST_RADIUS: Float = 70f
internal const val MINE_RADIUS: Float = 9f
internal const val MINE_DROP_SECONDS: Float = 3f
/** Minelayers never drop a mine closer than this to the Core, and stop while this many mines are live. */
internal const val MINE_MINIMUM_CORE_DISTANCE: Float = 260f
internal const val MAX_LIVE_MINES: Int = 10
internal const val PHANTOM_PHASE_SECONDS: Float = 1.4f
internal const val PHANTOM_REAPPEAR_DISTANCE: Float = 260f
internal const val PHANTOM_WINDUP_SECONDS: Float = 0.35f
internal const val PHANTOM_STRIKE_SPEED: Float = 520f
internal const val PHANTOM_STRIKE_SECONDS: Float = 0.5f
internal const val PHANTOM_STRIKE_DAMAGE: Float = 14f
internal const val BROOD_SIZE: Int = 3
internal const val BROOD_SECONDS: Float = 4f
internal const val REFLECTOR_ARC: Float = 60f * (kotlin.math.PI / 180.0).toFloat()
internal const val REFLECTOR_TURN_RATE: Float = 1.1f
internal const val REFLECTOR_SHOT_SPEED: Float = 600f
internal const val REFLECTOR_REFLECT_SECONDS: Float = 0.3f
internal const val HERALD_SHIELD_SECONDS: Float = 3f
internal const val HERALD_SHIELD_INTERVAL_SECONDS: Float = 12f
internal const val HERALD_FIRST_SHIELD_SECONDS: Float = 6f

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

private fun turnToward(current: Float, target: Float, maximumStep: Float): Float {
    var difference = target - current
    while (difference > TAU * 0.5f) difference -= TAU
    while (difference < -TAU * 0.5f) difference += TAU
    return current + clamp(difference, -maximumStep, maximumStep)
}

private fun angleBetween(a: Float, b: Float): Float {
    var difference = a - b
    while (difference > TAU * 0.5f) difference -= TAU
    while (difference < -TAU * 0.5f) difference += TAU
    return abs(difference)
}

private fun Enemy.damp(rate: Float, delta: Float) {
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
    val swirl = if (enemy.id % 2 == 0) 1f else -1f
    var separationX = 0f
    var separationY = 0f
    for (index in enemies.indices) {
        val other = enemies[index]
        if (other === enemy || other.type != EnemyType.SWARMLING || other.dead) continue
        val ox = enemy.x - other.x
        val oy = enemy.y - other.y
        val squared = ox * ox + oy * oy
        if (squared >= SWARM_SPACING * SWARM_SPACING || squared < 1e-4f) continue
        val apart = kotlin.math.sqrt(squared)
        val push = (SWARM_SPACING - apart) / SWARM_SPACING
        separationX += ox / apart * push
        separationY += oy / apart * push
    }
    steerEnemy(
        enemy,
        dx / distance * 150f - dy / distance * 42f * swirl + separationX * 110f,
        dy / distance * 150f + dx / distance * 42f * swirl + separationY * 110f,
        3.2f,
        delta,
    )
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
    // Tether the three nearest allies in range; the Herald, the Architect and other Aegises are never linked.
    var firstIndex = -1
    var secondIndex = -1
    var thirdIndex = -1
    var first = Float.MAX_VALUE
    var second = Float.MAX_VALUE
    var third = Float.MAX_VALUE
    val rangeSquared = AEGIS_LINK_RANGE * AEGIS_LINK_RANGE
    for (index in enemies.indices) {
        val ally = enemies[index]
        if (ally === enemy || !ally.alive || ally.isBoss || ally.isMiniBoss || ally.type == EnemyType.AEGIS) continue
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
    if (firstIndex >= 0) link(enemies[firstIndex], enemy)
    if (secondIndex >= 0) link(enemies[secondIndex], enemy)
    if (thirdIndex >= 0) link(enemies[thirdIndex], enemy)
}

private fun MutableGameState.link(ally: Enemy, aegis: Enemy) {
    ally.linkedBy = aegis.id
    ally.linkedUntil = elapsed + AEGIS_LINK_HOLD_SECONDS
}

/** A link lapses this long after its Aegis stops renewing it (destroyed, stunned, out of range). */
private const val AEGIS_LINK_HOLD_SECONDS: Float = 0.2f

/** True while an Aegis tethers [enemy]. */
internal fun MutableGameState.isLinked(enemy: Enemy): Boolean = enemy.linkedBy >= 0 && enemy.linkedUntil > elapsed

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

private fun MutableGameState.liveMines(): Int {
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
    var x = coreX + cos(angle) * PHANTOM_REAPPEAR_DISTANCE
    var y = coreY + sin(angle) * PHANTOM_REAPPEAR_DISTANCE
    var attempts = 0
    while (attempts < 4 && isSpawnPointBlocked(x, y, enemy.radius)) {
        x += cos(angle) * 50f
        y += sin(angle) * 50f
        attempts++
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + PHANTOM_WINDUP_SECONDS
    enemy.facing = atan2(coreY - y, coreX - x)
    burst(x, y, 8, 2)
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

/** True when a hit arriving from ([fromX], [fromY]) meets the Reflector's front shield. */
internal fun reflectorShieldFaces(enemy: Enemy, fromX: Float, fromY: Float): Boolean =
    enemy.type == EnemyType.REFLECTOR && angleBetween(enemy.facing, atan2(fromY - enemy.y, fromX - enemy.x)) <= REFLECTOR_ARC

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
        else -> Unit
    }
}
