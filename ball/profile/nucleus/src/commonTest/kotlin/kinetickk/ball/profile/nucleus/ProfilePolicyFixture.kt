// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.*
import kinetickk.foundation.collections.toImmutableList

internal val TestProfilePolicy: ProfilePolicySnapshot by lazy(::profilePolicyFixture)

private fun profilePolicyFixture(): ProfilePolicySnapshot = ProfilePolicySnapshot(
    version = ContentVersion("test-content"),
    itemCount = 400,
    coreShapes = listOf(0L, 25L, 90L).mapIndexed { index, cost ->
        CoreShapeDefinition(CoreShape.entries[index], cost)
    }.toImmutableList(),
    weapons = intArrayOf(0, 25, 55, 95, 145, 215, 305, 430, 610, 860, 1_200, 1_650)
        .mapIndexed { index, cost ->
            WeaponDefinition(WeaponId.entries[index], "Weapon $index", "Test weapon $index", listOf("TEST"), cost)
        }.toImmutableList(),
    metaUpgrades = listOf(10 to 18, 10 to 22, 8 to 24, 8 to 26, 8 to 30, 10 to 34, 10 to 38, 12 to 45)
        .mapIndexed { index, (maxRanks, baseCost) ->
            MetaUpgradeDefinition(
                MetaUpgradeId.entries[index],
                "Upgrade $index",
                "Test upgrade $index",
                maxRanks,
                baseCost,
                ItemModifier(ItemEffect.MAX_INTEGRITY, 1f),
            )
        }.toImmutableList(),
    rebirth = testRebirthPolicy(),
)

private fun testRebirthPolicy(): RebirthPolicySnapshot = RebirthPolicySnapshot(
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
