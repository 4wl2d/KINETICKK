// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.WeaponSynergyProjection
import kotlin.math.min
import kotlin.math.pow

/*
 * The weapon synergy rules (SPEC 10.2): what links the equipped weapon to the build's tags, the
 * character, the bound Relics, the element affinity and one scaling stat. Every term is a pure read of
 * owned State; effectiveWeaponPower multiplies them once.
 */

/** Weapon power per point in the weapon's two tags, up to [TAG_RESONANCE_CAP]. */
internal const val TAG_RESONANCE_PER_POINT: Float = 0.05f
internal const val TAG_RESONANCE_CAP: Float = 0.5f

/** The signature character's weapon power and its start level. */
internal const val SIGNATURE_POWER: Float = 1.2f
internal const val SIGNATURE_START_LEVELS: Int = 1

/** A bound Relic carrying the weapon's key: weapon power, and that Relic's upside magnitude. */
internal const val HARMONY_POWER: Float = 1.2f
internal const val HARMONY_MAGNITUDE: Float = 1.1f

/** Scaling multipliers stay within [1, SCALING_MAX]. */
internal const val SCALING_MAX: Float = 1.6f

/** CRIT scaling's extra crit chance on the weapon's own hits; STATUS scaling's bonus against statused targets. */
internal const val CRIT_SCALING_CHANCE: Float = 0.15f
internal const val STATUS_SCALING_BONUS: Float = 1.4f

/** DASH scaling's surge after a player Dash. */
internal const val DASH_SCALING_SURGE: Float = 1.3f
internal const val DASH_SCALING_SECONDS: Float = 0.75f

/** Overdrive's generic weapon power (an OVERDRIVE-scaling weapon takes its scaling instead). */
internal const val OVERDRIVE_WEAPON_POWER: Float = 1.45f

/** Kinetic weapons adopt the dominant element on every this-many qualifying hit at affinity tier 2. */
internal const val KINETIC_INFUSION_EVERY: Int = 3
internal const val KINETIC_INFUSION_TIER: Int = 2

/** Levels at which the three mastery forms open. */
internal val MASTERY_FORM_LEVELS: IntArray = intArrayOf(3, 6, 10)

/** Points the build holds in the weapon's two tags. */
internal fun MutableGameState.weaponTagPoints(definition: WeaponDefinition = currentWeaponDefinition): Int {
    var points = 0
    val tags = definition.tags
    for (index in tags.indices) points += build.tagPoints(tags[index])
    return points
}

/** Tag resonance: +5 % weapon power per point in the weapon's tags, at most +50 %. */
internal fun MutableGameState.weaponTagBonus(definition: WeaponDefinition = currentWeaponDefinition): Float =
    min(TAG_RESONANCE_CAP, TAG_RESONANCE_PER_POINT * weaponTagPoints(definition))

/** True when the run's character is the weapon's signature character. */
internal fun MutableGameState.signatureActive(definition: WeaponDefinition = currentWeaponDefinition): Boolean =
    coreShape == definition.signature

/** Levels the signature character adds to the run's start and to the automatic weapon ceiling. */
internal fun MutableGameState.signatureLevelBonus(): Int = if (signatureActive()) SIGNATURE_START_LEVELS else 0

/**
 * The first bound Relic (the chosen heirloom is bound like any other) that carries the weapon's key, or
 * null: that Relic harmonizes with the weapon. Read from the compiled build (no list scan on the hot path).
 */
internal fun MutableGameState.harmonizingRelic(definition: WeaponDefinition = currentWeaponDefinition): RelicId? =
    build.harmonizing(definition.key)

/**
 * The weapon's scaling stat as a multiplier in [1, [SCALING_MAX]]. The stat a weapon scales with never
 * also appears in its base damage or count formula. CRIT and STATUS act per hit (see dealWeaponDamage).
 */
