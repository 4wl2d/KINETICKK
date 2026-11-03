// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.audio.toAudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId

internal enum class SessionAudioCue {
    UI_CLICK,
}

/** Translates Session-owned semantics before crossing the mechanical Resource boundary. */
class SessionAudioExecutor(
    private val audioService: AudioService,
) {
    fun playUiClick() {
        play(SessionAudioCue.UI_CLICK)
    }

    fun updatePreferences(preferences: PlayerPreferences) {
        audioService.updatePreferences(preferences.toAudioPreferences())
    }

    fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }

    internal fun play(cue: SessionAudioCue) {
        audioService.ensureUnlocked()
        // A menu click is an effect only; it never moves the music clock.
        audioService.play(listOf(cue.soundId()))
    }
}

private fun SessionAudioCue.soundId(): SoundId = when (this) {
    SessionAudioCue.UI_CLICK -> SoundId.UI_CLICK
}
