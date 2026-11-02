// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class AudioPreloadAndroidTest {
    @Test
    fun preloadedOutputIsSilentUntilPlaybackAndShortEffectsAdvanceThePlaybackHead() = runBlocking {
        val created = AtomicInteger()
        var output: AudioTrack? = null
        val player = AndroidTonePlaybackCapability {
            createAndroidToneTrack().also { output = it; created.incrementAndGet() }
        }
        val tone = ToneRequest(520f, 0.035f, 0f, ToneWave.SINE)
        try {
            player.prepare(listOf(tone))
            val track = checkNotNull(output)
            assertEquals(AudioTrack.PLAYSTATE_STOPPED, track.playState)
            assertEquals(0, track.playbackHeadPosition)
            val advanced = CountDownLatch(1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { advanced.countDown() }
                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }, Handler(Looper.getMainLooper()))
            assertEquals(AudioTrack.SUCCESS, track.setNotificationMarkerPosition(1))
            player.play(tone)
            assertTrue("The 35 ms cue must start without filling a one-second streaming threshold", advanced.await(3, TimeUnit.SECONDS))
            assertEquals(1, created.get())
        } finally {
            player.close()
        }
    }
}
