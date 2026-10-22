// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ImpulseDirection
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillPick
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.ZoneKind
import kinetickk.ball.content.api.isLiability
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldFlags
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 0.5.0 rule vocabulary at run time: the six new triggers dispatch where and as often as combat-final §6.1
 * says, the new conditions read the run, the new actions, rules, conversions and timed stats do what their text
 * says within their bounds, and the offers leave out what a run cannot use.
 */
class RuleVocabularyRuntimeTest {
    // -----------------------------------------------------------------------------------------
    // Triggers.

    @Test
    fun aSkillCastDispatchesOnceAndItsFirstFourHitsCarryTheSkillAndItsElement() {
        val casts = probe("Cast Counter", on(EffectTrigger.SKILL_CAST, EffectAction.GainData(1f)))
        val thermalHits = probe("Thermal Hit Counter", on(EffectTrigger.SKILL_HIT, EffectAction.GainData(10f),
            given = listOf(EffectCondition.ElementIs(StatusElement.THERMAL))))
        val otherSkill = probe("Hook Counter", on(EffectTrigger.SKILL_CAST, EffectAction.GainData(100f),
            given = listOf(EffectCondition.SkillIs(SkillId.ARC_HOOK))))
        val state = stateWith(casts, thermalHits, otherSkill)
        repeat(6) { index -> state.dummy(x = 60f + index * 15f, y = 0f) }
        state.acquireSkill(SkillId.SOLAR_FLARE)
        val before = state.dataCollected

        assertTrue(state.castSkill(0))

        // One cast (1) and four Thermal skill hits (4 × 10) of six enemies struck; the Arc Hook clause stays quiet.
        assertEquals(41L, state.dataCollected - before)
    }

    @Test
    fun theApexDispatchesWhenItActivates() {
        val state = stateWith(probe("Crown Counter", on(EffectTrigger.APEX, EffectAction.GainData(3f))))
        state.unlockApex()
        val before = state.dataCollected
        state.apexBufferTime = MutableGameState.SKILL_INPUT_BUFFER_SECONDS
        state.updateSkills(MutableGameState.FIXED_STEP)

        assertTrue(state.apexActive())
        assertEquals(3L, state.dataCollected - before)
    }

    @Test
    fun enteringAFieldDispatchesOncePerEntryAndAtMostEveryHalfSecond() {
        val state = stateWith(probe("Pool Counter", on(EffectTrigger.FIELD_ENTER, EffectAction.GainData(1f),
            given = listOf(EffectCondition.ReactionIs(ReactionId.PLASMA_BURST)))))
        state.addField(CombatFieldKind.PLASMA, FieldSource.REACTION, ReactionId.PLASMA_BURST.ordinal, 0f, 0f, radius = 80f,
            seconds = 10f, damagePerSecond = 0f)
        fun step(x: Float): Long {
            val before = state.dataCollected
            state.coreX = x
            state.elapsed += 0.1f
            state.updateCombatFields(0.1f)
            return state.dataCollected - before
        }
        assertEquals(1L, step(0f), "the Core stands in the pool it just entered")
        assertEquals(0L, step(10f), "staying inside is no new entry")
        assertEquals(0L, step(300f))
        assertEquals(0L, step(0f), "an entry within half a second of the last dispatch waits")
        assertEquals(0L, step(300f))
        assertEquals(0L, step(300f))
        assertEquals(1L, step(0f))
        // A field of another reaction counts as an entry but fails the condition.
        state.addField(CombatFieldKind.HUSH, FieldSource.REACTION, ReactionId.SILENCE.ordinal, 600f, 0f, radius = 80f,
            seconds = 10f, damagePerSecond = 0f)
        state.elapsed += 1f
        assertEquals(0L, step(600f))
    }

    @Test
    fun erasedShotsDispatchAtMostFourTimesPerStepAsCascades() {
        val counter = probe("Erasure Counter", on(EffectTrigger.SHOT_ERASED, EffectAction.GainData(1f)))
        val spark = probe("Erasure Spark", on(EffectTrigger.SHOT_ERASED, EffectAction.ApplyStatus(ElementPick.THERMAL, 1, 60f), cooldown = 1f),
            elements = listOf(StatusElement.THERMAL))
        val state = stateWith(counter, spark)
        val bystander = state.dummy(x = 200f, y = 0f)
        val bolts = (0 until 6).map { state.hostileBolt(200f + it, 0f) }
        val before = state.dataCollected

        bolts.forEach { assertTrue(state.eraseHostileShot(it, EraseCause.ITEM, it.x, it.y)) }

        assertEquals(6L, state.shotsErased)
        assertEquals(4L, state.dataCollected - before)
        assertEquals(1, bystander.stacksOf(StatusElement.THERMAL))
        assertEquals(1, bystander.generationOf(StatusElement.THERMAL), "an erasure's effects are cascades")
        // A new fixed step starts a new count.
        state.shotErasedDispatches = 0
        state.eraseHostileShot(state.hostileBolt(0f, 300f), EraseCause.REACTION, 0f, 300f)
        assertEquals(5L, state.dataCollected - before)
    }

