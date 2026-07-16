// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.AdditionalLimitMeasurement
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightCandidate
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.protocol.EffectRequest
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameEffect
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GamePulse
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProgressPersistenceUnknownReason
import kinetickk.features.game.nucleus.protocol.ProgressProvider
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticHandle
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.features.game.nucleus.read.CommittedStateSnapshot
import kinetickk.features.game.nucleus.read.ConsistencyStamp
import kinetickk.features.game.nucleus.read.ReadContext
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.features.game.nucleus.transition.GameBallState
import kinetickk.features.game.nucleus.transition.GameNucleus
import kinetickk.features.game.nucleus.transition.initialGameBallState
import kinetickk.features.game.resources.audio.AudioResource
import kinetickk.features.game.resources.progress.ProgressPersistResult
import kinetickk.features.game.resources.progress.ProgressProviderId
import kinetickk.features.game.resources.progress.ProgressResourceFailure
import kinetickk.features.game.resources.progress.ProgressStore
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

sealed interface GameDispatchResult {
    data class Committed(
        /** Commit created directly by the submitted root Intent. */
        val sourceCommitRevision: ULong,
        /** Read from the final committed snapshot after the bounded synchronous causal loop. */
        val projectionRead: ReadResult<GameProjection>,
        /** Drop-eligible presentation consequences from the accepted root frame. */
        val visualFxCues: ImmutableList<VisualFxCue>,
        val continuationStatus: GameContinuationStatus,
    ) : GameDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : GameDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : GameDispatchResult
}

sealed interface GameCompletionAttemptFailure {
    data class DecisionRejected(val reason: BusinessRejection) : GameCompletionAttemptFailure
    data class AdmissionRejected(val reason: AdmissionFailure) : GameCompletionAttemptFailure
}

/** Mechanical status of the one fixed synchronous-completion slot owned by this Inline Ball. */
sealed interface GameContinuationStatus {
    data object Idle : GameContinuationStatus

    data class Retained(
        val handle: SemanticHandle,
        val causalBudgetScope: OperationId,
        val lastFailure: GameCompletionAttemptFailure?,
    ) : GameContinuationStatus
}

/**
 * The single behavior-authoritative LocalGame Feature Ball.
 *
 * The Ball owns one Inline runtime and exposes only closed Pulses and canonically stamped query
 * results. Resource capabilities are private bindings and run strictly after the complete
 * accepted frame has been published. One pre-reserved slot retains the sole synchronous progress
 * completion; a Fact is cleared only after its own Decision commits.
 */
