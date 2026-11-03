// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.abs
import kotlin.math.floor

internal const val PERCENT_DECIMALS = 1
internal const val FLAT_DECIMALS = 1
internal const val SECOND_DECIMALS = 2
internal const val DAMAGE_DECIMALS = 0
internal const val MAX_SHORTENING = 0.95

/** Venting never removes more than the whole 0..100 heat gauge. */
internal const val MAX_HEAT = 100.0
internal const val ROUNDING_EPSILON = 1e-6

/**
 * Formats a display number, rounding its size half up to [decimals] and trimming trailing zeros.
 * Negative values keep a minus sign unless they round to zero. English groups thousands with
 * commas and uses a dot; Russian groups with a no-break space and uses a comma.
 */
internal fun AppLanguage.number(value: Double, decimals: Int): String {
    require(value.isFinite()) { "Display numbers must be finite" }
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = floor(abs(value) * factor + 0.5 + ROUNDING_EPSILON).toLong()
    val sign = if (value < 0.0 && scaled != 0L) "−" else ""
    val whole = (scaled / factor).toString()
    val grouping = if (this == AppLanguage.Russian) '\u00A0' else ','
    val grouped = if (whole.length < 4) whole else buildString {
        whole.forEachIndexed { index, digit ->
            if (index > 0 && (whole.length - index) % 3 == 0) append(grouping)
            append(digit)
        }
    }
    val fraction = scaled % factor
    if (fraction == 0L) return sign + grouped
    val digits = fraction.toString().padStart(decimals, '0').trimEnd('0')
    return sign + grouped + (if (this == AppLanguage.Russian) ',' else '.') + digits
}

internal val ItemEffect.phrase: BuildRulesText
    get() = when (this) {
        ItemEffect.IMPACT_DAMAGE -> BuildRulesText.StatImpactDamage
        ItemEffect.WEAPON_POWER -> BuildRulesText.StatWeaponPower
        ItemEffect.MASS -> BuildRulesText.StatMass
        ItemEffect.MAGNETISM -> BuildRulesText.StatMagnetism
        ItemEffect.COOLING -> BuildRulesText.StatCooling
        ItemEffect.MAX_INTEGRITY -> BuildRulesText.StatMaxIntegrity
        ItemEffect.REGEN -> BuildRulesText.StatRegeneration
        ItemEffect.DASH_POWER -> BuildRulesText.StatDashPower
        ItemEffect.DASH_EFFICIENCY -> BuildRulesText.StatDashEfficiency
        ItemEffect.CRIT_CHANCE -> BuildRulesText.StatCritChance
        ItemEffect.CRIT_DAMAGE -> BuildRulesText.StatCritDamage
        ItemEffect.PICKUP_RADIUS -> BuildRulesText.StatPickupRadius
        ItemEffect.LUCK -> BuildRulesText.StatLuck
        ItemEffect.DATA_GAIN -> BuildRulesText.StatDataGain
        ItemEffect.MATTER_GAIN -> BuildRulesText.StatMatterGain
        ItemEffect.ATTACK_SPEED -> BuildRulesText.StatAttackSpeed
        ItemEffect.SHIELD_CAPACITY -> BuildRulesText.StatShieldCapacity
        ItemEffect.DAMAGE_REDUCTION -> BuildRulesText.StatDamageReduction
        ItemEffect.COMBO_WINDOW -> BuildRulesText.StatComboWindow
        ItemEffect.OVERDRIVE_GAIN -> BuildRulesText.StatOverdriveGain
        ItemEffect.GRIP -> BuildRulesText.StatGrip
        ItemEffect.SLING -> BuildRulesText.StatSling
    }

internal val BuffStat.phrase: BuildRulesText
    get() = when (this) {
        BuffStat.DAMAGE -> BuildRulesText.BuffDamage
        BuffStat.IMPACT -> BuildRulesText.BuffImpact
        BuffStat.WEAPON -> BuildRulesText.BuffWeapon
        BuffStat.ACTIVATION -> BuildRulesText.BuffActivation
        BuffStat.CRIT_CHANCE -> BuildRulesText.BuffCritChance
        BuffStat.DAMAGE_REDUCTION -> BuildRulesText.BuffDamageReduction
        BuffStat.COOLING -> BuildRulesText.BuffCooling
        BuffStat.DAMAGE_TAKEN -> BuildRulesText.BuffDamageTaken
        BuffStat.SKILL_RECHARGE -> BuildRulesText.BuffSkillRecharge
        BuffStat.SKILL_POWER -> BuildRulesText.BuffSkillPower
    }

