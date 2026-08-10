// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentBalanceCharacterizationTest {
    private val catalog = createContentCatalog()
    private val ui = catalog.uiCatalog()

    @Test
    fun itemCatalogCardinalityOrderingAndContentAreGolden() {
        val items = ui.items

        assertEquals(ContentBounds.MAX_ITEMS, items.size)
        assertEquals((0 until ContentBounds.MAX_ITEMS).toList(), items.map(ItemDefinition::id))
        assertEquals(
            "456e042b2ce96b71",
            goldenFingerprint {
                add("item-effects")
                ItemEffect.entries.forEach { effect ->
                    add(effect.name)
                    add(effect.displayLabel)
                    add(effect.unit.name)
                }
                add("item-rarities")
                ItemRarity.entries.forEach { rarity ->
                    add(rarity.name)
                    add(rarity.displayLabel)
                    add(rarity.rank)
                }
                add("items")
                add(items.size)
                items.forEach { item ->
                    add(item.id)
                    add(item.name)
                    add(item.description)
                    add(item.rarity.name)
                    add(item.primary.effect.name)
                    add(item.primary.amount)
                    add(item.secondary.effect.name)
                    add(item.secondary.amount)
                    add(item.maxStacks)
                    add(item.unlockLevel)
                    add(item.family)
                }
            },
        )
    }

    @Test
    fun weaponAndMetaUpgradeEconomyPoliciesAreGolden() {
        assertEquals(
            listOf(
                WeaponId.FLUX_WAKE to 0,
                WeaponId.MORNINGSTAR to 25,
                WeaponId.PHASE_LATTICE to 55,
                WeaponId.NULL_LANCE to 95,
                WeaponId.GRAVITY_MINES to 145,
                WeaponId.ION_SWARM to 215,
                WeaponId.RIFT_BLADES to 305,
                WeaponId.ARC_COIL to 430,
                WeaponId.QUASAR_CANNON to 610,
                WeaponId.ENTROPY_FIELD to 860,
                WeaponId.SINGULARITY_SPEAR to 1_200,
                WeaponId.PRISM_RELAY to 1_650,
            ),
            ui.weapons.map { weapon -> weapon.id to weapon.permanentUnlockCost },
        )
        assertEquals(
            listOf(
                MetaPolicy(MetaUpgradeId.CORE_INTEGRITY, maxRanks = 10, baseCost = 18),
                MetaPolicy(MetaUpgradeId.KINETIC_AMPLIFIER, maxRanks = 10, baseCost = 22),
                MetaPolicy(MetaUpgradeId.MAGNETIC_RESONANCE, maxRanks = 8, baseCost = 24),
                MetaPolicy(MetaUpgradeId.CRYO_VENTS, maxRanks = 8, baseCost = 26),
                MetaPolicy(MetaUpgradeId.DASH_CAPACITOR, maxRanks = 8, baseCost = 30),
                MetaPolicy(MetaUpgradeId.SALVAGE_PROTOCOL, maxRanks = 10, baseCost = 34),
                MetaPolicy(MetaUpgradeId.DATA_ARCHIVE, maxRanks = 10, baseCost = 38),
                MetaPolicy(MetaUpgradeId.ARMORY_LICENSE, maxRanks = 12, baseCost = 45),
            ),
            ui.metaUpgrades.map { upgrade ->
                MetaPolicy(upgrade.id, upgrade.maxRanks, upgrade.baseCost)
            },
        )
        assertEquals(
            "3d19c0391c2422a3",
            goldenFingerprint {
                add("weapons")
                ui.weapons.forEach { weapon ->
                    add(weapon.id.name)
                    add(weapon.name)
                    add(weapon.description)
                    add(weapon.tags.size)
                    weapon.tags.forEach(::add)
                    add(weapon.permanentUnlockCost)
                }
                add("meta-upgrades")
                ui.metaUpgrades.forEach { upgrade ->
                    add(upgrade.id.name)
                    add(upgrade.name)
                    add(upgrade.description)
                    add(upgrade.maxRanks)
                    add(upgrade.baseCost)
                    add(upgrade.modifierPerRank.effect.name)
                    add(upgrade.modifierPerRank.amount)
                }
            },
        )
    }

    @Test
    fun rebirthLevelsZeroThroughTenAreGolden() {
        val rebirth = ui.rebirth
        val profiles = rebirth.profiles

        assertEquals(10, rebirth.maximumLevel)
        assertEquals(120, rebirth.maxActiveEnemies)
        assertEquals(0.09f, rebirth.minSpawnIntervalSeconds)
        assertEquals(24f, rebirth.minEliteIntervalSeconds)
        assertEquals((0..10).toList(), profiles.map(RebirthProfile::tier))
        assertEquals(
            "4748c45a6b647fcf",
            goldenFingerprint {
                profiles.forEach { profile ->
                    add(profile.tier)
                    add(profile.directive.name)
                    add(profile.openingEnemyCount)
                    add(profile.enemyCapMultiplier)
                    add(profile.spawnRateMultiplier)
                    add(profile.enemyHealthMultiplier)
                    add(profile.enemySpeedMultiplier)
                    add(profile.incomingDamageMultiplier)
                    add(profile.eliteRateMultiplier)
                    add(profile.threatTimeOffsetSeconds)
                    add(profile.playerPowerMultiplier)
                    add(profile.playerIntegrityBonus)
                    add(profile.matterGainMultiplier)
                    add(profile.bonusRerolls)
                }
            },
        )
    }

    @Test
    fun relicLimitsOrderingAndContentIdentityAreGolden() {
        val relics = ui.relics

        assertEquals(ContentBounds.MAX_RELICS, relics.size)
        assertEquals(4, ui.relicPolicy.maxSlots)
        assertEquals(5, ui.relicPolicy.maxRank)
        assertEquals(RelicId.entries.toList(), relics.map(RelicDefinition::id))
        assertEquals(
            "5b480a9cc724582c",
            goldenFingerprint {
                add(relics.size)
                relics.forEach { relic ->
                    add(relic.id.name)
                    add(relic.name)
                    add(relic.aspect.name)
                    add(relic.description)
                    add(relic.rankEffect)
                }
            },
        )
    }
}

private data class MetaPolicy(
    val id: MetaUpgradeId,
    val maxRanks: Int,
    val baseCost: Int,
)

private fun goldenFingerprint(values: GoldenFingerprint.() -> Unit): String =
    GoldenFingerprint().apply(values).finish()

private class GoldenFingerprint {
    private var hash = FNV_OFFSET_BASIS

    fun add(value: String) {
        value.forEach { character -> mix(character.code) }
        mix(FIELD_SEPARATOR)
    }

    fun add(value: Int) = add(value.toString())

    fun add(value: Float) = add(value.toBits())

    fun finish(): String = hash.toString(radix = 16).padStart(16, '0')

    private fun mix(value: Int) {
        hash = (hash xor value.toULong()) * FNV_PRIME
    }

    private companion object {
        const val FIELD_SEPARATOR = 0x1F
        const val FNV_OFFSET_BASIS = 14_695_981_039_346_656_037uL
        const val FNV_PRIME = 1_099_511_628_211uL
    }
}
