// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.home.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.CoreShape
import kinetickk.core.content.RebirthProfile

data class HomeUiModel(
    val coreShape: CoreShape,
    val totalMatter: Long,
    val lifetimeMatter: Long,
    val discoveredItemCount: Int,
    val unlockedWeaponCount: Int,
    val rebirthLevel: Int,
    val rebirthProfile: RebirthProfile,
    val canRebirth: Boolean,
) {
    fun isCoreShapeUnlocked(shape: CoreShape): Boolean = lifetimeMatter >= shape.unlockMatter
}

sealed interface HomeOutput {
    data object StartRun : HomeOutput
    data object OpenSettings : HomeOutput
    data object OpenLab : HomeOutput
    data object OpenArmory : HomeOutput
    data object OpenRebirth : HomeOutput
    data object OpenCodex : HomeOutput
    data class Cue(val cue: AudioCue) : HomeOutput
}

interface HomeFeature {
    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (HomeOutput) -> Unit,
    )
}

val CoreShape.unlockMatter: Long
    get() = when (this) {
        CoreShape.ORB -> 0L
        CoreShape.PRISM -> 25L
        CoreShape.SHARD -> 90L
    }
