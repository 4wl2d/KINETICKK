// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * Mechanical archetypes. A build is never a fixed class: every owned item adds points to its tags
 * and the totals unlock resonance tiers.
 */
enum class BuildTag(val displayLabel: String) {
    RAM("Ram"),
    DRIVE("Drive"),
    ANCHOR("Anchor"),
    ARSENAL("Arsenal"),
    PRECISION("Precision"),
    BULWARK("Bulwark"),
    OVERCLOCK("Overclock"),
    CHAIN("Chain"),
    FORTUNE("Fortune"),
}

/**
 * Elemental fields. Impacts and weapons deal Kinetic damage, which carries no status. New elements are
 * appended: gameplay, presentation and the profile index cells and colors by ordinal.
 */
enum class StatusElement(
    val displayLabel: String,
    val statusLabel: String,
    val breakLabel: String,
) {
    THERMAL("Thermal", "Scorch", "Ignite"),
    CRYO("Cryo", "Chill", "Freeze"),
    ION("Ion", "Charge", "Overload"),
    GRAVITIC("Gravitic", "Mass", "Collapse"),
    ENTROPY("Entropy", "Decay", "Rupture"),
    SONIC("Sonic", "Resonance", "Shatter"),
    VOID("Void", "Hollow", "Unmake"),
}

/** Captured status tuning; every number is mechanical content, never presentation. */
data class ElementDefinition(
    val element: StatusElement,
    val statusDescription: String,
    val breakDescription: String,
    val breakThreshold: Int,
    val decaySeconds: Float,
    /** Per-stack strength: damage per second, slow fraction, arc damage or damage-taken bonus. */
    val perStack: Float,
    /** Break strength: area damage per stack, freeze seconds or a fraction of maximum integrity. */
    val breakPower: Float,
    val breakRadius: Float,
    /** No combination of affinity and item rules lowers the threshold below this floor. */
    val breakFloor: Int,
) {
    init {
        require(statusDescription.isNotBlank() && breakDescription.isNotBlank())
        require(breakThreshold in 2..ContentBounds.MAX_STATUS_THRESHOLD)
        require(breakFloor in 2..breakThreshold)
        require(decaySeconds.isFinite() && decaySeconds > 0f)
        require(perStack.isFinite() && perStack > 0f)
        require(breakPower.isFinite() && breakPower > 0f)
        require(breakRadius.isFinite() && breakRadius >= 0f)
    }

    /** Stacks above the threshold only deepen reactions and breaks. */
    val stackCap: Int get() = breakThreshold + STATUS_OVERFLOW_STACKS

    companion object {
        const val STATUS_OVERFLOW_STACKS: Int = 4
    }
}

/**
 * The twenty-one unordered element pairs. Each pair has exactly one reaction. Entries are appended in
 * release order; the profile stores discoveries by a stable wire id and gameplay by ordinal.
 */
enum class ReactionId(val first: StatusElement, val second: StatusElement) {
    THERMAL_SHOCK(StatusElement.THERMAL, StatusElement.CRYO),
    PLASMA_BURST(StatusElement.THERMAL, StatusElement.ION),
    FUSION(StatusElement.THERMAL, StatusElement.GRAVITIC),
    WILDFIRE(StatusElement.THERMAL, StatusElement.ENTROPY),
    SUPERCONDUCT(StatusElement.CRYO, StatusElement.ION),
    GLACIER_CRUSH(StatusElement.CRYO, StatusElement.GRAVITIC),
    EMBRITTLE(StatusElement.CRYO, StatusElement.ENTROPY),
    MAGNETIZE(StatusElement.ION, StatusElement.GRAVITIC),
    FEEDBACK(StatusElement.ION, StatusElement.ENTROPY),
    ROT_WELL(StatusElement.GRAVITIC, StatusElement.ENTROPY),
    THUNDERCLAP(StatusElement.THERMAL, StatusElement.SONIC),
    SHATTERPOINT(StatusElement.CRYO, StatusElement.SONIC),
    RESONANT_ARC(StatusElement.ION, StatusElement.SONIC),
    SEISMIC(StatusElement.GRAVITIC, StatusElement.SONIC),
    FRACTURE(StatusElement.ENTROPY, StatusElement.SONIC),
    SILENCE(StatusElement.SONIC, StatusElement.VOID),
    BLACKFIRE(StatusElement.THERMAL, StatusElement.VOID),
    STASIS(StatusElement.CRYO, StatusElement.VOID),
    ANNIHILATE(StatusElement.ION, StatusElement.VOID),
    NULL_WELL(StatusElement.GRAVITIC, StatusElement.VOID),
    OBLIVION(StatusElement.ENTROPY, StatusElement.VOID);

    fun involves(element: StatusElement): Boolean = first == element || second == element

    companion object {
        fun between(a: StatusElement, b: StatusElement): ReactionId? =
            entries.firstOrNull { (it.first == a && it.second == b) || (it.first == b && it.second == a) }
    }
}

