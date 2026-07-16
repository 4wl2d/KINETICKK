// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.CommittedStateSnapshot
import kinetickk.application.runtime.ConsistencyStamp
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.ReadContext
import kinetickk.application.runtime.ReadResult
import kinetickk.application.runtime.Rejected
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.settings.nucleus.protocol.SettingsProtocol
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.resources.ProgressPersistResult
import kinetickk.flows.persistence.resources.ProgressResourceFailure
import kinetickk.flows.persistence.resources.ProgressStore
import kotlin.jvm.JvmInline

/** Stable identity of one accepted combined-snapshot persistence request. */
@JvmInline
value class ProgressOperationId(val value: ULong)

data class ProgressPersistenceHandle(
    val operationId: ProgressOperationId,
    val generation: Long,
    val outputKind: ProgressPersistenceOutputKind =
        ProgressPersistenceOutputKind.PERSIST_COMBINED_SNAPSHOT,
    val localOrdinalOrName: String = "combined-snapshot",
)

enum class ProgressPersistenceOutputKind { PERSIST_COMBINED_SNAPSHOT }

data class ProgressCaptureProvenance(
    val profileSnapshot: ConsistencyStamp,
    val settingsSnapshot: ConsistencyStamp,
)

enum class ProgressPersistenceUnknownReason {
    PROVIDER_READ_FAILED,
    ENCODING_FAILED,
    PAYLOAD_LIMIT_EXCEEDED,
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
}

sealed interface ProgressPersistenceStatus {
    data object NeverRequested : ProgressPersistenceStatus
    data class Pending(val handle: ProgressPersistenceHandle) : ProgressPersistenceStatus
    data class Persisted(val handle: ProgressPersistenceHandle) : ProgressPersistenceStatus
    data class OutcomeUnknown(
        val handle: ProgressPersistenceHandle,
        val reason: ProgressPersistenceUnknownReason,
    ) : ProgressPersistenceStatus
}

sealed interface ProgressPersistencePulse

data class PersistCombinedSnapshot(
    val snapshot: PersistedProgress,
    val provenance: ProgressCaptureProvenance,
) : ProgressPersistencePulse

private sealed interface ProgressPersistenceFact : ProgressPersistencePulse {
    val handle: ProgressPersistenceHandle

    data class Persisted(
        override val handle: ProgressPersistenceHandle,
    ) : ProgressPersistenceFact

    data class OutcomeUnknown(
        override val handle: ProgressPersistenceHandle,
        val reason: ProgressPersistenceUnknownReason,
    ) : ProgressPersistenceFact
}

data class ProgressPersistenceContext(
    val operationId: ProgressOperationId,
    val causalBudgetScope: ProgressOperationId = operationId,
    val causalDepth: Int = 1,
    val retryCount: Int = 0,
    val transitionArtifact: String = TRANSITION_ARTIFACT,
)

private const val TRANSITION_ARTIFACT = "progress-persistence-v1"

private data class ProgressPersistenceState(
    val generation: Long = 0L,
    val outstanding: ProgressPersistenceHandle? = null,
    val status: ProgressPersistenceStatus = ProgressPersistenceStatus.NeverRequested,
    val transitionSteps: Int = 0,
)

private data class PersistProgressEffect(
    val handle: ProgressPersistenceHandle,
    val sourceOrdinal: UInt,
    val snapshot: PersistedProgress,
    val provenance: ProgressCaptureProvenance,
)

sealed interface ProgressPersistenceRejection : BusinessRejection {
    data class InvalidInput(val field: String, val reason: String) : ProgressPersistenceRejection
    data class Busy(val outstanding: ProgressPersistenceHandle) : ProgressPersistenceRejection
    data class StaleFact(
        val received: ProgressPersistenceHandle,
        val expected: ProgressPersistenceHandle?,
    ) : ProgressPersistenceRejection
    data class GenerationExhausted(val generation: Long) : ProgressPersistenceRejection
}

private class ProgressPersistenceNucleus : Decider<
    ProgressPersistenceState,
    ProgressPersistencePulse,
    ProgressPersistenceContext,
    PersistProgressEffect
