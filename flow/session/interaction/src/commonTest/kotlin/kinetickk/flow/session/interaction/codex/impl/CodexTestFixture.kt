// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.*
import kinetickk.ball.profile.api.*
import kinetickk.foundation.collections.*
import kinetickk.flow.session.interaction.TestCoreShapes
import kinetickk.flow.session.interaction.TestRebirthPolicy
import kinetickk.flow.session.interaction.testItems

internal fun codexTestCatalog(count: Int = 400): UiCatalogSnapshot = UiCatalogSnapshot(
    version = ContentVersion("test-v1"), items = testItems(count),
    weapons = WeaponId.entries.map { WeaponDefinition(it, it.name, "Full weapon description for ${it.name}", listOf("test"), 10) }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(), metaUpgrades = immutableListOf(),
    relics = RelicId.entries.map { RelicDefinition(it, it.name, RelicAspect.entries[(it.ordinal / 6).coerceAtMost(6)], "Full relic description for ${it.name}", "Rank improves this relic") }.toImmutableList(),
    coreShapes = TestCoreShapes, rebirth = TestRebirthPolicy, relicPolicy = RelicPolicy(4, 3),
)

internal fun codexTestProgress(discovered: Set<Int> = setOf(2, 399)): HomeProgressProjection = HomeProgressProjection(
    LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO, PlayerEconomy(), PlayerLoadout(), PlayerCollection(discovered), RebirthProgress(), false,
)
