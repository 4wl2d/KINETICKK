// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

internal val TestCoreShapes: ImmutableList<CoreShapeDefinition> =
    CoreShape.entries.map { CoreShapeDefinition(it) }.toImmutableList()

internal val TestRebirthPolicy: RebirthPolicySnapshot = RebirthPolicySnapshot(
    minimumLevel = 0,
    maximumLevel = 10,
    profiles = (0..10).map { tier ->
        RebirthProfile(
            tier, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )
    }.toImmutableList(),
    maxActiveEnemies = 120,
    minSpawnIntervalSeconds = 0.09f,
    minEliteIntervalSeconds = 24f,
)

/** The shipped catalog's size; codex fixtures default to it. */
internal const val TEST_ITEM_COUNT = 80

/** Plain test items: one tag each, every fourth with an element, one passive stat. */
internal fun testItems(count: Int = TEST_ITEM_COUNT): ImmutableList<ItemDefinition> =
    (0 until count).map { id ->
        ItemDefinition(
            id = id,
            name = "Item $id",
            description = "Test item $id",
            rarity = ItemRarity.COMMON,
            tags = immutableListOf(BuildTag.entries[id % BuildTag.entries.size]),
            elements = if (id % 4 == 0) immutableListOf(StatusElement.entries[(id / 4) % StatusElement.entries.size]) else immutableListOf(),
            stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.1f)),
            maxStacks = 1,
            unlockLevel = 1,
        )
    }.toImmutableList()

/** A neutral weapon for Session scenes that only read its id, name, description, price and Rebirth gate. */
internal fun testWeapon(
    id: WeaponId,
    name: String = id.name,
    description: String = "Description",
    cost: Int = 100,
    minimumRebirth: Int = 0,
): WeaponDefinition =
    WeaponDefinition(
        id = id,
        name = name,
        description = description,
        family = WeaponFamily.PROJECTILE,
        element = null,
        tags = listOf(BuildTag.DRIVE, BuildTag.PRECISION),
        key = RelicKey.MOTION,
        signature = CoreShape.entries[id.ordinal % CoreShape.entries.size],
        scaling = WeaponScaling.SPEED,
        cadence = WeaponCadence.DISCRETE,
        permanentUnlockCost = cost,
        minimumRebirth = minimumRebirth,
        masteryForms = listOf("Test form one", "Test form two", "Test form three"),
        signatureText = "Test signature twist.",
    )
