// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Behaviours of the 0.6.0 enemy kinds. Each runs only while the enemy can act and checks canAttack before
 * every attack; modes are EnemyBehavior values with absolute run times, so presentation reads the same
 * fields. Every step is allocation-free: index loops and the inline obstacle and surface queries only.
 */

internal const val COMET_TELEGRAPH_SECONDS: Float = 0.6f
internal const val COMET_SPEED: Float = 720f
internal const val COMET_LANE_LENGTH: Float = 1_500f
/** The lane passes this far beside the Core's predicted position, on the Comet's own side. */
internal const val COMET_LANE_OFFSET: Float = 70f
internal const val COMET_RECOVERY_SECONDS: Float = 0.9f
/** Pause after a recovery before the next lane, at the base attack rate. */
internal const val COMET_COOLDOWN_SECONDS: Float = 1.8f
internal const val COMET_ENGAGE_RANGE: Float = 900f
/** Seconds of the Core's velocity the Comet's lane leads by. */
internal const val COMET_LEAD_SECONDS: Float = 0.45f
private const val COMET_FIRST_LANE_SECONDS: Float = 1f
private const val COMET_HOLD_FAR: Float = 620f
private const val COMET_HOLD_NEAR: Float = 420f
private const val COMET_RADIAL_SPEED: Float = 120f
private const val COMET_ORBIT_SPEED: Float = 90f
private const val COMET_AGILITY: Float = 2f

internal const val PYLON_DOCK_SEARCH: Float = 600f
/** Room a docked Pylon keeps between its body and its rock. */
internal const val PYLON_DOCK_GAP: Float = 6f
internal const val PYLON_SEEK_SECONDS: Float = 4f
internal const val PYLON_SEEK_SPEED: Float = 140f
internal const val PYLON_TELEGRAPH_SECONDS: Float = 0.8f
/** The sight line stops tracking the Core this long before the volley. */
internal const val PYLON_LOCK_SECONDS: Float = 0.2f
internal const val PYLON_RANGE: Float = 900f
internal const val PYLON_COOLDOWN_SECONDS: Float = 3f
internal const val PYLON_SHOT_SPEED: Float = 280f
internal const val PYLON_VOLLEY: Int = 3
internal const val PYLON_DENSE_VOLLEY: Int = 5
internal const val PYLON_VOLLEY_SPACING: Float = 0.16f
/** A seeking Pylon this close to its dock point mounts there. */
internal const val PYLON_DOCK_TOLERANCE: Float = 10f
/** A Pylon waits this long after mounting before its first sight line. */
internal const val PYLON_MOUNT_SECONDS: Float = 1f
private const val PYLON_SIGHT_RADIUS: Float = 7f

internal const val SENTRY_MOUNT_DISTANCE: Float = 560f
internal const val SENTRY_TURN_RATE: Float = 2.4f
internal const val SENTRY_SIGHT_RANGE: Float = 760f
internal const val SENTRY_AIM_TOLERANCE: Float = 0.25f
internal const val SENTRY_TELEGRAPH_SECONDS: Float = 0.45f
internal const val SENTRY_COOLDOWN_SECONDS: Float = 1.6f
internal const val SENTRY_TRAIN: Int = 3
internal const val SENTRY_BOLT_SPEED: Float = 330f
internal const val SENTRY_BOLT_RADIUS: Float = 6f
internal const val SENTRY_BOLT_LIFE: Float = 2.4f
/** Gap between the bolts of one train along the Sentry's facing. */
internal const val SENTRY_BOLT_SPACING: Float = 28f
private const val SENTRY_APPROACH_SPEED: Float = 90f
private const val SENTRY_APPROACH_AGILITY: Float = 1.6f
private const val SENTRY_SIGHT_RADIUS: Float = 6f

