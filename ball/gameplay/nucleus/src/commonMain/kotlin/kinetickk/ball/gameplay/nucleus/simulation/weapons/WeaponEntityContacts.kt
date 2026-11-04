// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

// ---------------------------------------------------------------------------------------------
// Contacts and fields.

private fun MutableGameState.sweptContact(entity: WeaponEntity, enemy: Enemy): Boolean =
    segmentCircleIntersects(
        entity.previousX - enemy.previousX,
        entity.previousY - enemy.previousY,
        entity.x - enemy.x,
        entity.y - enemy.y,
        0f,
        0f,
        entity.radius + enemy.radius,
    )

/**
 * A directional guard (a Reflector's front, a Throne's arcs) meets an entity's hit by where the hit comes from
 * ([fromX], [fromY]), as it meets a shot, not by where the Core is; its Core-facing guard holds again after the hit.
 */
internal inline fun <T> hitFrom(enemy: Enemy, fromX: Float, fromY: Float, hit: () -> T): T {
    if (!enemy.type.profile.directionalGuard) return hit()
    val shieldBefore = enemy.guarded
    enemy.guarded = shieldFaces(enemy, fromX, fromY)
    try {
        return hit()
    } finally {
        enemy.guarded = shieldBefore
    }
}

internal fun MutableGameState.discHits(disc: WeaponEntity) {
    // Orb signature: a disc thrown or flying while Orbit is charged hits harder; the third form's return pass too.
    val scale = (if (signatureActive() && orbitCharged) GYRE_ORBIT_BONUS else 1f) *
        (if (disc.phase == 1 && hasMasteryForm(3)) GYRE_RETURN_BONUS else 1f)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || disc.hasHit(enemy.id) || !sweptContact(disc, enemy)) continue
        disc.recordHit(enemy.id)
        hitFrom(enemy, disc.previousX, disc.previousY) { dealWeaponDamage(enemy, disc.damage * scale, canCrit = true, procKey = disc.procKey) }
        burst(enemy.x, enemy.y, 4, 0)
    }
}

internal fun MutableGameState.lensContact(lens: WeaponEntity) {
    if (lens.phase != 0) return
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || !sweptContact(lens, enemy)) continue
        collapseLens(lens)
        return
    }
}

internal fun MutableGameState.wellTick(well: WeaponEntity, delta: Float) {
    // A Dipole's ATTRACT doubles the pull.
    val pull = LENS_PULL * (if (signatureActive() && polarity == Polarity.ATTRACT && weapon == WeaponId.EVENT_LENS) 2f else 1f) * delta
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val dx = well.x - enemy.x
        val dy = well.y - enemy.y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared > square(well.radius + enemy.radius)) continue
        val distance = max(1f, sqrt(distanceSquared))
        val knockback = knockbackTaken(enemy)
        enemy.vx += dx / distance * pull * knockback
        enemy.vy += dy / distance * pull * knockback
        dealWeaponDamage(enemy, well.damage * delta, procKey = well.procKey)
    }
}

internal fun MutableGameState.shardHits(shard: WeaponEntity) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || shard.hasHit(enemy.id) || !sweptContact(shard, enemy)) continue
        shard.recordHit(enemy.id)
        hitFrom(enemy, shard.previousX, shard.previousY) { dealWeaponDamage(enemy, shard.damage, canCrit = true, procKey = shard.procKey) }
        burst(enemy.x, enemy.y, 4, 0)
        if (shard.phase > 0) {
            // A shard pierces on to the nearest enemy it has not struck (two more, three with the second form),
            // losing a little strength at each.
            shard.phase--
            shard.damage *= AEGIS_PIERCE_SHARE
            shard.targetId = nearestUnstruckEnemy(shard, enemy.x, enemy.y, AEGIS_RANGE)?.id ?: -1
            shard.life = max(shard.life, AEGIS_SHARD_LIFE * 0.6f)
        } else {
            shard.life = 0f
            return
        }
    }
}

/** The nearest enemy within [range] of (x, y) that [entity] has not struck yet on this pass, or null. */
internal fun MutableGameState.nearestUnstruckEnemy(entity: WeaponEntity, x: Float, y: Float, range: Float): Enemy? {
    var nearest: Enemy? = null
    var nearestDistanceSquared = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (!candidate.alive || isShifting(candidate) || entity.hasHit(candidate.id)) continue
        val candidateDistanceSquared = distanceSquared(x, y, candidate.x, candidate.y)
        if (candidateDistanceSquared < nearestDistanceSquared || (nearest == null && candidateDistanceSquared == nearestDistanceSquared)) {
            nearest = candidate
            nearestDistanceSquared = candidateDistanceSquared
        }
    }
    return nearest
}

internal fun MutableGameState.missileHits(missile: WeaponEntity) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || !sweptContact(missile, enemy)) continue
        val landed = hitFrom(enemy, missile.previousX, missile.previousY) {
            dealWeaponDamage(enemy, missile.damage, canCrit = true, procKey = missile.procKey)
        }.amount > 0f
        if (landed) {
            val runtime = weaponRuntime
            runtime.elementHits = (runtime.elementHits + 1) % HUNTER_DECAY_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.ENTROPY, 1)
        }
        missileBurst(missile, enemy)
        // Third form: a missile that kills splits into two smaller ones.
        if (!enemy.alive && missile.phase == 0 && hasMasteryForm(3)) {
            val heading = atan2(missile.vy, missile.vx)
            launchMissile(enemy.x, enemy.y, heading - 0.7f, missile.damage * 0.5f, WeaponRuntime.PROC_NEVER, split = false)
            launchMissile(enemy.x, enemy.y, heading + 0.7f, missile.damage * 0.5f, WeaponRuntime.PROC_NEVER, split = false)
        }
        missile.life = 0f
        return
    }
}

