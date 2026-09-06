// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.*

internal fun MutableGameState.updateCharacterRuntime(delta: Float) {
    var ability = characterRuntime
    val currentSpeed = speed
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val heading = atan2(smoothedVelocityY, smoothedVelocityX)
    val angle = atan2(sin(heading - ability.previousHeading), cos(heading - ability.previousHeading))
    val brakingStarted = braking && !ability.wasBraking
    ability = ability.copy(
        barrierTime = max(0f, ability.barrierTime - delta),
        parryWindow = max(0f, ability.parryWindow - delta),
        parryCooldown = max(0f, ability.parryCooldown - delta),
        latticeTime = max(0f, ability.latticeTime - delta),
    )
    if (ability.barrierTime <= 0f) ability = ability.copy(barrier = 0f)
    if (ability.latticeTime <= 0f) ability = ability.copy(lattice = emptyList())
    for (enemy in enemies) if (enemy.characterMarkTime > 0f) enemy.characterMarkTime = max(0f, enemy.characterMarkTime - delta)
    when (coreShape) {
        CoreShape.ORB -> {
            if (!braking && currentSpeed >= 200f && ability.previousSpeed >= 200f && moved > 0f) {
                val sameDirection = ability.turnArc == 0f || angle * ability.turnArc >= 0f
                val arc = if (sameDirection) ability.turnArc + angle else angle
                val distance = if (sameDirection) ability.turnDistance + moved else moved
                val earned = if (abs(arc) >= 0.698f && distance >= 100f) abs(angle) / PI.toFloat() else 0f
                ability = ability.copy(turnArc = arc.coerceIn(-TAU, TAU), turnDistance = min(2_000f, distance),
                    charge = min(1f, ability.charge + earned))
            } else ability = ability.copy(turnArc = 0f, turnDistance = 0f)
        }
        CoreShape.PRISM -> {
            val lostSpeed = max(0f, ability.previousSpeed - currentSpeed)
            if (braking && ability.previousSpeed >= 250f && moved > 0f && lostSpeed > 0f) {
                val gained = lostSpeed / 500f
                ability = ability.copy(charge = min(1f, ability.charge + gained),
                    barrier = min(30f, ability.barrier + gained * 30f), barrierTime = 4f)
            }
        }
        CoreShape.SHARD -> Unit // Marks are only created by a genuine dash contact.
        CoreShape.RING -> {
            val target = if (braking || currentSpeed < 120f) 55f else 155f
            val radius = ability.ringRadius + (target - ability.ringRadius) * min(1f, delta * 4f)
            ability = ability.copy(ringRadius = radius, charge = (radius - 55f) / 100f)
            if (moved > 0.01f && currentSpeed >= 40f) for (enemy in enemies) {
                val distance = length(enemy.x - coreX, enemy.y - coreY)
                if (distance in max(36f, radius - 18f)..(radius + 18f + enemy.radius)) {
                    damageEnemy(enemy, 27f * delta * effectiveWeaponPower())
                }
            }
        }
        CoreShape.DIAMOND -> {
            if (brakingStarted && ability.previousSpeed >= 200f && moved > 0f && ability.parryCooldown <= 0f) {
                ability = ability.copy(parryWindow = 0.18f, parryCooldown = 1.2f)
            }
            ability = ability.copy(charge = if (ability.parryWindow > 0f) 1f else max(0f, 1f - ability.parryCooldown / 1.2f))
        }
        CoreShape.TESSERACT -> {
            val origin = ability.dashOrigin
            if (dashPhaseTime > 0f && origin != null && !ability.dashRecorded &&
                distanceSquared(origin.x, origin.y, coreX, coreY) >= square(120f)
            ) {
                val nodes = ability.lattice
                if (nodes.size >= 3 && distanceSquared(origin.x, origin.y, nodes.first().x, nodes.first().y) < square(130f)) {
                    collapseCharacterLattice(nodes)
                    ability = ability.copy(lattice = emptyList(), latticeTime = 0f, dashRecorded = true)
                } else if (nodes.all { distanceSquared(origin.x, origin.y, it.x, it.y) >= square(150f) }) {
                    val next = nodes + origin
                    if (next.size == 4) {
                        collapseCharacterLattice(next)
                        ability = ability.copy(lattice = emptyList(), latticeTime = 0f, dashRecorded = true)
                    } else ability = ability.copy(lattice = next, latticeTime = 7f, dashRecorded = true)
                } else ability = ability.copy(dashRecorded = true)
            }
            if (brakingStarted && ability.lattice.size >= 2) {
                collapseCharacterLattice(ability.lattice)
                ability = ability.copy(lattice = emptyList(), latticeTime = 0f)
            }
            ability = ability.copy(charge = ability.lattice.size / 4f)
        }
    }
    characterRuntime = ability.copy(previousSpeed = currentSpeed, previousHeading = heading, wasBraking = braking)
}

