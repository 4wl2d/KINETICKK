// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.rebirth

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.game.nucleus.protocol.GameRunStartRejectionReason
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileSnapshotReference
import kinetickk.flows.rebirth.nucleus.protocol.GameRunStartResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.ProfileAdvanceRebirthResultObserved
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCommandTarget
import kinetickk.flows.rebirth.nucleus.protocol.RebirthCausalScope
import kinetickk.flows.rebirth.nucleus.protocol.RebirthDecisionContext
import kinetickk.flows.rebirth.nucleus.protocol.RebirthFlowProtocol
import kinetickk.flows.rebirth.nucleus.protocol.RebirthModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthOperationId
import kinetickk.flows.rebirth.nucleus.protocol.RebirthOutputKind
import kinetickk.flows.rebirth.nucleus.protocol.RebirthRejection
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartCommandSource
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartContract
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartModuleCommand
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStatus
import kinetickk.flows.rebirth.nucleus.state.RebirthFlowState
import kinetickk.flows.rebirth.nucleus.transition.RebirthFlowNucleus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RebirthFlowBallTest {
    @Test
    fun startCreatesOneExactProfileTargetCommandAndRetainsIt() {
        val ball = RebirthFlowBall.create()

        val result = assertIs<RebirthDispatchResult.Committed>(ball.start(expectedLevel = 0))

        assertEquals(1uL, result.sourceCommitRevision)
        val committedCommand = result.commands.single()
        assertEquals(RebirthCommandTarget.PROFILE, committedCommand.request.target)
        assertEquals(
            RebirthOutputKind.PROFILE_ADVANCE_REBIRTH,
            committedCommand.source.semanticHandle.outputKind,
        )
        assertEquals(RebirthFlowProtocol.BALL_INSTANCE_ID, committedCommand.source.sourceBallInstanceId)
        assertEquals(1uL, committedCommand.source.sourceCommitRevision)
        assertEquals(0u, committedCommand.source.sourceOrdinal)

        val payload = assertIs<RebirthModuleCommand.AdvanceProfileRebirth>(
            committedCommand.request.payload,
        )
        assertEquals(0, payload.expectedLevel)
        val target = payload.targetCommand
        assertEquals(committedCommand.source.sourceBallInstanceId, target.commandSource.sourceBallInstanceId)
        assertEquals(committedCommand.source.sourceCommitRevision, target.commandSource.sourceCommitRevision)
        assertEquals(committedCommand.source.sourceOrdinal, target.commandSource.sourceOrdinal)
        assertEquals(
            committedCommand.source.semanticHandle.operationId.value,
            target.commandSource.sourceOperationId,
        )
        assertEquals(
            committedCommand.source.semanticHandle.outputKind.name,
            target.commandSource.sourceOutputKind,
        )
        assertEquals(
            committedCommand.source.semanticHandle.localOrdinalOrName,
            target.commandSource.sourceLocalOrdinalOrName,
        )
        assertEquals(RebirthStartContract.SOURCE_BALL_INSTANCE_ID, target.causalBudgetScope.ownerBallInstanceId)
        assertEquals(
            committedCommand.source.semanticHandle.operationId.value,
            target.causalBudgetScope.operationId,
        )
        assertEquals(ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH, target.causalDepth)

        val status = assertIs<RebirthStatus.AwaitingProfileResult>(result.statusRead.payload)
        assertEquals(rebirthStartCommand(expectedLevel = 0), status.startCommand)
        assertEquals(committedCommand.source, status.profileCommandSource)
        assertEquals(target, status.targetCommand)
        assertEquals(0, status.expectedLevel)
        assertEquals(1uL, result.statusRead.consistencyStamp.commitRevision)
    }

    @Test
    fun matchingProfileAdvancedResultCommandsGameWithTheExactTargetSnapshotReference() {
        val ball = RebirthFlowBall.create()
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(2))
        val profileCommand = start.profileTargetCommand()
        val profileResult = profileCommand.advancedResult(
            newLevel = 3,
            profileRevision = 9uL,
        )

        val advanced = assertIs<RebirthDispatchResult.Committed>(ball.accept(profileResult))

        assertEquals(2uL, advanced.sourceCommitRevision)
        val gameCommand = advanced.commands.single()
        assertEquals(RebirthCommandTarget.GAME, gameCommand.request.target)
        assertEquals(RebirthOutputKind.GAME_START_RUN, gameCommand.source.semanticHandle.outputKind)
        val payload = assertIs<RebirthModuleCommand.StartGameRun>(gameCommand.request.payload)
        assertEquals(3, payload.newLevel)
        assertEquals(profileResult.profileSnapshotReference, payload.profileSnapshotReference)
        assertEquals(gameCommand.source.sourceBallInstanceId, payload.targetCommand.commandSource.sourceBallInstanceId)
        assertEquals(gameCommand.source.sourceCommitRevision, payload.targetCommand.commandSource.sourceCommitRevision)
        assertEquals(gameCommand.source.sourceOrdinal, payload.targetCommand.commandSource.sourceOrdinal)
        assertEquals(
            gameCommand.source.semanticHandle.operationId.value,
            payload.targetCommand.commandSource.sourceOperationId,
        )
        assertEquals(
            gameCommand.source.semanticHandle.outputKind.name,
            payload.targetCommand.commandSource.sourceOutputKind,
        )
        assertEquals(
            gameCommand.source.semanticHandle.localOrdinalOrName,
            payload.targetCommand.commandSource.sourceLocalOrdinalOrName,
        )
        assertEquals(
            RebirthStartContract.SOURCE_BALL_INSTANCE_ID,
            payload.targetCommand.causalBudgetScope.ownerBallInstanceId,
        )
        assertEquals(
            gameCommand.source.semanticHandle.operationId.value,
            payload.targetCommand.causalBudgetScope.operationId,
        )
        assertEquals(GameRunStartContract.COMMAND_CAUSAL_DEPTH, payload.targetCommand.causalDepth)
        assertEquals(
            profileResult.profileSnapshotReference.profileBallInstanceId,
            payload.targetCommand.runConfigurationReference.profileBallInstanceId,
        )
        assertEquals(
            profileResult.profileSnapshotReference.profileCommitRevision,
            payload.targetCommand.runConfigurationReference.profileCommitRevision,
        )
        assertEquals(3, payload.targetCommand.runConfigurationReference.rebirthLevel)

        val status = assertIs<RebirthStatus.AwaitingGameStartResult>(advanced.statusRead.payload)
        assertEquals(start.commands.single().source, status.profileCommandSource)
        assertEquals(profileResult, status.profileResult)
        assertEquals(gameCommand.source, status.gameCommandSource)
        assertEquals(payload.targetCommand, status.targetCommand)
        assertEquals(profileResult.profileSnapshotReference, status.profileSnapshotReference)
        assertEquals(3, status.newLevel)
    }

    @Test
    fun matchingProfileRejectedResultBecomesTerminalWithoutAnotherCommand() {
        val ball = RebirthFlowBall.create()
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(4))
        val profileCommand = start.profileTargetCommand()
        val profileResult = profileCommand.rejectedResult(
            ProfileAdvanceRebirthRejectionReason.CURRENT_LEVEL_NOT_CLEARED,
        )

        val rejected = assertIs<RebirthDispatchResult.Committed>(ball.accept(profileResult))

        assertTrue(rejected.commands.isEmpty())
        val status = assertIs<RebirthStatus.Rejected>(rejected.statusRead.payload)
        assertEquals(start.commands.single().source, status.profileCommandSource)
        assertEquals(profileResult, status.result)
    }

    @Test
    fun exactStartRedeliveryReplaysWhileANewStartIsRejectedAsBusy() {
        val ball = RebirthFlowBall.create()
        val command = rebirthStartCommand(expectedLevel = 0)
        val first = assertIs<RebirthDispatchResult.Committed>(ball.start(command))

        val replay = assertIs<RebirthDispatchResult.Replayed>(ball.start(command))
        assertEquals(first.statusRead, replay.statusRead)

        val conflict = assertIs<RebirthDispatchResult.DecisionRejected>(
            ball.start(command.copy(expectedLevel = 1)),
        )
        assertIs<RebirthRejection.ConflictingStartCommand>(conflict.reason)

        val second = assertIs<RebirthDispatchResult.DecisionRejected>(
            ball.start(
                rebirthStartCommand(
                    expectedLevel = 0,
                    sourceCommitRevision = 2uL,
                    sourceOperationId = 2uL,
                ),
            ),
        )

        val busy = assertIs<RebirthRejection.Busy>(second.reason)
        assertEquals(first.commands.single().source, busy.outstanding)
        val current = ball.status()
        assertIs<RebirthStatus.AwaitingProfileResult>(current.payload)
        assertEquals(1uL, current.consistencyStamp.commitRevision)
    }

    @Test
    fun aProfileResultMustMatchTheCompleteTargetCommandSource() {
        val ball = RebirthFlowBall.create()
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
        val profileCommand = start.profileTargetCommand()
        val wrongRevision = profileCommand.advancedResult(1, 4uL).copy(
            commandSource = profileCommand.commandSource.copy(
                sourceCommitRevision = profileCommand.commandSource.sourceCommitRevision + 1uL,
            ),
        )

        val result = assertIs<RebirthDispatchResult.DecisionRejected>(ball.accept(wrongRevision))

        val stale = assertIs<RebirthRejection.StaleProfileResult>(result.reason)
        assertEquals(profileCommand.commandSource, stale.expected)
        assertEquals(1uL, ball.status().consistencyStamp.commitRevision)
    }

    @Test
    fun profileResultMustMatchExactCausalScopeAndDepth() {
        val scopeBall = RebirthFlowBall.create()
        val scopeCommand = assertIs<RebirthDispatchResult.Committed>(scopeBall.start(0))
            .profileTargetCommand()
        val wrongScope = scopeCommand.advancedResult(1, 4uL).copy(
            causalBudgetScope = scopeCommand.causalBudgetScope.copy(
                ownerBallInstanceId = "wrong-owner",
            ),
        )

        val scopeRejected = assertIs<RebirthDispatchResult.DecisionRejected>(
            scopeBall.accept(wrongScope),
        )

        assertIs<RebirthRejection.InvalidProfileResultCausalScope>(scopeRejected.reason)
        assertEquals(1uL, scopeBall.status().consistencyStamp.commitRevision)

        val depthBall = RebirthFlowBall.create()
        val depthCommand = assertIs<RebirthDispatchResult.Committed>(depthBall.start(0))
            .profileTargetCommand()
        val wrongDepth = depthCommand.advancedResult(1, 4uL).copy(
            causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH + 1,
        )

        val depthRejected = assertIs<RebirthDispatchResult.DecisionRejected>(
            depthBall.accept(wrongDepth),
        )

        assertIs<RebirthRejection.InvalidProfileResult>(depthRejected.reason)
        assertEquals(1uL, depthBall.status().consistencyStamp.commitRevision)
    }

    @Test
    fun exactProfileDuplicateIsIdempotentButSameSourceWithDifferentResultFailsClosed() {
        val ball = RebirthFlowBall.create()
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
        val result = start.profileTargetCommand().advancedResult(1, 4uL)
        val first = assertIs<RebirthDispatchResult.Committed>(ball.accept(result))
        assertEquals(1, first.commands.size)

        val duplicate = assertIs<RebirthDispatchResult.Committed>(ball.accept(result))
        assertTrue(duplicate.commands.isEmpty())

        val conflict = assertIs<RebirthDispatchResult.DecisionRejected>(
            ball.accept(result.copy(newLevel = 2)),
        )
        assertIs<RebirthRejection.ConflictingProfileResult>(conflict.reason)
        val status = assertIs<RebirthStatus.AwaitingGameStartResult>(ball.status().payload)
        assertEquals(1, status.newLevel)
    }

    @Test
    fun exactGameStartedResultCompletesTheWorkflow() {
        val ball = RebirthFlowBall.create()
        val awaiting = advanceToAwaitingGame(ball)
        val started = awaiting.startedResult(gameCommitRevision = 8uL)

        val completed = assertIs<RebirthDispatchResult.Committed>(ball.accept(started))

        assertTrue(completed.commands.isEmpty())
        assertEquals(3uL, completed.sourceCommitRevision)
        val status = assertIs<RebirthStatus.Completed>(completed.statusRead.payload)
        assertEquals(awaiting.gameCommandSource, status.gameCommandSource)
        assertEquals(awaiting.profileResult, status.profileResult)
        assertEquals(started, status.result)
    }

    @Test
    fun terminalStartReceiptReplaysExactlyAndRejectsOlderSourcesAfterANewerStart() {
        val ball = RebirthFlowBall.create()
        val firstCommand = rebirthStartCommand(expectedLevel = 0)
        val first = assertIs<RebirthDispatchResult.Committed>(ball.start(firstCommand))
        val advanced = assertIs<RebirthDispatchResult.Committed>(
            ball.accept(first.profileTargetCommand().advancedResult(1, 4uL)),
        )
        val awaiting = assertIs<RebirthStatus.AwaitingGameStartResult>(
            advanced.statusRead.payload,
        )
        assertIs<RebirthDispatchResult.Committed>(
            ball.accept(awaiting.startedResult(gameCommitRevision = 8uL)),
        )
        val terminal = ball.status()

        val exactTerminalReplay = assertIs<RebirthDispatchResult.Replayed>(
            ball.start(firstCommand),
        )
        assertEquals(terminal, exactTerminalReplay.statusRead)
        assertEquals(terminal, ball.status())

        val laterCommand = rebirthStartCommand(
            expectedLevel = 1,
            sourceCommitRevision = 4uL,
            sourceOperationId = 2uL,
        )
        val later = assertIs<RebirthDispatchResult.Committed>(ball.start(laterCommand))

        val stale = assertIs<RebirthDispatchResult.DecisionRejected>(
            ball.start(firstCommand),
        )
        assertIs<RebirthRejection.StaleStartCommand>(stale.reason)
        val laterReplay = assertIs<RebirthDispatchResult.Replayed>(ball.start(laterCommand))
        assertEquals(later.statusRead, laterReplay.statusRead)
        assertEquals(later.statusRead, ball.status())
    }

    @Test
    fun profileCompletionAdmissionFailureIsRetainedExactlyAndResumeEmitsItsGameCommand() {
        var rejectNextProfileCompletion = true
        val ball = RebirthFlowBall.createForTest { pulse, _ ->
            if (pulse is ProfileAdvanceRebirthResultObserved && rejectNextProfileCompletion) {
                rejectNextProfileCompletion = false
                AdmissionFailure.ReentrantSubmission
            } else {
                null
            }
        }
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
        val profileCommand = start.profileTargetCommand()
        val exactCompletion = profileCommand.advancedResult(1, 7uL)

        val rejected = assertIs<RebirthDispatchResult.AdmissionRejected>(
            ball.accept(exactCompletion),
        )

        assertEquals(AdmissionFailure.ReentrantSubmission, rejected.reason)
        assertIs<RebirthStatus.AwaitingProfileResult>(ball.status().payload)
        assertEquals(1uL, ball.status().consistencyStamp.commitRevision)
        val retained = assertIs<RebirthContinuationStatus.Retained>(ball.completionStatus())
        assertEquals(RebirthOperationId(1uL), retained.operationId)
        assertEquals(
            RebirthCausalScope(RebirthStartContract.SOURCE_BALL_INSTANCE_ID, 1uL),
            retained.causalBudgetScope,
        )
        assertEquals(ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH, retained.causalDepth)
        assertEquals(RebirthCompletionKind.PROFILE_RESULT, retained.completionKind)
        assertEquals(0, retained.retriesUsed)
        assertEquals(1, retained.maxRetries)
        assertEquals(AdmissionFailure.ReentrantSubmission, retained.lastFailure)

        val competing = assertIs<RebirthDispatchResult.AdmissionRejected>(
            ball.accept(
                profileCommand.rejectedResult(
                    ProfileAdvanceRebirthRejectionReason.TARGET_ADMISSION_REJECTED,
                ),
            ),
        )
        assertIs<AdmissionFailure.DeliveryBackpressure>(competing.reason)
        assertEquals(retained, ball.completionStatus())

        val resumed = assertIs<RebirthDispatchResult.Committed>(
            ball.resumeRetainedCompletion(),
        )

        assertEquals(2uL, resumed.sourceCommitRevision)
        val gameCommand = assertIs<RebirthModuleCommand.StartGameRun>(
            resumed.commands.single().request.payload,
        )
        assertEquals(exactCompletion.profileSnapshotReference, gameCommand.profileSnapshotReference)
        val status = assertIs<RebirthStatus.AwaitingGameStartResult>(resumed.statusRead.payload)
        assertEquals(exactCompletion, status.profileResult)
        assertEquals(RebirthContinuationStatus.Idle, ball.completionStatus())
    }

    @Test
    fun gameCompletionAdmissionFailureIsRetainedExactlyAndResumeCompletesTheFlow() {
        var rejectNextGameCompletion = true
        val ball = RebirthFlowBall.createForTest { pulse, _ ->
            if (pulse is GameRunStartResultObserved && rejectNextGameCompletion) {
                rejectNextGameCompletion = false
                AdmissionFailure.ReentrantSubmission
            } else {
                null
            }
        }
        val awaiting = advanceToAwaitingGame(ball)
        val exactCompletion = awaiting.startedResult(gameCommitRevision = 12uL)

        val rejected = assertIs<RebirthDispatchResult.AdmissionRejected>(
            ball.accept(exactCompletion),
        )

        assertEquals(AdmissionFailure.ReentrantSubmission, rejected.reason)
        assertIs<RebirthStatus.AwaitingGameStartResult>(ball.status().payload)
        assertEquals(2uL, ball.status().consistencyStamp.commitRevision)
        val retained = assertIs<RebirthContinuationStatus.Retained>(ball.completionStatus())
        assertEquals(RebirthCompletionKind.GAME_RUN_START_RESULT, retained.completionKind)
        assertEquals(GameRunStartContract.RESULT_CAUSAL_DEPTH, retained.causalDepth)
        assertEquals(0, retained.retriesUsed)
        assertEquals(1, retained.maxRetries)

        val resumed = assertIs<RebirthDispatchResult.Committed>(
            ball.resumeRetainedCompletion(),
        )

        assertTrue(resumed.commands.isEmpty())
        val completed = assertIs<RebirthStatus.Completed>(resumed.statusRead.payload)
        assertEquals(exactCompletion, completed.result)
        assertEquals(RebirthContinuationStatus.Idle, ball.completionStatus())
        assertEquals(null, ball.resumeRetainedCompletion())
    }

    @Test
    fun repeatedCompletionAdmissionFailureStopsAtTheFiniteRetryLimit() {
        var attempts = 0
        val ball = RebirthFlowBall.createForTest { pulse, _ ->
            if (pulse is ProfileAdvanceRebirthResultObserved) {
                attempts++
                AdmissionFailure.ReentrantSubmission
            } else {
                null
            }
        }
        val start = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
        val exactCompletion = start.profileTargetCommand().advancedResult(1, 7uL)

        assertIs<RebirthDispatchResult.AdmissionRejected>(ball.accept(exactCompletion))
        assertIs<RebirthDispatchResult.AdmissionRejected>(ball.resumeRetainedCompletion())

        val stopped = assertIs<RebirthContinuationStatus.DispatchStopped>(
            ball.completionStatus(),
        )
        assertEquals(1, stopped.retriesUsed)
        assertEquals(1, stopped.maxRetries)
        assertEquals(2, attempts)
        val backpressure = assertIs<RebirthDispatchResult.AdmissionRejected>(
            ball.resumeRetainedCompletion(),
        )
        assertIs<AdmissionFailure.DeliveryBackpressure>(backpressure.reason)
        assertEquals(2, attempts)
    }

    @Test
    fun exactGameRejectionProducesAnHonestRejectedTerminalStatus() {
        val ball = RebirthFlowBall.create()
        val awaiting = advanceToAwaitingGame(ball)
        val rejectedResult = GameRunStartModuleResult.Rejected(
            commandSource = awaiting.targetCommand.commandSource,
            causalBudgetScope = awaiting.targetCommand.causalBudgetScope,
            causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
            provenance = GameRunStartContract.RESULT_PROVENANCE,
            reason = GameRunStartRejectionReason.PROFILE_REFERENCE_NOT_CURRENT,
        )

        val rejected = assertIs<RebirthDispatchResult.Committed>(ball.accept(rejectedResult))

        assertTrue(rejected.commands.isEmpty())
        val status = assertIs<RebirthStatus.GameStartRejected>(rejected.statusRead.payload)
        assertEquals(rejectedResult, status.result)
    }

    @Test
    fun workflowRemainsBusyUntilTheGameResultArrives() {
        val ball = RebirthFlowBall.create()
        val awaiting = advanceToAwaitingGame(ball)

        val second = assertIs<RebirthDispatchResult.DecisionRejected>(
            ball.start(
                rebirthStartCommand(
                    expectedLevel = 0,
                    sourceCommitRevision = 2uL,
                    sourceOperationId = 2uL,
                ),
            ),
        )

        val busy = assertIs<RebirthRejection.Busy>(second.reason)
        assertEquals(awaiting.gameCommandSource, busy.outstanding)
        assertIs<RebirthStatus.AwaitingGameStartResult>(ball.status().payload)
        assertEquals(2uL, ball.status().consistencyStamp.commitRevision)
    }

    @Test
    fun staleGameResultIsRejectedAndExactDuplicateIsIdempotentWhileConflictFailsClosed() {
        val ball = RebirthFlowBall.create()
        val awaiting = advanceToAwaitingGame(ball)
        val started = awaiting.startedResult(gameCommitRevision = 8uL)
        val staleResult = started.copy(
            commandSource = started.commandSource.copy(
                sourceCommitRevision = started.commandSource.sourceCommitRevision + 1uL,
            ),
        )

        val stale = assertIs<RebirthDispatchResult.DecisionRejected>(ball.accept(staleResult))
        assertIs<RebirthRejection.StaleGameStartResult>(stale.reason)
        assertEquals(2uL, ball.status().consistencyStamp.commitRevision)

        assertIs<RebirthDispatchResult.Committed>(ball.accept(started))
        val duplicate = assertIs<RebirthDispatchResult.Committed>(ball.accept(started))
        assertTrue(duplicate.commands.isEmpty())

        val conflicting = GameRunStartModuleResult.Rejected(
            commandSource = started.commandSource,
            causalBudgetScope = started.causalBudgetScope,
            causalDepth = started.causalDepth,
            provenance = started.provenance,
            reason = GameRunStartRejectionReason.TARGET_DECISION_REJECTED,
        )
        val conflict = assertIs<RebirthDispatchResult.DecisionRejected>(ball.accept(conflicting))
        assertIs<RebirthRejection.ConflictingGameStartResult>(conflict.reason)
        assertIs<RebirthStatus.Completed>(ball.status().payload)
    }

    @Test
    fun invalidAdvancedLevelAndProfileSnapshotReferenceAreRejectedWithoutMutation() {
        val levelBall = RebirthFlowBall.create()
        val levelCommand = assertIs<RebirthDispatchResult.Committed>(levelBall.start(3))
            .profileTargetCommand()
        val wrongLevel = assertIs<RebirthDispatchResult.DecisionRejected>(
            levelBall.accept(levelCommand.advancedResult(5, 2uL)),
        )
        assertIs<RebirthRejection.UnexpectedAdvancedLevel>(wrongLevel.reason)
        assertEquals(1uL, levelBall.status().consistencyStamp.commitRevision)

        val referenceBall = RebirthFlowBall.create()
        val referenceCommand = assertIs<RebirthDispatchResult.Committed>(referenceBall.start(0))
            .profileTargetCommand()
        val badReference = assertIs<RebirthDispatchResult.DecisionRejected>(
            referenceBall.accept(
                referenceCommand.advancedResult(1, 2uL).copy(
                    profileSnapshotReference = profileSnapshotReference(2uL).copy(
                        profileStateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION + 1,
                    ),
                ),
            ),
        )
        assertIs<RebirthRejection.InvalidProfileSnapshotReference>(badReference.reason)
        assertEquals(1uL, referenceBall.status().consistencyStamp.commitRevision)
    }

    @Test
    fun profileSnapshotMustIdentifyTheProfileAuthorityAndAnAcceptedCommit() {
        val authorityBall = RebirthFlowBall.create()
        val authorityCommand = assertIs<RebirthDispatchResult.Committed>(authorityBall.start(0))
            .profileTargetCommand()
        val wrongAuthority = authorityCommand.advancedResult(1, 2uL).copy(
            profileSnapshotReference = profileSnapshotReference(2uL).copy(
                profileBallInstanceId = "wrong-profile",
            ),
        )
        val authorityRejected = assertIs<RebirthDispatchResult.DecisionRejected>(
            authorityBall.accept(wrongAuthority),
        )
        assertIs<RebirthRejection.InvalidProfileSnapshotReference>(authorityRejected.reason)

        val revisionBall = RebirthFlowBall.create()
        val revisionCommand = assertIs<RebirthDispatchResult.Committed>(revisionBall.start(0))
            .profileTargetCommand()
        val missingCommit = revisionCommand.advancedResult(1, 0uL)
        val revisionRejected = assertIs<RebirthDispatchResult.DecisionRejected>(
            revisionBall.accept(missingCommit),
        )
        assertIs<RebirthRejection.InvalidProfileSnapshotReference>(revisionRejected.reason)
    }

    @Test
    fun oversizedProfileResultMetadataIsRejectedByTheInputByteLimit() {
        val ball = RebirthFlowBall.create()
        val profileCommand = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
            .profileTargetCommand()
        val oversized = profileCommand.advancedResult(1, 3uL).copy(
            commandSource = profileCommand.commandSource.copy(
                sourceBallInstanceId = "x".repeat(200),
            ),
        )

        val result = assertIs<RebirthDispatchResult.AdmissionRejected>(ball.accept(oversized))

        val exceeded = assertIs<AdmissionFailure.LimitExceeded>(result.reason)
        assertEquals(MandatoryDecisionLimit.INPUT_BYTES, exceeded.limit)
        assertEquals(RebirthFlowBall.LIMITS.maxInputBytes, exceeded.maximum)
        assertEquals(RebirthContinuationStatus.Idle, ball.completionStatus())
    }

    @Test
    fun oversizedTargetSourceLocalNameIsRejectedByTheInputByteLimit() {
        val ball = RebirthFlowBall.create()
        val profileCommand = assertIs<RebirthDispatchResult.Committed>(ball.start(0))
            .profileTargetCommand()
        val oversized = profileCommand.advancedResult(1, 3uL).copy(
            commandSource = profileCommand.commandSource.copy(
                sourceLocalOrdinalOrName = "x".repeat(200),
            ),
        )

        val result = assertIs<RebirthDispatchResult.AdmissionRejected>(ball.accept(oversized))

        val exceeded = assertIs<AdmissionFailure.LimitExceeded>(result.reason)
        assertEquals(MandatoryDecisionLimit.INPUT_BYTES, exceeded.limit)
        assertEquals(RebirthFlowBall.LIMITS.maxInputBytes, exceeded.maximum)
        assertEquals(1uL, ball.status().consistencyStamp.commitRevision)
        assertEquals(RebirthContinuationStatus.Idle, ball.completionStatus())
    }

    @Test
    fun identicalStatePulseAndContextProduceTheSamePureDecision() {
        val nucleus = RebirthFlowNucleus()
        val state = RebirthFlowState()
        val pulse = rebirthStartCommand(expectedLevel = 0)
        val context = RebirthDecisionContext(
            operationId = RebirthOperationId(1uL),
            causalBudgetScope = pulse.causalBudgetScope,
            proposedCommitRevision = 1uL,
            causalDepth = RebirthStartContract.COMMAND_CAUSAL_DEPTH,
        )

        assertEquals(
            nucleus.decide(state, pulse, context),
            nucleus.decide(state, pulse, context),
        )
    }

    @Test
    fun everyMandatoryDecisionLimitIsFiniteAndCommandsAreBoundedToOne() {
        val limits = RebirthFlowBall.LIMITS

        assertTrue(limits.maxInputBytes > 0L)
        assertTrue(limits.maxStateBytes > 0L)
        assertTrue(limits.maxCollectionItems > 0)
        assertEquals(1, limits.maxOutputsPerDecision)
        assertEquals(0, limits.maxEffectsPerDecision)
        assertEquals(1, limits.maxCommandsPerDecision)
        assertEquals(GameRunStartContract.RESULT_CAUSAL_DEPTH, limits.maxCausalDepth)
        assertEquals(1, limits.maxRetriesPerOperation)
        assertEquals(1, limits.maxTransitionSteps)
    }

    @Test
    fun negativeExpectedLevelIsABusinessRejection() {
        val ball = RebirthFlowBall.create()

        val result = assertIs<RebirthDispatchResult.DecisionRejected>(ball.start(-1))

        assertIs<RebirthRejection.InvalidExpectedLevel>(result.reason)
        assertEquals(RebirthStatus.Idle, ball.status().payload)
        assertEquals(0uL, ball.status().consistencyStamp.commitRevision)
    }
}

