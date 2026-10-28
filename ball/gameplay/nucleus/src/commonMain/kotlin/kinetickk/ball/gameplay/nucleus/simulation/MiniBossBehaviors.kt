// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The mini-bosses of 0.6.0: Warden Prime, Brood Queen, Throne and Pyre Herald. Each is gated by its content encounter
 * spec (thirds, a 3 % hit cap, a shift at each gate) and runs its phase start once per step outside the damage
 * pipeline (startMiniBossPhase). While a mini-boss shifts it attacks nothing: each update holds its attack clocks.
 * Attack cadence runs faster while the boss is enraged (bossAttackScale). Nothing here draws from a run stream.
 */

/** A gate drops this many repairs at the mini-boss (the Architect drops four). */
internal const val MINI_BOSS_SHIFT_REPAIRS: Int = 2
/** The shift's shockwave reaches this many radii. */
internal const val MINI_BOSS_SHIFT_RING: Float = 2.4f
private const val MINI_BOSS_RADIAL_GAIN: Float = 0.4f
internal const val MINI_BOSS_RADIAL_LIMIT: Float = 60f
/** Bone-family effects (shift rings, burst sacs, shattered arcs). */
internal const val MINI_BOSS_MARK_COLOR: Int = 2

internal const val PRIME_WELL_RADIUS: Float = 560f
internal const val PRIME_WELL_BASE_PULL: Float = 60f
internal const val PRIME_WELL_PULL: Float = 320f
/** The well is this much stronger in the final phase. */
internal const val PRIME_FINAL_WELL_SCALE: Float = 1.3f
internal const val PRIME_PULL_SECONDS: Float = 2.5f
internal const val PRIME_PUSH_SECONDS: Float = 1.5f
internal const val PRIME_SHOT_SPEED: Float = 130f
internal const val PRIME_VOLLEY_SPIN: Float = 0.35f
internal const val PRIME_FAN: Int = 3
internal const val PRIME_FAN_SPACING: Float = 0.16f
internal const val PRIME_FAN_SPEED: Float = 250f
/** Wardens the final phase calls in (at half their integrity), and the most alive at once. */
internal const val PRIME_GUARDS: Int = 2
internal const val PRIME_GUARD_SHARE: Float = 0.5f
private const val PRIME_HOLD: Float = 340f
private const val PRIME_ORBIT_SPEED: Float = 40f
private const val PRIME_AGILITY: Float = 1f

internal const val QUEEN_SACS: Int = 3
/** A Core impact at least this fast bursts one egg sac. */
internal const val QUEEN_SAC_BURST_SPEED: Float = 600f
internal const val QUEEN_SAC_COOLDOWN: Float = 0.5f
internal const val QUEEN_BROOD_LAUNCH_SPEED: Float = 140f
internal const val QUEEN_BROOD_LIVE: Int = 10
internal const val QUEEN_SPIRAL_SECONDS: Float = 2f
internal const val QUEEN_SPIRAL: Int = 8
internal const val QUEEN_FINAL_SPIRAL: Int = 10
internal const val QUEEN_SPIRAL_SPEED: Float = 120f
internal const val QUEEN_SPIRAL_SPIN: Float = 1.1f
private const val QUEEN_HOLD: Float = 300f
private const val QUEEN_ORBIT_SPEED: Float = 25f
private const val QUEEN_AGILITY: Float = 0.8f

