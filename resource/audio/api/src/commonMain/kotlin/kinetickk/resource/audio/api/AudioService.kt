// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.api

enum class ToneWave {
    SINE,
    SQUARE,
    SAW,
    TRIANGLE,
}

object ToneRequestLimits {
    const val MIN_FREQUENCY_HZ: Float = 20f
    const val MAX_FREQUENCY_HZ: Float = 20_000f
    const val MIN_DURATION_SECONDS: Float = 0.001f
    const val MAX_DURATION_SECONDS: Float = 1f
    const val MIN_GAIN: Float = 0f
    const val MAX_GAIN: Float = 1f
}

/** Validated mechanical work accepted by the platform audio resource. */
data class ToneRequest(
    val frequencyHz: Float,
    val durationSeconds: Float,
    val gain: Float,
    val wave: ToneWave,
) {
    init {
        require(
            frequencyHz.isFinite() &&
                frequencyHz >= ToneRequestLimits.MIN_FREQUENCY_HZ &&
                frequencyHz <= ToneRequestLimits.MAX_FREQUENCY_HZ,
        ) {
            "Tone frequency must be finite and between 20 and 20,000 Hz"
        }
        require(
            durationSeconds.isFinite() &&
                durationSeconds >= ToneRequestLimits.MIN_DURATION_SECONDS &&
                durationSeconds <= ToneRequestLimits.MAX_DURATION_SECONDS,
        ) {
            "Tone duration must be finite and between 0.001 and 1 second"
        }
        require(gain.isFinite() && gain in ToneRequestLimits.MIN_GAIN..ToneRequestLimits.MAX_GAIN) {
            "Tone gain must be finite and between 0 and 1"
        }
    }
}

/** The persisted preference slice observed by application-owned audio. */
data class AudioPreferences(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
)

/** Application-scoped lifecycle, bounded effect playback and the music clock. */
interface AudioService {
    /** Prepares the whole catalog (effects and music) plus [requests]; never plays or unlocks. */
    suspend fun prepare(requests: List<ToneRequest> = emptyList()) = Unit

    fun updatePreferences(preferences: AudioPreferences)

    /** Legacy combined call; removed in Close-out. */
    fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit

    /** Effects only: more than 32 ids drop all (as today); stable priority sort; the first 3 distinct play. Never moves music. */
    fun play(sounds: List<SoundId>) = Unit

    /** Music only: advances the sequencer by one display frame; resets while music is off. */
    fun tick(realDeltaSeconds: Float) = Unit

    fun ensureUnlocked()
    fun close()
}
