// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.features.audio.nucleus.protocol.AudioCue
import kinetickk.features.audio.nucleus.protocol.AudioDecisionContext
import kinetickk.features.audio.nucleus.protocol.AudioEffect
import kinetickk.features.audio.nucleus.protocol.AudioEffectRequest
import kinetickk.features.audio.nucleus.protocol.AudioIntent
import kinetickk.features.audio.nucleus.protocol.AudioOutputKind
import kinetickk.features.audio.nucleus.protocol.AudioProtocol
import kinetickk.features.audio.nucleus.protocol.AudioPulse
import kinetickk.features.audio.nucleus.protocol.AudioRejection
import kinetickk.features.audio.nucleus.protocol.AudioSemanticHandle
import kinetickk.features.audio.nucleus.protocol.AudioSemanticOutput
import kinetickk.features.audio.nucleus.state.AudioState
import kinetickk.foundation.collections.toImmutableList

internal class AudioNucleus :
    Decider<AudioState, AudioPulse, AudioDecisionContext, AudioSemanticOutput> {
    override fun decide(
        state: AudioState,
        pulse: AudioPulse,
        context: AudioDecisionContext,
    ): DecisionResult<AudioState, AudioSemanticOutput> {
        if (context.transitionArtifact != AudioProtocol.TRANSITION_ARTIFACT) {
            return Rejected(AudioRejection.InvalidContext("transitionArtifact", "unsupported artifact"))
        }
        if (context.operationId.value == 0uL) {
            return Rejected(AudioRejection.InvalidContext("operationId", "must be reserved"))
        }

        return when (pulse) {
            is AudioIntent.Advance -> decideAdvance(state, pulse, context)
            AudioIntent.EnsureUnlocked -> decideUnlock(state, context)
            AudioIntent.CloseRequested -> decideClose(state, context)
        }
    }

    private fun decideAdvance(
        state: AudioState,
        intent: AudioIntent.Advance,
        context: AudioDecisionContext,
    ): DecisionResult<AudioState, AudioSemanticOutput> {
        if (state.closed) return accepted(state.copy(transitionSteps = 1), emptyList())

        val outputs = AudioOutputBuilder(context.operationId)
        val settings = context.settings
        val volume = settings.masterVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        var transitionSteps = 1

        if (settings.soundEnabled && volume > 0f && intent.cues.size <= MAX_ACCEPTED_CUES) {
            val selected = selectAudioCues(intent.cues, MAX_CUES_PER_ADVANCE)
            transitionSteps += intent.cues.size + selected.size
            selected.forEachIndexed { index, cue ->
                val tone = cue.tone()
                outputs.effect(
                    kind = AudioOutputKind.PLAY_TONE,
                    localName = "cue-$index-${cue.name.lowercase()}",
                    payload = AudioEffect.PlayTone(
                        frequency = tone.frequency,
                        durationSeconds = tone.durationSeconds,
                        gain = tone.gain * volume,
                        wave = tone.wave,
                    ),
                )
            }
        } else {
            transitionSteps++
        }

        var nextClock = state.musicClockSeconds
        var nextStep = state.musicStep
        if (!settings.musicEnabled || volume <= 0f) {
            nextClock = 0f
        } else {
            val boundedDelta = intent.realDeltaSeconds
                .takeIf { it.isFinite() }
                ?.coerceIn(0f, MAX_REAL_DELTA_SECONDS)
                ?: 0f
            nextClock -= boundedDelta
            transitionSteps++
            if (nextClock <= 0f) {
                nextClock += MUSIC_STEP_SECONDS
                val noteIndex = (nextStep % MUSIC_NOTES.size.toULong()).toInt()
                val frequency = MUSIC_NOTES[noteIndex]
                val accent = if (nextStep % 8uL == 0uL) 1.35f else 1f
                outputs.effect(
                    kind = AudioOutputKind.PLAY_TONE,
                    localName = "music-$nextStep",
                    payload = AudioEffect.PlayTone(
                        frequency = frequency,
                        durationSeconds = 0.18f,
                        gain = volume * 0.035f * accent,
                        wave = WAVE_TRIANGLE,
                    ),
                )
                nextStep++
                transitionSteps++
            }
        }

        return accepted(
            state.copy(
                musicClockSeconds = nextClock,
                musicStep = nextStep,
                transitionSteps = transitionSteps,
            ),
            outputs.build(),
        )
    }

    private fun decideUnlock(
        state: AudioState,
        context: AudioDecisionContext,
    ): DecisionResult<AudioState, AudioSemanticOutput> {
        if (state.closed) return accepted(state.copy(transitionSteps = 1), emptyList())
        val outputs = AudioOutputBuilder(context.operationId).apply {
            effect(
                kind = AudioOutputKind.UNLOCK_TONE_PLAYER,
                localName = "unlock",
                payload = AudioEffect.UnlockTonePlayer,
            )
        }
        return accepted(state.copy(transitionSteps = 1), outputs.build())
    }

    private fun decideClose(
        state: AudioState,
        context: AudioDecisionContext,
    ): DecisionResult<AudioState, AudioSemanticOutput> {
        if (state.closed) return accepted(state.copy(transitionSteps = 1), emptyList())
        val outputs = AudioOutputBuilder(context.operationId).apply {
            effect(
                kind = AudioOutputKind.CLOSE_TONE_PLAYER,
                localName = "close",
                payload = AudioEffect.CloseTonePlayer,
            )
        }
        return accepted(
            state.copy(
                musicClockSeconds = 0f,
                closed = true,
                transitionSteps = 1,
            ),
            outputs.build(),
        )
    }

    private fun accepted(
        state: AudioState,
        outputs: List<AudioSemanticOutput>,
    ): DecisionResult<AudioState, AudioSemanticOutput> = Accepted(Decision(state, outputs))

    private companion object {
        const val MAX_ACCEPTED_CUES = 32
        const val MAX_CUES_PER_ADVANCE = 3
        const val MAX_REAL_DELTA_SECONDS = 0.1f
        const val MUSIC_STEP_SECONDS = 0.32f
        const val WAVE_TRIANGLE = 3
        val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)
    }
}

