// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PreparedPcmTonesTest {
    @Test
    fun preparedWaveformsAreReusedAcrossVolumeChangesAndDuplicateRequests() {
        val request = ToneRequest(440f, 0.18f, 0.2f, ToneWave.SINE)
        val bank = PreparedPcmTones(listOf(request, request.copy(gain = 1f)), 22_050)
        val original = bank.samples(request)
        assertSame(original, bank.samples(request.copy(gain = 0.75f)))
        assertSame(original, bank.samples(request.copy(gain = 0f)))
        assertEquals(original.size, bank.sampleCount)
        assertSame(original, bank.samples(request.copy(gain = 0.65f)))
    }

    @Test
    fun cachedSamplesKeepThePreviousNativeWaveformExactlyAtEveryGain() {
        val rate = 22_050
        for (wave in ToneWave.entries) {
            val request = ToneRequest(185f, 0.11f, 1f, wave)
            val samples = PreparedPcmTones(listOf(request), rate).samples(request)
            for (gain in listOf(0f, 0.035f, 0.1495f, 0.65f, 1f)) {
                for (index in samples.indices) {
                    // Preserve the previous native synthesis as the parity oracle.
                    val phase = index * request.frequencyHz / rate
                    val raw = when (wave) {
                        ToneWave.SQUARE -> if (phase % 1f < 0.5f) 1f else -1f
                        ToneWave.SAW -> phase % 1f * 2f - 1f
                        ToneWave.TRIANGLE -> 1f - 4f * abs(phase % 1f - 0.5f)
                        ToneWave.SINE -> sin(phase * PI * 2.0).toFloat()
                    }
                    val envelope = min(1f, index / (rate * 0.008f)) *
                        (1f - index.toFloat() / samples.size).coerceAtLeast(0f)
                    val expected = (raw * envelope * gain * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    assertEquals(expected, pcmSample(samples[index], gain), "$wave/$gain/$index")
                }
            }
        }
    }

    @Test
    fun preparationAndFallbackStayBoundedAtTheMaximumValidatedDuration() {
        val request = ToneRequest(20_000f, 1f, 1f, ToneWave.SQUARE)
        val bank = PreparedPcmTones(listOf(request), 22_050)
        assertEquals(22_050, bank.sampleCount)
        assertEquals(22_050, bank.samples(request.copy(wave = ToneWave.SAW)).size)
        assertEquals(22_050, bank.sampleCount)
        // The cap counts distinct waveforms: gain-only variants share one entry.
        assertEquals(22_050, PreparedPcmTones(List(65) { request }, 22_050).sampleCount)
        val distinct = List(65) { index -> ToneRequest(100f + index, 0.01f, 1f, ToneWave.SINE) }
        PreparedPcmTones(distinct.take(64) + distinct.take(64).map { it.copy(gain = 0.5f) }, 22_050)
        assertFailsWith<IllegalArgumentException> { PreparedPcmTones(distinct, 22_050) }
        assertFailsWith<IllegalArgumentException> { PreparedPcmTones(listOf(request), 192_000) }
    }
}
