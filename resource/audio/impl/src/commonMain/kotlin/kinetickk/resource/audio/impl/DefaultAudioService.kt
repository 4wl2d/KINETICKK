// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
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

    override suspend fun prepare(requests: List<ToneRequest>) {
        if (!closed) platform.prepare(ToneCatalog.effectTones + MUSIC_TONES + requests)
    }

    override fun updatePreferences(preferences: AudioPreferences) {
        if (!closed) this.preferences = preferences
    }

    override fun play(sounds: List<SoundId>) {
        if (closed) return
        val volume = effectiveVolume()
        if (!acceptsEffects(volume, sounds.size)) return
        // A stable sort keeps the emission order among equal priorities.
        val ranked = if (sounds.size == 1) sounds else sounds.sortedByDescending(ToneCatalog::priority)
        selectDistinct(ranked, MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE).forEach { sound ->
            playEffect(ToneCatalog.request(sound), volume)
        }
    }

    override fun tick(realDeltaSeconds: Float) {
        if (closed) return
        val volume = effectiveVolume()
        if (!preferences.musicEnabled || volume <= 0f) {
            musicClock = 0f
            return
        }
        // A zero delta is not a display frame: it must not start a note on an expired clock.
        val delta = selectMusicAdvanceDeltaSeconds(realDeltaSeconds)
        if (delta <= 0f) return
        musicClock -= delta
        if (musicClock <= 0f) {
            musicClock += MUSIC_STEP_SECONDS
            val frequency = MUSIC_NOTES[musicStep % MUSIC_NOTES.size]
            val accent = if (musicStep % 8 == 0) 1.35f else 1f
            platform.playIfAllowed(musicTone(frequency, gain = volume * 0.035f * accent))
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

    private fun effectiveVolume(): Float = preferences.masterVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    private fun acceptsEffects(volume: Float, count: Int): Boolean =
        preferences.soundEnabled && volume > 0f && count > 0 && count <= MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE

    private fun playEffect(request: ToneRequest, volume: Float) {
        platform.playIfAllowed(request.copy(gain = request.gain * volume))
    }

    private companion object {
        const val MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE = 32
        const val MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE = 3
        const val MUSIC_STEP_SECONDS = 0.32f
        val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)
        val MUSIC_TONES = MUSIC_NOTES.distinct().map { musicTone(it, gain = 1f) }

        /** The one music tone shape, so every note matches the prepared bank. */
        fun musicTone(frequencyHz: Float, gain: Float) = ToneRequest(frequencyHz, 0.18f, gain, ToneWave.TRIANGLE)
    }
}

internal const val MAX_MUSIC_ADVANCE_DELTA_SECONDS: Float = 0.1f

internal fun selectMusicAdvanceDeltaSeconds(realDeltaSeconds: Float): Float =
    realDeltaSeconds
        .takeIf { it.isFinite() }
        ?.coerceIn(0f, MAX_MUSIC_ADVANCE_DELTA_SECONDS)
        ?: 0f

internal fun <T> selectDistinct(items: List<T>, limit: Int): List<T> {
    if (limit <= 0 || items.isEmpty()) return emptyList()
    if (items.size == 1 || limit == 1) return listOf(items[0])
    // Playback admits at most three distinct effects. Stop once they are selected instead of
    // building a set for the full input and then copying its prefix into another list.
    val selected = ArrayList<T>(minOf(limit, items.size))
    for (item in items) {
        if (item !in selected) {
            selected.add(item)
            if (selected.size == limit) break
        }
    }
    return selected
}

/** Narrow mechanical capability supplied by the app's platform composition broker. */
interface TonePlaybackCapability {
    suspend fun prepare(requests: List<ToneRequest>) = Unit

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
