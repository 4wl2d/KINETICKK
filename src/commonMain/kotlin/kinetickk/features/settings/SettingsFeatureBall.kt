// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.BoundedPreflightPolicy
import kinetickk.application.runtime.BusinessRejection
import kinetickk.application.runtime.DecisionLimits
import kinetickk.application.runtime.InlineAcceptedFrameRuntime
import kinetickk.application.runtime.OutputDispatcher
import kinetickk.application.runtime.PreflightCandidate
import kinetickk.application.runtime.PreflightEstimators
import kinetickk.application.runtime.SubmissionResult
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.protocol.SettingsDecisionContext
import kinetickk.features.settings.nucleus.protocol.SettingsIntent
import kinetickk.features.settings.nucleus.protocol.SettingsOperationId
import kinetickk.features.settings.nucleus.protocol.SettingsOutputKind
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionOutput
import kinetickk.features.settings.nucleus.protocol.SettingsProtocol
import kinetickk.features.settings.nucleus.protocol.SettingsQuery
import kinetickk.features.settings.nucleus.protocol.SettingsQueryResult
import kinetickk.features.settings.nucleus.read.SettingsConsistencyStamp
import kinetickk.features.settings.nucleus.read.SettingsReadContext
import kinetickk.features.settings.nucleus.read.SettingsReadResult
import kinetickk.features.settings.nucleus.transition.SettingsBallState
import kinetickk.features.settings.nucleus.transition.SettingsNucleus
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

sealed interface SettingsDispatchResult {
    data class Committed(
        val sourceCommitRevision: ULong,
        val settingsRead: SettingsReadResult<SettingsValues>,
        val projections: ImmutableList<SettingsProjectionOutput>,
    ) : SettingsDispatchResult

    data class DecisionRejected(val reason: BusinessRejection) : SettingsDispatchResult
    data class AdmissionRejected(val reason: AdmissionFailure) : SettingsDispatchResult
}

/** Singleton local Settings Feature Ball backed by the shared caller-confined Inline runtime. */
class SettingsFeatureBall(
    initialSettings: SettingsValues = SettingsValues(),
) {
    private val runtime = InlineAcceptedFrameRuntime(
        initialState = SettingsBallState(initialSettings.normalized()),
        decider = SettingsNucleus(),
        preflight = BoundedPreflightPolicy(
            limits = LIMITS,
            estimators = PreflightEstimators(
                inputBytes = ::estimateInputBytes,
                stateBytes = { SETTINGS_STATE_BYTES },
                collectionItemCounts = ::collectionItemCounts,
                isEffect = { false },
                isCommand = { false },
                causalDepth = { _, context -> context.causalDepth },
                retries = { _, context -> context.retryCount },
                transitionSteps = { candidate -> candidate.decision.nextState.transitionSteps },
                sourceOrdinal = SettingsProjectionOutput::sourceOrdinal,
                hasMatchingOutputKind = { output ->
                    output.semanticHandle.outputKind == SettingsOutputKind.SETTINGS_CHANGED
                },
                causalBudgetScope = { _, context -> context.causalBudgetScope.value.toString() },
            ),
        ),
        outputDispatcher = OutputDispatcher<SettingsProjectionOutput> { _ -> Unit },
    )
    private var nextRootOperationId = 1uL

    fun dispatch(intent: SettingsIntent): SettingsDispatchResult {
        val operationId = reserveRootOperationId()
            ?: return SettingsDispatchResult.AdmissionRejected(
                AdmissionFailure.OperationIdentityExhausted,
            )
        return dispatch(
            intent = intent,
            context = SettingsDecisionContext(
                operationId = operationId,
                causalBudgetScope = operationId,
            ),
        )
    }

    fun query(query: SettingsQuery): SettingsQueryResult = read(
        query = query,
        context = SettingsReadContext(SettingsProtocol.VERSION),
    )

    internal fun dispatch(
        intent: SettingsIntent,
        context: SettingsDecisionContext,
    ): SettingsDispatchResult = when (val result = runtime.submit(intent, context)) {
        is SubmissionResult.Committed -> SettingsDispatchResult.Committed(
            sourceCommitRevision = result.frame.revision.value,
            settingsRead = settingsRead(),
            projections = result.frame.outputs.toImmutableList(),
        )
        is SubmissionResult.DecisionRejected -> SettingsDispatchResult.DecisionRejected(result.rejection)
        is SubmissionResult.AdmissionRejected -> SettingsDispatchResult.AdmissionRejected(result.failure)
    }

    internal fun read(
        query: SettingsQuery,
        context: SettingsReadContext,
    ): SettingsQueryResult {
        require(context.protocolVersion == SettingsProtocol.VERSION) {
            "Unsupported Settings read protocol version"
        }
        val frame = runtime.snapshot()
        val stamp = SettingsConsistencyStamp(
            ballInstanceId = SettingsProtocol.BALL_INSTANCE_ID,
            commitRevision = frame.revision.value,
            stateSchemaVersion = SettingsProtocol.STATE_SCHEMA_VERSION,
        )
        return when (query) {
            SettingsQuery.GetSettings -> SettingsQueryResult.Settings(
                SettingsReadResult(
                    payload = frame.state.values,
                    consistencyStamp = stamp,
                ),
            )
        }
    }

    private fun settingsRead(): SettingsReadResult<SettingsValues> =
        (query(SettingsQuery.GetSettings) as SettingsQueryResult.Settings).value

    private fun reserveRootOperationId(): SettingsOperationId? {
        val value = nextRootOperationId
        if (value == 0uL) return null
        nextRootOperationId = if (value == ULong.MAX_VALUE) 0uL else value + 1uL
        return SettingsOperationId(value)
    }

    companion object {
        val LIMITS = DecisionLimits(
            maxInputBytes = 256L,
            maxStateBytes = 256L,
            maxCollectionItems = 1,
            maxOutputsPerDecision = 1,
            maxEffectsPerDecision = 0,
            maxCommandsPerDecision = 0,
            maxCausalDepth = 1,
            maxRetriesPerOperation = 0,
            maxTransitionSteps = 1,
        )

        private fun estimateInputBytes(
            intent: SettingsIntent,
            context: SettingsDecisionContext,
        ): Long = when (intent) {
            is SettingsIntent.MasterVolumeAdjusted,
            is SettingsIntent.SimulationSpeedAdjusted,
            is SettingsIntent.TextScaleAdjusted,
            is SettingsIntent.ParticleDensityAdjusted,
            is SettingsIntent.DamageNumberSizeAdjusted,
            is SettingsIntent.DamageNumberFormatAdjusted,
            is SettingsIntent.DamageNumberTierThresholdAdjusted,
            -> 12L
            else -> 8L
        } + context.transitionArtifact.length * 4L

        private fun collectionItemCounts(
            candidate: PreflightCandidate<
                SettingsBallState,
                SettingsIntent,
                SettingsDecisionContext,
                SettingsProjectionOutput,
            >,
        ): Iterable<Int> = listOf(candidate.decision.outputs.size)

        private const val SETTINGS_STATE_BYTES = 64L
    }
}
