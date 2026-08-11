// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.impl

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kinetickk.resource.audio.api.ToneRequestLimits
import kotlin.math.PI
import kotlin.math.sin

internal object DesktopAudioExecutionPolicy {
    const val WORKER_COUNT = 1
    const val QUEUE_CAPACITY = 24
    const val THREAD_NAME = "kinetickk-audio"
    const val SAMPLE_RATE = 22_050
    const val BYTES_PER_SAMPLE = 2
    const val MAX_SAMPLE_COUNT = SAMPLE_RATE
    const val MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE
}

internal actual fun createPlatformTonePlayer(): NumericTonePlayer = DesktopTonePlayer()

internal fun createDesktopAudioExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    DesktopAudioExecutionPolicy.WORKER_COUNT,
    DesktopAudioExecutionPolicy.WORKER_COUNT,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(DesktopAudioExecutionPolicy.QUEUE_CAPACITY),
    { runnable -> Thread(runnable, DesktopAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
    ThreadPoolExecutor.DiscardOldestPolicy(),
)

internal data class DesktopToneBufferShape(
    val sampleCount: Int,
    val byteCount: Int,
)

internal fun desktopToneBufferShape(durationSeconds: Float): DesktopToneBufferShape {
    require(
        durationSeconds.isFinite() &&
            durationSeconds >= ToneRequestLimits.MIN_DURATION_SECONDS &&
            durationSeconds <= ToneRequestLimits.MAX_DURATION_SECONDS,
    ) {
        "Desktop tone duration must remain within the validated Resource bound"
    }
    val sampleCount = (DesktopAudioExecutionPolicy.SAMPLE_RATE * durationSeconds)
        .toInt()
        .coerceAtLeast(1)
    check(sampleCount <= DesktopAudioExecutionPolicy.MAX_SAMPLE_COUNT)
    val byteCount = sampleCount * DesktopAudioExecutionPolicy.BYTES_PER_SAMPLE
    check(byteCount <= DesktopAudioExecutionPolicy.MAX_PCM_BYTES)
    return DesktopToneBufferShape(
        sampleCount = sampleCount,
        byteCount = byteCount,
    )
}

private class DesktopTonePlayer : NumericTonePlayer {
    private val executor = createDesktopAudioExecutor()

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
        val shape = desktopToneBufferShape(durationSeconds)
        val format = AudioFormat(DesktopAudioExecutionPolicy.SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val bytes = ByteArray(shape.byteCount)
        for (index in 0 until shape.sampleCount) {
            val phase = index * frequency / DesktopAudioExecutionPolicy.SAMPLE_RATE
            val raw = when (wave) {
                1 -> if (phase % 1f < 0.5f) 1f else -1f
                2 -> phase % 1f * 2f - 1f
                3 -> 1f - 4f * kotlin.math.abs(phase % 1f - 0.5f)
                else -> sin(phase * PI * 2.0).toFloat()
            }
            val envelope = kotlin.math.min(
                1f,
                index / (DesktopAudioExecutionPolicy.SAMPLE_RATE * 0.008f),
            ) * (1f - index.toFloat() / shape.sampleCount).coerceAtLeast(0f)
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
}
