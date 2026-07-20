// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.protocol

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.collections.ImmutableList
import kinetickk.core.profile.api.GameplayProgressUpdate
import kinetickk.core.profile.api.PlayerPreferences

/** Live-run user and lifecycle events accepted by the synchronous gameplay reducer. */
sealed interface GameplayAction {
    data class FrameElapsed(val realDeltaSeconds: Float) : GameplayAction
    data class ViewportChanged(val width: Float, val height: Float, val density: Float) : GameplayAction
    data class PointerMoved(val x: Float, val y: Float, val active: Boolean = true) : GameplayAction
    data class BrakeChanged(val source: BrakeSource, val active: Boolean) : GameplayAction
    data object DashRequested : GameplayAction
    data object PauseToggled : GameplayAction
    data object PauseForOverlay : GameplayAction
    data object ExitRunRequested : GameplayAction
    data class PreferencesChanged(val preferences: PlayerPreferences) : GameplayAction
    data class ChoiceSelected(val index: Int) : GameplayAction
    data object ChoicesRerolled : GameplayAction
    data object UserGestureObserved : GameplayAction
}

enum class BrakeSource { KEYBOARD, SECONDARY_POINTER, TOUCH_CONTROL }

/** Work requested by the domain and executed by the feature composition root after commit. */
sealed interface GameEffect {
    data class AdvanceAudio(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<AudioCue>,
    ) : GameEffect

    data object EnsureAudioUnlocked : GameEffect

    data class PublishProgress(
        val update: GameplayProgressUpdate,
    ) : GameEffect

    data class EmitVisualFx(
        val cues: ImmutableList<VisualFxCue>,
    ) : GameEffect
}

sealed interface GameRejection {
    data class InvalidInput(val field: String, val reason: String) : GameRejection
    data object RevisionExhausted : GameRejection
}
