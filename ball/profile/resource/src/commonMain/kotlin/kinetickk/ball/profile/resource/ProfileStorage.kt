// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileLoadRejection
import kinetickk.ball.profile.api.ProfileLoadResult
import kinetickk.ball.profile.api.ProfilePersistResult
import kinetickk.ball.profile.api.ProfileProviderId
import kinetickk.ball.profile.api.ProfileResource
import kinetickk.ball.profile.api.ProfileResourceFailure

internal object ProfileStorageKeys {
    const val DESKTOP_NODE: String = "kinetickk/progression"
    const val DESKTOP_PRIMARY: String = "progress_v2"
    const val WEB_PRIMARY: String = "kinetickk_progress_v2"
    const val LEGACY_MATTER: String = "kinetickk_matter"
}

expect fun createPlatformProfileResource(policy: ProfilePolicySnapshot): ProfileResource

internal class FixedKeyProfileResource(
    private val policy: ProfilePolicySnapshot,
    private val readProfilePayload: () -> String?,
    private val readLegacyMatter: () -> String?,
    private val writeProfilePayload: (String) -> Unit,
    private val writeLegacyMatter: (Int) -> Unit,
) : ProfileResource {
    override val providerId: ProfileProviderId = ProfileProviderId.PLATFORM_LOCAL

    override fun load(): ProfileLoadResult {
        val profilePayload = try {
            readProfilePayload()
        } catch (_: Throwable) {
            return ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED)
        }
        if (profilePayload != null) return decodeProfilePayload(profilePayload, policy)

        val legacyMatter = try {
            readLegacyMatter()
        } catch (_: Throwable) {
            return ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED)
        } ?: return ProfileLoadResult.NotFound

        return decodeLegacyMatter(legacyMatter)
    }

    override fun persist(profile: PlayerProfile): ProfilePersistResult {
        val encoded = try {
            ProfileCodec.encode(profile, policy)
        } catch (_: Throwable) {
            return ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.ENCODING_FAILED)
        }
        if (encoded.utf8Validation() != Utf8Validation.Accepted) {
            return ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.PAYLOAD_LIMIT_EXCEEDED)
        }

        return try {
            writeProfilePayload(encoded)
            writeLegacyMatter(profile.economy.matter.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            ProfilePersistResult.Persisted
        } catch (_: Throwable) {
            ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED)
        }
    }
}

internal fun decodeProfilePayload(
    payload: String,
    policy: ProfilePolicySnapshot,
): ProfileLoadResult = when (payload.utf8Validation()) {
    Utf8Validation.TooLarge -> ProfileLoadResult.Rejected(ProfileLoadRejection.PAYLOAD_TOO_LARGE)
    Utf8Validation.Invalid -> ProfileLoadResult.Rejected(ProfileLoadRejection.INVALID_UTF8)
    Utf8Validation.Accepted -> ProfileCodec.decode(payload, policy)
        ?.let(ProfileLoadResult::Loaded)
        ?: ProfileLoadResult.Rejected(ProfileLoadRejection.MALFORMED_PAYLOAD)
}

private fun decodeLegacyMatter(payload: String): ProfileLoadResult = when (payload.utf8Validation()) {
    Utf8Validation.TooLarge -> ProfileLoadResult.Rejected(ProfileLoadRejection.PAYLOAD_TOO_LARGE)
    Utf8Validation.Invalid -> ProfileLoadResult.Rejected(ProfileLoadRejection.INVALID_UTF8)
    Utf8Validation.Accepted -> payload.toIntOrNull()
        ?.coerceAtLeast(0)
        ?.toLong()
        ?.let { matter ->
            ProfileLoadResult.Loaded(
                PlayerProfile(economy = PlayerEconomy(matter = matter, lifetimeMatter = matter)),
            )
        }
        ?: ProfileLoadResult.Rejected(ProfileLoadRejection.MALFORMED_LEGACY_MATTER)
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
        if (byteCount > MAX_PROFILE_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge
        byteCount += encodedBytes
        index += 1
    }
    return Utf8Validation.Accepted
}

const val MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536
