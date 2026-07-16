// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.runtime

/** The nine finite limits required for every Pokeball decision boundary. */
data class DecisionLimits(
    val maxInputBytes: Long,
    val maxStateBytes: Long,
    val maxCollectionItems: Int,
    val maxOutputsPerDecision: Int,
    val maxEffectsPerDecision: Int,
    val maxCommandsPerDecision: Int,
    val maxCausalDepth: Int,
    val maxRetriesPerOperation: Int,
    val maxTransitionSteps: Int,
) {
    init {
        require(maxInputBytes > 0) { "maxInputBytes must be positive" }
        require(maxStateBytes > 0) { "maxStateBytes must be positive" }
        require(maxCollectionItems > 0) { "maxCollectionItems must be positive" }
        require(maxOutputsPerDecision > 0) { "maxOutputsPerDecision must be positive" }
        require(maxEffectsPerDecision >= 0) { "maxEffectsPerDecision must not be negative" }
        require(maxCommandsPerDecision >= 0) { "maxCommandsPerDecision must not be negative" }
        require(maxCausalDepth > 0) { "maxCausalDepth must be positive" }
        require(maxRetriesPerOperation >= 0) { "maxRetriesPerOperation must not be negative" }
        require(maxTransitionSteps > 0) { "maxTransitionSteps must be positive" }
    }
}

/** Canonical identity of a mandatory limit, retained in typed admission evidence. */
enum class MandatoryDecisionLimit(val canonicalName: String) {
    INPUT_BYTES("maxInputBytes"),
    STATE_BYTES("maxStateBytes"),
    COLLECTION_ITEMS("maxCollectionItems"),
    OUTPUTS_PER_DECISION("maxOutputsPerDecision"),
    EFFECTS_PER_DECISION("maxEffectsPerDecision"),
    COMMANDS_PER_DECISION("maxCommandsPerDecision"),
    CAUSAL_DEPTH("maxCausalDepth"),
    RETRIES_PER_OPERATION("maxRetriesPerOperation"),
    TRANSITION_STEPS("maxTransitionSteps"),
}

/** A pure candidate passed to project-supplied size and work meters. */
data class PreflightCandidate<State, Pulse, Context, Output>(
    val currentState: State,
    val pulse: Pulse,
    val context: Context,
    val decision: Decision<State, Output>,
)

/** One finite project-specific cap measured in addition to the nine mandatory limits. */
data class AdditionalLimitMeasurement(
    val name: String,
    val actual: Long,
    val maximum: Long,
)

/**
 * Project-specific pure meters needed to bind generic values to finite limits.
 *
 * [collectionItemCounts] returns the size of every individually bounded collection reachable
 * through the normalized Pulse, next retained State, and complete output batch. An empty result
 * therefore means the bound protocol contains no collections, not that collection checks are
 * silently omitted.
 */
class PreflightEstimators<State, Pulse, Context, Output>(
    val inputBytes: (pulse: Pulse, context: Context) -> Long,
    val stateBytes: (state: State) -> Long,
    val collectionItemCounts: (candidate: PreflightCandidate<State, Pulse, Context, Output>) -> Iterable<Int>,
    val isEffect: (output: Output) -> Boolean,
    val isCommand: (output: Output) -> Boolean,
    val causalDepth: (pulse: Pulse, context: Context) -> Int,
    val retries: (pulse: Pulse, context: Context) -> Int,
    val transitionSteps: (candidate: PreflightCandidate<State, Pulse, Context, Output>) -> Int,
    /** Project-specific finite caps such as domain collection maxima. */
    val additionalLimits: (
        candidate: PreflightCandidate<State, Pulse, Context, Output>,
    ) -> Iterable<AdditionalLimitMeasurement> = { emptyList() },
    /** Returns the canonical ordinal when [Output] is a SemanticOutput envelope. */
    val sourceOrdinal: (output: Output) -> UInt? = { null },
    /** Verifies that an envelope's declared output kind matches its closed payload variant. */
    val hasMatchingOutputKind: (output: Output) -> Boolean = { true },
    /** Number of outputs in this Decision that synchronously produce a causal completion. */
    val synchronousCompletionCount: (
        candidate: PreflightCandidate<State, Pulse, Context, Output>,
    ) -> Int = { 0 },
    /** Currently available slots in the owner's fixed synchronous-completion storage. */
    val availableSynchronousCompletionSlots: () -> Int = { 0 },
    /** Stable identity of the total causal budget retained across completion hops. */
    val causalBudgetScope: (pulse: Pulse, context: Context) -> String = { _, _ -> "local" },
)

