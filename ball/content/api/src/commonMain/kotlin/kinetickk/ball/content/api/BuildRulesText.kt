// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.TextResource
import kinetickk.foundation.common.localization.text
import kotlin.math.abs
import kotlin.math.floor

/**
 * Values that turn an item's data into concrete numbers for one owner. Without a scale, flat,
 * weapon and shield amounts read as neutral phrases; without an affinity, dominant and secondary
 * picks read as "your dominant element" and "your second element".
 */
data class RuleTextContext(
    /** Multiplies percentages, amounts and status stacks (stacks round half up, minimum 1). */
    val stackMagnitude: Float = 1f,
    val dominant: StatusElement? = null,
    val secondary: StatusElement? = null,
    /** Run progress and Rebirth factor applied to [DamageBasis.FLAT] amounts. */
    val flatScale: Float? = null,
    /** Damage of one [DamageBasis.WEAPON] unit (40 × ability power). */
    val weaponUnit: Float? = null,
    val shieldCapacity: Float? = null,
    /**
     * The item's Reborn and Relic attunement multiplier, as gameplay applies it: it scales damage,
     * stats, buffs, system powers and resource gains, never stack counts, heat, durations or terms
     * based on maximum integrity.
     */
    val bonus: Float = 1f,
    /** Copies of the item the text describes; threshold cuts and overfill grow per copy, not by magnitude. */
    val stacks: Int = 1,
) {
    init {
        require(stackMagnitude.isFinite() && stackMagnitude > 0f) { "Stack magnitude must be finite and positive" }
        require(stacks in 1..ContentBounds.MAX_ITEM_STACKS) { "Rule text stack count is outside the item bound" }
        require(bonus.isFinite() && bonus > 0f) { "Item bonus must be finite and positive" }
        require(listOfNotNull(flatScale, weaponUnit, shieldCapacity).all { it.isFinite() && it >= 0f }) {
            "Rule text scales must be finite and non-negative"
        }
    }
}

enum class RuleLineKind { PASSIVE, TRIGGER, EFFECT, RULE, COST }

/** One display line. [negative] marks a cost; [element] tints the line when it names one. */
data class RuleLine(
    val kind: RuleLineKind,
    val text: String,
    val element: StatusElement? = null,
    val negative: Boolean = false,
)

/**
 * Every phrase of the generated mechanical text, in both languages. `{n}` arguments are filled
 * with already formatted values; element slots take the English label or the Russian genitive.
 */
enum class BuildRulesText(override val english: String, override val russian: String) : TextResource {
    OnImpact("On impact", "При столкновении"),
    OnWeaponHit("On weapon hit", "При попадании оружия"),
    OnCrit("On critical hit", "При критическом ударе"),
    OnKill("On kill", "При убийстве"),
    OnDash("On dash", "При рывке"),
    OnDashHit("On dash hit", "При попадании рывком"),
    OnBrakeRelease("On brake release", "При отпускании тормоза"),
    OnTurn("On turn", "При повороте"),
    OnHurt("When hit", "При получении урона"),
    OnShieldBreak("On shield break", "При разрушении щита"),
    OnOverdrive("On Overdrive", "При перегрузке"),
    OnOverheat("On overheat", "При перегреве"),
    OnReaction("On reaction", "При реакции"),
    OnBreak("On break", "При пробое"),
    OnLevelUp("On level up", "При повышении уровня"),
    OnPickup("On data pickup", "При сборе данных"),
    OnRicochet("On ricochet", "При рикошете"),
    OnSlam("On slam", "При впечатывании врага"),

