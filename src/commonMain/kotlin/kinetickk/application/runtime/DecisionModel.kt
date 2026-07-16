// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.runtime

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** A closed protocol-specific reason why a valid Pulse is prohibited by business policy. */
interface BusinessRejection

/**
 * The complete semantic result proposed by a pure [Decider].
 *
 * State and output values must themselves be immutable (or treated as immutable) after this
 * value is returned. The output container is defensively copied so callers cannot mutate the
 * batch after the decision is built.
 */
class Decision<out State, out Output>(
    val nextState: State,
    outputs: List<Output>,
) {
    val outputs: ImmutableList<Output> = outputs.toImmutableList()

    override fun equals(other: Any?): Boolean =
        other is Decision<*, *> &&
            nextState == other.nextState &&
            outputs == other.outputs

    override fun hashCode(): Int = 31 * (nextState?.hashCode() ?: 0) + outputs.hashCode()

    override fun toString(): String = "Decision(nextState=$nextState, outputs=$outputs)"
}

/** The exact two-result shape returned by a Nucleus decision function. */
sealed interface DecisionResult<out State, out Output>

data class Accepted<out State, out Output>(
    val decision: Decision<State, Output>,
) : DecisionResult<State, Output>

data class Rejected(
    val rejection: BusinessRejection,
) : DecisionResult<Nothing, Nothing>

/** A pure, deterministic decision function with every contextual observation supplied explicitly. */
fun interface Decider<State, Pulse, Context, Output> {
    fun decide(
        state: State,
        pulse: Pulse,
        context: Context,
    ): DecisionResult<State, Output>
}

/** Monotonic local acceptance revision; it is deliberately not a clock value. */
data class CommitRevision(val value: ULong) {
    companion object {
        val ZERO: CommitRevision = CommitRevision(0uL)
    }

    internal fun nextOrNull(): CommitRevision? =
        if (value == ULong.MAX_VALUE) null else CommitRevision(value + 1uL)
}

/**
 * One immutable publication unit containing the complete accepted state/output pair.
 *
 * The runtime replaces this object with one reference assignment; it never publishes state and
 * outputs through separate mutable fields.
 */
class AcceptedFrame<out State, out Output> internal constructor(
    val revision: CommitRevision,
    val state: State,
    outputs: List<Output>,
) {
    val outputs: ImmutableList<Output> = outputs.toImmutableList()

    override fun equals(other: Any?): Boolean =
        other is AcceptedFrame<*, *> &&
            revision == other.revision &&
            state == other.state &&
            outputs == other.outputs

    override fun hashCode(): Int {
        var result = revision.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        return 31 * result + outputs.hashCode()
    }

    override fun toString(): String =
        "AcceptedFrame(revision=$revision, state=$state, outputs=$outputs)"
}
