// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game

import kinetickk.application.runtime.AdditionalLimitMeasurement
import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightCandidate
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.GameBootstrapSnapshot
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.protocol.CommandRequest
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameDependencyContract
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProfileReplica
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GamePulse
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.GameRejection
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.game.nucleus.protocol.GameRunStartRejectionReason
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.features.game.nucleus.read.CommittedStateSnapshot
import kinetickk.features.game.nucleus.read.ConsistencyStamp
import kinetickk.features.game.nucleus.read.ReadContext
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.features.game.nucleus.transition.GameBallState
import kinetickk.features.game.nucleus.transition.GameNucleus
import kinetickk.features.game.nucleus.transition.initialGameBallState
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

sealed interface GameDispatchResult {
    data class Committed(
        /** Commit created directly by the submitted root pulse. */
        val sourceCommitRevision: ULong,
        /** Stamped Game projection before Assembly applies cross-Ball follow-on commands. */
        val projectionRead: ReadResult<GameProjection>,
        /** Drop-eligible presentation consequences from this accepted frame. */
        val visualFxCues: ImmutableList<VisualFxCue>,
        /** Typed cross-Ball commands routed only by the static Assembly. */
        val commands: ImmutableList<CommandRequest>,
    ) : GameDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : GameDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : GameDispatchResult
}

data class GameRunStartExecution(
    val moduleResult: GameRunStartModuleResult,
    val committed: GameDispatchResult.Committed?,
)

/**
 * Singleton active-run Game Feature Ball.
 *
 * Game owns simulation order and run-local state. It has no Resource bindings and cannot mutate
 * Settings or permanent Profile authority. Cross-Ball work leaves as typed Commands for Assembly;
 * dependency updates return as trusted, captured replica Facts.
 */
