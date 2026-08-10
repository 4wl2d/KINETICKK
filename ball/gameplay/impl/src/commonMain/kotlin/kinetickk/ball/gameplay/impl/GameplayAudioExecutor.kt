// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioCue
import kinetickk.resource.audio.api.AudioService

/** Executes accepted gameplay audio work through the mechanical Resource boundary. */
internal interface GameplayAudioExecutor {
    fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>)
    fun ensureUnlocked()
}

internal class ResourceGameplayAudioExecutor(
    private val audioService: AudioService,
) : GameplayAudioExecutor {
    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        audioService.advance(realDeltaSeconds, cues.map(GameplayAudioCue::toResourceCue))
    }

    override fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }
}

private fun GameplayAudioCue.toResourceCue(): AudioCue = when (this) {
    GameplayAudioCue.UI_CLICK -> AudioCue.UI_CLICK
    GameplayAudioCue.DASH -> AudioCue.DASH
    GameplayAudioCue.WEAPON_LIGHT -> AudioCue.WEAPON_LIGHT
    GameplayAudioCue.WEAPON_HEAVY -> AudioCue.WEAPON_HEAVY
    GameplayAudioCue.IMPACT -> AudioCue.IMPACT
    GameplayAudioCue.ENEMY_DESTROYED -> AudioCue.ENEMY_DESTROYED
    GameplayAudioCue.PICKUP -> AudioCue.PICKUP
    GameplayAudioCue.LEVEL_UP -> AudioCue.LEVEL_UP
    GameplayAudioCue.OVERHEAT -> AudioCue.OVERHEAT
    GameplayAudioCue.RECOVERED -> AudioCue.RECOVERED
    GameplayAudioCue.HURT -> AudioCue.HURT
    GameplayAudioCue.OVERDRIVE -> AudioCue.OVERDRIVE
    GameplayAudioCue.WEAPON_ACQUIRED -> AudioCue.WEAPON_ACQUIRED
    GameplayAudioCue.GAME_OVER -> AudioCue.GAME_OVER
    GameplayAudioCue.VICTORY -> AudioCue.VICTORY
}
