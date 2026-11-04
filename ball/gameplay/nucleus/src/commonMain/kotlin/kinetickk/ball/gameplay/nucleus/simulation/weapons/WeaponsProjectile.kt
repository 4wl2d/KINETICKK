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
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

internal fun MutableGameState.fireNullLance(power: Float, agonyRank: Int) {
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

internal fun MutableGameState.fireQuasar(power: Float, agonyRank: Int) {
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

internal fun MutableGameState.fireBlinkSpear(damage: Float, serial: Int) {
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
        // The second form's twin (and Agony's extra relay) carry [PRISM_TWIN_SHARE] of a relay's damage.
        val share = if (index == 0) 1f else PRISM_TWIN_SHARE
        firePlayerProjectile(coreX, coreY, baseAngle + offset, 760f, PRISM_RELAY_DAMAGE * share * power, bounces, 6f, 3, serial)
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.PRISM_RELAY.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.throwGyreDiscs(power: Float, agonyRank: Int) {
    weaponClock = cooldown(GYRE_COOLDOWN)
    val serial = beginWeaponActivation()
    val angle = atan2(lastAimDirectionY, lastAimDirectionX)
    val count = 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyRank > 0) 1 else 0
    val damage = GYRE_DAMAGE * power * (1f + 0.1f * agonyRank)
    val radius = GYRE_RADIUS * if (hasMasteryForm(1)) GYRE_RADIUS_FORM else 1f
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

internal fun MutableGameState.updateScatterBloom(delta: Float, power: Float, agonyRank: Int, dashed: Boolean) {
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

internal fun MutableGameState.launchSalvo(power: Float, agonyRank: Int) {
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
