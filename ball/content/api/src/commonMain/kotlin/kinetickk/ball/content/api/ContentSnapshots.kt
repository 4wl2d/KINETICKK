// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList

data class CoreShapeDefinition(
    val id: CoreShape,
    val unlockLifetimeMatter: Long,
) {
    init {
        require(unlockLifetimeMatter >= 0L) { "Core shape unlock cost must be non-negative" }
    }
}

data class RebirthPolicySnapshot(
    val minimumLevel: Int,
    val maximumLevel: Int,
    val profiles: ImmutableList<RebirthProfile>,
    val maxActiveEnemies: Int,
    val minSpawnIntervalSeconds: Float,
    val minEliteIntervalSeconds: Float,
) {
    init {
        require(minimumLevel >= 0) { "Minimum Rebirth level must be non-negative" }
        require(maximumLevel >= minimumLevel) {
            "Maximum Rebirth level must not precede minimum level"
        }
        require(profiles.isNotEmpty()) { "Rebirth profiles must not be empty" }
        require(maxActiveEnemies > 0) { "Rebirth enemy cap must be positive" }
        require(minSpawnIntervalSeconds.isFinite() && minSpawnIntervalSeconds > 0f) {
            "Minimum spawn interval must be finite and positive"
        }
        require(minEliteIntervalSeconds.isFinite() && minEliteIntervalSeconds > 0f) {
            "Minimum elite interval must be finite and positive"
        }
    }

    /** Preserves the previous behavior of clamping out-of-range requested levels. */
    fun profile(level: Int): RebirthProfile = profiles[level.coerceIn(minimumLevel, maximumLevel) - minimumLevel]
}

data class ProfilePolicySnapshot(
    val version: ContentVersion,
    val itemCount: Int,
    val coreShapes: ImmutableList<CoreShapeDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
    val rebirth: RebirthPolicySnapshot,
) {
    init {
        require(itemCount >= 0) { "Item count must be non-negative" }
    }

    fun containsItem(id: Int): Boolean = id in 0 until itemCount

    fun coreShape(id: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == id }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun metaUpgrade(id: MetaUpgradeId): MetaUpgradeDefinition =
        metaUpgrades.first { definition -> definition.id == id }
}

data class GameplayContentSnapshot(
    val version: ContentVersion,
    val items: ImmutableList<ItemDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val weaponMasteries: ImmutableList<WeaponMastery>,
    val metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
    val relics: ImmutableList<RelicDefinition>,
    val rebirth: RebirthPolicySnapshot,
    val relicPolicy: RelicPolicy,
) {
    fun item(id: Int): ItemDefinition? = items.getOrNull(id)?.takeIf { item -> item.id == id }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun weaponMasteryForLevel(level: Int): WeaponMastery =
        weaponMasteries.last { mastery -> level >= mastery.minimumLevel }

    fun weaponMasteryAfter(level: Int): WeaponMastery? =
        weaponMasteries.firstOrNull { mastery -> level < mastery.minimumLevel }

    fun metaUpgrade(id: MetaUpgradeId): MetaUpgradeDefinition =
        metaUpgrades.first { definition -> definition.id == id }

    fun relic(id: RelicId): RelicDefinition =
        relics.first { definition -> definition.id == id }
}

data class UiCatalogSnapshot(
    val version: ContentVersion,
    val items: ImmutableList<ItemDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val weaponMasteries: ImmutableList<WeaponMastery>,
    val metaUpgrades: ImmutableList<MetaUpgradeDefinition>,
    val relics: ImmutableList<RelicDefinition>,
    val coreShapes: ImmutableList<CoreShapeDefinition>,
    val rebirth: RebirthPolicySnapshot,
    val relicPolicy: RelicPolicy,
) {
    fun item(id: Int): ItemDefinition? = items.getOrNull(id)?.takeIf { item -> item.id == id }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun weaponMasteryForLevel(level: Int): WeaponMastery =
        weaponMasteries.last { mastery -> level >= mastery.minimumLevel }

    fun weaponMasteryAfter(level: Int): WeaponMastery? =
        weaponMasteries.firstOrNull { mastery -> level < mastery.minimumLevel }

    fun metaUpgrade(id: MetaUpgradeId): MetaUpgradeDefinition =
        metaUpgrades.first { definition -> definition.id == id }

    fun relic(id: RelicId): RelicDefinition =
        relics.first { definition -> definition.id == id }

    fun coreShape(id: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == id }
}

/** Application-lifetime, query-only authority over shared game content. */
interface ContentCatalog {
    val version: ContentVersion

    fun profilePolicy(): ProfilePolicySnapshot

    fun gameplayContent(): GameplayContentSnapshot

    fun uiCatalog(): UiCatalogSnapshot
}
