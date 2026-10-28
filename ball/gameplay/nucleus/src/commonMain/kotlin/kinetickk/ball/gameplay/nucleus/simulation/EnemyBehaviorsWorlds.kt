// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Behaviours of the 0.6.0 enemy kinds (space, structure and dimension kinds). Each runs only while the enemy can act
 * and checks canAttack before every attack; modes are EnemyBehavior values with absolute run times, so presentation
 * reads the same fields. Every step is allocation-free: index loops and the inline obstacle and surface queries only.
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

internal const val SERAPH_PULSE_SECONDS: Float = 3f
internal const val SERAPH_LINK_RANGE: Float = 260f
internal const val SERAPH_MAX_LINKS: Int = 2
/** Share of an ally's integrity one mend restores. */
internal const val SERAPH_HEAL_SHARE: Float = 0.06f
internal const val SERAPH_SHIELD_SECONDS: Float = 1.5f
private const val SERAPH_HOLD_NEAR: Float = 380f
private const val SERAPH_HOLD_FAR: Float = 520f
private const val SERAPH_RETREAT_SPEED: Float = 90f
private const val SERAPH_CLOSE_SPEED: Float = 70f
private const val SERAPH_WEAVE: Float = 150f
private const val SERAPH_WEAVE_RATE: Float = 2.4f
private const val SERAPH_WEAVE_PHASE: Float = 0.9f
private const val SERAPH_AGILITY: Float = 2.4f

internal const val CHOIR_PULSE_SECONDS: Float = 3.2f
internal const val CHOIR_TELEGRAPH_SECONDS: Float = 0.6f
/** A pulse shoves a Core within this reach of the Choir (plus the Core's radius). */
internal const val CHOIR_WAVE_RADIUS: Float = 300f
internal const val CHOIR_WAVE_PUSH: Float = 260f
internal const val CHOIR_VOLLEY: Int = 10
internal const val CHOIR_DENSE_VOLLEY: Int = 12
internal const val CHOIR_SHOT_SPEED: Float = 140f
/** Radians per second the pulse's bolt ring turns with the run clock. */
internal const val CHOIR_VOLLEY_SPIN: Float = 0.5f
private const val CHOIR_HOLD_FAR: Float = 420f
private const val CHOIR_HOLD_NEAR: Float = 300f
private const val CHOIR_CLOSE_SPEED: Float = 60f
private const val CHOIR_RETREAT_SPEED: Float = 56f
private const val CHOIR_ORBIT_SPEED: Float = 30f
private const val CHOIR_AGILITY: Float = 1.55f

internal const val IMP_PRIME_DISTANCE: Float = 130f
internal const val IMP_PRIME_SECONDS: Float = 0.5f
internal const val IMP_BLAST_RADIUS: Float = 95f
internal const val IMP_BLAST_DAMAGE: Float = 12f
internal const val IMP_WEAVE: Float = 110f
/** Embers a burst leaves: at its centre and this far to either side across its heading, after these delays. */
internal const val IMP_EMBER_SPREAD: Float = 60f
internal const val IMP_EMBER_SECONDS: Float = 0.5f
internal const val IMP_LEFT_EMBER_SECONDS: Float = 0.7f
internal const val IMP_RIGHT_EMBER_SECONDS: Float = 0.9f
/** A killed Imp leaves one ember after this delay. */
internal const val IMP_KILL_EMBER_SECONDS: Float = 0.6f
private const val IMP_WEAVE_RATE: Float = 3.1f
private const val IMP_AGILITY: Float = 1.8f

internal const val HOUND_WINDUP: Float = 0.35f
internal const val HOUND_DASH_SPEED: Float = 430f
internal const val HOUND_STRIKE_SECONDS: Float = 0.45f
internal const val HOUND_DASH_CADENCE: Float = 2.6f
private const val HOUND_SPACING: Float = 46f
private const val HOUND_APPROACH: Float = 125f
private const val HOUND_SWIRL: Float = 50f
private const val HOUND_SEPARATION: Float = 120f
private const val HOUND_AGILITY: Float = 3f
/** Below this speed a Hound faces the Core instead of its motion. */
private const val HOUND_FACING_SPEED: Float = 20f

