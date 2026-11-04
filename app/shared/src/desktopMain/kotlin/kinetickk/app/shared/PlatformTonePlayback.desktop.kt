// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.LineUnavailableException
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.impl.PreparedPcmTones
import kinetickk.resource.audio.impl.pcmSample
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object DesktopAudioExecutionPolicy {
    const val WORKER_COUNT = 1
    const val QUEUE_CAPACITY = 24
    const val THREAD_NAME = "kinetickk-audio"
    const val SAMPLE_RATE = 22_050
    const val BYTES_PER_SAMPLE = 2
    const val MAX_SAMPLE_COUNT = SAMPLE_RATE
    const val MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE
}

internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
    DesktopTonePlaybackCapability()

internal class DesktopTonePlaybackCapability(
    private val createLine: (AudioFormat) -> SourceDataLine = AudioSystem::getSourceDataLine,
) : TonePlaybackCapability {
    private val lifecycleLock = Any()
    private val format = AudioFormat(DesktopAudioExecutionPolicy.SAMPLE_RATE.toFloat(), 16, 1, true, false)
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var outputUnavailable = false
    private var tones = PreparedPcmTones(emptyList(), DesktopAudioExecutionPolicy.SAMPLE_RATE)
    private val bytes = ByteArray(DesktopAudioExecutionPolicy.MAX_PCM_BYTES)
    private val executor = ThreadPoolExecutor(
        DesktopAudioExecutionPolicy.WORKER_COUNT,
        DesktopAudioExecutionPolicy.WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(DesktopAudioExecutionPolicy.QUEUE_CAPACITY),
        { runnable -> Thread(runnable, DesktopAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
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
            val prepared = PreparedPcmTones(requests, DesktopAudioExecutionPolicy.SAMPLE_RATE)
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
                if (!executor.isShutdown) {
                    try {
                        playPrepared(request)
                    } catch (failure: RuntimeException) {
                        // Closing the line unblocks native write/drain; cancellation is terminal.
                        if (!executor.isShutdown) throw failure
                    }
                }
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
        // Native drain need not respond to thread interruption. Close before waiting for the worker.
        val activeLine = line
        activeLine?.close()
        synchronized(lifecycleLock) {
            if (line !== activeLine) line?.close()
            line = null
        }
    }

    private fun output(): SourceDataLine? {
        if (outputUnavailable) return null
        return line ?: try {
            openOutput()
        } catch (_: LineUnavailableException) {
            outputUnavailable = true
            null
        } catch (_: IllegalArgumentException) {
            // Java Sound uses this when no mixer supports the requested PCM format.
            outputUnavailable = true
            null
        } catch (_: SecurityException) {
            outputUnavailable = true
            null
        }
    }

    private fun openOutput(): SourceDataLine = createLine(format).also { output ->
        try {
            output.open(format, DesktopAudioExecutionPolicy.MAX_PCM_BYTES)
        } catch (failure: Exception) {
            output.close()
            throw failure
        }
        line = output
    }

    private fun playPrepared(request: ToneRequest) {
        val output = output() ?: return
        if (executor.isShutdown) return
        val samples = tones.samples(request)
        for (index in samples.indices) {
            val sample = pcmSample(samples[index], request.gain).toInt()
            bytes[index * 2] = (sample and 0xFF).toByte()
            bytes[index * 2 + 1] = (sample shr 8 and 0xFF).toByte()
        }
        output.start()
        var written = 0
        val byteCount = samples.size * DesktopAudioExecutionPolicy.BYTES_PER_SAMPLE
        while (written < byteCount && !executor.isShutdown) {
            val count = output.write(bytes, written, byteCount - written)
            if (count <= 0) {
                // A lost device stops accepting samples. Reopen it for the next tone.
                output.close()
                line = null
                return
            }
            written += count
        }
        if (!executor.isShutdown) {
            output.drain()
            if (!executor.isShutdown) output.stop()
        }
    }
}
