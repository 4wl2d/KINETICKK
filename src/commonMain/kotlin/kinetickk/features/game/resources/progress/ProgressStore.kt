// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.resources.progress

import kinetickk.features.game.nucleus.StoredProgress

/** Fixed provenance for the one local progress capability bound by Assembly. */
enum class ProgressProviderId {
    PLATFORM_LOCAL,
}

enum class ProgressLoadRejection {
    PAYLOAD_TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_PAYLOAD,
    MALFORMED_LEGACY_MATTER,
    BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
    BOOTSTRAP_NON_FINITE_NUMBER,
}

enum class ProgressResourceFailure {
    PROVIDER_READ_FAILED,
    ENCODING_FAILED,
    PAYLOAD_LIMIT_EXCEEDED,
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
}

sealed interface ProgressLoadResult {
    data class Loaded(val progress: StoredProgress) : ProgressLoadResult
    data object NotFound : ProgressLoadResult
    data class Rejected(val reason: ProgressLoadRejection) : ProgressLoadResult
    data class OutcomeUnknown(val reason: ProgressResourceFailure) : ProgressLoadResult
}

sealed interface ProgressPersistResult {
    data object Persisted : ProgressPersistResult
    data class OutcomeUnknown(val reason: ProgressResourceFailure) : ProgressPersistResult
}

interface ProgressStore {
    val providerId: ProgressProviderId
    fun load(): ProgressLoadResult
    fun persist(progress: StoredProgress): ProgressPersistResult
}

expect fun createPlatformProgressStore(): ProgressStore

internal class FixedKeyProgressStore(
    private val readProgressPayload: () -> String?,
    private val readLegacyMatter: () -> String?,
    private val writeProgressPayload: (String) -> Unit,
    private val writeLegacyMatter: (Int) -> Unit,
) : ProgressStore {
    override val providerId: ProgressProviderId = ProgressProviderId.PLATFORM_LOCAL

    override fun load(): ProgressLoadResult {
        val progressPayload = try {
            readProgressPayload()
        } catch (_: Throwable) {
            return ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED)
        }
        if (progressPayload != null) return decodeProgressPayload(progressPayload)

        val legacyMatter = try {
            readLegacyMatter()
        } catch (_: Throwable) {
            return ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED)
        } ?: return ProgressLoadResult.NotFound

        return decodeLegacyMatter(legacyMatter)
    }

    override fun persist(progress: StoredProgress): ProgressPersistResult {
        val encoded = try {
            ProgressCodec.encode(progress)
        } catch (_: Throwable) {
            return ProgressPersistResult.OutcomeUnknown(ProgressResourceFailure.ENCODING_FAILED)
        }
        if (encoded.utf8Validation() != Utf8Validation.Accepted) {
            return ProgressPersistResult.OutcomeUnknown(ProgressResourceFailure.PAYLOAD_LIMIT_EXCEEDED)
        }

        return try {
            writeProgressPayload(encoded)
            writeLegacyMatter(progress.matter.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            ProgressPersistResult.Persisted
        } catch (_: Throwable) {
            ProgressPersistResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED)
        }
    }
}

internal fun decodeProgressPayload(payload: String): ProgressLoadResult = when (payload.utf8Validation()) {
    Utf8Validation.TooLarge -> ProgressLoadResult.Rejected(ProgressLoadRejection.PAYLOAD_TOO_LARGE)
    Utf8Validation.Invalid -> ProgressLoadResult.Rejected(ProgressLoadRejection.INVALID_UTF8)
    Utf8Validation.Accepted -> ProgressCodec.decode(payload)
        ?.let(ProgressLoadResult::Loaded)
        ?: ProgressLoadResult.Rejected(ProgressLoadRejection.MALFORMED_PAYLOAD)
}

private fun decodeLegacyMatter(payload: String): ProgressLoadResult = when (payload.utf8Validation()) {
    Utf8Validation.TooLarge -> ProgressLoadResult.Rejected(ProgressLoadRejection.PAYLOAD_TOO_LARGE)
    Utf8Validation.Invalid -> ProgressLoadResult.Rejected(ProgressLoadRejection.INVALID_UTF8)
    Utf8Validation.Accepted -> payload.toIntOrNull()
        ?.coerceAtLeast(0)
        ?.toLong()
        ?.let { ProgressLoadResult.Loaded(StoredProgress(matter = it, lifetimeMatter = it)) }
        ?: ProgressLoadResult.Rejected(ProgressLoadRejection.MALFORMED_LEGACY_MATTER)
}

private enum class Utf8Validation { Accepted, TooLarge, Invalid }

private fun String.utf8Validation(): Utf8Validation {
    var byteCount = 0
    var index = 0
    while (index < length) {
        val char = this[index]
        val encodedBytes = when {
            char.code <= 0x7F -> 1
            char.code <= 0x7FF -> 2
            char.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return Utf8Validation.Invalid
                index += 1
                4
            }
            char.isLowSurrogate() -> return Utf8Validation.Invalid
            else -> 3
        }
        if (byteCount > MAX_PROGRESS_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge
        byteCount += encodedBytes
        index += 1
    }
    return Utf8Validation.Accepted
}

const val MAX_PROGRESS_PAYLOAD_BYTES: Int = 65_536
