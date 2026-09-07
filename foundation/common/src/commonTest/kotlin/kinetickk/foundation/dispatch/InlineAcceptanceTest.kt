// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InlineAcceptanceTest {
    @Test
    fun publicationPrecedesOutputsAndFifoCompletionsKeepTheirOwnInputAndCurrentState() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(2))
        var state = 0
        val events = mutableListOf<String>()
        val first = Input("first", 3)
        val second = Input("second", 5)

        acceptance.dispatch {
            acceptance.acceptAndDrain(
                rootItem = Input("root", 100),
                rootFrame = Frame(1, immutableListOf("first", "second")),
                outputs = { frame ->
                    events += "batch:${frame.state}"
                    frame.outputs
                },
                acceptFrame = { _, frame ->
                    state = frame.state
                    events += "publish:$state"
                },
                decideCompletion = { item ->
                    assertSame(if (item.name == "first") first else second, item)
                    events += "decide:${item.name}:$state"
                    Frame(state + item.context, immutableListOf("complete:${item.name}"))
                },
                execute = { output, item ->
                    assertTrue(acceptance.isDispatching)
                    events += "output:$output:$state"
                    if (item.name == "root") {
                        acceptance.retainCompletion(if (output == "first") first else second)
                    }
                },
            )
        }

        assertEquals(9, state)
        assertEquals(
            listOf(
                "batch:1", "publish:1", "output:first:1", "output:second:1",
                "decide:first:1", "batch:4", "publish:4", "output:complete:first:4",
                "decide:second:4", "batch:9", "publish:9", "output:complete:second:9",
            ),
            events,
        )
        assertTrue(acceptance.isEmpty)
        assertFalse(acceptance.isDispatching)
    }

    @Test
    fun firstExecutionFaultEscapesOnlyAfterAllOutputsAndAcceptedCompletions() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(2))
        val firstFault = ExpectedFailure("first output")
        val laterFault = ExpectedFailure("later output")
        var state = 0
        val events = mutableListOf<String>()

        val thrown = assertFailsWith<ExpectedFailure> {
            acceptance.dispatch {
                acceptance.acceptAndDrain(
                    rootItem = Input("root", 0),
                    rootFrame = Frame(1, immutableListOf("first", "second")),
                    outputs = { it.outputs },
                    acceptFrame = { _, frame -> state = frame.state },
                    decideCompletion = { item ->
                        Frame(state + item.context, immutableListOf("complete:${item.name}"))
                    },
                    execute = { output, item ->
                        events += "$output:$state"
                        if (item.name == "root") {
                            acceptance.retainCompletion(Input(output, 1))
                            throw if (output == "first") firstFault else laterFault
                        }
                        if (item.name == "first") throw laterFault
                    },
                )
            }
        }

        assertSame(firstFault, thrown)
        assertEquals(listOf("first:1", "second:1", "complete:first:2", "complete:second:3"), events)
        assertEquals(3, state)
        assertTrue(acceptance.isEmpty)
        assertFalse(acceptance.isDispatching)
        assertEquals("next input", acceptance.dispatch { "next input" })
    }

    @Test
    fun preflightFaultPublishesNoStateOrOutputAndReleasesTheWriter() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(1))
        val fault = ExpectedFailure("preflight")
        var state = 4
        var outputCount = 0
        var batchAvailable = false

        val thrown = assertFailsWith<ExpectedFailure> {
            acceptance.dispatch {
                acceptance.acceptAndDrain(
                    rootItem = Input("root", 0),
                    rootFrame = Frame(5, immutableListOf("must not execute")),
                    outputs = {
                        batchAvailable = true
                        it.outputs
                    },
                    acceptFrame = { _, frame ->
                        assertTrue(batchAvailable)
                        if (frame.state > 4) throw fault
                        state = frame.state
                    },
                    decideCompletion = { error("No completion was accepted") },
                    execute = { _, _ -> outputCount++ },
                )
            }
        }

        assertSame(fault, thrown)
        assertEquals(4, state)
        assertEquals(0, outputCount)
        assertTrue(acceptance.isEmpty)
        assertFalse(acceptance.isDispatching)
    }

    @Test
    fun unavailableOutputBatchCannotPublishTheCandidate() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(1))
        var published = false

        assertFailsWith<ExpectedFailure> {
            acceptance.dispatch {
                acceptance.acceptAndDrain<Frame, String>(
                    rootItem = Input("root", 0),
                    rootFrame = Frame(1, immutableListOf("output")),
                    outputs = { throw ExpectedFailure("batch") },
                    acceptFrame = { _, _ -> published = true },
                    decideCompletion = { error("No completion was accepted") },
                    execute = { _, _ -> error("No output was accepted") },
                )
            }
        }

        assertFalse(published)
        assertFalse(acceptance.isDispatching)
    }

    @Test
    fun recursiveIngressCannotDecideAndCompletionRunsAfterTheOutputReturns() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(1))
        val events = mutableListOf<String>()

        acceptance.dispatch {
            acceptance.acceptAndDrain(
                rootItem = Input("root", 0),
                rootFrame = Frame(1, immutableListOf("deliver")),
                outputs = { it.outputs },
                acceptFrame = { item, _ -> events += "publish:${item.name}" },
                decideCompletion = {
                    events += "decide:completion"
                    Frame(2, immutableListOf())
                },
                execute = { _, _ ->
                    events += "output:begin"
                    assertFailsWith<IllegalStateException> {
                        acceptance.dispatch { events += "recursive decision" }
                    }
                    assertTrue(acceptance.isDispatching)
                    acceptance.retainCompletion(Input("completion", 0))
                    events += "output:end"
                },
            )
        }

        assertEquals(
            listOf("publish:root", "output:begin", "output:end", "decide:completion", "publish:completion"),
            events,
        )
    }

    @Test
    fun exactCompletionCapacityRetainsEveryItemAndRefusesFirstOverflow() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(2))
        val received = mutableListOf<String>()

        acceptance.dispatch {
            acceptance.acceptAndDrain(
                rootItem = Input("root", 0),
                rootFrame = Frame(1, immutableListOf("deliver")),
                outputs = { it.outputs },
                acceptFrame = { _, _ -> },
                decideCompletion = { item ->
                    received += item.name
                    Frame(2, immutableListOf())
                },
                execute = { _, _ ->
                    acceptance.retainCompletion(Input("first", 0))
                    acceptance.retainCompletion(Input("second", 0))
                    assertEquals(0, acceptance.remainingCapacity)
                    assertFailsWith<IllegalStateException> {
                        acceptance.retainCompletion(Input("overflow", 0))
                    }
                },
            )
        }

        assertEquals(listOf("first", "second"), received)
        assertEquals(2, acceptance.remainingCapacity)
    }

    @Test
    fun aSmallOwnerReusesAcceptanceAndReturnsTheRootRevisionAfterDraining() {
        val owner = Counter()

        assertNull(owner.increment(-1))
        assertEquals(0, owner.value)
        assertEquals(0, owner.revision)

        assertEquals(1, owner.increment(3))
        assertEquals(3, owner.value)
        assertEquals(2, owner.revision)
        assertEquals(3, owner.increment(4))
        assertEquals(7, owner.value)
        assertEquals(4, owner.revision)
    }

    @Test
    fun completionOutsideTheCurrentCallIsRejected() {
        val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(1))

        assertFailsWith<IllegalStateException> {
            acceptance.retainCompletion(Input("late", 0))
        }

        assertTrue(acceptance.isEmpty)
    }

    @Test
    fun aCompletionRemainsQueuedUntilItsDecisionAndEntireFrameAreAccepted() {
        listOf("decision", "output batch", "preflight").forEach { failingStage ->
            val completions = BoundedCompletionDeque<Input>(1)
            val acceptance = InlineAcceptance(completions)
            val first = ExpectedFailure("target")
            val later = ExpectedFailure(failingStage)
            val completion = Input("accepted target result", 3)
            var state = 0
            val thrown = assertFailsWith<ExpectedFailure> {
                acceptance.dispatch {
                    acceptance.acceptAndDrain(
                        rootItem = Input("root", 100),
                        rootFrame = Frame(1, immutableListOf("invoke")),
                        outputs = { frame ->
                            if (frame.state != 1 && failingStage == "output batch") throw later
                            frame.outputs
                        },
                        acceptFrame = { item, frame ->
                            if (item === completion && failingStage == "preflight") throw later
                            state = frame.state
                        },
                        decideCompletion = { item ->
                            assertSame(completion, item)
                            if (failingStage == "decision") throw later
                            Frame(state + item.context, immutableListOf())
                        },
                        execute = { _, _ ->
                            acceptance.retainCompletion(completion)
                            throw first
                        },
                    )
                }
            }

            assertSame(first, thrown)
            assertEquals(listOf(later), first.suppressedExceptions)
            assertEquals(1, state)
            assertEquals(1, completions.size)
            assertSame(completion, completions.peekFirstOrNull())
            var rootDecided = false
            assertFailsWith<IllegalStateException> {
                acceptance.dispatch { rootDecided = true }
            }
            assertFalse(rootDecided)
            assertEquals(1, completions.size)
        }
    }

    private class Counter {
        private val acceptance = InlineAcceptance(BoundedCompletionDeque<Int>(1))
        var value: Int = 0
            private set
        var revision: Int = 0
            private set

        fun increment(amount: Int): Int? = acceptance.dispatch {
            if (amount < 0) return@dispatch null
            val root = Frame(revision + 1, immutableListOf("saved"))
            acceptance.acceptAndDrain(
                rootItem = amount,
                rootFrame = root,
                outputs = { it.outputs },
                acceptFrame = { delta, frame ->
                    value += delta
                    revision = frame.state
                },
                decideCompletion = { Frame(revision + 1, immutableListOf()) },
                execute = { _, _ -> acceptance.retainCompletion(0) },
            )
            root.state
        }
    }

    private data class Input(val name: String, val context: Int)
    private data class Frame(val state: Int, val outputs: ImmutableList<String>)
    private class ExpectedFailure(message: String) : RuntimeException(message)
}
