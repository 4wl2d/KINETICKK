// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/** Prism signature: damage and size per intact facet. */
internal fun MutableGameState.morningstarScale(): Float = if (signatureActive()) 1f + 0.1f * intactFacets else 1f

internal fun MutableGameState.morningstarOrbitRadius(): Float =
    (104f + min(70f, softVelocity(speed) * 0.055f) + agonyPower() * 4f) * if (hasMasteryForm(1)) 1.15f else 1f

internal fun MutableGameState.updateMorningstar(delta: Float, agonyRank: Int) {
    val angularSpeed = (3.2f + softVelocity(speed) / 135f + agonyRank * 0.12f) * continuousWeaponRate()
    morningstarAngle = (morningstarAngle + angularSpeed * delta) % TAU
    val radius = morningstarOrbitRadius()
    previousMorningstarX = morningstarX + blinkShiftX()
    previousMorningstarY = morningstarY + blinkShiftY()
    morningstarX = coreX + cos(morningstarAngle) * radius
    morningstarY = coreY + sin(morningstarAngle) * radius
    if (agonyRank > 0) agonyMutationCounts[WeaponId.MORNINGSTAR.ordinal]++
}

internal fun MutableGameState.updateIonSwarm(delta: Float, power: Float, agonyRank: Int) {
    ensureOrbitals(ionOrbitalCount(), 145f + agonyRank * 3f, 8f, delta, 1.8f + agonyRank * 0.05f)
    if (agonyRank > 0) agonyMutationCounts[WeaponId.ION_SWARM.ordinal]++
    if (weaponClock <= 0f && enemies.isNotEmpty()) {
        weaponClock = cooldown(ION_SWARM_COOLDOWN / if (hasMasteryForm(3)) ION_SWARM_FIRE_RATE_FORM else 1f)
        val serial = beginWeaponActivation()
        val pierce = ION_SWARM_PIERCE + if (hasMasteryForm(2)) 1 else 0
        for (index in weaponOrbitals.indices) {
            val orbital = weaponOrbitals[index]
            nearestEnemy(orbital.x, orbital.y, 620f)?.let { enemy ->
                val angle = atan2(enemy.y - orbital.y, enemy.x - orbital.x)
                firePlayerProjectile(orbital.x, orbital.y, angle, 620f, ION_SWARM_DAMAGE * power, pierce, 4f, 1, serial)
            }
        }
        // Hexagon signature: up to three drones fire with the swarm.
        if (signatureActive()) {
            for (drone in 0 until min(droneCount, 3)) {
                val x = droneX(drone)
                val y = droneY(drone)
                nearestEnemy(x, y, 620f)?.let { enemy ->
                    firePlayerProjectile(x, y, atan2(enemy.y - y, enemy.x - x), 620f, ION_SWARM_DAMAGE * power, pierce, 4f, 1, serial)
                }
            }
        }
        emitSound(GameplayAudioCue.WEAPON_LIGHT)
    }
}

/** Star signature: Rift Blades grow 2 % per Stellar Mass stack; the second form adds 30 %. */
internal fun MutableGameState.riftBladeReach(): Float =
    17f * (if (hasMasteryForm(2)) 1.3f else 1f) * (if (signatureActive()) 1f + 0.02f * stellarMass else 1f)

internal fun MutableGameState.ensureOrbitals(
    count: Int,
    orbitRadius: Float,
    hitRadius: Float,
    delta: Float,
    angularSpeed: Float,
) {
    require(count in 0..MutableGameState.MAX_WEAPON_ORBITALS) {
        "weapon orbital count cannot exceed ${MutableGameState.MAX_WEAPON_ORBITALS}"
    }
    var orbitalsMatch = weaponOrbitals.size == count
    if (orbitalsMatch) {
        for (index in weaponOrbitals.indices) {
            if (weaponOrbitals[index].radius != hitRadius) {
                orbitalsMatch = false
                break
            }
        }
    }
    if (!orbitalsMatch) {
        weaponOrbitals.clear()
        repeat(count) { weaponOrbitals += WeaponOrbital(it, coreX, coreY, hitRadius) }
    }
    // The phase accumulates, so a change of angular speed (attack speed, a mastery milestone, a buff) only bends
    // the orbit; deriving it from the run clock would jump every orbital by elapsed × the change.
    val runtime = weaponRuntime
    runtime.orbitalPhase = (runtime.orbitalPhase + angularSpeed * delta) % TAU
    for (index in weaponOrbitals.indices) {
        val orbital = weaponOrbitals[index]
        val angle = runtime.orbitalPhase + orbital.index * TAU / max(1, count)
        orbital.previousX = orbital.x + blinkShiftX()
        orbital.previousY = orbital.y + blinkShiftY()
        orbital.x = coreX + cos(angle) * orbitRadius
        orbital.y = coreY + sin(angle) * orbitRadius
        // A freshly formed orbital appears on its orbit instead of sweeping out from the Core.
        if (!orbitalsMatch) {
            orbital.previousX = orbital.x
            orbital.previousY = orbital.y
        }
    }
}

