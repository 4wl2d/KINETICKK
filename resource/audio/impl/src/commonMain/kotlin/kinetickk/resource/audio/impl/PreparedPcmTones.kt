// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Immutable, bounded waveforms. Gain is applied at playback so volume changes never rebuild them. */
class PreparedPcmTones(requests: List<ToneRequest>, private val sampleRate: Int) {
    private class Entry(val request: ToneRequest, val samples: FloatArray)

    init {
        require(sampleRate in 8_000..48_000)
        require(requests.size <= 64) { "Audio preparation accepts at most 64 fixed tones" }
    }

    private val entries = buildList<Entry> {
        for (request in requests) {
            if (none { it.request.sameWaveform(request) }) {
                add(Entry(request, synthesizeTone(request, sampleRate)))
            }
        }
    }

    /** Fallback supports arbitrary validated callers; the shipped fixed tones always hit the bank. */
    fun samples(request: ToneRequest): FloatArray =
        entries.firstOrNull { it.request.sameWaveform(request) }?.samples ?: synthesizeTone(request, sampleRate)

    fun contains(request: ToneRequest): Boolean = entries.any { it.request.sameWaveform(request) }

    val sampleCount: Int get() = entries.sumOf { it.samples.size }
}

private fun ToneRequest.sameWaveform(other: ToneRequest): Boolean =
    frequencyHz == other.frequencyHz && durationSeconds == other.durationSeconds && wave == other.wave

private fun synthesizeTone(request: ToneRequest, sampleRate: Int): FloatArray {
    val sampleCount = (sampleRate * request.durationSeconds).toInt().coerceAtLeast(1)
    return FloatArray(sampleCount) { index ->
        val phase = index * request.frequencyHz / sampleRate
        val raw = when (request.wave) {
            ToneWave.SQUARE -> if (phase % 1f < 0.5f) 1f else -1f
            ToneWave.SAW -> phase % 1f * 2f - 1f
            ToneWave.TRIANGLE -> 1f - 4f * abs(phase % 1f - 0.5f)
            ToneWave.SINE -> sin(phase * PI * 2.0).toFloat()
        }
        val envelope = min(1f, index / (sampleRate * 0.008f)) *
            (1f - index.toFloat() / sampleCount).coerceAtLeast(0f)
        raw * envelope
    }
}

fun pcmSample(sample: Float, gain: Float): Short =
    (sample * gain * Short.MAX_VALUE).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
