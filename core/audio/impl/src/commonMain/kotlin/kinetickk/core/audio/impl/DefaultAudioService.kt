// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.audio.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.audio.api.AudioPreferences
import kinetickk.core.audio.api.AudioService

private data class Tone(val frequency: Float, val durationSeconds: Float, val gain: Float, val wave: Int)

class DefaultAudioService internal constructor(
    private val platform: NumericTonePlayer,
) : AudioService {
    constructor() : this(createPlatformTonePlayer())

    private var preferences = AudioPreferences()
    private var musicClock = 0f
    private var musicStep = 0
    private var closed = false

    override fun updatePreferences(preferences: AudioPreferences) {
        if (!closed) this.preferences = preferences
    }

    override fun advance(realDeltaSeconds: Float, cues: List<AudioCue>) {
        if (closed) return
        val volume = preferences.masterVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (preferences.soundEnabled && volume > 0f && cues.size <= MAX_ACCEPTED_CUES) {
            selectSoundCues(cues, MAX_CUES_PER_FRAME).forEach { cue ->
                val tone = cue.tone()
                platform.playSafely(tone.frequency, tone.durationSeconds, tone.gain * volume, tone.wave)
            }
        }

        if (!preferences.musicEnabled || volume <= 0f) {
            musicClock = 0f
            return
        }
        val boundedDelta = realDeltaSeconds.takeIf { it.isFinite() }?.coerceIn(0f, 0.1f) ?: 0f
        musicClock -= boundedDelta
        if (musicClock <= 0f) {
            musicClock += MUSIC_STEP_SECONDS
            val frequency = MUSIC_NOTES[musicStep % MUSIC_NOTES.size]
            val accent = if (musicStep % 8 == 0) 1.35f else 1f
            platform.playSafely(frequency, 0.18f, volume * 0.035f * accent, WAVE_TRIANGLE)
            musicStep++
        }
    }

    override fun ensureUnlocked() {
        if (!closed) runCatching { platform.unlock() }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { platform.close() }
    }

    private fun AudioCue.tone(): Tone = when (this) {
        AudioCue.UI_CLICK -> Tone(520f, 0.035f, 0.11f, WAVE_SINE)
        AudioCue.DASH -> Tone(185f, 0.11f, 0.23f, WAVE_SAW)
        AudioCue.WEAPON_LIGHT -> Tone(420f, 0.055f, 0.1f, WAVE_TRIANGLE)
        AudioCue.WEAPON_HEAVY -> Tone(132f, 0.12f, 0.18f, WAVE_SAW)
        AudioCue.IMPACT -> Tone(92f, 0.07f, 0.2f, WAVE_SQUARE)
        AudioCue.ENEMY_DESTROYED -> Tone(330f, 0.06f, 0.13f, WAVE_TRIANGLE)
        AudioCue.PICKUP -> Tone(710f, 0.055f, 0.13f, WAVE_SINE)
        AudioCue.LEVEL_UP -> Tone(560f, 0.16f, 0.18f, WAVE_TRIANGLE)
        AudioCue.OVERHEAT -> Tone(118f, 0.22f, 0.2f, WAVE_SAW)
        AudioCue.RECOVERED -> Tone(445f, 0.09f, 0.13f, WAVE_SINE)
        AudioCue.HURT -> Tone(76f, 0.13f, 0.22f, WAVE_SQUARE)
        AudioCue.OVERDRIVE -> Tone(820f, 0.2f, 0.2f, WAVE_SAW)
        AudioCue.WEAPON_ACQUIRED -> Tone(640f, 0.18f, 0.2f, WAVE_TRIANGLE)
        AudioCue.PURCHASE -> Tone(490f, 0.1f, 0.16f, WAVE_SINE)
        AudioCue.GAME_OVER -> Tone(64f, 0.34f, 0.24f, WAVE_SAW)
        AudioCue.VICTORY -> Tone(784f, 0.32f, 0.2f, WAVE_TRIANGLE)
    }

    private companion object {
        const val MAX_ACCEPTED_CUES = 32
        const val MAX_CUES_PER_FRAME = 3
        const val MUSIC_STEP_SECONDS = 0.32f
        const val WAVE_SINE = 0
        const val WAVE_SQUARE = 1
        const val WAVE_SAW = 2
        const val WAVE_TRIANGLE = 3
        val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)
    }
}

internal fun selectSoundCues(cues: List<AudioCue>, limit: Int): List<AudioCue> = cues
    .distinct()
    .sortedByDescending { it.priority }
    .take(limit.coerceAtLeast(0))

private val AudioCue.priority: Int
    get() = when (this) {
        AudioCue.GAME_OVER, AudioCue.VICTORY -> 100
        AudioCue.HURT, AudioCue.OVERHEAT -> 90
        AudioCue.DASH, AudioCue.OVERDRIVE -> 80
        AudioCue.WEAPON_HEAVY, AudioCue.IMPACT -> 70
        AudioCue.LEVEL_UP, AudioCue.WEAPON_ACQUIRED, AudioCue.PURCHASE, AudioCue.RECOVERED -> 60
        AudioCue.WEAPON_LIGHT, AudioCue.PICKUP -> 40
        AudioCue.ENEMY_DESTROYED, AudioCue.UI_CLICK -> 20
    }

internal interface NumericTonePlayer {
    fun unlock()
    fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int)
    fun close()
}

private fun NumericTonePlayer.playSafely(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
    if (!isToneRequestAllowed(frequency, durationSeconds, volume, wave)) return
    runCatching { play(frequency, durationSeconds, volume, wave) }
}

internal fun isToneRequestAllowed(
    frequency: Float,
    durationSeconds: Float,
    volume: Float,
    wave: Int,
): Boolean = frequency.isFinite() && frequency in 20f..20_000f &&
    durationSeconds.isFinite() && durationSeconds in 0.001f..1f &&
    volume.isFinite() && volume in 0f..1f && wave in 0..3

internal expect fun createPlatformTonePlayer(): NumericTonePlayer
