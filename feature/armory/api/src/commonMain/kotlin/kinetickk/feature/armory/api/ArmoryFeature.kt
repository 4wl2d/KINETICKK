// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.armory.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.ImmutableSet
import kinetickk.core.content.WeaponId

data class ArmoryRenderModel(
    val totalMatter: Long,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val activeRunWeapon: WeaponId?,
)

sealed interface ArmoryOutput {
    data object Back : ArmoryOutput
    data class Cue(val cue: AudioCue) : ArmoryOutput
}

interface ArmoryFeature {
    @Composable
    fun Content(
        activeRunWeapon: WeaponId?,
        onOutput: (ArmoryOutput) -> Unit,
    )
}
