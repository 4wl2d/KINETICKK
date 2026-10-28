// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The secret bosses of 0.6.0: the Cartographer (a far sigil summons it) and the Antecedent (the Archive's objective).
 * Each is gated by its content encounter spec (thirds or quarters, a 1.5 % hit cap, a long shift at each gate), never
 * ends the run, arrives at most once per run and pays the secret-boss reward when it falls. While one shifts it attacks
 * nothing: each update holds its attack clocks. Attack cadence runs faster once its encounter clock enrages it. Nothing
 * here draws from a run stream: the Cartographer's open-space jump turns by a stateless run hash.
 */

/** A Cartographer gate drops this many repairs; a secret boss's fall drops [SECRET_BOSS_DEFEAT_REPAIRS]. */
internal const val CARTO_SHIFT_REPAIRS: Int = 3
internal const val SECRET_BOSS_DEFEAT_REPAIRS: Int = 4
/** Seconds the defeat and enrage notices stay up. */
internal const val SECRET_BOSS_DEFEAT_NOTICE_SECONDS: Float = 3f
internal const val SECRET_BOSS_ENRAGE_NOTICE_SECONDS: Float = 2.5f

internal const val CARTO_ORBIT_RADIUS: Float = 420f
internal const val CARTO_ORBIT_RATE: Float = 0.18f
private const val CARTO_ORBIT_GAIN: Float = 0.6f
private const val CARTO_AGILITY: Float = 1.2f
/** Between jumps of its final phase it holds its rim, its drift damped at this rate. */
private const val CARTO_HOLD_DAMPING: Float = 4f
/** Seconds between Surveys (first phase), Cartography turns (second) and jumps (final). */
internal const val CARTO_SURVEY_SECONDS: Float = 3f
internal const val CARTO_CARTOGRAPHY_SECONDS: Float = 4f
internal const val CARTO_JUMP_SECONDS: Float = 3.5f
/** The lanes are marked this long (facing locked) before their bolt trains run. */
internal const val CARTO_LANE_TELEGRAPH_SECONDS: Float = 1f
/** Three parallel lanes, this far apart across the facing, each a train of bolts. */
internal const val CARTO_LANE_OFFSET: Float = 60f
internal const val CARTO_LANE_TRAIN: Int = 3
internal const val CARTO_LANE_SPACING: Float = 40f
internal const val CARTO_LANE_SPEED: Float = 460f
internal const val CARTO_LANE_RADIUS: Float = 7f
internal const val CARTO_LANE_LIFE: Float = 3.2f
/** A line of at most this many mines, this far ahead of the Core across its heading, this far apart. */
internal const val CARTO_MINE_LINE: Int = 6
internal const val CARTO_MINE_AHEAD: Float = 380f
internal const val CARTO_MINE_SPACING: Float = 70f
/** Below this Core speed the mine line reads its heading from the Cartographer to the Core. */
internal const val CARTO_HEADING_MIN_SPEED: Float = 20f
/** Out of phase this long (intangible), then it reappears at a rock's far rim. */
internal const val CARTO_PHASE_SECONDS: Float = 0.6f
/** It jumps to the rock nearest the Core within this reach but farther than [CARTO_JUMP_NEAREST] from it. */
internal const val CARTO_JUMP_REACH: Float = 900f
internal const val CARTO_JUMP_NEAREST: Float = 220f
/** Gap kept between the rock's surface and its own body on the far rim. */
internal const val CARTO_RIM_GAP: Float = 10f
/** With no rock in reach it reappears this far from the Core at a hashed angle. */
internal const val CARTO_FALLBACK_REACH: Float = 420f
internal const val CARTO_AIM_SECONDS: Float = 0.25f
internal const val CARTO_RING: Int = 14
internal const val CARTO_RING_SPEED: Float = 135f
internal const val CARTO_FAN: Int = 5
internal const val CARTO_FAN_SPEED: Float = 250f
internal const val CARTO_FAN_SPACING: Float = 0.18f
/** Salt of the Cartographer's open-space jump hash (`runHash`); registered in the lane notes. */
internal const val CARTO_SALT: Long = 0x0CA2_70E5_EED0_0021L
private const val CARTO_HASH_TICKS: Float = 120f
private const val CARTO_NUDGE_ATTEMPTS: Int = 4
private const val CARTO_NUDGE_DISTANCE: Float = 50f

