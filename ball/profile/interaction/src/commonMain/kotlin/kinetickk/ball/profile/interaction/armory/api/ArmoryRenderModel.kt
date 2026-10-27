// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.profile.api.PermanentUpgrades
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
    /** Bought Honing levels (owned tiles show "Lvl N" from 1). */
    val upgrades: PermanentUpgrades = PermanentUpgrades(),
)

/** What the Weapons body asks of its host (the Workshop's shared header owns Back). */
sealed interface ArmoryOutput {
    data object Back : ArmoryOutput
}
