// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave

internal enum class ProfileAudioCue {
    UI_CLICK,
    PURCHASE,
}

/** Translates Profile-owned semantics before crossing the mechanical Resource boundary. */
internal class ProfileAudioExecutor(
    private val audioService: AudioService,
) {
    fun play(cue: ProfileAudioCue) {
        audioService.advance(0f, listOf(cue.toToneRequest()))
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
}

private fun ProfileAudioCue.toToneRequest(): ToneRequest = when (this) {
    ProfileAudioCue.UI_CLICK -> ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)
    ProfileAudioCue.PURCHASE -> ToneRequest(490f, 0.1f, 0.16f, ToneWave.SINE)
}
