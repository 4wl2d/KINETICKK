// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.features.audio.nucleus.protocol.AudioCue
import kinetickk.features.audio.nucleus.protocol.AudioDecisionContext
import kinetickk.features.audio.nucleus.protocol.AudioIntent
import kinetickk.features.audio.nucleus.protocol.AudioOperationId
import kinetickk.features.audio.nucleus.protocol.AudioPlaybackSettings
import kinetickk.features.audio.nucleus.state.AudioState
import kinetickk.features.audio.nucleus.transition.AudioNucleus
import kinetickk.features.audio.nucleus.transition.selectAudioCues
import kinetickk.features.audio.resources.tone.NumericTonePlayer
import kinetickk.features.audio.resources.tone.isToneRequestAllowed
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudioFeatureBallTest {
    @Test
    fun urgentCuesSurviveNoisyMultiKillFrames() {
        val selected = selectAudioCues(
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
    fun sameStatePulseAndContextProduceTheSamePureDecision() {
        val nucleus = AudioNucleus()
        val state = AudioState()
        val pulse = AudioIntent.Advance(
            realDeltaSeconds = 0.016f,
            cues = listOf(AudioCue.HURT, AudioCue.DASH).toImmutableList(),
        )
        val context = AudioDecisionContext(
            operationId = AudioOperationId(1uL),
            settings = AudioPlaybackSettings(masterVolume = 1f),
        )

        assertEquals(
            nucleus.decide(state, pulse, context),
            nucleus.decide(state, pulse, context),
        )
    }

    @Test
    fun acceptedFrameDrivesCuePriorityAndMusicSequence() {
        val player = RecordingTonePlayer()
        val ball = AudioFeatureBall(player)

        val result = ball.advance(
            settings = AudioPlaybackSettings(masterVolume = 1f, musicEnabled = true),
            realDeltaSeconds = 0.016f,
            cues = listOf(AudioCue.UI_CLICK, AudioCue.HURT, AudioCue.DASH, AudioCue.PICKUP),
        )

        assertIs<AudioDispatchResult.Committed>(result)
        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequency })
        assertEquals(3, player.tones.last().wave)
        assertEquals(1uL, ball.snapshotForTesting().musicStep)
    }

    @Test
    fun capabilityFailuresAndInvalidToneRequestsDoNotEscape() {
        val ball = AudioFeatureBall(ThrowingTonePlayer)

        assertIs<AudioDispatchResult.Committed>(ball.ensureUnlocked())
        assertIs<AudioDispatchResult.Committed>(
            ball.advance(AudioPlaybackSettings(), 0.016f, listOf(AudioCue.HURT)),
        )
        assertIs<AudioDispatchResult.Committed>(ball.close())

        assertTrue(isToneRequestAllowed(440f, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(Float.NaN, 0.1f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 2f, 0.5f, 0))
        assertTrue(!isToneRequestAllowed(440f, 0.1f, 0.5f, 4))
    }

    @Test
    fun acceptedCueBoundPreservesThePreviousThirtyTwoThirtyThreeBehavior() {
        val player = RecordingTonePlayer()
        val ball = AudioFeatureBall(player)
        val settings = AudioPlaybackSettings(musicEnabled = false)

        assertIs<AudioDispatchResult.Committed>(
            ball.advance(settings, 0.016f, List(32) { AudioCue.UI_CLICK }),
        )
        assertEquals(1, player.tones.size)

        player.tones.clear()
        assertIs<AudioDispatchResult.Committed>(
            ball.advance(settings, 0.016f, List(33) { AudioCue.UI_CLICK }),
        )
        assertTrue(player.tones.isEmpty())
    }

    @Test
    fun normalizedInputCollectionHasAFiniteAdmissionBoundary() {
        val ball = AudioFeatureBall(RecordingTonePlayer())

        val result = ball.advance(
            settings = AudioPlaybackSettings(musicEnabled = false),
            realDeltaSeconds = 0.016f,
            cues = List(AudioFeatureBall.MAX_INPUT_CUES + 1) { AudioCue.UI_CLICK },
        )

        val rejection = assertIs<AudioDispatchResult.AdmissionRejected>(result)
        val limit = assertIs<AdmissionFailure.LimitExceeded>(rejection.reason)
        assertEquals(MandatoryDecisionLimit.COLLECTION_ITEMS, limit.limit)
        assertEquals((AudioFeatureBall.MAX_INPUT_CUES + 1).toLong(), limit.actual)
    }

    @Test
    fun closePublishesClosedStateBeforeCallingTheSinkAndIsIdempotent() {
        lateinit var ball: AudioFeatureBall
        var closeCalls = 0
        var observedClosedState = false
        val player = RecordingTonePlayer(
            onClose = {
                closeCalls++
                observedClosedState = ball.snapshotForTesting().closed
            },
        )
        ball = AudioFeatureBall(player)

        assertIs<AudioDispatchResult.Committed>(ball.close())
        assertTrue(observedClosedState)
        assertEquals(1, closeCalls)

        assertIs<AudioDispatchResult.Committed>(ball.close())
        assertIs<AudioDispatchResult.Committed>(
            ball.advance(AudioPlaybackSettings(), 0.016f, listOf(AudioCue.HURT)),
        )
        assertEquals(1, closeCalls)
        assertTrue(player.tones.isEmpty())
    }
}

private data class RecordedTone(
    val frequency: Float,
    val duration: Float,
    val volume: Float,
    val wave: Int,
)

private class RecordingTonePlayer(
    private val onClose: () -> Unit = {},
) : NumericTonePlayer {
    val tones = mutableListOf<RecordedTone>()

    override fun unlock() = Unit

    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        tones += RecordedTone(frequency, durationSeconds, volume, wave)
    }

    override fun close() = onClose()
}

private object ThrowingTonePlayer : NumericTonePlayer {
    override fun unlock(): Unit = error("unlock failure")
    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int): Unit =
        error("play failure")
    override fun close(): Unit = error("close failure")
}
