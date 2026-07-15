package void.kinetic.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentCatalogTest {
    @Test
    fun itemCatalogContainsFourHundredDistinctValidItems() {
        val items = ItemCatalog.all

        assertEquals(400, ItemCatalog.ITEM_COUNT)
        assertEquals(ItemCatalog.ITEM_COUNT, items.size)
        assertEquals(20, ItemEffect.entries.size)
        assertEquals((0 until ItemCatalog.ITEM_COUNT).toList(), items.map(ItemDefinition::id))
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
            assertEquals(item, ItemCatalog.byId(item.id))
        }

        assertNull(ItemCatalog.byId(-1))
        assertNull(ItemCatalog.byId(ItemCatalog.ITEM_COUNT))
    }

    @Test
    fun weaponCatalogContainsTwelveOrderedUniqueWeapons() {
        val weapons = WeaponCatalog.all

        assertEquals(12, WeaponId.entries.size)
        assertEquals(12, weapons.size)
        assertEquals(WeaponId.entries.toList(), weapons.map(WeaponDefinition::id))
        assertEquals(weapons.size, weapons.map(WeaponDefinition::id).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::name).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::description).toSet().size)
        assertTrue(weapons.zipWithNext().all { (left, right) ->
            right.permanentUnlockCost > left.permanentUnlockCost
        })

        weapons.forEach { weapon ->
            assertTrue(weapon.name.isNotBlank())
            assertTrue(weapon.description.isNotBlank())
            assertTrue(weapon.tags.isNotEmpty() && weapon.tags.none(String::isBlank))
            assertTrue(weapon.permanentUnlockCost >= 0)
            assertEquals(weapon, WeaponCatalog.byId(weapon.id))
        }
    }

    @Test
    fun weaponMasteryHasOrderedMeaningfulMilestones() {
        val milestones = WeaponMastery.entries

        assertEquals(WeaponMastery.CALIBRATED, WeaponMastery.forLevel(1))
        assertEquals(WeaponMastery.AMPLIFIED, WeaponMastery.forLevel(3))
        assertEquals(WeaponMastery.RESONANT, WeaponMastery.forLevel(6))
        assertEquals(WeaponMastery.ASCENDED, WeaponMastery.forLevel(10))
        assertEquals(WeaponMastery.AMPLIFIED, WeaponMastery.after(1))
        assertNull(WeaponMastery.after(10))
        assertTrue(milestones.zipWithNext().all { (left, right) ->
            right.minimumLevel > left.minimumLevel &&
                right.damageBonus > left.damageBonus &&
                right.activationSpeedBonus > left.activationSpeedBonus
        })
    }

    @Test
    fun metaUpgradeCatalogContainsEightOrderedUpgradesWithValidCosts() {
        val upgrades = MetaUpgradeCatalog.all

        assertEquals(8, MetaUpgradeId.entries.size)
        assertEquals(8, upgrades.size)
        assertEquals(MetaUpgradeId.entries.toList(), upgrades.map(MetaUpgradeDefinition::id))
        assertEquals(upgrades.size, upgrades.map(MetaUpgradeDefinition::id).toSet().size)
        assertEquals(upgrades.size, upgrades.map(MetaUpgradeDefinition::name).toSet().size)

        upgrades.forEach { upgrade ->
            assertTrue(upgrade.name.isNotBlank())
            assertTrue(upgrade.description.isNotBlank())
            assertTrue(upgrade.maxRanks > 0)
            assertTrue(upgrade.baseCost > 0)
            assertEquals(upgrade, MetaUpgradeCatalog.byId(upgrade.id))

            val costs = (0 until upgrade.maxRanks).map(upgrade::cost)
            assertEquals(upgrade.baseCost, costs.first())
            assertTrue(costs.all { it > 0 })
            assertTrue(costs.zipWithNext().all { (left, right) -> right > left })
            assertFailsWith<IllegalArgumentException> { upgrade.cost(-1) }
            assertFailsWith<IllegalArgumentException> { upgrade.cost(upgrade.maxRanks) }
        }
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
