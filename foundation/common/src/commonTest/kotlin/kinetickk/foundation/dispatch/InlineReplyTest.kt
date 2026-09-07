// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InlineReplyTest {
    @Test
    fun acceptedResultSurvivesTargetFaultAndSourceCompletesBeforeItEscapes() {
        val source = Source()
        val target = InlineAcceptance(BoundedCompletionDeque<Unit>(1))
        val fault = ExpectedFailure("after target result")
        var targetState = 0

        val thrown = assertFailsWith<ExpectedFailure> {
            source.run({
                source.call { reply ->
                    assertEquals(1, source.state, "source must accept before invoking the target")
                    target.dispatch {
                        target.acceptAndDrain(
                            rootItem = Unit,
                            rootFrame = immutableListOf("complete", "fault"),
                            outputs = { it },
                            acceptFrame = { _, _ -> targetState = 7 },
                            decideCompletion = { error("target has no retained work") },
                            execute = { output, _ ->
                                assertEquals(7, targetState)
                                if (output == "complete") reply.accepted(TargetResult("accepted", 7))
                                else throw fault
                            },
                        )
                    }
                }
            }, { source.events += "remaining output" })
        }

        assertSame(fault, thrown)
        assertEquals(7, targetState)
        assertEquals(8, source.state)
        assertEquals(listOf(Input("accepted", 7)), source.received)
        assertEquals(
            listOf("publish:root:1", "remaining output", "decide:accepted:1:7", "publish:accepted:8"),
            source.events,
        )
        assertTrue(source.acceptance.isEmpty)
        assertFalse(source.acceptance.isDispatching)
    }

    @Test
    fun duplicateCompletionPreservesFirstResultAndDrainsItOnce() {
        val source = Source()

        val thrown = assertFailsWith<IllegalStateException> {
            source.run({
                source.call { reply ->
                    reply.accepted(TargetResult("first", 2))
                    reply.accepted(TargetResult("duplicate", 50))
                }
            })
        }

        assertEquals("Inline invocation already has its one result or refusal", thrown.message)
        assertEquals(listOf(Input("first", 2)), source.received)
        assertEquals(3, source.state)
    }

    @Test
    fun conflictingRefusalCannotReplaceAnAcceptedResult() {
        val source = Source()

        assertFailsWith<IllegalStateException> {
            source.run({
                source.call { reply ->
                    reply.accepted(TargetResult("first", 2))
                    reply.refused(Refusal.BUSY)
                }
            })
        }

        assertEquals(listOf(Input("first", 2)), source.received)
    }

    @Test
    fun aClosedReplyCannotCompleteALaterOrForeignInvocation() {
        val firstSource = Source()
        lateinit var retained: InlineReply<TargetResult, Refusal>
        firstSource.run({
            firstSource.call { reply ->
                retained = reply
                reply.accepted(TargetResult("first", 2))
            }
        })
        assertFailsWith<IllegalStateException> { retained.accepted(TargetResult("late", 30)) }
        assertFailsWith<IllegalStateException> { retained.refused(Refusal.BUSY) }

        val foreignSource = Source()
        foreignSource.run({
            foreignSource.call { current ->
                assertFailsWith<IllegalStateException> {
                    retained.accepted(TargetResult("wrong invocation", 40))
                }
                current.accepted(TargetResult("foreign", 5))
            }
        })
        firstSource.run({
            firstSource.call { current -> current.accepted(TargetResult("next", 3)) }
        })

        assertEquals(listOf(Input("first", 2), Input("next", 3)), firstSource.received)
        assertEquals(listOf(Input("foreign", 5)), foreignSource.received)
    }

    @Test
    fun refusalEntersTheSerializedSourceWithoutAnAcceptedTargetMutation() {
        val source = Source()
        var acceptedInputs = 0
        source.run({
            source.acceptance.call(
                invoke = { reply: InlineReply<TargetResult, Refusal> ->
                    reply.refused(Refusal.BUSY)
                    assertTrue(source.received.isEmpty())
                },
                acceptedInput = { result ->
                    acceptedInputs++
                    Input(result.name, result.context)
                },
                refusedInput = { reason -> Input("refused:${reason.name}", 0) },
            )
        })

        assertEquals(0, acceptedInputs)
        assertEquals(listOf(Input("refused:BUSY", 0)), source.received)
        assertEquals(1, source.state)
    }

    @Test
    fun normalReturnWithoutAnOutcomeIsAFaultAndDoesNotInventARefusal() {
        val source = Source()
        lateinit var retained: InlineReply<TargetResult, Refusal>

        val thrown = assertFailsWith<IllegalStateException> {
            source.run({ source.call { reply -> retained = reply } })
        }

        assertEquals("Inline target returned without a result or refusal", thrown.message)
        assertTrue(source.received.isEmpty())
        assertEquals(1, source.state)
        assertFailsWith<IllegalStateException> { retained.refused(Refusal.BUSY) }
    }

    @Test
    fun faultWithoutAnOutcomeEscapesUnchangedWithoutSynthesizingARefusal() {
        val source = Source()
        val fault = ExpectedFailure("target failed before any result")
        lateinit var retained: InlineReply<TargetResult, Refusal>

        val thrown = assertFailsWith<ExpectedFailure> {
            source.run({
                source.call { reply ->
                    retained = reply
                    throw fault
                }
            })
        }

        assertSame(fault, thrown)
        assertTrue(fault.suppressedExceptions.isEmpty())
        assertTrue(source.received.isEmpty())
        assertFailsWith<IllegalStateException> { retained.accepted(TargetResult("late", 8)) }
    }

    @Test
    fun inputsAreConstructedAfterTheTargetReturnsAndFifoKeepsEachContext() {
        val source = Source()
        var insideTarget = false
        fun invoke(name: String, context: Int) {
            source.acceptance.call(
                invoke = { reply: InlineReply<TargetResult, Refusal> ->
                    insideTarget = true
                    try {
                        assertEquals(1, source.state)
                        val expectedCapacity = if (name == "first") 1 else 0
                        val expectedQueued = if (name == "first") 0 else 1
                        assertEquals(expectedCapacity, source.acceptance.remainingCapacity)
                        assertEquals(expectedQueued, source.completions.size)
                        reply.accepted(TargetResult(name, context))
                        assertEquals(expectedCapacity, source.acceptance.remainingCapacity)
                        assertEquals(expectedQueued, source.completions.size)
                        assertTrue(source.received.isEmpty())
                        source.events += "target:$name:return"
                    } finally {
                        insideTarget = false
                    }
                },
                acceptedInput = { result ->
                    assertFalse(insideTarget)
                    source.events += "adapt:${result.name}"
                    Input(result.name, result.context)
                },
                refusedInput = { reason -> error("Unexpected refusal: $reason") },
            )
        }

        source.run({ invoke("first", 3) }, { invoke("second", 5) })

        assertEquals(listOf(Input("first", 3), Input("second", 5)), source.received)
        assertEquals(9, source.state)
        assertEquals(
            listOf(
                "publish:root:1", "target:first:return", "target:second:return",
                "adapt:first", "decide:first:1:3", "publish:first:4",
                "adapt:second", "decide:second:4:5", "publish:second:9",
            ),
            source.events,
        )
    }

    @Test
    fun anInputAdaptationFaultCannotReplaceAnEarlierTargetFault() {
        val source = Source()
        val first = ExpectedFailure("target")
        val later = ExpectedFailure("input adapter")
        var adapterAvailable = false
        var targetInvocations = 0

        val thrown = assertFailsWith<ExpectedFailure> {
            source.run({
                source.acceptance.call(
                    invoke = { reply: InlineReply<TargetResult, Refusal> ->
                        targetInvocations++
                        reply.accepted(TargetResult("accepted", 2))
                        throw first
                    },
                    acceptedInput = { result ->
                        if (!adapterAvailable) throw later
                        Input(result.name, result.context)
                    },
                    refusedInput = { error("no refusal") },
                )
            })
        }

        assertSame(first, thrown)
        assertEquals(listOf(later), first.suppressedExceptions)
        assertEquals(1, source.completions.size)
        assertTrue(source.received.isEmpty())
        var nextRootStarted = false
        assertFailsWith<IllegalStateException> {
            source.acceptance.dispatch { nextRootStarted = true }
        }
        assertFalse(nextRootStarted)
        assertEquals(1, targetInvocations)
        assertEquals(1, source.state)
        // A test-only read of the retained preparation proves the raw typed payload survived.
        adapterAvailable = true
        assertEquals(Input("accepted", 2), source.completions.peekFirstOrNull())
        assertEquals(1, source.completions.size)
    }

    @Test
    fun inputAdaptationFaultRetainsAcceptedReplyAndBlocksNewRootsWithoutARefusal() {
        val source = Source()
        val fault = ExpectedFailure("input adapter")
        var refusals = 0
        var adapterAvailable = false

        val thrown = assertFailsWith<ExpectedFailure> {
            source.run({
                source.acceptance.call(
                    invoke = { reply: InlineReply<TargetResult, Refusal> ->
                        reply.accepted(TargetResult("retained", 7))
                    },
                    acceptedInput = { result ->
                        if (!adapterAvailable) throw fault
                        Input(result.name, result.context)
                    },
                    refusedInput = {
                        refusals++
                        Input("fabricated refusal", 0)
                    },
                )
            }, { source.events += "remaining accepted output" })
        }

        assertSame(fault, thrown)
        assertEquals(listOf("publish:root:1", "remaining accepted output"), source.events)
        assertEquals(0, refusals)
        assertEquals(1, source.completions.size)
        assertFalse(source.acceptance.isEmpty)
        assertTrue(source.received.isEmpty())
        var newRootExecuted = false
        assertFailsWith<IllegalStateException> { source.run({ newRootExecuted = true }) }
        assertFalse(newRootExecuted)
        assertEquals(1, source.state)
        adapterAvailable = true
        assertEquals(Input("retained", 7), source.completions.peekFirstOrNull())
        assertEquals(1, source.completions.size)
    }

    @Test
    fun callProtectsItsCompletionSlotUntilTheTargetReturns() {
        val source = Source(capacity = 1)
        source.run({
            source.call { reply ->
                assertEquals(0, source.acceptance.remainingCapacity)
                assertTrue(source.completions.isEmpty)
                assertFailsWith<IllegalStateException> {
                    source.acceptance.retainCompletion(Input("steals call slot", 20))
                }
                reply.accepted(TargetResult("reserved", 2))
            }
        })

        assertEquals(listOf(Input("reserved", 2)), source.received)
        assertEquals(1, source.acceptance.remainingCapacity)
    }

    @Test
    fun aFailingHeadRetainsLaterAcceptedRepliesWithoutProcessingThemOutOfOrder() {
        val source = Source()
        val fault = ExpectedFailure("first input adapter")
        var adapterAvailable = false
        var targetCalls = 0
        val thrown = assertFailsWith<ExpectedFailure> {
            source.run({
                source.acceptance.call(
                    invoke = { reply: InlineReply<TargetResult, Refusal> ->
                        targetCalls++
                        reply.accepted(TargetResult("first", 3))
                    },
                    acceptedInput = { result ->
                        if (!adapterAvailable) throw fault
                        Input(result.name, result.context)
                    },
                    refusedInput = { error("unexpected refusal") },
                )
            }, {
                source.call { reply ->
                    targetCalls++
                    reply.accepted(TargetResult("second", 5))
                }
            })
        }

        assertSame(fault, thrown)
        assertEquals(2, targetCalls)
        assertEquals(2, source.completions.size)
        assertTrue(source.received.isEmpty())
        assertEquals(1, source.state)
        adapterAvailable = true
        assertEquals(Input("first", 3), source.completions.peekFirstOrNull())
        assertEquals(2, source.completions.size)
    }

    @Test
    fun acceptedCompletionCanReuseItsOneSlotForTheNextCommand() {
        val completions = BoundedCompletionDeque<Input>(1)
        val acceptance = InlineAcceptance(completions)
        val received = mutableListOf<String>()
        var state = 0
        fun target(name: String, context: Int) {
            assertTrue(completions.isEmpty, "the preceding input was accepted before dispatch")
            acceptance.call(
                invoke = { reply: InlineReply<TargetResult, Refusal> ->
                    reply.accepted(TargetResult(name, context))
                },
                acceptedInput = { result -> Input(result.name, result.context) },
                refusedInput = { error("unexpected refusal") },
            )
        }

        acceptance.dispatch {
            acceptance.acceptAndDrain(
                rootItem = Input("root", 100),
                rootFrame = Frame(1, immutableListOf({ target("first", 3) })),
                outputs = { it.outputs },
                acceptFrame = { item, frame ->
                    if (item.name != "root") assertEquals(1, completions.size)
                    check(acceptance.remainingCapacity >= frame.outputs.size)
                    state = frame.state
                    received += item.name
                },
                decideCompletion = { item ->
                    Frame(
                        state + item.context,
                        if (item.name == "first") immutableListOf({ target("second", 5) })
                        else immutableListOf(),
                    )
                },
                execute = { output, _ -> output() },
            )
        }

        assertEquals(listOf("root", "first", "second"), received)
        assertEquals(9, state)
        assertTrue(completions.isEmpty)
    }

    @Test
    fun callingOutsideAcceptedOutputExecutionNeverInvokesTheTarget() {
        val source = Source()
        var targetInvoked = false
        fun invoke() = source.call { targetInvoked = true }

        assertFailsWith<IllegalStateException> { invoke() }
        source.acceptance.dispatch {
            assertFailsWith<IllegalStateException> { invoke() }
        }

        assertFalse(targetInvoked)
        assertTrue(source.received.isEmpty())
    }

    @Test
    fun missingCompletionCapacityStopsTheTargetWithoutDroppingEarlierWork() {
        val source = Source(capacity = 1)
        var targetInvoked = false

        assertFailsWith<IllegalStateException> {
            source.run({
                source.acceptance.retainCompletion(Input("already retained", 2))
                source.call { targetInvoked = true }
            })
        }

        assertFalse(targetInvoked)
        assertEquals(listOf(Input("already retained", 2)), source.received)
        assertTrue(source.acceptance.isEmpty)
    }

    private class Source(capacity: Int = 2) {
        val completions = BoundedCompletionDeque<Input>(capacity)
        val acceptance = InlineAcceptance(completions)
        val events = mutableListOf<String>()
        val received = mutableListOf<Input>()
        var state = 0

        fun run(vararg outputs: () -> Unit) = acceptance.dispatch {
            acceptance.acceptAndDrain(
                rootItem = Input("root", 100),
                rootFrame = Frame(state + 1, outputs.toList().toImmutableList()),
                outputs = { it.outputs },
                acceptFrame = { item, frame ->
                    state = frame.state
                    events += "publish:${item.name}:$state"
                },
                decideCompletion = { item ->
                    received += item
                    events += "decide:${item.name}:$state:${item.context}"
                    Frame(state + item.context, immutableListOf())
                },
                execute = { output, _ -> output() },
            )
        }

        fun call(invoke: (InlineReply<TargetResult, Refusal>) -> Unit) = acceptance.call(
            invoke = invoke,
            acceptedInput = { result -> Input(result.name, result.context) },
            refusedInput = { refusal -> Input("refused:${refusal.name}", 0) },
        )
    }

    private data class TargetResult(val name: String, val context: Int)
    private enum class Refusal { BUSY }
    private data class Input(val name: String, val context: Int)
    private data class Frame(val state: Int, val outputs: ImmutableList<() -> Unit>)
    private class ExpectedFailure(message: String) : RuntimeException(message)
}