internal fun selectAudioCues(cues: Iterable<AudioCue>, limit: Int): List<AudioCue> = cues
    .distinct()
    .sortedByDescending { it.priority }
    .take(limit.coerceAtLeast(0))

private data class Tone(
    val frequency: Float,
    val durationSeconds: Float,
    val gain: Float,
    val wave: Int,
)

private fun AudioCue.tone(): Tone = when (this) {
    AudioCue.UI_CLICK -> Tone(520f, 0.035f, 0.11f, 0)
    AudioCue.DASH -> Tone(185f, 0.11f, 0.23f, 2)
    AudioCue.WEAPON_LIGHT -> Tone(420f, 0.055f, 0.1f, 3)
    AudioCue.WEAPON_HEAVY -> Tone(132f, 0.12f, 0.18f, 2)
    AudioCue.IMPACT -> Tone(92f, 0.07f, 0.2f, 1)
    AudioCue.ENEMY_DESTROYED -> Tone(330f, 0.06f, 0.13f, 3)
    AudioCue.PICKUP -> Tone(710f, 0.055f, 0.13f, 0)
    AudioCue.LEVEL_UP -> Tone(560f, 0.16f, 0.18f, 3)
    AudioCue.OVERHEAT -> Tone(118f, 0.22f, 0.2f, 2)
    AudioCue.RECOVERED -> Tone(445f, 0.09f, 0.13f, 0)
    AudioCue.HURT -> Tone(76f, 0.13f, 0.22f, 1)
    AudioCue.OVERDRIVE -> Tone(820f, 0.2f, 0.2f, 2)
    AudioCue.WEAPON_ACQUIRED -> Tone(640f, 0.18f, 0.2f, 3)
    AudioCue.PURCHASE -> Tone(490f, 0.1f, 0.16f, 0)
    AudioCue.GAME_OVER -> Tone(64f, 0.34f, 0.24f, 2)
    AudioCue.VICTORY -> Tone(784f, 0.32f, 0.2f, 3)
}

private val AudioCue.priority: Int
    get() = when (this) {
        AudioCue.GAME_OVER, AudioCue.VICTORY -> 100
        AudioCue.HURT, AudioCue.OVERHEAT -> 90
        AudioCue.DASH, AudioCue.OVERDRIVE -> 80
        AudioCue.WEAPON_HEAVY, AudioCue.IMPACT -> 70
        AudioCue.LEVEL_UP,
        AudioCue.WEAPON_ACQUIRED,
        AudioCue.PURCHASE,
        AudioCue.RECOVERED,
        -> 60
        AudioCue.WEAPON_LIGHT, AudioCue.PICKUP -> 40
        AudioCue.ENEMY_DESTROYED, AudioCue.UI_CLICK -> 20
    }

private class AudioOutputBuilder(
    private val operationId: kinetickk.features.audio.nucleus.protocol.AudioOperationId,
) {
    private val outputs = mutableListOf<AudioSemanticOutput>()

    fun effect(
        kind: AudioOutputKind,
        localName: String,
        payload: AudioEffect,
    ) {
        outputs += AudioEffectRequest(
            semanticHandle = AudioSemanticHandle(operationId, kind, localName),
            sourceOrdinal = outputs.size.toUInt(),
            payload = payload,
        )
    }

    fun build(): List<AudioSemanticOutput> = outputs.toImmutableList()
}
