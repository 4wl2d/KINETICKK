// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionAudioExecutorTest {
    @Test
    fun sessionClickMapsToStableMechanicalRequest() {
        val service = RecordingSessionAudioService()

        SessionAudioExecutor(service).playUiClick()

        assertEquals(
            listOf(ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)),
            service.requests,
        )
    }

    @Test
    fun sessionPreferencesAndUnlockUseTheNarrowMechanicalBoundary() {
        val service = RecordingSessionAudioService()
        val preferences = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = true,
            masterVolume = 0.4f,
        )

        val executor = SessionAudioExecutor(service)
        executor.updatePreferences(preferences)
        executor.ensureUnlocked()

        assertEquals(
            AudioPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 0.4f),
            service.preferences,
        )
        assertEquals(1, service.unlockCalls)
    }
}

private class RecordingSessionAudioService : AudioService {
    var requests = emptyList<ToneRequest>()
    var preferences = AudioPreferences()
    var unlockCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
        this.preferences = preferences
    }

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        assertEquals(0f, realDeltaSeconds)
        this.requests = requests
    }

    override fun ensureUnlocked() {
        unlockCalls++
    }
    override fun close() = Unit
}
