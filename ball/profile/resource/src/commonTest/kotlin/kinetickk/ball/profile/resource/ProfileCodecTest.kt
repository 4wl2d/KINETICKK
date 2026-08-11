// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileCodecTest {
    @Test
    fun defaultProfileHasCanonicalGoldenAndRoundTrips() {
        val snapshot = testV4Snapshot()
        val encoded = requireEncoded(snapshot)

        assertEquals(DEFAULT_V4_GOLDEN, encoded)
        assertEquals(
            ProfileV4DecodeResult.Decoded(snapshot),
            ProfileV4Codec.decode(encoded),
        )
    }

    @Test
    fun schemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss() {
        val profile = PlayerProfile(
            preferences = PlayerPreferences(
                soundEnabled = false,
                musicEnabled = true,
                masterVolume = 0.5f,
                simulationSpeed = 1.6f,
                textScale = 1.4f,
                screenShake = false,
                particleDensity = ParticleDensity.HIGH,
                damageNumbers = false,
                damageNumberSize = DamageNumberSize.LARGE,
                damageNumberFormat = DamageNumberFormat.FULL,
                damageNumberTierThreshold = 2_500,
            ),
            economy = PlayerEconomy(9_876_543_210L, 12_345_678_901L),
            loadout = PlayerLoadout(
                coreShape = CoreShape.SHARD,
                selectedWeapon = WeaponId.SINGULARITY_SPEAR,
                unlockedWeapons = WeaponId.entries.reversed().toSet(),
            ),
            labProgress = LabProgress(MetaUpgradeId.entries.map { 1 }),
            collection = PlayerCollection((0 until ContentBounds.MAX_ITEMS).reversed().toSet()),
            rebirthProgress = RebirthProgress(7, 6),
        )
        val snapshot = testV4Snapshot(profile, revision = 42L, legacyResetConfirmed = true)

        val encoded = requireEncoded(snapshot)
        val decoded = assertIs<ProfileV4DecodeResult.Decoded>(
            ProfileV4Codec.decode(encoded),
        )

        assertEquals(snapshot, decoded.snapshot)
        assertEquals(ContentBounds.MAX_WEAPONS, decoded.snapshot.profile.loadout.unlockedWeapons.size)
        assertEquals(ContentBounds.MAX_META_UPGRADES, decoded.snapshot.profile.labProgress.ranks.size)
        assertEquals(ContentBounds.MAX_ITEMS, decoded.snapshot.profile.collection.discoveredItemIds.size)
        assertEquals(
            WeaponId.entries.map(WeaponId::name).sorted(),
            encoded.extractStringArray("unlockedWeaponIds"),
        )
        assertEquals((0 until ContentBounds.MAX_ITEMS).toList(), encoded.extractIntArray("discoveredItemIds"))
        assertEquals(
            MetaUpgradeId.entries.map { it.name }.sorted(),
            RANK_ID.findAll(encoded).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun contentVersionIsRetainedForNucleusCompatibilityPolicy() {
        val incompatible = testV4Snapshot(contentVersion = ContentVersion("future-content"))

        val decoded = assertIs<ProfileV4DecodeResult.Decoded>(
            ProfileV4Codec.decode(requireEncoded(incompatible)),
        )

        assertEquals(ContentVersion("future-content"), decoded.snapshot.contentVersion)
    }

    @Test
    fun unknownMissingAndNonCanonicalJsonAreRejectedStrictly() {
        val unknown = DEFAULT_V4_GOLDEN.replace(
            "\"schemaVersion\":4,",
            "\"schemaVersion\":4,\"unknown\":0,",
        )
        val missing = DEFAULT_V4_GOLDEN.replace("\"profileId\":\"local-player\",", "")
        val missingNested = DEFAULT_V4_GOLDEN.replace("\"soundEnabled\":true,", "")
        val reordered = DEFAULT_V4_GOLDEN.replace(
            "\"schemaVersion\":4,\"profileId\":\"local-player\"",
            "\"profileId\":\"local-player\",\"schemaVersion\":4",
        )

        listOf(unknown, missing, missingNested).forEach { payload ->
            assertEquals(ProfileV4Rejection.MALFORMED_JSON, decodeRejection(payload))
        }
        listOf(reordered, "$DEFAULT_V4_GOLDEN\n").forEach { payload ->
            assertEquals(ProfileV4Rejection.NON_CANONICAL_PAYLOAD, decodeRejection(payload))
        }
    }

    @Test
    fun schemaAndProfileIdentityMustMatchExactly() {
        val wrongSchema = DEFAULT_V4_GOLDEN.replace("\"schemaVersion\":4", "\"schemaVersion\":3")
        val wrongProfile = DEFAULT_V4_GOLDEN.replace("\"local-player\"", "\"another-player\"")

        assertEquals(ProfileV4Rejection.UNSUPPORTED_SCHEMA_VERSION, decodeRejection(wrongSchema))
        assertEquals(ProfileV4Rejection.PROFILE_ID_MISMATCH, decodeRejection(wrongProfile))
    }

    @Test
    fun revisionAndEconomyUseCanonicalBoundedDecimalStrings() {
        val maxSnapshot = testV4Snapshot(
            profile = PlayerProfile(economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE)),
            revision = Long.MAX_VALUE,
        )
        assertEquals(
            maxSnapshot,
            assertIs<ProfileV4DecodeResult.Decoded>(
                ProfileV4Codec.decode(requireEncoded(maxSnapshot)),
            ).snapshot,
        )

        listOf("", "00", "01", "+1", "-1", " 1", "1 ", "1.0", "1e1", "9223372036854775808")
            .forEach { invalid ->
                val payload = DEFAULT_V4_GOLDEN.replace("\"revision\":\"0\"", "\"revision\":\"$invalid\"")
                assertEquals(ProfileV4Rejection.INVALID_DECIMAL, decodeRejection(payload), invalid)
            }
        val invalidMatter = DEFAULT_V4_GOLDEN.replace("\"matter\":\"0\"", "\"matter\":\"01\"")
        assertEquals(ProfileV4Rejection.INVALID_DECIMAL, decodeRejection(invalidMatter))
    }

    @Test
    fun stableIdsOrderingAndCrossFieldInvariantsAreStrict() {
        val unknownWeapon = DEFAULT_V4_GOLDEN.replace(
            "\"selectedWeaponId\":\"FLUX_WAKE\"",
            "\"selectedWeaponId\":\"UNKNOWN\"",
        )
        val duplicateWeapon = DEFAULT_V4_GOLDEN.replace(
            "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]",
            "\"unlockedWeaponIds\":[\"FLUX_WAKE\",\"FLUX_WAKE\"]",
        )
        val selectedLocked = DEFAULT_V4_GOLDEN.replace(
            "\"selectedWeaponId\":\"FLUX_WAKE\"",
            "\"selectedWeaponId\":\"MORNINGSTAR\"",
        )
        val policyUnknownDiscovery = DEFAULT_V4_GOLDEN.replace(
            "\"discoveredItemIds\":[]",
            "\"discoveredItemIds\":[400]",
        )
        val invalidEconomy = DEFAULT_V4_GOLDEN
            .replace("\"matter\":\"0\"", "\"matter\":\"2\"")
            .replace("\"lifetimeMatter\":\"0\"", "\"lifetimeMatter\":\"1\"")

        assertEquals(ProfileV4Rejection.INVALID_STABLE_ID, decodeRejection(unknownWeapon))
        assertEquals(ProfileV4Rejection.INVALID_ORDER_OR_DUPLICATE, decodeRejection(duplicateWeapon))
        assertEquals(ProfileV4Rejection.INCONSISTENT_PROFILE, decodeRejection(selectedLocked))
        assertIs<ProfileV4DecodeResult.Decoded>(ProfileV4Codec.decode(policyUnknownDiscovery))
        assertEquals(ProfileV4Rejection.INCONSISTENT_PROFILE, decodeRejection(invalidEconomy))
    }

    @Test
    fun byteLimitAndUtf8AreCheckedBeforeJsonDecode() {
        assertEquals(
            ProfileV4Rejection.MALFORMED_JSON,
            decodeRejection("x".repeat(MAX_PROFILE_PAYLOAD_BYTES)),
        )
        assertEquals(
            ProfileV4Rejection.PAYLOAD_TOO_LARGE,
            decodeRejection("x".repeat(MAX_PROFILE_PAYLOAD_BYTES + 1)),
        )
        assertEquals(ProfileV4Rejection.INVALID_UTF8, decodeRejection("\uD800"))
    }

    @Test
    fun encodedByteLimitAcceptsExactlyNAndRejectsFirstNPlusOne() {
        val basePayload = requireEncoded(testV4Snapshot())
        val baseVersion = "test-content"
        val envelopeBytesWithoutVersion =
            basePayload.encodeToByteArray().size - baseVersion.encodeToByteArray().size
        val exactVersionBytes = MAX_PROFILE_PAYLOAD_BYTES - envelopeBytesWithoutVersion

        val exact = assertIs<ProfileV4EncodeResult.Encoded>(
            ProfileV4Codec.encode(
                testV4Snapshot(contentVersion = ContentVersion("x".repeat(exactVersionBytes))),
            ),
        ).payload

        assertEquals(MAX_PROFILE_PAYLOAD_BYTES, exact.encodeToByteArray().size)
        assertEncodeRejection(
            ProfileV4Rejection.PAYLOAD_TOO_LARGE,
            testV4Snapshot(contentVersion = ContentVersion("x".repeat(exactVersionBytes + 1))),
        )
    }

    @Test
    fun outboundProfileIsValidatedWithoutClampingOrFallback() {
        val invalidRange = testV4Snapshot(
            PlayerProfile(preferences = PlayerPreferences(masterVolume = 2f)),
        )
        val inconsistentLoadout = testV4Snapshot(
            PlayerProfile(
                loadout = PlayerLoadout(
                    selectedWeapon = WeaponId.MORNINGSTAR,
                    unlockedWeapons = setOf(WeaponId.FLUX_WAKE),
                ),
            ),
        )
        val invalidCollection = testV4Snapshot(
            PlayerProfile(collection = PlayerCollection(setOf(-1))),
        )
        val incompleteRanks = testV4Snapshot(
            PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size - 1) { 0 })),
        )
        val oversizedEnvelope = testV4Snapshot(
            contentVersion = ContentVersion("x".repeat(MAX_PROFILE_PAYLOAD_BYTES)),
        )

        assertEncodeRejection(ProfileV4Rejection.VALUE_OUT_OF_RANGE, invalidRange)
        assertEncodeRejection(ProfileV4Rejection.INCONSISTENT_PROFILE, inconsistentLoadout)
        assertEncodeRejection(ProfileV4Rejection.VALUE_OUT_OF_RANGE, invalidCollection)
        assertEncodeRejection(ProfileV4Rejection.INCONSISTENT_PROFILE, incompleteRanks)
        assertEncodeRejection(ProfileV4Rejection.PAYLOAD_TOO_LARGE, oversizedEnvelope)
    }

    @Test
    fun preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers() {
        val exact = PlayerPreferences(
            masterVolume = 1f,
            simulationSpeed = SIMULATION_SPEED_OPTIONS.last(),
            textScale = 1.75f,
            damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        )
        val exactSnapshot = testV4Snapshot(PlayerProfile(preferences = exact))

        assertEquals(
            exactSnapshot,
            assertIs<ProfileV4DecodeResult.Decoded>(
                ProfileV4Codec.decode(requireEncoded(exactSnapshot)),
            ).snapshot,
        )

        val invalidPreferences = listOf(
            exact.copy(masterVolume = Float.fromBits(1f.toBits() + 1)),
            exact.copy(simulationSpeed = Float.fromBits(SIMULATION_SPEED_OPTIONS.last().toBits() + 1)),
            exact.copy(textScale = Float.fromBits(1.75f.toBits() + 1)),
            exact.copy(simulationSpeed = Float.fromBits(1f.toBits() + 1)),
            exact.copy(
                damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.first() + 1,
            ),
        )

        invalidPreferences.forEach { preferences ->
            assertEncodeRejection(
                ProfileV4Rejection.VALUE_OUT_OF_RANGE,
                testV4Snapshot(PlayerProfile(preferences = preferences)),
            )
        }
    }

    @Test
    fun preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers() {
        assertIs<ProfileV4DecodeResult.Decoded>(ProfileV4Codec.decode(DEFAULT_V4_GOLDEN))

        val adjacentSimulationSpeed = DEFAULT_V4_GOLDEN.replace(
            "\"simulationSpeedPercent\":115",
            "\"simulationSpeedPercent\":116",
        )
        val adjacentTierThreshold = DEFAULT_V4_GOLDEN.replace(
            "\"damageNumberTierThreshold\":50",
            "\"damageNumberTierThreshold\":51",
        )

        assertEquals(ProfileV4Rejection.VALUE_OUT_OF_RANGE, decodeRejection(adjacentSimulationSpeed))
        assertEquals(ProfileV4Rejection.VALUE_OUT_OF_RANGE, decodeRejection(adjacentTierThreshold))
    }

    @Test
    fun outboundLabRanksAndDiscoveriesRejectFirstNPlusOne() {
        assertEncodeRejection(
            ProfileV4Rejection.INCONSISTENT_PROFILE,
            testV4Snapshot(
                PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size + 1) { 0 })),
            ),
        )
        assertEncodeRejection(
            ProfileV4Rejection.VALUE_OUT_OF_RANGE,
            testV4Snapshot(
                PlayerProfile(collection = PlayerCollection((0..ContentBounds.MAX_ITEMS).toSet())),
            ),
        )
    }

    @Test
    fun percentageFieldsPreserveTheExistingIntegerQuantization() {
        val snapshot = testV4Snapshot(
            PlayerProfile(
                preferences = PlayerPreferences(
                    masterVolume = 0.555f,
                    simulationSpeed = 1.35f,
                    textScale = 1.424f,
                ),
            ),
        )

        val decoded = assertIs<ProfileV4DecodeResult.Decoded>(
            ProfileV4Codec.decode(requireEncoded(snapshot)),
        ).snapshot

        assertEquals(0.56f, decoded.profile.preferences.masterVolume)
        assertEquals(1.35f, decoded.profile.preferences.simulationSpeed)
        assertEquals(1.42f, decoded.profile.preferences.textScale)
    }

    private fun decodeRejection(payload: String): ProfileV4Rejection =
        assertIs<ProfileV4DecodeResult.Rejected>(
            ProfileV4Codec.decode(payload),
        ).reason

    private fun assertEncodeRejection(
        expected: ProfileV4Rejection,
        snapshot: kinetickk.ball.profile.api.ProfileV4Snapshot,
    ) {
        assertEquals(
            expected,
            assertIs<ProfileV4EncodeResult.Rejected>(
                ProfileV4Codec.encode(snapshot),
            ).reason,
        )
    }
}

