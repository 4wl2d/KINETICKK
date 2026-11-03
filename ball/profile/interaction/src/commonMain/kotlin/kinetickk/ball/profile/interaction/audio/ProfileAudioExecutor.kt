// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId

internal enum class ProfileAudioCue {
    UI_CLICK,
    PURCHASE,
    /** A press the Profile would refuse (a locked unlock): lower and shorter than a click. */
    REFUSED,
}

/** Translates Profile-owned semantics before crossing the mechanical Resource boundary. */
internal class ProfileAudioExecutor(
    private val audioService: AudioService,
) {
    fun play(cue: ProfileAudioCue) {
        audioService.ensureUnlocked()
        // A menu press is an effect only; it never moves the music clock.
        audioService.play(listOf(cue.soundId()))
    }

    fun updatePreferences(preferences: PlayerPreferences) {
        audioService.updatePreferences(preferences.toAudioPreferences())
    }
}

private fun ProfileAudioCue.soundId(): SoundId = when (this) {
    ProfileAudioCue.UI_CLICK -> SoundId.UI_CLICK
    ProfileAudioCue.PURCHASE -> SoundId.UI_CONFIRM
    ProfileAudioCue.REFUSED -> SoundId.UI_REFUSED
}