internal fun MutableGameState.updateTethers(delta: Float, agonyRank: Int) {
    val runtime = weaponRuntime
    val range = TETHER_RANGE + if (hasMasteryForm(1)) 80f else 0f
    val ramp = TETHER_RAMP_MAX + if (hasMasteryForm(3)) TETHER_RAMP_FORM else 0f
    // The primary tether holds its target while it lives in range and can be hit (a phased Phantom or shifting
    // Architect takes nothing); a kill passes most of the ramp on.
    val held = enemyById(runtime.tetherTargetId)?.takeIf {
        it.alive && !isShifting(it) && distanceSquared(coreX, coreY, it.x, it.y) <= square(range + it.radius)
    }
    val rampRate = (ramp - 1f) / TETHER_RAMP_SECONDS * delta
    if (held == null) {
        // A new target (after a kill, or when the old one left the range) keeps 60 % of the ramp; idle, it fades.
        val next = nearestEnemy(coreX, coreY, range)
        runtime.tetherRamp = if (next != null) 1f + (runtime.tetherRamp - 1f) * TETHER_KILL_KEEP else max(1f, runtime.tetherRamp - rampRate)
        runtime.tetherTargetId = next?.id ?: -1
    } else {
        runtime.tetherRamp = min(ramp, runtime.tetherRamp + rampRate)
    }
    val second = if (hasMasteryForm(2) || agonyRank > 0) {
        enemyById(runtime.secondTetherId)?.takeIf {
            it.alive && !isShifting(it) && it.id != runtime.tetherTargetId && distanceSquared(coreX, coreY, it.x, it.y) <= square(range + it.radius)
        } ?: nearestOtherEnemy(coreX, coreY, runtime.tetherTargetId, range)
    } else null
    if (second == null || second.id != runtime.secondTetherId) runtime.secondTetherRamp = 1f
    else runtime.secondTetherRamp = min(ramp, runtime.secondTetherRamp + (ramp - 1f) / TETHER_RAMP_SECONDS * delta)
    runtime.secondTetherId = second?.id ?: -1
    syncTetherEntity(0, enemyById(runtime.tetherTargetId))
    syncTetherEntity(1, second)
    if (agonyRank > 0) agonyMutationCounts[WeaponId.TETHER_ARC.ordinal]++
}

/** Keeps one TETHER entity per held target (slot 0 primary, 1 second), anchored at the target. */
private fun MutableGameState.syncTetherEntity(slot: Int, target: Enemy?) {
    val entities = weaponRuntime.entities
    var existing: WeaponEntity? = null
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind == WeaponEntityKind.TETHER && entity.phase == slot) existing = entity
    }
    if (target == null) {
        existing?.life = 0f
        return
    }
    val tether = existing ?: WeaponEntity(WeaponEntityKind.TETHER, target.x, target.y, 0f, 1f, 1f, phase = slot,
        procKey = WeaponRuntime.sourceKey(slot)).also { addWeaponEntity(it) }
    tether.life = 1f
    tether.endX = coreX
    tether.endY = coreY
    if (tether.targetId != target.id) {
        tether.targetId = target.id
        tether.x = target.x
        tether.y = target.y
        tether.settle()
    }
}

/** Concussion Engine: a Core impact at speed emits a shockwave (called by the contact pass). */
internal fun MutableGameState.onWeaponImpact(impactSpeed: Float, impactDamage: Float) {
    if (weapon != WeaponId.CONCUSSION_ENGINE || impactSpeed < CONCUSSION_MIN_SPEED) return
    val runtime = weaponRuntime
    if (elapsed < runtime.readyAt) return
    runtime.readyAt = elapsed + CONCUSSION_COOLDOWN
    val agonyRank = agonyPower()
    val radius = concussionRadius(impactSpeed, agonyRank)
    // Impacts are no cooldown: attack speed raises the shockwave's damage instead. The impact's run growth and
    // Rebirth power come out of its share, since the weapon power brings both back.
    val impactBase = impactDamage / (impactRunGrowth() * rebirthProfile.playerPowerMultiplier)
    val damage = (CONCUSSION_DAMAGE + CONCUSSION_IMPACT_SHARE * impactBase) * effectiveWeaponPower() *
        (1f + 0.1f * agonyRank) * continuousWeaponRate()
    concussionWave(coreX, coreY, radius, damage, beginWeaponActivation())
}

