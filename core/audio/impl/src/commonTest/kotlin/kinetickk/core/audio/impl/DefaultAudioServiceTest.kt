// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.audio.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.audio.api.AudioPreferences
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class DefaultAudioServiceTest {
    @Test
    fun urgentCuesSurviveNoisyMultiKillFrames() {
        val selected = selectSoundCues(
            listOf(
                AudioCue.ENEMY_DESTROYED,
                AudioCue.DASH,
                AudioCue.ENEMY_DESTROYED,
                AudioCue.PICKUP,
                AudioCue.HURT,
            ),
            limit = 3,
        )

        assertEquals(listOf(AudioCue.HURT, AudioCue.DASH, AudioCue.PICKUP), selected)
    }

    @Test
    fun servicePreservesCuePriorityAndMusicSequence() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(masterVolume = 1f))

        service.advance(
            realDeltaSeconds = 0.016f,
            cues = listOf(AudioCue.UI_CLICK, AudioCue.HURT, AudioCue.DASH, AudioCue.PICKUP),
        )

        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequency })
        assertEquals(3, player.tones.last().wave)

        repeat(4) { service.advance(realDeltaSeconds = 0.1f, cues = emptyList()) }
        assertEquals(146.83f, player.tones.last().frequency)
    }

    @Test
    fun capabilityFailuresAndInvalidToneRequestsDoNotEscape() {
        val service = DefaultAudioService(ThrowingTonePlayer)

        service.ensureUnlocked()
        service.advance(0.016f, listOf(AudioCue.HURT))
        service.close()

        assertTrue(isToneRequestAllowed(440f, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(Float.NaN, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 2f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 0.1f, 0.5f, 4))
    }

    @Test
    fun cueCapabilityEnforcesTheAcceptedEffectBound() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))

        service.advance(
            realDeltaSeconds = 0.016f,
            cues = List(32) { AudioCue.UI_CLICK },
        )
        assertEquals(1, player.tones.size)

        player.tones.clear()
        service.advance(
            realDeltaSeconds = 0.016f,
            cues = List(33) { AudioCue.UI_CLICK },
        )
        assertTrue(player.tones.isEmpty())
    }

    @Test
    fun cueToneMapRemainsStable() {
        val expected = mapOf(
            AudioCue.UI_CLICK to ExpectedTone(520f, 0.035f, 0.11f, 0),
            AudioCue.DASH to ExpectedTone(185f, 0.11f, 0.23f, 2),
            AudioCue.WEAPON_LIGHT to ExpectedTone(420f, 0.055f, 0.1f, 3),
            AudioCue.WEAPON_HEAVY to ExpectedTone(132f, 0.12f, 0.18f, 2),
            AudioCue.IMPACT to ExpectedTone(92f, 0.07f, 0.2f, 1),
            AudioCue.ENEMY_DESTROYED to ExpectedTone(330f, 0.06f, 0.13f, 3),
            AudioCue.PICKUP to ExpectedTone(710f, 0.055f, 0.13f, 0),
            AudioCue.LEVEL_UP to ExpectedTone(560f, 0.16f, 0.18f, 3),
            AudioCue.OVERHEAT to ExpectedTone(118f, 0.22f, 0.2f, 2),
            AudioCue.RECOVERED to ExpectedTone(445f, 0.09f, 0.13f, 0),
            AudioCue.HURT to ExpectedTone(76f, 0.13f, 0.22f, 1),
            AudioCue.OVERDRIVE to ExpectedTone(820f, 0.2f, 0.2f, 2),
            AudioCue.WEAPON_ACQUIRED to ExpectedTone(640f, 0.18f, 0.2f, 3),
            AudioCue.PURCHASE to ExpectedTone(490f, 0.1f, 0.16f, 0),
            AudioCue.GAME_OVER to ExpectedTone(64f, 0.34f, 0.24f, 2),
            AudioCue.VICTORY to ExpectedTone(784f, 0.32f, 0.2f, 3),
        )
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false, masterVolume = 1f))

        AudioCue.entries.forEach { cue ->
            player.tones.clear()
            service.advance(realDeltaSeconds = 0f, cues = listOf(cue))
            val tone = player.tones.single()
            assertEquals(
                expected.getValue(cue),
                ExpectedTone(tone.frequency, tone.duration, tone.volume, tone.wave),
            )
        }
    }

    @Test
    fun preferencesAreAppliedWithoutLettingInvalidVolumeReachThePlayer() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)

        service.updatePreferences(AudioPreferences(musicEnabled = false, masterVolume = Float.NaN))
        service.advance(0.016f, listOf(AudioCue.HURT))
        assertTrue(player.tones.isEmpty())

        service.updatePreferences(AudioPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 1f))
        service.advance(0.016f, listOf(AudioCue.HURT))
        assertEquals(listOf(110f), player.tones.map { it.frequency })
    }

    @Test
    fun closeIsIdempotentAndTerminal() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))

        service.close()
        service.close()
        service.ensureUnlocked()
        service.advance(0.016f, listOf(AudioCue.HURT))

        assertEquals(1, player.closeCalls)
        assertEquals(0, player.unlockCalls)
        assertTrue(player.tones.isEmpty())
    }
}

private data class ExpectedTone(
    val frequency: Float,
    val duration: Float,
    val volume: Float,
    val wave: Int,
)

private data class RecordedTone(
    val frequency: Float,
    val duration: Float,
    val volume: Float,
    val wave: Int,
)

private class RecordingTonePlayer : NumericTonePlayer {
    val tones = mutableListOf<RecordedTone>()
    var unlockCalls = 0
    var closeCalls = 0

    override fun unlock() {
        unlockCalls++
    }

    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        tones += RecordedTone(frequency, durationSeconds, volume, wave)
    }

    override fun close() {
        closeCalls++
    }
}

private object ThrowingTonePlayer : NumericTonePlayer {
    override fun unlock(): Unit = error("unlock failure")
    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int): Unit = error("play failure")
    override fun close(): Unit = error("close failure")
}
