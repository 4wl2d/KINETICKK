// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

/** One typed result or refusal retained only for the duration of its invoking call. */
class InlineReply<Result : Any, Refusal : Any> internal constructor() {
    private var open = true
    private var outcome: InlineReplyOutcome<Result, Refusal>? = null

    /** The target invokes this only for a result from its accepted output batch. */
    fun accepted(result: Result) {
        requireOpenReply()
        outcome = InlineReplyOutcome.Accepted(result)
    }

    /** The target invokes this only when it refused the command before acceptance. */
    fun refused(reason: Refusal) {
        requireOpenReply()
        outcome = InlineReplyOutcome.Refused(reason)
    }

    /** Target preflight verifies that its accepted result still has an available return slot. */
    fun checkAvailable() {
        check(open) { "Inline reply belongs to a closed invocation" }
        check(outcome == null) { "Inline invocation already has its one result or refusal" }
    }

    private fun requireOpenReply() = checkAvailable()

    internal fun close(): InlineReplyOutcome<Result, Refusal>? {
        open = false
        val completed = outcome
        outcome = null
        return completed
    }
}

internal sealed interface InlineReplyOutcome<out Result : Any, out Refusal : Any> {
    data class Accepted<Result : Any>(val value: Result) : InlineReplyOutcome<Result, Nothing>
    data class Refused<Refusal : Any>(val reason: Refusal) : InlineReplyOutcome<Nothing, Refusal>
}

/**
 * Invokes a target from an accepted output and retains its typed outcome after that call ends.
 * The owner admits the complete output batch; this call protects one of its real completion slots.
 * Input preparation runs in the same bounded FIFO and remains retained until source acceptance.
 * Business decisions and their current context remain in the owner's drain.
 * An exception without a target outcome is a fault, never a fabricated pre-acceptance refusal.
 */
fun <Item : Any, Result : Any, Refusal : Any> InlineAcceptance<Item>.call(
    invoke: (InlineReply<Result, Refusal>) -> Unit,
    acceptedInput: (Result) -> Item,
    refusedInput: (Refusal) -> Item,
) {
    reserveCallCompletion()
    val reply = InlineReply<Result, Refusal>()
    var firstFault: Throwable? = null
    try {
        invoke(reply)
    } catch (failure: Throwable) {
        firstFault = failure
    }
    val outcome = reply.close()
    val prepare: (() -> Item)? = when (outcome) {
        is InlineReplyOutcome.Accepted -> { { acceptedInput(outcome.value) } }
        is InlineReplyOutcome.Refused -> { { refusedInput(outcome.reason) } }
        null -> null
    }
    try {
        finishCallCompletion(prepare)
    } catch (failure: Throwable) {
        throw keepFirstInlineFault(firstFault, failure)
    }
    if (outcome == null && firstFault == null) {
        error("Inline target returned without a result or refusal")
    }
    firstFault?.let { throw it }
}
