// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.content.api.WeaponId

data class ArmoryRenderModel(
    val totalMatter: Long,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val activeRunWeapon: WeaponId?,
    /** Rebirth rank the weapons' unlock gates compare against. */
    val rebirthRank: Int,
    /** The character the next run starts with: the weapons whose signature it is get the signature bonus. */
    val character: CoreShape = CoreShape.ORB,
    /** The Profile's heirloom candidates in slot order: one carrying a weapon's Relic key harmonizes with it. */
    val heirloomRelics: ImmutableList<RelicId> = immutableListOf(),
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