data class ReactionDefinition(
    val id: ReactionId,
    val name: String,
    val description: String,
    /** Damage per consumed stack (or a fraction of maximum integrity for Feedback and Null Well). */
    val power: Float,
    val radius: Float,
    /** Multi-stage residue applied to every enemy the reaction touches. */
    val residue: StatusElement? = null,
    val residueStacks: Int = 0,
    /**
     * Content-owned numbers of the reaction's play-style behaviour (its verb), read by index in the order
     * the gameplay rules document; empty while the reaction has none, and a reaction without values keeps
     * only its base effect.
     */
    val values: ImmutableList<Float> = immutableListOf(),
    /** The play-style behaviour's name (Spall, Plasma Pool, ...); blank while the reaction has none. */
    val verb: String = "",
    /** The decision the verb asks of the player, at most [ContentBounds.MAX_REACTION_CUE_CHARS] characters. */
    val cue: String = "",
    /** The name of the reaction's keystone form, which a keystone item switches it to; blank when none. */
    val keystone: String = "",
    /** What the keystone form changes. */
    val keystoneDescription: String = "",
) {
    init {
        require(name.isNotBlank() && description.isNotBlank())
        require(power.isFinite() && power > 0f)
        require(radius.isFinite() && radius >= 0f)
        require((residue == null) == (residueStacks == 0)) { "Residue element and stacks are declared together" }
        require(residueStacks in 0..3)
        require(values.size <= ContentBounds.MAX_REACTION_VALUES) {
            "Reaction values size ${values.size} exceeds ${ContentBounds.MAX_REACTION_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Reaction values must be finite" }
        require(verb.isBlank() == cue.isBlank()) { "A verb and its cue are declared together" }
        require(cue.length <= ContentBounds.MAX_REACTION_CUE_CHARS) {
            "Reaction cue length ${cue.length} exceeds ${ContentBounds.MAX_REACTION_CUE_CHARS}"
        }
        require(keystone.isBlank() == keystoneDescription.isBlank()) { "A keystone and its description are declared together" }
    }
}

/** Resonance tier text for one archetype; thresholds are shared [BuildPolicy] values. */
data class TagResonanceDefinition(
    val tag: BuildTag,
    val description: String,
    val tiers: ImmutableList<String>,
) {
    init {
        require(description.isNotBlank())
        require(tiers.size == ContentBounds.RESONANCE_TIERS && tiers.none(String::isBlank))
    }
}

/** StatusElement affinity tier text, shared by all elements. */
data class AffinityTierDefinition(val description: String) {
    init {
        require(description.isNotBlank())
    }
}

/**
 * The per-run draft deck: each run deals a share of every rank-eligible rarity tier into its deck, and only dealt items
 * reach ordinary offers. The floors keep every run playable: early choice, an early Rare for pity, the Data ladder, the
 * character's archetype, every archetype and element, the risk wagers and skill support. History weights lower the
 * chance that recently taken items are dealt again. [FULL] deals the whole catalog (characterisation tests).
 * Shares and minimums are by [ItemRarity] (Common to Legendary).
 */
