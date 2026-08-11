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
    fun readV4(): String?

    fun writeV4(payload: String)

    fun readLegacyProgressV2(): String?

    fun readLegacyMatter(): String?

    fun removeLegacyProgressV2()

    fun removeLegacyMatter()
}

fun createProfileResource(
    persistence: ExactProfilePersistence,
): ProfileResource = FixedKeyProfileResource(persistence)

private class FixedKeyProfileResource(
    private val provider: ExactProfilePersistence,
) : ProfileResource {
    override fun readBootstrap(): ProfileBootstrapResourceResult {
        val payload: String?
        val legacyKeys: ProfileLegacyKeys
        try {
            payload = provider.readV4()
            legacyKeys = ProfileLegacyKeys(
                progressV2 = provider.readLegacyProgressV2() != null,
                matter = provider.readLegacyMatter() != null,
            )
        } catch (_: Throwable) {
            return ProfileBootstrapResourceResult.ResourceFailure(
                ProfileReadFailure.PROVIDER_READ_FAILED,
            )
        }

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

        return try {
            provider.writeV4(payload)
            if (provider.readV4() == payload) {
                ProfileV4WriteResult.Written(snapshot.revision)
            } else {
                ProfileV4WriteResult.OutcomeUnknown(
                    ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                )
            }
        } catch (_: Throwable) {
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileWriteOutcomeUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            )
        }
    }

    override fun purgeLegacy(): ProfileLegacyPurgeResult {
        val guardPayload = try {
            provider.readV4()
        } catch (_: Throwable) {
            return ProfileLegacyPurgeResult.ResourceFailure(
                ProfileReadFailure.PROVIDER_READ_FAILED,
            )
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

        val progressV2 = purgeKey(
            remove = provider::removeLegacyProgressV2,
            read = provider::readLegacyProgressV2,
        )
        val matter = purgeKey(
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
        remove: () -> Unit,
        read: () -> String?,
    ): PurgeKeyObservation {
        try {
            remove()
        } catch (_: Throwable) {
            return PurgeKeyObservation.Unknown
        }
        return try {
            if (read() == null) PurgeKeyObservation.Absent else PurgeKeyObservation.Present
        } catch (_: Throwable) {
            PurgeKeyObservation.Unknown
        }
    }
}

private enum class PurgeKeyObservation { Absent, Present, Unknown }
