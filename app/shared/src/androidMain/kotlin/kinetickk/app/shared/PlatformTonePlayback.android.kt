// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.impl.PreparedPcmTones
import kinetickk.resource.audio.impl.pcmSample
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AndroidAudioExecutionPolicy {
    const val WORKER_COUNT = 1
    const val QUEUE_CAPACITY = 24
    const val THREAD_NAME = "kinetickk-android-audio"
    const val SAMPLE_RATE = 22_050
    const val BYTES_PER_SAMPLE = 2
    const val MAX_SAMPLE_COUNT = SAMPLE_RATE
    const val MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE
}

internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
    AndroidTonePlaybackCapability()

internal class AndroidTonePlaybackCapability(
    private val createTrack: () -> AudioTrack = ::createAndroidToneTrack,
) : TonePlaybackCapability {
    private val lifecycleLock = Any()
    private var track: AudioTrack? = null
    @Volatile private var outputUnavailable = false
    private var tones = PreparedPcmTones(emptyList(), AndroidAudioExecutionPolicy.SAMPLE_RATE)
    private val samples = ShortArray(AndroidAudioExecutionPolicy.MAX_SAMPLE_COUNT)
    private var previousSampleCount = 0
    private val executor = ThreadPoolExecutor(
        AndroidAudioExecutionPolicy.WORKER_COUNT,
        AndroidAudioExecutionPolicy.WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(AndroidAudioExecutionPolicy.QUEUE_CAPACITY),
        { runnable -> Thread(runnable, AndroidAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    override fun unlock() {
        if (executor.isShutdown || !outputUnavailable) return
        executor.execute {
            synchronized(lifecycleLock) {
                if (!executor.isShutdown && outputUnavailable) {
                    outputUnavailable = false
                    output()
                }
            }
        }
    }

    override suspend fun prepare(requests: List<ToneRequest>) {
        withContext(Dispatchers.IO) {
            val prepared = PreparedPcmTones(requests, AndroidAudioExecutionPolicy.SAMPLE_RATE)
            synchronized(lifecycleLock) {
                if (executor.isShutdown) return@withContext
                tones = prepared
                output()
                executor.prestartAllCoreThreads()
            }
        }
    }

    override fun play(request: ToneRequest) {
        if (executor.isShutdown) return
        executor.execute {
            synchronized(lifecycleLock) {
                if (!executor.isShutdown) playPrepared(request)
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
        synchronized(lifecycleLock) {
            track?.release()
            track = null
        }
    }

    private fun output(): AudioTrack? {
        if (outputUnavailable) return null
        return track ?: try {
            openOutput().also { track = it; outputUnavailable = it == null }
        } catch (_: UnsupportedOperationException) {
            outputUnavailable = true
            null
        } catch (_: IllegalArgumentException) {
            outputUnavailable = true
            null
        } catch (_: SecurityException) {
            outputUnavailable = true
            null
        } catch (_: IllegalStateException) {
            // The silent priming write failed on a track that never became usable.
            outputUnavailable = true
            null
        }
    }

    private fun openOutput(): AudioTrack? {
        val output = createTrack()
        try {
            // A static buffer has no streaming start threshold. Fill its bounded tail with silence
            // once; short effects can start immediately on every supported API (including API 26).
            val written = output.write(ShortArray(samples.size), 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written == samples.size) return output
        } catch (failure: Exception) {
            output.release()
            throw failure
        }
        output.release()
        return null
    }

    private fun playPrepared(request: ToneRequest) {
        val output = output() ?: return
        val waveform = tones.samples(request)
        for (index in waveform.indices) samples[index] = pcmSample(waveform[index], request.gain)
        if (waveform.size < previousSampleCount) samples.fill(0, waveform.size, previousSampleCount)
        previousSampleCount = waveform.size
        try {
            try {
                // Some OEM AudioTrack implementations transiently report STATE_UNINITIALIZED after
                // native setup has succeeded. The blocking write is the authoritative capability
                // operation and still returns a negative error code for a genuinely unusable track.
                val written = output.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                check(written == samples.size) { "Android tone AudioTrack accepted $written samples" }
                check(output.reloadStaticData() == AudioTrack.SUCCESS) { "Android tone AudioTrack could not rewind" }
                output.play()
                Thread.sleep((request.durationSeconds * 1_000f).toLong().coerceAtLeast(1L))
            } finally {
                output.stop()
            }
        } catch (failure: InterruptedException) {
            // Closing the app cancels an in-flight tone through shutdownNow().
            // Preserve cancellation without allowing the audio worker to crash the process.
            Thread.currentThread().interrupt()
            if (!executor.isShutdown) throw failure
        } catch (_: RuntimeException) {
            // A dead track (audioserver restart, route change) fails write, rewind, play or stop.
            // An uncaught worker exception would kill the process: drop this track and open a
            // fresh one for the next tone, as desktop does for a line that stops accepting samples.
            output.release()
            track = null
        }
    }
}

internal fun createAndroidToneTrack(): AudioTrack = AudioTrack.Builder()
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
    )
    .setAudioFormat(
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AndroidAudioExecutionPolicy.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
    )
    .setBufferSizeInBytes(AndroidAudioExecutionPolicy.MAX_PCM_BYTES)
    .setTransferMode(AudioTrack.MODE_STATIC)
    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
    .build()
