// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kotlin.math.max
import kotlin.math.min


internal fun MutableGameState.onQualifiedWeaponHit(enemy: Enemy, result: DamageResult, sourceWeapon: WeaponId) {
    val orbitalRank = relicRank(RelicId.ORBITAL_NAIL)
    if (orbitalRank > 0) {
        val dx = coreX - enemy.x
        val dy = coreY - enemy.y
        val distance = max(1f, length(dx, dy))
        enemy.vx += dx / distance * 24f * orbitalRank
        enemy.vy += dy / distance * 24f * orbitalRank
        relicProcCounts[RelicId.ORBITAL_NAIL.ordinal]++
    }

    val massRank = relicRank(RelicId.MASS_ECHO)
    if (massRank > 0) {
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = max(1f, length(dx, dy))
        val recoil = 18f * massRank * mass
        enemy.vx += dx / distance * recoil
        enemy.vy += dy / distance * recoil
        relicProcCounts[RelicId.MASS_ECHO.ordinal]++
    }

    val tidalRank = relicRank(RelicId.TIDAL_LOCK)
    if (tidalRank > 0) {
        val index = RelicId.TIDAL_LOCK.ordinal
        enemy.relicCounters[index] = min(5, enemy.relicCounters[index] + 1)
    }

    val filamentRank = relicRank(RelicId.VOLTAIC_FILAMENT)
    if (filamentRank > 0 && relicCooldowns[RelicId.VOLTAIC_FILAMENT.ordinal] <= 0f) {
        nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 440f)?.let { target ->
            damageEnemy(target, result.amount * 0.16f * filamentRank)
            addRelicArc(enemy.x, enemy.y, target.x, target.y)
            relicCooldowns[RelicId.VOLTAIC_FILAMENT.ordinal] = 0.28f
            relicProcCounts[RelicId.VOLTAIC_FILAMENT.ordinal]++
        }
    }

    val staticRank = relicRank(RelicId.STATIC_CHORUS)
    if (staticRank > 0) {
        val index = RelicId.STATIC_CHORUS.ordinal
        relicCounters[index]++
        if (relicCounters[index] % 7 == 0) {
            chainRelicDamage(enemy, staticRank, 520f, result.amount * 0.12f)
            relicProcCounts[index]++
        }
    }

    val debtRank = relicRank(RelicId.ION_DEBT)
    if (debtRank > 0) {
        val index = RelicId.ION_DEBT.ordinal
        enemy.relicCounters[index]++
        if (enemy.relicCounters[index] >= 5) {
            enemy.relicCounters[index] = 0
            areaRelicDamage(enemy.x, enemy.y, 95f + 10f * debtRank, 14f * debtRank, enemy.id)
            relicProcCounts[index]++
        }
    }

    val circuitRank = relicRank(RelicId.CIRCUIT_BREAKER)
    if (circuitRank > 0 && enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] == 0) {
        enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] = 1
        val interruption = (1f - 0.08f * circuitRank).coerceAtLeast(0.35f)
        enemy.vx *= interruption
        enemy.vy *= interruption
        relicProcCounts[RelicId.CIRCUIT_BREAKER.ordinal]++
    }

    val stormRank = relicRank(RelicId.STORM_INDEX)
    if (stormRank > 0 && velocityTier >= 1) {
        val index = RelicId.STORM_INDEX.ordinal
        relicCounters[index]++
        if (relicCounters[index] % 4 == 0) {
            nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 220f + 45f * stormRank)?.let { target ->
                damageEnemy(target, 10f * stormRank)
                addRelicArc(enemy.x, enemy.y, target.x, target.y)
                relicProcCounts[index]++
            }
        }
    }

    val echoRank = relicRank(RelicId.ECHO_CHAMBER)
    if (echoRank > 0 && delayedRelicHits.size < MutableGameState.MAX_DELAYED_RELIC_HITS) {
        delayedRelicHits += DelayedRelicHit(RelicId.ECHO_CHAMBER, enemy.id, 0.45f, result.amount * 0.12f * echoRank)
    }
    val palimpsestRank = relicRank(RelicId.PALIMPSEST_ROUND)
    if (palimpsestRank > 0) {
        val index = RelicId.PALIMPSEST_ROUND.ordinal
        relicCounters[index]++
        if (relicCounters[index] % 7 == 0) {
            damageEnemy(enemy, result.amount * 0.20f * palimpsestRank)
            relicProcCounts[index]++
        }
    }
    val fractureRank = relicRank(RelicId.FRACTURE_GATE)
    if (fractureRank > 0) {
        val index = RelicId.FRACTURE_GATE.ordinal
        enemy.relicCounters[index]++
        if (enemy.relicCounters[index] % 6 == 0 && enemy.hp > 0f) {
            enemy.x = coreX - (enemy.x - coreX)
            enemy.y = coreY - (enemy.y - coreY)
            enemy.previousX = enemy.x
            enemy.previousY = enemy.y
            damageEnemy(enemy, 12f * fractureRank)
            shockwave(enemy.x, enemy.y, 0.2f, 52f + 8f * fractureRank, 2)
            relicProcCounts[index]++
        }
    }

    val glassRank = relicRank(RelicId.GLASS_WITNESS)
    if (glassRank > 0 && enemy.relicCounters[RelicId.GLASS_WITNESS.ordinal] == 0) {
        enemy.relicCounters[RelicId.GLASS_WITNESS.ordinal] = 1
        enemy.relicTimers[RelicId.GLASS_WITNESS.ordinal] = 3f
    }
    val spectralRank = relicRank(RelicId.SPECTRAL_FAN)
    if (spectralRank > 0) {
        val index = RelicId.SPECTRAL_FAN.ordinal
        relicCounters[index]++
        if (relicCounters[index] % 6 == 0) {
            chainRelicDamage(enemy, 2, 500f, result.amount * 0.14f * spectralRank)
            relicProcCounts[index]++
        }
    }
    val chromaRank = relicRank(RelicId.CHROMA_FEEDBACK)
    if (chromaRank > 0 && enemy.relicCounters[RelicId.CHROMA_FEEDBACK.ordinal] == 0) {
        enemy.relicCounters[RelicId.CHROMA_FEEDBACK.ordinal] = 1
        if (maxShield > 0f && shield < maxShield) {
            shield = min(maxShield, shield + 1.5f * chromaRank)
        } else {
            overdriveCharge = min(125f, overdriveCharge + 2f * chromaRank)
        }
        relicProcCounts[RelicId.CHROMA_FEEDBACK.ordinal]++
    }
    val mirrorCutRank = relicRank(RelicId.MIRROR_CUT)
    if (mirrorCutRank > 0 && result.critical) {
        nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 560f)?.let { target ->
            damageEnemy(target, result.amount * 0.15f * mirrorCutRank)
            addRelicArc(enemy.x, enemy.y, target.x, target.y)
            relicProcCounts[RelicId.MIRROR_CUT.ordinal]++
        }
    }

    val heatRank = relicRank(RelicId.HEAT_DEBT)
    if (heatRank > 0) {
        val index = RelicId.HEAT_DEBT.ordinal
        enemy.relicCounters[index]++
        enemy.relicValues[index] += result.amount * 0.08f * heatRank
        if (enemy.relicCounters[index] >= 5) {
            enemy.relicCounters[index] = 0
            val storedDamage = enemy.relicValues[index]
            enemy.relicValues[index] = 0f
            areaRelicDamage(enemy.x, enemy.y, 105f + 8f * heatRank, 16f * heatRank + storedDamage)
            relicProcCounts[index]++
        }
    }
    val scarRank = relicRank(RelicId.SCAR_TISSUE)
    if (scarRank > 0) {
        val index = RelicId.SCAR_TISSUE.ordinal
        enemy.relicCounters[index] = min(5, enemy.relicCounters[index] + 1)
        enemy.relicTimers[index] = 3f
    }

    val huntRank = relicRank(RelicId.MIRROR_OF_THE_HUNT)
    if (huntRank > 0 && enemy.relicCounters[RelicId.MIRROR_OF_THE_HUNT.ordinal] == 0) {
        enemy.relicCounters[RelicId.MIRROR_OF_THE_HUNT.ordinal] = 1
        nearestOtherEnemy(coreX, coreY, enemy.id, 720f)?.let { target ->
            damageEnemy(target, result.amount * 0.20f * huntRank)
            addRelicArc(enemy.x, enemy.y, target.x, target.y)
            relicProcCounts[RelicId.MIRROR_OF_THE_HUNT.ordinal]++
        }
    }

    if (sourceWeapon == WeaponId.PRISM_RELAY && relicRank(RelicId.AGONY_SCEPTER) > 0) {
        // Prism's Scepter mutation is applied at launch; retaining source identity here prevents accidental proc recursion.
    }
}