> {
    override fun decide(
        state: ProgressPersistenceState,
        pulse: ProgressPersistencePulse,
        context: ProgressPersistenceContext,
    ): DecisionResult<ProgressPersistenceState, PersistProgressEffect> {
        if (context.transitionArtifact != TRANSITION_ARTIFACT) {
            return Rejected(
                ProgressPersistenceRejection.InvalidInput(
                    field = "transitionArtifact",
                    reason = "unsupported artifact",
                ),
            )
        }
        if (context.operationId.value == 0uL) {
            return Rejected(
                ProgressPersistenceRejection.InvalidInput(
                    field = "operationId",
                    reason = "must be reserved",
                ),
            )
        }
        return when (pulse) {
            is PersistCombinedSnapshot -> decidePersist(state, pulse, context)
            is ProgressPersistenceFact -> decideFact(state, pulse, context)
        }
    }

    private fun decidePersist(
        state: ProgressPersistenceState,
        intent: PersistCombinedSnapshot,
        context: ProgressPersistenceContext,
    ): DecisionResult<ProgressPersistenceState, PersistProgressEffect> {
        state.outstanding?.let { return Rejected(ProgressPersistenceRejection.Busy(it)) }
        if (
            intent.provenance.profileSnapshot.ballInstanceId !=
            ProfileProjection.BALL_INSTANCE_ID ||
            intent.provenance.profileSnapshot.stateSchemaVersion !=
            ProfileProjection.STATE_SCHEMA_VERSION
        ) {
            return Rejected(
                ProgressPersistenceRejection.InvalidInput(
                    field = "provenance.profileSnapshot",
                    reason = "must identify the supported Profile authority/schema",
                ),
            )
        }
        if (
            intent.provenance.settingsSnapshot.ballInstanceId !=
            SettingsProtocol.BALL_INSTANCE_ID ||
            intent.provenance.settingsSnapshot.stateSchemaVersion !=
            SettingsProtocol.STATE_SCHEMA_VERSION
        ) {
            return Rejected(
                ProgressPersistenceRejection.InvalidInput(
                    field = "provenance.settingsSnapshot",
                    reason = "must identify the supported Settings authority/schema",
                ),
            )
        }
        validateSnapshot(intent.snapshot)?.let { return Rejected(it) }
        if (state.generation == Long.MAX_VALUE) {
            return Rejected(ProgressPersistenceRejection.GenerationExhausted(state.generation))
        }
        val generation = state.generation + 1L
        val handle = ProgressPersistenceHandle(context.operationId, generation)
        val effect = PersistProgressEffect(
            handle = handle,
            sourceOrdinal = 0u,
            snapshot = intent.snapshot,
            provenance = intent.provenance,
        )
        return Accepted(
            Decision(
                nextState = state.copy(
                    generation = generation,
                    outstanding = handle,
                    status = ProgressPersistenceStatus.Pending(handle),
                    transitionSteps = 1,
                ),
                outputs = listOf(effect),
            ),
        )
    }

    private fun decideFact(
        state: ProgressPersistenceState,
        fact: ProgressPersistenceFact,
        context: ProgressPersistenceContext,
    ): DecisionResult<ProgressPersistenceState, PersistProgressEffect> {
        if (fact.handle.operationId != context.operationId) {
            return Rejected(
                ProgressPersistenceRejection.InvalidInput(
                    field = "operationId",
                    reason = "must match Fact handle",
                ),
            )
        }
        if (fact.handle != state.outstanding) {
            return Rejected(ProgressPersistenceRejection.StaleFact(fact.handle, state.outstanding))
        }
        val status = when (fact) {
            is ProgressPersistenceFact.Persisted ->
                ProgressPersistenceStatus.Persisted(fact.handle)
            is ProgressPersistenceFact.OutcomeUnknown ->
                ProgressPersistenceStatus.OutcomeUnknown(fact.handle, fact.reason)
        }
        return Accepted(
            Decision(
                nextState = state.copy(
                    outstanding = null,
                    status = status,
                    transitionSteps = 1,
                ),
                outputs = emptyList(),
            ),
        )
    }

    private fun validateSnapshot(
        snapshot: PersistedProgress,
    ): ProgressPersistenceRejection.InvalidInput? {
        fun invalid(field: String, reason: String) =
            ProgressPersistenceRejection.InvalidInput(field, reason)

        if (snapshot.matter < 0L) return invalid("snapshot.matter", "must be non-negative")
        if (snapshot.lifetimeMatter < snapshot.matter) {
            return invalid("snapshot.lifetimeMatter", "must be at least current matter")
        }
        if (!ProgressPersistenceSchema.isSupportedCoreShapeCode(snapshot.coreShapeIndex)) {
            return invalid("snapshot.coreShapeIndex", "unknown stable code")
        }
        if (!ProgressPersistenceSchema.isSupportedWeaponCode(snapshot.selectedWeaponIndex)) {
            return invalid("snapshot.selectedWeaponIndex", "unknown stable code")
        }
        if (
            snapshot.unlockedWeaponIndices.size > ProgressPersistenceSchema.WEAPON_CODE_COUNT ||
            snapshot.unlockedWeaponIndices.any {
                !ProgressPersistenceSchema.isSupportedWeaponCode(it)
            }
        ) {
            return invalid("snapshot.unlockedWeaponIndices", "contains unsupported identities")
        }
        if (ProgressPersistenceSchema.BASELINE_WEAPON_CODE !in snapshot.unlockedWeaponIndices) {
            return invalid("snapshot.unlockedWeaponIndices", "must contain the baseline weapon")
        }
        if (snapshot.selectedWeaponIndex !in snapshot.unlockedWeaponIndices) {
            return invalid("snapshot.selectedWeaponIndex", "must identify an unlocked weapon")
        }
        if (snapshot.metaLevels.size != ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT) {
            return invalid("snapshot.metaLevels", "must contain every supported upgrade rank")
        }
        snapshot.metaLevels.forEachIndexed { index, rank ->
            val maxRank = requireNotNull(ProgressPersistenceSchema.maxMetaUpgradeRank(index))
            if (rank !in 0..maxRank) {
                return invalid("snapshot.metaLevels[$index]", "rank exceeds the supported range")
            }
        }
        if (
            snapshot.discoveredItemIds.size > ProgressPersistenceSchema.ITEM_ID_COUNT ||
            snapshot.discoveredItemIds.any {
                !ProgressPersistenceSchema.isSupportedItemId(it)
            }
        ) {
            return invalid("snapshot.discoveredItemIds", "contains unsupported identities")
        }
        if (
            !ProgressPersistenceSchema.isSupportedParticleDensityCode(
                snapshot.settings.particleDensityCode,
            )
        ) {
            return invalid("snapshot.settings.particleDensityCode", "unknown stable code")
        }
        if (
            !ProgressPersistenceSchema.isSupportedDamageNumberSizeCode(
                snapshot.settings.damageNumberSizeCode,
            )
        ) {
            return invalid("snapshot.settings.damageNumberSizeCode", "unknown stable code")
        }
        if (
            !ProgressPersistenceSchema.isSupportedDamageNumberFormatCode(
                snapshot.settings.damageNumberFormatCode,
            )
        ) {
            return invalid("snapshot.settings.damageNumberFormatCode", "unknown stable code")
        }
        if (snapshot.settings != snapshot.settings.normalized()) {
            return invalid("snapshot.settings", "must be finite and canonical")
        }
        if (
            !ProgressPersistenceSchema.isSupportedDamageNumberTierThreshold(
                snapshot.settings.damageNumberTierThreshold,
            )
        ) {
            return invalid("snapshot.settings.damageNumberTierThreshold", "unsupported option")
        }
        if (snapshot.rebirthLevel !in 0..ProgressPersistenceSchema.MAX_REBIRTH_LEVEL) {
            return invalid("snapshot.rebirthLevel", "unsupported level")
        }
        if (snapshot.highestClearedRebirth !in -1..snapshot.rebirthLevel) {
            return invalid(
                "snapshot.highestClearedRebirth",
                "must not exceed the current Rebirth level",
            )
        }
        return null
    }
}

