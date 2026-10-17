// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId

import kinetickk.ball.gameplay.api.*
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

// ---------------------------------------------------------------------------------------------
// The original twelve.

internal fun MutableGameState.sampleFluxTrail() {
    val runtime = weaponRuntime
    val heading = atan2(velocityY, velocityX)
    val turn = abs(atan2(sin(heading - runtime.previousHeading), cos(heading - runtime.previousHeading)))
    runtime.previousHeading = heading
    if (speed <= 45f) {
        trailLastX = coreX
        trailLastY = coreY
        return
    }
    val dx = coreX - trailLastX
    val dy = coreY - trailLastY
    val distance = length(dx, dy)
    if (distance <= 0f) return
    val agonyRank = agonyPower()
    // Orb signature: a trail laid while turning (a hard turn: 60° per 100 dp or tighter) burns harder and longer.
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val turning = signatureActive() && moved > 0f && turn / moved >= FLUX_TURN_CURVATURE
    val life = (if (hasMasteryForm(2)) FLUX_LONG_LIFE else TRAIL_LIFE_SECONDS) * (if (turning) 1.5f else 1f) + agonyRank * 0.18f
    val scale = if (turning) 1.5f else 1f
    // Samples spread so the bounded trail holds the longest live point's whole life and fade at this speed:
    // at most MAX_TRAIL_POINTS samples in that time, so the oldest burning point is never evicted early.
    var longestLife = life
    for (index in trail.indices) longestLife = max(longestLife, trail[index].life)
    val spacing = max(FLUX_SPACING, speed * (longestLife + TRAIL_FADE_SECONDS) / MutableGameState.MAX_TRAIL_POINTS)
    var sampleDistance = spacing - trailDistanceCarry
    var samples = 0
    while (sampleDistance <= distance && samples < MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE) {
        val amount = sampleDistance / distance
        trail += TrailPoint(lerp(trailLastX, coreX, amount), lerp(trailLastY, coreY, amount), life = life, scale = scale)
        sampleDistance += spacing
        samples++
    }
    trailDistanceCarry = (trailDistanceCarry + distance) % spacing
    while (trail.size > MutableGameState.MAX_TRAIL_POINTS) trail.removeAt(0)
    trailLastX = coreX
    trailLastY = coreY
    if (agonyRank > 0) agonyMutationCounts[WeaponId.FLUX_WAKE.ordinal]++
}

/** Prism signature: damage and size per intact facet. */
internal fun MutableGameState.morningstarScale(): Float = if (signatureActive()) 1f + 0.1f * intactFacets else 1f

internal fun MutableGameState.morningstarOrbitRadius(): Float =
    (104f + min(70f, softVelocity(speed) * 0.055f) + agonyPower() * 4f) * if (hasMasteryForm(1)) 1.15f else 1f

private fun MutableGameState.updateMorningstar(delta: Float, agonyRank: Int) {
    val angularSpeed = (3.2f + softVelocity(speed) / 135f + agonyRank * 0.12f) * continuousWeaponRate()
    morningstarAngle = (morningstarAngle + angularSpeed * delta) % TAU
    val radius = morningstarOrbitRadius()
    previousMorningstarX = morningstarX
    previousMorningstarY = morningstarY
    morningstarX = coreX + cos(morningstarAngle) * radius
    morningstarY = coreY + sin(morningstarAngle) * radius
    if (agonyRank > 0) agonyMutationCounts[WeaponId.MORNINGSTAR.ordinal]++
}

private fun MutableGameState.fireNullLance(power: Float, agonyRank: Int) {
    weaponClock = cooldown(1.15f)
    val serial = beginWeaponActivation()
    val angle = aimedMovementAngle()
    val lanceSpeed = 940f + softVelocity(speed) * 0.22f
    val pierce = NULL_LANCE_PIERCE + if (hasMasteryForm(1)) 3 else 0
    firePlayerProjectile(coreX, coreY, angle, lanceSpeed, NULL_LANCE_DAMAGE * power, pierce, 6f, 0, serial)
    if (hasMasteryForm(2)) firePlayerProjectile(coreX, coreY, angle + TAU * 0.5f, lanceSpeed * 0.9f, NULL_LANCE_DAMAGE * NULL_LANCE_REAR_SHARE * power, 3, 5f, 0, serial)
    if (agonyRank > 0) {
        firePlayerProjectile(coreX, coreY, angle + TAU * 0.5f + 0.15f, 820f, 18f * agonyRank * power, 2 + agonyRank, 5f, 4, serial)
        agonyMutationCounts[WeaponId.NULL_LANCE.ordinal]++
    }
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

private fun MutableGameState.updateGravityMines(agonyRank: Int) {
    val runtime = weaponRuntime
    // Diamond signature: a parry drops a mine that detonates at once, doubled.
    if (signatureActive() && parriedThisStep && elapsed >= runtime.signatureReadyAt) {
        runtime.signatureReadyAt = elapsed + PARRY_TWIST_GUARD
        val mine = WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, 0.001f, 0.001f, gravityMineRadius(agonyRank),
            procKey = beginWeaponActivation(), scale = 2f)
        // It joins the field to detonate this step; a full field (the node bound) sets it off at once instead.
        if (!tryAddWeaponNode(mine)) explodeMine(mine)
    }
    if (weaponClock <= 0f && (braking || speed > 220f)) {
        // The second form keeps more mines in the field: they come sooner.
        weaponClock = cooldown((if (braking) 0.72f else 1.15f) * if (hasMasteryForm(2)) 0.85f else 1f)
        val serial = beginWeaponActivation()
        val mineLife = 0.75f + agonyRank * 0.04f
        if (tryAddWeaponNode(WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, mineLife, mineLife, gravityMineRadius(agonyRank),
                procKey = serial))
        ) {
            if (agonyRank > 0) agonyMutationCounts[WeaponId.GRAVITY_MINES.ordinal]++
            emitSound(GameplayAudioCue.WEAPON_LIGHT)
        }
    }
}