private const val ANTECEDENT_HOLD: Float = 360f
private const val ANTECEDENT_ORBIT_SPEED: Float = 40f
private const val ANTECEDENT_AGILITY: Float = 1f
/** The final Zenith phase raises two of the Throne's shield arcs, turning at this rate. */
internal const val ANTECEDENT_ARCS: Int = 2
internal const val ANTECEDENT_ARC_SPIN: Float = 0.9f
/** First phase: an impact-only shield for [ANTECEDENT_SHIELD_SECONDS] every [ANTECEDENT_SHIELD_INTERVAL_SECONDS]. */
internal const val ANTECEDENT_SHIELD_SECONDS: Float = 2.5f
internal const val ANTECEDENT_SHIELD_INTERVAL_SECONDS: Float = 8f
/** Zenith phases sing the Choir's pulse (its telegraph, speed, spin, shove and reach) with this many bolts. */
internal const val ANTECEDENT_PULSE_SECONDS: Float = 4f
internal const val ANTECEDENT_PULSE: Int = 12
/** Nadir phases: the Pyre Herald's spiral (speed, spin, life) every [ANTECEDENT_SPIRAL_SECONDS]. */
internal const val ANTECEDENT_SPIRAL_SECONDS: Float = 1f
internal const val ANTECEDENT_SPIRAL: Int = 10
internal const val ANTECEDENT_FINAL_SPIRAL: Int = 14
internal const val ANTECEDENT_EMBER_SECONDS: Float = 0.5f
internal const val ANTECEDENT_FAN_SECONDS: Float = 1.3f
internal const val ANTECEDENT_FAN: Int = 5
internal const val ANTECEDENT_FAN_SPEED: Float = 250f
internal const val ANTECEDENT_FAN_SPACING: Float = 0.18f
/** The final phase rings itself with embers every [ANTECEDENT_EMBER_RING_SECONDS]. */
internal const val ANTECEDENT_EMBER_RING_SECONDS: Float = 6f
internal const val ANTECEDENT_RING_EMBERS: Int = 8
internal const val ANTECEDENT_EMBER_RING_RADIUS: Float = 170f
/** Adds at its phase starts, and the most of each alive at once. */
internal const val ANTECEDENT_IMPS: Int = 2
internal const val ANTECEDENT_IMPS_LIVE: Int = 4
internal const val ANTECEDENT_HOUNDS: Int = 3

/**
 * A secret boss's phase start, run once per step outside the damage pipeline: phase 0 on its first step, each later
 * one at a gate. A Cartographer gate rings a shockwave and drops three repairs; the Antecedent turns its aspect
 * against the build, raises or drops its arcs and calls the adds of its new phase.
 */