    EveryImpact("Every {0} impact", "Каждое {0}-е столкновение"),
    EveryWeaponHit("Every {0} weapon hit", "Каждое {0}-е попадание оружия"),
    EveryCrit("Every {0} critical hit", "Каждый {0}-й критический удар"),
    EveryKill("Every {0} kill", "Каждое {0}-е убийство"),
    EveryDash("Every {0} dash", "Каждый {0}-й рывок"),
    EveryDashHit("Every {0} dash hit", "Каждое {0}-е попадание рывком"),
    EveryBrakeRelease("Every {0} brake release", "Каждое {0}-е отпускание тормоза"),
    EveryTurn("Every {0} turn", "Каждый {0}-й поворот"),
    EveryHurt("Every {0} hit taken", "Каждый {0}-й полученный удар"),
    EveryShieldBreak("Every {0} shield break", "Каждое {0}-е разрушение щита"),
    EveryOverdrive("Every {0} Overdrive", "Каждая {0}-я перегрузка"),
    EveryOverheat("Every {0} overheat", "Каждый {0}-й перегрев"),
    EveryReaction("Every {0} reaction", "Каждая {0}-я реакция"),
    EveryBreak("Every {0} break", "Каждый {0}-й пробой"),
    EveryLevelUp("Every {0} level up", "Каждое {0}-е повышение уровня"),
    EveryPickup("Every {0} data pickup", "Каждый {0}-й сбор данных"),
    EveryRicochet("Every {0} ricochet", "Каждый {0}-й рикошет"),
    EverySlam("Every {0} slam", "Каждое {0}-е впечатывание врага"),

    OnElementBreak("On {0} break", "При пробое {0}"),
    EveryElementBreak("Every {0} {1} break", "Каждый {0}-й пробой {1}"),
    OnElementReaction("On a reaction involving {0}", "При реакции с участием {0}"),
    EveryElementReaction("Every {0} reaction involving {1}", "Каждая {0}-я реакция с участием {1}"),
    OnElementPairReaction("On a reaction between {0} and {1}", "При реакции {0} и {1}"),
    EveryElementPairReaction("Every {0} reaction between {1} and {2}", "Каждая {0}-я реакция {1} и {2}"),
    TriggerCooldown(", once every {0}", ", не чаще раза в {0}"),
    TriggerLuck(", sooner with Luck", ", с удачей чаще"),

    ConditionSpeedAtLeast("above {0}", "на скорости выше {0}"),
    ConditionSpeedBelow("below {0}", "на скорости ниже {0}"),
    ConditionIntegrityBelow("below {0} integrity", "при прочности ниже {0}"),
    ConditionHeatAtLeast("at {0} heat or more", "при нагреве от {0}"),
    ConditionShieldUp("while shielded", "пока активен щит"),
    ConditionOverdrive("during Overdrive", "во время перегрузки"),
    ConditionBraking("while braking", "во время торможения"),
    ConditionCombo("at combo {0} or higher", "с комбо от {0}"),
    ConditionTargetAnyStatus("against enemies with any status", "по врагам под действием любой стихии"),
    ConditionTargetAnyStatusStacks("against enemies with {0} or more status stacks", "по врагам, на которых от {0} слоёв любых стихий"),
    ConditionTargetElement("against enemies with {0}", "по врагам под действием {0}"),
    ConditionTargetElementStacks("against enemies with {0} or more {1}", "по врагам, на которых от {0} слоёв {1}"),
    ConditionTargetElite("against elites", "по элитным врагам"),
    ConditionTargetFrozen("against Frozen enemies", "по замороженным врагам"),
    ConditionTargetBelow("against enemies below {0} integrity", "по врагам с прочностью ниже {0}"),
    ConditionImpactAnyStatus("with an enemy under any status", "с врагом под действием любой стихии"),
    ConditionImpactAnyStatusStacks("with an enemy carrying {0} or more status stacks", "с врагом, на котором от {0} слоёв любых стихий"),
    ConditionImpactElement("with an enemy carrying {0}", "с врагом под действием {0}"),
    ConditionImpactElementStacks("with an enemy carrying {0} or more {1}", "с врагом, на котором от {0} слоёв {1}"),
    ConditionImpactElite("with an elite", "с элитным врагом"),
    ConditionImpactFrozen("with a Frozen enemy", "с замороженным врагом"),
    ConditionImpactBelow("with an enemy below {0} integrity", "с врагом, у которого прочность ниже {0}"),
    ConditionKillAnyStatus("of an enemy under any status", "врага под действием любой стихии"),
    ConditionKillAnyStatusStacks("of an enemy carrying {0} or more status stacks", "врага, на котором от {0} слоёв любых стихий"),
    ConditionKillElement("of an enemy carrying {0}", "врага под действием {0}"),
    ConditionKillElementStacks("of an enemy carrying {0} or more {1}", "врага, на котором от {0} слоёв {1}"),
    ConditionKillElite("of an elite", "элитного врага"),
    ConditionKillFrozen("of a Frozen enemy", "замороженного врага"),
    ConditionKillBelow("of an enemy below {0} integrity", "врага с прочностью ниже {0}"),
    ConditionSourceImpact("by impact", "столкновением"),
    ConditionSourceWeapon("by the weapon", "оружием"),
    ConditionInvolving("involving {0}", "с участием {0}"),

