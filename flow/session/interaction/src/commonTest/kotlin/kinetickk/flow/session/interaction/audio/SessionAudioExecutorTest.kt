// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.audio

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
}

private class RecordingSessionAudioService : AudioService {
    var requests = emptyList<ToneRequest>()

    override fun updatePreferences(preferences: AudioPreferences) = Unit

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        assertEquals(0f, realDeltaSeconds)
        this.requests = requests
    }

    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
