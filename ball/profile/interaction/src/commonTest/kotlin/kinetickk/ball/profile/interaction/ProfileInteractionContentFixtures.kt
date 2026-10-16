// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

internal val TestMetaUpgrades: ImmutableList<MetaUpgradeDefinition> =
    MetaUpgradeId.entries.mapIndexed { index, id ->
        MetaUpgradeDefinition(
            id = id,
            name = "Test ${id.name}",
            description = "Injected test upgrade $index",
            maxRanks = index + 2,
            baseCost = (index + 1) * 100,
            modifierPerRank = ItemModifier(ItemEffect.MAX_INTEGRITY, 1f),
        )
    }.toImmutableList()

internal val TestWeapons: ImmutableList<WeaponDefinition> = WeaponId.entries.mapIndexed { index, id ->
    WeaponDefinition(
        id = id,
        name = "Test ${id.name}",
        description = "Injected test weapon $index",
        tags = listOf("TEST"),
        permanentUnlockCost = index * 100,
    )
}.toImmutableList()

internal val TestWeaponMasteries: ImmutableList<WeaponMastery> = WeaponMastery.entries.toImmutableList()

internal fun testRebirthPolicy(maximumLevel: Int = 10): RebirthPolicySnapshot = RebirthPolicySnapshot(
    minimumLevel = 0,
    maximumLevel = maximumLevel,
    profiles = (0..maximumLevel).map(::testRebirthProfile).toImmutableList(),
    maxActiveEnemies = 120,
    minSpawnIntervalSeconds = 0.09f,
    minEliteIntervalSeconds = 24f,
)

private fun testRebirthProfile(tier: Int): RebirthProfile = RebirthProfile(
    tier = tier,
    directive = if (tier == 0) RebirthDirective.BASELINE else RebirthDirective.SWARM,
    openingEnemyCount = 5 + tier,
    enemyCapMultiplier = 1f,
    spawnRateMultiplier = 1f,
    enemyHealthMultiplier = 1f,
    enemySpeedMultiplier = 1f,
    incomingDamageMultiplier = 1f,
    eliteRateMultiplier = 1f,
    threatTimeOffsetSeconds = 0f,
    playerPowerMultiplier = 1f,
    playerIntegrityBonus = 0f,
    matterGainMultiplier = 1f,
    bonusRerolls = 0,
    maximumActiveEnemies = 120,
    minimumSpawnIntervalSeconds = 0.09f,
    minimumEliteIntervalSeconds = 24f,
)

internal fun testRelic(id: RelicId, aspect: RelicAspect = RelicAspect.KINETIC, gate: Int = 0): RelicDefinition = RelicDefinition(
    id = id,
    name = "Test ${id.name}",
    aspect = aspect,
    keys = immutableListOf(RelicKey.MOTION),
    attunedTags = immutableListOf(),
    upside = "Injected upside ${id.name}",
    liability = "Injected liability ${id.name}",
    minimumRebirth = gate,
)

/** Two ungated relics and three Rebirth-gated ones (tiers 1, 3 and 5), in catalog order. */
internal val TestRelics: ImmutableList<RelicDefinition> = listOf(
    testRelic(RelicId.PERPETUAL_VECTOR),
    testRelic(RelicId.FURNACE_COVENANT, RelicAspect.THERMAL),
    testRelic(RelicId.OUROBOROS_SEAL, RelicAspect.SOVEREIGN, gate = 5),
    testRelic(RelicId.AGONY_SCEPTER, RelicAspect.SOVEREIGN, gate = 1),
    testRelic(RelicId.HOLLOW_CROWN, RelicAspect.SOVEREIGN, gate = 3),
).toImmutableList()

internal fun testItem(id: Int, gate: Int, rarity: ItemRarity = ItemRarity.LEGENDARY): ItemDefinition = ItemDefinition(
    id = id,
    name = "Test item $id",
    description = "Injected item $id",
    rarity = rarity,
    tags = immutableListOf(BuildTag.CHAIN),
    elements = immutableListOf(StatusElement.THERMAL),
    rules = immutableListOf(ItemRule.ReactionResidue(1)),
    maxStacks = 1,
    unlockLevel = 1,
    minimumRebirth = gate,
)

/** One ungated item, two gated at tier 2 and one at tier 4. */
internal val TestItems: ImmutableList<ItemDefinition> =
    listOf(testItem(0, gate = 0, ItemRarity.COMMON), testItem(1, gate = 2), testItem(2, gate = 4), testItem(3, gate = 2)).toImmutableList()
