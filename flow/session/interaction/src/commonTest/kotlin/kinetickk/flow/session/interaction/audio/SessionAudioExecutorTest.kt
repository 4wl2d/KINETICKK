// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionAudioExecutorTest {
    @Test
    fun sessionClickMapsToStableMechanicalRequest() {
        val service = RecordingSessionAudioService()

        SessionAudioExecutor(service).playUiClick()

        assertEquals(1, service.unlockCalls)

        assertEquals(listOf(SoundId.UI_CLICK), service.sounds)
        assertEquals(0, service.tickCalls)
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
    var sounds = emptyList<SoundId>()
    var tickCalls = 0
    var preferences = AudioPreferences()
    var unlockCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
        this.preferences = preferences
    }

    override fun play(sounds: List<SoundId>) {
        assertEquals(1, unlockCalls, "Pointer feedback must unlock audio before playing it")
        this.sounds = sounds
    }

    override fun tick(realDeltaSeconds: Float) {
        tickCalls++
    }

    override fun ensureUnlocked() {
        unlockCalls++
    }
    override fun close() = Unit
}
