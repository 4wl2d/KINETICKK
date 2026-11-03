// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.math.abs
import kotlin.math.floor

internal enum class CountForm { ONE, FEW, MANY }

/** Target conditions read differently after a hit, an impact or a kill. */
internal enum class TargetCase { HIT, IMPACT, KILL }

internal class RuleWriter(val language: AppLanguage, val context: RuleTextContext) {
    val magnitude = context.stackMagnitude.toDouble()

    /** Stack magnitude times the item bonus, for what the bonus scales. */
    val gain = magnitude * context.bonus

    fun stat(delta: StatDelta): RuleLine {
        val amount = delta.amount * gain
        val value = when (delta.effect.unit) {
            ModifierUnit.PERCENT -> signedPercent(amount)
            ModifierUnit.FLAT -> signed(amount, FLAT_DECIMALS)
            ModifierUnit.PER_SECOND -> text(BuildRulesText.UnitPerSecond, signed(amount, FLAT_DECIMALS))
            ModifierUnit.SECONDS -> text(BuildRulesText.UnitSeconds, signed(amount, SECOND_DECIMALS))
        }
        val sentence = text(delta.effect.phrase, value)
        return if (delta.amount < 0f) {
            RuleLine(RuleLineKind.COST, sentence, negative = true)
        } else {
            RuleLine(RuleLineKind.PASSIVE, sentence)
        }
    }

    fun trigger(clause: EffectClause): RuleLine {
        val named = clause.conditions.mapNotNull { (it as? EffectCondition.ElementIs)?.element }.distinct()
        // Two elements on a reaction name the one reaction between them.
        val pair = named.takeIf { clause.trigger == EffectTrigger.REACTION && it.size >= 2 }?.let { it[0] to it[1] }
        val folded = named.firstOrNull()
            ?.takeIf { pair == null && (clause.trigger == EffectTrigger.BREAK || clause.trigger == EffectTrigger.REACTION) }
        val every = clause.everyNth
        val head = when {
            pair != null ->
                if (every == 1) text(BuildRulesText.OnElementPairReaction, element(pair.first), element(pair.second))
                else text(BuildRulesText.EveryElementPairReaction, ordinal(every), element(pair.first), element(pair.second))
            folded != null && clause.trigger == EffectTrigger.BREAK ->
                if (every == 1) text(BuildRulesText.OnElementBreak, element(folded))
                else text(BuildRulesText.EveryElementBreak, ordinal(every), element(folded))
            folded != null ->
                if (every == 1) text(BuildRulesText.OnElementReaction, element(folded))
                else text(BuildRulesText.EveryElementReaction, ordinal(every), element(folded))
            every == 1 -> text(clause.trigger.onPhrase)
            else -> text(clause.trigger.everyPhrase, ordinal(every))
        }
        val case = when (clause.trigger) {
            EffectTrigger.IMPACT -> TargetCase.IMPACT
            EffectTrigger.KILL -> TargetCase.KILL
            else -> TargetCase.HIT
        }
        val unconsumed = listOfNotNull(folded, pair?.first, pair?.second).toMutableList()
        val conditions = clause.conditions.mapNotNull { condition ->
            if (condition is EffectCondition.ElementIs && unconsumed.remove(condition.element)) {
                null
            } else {
                condition(condition, case)
            }
        }
        val sentence = buildString {
            append(head)
            if (conditions.isNotEmpty()) append(' ').append(join(conditions))
            if (clause.cooldownSeconds > 0f) append(text(BuildRulesText.TriggerCooldown, seconds(clause.cooldownSeconds.toDouble())))
            if (clause.luckShortened) append(text(BuildRulesText.TriggerLuck))
        }
        val tint = folded ?: pair?.first ?: clause.conditions.firstNotNullOfOrNull(::conditionElement)
        return RuleLine(RuleLineKind.TRIGGER, sentence, tint)
    }

    fun effect(clause: EffectClause): RuleLine {
        val sentence = join(clause.actions.map(::action)).capitalized()
        return RuleLine(RuleLineKind.EFFECT, sentence, clause.actions.firstNotNullOfOrNull(::actionElement))
    }

