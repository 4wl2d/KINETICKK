// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.rebirth.nucleus.protocol

import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.ReadResult
import kinetickk.features.game.nucleus.protocol.GameRunStartCausalScope
import kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileSnapshotReference
import kotlin.jvm.JvmInline

object RebirthFlowProtocol {
    const val VERSION = "1.0.0"
    const val STATE_SCHEMA_VERSION = 1
    const val TRANSITION_ARTIFACT = "rebirth-flow-v1"
    const val BALL_INSTANCE_ID = "kinetickk.local/RebirthFlow/local-player"
}

@JvmInline
value class RebirthOperationId(val value: ULong)

enum class RebirthOutputKind {
    PROFILE_ADVANCE_REBIRTH,
    GAME_START_RUN,
}

data class RebirthSemanticHandle(
    val operationId: RebirthOperationId,
    val outputKind: RebirthOutputKind,
    val localOrdinalOrName: String,
)

/** Complete causal identity of one previously committed Rebirth Flow command. */
data class RebirthCommandSource(
    val sourceBallInstanceId: String,
    val sourceCommitRevision: ULong,
    val sourceOrdinal: UInt,
    val semanticHandle: RebirthSemanticHandle,
)

sealed interface RebirthPulse

/** Complete Game source identity for one committed request to begin the Rebirth workflow. */
data class RebirthStartCommandSource(
    val sourceBallInstanceId: String,
    val sourceCommitRevision: ULong,
    val sourceOrdinal: UInt,
    val sourceOperationId: ULong,
    val sourceOutputKind: String,
    val sourceLocalOrdinalOrName: String,
)

/** Stable owner and operation identity of the end-to-end Game -> Rebirth causal budget. */
data class RebirthCausalScope(
    val ownerBallInstanceId: String,
    val operationId: ULong,
)

/** Target-owned public command mapped from Game's exact committed BeginRebirth output. */
data class RebirthStartModuleCommand(
    val commandSource: RebirthStartCommandSource,
    val causalBudgetScope: RebirthCausalScope,
    val causalDepth: Int,
    val expectedLevel: Int,
) : RebirthPulse

object RebirthStartContract {
    const val SOURCE_BALL_INSTANCE_ID = "kinetickk.local/Game/local-player"
    const val SOURCE_OUTPUT_KIND = "BEGIN_REBIRTH"
    const val SOURCE_LOCAL_ORDINAL_PREFIX = "authority-"
    const val COMMAND_CAUSAL_DEPTH = 2
}

data class ProfileAdvanceRebirthResultObserved(
    val result: ProfileAdvanceRebirthModuleResult,
) : RebirthPulse

data class GameRunStartResultObserved(
    val result: GameRunStartModuleResult,
) : RebirthPulse

sealed interface RebirthQuery {
    data object GetStatus : RebirthQuery
}

sealed interface RebirthQueryResult {
    data class Status(val value: ReadResult<RebirthStatus>) : RebirthQueryResult
}

enum class RebirthCommandTarget {
    PROFILE,
    GAME,
}

sealed interface RebirthModuleCommand {
    data class AdvanceProfileRebirth(
        val expectedLevel: Int,
        val targetCommand: ProfileAdvanceRebirthModuleCommand,
    ) : RebirthModuleCommand

    data class StartGameRun(
        val newLevel: Int,
        val profileSnapshotReference: ProfileSnapshotReference,
        val targetCommand: GameRunStartModuleCommand,
    ) : RebirthModuleCommand
}

data class RebirthModuleCommandRequest(
    val semanticHandle: RebirthSemanticHandle,
    val sourceOrdinal: UInt,
    val target: RebirthCommandTarget,
    val payload: RebirthModuleCommand,
)

/** Latest transient orchestration status, including the target-owned Game result. */
sealed interface RebirthStatus {
    data object Idle : RebirthStatus

    data class AwaitingProfileResult(
        val operationId: RebirthOperationId,
        val startCommand: RebirthStartModuleCommand,
        val expectedLevel: Int,
        val profileCommandSource: RebirthCommandSource,
        val targetCommand: ProfileAdvanceRebirthModuleCommand,
    ) : RebirthStatus