class GameFeatureBall internal constructor(
    initialState: GameBallState,
    private val progressStore: ProgressStore,
    private val audioResource: AudioResource,
) {
    private data class RetainedCompletion(
        val fact: GameFact,
        val context: GameDecisionContext,
        val lastFailure: GameCompletionAttemptFailure? = null,
    )

    private data class ProjectionCache(
        val revision: ULong,
        val projection: GameProjection,
    )

    private var pendingCompletion: RetainedCompletion? = null
    private val runtime = InlineAcceptedFrameRuntime(
        initialState = initialState,
        decider = GameNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = ::estimateStateBytes,
                collectionItemCounts = ::collectionItemCounts,
                isEffect = { output -> output is EffectRequest },
                isCommand = { false },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                additionalLimits = { candidate ->
                    candidate.decision.nextState.model.domainCollectionLimits().map { limit ->
                        AdditionalLimitMeasurement(
                            name = limit.name,
                            actual = limit.size.toLong(),
                            maximum = limit.maximum.toLong(),
                        )
                    }
                },
                sourceOrdinal = SemanticOutput::sourceOrdinal,
                hasMatchingOutputKind = ::hasMatchingOutputKind,
                synchronousCompletionCount = { candidate ->
                    candidate.decision.outputs.count { output ->
                        output is EffectRequest && output.payload is GameEffect.PersistProgress
                    }
                },
                availableSynchronousCompletionSlots = {
                    if (pendingCompletion == null) COMPLETION_CAPACITY else 0
                },
                causalBudgetScope = { _, context -> context.causalBudgetScope.value.toString() },
            ),
        ),
        outputDispatcher = OutputDispatcher(::dispatchAcceptedOutput),
    )
    private var projectionCache: ProjectionCache? = null
    private var nextRootOperationId: ULong = 1uL

    fun dispatch(intent: GameIntent): GameDispatchResult {
        resumeRetainedCompletion()
        pendingCompletion?.let { retained ->
            return GameDispatchResult.AdmissionRejected(
                AdmissionFailure.CausalBudgetExceeded(
                    scope = retained.context.causalBudgetScope.value.toString(),
                    limit = LIMITS.maxCausalDepth,
                ),
            )
        }

        val operationId = reserveRootOperationId()
            ?: return GameDispatchResult.AdmissionRejected(AdmissionFailure.OperationIdentityExhausted)
        val rootResult = runtime.submit(
            pulse = intent,
            context = GameDecisionContext(
                operationId = operationId,
                causalBudgetScope = operationId,
            ),
        )
        if (rootResult is SubmissionResult.Committed) {
            resumeRetainedCompletion()
        }
        return when (rootResult) {
            is SubmissionResult.Committed -> GameDispatchResult.Committed(
                sourceCommitRevision = rootResult.frame.revision.value,
                projectionRead = projectionRead(),
                visualFxCues = rootResult.frame.outputs
                    .filterIsInstance<ProjectionOutput>()
                    .flatMap { output ->
                        when (val payload = output.payload) {
                            is GameProjectionPayload.GameProjectionChanged ->
                                payload.visualFxCues
                        }
                    }
                    .toImmutableList(),
                continuationStatus = completionStatus(),
            )
            is SubmissionResult.DecisionRejected ->
                GameDispatchResult.DecisionRejected(rootResult.rejection)
            is SubmissionResult.AdmissionRejected ->
                GameDispatchResult.AdmissionRejected(rootResult.failure)
        }
    }

    /** Canonical Query -> ReadResult mapping for Game protocol 1.0.0. */
    fun query(query: GameQuery): GameQueryResult = read(
        query = query,
        context = ReadContext(
            protocolVersion = GameProjection.PROTOCOL_VERSION,
            actorContext = null,
        ),
    )

    internal fun read(query: GameQuery, context: ReadContext): GameQueryResult {
        val frame = runtime.snapshot()
        val snapshot = CommittedStateSnapshot(
            ballInstanceId = GameProjection.BALL_INSTANCE_ID,
            commitRevision = frame.revision.value,
            stateSchemaVersion = GameProjection.STATE_SCHEMA_VERSION,
            state = frame.state,
        )
        return readSnapshot(snapshot, query, context)
    }

    /** Reports whether the one fixed synchronous-completion slot is occupied. */
    fun completionStatus(): GameContinuationStatus = pendingCompletion?.let { retained ->
        GameContinuationStatus.Retained(
            handle = retained.fact.handle,
            causalBudgetScope = retained.context.causalBudgetScope,
            lastFailure = retained.lastFailure,
        )
    } ?: GameContinuationStatus.Idle

    /** Resumes the same retained causal scope; it never creates a replacement root operation. */
    fun resumeRetainedCompletion(): GameContinuationStatus {
        val retained = pendingCompletion ?: return GameContinuationStatus.Idle
        when (val result = runtime.submit(retained.fact, retained.context)) {
            is SubmissionResult.Committed -> pendingCompletion = null
            is SubmissionResult.DecisionRejected -> pendingCompletion = retained.copy(
                lastFailure = GameCompletionAttemptFailure.DecisionRejected(result.rejection),
            )
            is SubmissionResult.AdmissionRejected -> pendingCompletion = retained.copy(
                lastFailure = GameCompletionAttemptFailure.AdmissionRejected(result.failure),
            )
        }
        return completionStatus()
    }

    fun close() {
        runCatching(audioResource::close)
    }

    private fun readSnapshot(
        snapshot: CommittedStateSnapshot<GameBallState>,
        query: GameQuery,
        context: ReadContext,
    ): GameQueryResult {
        require(context.protocolVersion == GameProjection.PROTOCOL_VERSION) {
            "Unsupported Game read protocol version"
        }
        val stamp = ConsistencyStamp(
            ballInstanceId = snapshot.ballInstanceId,
            commitRevision = snapshot.commitRevision,
            stateSchemaVersion = snapshot.stateSchemaVersion,
        )
        return when (query) {
            GameQuery.GetGameProjection -> GameQueryResult.Projection(
                ReadResult(
                    payload = projectionFor(snapshot),
                    consistencyStamp = stamp,
                ),
            )
            GameQuery.GetPersistenceStatus -> GameQueryResult.Persistence(
                ReadResult(
                    payload = snapshot.state.persistenceStatus,
                    consistencyStamp = stamp,
                ),
            )
        }
    }

    private fun projectionRead(): ReadResult<GameProjection> =
        (query(GameQuery.GetGameProjection) as GameQueryResult.Projection).value

    private fun projectionFor(snapshot: CommittedStateSnapshot<GameBallState>): GameProjection {
        projectionCache?.takeIf { cache -> cache.revision == snapshot.commitRevision }
            ?.let { cache -> return cache.projection }
        return snapshot.state.model.toProjection().also { projection ->
            projectionCache = ProjectionCache(snapshot.commitRevision, projection)
        }
    }

    private fun dispatchAcceptedOutput(output: SemanticOutput) {
        when (output) {
            is ProjectionOutput -> when (output.payload) {
                is GameProjectionPayload.GameProjectionChanged -> {
                    // Live projection delivery is returned to the Interaction caller from the
                    // retained accepted root frame. It never mutates the stamped Game snapshot.
                    projectionCache = null
                }
            }
            is EffectRequest -> dispatchEffect(output)
        }
    }

    private fun dispatchEffect(request: EffectRequest) {
        when (val effect = request.payload) {
            is GameEffect.AdvanceAudio -> runCatching {
                audioResource.advance(
                    settings = effect.settings,
                    realDelta = effect.realDeltaSeconds,
                    cues = effect.cues,
                )
            }
            GameEffect.EnsureAudioUnlocked -> runCatching(audioResource::ensureUnlocked)
            is GameEffect.PersistProgress -> {
                // Assembly owns the route identity; the provider's declared identity is still
                // quarantined before execution and cannot leak an exception into the runtime.
                val providerAccepted = runCatching {
                    progressStore.providerId == ProgressProviderId.PLATFORM_LOCAL
                }.getOrDefault(false)
                val provider = ProgressProvider.PLATFORM_LOCAL
                val persistenceResult = if (providerAccepted) {
                    runCatching { progressStore.persist(effect.snapshot) }
                        .getOrElse {
                            ProgressPersistResult.OutcomeUnknown(
                                ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                            )
                        }
                } else {
                    ProgressPersistResult.OutcomeUnknown(
                        ProgressResourceFailure.PROVIDER_READ_FAILED,
                    )
                }
                val fact = when (val result = persistenceResult) {
                    ProgressPersistResult.Persisted -> GameFact.ProgressPersisted(
                        handle = request.semanticHandle,
                        provider = provider,
                    )
                    is ProgressPersistResult.OutcomeUnknown ->
                        GameFact.ProgressPersistenceOutcomeUnknown(
                            handle = request.semanticHandle,
                            provider = provider,
                            reason = result.reason.toProtocolReason(),
                        )
                }
                retainCompletion(
                    RetainedCompletion(
                        fact = fact,
                        context = GameDecisionContext(
                            operationId = request.semanticHandle.operationId,
                            causalBudgetScope = request.semanticHandle.operationId,
                            causalDepth = 2,
                        ),
                    ),
                )
            }
        }
    }

    private fun retainCompletion(completion: RetainedCompletion) {
        check(pendingCompletion == null) {
            "Synchronous completion slot was not reserved before acceptance"
        }
        pendingCompletion = completion
    }

    private fun reserveRootOperationId(): OperationId? {
        val value = nextRootOperationId
        if (value == 0uL) return null
        nextRootOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return OperationId(value)
    }

    companion object {
        private const val COMPLETION_CAPACITY = 1

        fun create(
            progressStore: ProgressStore,
            audioResource: AudioResource,
            bootstrapProgress: StoredProgress?,
            seed: Int = 731_991,
            initialMatter: Int? = null,
            initialRebirthLevel: Int = 0,
        ): GameFeatureBall = GameFeatureBall(
            initialState = initialGameBallState(
                seed = seed,
                bootstrapProgress = bootstrapProgress,
                initialMatter = initialMatter,
                initialRebirthLevel = initialRebirthLevel,
            ),
            progressStore = progressStore,
            audioResource = audioResource,
        )

        val LIMITS = DecisionLimits(
            maxInputBytes = 4_096L,
            maxStateBytes = 16_777_216L,
            maxCollectionItems = 2_048,
            maxOutputsPerDecision = 3,
            maxEffectsPerDecision = 2,
            maxCommandsPerDecision = 0,
            maxCausalDepth = 2,
            maxRetriesPerOperation = 0,
            maxTransitionSteps = 48,
        )

        private fun estimateInputBytes(pulse: GamePulse, context: GameDecisionContext): Long =
            when (pulse) {
                is GameIntent.FrameElapsed -> 16L
                is GameIntent.ViewportChanged -> 24L
                is GameIntent.PointerMoved -> 24L
                is GameIntent.PointerPressed -> 20L
                is GameIntent.ChoiceSelected -> 12L
                is GameIntent -> 8L
                is GameFact.ProgressPersisted -> 80L +
                    pulse.handle.localOrdinalOrName.length * 4L
                is GameFact.ProgressPersistenceOutcomeUnknown -> 88L +
                    pulse.handle.localOrdinalOrName.length * 4L
            } + context.transitionArtifact.length * 4L

        /** Includes the model plus every retained GameBallState correlation/status field. */
        private fun estimateStateBytes(state: GameBallState): Long =
            state.model.estimatedStateBytes() + GAME_BALL_STATE_FIXED_BYTES +
                (state.outstandingPersistence?.localOrdinalOrName?.length ?: 0) * 4L

        private fun collectionItemCounts(
            candidate: PreflightCandidate<
                GameBallState,
                GamePulse,
                GameDecisionContext,
                SemanticOutput,
            >,
        ): Iterable<Int> = buildList {
            addAll(candidate.decision.nextState.model.boundedCollectionSizes())
            add(candidate.decision.outputs.size)
            candidate.decision.outputs.forEach { output ->
                when (output) {
                    is ProjectionOutput -> when (val payload = output.payload) {
                        is GameProjectionPayload.GameProjectionChanged ->
                            add(payload.visualFxCues.size)
                    }
                    is EffectRequest -> when (val effect = output.payload) {
                        is GameEffect.AdvanceAudio -> add(effect.cues.size)
                        is GameEffect.PersistProgress -> {
                            add(effect.snapshot.unlockedWeaponIndices.size)
                            add(effect.snapshot.metaLevels.size)
                            add(effect.snapshot.discoveredItemIds.size)
                        }
                        GameEffect.EnsureAudioUnlocked -> Unit
                    }
                }
            }
        }

        private fun hasMatchingOutputKind(output: SemanticOutput): Boolean = when (output) {
            is ProjectionOutput ->
                output.semanticHandle.outputKind == GameOutputKind.GAME_PROJECTION_CHANGED &&
                    output.payload is GameProjectionPayload.GameProjectionChanged
            is EffectRequest -> output.semanticHandle.outputKind == when (output.payload) {
                is GameEffect.AdvanceAudio -> GameOutputKind.ADVANCE_AUDIO
                GameEffect.EnsureAudioUnlocked -> GameOutputKind.ENSURE_AUDIO_UNLOCKED
                is GameEffect.PersistProgress -> GameOutputKind.PERSIST_PROGRESS
            }
        }

        private fun ProgressResourceFailure.toProtocolReason(): ProgressPersistenceUnknownReason =
            when (this) {
                ProgressResourceFailure.PROVIDER_READ_FAILED ->
                    ProgressPersistenceUnknownReason.PROVIDER_READ_FAILED
                ProgressResourceFailure.ENCODING_FAILED ->
                    ProgressPersistenceUnknownReason.ENCODING_FAILED
                ProgressResourceFailure.PAYLOAD_LIMIT_EXCEEDED ->
                    ProgressPersistenceUnknownReason.PAYLOAD_LIMIT_EXCEEDED
                ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED ->
                    ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED
            }

        private const val GAME_BALL_STATE_FIXED_BYTES = 512L
    }
}