    private fun action(action: EffectAction): String = when (action) {
        is EffectAction.ApplyStatus -> text(
            if (action.radius > 0f) BuildRulesText.ActionApplyNearby else BuildRulesText.ActionApply,
            pickStacks(scaledStacks(action.stacks), action.pick),
        )
        is EffectAction.Burst -> text(BuildRulesText.ActionBurst, amount(action.basis, action.scale))
        is EffectAction.Strike -> text(BuildRulesText.ActionStrike, amount(action.basis, action.scale))
        is EffectAction.Chain -> buildString {
            append(
                if (action.count == 1) text(BuildRulesText.ActionChainOne, amount(action.basis, action.scale))
                else text(BuildRulesText.ActionChain, action.count, amount(action.basis, action.scale)),
            )
            action.pick?.let { pick -> append(text(BuildRulesText.ActionChainApplying, pickStacks(scaledStacks(action.stacks), pick))) }
        }
        is EffectAction.Shards -> {
            val form = countForm(action.count.toDouble())
            val damage = amount(action.basis, action.scale)
            val pick = action.pick
            if (pick == null) {
                text(
                    when (form) {
                        CountForm.ONE -> BuildRulesText.ActionShardsOne
                        CountForm.FEW -> BuildRulesText.ActionShardsFew
                        CountForm.MANY -> BuildRulesText.ActionShardsMany
                    },
                    action.count, damage,
                )
            } else {
                text(
                    when (form) {
                        CountForm.ONE -> BuildRulesText.ActionShardsOfOne
                        CountForm.FEW -> BuildRulesText.ActionShardsOfFew
                        CountForm.MANY -> BuildRulesText.ActionShardsOfMany
                    },
                    action.count, damage, pickName(pick),
                )
            }
        }
        is EffectAction.Pull -> text(BuildRulesText.ActionPull)
        is EffectAction.Push -> text(BuildRulesText.ActionPush)
        is EffectAction.Heal -> text(BuildRulesText.ActionHeal, language.number(action.amount * gain, FLAT_DECIMALS))
        is EffectAction.Shield -> text(BuildRulesText.ActionShield, language.number(action.amount * gain, FLAT_DECIMALS))
        is EffectAction.Vent -> text(
            BuildRulesText.ActionVent,
            language.number((action.heat * magnitude).coerceAtMost(MAX_HEAT), FLAT_DECIMALS),
        )
        is EffectAction.Overdrive -> text(BuildRulesText.ActionOverdrive, language.number(action.charge * gain, FLAT_DECIMALS))
        is EffectAction.Buff -> buildString {
            append(
                text(
                    BuildRulesText.ActionBuff,
                    text(action.stat.phrase, signedPercent(action.amount * gain)),
                    seconds(action.seconds.toDouble()),
                ),
            )
            if (action.maxStacks > 1) append(text(BuildRulesText.ActionBuffStacking, action.maxStacks))
        }
        is EffectAction.ExtendCombo -> text(BuildRulesText.ActionExtendCombo, seconds(action.seconds * magnitude))
        is EffectAction.RefreshWeapon -> text(BuildRulesText.ActionRefreshWeapon, seconds(action.seconds * magnitude))
        is EffectAction.GainData -> text(BuildRulesText.ActionGainData, language.number(action.amount * gain, FLAT_DECIMALS))
        is EffectAction.Detonate -> text(
            BuildRulesText.ActionDetonate,
            join(action.elements.map(::element)),
            percent(action.scale * gain),
        )
        is EffectAction.Well -> {
            val pick = action.pick
            if (pick == null) {
                text(BuildRulesText.ActionWell, seconds(action.seconds.toDouble()), amount(action.basis, action.scale))
            } else {
                text(BuildRulesText.ActionWellApplying, seconds(action.seconds.toDouble()), amount(action.basis, action.scale), pickStacks(1, pick))
            }
        }
        is EffectAction.RefundSkills -> text(BuildRulesText.ActionRefundSkills, seconds(action.seconds * magnitude), text(action.pick.phrase))
        is EffectAction.ChargeApex -> text(BuildRulesText.ActionChargeApex, seconds(action.seconds * magnitude))
        is EffectAction.ForceReaction ->
            if (action.maxTargets == 1) text(BuildRulesText.ActionForceReactionOne)
            else text(BuildRulesText.ActionForceReaction, action.maxTargets)
        is EffectAction.Zone -> text(BuildRulesText.ActionZone, text(action.kind.phrase), seconds(action.seconds.toDouble()))
        is EffectAction.EraseShots ->
            if (action.maximum == 1) text(BuildRulesText.ActionEraseShot, language.number(action.radius.toDouble(), 0))
            else text(BuildRulesText.ActionEraseShots, action.maximum, language.number(action.radius.toDouble(), 0))
        is EffectAction.CoreImpulse -> text(BuildRulesText.ActionCoreImpulse, speed(action.speed), text(action.direction.phrase))
        is EffectAction.ApplyMark -> text(BuildRulesText.ActionApplyMark, text(action.mark.phrase), seconds(action.seconds.toDouble()))
        is EffectAction.Throw -> text(BuildRulesText.ActionThrow)
        EffectAction.PrimeDash -> text(BuildRulesText.ActionPrimeDash)
    }