    PickDominant("your dominant element", "вашей основной стихии"),
    PickSecondary("your second element", "вашей второй стихии"),
    IncomingElement("the incoming element", "входящей стихии"),
    ElementAmount("{0} {1}", "{0} {1}"),
    StackAmountOne("{0} stack of {1}", "{0} слой {1}"),
    StackAmountFew("{0} stacks of {1}", "{0} слоя {1}"),
    StackAmountMany("{0} stacks of {1}", "{0} слоёв {1}"),

    BasisHit("{0} of the hit", "{0} урона попадания"),
    BasisImpact("{0} of impact damage", "{0} урона от столкновения"),
    BasisMaxIntegrity("{0} of its maximum integrity", "{0} от максимальной прочности"),
    BasisDamage("{0} damage", "{0} урона"),
    BasisFlat("damage that grows over the run", "урон, растущий по ходу забега"),
    BasisWeapon("damage based on weapon power", "урон от мощности оружия"),
    BasisShield("damage based on shield capacity", "урон от ёмкости щита"),

    ActionApply("apply {0}", "накладывает {0}"),
    ActionApplyNearby("apply {0} nearby", "накладывает {0} рядом"),
    ActionBurst("burst nearby for {0}", "наносит врагам рядом {0}"),
    ActionStrike("strike the target for {0}", "наносит цели {0}"),
    ActionChainOne("chain to another enemy for {0}", "перескакивает на другого врага, нанося {0}"),
    ActionChain("chain to {0} enemies for {1}", "перескакивает на {0} врагов, нанося {1}"),
    ActionChainApplying(" and apply {0}", " и накладывая {0}"),
    ActionShardsOne("release {0} shard that deals {1}", "выпускает {0} осколок, который наносит {1}"),
    ActionShardsFew("release {0} shards that each deal {1}", "выпускает {0} осколка, каждый наносит {1}"),
    ActionShardsMany("release {0} shards that each deal {1}", "выпускает {0} осколков, каждый наносит {1}"),
    ActionShardsOfOne("release {0} shard of {2} that deals {1}", "выпускает {0} осколок {2}, который наносит {1}"),
    ActionShardsOfFew("release {0} shards of {2} that each deal {1}", "выпускает {0} осколка {2}, каждый наносит {1}"),
    ActionShardsOfMany("release {0} shards of {2} that each deal {1}", "выпускает {0} осколков {2}, каждый наносит {1}"),
    ActionPull("pull nearby enemies in", "притягивает врагов рядом"),
    ActionPush("push nearby enemies away", "отталкивает врагов рядом"),
    ActionHeal("restore {0} integrity", "восстанавливает {0} прочности"),
    ActionShield("gain {0} shield", "даёт {0} щита"),
    ActionVent("vent {0} heat", "снижает нагрев на {0}"),
    ActionOverdrive("charge Overdrive by {0}", "заряжает перегрузку на {0}"),
    ActionBuff("gain {0} for {1}", "даёт {0} на {1}"),
    ActionBuffStacking(", stacking up to {0} times", ", до {0} раз"),
    ActionExtendCombo("extend the combo by {0}", "продлевает комбо на {0}"),
    ActionRefreshWeapon("advance weapon cooldowns by {0}", "ускоряет перезарядку оружия на {0}"),
    ActionGainData("gain {0} Data", "добавляет {0} к данным"),
    ActionDetonate(
        "detonate the target's {0} at once for {1} of the remaining damage",
        "мгновенно подрывает слои {0} на цели, нанося {1} оставшегося урона",
    ),
    ActionWell(
        "open a well for {0} that pulls enemies in and deals {1} each second",
        "открывает колодец на {0}: каждую секунду он притягивает врагов и наносит {1}",
    ),
    ActionWellApplying(
        "open a well for {0} that pulls enemies in, deals {1} and applies {2} each second",
        "открывает колодец на {0}: каждую секунду он притягивает врагов, наносит {1} и накладывает {2}",
    ),