internal fun MutableGameState.startSecretBossPhase(enemy: Enemy) {
    when (enemy.type) {
        EnemyType.CARTOGRAPHER -> {
            if (enemy.phase > 0) {
                shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * MINI_BOSS_SHIFT_RING, MINI_BOSS_MARK_COLOR)
                for (index in 0 until CARTO_SHIFT_REPAIRS) {
                    val side = if (index % 2 == 0) 1f else -1f
                    addPickup(Pickup(PickupType.REPAIR, enemy.x + side * 22f, enemy.y - 10f * index))
                }
            }
            // Cartography starts with its lanes, then alternates with mine lines.
            if (enemy.phase == 1) enemy.charges = 0
        }
        EnemyType.ANTECEDENT -> {
            // Its first and third gates harden it against the dominant element, the second against the secondary.
            assignAspect(enemy, adaptiveArchitectAspect(if (enemy.phase == 3) 1 else enemy.phase), immune = false)
            enemy.charges = if (enemy.phase == 2) ANTECEDENT_ARCS else 0
            enemy.mode = EnemyBehavior.IDLE
            resetAntecedentClocks(enemy)
            when (enemy.phase) {
                0 -> {
                    spawnBossAdds(enemy, EnemyType.SERAPH, 1, 1f, maxLive = 1)
                    spawnBossAdds(enemy, EnemyType.CHOIR, 1, 1f, maxLive = 1)
                }
                1 -> spawnBossAdds(enemy, EnemyType.IMP, ANTECEDENT_IMPS, 1f, maxLive = ANTECEDENT_IMPS_LIVE)
                3 -> spawnBossAdds(enemy, EnemyType.HOUND, ANTECEDENT_HOUNDS, 1f, maxLive = ANTECEDENT_HOUNDS)
                else -> Unit
            }
        }
        else -> Unit
    }
}

/**
 * Announces a tagged secret boss's enrage once, on the step its encounter clock crosses the spec's enrage time
 * (`elapsed − start ≥ T` and `elapsed − step − start < T`). Runs once per step outside every enemy loop, so a Frozen
 * boss still announces it.
 */
internal fun MutableGameState.announceSecretBossEnrage(enemy: Enemy) {
    val notice = when (enemy.type) {
        EnemyType.CARTOGRAPHER -> EnemyNotices.CARTOGRAPHER_ENRAGES
        EnemyType.ANTECEDENT -> EnemyNotices.ANTECEDENT_ENRAGES
        else -> return
    }
    val enrageAfter = encounterSpec(enemy)?.enrageAfterSeconds ?: return
    val start = encounterStartedAt(enemy.ownerTag)
    if (enrageAfter <= 0f || start.isNaN()) return
    if (elapsed - start >= enrageAfter && elapsed - MutableGameState.FIXED_STEP - start < enrageAfter) {
        message = notice
        messageTime = SECRET_BOSS_ENRAGE_NOTICE_SECONDS
    }
}

/**
 * The secret-boss reward on top of the Elite reward every boss rank pays: a second Key, four repairs (the Architect's
 * shift pattern), the discovery that reveals it in the Codex and the defeat notice. Draws nothing.
 */
internal fun MutableGameState.paySecretBossDefeat(enemy: Enemy) {
    val definition = content.enemy(enemy.type)
    addPickup(Pickup(PickupType.KEY, enemy.x - 15f, enemy.y + 8f))
    for (index in 0 until SECRET_BOSS_DEFEAT_REPAIRS) {
        val side = if (index % 2 == 0) 1f else -1f
        addPickup(Pickup(PickupType.REPAIR, enemy.x + side * 22f, enemy.y - 10f * index))
    }
    discoverWorld(checkNotNull(definition.revealedBy) { "Secret bosses name the discovery that reveals them" })
    message = EnemyNotices.SECRET_BOSS_DEFEATED + " // " + definition.name.uppercase()
    messageTime = SECRET_BOSS_DEFEAT_NOTICE_SECONDS
}

/**
 * CARTOGRAPHER: orbits 420 u out (the Architect's orbit) in its first two phases. Survey: every [CARTO_SURVEY_SECONDS]
 * it marks three parallel lanes along a locked aim for [CARTO_LANE_TELEGRAPH_SECONDS], then bolt trains run down them.
 * Cartography: every [CARTO_CARTOGRAPHY_SECONDS] the lanes or a line of Minelayer mines ahead of the Core, in turn.
 * Triangulation: every [CARTO_JUMP_SECONDS] it phases out (intangible) for [CARTO_PHASE_SECONDS], reappears at the far
 * rim of the rock nearest the Core and fires a ring and an aimed fan.
 */
