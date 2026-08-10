// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.api

import androidx.compose.runtime.Composable
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.content.api.WeaponId

data class ArmoryRenderModel(
    val totalMatter: Long,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val activeRunWeapon: WeaponId?,
)

sealed interface ArmoryOutput {
    data object Back : ArmoryOutput
}

interface ArmoryFeature {
    @Composable
    fun Content(
        activeRunWeapon: WeaponId?,
        onOutput: (ArmoryOutput) -> Unit,
    )
}
