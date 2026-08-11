// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopAudioExecutionPolicyTest {
    @Test
    fun workerQueueAndBestEffortLossPolicyAreFiniteAndExplicit() {
        assertEquals(1, DesktopAudioExecutionPolicy.WORKER_COUNT)
        assertEquals(24, DesktopAudioExecutionPolicy.QUEUE_CAPACITY)
    }

    @Test
    fun workerAndDiscardOldestQueueEnforceOneAndTwentyFour() {
        val executor = createDesktopAudioExecutor()
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val executed = Collections.synchronizedList(mutableListOf<Int>())

        try {
            executor.execute {
                blockerStarted.countDown()
                releaseBlocker.await(5, TimeUnit.SECONDS)
                executed += 0
            }
            assertTrue(blockerStarted.await(5, TimeUnit.SECONDS))
            assertEquals(1, executor.poolSize)

            (1..DesktopAudioExecutionPolicy.QUEUE_CAPACITY).forEach { value ->
                executor.execute { executed += value }
            }
            assertEquals(24, executor.queue.size)

            executor.execute { executed += 25 }
            assertEquals(24, executor.queue.size)

            releaseBlocker.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(listOf(0) + (2..25), executed.toList())
        } finally {
            releaseBlocker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun synthesisBufferAcceptsMaximumDurationAndRejectsNext() {
        val exact = desktopToneBufferShape(1f)

        assertEquals(DesktopAudioExecutionPolicy.MAX_SAMPLE_COUNT, exact.sampleCount)
        assertEquals(DesktopAudioExecutionPolicy.MAX_PCM_BYTES, exact.byteCount)
        assertFailsWith<IllegalArgumentException> {
            desktopToneBufferShape(Float.fromBits(1f.toBits() + 1))
        }
    }
}
