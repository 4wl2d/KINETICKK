// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkillApexUpgradeContentTest {
    private val skills = defaultSkillDefinitions()

    @Test
    fun theSkillPoolHasOneDefinitionPerIdAndEveryTagExactlyFourTimes() {
        assertEquals(18, SkillId.entries.size)
        assertTrue(SkillId.entries.size <= ContentBounds.MAX_SKILLS)
        assertEquals(SkillId.entries.toList(), skills.map(SkillDefinition::id))
        assertEquals(skills.size, skills.map(SkillDefinition::name).toSet().size)

        val tagCounts = skills.flatMap(SkillDefinition::tags).groupingBy { it }.eachCount()
        BuildTag.entries.forEach { tag -> assertEquals(4, tagCounts[tag], "tag $tag") }

        val elementCounts = skills.groupingBy(SkillDefinition::element).eachCount()
        StatusElement.entries.forEach { element -> assertEquals(2, elementCounts[element], "element $element") }
        assertEquals(4, elementCounts[null])

        val targeting = skills.groupingBy(SkillDefinition::targeting).eachCount()
        assertEquals(mapOf(SkillTargeting.SELF to 8, SkillTargeting.AREA to 6, SkillTargeting.AIM to 3, SkillTargeting.NEAREST to 1), targeting)
    }

    @Test
    fun everyShippedSkillHasThreeRanksWithinTheBounds() {
        skills.forEach { skill ->
            assertEquals(ContentBounds.MAX_SKILL_RANK, skill.ranks.size, "${skill.id}")
            skill.ranks.forEach { rank ->
                assertTrue(rank.cooldownSeconds >= ContentBounds.MIN_SKILL_COOLDOWN_SECONDS, "${skill.id}")
                assertTrue(rank.values.size in 1..ContentBounds.MAX_SKILL_VALUES, "${skill.id}")
            }
            assertTrue(skill.ranks.zipWithNext().all { (a, b) -> b.cooldownSeconds <= a.cooldownSeconds }, "${skill.id}")
            assertEquals(skill.ranks[0], skill.rank(0))
            assertEquals(skill.ranks[2], skill.rank(3))
            assertEquals(skill.ranks[2], skill.rank(4))
        }
        val meteor = skills[SkillId.METEOR.ordinal]
        assertEquals(listOf(12f, 11f, 10f), meteor.ranks.map(SkillRank::cooldownSeconds))
        assertEquals(StatusElement.THERMAL, meteor.element)
        assertEquals(520f, meteor.reach)
    }

    @Test
    fun skillRankValuesAcceptTheBoundAndRejectOneMore() {
        SkillRank(ContentBounds.MIN_SKILL_COOLDOWN_SECONDS, floats(ContentBounds.MAX_SKILL_VALUES))
        assertFailsWith<IllegalArgumentException> {
            SkillRank(10f, floats(ContentBounds.MAX_SKILL_VALUES + 1))
        }
        assertFailsWith<IllegalArgumentException> { SkillRank(2.49f, floats(1)) }
        assertFailsWith<IllegalArgumentException> { SkillRank(Float.NaN, floats(1)) }
        assertFailsWith<IllegalArgumentException> { SkillRank(5f, immutableListOf(Float.POSITIVE_INFINITY)) }
    }

    @Test
    fun skillDefinitionsRequireThreeOrderedRanksTwoTagsAndConsistentReach() {
        val base = skills[SkillId.SOLAR_FLARE.ordinal]
        val ranks = base.ranks

        assertFailsWith<IllegalArgumentException> { base.copy(ranks = ranks.take(2).toImmutableList()) }
        assertFailsWith<IllegalArgumentException> { base.copy(ranks = (ranks + ranks.last()).toImmutableList()) }
        assertFailsWith<IllegalArgumentException> { base.copy(ranks = ranks.reversed().toImmutableList()) }
        assertFailsWith<IllegalArgumentException> {
            base.copy(ranks = immutableListOf(ranks[0], ranks[1], SkillRank(8f, floats(2))))
        }
        assertFailsWith<IllegalArgumentException> { base.copy(tags = immutableListOf(BuildTag.RAM, BuildTag.RAM)) }
        assertFailsWith<IllegalArgumentException> { base.copy(tags = immutableListOf(BuildTag.RAM)) }
        assertFailsWith<IllegalArgumentException> { base.copy(reach = 100f) }
        assertFailsWith<IllegalArgumentException> { base.copy(targeting = SkillTargeting.AREA) }
        base.copy(targeting = SkillTargeting.AREA, reach = 100f)
        assertFailsWith<IllegalArgumentException> { base.copy(rankThree = " ") }
    }

    @Test
    fun everyCharacterHasADistinctApexWithinTheStrikeAndBoltBounds() {
        val apexes = CoreShape.entries.map { defaultCoreShapeDefinitions()[it.ordinal].apex }

        assertEquals(12, apexes.size)
        assertEquals(12, apexes.map(ApexDefinition::name).toSet().size)
        apexes.forEach { apex ->
            assertTrue(apex.bossStrikes <= ContentBounds.MAX_APEX_STRIKES, apex.name)
            assertTrue(apex.boltBudget <= ContentBounds.MAX_APEX_BOLTS, apex.name)
            assertTrue(apex.values.size <= ContentBounds.MAX_APEX_VALUES, apex.name)
            assertEquals(60f, apex.cooldownSeconds, apex.name)
            assertEquals(30f, apex.minimumCooldownSeconds, apex.name)
            // Lead addendum B1: per character, enough full-cap strikes to shorten an Architect fight by a like share.
            assertTrue(apex.bossStrikes in 5..ContentBounds.MAX_APEX_STRIKES, apex.name)
            assertTrue(apex.durationSeconds in 6f..10f, apex.name)
        }
        assertEquals(72, defaultApexDefinition(CoreShape.PENTAGON).boltBudget)
        assertEquals(0, defaultApexDefinition(CoreShape.SHARD).boltBudget)
    }

    @Test
    fun apexBoundsAcceptTheMaximumAndRejectOneMore() {
        val base = defaultApexDefinition(CoreShape.ORB)

        base.copy(bossStrikes = ContentBounds.MAX_APEX_STRIKES, boltBudget = ContentBounds.MAX_APEX_BOLTS)
        base.copy(values = floats(ContentBounds.MAX_APEX_VALUES))
        assertFailsWith<IllegalArgumentException> { base.copy(bossStrikes = ContentBounds.MAX_APEX_STRIKES + 1) }
        assertFailsWith<IllegalArgumentException> { base.copy(boltBudget = ContentBounds.MAX_APEX_BOLTS + 1) }
        assertFailsWith<IllegalArgumentException> { base.copy(values = floats(ContentBounds.MAX_APEX_VALUES + 1)) }
        assertFailsWith<IllegalArgumentException> { base.copy(bossStrikes = -1) }
        assertFailsWith<IllegalArgumentException> { base.copy(durationSeconds = 60f) }
        assertFailsWith<IllegalArgumentException> { base.copy(minimumCooldownSeconds = 61f) }
        assertFailsWith<IllegalArgumentException> { base.copy(minimumCooldownSeconds = 5f) }
    }

    @Test
    fun reactionValuesAreBoundedByFourAndEveryShippedReactionDeclaresItsVerb() {
        val catalog = defaultBuildCatalog()
        catalog.reactions.forEach { reaction ->
            assertTrue(reaction.values.isNotEmpty(), "${reaction.id} has verb values")
            assertTrue(reaction.verb.isNotBlank() && reaction.cue.isNotBlank(), "${reaction.id} has a verb and a cue")
            assertTrue(reaction.keystone.isNotBlank() && reaction.keystoneDescription.isNotBlank(), "${reaction.id} has a keystone")
        }
        val base = catalog.reactions.first()
        assertTrue(ReactionDefinition(base.id, base.name, base.description, base.power, base.radius).values.isEmpty(),
            "A reaction declared without values has none")

        base.copy(values = floats(ContentBounds.MAX_REACTION_VALUES))
        assertFailsWith<IllegalArgumentException> { base.copy(values = floats(ContentBounds.MAX_REACTION_VALUES + 1)) }
        assertFailsWith<IllegalArgumentException> { base.copy(values = immutableListOf(Float.NaN)) }
        base.copy(cue = "x".repeat(ContentBounds.MAX_REACTION_CUE_CHARS))
        assertFailsWith<IllegalArgumentException> { base.copy(cue = "x".repeat(ContentBounds.MAX_REACTION_CUE_CHARS + 1)) }
        assertFailsWith<IllegalArgumentException> { base.copy(cue = "") }
        assertFailsWith<IllegalArgumentException> { base.copy(keystoneDescription = "") }
    }

    @Test
    fun reactionVerbValuesFollowTheCombatSpec() {
        val values = defaultBuildCatalog().reactions.associate { it.id to it.values.toList() }
        assertEquals(
            mapOf(
                ReactionId.THERMAL_SHOCK to listOf(3f, 620f),
                ReactionId.PLASMA_BURST to listOf(110f, 2.5f, 0.25f, 0.5f),
                ReactionId.FUSION to listOf(300f, 500f, 500f),
                ReactionId.WILDFIRE to listOf(3f, 3f, 2f),
                ReactionId.SUPERCONDUCT to listOf(0.25f, 2.5f, 12f),
                ReactionId.GLACIER_CRUSH to listOf(25f, 300f),
                ReactionId.EMBRITTLE to listOf(500f),
                ReactionId.MAGNETIZE to listOf(3f, 260f, 220f, 4f),
                ReactionId.FEEDBACK to listOf(0.35f, 1f),
                ReactionId.ROT_WELL to listOf(2f, 20f, 0.5f, 3f),
                ReactionId.THUNDERCLAP to listOf(260f, 0.6f, 1.6f),
                ReactionId.SHATTERPOINT to listOf(4f, 74f, 6f),
                ReactionId.RESONANT_ARC to listOf(3f, 3f, 0.5f),
                ReactionId.SEISMIC to listOf(3f, 260f, 90f, 0.5f),
                ReactionId.FRACTURE to listOf(4f, 320f, 6f, 2f),
                ReactionId.SILENCE to listOf(170f, 2.5f, 0.5f),
                ReactionId.BLACKFIRE to listOf(200f, 0.6f, 8f, 1f),
                ReactionId.STASIS to listOf(140f, 2f, 24f, 1.5f),
                ReactionId.ANNIHILATE to listOf(3f, 30f, 520f),
                ReactionId.NULL_WELL to listOf(20f, 5f, 400f),
                ReactionId.OBLIVION to listOf(1.5f),
            ),
            values,
        )
        // The texts name two of these numbers in words; a retune must rewrite them.
        val build = defaultBuildCatalog()
        assertEquals(3f, build.reaction(ReactionId.MAGNETIZE).values[0])
        assertTrue("for three seconds" in build.reaction(ReactionId.MAGNETIZE).description)
        assertEquals(0.5f, build.reaction(ReactionId.PLASMA_BURST).values[3])
        assertTrue("half the heat" in build.reaction(ReactionId.PLASMA_BURST).description)
        // Pure damage only ever stayed or fell (combat spec §3.1).
        val powers = build.reactions.associate { it.id to it.power }
        val before = mapOf(
            ReactionId.THERMAL_SHOCK to 12f, ReactionId.PLASMA_BURST to 6f, ReactionId.WILDFIRE to 5f, ReactionId.SUPERCONDUCT to 7f,
            ReactionId.MAGNETIZE to 5f, ReactionId.SEISMIC to 4f, ReactionId.FRACTURE to 3f, ReactionId.ANNIHILATE to 9f,
        )
        assertEquals(mapOf(
            ReactionId.THERMAL_SHOCK to 10f, ReactionId.PLASMA_BURST to 5f, ReactionId.WILDFIRE to 4f, ReactionId.SUPERCONDUCT to 6f,
            ReactionId.MAGNETIZE to 4f, ReactionId.SEISMIC to 3.5f, ReactionId.FRACTURE to 2.5f, ReactionId.ANNIHILATE to 8f,
        ), before.keys.associateWith { powers.getValue(it) })
        before.forEach { (id, power) -> assertTrue(powers.getValue(id) < power, "$id power fell") }
    }

    @Test
    fun upgradePolicyBoundsAndDoublingPricesSaturate() {
        val policy = UpgradePolicy()
        assertEquals(10, policy.maxLevel)
        UpgradePolicy(maxLevel = ContentBounds.MAX_UPGRADE_LEVEL)
        UpgradePolicy(maxLevel = 1)
        assertFailsWith<IllegalArgumentException> { UpgradePolicy(maxLevel = ContentBounds.MAX_UPGRADE_LEVEL + 1) }
        assertFailsWith<IllegalArgumentException> { UpgradePolicy(maxLevel = 0) }
        assertFailsWith<IllegalArgumentException> { UpgradePolicy(weaponPowerPerLevel = -0.01f) }
        assertFailsWith<IllegalArgumentException> { UpgradePolicy(characterPowerPerLevel = Float.NaN) }

        assertEquals(listOf(50L, 100L, 200L, 400L, 800L, 1_600L, 3_200L, 6_400L, 12_800L, 25_600L), (0..9).map { policy.nextCost(50, it) })
        assertEquals(51_150L, (0..9).sumOf { policy.nextCost(50, it) })
        assertEquals(35_805L, (0..9).sumOf { policy.nextCost(35, it) })
        assertEquals(5_120_000L, policy.nextCost(ContentBounds.MAX_UPGRADE_BASE_COST, 9))
        assertEquals(1L shl 62, policy.nextCost(1, 62))
        assertEquals(Long.MAX_VALUE, policy.nextCost(1, 63))
        assertEquals(Long.MAX_VALUE, policy.nextCost(ContentBounds.MAX_UPGRADE_BASE_COST, 50))
        assertEquals(Long.MAX_VALUE, policy.nextCost(2, 62))
        assertEquals(0L, policy.nextCost(0, 70))
        assertFailsWith<IllegalArgumentException> { policy.nextCost(50, -1) }
        assertFailsWith<IllegalArgumentException> { policy.nextCost(-1, 0) }
    }

    @Test
    fun refitBasesFollowTheRebornRankLadder() {
        val bases = defaultCoreShapeDefinitions().associate { it.id to it.upgradeBaseCost }
        assertEquals(
            mapOf(
                CoreShape.ORB to 55, CoreShape.PRISM to 55, CoreShape.SHARD to 55, CoreShape.RING to 55,
                CoreShape.DIAMOND to 55, CoreShape.TESSERACT to 55, CoreShape.STAR to 55, CoreShape.SPIRAL to 65,
                CoreShape.DIPOLE to 55, CoreShape.PENTAGON to 90, CoreShape.CRESCENT to 110, CoreShape.HEXAGON to 130,
            ),
            bases,
        )
        assertEquals(listOf(40, 50, 55, 65, 70, 80, 90, 95, 105, 110, 120), (0..10).map(::defaultWeaponUpgradeBaseCost))
        assertEquals(CharacterUpgradeSignature(4f, 3f), defaultCharacterUpgradeSignature(CoreShape.DIPOLE))
        assertEquals(-0.04f, defaultCharacterUpgradeSignature(CoreShape.CRESCENT).perLevel)

        CoreShapeDefinition(CoreShape.ORB, upgradeBaseCost = ContentBounds.MAX_UPGRADE_BASE_COST)
        assertFailsWith<IllegalArgumentException> {
            CoreShapeDefinition(CoreShape.ORB, upgradeBaseCost = ContentBounds.MAX_UPGRADE_BASE_COST + 1)
        }
        assertFailsWith<IllegalArgumentException> { CoreShapeDefinition(CoreShape.ORB, upgradeBaseCost = 0) }
        assertFailsWith<IllegalArgumentException> { CharacterUpgradeSignature(Float.NaN) }
    }

    @Test
    fun snapshotsCarryTheSkillPoolInOrder() {
        val reordered = (listOf(skills[1], skills[0]) + skills.drop(2)).toImmutableList()
        val ui = UiCatalogSnapshot(
            version = KINETICKK_CONTENT_VERSION,
            items = immutableListOf(),
            weapons = immutableListOf(),
            weaponMasteries = immutableListOf(),
            relics = immutableListOf(),
            coreShapes = defaultCoreShapeDefinitions(),
            rebirth = RebirthPolicySnapshot(0, 0, immutableListOf(defaultRebirthProfile(0)), 120, 0.09f, 24f),
            relicPolicy = RelicPolicy(maxSlots = 4, maxRank = 3),
        )
        assertEquals(skills[SkillId.VOID_STEP.ordinal], ui.skill(SkillId.VOID_STEP))
        assertFailsWith<IllegalArgumentException> { ui.copy(skills = reordered) }
        assertFailsWith<IllegalArgumentException> { ui.copy(skills = skills.dropLast(1).toImmutableList()) }
    }

    @Test
    fun skillFieldReserveStaysBelowTheFieldBound() {
        val shipped = BuildPolicy()
        assertEquals(12, shipped.maximumFields)
        assertEquals(3, shipped.maximumSkillFields)
        BuildPolicy(maximumFields = 4, maximumSkillFields = 3)
        BuildPolicy(maximumFields = 4, maximumSkillFields = 1)
        assertFailsWith<IllegalArgumentException> { BuildPolicy(maximumFields = 4, maximumSkillFields = 4) }
        assertFailsWith<IllegalArgumentException> { BuildPolicy(maximumFields = 4, maximumSkillFields = 0) }
    }

    private fun floats(count: Int): ImmutableList<Float> = List(count) { it.toFloat() }.toImmutableList()
}
