// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave

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
        audioService.updatePreferences(
            AudioPreferences(
                soundEnabled = preferences.soundEnabled,
                musicEnabled = preferences.musicEnabled,
                masterVolume = preferences.masterVolume,
            ),
        )
    }

    fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }

    internal fun play(cue: SessionAudioCue) {
        audioService.advance(0f, listOf(cue.toToneRequest()))
    }
}

private fun SessionAudioCue.toToneRequest(): ToneRequest = when (this) {
    SessionAudioCue.UI_CLICK -> ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)
}