/** A non-business reason why a proposed transition cannot be accepted. */
sealed interface AdmissionFailure {
    data class LimitExceeded(
        val limit: MandatoryDecisionLimit,
        val actual: Long,
        val maximum: Long,
        val collectionIndex: Int? = null,
    ) : AdmissionFailure

    data class InvalidSemanticOutputEnvelope(
        val outputIndex: Int,
        val violation: SemanticOutputEnvelopeViolation,
        val expectedSourceOrdinal: UInt,
        val actualSourceOrdinal: UInt?,
    ) : AdmissionFailure

    data class CausalBudgetExceeded(
        val scope: String,
        val limit: Int,
    ) : AdmissionFailure

    /** A bounded delivery slot is occupied, so accepting more source work would drop an output. */
    data class DeliveryBackpressure(
        val scope: String,
        val pending: Int,
        val capacity: Int,
    ) : AdmissionFailure

    data class AdditionalLimitExceeded(
        val name: String,
        val actual: Long,
        val maximum: Long,
    ) : AdmissionFailure

    data object ReentrantSubmission : AdmissionFailure

    data object RevisionExhausted : AdmissionFailure

    data object OperationIdentityExhausted : AdmissionFailure
}

enum class SemanticOutputEnvelopeViolation {
    SOURCE_ORDINAL_NOT_CONTIGUOUS,
    OUTPUT_KIND_MISMATCH,
}

/**
 * Pure two-stage preflight. Ingress work is checked before [Decider.decide]; the complete
 * decision is checked before an [AcceptedFrame] is materialized or published.
 */
