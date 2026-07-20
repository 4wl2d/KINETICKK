// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.api

enum class ProfileProviderId {
    PLATFORM_LOCAL,
}

enum class ProfileLoadRejection {
    PAYLOAD_TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_PAYLOAD,
    MALFORMED_LEGACY_MATTER,
    BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
    BOOTSTRAP_NON_FINITE_NUMBER,
}

enum class ProfileResourceFailure {
    PROVIDER_READ_FAILED,
    ENCODING_FAILED,
    PAYLOAD_LIMIT_EXCEEDED,
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
}

sealed interface ProfileLoadResult {
    data class Loaded(val profile: PlayerProfile) : ProfileLoadResult
    data object NotFound : ProfileLoadResult
    data class Rejected(val reason: ProfileLoadRejection) : ProfileLoadResult
    data class OutcomeUnknown(val reason: ProfileResourceFailure) : ProfileLoadResult
}

sealed interface ProfilePersistResult {
    data object Persisted : ProfilePersistResult
    data class OutcomeUnknown(val reason: ProfileResourceFailure) : ProfilePersistResult
}

/** Untrusted platform persistence boundary. Implementations must never expose provider exceptions. */
interface ProfileResource {
    val providerId: ProfileProviderId
    fun load(): ProfileLoadResult
    fun persist(profile: PlayerProfile): ProfilePersistResult
}