    RuleConditional("{0} {1}", "{0} {1}"),
    RulePotency("{0} {1} potency", "{0} к силе {1}"),
    RuleDurationLonger("{0} stacks last {1} longer", "Слои {0} держатся на {1} дольше"),
    RuleDurationShorter("{0} stacks fade {1} sooner", "Слои {0} рассеиваются на {1} быстрее"),
    RuleThresholdOne("{0} needs 1 fewer stack to break", "Пробою {0} нужно на 1 слой меньше"),
    RuleThresholdFew("{0} needs {1} fewer stacks to break", "Пробою {0} нужно на {1} слоя меньше"),
    RuleBreakPower("{0} {1} power", "{0} к силе {1}"),
    RuleBreakSpread("{0} spreads {1} to nearby enemies", "{0} распространяет {1} на врагов рядом"),
    RuleReactionPower("{0} reaction power", "{0} к силе реакций"),
    RuleReactionResidue("Reactions leave {0} on the target", "Реакции оставляют на цели {0}"),
    RuleReactionRecoil("Reactions near you deal {0} damage to you", "Реакции рядом с вами наносят вам {0} урона"),
    RuleAlwaysCrit("Always critical {0}", "Всегда критический удар {0}"),
    RuleCritDamageVersus("{0} critical damage {1}", "{0} к критическому урону {1}"),
    RuleDamageVersus("{0} damage {1}", "{0} к урону {1}"),
    RuleNonCriticalMore("Non-critical hits deal {0} more", "Некритические удары наносят на {0} больше урона"),
    RuleNonCriticalLess("Non-critical hits deal {0} less", "Некритические удары наносят на {0} меньше урона"),
    RuleComboDamage("{0} damage per combo kill, up to {1}", "{0} к урону за каждое убийство в комбо, до {1}"),
    RuleRerollsOne("+{0} reroll when taken", "+{0} переброс при получении"),
    RuleRerollsFew("+{0} rerolls when taken", "+{0} переброса при получении"),
    RuleRerollsMany("+{0} rerolls when taken", "+{0} перебросов при получении"),
    RuleOfferCards("Offers show one more card (one card-adding item per run)", "В предложениях на одну карту больше (один такой предмет за забег)"),
    RuleGuaranteedCrit(
        "After {0} non-critical hits in a row, the next hit is critical",
        "После {0} некритических ударов подряд следующий удар будет критическим",
    ),
    RuleFreeDash(
        "Cooling below {0} heat makes the next dash free, once every {1}",
        "Остывание ниже {0} нагрева делает следующий рывок бесплатным, не чаще раза в {1}",
    ),
    RuleOverhealToShield(
        "Healing beyond full integrity becomes shield, up to {0} of capacity",
        "Лечение сверх полной прочности становится щитом, до {0} ёмкости",
    ),
    RuleRegenerationNoLongerHeals("Regeneration no longer heals", "Восстановление прочности больше не лечит"),
    RuleRegenerationToDecay(
        "Every second, each point of regeneration applies {0} nearby",
        "Каждую секунду каждая единица восстановления накладывает {0} рядом",
    ),
    RuleCritChanceCap("Critical chance is capped at {0}", "Шанс критического удара не выше {0}"),
    RuleCritChanceToDamage(
        "Each 1\u00A0% of critical chance above the cap becomes {0} critical damage",
        "Каждый 1\u00A0% шанса критического удара сверх предела превращается в {0} критического урона",
    ),