internal val SkillPick.phrase: BuildRulesText
    get() = when (this) {
        SkillPick.ALL -> BuildRulesText.PickAllSkills
        SkillPick.CAST -> BuildRulesText.PickThisSkill
        SkillPick.LONGEST -> BuildRulesText.PickSlowestSkill
    }

internal val ZoneKind.phrase: BuildRulesText
    get() = when (this) {
        ZoneKind.PLASMA -> BuildRulesText.ZonePlasma
        ZoneKind.HUSH -> BuildRulesText.ZoneHush
        ZoneKind.TIME_BUBBLE -> BuildRulesText.ZoneTimeBubble
        ZoneKind.ATTRACTOR -> BuildRulesText.ZoneAttractor
        ZoneKind.MAW -> BuildRulesText.ZoneMaw
    }

internal val ImpulseDirection.phrase: BuildRulesText
    get() = when (this) {
        ImpulseDirection.POINTER -> BuildRulesText.ImpulsePointer
        ImpulseDirection.AWAY_FROM_TARGET -> BuildRulesText.ImpulseAway
        ImpulseDirection.FORWARD -> BuildRulesText.ImpulseForward
    }

internal val MarkPick.phrase: BuildRulesText
    get() = when (this) {
        MarkPick.SPALL -> BuildRulesText.MarkSpall
        MarkPick.CRACKED -> BuildRulesText.MarkCracked
        MarkPick.SPORED -> BuildRulesText.MarkSpored
        MarkPick.EMBRITTLED -> BuildRulesText.MarkEmbrittled
    }

internal val ReactionAspect.phrase: BuildRulesText
    get() = when (this) {
        ReactionAspect.RADIUS -> BuildRulesText.AspectRadius
        ReactionAspect.DURATION -> BuildRulesText.AspectDuration
        ReactionAspect.FORCE -> BuildRulesText.AspectForce
    }

/** Shipped reaction names and keystone forms; mechanical text names reactions the way the catalog does. */
internal val DefaultReactions: BuildCatalog get() = defaultBuildCatalog()

internal val DefaultSkillNames: List<String> by lazy { defaultSkillDefinitions().map { it.name } }

/** The "On …" phrase matching [EffectTrigger.displayLabel]. */
val EffectTrigger.onPhrase: BuildRulesText
    get() = when (this) {
        EffectTrigger.IMPACT -> BuildRulesText.OnImpact
        EffectTrigger.WEAPON_HIT -> BuildRulesText.OnWeaponHit
        EffectTrigger.CRIT -> BuildRulesText.OnCrit
        EffectTrigger.KILL -> BuildRulesText.OnKill
        EffectTrigger.DASH -> BuildRulesText.OnDash
        EffectTrigger.DASH_HIT -> BuildRulesText.OnDashHit
        EffectTrigger.BRAKE_RELEASE -> BuildRulesText.OnBrakeRelease
        EffectTrigger.TURN -> BuildRulesText.OnTurn
        EffectTrigger.HURT -> BuildRulesText.OnHurt
        EffectTrigger.SHIELD_BREAK -> BuildRulesText.OnShieldBreak
        EffectTrigger.OVERDRIVE -> BuildRulesText.OnOverdrive
        EffectTrigger.OVERHEAT -> BuildRulesText.OnOverheat
        EffectTrigger.REACTION -> BuildRulesText.OnReaction
        EffectTrigger.BREAK -> BuildRulesText.OnBreak
        EffectTrigger.LEVEL_UP -> BuildRulesText.OnLevelUp
        EffectTrigger.PICKUP -> BuildRulesText.OnPickup
        EffectTrigger.RICOCHET -> BuildRulesText.OnRicochet
        EffectTrigger.SLAM -> BuildRulesText.OnSlam
        EffectTrigger.SKILL_CAST -> BuildRulesText.OnSkillCast
        EffectTrigger.SKILL_HIT -> BuildRulesText.OnSkillHit
        EffectTrigger.APEX -> BuildRulesText.OnApex
        EffectTrigger.FIELD_ENTER -> BuildRulesText.OnFieldEnter
        EffectTrigger.SHOT_ERASED -> BuildRulesText.OnShotErased
        EffectTrigger.MARK_CONSUMED -> BuildRulesText.OnMarkConsumed
        EffectTrigger.SHATTER -> BuildRulesText.OnShatter
        EffectTrigger.BOOST -> BuildRulesText.OnBoost
        EffectTrigger.GRAZE -> BuildRulesText.OnGraze
    }

