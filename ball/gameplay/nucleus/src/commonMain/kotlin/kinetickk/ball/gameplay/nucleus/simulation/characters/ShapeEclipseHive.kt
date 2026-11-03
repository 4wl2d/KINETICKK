// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_DART
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_LIFE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_READY_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_AGE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_DETONATE_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X1
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y1
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Advances the Crescent's Blink recharge by [seconds] (a charge completes as in the step). */
internal fun MutableGameState.advanceBlinkRecharge(seconds: Float) {
    val ability = characterRuntime
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) return
    ability.blinkRecharge += seconds
    val period = blinkRechargeSeconds()
    if (ability.blinkRecharge >= period) {
        ability.blinkRecharge -= period
        ability.blinkCharges++
    }
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) ability.blinkRecharge = 0f
}

/** Every idle Hive drone darts at [target] now (each dart as in the Hive's own cadence). */
internal fun MutableGameState.dronesDartAt(target: Enemy) {
    val ability = characterRuntime
    val drones = ability.drones
    for (drone in 0 until ability.droneCount) {
        if (!target.alive) return
        val base = drone * DRONE_STRIDE
        if (drones[base + DRONE_DART] > 0f) continue
        drones[base + DRONE_READY_AT] = elapsed + DRONE_INTERVAL
        drones[base + DRONE_TARGET_X] = target.x
        drones[base + DRONE_TARGET_Y] = target.y
        drones[base + DRONE_DART] = DRONE_DART_SECONDS
        val integrity = target.hp
        recordAbilityDamage(integrity, dealProcDamage(target, DRONE_DAMAGE * abilityPower()))
    }
}

/** Seconds per Blink charge: Total Eclipse recharges almost at once; otherwise the Refit-adjusted base. */
internal fun MutableGameState.blinkRechargeSeconds(): Float =
    if (apexActive()) apexValue(ECLIPSE_BLINK_RECHARGE) else baseBlinkRechargeSeconds()

internal fun MutableGameState.updateEclipse(ability: CharacterRuntime, delta: Float) {
    val period = blinkRechargeSeconds()
    if (ability.blinkCharges < CharacterRuntime.MAX_BLINK_CHARGES) {
        ability.blinkRecharge += delta
        if (ability.blinkRecharge >= period) {
            ability.blinkRecharge -= period
            ability.blinkCharges++
        }
    }
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) ability.blinkRecharge = 0f
    ability.charge = (ability.blinkCharges + (ability.blinkRecharge / period).coerceAtMost(1f)) / CharacterRuntime.MAX_BLINK_CHARGES
    val eclipse = apexActive()
    var index = 0
    while (index < ability.scarCount) {
        val age = ability.scarValue(index, SCAR_AGE)
        if (age < 0f) {
            if (elapsed >= ability.scarValue(index, SCAR_DETONATE_AT)) detonateScar(index)
            else if (eclipse) pullTowardScar(index, delta)
            index++
        } else if (age + delta >= SCAR_FADE_SECONDS) {
            removeScar(index)
        } else {
            ability.scars[index * SCAR_STRIDE + SCAR_AGE] = age + delta
            index++
        }
    }
}

/**
 * Crescent's Dash: a Blink along the aim that stops short of the singularity and of obstacles, keeps
 * most of the speed, costs a charge instead of heat, counts enemies it crosses as Dash hits and leaves
 * a void scar. Blink Drive extends it and adds its strike and pairings.
 */
