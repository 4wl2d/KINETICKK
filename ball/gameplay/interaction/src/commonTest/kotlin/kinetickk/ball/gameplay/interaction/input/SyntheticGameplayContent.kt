// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.content.api.*
import kinetickk.foundation.collections.toImmutableList

internal val SyntheticGameplayContent: GameplayContentSnapshot by lazy(::syntheticGameplayContent)

private fun syntheticGameplayContent(): GameplayContentSnapshot {
    val rebirth = RebirthPolicySnapshot(
        minimumLevel = 0,
        maximumLevel = 10,
        profiles = (0..10).map { tier ->
            RebirthProfile(
                tier, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
                120, 0.09f, 24f,
            )
        }.toImmutableList(),
        maxActiveEnemies = 120,
        minSpawnIntervalSeconds = 0.09f,
        minEliteIntervalSeconds = 24f,
    )
    return GameplayContentSnapshot(
        version = ContentVersion("synthetic-gameplay-test"),
        items = (0 until 400).map { id ->
            ItemDefinition(
                id = id,
                name = "Item $id",
                description = "Synthetic gameplay item $id",
                rarity = ItemRarity.entries[id % ItemRarity.entries.size],
                primary = ItemModifier(ItemEffect.entries[id % ItemEffect.entries.size], 0.1f),
                secondary = ItemModifier(ItemEffect.entries[(id + 1) % ItemEffect.entries.size], 0.05f),
                maxStacks = 8,
                unlockLevel = 1,
                family = "Synthetic",
            )
        }.toImmutableList(),
        weapons = WeaponId.entries.mapIndexed { index, id ->
            WeaponDefinition(id, "Weapon $index", "Synthetic weapon $index", listOf("TEST"), 0)
        }.toImmutableList(),
        weaponMasteries = WeaponMastery.entries.toImmutableList(),
        metaUpgrades = MetaUpgradeId.entries.mapIndexed { index, id ->
            MetaUpgradeDefinition(
                id, "Upgrade $index", "Synthetic upgrade $index", 10, 1,
                ItemModifier(ItemEffect.MAX_INTEGRITY, 1f),
            )
        }.toImmutableList(),
        relics = RelicId.entries.mapIndexed { index, id ->
            RelicDefinition(
                id, "Relic $index", RelicAspect.entries[index % RelicAspect.entries.size],
                "Synthetic relic $index", "Synthetic rank effect $index",
            )
        }.toImmutableList(),
        rebirth = rebirth,
        relicPolicy = RelicPolicy(maxSlots = 4, maxRank = 5),
    )
}
