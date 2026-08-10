// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal val TestCoreShapes: ImmutableList<CoreShapeDefinition> =
    listOf(0L, 25L, 90L).mapIndexed { index, cost ->
        CoreShapeDefinition(CoreShape.entries[index], cost)
    }.toImmutableList()

internal val TestRebirthPolicy: RebirthPolicySnapshot = RebirthPolicySnapshot(
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

internal fun testItems(count: Int = 400): ImmutableList<ItemDefinition> =
    (0 until count).map { id ->
        ItemDefinition(
            id = id,
            name = "Item $id",
            description = "Test item $id",
            rarity = ItemRarity.COMMON,
            primary = ItemModifier(ItemEffect.IMPACT_DAMAGE, 1f),
            secondary = ItemModifier(ItemEffect.WEAPON_POWER, 1f),
            maxStacks = 1,
            unlockLevel = 1,
            family = "Test",
        )
    }.toImmutableList()
