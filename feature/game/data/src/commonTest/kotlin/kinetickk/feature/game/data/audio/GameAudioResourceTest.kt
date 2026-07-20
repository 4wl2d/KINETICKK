// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.data.audio

import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.protocol.SoundCue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class GameAudioTest {
    @Test
    fun urgentCuesSurviveNoisyMultiKillFrames() {
        val selected = selectSoundCues(
            listOf(
                SoundCue.ENEMY_DESTROYED,
                SoundCue.DASH,
                SoundCue.ENEMY_DESTROYED,
                SoundCue.PICKUP,
                SoundCue.HURT,
            ),
            limit = 3,
        )

        assertEquals(listOf(SoundCue.HURT, SoundCue.DASH, SoundCue.PICKUP), selected)
    }

    @Test
    fun resourcePreservesCuePriorityAndMusicSequence() {
        val player = RecordingTonePlayer()
        val resource = GameAudioResource(player)

        resource.advance(
            settings = GameSettings(masterVolume = 1f, musicEnabled = true),
            realDelta = 0.016f,
            cues = listOf(SoundCue.UI_CLICK, SoundCue.HURT, SoundCue.DASH, SoundCue.PICKUP),
        )

        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequency })
        assertEquals(3, player.tones.last().wave)
    }

    @Test
    fun capabilityFailuresAndInvalidToneRequestsDoNotEscape() {
        val resource = GameAudioResource(ThrowingTonePlayer)

        resource.ensureUnlocked()
        resource.advance(GameSettings(), 0.016f, listOf(SoundCue.HURT))
        resource.close()

        assertTrue(isToneRequestAllowed(440f, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(Float.NaN, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 2f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 0.1f, 0.5f, 4))
    }

    @Test
    fun cueCapabilityEnforcesTheAcceptedEffectBound() {
        val player = RecordingTonePlayer()
        val resource = GameAudioResource(player)

        resource.advance(
            settings = GameSettings(musicEnabled = false),
            realDelta = 0.016f,
            cues = List(32) { SoundCue.UI_CLICK },
        )
        assertEquals(1, player.tones.size)

        player.tones.clear()
        resource.advance(
            settings = GameSettings(musicEnabled = false),
            realDelta = 0.016f,
            cues = List(33) { SoundCue.UI_CLICK },
        )
        assertTrue(player.tones.isEmpty())
    }
}

private data class RecordedTone(
    val frequency: Float,
    val duration: Float,
    val volume: Float,
    val wave: Int,
)

private class RecordingTonePlayer : NumericTonePlayer {
    val tones = mutableListOf<RecordedTone>()

    override fun unlock() = Unit

    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        tones += RecordedTone(frequency, durationSeconds, volume, wave)
    }

    override fun close() = Unit
}

private object ThrowingTonePlayer : NumericTonePlayer {
    override fun unlock(): Unit = error("unlock failure")
    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int): Unit = error("play failure")
    override fun close(): Unit = error("close failure")
}
