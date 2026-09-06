// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

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
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileCodecTest {
    @Test
    fun bothLanguageCodesRoundTripAndUnsupportedCodesAreRejected() {
        assertEquals(listOf("ru", "en"), AppLanguage.entries.map { it.code })
        AppLanguage.entries.forEach { language ->
            val snapshot = testSnapshot(PlayerProfile(preferences = PlayerPreferences(language = language)))
            assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(
                ProfileCodec.decode(requireEncoded(snapshot)),
            ).snapshot)
        }
        listOf("de", "RU", "", "Russian").forEach { invalid ->
            val payload = DEFAULT_GOLDEN.replace("\"languageCode\":\"ru\"", "\"languageCode\":\"$invalid\"")
            assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(payload), invalid)
        }
    }

    @Test
    fun profilesPredatingLanguageSelectionKeepProgressAndDefaultToRussian() {
        val profile = PlayerProfile(
            economy = PlayerEconomy(matter = 150L, lifetimeMatter = 900L),
            collection = PlayerCollection(setOf(1, 3, 5)),
            rebirthProgress = RebirthProgress(2, 1),
        )
        val snapshot = testSnapshot(profile, revision = 37L)
        val oldPayload = requireEncoded(snapshot).replace(",\"languageCode\":\"ru\"", "")

        assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(oldPayload)).snapshot)
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(" $oldPayload"))
        assertEquals(ProfileSnapshotRejection.MALFORMED_JSON, decodeRejection(
            oldPayload.replace("\"soundEnabled\":true,", ""),
        ))
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(
            DEFAULT_GOLDEN.replace("\"languageCode\":\"ru\"", "\"languageCode\":null"),
        ))
    }

    @Test
    fun defaultProfileHasCanonicalGoldenAndRoundTrips() {
        val snapshot = testSnapshot()
        val encoded = requireEncoded(snapshot)

        assertEquals(DEFAULT_GOLDEN, encoded)
        assertEquals(
            ProfileDecodeResult.Decoded(snapshot),
            ProfileCodec.decode(encoded),
        )
    }

    @Test
    fun currentSchemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss() {
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
        val snapshot = testSnapshot(profile, revision = 42L)

        val encoded = requireEncoded(snapshot)
        val decoded = assertIs<ProfileDecodeResult.Decoded>(
            ProfileCodec.decode(encoded),
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
    fun unknownMissingAndNonCanonicalJsonAreRejectedStrictly() {
        val unknown = DEFAULT_GOLDEN.replace(
            "\"revision\":\"0\",",
            "\"revision\":\"0\",\"unknown\":0,",
        )
        val missing = DEFAULT_GOLDEN.replace("\"revision\":\"0\",", "")
        val missingNested = DEFAULT_GOLDEN.replace("\"soundEnabled\":true,", "")

        listOf(unknown, missing, missingNested).forEach { payload ->
            assertEquals(ProfileSnapshotRejection.MALFORMED_JSON, decodeRejection(payload))
        }
        listOf(" $DEFAULT_GOLDEN", "$DEFAULT_GOLDEN\n").forEach { payload ->
            assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(payload))
        }
    }

    @Test
    fun revisionAndEconomyUseCanonicalBoundedDecimalStrings() {
        val maxSnapshot = testSnapshot(
            profile = PlayerProfile(economy = PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE)),
            revision = Long.MAX_VALUE,
        )
        assertEquals(
            maxSnapshot,
            assertIs<ProfileDecodeResult.Decoded>(
                ProfileCodec.decode(requireEncoded(maxSnapshot)),
            ).snapshot,
        )

        listOf("", "00", "01", "+1", "-1", " 1", "1 ", "1.0", "1e1", "9223372036854775808")
            .forEach { invalid ->
                val payload = DEFAULT_GOLDEN.replace("\"revision\":\"0\"", "\"revision\":\"$invalid\"")
                assertEquals(ProfileSnapshotRejection.INVALID_DECIMAL, decodeRejection(payload), invalid)
            }
        val invalidMatter = DEFAULT_GOLDEN.replace("\"matter\":\"0\"", "\"matter\":\"01\"")
        assertEquals(ProfileSnapshotRejection.INVALID_DECIMAL, decodeRejection(invalidMatter))
    }

    @Test
    fun stableIdsOrderingAndCrossFieldInvariantsAreStrict() {
        val unknownWeapon = DEFAULT_GOLDEN.replace(
            "\"selectedWeaponId\":\"FLUX_WAKE\"",
            "\"selectedWeaponId\":\"UNKNOWN\"",
        )
        val duplicateWeapon = DEFAULT_GOLDEN.replace(
            "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]",
            "\"unlockedWeaponIds\":[\"FLUX_WAKE\",\"FLUX_WAKE\"]",
        )
        val selectedLocked = DEFAULT_GOLDEN.replace(
            "\"selectedWeaponId\":\"FLUX_WAKE\"",
            "\"selectedWeaponId\":\"MORNINGSTAR\"",
        )
        val policyUnknownDiscovery = DEFAULT_GOLDEN.replace(
            "\"discoveredItemIds\":[]",
            "\"discoveredItemIds\":[400]",
        )
        val invalidEconomy = DEFAULT_GOLDEN
            .replace("\"matter\":\"0\"", "\"matter\":\"2\"")
            .replace("\"lifetimeMatter\":\"0\"", "\"lifetimeMatter\":\"1\"")

        assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(unknownWeapon))
        assertEquals(ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE, decodeRejection(duplicateWeapon))
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(selectedLocked))
        assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(policyUnknownDiscovery))
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(invalidEconomy))
    }

    @Test
    fun byteLimitAndUtf8AreCheckedBeforeJsonDecode() {
        assertEquals(
            ProfileSnapshotRejection.MALFORMED_JSON,
            decodeRejection("x".repeat(MAX_PROFILE_PAYLOAD_BYTES)),
        )
        assertEquals(
            ProfileSnapshotRejection.PAYLOAD_TOO_LARGE,
            decodeRejection("x".repeat(MAX_PROFILE_PAYLOAD_BYTES + 1)),
        )
        assertEquals(ProfileSnapshotRejection.INVALID_UTF8, decodeRejection("\uD800"))
    }

    @Test
    fun outboundProfileIsValidatedWithoutClampingOrFallback() {
        val invalidRange = testSnapshot(
            PlayerProfile(preferences = PlayerPreferences(masterVolume = 2f)),
        )
        val inconsistentLoadout = testSnapshot(
            PlayerProfile(
                loadout = PlayerLoadout(
                    selectedWeapon = WeaponId.MORNINGSTAR,
                    unlockedWeapons = setOf(WeaponId.FLUX_WAKE),
                ),
            ),
        )
        val invalidCollection = testSnapshot(
            PlayerProfile(collection = PlayerCollection(setOf(-1))),
        )
        val incompleteRanks = testSnapshot(
            PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size - 1) { 0 })),
        )
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, invalidRange)
        assertEncodeRejection(ProfileSnapshotRejection.INCONSISTENT_PROFILE, inconsistentLoadout)
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, invalidCollection)
        assertEncodeRejection(ProfileSnapshotRejection.INCONSISTENT_PROFILE, incompleteRanks)
    }

    @Test
    fun preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers() {
        val exact = PlayerPreferences(
            masterVolume = 1f,
            simulationSpeed = SIMULATION_SPEED_OPTIONS.last(),
            textScale = 1.75f,
            damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
        )
        val exactSnapshot = testSnapshot(PlayerProfile(preferences = exact))

        assertEquals(
            exactSnapshot,
            assertIs<ProfileDecodeResult.Decoded>(
                ProfileCodec.decode(requireEncoded(exactSnapshot)),
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
                ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
                testSnapshot(PlayerProfile(preferences = preferences)),
            )
        }
    }

    @Test
    fun preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers() {
        assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(DEFAULT_GOLDEN))

        val adjacentSimulationSpeed = DEFAULT_GOLDEN.replace(
            "\"simulationSpeedPercent\":100",
            "\"simulationSpeedPercent\":101",
        )
        val adjacentTierThreshold = DEFAULT_GOLDEN.replace(
            "\"damageNumberTierThreshold\":50",
            "\"damageNumberTierThreshold\":51",
        )

        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(adjacentSimulationSpeed))
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(adjacentTierThreshold))
    }

    @Test
    fun outboundLabRanksAndDiscoveriesRejectFirstNPlusOne() {
        assertEncodeRejection(
            ProfileSnapshotRejection.INCONSISTENT_PROFILE,
            testSnapshot(
                PlayerProfile(labProgress = LabProgress(List(MetaUpgradeId.entries.size + 1) { 0 })),
            ),
        )
        assertEncodeRejection(
            ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            testSnapshot(
                PlayerProfile(collection = PlayerCollection((0..ContentBounds.MAX_ITEMS).toSet())),
            ),
        )
    }

    @Test
    fun percentageFieldsPreserveTheExistingIntegerQuantization() {
        val snapshot = testSnapshot(
            PlayerProfile(
                preferences = PlayerPreferences(
                    masterVolume = 0.555f,
                    simulationSpeed = 1.35f,
                    textScale = 1.424f,
                ),
            ),
        )

        val decoded = assertIs<ProfileDecodeResult.Decoded>(
            ProfileCodec.decode(requireEncoded(snapshot)),
        ).snapshot

        assertEquals(0.56f, decoded.profile.preferences.masterVolume)
        assertEquals(1.35f, decoded.profile.preferences.simulationSpeed)
        assertEquals(1.42f, decoded.profile.preferences.textScale)
    }

    @Test
    fun everyCharacterAndCumulativeAchievementRoundTripsAndRejectsInvalidProgress() {
        CoreShape.entries.forEach { shape ->
            val achievements = kinetickk.ball.profile.api.CharacterAchievementProgress(
                eliteKills = Long.MAX_VALUE,
                dashHits = 20,
                completedOrbits = 1,
                architectVictories = 6,
                victoriousCharacters = kinetickk.foundation.collections.ImmutableSet.copyOf(CoreShape.entries),
            )
            val snapshot = testSnapshot(PlayerProfile(
                loadout = PlayerLoadout(coreShape = shape),
                characterAchievements = achievements,
            ))
            assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(requireEncoded(snapshot))).snapshot)
        }
        val negative = DEFAULT_GOLDEN.replace("\"eliteKills\":\"0\"", "\"eliteKills\":\"-1\"")
        assertEquals(ProfileSnapshotRejection.INVALID_DECIMAL, decodeRejection(negative))
        val inconsistent = DEFAULT_GOLDEN.replace("\"victoriousCharacterIds\":[]", "\"victoriousCharacterIds\":[\"ORB\"]")
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(inconsistent))
    }

    private fun decodeRejection(payload: String): ProfileSnapshotRejection =
        assertIs<ProfileDecodeResult.Rejected>(
            ProfileCodec.decode(payload),
        ).reason

    private fun assertEncodeRejection(
        expected: ProfileSnapshotRejection,
        snapshot: kinetickk.ball.profile.api.ProfileSnapshot,
    ) {
        assertEquals(
            expected,
            assertIs<ProfileEncodeResult.Rejected>(
                ProfileCodec.encode(snapshot),
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

private const val DEFAULT_GOLDEN: String =
    "{\"revision\":\"0\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\"}," +
        "\"economy\":{\"matter\":\"0\",\"lifetimeMatter\":\"0\"}," +
        "\"loadout\":{\"coreShapeId\":\"ORB\",\"selectedWeaponId\":\"FLUX_WAKE\"," +
        "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]},\"labProgress\":{\"ranks\":[" +
        "{\"id\":\"ARMORY_LICENSE\",\"rank\":0},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":0}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":0}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":0},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0}]}," +
        "\"collection\":{\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":0,\"highestCleared\":-1}," +
        "\"characterAchievements\":{\"eliteKills\":\"0\",\"dashHits\":\"0\",\"completedOrbits\":\"0\"," +
        "\"architectVictories\":\"0\",\"victoriousCharacterIds\":[]}}}"
