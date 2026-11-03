// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

internal fun RuleWriter.rule(rule: ItemRule): List<RuleLine> = when (rule) {
    is ItemRule.WhileModifier -> listOf(
        line(
            rule.isLiability,
            text(BuildRulesText.RuleConditional, text(rule.stat.phrase, signedPercent(rule.amount * gain)), condition(rule.condition)),
            conditionElement(rule.condition),
        ),
    )
    is ItemRule.ElementPotency -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RulePotency, signedPercent(rule.amount * gain), element(rule.element)), rule.element),
    )
    is ItemRule.StatusDuration -> statusDurationRule(rule)
    is ItemRule.BreakThreshold -> breakThresholdRule(rule)
    is ItemRule.BreakPower -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleBreakPower, signedPercent(rule.amount * gain), breakName(rule.element, genitive = true)), rule.element),
    )
    is ItemRule.BreakSpread -> listOf(
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleBreakSpread, breakName(rule.element, genitive = false), stacks(scaledStacks(rule.stacks).toDouble(), element(rule.element), named = true)),
            rule.element,
        ),
    )
    is ItemRule.ReactionPower -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleReactionPower, signedPercent(rule.amount * gain))),
    )
    is ItemRule.ReactionResidue -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleReactionResidue, stacks(rule.stacks.toDouble(), text(BuildRulesText.IncomingElement), named = false))),
    )
    is ItemRule.ReactionRecoil -> listOf(
        RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleReactionRecoil, language.number(rule.damage * magnitude, FLAT_DECIMALS)), negative = true),
    )
    is ItemRule.AlwaysCrit -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleAlwaysCrit, condition(rule.condition)), conditionElement(rule.condition)),
    )
    is ItemRule.CritDamageVersus -> listOf(
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleCritDamageVersus, signedPercent(rule.amount * gain), condition(rule.condition)),
            conditionElement(rule.condition),
        ),
    )
    is ItemRule.DamageVersus -> listOf(
        line(
            rule.amount < 0f,
            text(BuildRulesText.RuleDamageVersus, signedPercent(rule.amount * gain), condition(rule.condition)),
            conditionElement(rule.condition),
        ),
    )
    is ItemRule.NonCriticalDamage -> listOf(
        if (rule.amount < 0f) {
            RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleNonCriticalLess, percent(-rule.amount * magnitude)), negative = true)
        } else {
            RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleNonCriticalMore, percent(rule.amount * magnitude)))
        },
    )
    is ItemRule.ComboDamage -> listOf(
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleComboDamage, signedPercent(rule.perCombo * gain), percent(rule.cap * context.bonus.toDouble())),
        ),
    )
    is ItemRule.BonusRerolls -> bonusRerollsRule(rule)
    is ItemRule.GuaranteedCritAfter -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleGuaranteedCrit, rule.misses)),
    )
    is ItemRule.FreeDash -> listOf(
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleFreeDash, language.number(rule.heat.toDouble(), FLAT_DECIMALS), seconds(rule.cooldownSeconds.toDouble())),
        ),
    )
    is ItemRule.OfferCards -> listOf(RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleOfferCards)))
    is ItemRule.Convert -> conversion(rule)
    is ItemRule.ReactionKeystone -> listOf(
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleKeystone, reactionName(rule.reaction), localized(DefaultReactions.reaction(rule.reaction).keystone)),
            rule.reaction.first,
        ),
    )
    is ItemRule.ReactionTuning -> reactionTuningRule(rule)
    is ItemRule.FieldTuning -> fieldTuningRule(rule)
    is ItemRule.SkillCooldown -> skillCooldownRule(rule)
    is ItemRule.SkillPower -> skillPowerRule(rule)
    ItemRule.SkillCharges -> listOf(RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleSkillCharges)))
    is ItemRule.SkillAttunement -> skillAttunementRule(rule)
    is ItemRule.CatalysisRate -> listOf(RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleCatalysisRate, percent(rule.amount * gain))))
    is ItemRule.ApexCooldown -> listOf(RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleApexCooldown, percent(rule.amount * gain))))
    is ItemRule.CoreLinkRadius -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleCoreLinkRadius, language.number(rule.bonus * gain, 0))),
    )
    is ItemRule.ObstacleTuning -> obstacleTuningRule(rule)
}

private fun RuleWriter.statusDurationRule(rule: ItemRule.StatusDuration): List<RuleLine> = run {
    val change = (rule.multiplier - 1.0) * magnitude
    listOf(
        if (change >= 0.0) {
            RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleDurationLonger, element(rule.element), percent(change)), rule.element)
        } else {
            RuleLine(
                RuleLineKind.COST,
                text(BuildRulesText.RuleDurationShorter, element(rule.element), percent((-change).coerceAtMost(MAX_SHORTENING))),
                rule.element,
                negative = true,
            )
        },
    )
}

private fun RuleWriter.breakThresholdRule(rule: ItemRule.BreakThreshold): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        breakThresholdCut(rule.reduction, context.stacks).let { cut ->
            if (cut == 1) text(BuildRulesText.RuleThresholdOne, element(rule.element))
            else text(BuildRulesText.RuleThresholdFew, element(rule.element), cut)
        },
        rule.element,
    ),
    )

private fun RuleWriter.bonusRerollsRule(rule: ItemRule.BonusRerolls): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        text(
            when (countForm(rule.count.toDouble())) {
                CountForm.ONE -> BuildRulesText.RuleRerollsOne
                CountForm.FEW -> BuildRulesText.RuleRerollsFew
                CountForm.MANY -> BuildRulesText.RuleRerollsMany
            },
            rule.count,
        ),
    ),
    )

