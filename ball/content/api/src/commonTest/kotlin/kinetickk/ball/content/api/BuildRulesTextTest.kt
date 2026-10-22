// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildRulesTextTest {
    @Test
    fun everyPhraseHasBothLanguagesWithMatchingPlaceholders() {
        BuildRulesText.entries.forEach { phrase ->
            assertTrue(phrase.english.isNotBlank() && phrase.russian.isNotBlank(), "$phrase is blank")
            assertEquals(placeholders(phrase.english), placeholders(phrase.russian), "$phrase placeholders differ")
            assertFalse(phrase.russian.any { it in 'A'..'Z' || it in 'a'..'z' }, "$phrase Russian has Latin letters")
            assertFalse(phrase.english.any { it in 'Ѐ'..'ӿ' }, "$phrase English has Cyrillic")
            listOf("·", "→", "←").forEach { mark -> assertFalse(mark in phrase.english || mark in phrase.russian, "$phrase uses $mark") }
        }
        EffectTrigger.entries.forEach { trigger ->
            assertEquals(trigger.displayLabel, trigger.onPhrase.english)
            assertEquals(trigger.displayLabel.localizedContent(AppLanguage.Russian), trigger.onPhrase.russian)
        }
    }

    @Test
    fun clausesReadAsOneTriggerAndOneEffectSentenceWithCostsLast() {
        val item = item(
            stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.06f), StatDelta(ItemEffect.DASH_POWER, -0.2f)),
            clauses = immutableListOf(
                EffectClause(
                    EffectTrigger.IMPACT,
                    immutableListOf(EffectCondition.SpeedAtLeast(1_200f)),
                    immutableListOf(EffectAction.Burst(80f, DamageBasis.HIT, 0.25f), EffectAction.Push(80f, 100f)),
                ),
            ),
            risk = true,
        )

        assertEquals(
            listOf(
                RuleLine(RuleLineKind.PASSIVE, "+6 % impact damage"),
                RuleLine(RuleLineKind.TRIGGER, "On impact above 1,200 u/s"),
                RuleLine(RuleLineKind.EFFECT, "Burst nearby for 25 % of the hit and push nearby enemies away"),
                RuleLine(RuleLineKind.COST, "−20 % dash power", negative = true),
            ),
            item.ruleLines(AppLanguage.English).plain(),
        )
        assertEquals(
            listOf(
                "+6 % к урону от столкновений",
                "При столкновении на скорости выше 1 200 ед/с",
                "Наносит врагам рядом 25 % урона попадания и отталкивает врагов рядом",
                "−20 % к силе рывка",
            ),
            item.ruleLines(AppLanguage.Russian).plain().map { it.text },
        )
    }

    @Test
    fun everyNthTriggersUseOrdinalsCooldownsAndTheLuckNote() {
        fun trigger(trigger: EffectTrigger, every: Int, language: AppLanguage, cooldown: Float = 0f, luck: Boolean = false) =
            item(
                clauses = immutableListOf(
                    EffectClause(trigger, actions = immutableListOf(EffectAction.Vent(5f)), cooldownSeconds = cooldown, everyNth = every, luckShortened = luck),
                ),
            ).ruleLines(language).plain().first { it.kind == RuleLineKind.TRIGGER }.text

        assertEquals(
            listOf("2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd"),
            listOf(2, 3, 4, 11, 12, 13, 21, 22, 23).map { trigger(EffectTrigger.KILL, it, AppLanguage.English).removePrefix("Every ").removeSuffix(" kill") },
        )
        assertEquals("Каждое 3-е попадание оружия", trigger(EffectTrigger.WEAPON_HIT, 3, AppLanguage.Russian))
        assertEquals("Каждый 2-й рывок", trigger(EffectTrigger.DASH, 2, AppLanguage.Russian))
        assertEquals("Every 8th kill, sooner with Luck", trigger(EffectTrigger.KILL, 8, AppLanguage.English, luck = true))
        assertEquals("Каждое 8-е убийство, с удачей чаще", trigger(EffectTrigger.KILL, 8, AppLanguage.Russian, luck = true))
        assertEquals("On brake release, once every 2 s", trigger(EffectTrigger.BRAKE_RELEASE, 1, AppLanguage.English, cooldown = 2f))
        assertEquals("При отпускании тормоза, не чаще раза в 0,5 с", trigger(EffectTrigger.BRAKE_RELEASE, 1, AppLanguage.Russian, cooldown = 0.5f))
        assertEquals("On ricochet, once every 2 s", trigger(EffectTrigger.RICOCHET, 1, AppLanguage.English, cooldown = 2f))
        assertEquals("При рикошете, не чаще раза в 0,5 с", trigger(EffectTrigger.RICOCHET, 1, AppLanguage.Russian, cooldown = 0.5f))
        assertEquals("Every 3rd ricochet", trigger(EffectTrigger.RICOCHET, 3, AppLanguage.English))
        assertEquals("Каждый 3-й рикошет", trigger(EffectTrigger.RICOCHET, 3, AppLanguage.Russian))
        assertEquals("On slam, once every 2 s", trigger(EffectTrigger.SLAM, 1, AppLanguage.English, cooldown = 2f))
        assertEquals("При впечатывании врага, не чаще раза в 0,5 с", trigger(EffectTrigger.SLAM, 1, AppLanguage.Russian, cooldown = 0.5f))
        assertEquals("Every 2nd slam", trigger(EffectTrigger.SLAM, 2, AppLanguage.English))
        assertEquals("Каждое 2-е впечатывание врага", trigger(EffectTrigger.SLAM, 2, AppLanguage.Russian))
    }

    @Test
    fun elementConditionsFoldIntoBreakTriggersAndTargetsFollowTheTrigger() {
        val thermalBreak = item(
            elements = immutableListOf(StatusElement.THERMAL),
            clauses = immutableListOf(
                EffectClause(
                    EffectTrigger.BREAK,
                    immutableListOf(EffectCondition.ElementIs(StatusElement.THERMAL)),
                    immutableListOf(EffectAction.Heal(3f), EffectAction.Shield(5f)),
                    cooldownSeconds = 0.5f,
                ),
            ),
        )
        assertEquals(
            listOf(
                RuleLine(RuleLineKind.TRIGGER, "On Thermal break, once every 0.5 s", StatusElement.THERMAL),
                RuleLine(RuleLineKind.EFFECT, "Restore 3 integrity and gain 5 shield"),
            ),
            thermalBreak.ruleLines(AppLanguage.English).plain(),
        )
        assertEquals(
            listOf("При пробое Термо, не чаще раза в 0,5 с", "Восстанавливает 3 прочности и даёт 5 щита"),
            thermalBreak.ruleLines(AppLanguage.Russian).plain().map { it.text },
        )

        // Two elements on a reaction name the one reaction between them; one element reads as "involving".
        fun reaction(language: AppLanguage, every: Int, vararg elements: StatusElement) = item(
            elements = immutableListOf(StatusElement.THERMAL, StatusElement.SONIC),
            clauses = immutableListOf(
                EffectClause(
                    EffectTrigger.REACTION,
                    elements.map { EffectCondition.ElementIs(it) }.toImmutableList(),
                    immutableListOf(EffectAction.Burst(160f, DamageBasis.FLAT, 14f)),
                    everyNth = every,
                ),
            ),
        ).ruleLines(language).first()
        assertEquals(
            RuleLine(RuleLineKind.TRIGGER, "On a reaction between Thermal and Sonic", StatusElement.THERMAL),
            reaction(AppLanguage.English, 1, StatusElement.THERMAL, StatusElement.SONIC),
        )
        assertEquals("При реакции Термо и Звука", reaction(AppLanguage.Russian, 1, StatusElement.THERMAL, StatusElement.SONIC).text)
        assertEquals("Every 2nd reaction between Thermal and Sonic", reaction(AppLanguage.English, 2, StatusElement.THERMAL, StatusElement.SONIC).text)
        assertEquals("Каждая 2-я реакция Термо и Звука", reaction(AppLanguage.Russian, 2, StatusElement.THERMAL, StatusElement.SONIC).text)
        assertEquals("On a reaction involving Sonic", reaction(AppLanguage.English, 1, StatusElement.SONIC).text)

        fun head(trigger: EffectTrigger, condition: EffectCondition, language: AppLanguage) = item(
            elements = immutableListOf(StatusElement.ENTROPY),
            clauses = immutableListOf(EffectClause(trigger, immutableListOf(condition), immutableListOf(EffectAction.GainData(5f)))),
        ).ruleLines(language).first().text

        assertEquals("On kill of an elite", head(EffectTrigger.KILL, EffectCondition.TargetElite, AppLanguage.English))
        assertEquals("При убийстве элитного врага", head(EffectTrigger.KILL, EffectCondition.TargetElite, AppLanguage.Russian))
        assertEquals("При убийстве врага под действием Энтропии", head(EffectTrigger.KILL, EffectCondition.TargetHas(StatusElement.ENTROPY), AppLanguage.Russian))
        assertEquals("On impact with a Frozen enemy", head(EffectTrigger.IMPACT, EffectCondition.TargetFrozen, AppLanguage.English))
        assertEquals("При столкновении с замороженным врагом", head(EffectTrigger.IMPACT, EffectCondition.TargetFrozen, AppLanguage.Russian))
        assertEquals("При критическом ударе по замороженным врагам", head(EffectTrigger.CRIT, EffectCondition.TargetFrozen, AppLanguage.Russian))
        assertEquals(
            "On critical hit against enemies with 4 or more Ion",
            head(EffectTrigger.CRIT, EffectCondition.TargetHas(StatusElement.ION, 4), AppLanguage.English),
        )
        assertEquals("При убийстве с комбо от 10", head(EffectTrigger.KILL, EffectCondition.ComboAtLeast(10), AppLanguage.Russian))
        assertEquals("При реакции с участием Гравитации", head(EffectTrigger.REACTION, EffectCondition.ElementIs(StatusElement.GRAVITIC), AppLanguage.Russian))

        // Any-status thresholds keep their stack count: gameplay sums every status on the target.
        assertEquals("On weapon hit against enemies with any status", head(EffectTrigger.WEAPON_HIT, EffectCondition.TargetHas(null), AppLanguage.English))
        assertEquals(
            "On weapon hit against enemies with 3 or more status stacks",
            head(EffectTrigger.WEAPON_HIT, EffectCondition.TargetHas(null, 3), AppLanguage.English),
        )
        assertEquals(
            "On impact with an enemy carrying 5 or more status stacks",
            head(EffectTrigger.IMPACT, EffectCondition.TargetHas(null, 5), AppLanguage.English),
        )
        assertEquals("При убийстве врага, на котором от 4 слоёв любых стихий", head(EffectTrigger.KILL, EffectCondition.TargetHas(null, 4), AppLanguage.Russian))
        assertEquals(
            "При попадании оружия по врагам, на которых от 3 слоёв любых стихий",
            head(EffectTrigger.WEAPON_HIT, EffectCondition.TargetHas(null, 3), AppLanguage.Russian),
        )
    }

    @Test
    fun stacksScaleWithMagnitudeRoundHalfUpAndNameTheElement() {
        val igniter = item(
            elements = immutableListOf(StatusElement.THERMAL),
            clauses = immutableListOf(EffectClause(EffectTrigger.IMPACT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.THERMAL, 2)))),
        )
        fun effect(magnitude: Float, language: AppLanguage) =
            igniter.ruleLines(language, RuleTextContext(stackMagnitude = magnitude)).first { it.kind == RuleLineKind.EFFECT }

        assertEquals("Apply 2 Thermal", effect(1f, AppLanguage.English).text)
        assertEquals(StatusElement.THERMAL, effect(1f, AppLanguage.English).element)
        assertEquals("Apply 3 Thermal", effect(1.7f, AppLanguage.English).text)
        assertEquals("Apply 4 Thermal", effect(2.2f, AppLanguage.English).text)
        assertEquals("Apply 5 Thermal", effect(2.25f, AppLanguage.English).text)
        assertEquals("Apply 1 Thermal", effect(0.1f, AppLanguage.English).text)
        assertEquals("Накладывает 1 слой Термо", effect(0.5f, AppLanguage.Russian).text)
        assertEquals("Накладывает 3 слоя Термо", effect(1.7f, AppLanguage.Russian).text)
        assertEquals("Накладывает 5 слоёв Термо", effect(2.25f, AppLanguage.Russian).text)
    }

    @Test
    fun rebornBonusScalesPercentagesAndGainsButNeverCountsHeatOrDurations() {
        val compound = item(
            elements = immutableListOf(StatusElement.THERMAL),
            stats = immutableListOf(StatDelta(ItemEffect.WEAPON_POWER, 0.1f), StatDelta(ItemEffect.COOLING, -0.2f)),
            clauses = immutableListOf(
                EffectClause(
                    EffectTrigger.DASH,
                    actions = immutableListOf(
                        EffectAction.ApplyStatus(ElementPick.THERMAL, 2),
                        EffectAction.Shield(45f),
                        EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.2f),
                    ),
                ),
                EffectClause(
                    EffectTrigger.DASH,
                    actions = immutableListOf(EffectAction.Burst(100f, DamageBasis.HIT, 0.4f), EffectAction.Vent(10f), EffectAction.ExtendCombo(1f)),
                ),
            ),
            rules = immutableListOf(ItemRule.ReactionPower(0.2f), ItemRule.ReactionRecoil(5f, 120f), ItemRule.StatusDuration(StatusElement.THERMAL, 1.5f)),
            risk = true,
        )
        val reborn = compound.ruleLines(AppLanguage.English, RuleTextContext(bonus = 1.5f)).plain().map { it.text }

        assertEquals(
            listOf(
                "+15 % weapon power",
                "On dash",
                "Apply 2 Thermal, gain 67.5 shield and strike the target for 20 % of its maximum integrity",
                "On dash",
                "Burst nearby for 60 % of the hit, vent 10 heat and extend the combo by 1 s",
                "+30 % reaction power",
                "Thermal stacks last 50 % longer",
                "−30 % cooling",
                "Reactions near you deal 5 damage to you",
            ),
            reborn,
        )
        // Stack magnitude and the bonus multiply; stack counts still follow the magnitude alone.
        val stacked = compound.ruleLines(AppLanguage.English, RuleTextContext(stackMagnitude = 1.7f, bonus = 1.5f)).plain()
        assertEquals(
            listOf(
                "Apply 3 Thermal, gain 114.8 shield and strike the target for 20 % of its maximum integrity",
                "Burst nearby for 102 % of the hit, vent 17 heat and extend the combo by 1.7 s",
            ),
            stacked.filter { it.kind == RuleLineKind.EFFECT }.map { it.text },
        )
        assertEquals(compound.ruleLines(AppLanguage.Russian), compound.ruleLines(AppLanguage.Russian, RuleTextContext(bonus = 1f)))
    }

    @Test
    fun luckReadsAsAPlainScalar() {
        val coin = item(stats = immutableListOf(StatDelta(ItemEffect.LUCK, 0.3f)))

        assertEquals("+0.3 luck", coin.ruleLines(AppLanguage.English).single().text)
        assertEquals("+0,3 к удаче", coin.ruleLines(AppLanguage.Russian).single().text)
    }

    @Test
    fun dominantAndSecondaryPicksResolveFromTheContextOrStayNeutral() {
        val rounds = item(
            clauses = immutableListOf(
                EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.DOMINANT, 1)), everyNth = 4),
                EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.SECONDARY, 2, 100f)), everyNth = 5),
            ),
        )
        val neutral = rounds.ruleLines(AppLanguage.English).filter { it.kind == RuleLineKind.EFFECT }
        assertEquals(listOf("Apply 1 stack of your dominant element", "Apply 2 stacks of your second element nearby"), neutral.map { it.text })
        assertEquals(listOf(null, null), neutral.map { it.element })
        assertEquals(
            listOf("Накладывает 1 слой вашей основной стихии", "Накладывает 2 слоя вашей второй стихии рядом"),
            rounds.ruleLines(AppLanguage.Russian).filter { it.kind == RuleLineKind.EFFECT }.map { it.text },
        )

        val context = RuleTextContext(dominant = StatusElement.CRYO, secondary = StatusElement.ION)
        val resolved = rounds.ruleLines(AppLanguage.English, context).filter { it.kind == RuleLineKind.EFFECT }
        assertEquals(listOf("Apply 1 Cryo", "Apply 2 Ion nearby"), resolved.map { it.text })
        assertEquals(listOf(StatusElement.CRYO, StatusElement.ION), resolved.map { it.element })
        assertEquals(
            listOf("Накладывает 1 слой Крио", "Накладывает 2 слоя Иона рядом"),
            rounds.ruleLines(AppLanguage.Russian, context).filter { it.kind == RuleLineKind.EFFECT }.map { it.text },
        )
    }

    @Test
    fun onlyThreeBasisPhrasesReachPlayersAndScaledAmountsResolveWithTheirScale() {
        fun effect(action: EffectAction, context: RuleTextContext = RuleTextContext(), language: AppLanguage = AppLanguage.English) =
            item(clauses = immutableListOf(EffectClause(EffectTrigger.DASH, actions = immutableListOf(action))))
                .ruleLines(language, context).plain().first { it.kind == RuleLineKind.EFFECT }.text

        assertEquals("Strike the target for 45 % of impact damage", effect(EffectAction.Strike(DamageBasis.IMPACT, 0.45f)))
        assertEquals("Strike the target for 45 % of impact damage", effect(EffectAction.Strike(DamageBasis.BRAKE, 0.45f)))
        assertEquals("Strike the target for 60 % of the hit", effect(EffectAction.Strike(DamageBasis.HIT, 0.6f)))
        // Maximum-integrity terms ignore stack multipliers.
        assertEquals(
            "Strike the target for 35 % of its maximum integrity",
            effect(EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.35f), RuleTextContext(stackMagnitude = 2.2f)),
        )
        assertEquals("Vent 100 heat", effect(EffectAction.Vent(100f), RuleTextContext(stackMagnitude = 1.7f)))
        assertEquals("Снижает нагрев на 25,5", effect(EffectAction.Vent(15f), RuleTextContext(stackMagnitude = 1.7f), AppLanguage.Russian))
        assertEquals("Burst nearby for damage that grows over the run", effect(EffectAction.Burst(100f, DamageBasis.FLAT, 40f)))
        assertEquals(
            "Burst nearby for 120 damage",
            effect(EffectAction.Burst(100f, DamageBasis.FLAT, 40f), RuleTextContext(stackMagnitude = 1.5f, flatScale = 2f)),
        )
        assertEquals("Chain to 5 enemies for damage based on weapon power", effect(EffectAction.Chain(5, 300f, DamageBasis.WEAPON, 1.5f)))
        assertEquals("Chain to 5 enemies for 78 damage", effect(EffectAction.Chain(5, 300f, DamageBasis.WEAPON, 1.5f), RuleTextContext(weaponUnit = 52f)))
        assertEquals("Burst nearby for damage based on shield capacity", effect(EffectAction.Burst(100f, DamageBasis.SHIELD, 1f)))
        assertEquals("Наносит врагам рядом 60 урона", effect(EffectAction.Burst(100f, DamageBasis.SHIELD, 1f), RuleTextContext(shieldCapacity = 60f), AppLanguage.Russian))
        assertEquals("Перескакивает на другого врага, нанося 60 % урона попадания", effect(EffectAction.Chain(1, 260f, DamageBasis.HIT, 0.6f), language = AppLanguage.Russian))
        assertEquals(
            "Release 4 shards of Cryo that each deal 30 % of the hit",
            effect(EffectAction.Shards(4, DamageBasis.HIT, 0.3f, ElementPick.CRYO)),
        )
        assertEquals(
            "Выпускает 5 осколков, каждый наносит 30 % урона попадания",
            effect(EffectAction.Shards(5, DamageBasis.HIT, 0.3f), language = AppLanguage.Russian),
        )
        assertEquals(
            "Gain +30 % impact damage for 2.5 s, stacking up to 3 times",
            effect(EffectAction.Buff(BuffStat.IMPACT, 0.3f, 2.5f, maxStacks = 3)),
        )
        assertEquals(
            "Мгновенно подрывает слои Термо и Энтропии на цели, нанося 150 % оставшегося урона",
            effect(EffectAction.Detonate(immutableListOf(StatusElement.THERMAL, StatusElement.ENTROPY), 1.5f), language = AppLanguage.Russian),
        )
    }

    @Test
    fun rulesNameTheirSystemAndLiabilitiesBecomeCosts() {
        val thermal = item(
            elements = immutableListOf(StatusElement.THERMAL),
            rules = immutableListOf(
                ItemRule.BreakThreshold(StatusElement.THERMAL, 2),
                ItemRule.BreakPower(StatusElement.THERMAL, 0.4f),
                ItemRule.BreakSpread(StatusElement.THERMAL, 3, 140f),
            ),
        )
        assertEquals(
            listOf("Thermal needs 2 fewer stacks to break", "+40 % Ignite power", "Ignite spreads 3 Thermal to nearby enemies"),
            thermal.ruleLines(AppLanguage.English).plain().map { it.text },
        )
        assertEquals(
            listOf("Пробою Термо нужно на 2 слоя меньше", "+40 % к силе Воспламенения", "Воспламенение распространяет 3 слоя Термо на врагов рядом"),
            thermal.ruleLines(AppLanguage.Russian).plain().map { it.text },
        )

        val governor = item(
            rules = immutableListOf(
                ItemRule.WhileModifier(EffectCondition.SpeedBelow(500f), BuffStat.DAMAGE_TAKEN, 0.3f),
                ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.4f),
                ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_400f), BuffStat.DAMAGE, 0.35f),
            ),
            risk = true,
        )
        assertEquals(
            listOf(
                RuleLine(RuleLineKind.RULE, "−40 % damage taken while braking"),
                RuleLine(RuleLineKind.RULE, "+35 % damage above 1,400 u/s"),
                RuleLine(RuleLineKind.COST, "+30 % damage taken below 500 u/s", negative = true),
            ),
            governor.ruleLines(AppLanguage.English).plain(),
        )

        val exchange = item(
            elements = immutableListOf(StatusElement.ENTROPY),
            rules = immutableListOf(ItemRule.Convert(ConversionKind.REGENERATION_TO_DECAY, 2f), ItemRule.Convert(ConversionKind.CRIT_CHANCE_TO_DAMAGE, 2.5f)),
            risk = true,
        )
        assertEquals(
            listOf(
                RuleLine(RuleLineKind.RULE, "Every second, each point of regeneration applies 2 Entropy nearby", StatusElement.ENTROPY),
                RuleLine(RuleLineKind.RULE, "Each 1 % of critical chance above the cap becomes 2.5 % critical damage"),
                RuleLine(RuleLineKind.COST, "Regeneration no longer heals", negative = true),
                RuleLine(RuleLineKind.COST, "Critical chance is capped at 30 %", negative = true),
            ),
            exchange.ruleLines(AppLanguage.English).plain(),
        )
        assertEquals(
            "Каждую секунду каждая единица восстановления накладывает 2,5 слоя Энтропии рядом",
            exchange.ruleLines(AppLanguage.Russian, RuleTextContext(stackMagnitude = 1.25f)).first().text,
        )
    }

    @Test
    fun copiesChangeThresholdCutsAndOverfillAsGameplayDoesWhileTheComboCapFollowsTheBonusOnly() {
        val layered = item(
            elements = immutableListOf(StatusElement.THERMAL),
            rules = immutableListOf(
                ItemRule.BreakThreshold(StatusElement.THERMAL, 2),
                ItemRule.ComboDamage(0.02f, 0.3f),
                ItemRule.Convert(ConversionKind.OVERHEAL_TO_SHIELD, 1.5f),
            ),
        )
        fun lines(stacks: Int, bonus: Float = 1f) = layered.ruleLines(
            AppLanguage.English,
            RuleTextContext(stackMagnitude = BuildPolicy().stackMagnitude(stacks), bonus = bonus, stacks = stacks),
        ).plain().map { it.text }

        val one = lines(1)
        val two = lines(2)
        assertTrue("Thermal needs 2 fewer stacks to break" in one, one.toString())
        assertTrue("Thermal needs 3 fewer stacks to break" in two, two.toString())
        assertTrue(two.any { it.startsWith("+3.4 % damage per combo kill, up to 30 %") }, two.toString())
        assertTrue(lines(2, bonus = 1.5f).any { it.endsWith("up to 45 %") }, lines(2, bonus = 1.5f).toString())
        assertTrue(one.any { "150 %" in it } && two.any { "185 %" in it }, two.toString())
        assertEquals(3, breakThresholdCut(2, 3))
        assertEquals(1.5f + 0.7f, overhealOverfill(1.5f, 3), 0.0001f)
        assertFailsWith<IllegalArgumentException> { RuleTextContext(stacks = 0) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(stacks = ContentBounds.MAX_ITEM_STACKS + 1) }
    }

    @Test
    fun relicUpsidesFillTheirValuesAtRankAndKeepUnknownPlaceholders() {
        val relic = RelicDefinition(
            id = RelicId.VERDANT_ROT,
            name = "Verdant Rot",
            aspect = RelicAspect.ENTROPY,
            keys = immutableListOf(RelicKey.DECAY),
            attunedTags = immutableListOf(),
            upside = "Every Entropy stack within 320 u heals you {0} per second, up to 3.5.",
            liability = "Enemies carrying a status move 20 % faster.",
            values = immutableListOf(RelicValue(0.25f, 0.05f)),
        )
        assertEquals("Every Entropy stack within 320 u heals you 0.25 per second, up to 3.5.", relic.upsideText(AppLanguage.English, 1))
        assertEquals("Every Entropy stack within 320 u heals you 0.35 per second, up to 3.5.", relic.upsideText(AppLanguage.English, 3))
        assertEquals("Враги под действием стихии двигаются на 20 % быстрее.", relic.liabilityText(AppLanguage.Russian))

        val wide = relic.copy(upside = "Blink up to {0} u, then {3}.", values = immutableListOf(RelicValue(1_200f, 100f)))
        assertEquals("Blink up to 1,400 u, then {3}.", wide.upsideText(AppLanguage.English, 3))
        assertEquals("Blink up to 1\u00A0200 u, then {3}.", wide.upsideText(AppLanguage.Russian, 1))

        val falling = relic.copy(upside = "Cooling {0} %.", values = immutableListOf(RelicValue(-10f, -5f)))
        assertEquals("Cooling −20 %.", falling.upsideText(AppLanguage.English, 3))
    }

    @Test
    fun contextRejectsNonFiniteOrNonPositiveScales() {
        assertFailsWith<IllegalArgumentException> { RuleTextContext(stackMagnitude = 0f) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(stackMagnitude = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(flatScale = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(shieldCapacity = -1f) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(bonus = 0f) }
        assertFailsWith<IllegalArgumentException> { RuleTextContext(bonus = Float.NaN) }
        RuleTextContext(stackMagnitude = 2.2f, flatScale = 0f, weaponUnit = 40f, shieldCapacity = 0f)
    }

    @Test
    fun theSkillApexFieldAndMarkVocabularyReadsInBothLanguages() {
        fun lines(language: AppLanguage, context: RuleTextContext = RuleTextContext(), vararg parts: Any): List<String> = item(
            clauses = parts.filterIsInstance<EffectClause>().toImmutableList(),
            rules = parts.filterIsInstance<ItemRule>().toImmutableList(),
            risk = parts.any { it is ItemRule && it.isLiability },
        ).ruleLines(language, context).plain().map { it.text }
        fun english(vararg parts: Any) = lines(AppLanguage.English, RuleTextContext(), *parts)
        fun russian(vararg parts: Any) = lines(AppLanguage.Russian, RuleTextContext(), *parts)
        fun on(trigger: EffectTrigger, vararg actions: EffectAction, given: List<EffectCondition> = emptyList(), cooldown: Float = 0f, every: Int = 1) =
            EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown, every)

        assertEquals(
            listOf("On skill cast if the skill is Drive", "Apply 2 Ion nearby"),
            english(on(EffectTrigger.SKILL_CAST, EffectAction.ApplyStatus(ElementPick.ION, 2, 160f), given = listOf(EffectCondition.SkillTagged(BuildTag.DRIVE)))),
        )
        assertEquals(
            listOf("При применении навыка если навык — Порыв", "Накладывает 2 слоя Иона рядом"),
            russian(on(EffectTrigger.SKILL_CAST, EffectAction.ApplyStatus(ElementPick.ION, 2, 160f), given = listOf(EffectCondition.SkillTagged(BuildTag.DRIVE)))),
        )
        val oblivion = on(EffectTrigger.REACTION, EffectAction.ChargeApex(2f), given = listOf(EffectCondition.ReactionIs(ReactionId.OBLIVION)), cooldown = 1f)
        assertEquals(
            listOf("On reaction if it is Oblivion, once every 1 s", "Recharge the Apex by 2 s (skills by a quarter before the maximum level)"),
            english(oblivion),
        )
        assertEquals(
            listOf(
                "При реакции если это ${"Oblivion".localizedContent(AppLanguage.Russian)}, не чаще раза в 1 с",
                "Ускоряет Апекс на 2 с (до максимального уровня — навыки на четверть)",
            ),
            russian(oblivion),
        )
        assertEquals(
            listOf("Every 3rd skill hit if it is Meteor", "Recharge this skill by 0.5 s"),
            english(on(EffectTrigger.SKILL_HIT, EffectAction.RefundSkills(0.5f, SkillPick.CAST), given = listOf(EffectCondition.SkillIs(SkillId.METEOR)), every = 3)),
        )
        assertEquals(
            listOf("Каждое 3-е попадание навыком", "Ускоряет перезарядку самого долгого навыка на 0,5 с"),
            russian(on(EffectTrigger.SKILL_HIT, EffectAction.RefundSkills(0.5f, SkillPick.LONGEST), every = 3)),
        )
        assertEquals(
            listOf("On kill of a marked enemy, once every 1 s", "Set off reactions on up to 3 enemies"),
            english(on(EffectTrigger.KILL, EffectAction.ForceReaction(160f, 3), given = listOf(EffectCondition.TargetMarked), cooldown = 1f)),
        )
        assertEquals(
            listOf("On impact with a held enemy, once every 1 s", "Set off a reaction on the target and throw the target"),
            english(on(EffectTrigger.IMPACT, EffectAction.ForceReaction(0f, 1), EffectAction.Throw(500f), given = listOf(EffectCondition.TargetControlled), cooldown = 1f)),
        )
        assertEquals(
            listOf("On cashing a mark against a thrown enemy, once every 1 s", "Launch you forward at 350 u/s and make your next dash free"),
            english(on(EffectTrigger.MARK_CONSUMED, EffectAction.CoreImpulse(350f, ImpulseDirection.FORWARD), EffectAction.PrimeDash,
                given = listOf(EffectCondition.TargetThrown), cooldown = 1f)),
        )
        assertEquals(
            listOf("При срабатывании метки по отброшенному врагу, не чаще раза в 1 с", "Бросает вас вперёд со скоростью 350 ед/с и делает следующий рывок бесплатным"),
            russian(on(EffectTrigger.MARK_CONSUMED, EffectAction.CoreImpulse(350f, ImpulseDirection.FORWARD), EffectAction.PrimeDash,
                given = listOf(EffectCondition.TargetThrown), cooldown = 1f)),
        )
        assertEquals(
            listOf("On entering a field, once every 2 s", "Open a time bubble for 2 s"),
            english(on(EffectTrigger.FIELD_ENTER, EffectAction.Zone(ZoneKind.TIME_BUBBLE, 150f, 2f), cooldown = 2f)),
        )
        assertEquals(
            listOf("При входе в поле, не чаще раза в 2 с", "Открывает пузырь времени на 2 с"),
            russian(on(EffectTrigger.FIELD_ENTER, EffectAction.Zone(ZoneKind.TIME_BUBBLE, 150f, 2f), cooldown = 2f)),
        )
        assertEquals(
            listOf("Every 2nd erased shot", "Erase up to 4 hostile shots within 200 and erase a hostile shot within 80"),
            english(on(EffectTrigger.SHOT_ERASED, EffectAction.EraseShots(200f, 4), EffectAction.EraseShots(80f, 1), cooldown = 1f, every = 2))
                .map { it.replace(", once every 1 s", "") },
        )
        assertEquals(
            listOf("On Apex", "Mark the target: Cracked for 3 s"),
            english(on(EffectTrigger.SKILL_HIT, EffectAction.ApplyMark(MarkPick.CRACKED, 3f), cooldown = 1f)).let { listOf("On Apex", it[1]) },
        )
        assertEquals("Каждый 2-й Апекс", russian(on(EffectTrigger.APEX, EffectAction.Vent(5f), every = 2)).first())
        assertEquals(
            listOf("+20 % damage while a skill recharges", "+10 % damage while 2 skills recharge", "+15 % weapon power with Pulse weapons"),
            english(
                ItemRule.WhileModifier(EffectCondition.SkillsCharging(1), BuffStat.DAMAGE, 0.2f),
                ItemRule.WhileModifier(EffectCondition.SkillsCharging(2), BuffStat.DAMAGE, 0.1f),
                ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE), BuffStat.WEAPON, 0.15f),
            ),
        )
        assertEquals(
            listOf("+15 % к мощности оружия с оружием типа «Пульс»", "+20 % к урону пока вы внутри поля", "+10 % к силе навыков во время Апекса"),
            russian(
                ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE), BuffStat.WEAPON, 0.15f),
                ItemRule.WhileModifier(EffectCondition.CoreInField, BuffStat.DAMAGE, 0.2f),
                ItemRule.WhileModifier(EffectCondition.ApexActive, BuffStat.SKILL_POWER, 0.1f),
            ),
        )
        assertEquals(
            listOf(
                "Keystone — Fusion becomes Dwarf Star", "Fusion: +30 % radius", "Reactions: +25 % duration",
            ),
            english(
                ItemRule.ReactionKeystone(ReactionId.FUSION),
                ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.3f),
                ItemRule.ReactionTuning(null, ReactionAspect.DURATION, 0.25f),
            ),
        )
        val fusion = "Fusion".localizedContent(AppLanguage.Russian)
        assertEquals(
            listOf("Ключ — $fusion становится «${"Dwarf Star".localizedContent(AppLanguage.Russian)}»", "$fusion: +30 % к радиусу", "Реакции: +25 % к силе"),
            russian(
                ItemRule.ReactionKeystone(ReactionId.FUSION),
                ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.3f),
                ItemRule.ReactionTuning(null, ReactionAspect.FORCE, 0.25f),
            ),
        )
        assertEquals(
            listOf("Your fields reach 20 % farther", "Ion skills recharge 10 % faster", "Skills deal +20 % damage"),
            english(ItemRule.FieldTuning(FieldAspect.RADIUS, 0.2f), ItemRule.SkillCooldown(StatusElement.ION, 0.1f), ItemRule.SkillPower(null, 0.2f)),
        )
        assertEquals(
            listOf("Ваши поля держатся на 20 % дольше", "Навыки (Ион) перезаряжаются на 10 % быстрее", "Навыки (Пустота) наносят +20 % урона"),
            russian(ItemRule.FieldTuning(FieldAspect.DURATION, 0.2f), ItemRule.SkillCooldown(StatusElement.ION, 0.1f),
                ItemRule.SkillPower(StatusElement.VOID, 0.2f)),
        )
        assertEquals(
            listOf("Skills hold a second charge (one such item per run)", "Your skills take your dominant element", "Reactions recharge skills 50 % more"),
            english(ItemRule.SkillCharges, ItemRule.SkillAttunement(ElementPick.DOMINANT), ItemRule.CatalysisRate(0.5f)),
        )
        assertEquals(
            listOf("Your skills become Void", "The Apex recharges 10 % faster", "Reactions empower the Core from 120 farther"),
            lines(AppLanguage.English, RuleTextContext(dominant = StatusElement.VOID),
                ItemRule.SkillAttunement(ElementPick.DOMINANT), ItemRule.ApexCooldown(0.1f), ItemRule.CoreLinkRadius(120f)),
        )
        assertEquals(
            listOf("Ваши навыки обретают стихию: Пустота", "Апекс перезаряжается на 10 % быстрее", "Реакции усиливают Ядро на 120 дальше"),
            lines(AppLanguage.Russian, RuleTextContext(dominant = StatusElement.VOID),
                ItemRule.SkillAttunement(ElementPick.DOMINANT), ItemRule.ApexCooldown(0.1f), ItemRule.CoreLinkRadius(120f)),
        )
        assertEquals(
            listOf(
                "Every 10 dash heat recharges skills by 0.4 s", "Each Data pickup recharges skills by 0.15 s", "Skills recharge 40 % faster",
                "You cool 20 % slower", "Data pickups give 20 % less", "Each skill cast costs 25 heat",
            ),
            english(
                ItemRule.Convert(ConversionKind.DASH_HEAT_TO_SKILLS, 0.4f), ItemRule.Convert(ConversionKind.DATA_TO_SKILLS, 0.15f),
                ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 25f),
            ),
        )
        assertEquals(
            listOf("Your skill fields travel with you at 80 % of their radius", "Enemies you cannot move take the throw as damage"),
            english(ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 0.8f), ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE)),
        )
        assertEquals(
            listOf("Поля навыков следуют за вами, сохраняя 80 % радиуса", "Враги, которых нельзя сдвинуть, получают урон от броска"),
            russian(ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 0.8f), ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE)),
        )
        assertEquals(
            listOf("On skill cast", "Gain +20 % skill power for 4 s"),
            english(on(EffectTrigger.SKILL_CAST, EffectAction.Buff(BuffStat.SKILL_POWER, 0.2f, 4f))),
        )
        assertEquals(
            "Даёт +20 % к скорости перезарядки навыков на 4 с",
            russian(on(EffectTrigger.SKILL_CAST, EffectAction.Buff(BuffStat.SKILL_RECHARGE, 0.2f, 4f)))[1],
        )
    }

    private fun item(
        elements: kinetickk.foundation.collections.ImmutableList<StatusElement> = immutableListOf(),
        stats: kinetickk.foundation.collections.ImmutableList<StatDelta> = immutableListOf(),
        clauses: kinetickk.foundation.collections.ImmutableList<EffectClause> = immutableListOf(),
        rules: kinetickk.foundation.collections.ImmutableList<ItemRule> = immutableListOf(),
        risk: Boolean = false,
    ) = ItemDefinition(
        id = 0, name = "Sample", description = "Exercises the rules text.", rarity = ItemRarity.EPIC,
        tags = immutableListOf(BuildTag.RAM), elements = elements, stats = stats, clauses = clauses, rules = rules,
        maxStacks = 1, unlockLevel = 1, risk = risk,
    )

    /** No-break spaces keep numbers with their units; compare them as plain spaces. */
    private fun List<RuleLine>.plain(): List<RuleLine> = map { it.copy(text = it.text.replace('\u00A0', ' ')) }

    private fun placeholders(text: String): Set<String> = Regex("\\{[0-9]+\\}").findAll(text).map { it.value }.toSet()
}