    data class AwaitingGameStartResult(
        val operationId: RebirthOperationId,
        val startCommand: RebirthStartModuleCommand,
        val expectedLevel: Int,
        val newLevel: Int,
        val profileSnapshotReference: ProfileSnapshotReference,
        val profileCommandSource: RebirthCommandSource,
        val profileResult: ProfileAdvanceRebirthModuleResult.Advanced,
        val gameCommandSource: RebirthCommandSource,
        val targetCommand: GameRunStartModuleCommand,
    ) : RebirthStatus

    data class Completed(
        val operationId: RebirthOperationId,
        val startCommand: RebirthStartModuleCommand,
        val expectedLevel: Int,
        val newLevel: Int,
        val profileSnapshotReference: ProfileSnapshotReference,
        val profileCommandSource: RebirthCommandSource,
        val profileResult: ProfileAdvanceRebirthModuleResult.Advanced,
        val gameCommandSource: RebirthCommandSource,
        val result: GameRunStartModuleResult.Started,
    ) : RebirthStatus

    data class GameStartRejected(
        val operationId: RebirthOperationId,
        val startCommand: RebirthStartModuleCommand,
        val expectedLevel: Int,
        val newLevel: Int,
        val profileSnapshotReference: ProfileSnapshotReference,
        val profileCommandSource: RebirthCommandSource,
        val profileResult: ProfileAdvanceRebirthModuleResult.Advanced,
        val gameCommandSource: RebirthCommandSource,
        val result: GameRunStartModuleResult.Rejected,
    ) : RebirthStatus

    data class Rejected(
        val operationId: RebirthOperationId,
        val startCommand: RebirthStartModuleCommand,
        val expectedLevel: Int,
        val profileCommandSource: RebirthCommandSource,
        val result: ProfileAdvanceRebirthModuleResult.Rejected,
    ) : RebirthStatus
}

internal data class RebirthDecisionContext(
    val operationId: RebirthOperationId,
    val causalBudgetScope: RebirthCausalScope,
    val proposedCommitRevision: ULong,
    val transitionArtifact: String = RebirthFlowProtocol.TRANSITION_ARTIFACT,
    val causalDepth: Int,
    val retryCount: Int = 0,
)

sealed interface RebirthRejection : BusinessRejection {
    data class InvalidContext(
        val field: String,
        val reason: String,
    ) : RebirthRejection

    data class InvalidExpectedLevel(
        val received: Int,
    ) : RebirthRejection

    data class InvalidStartCommand(
        val field: String,
        val reason: String,
    ) : RebirthRejection

    data class ConflictingStartCommand(
        val source: RebirthStartCommandSource,
    ) : RebirthRejection

    data class StaleStartCommand(
        val received: RebirthStartCommandSource,
        val latest: RebirthStartCommandSource,
    ) : RebirthRejection

    data class Busy(
        val outstanding: RebirthCommandSource,
    ) : RebirthRejection

    data class StaleProfileResult(
        val received: ProfileCommandSource,
        val expected: ProfileCommandSource?,
    ) : RebirthRejection

    data class InvalidProfileResultProvenance(
        val reason: String,
    ) : RebirthRejection

    data class InvalidProfileResultCausalScope(
        val received: ProfileCausalScope,
        val expected: ProfileCausalScope,
    ) : RebirthRejection

    data class InvalidProfileResult(
        val field: String,
        val reason: String,
    ) : RebirthRejection

    data class UnexpectedAdvancedLevel(
        val expected: Long,
        val received: Int,
    ) : RebirthRejection

    data class InvalidProfileSnapshotReference(
        val field: String,
        val reason: String,
    ) : RebirthRejection

    data class ConflictingProfileResult(
        val source: ProfileCommandSource,
    ) : RebirthRejection

    data class StaleGameStartResult(
        val received: GameRunStartCommandSource,
        val expected: GameRunStartCommandSource?,
    ) : RebirthRejection

    data class InvalidGameStartResultProvenance(
        val reason: String,
    ) : RebirthRejection

    data class InvalidGameStartResultCausalScope(
        val received: GameRunStartCausalScope,
        val expected: GameRunStartCausalScope,
    ) : RebirthRejection

    data class InvalidGameStartResult(
        val field: String,
        val reason: String,
    ) : RebirthRejection

    data class ConflictingGameStartResult(
        val source: GameRunStartCommandSource,
    ) : RebirthRejection
}