internal fun MutableGameState.updateCartographer(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    if (enemy.mode == EnemyBehavior.PHASED) {
        enemy.vx = 0f
        enemy.vy = 0f
        if (elapsed >= enemy.modeUntil) rematerializeCartographer(enemy)
        return
    }
    if (enemy.phase < 2) {
        val orbit = elapsed * CARTO_ORBIT_RATE
        val targetX = coreX + cos(orbit) * CARTO_ORBIT_RADIUS
        val targetY = coreY + sin(orbit) * CARTO_ORBIT_RADIUS
        steerEnemy(enemy, (targetX - enemy.x) * CARTO_ORBIT_GAIN, (targetY - enemy.y) * CARTO_ORBIT_GAIN, CARTO_AGILITY, delta)
    } else {
        enemy.damp(CARTO_HOLD_DAMPING, delta)
    }
    if (isShifting(enemy)) {
        enemy.mode = EnemyBehavior.IDLE
        enemy.actionTimer = cartographerCycleSeconds(enemy.phase)
        return
    }
    if (enemy.mode == EnemyBehavior.TELEGRAPH) {
        // The lanes keep the aim they were marked with; a jump's volley tracks the Core.
        if (enemy.phase >= 2) enemy.facing = atan2(dy, dx)
        if (elapsed >= enemy.modeUntil) {
            if (canVolley(enemy)) {
                if (enemy.phase >= 2) triangulationVolley(enemy) else surveyLanes(enemy)
            }
            enemy.mode = EnemyBehavior.IDLE
        }
    }
    enemy.actionTimer -= cadence * bossAttackScale(enemy)
    if (enemy.actionTimer > 0f) return
    enemy.actionTimer = cartographerCycleSeconds(enemy.phase)
    if (!canAttack(enemy)) return
    if (enemy.phase >= 2) {
        enemy.mode = EnemyBehavior.PHASED
        enemy.modeUntil = elapsed + CARTO_PHASE_SECONDS
        // Out of phase it is untargetable everywhere a shifting enemy is.
        enemy.shiftUntil = max(enemy.shiftUntil, enemy.modeUntil)
        enemy.vx = 0f
        enemy.vy = 0f
        return
    }
    val mines = enemy.phase == 1 && enemy.charges % 2 == 1
    if (enemy.phase == 1) enemy.charges++
    if (mines) {
        layMineLine(enemy, dx, dy, distance)
    } else {
        enemy.mode = EnemyBehavior.TELEGRAPH
        enemy.modeUntil = elapsed + CARTO_LANE_TELEGRAPH_SECONDS
        enemy.facing = atan2(dy, dx)
    }
}

/** Seconds of the Cartographer's attack cycle in [phase]. */
internal fun cartographerCycleSeconds(phase: Int): Float = when (phase) {
    0 -> CARTO_SURVEY_SECONDS
    1 -> CARTO_CARTOGRAPHY_SECONDS
    else -> CARTO_JUMP_SECONDS
}

/** Three parallel bolt trains along the facing, [CARTO_LANE_OFFSET] apart, each starting at its rim and running out. */
private fun MutableGameState.surveyLanes(enemy: Enemy) {
    val damage = shotDamage(enemy)
    val lead = enemy.radius + 4f + CARTO_LANE_SPACING * (CARTO_LANE_TRAIN - 1)
    val sideX = -sin(enemy.facing) * CARTO_LANE_OFFSET
    val sideY = cos(enemy.facing) * CARTO_LANE_OFFSET
    for (lane in -1..1) {
        fireBoltTrain(
            enemy.x + sideX * lane, enemy.y + sideY * lane, enemy.facing, lead, CARTO_LANE_TRAIN, CARTO_LANE_SPACING,
            CARTO_LANE_SPEED, CARTO_LANE_RADIUS, CARTO_LANE_LIFE, damage,
        )
    }
}

