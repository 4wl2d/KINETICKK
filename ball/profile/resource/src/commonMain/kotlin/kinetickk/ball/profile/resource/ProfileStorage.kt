// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeRejection
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePurgeOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
import kinetickk.ball.profile.api.ProfileWriteFailure

/** Minimal synchronous port exposed to the Profile acceptor. */
interface ProfileResource {
    fun readBootstrap(): ProfileBootstrapResourceResult

    fun writeV4(snapshot: ProfileV4Snapshot): ProfileV4WriteResult

    fun purgeLegacy(): ProfileLegacyPurgeResult
}

/**
 * Exact persistence authority accepted by the Profile Resource.
 *
 * Platform stores, roots, nodes, key selection, bulk clearing, and global acquisition are
 * deliberately absent. The app platform broker is the only place that may hold those powers.
 */
interface ExactProfilePersistence {
    fun readV4(): ProfileProviderReadResult

    fun writeV4(payload: String): ProfileProviderMutationResult

    fun readLegacyProgressV2(): ProfileProviderReadResult

    fun readLegacyMatter(): ProfileProviderReadResult

    fun removeLegacyProgressV2(): ProfileProviderMutationResult

    fun removeLegacyMatter(): ProfileProviderMutationResult
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
    override fun readBootstrap(): ProfileBootstrapResourceResult {
        val payload = when (val result = provider.readV4()) {
            is ProfileProviderReadResult.Observed -> result.payload
            ProfileProviderReadResult.Failed -> return bootstrapReadFailure()
        }
        val legacyProgressV2 = when (val result = provider.readLegacyProgressV2()) {
            is ProfileProviderReadResult.Observed -> result.payload
            ProfileProviderReadResult.Failed -> return bootstrapReadFailure()
        }
        val legacyMatter = when (val result = provider.readLegacyMatter()) {
            is ProfileProviderReadResult.Observed -> result.payload
            ProfileProviderReadResult.Failed -> return bootstrapReadFailure()
        }
        val legacyKeys = ProfileLegacyKeys(
            progressV2 = legacyProgressV2 != null,
            matter = legacyMatter != null,
        )

        if (payload == null) {
            return ProfileBootstrapResourceResult.Observed(
                snapshot = null,
                legacyKeys = legacyKeys,
            )
        }
        return when (val decoded = ProfileV4Codec.decode(payload)) {
            is ProfileV4DecodeResult.Decoded -> ProfileBootstrapResourceResult.Observed(
                snapshot = decoded.snapshot,
                legacyKeys = legacyKeys,
            )
            is ProfileV4DecodeResult.Rejected -> ProfileBootstrapResourceResult.Rejected(
                reason = decoded.reason,
                legacyKeys = legacyKeys,
            )
        }
    }

    override fun writeV4(snapshot: ProfileV4Snapshot): ProfileV4WriteResult {
        val payload = when (val encoded = ProfileV4Codec.encode(snapshot)) {
            is ProfileV4EncodeResult.Encoded -> encoded.payload
            is ProfileV4EncodeResult.Rejected -> {
                return ProfileV4WriteResult.Rejected(encoded.reason)
            }
        }

        when (provider.writeV4(payload)) {
            ProfileProviderMutationResult.COMPLETED -> Unit
            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION -> {
                return ProfileV4WriteResult.ResourceFailure(
                    ProfileWriteFailure.PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                )
            }
            ProfileProviderMutationResult.POSSIBLE_EXECUTION -> return writeOutcomeUnknown()
        }
        return when (val readBack = provider.readV4()) {
            is ProfileProviderReadResult.Observed -> {
                if (readBack.payload == payload) {
                    ProfileV4WriteResult.Written(snapshot.revision)
                } else {
                    writeOutcomeUnknown()
                }
            }
            ProfileProviderReadResult.Failed -> writeOutcomeUnknown()
        }
    }

    override fun purgeLegacy(): ProfileLegacyPurgeResult {
        val guardPayload = when (val result = provider.readV4()) {
            is ProfileProviderReadResult.Observed -> result.payload
            ProfileProviderReadResult.Failed -> return purgeReadFailure()
        }
        val guard = guardPayload?.let(ProfileV4Codec::decode)
        if (
            guard !is ProfileV4DecodeResult.Decoded ||
            !guard.snapshot.legacyResetConfirmed
        ) {
            return ProfileLegacyPurgeResult.Rejected(
                ProfileLegacyPurgeRejection.RESET_NOT_CONFIRMED,
            )
        }

        val progressV2WasPresent = when (val result = provider.readLegacyProgressV2()) {
            is ProfileProviderReadResult.Observed -> result.payload != null
            ProfileProviderReadResult.Failed -> return purgeReadFailure()
        }
        val matterWasPresent = when (val result = provider.readLegacyMatter()) {
            is ProfileProviderReadResult.Observed -> result.payload != null
            ProfileProviderReadResult.Failed -> return purgeReadFailure()
        }

        val progressV2 = purgeKey(
            wasPresent = progressV2WasPresent,
            remove = provider::removeLegacyProgressV2,
            read = provider::readLegacyProgressV2,
        )
        val matter = purgeKey(
            wasPresent = matterWasPresent,
            remove = provider::removeLegacyMatter,
            read = provider::readLegacyMatter,
        )
        val remaining = ProfileLegacyKeys(
            progressV2 = progressV2 == PurgeKeyObservation.Present,
            matter = matter == PurgeKeyObservation.Present,
        )
        val unknown = ProfileLegacyKeys(
            progressV2 = progressV2 == PurgeKeyObservation.Unknown,
            matter = matter == PurgeKeyObservation.Unknown,
        )
        return when {
            unknown.progressV2 || unknown.matter -> ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = remaining,
                unknown = unknown,
                reason = ProfilePurgeOutcomeUnknownReason.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
            )
            remaining.progressV2 || remaining.matter -> ProfileLegacyPurgeResult.Partial(remaining)
            else -> ProfileLegacyPurgeResult.Purged
        }
    }

    private fun purgeKey(
        wasPresent: Boolean,
        remove: () -> ProfileProviderMutationResult,
        read: () -> ProfileProviderReadResult,
    ): PurgeKeyObservation {
        if (!wasPresent) return PurgeKeyObservation.Absent
        when (remove()) {
            ProfileProviderMutationResult.COMPLETED -> Unit
            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION -> return PurgeKeyObservation.Present
            ProfileProviderMutationResult.POSSIBLE_EXECUTION -> return PurgeKeyObservation.Unknown
        }
        return when (val result = read()) {
            is ProfileProviderReadResult.Observed -> {
                if (result.payload == null) PurgeKeyObservation.Absent else PurgeKeyObservation.Present
            }
            ProfileProviderReadResult.Failed -> PurgeKeyObservation.Unknown
        }
    }

    private fun bootstrapReadFailure(): ProfileBootstrapResourceResult.ResourceFailure =
        ProfileBootstrapResourceResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED)

    private fun purgeReadFailure(): ProfileLegacyPurgeResult.ResourceFailure =
        ProfileLegacyPurgeResult.ResourceFailure(ProfileReadFailure.PROVIDER_READ_FAILED)

    private fun writeOutcomeUnknown(): ProfileV4WriteResult.OutcomeUnknown =
        ProfileV4WriteResult.OutcomeUnknown(
            ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
        )

}

private enum class PurgeKeyObservation { Absent, Present, Unknown }