data class DraftDeckPolicy(
    val shares: ImmutableList<Float> = immutableListOf(0.55f, 0.55f, 0.6f, 0.65f, 0.75f),
    val minimums: ImmutableList<Int> = immutableListOf(18, 18, 12, 8, 2),
    /** Commons and Uncommons unlocking by [earlyOpenUnlock], so the first offers have choice. */
    val earlyOpenFloor: Int = 10,
    val earlyOpenUnlock: Int = 2,
    /** Rare-or-better items unlocking by [earlyRareUnlock], so pity can resolve early. */
    val earlyRareFloor: Int = 5,
    val earlyRareUnlock: Int = 6,
    /**
     * Rank-0 Data-economy items of Rare or lower ([ItemDefinition.isDataEconomy], the level-42 Data ladder), and how many
     * of them unlock by [earlyDataUnlock].
     */
    val dataEconomyFloor: Int = 4,
    val earlyDataFloor: Int = 2,
    val earlyDataUnlock: Int = 5,
    /** Items of Rare or lower carrying the run character's archetype. */
    val characterTagFloor: Int = 7,
    /** Items of Rare or lower per [BuildTag]. */
    val tagFloor: Int = 5,
    /** Items of Rare or lower per [StatusElement]. */
    val elementFloor: Int = 4,
    /** Risk items (the wagers). */
    val riskFloor: Int = 4,
    /** Items using skill vocabulary ([ItemDefinition.usesSkills]). */
    val skillFloor: Int = 6,
    /** A tier holding fewer offerable dealt items than this deals its next offerable reserve item. */
    val refillFloor: Int = 3,
    /** Deck weight of an item the Profile has never discovered. */
    val undiscoveredWeight: Float = 1.2f,
    /** History: deck weight 1 / (1 + [historyPenalty] × recency penalty), never below [historyFloor]. */
    val historyPenalty: Float = 0.8f,
    val historyFloor: Float = 0.35f,
) {
    init {
        require(shares.size == ItemRarity.entries.size && shares.all { it.isFinite() && it > 0f && it <= 1f })
        require(minimums.size == ItemRarity.entries.size && minimums.all { it >= 0 })
        require(earlyOpenFloor >= 0 && earlyOpenUnlock >= 1 && earlyRareFloor >= 0 && earlyRareUnlock >= 1)
        require(dataEconomyFloor >= 0 && earlyDataFloor in 0..dataEconomyFloor && earlyDataUnlock >= 1)
        require(characterTagFloor >= 0 && tagFloor >= 0 && elementFloor >= 0 && riskFloor >= 0 && skillFloor >= 0)
        require(refillFloor in 1..8)
        require(undiscoveredWeight.isFinite() && undiscoveredWeight in 1f..2f)
        require(historyPenalty.isFinite() && historyPenalty > 0f && historyPenalty <= 2f)
        require(historyFloor.isFinite() && historyFloor > 0f && historyFloor <= 1f)
    }

    /** True when every share is 1 and every floor 0: the deck is the whole rank-eligible catalog. */
    val dealsEverything: Boolean
        get() = shares.all { it >= 1f }

    companion object {
        /** No deck: every rank-eligible item is dealt (characterisation and pre-deck comparisons). */
        val FULL: DraftDeckPolicy = DraftDeckPolicy(
            shares = immutableListOf(1f, 1f, 1f, 1f, 1f),
            minimums = immutableListOf(0, 0, 0, 0, 0),
            earlyOpenFloor = 0, earlyRareFloor = 0, dataEconomyFloor = 0, earlyDataFloor = 0, characterTagFloor = 0,
            tagFloor = 0, elementFloor = 0, riskFloor = 0, skillFloor = 0,
        )
    }
}

/**
 * The item offer's lanes. The aligned card picks, [alignedChance] of the time, among items that fit the build, weighted
 * 1 + [alignedFitWeight] × min(fit, [alignedFitCap]); a tag counts toward fit only with points the player chose
 * ([loadoutFits] false leaves the character's own point out, and lets an item naming the equipped weapon's family fit
 * only once the build took one), and half when already at the top resonance tier ([saturatedTagFit]). The volatile card lifts its rarity [volatileLiftChance] of the time and prefers risk items only
 * [volatileRiskChance] of the time. [RELEASE_040] is the 0.4.0 funnel, for comparisons.
 */
data class OfferLanePolicy(
    val alignedChance: Float = 0.55f,
    val alignedFitWeight: Float = 0.6f,
    val alignedFitCap: Float = 3f,
    val loadoutFits: Boolean = false,
    val saturatedTagFit: Float = 0.5f,
    val volatileLiftChance: Float = 0.6f,
    val volatileRiskChance: Float = 0.36f,
) {
    init {
        require(alignedChance.isFinite() && alignedChance in 0f..1f)
        require(alignedFitWeight.isFinite() && alignedFitWeight >= 0f)
        require(alignedFitCap.isFinite() && alignedFitCap > 0f || alignedFitCap == Float.POSITIVE_INFINITY)
        require(saturatedTagFit.isFinite() && saturatedTagFit in 0f..1f)
        require(volatileLiftChance.isFinite() && volatileLiftChance in 0f..1f)
        require(volatileRiskChance.isFinite() && volatileRiskChance in 0f..volatileLiftChance)
    }

    companion object {
        /** The 0.4.0 lanes: the loadout fits, no saturation, an uncapped 0.8 fit weight, risk on every lift. */
        val RELEASE_040: OfferLanePolicy = OfferLanePolicy(
            alignedChance = 0.75f, alignedFitWeight = 0.8f, alignedFitCap = Float.POSITIVE_INFINITY, loadoutFits = true,
            saturatedTagFit = 1f, volatileLiftChance = 0.6f, volatileRiskChance = 0.6f,
        )
    }
}

