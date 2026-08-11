// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.Collections
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformCapabilitiesDesktopTest {
    @Test
    fun audioBrokerIsInstanceOwnedAndAcceptsOnlyTypedToneRequests() {
        val first = createPlatformTonePlaybackCapability()
        val second = createPlatformTonePlaybackCapability()
        assertNotSame(first, second)

        first.play(ToneRequest(440f, 0.001f, 0f, ToneWave.SINE))
        first.close()
        second.close()
    }

    @Test
    fun testPersistenceCapabilityUsesOnlyExactKeysAndPreservesUnrelatedData() {
        val testRoot = Preferences.userRoot().node(
            "kinetickk-test/app-platform-capability/${UUID.randomUUID()}",
        )
        val testParent = testRoot.parent()
        val profileNode = testRoot.node("profile")
        val legacyNode = testRoot.node("legacy")
        try {
            val capability = TestDesktopProfilePersistenceCapability(profileNode, legacyNode)
            legacyNode.put(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, "legacy")
            legacyNode.put(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, "1")
            legacyNode.put("unrelated", "preserve-me")
            legacyNode.flush()

            capability.writeV4("strict-v4-payload")
            assertEquals("strict-v4-payload", capability.readV4())
            assertNull(legacyNode.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null))
            assertEquals("legacy", capability.readLegacyProgressV2())
            assertEquals("1", capability.readLegacyMatter())

            capability.removeLegacyProgressV2()
            capability.removeLegacyMatter()
            assertNull(legacyNode.get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null))
            assertNull(legacyNode.get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null))
            assertEquals("preserve-me", legacyNode.get("unrelated", null))
        } finally {
            testRoot.removeNode()
            testParent.flush()
        }
    }

    @Test
    fun workerQueueAndBestEffortLossPolicyAreFiniteAndExplicit() {
        assertEquals(1, DesktopAudioExecutionPolicy.WORKER_COUNT)
        assertEquals(24, DesktopAudioExecutionPolicy.QUEUE_CAPACITY)
    }

    @Test
    fun workerAndDiscardOldestQueueEnforceOneAndTwentyFour() {
        val executor = createTestDesktopAudioExecutor()
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

private class TestDesktopProfilePersistenceCapability(
    private val profileNode: Preferences,
    private val legacyNode: Preferences,
) : ProfilePersistenceCapability {
    override fun readV4(): String? =
        profileNode.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)

    override fun writeV4(payload: String) {
        profileNode.apply {
            put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)
            flush()
        }
    }

    override fun readLegacyProgressV2(): String? =
        legacyNode.get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)

    override fun readLegacyMatter(): String? =
        legacyNode.get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)

    override fun removeLegacyProgressV2() {
        legacyNode.apply {
            remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)
            flush()
        }
    }

    override fun removeLegacyMatter() {
        legacyNode.apply {
            remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)
            flush()
        }
    }
}

private fun createTestDesktopAudioExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    DesktopAudioExecutionPolicy.WORKER_COUNT,
    DesktopAudioExecutionPolicy.WORKER_COUNT,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(DesktopAudioExecutionPolicy.QUEUE_CAPACITY),
    { runnable -> Thread(runnable, DesktopAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
    ThreadPoolExecutor.DiscardOldestPolicy(),
)
