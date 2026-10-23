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
    /** Cumulative achievements that unlock forms. */
    val characterAchievements: CharacterAchievementProgress = CharacterAchievementProgress(),
    val discoveredRelicCount: Int = 0,
    val relicCount: Int = 0,
    /** Catalog name (English content key) of the selected starting weapon, if cataloged. */
    val startingWeaponName: String? = null,
    /**
     * True when the Profile would accept a Workshop purchase now (a weapon unlock, a Matrix rank, a
     * Refit or a Honing level): the Workshop row's Affordable stamp.
     */
    val workshopAffordable: Boolean = false,
    /** Refit and Honing levels bought, over every level the Workshop sells (0 while unread). */
    val upgradeLevels: Int = 0,
    val upgradeLevelCap: Int = 0,
    /** Owned Core Matrix ranks over all ranks, and owned nodes over all nodes (0 while unread). */
    val matrixRanks: Int = 0,
    val matrixMaxRanks: Int = 0,
    val matrixOwnedNodes: Int = 0,
    val matrixNodeCount: Int = 0,
    /** Rebirth rank (one above the highest clear): the badge on the Rebirth chip. */
    val rebirthRank: Int = rebirthLevel,
    /** Every Rebirth tier cleared: the badge reads Ascendant under the gold crown. */
    val ascendant: Boolean = false,
) {
    fun coreShape(shape: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == shape }

    fun isCoreShapeUnlocked(shape: CoreShape): Boolean =
        shape in unlockedCoreShapes
}

sealed interface HomeOutput {
    data object StartRun : HomeOutput

    /** Open the Workshop on the tab it showed last; characters are chosen there. */
    data object OpenWorkshop : HomeOutput
    data object OpenRebirth : HomeOutput
    data object OpenCodex : HomeOutput
    data object OpenSettings : HomeOutput
}

interface HomeFeature {
    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (HomeOutput) -> Unit,
    )
}
