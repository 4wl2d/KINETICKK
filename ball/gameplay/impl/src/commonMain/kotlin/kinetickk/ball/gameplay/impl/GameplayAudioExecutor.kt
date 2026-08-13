// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave

/** Executes accepted gameplay audio work through the mechanical Resource boundary. */
internal interface GameplayAudioExecutor {
    fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>)
    fun ensureUnlocked()
}

internal class ResourceGameplayAudioExecutor(
    private val audioService: AudioService,
) : GameplayAudioExecutor {
    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        audioService.advance(
            realDeltaSeconds,
            cues.sortedByDescending(GameplayAudioCue::priority).map(GameplayAudioCue::toToneRequest),
        )
    }

    override fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }
}

private fun GameplayAudioCue.toToneRequest(): ToneRequest = when (this) {
    GameplayAudioCue.UI_CLICK -> ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)
    GameplayAudioCue.DASH -> ToneRequest(185f, 0.11f, 0.23f, ToneWave.SAW)
    GameplayAudioCue.WEAPON_LIGHT -> ToneRequest(420f, 0.055f, 0.1f, ToneWave.TRIANGLE)
    GameplayAudioCue.WEAPON_HEAVY -> ToneRequest(132f, 0.12f, 0.18f, ToneWave.SAW)
    GameplayAudioCue.IMPACT -> ToneRequest(92f, 0.07f, 0.2f, ToneWave.SQUARE)
    GameplayAudioCue.ENEMY_DESTROYED -> ToneRequest(330f, 0.06f, 0.13f, ToneWave.TRIANGLE)
    GameplayAudioCue.PICKUP -> ToneRequest(710f, 0.055f, 0.13f, ToneWave.SINE)
    GameplayAudioCue.LEVEL_UP -> ToneRequest(560f, 0.16f, 0.18f, ToneWave.TRIANGLE)
    GameplayAudioCue.OVERHEAT -> ToneRequest(118f, 0.22f, 0.2f, ToneWave.SAW)
    GameplayAudioCue.RECOVERED -> ToneRequest(445f, 0.09f, 0.13f, ToneWave.SINE)
    GameplayAudioCue.HURT -> ToneRequest(76f, 0.13f, 0.22f, ToneWave.SQUARE)
    GameplayAudioCue.OVERDRIVE -> ToneRequest(820f, 0.2f, 0.2f, ToneWave.SAW)
    GameplayAudioCue.WEAPON_ACQUIRED -> ToneRequest(640f, 0.18f, 0.2f, ToneWave.TRIANGLE)
    GameplayAudioCue.GAME_OVER -> ToneRequest(64f, 0.34f, 0.24f, ToneWave.SAW)
    GameplayAudioCue.VICTORY -> ToneRequest(784f, 0.32f, 0.2f, ToneWave.TRIANGLE)
}

private val GameplayAudioCue.priority: Int
    get() = when (this) {
        GameplayAudioCue.GAME_OVER, GameplayAudioCue.VICTORY -> 100
        GameplayAudioCue.HURT, GameplayAudioCue.OVERHEAT -> 90
        GameplayAudioCue.DASH, GameplayAudioCue.OVERDRIVE -> 80
        GameplayAudioCue.WEAPON_HEAVY, GameplayAudioCue.IMPACT -> 70
        GameplayAudioCue.LEVEL_UP,
        GameplayAudioCue.WEAPON_ACQUIRED,
        GameplayAudioCue.RECOVERED,
        -> 60
        GameplayAudioCue.WEAPON_LIGHT, GameplayAudioCue.PICKUP -> 40
        GameplayAudioCue.ENEMY_DESTROYED, GameplayAudioCue.UI_CLICK -> 20
    }
