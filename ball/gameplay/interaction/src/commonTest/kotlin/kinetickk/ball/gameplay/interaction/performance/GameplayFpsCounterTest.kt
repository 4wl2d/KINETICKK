// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayFpsCounterTest {
    private fun GameplayFpsCounter.run(startNanos: Long, frames: Int, intervalNanos: Long): List<Boolean> =
        (0..frames).map { frame(startNanos + it * intervalNanos) }

    @Test
    fun aWindowMeasuresFramesPerSecondAndPublishesOnlyAChangedValue() {
        val counter = GameplayFpsCounter()
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
        // 60 Hz: the first frame opens the window, the 30th closes it at exactly 500 ms.
        val published = counter.run(0L, 30, 16_666_667L)
        assertEquals(listOf(30), published.indices.filter { published[it] })
        assertEquals(60, counter.fps)
        // The same rate again closes a window without a new value.
        val again = (31..60).map { counter.frame(it * 16_666_667L) }
        assertTrue(again.none { it }, "an unchanged value is not published")
        assertEquals(60, counter.fps)
        // A slower loop (30 Hz) publishes its value when its window closes.
        val slow = (1..15).map { counter.frame(60 * 16_666_667L + it * 33_333_334L) }
        assertEquals(listOf(true), slow.takeLast(1))
        assertTrue(slow.dropLast(1).none { it })
        assertEquals(30, counter.fps)
    }

    @Test
    fun aWindowNeverClosesBeforeHalfASecondAndResetStartsOver() {
        val counter = GameplayFpsCounter()
        assertFalse(counter.frame(1_000L))
        assertFalse(counter.frame(1_000L + GameplayFpsCounter.WINDOW_NANOS - 1L))
        assertTrue(counter.frame(1_000L + GameplayFpsCounter.WINDOW_NANOS))
        assertEquals(4, counter.fps)
        counter.reset()
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
        // A clock that went backwards (a new loop) opens a fresh window instead of measuring a negative span.
        assertFalse(counter.frame(10L))
        assertFalse(counter.frame(5L))
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
    }
}