internal const val CRAWLER_HUG_RANGE: Float = 160f
internal const val CRAWLER_CRAWL_SPEED: Float = 150f
internal const val CRAWLER_LUNGE_DISTANCE: Float = 200f
internal const val CRAWLER_LUNGE_SPEED: Float = 230f
/** Gap from its centre to the surface a hugging Crawler steers toward. */
internal const val CRAWLER_HUG_GAP: Float = 6f
private const val CRAWLER_LUNGE_AGILITY: Float = 3.5f
private const val CRAWLER_AGILITY: Float = 3.2f
private const val CRAWLER_HUG_GAIN: Float = 2.5f
private const val CRAWLER_HUG_PUSH: Float = -40f
private const val CRAWLER_HUG_PULL: Float = 60f
private const val CRAWLER_SPACING: Float = 30f
private const val CRAWLER_APPROACH: Float = 165f
private const val CRAWLER_SWIRL: Float = 60f
private const val CRAWLER_SEPARATION: Float = 110f

/** A 0.6.0 kind whose behaviour has not landed: it steers like a Drifter. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updatePendingKind(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    steerEnemy(enemy, dx / distance * 78f, dy / distance * 78f, 2.1f, delta)
}

/** Initial modes and timers of the 0.6.0 kinds (facing already points at the Core). */
internal fun MutableGameState.initializeWorldsBehavior(enemy: Enemy) {
    when (enemy.type) {
        EnemyType.COMET -> enemy.abilityAt = elapsed + COMET_FIRST_LANE_SECONDS
        EnemyType.PYLON -> {
            enemy.mode = EnemyBehavior.IDLE
            enemy.modeUntil = elapsed + PYLON_SEEK_SECONDS
        }
        // A Sentry its encounter placed is already mounted; an ambient one walks in first.
        EnemyType.SENTRY -> enemy.mode = if (enemy.ownerTag != 0L) EnemyBehavior.ANCHORED else EnemyBehavior.IDLE
        else -> Unit
    }
}

/** A leashed Pylon or Sentry moved ahead of the Core leaves its mount and seeks a new one. */
internal fun MutableGameState.onLeashedEnemyRelocated(enemy: Enemy) {
    if (enemy.type != EnemyType.PYLON && enemy.type != EnemyType.SENTRY) return
    enemy.mode = EnemyBehavior.IDLE
    enemy.modeUntil = elapsed + PYLON_SEEK_SECONDS
}

/**
 * Separation flocking toward the Core: [approach] straight in, [swirl] sideways (side by id parity) and a push
 * of [separationGain] away from others of the same kind closer than [spacing]. The Swarmling flocks with
 * (34, 150, 42, 110, 3.2).
 */
internal fun MutableGameState.flock(
    enemy: Enemy,
    dx: Float,
    dy: Float,
    distance: Float,
    delta: Float,
    spacing: Float,
    approach: Float,
    swirl: Float,
    separationGain: Float,
    agility: Float,
) {
    val side = if (enemy.id % 2 == 0) 1f else -1f
    var separationX = 0f
    var separationY = 0f
    for (index in enemies.indices) {
        val other = enemies[index]
        if (other === enemy || other.type != enemy.type || other.dead) continue
        val ox = enemy.x - other.x
        val oy = enemy.y - other.y
        val squared = ox * ox + oy * oy
        if (squared >= spacing * spacing || squared < 1e-4f) continue
        val apart = sqrt(squared)
        val push = (spacing - apart) / spacing
        separationX += ox / apart * push
        separationY += oy / apart * push
    }
    steerEnemy(
        enemy,
        dx / distance * approach - dy / distance * swirl * side + separationX * separationGain,
        dy / distance * approach + dx / distance * swirl * side + separationY * separationGain,
        agility,
        delta,
    )
}

/** True for an obstacle whose body is always there to dock on or crawl along. */
internal fun Obstacle.isSteadySurface(): Boolean = kind.solid && !kind.phased

/**
 * Nearest solid surface within [reach] of ([x], [y]): calls [found] once with its outward normal and the gap from
 * the point to it, or never. Obstacles only for now; STRUCTURES adds capsule walls here.
 */
internal inline fun MutableGameState.nearestSurface(
    x: Float,
    y: Float,
    reach: Float,
    found: (nx: Float, ny: Float, gap: Float) -> Unit,
) {
    var bestGap = Float.MAX_VALUE
    var bestNx = 1f
    var bestNy = 0f
    forEachObstacleNear(x, y, reach) { _, obstacle ->
        if (obstacle.isSteadySurface()) {
            val ox = x - obstacle.x
            val oy = y - obstacle.y
            val apart = sqrt(ox * ox + oy * oy)
            val gap = apart - obstacle.radius
            if (gap <= reach && gap < bestGap) {
                bestGap = gap
                if (apart > 1e-3f) {
                    bestNx = ox / apart
                    bestNy = oy / apart
                } else {
                    bestNx = 1f
                    bestNy = 0f
                }
            }
        }
    }
    if (bestGap <= reach) found(bestNx, bestNy, bestGap)
}