internal const val CIPHER_GLITCH_SECONDS: Float = 3.4f
internal const val CIPHER_PHASE_SECONDS: Float = 0.35f
internal const val CIPHER_AIM_SECONDS: Float = 0.3f
internal const val CIPHER_VOLLEY: Int = 3
internal const val CIPHER_VOLLEY_SPACING: Float = 0.12f
internal const val CIPHER_SHOT_SPEED: Float = 260f
internal const val CIPHER_ENGAGE_RANGE: Float = 1_000f
/** A glitch puts the Cipher back between these distances from the Core. */
internal const val CIPHER_NEAREST: Float = 280f
internal const val CIPHER_FARTHEST: Float = 520f
/** A glitch turns the Cipher around the Core by 0.9-1.4 rad, either way. */
internal const val CIPHER_TURN: Float = 0.9f
internal const val CIPHER_TURN_SPREAD: Float = 0.5f
/** Salt of the stateless glitch hash (no run stream is drawn). */
internal const val CIPHER_SALT: Long = 0x0C1F_E55E_ED00_0013L
private const val CIPHER_FIRST_GLITCH_SECONDS: Float = 2f
private const val CIPHER_HASH_TICKS: Float = 120f
private const val CIPHER_NUDGE_ATTEMPTS: Int = 4
private const val CIPHER_NUDGE_DISTANCE: Float = 50f
private const val CIPHER_HOLD_FAR: Float = 420f
private const val CIPHER_HOLD_NEAR: Float = 300f
private const val CIPHER_CLOSE_SPEED: Float = 78f
private const val CIPHER_RETREAT_SPEED: Float = 72f
private const val CIPHER_ORBIT_SPEED: Float = 62f
private const val CIPHER_AGILITY: Float = 2.4f

/** The Mirror steers this many times its offset from its mirror point, at most [MIRROR_MAX_SPEED]. */
internal const val MIRROR_GAIN: Float = 3f
internal const val MIRROR_MAX_SPEED: Float = 760f
internal const val MIRROR_AGILITY: Float = 3.5f
internal const val MIRROR_SHOT_SECONDS: Float = 3f
internal const val MIRROR_VOLLEY: Int = 2
internal const val MIRROR_VOLLEY_SPACING: Float = 0.2f
internal const val MIRROR_SHOT_SPEED: Float = 230f

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
        EnemyType.CIPHER -> enemy.abilityAt = elapsed + CIPHER_FIRST_GLITCH_SECONDS
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
                if (canAttack(enemy)) {
                    fireBoltTrain(
                        enemy.x, enemy.y, enemy.facing, enemy.radius + 4f, SENTRY_TRAIN, SENTRY_BOLT_SPACING, SENTRY_BOLT_SPEED,
                        SENTRY_BOLT_RADIUS, SENTRY_BOLT_LIFE, shotDamage(enemy),
                    )
                }
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

/**
 * [count] bolts in a line along [angle], all at one velocity, so the train runs down a lane: the lead bolt
 * [leadOffset] from ([x], [y]), each next one [spacing] behind it (a Sentry's corridor, a Throne's judgement).
 */
internal fun MutableGameState.fireBoltTrain(
    x: Float,
    y: Float,
    angle: Float,
    leadOffset: Float,
    count: Int,
    spacing: Float,
    boltSpeed: Float,
    boltRadius: Float,
    life: Float,
    damage: Float,
) {
    val speed = boltSpeed * rebirthProfile.enemySpeedMultiplier
    val directionX = cos(angle)
    val directionY = sin(angle)
    for (bolt in 0 until count) {
        val offset = leadOffset - spacing * bolt
        addProjectile(Projectile(
            x = x + directionX * offset,
            y = y + directionY * offset,
            vx = directionX * speed,
            vy = directionY * speed,
            radius = boltRadius,
            life = life,
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
    // The two nearest eligible allies in range, kept in locals (the Aegis's selection with two slots).
    var firstIndex = -1
    var secondIndex = -1
    var first = Float.MAX_VALUE
    var second = Float.MAX_VALUE
    val rangeSquared = SERAPH_LINK_RANGE * SERAPH_LINK_RANGE
    for (index in enemies.indices) {
        val ally = enemies[index]
        if (ally === enemy || !ally.alive || ally.isBossClass || ally.type == EnemyType.SERAPH || isShifting(ally)) continue
        val squared = distanceSquared(enemy.x, enemy.y, ally.x, ally.y)
        if (squared > rangeSquared) continue
        if (squared < first) {
            secondIndex = firstIndex
            second = first
            firstIndex = index
            first = squared
        } else if (squared < second) {
            secondIndex = index
            second = squared
        }
    }
    if (firstIndex >= 0) mendAlly(enemies[firstIndex])
    if (secondIndex >= 0) mendAlly(enemies[secondIndex])
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
            if (distance <= CHOIR_WAVE_RADIUS + coreRadius && dashPhaseTime <= 0f) {
                velocityX += dx / distance * CHOIR_WAVE_PUSH
                velocityY += dy / distance * CHOIR_WAVE_PUSH
            }
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
    var x = coreX + directionX * reach
    var y = coreY + directionY * reach
    var attempts = 0
    while (attempts < CIPHER_NUDGE_ATTEMPTS && isSpawnPointBlocked(x, y, enemy.radius)) {
        x += directionX * CIPHER_NUDGE_DISTANCE
        y += directionY * CIPHER_NUDGE_DISTANCE
        attempts++
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + CIPHER_AIM_SECONDS
    enemy.facing = atan2(coreY - y, coreX - x)
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