private fun RebirthFlowBall.start(expectedLevel: Int): RebirthDispatchResult =
    start(rebirthStartCommand(expectedLevel))

private fun rebirthStartCommand(
    expectedLevel: Int,
    sourceCommitRevision: ULong = 1uL,
    sourceOperationId: ULong = 1uL,
): RebirthStartModuleCommand = RebirthStartModuleCommand(
    commandSource = RebirthStartCommandSource(
        sourceBallInstanceId = RebirthStartContract.SOURCE_BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = 1u,
        sourceOperationId = sourceOperationId,
        sourceOutputKind = RebirthStartContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName = "${RebirthStartContract.SOURCE_LOCAL_ORDINAL_PREFIX}0",
    ),
    causalBudgetScope = RebirthCausalScope(
        ownerBallInstanceId = RebirthStartContract.SOURCE_BALL_INSTANCE_ID,
        operationId = sourceOperationId,
    ),
    causalDepth = RebirthStartContract.COMMAND_CAUSAL_DEPTH,
    expectedLevel = expectedLevel,
)

private fun RebirthDispatchResult.Committed.profileTargetCommand():
    ProfileAdvanceRebirthModuleCommand =
    assertIs<RebirthModuleCommand.AdvanceProfileRebirth>(commands.single().request.payload)
        .targetCommand

