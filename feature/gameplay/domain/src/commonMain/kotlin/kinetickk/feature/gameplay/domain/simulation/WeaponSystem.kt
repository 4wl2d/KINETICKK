// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.core.content.*

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.core.audio.api.AudioCue
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


internal fun MutableGameState.updateWeapons(delta: Float) {
    weaponClock -= delta
    weaponSecondaryClock -= delta
    val power = effectiveWeaponPower()
    val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
    when (weapon) {
        WeaponId.FLUX_WAKE -> sampleFluxTrail()
        WeaponId.MORNINGSTAR -> {
            val angularSpeed = 3.2f + softVelocity(speed) / 135f + agonyRank * 0.12f
            morningstarAngle = (morningstarAngle + angularSpeed * delta) % TAU
            val radius = 104f + min(70f, softVelocity(speed) * 0.055f) + agonyRank * 4f
            morningstarX = coreX + cos(morningstarAngle) * radius
            morningstarY = coreY + sin(morningstarAngle) * radius
            if (agonyRank > 0) agonyMutationCounts[WeaponId.MORNINGSTAR.ordinal]++
        }
        WeaponId.PHASE_LATTICE -> if (agonyRank > 0) agonyMutationCounts[WeaponId.PHASE_LATTICE.ordinal]++
        WeaponId.NULL_LANCE -> {
            if (weaponClock <= 0f) {
                weaponClock = cooldown(1.15f)
                val angle = movementAngle()
                firePlayerProjectile(coreX, coreY, angle, 940f + softVelocity(speed) * 0.22f, 56f * power, 5, 6f, 0)
                if (agonyRank > 0) {
                    firePlayerProjectile(coreX, coreY, angle + TAU * 0.5f, 820f, 18f * agonyRank * power, 2 + agonyRank, 5f, 4)
                    agonyMutationCounts[WeaponId.NULL_LANCE.ordinal]++
                }
                emitSound(AudioCue.WEAPON_LIGHT)
            }
        }
        WeaponId.GRAVITY_MINES -> {
            if (weaponClock <= 0f && (braking || speed > 220f)) {
                weaponClock = cooldown(if (braking) 0.72f else 1.15f)
                if (weaponNodes.count { it.type == WeaponNodeType.GRAVITY_MINE } < 8) {
                    val mineLife = 0.75f + agonyRank * 0.04f
                    weaponNodes += WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, mineLife, mineLife, 96f + agonyRank * 12f)
                    if (agonyRank > 0) agonyMutationCounts[WeaponId.GRAVITY_MINES.ordinal]++
                    emitSound(AudioCue.WEAPON_LIGHT)
                }
            }
        }
        WeaponId.ION_SWARM -> {
            val orbitalCount = min(8, 2 + (weaponLevel - 1) / 3 + if (agonyRank > 0) 1 + agonyRank / 2 else 0)
            ensureOrbitals(orbitalCount, 145f + agonyRank * 3f, 8f, delta, 1.8f + agonyRank * 0.05f)
            if (agonyRank > 0) agonyMutationCounts[WeaponId.ION_SWARM.ordinal]++
            if (weaponClock <= 0f && enemies.isNotEmpty()) {
                weaponClock = cooldown(0.72f)
                weaponOrbitals.forEach { orbital ->
                    nearestEnemy(orbital.x, orbital.y, 620f)?.let { enemy ->
                        val angle = atan2(enemy.y - orbital.y, enemy.x - orbital.x)
                        firePlayerProjectile(orbital.x, orbital.y, angle, 620f, 15f * power, 0, 4f, 1)
                    }
                }
                emitSound(AudioCue.WEAPON_LIGHT)
            }
        }
        WeaponId.RIFT_BLADES -> {
            val bladeCount = min(8, 2 + (weaponLevel - 1) / 3 + if (agonyRank > 0) 1 + agonyRank / 2 else 0)
            ensureOrbitals(bladeCount, 82f + min(75f, softVelocity(speed) * 0.025f) + agonyRank * 5f, 17f, delta, 4.2f + agonyRank * 0.08f)
            if (agonyRank > 0) agonyMutationCounts[WeaponId.RIFT_BLADES.ordinal]++
        }
        WeaponId.ARC_COIL -> {
            if (weaponClock <= 0f && enemies.isNotEmpty()) {
                weaponClock = cooldown(0.88f)
                fireArcCoil(35f * power)
                emitSound(AudioCue.WEAPON_LIGHT)
            }
        }
        WeaponId.QUASAR_CANNON -> {
            if (weaponClock <= 0f) {
                weaponClock = cooldown(1.65f)
                val angle = movementAngle()
                firePlayerProjectile(coreX, coreY, angle, 690f, 135f * power, 10, 14f, 2)
                if (agonyRank > 0) {
                    val sideDamage = 28f * agonyRank * power
                    firePlayerProjectile(coreX, coreY, angle - 0.16f, 760f, sideDamage, 3, 7f, 4)
                    firePlayerProjectile(coreX, coreY, angle + 0.16f, 760f, sideDamage, 3, 7f, 4)
                    agonyMutationCounts[WeaponId.QUASAR_CANNON.ordinal]++
                }
                screenShake = max(screenShake, 4f)
                emitSound(AudioCue.WEAPON_HEAVY)
            }
        }
        WeaponId.ENTROPY_FIELD -> if (agonyRank > 0) agonyMutationCounts[WeaponId.ENTROPY_FIELD.ordinal]++
        WeaponId.SINGULARITY_SPEAR -> {
            if (weaponClock <= 0f) {
                weaponClock = cooldown(if (overdriveTime > 0f) 0.9f else 3.2f)
                fireSingularitySpear(185f * power)
            }
        }
        WeaponId.PRISM_RELAY -> {
            if (weaponClock <= 0f && enemies.isNotEmpty()) {
                weaponClock = cooldown(0.92f)
                firePrismRelay(power)
            }
        }
    }

    trail.forEach { it.age += delta }
    trail.removeAll { it.age > 2.25f }
    updateWeaponNodes(delta)
    emitVisualFx(VisualFxCue.WeaponArcsAdvanced(delta))
}