internal fun MutableGameState.concussionRadius(impactSpeed: Float, agonyRank: Int): Float =
    min(CONCUSSION_MAX_RADIUS, CONCUSSION_BASE_RADIUS + 0.08f * impactSpeed) * (if (hasMasteryForm(1)) 1.2f else 1f) + 12f * agonyRank

private fun MutableGameState.concussionWave(x: Float, y: Float, radius: Float, damage: Float, serial: Int) {
    val knockback = CONCUSSION_KNOCKBACK * if (hasMasteryForm(2)) 1.5f else 1f
    var caught = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && distanceSquared(x, y, enemy.x, enemy.y) <= square(radius + enemy.radius)) caught++
    }
    // A wave strikes harder the more enemies it catches: a lone enemy takes the lone share.
    val struck = damage * CONCUSSION_LONE_SHARE * (1f + CONCUSSION_CROWD_BONUS * min(caught - 1, CONCUSSION_CROWD_CAP))
    var hits = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, struck, canCrit = true, procKey = serial).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.SONIC, 1)
        pushEnemy(enemy, x, y, knockback)
        hits++
    }
    if (hasMasteryForm(3) && hits > 0) addShield(CONCUSSION_SHIELD_PER_HIT * hits)
    addWeaponEntity(WeaponEntity(WeaponEntityKind.RING, x, y, radius, RING_SECONDS, RING_SECONDS, phase = RING_CONCUSSION))
    shockwave(x, y, 0.3f, radius, 3)
    emitSound(GameplayAudioCue.IMPACT)
}

/**
 * Spiral signature: the echo ghost's contacts also emit (smaller) shockwaves. The ghost is a second source with
 * the Core's own shockwave cadence: at most one wave per [CONCUSSION_COOLDOWN] (on top of the per-enemy gate),
 * however many enemies it touches.
 */
internal fun MutableGameState.updateConcussionGhost(power: Float, agonyRank: Int) {
    if (agonyRank > 0) agonyMutationCounts[WeaponId.CONCUSSION_ENGINE.ordinal]++
    val runtime = weaponRuntime
    if (!signatureActive() || !ghostActive || ghostSpeed < CONCUSSION_MIN_SPEED || elapsed < runtime.signatureReadyAt) return
    val x = ghostX
    val y = ghostY
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        if (distanceSquared(x, y, enemy.x, enemy.y) > square(coreRadius + enemy.radius)) continue
        enemy.weaponCooldown = CONCUSSION_GHOST_GATE
        runtime.signatureReadyAt = elapsed + CONCUSSION_COOLDOWN
        // Like the Core's impacts, ghost contacts are no cooldown: attack speed raises the wave's damage instead.
        concussionWave(x, y, concussionRadius(ghostSpeed, agonyRank) * 0.7f, CONCUSSION_DAMAGE * power * 0.6f * continuousWeaponRate(),
            beginWeaponActivation())
        return
    }
}

