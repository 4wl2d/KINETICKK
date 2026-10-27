// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActiveItemContentTest {
    private val potency = ItemRule.ElementPotency(StatusElement.THERMAL, 0.2f)

    @Test
    fun anActiveItemRanksWithItsStacksWhateverItsRarityAndCarriesExactlyOnePassiveRule() {
        val flare = active(rules = listOf(potency))
        assertTrue(flare.isActive)
        assertEquals(SkillId.SOLAR_FLARE, flare.active)
        assertFalse(active(rules = listOf(potency), effect = null).isActive)
        active(rarity = ItemRarity.EPIC, rules = listOf(potency))

        assertRejected("stacks exactly to rank 3") { active(maxStacks = ContentBounds.MAX_SKILL_RANK - 1, rules = listOf(potency)) }
        assertRejected("exactly one passive rule") { active(rules = emptyList()) }
        assertRejected("exactly one passive rule") {
            active(rules = listOf(potency, ItemRule.StatusDuration(StatusElement.THERMAL, 1.2f)))
        }
        assertRejected("exactly one passive rule") {
            active(rules = listOf(potency), stats = listOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.05f)))
        }
        assertRejected("exactly one passive rule") {
            active(
                rules = listOf(potency),
                clauses = listOf(EffectClause(EffectTrigger.DASH, actions = immutableListOf(EffectAction.Vent(5f)))),
            )
        }
    }

    @Test
    fun anActivePassiveIsNeitherACostAWagerNorSupportForActives() {
        // Less damage taken while braking is a gain; more is the first value that turns it into a cost.
        active(rules = listOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.15f)))
        assertRejected("carries no liability") {
            active(rules = listOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, 0.15f)))
        }
        assertRejected("carries no liability") { active(rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetElite, -0.1f))) }
        assertRejected("carries no liability") {
            active(rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedBelow(400f), BuffStat.DAMAGE_TAKEN, 0.2f)), risk = true)
        }
        listOf(
            ItemRule.SkillCooldown(null, 0.1f),
            ItemRule.CatalysisRate(0.25f),
            ItemRule.WhileModifier(EffectCondition.SkillsCharging(1), BuffStat.DAMAGE, 0.1f),
            ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(900f), BuffStat.SKILL_RECHARGE, 0.1f),
        ).forEach { rule -> assertRejected("neither Data economy nor skill support") { active(rules = listOf(rule)) } }
        listOf(
            ItemRule.BonusRerolls(1),
            ItemRule.OfferCards(1),
            ItemRule.BreakThreshold(StatusElement.THERMAL, 1),
        ).forEach { rule -> assertRejected("reroll, offer, threshold or keystone") { active(rules = listOf(rule)) } }
        assertRejected("reroll, offer, threshold or keystone") {
            active(rarity = ItemRarity.EPIC, rules = listOf(ItemRule.ReactionKeystone(ReactionId.FUSION)))
        }

        val familyCooling = ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.BEAM), BuffStat.COOLING, 0.1f)
        assertRejected("names no weapon family") { active(rules = listOf(familyCooling)) }
        // The same rule is fine on a passive item.
        assertNull(active(rules = listOf(familyCooling), effect = null).active)
    }

    @Test
    fun offerLaneActiveFactorsAcceptZeroAndRejectTheFirstValueBelowIt() {
        val shipped = OfferLanePolicy()
        assertEquals(1f, shipped.activeWeight)
        assertEquals(2f, shipped.characterActiveWeight)
        assertEquals(1.5f, shipped.ownedActiveWeight)

        OfferLanePolicy(activeWeight = 0f, characterActiveWeight = 0f, ownedActiveWeight = 0f)
        listOf(-0.01f, Float.NaN, Float.POSITIVE_INFINITY).forEach { bad ->
            assertFailsWith<IllegalArgumentException>("activeWeight $bad") { OfferLanePolicy(activeWeight = bad) }
            assertFailsWith<IllegalArgumentException>("characterActiveWeight $bad") { OfferLanePolicy(characterActiveWeight = bad) }
            assertFailsWith<IllegalArgumentException>("ownedActiveWeight $bad") { OfferLanePolicy(ownedActiveWeight = bad) }
        }
    }

    @Test
    fun everyCharacterLaneNamesThreeDistinctActiveEffects() {
        val shapes = defaultCoreShapeDefinitions()
        shapes.forEach { shape ->
            assertEquals(CoreShapeDefinition.ACTIVE_LANE_SIZE, shape.activeLane.toSet().size, "${shape.id} lane")
        }
        assertEquals(
            listOf(SkillId.SLINGSHOT, SkillId.ARC_HOOK, SkillId.CATALYZE),
            shapes.first { it.id == CoreShape.ORB }.activeLane.toList(),
        )
        assertEquals(
            listOf(SkillId.DECAY_SPORES, SkillId.GRAVITY_WELL, SkillId.STORM_COIL),
            shapes.first { it.id == CoreShape.HEXAGON }.activeLane.toList(),
        )

        CoreShapeDefinition(CoreShape.ORB, activeLane = immutableListOf(SkillId.METEOR, SkillId.METEOR.next(), SkillId.CATALYZE))
        assertFailsWith<IllegalArgumentException> {
            CoreShapeDefinition(CoreShape.ORB, activeLane = immutableListOf(SkillId.METEOR, SkillId.CATALYZE))
        }
        assertFailsWith<IllegalArgumentException> {
            CoreShapeDefinition(CoreShape.ORB, activeLane = immutableListOf(SkillId.METEOR, SkillId.METEOR, SkillId.CATALYZE))
        }
        assertFailsWith<IllegalArgumentException> {
            CoreShapeDefinition(
                CoreShape.ORB,
                activeLane = immutableListOf(SkillId.METEOR, SkillId.FROST_LINE, SkillId.CATALYZE, SkillId.SLINGSHOT),
            )
        }
    }

    @Test
    fun theGameplaySnapshotFindsTheItemCarryingEachActiveEffect() {
        val passive = active(rules = listOf(potency), effect = null).copy(name = "Passive")
        val flare = active(rules = listOf(potency)).copy(id = 1)
        val snapshot = snapshot(listOf(passive, flare))

        assertEquals(SkillId.entries.size, snapshot.activeItemIds.size)
        assertEquals(1, snapshot.activeItemIds[SkillId.SOLAR_FLARE.ordinal])
        assertTrue(snapshot.activeItemIds.withIndex().all { (ordinal, id) -> ordinal == SkillId.SOLAR_FLARE.ordinal || id == -1 })
        assertSame(snapshot.items[1], snapshot.activeItem(SkillId.SOLAR_FLARE))
        assertNull(snapshot.activeItem(SkillId.METEOR))

        // A catalog without active items still constructs; a second carrier of one effect is refused.
        assertTrue(snapshot(listOf(passive)).activeItemIds.all { it == -1 })
        val twin = flare.copy(id = 2, name = "Second Flare")
        assertRejected("carried by more than one item") { snapshot(listOf(passive, flare, twin)) }
    }

    private fun SkillId.next(): SkillId = SkillId.entries[ordinal + 1]

    private fun snapshot(items: List<ItemDefinition>) = GameplayContentSnapshot(
        version = KINETICKK_CONTENT_VERSION,
        items = items.toImmutableList(),
        weapons = immutableListOf(),
        weaponMasteries = immutableListOf(),
        relics = immutableListOf(),
        rebirth = RebirthPolicySnapshot(0, 0, immutableListOf(defaultRebirthProfile(0)), 120, 0.09f, 24f),
        relicPolicy = RelicPolicy(maxSlots = 4, maxRank = 3),
    )

    private fun active(
        rarity: ItemRarity = ItemRarity.UNCOMMON,
        maxStacks: Int = ContentBounds.MAX_SKILL_RANK,
        stats: List<StatDelta> = emptyList(),
        clauses: List<EffectClause> = emptyList(),
        rules: List<ItemRule>,
        risk: Boolean = false,
        effect: SkillId? = SkillId.SOLAR_FLARE,
    ): ItemDefinition = ItemDefinition(
        id = 0, name = "Solar Flare", description = "Holds one active effect.", rarity = rarity,
        tags = immutableListOf(BuildTag.OVERCLOCK, BuildTag.BULWARK), elements = immutableListOf(StatusElement.THERMAL),
        stats = stats.toImmutableList(), clauses = clauses.toImmutableList(), rules = rules.toImmutableList(),
        maxStacks = maxStacks, unlockLevel = 5, risk = risk, active = effect,
    )

    private fun assertRejected(expected: String, block: () -> Unit) {
        val failure = assertFailsWith<IllegalArgumentException> { block() }
        assertTrue(failure.message.orEmpty().contains(expected), "Expected '$expected' in '${failure.message}'")
    }
}
