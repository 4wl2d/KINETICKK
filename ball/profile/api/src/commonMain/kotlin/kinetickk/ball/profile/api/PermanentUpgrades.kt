// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * Bought Refit levels indexed by [CoreShape.ordinal] and Honing levels indexed by [WeaponId.ordinal];
 * 0 is not upgraded. Sizes and level ranges are checked where a profile is admitted against the
 * captured content policy (the same rule as [MatrixProgress]).
 */
data class PermanentUpgrades(
    val characterLevels: ImmutableList<Int> = ImmutableList.copyOf(List(CoreShape.entries.size) { 0 }),
    val weaponLevels: ImmutableList<Int> = ImmutableList.copyOf(List(WeaponId.entries.size) { 0 }),
) {
    constructor(characterLevels: List<Int>, weaponLevels: List<Int>) :
        this(characterLevels.toImmutableList(), weaponLevels.toImmutableList())

    fun characterLevel(shape: CoreShape): Int = characterLevels.getOrElse(shape.ordinal) { 0 }

    fun weaponLevel(id: WeaponId): Int = weaponLevels.getOrElse(id.ordinal) { 0 }

    /** Nothing bought: every level is 0. */
    val isDefault: Boolean get() = characterLevels.all { it == 0 } && weaponLevels.all { it == 0 }
}