private fun MutableGameState.gravityMineRadius(agonyRank: Int): Float =
    GRAVITY_MINE_RADIUS * (if (hasMasteryForm(1)) 1.3f else 1f) + agonyRank * 12f

private fun MutableGameState.updateIonSwarm(delta: Float, power: Float, agonyRank: Int) {
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

private fun MutableGameState.fireQuasar(power: Float, agonyRank: Int) {
    weaponClock = cooldown(1.65f)
    val serial = beginWeaponActivation()
    val angle = aimedMovementAngle()
    val radius = quasarShotRadius()
    val life = if (hasMasteryForm(3)) QUASAR_BURST_LIFE else 4f
    firePlayerProjectile(coreX, coreY, angle, 690f, QUASAR_DAMAGE * power, 10, radius, 2, serial, life)
    if (hasMasteryForm(2)) {
        firePlayerProjectile(coreX, coreY, angle - 0.16f, 720f, QUASAR_DAMAGE * QUASAR_SIDE_SHARE * power, 3, radius * 0.6f, 2, serial, life)
        firePlayerProjectile(coreX, coreY, angle + 0.16f, 720f, QUASAR_DAMAGE * QUASAR_SIDE_SHARE * power, 3, radius * 0.6f, 2, serial, life)
    }
    if (agonyRank > 0) {
        val sideDamage = 28f * agonyRank * power
        firePlayerProjectile(coreX, coreY, angle - 0.3f, 760f, sideDamage, 3, 7f, 4, serial)
        firePlayerProjectile(coreX, coreY, angle + 0.3f, 760f, sideDamage, 3, 7f, 4, serial)
        agonyMutationCounts[WeaponId.QUASAR_CANNON.ordinal]++
    }
    screenShake = max(screenShake, 4f)
    emitSound(GameplayAudioCue.WEAPON_HEAVY)
}

/** Quasar shot radius: the first form adds 15 %; the Star signature 3 % per Stellar Mass stack. */
internal fun MutableGameState.quasarShotRadius(): Float =
    14f * (if (hasMasteryForm(1)) 1.1f else 1f) * (if (signatureActive()) 1f + 0.03f * stellarMass else 1f)

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
        orbital.previousX = orbital.x
        orbital.previousY = orbital.y
        orbital.x = coreX + cos(angle) * orbitRadius
        orbital.y = coreY + sin(angle) * orbitRadius
        // A freshly formed orbital appears on its orbit instead of sweeping out from the Core.
        if (!orbitalsMatch) {
            orbital.previousX = orbital.x
            orbital.previousY = orbital.y
        }
    }
}

internal fun MutableGameState.tryAddWeaponNode(node: WeaponNode): Boolean {
    val limit = if (hasMasteryForm(2)) MutableGameState.MAX_WEAPON_NODES else BASE_MINE_LIMIT
    if (weaponNodes.size >= limit) return false
    weaponNodes += node
    return true
}

internal fun MutableGameState.updateWeaponNodes(delta: Float) {
    var index = 0
    while (index < weaponNodes.size) {
        val node = weaponNodes[index]
        node.life -= delta
        // An enemy stepping onto a mine sets it off early.
        if (node.life > 0f && enemyWithin(node.x, node.y, node.radius * MINE_PROXIMITY)) node.life = 0f
        if (node.life <= 0f) {
            weaponNodes.removeAt(index)
            explodeMine(node)
        } else {
            index++
        }
    }
}

private fun MutableGameState.enemyWithin(x: Float, y: Float, radius: Float): Boolean {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && !isShifting(enemy) && distanceSquared(x, y, enemy.x, enemy.y) <= square(radius + enemy.radius)) return true
    }
    return false
}

internal fun MutableGameState.explodeMine(node: WeaponNode) {
    val power = effectiveWeaponPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        val distance = length(enemy.x - node.x, enemy.y - node.y)
        if (distance <= node.radius + enemy.radius) {
            val landed = dealWeaponDamage(enemy, MINE_DAMAGE * node.scale * power * (1f - distance / (node.radius * 1.7f)).coerceAtLeast(0.35f),
                canCrit = true, procKey = node.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.GRAVITIC, MINE_MASS_INTERVAL)
            val pull = (1f - distance / max(1f, node.radius)).coerceAtLeast(0f) * 170f * knockbackTaken(enemy)
            enemy.vx += (node.x - enemy.x) / max(1f, distance) * pull
            enemy.vy += (node.y - enemy.y) / max(1f, distance) * pull
        }
    }
    // Second form: a blast sets off nearby mines.
    if (hasMasteryForm(2)) {
        for (index in weaponNodes.indices) {
            val other = weaponNodes[index]
            if (distanceSquared(other.x, other.y, node.x, node.y) <= square(node.radius * MINE_CHAIN_REACH)) other.life = min(other.life, MINE_CHAIN_FUSE)
        }
    }
    // Third form: the mine collapses into a short well, which grows with weapon power like the blast.
    if (hasMasteryForm(3)) {
        addWeaponEntity(WeaponEntity(WeaponEntityKind.WELL, node.x, node.y, node.radius * MINE_WELL_REACH, MINE_WELL_SECONDS, MINE_WELL_SECONDS,
            damage = MINE_WELL_DPS * power, procKey = node.procKey, phase = 1))
    }
    burst(node.x, node.y, 18, 2)
    screenShake = max(screenShake, 5f)
    emitSound(GameplayAudioCue.IMPACT)
}

