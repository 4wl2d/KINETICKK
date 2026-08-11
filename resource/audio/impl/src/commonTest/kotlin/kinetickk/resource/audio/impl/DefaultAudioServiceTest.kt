// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneRequestLimits
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultAudioServiceTest {
    @Test
    fun resourcePreservesOwnerOrderAndDeduplicatesMechanicalRequests() {
        val selected = selectToneRequests(
            listOf(HURT_REQUEST, DASH_REQUEST, HURT_REQUEST, PICKUP_REQUEST),
            limit = 3,
        )

        assertEquals(listOf(HURT_REQUEST, DASH_REQUEST, PICKUP_REQUEST), selected)
    }

    @Test
    fun servicePreservesRequestOrderAndMusicSequence() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(masterVolume = 1f))

        service.advance(
            realDeltaSeconds = 0.016f,
            requests = listOf(HURT_REQUEST, DASH_REQUEST, PICKUP_REQUEST, UI_CLICK_REQUEST),
        )

        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequency })
        assertEquals(ToneWave.TRIANGLE.ordinal, player.tones.last().wave)

        repeat(4) { service.advance(realDeltaSeconds = 0.1f, requests = emptyList()) }
        assertEquals(146.83f, player.tones.last().frequency)
    }

    @Test
    fun musicAdvanceDeltaAcceptsMaximumAndClampsNextRepresentableValue() {
        val nextRepresentable = Float.fromBits(MAX_MUSIC_ADVANCE_DELTA_SECONDS.toBits() + 1)

        assertEquals(
            MAX_MUSIC_ADVANCE_DELTA_SECONDS,
            selectMusicAdvanceDeltaSeconds(MAX_MUSIC_ADVANCE_DELTA_SECONDS),
        )
        assertEquals(
            MAX_MUSIC_ADVANCE_DELTA_SECONDS,
            selectMusicAdvanceDeltaSeconds(nextRepresentable),
        )
    }

    @Test
    fun capabilityFailuresDoNotEscapeAndRequestConstructionRejectsInvalidValues() {
        val service = DefaultAudioService(ThrowingTonePlayer)

        service.ensureUnlocked()
        service.advance(0.016f, listOf(HURT_REQUEST))
        service.close()

        assertTrue(isToneRequestAllowed(HURT_REQUEST))
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(Float.NaN, 0.1f, 0.5f, ToneWave.SINE)
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(440f, 2f, 0.5f, ToneWave.SINE)
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(440f, 0.1f, 2f, ToneWave.SINE)
        }
    }

    @Test
    fun callerEffectIngressAcceptsThirtyTwoAndRejectsThirtyThird() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))

        service.advance(
            realDeltaSeconds = 0.016f,
            requests = List(32) { index ->
                ToneRequest(440f + index, 0.1f, 0.1f, ToneWave.SINE)
            },
        )
        assertEquals(3, player.tones.size)

        player.tones.clear()
        service.advance(
            realDeltaSeconds = 0.016f,
            requests = List(33) { index ->
                ToneRequest(440f + index, 0.1f, 0.1f, ToneWave.SINE)
            },
        )
        assertTrue(player.tones.isEmpty())
    }

    @Test
    fun callerEffectRequestSelectionAcceptsThreeAndDropsFourth() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))
        val exact = listOf(HURT_REQUEST, DASH_REQUEST, PICKUP_REQUEST)

        service.advance(realDeltaSeconds = 0.016f, requests = exact)
        assertEquals(exact.map(ToneRequest::frequencyHz), player.tones.map(RecordedTone::frequency))

        player.tones.clear()
        service.advance(realDeltaSeconds = 0.016f, requests = exact + UI_CLICK_REQUEST)
        assertEquals(exact.map(ToneRequest::frequencyHz), player.tones.map(RecordedTone::frequency))
    }

    @Test
    fun toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues() {
        ToneRequest(
            ToneRequestLimits.MIN_FREQUENCY_HZ,
            ToneRequestLimits.MIN_DURATION_SECONDS,
            ToneRequestLimits.MIN_GAIN,
            ToneWave.SINE,
        )
        ToneRequest(
            ToneRequestLimits.MAX_FREQUENCY_HZ,
            ToneRequestLimits.MAX_DURATION_SECONDS,
            ToneRequestLimits.MAX_GAIN,
            ToneWave.TRIANGLE,
        )

        assertFailsWith<IllegalArgumentException> {
            ToneRequest(
                Float.fromBits(ToneRequestLimits.MIN_FREQUENCY_HZ.toBits() - 1),
                0.1f,
                0.5f,
                ToneWave.SINE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(
                Float.fromBits(ToneRequestLimits.MAX_FREQUENCY_HZ.toBits() + 1),
                0.1f,
                0.5f,
                ToneWave.SINE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(
                440f,
                Float.fromBits(ToneRequestLimits.MIN_DURATION_SECONDS.toBits() - 1),
                0.5f,
                ToneWave.SINE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(
                440f,
                Float.fromBits(ToneRequestLimits.MAX_DURATION_SECONDS.toBits() + 1),
                0.5f,
                ToneWave.SINE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(440f, 0.1f, -Float.MIN_VALUE, ToneWave.SINE)
        }
        assertFailsWith<IllegalArgumentException> {
            ToneRequest(
                440f,
                0.1f,
                Float.fromBits(ToneRequestLimits.MAX_GAIN.toBits() + 1),
                ToneWave.SINE,
            )
        }
    }

    @Test
    fun preferencesAreAppliedWithoutLettingInvalidVolumeReachThePlayer() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)

        service.updatePreferences(AudioPreferences(musicEnabled = false, masterVolume = Float.NaN))
        service.advance(0.016f, listOf(HURT_REQUEST))
        assertTrue(player.tones.isEmpty())

        service.updatePreferences(AudioPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 1f))
        service.advance(0.016f, listOf(HURT_REQUEST))
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
        service.advance(0.016f, listOf(HURT_REQUEST))

        assertEquals(1, player.closeCalls)
        assertEquals(0, player.unlockCalls)
        assertTrue(player.tones.isEmpty())
    }
}

private val UI_CLICK_REQUEST = ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)
private val DASH_REQUEST = ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW)
private val PICKUP_REQUEST = ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE)
private val HURT_REQUEST = ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE)

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
    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int): Unit =
        error("play failure")

    override fun close(): Unit = error("close failure")
}