internal fun MutableGameState.missileBurst(missile: WeaponEntity, direct: Enemy?) {
    val radius = HUNTER_BURST_RADIUS
    val share = HUNTER_BURST_SHARE * if (hasMasteryForm(2)) HUNTER_BURST_FORM else 1f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === direct || !enemy.alive || distanceSquared(missile.x, missile.y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        dealWeaponDamage(enemy, missile.damage * share, procKey = WeaponRuntime.PROC_NEVER)
    }
    burst(missile.x, missile.y, 6, 4)
}

internal fun MutableGameState.tetherTick(tether: WeaponEntity, delta: Float) {
    val target = enemyById(tether.targetId)
    if (target == null || !target.alive) {
        tether.life = 0f
        return
    }
    // The enemies have moved since the weapon step anchored the tether: its end follows the target to where it is
    // drawn now (its previous point is the target's own previous point).
    tether.x = target.x
    tether.y = target.y
    val runtime = weaponRuntime
    val agonyRank = agonyPower()
    val primary = tether.phase == 0
    // The second tether rides the primary's ramp.
    val ramp = runtime.tetherRamp
    val share = if (primary) 1f else TETHER_SECOND_SHARE
    val damage = TETHER_DPS * delta * effectiveWeaponPower() * ramp * share * (1f + 0.1f * agonyRank)
    val landed = dealWeaponDamage(target, damage, cadence = WeaponHitCadence.CONTINUOUS, procKey = tether.procKey).amount > 0f
    if (landed) weaponElementTick(target, StatusElement.ION, TETHER_CHARGE_INTERVAL)
    if (!target.alive) {
        // Third form: a tethered kill overloads into the nearest enemies.
        if (hasMasteryForm(3)) {
            val power = content.build.element(StatusElement.ION).perStack * build.potency(StatusElement.ION) * runScale() * TETHER_OVERLOAD_STACKS
            chain(target.x, target.y, target.id, TETHER_OVERLOAD_LINKS, TETHER_RANGE) { link ->
                dealStatusDamage(link, power, StatusElement.ION)
                if (link.alive) applyStatus(link, StatusElement.ION, 1, 1)
            }
        }
        return
    }
    // Dipole signature: ATTRACT draws the tethered enemy in, REPEL throws it out into slams.
    if (signatureActive()) {
        val knockback = knockbackTaken(target)
        val dx = coreX - target.x
        val dy = coreY - target.y
        val distance = max(1f, length(dx, dy))
        val accel = TETHER_PULL * knockback * delta
        if (polarity == Polarity.ATTRACT) {
            target.vx += dx / distance * accel
            target.vy += dy / distance * accel
        } else {
            target.vx -= dx / distance * accel * 1.6f
            target.vy -= dy / distance * accel * 1.6f
            markThrown(target)
        }
    }
}

internal fun MutableGameState.beamTick(beam: WeaponEntity, delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    val dps = SOLAR_DPS * power * (1f + 0.1f * agonyRank)
    val cosine = cos(beam.angle)
    val sine = sin(beam.angle)
    val innerX = coreX + cosine * beam.radius
    val innerY = coreY + sine * beam.radius
    val outerX = coreX + cosine * (beam.radius + beam.extent)
    val outerY = coreY + sine * (beam.radius + beam.extent)
    val arcs = hasMasteryForm(3)
    // The nearest enemy in the beam's path burns at full heat; the ones it shadows take [SOLAR_SHADOW_SHARE].
    var nearestSquared = Float.MAX_VALUE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || !segmentCircleIntersects(innerX, innerY, outerX, outerY, enemy.x, enemy.y, enemy.radius + SOLAR_REACH)) continue
        nearestSquared = min(nearestSquared, distanceSquared(coreX, coreY, enemy.x, enemy.y))
    }
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (segmentCircleIntersects(innerX, innerY, outerX, outerY, enemy.x, enemy.y, enemy.radius + SOLAR_REACH)) {
            val heat = if (distanceSquared(coreX, coreY, enemy.x, enemy.y) <= nearestSquared) 1f else SOLAR_SHADOW_SHARE
            val landed = dealWeaponDamage(enemy, dps * heat * delta, cadence = WeaponHitCadence.CONTINUOUS, procKey = beam.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.THERMAL, SOLAR_SCORCH_INTERVAL)
            continue
        }
        if (!arcs) continue
        // Third form: the arc each beam just swept keeps burning.
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = length(dx, dy)
        if (distance < beam.radius || distance > beam.radius + beam.extent + enemy.radius) continue
        val behind = atan2(sin(beam.angle - atan2(dy, dx)), cos(beam.angle - atan2(dy, dx)))
        if (behind in 0f..SOLAR_ARC_SECTOR) {
            dealWeaponDamage(enemy, dps * SOLAR_ARC_SHARE * delta, cadence = WeaponHitCadence.CONTINUOUS, procKey = beam.procKey)
        }
    }
}