class GameFeatureBall internal constructor(
    initialState: GameBallState,
) {
    private data class ProjectionCache(
        val revision: ULong,
        val projection: GameProjection,
    )

    private data class RunStartDedupRecord(
        val command: GameRunStartModuleCommand,
        val result: GameRunStartModuleResult,
    )

    private val runtime = InlineAcceptedFrameRuntime(
        initialState = initialState,
        decider = GameNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = { state ->
                    state.model.estimatedStateBytes() + GAME_BALL_STATE_FIXED_BYTES +
                        (state.settingsSource?.ballInstanceId?.length ?: 0) * 4L +
                        (state.profileSource?.ballInstanceId?.length ?: 0) * 4L
                },
                collectionItemCounts = ::collectionItemCounts,
                isEffect = { false },
                isCommand = { output -> output is CommandRequest },
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
                causalBudgetScope = { _, context ->
                    "${context.causalBudgetScopeOwnerBallInstanceId}:" +
                        context.causalBudgetScope.value
                },
            ),
        ),
        outputDispatcher = OutputDispatcher { output ->
            if (output is ProjectionOutput) projectionCache = null
        },
    )
    private var projectionCache: ProjectionCache? = null
    private var lastRunStart: RunStartDedupRecord? = null
    private var nextRootOperationId: ULong = 1uL

    fun dispatch(intent: GameIntent): GameDispatchResult = submit(intent)

    /** Executes the target-owned run-start operation and returns its provenance-bound result. */
    fun execute(command: GameRunStartModuleCommand): GameRunStartExecution {
        val correlatable = command.hasCorrelatableRunStartEnvelope()
        lastRunStart?.let { retained ->
            resolveRunStartRedelivery(
                received = command,
                retained = retained,
                receivedCorrelatable = correlatable,
            )?.let { return it }
        }

        val dispatch = submit(
            pulse = command,
            causalBudgetScope = OperationId(command.causalBudgetScope.operationId),
            causalBudgetScopeOwnerBallInstanceId =
            command.causalBudgetScope.ownerBallInstanceId,
            causalDepth = GameRunStartContract.COMMAND_CAUSAL_DEPTH,
        )
        val moduleResult = when (dispatch) {
            is GameDispatchResult.Committed -> GameRunStartModuleResult.Started(
                commandSource = command.commandSource,
                causalBudgetScope = command.causalBudgetScope,
                causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
                provenance = GameRunStartContract.RESULT_PROVENANCE,
                gameCommitRevision = dispatch.sourceCommitRevision,
            )
            is GameDispatchResult.DecisionRejected -> GameRunStartModuleResult.Rejected(
                commandSource = command.commandSource,
                causalBudgetScope = command.causalBudgetScope,
                causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
                provenance = GameRunStartContract.RESULT_PROVENANCE,
                reason = dispatch.reason.toRunStartRejectionReason(),
            )
            is GameDispatchResult.AdmissionRejected -> GameRunStartModuleResult.Rejected(
                commandSource = command.commandSource,
                causalBudgetScope = command.causalBudgetScope,
                causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
                provenance = GameRunStartContract.RESULT_PROVENANCE,
                reason = GameRunStartRejectionReason.TARGET_ADMISSION_REJECTED,
            )
        }
        if (correlatable && dispatch !is GameDispatchResult.AdmissionRejected) {
            lastRunStart = RunStartDedupRecord(command = command, result = moduleResult)
        }
        return GameRunStartExecution(
            moduleResult = moduleResult,
            committed = dispatch as? GameDispatchResult.Committed,
        )
    }

    private fun resolveRunStartRedelivery(
        received: GameRunStartModuleCommand,
        retained: RunStartDedupRecord,
        receivedCorrelatable: Boolean,
    ): GameRunStartExecution? {
        val receivedSource = received.commandSource
        val retainedSource = retained.command.commandSource
        if (receivedSource == retainedSource) {
            return if (received == retained.command) {
                GameRunStartExecution(moduleResult = retained.result, committed = null)
            } else {
                rejectedRunStartExecution(
                    received,
                    GameRunStartRejectionReason.COMMAND_SOURCE_CONFLICT,
                )
            }
        }
        if (receivedSource.sameDeliverySlot(retainedSource)) {
            return rejectedRunStartExecution(
                received,
                GameRunStartRejectionReason.COMMAND_SOURCE_CONFLICT,
            )
        }
        if (
            receivedCorrelatable &&
            receivedSource.sourceBallInstanceId == retainedSource.sourceBallInstanceId &&
            receivedSource.precedes(retainedSource)
        ) {
            return rejectedRunStartExecution(
                received,
                GameRunStartRejectionReason.STALE_COMMAND_SOURCE,
            )
        }
        return null
    }

    private fun rejectedRunStartExecution(
        command: GameRunStartModuleCommand,
        reason: GameRunStartRejectionReason,
    ): GameRunStartExecution = GameRunStartExecution(
        moduleResult = GameRunStartModuleResult.Rejected(
            commandSource = command.commandSource,
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
            provenance = GameRunStartContract.RESULT_PROVENANCE,
            reason = reason,
        ),
        committed = null,
    )

    internal fun observeDependencies(
        settings: GameSettings,
        profile: GameProfileReplica,
        settingsSource: ConsistencyStamp = ConsistencyStamp(
            ballInstanceId = GameDependencyContract.SETTINGS_BALL_INSTANCE_ID,
            commitRevision = 0uL,
            stateSchemaVersion = GameDependencyContract.SETTINGS_STATE_SCHEMA_VERSION,
        ),
        profileSource: ConsistencyStamp = ConsistencyStamp(
            ballInstanceId = GameDependencyContract.PROFILE_BALL_INSTANCE_ID,
            commitRevision = 0uL,
            stateSchemaVersion = GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION,
        ),
    ): GameDispatchResult = submit(
        GameFact.DependenciesObserved(
            settings = settings,
            profile = profile,
            settingsSource = settingsSource,
            profileSource = profileSource,
        ),
    )

    /** Canonical Query -> ReadResult mapping for Game protocol 2.0.0. */
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
        }
    }

    private fun submit(
        pulse: GamePulse,
        causalBudgetScope: OperationId? = null,
        causalBudgetScopeOwnerBallInstanceId: String = GameProjection.BALL_INSTANCE_ID,
        causalDepth: Int = 1,
    ): GameDispatchResult {
        val operationId = reserveRootOperationId()
            ?: return GameDispatchResult.AdmissionRejected(AdmissionFailure.OperationIdentityExhausted)
        val effectiveCausalBudgetScope = causalBudgetScope ?: operationId
        return when (
            val result = runtime.submit(
                pulse = pulse,
                context = GameDecisionContext(
                    operationId = operationId,
                    causalBudgetScope = effectiveCausalBudgetScope,
                    causalBudgetScopeOwnerBallInstanceId =
                    causalBudgetScopeOwnerBallInstanceId,
                    causalDepth = causalDepth,
                ),
            )
        ) {
            is SubmissionResult.Committed -> GameDispatchResult.Committed(
                sourceCommitRevision = result.frame.revision.value,
                projectionRead = projectionRead(),
                visualFxCues = result.frame.outputs
                    .filterIsInstance<ProjectionOutput>()
                    .flatMap { output ->
                        when (val payload = output.payload) {
                            is GameProjectionPayload.GameProjectionChanged -> payload.visualFxCues
                        }
                    }
                    .toImmutableList(),
                commands = result.frame.outputs.filterIsInstance<CommandRequest>().toImmutableList(),
            )
            is SubmissionResult.DecisionRejected ->
                GameDispatchResult.DecisionRejected(result.rejection)
            is SubmissionResult.AdmissionRejected ->
                GameDispatchResult.AdmissionRejected(result.failure)
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

    private fun reserveRootOperationId(): OperationId? {
        val value = nextRootOperationId
        if (value == 0uL) return null
        nextRootOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return OperationId(value)
    }

    companion object {
        fun create(
            bootstrapProgress: GameBootstrapSnapshot?,
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
        )

        val LIMITS = DecisionLimits(
            maxInputBytes = 65_536L,
            maxStateBytes = 16_777_216L,
            maxCollectionItems = 2_048,
            maxOutputsPerDecision = 8,
            maxEffectsPerDecision = 0,
            maxCommandsPerDecision = 7,
            maxCausalDepth = GameRunStartContract.COMMAND_CAUSAL_DEPTH,
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
                is GameRunStartModuleCommand -> 128L +
                    pulse.commandSource.sourceBallInstanceId.length * 4L +
                    pulse.commandSource.sourceOutputKind.length * 4L +
                    pulse.commandSource.sourceLocalOrdinalOrName.length * 4L +
                    pulse.causalBudgetScope.ownerBallInstanceId.length * 4L +
                    pulse.runConfigurationReference.profileBallInstanceId.length * 4L
                is GameFact.DependenciesObserved -> 512L +
                    pulse.profile.unlockedWeapons.size * 8L +
                    pulse.profile.metaRanks.size * 4L +
                    pulse.profile.discoveredItemIds.size * 4L +
                    pulse.settingsSource.ballInstanceId.length * 4L +
                    pulse.profileSource.ballInstanceId.length * 4L
            } + context.transitionArtifact.length * 4L

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
            if (candidate.pulse is GameFact.DependenciesObserved) {
                add(candidate.pulse.profile.unlockedWeapons.size)
                add(candidate.pulse.profile.metaRanks.size)
                add(candidate.pulse.profile.discoveredItemIds.size)
            }
            candidate.decision.outputs.forEach { output ->
                when (output) {
                    is ProjectionOutput -> when (val payload = output.payload) {
                        is GameProjectionPayload.GameProjectionChanged -> add(payload.visualFxCues.size)
                    }
                    is CommandRequest -> when (val command = output.payload) {
                        is GameCommand.AdvanceAudio -> add(command.cues.size)
                        is GameCommand.ChangeProfile,
                        is GameCommand.ChangeSettings,
                        is GameCommand.BeginRebirth,
                        GameCommand.EnsureAudioUnlocked,
                        -> Unit
                    }
                }
            }
        }

        private fun hasMatchingOutputKind(output: SemanticOutput): Boolean = when (output) {
            is ProjectionOutput ->
                output.semanticHandle.outputKind == GameOutputKind.GAME_PROJECTION_CHANGED &&
                    output.payload is GameProjectionPayload.GameProjectionChanged
            is CommandRequest -> output.semanticHandle.outputKind == when (output.payload) {
                is GameCommand.AdvanceAudio -> GameOutputKind.ADVANCE_AUDIO
                GameCommand.EnsureAudioUnlocked -> GameOutputKind.ENSURE_AUDIO_UNLOCKED
                is GameCommand.ChangeSettings -> GameOutputKind.CHANGE_SETTINGS
                is GameCommand.ChangeProfile -> GameOutputKind.CHANGE_PROFILE
                is GameCommand.BeginRebirth -> GameOutputKind.BEGIN_REBIRTH
            }
        }

        private const val GAME_BALL_STATE_FIXED_BYTES = 256L
    }
}