sealed interface ProgressPersistenceDispatchResult {
    data class Committed(
        val sourceCommitRevision: ULong,
        val statusRead: ReadResult<ProgressPersistenceStatus>,
        val continuationStatus: ProgressPersistenceContinuationStatus,
    ) : ProgressPersistenceDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : ProgressPersistenceDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : ProgressPersistenceDispatchResult
}

sealed interface ProgressPersistenceQuery {
    data object GetStatus : ProgressPersistenceQuery
}

sealed interface ProgressPersistenceQueryResult {
    data class Status(
        val value: ReadResult<ProgressPersistenceStatus>,
    ) : ProgressPersistenceQueryResult
}

sealed interface ProgressPersistenceCompletionFailure {
    data class DecisionRejected(val reason: BusinessRejection) : ProgressPersistenceCompletionFailure
    data class AdmissionRejected(val reason: AdmissionFailure) : ProgressPersistenceCompletionFailure
}

sealed interface ProgressPersistenceContinuationStatus {
    data object Idle : ProgressPersistenceContinuationStatus
    data class Retained(
        val handle: ProgressPersistenceHandle,
        val causalBudgetScope: ProgressOperationId,
        val lastFailure: ProgressPersistenceCompletionFailure?,
    ) : ProgressPersistenceContinuationStatus
}

