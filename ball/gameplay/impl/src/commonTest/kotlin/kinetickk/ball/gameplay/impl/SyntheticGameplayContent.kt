// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.content.api.*
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** Matches the shipped catalog size; the item bound is [ContentBounds.MAX_ITEMS]. */
private const val SYNTHETIC_ITEM_COUNT = 173

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
        items = (0 until SYNTHETIC_ITEM_COUNT).map { id ->
            ItemDefinition(
                id = id,
                name = "Item $id",
                description = "Synthetic gameplay item $id",
                rarity = ItemRarity.entries[id % ItemRarity.entries.size],
                tags = immutableListOf(BuildTag.entries[id % BuildTag.entries.size]),
                elements = if (id % 2 == 0) immutableListOf(StatusElement.entries[id / 2 % StatusElement.entries.size]) else immutableListOf(),
                stats = immutableListOf(
                    StatDelta(ItemEffect.entries[id % ItemEffect.entries.size], 0.1f),
                    StatDelta(ItemEffect.entries[(id + 1) % ItemEffect.entries.size], 0.05f),
                ),
                maxStacks = ContentBounds.MAX_ITEM_STACKS,
                unlockLevel = 1,
            )
        }.toImmutableList(),
        weapons = WeaponId.entries.mapIndexed { index, id ->
            WeaponDefinition(
                id = id,
                name = "Weapon $index",
                description = "Synthetic weapon $index",
                family = WeaponFamily.entries[index % WeaponFamily.entries.size],
                element = null,
                tags = listOf(BuildTag.entries[index % BuildTag.entries.size], BuildTag.entries[(index + 1) % BuildTag.entries.size]),
                key = RelicKey.entries[index % RelicKey.entries.size],
                signature = CoreShape.entries[index % CoreShape.entries.size],
                scaling = WeaponScaling.entries[index % WeaponScaling.entries.size],
                cadence = WeaponCadence.DISCRETE,
                permanentUnlockCost = 0,
                minimumRebirth = 0,
                masteryForms = listOf("Synthetic form one", "Synthetic form two", "Synthetic form three"),
                signatureText = "Synthetic signature twist $index",
            )
        }.toImmutableList(),
        weaponMasteries = WeaponMastery.entries.toImmutableList(),
        relics = RelicId.entries.mapIndexed { index, id ->
            RelicDefinition(
                id = id,
                name = "Relic $index",
                aspect = RelicAspect.entries[index % RelicAspect.entries.size],
                keys = immutableListOf(RelicKey.entries[index % RelicKey.entries.size]),
                attunedTags = immutableListOf(BuildTag.entries[index % BuildTag.entries.size]),
                upside = "Synthetic relic upside $index",
                liability = "Synthetic relic liability $index",
            )
        }.toImmutableList(),
        rebirth = rebirth,
        relicPolicy = RelicPolicy(maxSlots = 4, maxRank = 3),
    )
}
