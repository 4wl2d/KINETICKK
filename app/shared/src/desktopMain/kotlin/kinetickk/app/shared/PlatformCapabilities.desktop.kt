// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneRequestLimits
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
    DesktopProfilePersistenceCapability(
        profileNode = {
            Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_PROFILE_NODE)
        },
        legacyNode = {
            Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_LEGACY_NODE)
        },
    )

private class DesktopProfilePersistenceCapability(
    private val profileNode: () -> Preferences,
    private val legacyNode: () -> Preferences,
) : ProfilePersistenceCapability {
    override fun readV4(): String? =
        profileNode().get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)

    override fun writeV4(payload: String) {
        profileNode().apply {
            put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)
            flush()
        }
    }

    override fun readLegacyProgressV2(): String? =
        legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)

    override fun readLegacyMatter(): String? =
        legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)

    override fun removeLegacyProgressV2() {
        legacyNode().apply {
            remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)
            flush()
        }
    }

    override fun removeLegacyMatter() {
        legacyNode().apply {
            remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)
            flush()
        }
    }
}

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
    return DesktopToneBufferShape(sampleCount = sampleCount, byteCount = byteCount)
}

private class DesktopTonePlaybackCapability : TonePlaybackCapability {
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

    override fun play(request: ToneRequest) {
        if (executor.isShutdown) return
        runCatching {
            executor.execute {
                runCatching { synthesize(request) }
            }
        }
    }

    override fun close() {
        runCatching { executor.shutdownNow() }
    }

    private fun synthesize(request: ToneRequest) {
        val shape = desktopToneBufferShape(request.durationSeconds)
        val format = AudioFormat(DesktopAudioExecutionPolicy.SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val bytes = ByteArray(shape.byteCount)
        for (index in 0 until shape.sampleCount) {
            val phase = index * request.frequencyHz / DesktopAudioExecutionPolicy.SAMPLE_RATE
            val raw = when (request.wave) {
                ToneWave.SQUARE -> if (phase % 1f < 0.5f) 1f else -1f
                ToneWave.SAW -> phase % 1f * 2f - 1f
                ToneWave.TRIANGLE -> 1f - 4f * abs(phase % 1f - 0.5f)
                ToneWave.SINE -> sin(phase * PI * 2.0).toFloat()
            }
            val envelope = min(
                1f,
                index / (DesktopAudioExecutionPolicy.SAMPLE_RATE * 0.008f),
            ) * (1f - index.toFloat() / shape.sampleCount).coerceAtLeast(0f)
            val sample = (raw * envelope * request.gain * Short.MAX_VALUE).toInt()
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
