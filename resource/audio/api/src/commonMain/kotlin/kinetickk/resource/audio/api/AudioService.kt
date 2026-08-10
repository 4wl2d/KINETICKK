// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.api

enum class ToneWave {
    SINE,
    SQUARE,
    SAW,
    TRIANGLE,
}

/** Validated mechanical work accepted by the platform audio resource. */
data class ToneRequest(
    val frequencyHz: Float,
    val durationSeconds: Float,
    val gain: Float,
    val wave: ToneWave,
) {
    init {
        require(frequencyHz.isFinite() && frequencyHz in 20f..20_000f) {
            "Tone frequency must be finite and between 20 and 20,000 Hz"
        }
        require(durationSeconds.isFinite() && durationSeconds in 0.001f..1f) {
            "Tone duration must be finite and between 0.001 and 1 second"
        }
        require(gain.isFinite() && gain in 0f..1f) {
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

/** Application-scoped lifecycle and bounded mechanical tone sink. */
interface AudioService {
    fun updatePreferences(preferences: AudioPreferences)
    fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>)
    fun ensureUnlocked()
    fun close()
}