/** Catalog-wide build tuning captured with the content snapshot. */
data class BuildPolicy(
    val resonanceThresholds: ImmutableList<Int> = immutableListOf(3, 5, 7),
    val affinityThresholds: ImmutableList<Int> = immutableListOf(2, 4, 6),
    /** Magnitude multiplier of an item at 1, 2 and 3 stacks (diminishing returns). */
    val stackMagnitudes: ImmutableList<Float> = immutableListOf(1f, 1.7f, 2.2f),
    val rebornMagnitude: Float = 1.5f,
    val rebornChancePerTier: Float = 0.05f,
    val maximumRebornChance: Float = 0.4f,
    val attunementBonus: Float = 0.3f,
    val severanceSeconds: Float = 45f,
    val harmonyScale: Float = 1.1f,
    val dissonanceScale: Float = 0.85f,
    /** Damage pipeline: base × (1 + amp) × crit × (1 + vulnerability) × overrule. */
    val amplificationSoftCap: Float = 1.5f,
    val criticalMultiplierCap: Float = 4f,
    val vulnerabilityCap: Float = 1.5f,
    val overdriveGainCap: Float = 2.5f,
    /** Rolling per-second budgets for cascading combat work (reject past the budget, never clip). */
    val breaksPerSecond: Int = 40,
    val reactionsPerSecond: Int = 30,
    val maximumFields: Int = 12,
    /** Lingering fields reserved for skills (one per slot); reactions and items share the rest of [maximumFields]. */
    val maximumSkillFields: Int = 3,
    val reactionCooldownSeconds: Float = 0.5f,
    val reactionPairCooldownSeconds: Float = 2f,
    val masteredTagPoints: Int = 1,
    /**
     * Points an owned skill adds to each of its two tags and to its element's affinity (rank III adds as many
     * again to the element, or to the first tag of an element-less skill).
     */
    val skillTagPoints: Int = 1,
    /** The per-run draft deck of offerable items. */
    val draftDeck: DraftDeckPolicy = DraftDeckPolicy(),
    /** How the item offer's three lanes choose inside a rolled rarity. */
    val offerLanes: OfferLanePolicy = OfferLanePolicy(),
) {
    init {
        require(resonanceThresholds.size == ContentBounds.RESONANCE_TIERS)
        require(resonanceThresholds.zipWithNext().all { (a, b) -> a in 1 until b })
        require(affinityThresholds.size == ContentBounds.RESONANCE_TIERS)
        require(affinityThresholds.zipWithNext().all { (a, b) -> a in 1 until b })
        require(stackMagnitudes.size == ContentBounds.MAX_ITEM_STACKS)
        require(stackMagnitudes.all { it.isFinite() && it > 0f } && stackMagnitudes.zipWithNext().all { (a, b) -> a < b })
        require(rebornMagnitude.isFinite() && rebornMagnitude >= 1f)
        require(rebornChancePerTier.isFinite() && rebornChancePerTier in 0f..1f)
        require(maximumRebornChance.isFinite() && maximumRebornChance in 0f..1f)
        require(attunementBonus.isFinite() && attunementBonus >= 0f)
        require(severanceSeconds.isFinite() && severanceSeconds >= 0f)
        require(harmonyScale.isFinite() && harmonyScale > 0f)
        require(dissonanceScale.isFinite() && dissonanceScale > 0f)
        require(amplificationSoftCap.isFinite() && amplificationSoftCap > 0f)
        require(criticalMultiplierCap.isFinite() && criticalMultiplierCap >= 1f)
        require(vulnerabilityCap.isFinite() && vulnerabilityCap >= 0f)
        require(overdriveGainCap.isFinite() && overdriveGainCap >= 1f)
        require(breaksPerSecond in 1..240 && reactionsPerSecond in 1..240)
        require(maximumFields in 1..ContentBounds.MAX_COMBAT_FIELDS)
        require(maximumSkillFields in 1 until maximumFields)
        require(reactionCooldownSeconds.isFinite() && reactionCooldownSeconds > 0f)
        require(reactionPairCooldownSeconds.isFinite() && reactionPairCooldownSeconds >= reactionCooldownSeconds)
        require(masteredTagPoints in 0..2)
        require(skillTagPoints in 0..2)
    }

    fun stackMagnitude(stacks: Int): Float =
        if (stacks <= 0) 0f else stackMagnitudes[(stacks - 1).coerceAtMost(stackMagnitudes.lastIndex)]

    fun rebornChance(rebirthTier: Int): Float =
        (rebirthTier.coerceAtLeast(0) * rebornChancePerTier).coerceAtMost(maximumRebornChance)

    /** Highest reached tier (0 = none) for [points] against [thresholds]. */
    fun tier(points: Int, thresholds: ImmutableList<Int>): Int = thresholds.count { points >= it }
}

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
    /** A skill cast succeeded; its primary target (if any) is the clause target, else the effect starts at its anchor. */
    SKILL_CAST("On skill cast"),
    /** One of the first four enemies a skill cast damages; it is the clause target and the damage is the hit. */
    SKILL_HIT("On skill hit"),
    /** The Apex activated. */
    APEX("On Apex"),
    /** The Core entered one of your fields; the effect starts at the field's centre. */
    FIELD_ENTER("On entering a field"),
    /** One of your effects erased a hostile bolt; the effect starts where the bolt was. */
    SHOT_ERASED("On erasing a shot"),
    /** You cashed a mark (a Spall throw, a Cracked detonation, a Void Anchor blink, a ringing Shatter); the marked enemy is the clause target. */
    MARK_CONSUMED("On cashing a mark"),
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
}

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
    else -> true
}

