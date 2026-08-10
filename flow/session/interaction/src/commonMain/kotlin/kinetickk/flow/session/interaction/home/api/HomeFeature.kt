// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.foundation.collections.ImmutableList

data class HomeUiModel(
    val coreShape: CoreShape,
    val totalMatter: Long,
    val lifetimeMatter: Long,
    val discoveredItemCount: Int,
    val unlockedWeaponCount: Int,
    val rebirthLevel: Int,
    val rebirthProfile: RebirthProfile,
    val canRebirth: Boolean,
    val coreShapes: ImmutableList<CoreShapeDefinition>,
    val itemCount: Int,
    val weaponCount: Int,
) {
    fun coreShape(shape: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == shape }

    fun isCoreShapeUnlocked(shape: CoreShape): Boolean =
        lifetimeMatter >= coreShape(shape).unlockLifetimeMatter
}

sealed interface HomeOutput {
    data object StartRun : HomeOutput
    data object OpenSettings : HomeOutput
    data object OpenLab : HomeOutput
    data object OpenArmory : HomeOutput
    data object OpenRebirth : HomeOutput
    data object OpenCodex : HomeOutput
}

interface HomeFeature {
    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (HomeOutput) -> Unit,
    )
}