internal fun MutableGameState.updateGlacierBell(delta: Float, power: Float, agonyRank: Int) {
    val runtime = weaponRuntime
    if (agonyRank > 0) agonyMutationCounts[WeaponId.GLACIER_BELL.ordinal]++
    val damage = GLACIER_DAMAGE * power * (1f + 0.1f * agonyRank)
    val radius = GLACIER_RADIUS * (if (hasMasteryForm(1)) 1.2f else 1f) + 12f * agonyRank
    var tolled = false
    if (braking) {
        runtime.brakeHeld += delta
    } else {
        if (runtime.brakeHeld >= GLACIER_HOLD_SECONDS && elapsed >= runtime.readyAt) tolled = true
        runtime.brakeHeld = 0f
    }
    // Diamond signature: a parry tolls at once.
    if (signatureActive() && parriedThisStep && elapsed >= runtime.signatureReadyAt) {
        runtime.signatureReadyAt = elapsed + PARRY_TWIST_GUARD
        tolled = true
    }
    if (tolled) {
        runtime.readyAt = elapsed + cooldown(GLACIER_TOLL_COOLDOWN)
        runtime.passiveClock = cooldown(GLACIER_PASSIVE_INTERVAL)
        // A Brake release (or a parry) is no cooldown: attack speed raises the toll's damage instead.
        tollBell(coreX, coreY, radius, damage * continuousWeaponRate(), 2, beginWeaponActivation(), RING_TOLL)
        if (hasMasteryForm(3)) {
            runtime.followUpAt = elapsed + GLACIER_SECOND_TOLL
            runtime.followUpX = coreX
            runtime.followUpY = coreY
        }
    }
    if (runtime.followUpAt > 0f && elapsed >= runtime.followUpAt) {
        runtime.followUpAt = WeaponRuntime.NEVER
        // The second toll rings where the Core is now.
        tollBell(coreX, coreY, radius, damage, 2, beginWeaponActivation(), RING_TOLL)
    }
    runtime.passiveClock -= delta
    if (runtime.passiveClock <= 0f) {
        runtime.passiveClock = cooldown(GLACIER_PASSIVE_INTERVAL)
        tollBell(coreX, coreY, GLACIER_PASSIVE_RADIUS + 12f * agonyRank, damage * GLACIER_PASSIVE_SHARE, 1, beginWeaponActivation(), RING_PASSIVE)
    }
}

private fun MutableGameState.tollBell(x: Float, y: Float, radius: Float, damage: Float, chill: Int, serial: Int, variant: Int) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, damage, canCrit = true, procKey = serial).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.CRYO, chill)
        // Second form: half a Chill threshold freezes.
        if (hasMasteryForm(2) && enemy.alive && stacks(enemy, StatusElement.CRYO) * 2 >= breakThreshold(StatusElement.CRYO, enemy)) {
            forceElementBreak(enemy, StatusElement.CRYO)
        }
    }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.RING, x, y, radius, RING_SECONDS, RING_SECONDS, phase = variant))
    shockwave(x, y, 0.35f, radius, 1)
    emitSound(if (variant == RING_TOLL) GameplayAudioCue.WEAPON_HEAVY else GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.updateSolarBeams(delta: Float, agonyRank: Int) {
    val runtime = weaponRuntime
    val count = 2 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyRank > 0) 1 else 0
    runtime.sweepAngle = (runtime.sweepAngle + SOLAR_ANGULAR_SPEED * continuousWeaponRate() * delta) % TAU
    val inner = solarInnerRadius()
    val length = solarBeamLength()
    val entities = runtime.entities
    var slot = 0
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind != WeaponEntityKind.BEAM) continue
        if (entity.phase >= count) {
            entity.life = 0f
            continue
        }
        entity.angle = runtime.sweepAngle + entity.phase * TAU / count
        entity.radius = inner
        entity.extent = length
        entity.x = coreX
        entity.y = coreY
        slot = max(slot, entity.phase + 1)
    }
    while (slot < count) {
        addWeaponEntity(WeaponEntity(WeaponEntityKind.BEAM, coreX, coreY, inner, 1f, 1f, extent = length,
            angle = runtime.sweepAngle + slot * TAU / count, phase = slot, procKey = WeaponRuntime.sourceKey(0)))
        slot++
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.SOLAR_SWEEP.ordinal]++
}

/** Ring signature: beams start from the Halo radius. */
internal fun MutableGameState.solarInnerRadius(): Float = if (signatureActive()) haloRadius else 0f
internal fun MutableGameState.solarBeamLength(): Float = SOLAR_LENGTH + if (hasMasteryForm(1)) SOLAR_LENGTH_FORM else 0f

internal fun MutableGameState.updateAegisShards(delta: Float, power: Float, agonyRank: Int) {
    val runtime = weaponRuntime
    ensureOrbitals(aegisShardCount(), AEGIS_ORBIT_RADIUS, AEGIS_SHARD_RADIUS, delta, AEGIS_ORBIT_SPEED)
    if (agonyRank > 0) agonyMutationCounts[WeaponId.AEGIS_SHARDS.ordinal]++
    val capacity = shieldCapacity()
    val shieldUp = shield > 0.5f
    val broke = runtime.shieldWasUp && !shieldUp
    runtime.shieldWasUp = shieldUp
    if (broke) {
        // A shield break fires every shard at double damage.
        launchAegisShards(AEGIS_DAMAGE * power * 2f * (1f + 0.1f * agonyRank), AEGIS_BREAK_RANGE, beginWeaponActivation())
        if (hasMasteryForm(3) && elapsed >= runtime.shieldRestoreReadyAt) {
            runtime.shieldRestoreReadyAt = elapsed + AEGIS_RESTORE_COOLDOWN
            addShield(capacity * 0.25f)
        }
    }
    if (weaponClock <= 0f && nearestEnemy(coreX, coreY, AEGIS_RANGE) != null) {
        weaponClock = cooldown(AEGIS_COOLDOWN)
        launchAegisShards(AEGIS_DAMAGE * power * (1f + 0.1f * agonyRank), AEGIS_RANGE, beginWeaponActivation())
    }
}