/** Conditions that read a dispatch (the reaction or skill involved); item rules hold outside any dispatch. */
val EffectCondition.needsDispatch: Boolean
    get() = this is EffectCondition.ReactionIs || this is EffectCondition.SkillIs || this is EffectCondition.SkillTagged

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
    /** Skill cooldowns tick faster by the amount (through the refund bank). */
    SKILL_RECHARGE("Skill recharge"),
    /** Skill damage grows by the amount. */
    SKILL_POWER("Skill power"),
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

sealed interface EffectAction {
    /** Applies stacks to the target, or around it (around the Core without a target) when [radius] > 0. */
    data class ApplyStatus(val pick: ElementPick, val stacks: Int, val radius: Float = 0f) : EffectAction {
        init {
            require(stacks in 1..ContentBounds.MAX_STATUS_THRESHOLD)
            require(radius.isFinite() && radius in 0f..ContentBounds.MAX_EFFECT_RADIUS)
        }
    }
    data class Burst(val radius: Float, val basis: DamageBasis, val scale: Float) : EffectAction {
        init { requireArea(radius); requireScale(scale) }
    }
    data class Strike(val basis: DamageBasis, val scale: Float) : EffectAction {
        init { requireScale(scale) }
    }
    data class Chain(
        val count: Int,
        val range: Float,
        val basis: DamageBasis,
        val scale: Float,
        val pick: ElementPick? = null,
        val stacks: Int = 1,
    ) : EffectAction {
        init {
            require(count in 1..ContentBounds.MAX_CHAIN_TARGETS)
            requireArea(range)
            requireScale(scale)
            require(stacks in 1..ContentBounds.MAX_STATUS_THRESHOLD)
        }
    }
    data class Shards(val count: Int, val basis: DamageBasis, val scale: Float, val pick: ElementPick? = null) : EffectAction {
        init { require(count in 1..ContentBounds.MAX_SHARDS); requireScale(scale) }
    }
    data class Pull(val radius: Float, val strength: Float) : EffectAction {
        init { requireArea(radius); requireScale(strength) }
    }
    data class Push(val radius: Float, val strength: Float) : EffectAction {
        init { requireArea(radius); requireScale(strength) }
    }
    data class Heal(val amount: Float) : EffectAction {
        init { requireScale(amount) }
    }
    data class Shield(val amount: Float) : EffectAction {
        init { requireScale(amount) }
    }
    /** Removes heat; the amount is an absolute value on the 0..100 gauge. */
    data class Vent(val heat: Float) : EffectAction {
        init { requireScale(heat) }
    }
    data class Overdrive(val charge: Float) : EffectAction {
        init { requireScale(charge) }
    }
    data class Buff(val stat: BuffStat, val amount: Float, val seconds: Float, val maxStacks: Int = 1) : EffectAction {
        init {
            require(amount.isFinite() && amount != 0f)
            require(seconds.isFinite() && seconds > 0f && seconds <= 30f)
            require(maxStacks in 1..ContentBounds.MAX_BUFF_STACKS)
        }
    }
    data class ExtendCombo(val seconds: Float) : EffectAction {
        init { requireScale(seconds) }
    }
    /** Advances both weapon clocks by [seconds]. */
    data class RefreshWeapon(val seconds: Float) : EffectAction {
        init { require(seconds.isFinite() && seconds in 0.01f..2f) }
    }
    data class GainData(val amount: Float) : EffectAction {
        init { requireScale(amount) }
    }
    /**
     * Consumes the target's damage-over-time statuses and deals [scale] × their remaining damage at
     * once (damage over time becomes burst).
     */
    data class Detonate(val elements: ImmutableList<StatusElement>, val scale: Float) : EffectAction {
        init {
            require(elements.isNotEmpty() && elements.toSet().size == elements.size)
            requireScale(scale)
        }
    }
    /** A lingering gravity well that pulls and damages its occupants every second. */
    data class Well(
        val radius: Float,
        val seconds: Float,
        val basis: DamageBasis,
        val scale: Float,
        val pick: ElementPick? = null,
    ) : EffectAction {
        init {
            requireArea(radius)
            require(seconds.isFinite() && seconds > 0f && seconds <= 6f)
            requireScale(scale)
        }
    }
    /** Takes [seconds] off skill cooldowns through the refund bank. */
    data class RefundSkills(val seconds: Float, val pick: SkillPick = SkillPick.ALL) : EffectAction {
        init { requireWithin(seconds, 0.1f, 3f, "Skill refund seconds") }
    }
    /** At the maximum level shortens the Apex cooldown by [seconds]; before it, every skill by a quarter of it. */
    data class ChargeApex(val seconds: Float) : EffectAction {
        init { requireWithin(seconds, 0.5f, 5f, "Apex charge seconds") }
    }
    /** Sets off the reaction on the target, then on the nearest eligible enemies within [radius], [maxTargets] in all. */
    data class ForceReaction(val radius: Float, val maxTargets: Int) : EffectAction {
        init {
            requireWithin(radius, 0f, 300f, "Forced reaction radius")
            require(maxTargets in 1..MAX_FORCED_TARGETS) { "Forced reaction targets must be 1..$MAX_FORCED_TARGETS" }
        }
    }
    /** Opens an item field of [kind] (no damage of its own) at the effect's origin. */
    data class Zone(val kind: ZoneKind, val radius: Float, val seconds: Float) : EffectAction {
        init {
            requireWithin(radius, 60f, 300f, "Zone radius")
            requireWithin(seconds, 0.5f, 4f, "Zone seconds")
        }
    }
    /** Erases up to [maximum] hostile bolts nearest the effect's origin within [radius]. */
    data class EraseShots(val radius: Float, val maximum: Int) : EffectAction {
        init {
            requireWithin(radius, 40f, 400f, "Erase radius")
            require(maximum in 1..MAX_ERASED_SHOTS) { "Erased shots must be 1..$MAX_ERASED_SHOTS" }
        }
    }
    /** Launches the Core at [speed] along [direction] (gated like every reaction and item push). */
    data class CoreImpulse(val speed: Float, val direction: ImpulseDirection) : EffectAction {
        init { requireWithin(speed, 100f, 600f, "Core impulse speed") }
    }
    /** Marks the target with [mark] for [seconds]. */
    data class ApplyMark(val mark: MarkPick, val seconds: Float) : EffectAction {
        init { requireWithin(seconds, 1f, 6f, "Mark seconds") }
    }
    /** Throws the target away from the Core at [speed]. */
    data class Throw(val speed: Float) : EffectAction {
        init { requireWithin(speed, 200f, 900f, "Throw speed") }
    }
    /** The next Dash within a few seconds costs no heat. */
    data object PrimeDash : EffectAction

