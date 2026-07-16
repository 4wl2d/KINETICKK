// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.rebirth.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.features.game.nucleus.protocol.GameRunConfigurationReference
import kinetickk.features.game.nucleus.protocol.GameRunStartCausalScope
import kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileSnapshotReference
import kinetickk.flows.rebirth.nucleus.protocol.GameRunStartResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.ProfileAdvanceRebirthResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCommandTarget
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCausalScope
import kinetickk.flows.rebirth.nucleus.protocol.RebirthDecisionContext
import kinetickk.flows.rebirth.nucleus.protocol.RebirthFlowProtocol
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommandRequest
import kinetickk.flows.rebirth.nucleus.protocol.RebirthOutputKind
import kinetickk.flows.rebirth.nucleus.protocol.RebirthPulse
import kinetickk.flows.rebirth.nucleus.protocol.RebirthRejection
import kinetickk.flows.rebirth.nucleus.protocol.RebirthSemanticHandle
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartContract
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStatus
import kinetickk.flows.rebirth.nucleus.state.RebirthFlowState

internal class RebirthFlowNucleus : Decider<
    RebirthFlowState,
    RebirthPulse,
    RebirthDecisionContext,
    RebirthModuleCommandRequest,
> {
    override fun decide(
        state: RebirthFlowState,
        pulse: RebirthPulse,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        validateContext(state, pulse, context)?.let { return Rejected(it) }
        return when (pulse) {
            is RebirthStartModuleCommand -> decideStart(state, pulse, context)
            is ProfileAdvanceRebirthResultObserved ->
                decideProfileResult(state, pulse.result, context)
            is GameRunStartResultObserved -> decideGameResult(state, pulse.result, context)
        }
    }

    private fun validateContext(
        state: RebirthFlowState,
        pulse: RebirthPulse,
        context: RebirthDecisionContext,
    ): RebirthRejection? {
        if (context.transitionArtifact != RebirthFlowProtocol.TRANSITION_ARTIFACT) {
            return RebirthRejection.InvalidContext("transitionArtifact", "unsupported artifact")
        }
        if (context.operationId.value == 0uL) {
            return RebirthRejection.InvalidContext("operationId", "must be reserved")
        }
        if (context.causalBudgetScope.operationId != context.operationId.value) {
            return RebirthRejection.InvalidContext(
                "causalBudgetScope",
                "operationId must match the workflow operation",
            )
        }
        val expectedRevision = state.acceptedRevision.nextOrNull()
            ?: return RebirthRejection.InvalidContext(
                "proposedCommitRevision",
                "revision exhausted",
            )
        if (context.proposedCommitRevision != expectedRevision) {
            return RebirthRejection.InvalidContext(
                "proposedCommitRevision",
                "must be the next accepted revision",
            )
        }
        val expectedDepth = when (pulse) {
            is RebirthStartModuleCommand -> RebirthStartContract.COMMAND_CAUSAL_DEPTH
            is ProfileAdvanceRebirthResultObserved ->
                ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH
            is GameRunStartResultObserved -> GameRunStartContract.RESULT_CAUSAL_DEPTH
        }
        if (context.causalDepth != expectedDepth) {
            return RebirthRejection.InvalidContext("causalDepth", "expected $expectedDepth")
        }
        if (
            pulse is RebirthStartModuleCommand &&
            (
                pulse.commandSource.sourceOperationId != context.operationId.value ||
                    pulse.causalBudgetScope != context.causalBudgetScope
                )
        ) {
            return RebirthRejection.InvalidContext(
                "startCommand",
                "must match the initiating Game source and causal scope",
            )
        }
        if (
            pulse is ProfileAdvanceRebirthResultObserved &&
            (
                pulse.result.commandSource.sourceOperationId != context.operationId.value ||
                    pulse.result.causalBudgetScope.toRebirthCausalScope() !=
                    context.causalBudgetScope
                )
        ) {
            return RebirthRejection.InvalidContext(
                "operationId",
                "must match Profile result source",
            )
        }
        if (
            pulse is GameRunStartResultObserved &&
            (
                pulse.result.commandSource.sourceOperationId != context.operationId.value ||
                    pulse.result.causalBudgetScope.toRebirthCausalScope() !=
                    context.causalBudgetScope
                )
        ) {
            return RebirthRejection.InvalidContext(
                "operationId",
                "must match Game result source",
            )
        }
        return null
    }

    private fun decideStart(
        state: RebirthFlowState,
        command: RebirthStartModuleCommand,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        validateStartCommand(command)?.let { return Rejected(it) }
        if (command.expectedLevel < 0) {
            return Rejected(RebirthRejection.InvalidExpectedLevel(command.expectedLevel))
        }
        val current = state.status
        val outstanding = when (current) {
            is RebirthStatus.AwaitingProfileResult -> current.profileCommandSource
            is RebirthStatus.AwaitingGameStartResult -> current.gameCommandSource
            else -> null
        }
        if (outstanding != null) {
            return Rejected(RebirthRejection.Busy(outstanding))
        }

        val handle = RebirthSemanticHandle(
            operationId = context.operationId,
            outputKind = RebirthOutputKind.PROFILE_ADVANCE_REBIRTH,
            localOrdinalOrName = PROFILE_ADVANCE_LOCAL_NAME,
        )
        val source = RebirthCommandSource(
            sourceBallInstanceId = RebirthFlowProtocol.BALL_INSTANCE_ID,
            sourceCommitRevision = context.proposedCommitRevision,
            sourceOrdinal = 0u,
            semanticHandle = handle,
        )
        val targetCommand = ProfileAdvanceRebirthModuleCommand(
            commandSource = source.toProfileCommandSource(),
            causalBudgetScope = ProfileCausalScope(
                ownerBallInstanceId = context.causalBudgetScope.ownerBallInstanceId,
                operationId = context.causalBudgetScope.operationId,
            ),
            causalDepth = ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH,
            expectedLevel = command.expectedLevel,
        )
        val outputRequest = RebirthModuleCommandRequest(
            semanticHandle = handle,
            sourceOrdinal = 0u,
            target = RebirthCommandTarget.PROFILE,
            payload = RebirthModuleCommand.AdvanceProfileRebirth(
                expectedLevel = command.expectedLevel,
                targetCommand = targetCommand,
            ),
        )
        check(source == outputRequest.sourceAt(context.proposedCommitRevision))
        return accepted(
            state = state.copy(
                acceptedRevision = context.proposedCommitRevision,
                status = RebirthStatus.AwaitingProfileResult(
                    operationId = context.operationId,
                    startCommand = command,
                    expectedLevel = command.expectedLevel,
                    profileCommandSource = source,
                    targetCommand = targetCommand,
                ),
                transitionSteps = 1,
            ),
            outputs = listOf(outputRequest),
        )
    }

    private fun validateStartCommand(
        command: RebirthStartModuleCommand,
    ): RebirthRejection.InvalidStartCommand? {
        val source = command.commandSource
        if (source.sourceBallInstanceId != RebirthStartContract.SOURCE_BALL_INSTANCE_ID) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceBallInstanceId",
                reason = "must identify the declared Game source",
            )
        }
        if (source.sourceCommitRevision == 0uL) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceCommitRevision",
                reason = "must identify an accepted Game frame",
            )
        }
        if (source.sourceOrdinal == 0u) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceOrdinal",
                reason = "must follow Game's projection output",
            )
        }
        if (source.sourceOperationId == 0uL) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceOperationId",
                reason = "must identify a reserved Game operation",
            )
        }
        if (source.sourceOutputKind != RebirthStartContract.SOURCE_OUTPUT_KIND) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceOutputKind",
                reason = "must identify Game's BEGIN_REBIRTH output",
            )
        }
        val expectedLocalName = RebirthStartContract.SOURCE_LOCAL_ORDINAL_PREFIX +
            (source.sourceOrdinal - 1u).toString()
        if (source.sourceLocalOrdinalOrName != expectedLocalName) {
            return RebirthRejection.InvalidStartCommand(
                field = "sourceLocalOrdinalOrName",
                reason = "must match the source ordinal",
            )
        }
        if (
            command.causalBudgetScope.ownerBallInstanceId != source.sourceBallInstanceId ||
            command.causalBudgetScope.operationId != source.sourceOperationId
        ) {
            return RebirthRejection.InvalidStartCommand(
                field = "causalBudgetScope",
                reason = "must preserve the initiating Game operation",
            )
        }
        if (command.causalDepth != RebirthStartContract.COMMAND_CAUSAL_DEPTH) {
            return RebirthRejection.InvalidStartCommand(
                field = "causalDepth",
                reason = "must be ${RebirthStartContract.COMMAND_CAUSAL_DEPTH}",
            )
        }
        return null
    }

    private fun decideProfileResult(
        state: RebirthFlowState,
        result: ProfileAdvanceRebirthModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> =
        when (val status = state.status) {
            RebirthStatus.Idle -> Rejected(
                RebirthRejection.StaleProfileResult(result.commandSource, expected = null),
            )
            is RebirthStatus.AwaitingProfileResult ->
                decidePendingProfileResult(state, status, result, context)
            is RebirthStatus.AwaitingGameStartResult ->
                decideTerminalProfileResultDuplicate(state, status.profileResult, result, context)
            is RebirthStatus.Completed ->
                decideTerminalProfileResultDuplicate(state, status.profileResult, result, context)
            is RebirthStatus.GameStartRejected ->
                decideTerminalProfileResultDuplicate(state, status.profileResult, result, context)
            is RebirthStatus.Rejected ->
                decideTerminalProfileResultDuplicate(state, status.result, result, context)
        }

    private fun decidePendingProfileResult(
        state: RebirthFlowState,
        status: RebirthStatus.AwaitingProfileResult,
        result: ProfileAdvanceRebirthModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        val expectedCommand = status.targetCommand
        if (result.commandSource != expectedCommand.commandSource) {
            return Rejected(
                RebirthRejection.StaleProfileResult(
                    received = result.commandSource,
                    expected = expectedCommand.commandSource,
                ),
            )
        }
        if (result.causalBudgetScope != expectedCommand.causalBudgetScope) {
            return Rejected(
                RebirthRejection.InvalidProfileResultCausalScope(
                    received = result.causalBudgetScope,
                    expected = expectedCommand.causalBudgetScope,
                ),
            )
        }
        if (result.causalDepth != ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH) {
            return Rejected(
                RebirthRejection.InvalidProfileResult(
                    field = "causalDepth",
                    reason = "must be ${ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH}",
                ),
            )
        }
        if (result.provenance != ProfileAdvanceRebirthContract.RESULT_PROVENANCE) {
            return Rejected(
                RebirthRejection.InvalidProfileResultProvenance(
                    reason = "unsupported Profile result authority or protocol version",
                ),
            )
        }
        return when (result) {
            is ProfileAdvanceRebirthModuleResult.Advanced ->
                decideProfileAdvanced(state, status, result, context)
            is ProfileAdvanceRebirthModuleResult.Rejected -> accepted(
                state = state.copy(
                    acceptedRevision = context.proposedCommitRevision,
                    status = RebirthStatus.Rejected(
                        operationId = context.operationId,
                        startCommand = status.startCommand,
                        expectedLevel = status.expectedLevel,
                        profileCommandSource = status.profileCommandSource,
                        result = result,
                    ),
                    transitionSteps = 1,
                ),
                outputs = emptyList(),
            )
        }
    }

    private fun decideProfileAdvanced(
        state: RebirthFlowState,
        status: RebirthStatus.AwaitingProfileResult,
        result: ProfileAdvanceRebirthModuleResult.Advanced,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        val expectedNewLevel = status.expectedLevel.toLong() + 1L
        if (result.newLevel.toLong() != expectedNewLevel) {
            return Rejected(
                RebirthRejection.UnexpectedAdvancedLevel(expectedNewLevel, result.newLevel),
            )
        }
        validateProfileSnapshotReference(result.profileSnapshotReference)?.let {
            return Rejected(it)
        }

        val handle = RebirthSemanticHandle(
            operationId = context.operationId,
            outputKind = RebirthOutputKind.GAME_START_RUN,
            localOrdinalOrName = GAME_START_LOCAL_NAME,
        )
        val gameSource = RebirthCommandSource(
            sourceBallInstanceId = RebirthFlowProtocol.BALL_INSTANCE_ID,
            sourceCommitRevision = context.proposedCommitRevision,
            sourceOrdinal = 0u,
            semanticHandle = handle,
        )
        val snapshotReference = result.profileSnapshotReference
        val targetCommand = GameRunStartModuleCommand(
            commandSource = gameSource.toGameRunStartCommandSource(),
            causalBudgetScope = GameRunStartCausalScope(
                ownerBallInstanceId = context.causalBudgetScope.ownerBallInstanceId,
                operationId = context.causalBudgetScope.operationId,
            ),
            causalDepth = GameRunStartContract.COMMAND_CAUSAL_DEPTH,
            runConfigurationReference = GameRunConfigurationReference(
                profileBallInstanceId = snapshotReference.profileBallInstanceId,
                profileCommitRevision = snapshotReference.profileCommitRevision,
                profileStateSchemaVersion = snapshotReference.profileStateSchemaVersion,
                rebirthLevel = result.newLevel,
            ),
        )
        val command = RebirthModuleCommandRequest(
            semanticHandle = handle,
            sourceOrdinal = 0u,
            target = RebirthCommandTarget.GAME,
            payload = RebirthModuleCommand.StartGameRun(
                newLevel = result.newLevel,
                profileSnapshotReference = snapshotReference,
                targetCommand = targetCommand,
            ),
        )
        check(gameSource == command.sourceAt(context.proposedCommitRevision))
        return accepted(
            state = state.copy(
                acceptedRevision = context.proposedCommitRevision,
                status = RebirthStatus.AwaitingGameStartResult(
                    operationId = context.operationId,
                    startCommand = status.startCommand,
                    expectedLevel = status.expectedLevel,
                    newLevel = result.newLevel,
                    profileSnapshotReference = snapshotReference,
                    profileCommandSource = status.profileCommandSource,
                    profileResult = result,
                    gameCommandSource = gameSource,
                    targetCommand = targetCommand,
                ),
                transitionSteps = 1,
            ),
            outputs = listOf(command),
        )
    }

    private fun validateProfileSnapshotReference(
        reference: ProfileSnapshotReference,
    ): RebirthRejection.InvalidProfileSnapshotReference? {
        if (reference.profileBallInstanceId != ProfileProjection.BALL_INSTANCE_ID) {
            return RebirthRejection.InvalidProfileSnapshotReference(
                "profileBallInstanceId",
                "must identify the local Profile authority",
            )
        }
        if (reference.profileCommitRevision == 0uL) {
            return RebirthRejection.InvalidProfileSnapshotReference(
                "profileCommitRevision",
                "must identify an accepted Profile snapshot",
            )
        }
        if (reference.profileStateSchemaVersion != ProfileProjection.STATE_SCHEMA_VERSION) {
            return RebirthRejection.InvalidProfileSnapshotReference(
                "profileStateSchemaVersion",
                "unsupported schema version",
            )
        }
        return null
    }

    private fun decideGameResult(
        state: RebirthFlowState,
        result: GameRunStartModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> =
        when (val status = state.status) {
            is RebirthStatus.AwaitingGameStartResult ->
                decidePendingGameResult(state, status, result, context)
            is RebirthStatus.Completed ->
                decideTerminalGameResultDuplicate(state, status.result, result, context)
            is RebirthStatus.GameStartRejected ->
                decideTerminalGameResultDuplicate(state, status.result, result, context)
            RebirthStatus.Idle,
            is RebirthStatus.AwaitingProfileResult,
            is RebirthStatus.Rejected,
            -> Rejected(
                RebirthRejection.StaleGameStartResult(
                    received = result.commandSource,
                    expected = null,
                ),
            )
        }

    private fun decidePendingGameResult(
        state: RebirthFlowState,
        status: RebirthStatus.AwaitingGameStartResult,
        result: GameRunStartModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        val expectedCommand = status.targetCommand
        if (result.commandSource != expectedCommand.commandSource) {
            return Rejected(
                RebirthRejection.StaleGameStartResult(
                    received = result.commandSource,
                    expected = expectedCommand.commandSource,
                ),
            )
        }
        if (result.provenance != GameRunStartContract.RESULT_PROVENANCE) {
            return Rejected(
                RebirthRejection.InvalidGameStartResultProvenance(
                    reason = "unsupported Game result authority or protocol version",
                ),
            )
        }
        if (result.causalBudgetScope != expectedCommand.causalBudgetScope) {
            return Rejected(
                RebirthRejection.InvalidGameStartResultCausalScope(
                    received = result.causalBudgetScope,
                    expected = expectedCommand.causalBudgetScope,
                ),
            )
        }
        if (result.causalDepth != GameRunStartContract.RESULT_CAUSAL_DEPTH) {
            return Rejected(
                RebirthRejection.InvalidGameStartResult(
                    field = "causalDepth",
                    reason = "must be ${GameRunStartContract.RESULT_CAUSAL_DEPTH}",
                ),
            )
        }
        if (result is GameRunStartModuleResult.Started && result.gameCommitRevision == 0uL) {
            return Rejected(
                RebirthRejection.InvalidGameStartResult(
                    field = "gameCommitRevision",
                    reason = "must identify an accepted Game commit",
                ),
            )
        }

        val nextStatus = when (result) {
            is GameRunStartModuleResult.Started -> RebirthStatus.Completed(
                operationId = status.operationId,
                startCommand = status.startCommand,
                expectedLevel = status.expectedLevel,
                newLevel = status.newLevel,
                profileSnapshotReference = status.profileSnapshotReference,
                profileCommandSource = status.profileCommandSource,
                profileResult = status.profileResult,
                gameCommandSource = status.gameCommandSource,
                result = result,
            )
            is GameRunStartModuleResult.Rejected -> RebirthStatus.GameStartRejected(
                operationId = status.operationId,
                startCommand = status.startCommand,
                expectedLevel = status.expectedLevel,
                newLevel = status.newLevel,
                profileSnapshotReference = status.profileSnapshotReference,
                profileCommandSource = status.profileCommandSource,
                profileResult = status.profileResult,
                gameCommandSource = status.gameCommandSource,
                result = result,
            )
        }
        return accepted(
            state = state.copy(
                acceptedRevision = context.proposedCommitRevision,
                status = nextStatus,
                transitionSteps = 1,
            ),
            outputs = emptyList(),
        )
    }

    private fun decideTerminalProfileResultDuplicate(
        state: RebirthFlowState,
        retained: ProfileAdvanceRebirthModuleResult,
        received: ProfileAdvanceRebirthModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        if (received.commandSource != retained.commandSource) {
            return Rejected(
                RebirthRejection.StaleProfileResult(
                    received = received.commandSource,
                    expected = retained.commandSource,
                ),
            )
        }
        if (received != retained) {
            return Rejected(RebirthRejection.ConflictingProfileResult(received.commandSource))
        }
        return accepted(
            state = state.copy(
                acceptedRevision = context.proposedCommitRevision,
                transitionSteps = 1,
            ),
            outputs = emptyList(),
        )
    }

    private fun decideTerminalGameResultDuplicate(
        state: RebirthFlowState,
        retained: GameRunStartModuleResult,
        received: GameRunStartModuleResult,
        context: RebirthDecisionContext,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> {
        if (received.commandSource != retained.commandSource) {
            return Rejected(
                RebirthRejection.StaleGameStartResult(
                    received = received.commandSource,
                    expected = retained.commandSource,
                ),
            )
        }
        if (received != retained) {
            return Rejected(
                RebirthRejection.ConflictingGameStartResult(received.commandSource),
            )
        }
        return accepted(
            state = state.copy(
                acceptedRevision = context.proposedCommitRevision,
                transitionSteps = 1,
            ),
            outputs = emptyList(),
        )
    }

    private fun accepted(
        state: RebirthFlowState,
        outputs: List<RebirthModuleCommandRequest>,
    ): DecisionResult<RebirthFlowState, RebirthModuleCommandRequest> =
        Accepted(Decision(state, outputs))

    private fun RebirthModuleCommandRequest.sourceAt(
        commitRevision: ULong,
    ): RebirthCommandSource = RebirthCommandSource(
        sourceBallInstanceId = RebirthFlowProtocol.BALL_INSTANCE_ID,
        sourceCommitRevision = commitRevision,
        sourceOrdinal = sourceOrdinal,
        semanticHandle = semanticHandle,
    )

    private fun RebirthCommandSource.toProfileCommandSource(): ProfileCommandSource =
        ProfileCommandSource(
            sourceBallInstanceId = sourceBallInstanceId,
            sourceCommitRevision = sourceCommitRevision,
            sourceOrdinal = sourceOrdinal,
            sourceOperationId = semanticHandle.operationId.value,
            sourceOutputKind = semanticHandle.outputKind.name,
            sourceLocalOrdinalOrName = semanticHandle.localOrdinalOrName,
        )

    private fun RebirthCommandSource.toGameRunStartCommandSource(): GameRunStartCommandSource =
        GameRunStartCommandSource(
            sourceBallInstanceId = sourceBallInstanceId,
            sourceCommitRevision = sourceCommitRevision,
            sourceOrdinal = sourceOrdinal,
            sourceOperationId = semanticHandle.operationId.value,
            sourceOutputKind = semanticHandle.outputKind.name,
            sourceLocalOrdinalOrName = semanticHandle.localOrdinalOrName,
        )

    private fun ProfileCausalScope.toRebirthCausalScope(): RebirthCausalScope =
        RebirthCausalScope(
            ownerBallInstanceId = ownerBallInstanceId,
            operationId = operationId,
        )

    private fun GameRunStartCausalScope.toRebirthCausalScope(): RebirthCausalScope =
        RebirthCausalScope(
            ownerBallInstanceId = ownerBallInstanceId,
            operationId = operationId,
        )

    private fun ULong.nextOrNull(): ULong? = if (this == ULong.MAX_VALUE) null else this + 1uL

    private companion object {
        const val PROFILE_ADVANCE_LOCAL_NAME = "profile-advance-rebirth"
        const val GAME_START_LOCAL_NAME = "game-start-run"
    }
}
