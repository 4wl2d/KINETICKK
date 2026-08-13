// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneRequestLimits
import kinetickk.resource.audio.api.ToneWave

class DefaultAudioService(
    private val platform: TonePlaybackCapability,
) : AudioService {
    private var preferences = AudioPreferences()
    private var musicClock = 0f
    private var musicStep = 0
    private var closed = false

    override fun updatePreferences(preferences: AudioPreferences) {
        if (!closed) this.preferences = preferences
    }

    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) {
        if (closed) return
        val volume = preferences.masterVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (
            preferences.soundEnabled &&
            volume > 0f &&
            requests.size <= MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE
        ) {
            selectToneRequests(requests, MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE).forEach { request ->
                platform.playIfAllowed(request.copy(gain = request.gain * volume))
            }
        }

        if (!preferences.musicEnabled || volume <= 0f) {
            musicClock = 0f
            return
        }
        musicClock -= selectMusicAdvanceDeltaSeconds(realDeltaSeconds)
        if (musicClock <= 0f) {
            musicClock += MUSIC_STEP_SECONDS
            val frequency = MUSIC_NOTES[musicStep % MUSIC_NOTES.size]
            val accent = if (musicStep % 8 == 0) 1.35f else 1f
            platform.playIfAllowed(
                ToneRequest(
                    frequencyHz = frequency,
                    durationSeconds = 0.18f,
                    gain = volume * 0.035f * accent,
                    wave = ToneWave.TRIANGLE,
                ),
            )
            musicStep++
        }
    }

    override fun ensureUnlocked() {
        if (!closed) platform.unlock()
    }

    override fun close() {
        if (closed) return
        platform.close()
        closed = true
    }

    private companion object {
        const val MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE = 32
        const val MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE = 3
        const val MUSIC_STEP_SECONDS = 0.32f
        val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)
    }
}

internal const val MAX_MUSIC_ADVANCE_DELTA_SECONDS: Float = 0.1f

internal fun selectMusicAdvanceDeltaSeconds(realDeltaSeconds: Float): Float =
    realDeltaSeconds
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, MAX_MUSIC_ADVANCE_DELTA_SECONDS)
        ?: 0f

internal fun selectToneRequests(requests: List<ToneRequest>, limit: Int): List<ToneRequest> = requests
    .distinct()
    .take(limit.coerceAtLeast(0))

/** Narrow mechanical capability supplied by the app's platform composition broker. */
interface TonePlaybackCapability {
    fun unlock()

    fun play(request: ToneRequest)

    fun close()
}

private fun TonePlaybackCapability.playIfAllowed(request: ToneRequest) {
    if (!isToneRequestAllowed(request)) return
    play(request)
}

internal fun isToneRequestAllowed(request: ToneRequest): Boolean =
    isToneRequestAllowed(
        request.frequencyHz,
        request.durationSeconds,
        request.gain,
        request.wave.ordinal,
    )

internal fun isToneRequestAllowed(
    frequencyHz: Float,
    durationSeconds: Float,
    gain: Float,
    wave: Int,
): Boolean = frequencyHz.isFinite() &&
    frequencyHz >= ToneRequestLimits.MIN_FREQUENCY_HZ &&
    frequencyHz <= ToneRequestLimits.MAX_FREQUENCY_HZ &&
    durationSeconds.isFinite() &&
    durationSeconds >= ToneRequestLimits.MIN_DURATION_SECONDS &&
    durationSeconds <= ToneRequestLimits.MAX_DURATION_SECONDS &&
    gain.isFinite() && gain in ToneRequestLimits.MIN_GAIN..ToneRequestLimits.MAX_GAIN &&
    wave in ToneWave.entries.indices