private fun RuleWriter.reactionTuningRule(rule: ItemRule.ReactionTuning): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        text(
            BuildRulesText.RuleReactionTuning,
            rule.reaction?.let(::reactionName) ?: text(BuildRulesText.AllReactions),
            signedPercent(rule.amount * gain),
            text(rule.aspect.phrase),
        ),
        rule.reaction?.first,
    ),
    )

private fun RuleWriter.fieldTuningRule(rule: ItemRule.FieldTuning): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        text(
            if (rule.aspect == FieldAspect.RADIUS) BuildRulesText.RuleFieldRadius else BuildRulesText.RuleFieldDuration,
            percent(rule.amount * gain),
        ),
    ),
    )

private fun RuleWriter.skillCooldownRule(rule: ItemRule.SkillCooldown): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        rule.element?.let { text(BuildRulesText.RuleSkillCooldown, elementName(it), percent(rule.amount * gain)) }
            ?: text(BuildRulesText.RuleSkillCooldownAll, percent(rule.amount * gain)),
        rule.element,
    ),
    )

private fun RuleWriter.skillPowerRule(rule: ItemRule.SkillPower): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        rule.element?.let { text(BuildRulesText.RuleSkillPower, elementName(it), signedPercent(rule.amount * gain)) }
            ?: text(BuildRulesText.RuleSkillPowerAll, signedPercent(rule.amount * gain)),
        rule.element,
    ),
    )

private fun RuleWriter.skillAttunementRule(rule: ItemRule.SkillAttunement): List<RuleLine> = run {
    val resolved = resolve(rule.pick)
    listOf(
        RuleLine(
            RuleLineKind.RULE,
            when {
                resolved != null -> text(BuildRulesText.RuleSkillAttunement, elementName(resolved))
                rule.pick == ElementPick.SECONDARY -> text(BuildRulesText.RuleSkillAttunementSecondary)
                else -> text(BuildRulesText.RuleSkillAttunementDominant)
            },
            resolved,
        ),
    )
}

private fun RuleWriter.obstacleTuningRule(rule: ItemRule.ObstacleTuning): List<RuleLine> = listOf(
    RuleLine(
        RuleLineKind.RULE,
        when (rule.aspect) {
            ObstacleAspect.RESTITUTION -> text(BuildRulesText.RuleObstacleRestitution, percent(rule.amount * gain))
            ObstacleAspect.COLLISION_DAMAGE -> text(BuildRulesText.RuleObstacleDamage, signedPercent(-rule.amount * gain))
            ObstacleAspect.LETHAL_SPEED -> text(BuildRulesText.RuleObstacleLethal, speed((rule.amount * gain).toFloat()))
            ObstacleAspect.BOOST_POWER -> text(BuildRulesText.RuleBoostPower, percent(rule.amount * gain))
            ObstacleAspect.WELL_PULL -> text(BuildRulesText.RuleWellPull, percent(rule.amount * gain))
        },
    ),
    )

private fun RuleWriter.conversion(rule: ItemRule.Convert): List<RuleLine> = when (rule.kind) {
    ConversionKind.OVERHEAL_TO_SHIELD -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleOverhealToShield, percent(overhealOverfill(rule.amount, context.stacks).toDouble()))),
    )
    ConversionKind.REGENERATION_TO_DECAY -> listOf(
        RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleRegenerationNoLongerHeals), negative = true),
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleRegenerationToDecay, stacks(rule.amount * magnitude, element(StatusElement.ENTROPY), named = true)),
            StatusElement.ENTROPY,
        ),
    )
    ConversionKind.CRIT_CHANCE_TO_DAMAGE -> listOf(
        RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleCritChanceCap, percent(CRIT_CHANCE_CONVERSION_CAP)), negative = true),
        RuleLine(
            RuleLineKind.RULE,
            text(BuildRulesText.RuleCritChanceToDamage, text(BuildRulesText.UnitPercent, language.number(rule.amount * gain, PERCENT_DECIMALS))),
        ),
    )
    ConversionKind.DASH_HEAT_TO_SKILLS -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleDashHeatToSkills, seconds(rule.amount.toDouble()))),
        RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleCoolSlower, percent(1.0 - DASH_HEAT_CONVERSION_COOLING)), negative = true),
    )
    ConversionKind.DATA_TO_SKILLS -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleDataToSkills, seconds(rule.amount.toDouble()))),
        RuleLine(RuleLineKind.COST, text(BuildRulesText.RuleDataToSkillsCost, percent(1.0 - DATA_CONVERSION_GAIN)), negative = true),
    )
    ConversionKind.SKILLS_COST_HEAT -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleSkillCooldownAll, percent(1.0 - SKILL_HEAT_CONVERSION_COOLDOWN))),
        RuleLine(
            RuleLineKind.COST,
            text(BuildRulesText.RuleSkillsCostHeat, language.number(rule.amount.toDouble(), FLAT_DECIMALS)),
            negative = true,
        ),
    )
    ConversionKind.SKILL_FIELDS_FOLLOW -> listOf(
        RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleSkillFieldsFollow, percent(rule.amount.toDouble()))),
    )
    ConversionKind.KNOCKBACK_TO_DAMAGE -> listOf(RuleLine(RuleLineKind.RULE, text(BuildRulesText.RuleKnockbackToDamage)))
}