internal fun MutableGameState.weaponScalingMultiplier(definition: WeaponDefinition = currentWeaponDefinition): Float {
    val raw = when (definition.scaling) {
        WeaponScaling.SPEED -> 1f + 0.6f * ((speed - 400f) / 1_600f).coerceIn(0f, 1f)
        WeaponScaling.MASS -> min(SCALING_MAX, effectiveMass().pow(0.8f))
        WeaponScaling.HEAT -> 1f + 0.6f * (heat / MutableGameState.MAX_HEAT).coerceIn(0f, 1f)
        WeaponScaling.COMBO -> 1f + 0.6f * min(combo, 50) / 50f
        WeaponScaling.SHIELD -> {
            val capacity = shieldCapacity()
            if (capacity > 0f) 1f + 0.5f * (shield / capacity).coerceIn(0f, 1f) else 1f
        }
        WeaponScaling.BRAKE -> 1f + 0.6f * min(1f, weaponRuntime.recentShed() / 800f)
        WeaponScaling.DASH -> min(SCALING_MAX, effectiveDashImpulse() / BASE_DASH_IMPULSE) *
            if (elapsed - weaponRuntime.lastDashAt < DASH_SCALING_SECONDS) DASH_SCALING_SURGE else 1f
        WeaponScaling.CRIT, WeaponScaling.STATUS -> 1f
        WeaponScaling.OVERDRIVE -> if (overdriveTime > 0f) 1.5f else 1f
        WeaponScaling.LUCK -> 1f + 0.3f * min(effectiveLuck(), 2f)
        WeaponScaling.ATTACK_SPEED -> 1f + 0.5f * (attackSpeed - 1f)
    }
    return clampScaling(raw)
}

/** NaN-safe clamp into [1, SCALING_MAX]. */
internal fun clampScaling(value: Float): Float = if (!(value >= 1f)) 1f else min(value, SCALING_MAX)

/** Base Dash impulse the DASH scaling measures against. */
internal const val BASE_DASH_IMPULSE: Float = 590f

/** The lasting synergy multipliers on the weapon's own power: tag resonance, signature and harmony. */
internal fun MutableGameState.permanentWeaponSynergy(definition: WeaponDefinition = currentWeaponDefinition): Float {
    var power = 1f + weaponTagBonus(definition)
    if (signatureActive(definition)) power *= SIGNATURE_POWER
    if (harmonizingRelic(definition) != null) power *= HARMONY_POWER
    return power
}

/** Every synergy multiplier on the weapon's own power: the lasting ones and the live scaling stat. */
internal fun MutableGameState.weaponSynergyPower(): Float {
    val definition = currentWeaponDefinition
    return permanentWeaponSynergy(definition) * weaponScalingMultiplier(definition)
}

/** Overdrive's generic weapon power; an OVERDRIVE-scaling weapon takes Overdrive through its scaling only. */
internal fun MutableGameState.weaponOverdriveFactor(): Float =
    if (overdriveTime > 0f && currentWeaponDefinition.scaling != WeaponScaling.OVERDRIVE) OVERDRIVE_WEAPON_POWER else 1f

/**
 * Rate multiplier of CONTINUOUS and CONTACT weapons (damage and orbit or sweep speed): three quarters
 * of the attack-speed, mastery and build activation gain. Discrete weapons take [cooldown] instead.
 */
internal fun MutableGameState.continuousWeaponRate(): Float {
    val rate = attackSpeed * (1f + currentWeaponMastery.activationSpeedBonus) * buildActivationBonus()
    val scaled = 1f + 0.75f * (rate - 1f)
    return if (!(scaled >= 0.25f)) 0.25f else scaled
}

/** The element the weapon's hits apply now: its own, or the dominant one a kinetic weapon adopts. */
internal fun MutableGameState.weaponInfusionElement(definition: WeaponDefinition = currentWeaponDefinition): StatusElement? {
    definition.element?.let { return it }
    val dominant = build.dominant ?: return null
    return if (affinityTier(dominant) >= KINETIC_INFUSION_TIER) dominant else null
}

/** Mastery forms open at the weapon's current level (0..3). */
internal fun MutableGameState.openMasteryForms(): Int {
    var forms = 0
    for (level in MASTERY_FORM_LEVELS) if (weaponLevel >= level) forms++
    return forms
}

/** True once the weapon's [form]-th mastery form (1..3) is open. */
internal fun MutableGameState.hasMasteryForm(form: Int): Boolean = weaponLevel >= MASTERY_FORM_LEVELS[form - 1]

/** The synergy projection the pause build screen reads. */
internal fun MutableGameState.weaponSynergyProjection(reusable: WeaponSynergyProjection?): WeaponSynergyProjection {
    val definition = currentWeaponDefinition
    val forms = openMasteryForms()
    val projection = WeaponSynergyProjection(
        tagPoints = weaponTagPoints(definition),
        tagBonus = weaponTagBonus(definition),
        signatureActive = signatureActive(definition),
        harmonizingRelic = harmonizingRelic(definition),
        infusionElement = weaponInfusionElement(definition),
        scalingMultiplier = weaponScalingMultiplier(definition),
        masteryForms = forms,
        activeMasteryForm = if (forms > 0) definition.masteryForms[forms - 1] else null,
    )
    return if (reusable != null && reusable == projection) reusable else projection
}

/** Distance helper for weapon geometry. */
internal fun distanceBetween(x0: Float, y0: Float, x1: Float, y1: Float): Float = length(x1 - x0, y1 - y0)
