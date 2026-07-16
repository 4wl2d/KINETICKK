// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.state

import kinetickk.features.profile.nucleus.domain.CoreShape
import kinetickk.features.profile.nucleus.domain.CoreShapeProgression
import kinetickk.features.profile.nucleus.domain.ItemCatalogFacts
import kinetickk.features.profile.nucleus.domain.MetaUpgradeCatalog
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId
import kinetickk.features.profile.nucleus.domain.RebirthProgression
import kinetickk.features.profile.nucleus.domain.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet

/** Immutable permanent progression authority for one local player. */
class PlayerProfileValues(
    val matter: Long = 0L,
    val lifetimeMatter: Long = matter,
    metaRanks: Iterable<Int> = List(MetaUpgradeId.entries.size) { 0 },
    val selectedWeapon: WeaponId = WeaponId.FLUX_WAKE,
    unlockedWeapons: Iterable<WeaponId> = setOf(WeaponId.FLUX_WAKE),
    val selectedCoreShape: CoreShape = CoreShape.ORB,
    discoveredItemIds: Iterable<Int> = emptySet(),
    val rebirthLevel: Int = 0,
    val highestClearedRebirth: Int = -1,
) {
    val metaRanks: ImmutableList<Int> = metaRanks.toImmutableList()
    val unlockedWeapons: ImmutableSet<WeaponId> = unlockedWeapons.toImmutableSet()
    val discoveredItemIds: ImmutableSet<Int> = discoveredItemIds.toImmutableSet()

    init {
        require(matter >= 0L) { "matter must not be negative" }
        require(lifetimeMatter >= matter) { "lifetimeMatter must be at least matter" }
        require(this.metaRanks.size == MetaUpgradeId.entries.size) {
            "metaRanks must contain exactly ${MetaUpgradeId.entries.size} values"
        }
        this.metaRanks.forEachIndexed { index, rank ->
            require(rank in 0..MetaUpgradeCatalog.all[index].maxRanks) {
                "meta rank $index is outside its supported range"
            }
        }
        require(WeaponId.FLUX_WAKE in this.unlockedWeapons) {
            "Flux Wake must always be unlocked"
        }
        require(this.unlockedWeapons.size <= WeaponId.entries.size) {
            "unlockedWeapons exceeds the catalog"
        }
        require(selectedWeapon in this.unlockedWeapons) {
            "selectedWeapon must be unlocked"
        }
        require(lifetimeMatter >= CoreShapeProgression.requiredLifetimeMatter(selectedCoreShape)) {
            "selectedCoreShape must be unlocked"
        }
        require(this.discoveredItemIds.size <= ItemCatalogFacts.ITEM_COUNT) {
            "discoveredItemIds exceeds the catalog"
        }
        require(this.discoveredItemIds.all { it in 0 until ItemCatalogFacts.ITEM_COUNT }) {
            "discoveredItemIds contains an unknown item"
        }
        require(rebirthLevel in 0..RebirthProgression.MAX_LEVEL) {
            "rebirthLevel is outside its supported range"
        }
        require(highestClearedRebirth in -1..rebirthLevel) {
            "highestClearedRebirth must be between -1 and rebirthLevel"
        }
    }

    internal fun updated(
        matter: Long = this.matter,
        lifetimeMatter: Long = this.lifetimeMatter,
        metaRanks: Iterable<Int> = this.metaRanks,
        selectedWeapon: WeaponId = this.selectedWeapon,
        unlockedWeapons: Iterable<WeaponId> = this.unlockedWeapons,
        selectedCoreShape: CoreShape = this.selectedCoreShape,
        discoveredItemIds: Iterable<Int> = this.discoveredItemIds,
        rebirthLevel: Int = this.rebirthLevel,
        highestClearedRebirth: Int = this.highestClearedRebirth,
    ): PlayerProfileValues = PlayerProfileValues(
        matter = matter,
        lifetimeMatter = lifetimeMatter,
        metaRanks = metaRanks,
        selectedWeapon = selectedWeapon,
        unlockedWeapons = unlockedWeapons,
        selectedCoreShape = selectedCoreShape,
        discoveredItemIds = discoveredItemIds,
        rebirthLevel = rebirthLevel,
        highestClearedRebirth = highestClearedRebirth,
    )

    override fun equals(other: Any?): Boolean =
        other is PlayerProfileValues &&
            matter == other.matter &&
            lifetimeMatter == other.lifetimeMatter &&
            metaRanks == other.metaRanks &&
            selectedWeapon == other.selectedWeapon &&
            unlockedWeapons == other.unlockedWeapons &&
            selectedCoreShape == other.selectedCoreShape &&
            discoveredItemIds == other.discoveredItemIds &&
            rebirthLevel == other.rebirthLevel &&
            highestClearedRebirth == other.highestClearedRebirth

    override fun hashCode(): Int {
        var result = matter.hashCode()
        result = 31 * result + lifetimeMatter.hashCode()
        result = 31 * result + metaRanks.hashCode()
        result = 31 * result + selectedWeapon.hashCode()
        result = 31 * result + unlockedWeapons.hashCode()
        result = 31 * result + selectedCoreShape.hashCode()
        result = 31 * result + discoveredItemIds.hashCode()
        result = 31 * result + rebirthLevel
        return 31 * result + highestClearedRebirth
    }

    override fun toString(): String =
        "PlayerProfileValues(" +
            "matter=$matter, " +
            "lifetimeMatter=$lifetimeMatter, " +
            "metaRanks=$metaRanks, " +
            "selectedWeapon=$selectedWeapon, " +
            "unlockedWeapons=$unlockedWeapons, " +
            "selectedCoreShape=$selectedCoreShape, " +
            "discoveredItemIds=$discoveredItemIds, " +
            "rebirthLevel=$rebirthLevel, " +
            "highestClearedRebirth=$highestClearedRebirth)"
}
