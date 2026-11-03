// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId

/**
 * Executes accepted gameplay audio work through the mechanical Resource boundary. Cues play when a pulse emits them;
 * music advances only by the real time of an accepted frame, once per frame and after that frame's cues.
 */
internal interface GameplayAudioExecutor {
    fun play(cues: ImmutableList<GameplayAudioCue>)
    fun tick(realDeltaSeconds: Float)
    fun ensureUnlocked()
}

internal class ResourceGameplayAudioExecutor(
    private val audioService: AudioService,
) : GameplayAudioExecutor {
    override fun play(cues: ImmutableList<GameplayAudioCue>) {
        // The common empty/single-cue paths reuse immutable id lists instead of allocating at the
        // display rate; the resource ranks them.
        when (cues.size) {
            0 -> Unit
            1 -> audioService.play(SINGLE_SOUNDS[cues[0].ordinal])
            else -> audioService.play(List(cues.size) { index -> SOUNDS[cues[index].ordinal] })
        }
    }

    override fun tick(realDeltaSeconds: Float) {
        audioService.tick(realDeltaSeconds)
    }

    override fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }
}

private val SOUNDS = GameplayAudioCue.entries.map(GameplayAudioCue::soundId)
private val SINGLE_SOUNDS = SOUNDS.map { listOf(it) }

private fun GameplayAudioCue.soundId(): SoundId = when (this) {
    GameplayAudioCue.UI_CLICK -> SoundId.UI_CLICK
    GameplayAudioCue.DASH -> SoundId.DASH
    GameplayAudioCue.WEAPON_LIGHT -> SoundId.WEAPON_LIGHT
    GameplayAudioCue.WEAPON_HEAVY -> SoundId.WEAPON_HEAVY
    GameplayAudioCue.IMPACT -> SoundId.IMPACT
    GameplayAudioCue.ENEMY_DESTROYED -> SoundId.ENEMY_DESTROYED
    GameplayAudioCue.PICKUP -> SoundId.PICKUP
    GameplayAudioCue.LEVEL_UP -> SoundId.LEVEL_UP
    GameplayAudioCue.OVERHEAT -> SoundId.OVERHEAT
    GameplayAudioCue.RECOVERED -> SoundId.RECOVERED
    GameplayAudioCue.HURT -> SoundId.HURT
    GameplayAudioCue.OVERDRIVE -> SoundId.OVERDRIVE
    GameplayAudioCue.WEAPON_ACQUIRED -> SoundId.WEAPON_ACQUIRED
    GameplayAudioCue.GAME_OVER -> SoundId.GAME_OVER
    GameplayAudioCue.VICTORY -> SoundId.VICTORY
    GameplayAudioCue.SKILL -> SoundId.SKILL
    GameplayAudioCue.APEX -> SoundId.APEX
    GameplayAudioCue.STRUCTURE_COLLAPSE -> SoundId.STRUCTURE_COLLAPSE
    GameplayAudioCue.LEGENDARY_ACQUIRED -> SoundId.LEGENDARY_ACQUIRED
    GameplayAudioCue.CORE_FROZEN -> SoundId.CORE_FROZEN
}