private fun String.extractStringArray(field: String): List<String> {
    val body = Regex("\\\"$field\\\":\\[(.*?)]").find(this)?.groupValues?.get(1).orEmpty()
    if (body.isEmpty()) return emptyList()
    return body.split(',').map { it.removeSurrounding("\"") }
}

private fun String.extractIntArray(field: String): List<Int> {
    val body = Regex("\\\"$field\\\":\\[(.*?)]").find(this)?.groupValues?.get(1).orEmpty()
    if (body.isEmpty()) return emptyList()
    return body.split(',').map(String::toInt)
}

private val RANK_ID = Regex("\\{\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"rank\\\":")

private const val DEFAULT_V4_GOLDEN: String =
    "{\"schemaVersion\":4,\"profileId\":\"local-player\",\"contentVersion\":\"test-content\"," +
        "\"revision\":\"0\",\"legacyResetConfirmed\":false,\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":115,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50}," +
        "\"economy\":{\"matter\":\"0\",\"lifetimeMatter\":\"0\"}," +
        "\"loadout\":{\"coreShapeId\":\"ORB\",\"selectedWeaponId\":\"FLUX_WAKE\"," +
        "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]},\"labProgress\":{\"ranks\":[" +
        "{\"id\":\"ARMORY_LICENSE\",\"rank\":0},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":0}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":0}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":0},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0}]}," +
        "\"collection\":{\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":0,\"highestCleared\":-1}}}"