internal fun MutableGameState.crescentBlink(
    directionX: Float,
    directionY: Float,
    /** A reach that replaces the Blink's own (a Void Anchor's); NaN keeps it. */
    rangeOverride: Float = Float.NaN,
    /** Scales the Blink's reach (a Blast Jump). */
    rangeScale: Float = 1f,
) {
    val ability = characterRuntime
    ability.blinkCharges = max(0, ability.blinkCharges - 1)
    dashPhaseTime = DASH_PHASE_SECONDS
    val drive = build.upside(RelicId.BLINK_DRIVE)
    var range = BLINK_RANGE
    var strikeMass = 1f
    if (drive) {
        range *= BLINK_DRIVE_RANGE_MULTIPLIER
        if (build.pairing(RelicPairingId.HEAVY_BLINK)) {
            range *= 0.65f
            strikeMass = 2f
        }
    }
    if (!rangeOverride.isNaN()) range = rangeOverride
    range *= rangeScale
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val ahead = (targetX - coreX) * directionX + (targetY - coreY) * directionY
    if (ahead > 0f) range = min(range, max(0f, length(targetX - coreX, targetY - coreY) - BLINK_SINGULARITY_GAP))
    range = blinkRangeClearOfObstacles(directionX, directionY, range)
    val startX = coreX
    val startY = coreY
    val endX = coreX + directionX * range
    val endY = coreY + directionY * range
    val impact = impactPower(max(speed, 600f))
    val strike = if (drive) impact * strikeMass * relicValue(RelicId.BLINK_DRIVE, 0) / 100f else 0f
    val assassin = drive && build.pairing(RelicPairingId.ASSASSINS_LINE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (!segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) continue
        val newContact = enemy.lastCharacterDash != ability.dashSequence
        onCharacterDashContact(enemy)
        if (newContact) onDashHit(enemy, impact)
        if (drive && enemy.alive) {
            if (assassin) enemy.status[StatusCell.MARKED_UNTIL] = elapsed + 1f
            dealHit(enemy, strike, HitSource.DASH, canCrit = true)
            if (assassin) {
                enemy.status[StatusCell.MARKED_UNTIL] = 0f
                if (enemy.alive) enemy.status[StatusCell.BRITTLE] = 1f
            }
        }
    }
    if (drive) addRelicArc(startX, startY, endX, endY)
    if (apexActive()) cutShotsAlong(startX, startY, endX, endY)
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = targetX
    previousSingularityY = targetY
    velocityX *= BLINK_SPEED_RETENTION
    velocityY *= BLINK_SPEED_RETENTION
    markBlink(startX, startY, endX, endY)
    if (ability.scarCount >= CharacterRuntime.MAX_SCARS) {
        // The oldest scar detonates early rather than being dropped.
        if (ability.scarValue(0, SCAR_AGE) < 0f) detonateScar(0)
        removeScar(0)
    }
    val base = ability.scarCount * SCAR_STRIDE
    ability.scars[base + SCAR_X0] = startX
    ability.scars[base + SCAR_Y0] = startY
    ability.scars[base + SCAR_X1] = endX
    ability.scars[base + SCAR_Y1] = endY
    ability.scars[base + SCAR_DETONATE_AT] = elapsed + SCAR_FUSE_SECONDS
    ability.scars[base + SCAR_AGE] = -1f
    ability.scarCount++
    shockwave(endX, endY, 0.25f, 110f, 0)
}

/**
 * [range] of a Blink from the Core along (directionX, directionY), shortened in [BLINK_OBSTACLE_STEP] steps
 * until the path clears every obstacle: a Blink stops short of a rock, never lands in or past it.
 */
internal fun MutableGameState.blinkRangeClearOfObstacles(directionX: Float, directionY: Float, range: Float): Float {
    var clear = range
    while (clear > 0f && obstacleBlocksSegment(coreX, coreY, coreX + directionX * clear, coreY + directionY * clear, coreRadius)) {
        clear = max(0f, clear - BLINK_OBSTACLE_STEP)
    }
    return clear
}

/** Records a Blink from (startX, startY) to (endX, endY) for the rest of the step (see [blinkThisStep]). */
internal fun MutableGameState.markBlink(startX: Float, startY: Float, endX: Float, endY: Float) {
    val ability = characterRuntime
    ability.blinkFlagSteps = 1
    ability.blinkFromX = startX
    ability.blinkFromY = startY
    ability.blinkToX = endX
    ability.blinkToY = endY
}

/** Total Eclipse: a Blink from (x0, y0) to (x1, y1) erases the hostile bolts along its path (bolt budget). */
private fun MutableGameState.cutShotsAlong(x0: Float, y0: Float, x1: Float, y1: Float) {
    val reach = apexValue(ECLIPSE_CUT_RADIUS)
    for (index in projectiles.indices) {
        if (skillRuntime.apex(ApexCell.BOLTS_LEFT) < 1f) return
        val shot = projectiles[index]
        if (shot.hostile && segmentCircleIntersects(x0, y0, x1, y1, shot.x, shot.y, reach + shot.radius)) eraseApexShot(shot, shot.x, shot.y)
    }
    addWeaponArc(x0, y0, x1, y1, 0.3f, LinkStyleCode.apex(CoreShape.CRESCENT))
}

/** Total Eclipse: an armed scar draws the ordinary enemies near it toward its line before it detonates. */
private fun MutableGameState.pullTowardScar(index: Int, delta: Float) {
    val ability = characterRuntime
    val x0 = ability.scarValue(index, SCAR_X0)
    val y0 = ability.scarValue(index, SCAR_Y0)
    val x1 = ability.scarValue(index, SCAR_X1)
    val y1 = ability.scarValue(index, SCAR_Y1)
    val segmentX = x1 - x0
    val segmentY = y1 - y0
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val step = apexValue(ECLIPSE_PULL_SPEED) * delta
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.isBossClass || isShifting(enemy)) continue
        val along = if (lengthSquared > 0f) (((enemy.x - x0) * segmentX + (enemy.y - y0) * segmentY) / lengthSquared).coerceIn(0f, 1f) else 0f
        val dx = x0 + segmentX * along - enemy.x
        val dy = y0 + segmentY * along - enemy.y
        val distance = length(dx, dy)
        if (distance <= 1f || distance > ECLIPSE_PULL_REACH + enemy.radius) continue
        val move = min(distance, step * knockbackTaken(enemy))
        enemy.x += dx / distance * move
        enemy.y += dy / distance * move
    }
}

/**
 * A scar detonates on what touches its line, with one Void stack. During Total Eclipse it hits ordinary enemies
 * harder; boss-class enemies take the first detonations of the activation as Apex strikes, and later ones only
 * at the base Blink cadence.
 */