    fun condition(condition: EffectCondition, case: TargetCase = TargetCase.HIT): String = when (condition) {
        is EffectCondition.SpeedAtLeast -> text(BuildRulesText.ConditionSpeedAtLeast, speed(condition.speed))
        is EffectCondition.SpeedBelow -> text(BuildRulesText.ConditionSpeedBelow, speed(condition.speed))
        is EffectCondition.IntegrityBelow -> text(BuildRulesText.ConditionIntegrityBelow, percent(condition.fraction.toDouble()))
        is EffectCondition.HeatAtLeast -> text(BuildRulesText.ConditionHeatAtLeast, language.number(condition.heat.toDouble(), FLAT_DECIMALS))
        EffectCondition.ShieldUp -> text(BuildRulesText.ConditionShieldUp)
        EffectCondition.OverdriveActive -> text(BuildRulesText.ConditionOverdrive)
        EffectCondition.Braking -> text(BuildRulesText.ConditionBraking)
        is EffectCondition.ComboAtLeast -> text(BuildRulesText.ConditionCombo, condition.combo)
        is EffectCondition.TargetHas -> {
            val carried = condition.element
            when {
                carried == null && condition.stacks == 1 -> text(
                    case.pick(
                        BuildRulesText.ConditionTargetAnyStatus,
                        BuildRulesText.ConditionImpactAnyStatus,
                        BuildRulesText.ConditionKillAnyStatus,
                    ),
                )
                // Gameplay counts every status on the target together.
                carried == null -> text(
                    case.pick(
                        BuildRulesText.ConditionTargetAnyStatusStacks,
                        BuildRulesText.ConditionImpactAnyStatusStacks,
                        BuildRulesText.ConditionKillAnyStatusStacks,
                    ),
                    condition.stacks,
                )
                condition.stacks == 1 -> text(
                    case.pick(
                        BuildRulesText.ConditionTargetElement,
                        BuildRulesText.ConditionImpactElement,
                        BuildRulesText.ConditionKillElement,
                    ),
                    element(carried),
                )
                else -> text(
                    case.pick(
                        BuildRulesText.ConditionTargetElementStacks,
                        BuildRulesText.ConditionImpactElementStacks,
                        BuildRulesText.ConditionKillElementStacks,
                    ),
                    condition.stacks, element(carried),
                )
            }
        }
        EffectCondition.TargetElite -> text(
            case.pick(BuildRulesText.ConditionTargetElite, BuildRulesText.ConditionImpactElite, BuildRulesText.ConditionKillElite),
        )
        EffectCondition.TargetFrozen -> text(
            case.pick(BuildRulesText.ConditionTargetFrozen, BuildRulesText.ConditionImpactFrozen, BuildRulesText.ConditionKillFrozen),
        )
        is EffectCondition.TargetBelow -> text(
            case.pick(BuildRulesText.ConditionTargetBelow, BuildRulesText.ConditionImpactBelow, BuildRulesText.ConditionKillBelow),
            percent(condition.fraction.toDouble()),
        )
        EffectCondition.SourceImpact -> text(BuildRulesText.ConditionSourceImpact)
        EffectCondition.SourceWeapon -> text(BuildRulesText.ConditionSourceWeapon)
        is EffectCondition.ElementIs -> text(BuildRulesText.ConditionInvolving, element(condition.element))
        is EffectCondition.ReactionIs -> text(BuildRulesText.ConditionItIs, reactionName(condition.reaction))
        is EffectCondition.SkillIs -> text(BuildRulesText.ConditionItIs, localized(DefaultSkillNames[condition.skill.ordinal]))
        is EffectCondition.SkillTagged -> text(BuildRulesText.ConditionSkillTagged, localized(condition.tag.displayLabel))
        EffectCondition.CoreInField -> text(BuildRulesText.ConditionCoreInField)
        EffectCondition.TargetMarked -> text(
            case.pick(BuildRulesText.ConditionTargetMarked, BuildRulesText.ConditionImpactMarked, BuildRulesText.ConditionKillMarked),
        )
        EffectCondition.TargetControlled -> text(
            case.pick(BuildRulesText.ConditionTargetControlled, BuildRulesText.ConditionImpactControlled, BuildRulesText.ConditionKillControlled),
        )
        EffectCondition.TargetThrown -> text(
            case.pick(BuildRulesText.ConditionTargetThrown, BuildRulesText.ConditionImpactThrown, BuildRulesText.ConditionKillThrown),
        )
        EffectCondition.ApexActive -> text(BuildRulesText.ConditionApexActive)
        is EffectCondition.SkillsCharging ->
            if (condition.count == 1) text(BuildRulesText.ConditionSkillCharging)
            else text(BuildRulesText.ConditionSkillsCharging, condition.count)
        is EffectCondition.WeaponFamilyIs -> text(BuildRulesText.ConditionWeaponFamily, localized(condition.family.displayLabel))
        is EffectCondition.ObstacleIs -> text(BuildRulesText.ConditionObstacleIs, text(condition.kind.ruleName))
        is EffectCondition.NearObstacle -> text(BuildRulesText.ConditionNearObstacle, language.number(condition.distance.toDouble(), 0))
    }