    companion object {
        const val MAX_FORCED_TARGETS: Int = 4
        const val MAX_ERASED_SHOTS: Int = 12
    }
}

private fun requireWithin(value: Float, minimum: Float, maximum: Float, label: String) {
    require(value.isFinite() && value in minimum..maximum) { "$label must be within $minimum..$maximum" }
}

private fun requireArea(radius: Float) {
    require(radius.isFinite() && radius > 0f && radius <= ContentBounds.MAX_EFFECT_RADIUS) {
        "Effect radius must be finite, positive and at most ${ContentBounds.MAX_EFFECT_RADIUS}"
    }
}

private fun requireScale(value: Float) {
    require(value.isFinite() && value > 0f) { "Effect magnitude must be finite and positive" }
}

/** Trigger–Condition–Effect. All conditions must hold; actions run in order. */
data class EffectClause(
    val trigger: EffectTrigger,
    val conditions: ImmutableList<EffectCondition> = immutableListOf(),
    val actions: ImmutableList<EffectAction>,
    val cooldownSeconds: Float = 0f,
    val everyNth: Int = 1,
    /** Luck shortens [everyNth] by one per 0.25 luck, never below three. */
    val luckShortened: Boolean = false,
) {
    init {
        require(actions.isNotEmpty() && actions.size <= ContentBounds.MAX_CLAUSE_ACTIONS)
        require(conditions.size <= ContentBounds.MAX_CLAUSE_CONDITIONS)
        require(cooldownSeconds.isFinite() && cooldownSeconds in 0f..60f)
        require(everyNth in 1..64)
        require(!luckShortened || everyNth > 3)
        require(trigger !in FrequentTriggers || actions.none { it.isSustain } || cooldownSeconds >= 0.5f || everyNth >= 2) {
            "Sustain actions on frequent triggers need a cooldown of at least 0.5 s or an every-Nth gate"
        }
        conditions.forEach { condition ->
            require(condition.validOn(trigger)) { "Condition $condition cannot hold on $trigger" }
        }
        require(trigger in TargetedTriggers || actions.none { it is EffectAction.ApplyMark || it is EffectAction.Throw }) {
            "Marks and throws need a trigger with a target"
        }
        val frequent = trigger in FrequentTriggers
        require(!frequent || actions.none { it is EffectAction.ForceReaction } || cooldownSeconds >= 1f || everyNth >= 3) {
            "Forced reactions on frequent triggers need a cooldown of at least 1 s or every third"
        }
        require(!frequent || actions.none { it is EffectAction.Zone } || cooldownSeconds >= 2f || everyNth >= 4) {
            "Zones on frequent triggers need a cooldown of at least 2 s or every fourth"
        }
        require(!frequent || actions.none { it is EffectAction.EraseShots } || cooldownSeconds >= 1f) {
            "Erasing shots on frequent triggers needs a cooldown of at least 1 s"
        }
        require(trigger != EffectTrigger.APEX || everyNth <= MAX_APEX_EVERY_NTH) {
            "Apex clauses count at most every $MAX_APEX_EVERY_NTH activations"
        }
    }

    /** Effective every-Nth gate for the owner's current luck. */
    fun everyNthAt(luck: Float): Int =
        if (!luckShortened) everyNth else (everyNth - (luck.coerceIn(0f, 4f) * 4f).toInt()).coerceAtLeast(3)

    companion object {
        val FrequentTriggers: Set<EffectTrigger> = setOf(
            EffectTrigger.IMPACT, EffectTrigger.WEAPON_HIT, EffectTrigger.CRIT, EffectTrigger.KILL,
            EffectTrigger.DASH_HIT, EffectTrigger.BREAK, EffectTrigger.REACTION, EffectTrigger.PICKUP,
            EffectTrigger.HURT, EffectTrigger.RICOCHET, EffectTrigger.SLAM,
            EffectTrigger.SKILL_HIT, EffectTrigger.FIELD_ENTER, EffectTrigger.SHOT_ERASED, EffectTrigger.MARK_CONSUMED,
        )

        /** The Apex activates at most every 30 s, so its clauses count at most every third activation. */
        const val MAX_APEX_EVERY_NTH: Int = 3
    }
}

