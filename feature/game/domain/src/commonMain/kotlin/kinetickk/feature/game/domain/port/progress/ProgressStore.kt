// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.port.progress

import kinetickk.feature.game.domain.model.StoredProgress

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

/** Persistence port; platform storage details live in the data module. */
interface ProgressStore {
    val providerId: ProgressProviderId
    fun load(): ProgressLoadResult
    fun persist(progress: StoredProgress): ProgressPersistResult
}