/**
 * A line of Minelayer mines [CARTO_MINE_AHEAD] ahead of the Core across its heading, [CARTO_MINE_SPACING] apart: at
 * most [CARTO_MINE_LINE], and never past the live-mine bound.
 */
private fun MutableGameState.layMineLine(enemy: Enemy, dx: Float, dy: Float, distance: Float) {
    val count = min(CARTO_MINE_LINE, MAX_LIVE_MINES - liveMines())
    if (count <= 0) return
    val coreSpeed = length(velocityX, velocityY)
    val headingX = if (coreSpeed > CARTO_HEADING_MIN_SPEED) velocityX / coreSpeed else dx / distance
    val headingY = if (coreSpeed > CARTO_HEADING_MIN_SPEED) velocityY / coreSpeed else dy / distance
    val centerX = coreX + headingX * CARTO_MINE_AHEAD
    val centerY = coreY + headingY * CARTO_MINE_AHEAD
    val damage = shotDamage(enemy)
    for (index in 0 until count) {
        val across = (index - (count - 1) * 0.5f) * CARTO_MINE_SPACING
        addProjectile(Projectile(
            x = centerX - headingY * across,
            y = centerY + headingX * across,
            vx = 0f,
            vy = 0f,
            radius = MINE_RADIUS,
            life = MINE_LIFE_SECONDS,
            damage = damage,
            kind = ProjectileKind.MINE,
        ))
    }
}

/**
 * Puts the Cartographer on the far rim of the steady rock nearest the Core within [CARTO_JUMP_REACH] (and farther than
 * [CARTO_JUMP_NEAREST] from it), or [CARTO_FALLBACK_REACH] from the Core at a hashed angle when none is (nudged outward
 * clear of obstacles and walls), with no interpolation from where it was. Then it aims.
 */