/**
 * Arc Coil: the nearest enemy within [ARC_COIL_FIRST_RANGE] of the Core, then the nearest unhit enemy within
 * [arcCoilHopRange] of the previous target, for [arcCoilTargetCount] targets. Each hop deals [ARC_COIL_FALLOFF]
 * of the last and adds a Charge; only the first hit fires on-hit effects.
 */
internal fun MutableGameState.fireArcCoil(baseDamage: Float, serial: Int = WeaponRuntime.PROC_ALWAYS) {
    val maximumTargets = arcCoilTargetCount()
    val targets = arrayOfNulls<Enemy>(maximumTargets)
    var targetCount = 0
    var fromX = coreX
    var fromY = coreY
    var range = ARC_COIL_FIRST_RANGE
    while (targetCount < maximumTargets) {
        var nearest: Enemy? = null
        var nearestDistanceSquared = range * range
        for (enemyIndex in enemies.indices) {
            val candidate = enemies[enemyIndex]
            if (candidate.dead || !(candidate.hp > 0f) || isShifting(candidate)) continue
            var alreadySelected = false
            for (selectedIndex in 0 until targetCount) {
                if (targets[selectedIndex] === candidate) {
                    alreadySelected = true
                    break
                }
            }
            if (alreadySelected) continue
            val candidateDistanceSquared = distanceSquared(fromX, fromY, candidate.x, candidate.y)
            if (
                candidateDistanceSquared <= nearestDistanceSquared &&
                (nearest == null || candidateDistanceSquared < nearestDistanceSquared)
            ) {
                nearest = candidate
                nearestDistanceSquared = candidateDistanceSquared
            }
        }
        if (nearest == null) break
        targets[targetCount++] = nearest
        fromX = nearest.x
        fromY = nearest.y
        range = arcCoilHopRange()
    }
    fromX = coreX
    fromY = coreY
    for (index in 0 until targetCount) {
        val enemy = checkNotNull(targets[index])
        val targetX = enemy.x
        val targetY = enemy.y
        val landed = dealWeaponDamage(enemy, baseDamage * powFast(ARC_COIL_FALLOFF, index), canCrit = true, procKey = serial).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.ION, ARC_COIL_CHARGE)
        // Third form: the last hop overloads.
        if (index == targetCount - 1 && hasMasteryForm(3) && enemy.alive) forceElementBreak(enemy, StatusElement.ION)
        addWeaponArc(fromX, fromY, targetX, targetY)
        fromX = targetX
        fromY = targetY
    }
    val agonyRank = agonyPower()
    if (agonyRank > 0 && targetCount > 0) {
        val first = checkNotNull(targets[0])
        dealWeaponDamage(first, baseDamage * 0.18f * agonyRank, canCrit = true, procKey = serial)
        addRelicArc(fromX, fromY, first.x, first.y)
        agonyMutationCounts[WeaponId.ARC_COIL.ordinal]++
    }
}

/** Hop range of Arc Coil's chain; its second form adds 100. */
internal fun MutableGameState.arcCoilHopRange(): Float = ARC_COIL_HOP_RANGE + if (hasMasteryForm(2)) 100f else 0f

/** Upper bound of [arcCoilTargetCount]: four, plus two on a Pentagon's beat. */
internal const val MAX_ARC_COIL_TARGETS: Int = 6
internal const val ARC_COIL_FIRST_RANGE: Float = 380f
internal const val ARC_COIL_HOP_RANGE: Float = 220f
internal const val ARC_COIL_FALLOFF: Float = 0.9f

