// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.abs
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
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/*
 * Weapon activations (SPEC 10.3). Numbers are at weapon power 1; [effectiveWeaponPower] carries level,
 * mastery, run growth, Overdrive and the synergies. A discrete activation takes a proc serial
 * ([beginWeaponActivation]) that every hit it causes carries; continuous and contact damage resolves in
 * [resolveWeaponDamage], entity hits in [resolveWeaponEntityHits].
 */

internal fun MutableGameState.updateWeapons(delta: Float) {
    weaponClock -= delta
    weaponSecondaryClock -= delta
    val runtime = weaponRuntime
    val dashed = trackWeaponInputs(delta)
    for (index in runtime.entities.indices) runtime.entities[index].capturePrevious()
    if (blinkThisStep) shiftCoreAnchoredEntities(blinkShiftX(), blinkShiftY())
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> sampleFluxTrail()
        WeaponId.MORNINGSTAR -> updateMorningstar(delta, agonyRank)
        WeaponId.PHASE_LATTICE -> if (agonyRank > 0) agonyMutationCounts[WeaponId.PHASE_LATTICE.ordinal]++
        WeaponId.NULL_LANCE -> if (weaponClock <= 0f) fireNullLance(power, agonyRank)
        WeaponId.GRAVITY_MINES -> updateGravityMines(agonyRank)
        WeaponId.ION_SWARM -> updateIonSwarm(delta, power, agonyRank)
        WeaponId.RIFT_BLADES -> {
            val reach = riftBladeReach()
            ensureOrbitals(riftBladeCount(), 82f + min(75f, softVelocity(speed) * 0.025f) + agonyRank * 5f, reach, delta,
                (4.2f + agonyRank * 0.08f) * continuousWeaponRate())
            if (agonyRank > 0) agonyMutationCounts[WeaponId.RIFT_BLADES.ordinal]++
        }
        WeaponId.ARC_COIL -> if (weaponClock <= 0f && nearestEnemy(coreX, coreY, ARC_COIL_FIRST_RANGE) != null) {
            weaponClock = cooldown(ARC_COIL_COOLDOWN)
            fireArcCoil(ARC_COIL_DAMAGE * power, beginWeaponActivation())
            emitSound(GameplayAudioCue.WEAPON_LIGHT)
        }
        WeaponId.QUASAR_CANNON -> if (weaponClock <= 0f) fireQuasar(power, agonyRank)
        WeaponId.ENTROPY_FIELD -> if (agonyRank > 0) agonyMutationCounts[WeaponId.ENTROPY_FIELD.ordinal]++
        WeaponId.SINGULARITY_SPEAR -> {
            if (weaponClock <= 0f) {
                weaponClock = cooldown(if (overdriveTime > 0f) 0.9f else 3.2f)
                fireSingularitySpear(SPEAR_DAMAGE * power, beginWeaponActivation())
            }
            // Crescent: each Blink drives a spear along its path.
            if (signatureActive() && blinkThisStep && elapsed >= runtime.signatureReadyAt) {
                runtime.signatureReadyAt = elapsed + SPEAR_BLINK_COOLDOWN
                fireBlinkSpear(SPEAR_DAMAGE * power * continuousWeaponRate(), beginWeaponActivation())
            }
        }
        WeaponId.PRISM_RELAY -> if (weaponClock <= 0f && enemies.isNotEmpty()) {
            weaponClock = cooldown(0.92f)
            firePrismRelay(power, beginWeaponActivation())
        }
        WeaponId.GYRE_DISC -> if (weaponClock <= 0f) throwGyreDiscs(power, agonyRank)
        WeaponId.SCATTER_BLOOM -> updateScatterBloom(delta, power, agonyRank, dashed)
        WeaponId.TETHER_ARC -> updateTethers(delta, agonyRank)
        WeaponId.SENTRY_GRID -> if (weaponClock <= 0f) deploySentry(power, agonyRank)
        WeaponId.HUNTER_SALVO -> if (weaponClock <= 0f && nearestEnemy(coreX, coreY, HUNTER_RANGE) != null) launchSalvo(power, agonyRank)
        WeaponId.CONCUSSION_ENGINE -> updateConcussionGhost(power, agonyRank)
        WeaponId.GLACIER_BELL -> updateGlacierBell(delta, power, agonyRank)
        WeaponId.EVENT_LENS -> if (weaponClock <= 0f) castLenses(power, agonyRank)
        WeaponId.SOLAR_SWEEP -> updateSolarBeams(delta, agonyRank)
        WeaponId.AEGIS_SHARDS -> updateAegisShards(delta, power, agonyRank)
        WeaponId.HOLLOW_RIFT -> updateHollowRifts(delta, power, agonyRank, dashed)
        WeaponId.RESONANCE_DRUM -> updateResonanceDrum(power, agonyRank)
    }

    var retainedTrailCount = 0
    for (index in trail.indices) {
        val point = trail[index]
        point.age += delta
        if (!(point.age > point.life + TRAIL_FADE_SECONDS)) {
            if (retainedTrailCount != index) trail[retainedTrailCount] = point
            retainedTrailCount++
        }
    }
    var trailIndex = trail.lastIndex
    while (trailIndex >= retainedTrailCount) {
        trail.removeAt(trailIndex)
        trailIndex--
    }
    updateWeaponNodes(delta)
    advanceWeaponEntities(delta)
    emitVisualFx(VisualFxCue.WeaponArcsAdvanced(delta))
}

/**
 * The travel heading an aimed weapon fires along, nudged onto the enemy closest to it when one lies within
 * [AIM_ASSIST_CONE] of the heading and [AIM_ASSIST_RANGE] of the Core (a lance flown at an enemy finds it).
 */
