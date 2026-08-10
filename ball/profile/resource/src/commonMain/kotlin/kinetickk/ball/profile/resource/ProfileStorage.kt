// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeRejection
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult

internal object ProfileStorageKeys {
    const val DESKTOP_PROFILE_NODE: String = "kinetickk/profile"
    const val DESKTOP_SNAPSHOT_V4: String = "snapshot_v4"
    const val DESKTOP_LEGACY_NODE: String = "kinetickk/progression"
    const val DESKTOP_LEGACY_PROGRESS_V2: String = "progress_v2"
    const val DESKTOP_LEGACY_MATTER: String = "kinetickk_matter"

    const val WEB_SNAPSHOT_V4: String = "kinetickk_profile_v4"
    const val WEB_LEGACY_PROGRESS_V2: String = "kinetickk_progress_v2"
    const val WEB_LEGACY_MATTER: String = "kinetickk_matter"
}

/** Minimal synchronous port exposed to the Profile acceptor. */
interface ProfileResource {
    fun readBootstrap(): ProfileBootstrapResourceResult

    fun writeV4(snapshot: ProfileV4Snapshot): ProfileV4WriteResult

    fun purgeLegacy(): ProfileLegacyPurgeResult
}

expect fun createPlatformProfileResource(): ProfileResource

/** Exact-key capability exposed to common code; broad clear/remove-node operations are absent. */
internal interface ProfileStorageProvider {
    fun readV4(): String?

    fun writeV4(payload: String)

    fun readLegacyProgressV2(): String?

    fun readLegacyMatter(): String?

    fun removeLegacyProgressV2()

    fun removeLegacyMatter()
}

internal class FixedKeyProfileResource(
    private val provider: ProfileStorageProvider,
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
            return ProfileBootstrapResourceResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_READ_FAILED,
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
                    ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                )
            }
        } catch (_: Throwable) {
            ProfileV4WriteResult.OutcomeUnknown(
                ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            )
        }
    }

    override fun purgeLegacy(): ProfileLegacyPurgeResult {
        val guardPayload = try {
            provider.readV4()
        } catch (_: Throwable) {
            return ProfileLegacyPurgeResult.OutcomeUnknown(
                remaining = ProfileLegacyKeys(progressV2 = false, matter = false),
                unknown = ProfileLegacyKeys(progressV2 = true, matter = true),
                reason = ProfileResourceFailure.PROVIDER_READ_FAILED,
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
                reason = ProfileResourceFailure.PROVIDER_PURGE_MAY_HAVE_EXECUTED,
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
