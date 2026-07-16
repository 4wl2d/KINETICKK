// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.domain

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Profile-owned stable core-shape identity. Ordinals match the legacy Game contract. */
enum class CoreShape { ORB, PRISM, SHARD }

object CoreShapeProgression {
    fun requiredLifetimeMatter(shape: CoreShape): Long = when (shape) {
        CoreShape.ORB -> 0L
        CoreShape.PRISM -> 25L
        CoreShape.SHARD -> 90L
    }
}

/** Profile-owned stable weapon identity. Ordinals match the legacy Game contract. */
enum class WeaponId {
    FLUX_WAKE,
    MORNINGSTAR,
    PHASE_LATTICE,
    NULL_LANCE,
    GRAVITY_MINES,
    ION_SWARM,
    RIFT_BLADES,
    ARC_COIL,
    QUASAR_CANNON,
    ENTROPY_FIELD,
    SINGULARITY_SPEAR,
    PRISM_RELAY,
}

data class WeaponProgressionDefinition(
    val id: WeaponId,
    val permanentUnlockCost: Int,
) {
    init {
        require(permanentUnlockCost >= 0) { "Weapon unlock cost must be non-negative" }
    }
}

object WeaponCatalog {
    val all: ImmutableList<WeaponProgressionDefinition> = listOf(
        WeaponProgressionDefinition(WeaponId.FLUX_WAKE, 0),
        WeaponProgressionDefinition(WeaponId.MORNINGSTAR, 25),
        WeaponProgressionDefinition(WeaponId.PHASE_LATTICE, 55),
        WeaponProgressionDefinition(WeaponId.NULL_LANCE, 95),
        WeaponProgressionDefinition(WeaponId.GRAVITY_MINES, 145),
        WeaponProgressionDefinition(WeaponId.ION_SWARM, 215),
        WeaponProgressionDefinition(WeaponId.RIFT_BLADES, 305),
        WeaponProgressionDefinition(WeaponId.ARC_COIL, 430),
        WeaponProgressionDefinition(WeaponId.QUASAR_CANNON, 610),
        WeaponProgressionDefinition(WeaponId.ENTROPY_FIELD, 860),
        WeaponProgressionDefinition(WeaponId.SINGULARITY_SPEAR, 1_200),
        WeaponProgressionDefinition(WeaponId.PRISM_RELAY, 1_650),
    ).toImmutableList().also { definitions ->
        check(definitions.size == WeaponId.entries.size)
        check(definitions.withIndex().all { (index, definition) -> definition.id.ordinal == index })
    }

    fun byId(id: WeaponId): WeaponProgressionDefinition = all[id.ordinal]
}

/** Profile-owned stable meta-upgrade identity. Ordinals match the legacy Game contract. */
enum class MetaUpgradeId {
    CORE_INTEGRITY,
    KINETIC_AMPLIFIER,
    MAGNETIC_RESONANCE,
    CRYO_VENTS,
    DASH_CAPACITOR,
    SALVAGE_PROTOCOL,
    DATA_ARCHIVE,
    ARMORY_LICENSE,
}

data class MetaUpgradeDefinition(
    val id: MetaUpgradeId,
    val maxRanks: Int,
    val baseCost: Int,
) {
    init {
        require(maxRanks > 0) { "Meta-upgrade maxRanks must be positive" }
        require(baseCost > 0) { "Meta-upgrade baseCost must be positive" }
    }

    fun cost(level: Int): Int {
        require(level in 0 until maxRanks) { "level must be between 0 and ${maxRanks - 1}" }
        val rank = level.toLong() + 1L
        return (baseCost.toLong() * rank * rank).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

object MetaUpgradeCatalog {
    val all: ImmutableList<MetaUpgradeDefinition> = listOf(
        MetaUpgradeDefinition(MetaUpgradeId.CORE_INTEGRITY, maxRanks = 10, baseCost = 18),
        MetaUpgradeDefinition(MetaUpgradeId.KINETIC_AMPLIFIER, maxRanks = 10, baseCost = 22),
        MetaUpgradeDefinition(MetaUpgradeId.MAGNETIC_RESONANCE, maxRanks = 8, baseCost = 24),
        MetaUpgradeDefinition(MetaUpgradeId.CRYO_VENTS, maxRanks = 8, baseCost = 26),
        MetaUpgradeDefinition(MetaUpgradeId.DASH_CAPACITOR, maxRanks = 8, baseCost = 30),
        MetaUpgradeDefinition(MetaUpgradeId.SALVAGE_PROTOCOL, maxRanks = 10, baseCost = 34),
        MetaUpgradeDefinition(MetaUpgradeId.DATA_ARCHIVE, maxRanks = 10, baseCost = 38),
        MetaUpgradeDefinition(MetaUpgradeId.ARMORY_LICENSE, maxRanks = 12, baseCost = 45),
    ).toImmutableList().also { definitions ->
        check(definitions.size == MetaUpgradeId.entries.size)
        check(definitions.withIndex().all { (index, definition) -> definition.id.ordinal == index })
    }

    fun byId(id: MetaUpgradeId): MetaUpgradeDefinition = all[id.ordinal]
}

/** Profile owns the permanent Codex identity set; Game maps concrete item IDs at the route. */
object ItemCatalogFacts {
    const val ITEM_COUNT: Int = 400
}

enum class RebirthDirective(
    val displayName: String,
    val description: String,
) {
    BASELINE("Baseline", "The original Architect cycle."),
    SWARM("Swarm", "This cycle adds another rank of hostile density."),
    FORTIFIED("Fortified", "This cycle adds another rank of enemy integrity."),
    OVERCLOCKED("Overclocked", "This cycle adds another rank of hostile speed and damage."),
}

/** Profile-owned immutable run tuning transferred through RunConfiguration. */
data class RebirthProfile(
    val tier: Int,
    val directive: RebirthDirective,
    val openingEnemyCount: Int,
    val enemyCapMultiplier: Float,
    val spawnRateMultiplier: Float,
    val enemyHealthMultiplier: Float,
    val enemySpeedMultiplier: Float,
    val incomingDamageMultiplier: Float,
    val eliteRateMultiplier: Float,
    val threatTimeOffsetSeconds: Float,
    val playerPowerMultiplier: Float,
    val playerIntegrityBonus: Float,
    val matterGainMultiplier: Float,
    val bonusRerolls: Int,
) {
    fun enemyCap(base: Int): Int = min(
        RebirthProgression.MAX_ACTIVE_ENEMIES,
        max(base, floor(base.coerceAtLeast(0) * enemyCapMultiplier).toInt()),
    )

    fun spawnInterval(baseSeconds: Float): Float =
        max(RebirthProgression.MIN_SPAWN_INTERVAL_SECONDS, baseSeconds / spawnRateMultiplier)

    fun eliteInterval(baseSeconds: Float): Float =
        max(RebirthProgression.MIN_ELITE_INTERVAL_SECONDS, baseSeconds / eliteRateMultiplier)

    fun enemyHealth(base: Float): Float = base * enemyHealthMultiplier
}

object RebirthProgression {
    const val MAX_LEVEL = 10
    const val MAX_ACTIVE_ENEMIES = 120
    const val MIN_SPAWN_INTERVAL_SECONDS = 0.09f
    const val MIN_ELITE_INTERVAL_SECONDS = 24f

    fun profile(level: Int): RebirthProfile {
        val tier = level.coerceIn(0, MAX_LEVEL)
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
        )
    }
}