/**
 * COMET: holds 420-620 u out and circles; then marks a lane past the Core's lead point ([COMET_TELEGRAPH_SECONDS]),
 * streaks along it at [COMET_SPEED] for [COMET_LANE_LENGTH], recovers and cools down. Its streak never stops at an
 * obstacle: the contact slams it.
 */
internal fun MutableGameState.updateComet(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    when (enemy.mode) {
        EnemyBehavior.TELEGRAPH -> {
            enemy.damp(6f, delta)
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) {
                    val streakSpeed = COMET_SPEED * rebirthProfile.enemySpeedMultiplier
                    enemy.mode = EnemyBehavior.STRIKE
                    enemy.modeUntil = elapsed + COMET_LANE_LENGTH / streakSpeed
                    enemy.vx = cos(enemy.facing) * streakSpeed
                    enemy.vy = sin(enemy.facing) * streakSpeed
                } else {
                    enemy.mode = EnemyBehavior.RECOVERY
                    enemy.modeUntil = elapsed + COMET_RECOVERY_SECONDS
                }
            }
        }
        EnemyBehavior.STRIKE -> if (elapsed >= enemy.modeUntil) {
            enemy.mode = EnemyBehavior.RECOVERY
            enemy.modeUntil = elapsed + COMET_RECOVERY_SECONDS
        } else {
            val streakSpeed = COMET_SPEED * rebirthProfile.enemySpeedMultiplier
            enemy.vx = cos(enemy.facing) * streakSpeed
            enemy.vy = sin(enemy.facing) * streakSpeed
        }
        EnemyBehavior.RECOVERY -> {
            enemy.damp(2.2f, delta)
            if (elapsed >= enemy.modeUntil) {
                enemy.mode = EnemyBehavior.IDLE
                enemy.abilityAt = elapsed + COMET_COOLDOWN_SECONDS / hostileAttackRate()
            }
        }
        else -> {
            val radial = if (distance > COMET_HOLD_FAR) COMET_RADIAL_SPEED else if (distance < COMET_HOLD_NEAR) -COMET_RADIAL_SPEED else 0f
            val side = if (enemy.id % 2 == 0) 1f else -1f
            steerEnemy(
                enemy,
                dx / distance * radial - dy / distance * COMET_ORBIT_SPEED * side,
                dy / distance * radial + dx / distance * COMET_ORBIT_SPEED * side,
                COMET_AGILITY,
                delta,
            )
            if (elapsed >= enemy.abilityAt && distance < COMET_ENGAGE_RANGE && canAttack(enemy)) {
                val aimX = coreX + velocityX * COMET_LEAD_SECONDS - dy / distance * COMET_LANE_OFFSET * side
                val aimY = coreY + velocityY * COMET_LEAD_SECONDS + dx / distance * COMET_LANE_OFFSET * side
                enemy.facing = atan2(aimY - enemy.y, aimX - enemy.x)
                enemy.mode = EnemyBehavior.TELEGRAPH
                enemy.modeUntil = elapsed + COMET_TELEGRAPH_SECONDS
            }
        }
    }
}

/**
 * PYLON: seeks the nearest steady obstacle within [PYLON_DOCK_SEARCH] and docks on its Core side (mounting where
 * it is when none is near or [PYLON_SEEK_SECONDS] run out); mounted, it never moves, and with a clear sight line
 * within [PYLON_RANGE] it aims for [PYLON_TELEGRAPH_SECONDS] and fires an aimed volley.
 */