/** Shards: two, a third at the first form, one per intact Prism facet (at most two more), one from Agony. */
internal fun MutableGameState.aegisShardCount(): Int =
    (AEGIS_SHARDS_BASE + (if (hasMasteryForm(1)) 1 else 0) + (if (signatureActive()) min(2, intactFacets) else 0) +
        (if (agonyPower() > 0) 1 else 0)).coerceAtMost(MutableGameState.MAX_WEAPON_ORBITALS)

private fun MutableGameState.launchAegisShards(damage: Float, range: Float, serial: Int) {
    val pierce = AEGIS_PIERCE + if (hasMasteryForm(2)) 1 else 0
    // Each shard takes a different target when there are enough (the nearest one left), then doubles up.
    var previousTarget = -1
    for (index in weaponOrbitals.indices) {
        val orbital = weaponOrbitals[index]
        val target = nearestOtherEnemy(orbital.x, orbital.y, previousTarget, range + AEGIS_ORBIT_RADIUS)
            ?: nearestEnemy(orbital.x, orbital.y, range + AEGIS_ORBIT_RADIUS) ?: continue
        previousTarget = target.id
        val angle = atan2(target.y - orbital.y, target.x - orbital.x)
        addWeaponEntity(WeaponEntity(WeaponEntityKind.SHARD, orbital.x, orbital.y, AEGIS_SHARD_RADIUS, AEGIS_SHARD_LIFE, AEGIS_SHARD_LIFE,
            vx = cos(angle) * AEGIS_SPEED, vy = sin(angle) * AEGIS_SPEED, damage = damage, angle = angle, phase = pierce,
            procKey = serial, targetId = target.id))
    }
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.updateResonanceDrum(power: Float, agonyRank: Int) {
    val runtime = weaponRuntime
    if (agonyRank > 0) agonyMutationCounts[WeaponId.RESONANCE_DRUM.ordinal]++
    val radius = DRUM_RADIUS * (if (hasMasteryForm(1)) 1.2f else 1f) + 12f * agonyRank
    val damage = DRUM_DAMAGE * power * (1f + 0.1f * agonyRank)
    if (weaponClock <= 0f) runtime.drumArmed = true
    // Pentagon signature: a ready pulse waits for the beat and lands ×1.5.
    val snap = signatureActive()
    if (runtime.drumArmed && (!snap || isOnBeat() || weaponClock <= -BEAT_PERIOD)) {
        runtime.drumArmed = false
        weaponClock = cooldown(DRUM_COOLDOWN)
        val onBeat = snap && isOnBeat()
        drumPulse(coreX, coreY, radius, damage * if (onBeat) DRUM_BEAT_SCALE else 1f, beginWeaponActivation())
        if (hasMasteryForm(2)) {
            runtime.followUpAt = elapsed + DRUM_DOUBLE_BEAT
            runtime.followUpX = coreX
            runtime.followUpY = coreY
        }
    }
    if (runtime.followUpAt > 0f && elapsed >= runtime.followUpAt) {
        runtime.followUpAt = WeaponRuntime.NEVER
        drumPulse(coreX, coreY, radius, damage * DRUM_DOUBLE_SHARE, beginWeaponActivation())
    }
}

private fun MutableGameState.drumPulse(x: Float, y: Float, radius: Float, damage: Float, serial: Int) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, damage, canCrit = true, procKey = serial).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.SONIC, 1)
        // Third form: Resonant enemies (half a threshold) Shatter.
        if (hasMasteryForm(3) && enemy.alive && stacks(enemy, StatusElement.SONIC) * 2 >= breakThreshold(StatusElement.SONIC, enemy)) {
            forceElementBreak(enemy, StatusElement.SONIC)
        }
        pushEnemy(enemy, x, y, DRUM_KNOCKBACK)
    }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.RING, x, y, radius, RING_SECONDS, RING_SECONDS, phase = RING_DRUM))
    shockwave(x, y, 0.3f, radius, 0)
    emitSound(GameplayAudioCue.WEAPON_HEAVY)
}
