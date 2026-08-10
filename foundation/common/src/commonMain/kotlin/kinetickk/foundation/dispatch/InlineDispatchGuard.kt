// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

/** Prevents recursive entry while one synchronous semantic dispatch is active. */
class InlineDispatchGuard {
    private var dispatching: Boolean = false

    val isDispatching: Boolean
        get() = dispatching

    fun <T> dispatch(block: () -> T): T {
        check(!dispatching) { "Recursive inline dispatch is forbidden" }
        dispatching = true
        return try {
            block()
        } finally {
            dispatching = false
        }
    }
}