internal fun MutableGameState.sampleFluxTrail() {
    if (speed <= 45f) {
        trailLastX = coreX
        trailLastY = coreY
        return
    }
    val dx = coreX - trailLastX
    val dy = coreY - trailLastY
    val distance = length(dx, dy)
    if (distance <= 0f) return
    var sampleDistance = 22f - trailDistanceCarry
    var samples = 0
    while (sampleDistance <= distance && samples < 32) {
        val amount = sampleDistance / distance
        trail += TrailPoint(lerp(trailLastX, coreX, amount), lerp(trailLastY, coreY, amount))
        sampleDistance += 22f
        samples++
    }
    trailDistanceCarry = (trailDistanceCarry + distance) % 22f
    while (trail.size > MutableGameState.MAX_TRAIL_POINTS) trail.removeAt(0)
    trailLastX = coreX
    trailLastY = coreY
    if (relicRank(RelicId.AGONY_SCEPTER) > 0) agonyMutationCounts[WeaponId.FLUX_WAKE.ordinal]++
}

internal fun MutableGameState.ensureOrbitals(count: Int, orbitRadius: Float, hitRadius: Float, delta: Float, angularSpeed: Float) {
    if (weaponOrbitals.size != count || weaponOrbitals.any { it.radius != hitRadius }) {
        weaponOrbitals.clear()
        repeat(count) { weaponOrbitals += WeaponOrbital(it, coreX, coreY, hitRadius) }
    }
    weaponOrbitals.forEach { orbital ->
        val angle = elapsed * angularSpeed + orbital.index * TAU / max(1, count)
        orbital.x = coreX + cos(angle) * orbitRadius
        orbital.y = coreY + sin(angle) * orbitRadius
    }
}

internal fun MutableGameState.updateWeaponNodes(delta: Float) {
    val iterator = weaponNodes.iterator()
    while (iterator.hasNext()) {
        val node = iterator.next()
        node.life -= delta
        if (node.life <= 0f) {
            explodeMine(node)
            iterator.remove()
        }
    }
}