internal fun MutableGameState.updatePylon(enemy: Enemy, dx: Float, dy: Float, distance: Float) {
    when (enemy.mode) {
        EnemyBehavior.ANCHORED -> {
            enemy.vx = 0f
            enemy.vy = 0f
            enemy.facing = atan2(dy, dx)
            if (elapsed >= enemy.abilityAt && distance < PYLON_RANGE && canAttack(enemy) &&
                !obstacleBlocksSegment(enemy.x, enemy.y, coreX, coreY, PYLON_SIGHT_RADIUS)
            ) {
                enemy.mode = EnemyBehavior.TELEGRAPH
                enemy.modeUntil = elapsed + PYLON_TELEGRAPH_SECONDS
            }
        }
        EnemyBehavior.TELEGRAPH -> {
            enemy.vx = 0f
            enemy.vy = 0f
            // The sight line tracks the Core until a short lock before the volley.
            if (enemy.modeUntil - elapsed > PYLON_LOCK_SECONDS) enemy.facing = atan2(dy, dx)
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy) && !obstacleBlocksSegment(enemy.x, enemy.y, coreX, coreY, PYLON_SIGHT_RADIUS)) {
                    val volley = if (content.tempo.denseAttacks(threatElapsed)) PYLON_DENSE_VOLLEY else PYLON_VOLLEY
                    fireSpread(enemy.x, enemy.y, enemy.facing, volley, PYLON_VOLLEY_SPACING, PYLON_SHOT_SPEED, shotDamage(enemy))
                }
                enemy.mode = EnemyBehavior.ANCHORED
                enemy.modeUntil = elapsed
                enemy.abilityAt = elapsed + PYLON_COOLDOWN_SECONDS / hostileAttackRate()
            }
        }
        else -> seekPylonDock(enemy)
    }
}

private fun MutableGameState.seekPylonDock(enemy: Enemy) {
    if (elapsed >= enemy.modeUntil) {
        mountPylon(enemy)
        return
    }
    var bestGap = Float.MAX_VALUE
    var dockX = 0f
    var dockY = 0f
    var dockRadius = 0f
    forEachObstacleNear(enemy.x, enemy.y, PYLON_DOCK_SEARCH) { _, obstacle ->
        if (obstacle.isSteadySurface()) {
            val gap = length(enemy.x - obstacle.x, enemy.y - obstacle.y) - obstacle.radius
            if (gap <= PYLON_DOCK_SEARCH && gap < bestGap) {
                bestGap = gap
                dockX = obstacle.x
                dockY = obstacle.y
                dockRadius = obstacle.radius
            }
        }
    }
    if (bestGap > PYLON_DOCK_SEARCH) {
        mountPylon(enemy)
        return
    }
    // The dock point sits on the rock's Core side, a small gap off its surface.
    val outX = coreX - dockX
    val outY = coreY - dockY
    val out = max(1e-3f, length(outX, outY))
    val reach = dockRadius + enemy.radius + PYLON_DOCK_GAP
    val toX = dockX + outX / out * reach - enemy.x
    val toY = dockY + outY / out * reach - enemy.y
    val to = length(toX, toY)
    if (to <= PYLON_DOCK_TOLERANCE) {
        mountPylon(enemy)
        return
    }
    // The glide is held on the dock point each step (as a lance is held on its lane): the dock lies inside the rock's
    // avoidance band, where a steered approach would be bent along the surface every step and never arrive.
    if (isThrownByPolarity(enemy)) return
    val glide = PYLON_SEEK_SPEED * rebirthProfile.enemySpeedMultiplier * affixSpeedFactor(enemy)
    enemy.vx = toX / to * glide
    enemy.vy = toY / to * glide
}

private fun MutableGameState.mountPylon(enemy: Enemy) {
    mount(enemy)
    enemy.abilityAt = elapsed + PYLON_MOUNT_SECONDS
}

/** Stops [enemy] where it stands as a mounted turret. */
private fun MutableGameState.mount(enemy: Enemy) {
    enemy.vx = 0f
    enemy.vy = 0f
    enemy.mode = EnemyBehavior.ANCHORED
    enemy.modeUntil = elapsed
}

/**
 * SENTRY: an ambient Sentry walks in and mounts [SENTRY_MOUNT_DISTANCE] from the Core; mounted, it turns toward
 * the Core at [SENTRY_TURN_RATE] and, aimed within [SENTRY_AIM_TOLERANCE] with a clear sight line inside
 * [SENTRY_SIGHT_RANGE], locks its facing for [SENTRY_TELEGRAPH_SECONDS] and fires a train of [SENTRY_TRAIN] bolts.
 */