internal fun MutableGameState.isRelicTargetIsolated(enemy: Enemy, range: Float): Boolean = enemies.none {
    it.id != enemy.id && !it.dead && it.hp > 0f && distanceSquared(enemy.x, enemy.y, it.x, it.y) <= range * range
}

internal fun MutableGameState.nearestOtherEnemy(x: Float, y: Float, excludedId: Int, range: Float): Enemy? = enemies
    .asSequence()
    .filter { it.id != excludedId && !it.dead && it.hp > 0f && distanceSquared(x, y, it.x, it.y) <= range * range }
    .minByOrNull { distanceSquared(x, y, it.x, it.y) }

internal fun MutableGameState.chainRelicDamage(origin: Enemy, count: Int, range: Float, damage: Float) {
    val used = mutableSetOf(origin.id)
    var fromX = origin.x
    var fromY = origin.y
    repeat(count) {
        val target = enemies.asSequence()
            .filter { it.id !in used && !it.dead && it.hp > 0f && distanceSquared(fromX, fromY, it.x, it.y) <= range * range }
            .minByOrNull { distanceSquared(fromX, fromY, it.x, it.y) }
            ?: return@repeat
        used += target.id
        damageEnemy(target, damage)
        addRelicArc(fromX, fromY, target.x, target.y)
        fromX = target.x
        fromY = target.y
    }
}

internal fun MutableGameState.areaRelicDamage(x: Float, y: Float, radius: Float, damage: Float, excludedId: Int = -1) {
    enemies.forEach { target ->
        if (target.id != excludedId && !target.dead && target.hp > 0f &&
            distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)
        ) {
            damageEnemy(target, damage)
        }
    }
    shockwave(x, y, 0.24f, radius, 2)
}

internal fun MutableGameState.addRelicArc(fromX: Float, fromY: Float, toX: Float, toY: Float) {
    addWeaponArc(fromX, fromY, toX, toY, 0.12f)
}

internal fun MutableGameState.distinctRelicAspectCount(): Int {
    var count = 0
    for (index in equippedRelics.indices) {
        val relic = equippedRelics[index]
        val aspect = RelicCatalog.byId(relic.id).aspect
        var seen = false
        for (prior in 0 until index) {
            if (RelicCatalog.byId(equippedRelics[prior].id).aspect == aspect) {
                seen = true
                break
            }
        }
        if (aspect != RelicAspect.SOVEREIGN && !seen) {
            count++
        }
    }
    return count
}