/** Half the width of one of the Throne's shield arcs (80° each), and the spacing of the arcs from its facing. */
internal const val THRONE_ARC_HALF: Float = 40f * (PI / 180.0).toFloat()
internal const val THRONE_ARC_SPACING: Float = 120f * (PI / 180.0).toFloat()
internal const val THRONE_ARCS: Int = 3
internal const val THRONE_PUNISH_COOLDOWN: Float = 1.5f
internal const val THRONE_PUNISH_RING: Int = 8
internal const val THRONE_PUNISH_SPEED: Float = 160f
internal const val THRONE_VOLLEY_SECONDS: Float = 1.6f
internal const val THRONE_FAN: Int = 3
internal const val THRONE_FAN_SPACING: Float = 0.16f
internal const val THRONE_FAN_SPEED: Float = 250f
internal const val THRONE_RING: Int = 10
internal const val THRONE_RING_SPEED: Float = 130f
internal const val THRONE_JUDGEMENT_SECONDS: Float = 4f
internal const val THRONE_JUDGEMENT_TELEGRAPH_SECONDS: Float = 1f
internal const val THRONE_LANES: Int = 4
internal const val THRONE_TRAIN: Int = 6
internal const val THRONE_TRAIN_SPACING: Float = 34f
internal const val THRONE_TRAIN_SPEED: Float = 420f
internal const val THRONE_TRAIN_RADIUS: Float = 7f
internal const val THRONE_TRAIN_LIFE: Float = 2.2f
/** Radius of the ring the shield arcs sit on, outside the Throne's body. */
internal const val THRONE_ARC_GAP: Float = 9f
private const val THRONE_SHATTER_BURST: Int = 16
private const val THRONE_HOLD: Float = 360f
private const val THRONE_ORBIT_SPEED: Float = 30f
private const val THRONE_AGILITY: Float = 0.9f

internal const val PYRE_FINAL_SHIELD_INTERVAL_SECONDS: Float = 9f
internal const val PYRE_VOLLEY_SECONDS: Float = 1.1f
internal const val PYRE_SHOT_SPEED: Float = 150f
internal const val PYRE_VOLLEY_SPIN: Float = 1.3f
internal const val PYRE_SHOT_LIFE: Float = 6f
/** An ember drops on the Pyre Herald's trail this often, and bursts this long after it dropped. */
internal const val PYRE_EMBER_SECONDS: Float = 0.6f
internal const val PYRE_EMBER_DELAY: Float = 1.2f
/** The final phase starts with a ring of embers around it. */
internal const val PYRE_RING_EMBERS: Int = 6
internal const val PYRE_RING_RADIUS: Float = 160f
internal const val PYRE_RING_DELAY: Float = 0.8f
/** Imps each shield calls in, and the most alive at once. */
internal const val PYRE_IMPS: Int = 2
internal const val PYRE_IMPS_LIVE: Int = 4
private const val PYRE_HOLD: Float = 300f
private const val PYRE_RADIAL_LIMIT: Float = 70f
private const val PYRE_ORBIT_SPEED: Float = 70f
private const val PYRE_AGILITY: Float = 1.2f

/** Initial clocks of a mini-boss (its phase-0 start runs on its first step). */
internal fun MutableGameState.initializeMiniBoss(enemy: Enemy) {
    when (enemy.type) {
        EnemyType.WARDEN_PRIME -> {
            enemy.mode = EnemyBehavior.IDLE
            enemy.modeUntil = elapsed + PRIME_PULL_SECONDS
        }
        EnemyType.PYRE_HERALD -> {
            enemy.abilityAt = elapsed + HERALD_FIRST_SHIELD_SECONDS
            enemy.modeUntil = elapsed + PYRE_EMBER_SECONDS
        }
        else -> Unit
    }
}

/**
 * A mini-boss's phase start, run once per step outside the damage pipeline: phase 0 on its first step, each later
 * one at a gate. A gate also rings a shockwave and drops two repairs.
 */
internal fun MutableGameState.startMiniBossPhase(enemy: Enemy) {
    if (enemy.phase > 0) {
        shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * MINI_BOSS_SHIFT_RING, MINI_BOSS_MARK_COLOR)
        for (index in 0 until MINI_BOSS_SHIFT_REPAIRS) {
            val side = if (index % 2 == 0) 1f else -1f
            addPickup(Pickup(PickupType.REPAIR, enemy.x + side * 22f, enemy.y - 10f * index))
        }
    }
    when (enemy.type) {
        EnemyType.WARDEN_PRIME -> if (enemy.phase >= 2) {
            spawnBossAdds(enemy, EnemyType.WARDEN, PRIME_GUARDS, PRIME_GUARD_SHARE, maxLive = PRIME_GUARDS)
        }
        // The sacs regrow at the first two phase starts; the final phase keeps what is left.
        EnemyType.BROOD_QUEEN -> if (enemy.phase < 2) enemy.charges = QUEEN_SACS
        EnemyType.THRONE -> {
            val arcs = max(1, THRONE_ARCS - enemy.phase)
            // A gate shatters the arcs the new phase loses, each where it stood.
            for (arc in arcs until enemy.charges) {
                val angle = enemy.facing + arc * THRONE_ARC_SPACING
                val reach = enemy.radius + THRONE_ARC_GAP
                burst(enemy.x + cos(angle) * reach, enemy.y + sin(angle) * reach, THRONE_SHATTER_BURST, MINI_BOSS_MARK_COLOR)
            }
            enemy.charges = arcs
        }
        EnemyType.PYRE_HERALD -> if (enemy.phase >= 2) {
            for (index in 0 until PYRE_RING_EMBERS) {
                val angle = index * TAU / PYRE_RING_EMBERS + elapsed
                queueEmber(enemy.x + cos(angle) * PYRE_RING_RADIUS, enemy.y + sin(angle) * PYRE_RING_RADIUS, PYRE_RING_DELAY)
            }
        }
        else -> Unit
    }
}