internal fun MutableGameState.beginCharacterDash() {
    val ability = characterRuntime
    characterRuntime = ability.copy(dashSequence = ability.dashSequence + 1,
        dashOrigin = WorldPoint(coreX, coreY), dashRecorded = false,
        ramPower = if (coreShape == CoreShape.PRISM) ability.charge * 70f else 0f,
        charge = if (coreShape == CoreShape.PRISM) 0f else ability.charge,
        barrier = if (coreShape == CoreShape.PRISM) 0f else ability.barrier)
}

internal fun MutableGameState.onCharacterDashContact(enemy: Enemy) {
    if (dashPhaseTime <= 0f || enemy.lastCharacterDash == characterRuntime.dashSequence || enemy.dead || enemy.hp <= 0f) return
    enemy.lastCharacterDash = characterRuntime.dashSequence
    pendingDashHits++
    when (coreShape) {
        CoreShape.SHARD -> enemy.characterMarkTime = 3f
        CoreShape.PRISM -> if (characterRuntime.ramPower > 0f) damageEnemy(enemy, characterRuntime.ramPower * effectiveWeaponPower())
        else -> Unit
    }
}

internal fun MutableGameState.onCharacterPrimaryHit(enemy: Enemy) {
    when (coreShape) {
        CoreShape.ORB -> if (characterRuntime.charge >= 1f) {
            characterRuntime = characterRuntime.copy(charge = 0f, turnArc = 0f, turnDistance = 0f)
            areaRelicDamage(coreX, coreY, 210f, 55f * effectiveWeaponPower())
        }
        CoreShape.SHARD -> if (enemy.characterMarkTime > 0f) {
            enemy.characterMarkTime = 0f
            damageEnemy(enemy, 48f * effectiveWeaponPower())
            shockwave(enemy.x, enemy.y, 0.3f, 65f, 2)
        }
        else -> Unit
    }
}

internal fun MutableGameState.tryCharacterParry(
    x: Float, y: Float, vx: Float, vy: Float,
    threatenedCoreX: Float = coreX, threatenedCoreY: Float = coreY,
): Boolean {
    if (coreShape != CoreShape.DIAMOND || characterRuntime.parryWindow <= 0f) return false
    // Collision callers pass the beginning of the swept segment: an incoming projectile
    // may already be beyond the core at the end of the same fixed step.
    if (vx * (threatenedCoreX - x) + vy * (threatenedCoreY - y) <= 0f) return false
    characterRuntime = characterRuntime.copy(parryWindow = 0f)
    nearestOtherEnemy(coreX, coreY, -1, 800f)?.let { enemy ->
        fireRelicProjectile(coreX, coreY, enemy, 65f * effectiveWeaponPower())
    }
    shockwave(coreX, coreY, 0.25f, 110f, 2)
    return true
}

private fun MutableGameState.collapseCharacterLattice(nodes: List<WorldPoint>) {
    if (nodes.size < 2) return
    for (enemy in enemies) {
        var touches = false
        for (index in nodes.indices) {
            val a = nodes[index]
            val b = nodes[(index + 1) % nodes.size]
            if (segmentCircleIntersects(a.x, a.y, b.x, b.y, enemy.x, enemy.y, enemy.radius + 48f)) touches = true
        }
        if (touches) damageEnemy(enemy, (35f + nodes.size * 17f) * effectiveWeaponPower())
    }
    for (index in nodes.indices) {
        val a = nodes[index]
        val b = nodes[(index + 1) % nodes.size]
        addWeaponArc(a.x, a.y, b.x, b.y, 0.35f)
    }
}
