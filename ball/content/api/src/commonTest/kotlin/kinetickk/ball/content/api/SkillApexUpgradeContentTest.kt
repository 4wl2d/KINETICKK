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
            assertEquals(6, apex.bossStrikes, apex.name)
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
    fun reactionValuesAreEmptyByDefaultAndBoundedByFour() {
        val catalog = defaultBuildCatalog()
        assertTrue(catalog.reactions.all { it.values.isEmpty() })

        val base = catalog.reactions.first()
        base.copy(values = floats(ContentBounds.MAX_REACTION_VALUES))
        assertFailsWith<IllegalArgumentException> { base.copy(values = floats(ContentBounds.MAX_REACTION_VALUES + 1)) }
        assertFailsWith<IllegalArgumentException> { base.copy(values = immutableListOf(Float.NaN)) }
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
                CoreShape.ORB to 50, CoreShape.PRISM to 50, CoreShape.SHARD to 50, CoreShape.RING to 50,
                CoreShape.DIAMOND to 50, CoreShape.TESSERACT to 50, CoreShape.STAR to 50, CoreShape.SPIRAL to 60,
                CoreShape.DIPOLE to 50, CoreShape.PENTAGON to 80, CoreShape.CRESCENT to 100, CoreShape.HEXAGON to 120,
            ),
            bases,
        )
        assertEquals(listOf(35, 40, 50, 55, 65, 70, 75, 85, 90, 100, 105), (0..10).map(::defaultWeaponUpgradeBaseCost))
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
        val tempo = RunTempoProfile()
        assertEquals(3, tempo.skillSlotLevels.size)
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

    private fun floats(count: Int): ImmutableList<Float> = List(count) { it.toFloat() }.toImmutableList()
}
