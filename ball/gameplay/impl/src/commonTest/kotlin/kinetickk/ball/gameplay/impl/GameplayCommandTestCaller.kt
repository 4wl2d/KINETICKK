// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call

internal class GameplayCommandTestCaller<Result : Any> {
    val applied = mutableListOf<Result>()
    val refused = mutableListOf<GameplayRefusal>()
    lateinit var lastReply: InlineReply<Result, GameplayRefusal>
        private set
    private val acceptance = InlineAcceptance(BoundedCompletionDeque<Input<Result>>(1))

    fun call(invoke: (InlineReply<Result, GameplayRefusal>) -> Unit) = acceptance.dispatch {
        acceptance.acceptAndDrain(
            rootItem = Input.Requested,
            rootFrame = immutableListOf(Unit),
            outputs = { it },
            acceptFrame = { input, _ -> when (input) {
                Input.Requested -> Unit
                is Input.Applied -> applied += input.result
                is Input.Refused -> refused += input.reason
            } },
            decideCompletion = { immutableListOf<Unit>() },
            execute = { _, _ -> acceptance.call(
                invoke = { reply ->
                    lastReply = reply
                    invoke(reply)
                },
                acceptedInput = { Input.Applied(it) },
                refusedInput = { Input.Refused(it) },
            ) },
        )
    }

    private sealed interface Input<out Result> {
        data object Requested : Input<Nothing>
        data class Applied<Result>(val result: Result) : Input<Result>
        data class Refused(val reason: GameplayRefusal) : Input<Nothing>
    }
}
