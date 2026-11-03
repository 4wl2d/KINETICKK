// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileAudioExecutorTest {
    @Test
    fun profileCuesMapToStableMechanicalRequests() {
        val service = RecordingProfileAudioService()
        val executor = ProfileAudioExecutor(service)

        executor.play(ProfileAudioCue.UI_CLICK)
        executor.play(ProfileAudioCue.PURCHASE)
        executor.play(ProfileAudioCue.REFUSED)
        assertEquals(3, service.unlockCalls)

        assertEquals(
            listOf(
                listOf(SoundId.UI_CLICK),
                listOf(SoundId.UI_CONFIRM),
                listOf(SoundId.UI_REFUSED),
            ),
            service.soundBatches,
        )
        assertEquals(0, service.tickCalls)
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
    var unlockCalls = 0
    var preferences = AudioPreferences()
    val soundBatches = mutableListOf<List<SoundId>>()
    var tickCalls = 0

    override fun updatePreferences(preferences: AudioPreferences) {
        this.preferences = preferences
    }

    override fun play(sounds: List<SoundId>) {
        assertEquals(soundBatches.size + 1, unlockCalls, "Pointer feedback must unlock audio before playing it")
        soundBatches += sounds
    }

    override fun tick(realDeltaSeconds: Float) {
        tickCalls++
    }

    override fun ensureUnlocked() { unlockCalls++ }
    override fun close() = Unit
}