    @Test
    fun cashedMarksDispatchWithTheReactionThatLeftThemAtMostFourTimesPerStep() {
        val state = stateWith(probe("Breakpoint Ledger", on(EffectTrigger.MARK_CONSUMED, EffectAction.GainData(1f),
            given = listOf(EffectCondition.ReactionIs(ReactionId.FRACTURE)))))
        val cracked = (0 until 6).map { state.dummy(x = 40f + it * 10f, y = 0f).also { enemy -> state.setMark(enemy, MarkKind.CRACKED, 3f) } }
        val spalled = state.dummy(x = -60f, y = 0f).also { state.setMark(it, MarkKind.SPALL, 3f) }
        val before = state.dataCollected

        state.detonateCracked()
        assertEquals(4L, state.dataCollected - before, "six detonations, four dispatches")
        assertTrue(cracked.all { state.markOf(it) == null })

        state.markDispatches = 0
        state.cashSpall(spalled)
        assertEquals(null, state.markOf(spalled))
        assertEquals(4L, state.dataCollected - before, "a Spall throw is Thermal Shock's mark, not Fracture's")
    }

    // -----------------------------------------------------------------------------------------
    // Conditions.

    @Test
    fun newConditionsReadTheDispatchAndTheRun() {
        val state = buildTestState(weapon = WeaponId.FLUX_WAKE)
        val target = state.dummy()
        fun holds(condition: EffectCondition, reaction: ReactionId? = null, skill: SkillId? = null) =
            state.holds(condition, target, null, null, reaction, skill)

        assertTrue(holds(EffectCondition.ReactionIs(ReactionId.FUSION), reaction = ReactionId.FUSION))
        assertFalse(holds(EffectCondition.ReactionIs(ReactionId.FUSION), reaction = ReactionId.WILDFIRE))
        assertTrue(holds(EffectCondition.SkillIs(SkillId.METEOR), skill = SkillId.METEOR))
        assertFalse(holds(EffectCondition.SkillIs(SkillId.METEOR), skill = SkillId.SOLAR_FLARE))
        assertTrue(holds(EffectCondition.SkillTagged(BuildTag.DRIVE), skill = SkillId.ARC_HOOK))
        assertFalse(holds(EffectCondition.SkillTagged(BuildTag.RAM), skill = SkillId.ARC_HOOK))
        assertFalse(holds(EffectCondition.SkillTagged(BuildTag.DRIVE)))

        assertFalse(holds(EffectCondition.CoreInField))
        state.coreFieldMask = 1 shl CombatFieldKind.HUSH.ordinal
        assertTrue(holds(EffectCondition.CoreInField))

        assertFalse(holds(EffectCondition.TargetMarked))
        target.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 1f
        assertTrue(holds(EffectCondition.TargetMarked))
        target.status[StatusCell.EMBRITTLED_UNTIL] = 0f
        state.voidAnchorId = target.id
        state.voidAnchorUntil = state.elapsed + 1f
        assertTrue(holds(EffectCondition.TargetMarked))
        state.voidAnchorId = -1
        state.setMark(target, MarkKind.SPORED, 2f)
        assertTrue(holds(EffectCondition.TargetMarked))

        target.disabledUntil = 0f
        assertFalse(holds(EffectCondition.TargetControlled))
        target.silencedUntil = state.elapsed + 0.5f
        assertTrue(holds(EffectCondition.TargetControlled))
        target.silencedUntil = 0f
        target.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 0.5f
        assertTrue(holds(EffectCondition.TargetControlled))

        assertFalse(holds(EffectCondition.TargetThrown))
        state.markThrown(target)
        assertTrue(holds(EffectCondition.TargetThrown))

        assertFalse(holds(EffectCondition.ApexActive))
        state.skillRuntime.setApex(ApexCell.ACTIVE_UNTIL, state.elapsed + 2f)
        assertTrue(holds(EffectCondition.ApexActive))

        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.acquireSkill(SkillId.METEOR)
        assertFalse(holds(EffectCondition.SkillsCharging(1)))
        state.skillRuntime[0, SkillCell.READY_AT] = state.elapsed + 3f
        assertTrue(holds(EffectCondition.SkillsCharging(1)))
        assertFalse(holds(EffectCondition.SkillsCharging(2)))
        state.skillRuntime[1, SkillCell.READY_AT] = state.elapsed + 3f
        assertTrue(holds(EffectCondition.SkillsCharging(2)))

        assertTrue(holds(EffectCondition.WeaponFamilyIs(WeaponFamily.MOMENTUM)))
        assertFalse(holds(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE)))
    }

    // -----------------------------------------------------------------------------------------
    // Actions.

    @Test
    fun skillRefundsSkipTheCastingSlotAndPickTheirSkill() {
        val all = probe("All Refund", on(EffectTrigger.SKILL_CAST, EffectAction.RefundSkills(1f, SkillPick.ALL)))
        val state = stateWith(all)
        listOf(SkillId.SOLAR_FLARE, SkillId.METEOR, SkillId.FROST_LINE).forEach(state::acquireSkill)
        state.elapsed = 10f
        fun cooling(slot: Int, left: Float) {
            state.skillRuntime[slot, SkillCell.CAST_AT] = state.elapsed - 3f
            state.skillRuntime[slot, SkillCell.LAST_COOLDOWN] = 10f
            state.skillRuntime[slot, SkillCell.REFUNDED] = 0f
            state.skillRuntime[slot, SkillCell.READY_AT] = state.elapsed + left
        }
        (0..2).forEach { cooling(it, 6f) }
        state.fireClauses(EffectTrigger.SKILL_CAST, skill = SkillId.METEOR)
        assertEquals(listOf(5f, 6f, 5f), (0..2).map { state.skillRuntime[it, SkillCell.READY_AT] - state.elapsed },
            "every skill but the one that cast")

        val cast = probe("Cast Refund", on(EffectTrigger.SKILL_HIT, EffectAction.RefundSkills(1f, SkillPick.CAST), cooldown = 0.5f))
        val longest = probe("Longest Refund", on(EffectTrigger.KILL, EffectAction.RefundSkills(1f, SkillPick.LONGEST), cooldown = 0.5f))
        val picker = stateWith(cast, longest)
        listOf(SkillId.SOLAR_FLARE, SkillId.METEOR, SkillId.FROST_LINE).forEach(picker::acquireSkill)
        picker.elapsed = 10f
        fun pickerCooling(slot: Int, left: Float) {
            picker.skillRuntime[slot, SkillCell.CAST_AT] = picker.elapsed - 3f
            picker.skillRuntime[slot, SkillCell.LAST_COOLDOWN] = 10f
            picker.skillRuntime[slot, SkillCell.READY_AT] = picker.elapsed + left
        }
        pickerCooling(0, 4f)
        pickerCooling(1, 7f)
        pickerCooling(2, 5f)
        picker.fireClauses(EffectTrigger.SKILL_HIT, picker.dummy(), hit = 10f, skill = SkillId.FROST_LINE)
        assertEquals(listOf(4f, 7f, 4f), (0..2).map { picker.skillRuntime[it, SkillCell.READY_AT] - picker.elapsed })
        picker.fireClauses(EffectTrigger.KILL, picker.dummy(), hit = 0f)
        assertEquals(listOf(4f, 6f, 4f), (0..2).map { picker.skillRuntime[it, SkillCell.READY_AT] - picker.elapsed })
    }

    @Test
    fun chargingTheApexRefundsItAtTheCapAndTheSkillsByAQuarterBefore() {
        val state = stateWith(probe("Crown Battery", on(EffectTrigger.LEVEL_UP, EffectAction.ChargeApex(4f))))
        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.elapsed = 20f
        state.skillRuntime[0, SkillCell.CAST_AT] = 15f
        state.skillRuntime[0, SkillCell.LAST_COOLDOWN] = 10f
        state.skillRuntime[0, SkillCell.READY_AT] = 25f
        state.fireClauses(EffectTrigger.LEVEL_UP)
        assertEquals(24f, state.skillRuntime[0, SkillCell.READY_AT])

        state.unlockApex()
        state.skillRuntime.setApex(ApexCell.LAST_CAST, 10f)
        state.skillRuntime.setApex(ApexCell.READY_AT, 70f)
        state.fireClauses(EffectTrigger.LEVEL_UP)
        assertEquals(66f, state.skillRuntime.apex(ApexCell.READY_AT))
        assertEquals(24f, state.skillRuntime[0, SkillCell.READY_AT], "at the cap only the Apex charges")
    }

    @Test
    fun forcedReactionsReachTheTargetAndTheNearestEligibleEnemiesUpToTheirCount() {
        val state = stateWith(probe("Chain Igniter", on(EffectTrigger.IMPACT, EffectAction.ForceReaction(200f, 3), cooldown = 1f),
            elements = listOf(StatusElement.THERMAL, StatusElement.CRYO)))
        val loaded = listOf(0f, 50f, 100f, 150f).map { x ->
            state.dummy(x = 400f + x, y = 0f).also {
                it.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 3f
                it.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 2f
            }
        }
        state.fireClauses(EffectTrigger.IMPACT, loaded[0], hit = 10f, source = HitSource.IMPACT)

        val reacted = loaded.map { it.status[StatusCell.LAST_REACTION].toInt() == ReactionId.THERMAL_SHOCK.ordinal + 1 }
        assertEquals(listOf(true, true, true, false), reacted, "the target and its two nearest")
    }

    @Test
    fun zonesOpenItemFieldsOfTheirKindWithoutDamageOfTheirOwn() {
        val maw = probe("Pocket Maw", on(EffectTrigger.DASH, EffectAction.Zone(ZoneKind.MAW, 120f, 2f)))
        val attractor = probe("Pocket Attractor", on(EffectTrigger.IMPACT, EffectAction.Zone(ZoneKind.ATTRACTOR, 140f, 3f), cooldown = 2f))
        val state = stateWith(maw, attractor)
        val target = state.dummy(x = 300f, y = 40f)
        state.fireClauses(EffectTrigger.DASH)
        state.fireClauses(EffectTrigger.IMPACT, target, hit = 5f, source = HitSource.IMPACT)

        val records = state.fieldRecords
        assertEquals(listOf(CombatFieldKind.NULL_WELL, CombatFieldKind.ATTRACTOR), records.map { it.kind })
        assertTrue(records.all { it.source == FieldSource.ITEM && it.damagePerSecond == 0f })
        assertEquals(listOf(maw.idIn(state), attractor.idIn(state)), records.map { it.owner })
        assertEquals(listOf(0f, 0f, 120f, 2f), listOf(records[0].x, records[0].y, records[0].radius, records[0].remaining))
        assertEquals(target.id, records[1].anchorId)
        assertTrue(records[1].flags and FieldFlags.FOLLOWS_ANCHOR != 0)
        assertEquals(state.reactionValue(ReactionId.MAGNETIZE, ATTRACTOR_PULL_VALUE), records[1].pull)
    }

    @Test
    fun itemZonesBorrowTheirReactionsBehaviour() {
        val state = stateWith(probe("Pocket Bubble", on(EffectTrigger.DASH, EffectAction.Zone(ZoneKind.TIME_BUBBLE, 150f, 3f))))
        state.fireClauses(EffectTrigger.DASH)
        val bolt = state.hostileBolt(30f, 0f)
        state.elapsed += 0.1f
        state.updateCombatFields(0.1f)
        assertTrue(bolt.spent, "an item's time bubble spends hostile shots like Stasis's")
        assertEquals(1L, state.shotsErased)
    }

    @Test
    fun anItemMawFillsAndPopsLikeTheNullWellItBorrows() {
        val state = stateWith(probe("Pocket Maw", on(EffectTrigger.DASH, EffectAction.Zone(ZoneKind.MAW, 150f, 1f))))
        state.fireClauses(EffectTrigger.DASH)
        val budget = state.reactionValue(ReactionId.NULL_WELL, MAW_SHOTS_VALUE)
        repeat(2) { state.hostileBolt(20f + it * 5f, 0f) }
        state.elapsed += 0.1f
        state.updateCombatFields(0.1f)
        assertEquals(2f / budget, state.toRenderModel().combatFields.single().charge01, 1e-6f)

        val bystander = state.dummy(x = 60f, y = 0f)
        repeat(state.reactionValue(ReactionId.NULL_WELL, MAW_POP_VALUE).toInt()) { state.hostileBolt(30f, 10f) }
        state.elapsed += 0.1f
        state.updateCombatFields(0.1f)
        state.elapsed += 1f
        state.updateCombatFields(1f)
        assertTrue(state.fieldRecords.isEmpty())
        assertTrue(bystander.vx > 0f, "a fed maw throws what stands in it when it closes")
    }

    @Test
    fun absoluteZeroSuppressesThermalReactionParts() {
        val keystone = probe("Quench Seal", rules = listOf(ItemRule.ReactionKeystone(ReactionId.THERMAL_SHOCK)),
            elements = listOf(StatusElement.THERMAL, StatusElement.CRYO), rarity = ItemRarity.EPIC)
        val tuned = probe("Frost Lens", rules = listOf(ItemRule.ReactionTuning(ReactionId.SUPERCONDUCT, ReactionAspect.RADIUS, 0.3f)))
        val counter = probe("Blast Counter", on(EffectTrigger.REACTION, EffectAction.Vent(5f), cooldown = 1f,
            given = listOf(EffectCondition.ReactionIs(ReactionId.PLASMA_BURST))))
        val base = canonicalGameplayContent.items
        val content = canonicalGameplayContent.copy(
            items = (base + listOf(keystone, tuned, counter).mapIndexed { index, item -> item.copy(id = base.size + index) }).toImmutableList(),
        )
        val state = buildTestState(kinetickk.ball.content.api.RelicId.ABSOLUTE_ZERO, content = content)
        assertEquals(1f, state.itemSuppressedShare(state.itemNamed("Quench Seal")))
        assertEquals(0f, state.itemSuppressedShare(state.itemNamed("Frost Lens")))
        assertEquals(0.5f, state.itemSuppressedShare(state.itemNamed("Blast Counter")), "the condition, not the action")
    }

    @Test
    fun erasingShotsTakesTheNearestWithinRangeUpToTheMaximum() {
        val state = stateWith(probe("Sweeper", on(EffectTrigger.HURT, EffectAction.EraseShots(200f, 2), cooldown = 1f)))
        val bolts = listOf(150f, 50f, 300f, 100f).map { state.hostileBolt(it, 0f) }
        state.fireClauses(EffectTrigger.HURT)
        assertEquals(listOf(false, true, false, true), bolts.map { it.spent })
    }

    @Test
    fun coreLaunchesAreGatedLikeEveryReactionPush() {
        val state = stateWith(probe("Kick Plate", on(EffectTrigger.DASH, EffectAction.CoreImpulse(600f, ImpulseDirection.FORWARD), cooldown = 0.5f)))
        state.velocityX = 100f
        state.velocityY = 0f
        state.fireClauses(EffectTrigger.DASH)
        assertEquals(700f, state.velocityX, 0.01f)
        state.clauseReadyAt.fill(0f)
        state.elapsed += 0.25f
        state.fireClauses(EffectTrigger.DASH)
        assertEquals(700f, state.velocityX, 0.01f, "a second launch within half a second waits")
    }

    @Test
    fun marksThrowsAndPrimedDashesLandOnTheirTargets() {
        val cracker = probe("Crack Hammer", on(EffectTrigger.IMPACT, EffectAction.ApplyMark(MarkPick.CRACKED, 3f), cooldown = 0.3f))
        val brittle = probe("Glass Hammer", on(EffectTrigger.DASH_HIT, EffectAction.ApplyMark(MarkPick.EMBRITTLED, 2f), EffectAction.Throw(500f)))
        val primer = probe("Dash Primer", on(EffectTrigger.BRAKE_RELEASE, EffectAction.PrimeDash))
        val state = stateWith(cracker, brittle, primer)
        val target = state.dummy(x = 200f, y = 0f)
        state.fireClauses(EffectTrigger.IMPACT, target, hit = 5f, source = HitSource.IMPACT)
        assertEquals(MarkKind.CRACKED, state.markOf(target))
        assertEquals(state.elapsed + 3f, target.status[StatusCell.MARK_UNTIL])

        val other = state.dummy(x = 0f, y = 200f)
        state.fireClauses(EffectTrigger.DASH_HIT, other, hit = 5f, source = HitSource.DASH)
        assertEquals(state.elapsed + 2f, other.status[StatusCell.EMBRITTLED_UNTIL])
        assertTrue(other.vy > 0f && other.thrownUntil > state.elapsed, "thrown away from the Core")

        state.fireClauses(EffectTrigger.BRAKE_RELEASE)
        assertEquals(state.elapsed + PRIME_DASH_SECONDS, state.primedDashUntil)
        state.heat = 10f
        state.performDash()
        assertEquals(10f, state.heat, "the primed Dash costs no heat")
        assertEquals(0f, state.primedDashUntil)
        state.performDash()
        assertTrue(state.heat > 10f)
    }

    // -----------------------------------------------------------------------------------------
    // Rules.

    @Test
    fun keystoneItemsSwitchTheirReactionAndTuningStaysWithinItsBounds() {
        val keystone = probe("Star Seal", rules = listOf(ItemRule.ReactionKeystone(ReactionId.FUSION)),
            elements = listOf(StatusElement.THERMAL, StatusElement.GRAVITIC), rarity = ItemRarity.EPIC)
        val named = probe("Fusion Lens", rules = listOf(ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.5f)))
        val named2 = probe("Fusion Lens II", rules = listOf(ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.5f)))
        val shared = probe("Wide Lens", rules = listOf(ItemRule.ReactionTuning(null, ReactionAspect.DURATION, 0.5f)))
        val shared2 = probe("Wide Lens II", rules = listOf(ItemRule.ReactionTuning(null, ReactionAspect.DURATION, 0.5f)))
        val state = stateWith(keystone, named, named2, shared, shared2)

        assertTrue(state.build.keystone(ReactionId.FUSION))
        assertFalse(state.build.keystone(ReactionId.WILDFIRE))
        assertEquals(1f + MAX_REACTION_TUNING, state.build.reactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS))
        assertEquals(1f + MAX_SHARED_REACTION_TUNING, state.build.reactionTuning(ReactionId.WILDFIRE, ReactionAspect.DURATION))
        assertEquals(1f, state.build.reactionTuning(ReactionId.WILDFIRE, ReactionAspect.FORCE))
        assertEquals(state.elapsed + EMBRITTLE_SECONDS * 1.5f, state.run {
            val target = dummy()
            setOff(ReactionId.EMBRITTLE, target)
            target.status[StatusCell.EMBRITTLED_UNTIL]
        }, 1e-4f)
        assertEquals(240f * 1.75f, state.tunedRadius(ReactionId.FUSION, 240f))
        assertEquals(MAX_TUNED_RADIUS, state.tunedRadius(ReactionId.FUSION, 500f))
    }

    @Test
    fun fieldTuningScalesEveryFieldAndReactionFieldsStayWithinTheirProduct() {
        val wide = probe("Field Lens", rules = listOf(ItemRule.FieldTuning(FieldAspect.RADIUS, 0.5f), ItemRule.FieldTuning(FieldAspect.DURATION, 0.2f)))
        val plasma = probe("Pool Lens", rules = listOf(ItemRule.ReactionTuning(ReactionId.PLASMA_BURST, ReactionAspect.RADIUS, 0.5f)))
        val state = stateWith(wide, plasma)
        state.acquireSkill(SkillId.STORM_COIL)
        state.addField(CombatFieldKind.PLASMA, FieldSource.REACTION, ReactionId.PLASMA_BURST.ordinal, 0f, 0f, radius = 100f,
            seconds = 2f, damagePerSecond = 0f)
        state.addField(CombatFieldKind.HUSH, FieldSource.REACTION, ReactionId.SILENCE.ordinal, 0f, 0f, radius = 100f,
            seconds = 2f, damagePerSecond = 0f)
        state.addField(CombatFieldKind.DARK_STAR, FieldSource.ITEM, 0, 0f, 0f, radius = 100f, seconds = 2f, damagePerSecond = 0f)
        state.addField(CombatFieldKind.STORM_COIL, FieldSource.SKILL, 0, 0f, 0f, radius = 500f, seconds = 2f, damagePerSecond = 0f)

        assertEquals(listOf(175f, 150f, 150f, MAX_TUNED_RADIUS), state.fieldRecords.map { it.radius })
        assertEquals(listOf(2.4f, 2.4f, 2.4f, 2.4f), state.fieldRecords.map { it.remaining })
    }

    @Test
    fun skillRulesCutCooldownsRaisePowerAndAttuneWithinTheirFloors() {
        val cut = probe("Quick Cog", rules = listOf(ItemRule.SkillCooldown(null, 0.25f)))
        val cut2 = probe("Quick Cog II", rules = listOf(ItemRule.SkillCooldown(null, 0.25f)))
        val ion = probe("Ion Cog", rules = listOf(ItemRule.SkillCooldown(StatusElement.ION, 0.1f), ItemRule.SkillPower(StatusElement.THERMAL, 0.5f)))
        val state = stateWith(cut, ion)
        assertEquals(0.75f, state.build.skillCooldownScale(StatusElement.THERMAL), 1e-6f)
        assertEquals(0.65f, state.build.skillCooldownScale(StatusElement.ION), 1e-6f)
        assertEquals(0.75f, state.build.skillCooldownScale(null), 1e-6f)
        assertEquals(state.runScale() * 1.5f, state.skillPower(SkillId.SOLAR_FLARE), 1e-4f)
        assertEquals(state.runScale(), state.skillPower(SkillId.ARC_HOOK), 1e-4f)

        val floored = stateWith(cut, cut2, probe("Hot Cog", rules = listOf(ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 25f))))
        assertEquals(MIN_SKILL_COOLDOWN_SCALE, floored.build.skillCooldownScale(StatusElement.VOID), "0.6 × 0.6 stops at the floor")
        val capped = stateWith(cut, cut2)
        assertEquals(1f - MAX_SKILL_COOLDOWN_CUT, capped.build.skillCooldownScale(null), 1e-6f)

        val attuned = stateWith(probe("Void Tuner", rules = listOf(ItemRule.SkillAttunement(ElementPick.VOID)), elements = listOf(StatusElement.VOID)))
        val targets = (0 until 10).map { attuned.dummy(x = 40f + it * 8f, y = 0f) }
        attuned.acquireSkill(SkillId.SOLAR_FLARE)
        attuned.acquireSkill(SkillId.FULL_STOP)
        assertEquals(StatusElement.VOID, attuned.skillElement(SkillId.SOLAR_FLARE))
        attuned.castSkill(0)
        assertTrue(targets.all { it.stacksOf(StatusElement.THERMAL) == 0 && it.stacksOf(StatusElement.VOID) > 0 }, "Solar Flare burns Void")
        targets.forEach { it.status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 0f }
        attuned.velocityX = 600f
        attuned.castSkill(1)
        assertEquals(MAX_ATTUNED_STACKS_PER_CAST, targets.count { it.stacksOf(StatusElement.VOID) == 1 }, "Full Stop attunes at most eight")
    }

    @Test
    fun skillChargesHoldTwoCastsRechargingOneAfterAnotherAndKeepTheRecastFloor() {
        val state = stateWith(probe("Twin Drum", rules = listOf(ItemRule.SkillCharges), rarity = ItemRarity.EPIC))
        state.acquireSkill(SkillId.SOLAR_FLARE)
        val cooldown = state.content.skill(SkillId.SOLAR_FLARE).rank(1).cooldownSeconds * SKILL_CHARGE_RECHARGE
        val start = state.elapsed

        assertTrue(state.castSkill(0))
        assertFalse(state.castSkill(0), "the recast floor holds between charges")
        state.elapsed = start + MIN_SKILL_RECAST_SECONDS
        assertTrue(state.castSkill(0), "the second charge")
        state.elapsed = start + MIN_SKILL_RECAST_SECONDS + 1f
        assertFalse(state.castSkill(0))
        state.elapsed = start + MIN_SKILL_RECAST_SECONDS + cooldown
        assertTrue(state.castSkill(0), "a charge recharges on the cooldown × 1.2")
        state.elapsed += 2 * cooldown + 1f
        state.accrueSkillCharges(0)
        assertEquals(MAX_SKILL_CHARGES, state.skillRuntime[0, SkillCell.CHARGES], "never more than two")
    }

    @Test
    fun catalysisApexAndLinkRulesStopAtTheirCaps() {
        val rate = probe("Catalyst Coil", rules = listOf(ItemRule.CatalysisRate(1f)))
        val rate2 = probe("Catalyst Coil II", rules = listOf(ItemRule.CatalysisRate(1f)))
        val apex = probe("Crown Gear", rules = listOf(ItemRule.ApexCooldown(0.2f)))
        val apex2 = probe("Crown Gear II", rules = listOf(ItemRule.ApexCooldown(0.2f)))
        val link = probe("Long Tether", rules = listOf(ItemRule.CoreLinkRadius(240f)))
        val link2 = probe("Long Tether II", rules = listOf(ItemRule.CoreLinkRadius(200f)))
        val state = stateWith(rate, rate2, apex, apex2, link, link2)
        assertEquals(2f, state.build.catalysisRate)
        assertEquals(MAX_APEX_COOLDOWN_CUT, state.build.apexCooldownCut)
        assertEquals(MAX_CORE_LINK_BONUS, state.build.coreLinkBonus)
        assertTrue(state.nearCore(CORE_LINK_RADIUS + 239f, 0f))
        assertFalse(state.nearCore(CORE_LINK_RADIUS + 241f, 0f))
    }

    // -----------------------------------------------------------------------------------------
    // Conversions and timed stats.

    @Test
    fun dashHeatAndDataPayIntoTheSkillsAtTheirCosts() {
        val state = stateWith(
            probe("Heat Ledger", rules = listOf(ItemRule.Convert(ConversionKind.DASH_HEAT_TO_SKILLS, 0.4f))),
            probe("Data Ledger", rules = listOf(ItemRule.Convert(ConversionKind.DATA_TO_SKILLS, 0.15f))),
        )
        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.elapsed = 20f
        state.skillRuntime[0, SkillCell.CAST_AT] = 15f
        state.skillRuntime[0, SkillCell.LAST_COOLDOWN] = 10f
        state.skillRuntime[0, SkillCell.READY_AT] = 25f

        state.bankDashHeat(25f)
        assertEquals(24.2f, state.skillRuntime[0, SkillCell.READY_AT], 1e-4f, "two steps of ten heat")
        assertEquals(5f, state.dashHeatBank, 1e-4f)
        assertEquals(0.8f * buildTestState().effectiveCooling(), state.effectiveCooling(), 1e-4f)

        state.dataPickupRechargesSkills()
        assertEquals(24.05f, state.skillRuntime[0, SkillCell.READY_AT], 1e-4f)
        assertEquals(0.8f, state.dataGainLiability(), 1e-6f)
        state.unlockApex()
        state.skillRuntime.setApex(ApexCell.LAST_CAST, 10f)
        state.skillRuntime.setApex(ApexCell.READY_AT, 70f)
        state.dataPickupRechargesSkills()
        assertEquals(69.7f, state.skillRuntime.apex(ApexCell.READY_AT), 1e-4f, "the Apex takes twice the amount")
    }

    @Test
    fun skillsCostHeatAndSkillFieldsTravelWithTheCore() {
        val state = stateWith(
            probe("Hot Cog", rules = listOf(ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 25f))),
            probe("Nomad Field", rules = listOf(ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 0.8f))),
        )
        state.acquireSkill(SkillId.FROST_LINE)
        state.aimAt(300f, 0f)
        state.heat = 10f
        assertTrue(state.castSkill(0))
        assertEquals(35f, state.heat, 1e-4f)
        val line = state.fieldRecords.single()
        val values = state.content.skill(SkillId.FROST_LINE).rank(1).values
        assertEquals(values[4] * 0.8f, line.radius, 1e-4f)
        assertTrue(line.flags and FieldFlags.FOLLOWS_CORE != 0)

        state.coreX = 500f
        state.coreY = -200f
        state.elapsed += 0.1f
        state.updateCombatFields(0.1f)
        val moved = state.fieldRecords.single()
        // The line stays FROST_LINE_AHEAD ahead of the Core along the aim it was cast with, as long as it was.
        assertEquals(500f + FROST_LINE_AHEAD, (moved.x + moved.x2) * 0.5f, 1e-3f)
        assertEquals(-200f, (moved.y + moved.y2) * 0.5f, 1e-3f)
        assertEquals(values[0], kotlin.math.abs(moved.y2 - moved.y), 1e-3f)
    }

    @Test
    fun throwsOnImmovableEnemiesBecomeGatedDamage() {
        val state = stateWith(probe("Anvil Doctrine", rules = listOf(ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE, 1f))))
        val elite = state.dummy(x = 200f, y = 0f, type = EnemyType.ELITE)
        val drifter = state.dummy(x = 0f, y = 200f)
        val twin = state.dummy(x = -200f, y = 0f, type = EnemyType.ELITE)
        state.dealProcDamage(twin, 1f * 600f * KNOCKBACK_DAMAGE_SCALE * state.runScale(), rescale = false)
        val before = elite.hp
        state.hurl(state.coreX, state.coreY, elite, 600f)
        assertEquals(twin.maxHp - twin.hp, before - elite.hp, 1e-2f, "amount × speed × 0.15 as proc damage")
        assertTrue(before > elite.hp)
        assertEquals(0f, elite.vx, "taken as damage instead of a throw")
        val hit = elite.hp
        state.hurl(state.coreX, state.coreY, elite, 600f)
        assertEquals(hit, elite.hp, "once per half second")
        assertTrue(elite.vx > 0f, "a gated throw moves the enemy as usual")
        state.hurl(state.coreX, state.coreY, drifter, 600f)
        assertTrue(drifter.vy > 0f && drifter.hp == drifter.maxHp, "a movable enemy is thrown")
    }

    @Test
    fun skillPowerAndSkillRechargeBuffsReachEverySkill() {
        val state = stateWith(
            probe("Spell Surge", on(EffectTrigger.LEVEL_UP, EffectAction.Buff(BuffStat.SKILL_POWER, 0.3f, 5f))),
            probe("Quick Hands", on(EffectTrigger.LEVEL_UP, EffectAction.Buff(BuffStat.SKILL_RECHARGE, 1f, 5f))),
        )
        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.elapsed = 20f
        state.skillRuntime[0, SkillCell.CAST_AT] = 15f
        state.skillRuntime[0, SkillCell.LAST_COOLDOWN] = 10f
        state.skillRuntime[0, SkillCell.READY_AT] = 25f
        state.fireClauses(EffectTrigger.LEVEL_UP)
        assertEquals(state.runScale() * 1.3f, state.skillPower(SkillId.SOLAR_FLARE), 1e-4f)
        state.updateSkills(0.5f)
        assertEquals(24.5f, state.skillRuntime[0, SkillCell.READY_AT], 1e-4f, "twice as fast: half a second more per half second")
    }

    // -----------------------------------------------------------------------------------------
    // Offers.

    @Test
    fun skillAndFamilyPartsSleepUntilTheRunCanUseThem() {
        val caster = probe("Spark Gap", on(EffectTrigger.SKILL_CAST, EffectAction.ApplyStatus(ElementPick.ION, 2, 160f)),
            elements = listOf(StatusElement.ION))
        val mixed = probe("Half Caster", on(EffectTrigger.SKILL_CAST, EffectAction.Vent(5f)), stats = true)
        val pulse = probe("Drum Grip", rules = listOf(ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE), BuffStat.WEAPON, 0.2f)))
        val apex = probe("Crown Battery", on(EffectTrigger.APEX, EffectAction.Vent(5f)))
        val state = stateWith(caster, mixed, pulse, apex, own = false)

        assertEquals(1f, state.itemDormantShare(caster.of(state)))
        assertEquals(0.5f, state.itemDormantShare(mixed.of(state)))
        assertEquals(1f, state.itemDormantShare(pulse.of(state)))
        assertEquals(0f, state.itemDormantShare(apex.of(state)), "Apex parts are never dormant")
        assertFalse(state.hasUsefulItemEffect(caster.of(state)))
        assertTrue(state.hasUsefulItemEffect(mixed.of(state)))
        assertFalse(state.hasUsefulItemEffect(pulse.of(state)))
        assertTrue(state.offerableItems().none { it.name == "Spark Gap" || it.name == "Drum Grip" })
        state.acquireItem(caster.of(state).id)
        assertEquals(listOf(caster.idIn(state)), state.buildSummaryDormantIds())

        state.acquireSkill(SkillId.SOLAR_FLARE)
        assertEquals(0f, state.itemDormantShare(caster.of(state)))
        assertTrue(state.buildSummaryDormantIds().isEmpty())

        val drummer = stateWith(pulse, own = false, weapon = WeaponId.CONCUSSION_ENGINE)
        assertEquals(0f, drummer.itemDormantShare(pulse.of(drummer)))
        assertTrue(drummer.hasUsefulItemEffect(pulse.of(drummer)))
    }

    @Test
    fun aKeystoneIsOfferedOnlyOnceItsReactionIsReachable() {
        val keystone = probe("Star Seal", rules = listOf(ItemRule.ReactionKeystone(ReactionId.FUSION)),
            elements = listOf(StatusElement.THERMAL, StatusElement.GRAVITIC), rarity = ItemRarity.EPIC)
        val state = stateWith(keystone, own = false)
        assertFalse(state.hasUsefulItemEffect(keystone.of(state)))
        state.own("Incendiary Rounds")
        assertFalse(state.hasUsefulItemEffect(keystone.of(state)), "Thermal alone reaches no Fusion")
        state.own("Graviton Seed")
        assertTrue(state.hasUsefulItemEffect(keystone.of(state)))
    }

    // -----------------------------------------------------------------------------------------
    // Helpers.

    private fun on(trigger: EffectTrigger, vararg actions: EffectAction, given: List<EffectCondition> = emptyList(), cooldown: Float = 0f) =
        EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown)

    private fun probe(
        name: String,
        vararg clauses: EffectClause,
        rules: List<ItemRule> = emptyList(),
        elements: List<StatusElement> = emptyList(),
        rarity: ItemRarity = ItemRarity.RARE,
        stats: Boolean = false,
    ): ItemDefinition = ItemDefinition(
        id = 0, name = name, description = "$name holds one piece of the vocabulary.", rarity = rarity,
        tags = immutableListOf(BuildTag.CHAIN), elements = elements.toImmutableList(),
        stats = if (stats) immutableListOf(kinetickk.ball.content.api.StatDelta(kinetickk.ball.content.api.ItemEffect.COOLING, 0.1f))
        else immutableListOf(),
        clauses = clauses.toList().toImmutableList(), rules = rules.toImmutableList(), maxStacks = 1, unlockLevel = 1,
        risk = rules.any { it.isLiability },
    )

    /** A quiet run whose catalog ends with [probes] (owned unless [own] is false). */
    private fun stateWith(
        vararg probes: ItemDefinition,
        own: Boolean = true,
        weapon: WeaponId = WeaponId.FLUX_WAKE,
        shape: CoreShape = CoreShape.ORB,
    ): MutableGameState {
        val base = canonicalGameplayContent.items
        val content: GameplayContentSnapshot = canonicalGameplayContent.copy(
            items = (base + probes.mapIndexed { index, probe -> probe.copy(id = base.size + index) }).toImmutableList(),
        )
        return buildTestState(content = content, weapon = weapon, shape = shape).apply {
            // No level-up interrupts a test with a choice.
            nextLevelData = Int.MAX_VALUE / 2
            if (own) probes.forEach { own(it.name) }
        }
    }

    private fun ItemDefinition.of(state: MutableGameState): ItemDefinition = state.itemNamed(name)

    private fun ItemDefinition.idIn(state: MutableGameState): Int = state.itemNamed(name).id

    private fun MutableGameState.buildSummaryDormantIds(): List<Int> =
        buildSummary(kinetickk.ball.gameplay.api.GameplayInstanceId(kinetickk.ball.gameplay.api.RunId(1)),
            kinetickk.ball.gameplay.api.GameplayRevision(1)).dormantItemIds.toList()
}
