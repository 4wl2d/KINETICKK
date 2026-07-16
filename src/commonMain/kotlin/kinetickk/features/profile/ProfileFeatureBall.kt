// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile

import kinetickk.application.runtime.AdditionalLimitMeasurement
import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.profile.nucleus.domain.ItemCatalogFacts
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId
import kinetickk.features.profile.nucleus.domain.WeaponId
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.projection.toProjection
import kinetickk.features.profile.nucleus.projection.toRunConfiguration
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeContract
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileDecisionContext
import kinetickk.features.profile.nucleus.protocol.ProfileIntent
import kinetickk.features.profile.nucleus.protocol.ProfileOperationId
import kinetickk.features.profile.nucleus.protocol.ProfilePulse
import kinetickk.features.profile.nucleus.protocol.ProfileQuery
import kinetickk.features.profile.nucleus.protocol.ProfileQueryResult
import kinetickk.features.profile.nucleus.protocol.ProfileRejection
import kinetickk.features.profile.nucleus.protocol.ProfileSemanticOutput
import kinetickk.features.profile.nucleus.protocol.ProfileSnapshotReference
import kinetickk.features.profile.nucleus.protocol.RunSettlementId
import kinetickk.features.profile.nucleus.protocol.envelopeViolation
import kinetickk.features.profile.nucleus.read.ProfileCommittedStateSnapshot
import kinetickk.features.profile.nucleus.read.ProfileConsistencyStamp
import kinetickk.features.profile.nucleus.read.ProfileReadContext
import kinetickk.features.profile.nucleus.read.ProfileReadResult
import kinetickk.features.profile.nucleus.state.PlayerProfileValues
import kinetickk.features.profile.nucleus.state.ProfileState
import kinetickk.features.profile.nucleus.transition.ProfileNucleus
import kinetickk.features.profile.nucleus.transition.initialProfileState

sealed interface ProfileDispatchResult {
    data class Committed(
        val commitRevision: ULong,
        val profileRead: ProfileReadResult<ProfileProjection>,
    ) : ProfileDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : ProfileDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : ProfileDispatchResult
}

data class ProfileAdvanceRebirthExecution(
    val moduleResult: ProfileAdvanceRebirthModuleResult,
    /** Commit created by this call; null for exact result replays and all rejections. */
    val committed: ProfileDispatchResult.Committed?,
)

data class ProfileApplyRunOutcomeExecution(
    val moduleResult: ProfileApplyRunOutcomeModuleResult,
    /** Commit created by this call; null for exact result replays and all rejections. */
    val committed: ProfileDispatchResult.Committed?,
)

/**
 * Singleton local-player Profile Feature Ball.
 *
 * It owns permanent progression only. Protocol 1.0.0 produces no Effects or Commands. Target-owned
 * module commands enter through [execute], while the static Assembly remains the only router.
 */