internal fun MutableGameState.explodeMine(node: WeaponNode) {
    val power = effectiveWeaponPower()
    enemies.forEach { enemy ->
        val distance = length(enemy.x - node.x, enemy.y - node.y)
        if (distance <= node.radius + enemy.radius) {
            dealWeaponDamage(enemy, 62f * power * (1f - distance / (node.radius * 1.7f)).coerceAtLeast(0.35f), canCrit = true)
            val pull = (1f - distance / max(1f, node.radius)).coerceAtLeast(0f) * 170f
            enemy.vx += (node.x - enemy.x) / max(1f, distance) * pull
            enemy.vy += (node.y - enemy.y) / max(1f, distance) * pull
        }
    }
    burst(node.x, node.y, 18, 2)
    screenShake = max(screenShake, 5f)
    emitSound(AudioCue.IMPACT)
}

internal fun MutableGameState.fireArcCoil(baseDamage: Float) {
    val targets = enemies.asSequence()
        .filter { !it.dead && it.hp > 0f && distanceSquared(coreX, coreY, it.x, it.y) <= 560f * 560f }
        .sortedBy { distanceSquared(coreX, coreY, it.x, it.y) }
        .take(min(6, 3 + weaponLevel / 3))
        .toList()
    var fromX = coreX
    var fromY = coreY
    targets.forEachIndexed { index, enemy ->
        dealWeaponDamage(enemy, baseDamage * powFast(0.76f, index), canCrit = true)
        addWeaponArc(fromX, fromY, enemy.x, enemy.y)
        fromX = enemy.x
        fromY = enemy.y
    }
    val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
    if (agonyRank > 0 && targets.isNotEmpty()) {
        val first = targets.first()
        dealWeaponDamage(first, baseDamage * 0.18f * agonyRank, canCrit = true)
        addRelicArc(fromX, fromY, first.x, first.y)
        agonyMutationCounts[WeaponId.ARC_COIL.ordinal]++
    }
}

internal fun MutableGameState.fireSingularitySpear(damage: Float) {
    val angle = movementAngle()
    val length = 900f + softVelocity(speed) * 0.18f
    weaponBeamStartX = coreX
    weaponBeamStartY = coreY
    weaponBeamEndX = coreX + cos(angle) * length
    weaponBeamEndY = coreY + sin(angle) * length
    weaponBeamTime = 0.18f
    enemies.forEach { enemy ->
        if (segmentCircleIntersects(
                weaponBeamStartX,
                weaponBeamStartY,
                weaponBeamEndX,
                weaponBeamEndY,
                enemy.x,
                enemy.y,
                enemy.radius + 15f,
            )
        ) {
            dealWeaponDamage(enemy, damage, canCrit = true)
        }
    }
    val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
    if (agonyRank > 0) {
        val crossAngle = angle + TAU * 0.25f
        val crossEndX = coreX + cos(crossAngle) * length * 0.72f
        val crossEndY = coreY + sin(crossAngle) * length * 0.72f
        enemies.forEach { enemy ->
            if (segmentCircleIntersects(coreX, coreY, crossEndX, crossEndY, enemy.x, enemy.y, enemy.radius + 12f)) {
                dealWeaponDamage(enemy, damage * 0.28f * agonyRank, canCrit = true)
            }
        }
        addWeaponArc(coreX, coreY, crossEndX, crossEndY, 0.18f)
        agonyMutationCounts[WeaponId.SINGULARITY_SPEAR.ordinal]++
    }
    screenShake = max(screenShake, 7f)
    emitSound(AudioCue.WEAPON_HEAVY)
}

internal fun MutableGameState.firePrismRelay(power: Float) {
    val target = nearestEnemy(coreX, coreY, 820f) ?: return
    val baseAngle = atan2(target.y - coreY, target.x - coreX)
    val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
    val relayCount = (if (currentWeaponMastery >= WeaponMastery.RESONANT) 2 else 1) + if (agonyRank > 0) 1 else 0
    val bounces = when (currentWeaponMastery) {
        WeaponMastery.CALIBRATED -> 2
        WeaponMastery.AMPLIFIED -> 3
        WeaponMastery.RESONANT -> 4
        WeaponMastery.ASCENDED -> 6
    } + agonyRank
    repeat(relayCount) { index ->
        val offset = (index - (relayCount - 1) * 0.5f) * 0.075f
        firePlayerProjectile(coreX, coreY, baseAngle + offset, 760f, 27f * power, bounces, 6f, 3)
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.PRISM_RELAY.ordinal]++
    emitSound(AudioCue.WEAPON_LIGHT)
}
