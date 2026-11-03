// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Scaling helpers.

/** Flat element and proc damage keeps pace with enemy integrity through the run. */
internal fun MutableGameState.runScale(): Float =
    content.tempo.runScaleGrowth(elapsed) * rebirthProfile.playerPowerMultiplier

/** Impact growth through the run. */
internal fun MutableGameState.impactRunGrowth(): Float = content.tempo.impactRunGrowth(elapsed)

/** Weapon growth through the run (plus the Matrix bonus); effectiveWeaponPower and the build summary share it. */
internal fun MutableGameState.weaponRunGrowth(): Float =
    content.tempo.weaponRunGrowth(elapsed) + matrix.weaponRunGrowthBonus * runProgress

/** Scale of damage the build deals to the Core itself; it never follows the enemy curve. */
internal fun MutableGameState.selfDamageScale(): Float =
    content.tempo.selfDamageRunGrowth(elapsed) * rebirthProfile.playerPowerMultiplier

internal fun MutableGameState.effectiveMass(): Float {
    val neutron = if (build.upside(RelicId.NEUTRON_HEART)) {
        content.relic(RelicId.NEUTRON_HEART).value(0, relicRank(RelicId.NEUTRON_HEART)) / 100f *
            build.relicUpside(RelicId.NEUTRON_HEART)
    } else 0f
    return mass * (1f + neutron) * characterMassMultiplier() * skillMassMultiplier()
}

/** Impact power at [atSpeed], including run growth; the shared basis of impacts and IMPACT procs. */
internal fun MutableGameState.impactPower(atSpeed: Float): Float =
    max(6f, effectiveMass() * softVelocity(atSpeed) * 0.115f * damageMultiplier) *
        rebirthProfile.playerPowerMultiplier * impactRunGrowth()

internal fun MutableGameState.effectiveMagnetism(): Float =
    magnetStrength * if (build.liability(RelicId.NEUTRON_HEART)) 0.8f else 1f

internal fun MutableGameState.effectiveDashImpulse(): Float =
    dashImpulse * if (build.liability(RelicId.NEUTRON_HEART)) 0.65f else 1f

internal fun MutableGameState.effectiveCooling(): Float {
    var cooling = coolingRate * (1f + buffAmount(BuffStat.COOLING) + whileAmount(BuffStat.COOLING, null))
    if (tagTier(BuildTag.OVERCLOCK) >= 1) cooling *= 1.15f
    if (build.liability(RelicId.FURNACE_COVENANT)) cooling *= 0.45f
    // Dash Heat to Skills: the Core cools slower.
    if (build.dashHeatToSkills > 0f) cooling *= kinetickk.ball.content.api.DASH_HEAT_CONVERSION_COOLING
    return cooling
}

/** Data to Skills: a Data pickup recharges every skill (at the level cap also the Apex, twice as much). */
internal fun MutableGameState.dataPickupRechargesSkills() {
    val amount = build.dataToSkills
    if (amount <= 0f) return
    refundAllSkills(amount)
    if (apexUnlocked) refundApex(amount * DATA_APEX_REFUND_SHARE)
}

/** Dash Heat to Skills: every [DASH_HEAT_PER_REFUND] Dash heat spent recharges every skill by the conversion's amount. */
internal fun MutableGameState.bankDashHeat(spent: Float) {
    val amount = build.dashHeatToSkills
    if (amount <= 0f || !(spent > 0f)) return
    var bank = dashHeatBank + spent
    while (bank >= DASH_HEAT_PER_REFUND) {
        bank -= DASH_HEAT_PER_REFUND
        refundAllSkills(amount)
    }
    dashHeatBank = bank
}

internal const val DATA_APEX_REFUND_SHARE: Float = 2f
internal const val DASH_HEAT_PER_REFUND: Float = 10f

internal fun MutableGameState.effectiveDashHeatCost(): Float {
    var cost = dashHeatCost
    if (tagTier(BuildTag.DRIVE) >= 1) cost *= 0.85f
    if (build.upside(RelicId.BLINK_DRIVE) && !build.pairing(RelicPairingId.FOLD_VECTOR)) cost *= 0.65f
    // Plasma Pool: Dashes from inside a pool cost less heat.
    if (inPlasmaPool()) cost *= reactionValue(ReactionId.PLASMA_BURST, PLASMA_DASH_HEAT_VALUE)
    return cost
}

/** True while the Core stands in a Plasma Burst's pool. */
internal fun MutableGameState.inPlasmaPool(): Boolean =
    coreInField(CombatFieldKind.PLASMA) && hasVerb(ReactionId.PLASMA_BURST)

/** Magnet radius; Event Horizon's liability removes the latch instead of shrinking it. */
internal fun MutableGameState.effectivePickupRadius(): Float = pickupRadius

