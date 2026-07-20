// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.ProfileLoadRejection
import kinetickk.core.profile.api.ProfileLoadResult
import kinetickk.core.profile.api.ProfilePersistResult
import kinetickk.core.profile.api.ProfileProviderId
import kinetickk.core.profile.api.ProfileResource
import kinetickk.core.profile.api.ProfileResourceFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ProfileStorageTest {
    @Test
    fun profileSlicesDefensivelyOwnImmutableCollectionPayloads() {
        val weapons = mutableSetOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR)
        val levels = mutableListOf(1, 2, 3)
        val discoveries = mutableSetOf(4, 5)
        val profile = PlayerProfile(
            loadout = PlayerLoadout(unlockedWeapons = weapons),
            labProgress = LabProgress(levels),
            collection = PlayerCollection(discoveries),
        )

        weapons.clear()
        levels.clear()
        discoveries.clear()

        assertEquals(setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR), profile.loadout.unlockedWeapons)
        assertEquals(listOf(1, 2, 3), profile.labProgress.ranks)
        assertEquals(setOf(4, 5), profile.collection.discoveredItemIds)
        assertFalse((profile.loadout.unlockedWeapons as Any) is MutableSet<*>)
        assertFalse((profile.labProgress.ranks as Any) is MutableList<*>)
        assertFalse((profile.collection.discoveredItemIds as Any) is MutableSet<*>)
    }

    @Test
    fun loadsValidatedProfileWithFixedProviderIdentity() {
        val expected = PlayerProfile(economy = PlayerEconomy(42L, 99L))
        val resource = fakeResource(profilePayload = { ProfileCodec.encode(expected) })

        assertEquals(ProfileProviderId.PLATFORM_LOCAL, resource.providerId)
        assertEquals(ProfileLoadResult.Loaded(expected), resource.load())
    }

    @Test
    fun absentProfileFallsBackToLegacyMatter() {
        val resource = fakeResource(profilePayload = { null }, legacyMatter = { "37" })

        assertEquals(
            ProfileLoadResult.Loaded(PlayerProfile(economy = PlayerEconomy(37L, 37L))),
            resource.load(),
        )
    }

    @Test
    fun missingKeysAreNotFound() {
        assertEquals(ProfileLoadResult.NotFound, fakeResource().load())
    }

    @Test
    fun rawUtf8LimitAndInvalidSurrogatesAreRejectedBeforeDecode() {
        val atLimit = decodeProfilePayload("x".repeat(MAX_PROFILE_PAYLOAD_BYTES))
        val overLimit = decodeProfilePayload("x".repeat(MAX_PROFILE_PAYLOAD_BYTES + 1))
        val invalidUtf8 = decodeProfilePayload("\uD800")

        assertEquals(ProfileLoadResult.Rejected(ProfileLoadRejection.MALFORMED_PAYLOAD), atLimit)
        assertEquals(ProfileLoadResult.Rejected(ProfileLoadRejection.PAYLOAD_TOO_LARGE), overLimit)
        assertEquals(ProfileLoadResult.Rejected(ProfileLoadRejection.INVALID_UTF8), invalidUtf8)
    }

    @Test
    fun malformedPrimaryPayloadDoesNotFallBackToLegacyAuthority() {
        val resource = fakeResource(profilePayload = { "not-a-profile-snapshot" }, legacyMatter = { "37" })

        assertEquals(
            ProfileLoadResult.Rejected(ProfileLoadRejection.MALFORMED_PAYLOAD),
            resource.load(),
        )
    }

    @Test
    fun persistsEncodedProfileAndLegacyMatter() {
        var persistedProfile: String? = null
        var persistedMatter: Int? = null
        val expected = PlayerProfile(economy = PlayerEconomy(71L, 80L))
        val resource = fakeResource(
            writeProfile = { persistedProfile = it },
            writeMatter = { persistedMatter = it },
        )

        assertEquals(ProfilePersistResult.Persisted, resource.persist(expected))
        assertEquals(expected, ProfileCodec.decode(persistedProfile))
        assertEquals(71, persistedMatter)
    }

    @Test
    fun providerExceptionsAndPartialWritesBecomeTypedUnknownOutcomes() {
        val readFailure = fakeResource(profilePayload = { error("private provider detail") })
        val primaryWriteFailure = fakeResource(writeProfile = { error("private provider detail") })
        var primaryWritten = false
        val legacyWriteFailure = fakeResource(
            writeProfile = { primaryWritten = true },
            writeMatter = { error("private provider detail") },
        )

        assertEquals(
            ProfileLoadResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_READ_FAILED),
            readFailure.load(),
        )
        listOf(primaryWriteFailure, legacyWriteFailure).forEach { resource ->
            assertEquals(
                ProfilePersistResult.OutcomeUnknown(ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED),
                resource.persist(PlayerProfile()),
            )
        }
        assertEquals(true, primaryWritten)
    }

    @Test
    fun oversizedOutboundSnapshotIsRejectedBySafeSink() {
        val result = fakeResource().persist(
            PlayerProfile(collection = PlayerCollection((0..30_000).toSet())),
        )

        val unknown = assertIs<ProfilePersistResult.OutcomeUnknown>(result)
        assertEquals(ProfileResourceFailure.PAYLOAD_LIMIT_EXCEEDED, unknown.reason)
    }

    @Test
    fun historicalStorageKeysAreGolden() {
        assertEquals("kinetickk/progression", ProfileStorageKeys.DESKTOP_NODE)
        assertEquals("progress_v2", ProfileStorageKeys.DESKTOP_PRIMARY)
        assertEquals("kinetickk_progress_v2", ProfileStorageKeys.WEB_PRIMARY)
        assertEquals("kinetickk_matter", ProfileStorageKeys.LEGACY_MATTER)
    }

    private fun fakeResource(
        profilePayload: () -> String? = { null },
        legacyMatter: () -> String? = { null },
        writeProfile: (String) -> Unit = {},
        writeMatter: (Int) -> Unit = {},
    ): ProfileResource = FixedKeyProfileResource(
        readProfilePayload = profilePayload,
        readLegacyMatter = legacyMatter,
        writeProfilePayload = writeProfile,
        writeLegacyMatter = writeMatter,
    )
}
