// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.rebirth

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.CommittedStateSnapshot
import kinetickk.application.runtime.ConsistencyStamp
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightCandidate
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.ReadContext
import kinetickk.application.runtime.ReadResult
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.GameRunStartResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.ProfileAdvanceRebirthResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCommandTarget
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCausalScope
import kinetickk.flows.rebirth.nucleus.protocol.RebirthDecisionContext
import kinetickk.flows.rebirth.nucleus.protocol.RebirthFlowProtocol
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommandRequest
import kinetickk.flows.rebirth.nucleus.protocol.RebirthOperationId
import kinetickk.flows.rebirth.nucleus.protocol.RebirthOutputKind
import kinetickk.flows.rebirth.nucleus.protocol.RebirthPulse
import kinetickk.flows.rebirth.nucleus.protocol.RebirthQuery
import kinetickk.flows.rebirth.nucleus.protocol.RebirthQueryResult
import kinetickk.flows.rebirth.nucleus.protocol.RebirthRejection
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartContract
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStatus
import kinetickk.flows.rebirth.nucleus.state.RebirthFlowState
import kinetickk.flows.rebirth.nucleus.transition.RebirthFlowNucleus
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

data class RebirthCommittedCommand(
    val source: RebirthCommandSource,
    val request: RebirthModuleCommandRequest,
)

sealed interface RebirthDispatchResult {
    data class Committed(
        val sourceCommitRevision: ULong,
        val statusRead: ReadResult<RebirthStatus>,
        val commands: ImmutableList<RebirthCommittedCommand>,
    ) : RebirthDispatchResult

    /** Exact start-command replay; it creates no new Flow commit or participant command. */
    data class Replayed(
        val statusRead: ReadResult<RebirthStatus>,
    ) : RebirthDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : RebirthDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : RebirthDispatchResult
}

enum class RebirthCompletionKind {
    PROFILE_RESULT,
    GAME_RUN_START_RESULT,
}

sealed interface RebirthContinuationStatus {
    data object Idle : RebirthContinuationStatus

    data class Retained(
        val operationId: RebirthOperationId,
        val causalBudgetScope: RebirthCausalScope,
        val causalDepth: Int,
        val completionKind: RebirthCompletionKind,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastFailure: AdmissionFailure,
    ) : RebirthContinuationStatus

    data class DispatchStopped(
        val operationId: RebirthOperationId,
        val causalBudgetScope: RebirthCausalScope,
        val causalDepth: Int,
        val completionKind: RebirthCompletionKind,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastFailure: AdmissionFailure,
    ) : RebirthContinuationStatus
}

/**
 * Inline + Transient coordinator for one local Rebirth workflow at a time.
 *
 * It owns only orchestration status and exact causal references. The returned commands are
 * accepted semantic outputs for Assembly to route; their presence does not prove participant
 * dispatch or acceptance.
 */
