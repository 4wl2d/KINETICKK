// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
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
    override fun readV4(): ProfilePersistenceReadResult {
        val node = try {
            profileNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceReadResult.Failed
        } catch (_: IllegalStateException) {
            return ProfilePersistenceReadResult.Failed
        }
        return desktopProfileReadCall(
            exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4,
            loadKeyNames = node::keys,
            loadExactValue = {
                node.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)
            },
        )
    }

    override fun writeV4(payload: String): ProfilePersistenceMutationResult {
        desktopProfilePayloadAdmission(payload.length)?.let { return it }
        val node = try {
            profileNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        } catch (_: IllegalStateException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        }
        return desktopProfileMutationCall(
            mutate = {
                node.put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)
            },
            flush = node::flush,
        )
    }

    override fun readLegacyProgressV2(): ProfilePersistenceReadResult {
        val node = try {
            legacyNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceReadResult.Failed
        } catch (_: IllegalStateException) {
            return ProfilePersistenceReadResult.Failed
        }
        return desktopProfileReadCall(
            exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2,
            loadKeyNames = node::keys,
            loadExactValue = {
                node.get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)
            },
        )
    }

    override fun readLegacyMatter(): ProfilePersistenceReadResult {
        val node = try {
            legacyNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceReadResult.Failed
        } catch (_: IllegalStateException) {
            return ProfilePersistenceReadResult.Failed
        }
        return desktopProfileReadCall(
            exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_MATTER,
            loadKeyNames = node::keys,
            loadExactValue = {
                node.get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)
            },
        )
    }

    override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult {
        val node = try {
            legacyNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        } catch (_: IllegalStateException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        }
        return desktopProfileMutationCall(
            mutate = {
                node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)
            },
            flush = node::flush,
        )
    }

    override fun removeLegacyMatter(): ProfilePersistenceMutationResult {
        val node = try {
            legacyNode()
        } catch (_: SecurityException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        } catch (_: IllegalStateException) {
            return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
        }
        return desktopProfileMutationCall(
            mutate = {
                node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)
            },
            flush = node::flush,
        )
    }
}

internal fun desktopProfileReadCall(
    exactKey: String,
    loadKeyNames: () -> Array<String>,
    loadExactValue: () -> String?,
): ProfilePersistenceReadResult {
    val storedKeys = try {
        loadKeyNames()
    } catch (_: BackingStoreException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: SecurityException) {
        return ProfilePersistenceReadResult.Failed
    } catch (_: IllegalStateException) {
        return ProfilePersistenceReadResult.Failed
    }
    desktopPreferenceKeyCountAdmission(storedKeys.size)?.let { return it }
    val keyIsPresent = storedKeys.any { storedKey -> storedKey == exactKey }
    if (!keyIsPresent) {
        return ProfilePersistenceReadResult.Observed(null)
    }
    val payload = try {
        loadExactValue()
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

internal fun desktopProfileMutationCall(
    mutate: () -> Unit,
    flush: () -> Unit,
): ProfilePersistenceMutationResult {
    try {
        mutate()
    } catch (_: IllegalStateException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    } catch (_: IllegalArgumentException) {
        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    }
    return try {
        flush()
        ProfilePersistenceMutationResult.COMPLETED
    } catch (_: BackingStoreException) {
        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
    }
}

internal const val MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE: Int = 64

internal fun desktopPreferenceKeyCountAdmission(keyCount: Int): ProfilePersistenceReadResult? {
    require(keyCount >= 0) { "Desktop preference key count must be non-negative" }
    return if (keyCount <= MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE) {
        null
    } else {
        ProfilePersistenceReadResult.Failed
    }
}

/** Pure pre-provider admission for the Preferences value-size boundary. */
internal fun desktopProfilePayloadAdmission(valueLength: Int): ProfilePersistenceMutationResult? {
    require(valueLength >= 0) { "Profile persistence payload length must be non-negative" }
    return if (valueLength <= Preferences.MAX_VALUE_LENGTH) {
        null
    } else {
        ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
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
        executor.execute {
            synthesize(request)
        }
    }

    override fun close() {
        executor.shutdownNow()
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