class ProfileFeatureBall internal constructor(
    initialState: ProfileState,
) {
    private data class DeliveryReceipt<Command, Result>(
        val command: Command,
        val result: Result,
    )

    private val runtime = InlineAcceptedFrameRuntime(
        initialState = initialState,
        decider = ProfileNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = ::estimateStateBytes,
                collectionItemCounts = { candidate ->
                    buildList {
                        add(candidate.decision.nextState.values.metaRanks.size)
                        add(candidate.decision.nextState.values.unlockedWeapons.size)
                        add(candidate.decision.nextState.values.discoveredItemIds.size)
                        add(candidate.decision.outputs.size)
                        if (candidate.pulse is ProfileIntent.RecordItemDiscoveries) {
                            add(candidate.pulse.itemIds.size)
                        }
                    }
                },
                isEffect = { false },
                isCommand = { false },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                additionalLimits = { candidate ->
                    val values = candidate.decision.nextState.values
                    listOf(
                        AdditionalLimitMeasurement(
                            name = "profile.metaRanks",
                            actual = values.metaRanks.size.toLong(),
                            maximum = MetaUpgradeId.entries.size.toLong(),
                        ),
                        AdditionalLimitMeasurement(
                            name = "profile.unlockedWeapons",
                            actual = values.unlockedWeapons.size.toLong(),
                            maximum = WeaponId.entries.size.toLong(),
                        ),
                        AdditionalLimitMeasurement(
                            name = "profile.discoveredItems",
                            actual = values.discoveredItemIds.size.toLong(),
                            maximum = ItemCatalogFacts.ITEM_COUNT.toLong(),
                        ),
                    )
                },
                causalBudgetScope = { _, context ->
                    "${context.causalBudgetScopeOwnerBallInstanceId}:" +
                        context.causalBudgetScopeOperationId
                },
            ),
        ),
        outputDispatcher = OutputDispatcher<ProfileSemanticOutput> {
            error("Profile protocol 1.0.0 declares no semantic outputs")
        },
    )
    private var nextRootOperationId: ULong = 1uL
    /** One exact, bounded receipt per imported operation family. */
    private var latestAdvanceReceipt:
        DeliveryReceipt<ProfileAdvanceRebirthModuleCommand, ProfileAdvanceRebirthModuleResult>? = null
    private var latestRunOutcomeReceipt:
        DeliveryReceipt<ProfileApplyRunOutcomeModuleCommand, ProfileApplyRunOutcomeModuleResult>? = null

    fun dispatch(intent: ProfileIntent): ProfileDispatchResult {
        val operationId = reserveRootOperationId()
            ?: return ProfileDispatchResult.AdmissionRejected(AdmissionFailure.OperationIdentityExhausted)
        return submit(
            pulse = intent,
            operationId = operationId,
            causalBudgetScope = ProfileCausalScope(
                ownerBallInstanceId = ProfileProjection.BALL_INSTANCE_ID,
                operationId = operationId.value,
            ),
            causalDepth = ROOT_CAUSAL_DEPTH,
        )
    }

    /** Executes Rebirth Flow's target-owned command and returns an exactly correlated result. */
    fun execute(
        command: ProfileAdvanceRebirthModuleCommand,
    ): ProfileAdvanceRebirthExecution {
        val correlatable = command.envelopeViolation() == null
        if (correlatable) {
            latestAdvanceReceipt?.let { retained ->
                resolveRedelivery(
                    received = command,
                    receivedSource = command.commandSource,
                    retained = retained,
                    retainedSource = retained.command.commandSource,
                    replay = { ProfileAdvanceRebirthExecution(it, committed = null) },
                    conflicting = {
                        rejectedAdvanceExecution(
                            command,
                            ProfileAdvanceRebirthRejectionReason.CONFLICTING_REDELIVERY,
                        )
                    },
                    stale = {
                        rejectedAdvanceExecution(
                            command,
                            ProfileAdvanceRebirthRejectionReason.STALE_COMMAND_SOURCE,
                        )
                    },
                )?.let { return it }
            }
        }
        val dispatch = submit(
            pulse = command,
            operationId = ProfileOperationId(command.commandSource.sourceOperationId),
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = command.causalDepth,
        )
        val execution = advanceExecution(command, dispatch)
        // Admission failures are intentionally retryable and therefore never become receipts.
        if (correlatable && dispatch !is ProfileDispatchResult.AdmissionRejected) {
            latestAdvanceReceipt = DeliveryReceipt(command, execution.moduleResult)
        }
        return execution
    }

    /** Executes Game's target-owned settlement command and returns an exactly correlated result. */
    fun execute(
        command: ProfileApplyRunOutcomeModuleCommand,
    ): ProfileApplyRunOutcomeExecution {
        val correlatable = command.envelopeViolation() == null
        if (correlatable) {
            latestRunOutcomeReceipt?.let { retained ->
                resolveRedelivery(
                    received = command,
                    receivedSource = command.commandSource,
                    retained = retained,
                    retainedSource = retained.command.commandSource,
                    replay = { ProfileApplyRunOutcomeExecution(it, committed = null) },
                    conflicting = {
                        rejectedRunOutcomeExecution(
                            command,
                            ProfileApplyRunOutcomeRejectionReason.CONFLICTING_REDELIVERY,
                        )
                    },
                    stale = {
                        rejectedRunOutcomeExecution(
                            command,
                            ProfileApplyRunOutcomeRejectionReason.STALE_COMMAND_SOURCE,
                        )
                    },
                )?.let { return it }
            }
        }
        val dispatch = submit(
            pulse = command,
            operationId = ProfileOperationId(command.commandSource.sourceOperationId),
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = command.causalDepth,
        )
        val execution = runOutcomeExecution(command, dispatch)
        // Assembly may retry an exact admission-rejected settlement after capacity is available.
        if (correlatable && dispatch !is ProfileDispatchResult.AdmissionRejected) {
            latestRunOutcomeReceipt = DeliveryReceipt(command, execution.moduleResult)
        }
        return execution
    }

    private fun submit(
        pulse: ProfilePulse,
        operationId: ProfileOperationId,
        causalBudgetScope: ProfileCausalScope,
        causalDepth: Int,
    ): ProfileDispatchResult = when (
        val result = runtime.submit(
            pulse = pulse,
            context = ProfileDecisionContext(
                operationId = operationId,
                causalBudgetScopeOperationId = causalBudgetScope.operationId,
                causalBudgetScopeOwnerBallInstanceId = causalBudgetScope.ownerBallInstanceId,
                causalDepth = causalDepth,
            ),
        )
    ) {
        is SubmissionResult.Committed -> ProfileDispatchResult.Committed(
            commitRevision = result.frame.revision.value,
            profileRead = profileRead(),
        )
        is SubmissionResult.DecisionRejected ->
            ProfileDispatchResult.DecisionRejected(result.rejection)
        is SubmissionResult.AdmissionRejected ->
            ProfileDispatchResult.AdmissionRejected(result.failure)
    }

    private fun advanceExecution(
        command: ProfileAdvanceRebirthModuleCommand,
        dispatch: ProfileDispatchResult,
    ): ProfileAdvanceRebirthExecution = when (dispatch) {
        is ProfileDispatchResult.Committed -> {
            val profile = dispatch.profileRead
            ProfileAdvanceRebirthExecution(
                moduleResult = ProfileAdvanceRebirthModuleResult.Advanced(
                    commandSource = command.commandSource,
                    causalBudgetScope = command.causalBudgetScope,
                    causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
                    provenance = ProfileAdvanceRebirthContract.RESULT_PROVENANCE,
                    newLevel = profile.payload.rebirthLevel,
                    profileSnapshotReference = profile.snapshotReference(),
                ),
                committed = dispatch,
            )
        }
        is ProfileDispatchResult.DecisionRejected -> rejectedAdvanceExecution(
            command = command,
            reason = dispatch.reason.toAdvanceRebirthReason(),
        )
        is ProfileDispatchResult.AdmissionRejected -> rejectedAdvanceExecution(
            command = command,
            reason = ProfileAdvanceRebirthRejectionReason.TARGET_ADMISSION_REJECTED,
        )
    }

    private fun runOutcomeExecution(
        command: ProfileApplyRunOutcomeModuleCommand,
        dispatch: ProfileDispatchResult,
    ): ProfileApplyRunOutcomeExecution = when (dispatch) {
        is ProfileDispatchResult.Committed -> {
            val profile = dispatch.profileRead
            ProfileApplyRunOutcomeExecution(
                moduleResult = ProfileApplyRunOutcomeModuleResult.Applied(
                    commandSource = command.commandSource,
                    causalBudgetScope = command.causalBudgetScope,
                    causalDepth = ProfileApplyRunOutcomeContract.RESULT_CAUSAL_DEPTH,
                    provenance = ProfileApplyRunOutcomeContract.RESULT_PROVENANCE,
                    settlementId = RunSettlementId(command.commandSource.sourceOperationId),
                    profileSnapshotReference = profile.snapshotReference(),
                    resultingMatter = profile.payload.matter,
                    resultingLifetimeMatter = profile.payload.lifetimeMatter,
                    highestClearedRebirth = profile.payload.highestClearedRebirth,
                ),
                committed = dispatch,
            )
        }
        is ProfileDispatchResult.DecisionRejected -> rejectedRunOutcomeExecution(
            command = command,
            reason = dispatch.reason.toApplyRunOutcomeReason(),
        )
        is ProfileDispatchResult.AdmissionRejected -> rejectedRunOutcomeExecution(
            command = command,
            reason = ProfileApplyRunOutcomeRejectionReason.TARGET_ADMISSION_REJECTED,
        )
    }

    private fun rejectedAdvanceExecution(
        command: ProfileAdvanceRebirthModuleCommand,
        reason: ProfileAdvanceRebirthRejectionReason,
    ): ProfileAdvanceRebirthExecution = ProfileAdvanceRebirthExecution(
        moduleResult = ProfileAdvanceRebirthModuleResult.Rejected(
            commandSource = command.commandSource,
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
            provenance = ProfileAdvanceRebirthContract.RESULT_PROVENANCE,
            reason = reason,
        ),
        committed = null,
    )

    private fun rejectedRunOutcomeExecution(
        command: ProfileApplyRunOutcomeModuleCommand,
        reason: ProfileApplyRunOutcomeRejectionReason,
    ): ProfileApplyRunOutcomeExecution = ProfileApplyRunOutcomeExecution(
        moduleResult = ProfileApplyRunOutcomeModuleResult.Rejected(
            commandSource = command.commandSource,
            causalBudgetScope = command.causalBudgetScope,
            causalDepth = ProfileApplyRunOutcomeContract.RESULT_CAUSAL_DEPTH,
            provenance = ProfileApplyRunOutcomeContract.RESULT_PROVENANCE,
            reason = reason,
        ),
        committed = null,
    )

    private fun <Command, Result, Execution> resolveRedelivery(
        received: Command,
        receivedSource: ProfileCommandSource,
        retained: DeliveryReceipt<Command, Result>,
        retainedSource: ProfileCommandSource,
        replay: (Result) -> Execution,
        conflicting: () -> Execution,
        stale: () -> Execution,
    ): Execution? {
        if (receivedSource == retainedSource) {
            return if (received == retained.command) replay(retained.result) else conflicting()
        }
        if (receivedSource.sameDeliverySlot(retainedSource)) return conflicting()
        if (receivedSource.precedes(retainedSource)) return stale()
        return null
    }

    fun query(query: ProfileQuery): ProfileQueryResult = read(
        query = query,
        context = ProfileReadContext(protocolVersion = ProfileProjection.PROTOCOL_VERSION),
    )

    internal fun read(
        query: ProfileQuery,
        context: ProfileReadContext,
    ): ProfileQueryResult {
        require(context.protocolVersion == ProfileProjection.PROTOCOL_VERSION) {
            "Unsupported Profile read protocol version"
        }
        val frame = runtime.snapshot()
        return readSnapshot(
            snapshot = ProfileCommittedStateSnapshot(
                ballInstanceId = ProfileProjection.BALL_INSTANCE_ID,
                commitRevision = frame.revision.value,
                stateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION,
                state = frame.state,
            ),
            query = query,
        )
    }

    private fun readSnapshot(
        snapshot: ProfileCommittedStateSnapshot<ProfileState>,
        query: ProfileQuery,
    ): ProfileQueryResult {
        val stamp = ProfileConsistencyStamp(
            ballInstanceId = snapshot.ballInstanceId,
            commitRevision = snapshot.commitRevision,
            stateSchemaVersion = snapshot.stateSchemaVersion,
        )
        return when (query) {
            ProfileQuery.GetProfileProjection -> ProfileQueryResult.Projection(
                ProfileReadResult(
                    payload = snapshot.state.values.toProjection(
                        snapshot.state.lastRunSettlement?.settlementId,
                    ),
                    consistencyStamp = stamp,
                ),
            )
            ProfileQuery.GetRunConfiguration -> ProfileQueryResult.Configuration(
                ProfileReadResult(
                    payload = snapshot.state.values.toRunConfiguration(),
                    consistencyStamp = stamp,
                ),
            )
        }
    }

    private fun profileRead(): ProfileReadResult<ProfileProjection> =
        (query(ProfileQuery.GetProfileProjection) as ProfileQueryResult.Projection).value

    private fun reserveRootOperationId(): ProfileOperationId? {
        val value = nextRootOperationId
        if (value == 0uL) return null
        nextRootOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return ProfileOperationId(value)
    }

    companion object {
        val LIMITS = DecisionLimits(
            maxInputBytes = 4_096L,
            maxStateBytes = 65_536L,
            maxCollectionItems = ItemCatalogFacts.ITEM_COUNT,
            maxOutputsPerDecision = 1,
            maxEffectsPerDecision = 0,
            maxCommandsPerDecision = 0,
            maxCausalDepth = ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH,
            maxRetriesPerOperation = 0,
            maxTransitionSteps = 1,
        )

        fun create(
            initialValues: PlayerProfileValues = PlayerProfileValues(),
        ): ProfileFeatureBall = ProfileFeatureBall(initialProfileState(initialValues))

        private fun estimateInputBytes(
            pulse: ProfilePulse,
            context: ProfileDecisionContext,
        ): Long = when (pulse) {
            is ProfileIntent.PurchaseMetaUpgrade -> 16L
            is ProfileIntent.PurchaseOrSelectWeapon -> 16L
            is ProfileIntent.SelectCoreShape -> 16L
            is ProfileIntent.RecordItemDiscoveries -> 16L + pulse.itemIds.size.toLong() * 4L
            is ProfileIntent.ApplyRunOutcome -> 48L
            is ProfileIntent.AdvanceRebirth -> 16L
            is ProfileAdvanceRebirthModuleCommand -> 24L + pulse.estimatedEnvelopeBytes()
            is ProfileApplyRunOutcomeModuleCommand -> 48L + pulse.estimatedEnvelopeBytes()
        } + context.transitionArtifact.length.toLong() * 4L +
            context.causalBudgetScopeOwnerBallInstanceId.length.toLong() * 4L + 16L

        private fun estimateStateBytes(state: ProfileState): Long =
            512L +
                state.values.metaRanks.size * 8L +
                state.values.unlockedWeapons.size * 8L +
                state.values.discoveredItemIds.size * 8L +
                if (state.lastRunSettlement == null) 0L else 48L

        private const val ROOT_CAUSAL_DEPTH = 1
    }
}

