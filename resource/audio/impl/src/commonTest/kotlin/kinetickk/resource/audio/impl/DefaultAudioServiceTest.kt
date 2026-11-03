// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.SoundId
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneRequestLimits
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class DefaultAudioServiceTest {
    @Test
    fun preparationIncludesEffectsAndMusicWithoutPlayingOrUnlocking() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        val extra = ToneRequest(300f, 0.05f, 0.1f, ToneWave.SINE)
        var completed = false
        suspend { service.prepare() }.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) {
                    result.getOrThrow()
                    completed = true
                }
            },
        )
        assertTrue(completed)
        assertTrue(player.prepared.containsAll(SoundId.entries.map(ToneCatalog::request)))
        assertTrue(player.prepared.any { it.frequencyHz == 110f && it.wave == ToneWave.TRIANGLE })
        assertEquals(SoundId.entries.size + 6, player.prepared.size)
        suspend { service.prepare(listOf(extra)) }.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) = result.getOrThrow()
            },
        )
        assertTrue(player.prepared.containsAll(SoundId.entries.map(ToneCatalog::request) + extra))
        assertTrue(player.tones.isEmpty())
        assertEquals(0, player.unlockCalls)
        service.tick(0.016f)
        assertEquals(110f, player.tones.single().frequencyHz)
    }

    @Test
    fun catalogKeepsEveryShippedToneAndItsPriorityOrder() {
        assertEquals(
            listOf(
                ToneRequest(64f, 0.34f, 0.24f, ToneWave.SAW),
                ToneRequest(784f, 0.32f, 0.2f, ToneWave.TRIANGLE),
                ToneRequest(118f, 0.22f, 0.2f, ToneWave.SAW),
                ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE),
                ToneRequest(988f, 0.3f, 0.22f, ToneWave.SAW),
                ToneRequest(1_047f, 0.42f, 0.22f, ToneWave.TRIANGLE),
                ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW),
                ToneRequest(820f, 0.2f, 0.2f, ToneWave.SAW),
                ToneRequest(58f, 0.40f, 0.22f, ToneWave.SAW),
                ToneRequest(132f, 0.12f, 0.18f, ToneWave.SAW),
                ToneRequest(92f, 0.07f, 0.2f, ToneWave.SQUARE),
                ToneRequest(480f, 0.09f, 0.16f, ToneWave.TRIANGLE),
                ToneRequest(560f, 0.16f, 0.18f, ToneWave.TRIANGLE),
                ToneRequest(445f, 0.09f, 0.13f, ToneWave.SINE),
                ToneRequest(640f, 0.18f, 0.2f, ToneWave.TRIANGLE),
                ToneRequest(420f, 0.055f, 0.1f, ToneWave.TRIANGLE),
                ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE),
                ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE),
                ToneRequest(490f, 0.1f, 0.16f, ToneWave.SINE),
                ToneRequest(260f, 0.06f, 0.12f, ToneWave.SINE),
                ToneRequest(330f, 0.06f, 0.13f, ToneWave.TRIANGLE),
            ),
            SoundId.entries.sortedByDescending(ToneCatalog::priority).map(ToneCatalog::request),
        )
        assertEquals(SoundId.entries.size, ToneCatalog.effectTones.distinct().size)
    }

    @Test
    fun equalPrioritySoundsKeepIngressOrderBeforeDistinctSelection() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))

        service.play(
            listOf(
                SoundId.PICKUP,
                SoundId.SKILL,
                SoundId.IMPACT,
                SoundId.WEAPON_HEAVY,
                SoundId.SKILL,
                SoundId.WEAPON_LIGHT,
            ),
        )

        assertEquals(listOf(480f, 92f, 132f), player.tones.map { it.frequencyHz })
    }

    @Test
    fun resourcePreservesOwnerOrderAndDeduplicatesMechanicalRequests() {
        val selected = selectDistinct(
            listOf(SoundId.HURT, SoundId.DASH, SoundId.HURT, SoundId.PICKUP),
            limit = 3,
        )

        assertEquals(listOf(SoundId.HURT, SoundId.DASH, SoundId.PICKUP), selected)
    }

    @Test
    fun selectionLimitsApplyToDistinctRequestsAndPreserveTheInput() {
        val sounds = listOf(SoundId.HURT, SoundId.HURT, SoundId.DASH, SoundId.DASH, SoundId.PICKUP, SoundId.UI_CLICK)
        assertEquals(emptyList(), selectDistinct(sounds, -1))
        assertEquals(emptyList(), selectDistinct(sounds, 0))
        assertEquals(emptyList(), selectDistinct(emptyList<SoundId>(), 3))
        assertEquals(listOf(SoundId.HURT), selectDistinct(sounds, 1))
        assertEquals(listOf(SoundId.HURT, SoundId.DASH), selectDistinct(sounds, 2))
        assertEquals(listOf(SoundId.HURT, SoundId.DASH, SoundId.PICKUP), selectDistinct(sounds, 3))
        assertEquals(listOf(SoundId.HURT, SoundId.DASH, SoundId.PICKUP, SoundId.UI_CLICK), selectDistinct(sounds, 10))
        assertEquals(listOf(SoundId.HURT), selectDistinct(listOf(SoundId.HURT), 3))
        assertEquals(6, sounds.size)
    }

    @Test
    fun oversizedEffectsAreRejectedWhileMusicContinuesAdvancing() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.play(List(33) { SoundId.HURT })
        service.tick(0.1f)
        repeat(4) { service.tick(0.1f) }

        assertEquals(listOf(110f, 146.83f), player.tones.map { it.frequencyHz })
    }

    @Test
    fun serviceRanksEffectsByPriorityAndThenPlaysTheMusicSequence() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(masterVolume = 1f))

        service.play(listOf(SoundId.PICKUP, SoundId.UI_CLICK, SoundId.HURT, SoundId.DASH))
        service.tick(realDeltaSeconds = 0.016f)

        assertEquals(listOf(76f, 185f, 710f, 110f), player.tones.map { it.frequencyHz })
        assertEquals(ToneWave.TRIANGLE, player.tones.last().wave)

        repeat(4) { service.tick(realDeltaSeconds = 0.1f) }
        assertEquals(146.83f, player.tones.last().frequencyHz)
    }

    @Test
    fun uiPlayNeverAdvancesMusic() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)

        // Fresh music clock: a menu click must not start the sequencer.
        service.play(listOf(SoundId.UI_CLICK))
        assertEquals(listOf(520f), player.tones.map { it.frequencyHz })

        // Music toggled off and on resets the clock; the next click stays a click.
        service.updatePreferences(AudioPreferences(musicEnabled = false))
        service.tick(0.016f)
        service.updatePreferences(AudioPreferences(musicEnabled = true))
        player.tones.clear()
        service.play(listOf(SoundId.UI_CONFIRM))
        assertEquals(listOf(490f), player.tones.map { it.frequencyHz })

        // The first display frame still starts the music on its first note.
        service.tick(0.016f)
        assertEquals(listOf(490f, 110f), player.tones.map { it.frequencyHz })
    }

    @Test
    fun zeroOrInvalidTickDeltaPlaysNoMusicNote() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)

        service.tick(0f)
        service.tick(-0.016f)
        service.tick(Float.NaN)
        assertTrue(player.tones.isEmpty())

        service.tick(0.016f)
        assertEquals(listOf(110f), player.tones.map { it.frequencyHz })
        // The clock is still running: a zero delta neither advances nor replays it.
        repeat(3) { service.tick(0f) }
        assertEquals(listOf(110f), player.tones.map { it.frequencyHz })
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
            playService.play(listOf(SoundId.HURT))
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

        service.play(List(32) { index -> SoundId.entries[index % SoundId.entries.size] })
        assertEquals(3, player.tones.size)

        player.tones.clear()
        service.play(List(33) { index -> SoundId.entries[index % SoundId.entries.size] })
        assertTrue(player.tones.isEmpty())
    }

    @Test
    fun callerEffectRequestSelectionAcceptsThreeAndDropsFourth() {
        val player = RecordingTonePlayer()
        val service = DefaultAudioService(player)
        service.updatePreferences(AudioPreferences(musicEnabled = false))
        val exact = listOf(SoundId.HURT, SoundId.DASH, SoundId.PICKUP)
        val exactTones = listOf(HURT_REQUEST, DASH_REQUEST, PICKUP_REQUEST)

        service.play(exact)
        assertEquals(exactTones.map(ToneRequest::frequencyHz), player.tones.map(ToneRequest::frequencyHz))

        player.tones.clear()
        service.play(exact + SoundId.UI_CLICK)
        assertEquals(exactTones.map(ToneRequest::frequencyHz), player.tones.map(ToneRequest::frequencyHz))
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

        service.updatePreferences(AudioPreferences(musicEnabled = false, masterVolume = 0.25f))
        service.play(listOf(SoundId.HURT))
        service.tick(0.016f)
        assertEquals(HURT_REQUEST.copy(gain = 0.055f), player.tones.single())
        player.tones.clear()

        service.updatePreferences(AudioPreferences(musicEnabled = false, masterVolume = Float.NaN))
        service.play(listOf(SoundId.HURT))
        service.tick(0.016f)
        assertTrue(player.tones.isEmpty())

        service.updatePreferences(AudioPreferences(soundEnabled = false, musicEnabled = true, masterVolume = 1f))
        service.play(listOf(SoundId.HURT))
        service.tick(0.016f)
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
        service.play(listOf(SoundId.HURT))
        service.tick(0.016f)

        assertEquals(1, player.closeCalls)
        assertEquals(0, player.unlockCalls)
        assertTrue(player.tones.isEmpty())
    }
}

private val DASH_REQUEST = ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW)
private val PICKUP_REQUEST = ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE)
private val HURT_REQUEST = ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE)

private class RecordingTonePlayer : TonePlaybackCapability {
    var prepared = emptyList<ToneRequest>()
    val tones = mutableListOf<ToneRequest>()
    var unlockCalls = 0
    var closeCalls = 0

    override suspend fun prepare(requests: List<ToneRequest>) {
        prepared = requests
    }

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