internal fun MutableGameState.fireSingularitySpear(damage: Float, serial: Int) {
    val angle = aimedMovementAngle()
    val length = (900f + softVelocity(speed) * 0.18f) * if (hasMasteryForm(1)) 1.25f else 1f
    weaponBeamStartX = coreX
    weaponBeamStartY = coreY
    weaponBeamEndX = coreX + cos(angle) * length
    weaponBeamEndY = coreY + sin(angle) * length
    weaponBeamTime = 0.18f
    spearLine(weaponBeamStartX, weaponBeamStartY, weaponBeamEndX, weaponBeamEndY, SPEAR_REACH, damage, serial)
    if (hasMasteryForm(2)) {
        // The cross beam runs both ways across the spear.
        val crossX = -sin(angle) * length * 0.72f
        val crossY = cos(angle) * length * 0.72f
        spearLine(coreX - crossX, coreY - crossY, coreX + crossX, coreY + crossY, 12f, damage * SPEAR_CROSS_SHARE, serial)
        addWeaponArc(coreX - crossX, coreY - crossY, coreX + crossX, coreY + crossY, 0.18f)
    }
    if (hasMasteryForm(3)) {
        addWeaponEntity(WeaponEntity(WeaponEntityKind.RIFT, weaponBeamStartX, weaponBeamStartY, SPEAR_SCAR_REACH, SPEAR_SCAR_FUSE + 0.2f,
            SPEAR_SCAR_FUSE + 0.2f, damage = damage * SPEAR_SCAR_SHARE, endX = weaponBeamEndX, endY = weaponBeamEndY, timer = SPEAR_SCAR_FUSE,
            procKey = serial, phase = RIFT_SPEAR_SCAR))
    }
    val agonyRank = agonyPower()
    if (agonyRank > 0) {
        val crossAngle = angle - TAU * 0.25f
        val crossEndX = coreX + cos(crossAngle) * length * 0.72f
        val crossEndY = coreY + sin(crossAngle) * length * 0.72f
        spearLine(coreX, coreY, crossEndX, crossEndY, 12f, damage * 0.28f * agonyRank, serial)
        addWeaponArc(coreX, coreY, crossEndX, crossEndY, 0.18f)
        agonyMutationCounts[WeaponId.SINGULARITY_SPEAR.ordinal]++
    }
    screenShake = max(screenShake, 7f)
    emitSound(GameplayAudioCue.WEAPON_HEAVY)
}

private fun MutableGameState.fireBlinkSpear(damage: Float, serial: Int) {
    val dx = blinkToX - blinkFromX
    val dy = blinkToY - blinkFromY
    val distance = max(1f, length(dx, dy))
    val endX = blinkToX + dx / distance * SPEAR_BLINK_REACH
    val endY = blinkToY + dy / distance * SPEAR_BLINK_REACH
    spearLine(blinkFromX, blinkFromY, endX, endY, 15f, damage, serial)
    weaponBeamStartX = blinkFromX
    weaponBeamStartY = blinkFromY
    weaponBeamEndX = endX
    weaponBeamEndY = endY
    weaponBeamTime = 0.18f
    emitSound(GameplayAudioCue.WEAPON_HEAVY)
}

private fun MutableGameState.spearLine(x0: Float, y0: Float, x1: Float, y1: Float, reach: Float, damage: Float, serial: Int) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + reach)) {
            val landed = dealWeaponDamage(enemy, damage, canCrit = true, procKey = serial).amount > 0f
            if (landed) applyWeaponStatus(enemy, StatusElement.VOID, 2)
        }
    }
}