class BoundedPreflightPolicy<State, Pulse, Context, Output>(
    private val limits: DecisionLimits,
    private val estimators: PreflightEstimators<State, Pulse, Context, Output>,
) {
    internal fun checkIngress(
        pulse: Pulse,
        context: Context,
    ): AdmissionFailure? {
        val inputBytes = estimators.inputBytes(pulse, context).nonNegative("inputBytes")
        exceeds(
            limit = MandatoryDecisionLimit.INPUT_BYTES,
            actual = inputBytes,
            maximum = limits.maxInputBytes,
        )?.let { return it }

        val causalDepth = estimators.causalDepth(pulse, context)
        require(causalDepth > 0) { "causalDepth must include the root decision and be positive" }
        exceeds(
            limit = MandatoryDecisionLimit.CAUSAL_DEPTH,
            actual = causalDepth.toLong(),
            maximum = limits.maxCausalDepth.toLong(),
        )?.let { return it }

        val retries = estimators.retries(pulse, context).nonNegative("retries")
        return exceeds(
            limit = MandatoryDecisionLimit.RETRIES_PER_OPERATION,
            actual = retries.toLong(),
            maximum = limits.maxRetriesPerOperation.toLong(),
        )
    }

    internal fun checkDecision(
        currentState: State,
        pulse: Pulse,
        context: Context,
        decision: Decision<State, Output>,
    ): AdmissionFailure? {
        val stateBytes = estimators.stateBytes(decision.nextState).nonNegative("stateBytes")
        exceeds(
            limit = MandatoryDecisionLimit.STATE_BYTES,
            actual = stateBytes,
            maximum = limits.maxStateBytes,
        )?.let { return it }

        val candidate = PreflightCandidate(currentState, pulse, context, decision)
        estimators.collectionItemCounts(candidate).forEachIndexed { index, count ->
            val checkedCount = count.nonNegative("collectionItemCounts[$index]")
            exceeds(
                limit = MandatoryDecisionLimit.COLLECTION_ITEMS,
                actual = checkedCount.toLong(),
                maximum = limits.maxCollectionItems.toLong(),
                collectionIndex = index,
            )?.let { return it }
        }

        estimators.additionalLimits(candidate).forEach { measurement ->
            require(measurement.name.isNotBlank()) { "additional limit name must not be blank" }
            val actual = measurement.actual.nonNegative("additionalLimits[${measurement.name}].actual")
            val maximum = measurement.maximum.nonNegative("additionalLimits[${measurement.name}].maximum")
            if (actual > maximum) {
                return AdmissionFailure.AdditionalLimitExceeded(
                    name = measurement.name,
                    actual = actual,
                    maximum = maximum,
                )
            }
        }

        exceeds(
            limit = MandatoryDecisionLimit.OUTPUTS_PER_DECISION,
            actual = decision.outputs.size.toLong(),
            maximum = limits.maxOutputsPerDecision.toLong(),
        )?.let { return it }

        decision.outputs.forEachIndexed { index, output ->
            val expectedOrdinal = index.toUInt()
            val actualOrdinal = estimators.sourceOrdinal(output)
            if (actualOrdinal != null && actualOrdinal != expectedOrdinal) {
                return AdmissionFailure.InvalidSemanticOutputEnvelope(
                    outputIndex = index,
                    violation = SemanticOutputEnvelopeViolation.SOURCE_ORDINAL_NOT_CONTIGUOUS,
                    expectedSourceOrdinal = expectedOrdinal,
                    actualSourceOrdinal = actualOrdinal,
                )
            }
            if (!estimators.hasMatchingOutputKind(output)) {
                return AdmissionFailure.InvalidSemanticOutputEnvelope(
                    outputIndex = index,
                    violation = SemanticOutputEnvelopeViolation.OUTPUT_KIND_MISMATCH,
                    expectedSourceOrdinal = expectedOrdinal,
                    actualSourceOrdinal = actualOrdinal,
                )
            }
        }

        val effectCount = decision.outputs.count(estimators.isEffect)
        exceeds(
            limit = MandatoryDecisionLimit.EFFECTS_PER_DECISION,
            actual = effectCount.toLong(),
            maximum = limits.maxEffectsPerDecision.toLong(),
        )?.let { return it }

        val commandCount = decision.outputs.count(estimators.isCommand)
        exceeds(
            limit = MandatoryDecisionLimit.COMMANDS_PER_DECISION,
            actual = commandCount.toLong(),
            maximum = limits.maxCommandsPerDecision.toLong(),
        )?.let { return it }

        val synchronousCompletionCount = estimators.synchronousCompletionCount(candidate)
            .nonNegative("synchronousCompletionCount")
        if (synchronousCompletionCount > 0) {
            val availableSlots = estimators.availableSynchronousCompletionSlots()
                .nonNegative("availableSynchronousCompletionSlots")
            val causalDepth = estimators.causalDepth(pulse, context)
            require(causalDepth > 0) {
                "causalDepth must include the root decision and be positive"
            }
            if (
                synchronousCompletionCount > availableSlots ||
                causalDepth >= limits.maxCausalDepth
            ) {
                return AdmissionFailure.CausalBudgetExceeded(
                    scope = estimators.causalBudgetScope(pulse, context),
                    limit = limits.maxCausalDepth,
                )
            }
        }

        val transitionSteps = estimators.transitionSteps(candidate).nonNegative("transitionSteps")
        return exceeds(
            limit = MandatoryDecisionLimit.TRANSITION_STEPS,
            actual = transitionSteps.toLong(),
            maximum = limits.maxTransitionSteps.toLong(),
        )
    }

    private fun exceeds(
        limit: MandatoryDecisionLimit,
        actual: Long,
        maximum: Long,
        collectionIndex: Int? = null,
    ): AdmissionFailure.LimitExceeded? =
        if (actual > maximum) {
            AdmissionFailure.LimitExceeded(limit, actual, maximum, collectionIndex)
        } else {
            null
        }

    private fun Long.nonNegative(name: String): Long {
        require(this >= 0) { "$name estimator returned a negative value" }
        return this
    }

    private fun Int.nonNegative(name: String): Int {
        require(this >= 0) { "$name estimator returned a negative value" }
        return this
    }
}
