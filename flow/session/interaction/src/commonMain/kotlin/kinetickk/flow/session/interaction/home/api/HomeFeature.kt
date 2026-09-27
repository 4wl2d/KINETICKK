// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.foundation.collections.ImmutableSet
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
    val unlockedCoreShapes: ImmutableSet<CoreShape>,
    /** Cumulative achievements that unlock forms (progress behind a locked form's (!)). */
    val characterAchievements: CharacterAchievementProgress = CharacterAchievementProgress(),
    val discoveredRelicCount: Int = 0,
    val relicCount: Int = 0,
    /** Catalog name (English content key) of the selected starting weapon, if cataloged. */
    val startingWeaponName: String? = null,
    /** True when Matter covers the unlock cost of at least one locked weapon. */
    val weaponUnlockAffordable: Boolean = false,
    val labRanks: Int = 0,
    val labMaxRanks: Int = 0,
    val labMaxedUpgrades: Int = 0,
    val labUpgradeCount: Int = 0,
) {
    fun coreShape(shape: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == shape }

    fun isCoreShapeUnlocked(shape: CoreShape): Boolean =
        shape in unlockedCoreShapes
}

sealed interface HomeOutput {
    data class SelectCoreShape(val shape: CoreShape) : HomeOutput
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
