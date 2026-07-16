// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.foundation.collections.ImmutableList
import kotlin.jvm.JvmInline

object AudioProtocol {
    const val VERSION = "1.0.0"
    const val STATE_SCHEMA_VERSION = 1
    const val TRANSITION_ARTIFACT = "audio-v1"
    const val BALL_INSTANCE_ID = "kinetickk.local/Audio/local-player"
}

sealed interface AudioPulse

sealed interface AudioIntent : AudioPulse {
    data class Advance(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<AudioCue>,
    ) : AudioIntent

    data object EnsureUnlocked : AudioIntent
    data object CloseRequested : AudioIntent
}

/** The field-minimized Settings read supplied explicitly to one Audio decision. */
data class AudioPlaybackSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val masterVolume: Float = 0.65f,
)

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

@JvmInline
value class AudioOperationId(val value: ULong)

enum class AudioOutputKind {
    PLAY_TONE,
    UNLOCK_TONE_PLAYER,
    CLOSE_TONE_PLAYER,
}

data class AudioSemanticHandle(
    val operationId: AudioOperationId,
    val outputKind: AudioOutputKind,
    val localOrdinalOrName: String,
)

sealed interface AudioSemanticOutput {
    val semanticHandle: AudioSemanticHandle
    val sourceOrdinal: UInt
}

data class AudioEffectRequest(
    override val semanticHandle: AudioSemanticHandle,
    override val sourceOrdinal: UInt,
    val payload: AudioEffect,
) : AudioSemanticOutput

sealed interface AudioEffect {
    data class PlayTone(
        val frequency: Float,
        val durationSeconds: Float,
        val gain: Float,
        val wave: Int,
    ) : AudioEffect

    data object UnlockTonePlayer : AudioEffect
    data object CloseTonePlayer : AudioEffect
}

data class AudioDecisionContext(
    val operationId: AudioOperationId,
    val settings: AudioPlaybackSettings = AudioPlaybackSettings(),
    val transitionArtifact: String = AudioProtocol.TRANSITION_ARTIFACT,
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
)

sealed interface AudioRejection : BusinessRejection {
    data class InvalidContext(val field: String, val reason: String) : AudioRejection
}