private fun ProfileAdvanceRebirthModuleCommand.estimatedEnvelopeBytes(): Long =
    commandSource.estimatedBytes() + causalBudgetScope.estimatedBytes() + 8L

private fun ProfileApplyRunOutcomeModuleCommand.estimatedEnvelopeBytes(): Long =
    commandSource.estimatedBytes() + causalBudgetScope.estimatedBytes() + 8L

private fun ProfileCommandSource.estimatedBytes(): Long =
    48L +
        sourceBallInstanceId.length.toLong() * 4L +
        sourceOutputKind.length.toLong() * 4L +
        sourceLocalOrdinalOrName.length.toLong() * 4L

private fun ProfileCausalScope.estimatedBytes(): Long =
    16L + ownerBallInstanceId.length.toLong() * 4L

private fun ProfileReadResult<ProfileProjection>.snapshotReference(): ProfileSnapshotReference =
    ProfileSnapshotReference(
        profileBallInstanceId = consistencyStamp.ballInstanceId,
        profileCommitRevision = consistencyStamp.commitRevision,
        profileStateSchemaVersion = consistencyStamp.stateSchemaVersion,
    )

private fun ProfileCommandSource.sameDeliverySlot(other: ProfileCommandSource): Boolean =
    sourceBallInstanceId == other.sourceBallInstanceId &&
        sourceCommitRevision == other.sourceCommitRevision &&
        sourceOrdinal == other.sourceOrdinal

