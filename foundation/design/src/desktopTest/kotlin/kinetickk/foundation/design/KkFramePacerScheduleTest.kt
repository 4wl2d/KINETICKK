// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class KkFramePacerScheduleTest {
    @Test
    fun earlyTimerWakeDoesNotAccumulateIntoAFasterFrameRate() = runBlocking {
        for (rate in listOf(240, 60, 120, 144, 480)) {
            val clock = FakeFrames()
            withContext(clock) {
                val pacer = KkFramePacer(rate, { clock.now }, clock::delayMillis)
                val first = pacer.awaitFrame()
                repeat(rate) { pacer.awaitFrame() }
                // Millisecond timer rounding plus 0.5 ms early wake is bounded once, not per frame.
                val elapsed = clock.now - first
                assertTrue(elapsed >= 998_500_000L, "$rate intervals at $rate Hz took $elapsed ns")
                assertTrue(elapsed <= 1_000_000_000L, "unexpected timer oversleep at $rate Hz: $elapsed ns")
            }
        }
    }

    @Test
    fun capMatchingVsyncKeepsEveryRefresh() = runBlocking {
        for (rate in listOf(60, 120, 144, 240, 480)) {
            val refreshNanos = 1_000_000_000L / rate
            val clock = FakeFrames(refreshNanos)
            withContext(clock) {
                val pacer = KkFramePacer(rate, { clock.now }, clock::delayMillis)
                val first = pacer.awaitFrame()
                repeat(rate) { pacer.awaitFrame() }
                assertEquals(rate * refreshNanos, clock.now - first, "missed a refresh at $rate Hz")
                assertEquals(rate + 1, clock.requests)
            }
        }
    }

    @Test
    fun aSlowDisplayDoesNotWaitForTheHigherCap() = runBlocking {
        val clock = FakeFrames(1_000_000_000L / 60)
        withContext(clock) {
            val pacer = KkFramePacer(240, { clock.now }, clock::delayMillis)
            val first = pacer.awaitFrame()
            repeat(60) { pacer.awaitFrame() }
            assertEquals(60 * clock.refreshNanos, clock.now - first)
        }
    }

    @Test
    fun aLongFrameDoesNotCreateCatchUpFrames() = runBlocking {
        val clock = FakeFrames()
        withContext(clock) {
            val pacer = KkFramePacer(240, { clock.now }, clock::delayMillis)
            pacer.awaitFrame()
            pacer.awaitFrame()
            clock.now += 2_000_000_000L
            val resumed = pacer.awaitFrame()
            val next = pacer.awaitFrame()
            assertTrue(next - resumed >= 2_666_666L, "late frames must not build a catch-up backlog")
        }
    }

    @Test
    fun unlimitedFramesNeverUseTheTimer() = runBlocking {
        val clock = FakeFrames()
        withContext(clock) {
            val pacer = KkFramePacer(0, { clock.now }) { fail("Unlimited mode delayed by $it ms") }
            repeat(1_000) { pacer.awaitFrame() }
            assertEquals(1_000, clock.requests)
            assertEquals(0L, clock.now)
        }
    }

    @Test
    fun cancellingATimerWaitDoesNotRequestAnotherFrame() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val clock = FakeFrames()
        val job = launch(clock) {
            val pacer = KkFramePacer(60, { clock.now }) {
                waiting.complete(Unit)
                awaitCancellation()
            }
            pacer.awaitFrame()
            pacer.awaitFrame()
            fail("cancelled timer resumed")
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(1, clock.requests)
    }

    /** Deterministic timer and ready/V-Sync display; advances virtual time without wall-clock sleeps. */
    private class FakeFrames(val refreshNanos: Long = 0L) : MonotonicFrameClock {
        var now = 0L
        var requests = 0

        suspend fun delayMillis(milliseconds: Long) {
            assertTrue(milliseconds > 0L)
            now += milliseconds * 1_000_000L
        }

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            requests++
            if (refreshNanos > 0L) now = (now / refreshNanos + 1L) * refreshNanos
            return onFrame(now)
        }
    }
}
