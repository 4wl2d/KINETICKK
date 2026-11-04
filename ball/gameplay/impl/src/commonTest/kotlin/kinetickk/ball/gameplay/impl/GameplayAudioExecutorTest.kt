// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.toImmutableList
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kotlin.test.Test
import kotlin.test.assertEquals

class GameplayAudioExecutorTest {
    @Test
    fun mapsGameplayOwnedCuesToSoundIdsInIngressOrderWithoutTickingMusic() {
        val service = RecordingAudioService()
        val executor = ResourceGameplayAudioExecutor(service)

        executor.play(GameplayAudioCue.entries.toImmutableList())

        assertEquals(GameplayAudioCue.entries.map { SoundId.valueOf(it.name) }, service.lastSounds)
        assertEquals(listOf("play"), service.calls)
    }

    @Test
    fun ticksMusicByTheGivenFrameTimeOnly() {
        val service = RecordingAudioService()

        ResourceGameplayAudioExecutor(service).tick(0.125f)

        assertEquals(listOf("tick"), service.calls)
        assertEquals(0.125f, service.lastDeltaSeconds)
    }

    @Test
    fun forwardsAudioUnlockToTheResource() {
        val service = RecordingAudioService()

        ResourceGameplayAudioExecutor(service).ensureUnlocked()

        assertEquals(1, service.unlockCount)
    }

    @Test
    fun emptyAndSingleCueFramesStillAdvanceAudioAndLeaveRetainedRequestsUnchanged() {
        val service = RecordingAudioService()
        val executor = ResourceGameplayAudioExecutor(service)
        fun frame(delta: Float, cues: List<GameplayAudioCue>) {
            executor.play(cues.toImmutableList())
            executor.tick(delta)
        }
        frame(0.01f, listOf(GameplayAudioCue.DASH))
        val retained = service.lastSounds

        frame(0.02f, emptyList())
        assertEquals(0.02f, service.lastDeltaSeconds)
        assertEquals(listOf("play", "tick", "tick"), service.calls, "An empty frame plays nothing and still ticks")

        frame(0.03f, listOf(GameplayAudioCue.HURT))
        assertEquals(0.03f, service.lastDeltaSeconds)
        assertEquals(listOf(SoundId.HURT), service.lastSounds)
        assertEquals(listOf(SoundId.DASH), retained)
    }

    @Test
    fun equalPriorityCuesKeepIngressOrderAndDuplicatesForTheResourceBoundary() {
        val service = RecordingAudioService()
        ResourceGameplayAudioExecutor(service).play(
            listOf(
                GameplayAudioCue.PICKUP,
                GameplayAudioCue.SKILL,
                GameplayAudioCue.IMPACT,
                GameplayAudioCue.WEAPON_HEAVY,
                GameplayAudioCue.SKILL,
                GameplayAudioCue.WEAPON_LIGHT,
            ).toImmutableList(),
        )

        assertEquals(
            listOf(
                SoundId.PICKUP,
                SoundId.SKILL,
                SoundId.IMPACT,
                SoundId.WEAPON_HEAVY,
                SoundId.SKILL,
                SoundId.WEAPON_LIGHT,
            ),
            service.lastSounds,
        )
    }
}

private class RecordingAudioService : AudioService {
    var lastDeltaSeconds = 0f
    var lastSounds = emptyList<SoundId>()
    val calls = mutableListOf<String>()
    var unlockCount = 0

    override fun updatePreferences(preferences: AudioPreferences) = Unit

    override fun play(sounds: List<SoundId>) {
        calls += "play"
        lastSounds = sounds
    }

    override fun tick(realDeltaSeconds: Float) {
        calls += "tick"
        lastDeltaSeconds = realDeltaSeconds
    }

    override fun ensureUnlocked() {
        unlockCount++
    }

    override fun close() = Unit
}
