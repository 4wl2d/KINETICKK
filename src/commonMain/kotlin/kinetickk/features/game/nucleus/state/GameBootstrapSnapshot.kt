// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet

/** Field-minimized bootstrap captured by Game; it is not the persistence wire model. */
class GameBootstrapSnapshot(
    val matter: Long = 0,
    val lifetimeMatter: Long = matter,
    val coreShapeIndex: Int = 0,
    val selectedWeaponIndex: Int = 0,
    unlockedWeaponIndices: Set<Int> = setOf(0),
    metaLevels: List<Int> = List(8) { 0 },
    discoveredItemIds: Set<Int> = emptySet(),
    val settings: GameSettings = GameSettings(),
    val rebirthLevel: Int = 0,
    val highestClearedRebirth: Int = -1,
) {
    val unlockedWeaponIndices: ImmutableSet<Int> = unlockedWeaponIndices.toImmutableSet()
    val metaLevels: ImmutableList<Int> = metaLevels.toImmutableList()
    val discoveredItemIds: ImmutableSet<Int> = discoveredItemIds.toImmutableSet()

    override fun equals(other: Any?): Boolean =
        other is GameBootstrapSnapshot &&
            matter == other.matter &&
            lifetimeMatter == other.lifetimeMatter &&
            coreShapeIndex == other.coreShapeIndex &&
            selectedWeaponIndex == other.selectedWeaponIndex &&
            unlockedWeaponIndices == other.unlockedWeaponIndices &&
            metaLevels == other.metaLevels &&
            discoveredItemIds == other.discoveredItemIds &&
            settings == other.settings &&
            rebirthLevel == other.rebirthLevel &&
            highestClearedRebirth == other.highestClearedRebirth

    override fun hashCode(): Int {
        var result = matter.hashCode()
        result = 31 * result + lifetimeMatter.hashCode()
        result = 31 * result + coreShapeIndex
        result = 31 * result + selectedWeaponIndex
        result = 31 * result + unlockedWeaponIndices.hashCode()
        result = 31 * result + metaLevels.hashCode()
        result = 31 * result + discoveredItemIds.hashCode()
        result = 31 * result + settings.hashCode()
        result = 31 * result + rebirthLevel
        return 31 * result + highestClearedRebirth
    }
}
