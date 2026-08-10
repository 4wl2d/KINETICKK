// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.toImmutableList
import kinetickk.resource.audio.api.AudioCue
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kotlin.test.Test
import kotlin.test.assertEquals

class GameplayAudioExecutorTest {
    @Test
    fun mapsGameplayOwnedCuesToTheExistingResourceCuesInOrder() {
        val service = RecordingAudioService()
        val executor = ResourceGameplayAudioExecutor(service)

        executor.advance(0.125f, GameplayAudioCue.entries.toImmutableList())

        assertEquals(0.125f, service.lastDeltaSeconds)
        assertEquals(
            listOf(
                AudioCue.UI_CLICK,
                AudioCue.DASH,
                AudioCue.WEAPON_LIGHT,
                AudioCue.WEAPON_HEAVY,
                AudioCue.IMPACT,
                AudioCue.ENEMY_DESTROYED,
                AudioCue.PICKUP,
                AudioCue.LEVEL_UP,
                AudioCue.OVERHEAT,
                AudioCue.RECOVERED,
                AudioCue.HURT,
                AudioCue.OVERDRIVE,
                AudioCue.WEAPON_ACQUIRED,
                AudioCue.GAME_OVER,
                AudioCue.VICTORY,
            ),
            service.lastCues,
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
    var lastCues = emptyList<AudioCue>()
    var unlockCount = 0

    override fun updatePreferences(preferences: AudioPreferences) = Unit

    override fun advance(realDeltaSeconds: Float, cues: List<AudioCue>) {
        lastDeltaSeconds = realDeltaSeconds
        lastCues = cues
    }

    override fun ensureUnlocked() {
        unlockCount++
    }

    override fun close() = Unit
}
