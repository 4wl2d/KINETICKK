package void.kinetic.audio

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

internal actual class PlatformTonePlayer actual constructor() {
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(24),
        { runnable -> Thread(runnable, "kinetic-void-audio").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    actual fun unlock() = Unit

    actual fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        if (frequency <= 0f || durationSeconds <= 0f || volume <= 0f || executor.isShutdown) return
        executor.execute {
            runCatching { synthesize(frequency, durationSeconds, volume.coerceIn(0f, 1f), wave) }
        }
    }

    actual fun close() {
        executor.shutdownNow()
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
            val sample = (raw * envelope * volume * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
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
