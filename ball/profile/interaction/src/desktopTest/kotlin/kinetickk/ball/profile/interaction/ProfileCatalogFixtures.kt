// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/*
 * The game's shipped copy for text-fit tests: mirrors `defaultWeapons()` and `defaultMetaUpgrades()`
 * in ball/content/impl DefaultCatalogData.kt and DefaultRebirthData.kt (the interaction module
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

internal val CatalogMetaUpgrades: ImmutableList<MetaUpgradeDefinition> = listOf(
    MetaUpgradeDefinition(MetaUpgradeId.CORE_INTEGRITY, "Core Integrity", "+10 maximum integrity at the start of every run.", 10, 18, ItemModifier(ItemEffect.MAX_INTEGRITY, 10f)),
    MetaUpgradeDefinition(MetaUpgradeId.KINETIC_AMPLIFIER, "Kinetic Amplifier", "+5% collision damage at the start of every run.", 10, 22, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.MAGNETIC_RESONANCE, "Magnetic Resonance", "+4% magnetic pull strength at the start of every run.", 8, 24, ItemModifier(ItemEffect.MAGNETISM, 0.04f)),
    MetaUpgradeDefinition(MetaUpgradeId.CRYO_VENTS, "Cryo Vents", "+5% heat dissipation at the start of every run.", 8, 26, ItemModifier(ItemEffect.COOLING, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.DASH_CAPACITOR, "Dash Capacitor", "+5% dash impulse at the start of every run.", 8, 30, ItemModifier(ItemEffect.DASH_POWER, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.SALVAGE_PROTOCOL, "Salvage Protocol", "+5% Kinetic Matter gained during runs.", 10, 34, ItemModifier(ItemEffect.MATTER_GAIN, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.DATA_ARCHIVE, "Data Archive", "+5% Data gained during runs.", 10, 38, ItemModifier(ItemEffect.DATA_GAIN, 0.05f)),
    MetaUpgradeDefinition(MetaUpgradeId.ARMORY_LICENSE, "Armory License", "+4% weapon power per rank.", 12, 45, ItemModifier(ItemEffect.WEAPON_POWER, 0.04f)),
).toImmutableList()

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
