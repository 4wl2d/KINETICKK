// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformCapabilitiesAndroidTest {
    @Test
    fun persistenceCapabilityUsesTheCurrentSnapshotKeyAndLeavesHistoricalKeysUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        AndroidApplicationContext.install(context)
        val preferences = context.getSharedPreferences("kinetickk.profile", 0)
        preferences.edit()
            .remove("snapshot")
            .putString("snapshot_v4", "historical-snapshot")
            .putString("progress_v2", "historical-progress")
            .commit()
        try {
            val capability = createPlatformProfilePersistenceCapability()
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
