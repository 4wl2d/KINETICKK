// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteFailure
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.impl.storage.ProfileCodec
import kinetickk.ball.profile.impl.storage.ProfileDecodeResult
import kinetickk.ball.profile.impl.storage.ProfileEncodeResult

/** Minimal synchronous port exposed to the Profile acceptor. */
internal interface ProfileResource {
    fun readSnapshot(): ProfileSnapshotReadResult

    fun writeSnapshot(snapshot: ProfileSnapshot): ProfileWriteResult
}

internal fun createProfileResource(
    persistence: ProfilePersistenceCapability,
): ProfileResource = FixedKeyProfileResource(persistence)

private class FixedKeyProfileResource(
    private val provider: ProfilePersistenceCapability,
) : ProfileResource {
    override fun readSnapshot(): ProfileSnapshotReadResult {
        val payload = when (val result = provider.readSnapshot()) {
            is ProfilePersistenceReadResult.Observed -> result.payload
            ProfilePersistenceReadResult.Failed -> return readFailure()
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
            ProfilePersistenceMutationResult.COMPLETED -> Unit
            ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION -> {
                return ProfileWriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                )
            }
            ProfilePersistenceMutationResult.POSSIBLE_EXECUTION -> return writeOutcomeUnknown()
        }
        return when (val readBack = provider.readSnapshot()) {
            is ProfilePersistenceReadResult.Observed -> {
                if (readBack.payload == payload) {
                    ProfileWriteResult.Written(snapshot.revision)
                } else {
                    writeOutcomeUnknown()
                }
            }
            ProfilePersistenceReadResult.Failed -> writeOutcomeUnknown()
        }
    }

    private fun readFailure(): ProfileSnapshotReadResult.ResourceFailure =
        ProfileSnapshotReadResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED)

    private fun writeOutcomeUnknown(): ProfileWriteResult.OutcomeUnknown =
        ProfileWriteResult.OutcomeUnknown(
            ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
        )
}
