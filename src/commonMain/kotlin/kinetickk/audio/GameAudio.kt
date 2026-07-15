package kinetickk.audio

import kinetickk.model.GameSettings

enum class SoundCue {
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

private data class Tone(val frequency: Float, val durationSeconds: Float, val gain: Float, val wave: Int)

class GameAudio {
    private val platform = PlatformTonePlayer()
    private var musicClock = 0f
    private var musicStep = 0

    fun update(settings: GameSettings, realDelta: Float, cues: List<SoundCue>) {
        val volume = settings.masterVolume.coerceIn(0f, 1f)
        if (settings.soundEnabled && volume > 0f) {
            selectSoundCues(cues, MAX_CUES_PER_FRAME).forEach { cue ->
                val tone = cue.tone()
                platform.play(tone.frequency, tone.durationSeconds, tone.gain * volume, tone.wave)
            }
        }

        if (!settings.musicEnabled || volume <= 0f) {
            musicClock = 0f
            return
        }
        musicClock -= realDelta.coerceIn(0f, 0.1f)
        if (musicClock <= 0f) {
            musicClock += MUSIC_STEP_SECONDS
            val frequency = MUSIC_NOTES[musicStep % MUSIC_NOTES.size]
            val accent = if (musicStep % 8 == 0) 1.35f else 1f
            platform.play(frequency, 0.18f, volume * 0.035f * accent, WAVE_TRIANGLE)
            musicStep++
        }
    }

    fun unlock() = platform.unlock()

    fun close() = platform.close()

    private fun SoundCue.tone(): Tone = when (this) {
        SoundCue.UI_CLICK -> Tone(520f, 0.035f, 0.11f, WAVE_SINE)
        SoundCue.DASH -> Tone(185f, 0.11f, 0.23f, WAVE_SAW)
        SoundCue.WEAPON_LIGHT -> Tone(420f, 0.055f, 0.1f, WAVE_TRIANGLE)
        SoundCue.WEAPON_HEAVY -> Tone(132f, 0.12f, 0.18f, WAVE_SAW)
        SoundCue.IMPACT -> Tone(92f, 0.07f, 0.2f, WAVE_SQUARE)
        SoundCue.ENEMY_DESTROYED -> Tone(330f, 0.06f, 0.13f, WAVE_TRIANGLE)
        SoundCue.PICKUP -> Tone(710f, 0.055f, 0.13f, WAVE_SINE)
        SoundCue.LEVEL_UP -> Tone(560f, 0.16f, 0.18f, WAVE_TRIANGLE)
        SoundCue.OVERHEAT -> Tone(118f, 0.22f, 0.2f, WAVE_SAW)
        SoundCue.RECOVERED -> Tone(445f, 0.09f, 0.13f, WAVE_SINE)
        SoundCue.HURT -> Tone(76f, 0.13f, 0.22f, WAVE_SQUARE)
        SoundCue.OVERDRIVE -> Tone(820f, 0.2f, 0.2f, WAVE_SAW)
        SoundCue.WEAPON_ACQUIRED -> Tone(640f, 0.18f, 0.2f, WAVE_TRIANGLE)
        SoundCue.PURCHASE -> Tone(490f, 0.1f, 0.16f, WAVE_SINE)
        SoundCue.GAME_OVER -> Tone(64f, 0.34f, 0.24f, WAVE_SAW)
        SoundCue.VICTORY -> Tone(784f, 0.32f, 0.2f, WAVE_TRIANGLE)
    }

    private companion object {
        const val MAX_CUES_PER_FRAME = 3
        const val MUSIC_STEP_SECONDS = 0.32f
        const val WAVE_SINE = 0
        const val WAVE_SQUARE = 1
        const val WAVE_SAW = 2
        const val WAVE_TRIANGLE = 3
        val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)
    }
}

internal fun selectSoundCues(cues: List<SoundCue>, limit: Int): List<SoundCue> = cues
    .distinct()
    .sortedByDescending { it.priority }
    .take(limit.coerceAtLeast(0))

private val SoundCue.priority: Int
    get() = when (this) {
        SoundCue.GAME_OVER, SoundCue.VICTORY -> 100
        SoundCue.HURT, SoundCue.OVERHEAT -> 90
        SoundCue.DASH, SoundCue.OVERDRIVE -> 80
        SoundCue.WEAPON_HEAVY, SoundCue.IMPACT -> 70
        SoundCue.LEVEL_UP, SoundCue.WEAPON_ACQUIRED, SoundCue.PURCHASE, SoundCue.RECOVERED -> 60
        SoundCue.WEAPON_LIGHT, SoundCue.PICKUP -> 40
        SoundCue.ENEMY_DESTROYED, SoundCue.UI_CLICK -> 20
    }

internal expect class PlatformTonePlayer() {
    fun unlock()
    fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int)
    fun close()
}
