// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightCandidate
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.audio.nucleus.protocol.AudioCue
import kinetickk.features.audio.nucleus.protocol.AudioDecisionContext
import kinetickk.features.audio.nucleus.protocol.AudioEffect
import kinetickk.features.audio.nucleus.protocol.AudioEffectRequest
import kinetickk.features.audio.nucleus.protocol.AudioIntent
import kinetickk.features.audio.nucleus.protocol.AudioOperationId
import kinetickk.features.audio.nucleus.protocol.AudioOutputKind
import kinetickk.features.audio.nucleus.protocol.AudioPlaybackSettings
import kinetickk.features.audio.nucleus.protocol.AudioPulse
import kinetickk.features.audio.nucleus.protocol.AudioSemanticOutput
import kinetickk.features.audio.nucleus.state.AudioState
import kinetickk.features.audio.nucleus.transition.AudioNucleus
import kinetickk.features.audio.resources.tone.NumericTonePlayer
import kinetickk.features.audio.resources.tone.PlatformTonePlayer
import kinetickk.features.audio.resources.tone.playSafely
import kinetickk.foundation.collections.toImmutableList

sealed interface AudioDispatchResult {
    data class Committed(val commitRevision: ULong) : AudioDispatchResult
    data class DecisionRejected(val reason: BusinessRejection) : AudioDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : AudioDispatchResult
}

/**
 * Inline + Transient Audio Feature Ball.
 *
 * Tone scheduling and cue selection are pure Nucleus decisions. The injected player is a private,
 * numeric-only Resource capability and is called only after the complete accepted frame publishes.
 */
class AudioFeatureBall internal constructor(
    private val tonePlayer: NumericTonePlayer,
) {
    private val runtime = InlineAcceptedFrameRuntime(
        initialState = AudioState(),
        decider = AudioNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = { AUDIO_STATE_BYTES },
                collectionItemCounts = ::collectionItemCounts,
                isEffect = { true },
                isCommand = { false },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                sourceOrdinal = AudioSemanticOutput::sourceOrdinal,
                hasMatchingOutputKind = ::hasMatchingOutputKind,
                causalBudgetScope = { _, context -> context.operationId.value.toString() },
            ),
        ),
        outputDispatcher = OutputDispatcher(::dispatchAcceptedOutput),
    )
    private var nextOperationId: ULong = 1uL

    fun advance(
        settings: AudioPlaybackSettings,
        realDeltaSeconds: Float,
        cues: List<AudioCue>,
    ): AudioDispatchResult {
        if (cues.size > MAX_INPUT_CUES) {
            return AudioDispatchResult.AdmissionRejected(
                AdmissionFailure.LimitExceeded(
                    limit = MandatoryDecisionLimit.COLLECTION_ITEMS,
                    actual = cues.size.toLong(),
                    maximum = MAX_INPUT_CUES.toLong(),
                    collectionIndex = 0,
                ),
            )
        }
        return submit(
            intent = AudioIntent.Advance(realDeltaSeconds, cues.toImmutableList()),
            settings = settings,
        )
    }

    fun ensureUnlocked(): AudioDispatchResult = submit(AudioIntent.EnsureUnlocked)

    fun close(): AudioDispatchResult = submit(AudioIntent.CloseRequested)

    internal fun snapshotForTesting(): AudioState = runtime.snapshot().state

    private fun submit(
        intent: AudioIntent,
        settings: AudioPlaybackSettings = AudioPlaybackSettings(),
    ): AudioDispatchResult {
        val operationId = reserveOperationId()
            ?: return AudioDispatchResult.AdmissionRejected(AdmissionFailure.OperationIdentityExhausted)
        return when (
            val result = runtime.submit(
                pulse = intent,
                context = AudioDecisionContext(operationId = operationId, settings = settings),
            )
        ) {
            is SubmissionResult.Committed -> AudioDispatchResult.Committed(result.frame.revision.value)
            is SubmissionResult.DecisionRejected -> AudioDispatchResult.DecisionRejected(result.rejection)
            is SubmissionResult.AdmissionRejected -> AudioDispatchResult.AdmissionRejected(result.failure)
        }
    }

    private fun dispatchAcceptedOutput(output: AudioSemanticOutput) {
        when (val effect = (output as AudioEffectRequest).payload) {
            is AudioEffect.PlayTone -> tonePlayer.playSafely(
                frequency = effect.frequency,
                durationSeconds = effect.durationSeconds,
                volume = effect.gain,
                wave = effect.wave,
            )
            AudioEffect.UnlockTonePlayer -> runCatching(tonePlayer::unlock)
            AudioEffect.CloseTonePlayer -> runCatching(tonePlayer::close)
        }
    }

    private fun reserveOperationId(): AudioOperationId? {
        val value = nextOperationId
        if (value == 0uL) return null
        nextOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return AudioOperationId(value)
    }

    companion object {
        fun create(): AudioFeatureBall = AudioFeatureBall(PlatformTonePlayer())

        internal const val MAX_INPUT_CUES = 256

        val LIMITS = DecisionLimits(
            maxInputBytes = 2_048L,
            maxStateBytes = 128L,
            maxCollectionItems = MAX_INPUT_CUES,
            maxOutputsPerDecision = 4,
            maxEffectsPerDecision = 4,
            maxCommandsPerDecision = 0,
            maxCausalDepth = 1,
            maxRetriesPerOperation = 0,
            maxTransitionSteps = 64,
        )

        private fun estimateInputBytes(
            pulse: AudioPulse,
            context: AudioDecisionContext,
        ): Long = when (pulse) {
            is AudioIntent.Advance -> 48L + pulse.cues.size * 4L
            AudioIntent.EnsureUnlocked, AudioIntent.CloseRequested -> 16L
        } + context.transitionArtifact.length * 4L

        private fun collectionItemCounts(
            candidate: PreflightCandidate<
                AudioState,
                AudioPulse,
                AudioDecisionContext,
                AudioSemanticOutput,
            >,
        ): Iterable<Int> = buildList {
            val intent = candidate.pulse
            if (intent is AudioIntent.Advance) add(intent.cues.size)
            add(candidate.decision.outputs.size)
        }

        private fun hasMatchingOutputKind(output: AudioSemanticOutput): Boolean {
            val effect = (output as? AudioEffectRequest)?.payload ?: return false
            return output.semanticHandle.outputKind == when (effect) {
                is AudioEffect.PlayTone -> AudioOutputKind.PLAY_TONE
                AudioEffect.UnlockTonePlayer -> AudioOutputKind.UNLOCK_TONE_PLAYER
                AudioEffect.CloseTonePlayer -> AudioOutputKind.CLOSE_TONE_PLAYER
            }
        }

        private const val AUDIO_STATE_BYTES = 64L
    }
}