class RebirthFlowBall private constructor(
    private val completionAdmissionOverride: (
        RebirthPulse,
        RebirthDecisionContext,
    ) -> AdmissionFailure? = { _, _ -> null },
) {
    private data class StartReceipt(
        val command: RebirthStartModuleCommand,
        val result: RebirthDispatchResult,
    )

    private data class RetainedCompletion(
        val pulse: RebirthPulse,
        val context: RebirthDecisionContext,
        val completionKind: RebirthCompletionKind,
        val retriesUsed: Int,
        val lastFailure: AdmissionFailure,
    )

    private var pendingCompletion: RetainedCompletion? = null
    private var latestStartReceipt: StartReceipt? = null
    private val runtime = InlineAcceptedFrameRuntime(
        initialState = RebirthFlowState(),
        decider = RebirthFlowNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = { REBIRTH_STATE_BYTES },
                collectionItemCounts = ::collectionItemCounts,
                isEffect = { false },
                isCommand = { true },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                sourceOrdinal = RebirthModuleCommandRequest::sourceOrdinal,
                hasMatchingOutputKind = ::hasMatchingOutputKind,
                synchronousCompletionCount = { candidate -> candidate.decision.outputs.size },
                availableSynchronousCompletionSlots = {
                    if (pendingCompletion == null) COMPLETION_CAPACITY else 0
                },
                causalBudgetScope = { _, context ->
                    "${context.causalBudgetScope.ownerBallInstanceId}:" +
                        context.causalBudgetScope.operationId
                },
            ),
        ),
        // Assembly takes the complete already-accepted command batch from RebirthDispatchResult.
        outputDispatcher = OutputDispatcher<RebirthModuleCommandRequest> { _ -> Unit },
    )
    fun start(command: RebirthStartModuleCommand): RebirthDispatchResult {
        val correlatable = command.hasCorrelatableStartEnvelope()
        latestStartReceipt?.let { retained ->
            resolveStartRedelivery(
                received = command,
                retained = retained,
                receivedCorrelatable = correlatable,
            )?.let { return it }
        }
        pendingCompletion?.let { return completionSlotUnavailable(it) }
        val result = submit(
            pulse = command,
            operationId = RebirthOperationId(command.commandSource.sourceOperationId),
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = command.causalDepth,
        )
        if (correlatable && result !is RebirthDispatchResult.AdmissionRejected) {
            latestStartReceipt = StartReceipt(command = command, result = result)
        }
        return result
    }

    private fun resolveStartRedelivery(
        received: RebirthStartModuleCommand,
        retained: StartReceipt,
        receivedCorrelatable: Boolean,
    ): RebirthDispatchResult? {
        val receivedSource = received.commandSource
        val retainedSource = retained.command.commandSource
        if (receivedSource == retainedSource) {
            return if (received == retained.command) {
                when (retained.result) {
                    is RebirthDispatchResult.Committed,
                    is RebirthDispatchResult.Replayed,
                    -> RebirthDispatchResult.Replayed(statusRead = status())
                    is RebirthDispatchResult.DecisionRejected,
                    is RebirthDispatchResult.AdmissionRejected,
                    -> retained.result
                }
            } else {
                RebirthDispatchResult.DecisionRejected(
                    RebirthRejection.ConflictingStartCommand(receivedSource),
                )
            }
        }
        if (receivedSource.sameDeliverySlot(retainedSource)) {
            return RebirthDispatchResult.DecisionRejected(
                RebirthRejection.ConflictingStartCommand(receivedSource),
            )
        }
        if (
            receivedCorrelatable &&
            receivedSource.sourceBallInstanceId == retainedSource.sourceBallInstanceId &&
            receivedSource.precedes(retainedSource)
        ) {
            return RebirthDispatchResult.DecisionRejected(
                RebirthRejection.StaleStartCommand(
                    received = receivedSource,
                    latest = retainedSource,
                ),
            )
        }
        return null
    }

    fun accept(result: ProfileAdvanceRebirthModuleResult): RebirthDispatchResult = submitCompletion(
        pulse = ProfileAdvanceRebirthResultObserved(result),
        operationId = RebirthOperationId(result.commandSource.sourceOperationId),
        causalBudgetScope = RebirthCausalScope(
            ownerBallInstanceId = result.causalBudgetScope.ownerBallInstanceId,
            operationId = result.causalBudgetScope.operationId,
        ),
        causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
        completionKind = RebirthCompletionKind.PROFILE_RESULT,
    )

    fun accept(result: GameRunStartModuleResult): RebirthDispatchResult = submitCompletion(
        pulse = GameRunStartResultObserved(result),
        operationId = RebirthOperationId(result.commandSource.sourceOperationId),
        causalBudgetScope = RebirthCausalScope(
            ownerBallInstanceId = result.causalBudgetScope.ownerBallInstanceId,
            operationId = result.causalBudgetScope.operationId,
        ),
        causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
        completionKind = RebirthCompletionKind.GAME_RUN_START_RESULT,
    )

    fun completionStatus(): RebirthContinuationStatus = pendingCompletion?.toStatus()
        ?: RebirthContinuationStatus.Idle

    fun hasRetainedCompletion(): Boolean = pendingCompletion != null

    /**
     * Retries the exact retained participant completion and context once.
     *
     * A non-null committed result can contain the next accepted command batch and must be routed
     * by Assembly exactly like a direct [accept] result. A null result means no completion was
     * retained.
     */
    fun resumeRetainedCompletion(): RebirthDispatchResult? {
        val retained = pendingCompletion ?: return null
        if (retained.retriesUsed >= MAX_COMPLETION_RETRIES) {
            return completionSlotUnavailable(retained)
        }
        val retryContext = retained.context.copy(
            retryCount = retained.retriesUsed + 1,
        )
        pendingCompletion = null
        val result = submitCompletionAttempt(retained.pulse, retryContext)
        if (result is RebirthDispatchResult.AdmissionRejected) {
            pendingCompletion = retained.copy(
                context = retryContext,
                retriesUsed = retained.retriesUsed + 1,
                lastFailure = result.reason,
            )
        }
        return result
    }

    fun query(
        query: RebirthQuery,
        context: ReadContext = ReadContext(RebirthFlowProtocol.VERSION),
    ): RebirthQueryResult = when (query) {
        RebirthQuery.GetStatus -> RebirthQueryResult.Status(readStatus(context))
    }

    fun status(
        context: ReadContext = ReadContext(RebirthFlowProtocol.VERSION),
    ): ReadResult<RebirthStatus> =
        (query(RebirthQuery.GetStatus, context) as RebirthQueryResult.Status).value

    private fun readStatus(
        context: ReadContext,
    ): ReadResult<RebirthStatus> {
        require(context.protocolVersion == RebirthFlowProtocol.VERSION) {
            "Unsupported Rebirth Flow read protocol version"
        }
        val frame = runtime.snapshot()
        val snapshot = CommittedStateSnapshot(
            ballInstanceId = RebirthFlowProtocol.BALL_INSTANCE_ID,
            commitRevision = frame.revision.value,
            stateSchemaVersion = RebirthFlowProtocol.STATE_SCHEMA_VERSION,
            state = frame.state,
        )
        return ReadResult(
            payload = snapshot.state.status,
            consistencyStamp = ConsistencyStamp(
                ballInstanceId = snapshot.ballInstanceId,
                commitRevision = snapshot.commitRevision,
                stateSchemaVersion = snapshot.stateSchemaVersion,
            ),
        )
    }

    private fun submit(
        pulse: RebirthPulse,
        operationId: RebirthOperationId,
        causalBudgetScope: RebirthCausalScope,
        causalDepth: Int,
    ): RebirthDispatchResult {
        val proposedRevision = runtime.snapshot().revision.value.nextOrNull()
            ?: return RebirthDispatchResult.AdmissionRejected(AdmissionFailure.RevisionExhausted)
        val context = RebirthDecisionContext(
            operationId = operationId,
            causalBudgetScope = causalBudgetScope,
            proposedCommitRevision = proposedRevision,
            causalDepth = causalDepth,
        )
        return mapSubmission(runtime.submit(pulse, context), proposedRevision)
    }

    private fun submitCompletion(
        pulse: RebirthPulse,
        operationId: RebirthOperationId,
        causalBudgetScope: RebirthCausalScope,
        causalDepth: Int,
        completionKind: RebirthCompletionKind,
    ): RebirthDispatchResult {
        pendingCompletion?.let { return completionSlotUnavailable(it) }
        val proposedRevision = runtime.snapshot().revision.value.nextOrNull()
            ?: return RebirthDispatchResult.AdmissionRejected(AdmissionFailure.RevisionExhausted)
        val context = RebirthDecisionContext(
            operationId = operationId,
            causalBudgetScope = causalBudgetScope,
            proposedCommitRevision = proposedRevision,
            causalDepth = causalDepth,
        )
        val result = submitCompletionAttempt(pulse, context)
        if (
            result is RebirthDispatchResult.AdmissionRejected &&
            isExactCurrentCompletion(pulse)
        ) {
            check(pendingCompletion == null) {
                "Rebirth completion slot was not reserved before participant submission"
            }
            pendingCompletion = RetainedCompletion(
                pulse = pulse,
                context = context,
                completionKind = completionKind,
                retriesUsed = 0,
                lastFailure = result.reason,
            )
        }
        return result
    }

    private fun submitCompletionAttempt(
        pulse: RebirthPulse,
        context: RebirthDecisionContext,
    ): RebirthDispatchResult {
        completionAdmissionOverride(pulse, context)?.let {
            return RebirthDispatchResult.AdmissionRejected(it)
        }
        return mapSubmission(
            result = runtime.submit(pulse, context),
            proposedRevision = context.proposedCommitRevision,
        )
    }

    private fun mapSubmission(
        result: SubmissionResult<RebirthFlowState, RebirthModuleCommandRequest>,
        proposedRevision: ULong,
    ): RebirthDispatchResult = when (result) {
            is SubmissionResult.Committed -> {
                check(result.frame.revision.value == proposedRevision) {
                    "Rebirth Flow committed an unexpected revision"
                }
                RebirthDispatchResult.Committed(
                    sourceCommitRevision = result.frame.revision.value,
                    statusRead = status(),
                    commands = result.frame.outputs.map { request ->
                        RebirthCommittedCommand(
                            source = request.sourceAt(result.frame.revision.value),
                            request = request,
                        )
                    }.toImmutableList(),
                )
            }
            is SubmissionResult.DecisionRejected ->
                RebirthDispatchResult.DecisionRejected(result.rejection)
            is SubmissionResult.AdmissionRejected ->
                RebirthDispatchResult.AdmissionRejected(result.failure)
        }

    private fun isExactCurrentCompletion(pulse: RebirthPulse): Boolean =
        when (val status = runtime.snapshot().state.status) {
            is RebirthStatus.AwaitingProfileResult ->
                (pulse as? ProfileAdvanceRebirthResultObserved)?.result?.let { result ->
                    result.commandSource == status.targetCommand.commandSource &&
                        result.causalBudgetScope == status.targetCommand.causalBudgetScope &&
                        result.causalDepth ==
                        ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH &&
                        result.provenance == ProfileAdvanceRebirthContract.RESULT_PROVENANCE
                } == true
            is RebirthStatus.AwaitingGameStartResult -> {
                val result = (pulse as? GameRunStartResultObserved)?.result
                result != null &&
                    result.commandSource == status.targetCommand.commandSource &&
                    result.causalBudgetScope == status.targetCommand.causalBudgetScope &&
                    result.causalDepth == GameRunStartContract.RESULT_CAUSAL_DEPTH &&
                    result.provenance == GameRunStartContract.RESULT_PROVENANCE
            }
            RebirthStatus.Idle,
            is RebirthStatus.Completed,
            is RebirthStatus.GameStartRejected,
            is RebirthStatus.Rejected,
            -> false
        }

    private fun completionSlotUnavailable(
        retained: RetainedCompletion,
    ): RebirthDispatchResult.AdmissionRejected = RebirthDispatchResult.AdmissionRejected(
        AdmissionFailure.DeliveryBackpressure(
            scope = "${retained.context.causalBudgetScope.ownerBallInstanceId}:" +
                retained.context.causalBudgetScope.operationId,
            pending = 1,
            capacity = COMPLETION_CAPACITY,
        ),
    )

    private fun RetainedCompletion.toStatus(): RebirthContinuationStatus =
        if (retriesUsed >= MAX_COMPLETION_RETRIES) {
            RebirthContinuationStatus.DispatchStopped(
                operationId = context.operationId,
                causalBudgetScope = context.causalBudgetScope,
                causalDepth = context.causalDepth,
                completionKind = completionKind,
                retriesUsed = retriesUsed,
                maxRetries = MAX_COMPLETION_RETRIES,
                lastFailure = lastFailure,
            )
        } else RebirthContinuationStatus.Retained(
            operationId = context.operationId,
            causalBudgetScope = context.causalBudgetScope,
            causalDepth = context.causalDepth,
            completionKind = completionKind,
            retriesUsed = retriesUsed,
            maxRetries = MAX_COMPLETION_RETRIES,
            lastFailure = lastFailure,
        )

    private fun RebirthModuleCommandRequest.sourceAt(
        commitRevision: ULong,
    ): RebirthCommandSource = RebirthCommandSource(
        sourceBallInstanceId = RebirthFlowProtocol.BALL_INSTANCE_ID,
        sourceCommitRevision = commitRevision,
        sourceOrdinal = sourceOrdinal,
        semanticHandle = semanticHandle,
    )

    private fun ULong.nextOrNull(): ULong? = if (this == ULong.MAX_VALUE) null else this + 1uL

    companion object {
        val LIMITS = DecisionLimits(
            maxInputBytes = 1_024L,
            maxStateBytes = 4_096L,
            maxCollectionItems = 1,
            maxOutputsPerDecision = 1,
            maxEffectsPerDecision = 0,
            maxCommandsPerDecision = 1,
            maxCausalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
            maxRetriesPerOperation = MAX_COMPLETION_RETRIES,
            maxTransitionSteps = 1,
        )

        fun create(): RebirthFlowBall = RebirthFlowBall()

        internal fun createForTest(
            completionAdmissionOverride: (
                RebirthPulse,
                RebirthDecisionContext,
            ) -> AdmissionFailure?,
        ): RebirthFlowBall = RebirthFlowBall(completionAdmissionOverride)

        private fun estimateInputBytes(
            pulse: RebirthPulse,
            context: RebirthDecisionContext,
        ): Long {
            val pulseBytes = when (pulse) {
                is RebirthStartModuleCommand -> 64L +
                    pulse.commandSource.sourceBallInstanceId.length.toLong() * 4L +
                    pulse.commandSource.sourceOutputKind.length.toLong() * 4L +
                    pulse.commandSource.sourceLocalOrdinalOrName.length.toLong() * 4L +
                    pulse.causalBudgetScope.ownerBallInstanceId.length.toLong() * 4L
                is ProfileAdvanceRebirthResultObserved -> 128L +
                    pulse.result.commandSource.estimatedStringBytes() +
                    pulse.result.causalBudgetScope.ownerBallInstanceId.length.toLong() * 4L +
                    when (val result = pulse.result) {
                        is ProfileAdvanceRebirthModuleResult.Advanced ->
                            result.profileSnapshotReference.profileBallInstanceId.length.toLong() * 4L
                        is ProfileAdvanceRebirthModuleResult.Rejected -> 0L
                    }
                is GameRunStartResultObserved -> 96L +
                    pulse.result.commandSource.estimatedStringBytes() +
                    pulse.result.causalBudgetScope.ownerBallInstanceId.length.toLong() * 4L
            }
            return pulseBytes + context.transitionArtifact.length.toLong() * 4L
        }

        private fun ProfileCommandSource.estimatedStringBytes(): Long =
            sourceBallInstanceId.length.toLong() * 4L +
                sourceOutputKind.length.toLong() * 4L +
                sourceLocalOrdinalOrName.length.toLong() * 4L

        private fun kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
            .estimatedStringBytes(): Long =
            sourceBallInstanceId.length.toLong() * 4L +
                sourceOutputKind.length.toLong() * 4L +
                sourceLocalOrdinalOrName.length.toLong() * 4L

        private fun collectionItemCounts(
            candidate: PreflightCandidate<
                RebirthFlowState,
                RebirthPulse,
                RebirthDecisionContext,
                RebirthModuleCommandRequest,
            >,
        ): Iterable<Int> = listOf(candidate.decision.outputs.size)

        private fun hasMatchingOutputKind(output: RebirthModuleCommandRequest): Boolean =
            when (output.semanticHandle.outputKind) {
                RebirthOutputKind.PROFILE_ADVANCE_REBIRTH ->
                    output.target == RebirthCommandTarget.PROFILE &&
                        output.payload is RebirthModuleCommand.AdvanceProfileRebirth
                RebirthOutputKind.GAME_START_RUN ->
                    output.target == RebirthCommandTarget.GAME &&
                        output.payload is RebirthModuleCommand.StartGameRun
            }

        private const val REBIRTH_STATE_BYTES = 2_048L
        private const val COMPLETION_CAPACITY = 1
        private const val MAX_COMPLETION_RETRIES = 1
    }
}