    StatImpactDamage("{0} impact damage", "{0} к урону от столкновений"),
    StatWeaponPower("{0} weapon power", "{0} к мощности оружия"),
    StatMass("{0} mass", "{0} к массе"),
    StatMagnetism("{0} magnetism", "{0} к магнетизму"),
    StatCooling("{0} cooling", "{0} к охлаждению"),
    StatMaxIntegrity("{0} maximum integrity", "{0} к максимальной прочности"),
    StatRegeneration("{0} integrity regeneration", "{0} к восстановлению прочности"),
    StatDashPower("{0} dash power", "{0} к силе рывка"),
    StatDashEfficiency("{0} dash efficiency", "{0} к эффективности рывка"),
    StatCritChance("{0} critical chance", "{0} к шансу критического удара"),
    StatCritDamage("{0} critical damage", "{0} к критическому урону"),
    StatPickupRadius("{0} pickup radius", "{0} к радиусу сбора"),
    StatLuck("{0} luck", "{0} к удаче"),
    StatDataGain("{0} Data gain", "{0} к получению данных"),
    StatMatterGain("{0} Matter gain", "{0} к получению материи"),
    StatAttackSpeed("{0} attack speed", "{0} к скорости атаки"),
    StatShieldCapacity("{0} shield capacity", "{0} к ёмкости щита"),
    StatDamageReduction("{0} damage reduction", "{0} к снижению урона"),
    StatComboWindow("{0} combo window", "{0} к окну комбо"),
    StatOverdriveGain("{0} Overdrive gain", "{0} к накоплению перегрузки"),

    BuffDamage("{0} damage", "{0} к урону"),
    BuffImpact("{0} impact damage", "{0} к урону от столкновений"),
    BuffWeapon("{0} weapon power", "{0} к мощности оружия"),
    BuffActivation("{0} activation speed", "{0} к скорости активации"),
    BuffCritChance("{0} critical chance", "{0} к шансу критического удара"),
    BuffDamageReduction("{0} damage reduction", "{0} к снижению урона"),
    BuffCooling("{0} cooling", "{0} к охлаждению"),
    BuffDamageTaken("{0} damage taken", "{0} к получаемому урону"),

    UnitPercent("{0}\u00A0%", "{0}\u00A0%"),
    UnitSpeed("{0}\u00A0u/s", "{0}\u00A0ед/с"),
    UnitSeconds("{0}\u00A0s", "{0}\u00A0с"),
    UnitPerSecond("{0}/s", "{0}/с"),
    ListComma(", ", ", "),
    ListAnd(" and ", " и "),
}

/**
 * The item's mechanics as display lines: passive stats, then one TRIGGER and one EFFECT line per
 * clause, then rules; every cost line follows the rest in declaration order.
 */
fun ItemDefinition.ruleLines(
    language: AppLanguage,
    context: RuleTextContext = RuleTextContext(),
): ImmutableList<RuleLine> {
    val writer = RuleWriter(language, context)
    val lines = ArrayList<RuleLine>(stats.size + clauses.size * 2 + rules.size * 2)
    stats.forEach { lines += writer.stat(it) }
    clauses.forEach { clause ->
        lines += writer.trigger(clause)
        lines += writer.effect(clause)
    }
    rules.forEach { lines += writer.rule(it) }
    val (costs, rest) = lines.partition { it.kind == RuleLineKind.COST }
    return (rest + costs).toImmutableList()
}

/** The upside in [language] with its `{n}` values at [rank], using the language's decimal separator. */
fun RelicDefinition.upsideText(language: AppLanguage, rank: Int): String =
    RelicValuePlaceholder.replace(upside.localizedContent(language)) { match ->
        match.groupValues[1].toIntOrNull()?.let(values::getOrNull)
            ?.let { value -> language.number(value.at(rank).toDouble(), RELIC_DECIMALS) }
            ?: match.value
    }

/** The mandatory cost in [language]; liabilities never scale with rank. */
fun RelicDefinition.liabilityText(language: AppLanguage): String = liability.localizedContent(language)

private val RelicValuePlaceholder = Regex("\\{([0-9]+)\\}")
private const val RELIC_DECIMALS = 2

/** Critical chance kept by [ConversionKind.CRIT_CHANCE_TO_DAMAGE]. */
private const val CRIT_CHANCE_CONVERSION_CAP = 0.3

private enum class CountForm { ONE, FEW, MANY }

/** Target conditions read differently after a hit, an impact or a kill. */
private enum class TargetCase { HIT, IMPACT, KILL }

private class RuleWriter(private val language: AppLanguage, private val context: RuleTextContext) {
    private val magnitude = context.stackMagnitude.toDouble()

    /** Stack magnitude times the item bonus, for what the bonus scales. */
    private val gain = magnitude * context.bonus

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

