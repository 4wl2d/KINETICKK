// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayFpsCounterTest {
    private companion object {
        const val FRAME_60_HZ = 16_666_667L
    }

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
    fun aStallInFrameDeliveryStartsANewWindowInsteadOfPublishingALowValue() {
        val counter = GameplayFpsCounter()
        counter.run(0L, 30, FRAME_60_HZ)
        assertEquals(60, counter.fps)
        // Half a window at 60 Hz, then no frames for 10 s (a hidden tab, the app in the background).
        val windowStart = 30 * FRAME_60_HZ
        (1..15).forEach { counter.frame(windowStart + it * FRAME_60_HZ) }
        val resumed = windowStart + 15 * FRAME_60_HZ + 10_000_000_000L
        val afterStall = counter.run(resumed, 30, FRAME_60_HZ)
        assertTrue(afterStall.none { it }, "the loop measures 60 again after the stall, so nothing new is published")
        assertEquals(60, counter.fps)
        // A slow loop is still a frame rate: a frame 0.9 s after the last one measures 1 FPS.
        assertTrue(counter.frame(resumed + 30 * FRAME_60_HZ + 900_000_000L))
        assertEquals(1, counter.fps)
    }

    @Test
    fun hidingTheCounterDropsItsOpenWindow() {
        val counter = GameplayFpsCounter()
        // Shown for 300 ms at 60 Hz, hidden for 200 ms (its frames are not counted), shown again.
        (0..18).forEach { counter.frame(it * FRAME_60_HZ) }
        assertFalse(counter.hide(), "no value was shown yet")
        val published = counter.run(30 * FRAME_60_HZ, 30, FRAME_60_HZ)
        assertEquals(listOf(30), published.indices.filter { published[it] })
        assertEquals(60, counter.fps)
        assertTrue(counter.hide(), "hiding clears the shown value")
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
        assertFalse(counter.hide())
    }

    @Test
    fun aWindowNeverClosesBeforeHalfASecondAndHidingStartsOver() {
        val counter = GameplayFpsCounter()
        assertFalse(counter.frame(1_000L))
        assertFalse(counter.frame(1_000L + GameplayFpsCounter.WINDOW_NANOS - 1L))
        assertTrue(counter.frame(1_000L + GameplayFpsCounter.WINDOW_NANOS))
        assertEquals(4, counter.fps)
        assertTrue(counter.hide())
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
        // A clock that went backwards (a new loop) opens a fresh window instead of measuring a negative span.
        assertFalse(counter.frame(10L))
        assertFalse(counter.frame(5L))
        assertEquals(GameplayFpsCounter.NO_VALUE, counter.fps)
    }
}
