// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileAudioExecutorTest {
    @Test
    fun profileCuesMapToStableMechanicalRequests() {
        val service = RecordingProfileAudioService()
        val executor = ProfileAudioExecutor(service)

        executor.play(ProfileAudioCue.UI_CLICK)
        executor.play(ProfileAudioCue.PURCHASE)

        assertEquals(
            listOf(
                listOf(ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)),
                listOf(ToneRequest(490f, 0.1f, 0.16f, ToneWave.SINE)),
            ),
            service.requestBatches,
        )
    }

    @Test
    fun profilePreferencesMapAtTheOwnerBoundary() {
        val service = RecordingProfileAudioService()

        ProfileAudioExecutor(service).updatePreferences(
            PlayerPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 0.4f),
        )

        assertEquals(
            AudioPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 0.4f),
            service.preferences,
        )
    }
}

private class RecordingProfileAudioService : AudioService {
    var preferences = AudioPreferences()
    val requestBatches = mutableListOf<List<ToneRequest>>()

    override fun updatePreferences(preferences: AudioPreferences) {
        this.preferences = preferences
    }

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        assertEquals(0f, realDeltaSeconds)
        requestBatches += requests
    }

    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
