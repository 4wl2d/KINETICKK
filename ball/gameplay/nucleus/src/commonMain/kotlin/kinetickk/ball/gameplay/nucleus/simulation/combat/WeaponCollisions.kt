// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.exp
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*

internal fun MutableGameState.resolveWeaponDamage(delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> resolveFluxWake(delta, power, agonyRank)
        WeaponId.MORNINGSTAR -> resolveMorningstar(power, agonyRank)
        WeaponId.PHASE_LATTICE -> resolvePhaseLattice(delta, power, agonyRank)
        WeaponId.RIFT_BLADES -> resolveRiftBlades(power)
        WeaponId.ENTROPY_FIELD -> resolveEntropyField(delta, power, agonyRank)
        WeaponId.NULL_LANCE, WeaponId.GRAVITY_MINES, WeaponId.ION_SWARM, WeaponId.ARC_COIL, WeaponId.QUASAR_CANNON,
        WeaponId.SINGULARITY_SPEAR, WeaponId.PRISM_RELAY, WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM, WeaponId.TETHER_ARC,
        WeaponId.SENTRY_GRID, WeaponId.HUNTER_SALVO, WeaponId.CONCUSSION_ENGINE, WeaponId.GLACIER_BELL, WeaponId.EVENT_LENS,
        WeaponId.SOLAR_SWEEP, WeaponId.AEGIS_SHARDS, WeaponId.HOLLOW_RIFT, WeaponId.RESONANCE_DRUM -> Unit
    }
    resolveWeaponEntityHits(delta)
}

/**
 * Flux Wake: [FLUX_DPS] per enemy touching a live trail point (no speed term: it scales with speed), Scorch every
 * [FLUX_SCORCH_INTERVAL]. The trail's heat spreads thin over a crowd: with more than [FLUX_FULL_HEAT_ENEMIES]
 * enemies in it at once, they share that many enemies' worth.
 */
private fun MutableGameState.resolveFluxWake(delta: Float, power: Float, agonyRank: Int) {
    val width = FLUX_WIDTH * (if (hasMasteryForm(1)) FLUX_WIDTH_FORM else 1f) + agonyRank * 4f
    val earlyIgnite = hasMasteryForm(3)
    var burning = 0
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.alive && fluxTrailScale(enemy, width) > 0f) burning++
    }
    val spread = if (burning > FLUX_FULL_HEAT_ENEMIES) FLUX_FULL_HEAT_ENEMIES.toFloat() / burning else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val scale = fluxTrailScale(enemy, width)
        if (scale > 0f) {
            val landed = dealWeaponDamage(enemy, FLUX_DPS * delta * power * scale * spread * (1f + agonyRank * 0.10f),
                cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.THERMAL, FLUX_SCORCH_INTERVAL, extraBeforeBreak = earlyIgnite)
        }
    }
}

/** The strongest live trail point [enemy] touches (a turning trail's is larger), or 0. */
private fun MutableGameState.fluxTrailScale(enemy: Enemy, width: Float): Float {
    var scale = 0f
    for (trailIndex in trail.indices) {
        val point = trail[trailIndex]
        if (point.age < point.life && point.scale > scale &&
            distanceSquared(point.x, point.y, enemy.x, enemy.y) < square(enemy.radius + width)
        ) {
            scale = point.scale
        }
    }
    return scale
}

