// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

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
    /** Scales one obstacle mechanic while owned; magnitudes scale with stacks, totals are capped when compiled. */
    data class ObstacleTuning(val aspect: ObstacleAspect, val amount: Float) : ItemRule {
        init {
            when (aspect) {
                ObstacleAspect.RESTITUTION -> requireWithin(amount, 0.05f, 0.3f, "Restitution bonus")
                ObstacleAspect.COLLISION_DAMAGE -> requireWithin(amount, 0.1f, 0.5f, "Obstacle damage cut")
                ObstacleAspect.LETHAL_SPEED -> requireWithin(amount, 50f, 300f, "Lethal speed bonus")
                ObstacleAspect.BOOST_POWER -> requireWithin(amount, 0.1f, 0.6f, "Boost power")
                ObstacleAspect.WELL_PULL -> requireWithin(amount, 0.1f, 0.6f, "Knot pull on enemies")
            }
        }
    }
}

private fun requireRuleCondition(condition: EffectCondition) {
    require(!condition.needsDispatch) { "Rule condition $condition needs a dispatch; rules hold outside any" }
}