private fun ProfileCommandSource.precedes(other: ProfileCommandSource): Boolean =
    sourceCommitRevision < other.sourceCommitRevision ||
        (sourceCommitRevision == other.sourceCommitRevision && sourceOrdinal < other.sourceOrdinal)

private fun BusinessRejection.toAdvanceRebirthReason(): ProfileAdvanceRebirthRejectionReason =
    when (this) {
        is ProfileRejection.InvalidModuleCommandSource ->
            ProfileAdvanceRebirthRejectionReason.INVALID_COMMAND_SOURCE
        is ProfileRejection.InvalidModuleCommandCausalContext ->
            ProfileAdvanceRebirthRejectionReason.INVALID_CAUSAL_CONTEXT
        is ProfileRejection.RebirthLevelMismatch ->
            ProfileAdvanceRebirthRejectionReason.LEVEL_MISMATCH
        is ProfileRejection.RebirthNotCleared ->
            ProfileAdvanceRebirthRejectionReason.CURRENT_LEVEL_NOT_CLEARED
        is ProfileRejection.RebirthMaximumReached ->
            ProfileAdvanceRebirthRejectionReason.MAXIMUM_LEVEL_REACHED
        else -> ProfileAdvanceRebirthRejectionReason.TARGET_DECISION_REJECTED
    }

private fun BusinessRejection.toApplyRunOutcomeReason(): ProfileApplyRunOutcomeRejectionReason =
    when (this) {
        is ProfileRejection.InvalidModuleCommandSource ->
            ProfileApplyRunOutcomeRejectionReason.INVALID_COMMAND_SOURCE
        is ProfileRejection.InvalidModuleCommandCausalContext ->
            ProfileApplyRunOutcomeRejectionReason.INVALID_CAUSAL_CONTEXT
        is ProfileRejection.InvalidRunMatter ->
            ProfileApplyRunOutcomeRejectionReason.INVALID_RUN_MATTER
        is ProfileRejection.InvalidClearedRebirthLevel ->
            ProfileApplyRunOutcomeRejectionReason.INVALID_CLEARED_REBIRTH_LEVEL
        is ProfileRejection.ConflictingRunSettlement ->
            ProfileApplyRunOutcomeRejectionReason.CONFLICTING_RUN_SETTLEMENT
        is ProfileRejection.StaleRunSettlement ->
            ProfileApplyRunOutcomeRejectionReason.STALE_RUN_SETTLEMENT
        else -> ProfileApplyRunOutcomeRejectionReason.TARGET_DECISION_REJECTED
    }
