// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** First acquisitions reach the Profile's recent-item history (items.md §5.2): in order, bounded, never urgent. */
class AcquiredItemReportTest {
    @Test
    fun firstAcquisitionsRideTheNextUpdateInOrderAndOnlyOnce() {
        val state = buildTestState()
        state.acquireItem(5)
        state.acquireItem(2)
        state.acquireItem(5)
        state.acquireItem(9)
        assertEquals(listOf(5, 2, 9), state.pendingAcquiredItemIds)
        // The new discoveries make the update urgent; the acquisitions ride along, then the buffer is empty.
        val update = requireNotNull(state.takeProgressUpdate())
        assertEquals(listOf(5, 2, 9), update.acquiredItemIds.toList())
        assertTrue(state.pendingAcquiredItemIds.isEmpty())
        assertNull(state.takeProgressUpdate())
    }

    @Test
    fun acquisitionsAloneWaitForTheRunEndFlush() {
        // Items the Profile already discovered: taking them again is history, not a discovery.
        val next = buildTestState().apply {
            discoveredItemIds += 4
            discoveredItemIds += 6
        }
        next.acquireItem(4)
        next.acquireItem(6)
        assertFalse(next.hasPublishableProgress(), "Acquisitions alone never force a frame to publish")
        assertNull(next.takeProgressUpdate())
        next.bankRunMatter()
        val flushed = requireNotNull(next.takeProgressUpdate())
        assertEquals(listOf(4, 6), flushed.acquiredItemIds.toList())
        assertTrue(flushed.discoveredItemIds.isEmpty())
    }

    @Test
    fun theBufferKeepsTheNewestSixtyFourAcquisitions() {
        val state = buildTestState(rebirth = 10)
        val taken = state.content.items.take(MAX_RECENT_ITEM_IDS + 6).map { it.id }
        taken.forEach(state::acquireItem)
        assertEquals(taken.takeLast(MAX_RECENT_ITEM_IDS), state.pendingAcquiredItemIds)
        assertEquals(MAX_RECENT_ITEM_IDS, requireNotNull(state.takeProgressUpdate()).acquiredItemIds.size)
    }

    @Test
    fun aSalvagedCardIsNoAcquisition() {
        val state = buildTestState()
        val item = state.content.items.first { it.maxStacks == 1 }
        state.acquireItem(item.id)
        state.takeProgressUpdate()
        state.acquireItem(item.id)
        assertTrue(state.pendingAcquiredItemIds.isEmpty(), "A copy past the stack limit is salvaged, not acquired")
    }
}
