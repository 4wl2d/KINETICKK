// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundedCompletionDequeTest {
    @Test
    fun acceptsExactlyNAndRefusesFirstNPlusOneWithoutTruncating() {
        val deque = BoundedCompletionDeque<Int>(capacity = 3)

        assertTrue(deque.tryAddLast(1))
        assertTrue(deque.tryAddLast(2))
        assertTrue(deque.tryAddLast(3))
        assertEquals(0, deque.remainingCapacity)
        assertFalse(deque.tryAddLast(4))
        assertEquals(3, deque.size)
        assertEquals(1, deque.removeFirstOrNull())
        assertEquals(1, deque.remainingCapacity)
        assertEquals(2, deque.removeFirstOrNull())
        assertEquals(3, deque.removeFirstOrNull())
        assertNull(deque.removeFirstOrNull())
    }

    @Test
    fun freedCapacityCanBeReusedAndOrderRemainsFifo() {
        val deque = BoundedCompletionDeque<String>(capacity = 2)

        assertTrue(deque.tryAddLast("first"))
        assertTrue(deque.tryAddLast("second"))
        assertEquals("first", deque.removeFirstOrNull())
        assertTrue(deque.tryAddLast("third"))
        assertEquals("second", deque.removeFirstOrNull())
        assertEquals("third", deque.removeFirstOrNull())
        assertTrue(deque.isEmpty)
    }
}