internal fun MutableGameState.aimedMovementAngle(): Float {
    val heading = movementAngle()
    var best = heading
    var bestOffset = AIM_ASSIST_CONE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        if (dx * dx + dy * dy > AIM_ASSIST_RANGE * AIM_ASSIST_RANGE) continue
        val bearing = atan2(dy, dx)
        val offset = abs(atan2(sin(bearing - heading), cos(bearing - heading)))
        if (offset < bestOffset) {
            bestOffset = offset
            best = bearing
        }
    }
    return best
}

/** A discrete activation: its proc serial, and Agony Scepter's heat liability. */
internal fun MutableGameState.beginWeaponActivation(): Int {
    onWeaponActivation()
    return weaponRuntime.beginActivation()
}

/**
 * Tracks the scaling and trigger inputs of this step: a player Dash (returned), Brake speed loss, how long
 * the Brake has been held and the heading the trail compares against.
 */
private fun MutableGameState.trackWeaponInputs(delta: Float): Boolean {
    val runtime = weaponRuntime
    val sequence = characterRuntime.dashSequence
    val dashed = sequence != runtime.seenDashSequence
    if (dashed) {
        runtime.seenDashSequence = sequence
        runtime.lastDashAt = elapsed
    }
    val currentSpeed = speed
    runtime.recordShed(if (braking) max(0f, runtime.previousSpeed - currentSpeed) else 0f, delta)
    runtime.previousSpeed = currentSpeed
    return dashed
}

/** Applies the weapon's own element on a per-enemy interval (continuous weapons). */
internal fun MutableGameState.weaponElementTick(enemy: Enemy, element: StatusElement, interval: Float, extraBeforeBreak: Boolean = false) {
    if (!enemy.alive || enemy.hitQualificationCooldown > 0f) return
    // A faster weapon applies its element faster too.
    enemy.hitQualificationCooldown = interval / continuousWeaponRate()
    var count = 1
    // A form that breaks one stack sooner adds the last stack early.
    if (extraBeforeBreak && stacks(enemy, element) + 2 >= breakThreshold(element, enemy)) count = 2
    applyStatus(enemy, element, count, 0)
}

internal fun MutableGameState.applyWeaponStatus(enemy: Enemy, element: StatusElement, count: Int) {
    if (enemy.alive) applyStatus(enemy, element, count, 0)
}

/**
 * How far a Blink moved the Core this step, or 0. Visuals anchored at the Core (the Morningstar, orbitals, solar
 * beams, the tether's Core end) shift their previous points by it, so they jump with the Core instead of sliding
 * across the gap between the two draws.
 */
internal fun MutableGameState.blinkShiftX(): Float = if (blinkThisStep) blinkToX - blinkFromX else 0f
internal fun MutableGameState.blinkShiftY(): Float = if (blinkThisStep) blinkToY - blinkFromY else 0f

/** Moves the previous Core-side points of beams and tethers by a Blink's ([dx], [dy]). */
private fun MutableGameState.shiftCoreAnchoredEntities(dx: Float, dy: Float) {
    val entities = weaponRuntime.entities
    for (index in entities.indices) {
        val entity = entities[index]
        when (entity.kind) {
            WeaponEntityKind.BEAM -> {
                entity.previousX += dx
                entity.previousY += dy
            }
            WeaponEntityKind.TETHER -> {
                entity.previousEndX += dx
                entity.previousEndY += dy
            }
            else -> Unit
        }
    }
}

/** Knocks [enemy] away from (x, y); a thrown enemy can slam into obstacles and other enemies. */
internal fun MutableGameState.pushEnemy(enemy: Enemy, x: Float, y: Float, strength: Float) {
    val knockback = knockbackTaken(enemy)
    if (knockback <= 0f || !enemy.alive) return
    val dx = enemy.x - x
    val dy = enemy.y - y
    val distance = max(1f, length(dx, dy))
    enemy.vx += dx / distance * strength * knockback
    enemy.vy += dy / distance * strength * knockback
    markThrown(enemy)
}

/**
 * Adds a weapon entity within [WeaponRuntime.MAX_WEAPON_ENTITIES] live ones: a finished entity gives way first,
 * else the oldest one other than a tether or a beam; with neither, the new entity is dropped. Inside an entity
 * pass nothing leaves the list: the new entity joins its end (the pass does not visit it) and the one making room
 * finishes, leaving with the pass's other finished entities, so no index shifts under the pass.
 */
internal fun MutableGameState.addWeaponEntity(entity: WeaponEntity) {
    val runtime = weaponRuntime
    val entities = runtime.entities
    if (runtime.entityPassActive) {
        var live = 0
        for (index in entities.indices) if (entities[index].life > 0f) live++
        if (live >= WeaponRuntime.MAX_WEAPON_ENTITIES) {
            val victim = oldestEvictableWeaponEntity(entities)
            if (victim < 0) return
            entities[victim].life = 0f
        }
        entities += entity
        return
    }
    if (entities.size >= WeaponRuntime.MAX_WEAPON_ENTITIES) {
        var victim = -1
        for (index in entities.indices) if (entities[index].life <= 0f) { victim = index; break }
        if (victim < 0) victim = oldestEvictableWeaponEntity(entities)
        if (victim < 0) return
        entities.removeAt(victim)
    }
    entities += entity
}

/** The oldest live entity other than a tether or a beam, or -1. */
private fun oldestEvictableWeaponEntity(entities: List<WeaponEntity>): Int {
    for (index in entities.indices) {
        val candidate = entities[index]
        if (candidate.life > 0f && candidate.kind != WeaponEntityKind.TETHER && candidate.kind != WeaponEntityKind.BEAM) return index
    }
    return -1
}
