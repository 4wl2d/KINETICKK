// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

import kinetickk.foundation.collections.ImmutableList

/** One owner's same-stack acceptance and completion scheduling. Domain decisions stay in the owner. */
class InlineAcceptance<Item : Any>(
    @PublishedApi internal val completions: BoundedCompletionDeque<Item>,
) {
    @PublishedApi
    internal val guard: InlineDispatchGuard = InlineDispatchGuard()

    @PublishedApi
    internal var executingAcceptedOutput: Boolean = false

    @PublishedApi
    internal var acceptingQueuedCompletion: Boolean = false

    @PublishedApi
    internal var reservedCallCompletions: Int = 0

    val isDispatching: Boolean
        get() = guard.isDispatching

    val isEmpty: Boolean
        get() = completions.isEmpty

    /** Admission may reuse the queued input's slot when its frame is accepted atomically. */
    val remainingCapacity: Int
        get() = completions.remainingCapacity - reservedCallCompletions +
            if (acceptingQueuedCompletion) 1 else 0

    inline fun <Result> dispatch(block: () -> Result): Result = guard.dispatch {
        check(completions.isEmpty) { "Inline owner has accepted completions pending; a new root cannot start" }
        check(reservedCallCompletions == 0) { "Inline call reservation leaked across dispatches" }
        val result = block()
        check(completions.isEmpty) { "Inline dispatch returned without draining its accepted completions" }
        check(reservedCallCompletions == 0) { "Inline dispatch returned with an active call reservation" }
        result
    }

    fun retainCompletion(item: Item) {
        check(isDispatching && executingAcceptedOutput) { "Inline completion arrived outside accepted output execution" }
        check(remainingCapacity > 0) { "Pre-reserved inline completion could not be retained" }
        check(completions.tryAddLast(item)) { "Pre-reserved inline completion could not be retained" }
    }

    internal fun reserveCallCompletion() {
        check(isDispatching && executingAcceptedOutput) {
            "Inline target invocation requires an accepted source output"
        }
        check(remainingCapacity > 0) { "Inline target invocation has no reserved completion capacity" }
        reservedCallCompletions++
    }

    internal fun finishCallCompletion(prepare: (() -> Item)?) {
        check(reservedCallCompletions > 0) { "Inline call has no completion reservation" }
        reservedCallCompletions--
        if (prepare != null) {
            check(completions.tryAddLastPreparation(prepare)) {
                "Reserved inline call completion could not be retained"
            }
        }
    }

    /**
     * Obtain the complete output batch before the owner's preflight and atomic publication.
     * Run every accepted output, then FIFO completions, before throwing the first execution fault.
     * A completion preparation/decision/preflight fault leaves its input queued and blocks new roots.
     * Inlining keeps ordinary Gameplay inputs free of capturing callback and iterator allocations.
     */
    inline fun <Frame, Output> acceptAndDrain(
        rootItem: Item,
        rootFrame: Frame,
        outputs: (Frame) -> ImmutableList<Output>,
        acceptFrame: (Item, Frame) -> Unit,
        decideCompletion: (Item) -> Frame,
        execute: (Output, Item) -> Unit,
    ) {
        check(isDispatching) { "Inline acceptance requires a serialized dispatch" }
        var item = rootItem
        var frame = rootFrame
        var queuedCompletion = false
        var deferredFault: Throwable? = null
        while (true) {
            val batch = try {
                val availableOutputs = outputs(frame)
                acceptingQueuedCompletion = queuedCompletion
                acceptFrame(item, frame)
                // Acceptance consumes this input's slot atomically with the complete next batch.
                if (queuedCompletion) completions.removePreparedFirst()
                availableOutputs
            } catch (failure: Throwable) {
                throw keepFirstInlineFault(deferredFault, failure)
            } finally {
                acceptingQueuedCompletion = false
            }
            var outputIndex = 0
            while (outputIndex < batch.size) {
                try {
                    executingAcceptedOutput = true
                    execute(batch[outputIndex], item)
                } catch (failure: Throwable) {
                    deferredFault = keepFirstInlineFault(deferredFault, failure)
                } finally {
                    executingAcceptedOutput = false
                }
                outputIndex++
            }
            if (completions.isEmpty) break
            try {
                item = checkNotNull(completions.peekFirstOrNull())
                frame = decideCompletion(item)
                queuedCompletion = true
            } catch (failure: Throwable) {
                // Keep the raw typed preparation until its input and next frame are accepted.
                throw keepFirstInlineFault(deferredFault, failure)
            }
        }
        deferredFault?.let { throw it }
    }
}

@PublishedApi
internal fun keepFirstInlineFault(first: Throwable?, later: Throwable): Throwable {
    if (first == null) return later
    if (first !== later) first.addSuppressed(later)
    return first
}
