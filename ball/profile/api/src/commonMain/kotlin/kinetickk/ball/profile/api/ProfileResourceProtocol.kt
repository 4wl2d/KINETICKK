// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.ContentVersion

/** Validated v4 representation. Schema and profile ID are checked by Resource. */
data class ProfileV4Snapshot(
    val contentVersion: ContentVersion,
    val revision: ProfileRevision,
    val legacyResetConfirmed: Boolean,
    val profile: PlayerProfile,
)

/** Presence of the only legacy keys the application is authorized to remove. */
data class ProfileLegacyKeys(
    val progressV2: Boolean,
    val matter: Boolean,
) {
    val isEmpty: Boolean
        get() = !progressV2 && !matter

    val isNotEmpty: Boolean
        get() = !isEmpty

    infix fun union(other: ProfileLegacyKeys): ProfileLegacyKeys = ProfileLegacyKeys(
        progressV2 = progressV2 || other.progressV2,
        matter = matter || other.matter,
    )

    companion object {
        val NONE: ProfileLegacyKeys = ProfileLegacyKeys(progressV2 = false, matter = false)
        val ALL: ProfileLegacyKeys = ProfileLegacyKeys(progressV2 = true, matter = true)
    }
}

enum class ProfileV4Rejection {
    PAYLOAD_TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_JSON,
    NON_CANONICAL_PAYLOAD,
    UNSUPPORTED_SCHEMA_VERSION,
    PROFILE_ID_MISMATCH,
    INVALID_DECIMAL,
    INVALID_STABLE_ID,
    INVALID_ORDER_OR_DUPLICATE,
    VALUE_OUT_OF_RANGE,
    INCONSISTENT_PROFILE,
}

enum class ProfileResourceFailure {
    PROVIDER_READ_FAILED,
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
    PROVIDER_PURGE_MAY_HAVE_EXECUTED,
}

sealed interface ProfileBootstrapResourceResult {
    data class Observed(
        val snapshot: ProfileV4Snapshot?,
        val legacyKeys: ProfileLegacyKeys,
    ) : ProfileBootstrapResourceResult

    data class Rejected(
        val reason: ProfileV4Rejection,
        val legacyKeys: ProfileLegacyKeys,
    ) : ProfileBootstrapResourceResult

    data class OutcomeUnknown(
        val reason: ProfileResourceFailure,
    ) : ProfileBootstrapResourceResult
}

sealed interface ProfileV4WriteResult {
    data class Written(
        val revision: ProfileRevision,
    ) : ProfileV4WriteResult

    data class Rejected(
        val reason: ProfileV4Rejection,
    ) : ProfileV4WriteResult

    data class OutcomeUnknown(
        val reason: ProfileResourceFailure,
    ) : ProfileV4WriteResult
}

enum class ProfileLegacyPurgeRejection {
    RESET_NOT_CONFIRMED,
}

sealed interface ProfileLegacyPurgeResult {
    data object Purged : ProfileLegacyPurgeResult

    data class Partial(
        val remaining: ProfileLegacyKeys,
    ) : ProfileLegacyPurgeResult

    data class OutcomeUnknown(
        val remaining: ProfileLegacyKeys,
        val unknown: ProfileLegacyKeys,
        val reason: ProfileResourceFailure,
    ) : ProfileLegacyPurgeResult

    data class Rejected(
        val reason: ProfileLegacyPurgeRejection,
    ) : ProfileLegacyPurgeResult
}

/** Correlates a Resource completion with the accepted frame that emitted it. */
data class ProfileEffectRef(
    val sourceRevision: ProfileRevision,
    val ordinal: Int,
) {
    init {
        require(ordinal >= 0) { "Profile effect ordinal must be non-negative" }
    }
}
