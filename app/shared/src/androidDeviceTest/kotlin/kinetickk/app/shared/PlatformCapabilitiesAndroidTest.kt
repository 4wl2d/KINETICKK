// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.foundation.diagnostics.CrashDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PlatformCapabilitiesAndroidTest {
    @Test
    fun persistenceCapabilityUsesTheCurrentSnapshotKeyAndLeavesHistoricalKeysUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        AndroidApplicationContext.install(context)
        val preferences = context.getSharedPreferences("kinetickk.profile.v2", 0)
        preferences.edit()
            .remove("snapshot")
            .putString("snapshot_v4", "historical-snapshot")
            .putString("progress_v2", "historical-progress")
            .commit()
        try {
            val capability = createPlatformProfilePersistenceCapability(CrashDiagnostics.None)
            assertEquals(ProfilePersistenceReadResult.Observed(null), capability.readSnapshot())
            assertEquals(
                ProfilePersistenceMutationResult.COMPLETED,
                capability.writeSnapshot("current-snapshot"),
            )
            assertEquals(
                ProfilePersistenceReadResult.Observed("current-snapshot"),
                capability.readSnapshot(),
            )
            assertEquals("historical-snapshot", preferences.getString("snapshot_v4", null))
            assertEquals("historical-progress", preferences.getString("progress_v2", null))
        } finally {
            preferences.edit()
                .remove("snapshot")
                .remove("snapshot_v4")
                .remove("progress_v2")
                .commit()
        }
    }

    @Test
    fun androidAudioBrokerIsInstanceOwnedAndCloseIsIdempotent() {
        val first = createPlatformTonePlaybackCapability()
        val second = createPlatformTonePlaybackCapability()

        assertNotSame(first, second)
        first.close()
        first.close()
        second.close()
    }

    @Test
    fun closingDuringPlaybackCancelsWithoutAnUncaughtWorkerFailure() {
        val existing = Thread.getAllStackTraces().keys
        val playback = createPlatformTonePlaybackCapability()
        val failure = AtomicReference<Throwable?>()
        try {
            playback.play(ToneRequest(440f, 1f, 0.01f, ToneWave.SINE))
            var worker: Thread? = null
            repeat(100) {
                if (worker == null) {
                    worker = Thread.getAllStackTraces().keys.firstOrNull {
                        it !in existing && it.name == AndroidAudioExecutionPolicy.THREAD_NAME
                    }
                    if (worker == null) Thread.sleep(5)
                }
            }
            val activeWorker = checkNotNull(worker) { "Audio worker never started" }
            activeWorker.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> failure.set(error) }
            repeat(100) {
                if (activeWorker.state != Thread.State.TIMED_WAITING) Thread.sleep(5)
            }
            assertEquals(Thread.State.TIMED_WAITING, activeWorker.state)
            playback.close()
            activeWorker.join(2_000)
            assertTrue("Audio worker should stop on close", !activeWorker.isAlive)
            assertEquals(null, failure.get())
        } finally {
            playback.close()
        }
    }

    @Test
    fun androidWorkerAndDiscardOldestQueueEnforceOneAndTwentyFour() {
        assertEquals(1, AndroidAudioExecutionPolicy.WORKER_COUNT)
        assertEquals(24, AndroidAudioExecutionPolicy.QUEUE_CAPACITY)
        assertEquals("kinetickk-android-audio", AndroidAudioExecutionPolicy.THREAD_NAME)
    }

    @Test
    fun androidSynthesisBufferAcceptsMaximumDurationAndRejectsNext() {
        val maximum = androidToneBufferShape(1f)
        assertEquals(22_050, maximum.sampleCount)
        assertEquals(44_100, maximum.byteCount)

        try {
            androidToneBufferShape(Math.nextUp(1f))
            fail("The first duration above the Resource maximum must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("validated Resource bound"))
        }
    }
}