internal fun MutableGameState.firePrismRelay(power: Float, serial: Int = WeaponRuntime.PROC_ALWAYS) {
    val target = nearestEnemy(coreX, coreY, 820f) ?: return
    val baseAngle = atan2(target.y - coreY, target.x - coreX)
    val agonyRank = agonyPower()
    val relayCount = prismRelayCount()
    val bounces = prismRelayBounces()
    repeat(relayCount) { index ->
        val offset = (index - (relayCount - 1) * 0.5f) * 0.075f
        // The second form's twin (and Agony's extra relay) carry half a relay's damage.
        val share = if (index == 0) 1f else PRISM_TWIN_SHARE
        firePlayerProjectile(coreX, coreY, baseAngle + offset, 760f, PRISM_RELAY_DAMAGE * share * power, bounces, 6f, 3, serial)
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.PRISM_RELAY.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

// ---------------------------------------------------------------------------------------------
// The twelve additions.

private fun MutableGameState.throwGyreDiscs(power: Float, agonyRank: Int) {
    weaponClock = cooldown(GYRE_COOLDOWN)
    val serial = beginWeaponActivation()
    val angle = atan2(lastAimDirectionY, lastAimDirectionX)
    val count = 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyRank > 0) 1 else 0
    val damage = GYRE_DAMAGE * power * (1f + 0.1f * agonyRank)
    val radius = GYRE_RADIUS * if (hasMasteryForm(1)) 1.15f else 1f
    for (disc in 0 until count) {
        val discAngle = angle + (disc - (count - 1) * 0.5f) * GYRE_SPREAD
        // The second form's twin (and Agony's extra disc) carry 60 % of a disc's damage.
        val share = if (disc == 0) 1f else GYRE_EXTRA_SHARE
        addWeaponEntity(WeaponEntity(WeaponEntityKind.DISC, coreX, coreY, radius, GYRE_MAX_LIFE, GYRE_MAX_LIFE,
            vx = cos(discAngle) * GYRE_SPEED, vy = sin(discAngle) * GYRE_SPEED, damage = damage * share, angle = discAngle,
            extent = GYRE_REACH, procKey = serial))
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.GYRE_DISC.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

private fun MutableGameState.updateScatterBloom(delta: Float, power: Float, agonyRank: Int, dashed: Boolean) {
    val runtime = weaponRuntime
    runtime.passiveClock -= delta
    val pellets = SCATTER_PELLETS + (if (hasMasteryForm(1)) 2 else 0) + if (agonyRank > 0) 2 else 0
    val damage = SCATTER_DAMAGE * power * (1f + 0.1f * agonyRank)
    if (dashed) {
        // The bloom opens toward the nearest enemy in reach, else along the Dash.
        val near = nearestEnemy(coreX, coreY, SCATTER_RANGE)
        val angle = when {
            near != null -> atan2(near.y - coreY, near.x - coreX)
            speed > 20f -> atan2(velocityY, velocityX)
            else -> atan2(lastAimDirectionY, lastAimDirectionX)
        }
        // A Dash is no cooldown: attack speed raises the bloom's damage instead.
        fireScatterCone(angle, pellets, damage * continuousWeaponRate(), beginWeaponActivation())
        if (agonyRank > 0) agonyMutationCounts[WeaponId.SCATTER_BLOOM.ordinal]++
    }
    if (runtime.passiveClock <= 0f) {
        runtime.passiveClock = cooldown(SCATTER_PASSIVE_INTERVAL)
        val target = nearestEnemy(coreX, coreY, SCATTER_RANGE)
        val angle = if (target != null) atan2(target.y - coreY, target.x - coreX) else atan2(lastAimDirectionY, lastAimDirectionX)
        val volley = SCATTER_PASSIVE_PELLETS + if (agonyRank > 0) 2 else 0
        fireScatterCone(angle, volley, damage, beginWeaponActivation(), spread = SCATTER_PASSIVE_SPREAD)
        if (agonyRank > 0) agonyMutationCounts[WeaponId.SCATTER_BLOOM.ordinal]++
    }
}

private fun MutableGameState.fireScatterCone(angle: Float, pellets: Int, damage: Float, serial: Int, spread: Float = SCATTER_CONE) {
    val pierce = SCATTER_PIERCE + if (hasMasteryForm(2)) 1 else 0
    for (pellet in 0 until pellets) {
        val offset = if (pellets <= 1) 0f else (pellet / (pellets - 1f) - 0.5f) * spread
        firePlayerProjectile(coreX, coreY, angle + offset, SCATTER_SPEED, damage, pierce, 4f, 0, serial,
            life = SCATTER_RANGE / SCATTER_SPEED)
    }
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

private fun MutableGameState.updateTethers(delta: Float, agonyRank: Int) {
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
    if (tether.targetId != target.id) {
        tether.targetId = target.id
        tether.x = target.x
        tether.y = target.y
        tether.settle()
    }
    tether.x = target.x
    tether.y = target.y
    tether.endX = coreX
    tether.endY = coreY
}

private fun MutableGameState.deploySentry(power: Float, agonyRank: Int) {
    weaponClock = cooldown(SENTRY_INTERVAL)
    onWeaponActivation()
    var x = coreX
    var y = coreY
    // Tesseract signature: sentries take the newest lattice node.
    val nodes = latticeNodeCount
    if (signatureActive() && nodes > 0) {
        x = latticeNodeX(nodes - 1)
        y = latticeNodeY(nodes - 1)
    }
    val limit = SENTRY_LIMIT + (if (hasMasteryForm(1)) 1 else 0) + if (agonyRank > 0) 1 else 0
    val entities = weaponRuntime.entities
    var live = 0
    var oldest: WeaponEntity? = null
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind != WeaponEntityKind.SENTRY || entity.life <= 0f) continue
        live++
        if (oldest == null || entity.life < oldest.life) oldest = entity
    }
    // At the limit the oldest sentry folds (no expiry blast) to make room.
    if (live >= limit) oldest?.let { it.life = 0f; it.phase = SENTRY_FOLDED }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.SENTRY, x, y, SENTRY_RADIUS, SENTRY_LIFE, SENTRY_LIFE,
        damage = SENTRY_DAMAGE * power * (1f + 0.1f * agonyRank), timer = 0f, procKey = WeaponRuntime.sourceKey(SENTRY_SOURCE)))
    if (agonyRank > 0) agonyMutationCounts[WeaponId.SENTRY_GRID.ordinal]++
    shockwave(x, y, 0.25f, 60f, 0)
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

private fun MutableGameState.launchSalvo(power: Float, agonyRank: Int) {
    weaponClock = cooldown(HUNTER_COOLDOWN)
    val serial = beginWeaponActivation()
    val missiles = HUNTER_MISSILES + (if (hasMasteryForm(1)) 1 else 0) + if (agonyRank > 0) 1 else 0
    val damage = HUNTER_DAMAGE * power * (1f + 0.1f * agonyRank)
    val heading = movementAngle()
    var previousTarget = -1
    for (missile in 0 until missiles) {
        val angle = heading + TAU * 0.5f + (missile - (missiles - 1) * 0.5f) * 0.55f
        launchMissile(coreX, coreY, angle, damage, serial, split = true)
        // Missiles of one salvo seek different enemies when there are enough.
        val target = nearestOtherEnemy(coreX, coreY, previousTarget, HUNTER_RANGE) ?: nearestEnemy(coreX, coreY, HUNTER_RANGE)
        if (target != null) {
            weaponRuntime.entities.lastOrNull()?.takeIf { it.kind == WeaponEntityKind.MISSILE }?.targetId = target.id
            previousTarget = target.id
        }
    }
    // Hexagon signature: up to three drones each launch one.
    if (signatureActive()) {
        for (drone in 0 until min(droneCount, 3)) launchMissile(droneX(drone), droneY(drone), heading, damage, serial, split = true)
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.HUNTER_SALVO.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.launchMissile(x: Float, y: Float, angle: Float, damage: Float, procKey: Int, split: Boolean) {
    addWeaponEntity(WeaponEntity(WeaponEntityKind.MISSILE, x, y, HUNTER_MISSILE_RADIUS, HUNTER_MISSILE_LIFE, HUNTER_MISSILE_LIFE,
        vx = cos(angle) * HUNTER_LAUNCH_SPEED, vy = sin(angle) * HUNTER_LAUNCH_SPEED, damage = damage, angle = angle,
        procKey = procKey, phase = if (split) 0 else 1))
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
    var hits = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, damage, canCrit = true, procKey = serial).amount > 0f
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
private fun MutableGameState.updateConcussionGhost(power: Float, agonyRank: Int) {
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

private fun MutableGameState.updateGlacierBell(delta: Float, power: Float, agonyRank: Int) {
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

private fun MutableGameState.castLenses(power: Float, agonyRank: Int) {
    weaponClock = cooldown(LENS_COOLDOWN)
    val serial = beginWeaponActivation()
    val angle = atan2(lastAimDirectionY, lastAimDirectionX)
    val count = 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyRank > 0) 1 else 0
    for (lens in 0 until count) {
        val lensAngle = angle + (lens - (count - 1) * 0.5f) * LENS_SPREAD
        addWeaponEntity(WeaponEntity(WeaponEntityKind.LENS, coreX, coreY, LENS_RADIUS, LENS_FUSE, LENS_FUSE,
            vx = cos(lensAngle) * LENS_SPEED, vy = sin(lensAngle) * LENS_SPEED,
            damage = power * (1f + 0.1f * agonyRank) * if (lens == 0) 1f else LENS_EXTRA_SHARE,
            angle = lensAngle, procKey = serial))
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.EVENT_LENS.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.lensWellRadius(): Float = LENS_WELL_RADIUS * if (hasMasteryForm(1)) 1.1f else 1f

private fun MutableGameState.updateSolarBeams(delta: Float, agonyRank: Int) {
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

private fun MutableGameState.updateAegisShards(delta: Float, power: Float, agonyRank: Int) {
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

private fun MutableGameState.updateHollowRifts(delta: Float, power: Float, agonyRank: Int, dashed: Boolean) {
    val runtime = weaponRuntime
    val damage = HOLLOW_DAMAGE * power * (1f + 0.1f * agonyRank)
    val reach = HOLLOW_REACH * if (hasMasteryForm(1)) 1.3f else 1f
    val entities = runtime.entities
    // A rift laid by the current Dash stretches with the Core until the Dash ends.
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind == WeaponEntityKind.RIFT && entity.phase == RIFT_GROWING) {
            if (dashPhaseTime > 0f) {
                entity.endX = coreX
                entity.endY = coreY
            } else {
                entity.phase = RIFT_ARMED
            }
        }
    }
    if (dashed) {
        val blink = blinkThisStep
        val startX = if (blink) blinkFromX else coreX
        val startY = if (blink) blinkFromY else coreY
        // Crescent signature: Blink scars become full rifts, at the strength of the Crescent's own scar.
        val scale = if (blink && signatureActive()) HOLLOW_BLINK_SCALE else 1f
        addWeaponEntity(WeaponEntity(WeaponEntityKind.RIFT, startX, startY, reach, HOLLOW_FUSE + HOLLOW_LINGER + DASH_PHASE_SECONDS,
            HOLLOW_FUSE + HOLLOW_LINGER + DASH_PHASE_SECONDS, damage = damage * scale * continuousWeaponRate(), endX = coreX, endY = coreY,
            timer = HOLLOW_FUSE + if (blink) 0f else DASH_PHASE_SECONDS, procKey = beginWeaponActivation(),
            phase = if (blink) RIFT_ARMED else RIFT_GROWING))
        if (agonyRank > 0) agonyMutationCounts[WeaponId.HOLLOW_RIFT.ordinal]++
    }
    runtime.passiveClock -= delta
    if (runtime.passiveClock <= 0f && speed >= HOLLOW_PASSIVE_SPEED) {
        runtime.passiveClock = cooldown(HOLLOW_PASSIVE_INTERVAL)
        val back = 0.15f
        addWeaponEntity(WeaponEntity(WeaponEntityKind.RIFT, coreX - velocityX * back, coreY - velocityY * back, reach,
            HOLLOW_FUSE + HOLLOW_LINGER, HOLLOW_FUSE + HOLLOW_LINGER, damage = damage * HOLLOW_PASSIVE_SHARE, endX = coreX, endY = coreY,
            timer = HOLLOW_FUSE, procKey = beginWeaponActivation(), phase = RIFT_ARMED))
        if (agonyRank > 0) agonyMutationCounts[WeaponId.HOLLOW_RIFT.ordinal]++
    }
}

private fun MutableGameState.updateResonanceDrum(power: Float, agonyRank: Int) {
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

internal fun MutableGameState.enemyById(id: Int): Enemy? {
    if (id < 0) return null
    for (index in enemies.indices) if (enemies[index].id == id) return enemies[index]
    return null
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

// ---------------------------------------------------------------------------------------------
// Tuning (weapon power 1).

internal const val AIM_ASSIST_CONE: Float = 0.5f
internal const val AIM_ASSIST_RANGE: Float = 700f
internal const val NULL_LANCE_DAMAGE: Float = 68f
internal const val NULL_LANCE_PIERCE: Int = 5
internal const val NULL_LANCE_REAR_SHARE: Float = 0.3f
internal const val MINE_DAMAGE: Float = 68f
internal const val MINE_PROXIMITY: Float = 0.45f
internal const val GRAVITY_MINE_RADIUS: Float = 100f
internal const val MINE_MASS_INTERVAL: Float = 1.5f
internal const val BASE_MINE_LIMIT: Int = 8
internal const val MINE_CHAIN_FUSE: Float = 0.05f
internal const val MINE_CHAIN_REACH: Float = 0.9f
internal const val MINE_WELL_SECONDS: Float = 1.2f
internal const val MINE_WELL_DPS: Float = 15f
internal const val MINE_WELL_REACH: Float = 0.7f
internal const val ION_SWARM_DAMAGE: Float = 8.8f
internal const val ION_SWARM_COOLDOWN: Float = 0.72f
internal const val ION_SWARM_PIERCE: Int = 1
internal const val ION_SWARM_FIRE_RATE_FORM: Float = 1.25f
internal const val ARC_COIL_DAMAGE: Float = 16f
internal const val ARC_COIL_COOLDOWN: Float = 1.15f
internal const val ARC_COIL_CHARGE: Int = 1
internal const val QUASAR_DAMAGE: Float = 87f
internal const val QUASAR_BURST_LIFE: Float = 1.3f
internal const val QUASAR_SIDE_SHARE: Float = 0.1f
internal const val QUASAR_BURST_SHARE: Float = 0.15f
internal const val QUASAR_BURST_RADIUS: Float = 120f
internal const val SPEAR_DAMAGE: Float = 100f
internal const val SPEAR_BLINK_COOLDOWN: Float = 2f
internal const val SPEAR_CROSS_SHARE: Float = 1f
internal const val SPEAR_REACH: Float = 28f
internal const val SPEAR_SCAR_SHARE: Float = 1f
internal const val SPEAR_BLINK_REACH: Float = 320f
internal const val SPEAR_SCAR_FUSE: Float = 0.4f
internal const val SPEAR_SCAR_REACH: Float = 30f
internal const val PRISM_RELAY_DAMAGE: Float = 18f
internal const val PRISM_TWIN_SHARE: Float = 0.5f
/** Heading change per dp travelled that counts as a hard turn (60° over 100 dp). */
internal const val FLUX_TURN_CURVATURE: Float = 0.0105f
internal const val FLUX_LONG_LIFE: Float = 4f
internal const val FLUX_SPACING: Float = 22f
internal const val PARRY_TWIST_GUARD: Float = 0.2f

internal const val GYRE_DAMAGE: Float = 45f
internal const val GYRE_COOLDOWN: Float = 1.3f
internal const val GYRE_SPEED: Float = 760f
internal const val GYRE_RETURN_SPEED: Float = 900f
internal const val GYRE_REACH: Float = 420f
internal const val GYRE_RADIUS: Float = 16f
internal const val GYRE_SPREAD: Float = 0.45f
internal const val GYRE_MAX_LIFE: Float = 3f
internal const val GYRE_ORBIT_BONUS: Float = 1.25f
internal const val GYRE_RETURN_BONUS: Float = 1.5f
internal const val GYRE_EXTRA_SHARE: Float = 0.6f

internal const val SCATTER_DAMAGE: Float = 10f
internal const val SCATTER_PIERCE: Int = 2
internal const val SCATTER_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_INTERVAL: Float = 2.4f
internal const val SCATTER_RANGE: Float = 380f
internal const val SCATTER_SPEED: Float = 950f
internal const val SCATTER_CONE: Float = TAU / 6f
internal const val SCATTER_POINT_BLANK: Float = 140f
internal const val SCATTER_PASSIVE_SPREAD: Float = 0.9f

internal const val TETHER_RANGE: Float = 320f
internal const val TETHER_DPS: Float = 5.5f
internal const val TETHER_RAMP_MAX: Float = 3f
internal const val TETHER_RAMP_FORM: Float = 5f
internal const val TETHER_RAMP_SECONDS: Float = 2.5f
internal const val TETHER_KILL_KEEP: Float = 0.6f
internal const val TETHER_SECOND_SHARE: Float = 0.75f
internal const val TETHER_PULL: Float = 260f

internal const val SENTRY_INTERVAL: Float = 3f
internal const val SENTRY_LIMIT: Int = 3
internal const val SENTRY_LIFE: Float = 10f
internal const val SENTRY_DAMAGE: Float = 12f
internal const val SENTRY_FIRE_INTERVAL: Float = 0.6f
internal const val SENTRY_RANGE: Float = 520f
internal const val SENTRY_RADIUS: Float = 12f
internal const val SENTRY_SOURCE: Int = 2
internal const val SENTRY_FOLDED: Int = 1
internal const val SENTRY_BLAST_RADIUS: Float = 120f
internal const val SENTRY_BLAST_DAMAGE: Float = 40f

internal const val HUNTER_DAMAGE: Float = 9f
internal const val HUNTER_DECAY_EVERY: Int = 2
internal const val HUNTER_COOLDOWN: Float = 2.2f
internal const val HUNTER_MISSILES: Int = 3
internal const val HUNTER_RANGE: Float = 700f
internal const val HUNTER_LAUNCH_SPEED: Float = 420f
internal const val HUNTER_TOP_SPEED: Float = 780f
internal const val HUNTER_TURN_RATE: Float = 7f
internal const val HUNTER_MISSILE_RADIUS: Float = 7f
internal const val HUNTER_MISSILE_LIFE: Float = 2.6f
internal const val HUNTER_BURST_RADIUS: Float = 110f
internal const val HUNTER_BURST_SHARE: Float = 0.75f
internal const val HUNTER_BURST_FORM: Float = 1.2f

internal const val CONCUSSION_MIN_SPEED: Float = 300f
internal const val CONCUSSION_COOLDOWN: Float = 0.35f
internal const val CONCUSSION_DAMAGE: Float = 24f
internal const val CONCUSSION_IMPACT_SHARE: Float = 0.25f
internal const val CONCUSSION_BASE_RADIUS: Float = 90f
internal const val CONCUSSION_MAX_RADIUS: Float = 260f
internal const val CONCUSSION_KNOCKBACK: Float = 260f
internal const val CONCUSSION_SHIELD_PER_HIT: Float = 4f
internal const val CONCUSSION_GHOST_GATE: Float = 0.4f

internal const val GLACIER_DAMAGE: Float = 37f
internal const val GLACIER_RADIUS: Float = 240f
internal const val GLACIER_HOLD_SECONDS: Float = 0.3f
internal const val GLACIER_TOLL_COOLDOWN: Float = 0.8f
internal const val GLACIER_PASSIVE_INTERVAL: Float = 3.5f
internal const val GLACIER_PASSIVE_RADIUS: Float = 170f
internal const val GLACIER_PASSIVE_SHARE: Float = 0.6f
internal const val GLACIER_SECOND_TOLL: Float = 0.5f

internal const val LENS_COOLDOWN: Float = 2.8f
internal const val LENS_SPEED: Float = 380f
internal const val LENS_FUSE: Float = 0.9f
internal const val LENS_RADIUS: Float = 14f
internal const val LENS_SPREAD: Float = 0.7f
internal const val LENS_EXTRA_SHARE: Float = 0.25f
internal const val LENS_WELL_RADIUS: Float = 170f
internal const val LENS_WELL_SECONDS: Float = 1.6f
internal const val LENS_WELL_DPS: Float = 15f
internal const val LENS_COLLAPSE_DAMAGE: Float = 32f
internal const val LENS_IMPLOSION_DAMAGE: Float = 10f
internal const val LENS_PULL: Float = 240f

internal const val SOLAR_LENGTH: Float = 240f
internal const val SOLAR_LENGTH_FORM: Float = 120f
internal const val SOLAR_ANGULAR_SPEED: Float = 2.2f
internal const val SOLAR_DPS: Float = 44f
internal const val SOLAR_ARC_SECTOR: Float = 0.4f
internal const val SOLAR_ARC_SHARE: Float = 0.7f

internal const val AEGIS_SHIELD_CAPACITY: Float = 15f
internal const val AEGIS_SHARDS_BASE: Int = 2
internal const val AEGIS_PIERCE: Int = 1
internal const val AEGIS_DAMAGE: Float = 34f
internal const val AEGIS_COOLDOWN: Float = 1.4f
internal const val AEGIS_RANGE: Float = 260f
internal const val AEGIS_BREAK_RANGE: Float = 420f
internal const val AEGIS_ORBIT_RADIUS: Float = 52f
internal const val AEGIS_ORBIT_SPEED: Float = 3.4f
internal const val AEGIS_SHARD_RADIUS: Float = 9f
internal const val AEGIS_SPEED: Float = 820f
internal const val AEGIS_SHARD_LIFE: Float = 0.9f
internal const val AEGIS_RESTORE_COOLDOWN: Float = 60f

internal const val HOLLOW_DAMAGE: Float = 54f
internal const val HOLLOW_REACH: Float = 40f
internal const val HOLLOW_FUSE: Float = 0.55f
internal const val HOLLOW_LINGER: Float = 0.45f
internal const val HOLLOW_PASSIVE_SPEED: Float = 600f
internal const val HOLLOW_PASSIVE_INTERVAL: Float = 2f
internal const val HOLLOW_PASSIVE_SHARE: Float = 30f / 70f
internal const val HOLLOW_BLINK_SCALE: Float = 2f
internal const val HOLLOW_SECOND_SHARE: Float = 0.6f
internal const val HOLLOW_SECOND_DELAY: Float = 0.3f
internal const val HOLLOW_PULL_RADIUS: Float = 120f

internal const val DRUM_DAMAGE: Float = 30f
internal const val DRUM_COOLDOWN: Float = 1.6f
internal const val DRUM_RADIUS: Float = 210f
internal const val DRUM_KNOCKBACK: Float = 240f
internal const val DRUM_BEAT_SCALE: Float = 1.5f
internal const val DRUM_DOUBLE_BEAT: Float = 0.25f
internal const val DRUM_DOUBLE_SHARE: Float = 0.6f

/** Ring variants ([WeaponEntity.phase] of a RING). */
internal const val RING_TOLL: Int = 0
internal const val RING_DRUM: Int = 1
internal const val RING_CONCUSSION: Int = 2
internal const val RING_PASSIVE: Int = 3
internal const val RING_SECONDS: Float = 0.35f

/** Rift steps ([WeaponEntity.phase] of a RIFT). */
internal const val RIFT_GROWING: Int = 0
internal const val RIFT_ARMED: Int = 1
internal const val RIFT_DETONATED: Int = 2
internal const val RIFT_SPEAR_SCAR: Int = 3
