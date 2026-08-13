// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneRequestLimits
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private const val ANDROID_PROFILE_PREFERENCES = "kinetickk.profile"
private const val ANDROID_LEGACY_PREFERENCES = "kinetickk.progression"
private const val ANDROID_SNAPSHOT_V4 = "snapshot_v4"
private const val ANDROID_LEGACY_PROGRESS_V2 = "progress_v2"
private const val ANDROID_LEGACY_MATTER = "kinetickk_matter"

internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability {
    val context = AndroidApplicationContext.requireContext()
    return AndroidProfilePersistenceCapability(
        profile = context.getSharedPreferences(ANDROID_PROFILE_PREFERENCES, Context.MODE_PRIVATE),
        legacy = context.getSharedPreferences(ANDROID_LEGACY_PREFERENCES, Context.MODE_PRIVATE),
    )
}

private class AndroidProfilePersistenceCapability(
    private val profile: SharedPreferences,
    private val legacy: SharedPreferences,
) : ProfilePersistenceCapability {
    override fun readV4(): ProfilePersistenceReadResult =
        androidProfileReadCall(profile, ANDROID_SNAPSHOT_V4)

    override fun writeV4(payload: String): ProfilePersistenceMutationResult =
        androidProfileMutationCall {
            profile.edit().putString(ANDROID_SNAPSHOT_V4, payload)
        }

    override fun readLegacyProgressV2(): ProfilePersistenceReadResult =
        androidProfileReadCall(legacy, ANDROID_LEGACY_PROGRESS_V2)

    override fun readLegacyMatter(): ProfilePersistenceReadResult =
        androidProfileReadCall(legacy, ANDROID_LEGACY_MATTER)

    override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult =
        androidProfileMutationCall {
            legacy.edit().remove(ANDROID_LEGACY_PROGRESS_V2)
        }

    override fun removeLegacyMatter(): ProfilePersistenceMutationResult =
        androidProfileMutationCall {
            legacy.edit().remove(ANDROID_LEGACY_MATTER)
        }
}

private fun androidProfileReadCall(
    preferences: SharedPreferences,
    exactKey: String,
): ProfilePersistenceReadResult {
    val keyIsPresent = try {
        preferences.contains(exactKey)
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    if (!keyIsPresent) return ProfilePersistenceReadResult.Observed(null)

    val payload = try {
        preferences.getString(exactKey, null)
    } catch (_: ClassCastException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    return if (payload == null) {
        ProfilePersistenceReadResult.Failed
    } else {
        ProfilePersistenceReadResult.Observed(payload)
    }
}

private fun androidProfileMutationCall(
    prepare: () -> SharedPreferences.Editor,
): ProfilePersistenceMutationResult {
    val editor = try {
        prepare()
    } catch (_: SecurityException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalArgumentException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalStateException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    }
    return try {
        if (editor.commit()) {
            ProfilePersistenceMutationResult.COMPLETED
        } else {
            ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
        }
    } catch (_: SecurityException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    } catch (_: IllegalStateException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    }
}

internal object AndroidAudioExecutionPolicy {
    const val WORKER_COUNT = 1
    const val QUEUE_CAPACITY = 24
    const val THREAD_NAME = "kinetickk-android-audio"
    const val SAMPLE_RATE = 22_050
    const val BYTES_PER_SAMPLE = 2
    const val MAX_SAMPLE_COUNT = SAMPLE_RATE
    const val MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE
}

internal data class AndroidToneBufferShape(
    val sampleCount: Int,
    val byteCount: Int,
)

internal fun androidToneBufferShape(durationSeconds: Float): AndroidToneBufferShape {
    require(
        durationSeconds.isFinite() &&
            durationSeconds >= ToneRequestLimits.MIN_DURATION_SECONDS &&
            durationSeconds <= ToneRequestLimits.MAX_DURATION_SECONDS,
    ) {
        "Android tone duration must remain within the validated Resource bound"
    }
    val sampleCount = (AndroidAudioExecutionPolicy.SAMPLE_RATE * durationSeconds)
        .toInt()
        .coerceAtLeast(1)
    check(sampleCount <= AndroidAudioExecutionPolicy.MAX_SAMPLE_COUNT)
    val byteCount = sampleCount * AndroidAudioExecutionPolicy.BYTES_PER_SAMPLE
    check(byteCount <= AndroidAudioExecutionPolicy.MAX_PCM_BYTES)
    return AndroidToneBufferShape(sampleCount = sampleCount, byteCount = byteCount)
}

internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
    AndroidTonePlaybackCapability()

private class AndroidTonePlaybackCapability : TonePlaybackCapability {
    private val executor = ThreadPoolExecutor(
        AndroidAudioExecutionPolicy.WORKER_COUNT,
        AndroidAudioExecutionPolicy.WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(AndroidAudioExecutionPolicy.QUEUE_CAPACITY),
        { runnable -> Thread(runnable, AndroidAudioExecutionPolicy.THREAD_NAME).apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    override fun unlock() = Unit

    override fun play(request: ToneRequest) {
        if (executor.isShutdown) return
        executor.execute { synthesize(request) }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun synthesize(request: ToneRequest) {
        val shape = androidToneBufferShape(request.durationSeconds)
        val samples = ShortArray(shape.sampleCount)
        for (index in samples.indices) {
            val phase = index * request.frequencyHz / AndroidAudioExecutionPolicy.SAMPLE_RATE
            val raw = when (request.wave) {
                ToneWave.SQUARE -> if (phase % 1f < 0.5f) 1f else -1f
                ToneWave.SAW -> phase % 1f * 2f - 1f
                ToneWave.TRIANGLE -> 1f - 4f * abs(phase % 1f - 0.5f)
                ToneWave.SINE -> sin(phase * PI * 2.0).toFloat()
            }
            val envelope = min(
                1f,
                index / (AndroidAudioExecutionPolicy.SAMPLE_RATE * 0.008f),
            ) * (1f - index.toFloat() / shape.sampleCount).coerceAtLeast(0f)
            samples[index] = (raw * envelope * request.gain * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val track = AudioTrack.Builder()
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
            .setBufferSizeInBytes(shape.byteCount)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        try {
            // Some OEM AudioTrack implementations transiently report STATE_UNINITIALIZED after
            // native setup has succeeded. The blocking write is the authoritative capability
            // operation and still returns a negative error code for a genuinely unusable track.
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "Android tone AudioTrack accepted $written samples" }
            track.play()
            Thread.sleep((request.durationSeconds * 1_000f).toLong().coerceAtLeast(1L))
        } finally {
            track.release()
        }
    }
}