    /** %-of-maximum-integrity terms ignore stack multipliers and the bonus; every other basis scales with them. */
    private fun amount(basis: DamageBasis, scale: Float): String = when (basis) {
        DamageBasis.HIT -> text(BuildRulesText.BasisHit, percent(scale * gain))
        DamageBasis.IMPACT, DamageBasis.BRAKE -> text(BuildRulesText.BasisImpact, percent(scale * gain))
        DamageBasis.VICTIM_MAX_HP -> text(BuildRulesText.BasisMaxIntegrity, percent(scale.toDouble()))
        DamageBasis.FLAT -> resolvedDamage(scale, context.flatScale) ?: text(BuildRulesText.BasisFlat)
        DamageBasis.WEAPON -> resolvedDamage(scale, context.weaponUnit) ?: text(BuildRulesText.BasisWeapon)
        DamageBasis.SHIELD -> resolvedDamage(scale, context.shieldCapacity) ?: text(BuildRulesText.BasisShield)
    }

    private fun resolvedDamage(scale: Float, unit: Float?): String? =
        unit?.let { text(BuildRulesText.BasisDamage, language.number(scale * it * gain, DAMAGE_DECIMALS)) }

    private fun pickStacks(count: Int, pick: ElementPick): String {
        val resolved = resolve(pick)
        return if (resolved != null) stacks(count.toDouble(), element(resolved), named = true)
        else stacks(count.toDouble(), pickName(pick), named = false)
    }

    /** English names a resolved element by itself ("2 Gravitic"); Russian always counts layers. */
    fun stacks(count: Double, of: String, named: Boolean): String {
        val amount = language.number(count, FLAT_DECIMALS)
        if (named && language == AppLanguage.English) return text(BuildRulesText.ElementAmount, amount, of)
        return text(
            when (countForm(count)) {
                CountForm.ONE -> BuildRulesText.StackAmountOne
                CountForm.FEW -> BuildRulesText.StackAmountFew
                CountForm.MANY -> BuildRulesText.StackAmountMany
            },
            amount, of,
        )
    }

    private fun pickName(pick: ElementPick): String = resolve(pick)?.let(::element) ?: text(
        if (pick == ElementPick.SECONDARY) BuildRulesText.PickSecondary else BuildRulesText.PickDominant,
    )

    fun resolve(pick: ElementPick): StatusElement? = pick.element ?: when (pick) {
        ElementPick.DOMINANT -> context.dominant
        ElementPick.SECONDARY -> context.secondary
        else -> null
    }

    private fun actionElement(action: EffectAction): StatusElement? = when (action) {
        is EffectAction.ApplyStatus -> resolve(action.pick)
        is EffectAction.Chain -> action.pick?.let(::resolve)
        is EffectAction.Shards -> action.pick?.let(::resolve)
        is EffectAction.Well -> action.pick?.let(::resolve)
        is EffectAction.Detonate -> action.elements.first()
        else -> null
    }

