// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicValue
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.profile.interaction.rebirth.api.RebirthBuildModel
import kinetickk.ball.profile.interaction.rebirth.impl.RebirthBuildContent
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/*
 * The game's shipped copy for text-fit tests: mirrors `defaultWeapons()`
 * in ball/content/impl DefaultCatalogData.kt and DefaultRebirthData.kt, and the Rebirth-gated
 * Relics and items with a few Heirloom candidates from DefaultRelicData.kt and DefaultItemData.kt
 * (names, aspects, rarities, tags, elements and gates as shipped; the interaction module
 * cannot depend on the content implementation). Russian text comes from the content API's
 * phrase table through `localizedContent`, exactly as on screen.
 */

internal val CatalogWeapons: ImmutableList<WeaponDefinition> = listOf(
    WeaponDefinition(WeaponId.FLUX_WAKE, "Flux Wake", "High-speed movement leaves a damaging trail that lingers behind the Core.", listOf("MOMENTUM", "TRAIL", "AREA"), 0),
    WeaponDefinition(WeaponId.MORNINGSTAR, "Morningstar", "A heavy orbital mass converts velocity and mass into crushing contact damage.", listOf("ORBITAL", "IMPACT", "MASS"), 25),
    WeaponDefinition(WeaponId.PHASE_LATTICE, "Phase Lattice", "A pulsing gravity ring damages enemies held near its unstable perimeter.", listOf("AURA", "CONTROL", "PHASE"), 55),
    WeaponDefinition(WeaponId.NULL_LANCE, "Null Lance", "Momentum periodically projects a piercing lance along the Core's travel vector.", listOf("PIERCE", "DIRECTIONAL", "MOMENTUM"), 95),
    WeaponDefinition(WeaponId.GRAVITY_MINES, "Gravity Mines", "Braking plants implosive mines that pull enemies inward before detonating.", listOf("MINE", "PULL", "BRAKE"), 145),
    WeaponDefinition(WeaponId.ION_SWARM, "Ion Swarm", "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.", listOf("DRONE", "SEEKING", "RAPID"), 215),
    WeaponDefinition(WeaponId.RIFT_BLADES, "Rift Blades", "Paired blades tear outward through enemies and return across their original paths.", listOf("BLADE", "RETURNING", "CRITICAL"), 305),
    WeaponDefinition(WeaponId.ARC_COIL, "Arc Coil", "Stored kinetic charge erupts as lightning that chains between nearby enemies.", listOf("CHAIN", "LIGHTNING", "CHARGE"), 430),
    WeaponDefinition(WeaponId.QUASAR_CANNON, "Quasar Cannon", "A slow-charging cannon compresses momentum into a colossal piercing projectile.", listOf("HEAVY", "CHARGE", "PIERCE"), 610),
    WeaponDefinition(WeaponId.ENTROPY_FIELD, "Entropy Field", "A widening decay field slows hostile motion and deals escalating damage over time.", listOf("AURA", "DECAY", "SLOW"), 860),
    WeaponDefinition(WeaponId.SINGULARITY_SPEAR, "Singularity Spear", "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.", listOf("ULTIMATE", "OVERDRIVE", "BOSS"), 1_200),
    WeaponDefinition(WeaponId.PRISM_RELAY, "Prism Relay", "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.", listOf("SEEKING", "RICOCHET", "REFRACTION"), 1_650),
).toImmutableList()

/** The shipped Core Matrix: its data lives in the content API, so the fixture is the real catalog. */
internal val CatalogMatrixNodes: ImmutableList<MatrixNodeDefinition> = defaultMatrixNodes()

internal fun catalogRebirthProfile(tier: Int): RebirthProfile {
    val swarmRanks = (tier + 2) / 3
    val fortifiedRanks = (tier + 1) / 3
    val overclockedRanks = tier / 3
    val directive = when {
        tier == 0 -> RebirthDirective.BASELINE
        (tier - 1) % 3 == 0 -> RebirthDirective.SWARM
        (tier - 1) % 3 == 1 -> RebirthDirective.FORTIFIED
        else -> RebirthDirective.OVERCLOCKED
    }
    return RebirthProfile(
        tier = tier,
        directive = directive,
        openingEnemyCount = 5 + (tier + 1) / 2,
        enemyCapMultiplier = 1f + tier * 0.08f + swarmRanks * 0.01f,
        spawnRateMultiplier = 1f + tier * 0.06f + swarmRanks * 0.01f,
        enemyHealthMultiplier = 1f + tier * 0.18f + tier * tier * 0.012f + fortifiedRanks * 0.02f,
        enemySpeedMultiplier = 1f + tier * 0.025f + overclockedRanks * 0.005f,
        incomingDamageMultiplier = 1f + tier * 0.08f + overclockedRanks * 0.005f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = tier * 8f,
        playerPowerMultiplier = 1f + tier * 0.05f,
        playerIntegrityBonus = tier * 3f,
        matterGainMultiplier = 1f + tier * 0.12f + fortifiedRanks * 0.01f,
        bonusRerolls = tier / 5,
        maximumActiveEnemies = 120,
        minimumSpawnIntervalSeconds = 0.09f,
        minimumEliteIntervalSeconds = 24f,
    )
}

