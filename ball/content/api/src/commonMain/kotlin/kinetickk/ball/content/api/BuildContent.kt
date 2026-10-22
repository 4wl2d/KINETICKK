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
     * Content-owned numbers of the reaction's play-style behaviour, read by index (the description
     * may name them as `{v0}`..`{v3}`); empty while the reaction has none.
     */
    val values: ImmutableList<Float> = immutableListOf(),
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
    val reactionCooldownSeconds: Float = 0.5f,
    val reactionPairCooldownSeconds: Float = 2f,
    val masteredTagPoints: Int = 1,
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
        require(reactionCooldownSeconds.isFinite() && reactionCooldownSeconds > 0f)
        require(reactionPairCooldownSeconds.isFinite() && reactionPairCooldownSeconds >= reactionCooldownSeconds)
        require(masteredTagPoints in 0..2)
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
}

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
    /** For BREAK and REACTION triggers: the element that broke or took part. */
    data class ElementIs(val element: StatusElement) : EffectCondition
}

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
}

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
    }

    /** Effective every-Nth gate for the owner's current luck. */
    fun everyNthAt(luck: Float): Int =
        if (!luckShortened) everyNth else (everyNth - (luck.coerceIn(0f, 4f) * 4f).toInt()).coerceAtLeast(3)

    companion object {
        val FrequentTriggers: Set<EffectTrigger> = setOf(
            EffectTrigger.IMPACT, EffectTrigger.WEAPON_HIT, EffectTrigger.CRIT, EffectTrigger.KILL,
            EffectTrigger.DASH_HIT, EffectTrigger.BREAK, EffectTrigger.REACTION, EffectTrigger.PICKUP,
            EffectTrigger.HURT, EffectTrigger.RICOCHET, EffectTrigger.SLAM,
        )
    }
}

/** Healing, shields, Overdrive, heat venting and weapon refunds must not fire unbounded. */
val EffectAction.isSustain: Boolean
    get() = this is EffectAction.Heal || this is EffectAction.Shield || this is EffectAction.Overdrive ||
        this is EffectAction.Vent || this is EffectAction.RefreshWeapon

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
}

/** Persistent rule modifiers that hold while the item is owned. Magnitudes scale with stacks. */
sealed interface ItemRule {
    /** "While X: Y". Evaluated at the moment of use. */
    data class WhileModifier(val condition: EffectCondition, val stat: BuffStat, val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount != 0f) }
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
    data class AlwaysCrit(val condition: EffectCondition) : ItemRule
    data class CritDamageVersus(val condition: EffectCondition, val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount > 0f) }
    }
    data class DamageVersus(val condition: EffectCondition, val amount: Float) : ItemRule {
        init { require(amount.isFinite() && amount != 0f) }
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
        init { require(amount.isFinite() && amount > 0f) }
    }
}