private fun MutableGameState.detonateScar(index: Int) {
    val ability = characterRuntime
    val x0 = ability.scarValue(index, SCAR_X0)
    val y0 = ability.scarValue(index, SCAR_Y0)
    val x1 = ability.scarValue(index, SCAR_X1)
    val y1 = ability.scarValue(index, SCAR_Y1)
    ability.scars[index * SCAR_STRIDE + SCAR_AGE] = 0f
    val damage = SCAR_DAMAGE * abilityPower()
    val eclipse = apexActive()
    val scale = if (eclipse) apexValue(ECLIPSE_SCAR_SCALE) else 1f
    val runtime = skillRuntime
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        if (eclipse && enemy.isBossClass && segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + APEX_BOSS_REACH) &&
            apexStrikeFrom((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, enemy) > 0f
        ) continue
        if (!segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + SCAR_REACH)) continue
        if (eclipse && enemy.isBossClass) {
            if (elapsed < runtime.apex(ApexCell.BOSS_BASE_READY_AT)) continue
            runtime.setApex(ApexCell.BOSS_BASE_READY_AT, elapsed + baseBlinkRechargeSeconds())
            dealCharacterDamage(enemy, damage)
        } else {
            dealCharacterDamage(enemy, damage * scale)
        }
        if (enemy.alive) applyStatus(enemy, StatusElement.VOID, 1, 0)
    }
    addWeaponArc(x0, y0, x1, y1, 0.3f)
}

private fun MutableGameState.removeScar(index: Int) {
    val ability = characterRuntime
    val last = ability.scarCount - 1
    for (slot in index until last) {
        for (field in 0 until SCAR_STRIDE) {
            ability.scars[slot * SCAR_STRIDE + field] = ability.scars[(slot + 1) * SCAR_STRIDE + field]
        }
    }
    ability.scarCount = last
}

internal fun MutableGameState.updateHive(ability: CharacterRuntime, delta: Float) {
    var index = 0
    while (index < ability.droneCount) {
        val base = index * DRONE_STRIDE
        val life = ability.drones[base + DRONE_LIFE] - delta
        if (life <= 0f) {
            val last = ability.droneCount - 1
            for (field in 0 until DRONE_STRIDE) ability.drones[base + field] = ability.drones[last * DRONE_STRIDE + field]
            ability.droneCount = last
            continue
        }
        ability.drones[base + DRONE_LIFE] = life
        index++
    }
    val count = ability.droneCount
    ability.charge = count / CharacterRuntime.MAX_DRONES.toFloat()
    for (drone in 0 until count) {
        val base = drone * DRONE_STRIDE
        val drones = ability.drones
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_X]
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_Y]
        val angle = elapsed * DRONE_ORBIT_SPEED + drone * TAU / count
        val orbitX = coreX + cos(angle) * DRONE_ORBIT_RADIUS
        val orbitY = coreY + sin(angle) * DRONE_ORBIT_RADIUS
        var dart = drones[base + DRONE_DART]
        if (dart <= 0f && elapsed >= drones[base + DRONE_READY_AT]) {
            // Swarm Queen: while strikes remain the drones reach for a boss-class enemy in Apex reach first.
            val target = (if (apexActive()) apexBossInReach(coreX, coreY) else null) ?: nearestEnemy(orbitX, orbitY, DRONE_RANGE)
            if (target != null) {
                // Swarm Queen: ordinary enemies take faster, harder darts; a boss-class target keeps the base
                // interval, and the first darts of the activation at it are Apex strikes.
                val queen = apexAmplifies(target)
                drones[base + DRONE_READY_AT] = elapsed + if (queen) apexValue(SWARM_DART_INTERVAL) else DRONE_INTERVAL
                drones[base + DRONE_TARGET_X] = target.x
                drones[base + DRONE_TARGET_Y] = target.y
                dart = DRONE_DART_SECONDS
                val integrity = target.hp
                val strike = target.isBossClass && apexActive() && apexStrike(target) > 0f
                if (!strike) {
                    val damage = (if (queen) apexValue(SWARM_DART_DAMAGE) else DRONE_DAMAGE) * abilityPower()
                    recordAbilityDamage(integrity, dealProcDamage(target, damage))
                }
                if (!target.alive && elapsed >= ability.droneHealReadyAt) {
                    ability.droneHealReadyAt = elapsed + DRONE_HEAL_INTERVAL
                    heal(DRONE_KILL_HEAL, HealSource.ITEM)
                }
            }
        }
        if (dart > 0f) {
            dart = max(0f, dart - delta)
            val progress = 1f - dart / DRONE_DART_SECONDS
            val reach = 1f - abs(progress * 2f - 1f)
            drones[base + DRONE_X] = lerp(orbitX, drones[base + DRONE_TARGET_X], reach)
            drones[base + DRONE_Y] = lerp(orbitY, drones[base + DRONE_TARGET_Y], reach)
        } else {
            drones[base + DRONE_X] = orbitX
            drones[base + DRONE_Y] = orbitY
        }
        drones[base + DRONE_DART] = dart
    }
}
