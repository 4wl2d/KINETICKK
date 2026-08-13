// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

/** Validated representation of the only profile schema supported before 1.0.0. */
data class ProfileSnapshot(
    val revision: ProfileRevision,
    val profile: PlayerProfile,
)

enum class ProfileSnapshotRejection {
    PAYLOAD_TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_JSON,
    NON_CANONICAL_PAYLOAD,
    INVALID_DECIMAL,
    INVALID_STABLE_ID,
    INVALID_ORDER_OR_DUPLICATE,
    VALUE_OUT_OF_RANGE,
    INCONSISTENT_PROFILE,
}

enum class ProfileReadFailure {
    PROVIDER_READ_FAILED,
}

enum class ProfileWriteOutcomeUnknownReason {
    PROVIDER_WRITE_MAY_HAVE_EXECUTED,
}

enum class ProfileWriteFailure {
    PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
}

sealed interface ProfileSnapshotReadResult {
    data class Observed(
        val snapshot: ProfileSnapshot?,
    ) : ProfileSnapshotReadResult

    data class Rejected(
        val reason: ProfileSnapshotRejection,
    ) : ProfileSnapshotReadResult

    /** The snapshot read is known not to have mutated provider state. */
    data class ResourceFailure(
        val reason: ProfileReadFailure,
    ) : ProfileSnapshotReadResult
}

sealed interface ProfileWriteResult {
    data class Written(
        val revision: ProfileRevision,
    ) : ProfileWriteResult

    data class Rejected(
        val reason: ProfileSnapshotRejection,
    ) : ProfileWriteResult

    data class ResourceFailure(
        val reason: ProfileWriteFailure,
    ) : ProfileWriteResult

    data class OutcomeUnknown(
        val reason: ProfileWriteOutcomeUnknownReason,
    ) : ProfileWriteResult
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
