// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.audio.api

/** A semantic audio event. Feature code never depends on a platform sound implementation. */
enum class AudioCue {
    UI_CLICK,
    DASH,
    WEAPON_LIGHT,
    WEAPON_HEAVY,
    IMPACT,
    ENEMY_DESTROYED,
    PICKUP,
    LEVEL_UP,
    OVERHEAT,
    RECOVERED,
    HURT,
    OVERDRIVE,
    WEAPON_ACQUIRED,
    PURCHASE,
    GAME_OVER,
    VICTORY,
}

/** The persisted preference slice observed by application-owned audio. */
data class AudioPreferences(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
)

/** Application-scoped audio lifecycle and cue sink. */
interface AudioService {
    fun updatePreferences(preferences: AudioPreferences)
    fun advance(realDeltaSeconds: Float, cues: List<AudioCue>)
    fun ensureUnlocked()
    fun close()
}