/** Activation multiplier from items, resonance, buffs and relics (mastery and Overdrive apply separately). */
internal fun MutableGameState.buildActivationBonus(): Float {
    var bonus = buffAmount(BuffStat.ACTIVATION) + whileAmount(BuffStat.ACTIVATION, null)
    if (tagTier(BuildTag.ARSENAL) >= 1) bonus += 0.10f
    if (build.upside(RelicId.HOLLOW_CROWN)) bonus += 0.08f * emptyRelicSlots()
    if (inPlasmaPool()) bonus += reactionValue(ReactionId.PLASMA_BURST, PLASMA_ACTIVATION_VALUE)
    var multiplier = 1f + bonus
    if (build.liability(RelicId.STASIS_ANCHOR) && !braking) multiplier *= 0.7f
    return multiplier
}

/** FORTUNE I adds luck; derived so resonance never writes the base stat. */
internal fun MutableGameState.effectiveLuck(): Float = luck + if (tagTier(BuildTag.FORTUNE) >= 1) 0.2f else 0f

/** CHAIN I adds a second to the combo window. */
internal fun MutableGameState.effectiveComboWindow(): Float = comboWindow + if (tagTier(BuildTag.CHAIN) >= 1) 1f else 0f

internal fun MutableGameState.emptyRelicSlots(): Int = (content.relicPolicy.maxSlots - equippedRelics.size).coerceAtLeast(0)

internal fun MutableGameState.shieldCapacity(): Float {
    if (build.liability(RelicId.GLASS_COMET)) return 0f
    var capacity = maxShield
    if (tagTier(BuildTag.BULWARK) >= 1) capacity += 18f
    // Aegis Shards add shield capacity while equipped.
    if (weapon == WeaponId.AEGIS_SHARDS) capacity += AEGIS_SHIELD_CAPACITY
    if (build.upside(RelicId.AEGIS_INVERSION)) {
        capacity += maxHp * 0.6f * (if (build.pairing(RelicPairingId.ROT_WARD)) 1.3f else 1f)
    }
    return capacity
}

internal fun MutableGameState.effectiveCritChance(): Float =
    (critChance + buffAmount(BuffStat.CRIT_CHANCE) + whileAmount(BuffStat.CRIT_CHANCE, null) +
        if (tagTier(BuildTag.PRECISION) >= 1) 0.06f else 0f).coerceIn(0f, 0.75f)

internal fun MutableGameState.effectiveDamageReduction(): Float {
    if (build.liability(RelicId.GLASS_COMET)) return 0f
    return (damageReduction + buffAmount(BuffStat.DAMAGE_REDUCTION) + whileAmount(BuffStat.DAMAGE_REDUCTION, null))
        .coerceIn(0f, 0.65f)
}

/**
 * Power of the equipped weapon's own hits: item weapon power, Rebirth, level, generic mastery, run growth
 * (1 + 1.6p plus the Matrix bonus), Overdrive and the synergies ([weaponSynergyPower]: tag resonance,
 * signature, Relic key harmony, scaling stat) and Honing ([weaponUpgradePower]). An OVERDRIVE-scaling weapon
 * takes Overdrive through its scaling only, so no input counts twice.
 */
internal fun MutableGameState.effectiveWeaponPower(): Float =
    weaponPower *
        rebirthProfile.playerPowerMultiplier *
        (1f + (weaponLevel - 1) * 0.08f) *
        (1f + currentWeaponMastery.damageBonus) *
        weaponRunGrowth() *
        weaponSynergyPower() *
        weaponOverdriveFactor() *
        weaponUpgradePower()

/**
 * Honing of the equipped weapon: its own hits only (never [abilityPower]), read by the current weapon so the
 * bonus follows a Totem choice's weapon change or a weapon choice.
 */
internal fun MutableGameState.weaponUpgradePower(): Float =
    1f + content.upgrades.weaponPowerPerLevel * weaponUpgradeLevels[weapon.ordinal]

/**
 * Power of character abilities and weapon-based item effects. It shares the weapon's level, mastery and
 * Overdrive factors but grows with [kinetickk.ball.content.api.RunTempoProfile.abilityRunGrowth], so
 * equipped-weapon bonuses applied only to [effectiveWeaponPower] never leak into abilities.
 */
internal fun MutableGameState.abilityPower(): Float =
    weaponPower *
        rebirthProfile.playerPowerMultiplier *
        (1f + (weaponLevel - 1) * 0.08f) *
        (1f + currentWeaponMastery.damageBonus) *
        content.tempo.abilityRunGrowth(elapsed) *
        if (overdriveTime > 0f) 1.45f else 1f

internal fun MutableGameState.cooldown(base: Float): Float =
    base / (
        attackSpeed *
            (1f + currentWeaponMastery.activationSpeedBonus) *
            buildActivationBonus() *
            characterActivationMultiplier() *
            (if (overdriveTime > 0f) 1.35f else 1f)
        ).coerceAtLeast(0.2f)
