// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.Collections
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformCapabilitiesDesktopTest {
    @Test
    fun audioBrokerIsInstanceOwnedAndCloseIsIdempotent() {
        val first = createPlatformTonePlaybackCapability()
        val second = createPlatformTonePlaybackCapability()
        assertNotSame(first, second)

        first.close()
        first.close()
        second.close()
    }

    @Test
    fun persistenceCapabilityUsesOnlySnapshotAndPreservesUnrelatedData() {
        val testRoot = Preferences.userRoot().node(
            "kinetickk-test/app-platform-capability/${UUID.randomUUID()}",
        )
        val testParent = testRoot.parent()
        val profileNode = testRoot.node("profile")
        val legacyNode = testRoot.node("legacy")
        try {
            val capability = TestDesktopProfilePersistenceCapability(profileNode)
            profileNode.put("unrelated", "preserve-me")
            profileNode.flush()
            legacyNode.put("progress_v2", "legacy")
            legacyNode.put("kinetickk_matter", "1")
            legacyNode.flush()

            assertEquals(
                ProfilePersistenceMutationResult.COMPLETED,
                capability.writeSnapshot("strict-current-payload"),
            )
            assertEquals(
                ProfilePersistenceReadResult.Observed("strict-current-payload"),
                capability.readSnapshot(),
            )
            assertNull(legacyNode.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT, null))
            assertEquals("preserve-me", profileNode.get("unrelated", null))
            assertEquals("legacy", legacyNode.get("progress_v2", null))
            assertEquals("1", legacyNode.get("kinetickk_matter", null))
        } finally {
            testRoot.removeNode()
            testParent.flush()
        }
    }

    @Test
    fun desktopPreferencesValueLengthAccepts8192AndRejects8193BeforeExecution() {
        assertEquals(8_192, Preferences.MAX_VALUE_LENGTH)
        assertNull(desktopProfilePayloadAdmission(Preferences.MAX_VALUE_LENGTH))
        assertEquals(
            ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
            desktopProfilePayloadAdmission(Preferences.MAX_VALUE_LENGTH + 1),
        )
    }

    @Test
    fun desktopPreferenceKeyCountAccepts64AndRejects65BeforeIteration() {
        assertEquals(64, MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE)
        assertNull(desktopPreferenceKeyCountAdmission(MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE))
        assertEquals(
            ProfilePersistenceReadResult.Failed,
            desktopPreferenceKeyCountAdmission(MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE + 1),
        )

        val exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT
        val acceptedNames = Array(MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE) { index ->
            if (index == MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE - 1) exactKey else "unrelated-$index"
        }
        var acceptedExactReads = 0
        assertEquals(
            ProfilePersistenceReadResult.Observed("payload"),
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = { acceptedNames },
                loadExactValue = {
                    acceptedExactReads += 1
                    "payload"
                },
            ),
        )
        assertEquals(1, acceptedExactReads)

        var refusedExactReads = 0
        assertEquals(
            ProfilePersistenceReadResult.Failed,
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = {
                    Array(MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE + 1) { exactKey }
                },
                loadExactValue = {
                    refusedExactReads += 1
                    "unreachable"
                },
            ),
        )
        assertEquals(0, refusedExactReads)
    }

    @Test
    fun desktopReadStageClassifiesOnlyDocumentedProviderFailures() {
        val exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT
        listOf<Throwable>(
            BackingStoreException("unavailable"),
            SecurityException("denied"),
            IllegalStateException("removed"),
        ).forEach { failure ->
            assertEquals(
                ProfilePersistenceReadResult.Failed,
                desktopProfileReadCall(
                    exactKey = exactKey,
                    loadKeyNames = { throw failure },
                    loadExactValue = { error("exact value must not be read") },
                ),
            )
        }

        var exactReadAttempted = false
        assertEquals(
            ProfilePersistenceReadResult.Observed(null),
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = { arrayOf("unrelated") },
                loadExactValue = {
                    exactReadAttempted = true
                    "unreachable"
                },
            ),
        )
        assertFalse(exactReadAttempted)

        listOf<Throwable>(
            SecurityException("denied"),
            IllegalStateException("removed"),
        ).forEach { failure ->
            assertEquals(
                ProfilePersistenceReadResult.Failed,
                desktopProfileReadCall(
                    exactKey = exactKey,
                    loadKeyNames = { arrayOf(exactKey) },
                    loadExactValue = { throw failure },
                ),
            )
        }
        assertEquals(
            ProfilePersistenceReadResult.Failed,
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = { arrayOf(exactKey) },
                loadExactValue = { null },
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = { throw IllegalArgumentException("programming fault") },
                loadExactValue = { null },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            desktopProfileReadCall(
                exactKey = exactKey,
                loadKeyNames = { arrayOf(exactKey) },
                loadExactValue = { throw IllegalArgumentException("programming fault") },
            )
        }
    }

    @Test
    fun desktopMutationStageSeparatesBeforeExecutionPossibleExecutionAndProgrammingFaults() {
        listOf<Throwable>(
            IllegalArgumentException("invalid provider input"),
            IllegalStateException("removed before mutation"),
        ).forEach { failure ->
            var flushed = false
            assertEquals(
                ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
                desktopProfileMutationCall(
                    mutate = { throw failure },
                    flush = { flushed = true },
                ),
            )
            assertFalse(flushed)
        }

        var mutated = false
        assertEquals(
            ProfilePersistenceMutationResult.POSSIBLE_EXECUTION,
            desktopProfileMutationCall(
                mutate = { mutated = true },
                flush = { throw BackingStoreException("ambiguous flush") },
            ),
        )
        assertTrue(mutated)

        assertFailsWith<SecurityException> {
            desktopProfileMutationCall(
                mutate = { throw SecurityException("unclassified mutation fault") },
                flush = {},
            )
        }
        assertFailsWith<IllegalStateException> {
            desktopProfileMutationCall(
                mutate = {},
                flush = { throw IllegalStateException("unclassified post-mutation fault") },
            )
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
) : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(profileNode.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT, null))

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        profileNode.apply {
            put(ProfilePersistenceContract.DESKTOP_SNAPSHOT, payload)
            flush()
        }
        return ProfilePersistenceMutationResult.COMPLETED
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
