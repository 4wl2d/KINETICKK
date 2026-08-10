// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.PlayerPreferences

/** Immutable inputs captured when a run is created. Profile changes never mutate this object. */
data class RunConfiguration(
    val preferences: PlayerPreferences = PlayerPreferences(),
    val coreShape: CoreShape = CoreShape.ORB,
    val startingWeapon: WeaponId = WeaponId.FLUX_WAKE,
    val unlockedWeapons: ImmutableSet<WeaponId> = immutableSetOf(WeaponId.FLUX_WAKE),
    val metaRanks: ImmutableList<Int> = immutableListOf(0, 0, 0, 0, 0, 0, 0, 0),
    val knownItemIds: ImmutableSet<Int> = immutableSetOf(),
    val rebirthLevel: Int = 0,
    val matterAtStart: Long = 0L,
    val lifetimeMatterAtStart: Long = 0L,
)

enum class GameplayUiPhase {
    IDLE,
    RUNNING,
    PAUSED,
    CHOICE,
    GAME_OVER,
    VICTORY,
}

/** Small shell-facing render model; the simulation render payload remains feature-internal. */
data class GameplayUiModel(
    val phase: GameplayUiPhase = GameplayUiPhase.IDLE,
    val activeWeapon: WeaponId? = null,
    val itemStacks: ImmutableList<Int> = immutableListOf(),
)

sealed interface GameplayOutput {
    data object OpenSettings : GameplayOutput
    data object OpenRebirth : GameplayOutput
    data object ExitToHome : GameplayOutput
    data object RestartRun : GameplayOutput
}

/** Non-Compose lifecycle and query surface consumed by the Session flow. */
interface GameplayController {
    fun start(configuration: RunConfiguration)
    fun applyPreferences(preferences: PlayerPreferences)
    fun pauseForOverlay(): Boolean
    fun togglePause()
    fun uiModel(): GameplayUiModel
}