/**
 * A Core impact on [enemy] at [coreImpactSpeed] (its speed along the contact normal), after the hit: a fast one bursts
 * a Brood Queen's egg sac; one that meets a Throne's shield arc is punished with a ring of bolts.
 */
internal fun MutableGameState.onEnemyCoreImpact(enemy: Enemy, coreImpactSpeed: Float) {
    when (enemy.type) {
        EnemyType.BROOD_QUEEN ->
            if (coreImpactSpeed >= QUEEN_SAC_BURST_SPEED && enemy.charges > 0 && elapsed >= enemy.abilityAt) {
                enemy.charges--
                enemy.abilityAt = elapsed + QUEEN_SAC_COOLDOWN
                burst(enemy.x, enemy.y, 12, MINI_BOSS_MARK_COLOR)
                shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 1.6f, MINI_BOSS_MARK_COLOR)
            }
        EnemyType.THRONE ->
            if (enemy.guarded && elapsed >= enemy.abilityAt && canAttack(enemy) && !isShifting(enemy)) {
                enemy.abilityAt = elapsed + THRONE_PUNISH_COOLDOWN
                fireRadial(enemy.x, enemy.y, THRONE_PUNISH_RING, THRONE_PUNISH_SPEED, enemy.facing, shotDamage(enemy))
            }
        else -> Unit
    }
}

/**
 * True when direction [theta] (from the enemy) falls inside one of its [Enemy.charges] shield arcs: arcs of twice
 * [THRONE_ARC_HALF], centred [THRONE_ARC_SPACING] apart from its facing.
 */
internal fun arcGuards(enemy: Enemy, theta: Float): Boolean {
    for (arc in 0 until enemy.charges) {
        if (angleBetween(theta, enemy.facing + arc * THRONE_ARC_SPACING) <= THRONE_ARC_HALF) return true
    }
    return false
}

/**
 * WARDEN_PRIME: circles 340 u out with a gravity well of [PRIME_WELL_RADIUS]: it pulls the Core in the first phase;
 * afterwards it pulls for [PRIME_PULL_SECONDS] and pushes for [PRIME_PUSH_SECONDS] in turn (stronger in the final
 * phase). Radial volleys of 12 / 14 / 16 bolts; the second phase adds an aimed fan to every other volley.
 */