internal fun MutableGameState.updateSentry(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    when (enemy.mode) {
        EnemyBehavior.ANCHORED -> {
            enemy.vx = 0f
            enemy.vy = 0f
            val toCore = atan2(dy, dx)
            enemy.facing = turnToward(enemy.facing, toCore, SENTRY_TURN_RATE * delta)
            if (elapsed >= enemy.abilityAt && distance < SENTRY_SIGHT_RANGE && angleBetween(enemy.facing, toCore) < SENTRY_AIM_TOLERANCE &&
                canAttack(enemy) && !obstacleBlocksSegment(enemy.x, enemy.y, coreX, coreY, SENTRY_SIGHT_RADIUS)
            ) {
                enemy.mode = EnemyBehavior.TELEGRAPH
                enemy.modeUntil = elapsed + SENTRY_TELEGRAPH_SECONDS
            }
        }
        EnemyBehavior.TELEGRAPH -> {
            enemy.vx = 0f
            enemy.vy = 0f
            if (elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) fireSentryTrain(enemy)
                enemy.mode = EnemyBehavior.ANCHORED
                enemy.modeUntil = elapsed
                enemy.abilityAt = elapsed + SENTRY_COOLDOWN_SECONDS / hostileAttackRate()
            }
        }
        else -> {
            steerEnemy(enemy, dx / distance * SENTRY_APPROACH_SPEED, dy / distance * SENTRY_APPROACH_SPEED, SENTRY_APPROACH_AGILITY, delta)
            if (distance <= SENTRY_MOUNT_DISTANCE) mount(enemy)
        }
    }
}

/** Bolts in a line along the Sentry's facing, all at one velocity, so the train runs down a corridor. */
private fun MutableGameState.fireSentryTrain(enemy: Enemy) {
    val boltSpeed = SENTRY_BOLT_SPEED * rebirthProfile.enemySpeedMultiplier
    val directionX = cos(enemy.facing)
    val directionY = sin(enemy.facing)
    val damage = shotDamage(enemy)
    for (bolt in 0 until SENTRY_TRAIN) {
        val offset = enemy.radius + 4f - SENTRY_BOLT_SPACING * bolt
        addProjectile(Projectile(
            x = enemy.x + directionX * offset,
            y = enemy.y + directionY * offset,
            vx = directionX * boltSpeed,
            vy = directionY * boltSpeed,
            radius = SENTRY_BOLT_RADIUS,
            life = SENTRY_BOLT_LIFE,
            damage = damage,
        ))
    }
}

/**
 * CRAWLER: lunges inside [CRAWLER_LUNGE_DISTANCE]; otherwise hugs the nearest surface within [CRAWLER_HUG_RANGE],
 * creeping along it toward the Core's side, and flocks with other Crawlers in the open.
 */
internal fun MutableGameState.updateCrawler(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    if (distance < CRAWLER_LUNGE_DISTANCE) {
        steerEnemy(enemy, dx / distance * CRAWLER_LUNGE_SPEED, dy / distance * CRAWLER_LUNGE_SPEED, CRAWLER_LUNGE_AGILITY, delta)
        return
    }
    nearestSurface(enemy.x, enemy.y, CRAWLER_HUG_RANGE) { nx, ny, gap ->
        var tangentX = -ny
        var tangentY = nx
        if (tangentX * dx + tangentY * dy < 0f) {
            tangentX = -tangentX
            tangentY = -tangentY
        }
        val pull = clamp(gap - CRAWLER_HUG_GAP, CRAWLER_HUG_PUSH, CRAWLER_HUG_PULL) * CRAWLER_HUG_GAIN
        steerEnemy(
            enemy,
            tangentX * CRAWLER_CRAWL_SPEED - nx * pull,
            tangentY * CRAWLER_CRAWL_SPEED - ny * pull,
            CRAWLER_AGILITY,
            delta,
        )
        return
    }
    flock(enemy, dx, dy, distance, delta, CRAWLER_SPACING, CRAWLER_APPROACH, CRAWLER_SWIRL, CRAWLER_SEPARATION, CRAWLER_AGILITY)
}
