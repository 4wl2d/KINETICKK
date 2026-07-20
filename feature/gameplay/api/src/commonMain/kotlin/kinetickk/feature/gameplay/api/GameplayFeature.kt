// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.api

import androidx.compose.runtime.Composable
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.ImmutableSet
import kinetickk.core.collections.immutableListOf
import kinetickk.core.collections.immutableSetOf
import kinetickk.core.content.CoreShape
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.PlayerPreferences

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
    data object UserGestureObserved : GameplayOutput
    data class AudioFrame(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<AudioCue>,
    ) : GameplayOutput
}

interface GameplayFeature {
    fun start(configuration: RunConfiguration)
    fun applyPreferences(preferences: PlayerPreferences)
    fun pauseForOverlay(): Boolean
    fun togglePause()
    fun uiModel(): GameplayUiModel

    @Composable
    fun Content(
        inputEnabled: Boolean,
        onOutput: (GameplayOutput) -> Unit,
    )
}
