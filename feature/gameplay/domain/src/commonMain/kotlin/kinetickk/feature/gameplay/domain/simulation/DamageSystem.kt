// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.core.content.*

import kinetickk.feature.gameplay.domain.model.*
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kotlin.math.max
import kotlin.math.roundToLong


internal fun MutableGameState.damageEnemy(
    enemy: Enemy,
    baseAmount: Float,
    canCrit: Boolean = false,
    bonusCritChance: Float = 0f,
    bonusCritDamage: Float = 0f,
    relicKillProcsEligible: Boolean = false,
): DamageResult {
    if (baseAmount <= 0f || enemy.dead || enemy.hp <= 0f) return DamageResult(0f, false)
    val effectiveCritChance = (critChance + bonusCritChance).coerceIn(0f, 0.75f)
    val effectiveCritDamage = critMultiplier + bonusCritDamage
    val critical = canCrit && gameplayRandom.nextFloat() < effectiveCritChance
    val amount = when {
        critical -> baseAmount * effectiveCritDamage
        !canCrit -> baseAmount * (1f + effectiveCritChance * (effectiveCritDamage - 1f))
        else -> baseAmount
    }
    enemy.hp -= amount
    if (enemy.hp <= 0f) enemy.relicKillProcsEligible = relicKillProcsEligible
    enemy.flash = max(enemy.flash, if (amount >= 5f) 1f else 0.16f)
    if (settings.damageNumbers && amount >= 5f) {
        emitVisualFx(
            VisualFxCue.DamageNumberAdded(
                x = enemy.x,
                y = enemy.y - enemy.radius,
                amount = amount.roundToLong(),
                critical = critical,
            ),
        )
    }
    return DamageResult(amount, critical)
}

internal fun MutableGameState.dealWeaponDamage(
    enemy: Enemy,
    baseAmount: Float,
    canCrit: Boolean = false,
    cadence: WeaponHitCadence = WeaponHitCadence.DISCRETE,
    sourceWeapon: WeaponId = weapon,
): DamageResult {
    if (baseAmount <= 0f || enemy.dead || enemy.hp <= 0f) return DamageResult(0f, false)
    val qualified = cadence == WeaponHitCadence.DISCRETE || enemy.relicQualificationCooldown <= 0f
    var multiplier = 1f

    val flywheelRank = relicRank(RelicId.KINETIC_FLYWHEEL)
    if (flywheelRank > 0 && speed >= 500f) {
        multiplier += 0.05f * flywheelRank * if (speed >= 1_600f) 2f else 1f
    }
    val overtakeRank = relicRank(RelicId.OVERTAKE_PROTOCOL)
    if (overtakeRank > 0 && length(enemy.vx, enemy.vy) >= 170f) multiplier += 0.07f * overtakeRank
    if (qualified && brakepointCharge > 0f) multiplier += brakepointCharge
    val polarityRank = relicRank(RelicId.POLARITY_SLING)
    if (polarityRank > 0) multiplier += 0.08f * polarityRank * (1f - polarityStability)
    val distanceFromCore = length(enemy.x - coreX, enemy.y - coreY)
    val periapsisRank = relicRank(RelicId.PERIAPSIS_HOOK)
    if (periapsisRank > 0 && distanceFromCore > 300f) multiplier += 0.08f * periapsisRank
    val crushRank = relicRank(RelicId.CRUSH_DEPTH)
    if (crushRank > 0 && distanceFromCore <= 155f) multiplier += 0.06f * crushRank
    val massRank = relicRank(RelicId.MASS_ECHO)
    if (massRank > 0) multiplier += 0.04f * massRank * mass
    val tidalRank = relicRank(RelicId.TIDAL_LOCK)
    if (tidalRank > 0) {
        multiplier += 0.02f * tidalRank * enemy.relicCounters[RelicId.TIDAL_LOCK.ordinal].coerceIn(0, 5)
    }
    val returnRank = relicRank(RelicId.RETURN_CIRCUIT)
    if (returnRank > 0 && isRelicTargetIsolated(enemy, 260f)) multiplier += 0.09f * returnRank
    val glassRank = relicRank(RelicId.GLASS_WITNESS)
    if (glassRank > 0 && enemy.relicTimers[RelicId.GLASS_WITNESS.ordinal] > 0f) multiplier += 0.07f * glassRank
    val doomRank = relicRank(RelicId.DOOM_CLOCK)
    if (doomRank > 0 && (enemy.type == EnemyType.ELITE || enemy.type == EnemyType.ARCHITECT)) {
        multiplier += 0.08f * doomRank * (1f + runProgress)
    }
    val lastLightRank = relicRank(RelicId.LAST_LIGHT)
    if (lastLightRank > 0 && hp <= maxHp * 0.35f) multiplier += 0.12f * lastLightRank
    val crownRank = relicRank(RelicId.CROWN_OF_FOUR_WINDS)
    if (crownRank > 0) multiplier += 0.04f * crownRank * distinctRelicAspectCount()

    var amount = baseAmount * multiplier
    val circuitRank = relicRank(RelicId.CIRCUIT_BREAKER)
    if (qualified && circuitRank > 0 && enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] == 0) {
        amount += 11f * circuitRank
    }
    val fractureLensRank = relicRank(RelicId.FRACTURE_LENS)
    val bonusCritChance = if (fractureLensRank > 0 && enemy.hp < enemy.maxHp) 0.025f * fractureLensRank else 0f
    val hardlightRank = relicRank(RelicId.HARDLIGHT_EDGE)
    val result = damageEnemy(
        enemy,
        amount,
        canCrit,
        bonusCritChance,
        0.12f * hardlightRank,
        relicKillProcsEligible = true,
    )
    if (qualified && result.amount > 0f) {
        if (cadence == WeaponHitCadence.CONTINUOUS) enemy.relicQualificationCooldown = 0.22f
        if (brakepointCharge > 0f) brakepointCharge = 0f
        onQualifiedWeaponHit(enemy, result, sourceWeapon)
    }
    return result
}
