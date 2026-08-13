// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult

/** Minimal synchronous port exposed to the Profile acceptor. */
interface ProfileResource {
    fun readSnapshot(): ProfileSnapshotReadResult

    fun writeSnapshot(snapshot: ProfileSnapshot): ProfileWriteResult
}

/**
 * Exact persistence authority accepted by the Profile Resource.
 *
 * Platform stores, roots, nodes, key selection, bulk clearing, and global acquisition are
 * deliberately absent. The app platform broker is the only place that may hold those powers.
 */
interface ExactProfilePersistence {
    fun readSnapshot(): ProfileProviderReadResult

    fun writeSnapshot(payload: String): ProfileProviderMutationResult
}

/** Closed evidence returned by the exact persistence provider for a non-mutating read. */
sealed interface ProfileProviderReadResult {
    data class Observed(val payload: String?) : ProfileProviderReadResult

    data object Failed : ProfileProviderReadResult
}

/** Closed evidence returned by the exact persistence provider for a possibly mutating call. */
enum class ProfileProviderMutationResult {
    COMPLETED,
    FAILED_BEFORE_EXECUTION,
    POSSIBLE_EXECUTION,
}

fun createProfileResource(
    persistence: ExactProfilePersistence,
): ProfileResource = FixedKeyProfileResource(persistence)

private class FixedKeyProfileResource(
    private val provider: ExactProfilePersistence,
) : ProfileResource {
    override fun readSnapshot(): ProfileSnapshotReadResult {
        val payload = when (val result = provider.readSnapshot()) {
            is ProfileProviderReadResult.Observed -> result.payload
            ProfileProviderReadResult.Failed -> return readFailure()
        } ?: return ProfileSnapshotReadResult.Observed(snapshot = null)

        return when (val decoded = ProfileCodec.decode(payload)) {
            is ProfileDecodeResult.Decoded -> ProfileSnapshotReadResult.Observed(decoded.snapshot)
            is ProfileDecodeResult.Rejected -> ProfileSnapshotReadResult.Rejected(decoded.reason)
        }
    }

    override fun writeSnapshot(snapshot: ProfileSnapshot): ProfileWriteResult {
        val payload = when (val encoded = ProfileCodec.encode(snapshot)) {
            is ProfileEncodeResult.Encoded -> encoded.payload
            is ProfileEncodeResult.Rejected -> return ProfileWriteResult.Rejected(encoded.reason)
        }

        when (provider.writeSnapshot(payload)) {
            ProfileProviderMutationResult.COMPLETED -> Unit
            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION -> {
                return ProfileWriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                )
            }
            ProfileProviderMutationResult.POSSIBLE_EXECUTION -> return writeOutcomeUnknown()
        }
        return when (val readBack = provider.readSnapshot()) {
            is ProfileProviderReadResult.Observed -> {
                if (readBack.payload == payload) {
                    ProfileWriteResult.Written(snapshot.revision)
                } else {
                    writeOutcomeUnknown()
                }
            }
            ProfileProviderReadResult.Failed -> writeOutcomeUnknown()
        }
    }

    private fun readFailure(): ProfileSnapshotReadResult.ResourceFailure =
        ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED)

    private fun writeOutcomeUnknown(): ProfileWriteResult.OutcomeUnknown =
        ProfileWriteResult.OutcomeUnknown(
            ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
        )
}