    fun conditionElement(condition: EffectCondition): StatusElement? = when (condition) {
        is EffectCondition.TargetHas -> condition.element
        is EffectCondition.ElementIs -> condition.element
        EffectCondition.TargetFrozen -> StatusElement.CRYO
        else -> null
    }

    /** English label, or the Russian genitive every Russian element slot takes. */
    fun element(element: StatusElement): String = when (language) {
        AppLanguage.English -> element.displayLabel
        AppLanguage.Russian -> when (element) {
            StatusElement.THERMAL -> "Термо"
            StatusElement.CRYO -> "Крио"
            StatusElement.ION -> "Иона"
            StatusElement.GRAVITIC -> "Гравитации"
            StatusElement.ENTROPY -> "Энтропии"
            StatusElement.SONIC -> "Звука"
            StatusElement.VOID -> "Пустоты"
        }
    }

    fun breakName(element: StatusElement, genitive: Boolean): String = when (language) {
        AppLanguage.English -> element.breakLabel
        AppLanguage.Russian -> if (!genitive) element.breakLabel.localizedContent(language) else when (element) {
            StatusElement.THERMAL -> "Воспламенения"
            StatusElement.CRYO -> "Заморозки"
            StatusElement.ION -> "Разряда"
            StatusElement.GRAVITIC -> "Коллапса"
            StatusElement.ENTROPY -> "Разрыва"
            StatusElement.SONIC -> "Раскола"
            StatusElement.VOID -> "Развоплощения"
        }
    }

    /** A reaction's name in this language. */
    fun reactionName(reaction: ReactionId): String = localized(DefaultReactions.reaction(reaction).name)

    /** An element's own name (nominative in Russian), as a label rather than an element slot. */
    fun elementName(element: StatusElement): String = localized(element.displayLabel)

    fun localized(english: String): String = english.localizedContent(language)

    fun line(cost: Boolean, text: String, element: StatusElement?): RuleLine =
        RuleLine(if (cost) RuleLineKind.COST else RuleLineKind.RULE, text, element, negative = cost)

    fun scaledStacks(stacks: Int): Int = floor(stacks * magnitude + 0.5 + ROUNDING_EPSILON).toInt().coerceAtLeast(1)

    fun countForm(count: Double): CountForm {
        val whole = floor(count + ROUNDING_EPSILON)
        if (abs(count - whole) > ROUNDING_EPSILON) return if (language == AppLanguage.English) CountForm.MANY else CountForm.FEW
        val n = whole.toLong()
        return when (language) {
            AppLanguage.English -> if (n == 1L) CountForm.ONE else CountForm.MANY
            AppLanguage.Russian -> when {
                n % 10 == 1L && n % 100 != 11L -> CountForm.ONE
                n % 10 in 2L..4L && n % 100 !in 12L..14L -> CountForm.FEW
                else -> CountForm.MANY
            }
        }
    }

    private fun ordinal(n: Int): String = when (language) {
        AppLanguage.Russian -> n.toString()
        AppLanguage.English -> n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    private fun join(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(text(BuildRulesText.ListComma)) + text(BuildRulesText.ListAnd) + parts.last()
    }

    fun percent(fraction: Double): String = text(BuildRulesText.UnitPercent, language.number(fraction * 100.0, PERCENT_DECIMALS))

    fun signedPercent(fraction: Double): String = sign(fraction) + percent(abs(fraction))

    private fun signed(value: Double, decimals: Int): String = sign(value) + language.number(abs(value), decimals)

    private fun sign(value: Double): String = if (value < 0.0) "−" else "+"

    fun seconds(value: Double): String = text(BuildRulesText.UnitSeconds, language.number(value, SECOND_DECIMALS))

    fun speed(value: Float): String = text(BuildRulesText.UnitSpeed, language.number(value.toDouble(), 0))

    fun text(resource: BuildRulesText, vararg arguments: Any): String = language.text(resource, *arguments)
}

private fun TargetCase.pick(hit: BuildRulesText, impact: BuildRulesText, kill: BuildRulesText): BuildRulesText = when (this) {
    TargetCase.HIT -> hit
    TargetCase.IMPACT -> impact
    TargetCase.KILL -> kill
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