internal fun MutableGameState.updateWardenPrime(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    holdRange(enemy, dx, dy, distance, PRIME_HOLD, MINI_BOSS_RADIAL_LIMIT, PRIME_ORBIT_SPEED, PRIME_AGILITY, delta)
    val shifting = isShifting(enemy)
    if (enemy.phase == 0 || shifting) {
        enemy.mode = EnemyBehavior.IDLE
        enemy.modeUntil = elapsed + PRIME_PULL_SECONDS
    } else if (elapsed >= enemy.modeUntil) {
        val pushing = enemy.mode == EnemyBehavior.PRIMED
        enemy.mode = if (pushing) EnemyBehavior.IDLE else EnemyBehavior.PRIMED
        enemy.modeUntil = elapsed + if (pushing) PRIME_PULL_SECONDS else PRIME_PUSH_SECONDS
    }
    if (distance < PRIME_WELL_RADIUS && dashPhaseTime <= 0f && canAttack(enemy)) {
        // Ion charge disrupts the well, as it does a Warden's.
        val disruption = max(0.2f, 1f - 0.12f * stacks(enemy, StatusElement.ION))
        var gravity = (PRIME_WELL_BASE_PULL + (1f - distance / PRIME_WELL_RADIUS) * PRIME_WELL_PULL) * disruption
        if (enemy.phase >= 2) gravity *= PRIME_FINAL_WELL_SCALE
        if (enemy.mode == EnemyBehavior.PRIMED) gravity = -gravity
        velocityX -= dx / distance * gravity * delta
        velocityY -= dy / distance * gravity * delta
    }
    val period = primeVolleySeconds(enemy.phase)
    if (shifting) {
        enemy.actionTimer = if (enemy.phase == 1) period * 2f else period
        return
    }
    val before = enemy.actionTimer
    enemy.actionTimer -= cadence * bossAttackScale(enemy)
    if (enemy.phase == 1) {
        // A double cycle: a ring at its half, then a ring and the aimed fan at its end.
        if (before > period && enemy.actionTimer <= period) primeVolley(enemy, dx, dy, fan = false)
        if (enemy.actionTimer <= 0f) {
            enemy.actionTimer = period * 2f
            primeVolley(enemy, dx, dy, fan = true)
        }
    } else if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = period
        primeVolley(enemy, dx, dy, fan = false)
    }
}

/** Seconds between Warden Prime's radial volleys in [phase]. */
internal fun primeVolleySeconds(phase: Int): Float = when (phase) {
    0 -> 2.2f
    1 -> 2f
    else -> 1.8f
}

/** Bolts in Warden Prime's radial volley in [phase]. */
internal fun primeVolleyBolts(phase: Int): Int = when (phase) {
    0 -> 12
    1 -> 14
    else -> 16
}

private fun MutableGameState.primeVolley(enemy: Enemy, dx: Float, dy: Float, fan: Boolean) {
    if (!canAttack(enemy)) return
    val damage = shotDamage(enemy)
    fireRadial(enemy.x, enemy.y, primeVolleyBolts(enemy.phase), PRIME_SHOT_SPEED, elapsed * PRIME_VOLLEY_SPIN, damage)
    if (fan) fireSpread(enemy.x, enemy.y, atan2(dy, dx), PRIME_FAN, PRIME_FAN_SPACING, PRIME_FAN_SPEED, damage)
}

/**
 * BROOD_QUEEN: circles 300 u out and hatches a brood of `1 + sacs` Swarmlings, launched sideways, every 5 / 4 / 3.5 s
 * (at most [QUEEN_BROOD_LIVE] of hers alive); from the second phase a spiral volley every [QUEEN_SPIRAL_SECONDS].
 * Fast impacts burst its sacs ([onEnemyCoreImpact]), so ramming it shrinks its broods.
 */
internal fun MutableGameState.updateBroodQueen(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    holdRange(enemy, dx, dy, distance, QUEEN_HOLD, MINI_BOSS_RADIAL_LIMIT, QUEEN_ORBIT_SPEED, QUEEN_AGILITY, delta)
    val broodSeconds = queenBroodSeconds(enemy.phase)
    if (isShifting(enemy)) {
        enemy.actionTimer = broodSeconds
        enemy.modeUntil = elapsed + bossClockSeconds(enemy, QUEEN_SPIRAL_SECONDS)
        return
    }
    enemy.actionTimer -= cadence * bossAttackScale(enemy)
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = broodSeconds
        if (canAttack(enemy)) hatchBrood(enemy)
    }
    if (enemy.phase >= 1 && elapsed >= enemy.modeUntil) {
        enemy.modeUntil = elapsed + bossClockSeconds(enemy, QUEEN_SPIRAL_SECONDS)
        if (canAttack(enemy)) {
            val bolts = if (enemy.phase >= 2) QUEEN_FINAL_SPIRAL else QUEEN_SPIRAL
            fireRadial(enemy.x, enemy.y, bolts, QUEEN_SPIRAL_SPEED, elapsed * QUEEN_SPIRAL_SPIN, shotDamage(enemy))
        }
    }
}

/** Seconds between the Brood Queen's broods in [phase]. */
internal fun queenBroodSeconds(phase: Int): Float = when (phase) {
    0 -> 5f
    1 -> 4f
    else -> 3.5f
}

