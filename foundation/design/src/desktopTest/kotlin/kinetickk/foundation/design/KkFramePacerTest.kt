// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class KkFramePacerTest {
    /** A display that is always ready: every frame request is answered at once (V-Sync off, unlimited). */
    private object ImmediateFrames : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frames++
            return onFrame(System.nanoTime())
        }
    }

    private fun framesOver(capHz: Int, count: Int): Long = runBlocking {
        withContext(ImmediateFrames) {
            val pacer = KkFramePacer(capHz)
            val start = TimeSource.Monotonic.markNow()
            repeat(count) { pacer.awaitFrame() }
            start.elapsedNow().inWholeMilliseconds
        }
    }

    @Test
    fun aCapSpacesFramesAtLeastItsIntervalApart() {
        ImmediateFrames.frames = 0
        // 100 Hz: 10 ms per frame, woken at most 1.5 ms early; the first frame does not wait.
        val elapsed = framesOver(100, 11)
        assertEquals(11, ImmediateFrames.frames)
        assertTrue(elapsed >= 10 * 8, "11 frames at 100 Hz took $elapsed ms")
    }

    @Test
    fun noCapAwaitsFramesWithoutWaiting() {
        val elapsed = framesOver(0, 200)
        assertTrue(elapsed < 1_000, "200 unpaced frames took $elapsed ms")
    }
}