/**
 * Healing, shields, Overdrive, heat venting, weapon, skill and Apex refunds, Core launches and free Dashes must
 * not fire unbounded.
 */
val EffectAction.isSustain: Boolean
    get() = this is EffectAction.Heal || this is EffectAction.Shield || this is EffectAction.Overdrive ||
        this is EffectAction.Vent || this is EffectAction.RefreshWeapon || this is EffectAction.RefundSkills ||
        this is EffectAction.ChargeApex || this is EffectAction.CoreImpulse || this is EffectAction.PrimeDash

/** Signed passive stat change per stack; negative amounts are liabilities. */
data class StatDelta(val effect: ItemEffect, val amount: Float) {
    init {
        require(amount.isFinite() && amount != 0f) { "Stat delta must be finite and non-zero" }
    }
}

/** Stacks a [ItemRule.BreakThreshold] removes at [copies] copies: the second copy cuts one more. */
fun breakThresholdCut(reduction: Int, copies: Int): Int = reduction + if (copies >= 2) 1 else 0

/** Shield overfill an [ConversionKind.OVERHEAL_TO_SHIELD] rule allows at [copies] copies, as a capacity fraction. */
fun overhealOverfill(amount: Float, copies: Int): Float = amount + OVERFILL_PER_EXTRA_COPY * (copies - 1).coerceAtLeast(0)

private const val OVERFILL_PER_EXTRA_COPY: Float = 0.35f

/** System conversions that change how an entire mechanic behaves while the owner holds them. */
enum class ConversionKind {
    /** Healing beyond full integrity becomes shield that may overfill to [ItemRule.Convert.amount] × capacity. */
    OVERHEAL_TO_SHIELD,
    /** Regeneration no longer heals; each second it applies [ItemRule.Convert.amount] Decay per point nearby. */
    REGENERATION_TO_DECAY,
    /** Critical chance is capped at 30 %; each 1 % above becomes [ItemRule.Convert.amount] % critical damage. */
    CRIT_CHANCE_TO_DAMAGE,
    /** Every 10 Dash heat spent refunds [ItemRule.Convert.amount] seconds to every skill; cooling −20 %. */
    DASH_HEAT_TO_SKILLS,
    /**
     * Each Data pickup refunds [ItemRule.Convert.amount] seconds to every skill (at the maximum level also the Apex,
     * twice that); Data gain −20 %.
     */
    DATA_TO_SKILLS,
    /** Skill cooldowns ×0.6; each cast adds [ItemRule.Convert.amount] heat. */
    SKILLS_COST_HEAT,
    /** Skill fields travel with the Core at [ItemRule.Convert.amount] × their radius. */
    SKILL_FIELDS_FOLLOW,
    /**
     * A throw, push or pull you cause on an enemy that barely moves becomes proc damage of
     * [ItemRule.Convert.amount] × speed × 0.15, once per enemy per half second.
     */
    KNOCKBACK_TO_DAMAGE,
}

