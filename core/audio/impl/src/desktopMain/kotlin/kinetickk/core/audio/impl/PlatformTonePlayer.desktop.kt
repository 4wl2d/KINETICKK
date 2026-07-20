// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.audio.impl

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

internal object DesktopAudioExecutionPolicy {
    const val WORKER_COUNT = 1
    const val QUEUE_CAPACITY = 24
    const val THREAD_NAME = "kinetickk-audio"
    const val BACKPRESSURE_POLICY = "discard-oldest"
}

internal actual fun createPlatformTonePlayer(): NumericTonePlayer = DesktopTonePlayer()

private class DesktopTonePlayer : NumericTonePlayer {
    private val executor = ThreadPoolExecutor(
        DesktopAudioExecutionPolicy.WORKER_COUNT,
        DesktopAudioExecutionPolicy.WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(DesktopAudioExecutionPolicy.QUEUE_CAPACITY),
        { runnable -> Thread(runnable, DesktopAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    override fun unlock() = Unit

    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        if (!isToneRequestAllowed(frequency, durationSeconds, volume, wave) || executor.isShutdown) return
        runCatching {
            executor.execute {
                runCatching { synthesize(frequency, durationSeconds, volume, wave) }
            }
        }
    }

    override fun close() {
        runCatching { executor.shutdownNow() }
    }

    private fun synthesize(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val sampleCount = (SAMPLE_RATE * durationSeconds).toInt().coerceAtLeast(1)
        val bytes = ByteArray(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val phase = index * frequency / SAMPLE_RATE
            val raw = when (wave) {
                1 -> if (phase % 1f < 0.5f) 1f else -1f
                2 -> phase % 1f * 2f - 1f
                3 -> 1f - 4f * kotlin.math.abs(phase % 1f - 0.5f)
                else -> sin(phase * PI * 2.0).toFloat()
            }
            val envelope = kotlin.math.min(1f, index / (SAMPLE_RATE * 0.008f)) *
                (1f - index.toFloat() / sampleCount).coerceAtLeast(0f)
            val sample = (raw * envelope * volume * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[index * 2] = (sample and 0xFF).toByte()
            bytes[index * 2 + 1] = (sample shr 8 and 0xFF).toByte()
        }
        AudioSystem.getSourceDataLine(format).use { line ->
            line.open(format, bytes.size)
            line.start()
            line.write(bytes, 0, bytes.size)
            line.drain()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