private fun MutableGameState.rematerializeCartographer(enemy: Enemy) {
    var bestDistance = Float.MAX_VALUE
    var rockX = 0f
    var rockY = 0f
    var rockRadius = 0f
    forEachObstacleNear(coreX, coreY, CARTO_JUMP_REACH) { _, obstacle ->
        if (obstacle.isSteadySurface()) {
            val apart = distanceSquared(obstacle.x, obstacle.y, coreX, coreY)
            if (apart > CARTO_JUMP_NEAREST * CARTO_JUMP_NEAREST && apart <= CARTO_JUMP_REACH * CARTO_JUMP_REACH &&
                apart < bestDistance
            ) {
                bestDistance = apart
                rockX = obstacle.x
                rockY = obstacle.y
                rockRadius = obstacle.radius
            }
        }
    }
    var x: Float
    var y: Float
    if (bestDistance < Float.MAX_VALUE) {
        // The rim keeps its gap from the rock by construction; the push-out settles any other overlap.
        val apart = sqrt(bestDistance)
        val rim = rockRadius + enemy.radius + CARTO_RIM_GAP
        x = rockX + (rockX - coreX) / apart * rim
        y = rockY + (rockY - coreY) / apart * rim
    } else {
        val hash = runHash(runSeed, CARTO_SALT, (enemy.id.toLong() shl 20) xor (elapsed * CARTO_HASH_TICKS).toLong())
        val angle = hashUnit(hash) * TAU
        val directionX = cos(angle)
        val directionY = sin(angle)
        x = coreX + directionX * CARTO_FALLBACK_REACH
        y = coreY + directionY * CARTO_FALLBACK_REACH
        var attempts = 0
        while (attempts < CARTO_NUDGE_ATTEMPTS && isSpawnPointBlocked(x, y, enemy.radius)) {
            x += directionX * CARTO_NUDGE_DISTANCE
            y += directionY * CARTO_NUDGE_DISTANCE
            attempts++
        }
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
    enemy.vx = 0f
    enemy.vy = 0f
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + CARTO_AIM_SECONDS
    enemy.facing = atan2(coreY - y, coreX - x)
}

private fun MutableGameState.triangulationVolley(enemy: Enemy) {
    val damage = shotDamage(enemy)
    fireRadial(enemy.x, enemy.y, CARTO_RING, CARTO_RING_SPEED, elapsed, damage)
    fireSpread(enemy.x, enemy.y, enemy.facing, CARTO_FAN, CARTO_FAN_SPACING, CARTO_FAN_SPEED, damage)
}

/**
 * ANTECEDENT: hovers 360 u out and alternates Zenith and Nadir phases. Zenith (first): an impact-only shield and the
 * Choir's pulse, with a Seraph and a Choir; Nadir (second): an ember trail and a spiral, with Imps, and its contact
 * hurts at any speed; Zenith (third): two of the Throne's shield arcs, aimed fans and the pulse; Nadir (final): rings
 * of embers and a denser spiral, with Hounds.
 */
internal fun MutableGameState.updateAntecedent(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    holdRange(enemy, dx, dy, distance, ANTECEDENT_HOLD, MINI_BOSS_RADIAL_LIMIT, ANTECEDENT_ORBIT_SPEED, ANTECEDENT_AGILITY, delta)
    if (enemy.charges > 0) {
        enemy.facing += ANTECEDENT_ARC_SPIN * delta
        if (enemy.facing >= TAU) enemy.facing -= TAU
    }
    // Impacts read the guard: only its third phase raises arcs, so it is unguarded in every other one.
    enemy.guarded = arcGuards(enemy, atan2(dy, dx))
    // A shift, or a phase start still pending (its first step), holds the attacks with the new phase's clocks.
    if (isShifting(enemy) || enemy.phaseStartPending) {
        enemy.mode = EnemyBehavior.IDLE
        resetAntecedentClocks(enemy)
        return
    }
    val step = cadence * bossAttackScale(enemy)
    when (enemy.phase) {
        0 -> {
            antecedentPulse(enemy, dx, dy, distance)
            if (elapsed >= enemy.abilityAt && enemy.shieldUntil <= elapsed) {
                enemy.shieldUntil = elapsed + ANTECEDENT_SHIELD_SECONDS
                enemy.abilityAt = elapsed + ANTECEDENT_SHIELD_INTERVAL_SECONDS
                shockwave(enemy.x, enemy.y, 0.45f, enemy.radius * 2.6f, 3)
            }
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = ANTECEDENT_PULSE_SECONDS
                singPulse(enemy)
            }
        }
        1 -> {
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = ANTECEDENT_SPIRAL_SECONDS
                antecedentSpiral(enemy, ANTECEDENT_SPIRAL)
            }
            if (elapsed >= enemy.modeUntil) {
                enemy.modeUntil = elapsed + bossClockSeconds(enemy, ANTECEDENT_EMBER_SECONDS)
                if (canAttack(enemy)) queueEmber(enemy.x, enemy.y, PYRE_EMBER_DELAY)
            }
        }
        2 -> {
            antecedentPulse(enemy, dx, dy, distance)
            if (elapsed >= enemy.abilityAt) {
                enemy.abilityAt = elapsed + bossClockSeconds(enemy, ANTECEDENT_PULSE_SECONDS)
                singPulse(enemy)
            }
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = ANTECEDENT_FAN_SECONDS
                if (canVolley(enemy)) {
                    fireSpread(enemy.x, enemy.y, atan2(dy, dx), ANTECEDENT_FAN, ANTECEDENT_FAN_SPACING, ANTECEDENT_FAN_SPEED, shotDamage(enemy))
                }
            }
        }
        else -> {
            enemy.actionTimer -= step
            if (enemy.actionTimer <= 0f) {
                enemy.actionTimer = ANTECEDENT_SPIRAL_SECONDS
                antecedentSpiral(enemy, ANTECEDENT_FINAL_SPIRAL)
            }
            if (elapsed >= enemy.abilityAt) {
                enemy.abilityAt = elapsed + bossClockSeconds(enemy, ANTECEDENT_EMBER_RING_SECONDS)
                if (canAttack(enemy)) {
                    for (index in 0 until ANTECEDENT_RING_EMBERS) {
                        val angle = index * TAU / ANTECEDENT_RING_EMBERS + elapsed
                        queueEmber(
                            enemy.x + cos(angle) * ANTECEDENT_EMBER_RING_RADIUS,
                            enemy.y + sin(angle) * ANTECEDENT_EMBER_RING_RADIUS,
                            PYRE_RING_DELAY,
                        )
                    }
                }
            }
        }
    }
}

