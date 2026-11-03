// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

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

internal fun requireWithin(value: Float, minimum: Float, maximum: Float, label: String) {
    require(value.isFinite() && value in minimum..maximum) { "$label must be within $minimum..$maximum" }
}

internal fun requireArea(radius: Float) {
    require(radius.isFinite() && radius > 0f && radius <= ContentBounds.MAX_EFFECT_RADIUS) {
        "Effect radius must be finite, positive and at most ${ContentBounds.MAX_EFFECT_RADIUS}"
    }
}

internal fun requireScale(value: Float) {
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
            EffectTrigger.SHATTER, EffectTrigger.BOOST, EffectTrigger.GRAZE,
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