internal val EffectTrigger.everyPhrase: BuildRulesText
    get() = when (this) {
        EffectTrigger.IMPACT -> BuildRulesText.EveryImpact
        EffectTrigger.WEAPON_HIT -> BuildRulesText.EveryWeaponHit
        EffectTrigger.CRIT -> BuildRulesText.EveryCrit
        EffectTrigger.KILL -> BuildRulesText.EveryKill
        EffectTrigger.DASH -> BuildRulesText.EveryDash
        EffectTrigger.DASH_HIT -> BuildRulesText.EveryDashHit
        EffectTrigger.BRAKE_RELEASE -> BuildRulesText.EveryBrakeRelease
        EffectTrigger.TURN -> BuildRulesText.EveryTurn
        EffectTrigger.HURT -> BuildRulesText.EveryHurt
        EffectTrigger.SHIELD_BREAK -> BuildRulesText.EveryShieldBreak
        EffectTrigger.OVERDRIVE -> BuildRulesText.EveryOverdrive
        EffectTrigger.OVERHEAT -> BuildRulesText.EveryOverheat
        EffectTrigger.REACTION -> BuildRulesText.EveryReaction
        EffectTrigger.BREAK -> BuildRulesText.EveryBreak
        EffectTrigger.LEVEL_UP -> BuildRulesText.EveryLevelUp
        EffectTrigger.PICKUP -> BuildRulesText.EveryPickup
        EffectTrigger.RICOCHET -> BuildRulesText.EveryRicochet
        EffectTrigger.SLAM -> BuildRulesText.EverySlam
        EffectTrigger.SKILL_CAST -> BuildRulesText.EverySkillCast
        EffectTrigger.SKILL_HIT -> BuildRulesText.EverySkillHit
        EffectTrigger.APEX -> BuildRulesText.EveryApex
        EffectTrigger.FIELD_ENTER -> BuildRulesText.EveryFieldEnter
        EffectTrigger.SHOT_ERASED -> BuildRulesText.EveryShotErased
        EffectTrigger.MARK_CONSUMED -> BuildRulesText.EveryMarkConsumed
        EffectTrigger.SHATTER -> BuildRulesText.EveryShatter
        EffectTrigger.BOOST -> BuildRulesText.EveryBoost
        EffectTrigger.GRAZE -> BuildRulesText.EveryGraze
    }

/** How rule text names an obstacle kind ("On boost (gravity knot)"). */
val ObstacleKind.ruleName: BuildRulesText
    get() = when (this) {
        ObstacleKind.ASTEROID -> BuildRulesText.ObstacleAsteroid
        ObstacleKind.BUMPER -> BuildRulesText.ObstacleBumper
        ObstacleKind.UNSTABLE -> BuildRulesText.ObstacleUnstable
        ObstacleKind.GRAVITY_WELL -> BuildRulesText.ObstacleGravityWell
        ObstacleKind.ACCEL_GATE -> BuildRulesText.ObstacleAccelGate
        ObstacleKind.CRYSTAL -> BuildRulesText.ObstacleCrystal
        ObstacleKind.DERELICT_MINE -> BuildRulesText.ObstacleDerelictMine
        ObstacleKind.PHASE_SHARD -> BuildRulesText.ObstaclePhaseShard
        ObstacleKind.THERMAL_VENT -> BuildRulesText.ObstacleThermalVent
    }
