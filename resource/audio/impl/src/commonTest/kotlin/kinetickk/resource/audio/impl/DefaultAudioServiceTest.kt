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

        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequencyHz })
        assertEquals(ToneWave.TRIANGLE, player.tones.last().wave)

        repeat(4) { service.advance(realDeltaSeconds = 0.1f, requests = emptyList()) }
        assertEquals(146.83f, player.tones.last().frequencyHz)
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
    fun capabilityFaultsPropagateForUnlockPlayAndCloseWithoutInventingClosedState() {
        val unlockPlayer = FaultingTonePlayer(ToneOperation.UNLOCK)
        val unlockService = DefaultAudioService(unlockPlayer)
        assertFailsWith<ToneCapabilityFault> { unlockService.ensureUnlocked() }
        assertEquals(1, unlockPlayer.unlockCalls)

        val playPlayer = FaultingTonePlayer(ToneOperation.PLAY)
        val playService = DefaultAudioService(playPlayer)
        playService.updatePreferences(AudioPreferences(musicEnabled = false))
        assertFailsWith<ToneCapabilityFault> {
            playService.advance(0.016f, listOf(HURT_REQUEST))
        }
        assertEquals(1, playPlayer.playCalls)

        val closePlayer = FaultingTonePlayer(ToneOperation.CLOSE)
        val closeService = DefaultAudioService(closePlayer)
        assertFailsWith<ToneCapabilityFault> { closeService.close() }
        assertFailsWith<ToneCapabilityFault> { closeService.close() }
        assertEquals(2, closePlayer.closeCalls)
    }

    @Test
    fun requestConstructionRejectsInvalidValues() {
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
        assertEquals(exact.map(ToneRequest::frequencyHz), player.tones.map(ToneRequest::frequencyHz))

        player.tones.clear()
        service.advance(realDeltaSeconds = 0.016f, requests = exact + UI_CLICK_REQUEST)
        assertEquals(exact.map(ToneRequest::frequencyHz), player.tones.map(ToneRequest::frequencyHz))
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
        assertEquals(listOf(110f), player.tones.map { it.frequencyHz })
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

private class RecordingTonePlayer : TonePlaybackCapability {
    val tones = mutableListOf<ToneRequest>()
    var unlockCalls = 0
    var closeCalls = 0

    override fun unlock() {
        unlockCalls++
    }

    override fun play(request: ToneRequest) {
        tones += request
    }

    override fun close() {
        closeCalls++
    }
}

private enum class ToneOperation {
    UNLOCK,
    PLAY,
    CLOSE,
}

private class FaultingTonePlayer(
    private val faultingOperation: ToneOperation,
) : TonePlaybackCapability {
    var unlockCalls = 0
    var playCalls = 0
    var closeCalls = 0

    override fun unlock() {
        unlockCalls++
        if (faultingOperation == ToneOperation.UNLOCK) throw ToneCapabilityFault()
    }

    override fun play(request: ToneRequest) {
        playCalls++
        if (faultingOperation == ToneOperation.PLAY) throw ToneCapabilityFault()
    }

    override fun close() {
        closeCalls++
        if (faultingOperation == ToneOperation.CLOSE) throw ToneCapabilityFault()
    }
}

private class ToneCapabilityFault : RuntimeException()