/**
 * Flow authority for the existing combined profile/settings save record.
 *
 * It owns only persistence coordination and status. The supplied snapshot is captured input from
 * the Profile and Settings authorities; it never becomes command authority for either one.
 */
class ProgressPersistenceFlowBall private constructor(
    private val progressStore: ProgressStore,
) {
    private data class RetainedCompletion(
        val fact: ProgressPersistenceFact,
        val context: ProgressPersistenceContext,
        val lastFailure: ProgressPersistenceCompletionFailure? = null,
    )

    private var pendingCompletion: RetainedCompletion? = null
    private var nextOperationId: ULong = 1uL
    private val runtime = InlineAcceptedFrameRuntime(
        initialState = ProgressPersistenceState(),
        decider = ProgressPersistenceNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = { 512L },
                collectionItemCounts = { candidate ->
                    buildList {
                        val snapshot = when (val pulse = candidate.pulse) {
                            is PersistCombinedSnapshot -> pulse.snapshot
                            is ProgressPersistenceFact -> null
                        }
                        snapshot?.let {
                            add(it.unlockedWeaponIndices.size)
                            add(it.metaLevels.size)
                            add(it.discoveredItemIds.size)
                        }
                        add(candidate.decision.outputs.size)
                        candidate.decision.outputs.forEach { output ->
                            add(output.snapshot.unlockedWeaponIndices.size)
                            add(output.snapshot.metaLevels.size)
                            add(output.snapshot.discoveredItemIds.size)
                        }
                    }
                },
                isEffect = { true },
                isCommand = { false },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                sourceOrdinal = PersistProgressEffect::sourceOrdinal,
                hasMatchingOutputKind = { output ->
                    output.handle.outputKind ==
                        ProgressPersistenceOutputKind.PERSIST_COMBINED_SNAPSHOT
                },
                synchronousCompletionCount = { candidate -> candidate.decision.outputs.size },
                availableSynchronousCompletionSlots = {
                    if (pendingCompletion == null) COMPLETION_CAPACITY else 0
                },
                causalBudgetScope = { _, context -> context.causalBudgetScope.value.toString() },
            ),
        ),
        outputDispatcher = OutputDispatcher(::dispatchAcceptedEffect),
    )

    fun persist(
        snapshot: PersistedProgress,
        provenance: ProgressCaptureProvenance,
    ): ProgressPersistenceDispatchResult {
        resumeRetainedCompletion()
        pendingCompletion?.let { retained ->
            return ProgressPersistenceDispatchResult.AdmissionRejected(
                AdmissionFailure.CausalBudgetExceeded(
                    scope = retained.context.causalBudgetScope.value.toString(),
                    limit = LIMITS.maxCausalDepth,
                ),
            )
        }
        val operationId = reserveOperationId()
            ?: return ProgressPersistenceDispatchResult.AdmissionRejected(
                AdmissionFailure.OperationIdentityExhausted,
            )
        val result = runtime.submit(
            pulse = PersistCombinedSnapshot(snapshot, provenance),
            context = ProgressPersistenceContext(operationId),
        )
        if (result is SubmissionResult.Committed) resumeRetainedCompletion()
        return when (result) {
            is SubmissionResult.Committed -> ProgressPersistenceDispatchResult.Committed(
                sourceCommitRevision = result.frame.revision.value,
                statusRead = status(),
                continuationStatus = completionStatus(),
            )
            is SubmissionResult.DecisionRejected ->
                ProgressPersistenceDispatchResult.DecisionRejected(result.rejection)
            is SubmissionResult.AdmissionRejected ->
                ProgressPersistenceDispatchResult.AdmissionRejected(result.failure)
        }
    }

    fun query(
        query: ProgressPersistenceQuery,
        context: ReadContext = ReadContext(protocolVersion = PROTOCOL_VERSION),
    ): ProgressPersistenceQueryResult = when (query) {
        ProgressPersistenceQuery.GetStatus -> ProgressPersistenceQueryResult.Status(
            readStatus(context),
        )
    }

    fun status(
        context: ReadContext = ReadContext(protocolVersion = PROTOCOL_VERSION),
    ): ReadResult<ProgressPersistenceStatus> =
        (query(ProgressPersistenceQuery.GetStatus, context) as
            ProgressPersistenceQueryResult.Status).value

    private fun readStatus(
        context: ReadContext,
    ): ReadResult<ProgressPersistenceStatus> {
        require(context.protocolVersion == PROTOCOL_VERSION) {
            "Unsupported ProgressPersistence read protocol version"
        }
        val frame = runtime.snapshot()
        val snapshot = CommittedStateSnapshot(
            ballInstanceId = BALL_INSTANCE_ID,
            commitRevision = frame.revision.value,
            stateSchemaVersion = STATE_SCHEMA_VERSION,
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

    fun completionStatus(): ProgressPersistenceContinuationStatus = pendingCompletion?.let { retained ->
        ProgressPersistenceContinuationStatus.Retained(
            handle = retained.fact.handle,
            causalBudgetScope = retained.context.causalBudgetScope,
            lastFailure = retained.lastFailure,
        )
    } ?: ProgressPersistenceContinuationStatus.Idle

    fun resumeRetainedCompletion(): ProgressPersistenceContinuationStatus {
        val retained = pendingCompletion ?: return ProgressPersistenceContinuationStatus.Idle
        when (val result = runtime.submit(retained.fact, retained.context)) {
            is SubmissionResult.Committed -> pendingCompletion = null
            is SubmissionResult.DecisionRejected -> pendingCompletion = retained.copy(
                lastFailure = ProgressPersistenceCompletionFailure.DecisionRejected(result.rejection),
            )
            is SubmissionResult.AdmissionRejected -> pendingCompletion = retained.copy(
                lastFailure = ProgressPersistenceCompletionFailure.AdmissionRejected(result.failure),
            )
        }
        return completionStatus()
    }

    private fun dispatchAcceptedEffect(effect: PersistProgressEffect) {
        val result = runCatching { progressStore.persist(effect.snapshot) }
            .getOrElse {
                ProgressPersistResult.OutcomeUnknown(
                    ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                )
            }
        val fact = when (result) {
            ProgressPersistResult.Persisted -> ProgressPersistenceFact.Persisted(
                handle = effect.handle,
            )
            is ProgressPersistResult.OutcomeUnknown -> ProgressPersistenceFact.OutcomeUnknown(
                handle = effect.handle,
                reason = result.reason.toFlowReason(),
            )
        }
        check(pendingCompletion == null) {
            "Persistence completion slot was not reserved before acceptance"
        }
        pendingCompletion = RetainedCompletion(
            fact = fact,
            context = ProgressPersistenceContext(
                operationId = effect.handle.operationId,
                causalBudgetScope = effect.handle.operationId,
                causalDepth = 2,
            ),
        )
    }

    private fun reserveOperationId(): ProgressOperationId? {
        val value = nextOperationId
        if (value == 0uL) return null
        nextOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return ProgressOperationId(value)
    }

    companion object {
        const val BALL_INSTANCE_ID = "kinetickk.local/ProgressPersistence/local-player"
        const val PROTOCOL_VERSION = "1.0.0"
        const val STATE_SCHEMA_VERSION = 1
        private const val COMPLETION_CAPACITY = 1

        val LIMITS = DecisionLimits(
            maxInputBytes = 65_536L,
            maxStateBytes = 4_096L,
            maxCollectionItems = 2_048,
            maxOutputsPerDecision = 1,
            maxEffectsPerDecision = 1,
            maxCommandsPerDecision = 0,
            maxCausalDepth = 2,
            maxRetriesPerOperation = 0,
            maxTransitionSteps = 1,
        )

        fun create(progressStore: ProgressStore): ProgressPersistenceFlowBall =
            ProgressPersistenceFlowBall(progressStore)

        private fun estimateInputBytes(
            pulse: ProgressPersistencePulse,
            context: ProgressPersistenceContext,
        ): Long = when (pulse) {
            is PersistCombinedSnapshot -> 512L +
                pulse.snapshot.unlockedWeaponIndices.size * 8L +
                pulse.snapshot.metaLevels.size * 4L +
                pulse.snapshot.discoveredItemIds.size * 8L +
                pulse.provenance.profileSnapshot.ballInstanceId.length * 4L +
                pulse.provenance.settingsSnapshot.ballInstanceId.length * 4L
            is ProgressPersistenceFact -> 96L
        } + context.transitionArtifact.length * 4L

        private fun ProgressResourceFailure.toFlowReason(): ProgressPersistenceUnknownReason =
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

    }
}
