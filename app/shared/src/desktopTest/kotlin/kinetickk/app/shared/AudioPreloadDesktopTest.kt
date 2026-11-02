// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPreloadDesktopTest {
    private val tone = ToneRequest(520f, 0.035f, 0.11f, ToneWave.SINE)

    @Test
    fun preparationOpensSilentlyAndPlaybackReusesTheOutputAndBuffer() = runBlocking {
        val output = RecordingLine(expectedWrites = 2)
        val creations = AtomicInteger()
        val player = DesktopTonePlaybackCapability { creations.incrementAndGet(); output.line }
        try {
            player.prepare(listOf(tone))
            assertEquals(1, output.opens.get())
            assertEquals(0, output.starts.get())
            assertEquals(0, output.writes.get())
            player.play(tone)
            player.play(tone.copy(gain = 0.2f))
            assertTrue(output.written.await(3, TimeUnit.SECONDS))
            assertEquals(1, creations.get())
            assertEquals(1, output.opens.get())
            assertEquals(2, output.writes.get())
            assertEquals(1, output.buffers.size)
        } finally {
            player.close()
        }
        player.close()
        assertEquals(1, output.closes.get())
    }

    @Test
    fun missingOutputDoesNotBlockStartupOrRetryDeviceCreationDuringGameplay() = runBlocking {
        val attempts = AtomicInteger()
        val player = DesktopTonePlaybackCapability {
            attempts.incrementAndGet()
            throw LineUnavailableException("no output device")
        }
        try {
            player.prepare(listOf(tone))
            player.prepare(listOf(tone))
            player.play(tone)
            assertEquals(1, attempts.get())
        } finally {
            player.close()
        }
    }

    @Test
    fun explicitUserGestureCanRecoverAnOutputThatWasUnavailableAtStartup() = runBlocking {
        val output = RecordingLine(expectedWrites = 1)
        val attempts = AtomicInteger()
        val player = DesktopTonePlaybackCapability {
            if (attempts.incrementAndGet() == 1) throw LineUnavailableException("temporarily unavailable")
            output.line
        }
        try {
            player.prepare(listOf(tone))
            player.unlock()
            player.play(tone)
            assertTrue(output.written.await(3, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())
        } finally {
            player.close()
        }
    }

    @Test
    fun closeUnblocksAnInFlightNativeDrainAndDoesNotReopenTheOutput() = runBlocking {
        val output = RecordingLine(expectedWrites = 1, blockDrain = true)
        val player = DesktopTonePlaybackCapability { output.line }
        player.prepare(listOf(tone))
        player.play(tone)
        assertTrue(output.draining.await(3, TimeUnit.SECONDS))
        val closing = Thread { player.close() }.apply { start() }
        closing.join(3_000)
        assertTrue(!closing.isAlive, "Closing must unblock a non-interruptible native drain")
        player.prepare(listOf(tone))
        player.play(tone)
        assertEquals(1, output.opens.get())
        assertEquals(1, output.closes.get())
    }
}

private class RecordingLine(expectedWrites: Int, private val blockDrain: Boolean = false) {
    val opens = AtomicInteger()
    val starts = AtomicInteger()
    val writes = AtomicInteger()
    val closes = AtomicInteger()
    val written = CountDownLatch(expectedWrites)
    val draining = CountDownLatch(1)
    val buffers = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    private val released = CountDownLatch(1)
    val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
        when (method.name) {
            "open" -> { opens.incrementAndGet(); null }
            "start" -> { starts.incrementAndGet(); null }
            "stop" -> null
            "write" -> {
                buffers.add(checkNotNull(args)[0])
                writes.incrementAndGet()
                written.countDown()
                args[2] as Int
            }
            "drain" -> {
                draining.countDown()
                if (blockDrain) {
                    while (released.count > 0) {
                        try { released.await() } catch (_: InterruptedException) { /* native drain ignores interruption */ }
                    }
                }
                null
            }
            "close" -> { closes.incrementAndGet(); released.countDown(); null }
            else -> error("Unexpected audio call: ${method.name}")
        }
    } as SourceDataLine
}
