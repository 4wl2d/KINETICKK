// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The 0.5.0 rule vocabulary (skills, Apex, fields, shots, marks, reaction keystones and tuning): every bounded value
 * accepts its bounds and rejects the first value past them, conditions hold only on triggers that carry their facts,
 * marks and throws need a target, the frequent triggers rate-limit the new actions, and the exclusive rules stay
 * exclusive.
 */
class RuleVocabularyTest {
    @Test
    fun newActionsAcceptTheirBoundsAndRejectTheFirstValueOutside() {
        EffectAction.RefundSkills(0.1f)
        EffectAction.RefundSkills(3f, SkillPick.LONGEST)
        assertFailsWith<IllegalArgumentException> { EffectAction.RefundSkills(0.09f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.RefundSkills(3.01f) }

        EffectAction.ChargeApex(0.5f)
        EffectAction.ChargeApex(5f)
        assertFailsWith<IllegalArgumentException> { EffectAction.ChargeApex(0.49f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.ChargeApex(5.01f) }

        EffectAction.ForceReaction(0f, 1)
        EffectAction.ForceReaction(300f, 4)
        assertFailsWith<IllegalArgumentException> { EffectAction.ForceReaction(300.5f, 1) }
        assertFailsWith<IllegalArgumentException> { EffectAction.ForceReaction(100f, 0) }
        assertFailsWith<IllegalArgumentException> { EffectAction.ForceReaction(100f, 5) }

        EffectAction.Zone(ZoneKind.MAW, 60f, 0.5f)
        EffectAction.Zone(ZoneKind.TIME_BUBBLE, 300f, 4f)
        assertFailsWith<IllegalArgumentException> { EffectAction.Zone(ZoneKind.HUSH, 59f, 1f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.Zone(ZoneKind.HUSH, 301f, 1f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.Zone(ZoneKind.PLASMA, 100f, 0.49f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.Zone(ZoneKind.PLASMA, 100f, 4.01f) }

        EffectAction.EraseShots(40f, 1)
        EffectAction.EraseShots(400f, 12)
        assertFailsWith<IllegalArgumentException> { EffectAction.EraseShots(39f, 1) }
        assertFailsWith<IllegalArgumentException> { EffectAction.EraseShots(401f, 1) }
        assertFailsWith<IllegalArgumentException> { EffectAction.EraseShots(100f, 0) }
        assertFailsWith<IllegalArgumentException> { EffectAction.EraseShots(100f, 13) }

        EffectAction.CoreImpulse(100f, ImpulseDirection.POINTER)
        EffectAction.CoreImpulse(600f, ImpulseDirection.FORWARD)
        assertFailsWith<IllegalArgumentException> { EffectAction.CoreImpulse(99f, ImpulseDirection.FORWARD) }
        assertFailsWith<IllegalArgumentException> { EffectAction.CoreImpulse(601f, ImpulseDirection.FORWARD) }

        EffectAction.ApplyMark(MarkPick.SPALL, 1f)
        EffectAction.ApplyMark(MarkPick.EMBRITTLED, 6f)
        assertFailsWith<IllegalArgumentException> { EffectAction.ApplyMark(MarkPick.CRACKED, 0.99f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.ApplyMark(MarkPick.CRACKED, 6.01f) }

        EffectAction.Throw(200f)
        EffectAction.Throw(900f)
        assertFailsWith<IllegalArgumentException> { EffectAction.Throw(199f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.Throw(901f) }
        assertFailsWith<IllegalArgumentException> { EffectAction.Throw(Float.NaN) }
    }

    @Test
    fun newRulesAcceptTheirBoundsAndRejectTheFirstValueOutside() {
        fun bounded(minimum: Float, maximum: Float, make: (Float) -> ItemRule) {
            make(minimum)
            make(maximum)
            assertFailsWith<IllegalArgumentException> { make(minimum - 0.001f) }
            assertFailsWith<IllegalArgumentException> { make(maximum + 0.001f) }
            assertFailsWith<IllegalArgumentException> { make(Float.POSITIVE_INFINITY) }
        }
        bounded(0.1f, 0.5f) { ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, it) }
        bounded(0.1f, 0.5f) { ItemRule.ReactionTuning(null, ReactionAspect.FORCE, it) }
        bounded(0.1f, 0.5f) { ItemRule.FieldTuning(FieldAspect.DURATION, it) }
        bounded(0.05f, 0.25f) { ItemRule.SkillCooldown(StatusElement.ION, it) }
        bounded(0.1f, 0.5f) { ItemRule.SkillPower(null, it) }
        bounded(0.25f, 1f) { ItemRule.CatalysisRate(it) }
        bounded(0.05f, 0.2f) { ItemRule.ApexCooldown(it) }
        bounded(40f, 240f) { ItemRule.CoreLinkRadius(it) }
        EffectCondition.SkillsCharging(1)
        EffectCondition.SkillsCharging(ContentBounds.SKILL_SLOTS)
        assertFailsWith<IllegalArgumentException> { EffectCondition.SkillsCharging(0) }
        assertFailsWith<IllegalArgumentException> { EffectCondition.SkillsCharging(ContentBounds.SKILL_SLOTS + 1) }
        ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 1f)
        assertFailsWith<IllegalArgumentException> { ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 1.01f) }
    }

    @Test
    fun theDataOfferWeightAcceptsAPositiveFractionUpToTwo() {
        MatrixEffect.DataOfferWeight(0.001f)
        MatrixEffect.DataOfferWeight(2f)
        assertFailsWith<IllegalArgumentException> { MatrixEffect.DataOfferWeight(0f) }
        assertFailsWith<IllegalArgumentException> { MatrixEffect.DataOfferWeight(-0.5f) }
        assertFailsWith<IllegalArgumentException> { MatrixEffect.DataOfferWeight(2.001f) }
        assertFailsWith<IllegalArgumentException> { MatrixEffect.DataOfferWeight(Float.NaN) }
    }

    @Test
    fun conditionsHoldOnlyOnTriggersThatCarryTheirFacts() {
        val reactionCarriers = setOf(EffectTrigger.REACTION, EffectTrigger.FIELD_ENTER, EffectTrigger.MARK_CONSUMED)
        val skillCarriers = setOf(EffectTrigger.SKILL_CAST, EffectTrigger.SKILL_HIT)
        val targeted = setOf(
            EffectTrigger.IMPACT, EffectTrigger.WEAPON_HIT, EffectTrigger.CRIT, EffectTrigger.DASH_HIT, EffectTrigger.REACTION,
            EffectTrigger.BREAK, EffectTrigger.SLAM, EffectTrigger.SKILL_HIT, EffectTrigger.MARK_CONSUMED,
        )
        assertEquals(targeted, TargetedTriggers)
        val targetConditions = listOf(
            EffectCondition.TargetHas(null), EffectCondition.TargetElite, EffectCondition.TargetFrozen, EffectCondition.TargetBelow(0.3f),
            EffectCondition.TargetMarked, EffectCondition.TargetControlled, EffectCondition.TargetThrown,
        )
        val anywhere = listOf(
            EffectCondition.CoreInField, EffectCondition.ApexActive, EffectCondition.SkillsCharging(2),
            EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE), EffectCondition.SpeedAtLeast(500f),
        )
        for (trigger in EffectTrigger.entries) {
            assertEquals(trigger in reactionCarriers, EffectCondition.ReactionIs(ReactionId.OBLIVION).validOn(trigger), "ReactionIs on $trigger")
            assertEquals(trigger in skillCarriers, EffectCondition.SkillIs(SkillId.METEOR).validOn(trigger), "SkillIs on $trigger")
            assertEquals(trigger in skillCarriers, EffectCondition.SkillTagged(BuildTag.DRIVE).validOn(trigger), "SkillTagged on $trigger")
            targetConditions.forEach { condition ->
                assertEquals(trigger in targeted || trigger == EffectTrigger.KILL, condition.validOn(trigger), "$condition on $trigger")
            }
            anywhere.forEach { condition -> assertTrue(condition.validOn(trigger), "$condition on $trigger") }
        }

        val burst = EffectAction.Burst(80f, DamageBasis.FLAT, 1f)
        clause(EffectTrigger.FIELD_ENTER, burst, conditions = listOf(EffectCondition.ReactionIs(ReactionId.ROT_WELL)))
        assertFailsWith<IllegalArgumentException> {
            clause(EffectTrigger.KILL, burst, conditions = listOf(EffectCondition.ReactionIs(ReactionId.ROT_WELL)))
        }
        clause(EffectTrigger.SKILL_HIT, EffectAction.Strike(DamageBasis.HIT, 0.2f), conditions = listOf(EffectCondition.SkillIs(SkillId.METEOR)))
        assertFailsWith<IllegalArgumentException> {
            clause(EffectTrigger.WEAPON_HIT, EffectAction.Strike(DamageBasis.HIT, 0.2f), conditions = listOf(EffectCondition.SkillTagged(BuildTag.RAM)))
        }
        clause(EffectTrigger.KILL, burst, conditions = listOf(EffectCondition.TargetMarked))
        assertFailsWith<IllegalArgumentException> {
            clause(EffectTrigger.DASH, burst, conditions = listOf(EffectCondition.TargetThrown))
        }
        // Rules hold outside any dispatch, so a dispatch fact can never gate one.
        ItemRule.WhileModifier(EffectCondition.ApexActive, BuffStat.DAMAGE, 0.2f)
        assertFailsWith<IllegalArgumentException> { ItemRule.WhileModifier(EffectCondition.ReactionIs(ReactionId.FUSION), BuffStat.DAMAGE, 0.2f) }
        assertFailsWith<IllegalArgumentException> { ItemRule.DamageVersus(EffectCondition.SkillIs(SkillId.METEOR), 0.2f) }
        assertFailsWith<IllegalArgumentException> { ItemRule.AlwaysCrit(EffectCondition.SkillTagged(BuildTag.RAM)) }
        assertFailsWith<IllegalArgumentException> { ItemRule.CritDamageVersus(EffectCondition.ReactionIs(ReactionId.FUSION), 0.2f) }
    }

    @Test
    fun marksAndThrowsNeedATargetedTrigger() {
        for (trigger in EffectTrigger.entries) {
            val mark = runCatching { clause(trigger, EffectAction.ApplyMark(MarkPick.CRACKED, 2f), cooldown = 1f) }
            val toss = runCatching { clause(trigger, EffectAction.Throw(400f), cooldown = 1f) }
            assertEquals(trigger in TargetedTriggers, mark.isSuccess, "ApplyMark on $trigger")
            assertEquals(trigger in TargetedTriggers, toss.isSuccess, "Throw on $trigger")
        }
    }

    @Test
    fun frequentTriggersRateLimitTheNewActions() {
        assertEquals(
            setOf(EffectTrigger.SKILL_HIT, EffectTrigger.FIELD_ENTER, EffectTrigger.SHOT_ERASED, EffectTrigger.MARK_CONSUMED),
            EffectClause.FrequentTriggers - setOf(
                EffectTrigger.IMPACT, EffectTrigger.WEAPON_HIT, EffectTrigger.CRIT, EffectTrigger.KILL, EffectTrigger.DASH_HIT,
                EffectTrigger.BREAK, EffectTrigger.REACTION, EffectTrigger.PICKUP, EffectTrigger.HURT, EffectTrigger.RICOCHET,
                EffectTrigger.SLAM,
            ),
        )
        assertFalse(EffectTrigger.SKILL_CAST in EffectClause.FrequentTriggers)
        assertFalse(EffectTrigger.APEX in EffectClause.FrequentTriggers)

        // Forced reactions: a 1 s cooldown or every third.
        clause(EffectTrigger.SKILL_HIT, EffectAction.ForceReaction(160f, 2), cooldown = 1f)
        clause(EffectTrigger.SKILL_HIT, EffectAction.ForceReaction(160f, 2), every = 3)
        assertFailsWith<IllegalArgumentException> { clause(EffectTrigger.SKILL_HIT, EffectAction.ForceReaction(160f, 2), cooldown = 0.99f, every = 2) }
        clause(EffectTrigger.SKILL_CAST, EffectAction.ForceReaction(160f, 2))
        // Zones: a 2 s cooldown or every fourth.
        clause(EffectTrigger.FIELD_ENTER, EffectAction.Zone(ZoneKind.PLASMA, 120f, 2f), cooldown = 2f)
        clause(EffectTrigger.FIELD_ENTER, EffectAction.Zone(ZoneKind.PLASMA, 120f, 2f), every = 4)
        assertFailsWith<IllegalArgumentException> { clause(EffectTrigger.KILL, EffectAction.Zone(ZoneKind.HUSH, 120f, 2f), cooldown = 1.99f, every = 3) }
        clause(EffectTrigger.DASH, EffectAction.Zone(ZoneKind.HUSH, 120f, 2f))
        // Erasing shots: a 1 s cooldown, whatever the every-Nth gate.
        clause(EffectTrigger.HURT, EffectAction.EraseShots(200f, 4), cooldown = 1f)
        assertFailsWith<IllegalArgumentException> { clause(EffectTrigger.HURT, EffectAction.EraseShots(200f, 4), cooldown = 0.99f, every = 8) }
        // Refunds, Apex charge, launches and free Dashes are sustain: a 0.5 s cooldown or an every-Nth gate.
        listOf(EffectAction.RefundSkills(0.5f), EffectAction.ChargeApex(1f), EffectAction.CoreImpulse(300f, ImpulseDirection.FORWARD),
            EffectAction.PrimeDash).forEach { action ->
            assertTrue(action.isSustain, "$action is sustain")
            clause(EffectTrigger.MARK_CONSUMED, action, cooldown = 0.5f)
            clause(EffectTrigger.MARK_CONSUMED, action, every = 2)
            assertFailsWith<IllegalArgumentException> { clause(EffectTrigger.MARK_CONSUMED, action, cooldown = 0.49f) }
        }
        // The Apex comes at most every 30 s: its clauses count at most every third activation.
        clause(EffectTrigger.APEX, EffectAction.Vent(5f), every = 3)
        assertFailsWith<IllegalArgumentException> { clause(EffectTrigger.APEX, EffectAction.Vent(5f), every = 4) }
    }

    @Test
    fun keystonesAreOnePerItemOnEpicOrBetterItems() {
        val keystone = ItemRule.ReactionKeystone(ReactionId.FUSION)
        assertEquals(ReactionId.FUSION, item(ItemRarity.EPIC, rules = listOf(keystone)).keystone)
        assertEquals(ReactionId.FUSION, item(ItemRarity.LEGENDARY, rules = listOf(keystone)).keystone)
        assertNull(item(ItemRarity.EPIC, rules = listOf(ItemRule.ReactionPower(0.1f))).keystone)
        assertFailsWith<IllegalArgumentException> { item(ItemRarity.RARE, rules = listOf(keystone)) }
        assertFailsWith<IllegalArgumentException> {
            item(ItemRarity.EPIC, rules = listOf(keystone, ItemRule.ReactionKeystone(ReactionId.WILDFIRE)))
        }
    }

    @Test
    fun liabilitiesAreAnExplicitSetOfConversions() {
        val liabilities = ConversionKind.entries.filter { ItemRule.Convert(it, 0.5f).isLiability }.toSet()
        assertEquals(
            setOf(
                ConversionKind.REGENERATION_TO_DECAY, ConversionKind.CRIT_CHANCE_TO_DAMAGE, ConversionKind.DASH_HEAT_TO_SKILLS,
                ConversionKind.DATA_TO_SKILLS, ConversionKind.SKILLS_COST_HEAT,
            ),
            liabilities,
        )
        // A risk item may declare a skill conversion as its cost, but not a following field or a knockback conversion.
        item(ItemRarity.EPIC, rules = listOf(ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 25f)), risk = true)
        assertFailsWith<IllegalArgumentException> {
            item(ItemRarity.EPIC, rules = listOf(ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE)), risk = true)
        }
        listOf(ItemRule.SkillCharges, ItemRule.ReactionKeystone(ReactionId.FUSION), ItemRule.CoreLinkRadius(100f)).forEach {
            assertFalse(it.isLiability, "$it is no liability")
        }
    }

    @Test
    fun theDataEconomyPredicateCountsDataGainDataGrantsAndSporedMarks() {
        assertTrue(item(stats = listOf(StatDelta(ItemEffect.DATA_GAIN, 0.1f))).isDataEconomy)
        assertFalse(item(stats = listOf(StatDelta(ItemEffect.DATA_GAIN, -0.1f), StatDelta(ItemEffect.LUCK, 0.2f))).isDataEconomy)
        assertTrue(item(clauses = listOf(clause(EffectTrigger.KILL, EffectAction.GainData(2f)))).isDataEconomy)
        assertTrue(item(clauses = listOf(clause(EffectTrigger.IMPACT, EffectAction.ApplyMark(MarkPick.SPORED, 3f), cooldown = 1f))).isDataEconomy)
        assertFalse(item(clauses = listOf(clause(EffectTrigger.IMPACT, EffectAction.ApplyMark(MarkPick.SPALL, 3f), cooldown = 1f))).isDataEconomy)
        // Skills paid in Data are not Data economy.
        assertFalse(item(rules = listOf(ItemRule.Convert(ConversionKind.DATA_TO_SKILLS, 0.15f)), risk = true).isDataEconomy)
    }

    private fun clause(
        trigger: EffectTrigger,
        action: EffectAction,
        conditions: List<EffectCondition> = emptyList(),
        cooldown: Float = 0f,
        every: Int = 1,
    ) = EffectClause(trigger, conditions.toImmutableList(), immutableListOf(action), cooldown, every)

    private fun item(
        rarity: ItemRarity = ItemRarity.COMMON,
        stats: List<StatDelta> = emptyList(),
        clauses: List<EffectClause> = emptyList(),
        rules: List<ItemRule> = emptyList(),
        risk: Boolean = false,
    ): ItemDefinition = ItemDefinition(
        id = 0, name = "Probe", description = "Holds one piece of vocabulary.", rarity = rarity,
        tags = immutableListOf(BuildTag.CHAIN), stats = stats.list(), clauses = clauses.list(), rules = rules.list(),
        maxStacks = 1, unlockLevel = 1, risk = risk,
    )

    private fun <T> List<T>.list(): ImmutableList<T> = toImmutableList()
}