private fun GameRunStartModuleCommand.hasCorrelatableRunStartEnvelope(): Boolean {
    val source = commandSource
    return source.sourceBallInstanceId == GameRunStartContract.SOURCE_BALL_INSTANCE_ID &&
        source.sourceCommitRevision != 0uL &&
        source.sourceOrdinal == 0u &&
        source.sourceOperationId != 0uL &&
        source.sourceOutputKind == GameRunStartContract.SOURCE_OUTPUT_KIND &&
        source.sourceLocalOrdinalOrName == GameRunStartContract.SOURCE_LOCAL_ORDINAL_OR_NAME &&
        causalBudgetScope.ownerBallInstanceId ==
        GameRunStartContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID &&
        causalBudgetScope.operationId == source.sourceOperationId &&
        causalDepth == GameRunStartContract.COMMAND_CAUSAL_DEPTH
}

private fun GameRunStartCommandSource.sameDeliverySlot(
    other: GameRunStartCommandSource,
): Boolean = sourceBallInstanceId == other.sourceBallInstanceId &&
    sourceCommitRevision == other.sourceCommitRevision &&
    sourceOrdinal == other.sourceOrdinal

private fun GameRunStartCommandSource.precedes(
    other: GameRunStartCommandSource,
): Boolean = sourceCommitRevision < other.sourceCommitRevision ||
    (sourceCommitRevision == other.sourceCommitRevision && sourceOrdinal < other.sourceOrdinal)

private fun BusinessRejection.toRunStartRejectionReason(): GameRunStartRejectionReason =
    when (this) {
        is GameRejection.InvalidRunStartCommandSource ->
            GameRunStartRejectionReason.INVALID_COMMAND_SOURCE
        is GameRejection.InvalidRunStartCausalContext ->
            GameRunStartRejectionReason.INVALID_CAUSAL_CONTEXT
        is GameRejection.InvalidRunConfigurationReference ->
            GameRunStartRejectionReason.INVALID_RUN_CONFIGURATION_REFERENCE
        is GameRejection.ProfileReferenceNotCurrent ->
            GameRunStartRejectionReason.PROFILE_REFERENCE_NOT_CURRENT
        is GameRejection.RunStartRebirthLevelMismatch ->
            GameRunStartRejectionReason.REBIRTH_LEVEL_MISMATCH
        else -> GameRunStartRejectionReason.TARGET_DECISION_REJECTED
    }