private fun RebirthStartModuleCommand.hasCorrelatableStartEnvelope(): Boolean {
    val source = commandSource
    if (
        source.sourceBallInstanceId != RebirthStartContract.SOURCE_BALL_INSTANCE_ID ||
        source.sourceCommitRevision == 0uL ||
        source.sourceOrdinal == 0u ||
        source.sourceOperationId == 0uL ||
        source.sourceOutputKind != RebirthStartContract.SOURCE_OUTPUT_KIND
    ) {
        return false
    }
    val expectedLocalName = RebirthStartContract.SOURCE_LOCAL_ORDINAL_PREFIX +
        (source.sourceOrdinal - 1u).toString()
    return source.sourceLocalOrdinalOrName == expectedLocalName &&
        causalBudgetScope.ownerBallInstanceId == source.sourceBallInstanceId &&
        causalBudgetScope.operationId == source.sourceOperationId &&
        causalDepth == RebirthStartContract.COMMAND_CAUSAL_DEPTH
}

private fun RebirthStartCommandSource.sameDeliverySlot(
    other: RebirthStartCommandSource,
): Boolean = sourceBallInstanceId == other.sourceBallInstanceId &&
    sourceCommitRevision == other.sourceCommitRevision &&
    sourceOrdinal == other.sourceOrdinal

private fun RebirthStartCommandSource.precedes(
    other: RebirthStartCommandSource,
): Boolean = sourceCommitRevision < other.sourceCommitRevision ||
    (sourceCommitRevision == other.sourceCommitRevision && sourceOrdinal < other.sourceOrdinal)
