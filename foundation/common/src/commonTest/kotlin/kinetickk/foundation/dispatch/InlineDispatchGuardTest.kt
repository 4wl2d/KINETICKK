// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InlineDispatchGuardTest {
    @Test
    fun recursiveDispatchIsRejectedAndGuardIsReleased() {
        val guard = InlineDispatchGuard()

        guard.dispatch {
            assertTrue(guard.isDispatching)
            assertFailsWith<IllegalStateException> {
                guard.dispatch { error("must not run") }
            }
        }

        assertFalse(guard.isDispatching)
        assertEquals("accepted", guard.dispatch { "accepted" })
    }

    @Test
    fun throwingDispatchStillReleasesGuard() {
        val guard = InlineDispatchGuard()

        assertFailsWith<ExpectedFailure> {
            guard.dispatch { throw ExpectedFailure() }
        }

        assertFalse(guard.isDispatching)
        guard.dispatch { assertTrue(guard.isDispatching) }
    }
}

private class ExpectedFailure : RuntimeException()