/** Morningstar: (38 + 0.1 v) per contact (mass is its scaling, so no mass term); the forms add reach, a star and throws. */
private fun MutableGameState.resolveMorningstar(power: Float, agonyRank: Int) {
    val sizeScale = morningstarScale()
    val hitReach = MORNINGSTAR_REACH * sizeScale
    val damage = (MORNINGSTAR_DAMAGE + softVelocity(speed) * MORNINGSTAR_SPEED_SHARE) * power * sizeScale
    val secondStar = hasMasteryForm(2)
    val secondX = coreX - (morningstarX - coreX) * MORNINGSTAR_SECOND_ORBIT
    val secondY = coreY - (morningstarY - coreY) * MORNINGSTAR_SECOND_ORBIT
    val mirrorX = coreX * 2f - morningstarX
    val mirrorY = coreY * 2f - morningstarY
    val knockback = MORNINGSTAR_KNOCKBACK * if (hasMasteryForm(3)) 2f else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        val reach = enemy.radius + hitReach
        val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < reach * reach
        val secondReach = enemy.radius + hitReach * MORNINGSTAR_SECOND_SIZE
        val secondHit = secondStar && distanceSquared(secondX, secondY, enemy.x, enemy.y) < secondReach * secondReach
        val forbiddenHit = agonyRank > 0 && distanceSquared(mirrorX, mirrorY, enemy.x, enemy.y) < reach * reach
        if (!primaryHit && !secondHit && !forbiddenHit) continue
        val scale = when {
            primaryHit -> 1f
            secondHit -> MORNINGSTAR_SECOND_SIZE
            else -> 0.55f + 0.08f * agonyRank
        }
        val sourceX = if (primaryHit) morningstarX else if (secondHit) secondX else mirrorX
        val sourceY = if (primaryHit) morningstarY else if (secondHit) secondY else mirrorY
        dealWeaponDamage(enemy, damage * scale, canCrit = true, cadence = WeaponHitCadence.CONTACT,
            procKey = WeaponRuntime.sourceKey(if (primaryHit) 0 else 1))
        pushEnemy(enemy, sourceX, sourceY, knockback * scale)
        enemy.weaponCooldown = 0.2f
        screenShake = max(screenShake, 5f)
    }
}

/** Phase Lattice: a ring 26/s with Chill every 0.6 s; the Ring signature centres it on the Halo. */
private fun MutableGameState.resolvePhaseLattice(delta: Float, power: Float, agonyRank: Int) {
    val center = phaseLatticeCenter()
    val half = PHASE_LATTICE_HALF_WIDTH * if (hasMasteryForm(1)) 1.35f else 1f
    val inner = square(max(0f, center - half))
    val outer = square(center + half)
    val earlyFreeze = hasMasteryForm(3)
    val outerRing = hasMasteryForm(2)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
        if (ring in inner..outer) {
            val landed = dealWeaponDamage(enemy, PHASE_LATTICE_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.CRYO, PHASE_LATTICE_CHILL_INTERVAL, extraBeforeBreak = earlyFreeze)
        } else if (outerRing && ring in square(center + PHASE_OUTER_OFFSET - 16f)..square(center + PHASE_OUTER_OFFSET + 16f)) {
            dealWeaponDamage(enemy, PHASE_OUTER_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = WeaponRuntime.sourceKey(1))
        }
        if (agonyRank > 0 && ring in square(158f)..square(192f + agonyRank * 4f)) {
            dealWeaponDamage(enemy, (8f + 3f * agonyRank) * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = WeaponRuntime.sourceKey(1))
        }
    }
}

/** Centre radius of Phase Lattice's ring: the Halo radius for the Ring, else 110. */
internal fun MutableGameState.phaseLatticeCenter(): Float =
    if (signatureActive() && haloRadius > 0f) haloRadius else PHASE_LATTICE_CENTER

/** Rift Blades: (31 + 0.025 v) per contact, Hollow on every third hit; at the third form crits slash on. */
private fun MutableGameState.resolveRiftBlades(power: Float) {
    val runtime = weaponRuntime
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        var orbitalHit = false
        for (orbitalIndex in weaponOrbitals.indices) {
            val orbital = weaponOrbitals[orbitalIndex]
            if (distanceSquared(orbital.x, orbital.y, enemy.x, enemy.y) <= square(orbital.radius + enemy.radius)) {
                orbitalHit = true
                break
            }
        }
        if (!orbitalHit) continue
        val damage = (RIFT_BLADE_DAMAGE + softVelocity(speed) * RIFT_BLADE_SPEED_SHARE) * power
        val result = dealWeaponDamage(enemy, damage, canCrit = true, cadence = WeaponHitCadence.CONTACT)
        enemy.weaponCooldown = 0.17f
        if (result.amount > 0f) {
            runtime.elementHits = (runtime.elementHits + 1) % RIFT_BLADE_HOLLOW_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.VOID, 1)
        }
        if (result.critical && hasMasteryForm(3)) riftSlash(enemy, damage * RIFT_SLASH_SHARE)
    }
}