private fun MutableGameState.hatchBrood(queen: Enemy) {
    val hatched = spawnBossAdds(queen, EnemyType.SWARMLING, 1 + queen.charges, 1f, maxLive = QUEEN_BROOD_LIVE)
    val side = if (queen.id % 2 == 0) 1f else -1f
    for (index in enemies.size - hatched until enemies.size) {
        val child = enemies[index]
        val outX = child.x - queen.x
        val outY = child.y - queen.y
        val out = max(1e-3f, length(outX, outY))
        child.vx = -outY / out * QUEEN_BROOD_LAUNCH_SPEED * side
        child.vy = outX / out * QUEEN_BROOD_LAUNCH_SPEED * side
    }
}

/**
 * THRONE: hovers 360 u out behind its shield arcs (3 / 2 / 1, turning 0.6 / 0.8 / 1.0 rad/s); a hit through an arc
 * meets its guarded frame, one through a gap lands in full. Aimed fans every [THRONE_VOLLEY_SECONDS], with a radial
 * ring on every second one in the second phase; the final phase passes judgement every [THRONE_JUDGEMENT_SECONDS]:
 * the arcs stop for [THRONE_JUDGEMENT_TELEGRAPH_SECONDS], then four bolt trains run down the diagonals.
 */
internal fun MutableGameState.updateThrone(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    holdRange(enemy, dx, dy, distance, THRONE_HOLD, MINI_BOSS_RADIAL_LIMIT, THRONE_ORBIT_SPEED, THRONE_AGILITY, delta)
    if (enemy.mode != EnemyBehavior.TELEGRAPH) {
        enemy.facing += throneSpin(enemy.phase) * delta
        if (enemy.facing >= TAU) enemy.facing -= TAU
    }
    enemy.guarded = arcGuards(enemy, atan2(dy, dx))
    if (isShifting(enemy)) {
        enemy.mode = EnemyBehavior.IDLE
        enemy.actionTimer = when (enemy.phase) {
            0 -> THRONE_VOLLEY_SECONDS
            1 -> THRONE_VOLLEY_SECONDS * 2f
            else -> THRONE_JUDGEMENT_SECONDS
        }
        return
    }
    val step = cadence * bossAttackScale(enemy)
    when (enemy.phase) {
        0 -> {
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = THRONE_VOLLEY_SECONDS
                throneFan(enemy, dx, dy)
            }
        }
        1 -> {
            // A double cycle: a fan at its half, then a fan and the radial ring at its end.
            val before = enemy.actionTimer
            enemy.actionTimer -= step
            if (before > THRONE_VOLLEY_SECONDS && enemy.actionTimer <= THRONE_VOLLEY_SECONDS) throneFan(enemy, dx, dy)
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = THRONE_VOLLEY_SECONDS * 2f
                throneFan(enemy, dx, dy)
                if (canAttack(enemy)) fireRadial(enemy.x, enemy.y, THRONE_RING, THRONE_RING_SPEED, elapsed, shotDamage(enemy))
            }
        }
        else -> {
            if (enemy.mode == EnemyBehavior.TELEGRAPH && elapsed >= enemy.modeUntil) {
                if (canAttack(enemy)) passJudgement(enemy)
                enemy.mode = EnemyBehavior.IDLE
            }
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = THRONE_JUDGEMENT_SECONDS
                if (canAttack(enemy)) {
                    enemy.mode = EnemyBehavior.TELEGRAPH
                    enemy.modeUntil = elapsed + THRONE_JUDGEMENT_TELEGRAPH_SECONDS
                }
            }
        }
    }
}

/** Radians per second the Throne's arcs turn in [phase]. */
internal fun throneSpin(phase: Int): Float = when (phase) {
    0 -> 0.6f
    1 -> 0.8f
    else -> 1f
}

private fun MutableGameState.throneFan(enemy: Enemy, dx: Float, dy: Float) {
    if (canAttack(enemy)) fireSpread(enemy.x, enemy.y, atan2(dy, dx), THRONE_FAN, THRONE_FAN_SPACING, THRONE_FAN_SPEED, shotDamage(enemy))
}

