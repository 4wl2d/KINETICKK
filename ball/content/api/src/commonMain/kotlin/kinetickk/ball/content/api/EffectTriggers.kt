// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

enum class EffectTrigger(val displayLabel: String) {
    IMPACT("On impact"),
    WEAPON_HIT("On weapon hit"),
    CRIT("On critical hit"),
    KILL("On kill"),
    DASH("On dash"),
    DASH_HIT("On dash hit"),
    BRAKE_RELEASE("On brake release"),
    TURN("On turn"),
    HURT("When hit"),
    SHIELD_BREAK("On shield break"),
    OVERDRIVE("On Overdrive"),
    OVERHEAT("On overheat"),
    REACTION("On reaction"),
    BREAK("On break"),
    LEVEL_UP("On level up"),
    PICKUP("On data pickup"),
    /** The Core bounced off an obstacle fast enough to count as a collision. */
    RICOCHET("On ricochet"),
    /** An enemy struck an obstacle or another enemy hard enough to take slam damage; it is the clause target. */
    SLAM("On slam"),
    /** An active effect was cast; its primary target (if any) is the clause target, else the effect starts at its anchor. */
    SKILL_CAST("On active cast"),
    /** One of the first four enemies an active cast damages; it is the clause target and the damage is the hit. */
    SKILL_HIT("On active hit"),
    /** The Apex activated. */
    APEX("On Apex"),
    /** The Core entered one of your fields; the effect starts at the field's centre. */
    FIELD_ENTER("On entering a field"),
    /** One of your effects erased a hostile bolt; the effect starts where the bolt was. */
    SHOT_ERASED("On erasing a shot"),
    /** You cashed a mark (a Spall throw, a Cracked detonation, a Void Anchor blink, a ringing Shatter); the marked enemy is the clause target. */
    MARK_CONSUMED("On cashing a mark"),
    /** An obstacle broke: a crystal shattered, an unstable asteroid burst or a derelict mine went off; the effect starts at it. */
    SHATTER("On breaking an obstacle"),
    /** An obstacle sped the Core up: an accelerator gate, a gravity-knot sling or a vent's updraft. */
    BOOST("On boost"),
    /** The Core skimmed a grazeable obstacle fast without touching it. */
    GRAZE("On graze"),
}

/**
 * Triggers whose clause target is a live enemy the trigger is about. Target conditions need one of them (or
 * [EffectTrigger.KILL]); marks and throws need one of them.
 */
val TargetedTriggers: Set<EffectTrigger> = setOf(
    EffectTrigger.IMPACT, EffectTrigger.WEAPON_HIT, EffectTrigger.CRIT, EffectTrigger.DASH_HIT,
    EffectTrigger.REACTION, EffectTrigger.BREAK, EffectTrigger.SLAM, EffectTrigger.SKILL_HIT,
    EffectTrigger.MARK_CONSUMED,
)

/** A clause condition. Every value is validated at construction. */
sealed interface EffectCondition {
    data class SpeedAtLeast(val speed: Float) : EffectCondition {
        init { require(speed.isFinite() && speed > 0f) }
    }
    data class SpeedBelow(val speed: Float) : EffectCondition {
        init { require(speed.isFinite() && speed > 0f) }
    }
    data class IntegrityBelow(val fraction: Float) : EffectCondition {
        init { require(fraction.isFinite() && fraction > 0f && fraction < 1f) }
    }
    data class HeatAtLeast(val heat: Float) : EffectCondition {
        init { require(heat.isFinite() && heat > 0f) }
    }
    data object ShieldUp : EffectCondition
    data object OverdriveActive : EffectCondition
    data object Braking : EffectCondition
    data class ComboAtLeast(val combo: Int) : EffectCondition {
        init { require(combo > 0) }
    }
    /** [element] null means any status. */
    data class TargetHas(val element: StatusElement?, val stacks: Int = 1) : EffectCondition {
        init { require(stacks > 0) }
    }
    data object TargetElite : EffectCondition
    data object TargetFrozen : EffectCondition
    data class TargetBelow(val fraction: Float) : EffectCondition {
        init { require(fraction.isFinite() && fraction > 0f && fraction < 1f) }
    }
    data object SourceImpact : EffectCondition
    data object SourceWeapon : EffectCondition
    /** For BREAK and REACTION triggers: the element that broke or took part (on skill triggers: the skill's element). */
    data class ElementIs(val element: StatusElement) : EffectCondition
    /** The reaction that fired (REACTION), made the entered field (FIELD_ENTER) or left the cashed mark (MARK_CONSUMED). */
    data class ReactionIs(val reaction: ReactionId) : EffectCondition
    /** The skill that cast or hit (SKILL_CAST, SKILL_HIT). */
    data class SkillIs(val skill: SkillId) : EffectCondition
    /** The skill that cast or hit carries [tag] (SKILL_CAST, SKILL_HIT). */
    data class SkillTagged(val tag: BuildTag) : EffectCondition
    /** The Core stands inside one of your fields. */
    data object CoreInField : EffectCondition
    /** The target carries a live mark, Blackfire or Embrittle, or is the Void Anchor. */
    data object TargetMarked : EffectCondition
    /** The target is Frozen, stunned or stopped, or silenced. */
    data object TargetControlled : EffectCondition
    /** The target is flying from a throw. */
    data object TargetThrown : EffectCondition
    /** The Apex is running. */
    data object ApexActive : EffectCondition
    /** At least [count] owned skills are recharging. */
    data class SkillsCharging(val count: Int) : EffectCondition {
        init { require(count in 1..ContentBounds.SKILL_SLOTS) { "Skills charging count must be 1..${ContentBounds.SKILL_SLOTS}" } }
    }
    /** The equipped weapon belongs to [family] (follows a mid-run weapon switch). */
    data class WeaponFamilyIs(val family: WeaponFamily) : EffectCondition
    /** The obstacle the trigger is about (RICOCHET, SLAM against an obstacle, HURT from one, SHATTER, BOOST, GRAZE). */
    data class ObstacleIs(val kind: ObstacleKind) : EffectCondition
    /** The Core's surface is within [distance] of a solid (materialised) obstacle's surface. */
    data class NearObstacle(val distance: Float) : EffectCondition {
        init {
            require(distance.isFinite() && distance in 40f..NEAR_OBSTACLE_MAX_DISTANCE) {
                "Near-obstacle distance must be in 40..$NEAR_OBSTACLE_MAX_DISTANCE"
            }
        }
    }
}

