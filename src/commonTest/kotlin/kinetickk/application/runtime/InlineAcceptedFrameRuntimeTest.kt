// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.runtime

import kinetickk.foundation.collections.ImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class InlineAcceptedFrameRuntimeTest {
    @Test
    fun completeFrameIsCommittedBeforeAnyOutputDispatch() {
        val outputs = listOf(TestOutput.Effect("first"), TestOutput.Projection("second"))
        val observedFrames = mutableListOf<AcceptedFrame<TestState, TestOutput>>()
        lateinit var runtime: InlineAcceptedFrameRuntime<TestState, TestPulse, TestContext, TestOutput>

        runtime = runtime(
            dispatcher = OutputDispatcher {
                val snapshot = runtime.snapshot()
                assertEquals(1uL, snapshot.revision.value)
                assertEquals(1, snapshot.state.value)
                assertEquals(outputs, snapshot.outputs)
                observedFrames += snapshot
            },
        )

        val result = runtime.submit(TestPulse(outputs = outputs), TestContext)
        val committed = assertIs<SubmissionResult.Committed<TestState, TestOutput>>(result)

        assertSame(runtime.snapshot(), committed.frame)
        assertEquals(2, observedFrames.size)
        assertSame(observedFrames.first(), observedFrames.last())
        assertSame(committed.frame, observedFrames.first())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun decisionAndAcceptedFrameOwnAnActuallyImmutableOutputBatch() {
        val firstOutput: TestOutput = TestOutput.Projection("first")
        val source = mutableListOf<TestOutput>(firstOutput)
        val decision = Decision(TestState(value = 1, estimatedBytes = 1), source)

        source += TestOutput.Projection("source-only")
        assertIs<ImmutableList<TestOutput>>(decision.outputs)
        assertEquals(listOf(firstOutput), decision.outputs)

        val runtimeSource = mutableListOf<TestOutput>(firstOutput)
        val runtime = runtime()
        val result = assertIs<SubmissionResult.Committed<TestState, TestOutput>>(
            runtime.submit(TestPulse(outputs = runtimeSource), TestContext),
        )
        runtimeSource.clear()

        assertIs<ImmutableList<TestOutput>>(result.frame.outputs)
        assertEquals(listOf(firstOutput), result.frame.outputs)
        val outputsAsAny: Any = result.frame.outputs
        assertFalse(outputsAsAny is MutableList<*>)
        assertFailsWith<ClassCastException> {
            outputsAsAny as MutableList<TestOutput>
        }
    }

    @Test
    fun businessRejectionLeavesStateRevisionAndOutputsUnchanged() {
        var dispatchCount = 0
        val runtime = runtime(dispatcher = OutputDispatcher { dispatchCount += 1 })
        val before = runtime.snapshot()

        val result = runtime.submit(TestPulse(reject = true), TestContext)

        assertEquals(SubmissionResult.DecisionRejected(TestRejection), result)
        assertSame(before, runtime.snapshot())
        assertEquals(CommitRevision.ZERO, runtime.snapshot().revision)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun everyMandatoryPreflightLimitAcceptsNAndRejectsNPlusOneAtomically() {
        val baseLimits = generousLimits()
        val cases = listOf(
            LimitCase(
                limit = MandatoryDecisionLimit.INPUT_BYTES,
                limits = baseLimits.copy(maxInputBytes = 1),
                atLimit = TestPulse(inputBytes = 1),
                overLimit = TestPulse(inputBytes = 2),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.STATE_BYTES,
                limits = baseLimits.copy(maxStateBytes = 1),
                atLimit = TestPulse(nextStateBytes = 1),
                overLimit = TestPulse(nextStateBytes = 2),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.COLLECTION_ITEMS,
                limits = baseLimits.copy(maxCollectionItems = 1),
                atLimit = TestPulse(collectionCounts = listOf(1)),
                overLimit = TestPulse(collectionCounts = listOf(2)),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.OUTPUTS_PER_DECISION,
                limits = baseLimits.copy(maxOutputsPerDecision = 1),
                atLimit = TestPulse(outputs = listOf(TestOutput.Projection("one"))),
                overLimit = TestPulse(
                    outputs = listOf(TestOutput.Projection("one"), TestOutput.Projection("two")),
                ),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.EFFECTS_PER_DECISION,
                limits = baseLimits.copy(maxEffectsPerDecision = 0),
                atLimit = TestPulse(outputs = emptyList()),
                overLimit = TestPulse(outputs = listOf(TestOutput.Effect("one"))),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.COMMANDS_PER_DECISION,
                limits = baseLimits.copy(maxCommandsPerDecision = 0),
                atLimit = TestPulse(outputs = emptyList()),
                overLimit = TestPulse(outputs = listOf(TestOutput.Command("one"))),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.CAUSAL_DEPTH,
                limits = baseLimits.copy(maxCausalDepth = 1),
                atLimit = TestPulse(causalDepth = 1),
                overLimit = TestPulse(causalDepth = 2),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.RETRIES_PER_OPERATION,
                limits = baseLimits.copy(maxRetriesPerOperation = 0),
                atLimit = TestPulse(retries = 0),
                overLimit = TestPulse(retries = 1),
            ),
            LimitCase(
                limit = MandatoryDecisionLimit.TRANSITION_STEPS,
                limits = baseLimits.copy(maxTransitionSteps = 1),
                atLimit = TestPulse(transitionSteps = 1),
                overLimit = TestPulse(transitionSteps = 2),
            ),
        )

        cases.forEach { case ->
            val acceptedRuntime = runtime(limits = case.limits)
            assertIs<SubmissionResult.Committed<TestState, TestOutput>>(
                acceptedRuntime.submit(case.atLimit, TestContext),
                "${case.limit.canonicalName} must accept N",
            )
            assertEquals(1uL, acceptedRuntime.snapshot().revision.value)

            var dispatchCount = 0
            val rejectedRuntime = runtime(
                limits = case.limits,
                dispatcher = OutputDispatcher { dispatchCount += 1 },
            )
            val before = rejectedRuntime.snapshot()
            val rejected = assertIs<SubmissionResult.AdmissionRejected>(
                rejectedRuntime.submit(case.overLimit, TestContext),
                "${case.limit.canonicalName} must reject N+1",
            )
            val failure = assertIs<AdmissionFailure.LimitExceeded>(rejected.failure)

            assertEquals(case.limit, failure.limit)
            assertSame(before, rejectedRuntime.snapshot())
            assertEquals(0, dispatchCount)
        }
    }

    @Test
    fun projectSpecificFiniteLimitAcceptsNAndRejectsNPlusOneAtomically() {
        val acceptedRuntime = runtime()
        assertIs<SubmissionResult.Committed<TestState, TestOutput>>(
            acceptedRuntime.submit(
                TestPulse(additionalLimit = AdditionalLimitMeasurement("entities", 3, 3)),
                TestContext,
            ),
        )

        val rejectedRuntime = runtime()
        val before = rejectedRuntime.snapshot()
        val result = assertIs<SubmissionResult.AdmissionRejected>(
            rejectedRuntime.submit(
                TestPulse(additionalLimit = AdditionalLimitMeasurement("entities", 4, 3)),
                TestContext,
            ),
        )

        assertEquals(
            AdmissionFailure.AdditionalLimitExceeded("entities", actual = 4, maximum = 3),
            result.failure,
        )
        assertSame(before, rejectedRuntime.snapshot())
    }

    @Test
    fun reentrantSubmissionIsRejectedWithoutNestedMutation() {
        var nestedResult: SubmissionResult<TestState, TestOutput>? = null
        lateinit var runtime: InlineAcceptedFrameRuntime<TestState, TestPulse, TestContext, TestOutput>
        runtime = runtime(
            dispatcher = OutputDispatcher {
                nestedResult = runtime.submit(TestPulse(delta = 100), TestContext)
                assertEquals(1uL, runtime.snapshot().revision.value)
                assertEquals(1, runtime.snapshot().state.value)
            },
        )

        val outer = runtime.submit(
            TestPulse(outputs = listOf(TestOutput.Projection("dispatch"))),
            TestContext,
        )

        assertIs<SubmissionResult.Committed<TestState, TestOutput>>(outer)
        val nested = assertNotNull(nestedResult)
        val rejected = assertIs<SubmissionResult.AdmissionRejected>(nested)
        assertEquals(AdmissionFailure.ReentrantSubmission, rejected.failure)
        assertEquals(1, runtime.snapshot().state.value)
        assertEquals(1uL, runtime.snapshot().revision.value)

        assertIs<SubmissionResult.Committed<TestState, TestOutput>>(
            runtime.submit(TestPulse(), TestContext),
        )
        assertEquals(2, runtime.snapshot().state.value)
        assertEquals(2uL, runtime.snapshot().revision.value)
    }

    @Test
    fun synchronousCompletionRequiresBothDepthAndAPreReservedSlot() {
        val limits = generousLimits().copy(maxCausalDepth = 2)
        val accepted = runtime(limits = limits, availableCompletionSlots = 1)
        assertIs<SubmissionResult.Committed<TestState, TestOutput>>(
            accepted.submit(
                TestPulse(causalDepth = 1, synchronousCompletionCount = 1),
                TestContext,
            ),
        )

        listOf(
            runtime(limits = limits, availableCompletionSlots = 0) to
                TestPulse(causalDepth = 1, synchronousCompletionCount = 1),
            runtime(limits = limits, availableCompletionSlots = 1) to
                TestPulse(causalDepth = 2, synchronousCompletionCount = 1),
        ).forEach { (runtime, pulse) ->
            val before = runtime.snapshot()
            val rejected = assertIs<SubmissionResult.AdmissionRejected>(
                runtime.submit(pulse, TestContext),
            )
            assertEquals(
                AdmissionFailure.CausalBudgetExceeded(scope = "test-scope", limit = 2),
                rejected.failure,
            )
            assertSame(before, runtime.snapshot())
        }
    }

    @Test
    fun semanticOutputOrdinalsAndKindsAreVerifiedBeforeAcceptance() {
        listOf(
            TestPulse(
                outputs = listOf(
                    TestOutput.Projection("first", sourceOrdinal = 0u),
                    TestOutput.Projection("duplicate", sourceOrdinal = 0u),
                ),
            ) to SemanticOutputEnvelopeViolation.SOURCE_ORDINAL_NOT_CONTIGUOUS,
            TestPulse(
                outputs = listOf(
                    TestOutput.Projection(
                        "wrong-kind",
                        sourceOrdinal = 0u,
                        matchingOutputKind = false,
                    ),
                ),
            ) to SemanticOutputEnvelopeViolation.OUTPUT_KIND_MISMATCH,
        ).forEach { (pulse, expectedViolation) ->
            val runtime = runtime()
            val before = runtime.snapshot()
            val rejected = assertIs<SubmissionResult.AdmissionRejected>(
                runtime.submit(pulse, TestContext),
            )
            val failure = assertIs<AdmissionFailure.InvalidSemanticOutputEnvelope>(
                rejected.failure,
            )
            assertEquals(expectedViolation, failure.violation)
            assertSame(before, runtime.snapshot())
        }
    }

    private fun runtime(
        limits: DecisionLimits = generousLimits(),
        dispatcher: OutputDispatcher<TestOutput> = OutputDispatcher {},
        availableCompletionSlots: Int = 0,
    ): InlineAcceptedFrameRuntime<TestState, TestPulse, TestContext, TestOutput> {
        val estimators = PreflightEstimators<TestState, TestPulse, TestContext, TestOutput>(
            inputBytes = { pulse, _ -> pulse.inputBytes },
            stateBytes = TestState::estimatedBytes,
            collectionItemCounts = { candidate -> candidate.pulse.collectionCounts },
            isEffect = { it is TestOutput.Effect },
            isCommand = { it is TestOutput.Command },
            causalDepth = { pulse, _ -> pulse.causalDepth },
            retries = { pulse, _ -> pulse.retries },
            transitionSteps = { candidate -> candidate.pulse.transitionSteps },
            additionalLimits = { candidate -> listOfNotNull(candidate.pulse.additionalLimit) },
            sourceOrdinal = TestOutput::sourceOrdinal,
            hasMatchingOutputKind = TestOutput::matchingOutputKind,
            synchronousCompletionCount = { candidate ->
                candidate.pulse.synchronousCompletionCount
            },
            availableSynchronousCompletionSlots = { availableCompletionSlots },
            causalBudgetScope = { _, _ -> "test-scope" },
        )
        val decider = Decider<TestState, TestPulse, TestContext, TestOutput> { state, pulse, _ ->
            if (pulse.reject) {
                Rejected(TestRejection)
            } else {
                Accepted(
                    Decision(
                        nextState = TestState(
                            value = state.value + pulse.delta,
                            estimatedBytes = pulse.nextStateBytes,
                        ),
                        outputs = pulse.outputs,
                    ),
                )
            }
        }
        return InlineAcceptedFrameRuntime(
            initialState = TestState(value = 0, estimatedBytes = 1),
            decider = decider,
            preflight = BoundedPreflightPolicy(limits, estimators),
            outputDispatcher = dispatcher,
        )
    }

    private fun generousLimits() = DecisionLimits(
        maxInputBytes = 10,
        maxStateBytes = 10,
        maxCollectionItems = 10,
        maxOutputsPerDecision = 10,
        maxEffectsPerDecision = 10,
        maxCommandsPerDecision = 10,
        maxCausalDepth = 10,
        maxRetriesPerOperation = 10,
        maxTransitionSteps = 10,
    )

    private data class LimitCase(
        val limit: MandatoryDecisionLimit,
        val limits: DecisionLimits,
        val atLimit: TestPulse,
        val overLimit: TestPulse,
    )

    private data class TestState(
        val value: Int,
        val estimatedBytes: Long,
    )

    private data class TestPulse(
        val delta: Int = 1,
        val inputBytes: Long = 1,
        val nextStateBytes: Long = 1,
        val collectionCounts: List<Int> = emptyList(),
        val outputs: List<TestOutput> = emptyList(),
        val causalDepth: Int = 1,
        val retries: Int = 0,
        val transitionSteps: Int = 1,
        val synchronousCompletionCount: Int = 0,
        val additionalLimit: AdditionalLimitMeasurement? = null,
        val reject: Boolean = false,
    )

    private data object TestContext

    private data object TestRejection : BusinessRejection

    private sealed interface TestOutput {
        val sourceOrdinal: UInt?
        val matchingOutputKind: Boolean

        data class Effect(
            val name: String,
            override val sourceOrdinal: UInt? = null,
            override val matchingOutputKind: Boolean = true,
        ) : TestOutput

        data class Command(
            val name: String,
            override val sourceOrdinal: UInt? = null,
            override val matchingOutputKind: Boolean = true,
        ) : TestOutput

        data class Projection(
            val name: String,
            override val sourceOrdinal: UInt? = null,
            override val matchingOutputKind: Boolean = true,
        ) : TestOutput
    }
}