/** Four bolt trains down the diagonals between the arcs' rest, each starting at the Throne's rim and running out. */
private fun MutableGameState.passJudgement(enemy: Enemy) {
    val lead = enemy.radius + 4f + THRONE_TRAIN_SPACING * (THRONE_TRAIN - 1)
    val damage = shotDamage(enemy)
    for (lane in 0 until THRONE_LANES) {
        val angle = enemy.facing + PI.toFloat() * 0.25f + lane * PI.toFloat() * 0.5f
        fireBoltTrain(
            enemy.x, enemy.y, angle, lead, THRONE_TRAIN, THRONE_TRAIN_SPACING, THRONE_TRAIN_SPEED, THRONE_TRAIN_RADIUS,
            THRONE_TRAIN_LIFE, damage,
        )
    }
}

/**
 * PYRE_HERALD: the Herald with embers. It holds 300 u out, fires a spiral of `6 + 2·phase` bolts every
 * [PYRE_VOLLEY_SECONDS] and drops an ember on its trail every [PYRE_EMBER_SECONDS]; its impact-only shield rises for
 * 3 s every 12 s (9 s in the final phase) and calls in two Imps. Its contact hurts at any Core speed.
 */
internal fun MutableGameState.updatePyreHerald(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    holdRange(enemy, dx, dy, distance, PYRE_HOLD, PYRE_RADIAL_LIMIT, PYRE_ORBIT_SPEED, PYRE_AGILITY, delta)
    if (isShifting(enemy)) {
        enemy.actionTimer = PYRE_VOLLEY_SECONDS
        enemy.modeUntil = elapsed + bossClockSeconds(enemy, PYRE_EMBER_SECONDS)
    } else {
        enemy.actionTimer -= cadence * bossAttackScale(enemy)
        if (enemy.actionTimer <= 0f) {
            enemy.actionTimer = PYRE_VOLLEY_SECONDS
            if (canAttack(enemy)) {
                fireRadial(enemy.x, enemy.y, 6 + 2 * enemy.phase, PYRE_SHOT_SPEED, elapsed * PYRE_VOLLEY_SPIN, shotDamage(enemy),
                    life = PYRE_SHOT_LIFE)
            }
        }
        if (elapsed >= enemy.modeUntil) {
            enemy.modeUntil = elapsed + bossClockSeconds(enemy, PYRE_EMBER_SECONDS)
            if (canAttack(enemy)) queueEmber(enemy.x, enemy.y, PYRE_EMBER_DELAY)
        }
    }
    // The Herald's shield cycle; each shield also calls in Imps.
    if (elapsed >= enemy.abilityAt && enemy.shieldUntil <= elapsed) {
        enemy.shieldUntil = elapsed + HERALD_SHIELD_SECONDS
        enemy.abilityAt = elapsed + if (enemy.phase >= 2) PYRE_FINAL_SHIELD_INTERVAL_SECONDS else HERALD_SHIELD_INTERVAL_SECONDS
        spawnBossAdds(enemy, EnemyType.IMP, PYRE_IMPS, 1f, maxLive = PYRE_IMPS_LIVE)
        shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * 2.6f, 3)
    }
}

/** Holds [range] from the Core (radial speed `(d − range) × 0.4` within ±[limit]) and circles at [orbitSpeed]. */
internal fun MutableGameState.holdRange(
    enemy: Enemy,
    dx: Float,
    dy: Float,
    distance: Float,
    range: Float,
    limit: Float,
    orbitSpeed: Float,
    agility: Float,
    delta: Float,
) {
    val tangent = if (enemy.id % 2 == 0) 1f else -1f
    val radial = clamp((distance - range) * MINI_BOSS_RADIAL_GAIN, -limit, limit)
    steerEnemy(
        enemy,
        dx / distance * radial - dy / distance * orbitSpeed * tangent,
        dy / distance * radial + dx / distance * orbitSpeed * tangent,
        agility,
        delta,
    )
}

/** Run seconds of a boss attack on its own clock: [seconds] at the run's hostile attack rate, faster while enraged. */
internal fun MutableGameState.bossClockSeconds(enemy: Enemy, seconds: Float): Float =
    seconds / (hostileAttackRate() * bossAttackScale(enemy))