/** The Antecedent's attack clocks for its current phase, restarted at each phase start and held through a shift. */
private fun MutableGameState.resetAntecedentClocks(enemy: Enemy) {
    when (enemy.phase) {
        0 -> {
            enemy.actionTimer = ANTECEDENT_PULSE_SECONDS
            enemy.abilityAt = elapsed + ANTECEDENT_SHIELD_INTERVAL_SECONDS
        }
        1 -> {
            enemy.actionTimer = ANTECEDENT_SPIRAL_SECONDS
            enemy.modeUntil = elapsed + bossClockSeconds(enemy, ANTECEDENT_EMBER_SECONDS)
        }
        2 -> {
            enemy.actionTimer = ANTECEDENT_FAN_SECONDS
            enemy.abilityAt = elapsed + bossClockSeconds(enemy, ANTECEDENT_PULSE_SECONDS)
        }
        else -> {
            enemy.actionTimer = ANTECEDENT_SPIRAL_SECONDS
            enemy.abilityAt = elapsed + bossClockSeconds(enemy, ANTECEDENT_EMBER_RING_SECONDS)
        }
    }
}

/** Starts the Choir's pulse telegraph. */
private fun MutableGameState.singPulse(enemy: Enemy) {
    enemy.mode = EnemyBehavior.TELEGRAPH
    enemy.modeUntil = elapsed + CHOIR_TELEGRAPH_SECONDS
}

/** Ends a sung pulse: a ring of bolts, and a shove on a Core inside the Choir's wave (a dash slips through). */
private fun MutableGameState.antecedentPulse(enemy: Enemy, dx: Float, dy: Float, distance: Float) {
    if (enemy.mode != EnemyBehavior.TELEGRAPH || elapsed < enemy.modeUntil) return
    enemy.mode = EnemyBehavior.IDLE
    if (!canAttack(enemy)) return
    if (projectiles.size < ARCHITECT_VOLLEY_PROJECTILE_BUDGET) {
        fireRadial(enemy.x, enemy.y, ANTECEDENT_PULSE, CHOIR_SHOT_SPEED, elapsed * CHOIR_VOLLEY_SPIN, shotDamage(enemy))
    }
    if (distance <= CHOIR_WAVE_RADIUS + coreRadius && dashPhaseTime <= 0f) {
        velocityX += dx / distance * CHOIR_WAVE_PUSH
        velocityY += dy / distance * CHOIR_WAVE_PUSH
    }
}

private fun MutableGameState.antecedentSpiral(enemy: Enemy, bolts: Int) {
    if (canVolley(enemy)) {
        fireRadial(enemy.x, enemy.y, bolts, PYRE_SHOT_SPEED, elapsed * PYRE_VOLLEY_SPIN, shotDamage(enemy), life = PYRE_SHOT_LIFE)
    }
}

/** A secret boss fires only while it can attack and the screen holds fewer than the Architect's volley budget of shots. */
private fun MutableGameState.canVolley(enemy: Enemy): Boolean =
    projectiles.size < ARCHITECT_VOLLEY_PROJECTILE_BUDGET && canAttack(enemy)