private fun catalogRelic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    key: RelicKey,
    gate: Int = 0,
    upside: String = "$name upside",
    values: List<RelicValue> = emptyList(),
) = RelicDefinition(
    id = id,
    name = name,
    aspect = aspect,
    keys = immutableListOf(key),
    attunedTags = immutableListOf(),
    upside = upside,
    liability = "$name liability",
    values = values.toImmutableList(),
    minimumRebirth = gate,
)

/**
 * The three Rebirth-gated Sovereign relics (with their shipped rule rewrites, which the build table
 * shows) and four Heirloom candidates (long names in both languages).
 */
internal val CatalogRebirthRelics: ImmutableList<RelicDefinition> = listOf(
    catalogRelic(RelicId.EXECUTIONERS_PRISM, "Executioner's Prism", RelicAspect.KINETIC, RelicKey.CRITICAL),
    catalogRelic(RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL, RelicKey.HEAT),
    catalogRelic(RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO, RelicKey.COLD),
    catalogRelic(RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION, RelicKey.CHARGE),
    catalogRelic(RelicId.AGONY_SCEPTER, "Agony Scepter", RelicAspect.SOVEREIGN, RelicKey.ECHO, gate = 1,
        upside = "Your weapon takes its Forbidden form. Every rank deepens the mutation."),
    catalogRelic(RelicId.HOLLOW_CROWN, "Hollow Crown", RelicAspect.SOVEREIGN, RelicKey.VOID, gate = 3,
        upside = "Each empty Relic slot grants {0} % damage and 8 % activation speed.", values = listOf(RelicValue(35f))),
    catalogRelic(RelicId.OUROBOROS_SEAL, "Ouroboros Seal", RelicAspect.SOVEREIGN, RelicKey.LIFE, gate = 5,
        upside = "Once per run, lethal damage instead restores your integrity, releases a nova for {0} damage and " +
            "grants two seconds of invulnerability. Then the seal is spent but keeps its slot.",
        values = listOf(RelicValue(120f, 60f))),
).toImmutableList()

private fun catalogItem(
    id: Int,
    name: String,
    description: String,
    tags: List<BuildTag>,
    elements: List<StatusElement>,
    rule: ItemRule,
    gate: Int,
) = ItemDefinition(
    id = id,
    name = name,
    description = description,
    rarity = ItemRarity.LEGENDARY,
    tags = tags.toImmutableList(),
    elements = elements.toImmutableList(),
    rules = immutableListOf(rule),
    maxStacks = 1,
    unlockLevel = 16,
    minimumRebirth = gate,
)

/** The three Rebirth-gated legendaries with their shipped descriptions. */
internal val CatalogRebirthItems: ImmutableList<ItemDefinition> = listOf(
    catalogItem(0, "Solar Choir", "When one fire sings, the others answer.", listOf(BuildTag.CHAIN), listOf(StatusElement.THERMAL),
        ItemRule.BreakSpread(StatusElement.THERMAL, 3, 140f), gate = 2),
    catalogItem(1, "Glacial Heart", "Collide with the frozen and the whole field cracks.", listOf(BuildTag.RAM),
        listOf(StatusElement.CRYO), ItemRule.BreakPower(StatusElement.CRYO, 1f), gate = 2),
    catalogItem(2, "Resonance Fork", "A reaction that rings on after it ends.", emptyList(), emptyList(),
        ItemRule.ReactionResidue(2), gate = 4),
).toImmutableList()

/** Heirlooms of a victory with four bound relics, in slot order. */
internal val CatalogHeirlooms: List<RelicId> =
    listOf(RelicId.STORMHEART_CAPACITOR, RelicId.EXECUTIONERS_PRISM, RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO)

/** The screen's build model between [current] and [target] with the shipped Reborn odds. */
internal fun catalogRebirthBuild(current: Int, target: Int, heirlooms: List<RelicId> = CatalogHeirlooms): RebirthBuildModel =
    RebirthBuildContent(CatalogRebirthRelics, CatalogRebirthItems, BuildPolicy()).model(current, target, heirlooms)
