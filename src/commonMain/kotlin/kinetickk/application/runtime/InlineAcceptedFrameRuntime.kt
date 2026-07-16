// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.runtime

fun interface OutputDispatcher<Output> {
    fun dispatch(output: Output)
}

/** The boundary result of one serialized submission attempt. */
sealed interface SubmissionResult<out State, out Output> {
    data class Committed<out State, out Output>(
        val frame: AcceptedFrame<State, Output>,
    ) : SubmissionResult<State, Output>

    data class DecisionRejected(
        val rejection: BusinessRejection,
    ) : SubmissionResult<Nothing, Nothing>

    data class AdmissionRejected(
        val failure: AdmissionFailure,
    ) : SubmissionResult<Nothing, Nothing>
}

/**
 * A caller-confined Inline + Transient runtime.
 *
 * One submission runs synchronously to rejection or complete frame publication. The complete
 * [AcceptedFrame] is assigned before any output callback is invoked, and dispatch always reads
 * from that retained frame. Reentrant mutation is returned as a typed admission rejection.
 *
 * This class deliberately owns no clock, random generator, I/O adapter, coroutine, worker, or
 * mailbox. Every decision observation is supplied through [submit]'s explicit context argument.
 * State and output types must be immutable or treated as immutable after acceptance.
 */
class InlineAcceptedFrameRuntime<State, Pulse, Context, Output>(
    initialState: State,
    private val decider: Decider<State, Pulse, Context, Output>,
    private val preflight: BoundedPreflightPolicy<State, Pulse, Context, Output>,
    private val outputDispatcher: OutputDispatcher<Output>,
    initialRevision: CommitRevision = CommitRevision.ZERO,
) {
    private var currentFrame: AcceptedFrame<State, Output> =
        AcceptedFrame(
            revision = initialRevision,
            state = initialState,
            outputs = emptyList(),
        )
    private var submissionInProgress: Boolean = false

    /** Returns the one complete authority snapshot currently published by this runtime. */
    fun snapshot(): AcceptedFrame<State, Output> = currentFrame

    fun submit(
        pulse: Pulse,
        context: Context,
    ): SubmissionResult<State, Output> {
        if (submissionInProgress) {
            return SubmissionResult.AdmissionRejected(AdmissionFailure.ReentrantSubmission)
        }

        submissionInProgress = true
        try {
            preflight.checkIngress(pulse, context)?.let {
                return SubmissionResult.AdmissionRejected(it)
            }

            val sourceFrame = currentFrame
            return when (val result = decider.decide(sourceFrame.state, pulse, context)) {
                is Rejected -> SubmissionResult.DecisionRejected(result.rejection)
                is Accepted -> acceptAndDispatch(sourceFrame, pulse, context, result.decision)
            }
        } finally {
            submissionInProgress = false
        }
    }

    private fun acceptAndDispatch(
        sourceFrame: AcceptedFrame<State, Output>,
        pulse: Pulse,
        context: Context,
        decision: Decision<State, Output>,
    ): SubmissionResult<State, Output> {
        preflight.checkDecision(sourceFrame.state, pulse, context, decision)?.let {
            return SubmissionResult.AdmissionRejected(it)
        }

        val nextRevision = sourceFrame.revision.nextOrNull()
            ?: return SubmissionResult.AdmissionRejected(AdmissionFailure.RevisionExhausted)

        val acceptedFrame = AcceptedFrame(
            revision = nextRevision,
            state = decision.nextState,
            outputs = decision.outputs.toList(),
        )

        // This single reference publication is the acceptance point.
        currentFrame = acceptedFrame

        // Dispatch only from the retained, already-current frame.
        acceptedFrame.outputs.forEach(outputDispatcher::dispatch)
        return SubmissionResult.Committed(acceptedFrame)
    }
}
