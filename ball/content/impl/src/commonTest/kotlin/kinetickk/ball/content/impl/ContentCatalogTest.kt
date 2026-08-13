// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
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
        assertEquals("kinetickk-content-1", catalog.version.value)
        assertEquals(KINETICKK_CONTENT_VERSION, catalog.version)
        assertEquals(catalog.version, gameplay.version)
        assertEquals(catalog.version, profile.version)
        assertEquals(catalog.version, ui.version)

        assertSame(gameplay, catalog.gameplayContent())
        assertSame(profile, catalog.profilePolicy())
        assertSame(ui, catalog.uiCatalog())
        assertSame(gameplay.rebirth, profile.rebirth)
        assertSame(gameplay.rebirth, ui.rebirth)
    }

    @Test
    fun publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs() {
        assertFalse((gameplay.items as Any) is MutableList<*>)
        assertFalse((gameplay.weapons as Any) is MutableList<*>)
        assertFalse((gameplay.weaponMasteries as Any) is MutableList<*>)
        assertFalse((gameplay.metaUpgrades as Any) is MutableList<*>)
        assertFalse((gameplay.relics as Any) is MutableList<*>)
        assertFalse((profile.coreShapes as Any) is MutableList<*>)
        assertTrue(gameplay.weapons.all { weapon -> (weapon.tags as Any) !is MutableList<*> })

        val mutableItems = defaultContentBootstrapData().items.toMutableList()
        val copiedCatalog = createContentCatalogForTesting(
            defaultContentBootstrapData().copy(items = mutableItems),
        )
        mutableItems.clear()

        assertEquals(ContentBounds.MAX_ITEMS, copiedCatalog.gameplayContent().items.size)
    }

    @Test
    fun itemCatalogContainsFourHundredDistinctValidItems() {
        val items = gameplay.items

        assertEquals(ContentBounds.MAX_ITEMS, items.size)
        assertEquals(20, ItemEffect.entries.size)
        assertEquals((0 until ContentBounds.MAX_ITEMS).toList(), items.map(ItemDefinition::id))
        assertEquals(items.size, items.map(ItemDefinition::id).toSet().size)
        assertEquals(items.size, items.map(ItemDefinition::name).toSet().size)
        assertEquals(items.size, items.map(ItemDefinition::description).toSet().size)
        assertEquals(items.size, items.map(::mechanicalSignature).toSet().size)

        items.forEach { item ->
            assertTrue(item.name.isNotBlank(), "Item ${item.id} has a blank name")
            assertTrue(item.description.isNotBlank(), "Item ${item.id} has a blank description")
            assertTrue(item.family.isNotBlank(), "Item ${item.id} has a blank family")
            assertTrue(item.primary.amount.isFinite() && item.primary.amount > 0f)
            assertTrue(item.secondary.amount.isFinite() && item.secondary.amount > 0f)
            assertEquals(9 - item.rarity.rank, item.maxStacks)
            assertTrue(item.maxStacks in 1..8, "Item ${item.id} has invalid max stacks")
            assertTrue(item.unlockLevel in 1..80, "Item ${item.id} has invalid unlock level")
            assertEquals(item, gameplay.item(item.id))
            assertTrue(profile.containsItem(item.id))
        }

        assertNull(gameplay.item(-1))
        assertNull(gameplay.item(ContentBounds.MAX_ITEMS))
        assertFalse(profile.containsItem(-1))
        assertFalse(profile.containsItem(ContentBounds.MAX_ITEMS))
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
    fun metaUpgradeCatalogContainsEightOrderedUpgradesWithValidCosts() {
        val upgrades = profile.metaUpgrades

        assertEquals(ContentBounds.MAX_META_UPGRADES, MetaUpgradeId.entries.size)
        assertEquals(ContentBounds.MAX_META_UPGRADES, upgrades.size)
        assertEquals(MetaUpgradeId.entries.toList(), upgrades.map(MetaUpgradeDefinition::id))
        assertEquals(upgrades.size, upgrades.map(MetaUpgradeDefinition::id).toSet().size)
        assertEquals(upgrades.size, upgrades.map(MetaUpgradeDefinition::name).toSet().size)

        upgrades.forEach { upgrade ->
            assertEquals(upgrade, profile.metaUpgrade(upgrade.id))
            val costs = (0 until upgrade.maxRanks).map(upgrade::cost)
            assertEquals(upgrade.baseCost, costs.first())
            assertTrue(costs.zipWithNext().all { (left, right) -> right > left })
            assertFailsWith<IllegalArgumentException> { upgrade.cost(-1) }
            assertFailsWith<IllegalArgumentException> { upgrade.cost(upgrade.maxRanks) }
        }
    }

    @Test
    fun coreShapeUnlockPolicyIsCapturedInStableIdOrder() {
        assertEquals(CoreShape.entries.toList(), profile.coreShapes.map { definition -> definition.id })
        assertEquals(0L, profile.coreShape(CoreShape.ORB).unlockLifetimeMatter)
        assertEquals(25L, profile.coreShape(CoreShape.PRISM).unlockLifetimeMatter)
        assertEquals(90L, profile.coreShape(CoreShape.SHARD).unlockLifetimeMatter)
    }

    private fun mechanicalSignature(item: ItemDefinition): List<Pair<Int, Int>> {
        val totals = FloatArray(ItemEffect.entries.size)
        totals[item.primary.effect.ordinal] += item.primary.amount
        totals[item.secondary.effect.ordinal] += item.secondary.amount
        return buildList {
            totals.forEachIndexed { index, amount ->
                if (amount != 0f) add(index to amount.toBits())
            }
            add(-1 to item.maxStacks)
        }
    }
}