/** Persistent rule modifiers that hold while the item is owned. Magnitudes scale with stacks. */
sealed interface ItemRule {
    /** "While X: Y". Evaluated at the moment of use. */
    data class WhileModifier(val condition: EffectCondition, val stat: BuffStat, val amount: Float) : ItemRule {
        init {
            require(amount.isFinite() && amount != 0f)
            requireRuleCondition(condition)
        }
    }
    data class ElementPotency(val element: StatusElement, val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount > 0f) }
    }
    /** Multiplies how long each stack lasts before decaying. */
    data class StatusDuration(val element: StatusElement, val multiplier: Float) : ItemRule {
        init { require(multiplier.isFinite() && multiplier > 0f) }
    }
    /** Lowers the break threshold (does not scale with stacks). */
    data class BreakThreshold(val element: StatusElement, val reduction: Int) : ItemRule {
        init { require(reduction in 1..4) }
    }
    data class BreakPower(val element: StatusElement, val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount > 0f) }
    }
    data class BreakSpread(val element: StatusElement, val stacks: Int, val radius: Float) : ItemRule {
        init { require(stacks in 1..4); requireArea(radius) }
    }
    data class ReactionPower(val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount > 0f) }
    }
    /** Reactions leave stacks of the incoming element on the target (does not scale with stacks). */
    data class ReactionResidue(val stacks: Int) : ItemRule {
        init { require(stacks in 1..3) }
    }
    /** Reactions close to the Core hurt it. */
    data class ReactionRecoil(val damage: Float, val radius: Float) : ItemRule {
        init { requireScale(damage); requireArea(radius) }
    }
    data class AlwaysCrit(val condition: EffectCondition) : ItemRule {
        init { requireRuleCondition(condition) }
    }
    data class CritDamageVersus(val condition: EffectCondition, val amount: Float) : ItemRule {
        init {
            require(amount.isFinite() && amount > 0f)
            requireRuleCondition(condition)
        }
    }
    data class DamageVersus(val condition: EffectCondition, val amount: Float) : ItemRule {
        init {
            require(amount.isFinite() && amount != 0f)
            requireRuleCondition(condition)
        }
    }
    /** Non-critical hits deal this fraction more (negative = less). */
    data class NonCriticalDamage(val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount != 0f && amount > -1f) }
    }
    data class ComboDamage(val perCombo: Float, val cap: Float) : ItemRule {
        init { require(perCombo.isFinite() && perCombo > 0f && cap.isFinite() && cap >= perCombo) }
    }
    /** Granted once per stack when acquired. */
    data class BonusRerolls(val count: Int) : ItemRule {
        init { require(count in 1..3) }
    }
    /** After [misses] non-critical direct hits in a row, the next direct hit is critical. */
    data class GuaranteedCritAfter(val misses: Int) : ItemRule {
        init { require(misses in 2..10) }
    }
    /** Cooling below [heat] makes the next Dash free, at most once per [cooldownSeconds]. */
    data class FreeDash(val heat: Float, val cooldownSeconds: Float) : ItemRule {
        init { require(heat.isFinite() && heat in 1f..99f && cooldownSeconds.isFinite() && cooldownSeconds >= 1f) }
    }
    data class OfferCards(val count: Int) : ItemRule {
        init { require(count == 1) }
    }
    data class Convert(val kind: ConversionKind, val amount: Float = 1f) : ItemRule {
        init {
            require(amount.isFinite() && amount > 0f)
            require(kind != ConversionKind.SKILL_FIELDS_FOLLOW || amount <= 1f) { "Following skill fields never grow" }
        }
    }
    /** The reaction takes its keystone form. One per item, on Epic or Legendary items only, one item per reaction. */
    data class ReactionKeystone(val reaction: ReactionId) : ItemRule
    /** Scales [aspect] of [reaction] (null: every reaction) by +[amount]; never its damage. */
    data class ReactionTuning(val reaction: ReactionId?, val aspect: ReactionAspect, val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.1f, 0.5f, "Reaction tuning") }
    }
    /** Every field you make (reaction, skill, item) grows by +[amount] in [aspect]. */
    data class FieldTuning(val aspect: FieldAspect, val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.1f, 0.5f, "Field tuning") }
    }
    /** Skills of [element] (null: every skill) recharge [amount] faster. */
    data class SkillCooldown(val element: StatusElement?, val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.05f, 0.25f, "Skill cooldown cut") }
    }
    /** Skills of [element] (null: every skill) deal +[amount] damage. */
    data class SkillPower(val element: StatusElement?, val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.1f, 0.5f, "Skill power") }
    }
    /** Every skill holds a second charge; each charge recharges on its cooldown × 1.2. One such item per run. */
    data object SkillCharges : ItemRule
    /** Every skill takes the [pick] element; element-less skills apply it to what they damage. One such item per run. */
    data class SkillAttunement(val pick: ElementPick) : ItemRule
    /** Catalysis refunds grow by +[amount]. */
    data class CatalysisRate(val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.25f, 1f, "Catalysis rate") }
    }
    /** The Apex's base cooldown shrinks by [amount]. */
    data class ApexCooldown(val amount: Float) : ItemRule {
        init { requireWithin(amount, 0.05f, 0.2f, "Apex cooldown cut") }
    }
    /** Reactions feed the Core's verbs from [bonus] farther. */
    data class CoreLinkRadius(val bonus: Float) : ItemRule {
        init { requireWithin(bonus, 40f, 240f, "Core link bonus") }
    }
}

private fun requireRuleCondition(condition: EffectCondition) {
    require(!condition.needsDispatch) { "Rule condition $condition needs a dispatch; rules hold outside any" }
}
