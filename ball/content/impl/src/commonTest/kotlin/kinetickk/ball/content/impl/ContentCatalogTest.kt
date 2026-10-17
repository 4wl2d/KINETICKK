// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.isLiability
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentCatalogTest {
    private val catalog = createContentCatalog()
    private val gameplay = catalog.gameplayContent()
    private val profile = catalog.profilePolicy()
    private val ui = catalog.uiCatalog()

    @Test
    fun authorityPublishesOneVersionedSetOfCachedQuerySnapshots() {
        assertEquals("kinetickk-content-3", catalog.version.value)
        assertEquals(KINETICKK_CONTENT_VERSION, catalog.version)
        assertEquals(catalog.version, gameplay.version)
        assertEquals(catalog.version, profile.version)
        assertEquals(catalog.version, ui.version)

        assertSame(gameplay, catalog.gameplayContent())
        assertSame(profile, catalog.profilePolicy())
        assertSame(ui, catalog.uiCatalog())
        assertSame(gameplay.rebirth, profile.rebirth)
        assertSame(gameplay.rebirth, ui.rebirth)
        assertSame(gameplay.build, ui.build)
        assertSame(gameplay.items, ui.items)
    }

    @Test
    fun publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs() {
        assertFalse((gameplay.items as Any) is MutableList<*>)
        assertFalse((gameplay.weapons as Any) is MutableList<*>)
        assertFalse((gameplay.weaponMasteries as Any) is MutableList<*>)
        assertFalse((gameplay.matrixNodes as Any) is MutableList<*>)
        assertFalse((gameplay.relics as Any) is MutableList<*>)
        assertFalse((gameplay.clauseOffsets as Any) is MutableList<*>)
        assertFalse((gameplay.build.pairings as Any) is MutableList<*>)
        assertFalse((profile.coreShapes as Any) is MutableList<*>)
        assertTrue(gameplay.weapons.all { weapon -> (weapon.tags as Any) !is MutableList<*> })
        assertTrue(gameplay.items.all { item -> (item.clauses as Any) !is MutableList<*> && (item.rules as Any) !is MutableList<*> })

        val bootstrapItems = defaultContentBootstrapData().items
        val mutableItems = bootstrapItems.toMutableList()
        val copiedCatalog = createContentCatalogForTesting(
            defaultContentBootstrapData().copy(items = mutableItems),
        )
        mutableItems.clear()

        assertEquals(bootstrapItems.size, copiedCatalog.gameplayContent().items.size)
        assertEquals(bootstrapItems, copiedCatalog.gameplayContent().items)
    }

    @Test
    fun itemCatalogContainsNinetyDistinctHandAuthoredItemsWithinTheBound() {
        val items = gameplay.items

        assertEquals(90, items.size)
        assertTrue(items.size <= ContentBounds.MAX_ITEMS)
        assertEquals((0 until items.size).toList(), items.map(ItemDefinition::id))
        assertEquals(items.size, items.map(ItemDefinition::name).toSet().size)
        assertEquals(items.size, items.map(ItemDefinition::description).toSet().size)
        assertEquals(items.size, items.map(::mechanicalSignature).toSet().size, "Two items share the same mechanics")

        items.forEach { item ->
            val expectedStacks = when (item.rarity) {
                ItemRarity.COMMON -> 3
                ItemRarity.UNCOMMON, ItemRarity.RARE -> 2
                ItemRarity.EPIC, ItemRarity.LEGENDARY -> 1
            }
            assertEquals(expectedStacks, item.maxStacks, "${item.name} stacks follow its rarity")
            assertTrue(item.unlockLevel in 1..ContentBounds.MAX_ITEMS, "${item.name} has an invalid unlock level")
            assertTrue(
                item.tags.isNotEmpty() || item.elements.isNotEmpty() ||
                    item.rules.any { it is ItemRule.ReactionPower || it is ItemRule.ReactionResidue },
                "${item.name} belongs to no archetype, element or reaction family",
            )
            if (item.risk) {
                assertTrue(item.stats.any { it.amount < 0f } || item.rules.any { it.isLiability }, "${item.name} hides its cost")
            }
            assertEquals(item, gameplay.item(item.id))
            assertEquals(item, ui.item(item.id))
            assertTrue(profile.containsItem(item.id))
        }

        assertNull(gameplay.item(-1))
        assertNull(gameplay.item(items.size))
        assertFalse(profile.containsItem(-1))
        assertFalse(profile.containsItem(items.size))
    }

    @Test
    fun everyArchetypeAndElementIsDraftableAndRebirthGatesTheLegendaries() {
        val items = gameplay.items

        BuildTag.entries.forEach { tag ->
            assertTrue(items.count { tag in it.tags } >= 5, "$tag needs at least five items")
        }
        StatusElement.entries.forEach { element ->
            assertTrue(items.count { element in it.elements } >= 5, "$element needs at least five items")
            assertTrue(items.any { element in it.elements && it.unlockLevel <= 2 }, "$element needs an early enabler")
        }
        val legendaries = items.filter { it.rarity == ItemRarity.LEGENDARY }
        assertEquals(setOf("Solar Choir", "Glacial Heart", "Resonance Fork"), legendaries.map { it.name }.toSet())
        assertEquals(mapOf("Solar Choir" to 2, "Glacial Heart" to 2, "Resonance Fork" to 4), legendaries.associate { it.name to it.minimumRebirth })
        assertTrue(items.filter { it.rarity != ItemRarity.LEGENDARY }.all { it.minimumRebirth == 0 })
        assertTrue(items.filter { it.risk }.all { it.rarity == ItemRarity.EPIC }, "Volatile offers draw from high-risk epics")
    }

    @Test
    fun sonicAndVoidItemsAreAppendedUngatedCommonOrUncommonWithoutThresholdCuts() {
        val items = gameplay.items
        val sonic = items.subList(80, 85)
        val void = items.subList(85, 90)

        assertTrue(sonic.all { it.elements.toList() == listOf(StatusElement.SONIC) }, "ids 80-84 are Sonic")
        assertTrue(void.all { it.elements.toList() == listOf(StatusElement.VOID) }, "ids 85-89 are Void")
        (sonic + void).forEach { item ->
            assertTrue(item.rarity == ItemRarity.COMMON || item.rarity == ItemRarity.UNCOMMON, "${item.name} rarity")
            assertEquals(0, item.minimumRebirth, "${item.name} is ungated")
            assertFalse(item.rules.any { it is ItemRule.BreakThreshold }, "${item.name} cuts no threshold")
            assertFalse(item.risk)
        }
        assertEquals(1, sonic.count { it.unlockLevel <= 2 }, "one early Sonic enabler")
        assertEquals(1, void.count { it.unlockLevel <= 2 }, "one early Void enabler")
        // An enabler applies its element on an event every run sees often, not on a rare one such as a crit.
        val frequent = setOf(EffectTrigger.IMPACT, EffectTrigger.HURT, EffectTrigger.WEAPON_HIT)
        listOf(sonic, void).forEach { group ->
            val enabler = group.single { it.unlockLevel <= 2 }
            assertTrue(enabler.clauses.any { it.trigger in frequent }, "${enabler.name} applies its element on a frequent event")
        }
    }

    @Test
    fun clauseOffsetsFollowTheCapturedItemsWhenTheSnapshotIsCopied() {
        val items = gameplay.items

        assertEquals(items.sumOf { it.clauses.size }, gameplay.totalClauseCount)
        assertEquals(items.size, gameplay.clauseOffsets.size)
        var expected = 0
        items.forEach { item ->
            assertEquals(expected, gameplay.clauseOffsets[item.id])
            item.clauses.indices.forEach { clause -> assertEquals(expected + clause, gameplay.clauseIndex(item.id, clause)) }
            expected += item.clauses.size
        }

        val empty = gameplay.copy(items = immutableListOf())
        assertEquals(0, empty.totalClauseCount)
        assertTrue(empty.clauseOffsets.isEmpty())
        val oneClause = items.first { it.clauses.size == 1 }
        val single = gameplay.copy(items = immutableListOf(oneClause.copy(id = 0, clauses = (oneClause.clauses + oneClause.clauses).toImmutableList())))
        assertEquals(2, single.totalClauseCount)
        assertEquals(1, single.clauseIndex(0, 1))
        assertEquals(expected, gameplay.totalClauseCount)
    }

    @Test
    fun buildCatalogCoversEveryElementPairWithExactlyOneReaction() {
        val build = gameplay.build

        assertEquals(StatusElement.entries.toList(), build.elements.map { it.element })
        assertEquals(ReactionId.entries.toList(), build.reactions.map { it.id })
        assertEquals(BuildTag.entries.toList(), build.resonances.map { it.tag })
        assertEquals(ContentBounds.RESONANCE_TIERS, build.affinityTiers.size)

        val pairs = StatusElement.entries.flatMapIndexed { index, first ->
            StatusElement.entries.drop(index + 1).map { second -> first to second }
        }
        assertEquals(ReactionId.entries.size, pairs.size)
        pairs.forEach { (first, second) ->
            val reaction = ReactionId.between(first, second)
            assertEquals(reaction, ReactionId.between(second, first))
            assertTrue(reaction != null && reaction.involves(first) && reaction.involves(second))
        }
        assertEquals(ReactionId.entries.toSet(), pairs.mapNotNull { (a, b) -> ReactionId.between(a, b) }.toSet())
        StatusElement.entries.forEach { element -> assertNull(ReactionId.between(element, element)) }

        build.elements.forEach { definition ->
            assertTrue(definition.breakFloor in 2..definition.breakThreshold)
            assertEquals(definition.breakThreshold + 4, definition.stackCap)
        }
        build.reactions.forEach { reaction ->
            assertTrue(reaction.residue == null || reaction.residue in setOf(reaction.id.first, reaction.id.second))
        }
    }

    @Test
    fun buildPolicyTiersMagnitudesAndRebornChanceFollowTheirCurves() {
        val policy = gameplay.build.policy

        assertEquals(listOf(0, 0, 0, 1, 1, 2, 2, 3, 3), (0..8).map { policy.tier(it, policy.resonanceThresholds) })
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3), (0..6).map { policy.tier(it, policy.affinityThresholds) })
        assertEquals(3, policy.tier(99, policy.affinityThresholds))

        assertEquals(0f, policy.stackMagnitude(0))
        assertEquals(1f, policy.stackMagnitude(1))
        assertEquals(policy.stackMagnitude(ContentBounds.MAX_ITEM_STACKS), policy.stackMagnitude(ContentBounds.MAX_ITEM_STACKS + 1))
        val gains = (1..ContentBounds.MAX_ITEM_STACKS).map(policy::stackMagnitude).zipWithNext { a, b -> b - a }
        assertTrue(gains.zipWithNext().all { (a, b) -> b < a }, "Extra stacks give diminishing returns")

        assertEquals(0f, policy.rebornChance(-1))
        assertEquals(0f, policy.rebornChance(0))
        assertEquals(0.05f, policy.rebornChance(1))
        assertEquals(policy.maximumRebornChance, policy.rebornChance(ContentBounds.MAX_REBIRTH_LEVEL))
    }

    @Test
    fun weaponCatalogAndCapturedMasteryPolicyAreOrderedAndQueryable() {
        val weapons = gameplay.weapons

        assertEquals(ContentBounds.MAX_WEAPONS, WeaponId.entries.size)
        assertEquals(ContentBounds.MAX_WEAPONS, weapons.size)
        assertEquals(WeaponId.entries.toList(), weapons.map(WeaponDefinition::id))
        assertEquals(weapons.size, weapons.map(WeaponDefinition::id).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::name).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::description).toSet().size)
        assertTrue(weapons.zipWithNext().all { (left, right) ->
            right.permanentUnlockCost > left.permanentUnlockCost
        })
        weapons.forEach { weapon -> assertEquals(weapon, gameplay.weapon(weapon.id)) }

        val milestones = gameplay.weaponMasteries
        assertEquals(WeaponMastery.entries.toList(), milestones)
        assertEquals(WeaponMastery.CALIBRATED, gameplay.weaponMasteryForLevel(1))
        assertEquals(WeaponMastery.AMPLIFIED, gameplay.weaponMasteryForLevel(3))
        assertEquals(WeaponMastery.RESONANT, gameplay.weaponMasteryForLevel(6))
        assertEquals(WeaponMastery.ASCENDED, gameplay.weaponMasteryForLevel(10))
        assertEquals(WeaponMastery.AMPLIFIED, gameplay.weaponMasteryAfter(1))
        assertNull(gameplay.weaponMasteryAfter(10))
        assertTrue(milestones.zipWithNext().all { (left, right) ->
            right.minimumLevel > left.minimumLevel &&
                right.damageBonus > left.damageBonus &&
                right.activationSpeedBonus > left.activationSpeedBonus
        })
    }

    @Test
    fun matrixHasFourBranchesOfEightOrderedNodesEachEndingInOneKeystone() {
        val nodes = profile.matrixNodes

        assertEquals(32, MatrixNodeId.entries.size)
        assertTrue(MatrixNodeId.entries.size <= ContentBounds.MAX_MATRIX_NODES)
        assertEquals(MatrixNodeId.entries.toList(), nodes.map(MatrixNodeDefinition::id))
        assertEquals(nodes.size, nodes.map(MatrixNodeDefinition::name).toSet().size)
        assertSame(nodes, gameplay.matrixNodes)
        assertSame(nodes, ui.matrixNodes)
        MatrixBranch.entries.forEach { branch ->
            val inBranch = nodes.filter { it.branch == branch }
            assertEquals(8, inBranch.size, "$branch")
            assertEquals(1, inBranch.count(MatrixNodeDefinition::keystone), "$branch keystone")
            assertEquals(listOf(0, 1, 2, 3), inBranch.map(MatrixNodeDefinition::row).distinct().sorted(), "$branch rows")
            assertTrue(inBranch.single(MatrixNodeDefinition::keystone).row == 3, "$branch keystone is last")
        }
        nodes.forEach { node ->
            assertEquals(node, profile.matrixNode(node.id))
            assertEquals(node.maxRanks, node.costs.size)
            assertTrue(node.costs.zipWithNext().all { (left, right) -> right > left }, "${node.id} costs rise")
            assertFailsWith<IllegalArgumentException> { node.cost(-1) }
            assertFailsWith<IllegalArgumentException> { node.cost(node.maxRanks) }
            assertEquals(node.costs.sumOf { it.toLong() }, node.spent(node.maxRanks))
        }
    }

    @Test
    fun everyPureStatNodeMovesItsStatByANoticeableShareAtRankOne() {
        val percentEffects = setOf(
            ItemEffect.IMPACT_DAMAGE, ItemEffect.WEAPON_POWER, ItemEffect.COOLING, ItemEffect.DASH_POWER,
            ItemEffect.ATTACK_SPEED, ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN, ItemEffect.OVERDRIVE_GAIN,
        )
        profile.matrixNodes.forEach { node ->
            val stats = node.effects.filterIsInstance<MatrixEffect.Stat>().map { it.delta }
            val behaviors = node.effects.filterNot { it is MatrixEffect.Stat }
            if (behaviors.isEmpty()) {
                // A pure stat node: a positive percent stat of at least 12 % (weapon power and attack
                // speed; the rest give 15 %), or a flat stat worth more than 15 % of the Core's starting
                // value (25 of 100 integrity, 20 shield, 70 pickup radius, 0.5 luck).
                assertTrue(stats.any { delta ->
                    delta.amount > 0f && (delta.effect !in percentEffects || delta.amount >= 0.12f - 1e-4f)
                }, "${node.id} must change its stat noticeably")
            }
        }
    }

    @Test
    fun matrixNodesOnlySpendMoreForLaterRowsAndLaterGates() {
        MatrixBranch.entries.forEach { branch ->
            val inBranch = profile.matrixNodes.filter { it.branch == branch }
            val firstRankByRow = (0..3).map { row -> inBranch.filter { it.row == row }.minOf { it.costs.first() } }
            assertTrue(firstRankByRow.zipWithNext().all { (upper, lower) -> lower >= upper }, "$branch first ranks by row")
            inBranch.filter { it.row == 0 }.forEach { assertEquals(0, it.minimumRebirth, "${it.id} is open from the start") }
        }
    }

    @Test
    fun coreShapeUnlockPolicyIsCapturedInStableIdOrder() {
        assertEquals(CoreShape.entries.toList(), profile.coreShapes.map { definition -> definition.id })
        assertEquals(0, profile.coreShape(CoreShape.ORB).unlockTarget)
        assertEquals(3, profile.coreShape(CoreShape.PRISM).unlockTarget)
        assertEquals(20, profile.coreShape(CoreShape.SHARD).unlockTarget)
        assertEquals(profile.coreShapes, gameplay.coreShapes)
    }

    /** Everything that changes how an item plays, ignoring its name, flavor and id. */
    private fun mechanicalSignature(item: ItemDefinition): List<Any> =
        listOf(item.rarity, item.tags, item.elements, item.stats, item.clauses, item.rules, item.maxStacks, item.risk)
}