    fun rule(rule: ItemRule): List<RuleLine> = when (rule) {
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
        is ItemRule.StatusDuration -> {
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
        is ItemRule.BreakThreshold -> listOf(
            RuleLine(
                RuleLineKind.RULE,
                breakThresholdCut(rule.reduction, context.stacks).let { cut ->
                    if (cut == 1) text(BuildRulesText.RuleThresholdOne, element(rule.element))
                    else text(BuildRulesText.RuleThresholdFew, element(rule.element), cut)
                },
                rule.element,
            ),
        )
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
        is ItemRule.BonusRerolls -> listOf(
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
    }

    private fun conversion(rule: ItemRule.Convert): List<RuleLine> = when (rule.kind) {
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
    }

    private fun condition(condition: EffectCondition, case: TargetCase = TargetCase.HIT): String = when (condition) {
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
    private fun stacks(count: Double, of: String, named: Boolean): String {
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

    private fun resolve(pick: ElementPick): StatusElement? = pick.element ?: when (pick) {
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

    private fun conditionElement(condition: EffectCondition): StatusElement? = when (condition) {
        is EffectCondition.TargetHas -> condition.element
        is EffectCondition.ElementIs -> condition.element
        EffectCondition.TargetFrozen -> StatusElement.CRYO
        else -> null
    }

    /** English label, or the Russian genitive every Russian element slot takes. */
    private fun element(element: StatusElement): String = when (language) {
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

    private fun breakName(element: StatusElement, genitive: Boolean): String = when (language) {
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

    private fun line(cost: Boolean, text: String, element: StatusElement?): RuleLine =
        RuleLine(if (cost) RuleLineKind.COST else RuleLineKind.RULE, text, element, negative = cost)

    private fun scaledStacks(stacks: Int): Int = floor(stacks * magnitude + 0.5 + ROUNDING_EPSILON).toInt().coerceAtLeast(1)

    private fun countForm(count: Double): CountForm {
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

    private fun percent(fraction: Double): String = text(BuildRulesText.UnitPercent, language.number(fraction * 100.0, PERCENT_DECIMALS))

    private fun signedPercent(fraction: Double): String = sign(fraction) + percent(abs(fraction))

    private fun signed(value: Double, decimals: Int): String = sign(value) + language.number(abs(value), decimals)

    private fun sign(value: Double): String = if (value < 0.0) "−" else "+"

    private fun seconds(value: Double): String = text(BuildRulesText.UnitSeconds, language.number(value, SECOND_DECIMALS))

    private fun speed(value: Float): String = text(BuildRulesText.UnitSpeed, language.number(value.toDouble(), 0))

    private fun text(resource: BuildRulesText, vararg arguments: Any): String = language.text(resource, *arguments)
}

private fun TargetCase.pick(hit: BuildRulesText, impact: BuildRulesText, kill: BuildRulesText): BuildRulesText = when (this) {
    TargetCase.HIT -> hit
    TargetCase.IMPACT -> impact
    TargetCase.KILL -> kill
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

private const val PERCENT_DECIMALS = 1
private const val FLAT_DECIMALS = 1
private const val SECOND_DECIMALS = 2
private const val DAMAGE_DECIMALS = 0
private const val MAX_SHORTENING = 0.95

/** Venting never removes more than the whole 0..100 heat gauge. */
private const val MAX_HEAT = 100.0
private const val ROUNDING_EPSILON = 1e-6

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

private val ItemEffect.phrase: BuildRulesText
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
    }

private val BuffStat.phrase: BuildRulesText
    get() = when (this) {
        BuffStat.DAMAGE -> BuildRulesText.BuffDamage
        BuffStat.IMPACT -> BuildRulesText.BuffImpact
        BuffStat.WEAPON -> BuildRulesText.BuffWeapon
        BuffStat.ACTIVATION -> BuildRulesText.BuffActivation
        BuffStat.CRIT_CHANCE -> BuildRulesText.BuffCritChance
        BuffStat.DAMAGE_REDUCTION -> BuildRulesText.BuffDamageReduction
        BuffStat.COOLING -> BuildRulesText.BuffCooling
        BuffStat.DAMAGE_TAKEN -> BuildRulesText.BuffDamageTaken
    }

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
    }

private val EffectTrigger.everyPhrase: BuildRulesText
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
    }