private fun ProfileAdvanceRebirthModuleCommand.advancedResult(
    newLevel: Int,
    profileRevision: ULong,
): ProfileAdvanceRebirthModuleResult.Advanced = ProfileAdvanceRebirthModuleResult.Advanced(
    commandSource = commandSource,
    causalBudgetScope = causalBudgetScope,
    causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
    provenance = ProfileAdvanceRebirthContract.RESULT_PROVENANCE,
    newLevel = newLevel,
    profileSnapshotReference = profileSnapshotReference(profileRevision),
)

private fun ProfileAdvanceRebirthModuleCommand.rejectedResult(
    reason: ProfileAdvanceRebirthRejectionReason,
): ProfileAdvanceRebirthModuleResult.Rejected = ProfileAdvanceRebirthModuleResult.Rejected(
    commandSource = commandSource,
    causalBudgetScope = causalBudgetScope,
    causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
    provenance = ProfileAdvanceRebirthContract.RESULT_PROVENANCE,
    reason = reason,
)

private fun profileSnapshotReference(
    profileRevision: ULong,
): ProfileSnapshotReference = ProfileSnapshotReference(
    profileBallInstanceId = ProfileProjection.BALL_INSTANCE_ID,
    profileCommitRevision = profileRevision,
    profileStateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION,
)

private fun advanceToAwaitingGame(
    ball: RebirthFlowBall,
    expectedLevel: Int = 0,
    profileRevision: ULong = 4uL,
): RebirthStatus.AwaitingGameStartResult {
    val start = assertIs<RebirthDispatchResult.Committed>(ball.start(expectedLevel))
    val advanced = assertIs<RebirthDispatchResult.Committed>(
        ball.accept(
            start.profileTargetCommand().advancedResult(
                newLevel = expectedLevel + 1,
                profileRevision = profileRevision,
            ),
        ),
    )
    return assertIs(advanced.statusRead.payload)
}

private fun RebirthStatus.AwaitingGameStartResult.startedResult(
    gameCommitRevision: ULong,
): GameRunStartModuleResult.Started = GameRunStartModuleResult.Started(
    commandSource = targetCommand.commandSource,
    causalBudgetScope = targetCommand.causalBudgetScope,
    causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
    provenance = GameRunStartContract.RESULT_PROVENANCE,
    gameCommitRevision = gameCommitRevision,
)