/** A rift slash: a cut from the struck enemy outward from the Core. */
private fun MutableGameState.riftSlash(origin: Enemy, damage: Float) {
    val dx = origin.x - coreX
    val dy = origin.y - coreY
    val distance = max(1f, length(dx, dy))
    val endX = origin.x + dx / distance * RIFT_SLASH_LENGTH
    val endY = origin.y + dy / distance * RIFT_SLASH_LENGTH
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === origin || !enemy.alive) continue
        if (segmentCircleIntersects(origin.x, origin.y, endX, endY, enemy.x, enemy.y, enemy.radius + 10f)) {
            dealWeaponDamage(enemy, damage, procKey = WeaponRuntime.PROC_NEVER)
        }
    }
    addWeaponArc(origin.x, origin.y, endX, endY, 0.16f)
}

/** Entropy Field: 28/s with drag and Decay every 0.8 s; the forms widen it, execute and spread Ruptures. */
private fun MutableGameState.resolveEntropyField(delta: Float, power: Float, agonyRank: Int) {
    val radius = entropyFieldRadius()
    val executeLine = max(if (hasMasteryForm(2)) ENTROPY_EXECUTE else 0f, if (agonyRank > 0) 0.06f + agonyRank * 0.015f else 0f)
    entropyField(coreX, coreY, radius, power * (1f + agonyRank * 0.08f), executeLine, delta, WeaponRuntime.sourceKey(0))
    // Spiral signature: the echo ghost carries a half-strength field.
    if (signatureActive() && ghostActive) {
        entropyField(ghostX, ghostY, radius * ENTROPY_GHOST_RADIUS, power * 0.5f, 0f, delta, WeaponRuntime.sourceKey(1))
    }
}

private fun MutableGameState.entropyField(x: Float, y: Float, radius: Float, power: Float, executeLine: Float, delta: Float, procKey: Int) {
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        // The collapse executes outright, past every multiplier and tether; bosses only take the field.
        if (executeLine > 0f && !enemy.isBossClass && enemy.hp <= enemy.maxHp * executeLine) {
            executeEnemy(enemy, HitSource.WEAPON)
        } else {
            val landed = dealWeaponDamage(enemy, ENTROPY_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.ENTROPY, ENTROPY_DECAY_INTERVAL)
        }
        val drag = exp(-0.5f * delta * enemy.knockback)
        enemy.vx *= drag
        enemy.vy *= drag
    }
}

/** Entropy Field radius: 170 + 8 per level, +35 % at the first form, +18 per Agony power. */
internal fun MutableGameState.entropyFieldRadius(): Float =
    (170f + weaponLevel * 8f) * (if (hasMasteryForm(1)) 1.35f else 1f) + agonyPower() * 18f

/** Entropy Field's third form: a Rupture inside the field spreads Decay around it. */
internal fun MutableGameState.onWeaponBreak(enemy: Enemy, element: StatusElement) {
    if (weapon != WeaponId.ENTROPY_FIELD || element != StatusElement.ENTROPY || !hasMasteryForm(3)) return
    val radius = entropyFieldRadius()
    if (distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) return
    applyStatusAround(enemy.x, enemy.y, ENTROPY_SPREAD_RADIUS, StatusElement.ENTROPY, 2f, generation = 1, excludedId = enemy.id)
}
