// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call

/** A small source owner that exercises the same accepted-call binding as a real consumer. */
internal class ProfileCommandTestCaller<Result : Any> {
    val changed = mutableListOf<Result>()
    val refused = mutableListOf<ProfileRefusal>()
    private val acceptance = InlineAcceptance(BoundedCompletionDeque<Input<Result>>(1))

    fun call(invoke: (InlineReply<Result, ProfileRefusal>) -> Unit) = acceptance.dispatch {
        acceptance.acceptAndDrain(
            rootItem = Input.Requested,
            rootFrame = immutableListOf(Unit),
            outputs = { it },
            acceptFrame = { input, _ -> when (input) {
                Input.Requested -> Unit
                is Input.Changed -> changed += input.result
                is Input.Refused -> refused += input.reason
            } },
            decideCompletion = { immutableListOf<Unit>() },
            execute = { _, _ -> acceptance.call(
                invoke = invoke,
                acceptedInput = { Input.Changed(it) },
                refusedInput = { Input.Refused(it) },
            ) },
        )
    }

    private sealed interface Input<out Result> {
        data object Requested : Input<Nothing>
        data class Changed<Result>(val result: Result) : Input<Result>
        data class Refused(val reason: ProfileRefusal) : Input<Nothing>
    }
}