/** Triggers that carry the obstacle they are about, so [EffectCondition.ObstacleIs] can hold on them. */
val ObstacleTriggers: Set<EffectTrigger> = setOf(
    EffectTrigger.RICOCHET, EffectTrigger.SLAM, EffectTrigger.HURT, EffectTrigger.SHATTER, EffectTrigger.BOOST, EffectTrigger.GRAZE,
)

/**
 * True when [this] condition can hold on clauses of [trigger]: dispatch facts exist only on the triggers that
 * carry them (a reaction, a skill, a target).
 */
fun EffectCondition.validOn(trigger: EffectTrigger): Boolean = when (this) {
    is EffectCondition.ReactionIs ->
        trigger == EffectTrigger.REACTION || trigger == EffectTrigger.FIELD_ENTER || trigger == EffectTrigger.MARK_CONSUMED
    is EffectCondition.SkillIs, is EffectCondition.SkillTagged ->
        trigger == EffectTrigger.SKILL_CAST || trigger == EffectTrigger.SKILL_HIT
    is EffectCondition.TargetHas, EffectCondition.TargetElite, EffectCondition.TargetFrozen, is EffectCondition.TargetBelow,
    EffectCondition.TargetMarked, EffectCondition.TargetControlled, EffectCondition.TargetThrown,
    -> trigger in TargetedTriggers || trigger == EffectTrigger.KILL
    is EffectCondition.ObstacleIs -> trigger in ObstacleTriggers
    else -> true
}

/** Conditions that read a dispatch (the reaction, skill or obstacle involved); item rules hold outside any dispatch. */
val EffectCondition.needsDispatch: Boolean
    get() = this is EffectCondition.ReactionIs || this is EffectCondition.SkillIs || this is EffectCondition.SkillTagged ||
        this is EffectCondition.ObstacleIs

/** What a scaled amount is measured against. */
enum class DamageBasis {
    /** A fraction of the triggering hit. */
    HIT,
    /** Impact power at the Core's current speed. */
    IMPACT,
    /** Impact power at the speed shed while braking. */
    BRAKE,
    /** 40 × effective weapon power. */
    WEAPON,
    /** A flat amount scaled by run progress and Rebirth power. */
    FLAT,
    /**
     * A fraction of the victim's maximum integrity, weighted per enemy frame: heavy frames, Elites and the
     * Architect take less.
     */
    VICTIM_MAX_HP,
    /** A fraction of the shield capacity. */
    SHIELD,
}

/** An explicit element, or the build's dominant / secondary affinity at the moment of use. */
enum class ElementPick(val element: StatusElement?) {
    THERMAL(StatusElement.THERMAL),
    CRYO(StatusElement.CRYO),
    ION(StatusElement.ION),
    GRAVITIC(StatusElement.GRAVITIC),
    ENTROPY(StatusElement.ENTROPY),
    SONIC(StatusElement.SONIC),
    VOID(StatusElement.VOID),
    DOMINANT(null),
    SECONDARY(null);

    companion object {
        fun of(element: StatusElement): ElementPick = entries.first { it.element == element }
    }
}

/** Timed or conditional modifiers. Damage-like values are fractions (0.2 = +20 %). */
enum class BuffStat(val displayLabel: String) {
    DAMAGE("Damage"),
    IMPACT("Impact damage"),
    WEAPON("Weapon power"),
    ACTIVATION("Activation speed"),
    CRIT_CHANCE("Critical chance"),
    DAMAGE_REDUCTION("Damage reduction"),
    COOLING("Cooling"),
    DAMAGE_TAKEN("Damage taken"),
    /** Active-effect cooldowns tick faster by the amount (through the refund bank). */
    SKILL_RECHARGE("Active recharge"),
    /** Active-effect damage grows by the amount. */
    SKILL_POWER("Active power"),
}

/** Which skills a [EffectAction.RefundSkills] recharges. */
enum class SkillPick {
    ALL,
    /** The skill that dispatched the clause (on other triggers: the slowest). */
    CAST,
    /** The skill with the longest cooldown left. */
    LONGEST,
}

/** The kind of item field a [EffectAction.Zone] opens. */
enum class ZoneKind { PLASMA, HUSH, TIME_BUBBLE, ATTRACTOR, MAW }

/** Where a [EffectAction.CoreImpulse] launches the Core. */
enum class ImpulseDirection { POINTER, AWAY_FROM_TARGET, FORWARD }

/** The mark a [EffectAction.ApplyMark] writes. */
enum class MarkPick { SPALL, CRACKED, SPORED, EMBRITTLED }

/** What a [ItemRule.ReactionTuning] scales: reach, how long it lasts, or how hard it moves things. */
enum class ReactionAspect { RADIUS, DURATION, FORCE }

/** What a [ItemRule.FieldTuning] scales. */
enum class FieldAspect { RADIUS, DURATION }
