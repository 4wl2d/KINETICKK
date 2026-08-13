// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.toImmutableList
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals

class GameplayAudioExecutorTest {
    @Test
    fun mapsGameplayOwnedCuesToMechanicalRequestsInSemanticPriorityOrder() {
        val service = RecordingAudioService()
        val executor = ResourceGameplayAudioExecutor(service)

        executor.advance(0.125f, GameplayAudioCue.entries.toImmutableList())

        assertEquals(0.125f, service.lastDeltaSeconds)
        assertEquals(
            listOf(
                ToneRequest(64f, 0.34f, 0.24f, ToneWave.SAW),
                ToneRequest(784f, 0.32f, 0.2f, ToneWave.TRIANGLE),
                ToneRequest(118f, 0.22f, 0.2f, ToneWave.SAW),
                ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE),
                ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW),
                ToneRequest(820f, 0.2f, 0.2f, ToneWave.SAW),
                ToneRequest(132f, 0.12f, 0.18f, ToneWave.SAW),
                ToneRequest(92f, 0.07f, 0.2f, ToneWave.SQUARE),
                ToneRequest(560f, 0.16f, 0.18f, ToneWave.TRIANGLE),
                ToneRequest(445f, 0.09f, 0.13f, ToneWave.SINE),
                ToneRequest(640f, 0.18f, 0.2f, ToneWave.TRIANGLE),
                ToneRequest(420f, 0.055f, 0.1f, ToneWave.TRIANGLE),
                ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE),
                ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE),
                ToneRequest(330f, 0.06f, 0.13f, ToneWave.TRIANGLE),
            ),
            service.lastRequests,
        )
    }

    @Test
    fun forwardsAudioUnlockToTheResource() {
        val service = RecordingAudioService()

        ResourceGameplayAudioExecutor(service).ensureUnlocked()

        assertEquals(1, service.unlockCount)
    }
}

private class RecordingAudioService : AudioService {
    var lastDeltaSeconds = 0f
    var lastRequests = emptyList<ToneRequest>()
    var unlockCount = 0

    override fun updatePreferences(preferences: AudioPreferences) = Unit

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        lastDeltaSeconds = realDeltaSeconds
        lastRequests = requests
    }

    override fun ensureUnlocked() {
        unlockCount++
    }

    override fun close() = Unit
}
