// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.highestClearedWith
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfileCodecTest {
    @Test
    fun collectionDiscoveriesRoundTripAndLegacyProfilesKeepTheirProgress() {
        val old = testSnapshot(PlayerProfile(economy = PlayerEconomy(42L, 600L), collection = PlayerCollection(setOf(1, 3))), revision = 19L)
        val legacy = requireEncoded(old)
        assertTrue(legacy.contains("\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[1,3]}"), legacy)
        kotlin.test.assertFalse(legacy.contains("newItemIds"))
        kotlin.test.assertFalse(legacy.contains("discoveredRelicIds"))
        listOf("discoveredReactionIds", "discoveredPairingIds", "heirloomRelicIds").forEach { field ->
            assertFalse(legacy.contains(field), field)
        }
        assertEquals(old, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(legacy)).snapshot)
        val relics = RelicId.entries.toSet()
        val maximum = old.copy(profile = old.profile.copy(collection = PlayerCollection(
            (0 until ContentBounds.MAX_ITEMS).toSet(), (0 until ContentBounds.MAX_ITEMS).toSet(), relics, relics,
            ReactionId.entries.toSet(), RelicPairingId.entries.toSet(), RelicId.entries.takeLast(MAX_HEIRLOOM_RELICS),
        )))
        assertEquals(maximum, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(requireEncoded(maximum))).snapshot)
    }

    @Test
    fun retiredCatalogSaveKeepsEveryOtherFactAndDropsOnlyItsDiscoveries() {
        val legacyCollection = "\"collection\":{\"discoveredItemIds\":[" +
            (0 until ContentBounds.LEGACY_MAX_ITEMS).joinToString(",") + "],\"newItemIds\":[399]," +
            "\"discoveredRelicIds\":[" + RETIRED_RELIC_IDS.joinToString(",") { "\"$it\"" } + "]," +
            "\"newRelicIds\":[\"KINETIC_FLYWHEEL\",\"MASS_ECHO\"]}"
        val payload = LEGACY_REPRESENTATIVE_GOLDEN.replace(LEGACY_REPRESENTATIVE_COLLECTION, legacyCollection)
        assertEquals(ContentBounds.LEGACY_MAX_RELICS, RETIRED_RELIC_IDS.size)

        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload)).snapshot
        val profile = decoded.profile
        assertEquals(2_718L, decoded.revision.value)
        assertEquals(PlayerCollection(), profile.collection)
        // The retired Lab ranks come back as Matter (387) and the Matrix starts empty.
        assertEquals(PlayerEconomy(4_708L, 98_765L), profile.economy)
        assertEquals(PlayerLoadout(CoreShape.RING, WeaponId.ARC_COIL, setOf(WeaponId.ARC_COIL, WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR)), profile.loadout)
        assertEquals(MatrixProgress(), profile.matrix)
        assertEquals(RebirthProgress(4, 3), profile.rebirthProgress)
        assertEquals(
            kinetickk.ball.profile.api.CharacterAchievementProgress(12L, 340L, 5L, 2L, kinetickk.foundation.collections.immutableSetOf(CoreShape.ORB, CoreShape.RING)),
            profile.characterAchievements,
        )
        assertEquals(AppLanguage.English, profile.preferences.language)
        assertTrue(profile.preferences.runStatisticsOnLeft)
        // Everything except the collection matches the current-catalog save of the same player.
        val current = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        assertEquals(current.profile.copy(collection = PlayerCollection()), profile)
        // The next write upgrades the save to the current catalog generation.
        val upgraded = REPRESENTATIVE_GOLDEN.replace(REPRESENTATIVE_COLLECTION, EMPTY_CURRENT_COLLECTION)
        assertEquals(upgraded, requireEncoded(decoded))
        assertEquals(decoded, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(upgraded)).snapshot)

        // The retired goldens themselves stay readable and upgrade the same way.
        val legacyDefault = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(LEGACY_DEFAULT_GOLDEN)).snapshot
        assertEquals(testSnapshot(), legacyDefault)
        assertEquals(DEFAULT_GOLDEN, requireEncoded(legacyDefault))
        val legacyRepresentative = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(LEGACY_REPRESENTATIVE_GOLDEN)).snapshot
        assertEquals(decoded, legacyRepresentative)

        // Explicit retired generations behave like the absent epoch; the codec still checks structure.
        for (epoch in 0 until ContentBounds.COLLECTION_GENERATION) {
            val explicit = payload.replace("\"collection\":{", "\"collection\":{\"itemCatalogEpoch\":$epoch,")
            assertEquals(decoded, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(explicit)).snapshot, "epoch $epoch")
        }
        assertEquals(ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE, decodeRejection(
            payload.replace("\"newRelicIds\":[\"KINETIC_FLYWHEEL\",\"MASS_ECHO\"]", "\"newRelicIds\":[\"MASS_ECHO\",\"KINETIC_FLYWHEEL\"]"),
        ))
        // The retired codec never wrote the new fields, so a save without an epoch cannot carry them.
        listOf(
            ",\"discoveredReactionIds\":[\"FUSION\"]",
            ",\"discoveredPairingIds\":[\"MELTDOWN\"]",
            ",\"heirloomRelicIds\":[\"GLASS_COMET\"]",
        ).forEach { field ->
            val invented = LEGACY_REPRESENTATIVE_GOLDEN.replace("\"newRelicIds\":[\"MASS_ECHO\"]}", "\"newRelicIds\":[\"MASS_ECHO\"]$field}")
            assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(invented), field)
        }
    }

    @Test
    fun catalogEpochIsWrittenByEveryEncodeAndOnlyKnownGenerationsDecode() {
        assertTrue(requireEncoded(testSnapshot()).contains("\"itemCatalogEpoch\":${ContentBounds.COLLECTION_GENERATION}"))
        val epoch = "\"itemCatalogEpoch\":2"
        listOf(-1, ContentBounds.COLLECTION_GENERATION + 1, Int.MAX_VALUE).forEach { invalid ->
            assertEquals(
                ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
                decodeRejection(DEFAULT_GOLDEN.replace(epoch, "\"itemCatalogEpoch\":$invalid")),
                invalid.toString(),
            )
        }
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(DEFAULT_GOLDEN.replace(epoch, "\"itemCatalogEpoch\":null")))
        // A quoted number parses but is not what the encoder writes.
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(DEFAULT_GOLDEN.replace(epoch, "\"itemCatalogEpoch\":\"2\"")))
        assertEquals(ProfileSnapshotRejection.MALFORMED_JSON, decodeRejection(DEFAULT_GOLDEN.replace(epoch, "\"itemCatalogEpoch\":true")))
        // The epoch leads the collection; any other position is not the canonical encoding.
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(
            DEFAULT_GOLDEN.replace("{$epoch,\"discoveredItemIds\":[]}", "{\"discoveredItemIds\":[],$epoch}"),
        ))
    }

    @Test
    fun buildDiscoveriesAndHeirloomsRoundTripInTheirCanonicalOrder() {
        val representative = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        val collection = representative.profile.collection
        assertEquals(setOf(ReactionId.EMBRITTLE, ReactionId.THERMAL_SHOCK), collection.discoveredReactionIds)
        assertEquals(setOf(RelicPairingId.HEAVY_BLINK), collection.discoveredPairingIds)
        assertEquals(listOf(RelicId.GLASS_COMET, RelicId.BLINK_DRIVE), collection.heirloomRelicIds)

        // Heirlooms keep slot order, so both orders are canonical and distinct.
        val swapped = REPRESENTATIVE_GOLDEN.replace(
            "\"heirloomRelicIds\":[\"GLASS_COMET\",\"BLINK_DRIVE\"]",
            "\"heirloomRelicIds\":[\"BLINK_DRIVE\",\"GLASS_COMET\"]",
        )
        val reordered = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(swapped)).snapshot
        assertEquals(listOf(RelicId.BLINK_DRIVE, RelicId.GLASS_COMET), reordered.profile.collection.heirloomRelicIds)
        assertEquals(swapped, requireEncoded(reordered))

        // Discovery sets are written sorted whatever their in-memory order.
        val unordered = testSnapshot(PlayerProfile(collection = PlayerCollection(
            emptySet(),
            discoveredReactionIds = ReactionId.entries.reversed().toSet(),
            discoveredPairingIds = RelicPairingId.entries.reversed().toSet(),
            heirloomRelicIds = listOf(RelicId.OUROBOROS_SEAL, RelicId.ABSOLUTE_ZERO),
        )))
        val encoded = requireEncoded(unordered)
        assertEquals(ReactionId.entries.map { it.name }.sorted(), encoded.extractStringArray("discoveredReactionIds"))
        assertEquals(RelicPairingId.entries.map { it.name }.sorted(), encoded.extractStringArray("discoveredPairingIds"))
        assertEquals(listOf("OUROBOROS_SEAL", "ABSOLUTE_ZERO"), encoded.extractStringArray("heirloomRelicIds"))
        assertEquals(unordered, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded)).snapshot)

        // Unknown or retired ids in a current-generation save are still invalid stable ids.
        listOf(
            "\"discoveredRelicIds\":[\"BLINK_DRIVE\"" to "\"discoveredRelicIds\":[\"AAA_UNKNOWN\"",
            "\"heirloomRelicIds\":[\"GLASS_COMET\"" to "\"heirloomRelicIds\":[\"KINETIC_FLYWHEEL\"",
            "\"discoveredReactionIds\":[\"EMBRITTLE\"" to "\"discoveredReactionIds\":[\"BURN\"",
            "\"discoveredPairingIds\":[\"HEAVY_BLINK\"" to "\"discoveredPairingIds\":[\"heavy_blink\"",
        ).forEach { (canonical, invalid) ->
            assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(REPRESENTATIVE_GOLDEN.replace(canonical, invalid)), invalid)
        }
    }

    @Test
    fun collectionIngressBoundsAcceptNAndRejectFirstNPlusOne() {
        val maximum = testSnapshot(PlayerProfile(collection = PlayerCollection(
            (0 until ContentBounds.MAX_ITEMS).toSet(),
            discoveredRelicIds = RelicId.entries.toSet(),
            discoveredReactionIds = ReactionId.entries.toSet(),
            discoveredPairingIds = RelicPairingId.entries.toSet(),
            heirloomRelicIds = RelicId.entries.take(MAX_HEIRLOOM_RELICS),
        )))
        val encoded = requireEncoded(maximum)
        assertEquals(maximum, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded)).snapshot)

        fun appended(field: String, extra: List<String>): String {
            val body = Regex("\\\"$field\\\":\\[(.*?)]").find(encoded)!!.groupValues[1]
            return encoded.replace("\"$field\":[$body]", "\"$field\":[$body,${extra.joinToString(",")}]")
        }
        val overflowing = mapOf(
            "discoveredItemIds" to listOf(ContentBounds.MAX_ITEMS.toString()),
            // Only twenty Relics exist, so the current bound is reached with sorted names past them.
            "discoveredRelicIds" to (1..ContentBounds.MAX_RELICS - RelicId.entries.size + 1).map { "\"ZZ$it\"" },
            "discoveredReactionIds" to listOf("\"ZZ\""),
            "discoveredPairingIds" to listOf("\"ZZ\""),
            "heirloomRelicIds" to listOf("\"${RelicId.entries.last().name}\""),
        )
        overflowing.forEach { (field, extra) ->
            assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(appended(field, extra)), field)
        }

        // A retired-catalog save may list up to the retired bounds and is rejected one past them.
        val legacyAtBound = LEGACY_DEFAULT_GOLDEN.replace(
            "\"collection\":{\"discoveredItemIds\":[]}",
            "\"collection\":{\"discoveredItemIds\":[" + (0 until ContentBounds.LEGACY_MAX_ITEMS).joinToString(",") +
                "],\"discoveredRelicIds\":[" + RETIRED_RELIC_IDS.joinToString(",") { "\"$it\"" } + "]}",
        )
        assertEquals(testSnapshot(), assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(legacyAtBound)).snapshot)
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(
            legacyAtBound.replace(",${ContentBounds.LEGACY_MAX_ITEMS - 1}]", ",${ContentBounds.LEGACY_MAX_ITEMS - 1},${ContentBounds.LEGACY_MAX_ITEMS}]"),
        ))
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(
            legacyAtBound.replace("\"${RETIRED_RELIC_IDS.last()}\"]", "\"${RETIRED_RELIC_IDS.last()}\",\"ZZ_RETIRED\"]"),
        ))
    }

    @Test
    fun unseenMarkersCannotInventDiscoveriesAndRelicWireIdsAreValidated() {
        val snapshot = testSnapshot(PlayerProfile(collection = PlayerCollection(setOf(1), setOf(1),
            setOf(RelicId.PERPETUAL_VECTOR), setOf(RelicId.PERPETUAL_VECTOR))))
        val encoded = requireEncoded(snapshot)
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(encoded.replace("\"newItemIds\":[1]", "\"newItemIds\":[2]")))
        assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(encoded.replace("PERPETUAL_VECTOR", "UNKNOWN_RELIC")))
        // A retired Relic name is unknown in a save of the current catalog generation.
        assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(encoded.replace("PERPETUAL_VECTOR", "KINETIC_FLYWHEEL")))
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(
            encoded.replace("\"newRelicIds\":[\"PERPETUAL_VECTOR\"]", "\"newRelicIds\":[\"BLINK_DRIVE\"]"),
        ))
        assertEquals(ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE, decodeRejection(encoded.replace("\"newItemIds\":[1]", "\"newItemIds\":[1,1]")))
    }

    @Test
    fun discoveryFieldsRejectUnsortedValuesAndDuplicates() {
        val relics = setOf(RelicId.BLINK_DRIVE, RelicId.PERPETUAL_VECTOR)
        val snapshot = testSnapshot(PlayerProfile(
            collection = PlayerCollection(
                setOf(1, 3), setOf(1, 3), relics, relics,
                setOf(ReactionId.FUSION, ReactionId.WILDFIRE),
                setOf(RelicPairingId.BLACK_STAR, RelicPairingId.MELTDOWN),
                listOf(RelicId.PERPETUAL_VECTOR, RelicId.BLINK_DRIVE),
            ),
        ))
        val encoded = requireEncoded(snapshot)
        val fields = mapOf(
            "newItemIds" to listOf("1", "3"),
            "discoveredRelicIds" to listOf("\"BLINK_DRIVE\"", "\"PERPETUAL_VECTOR\""),
            "newRelicIds" to listOf("\"BLINK_DRIVE\"", "\"PERPETUAL_VECTOR\""),
            "discoveredReactionIds" to listOf("\"FUSION\"", "\"WILDFIRE\""),
            "discoveredPairingIds" to listOf("\"BLACK_STAR\"", "\"MELTDOWN\""),
        )
        for ((field, values) in fields) {
            val canonical = "\"$field\":[${values.joinToString(",")}]"
            for (invalid in listOf(values.reversed(), listOf(values.first(), values.first()))) {
                val payload = encoded.replace(canonical, "\"$field\":[${invalid.joinToString(",")}]")
                assertEquals(
                    ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
                    decodeRejection(payload),
                    field,
                )
            }
        }
        // Heirlooms are ordered by slot rather than sorted, but a Relic fills at most one slot.
        val heirlooms = "\"heirloomRelicIds\":[\"PERPETUAL_VECTOR\",\"BLINK_DRIVE\"]"
        assertTrue(encoded.contains(heirlooms), encoded)
        assertEquals(ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE, decodeRejection(
            encoded.replace(heirlooms, "\"heirloomRelicIds\":[\"PERPETUAL_VECTOR\",\"PERPETUAL_VECTOR\"]"),
        ))
        assertEncodeRejection(
            ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            testSnapshot(PlayerProfile(collection = PlayerCollection(
                kinetickk.foundation.collections.immutableSetOf(),
                heirloomRelicIds = kinetickk.foundation.collections.immutableListOf(RelicId.GLASS_COMET, RelicId.GLASS_COMET),
            ))),
        )
    }

    @Test
    fun statisticsSideRoundTripsAndOlderSavesKeepAllProgress() {
        for (onLeft in listOf(false, true)) {
            val snapshot = testSnapshot(PlayerProfile(
                preferences = PlayerPreferences(runStatisticsOnLeft = onLeft),
                economy = PlayerEconomy(120L, 900L),
            ), revision = 17L)
            val encoded = requireEncoded(snapshot)
            assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded)).snapshot)
            val legacy = encoded.replace(",\"runStatisticsOnLeft\":$onLeft", "")
            val old = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(legacy)).snapshot
            assertEquals(snapshot.copy(profile = snapshot.profile.copy(
                preferences = snapshot.profile.preferences.copy(runStatisticsOnLeft = false),
            )), old)
        }
    }

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
    fun existingSavesWithoutColorVisionDecodeAsDefaultAndReEncodeByteIdentically() {
        for (existing in listOf(DEFAULT_GOLDEN, REPRESENTATIVE_GOLDEN)) {
            val snapshot = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(existing)).snapshot
            assertEquals(ColorVision.DEFAULT, snapshot.profile.preferences.colorVision)
            assertEquals(existing, requireEncoded(snapshot))
        }
        val representative = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        assertEquals(2_718L, representative.revision.value)
        assertEquals(AppLanguage.English, representative.profile.preferences.language)
        assertEquals(true, representative.profile.preferences.runStatisticsOnLeft)
        assertEquals(
            setOf(RelicId.BLINK_DRIVE, RelicId.GLASS_COMET, RelicId.OUROBOROS_SEAL),
            representative.profile.collection.discoveredRelicIds,
        )
        assertEquals(setOf(RelicId.OUROBOROS_SEAL), representative.profile.collection.newRelicIds)

        // Saves predating language selection and the statistics side stay readable and canonical.
        val oldest = DEFAULT_GOLDEN.replace(",\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false", "")
        val decodedOldest = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(oldest)).snapshot
        assertEquals(ColorVision.DEFAULT, decodedOldest.profile.preferences.colorVision)
        assertEquals(DEFAULT_GOLDEN, requireEncoded(decodedOldest))
        val legacyOldest = LEGACY_DEFAULT_GOLDEN.replace(",\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false", "")
        assertEquals(DEFAULT_GOLDEN, requireEncoded(assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(legacyOldest)).snapshot))
    }

    @Test
    fun everyColorVisionRoundTripsAndOnlyNonDefaultChoicesAreWritten() {
        val base = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        ColorVision.entries.forEach { colorVision ->
            val snapshot = base.copy(profile = base.profile.copy(
                preferences = base.profile.preferences.copy(colorVision = colorVision),
            ))
            val encoded = requireEncoded(snapshot)
            assertEquals(snapshot, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded)).snapshot)
            if (colorVision == ColorVision.DEFAULT) {
                assertFalse(encoded.contains("colorVisionId"))
                assertEquals(REPRESENTATIVE_GOLDEN, encoded)
            } else {
                // The field is appended after the existing preferences; nothing else moves.
                val field = ",\"colorVisionId\":\"${colorVision.name}\""
                assertTrue(encoded.contains("\"runStatisticsOnLeft\":true$field},\"economy\""), encoded)
                assertEquals(REPRESENTATIVE_GOLDEN, encoded.replace(field, ""))
            }
        }
    }

    @Test
    fun theMinimapIsWrittenOnlyWhenOffAndOlderSavesShowIt() {
        val base = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        assertTrue(base.profile.preferences.minimapEnabled, "saves without the field show the minimap")
        assertEquals(REPRESENTATIVE_GOLDEN, requireEncoded(base))
        val hidden = base.copy(profile = base.profile.copy(preferences = base.profile.preferences.copy(minimapEnabled = false)))
        val encoded = requireEncoded(hidden)
        assertTrue(encoded.contains("\"runStatisticsOnLeft\":true,\"minimapEnabled\":false},\"economy\""), encoded)
        assertEquals(hidden, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded)).snapshot)
        assertEquals(REPRESENTATIVE_GOLDEN, encoded.replace(",\"minimapEnabled\":false", ""))
        // The default is never written, so an explicit "on" is not canonical.
        val explicitOn = encoded.replace("\"minimapEnabled\":false", "\"minimapEnabled\":true")
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(explicitOn))
    }

    @Test
    fun unknownExplicitDefaultAndNullColorVisionIdsAreRejected() {
        val mono = requireEncoded(testSnapshot(PlayerProfile(preferences = PlayerPreferences(colorVision = ColorVision.MONO))))
        listOf("DEFAULT", "default", "mono", "Mono", "", "SEPIA", "PROTANOPIA").forEach { invalid ->
            val payload = mono.replace("\"colorVisionId\":\"MONO\"", "\"colorVisionId\":\"$invalid\"")
            assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(payload), invalid)
        }
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD, decodeRejection(
            mono.replace("\"colorVisionId\":\"MONO\"", "\"colorVisionId\":null"),
        ))
        assertEquals(ProfileSnapshotRejection.MALFORMED_JSON, decodeRejection(
            mono.replace("\"colorVisionId\":\"MONO\"", "\"colorVisionId\":4"),
        ))
    }

    @Test
    fun displayAndControlPreferencesAreWrittenOnlyWhenNonDefaultAfterTheExistingFields() {
        // Saves from before these settings read as the defaults and keep their bytes.
        for (existing in listOf(DEFAULT_GOLDEN, REPRESENTATIVE_GOLDEN, MATRIX_GOLDEN, UPGRADES_GOLDEN)) {
            val snapshot = decoded(existing)
            assertEquals(PlayerPreferences().displayAndControls(), snapshot.profile.preferences.displayAndControls())
            assertEquals(existing, requireEncoded(snapshot))
        }

        val base = decoded(REPRESENTATIVE_GOLDEN)
        fun choosing(preferences: PlayerPreferences) = base.copy(profile = base.profile.copy(preferences = preferences))
        val every = choosing(everyDisplayChoice(base.profile.preferences))
        val encoded = requireEncoded(every)
        assertEquals(
            REPRESENTATIVE_GOLDEN.replace("\"runStatisticsOnLeft\":true}", "\"runStatisticsOnLeft\":true$EVERY_DISPLAY_FIELD}"),
            encoded,
        )
        assertEquals(every, decoded(encoded))

        // Each field alone is appended after the existing preferences; nothing else moves.
        val preferences = base.profile.preferences
        listOf(
            preferences.copy(windowMode = WindowMode.FULLSCREEN) to ",\"windowModeId\":\"FULLSCREEN\"",
            preferences.copy(windowSize = WindowSizePreset.W2560X1440) to ",\"windowSizeId\":\"W2560X1440\"",
            preferences.copy(vsync = false) to ",\"vsyncEnabled\":false",
            preferences.copy(frameRateCap = 30) to ",\"frameRateCap\":30",
            preferences.copy(refreshRateHz = REFRESH_RATE_RANGE.first) to ",\"refreshRateHz\":24",
            preferences.copy(immersive = true) to ",\"immersiveEnabled\":true",
            preferences.copy(interfaceScalePercent = 85) to ",\"interfaceScalePercent\":85",
            preferences.copy(showFps = true) to ",\"showFps\":true",
            preferences.copy(pauseOnFocusLoss = false) to ",\"pauseOnFocusLoss\":false",
            preferences.copy(reactionEffects = ReactionEffectsLevel.REDUCED) to ",\"reactionEffectsId\":\"REDUCED\"",
            preferences.copy(keyBindings = KeyBindings.DEFAULT.with(GameAction.PERFORMANCE_HUD, BindableKey.F12)) to
                ",\"keyBindings\":[{\"action\":\"PERFORMANCE_HUD\",\"key\":\"F12\"}]",
        ).forEach { (chosen, field) ->
            val single = requireEncoded(choosing(chosen))
            assertTrue(single.contains("\"runStatisticsOnLeft\":true$field},\"economy\""), single)
            assertEquals(choosing(chosen), decoded(single))
            assertEquals(REPRESENTATIVE_GOLDEN, single.replace(field, ""))
        }

        // Every member of every option set round-trips.
        val choices = WindowMode.entries.map { preferences.copy(windowMode = it) } +
            WindowSizePreset.entries.map { preferences.copy(windowSize = it) } +
            ReactionEffectsLevel.entries.map { preferences.copy(reactionEffects = it) } +
            FRAME_RATE_CAP_OPTIONS.map { preferences.copy(frameRateCap = it) } +
            INTERFACE_SCALE_OPTIONS.map { preferences.copy(interfaceScalePercent = it) } +
            listOf(0, REFRESH_RATE_RANGE.first, 60, REFRESH_RATE_RANGE.last).map { preferences.copy(refreshRateHz = it) } +
            BindableKey.entries.map { key ->
                preferences.copy(keyBindings = KeyBindings.DEFAULT.with(GameAction.PERFORMANCE_HUD, key))
            }
        choices.forEach { chosen -> assertEquals(choosing(chosen), decoded(requireEncoded(choosing(chosen))), chosen.toString()) }
    }

    @Test
    fun displayPreferenceIngressRejectsExplicitDefaultsUnknownIdsAndNonMembers() {
        val payload = requireEncoded(testSnapshot(PlayerProfile(preferences = everyDisplayChoice(PlayerPreferences()))))
        assertTrue(payload.contains(EVERY_DISPLAY_FIELD), payload)
        listOf(
            Triple("\"windowModeId\":\"BORDERLESS\"", "\"windowModeId\":\"WINDOWED\"", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"windowModeId\":\"BORDERLESS\"", "\"windowModeId\":\"borderless\"", ProfileSnapshotRejection.INVALID_STABLE_ID),
            Triple("\"windowModeId\":\"BORDERLESS\"", "\"windowModeId\":\"\"", ProfileSnapshotRejection.INVALID_STABLE_ID),
            Triple("\"windowModeId\":\"BORDERLESS\"", "\"windowModeId\":null", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple("\"windowModeId\":\"BORDERLESS\"", "\"windowModeId\":1", ProfileSnapshotRejection.MALFORMED_JSON),
            Triple("\"windowSizeId\":\"W1920X1080\"", "\"windowSizeId\":\"W1280X800\"", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"windowSizeId\":\"W1920X1080\"", "\"windowSizeId\":\"W800X600\"", ProfileSnapshotRejection.INVALID_STABLE_ID),
            Triple("\"vsyncEnabled\":false", "\"vsyncEnabled\":true", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"frameRateCap\":144", "\"frameRateCap\":0", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"frameRateCap\":144", "\"frameRateCap\":143", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"frameRateCap\":144", "\"frameRateCap\":241", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"frameRateCap\":144", "\"frameRateCap\":-144", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"frameRateCap\":144", "\"frameRateCap\":\"144\"", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple("\"refreshRateHz\":120", "\"refreshRateHz\":0", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"refreshRateHz\":120", "\"refreshRateHz\":23", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"refreshRateHz\":120", "\"refreshRateHz\":361", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"immersiveEnabled\":true", "\"immersiveEnabled\":false", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"interfaceScalePercent\":115", "\"interfaceScalePercent\":100", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"interfaceScalePercent\":115", "\"interfaceScalePercent\":116", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"interfaceScalePercent\":115", "\"interfaceScalePercent\":84", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"showFps\":true", "\"showFps\":false", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"pauseOnFocusLoss\":false", "\"pauseOnFocusLoss\":true", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"reactionEffectsId\":\"MINIMAL\"", "\"reactionEffectsId\":\"FULL\"", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple("\"reactionEffectsId\":\"MINIMAL\"", "\"reactionEffectsId\":\"NONE\"", ProfileSnapshotRejection.INVALID_STABLE_ID),
            // The fields keep their declared order.
            Triple("\"showFps\":true,\"pauseOnFocusLoss\":false", "\"pauseOnFocusLoss\":false,\"showFps\":true",
                ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
        ).forEach { (canonical, invalid, expected) ->
            assertTrue(payload.contains(canonical), canonical)
            assertEquals(expected, decodeRejection(payload.replace(canonical, invalid)), invalid)
        }
        // The refresh range bounds are stored values.
        for (hz in listOf(REFRESH_RATE_RANGE.first, REFRESH_RATE_RANGE.last)) {
            assertEquals(hz, decoded(payload.replace("\"refreshRateHz\":120", "\"refreshRateHz\":$hz")).profile.preferences.refreshRateHz)
        }

        // Outbound values outside the option sets are refused, never clamped.
        listOf(
            PlayerPreferences(frameRateCap = 59),
            PlayerPreferences(frameRateCap = 241),
            PlayerPreferences(refreshRateHz = REFRESH_RATE_RANGE.first - 1),
            PlayerPreferences(refreshRateHz = REFRESH_RATE_RANGE.last + 1),
            PlayerPreferences(interfaceScalePercent = 101),
            PlayerPreferences(interfaceScalePercent = 84),
        ).forEach { invalid ->
            assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, testSnapshot(PlayerProfile(preferences = invalid)))
        }
    }

    @Test
    fun keyBindingRecordsRejectUnsortedDuplicateUnknownDefaultAndConflictingForms() {
        val payload = requireEncoded(testSnapshot(PlayerProfile(preferences = everyDisplayChoice(PlayerPreferences()))))
        val records = "[{\"action\":\"APEX\",\"key\":\"F\"},{\"action\":\"DASH\",\"key\":\"Z\"},{\"action\":\"SKILL_1\",\"key\":\"SPACE\"}]"
        assertTrue(payload.contains("\"keyBindings\":$records"), payload)
        fun rebound(list: String) = payload.replace("\"keyBindings\":$records", "\"keyBindings\":$list")
        listOf(
            // Unsorted and duplicate actions.
            "[{\"action\":\"DASH\",\"key\":\"Z\"},{\"action\":\"APEX\",\"key\":\"F\"},{\"action\":\"SKILL_1\",\"key\":\"SPACE\"}]" to
                ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            "[{\"action\":\"APEX\",\"key\":\"F\"},{\"action\":\"APEX\",\"key\":\"G\"}]" to ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            // Two actions on one key, explicitly or by leaving the default holder in place.
            "[{\"action\":\"APEX\",\"key\":\"F\"},{\"action\":\"DASH\",\"key\":\"F\"},{\"action\":\"SKILL_1\",\"key\":\"SPACE\"}]" to
                ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            "[{\"action\":\"APEX\",\"key\":\"F\"},{\"action\":\"DASH\",\"key\":\"Z\"}]" to ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            // Unknown ids; Escape is never bindable and ids are case-sensitive.
            "[{\"action\":\"JUMP\",\"key\":\"F\"}]" to ProfileSnapshotRejection.INVALID_STABLE_ID,
            "[{\"action\":\"APEX\",\"key\":\"ESCAPE\"}]" to ProfileSnapshotRejection.INVALID_STABLE_ID,
            "[{\"action\":\"APEX\",\"key\":\"f\"}]" to ProfileSnapshotRejection.INVALID_STABLE_ID,
            // A record repeating its default is never written.
            "[{\"action\":\"APEX\",\"key\":\"V\"}]" to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            "[]" to ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            "[{\"key\":\"F\",\"action\":\"APEX\"}]" to ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            "null" to ProfileSnapshotRejection.MALFORMED_JSON,
            "[{\"action\":\"APEX\"}]" to ProfileSnapshotRejection.MALFORMED_JSON,
        ).forEach { (list, expected) ->
            assertEquals(expected, decodeRejection(rebound(list)), list)
        }
        assertEquals(
            KeyBindings.DEFAULT.with(GameAction.APEX, BindableKey.F),
            decoded(rebound("[{\"action\":\"APEX\",\"key\":\"F\"}]")).profile.preferences.keyBindings,
        )

        // Every action rebound is the largest record list; one record more is refused.
        val shifted = GameAction.entries.zip(BindableKey.entries.filter { it !in KeyBindings.DEFAULT.keys })
        val everyAction = shifted.fold(KeyBindings.DEFAULT) { bindings, (action, key) -> bindings.with(action, key) }
        val full = requireEncoded(testSnapshot(PlayerProfile(preferences = PlayerPreferences(keyBindings = everyAction))))
        assertEquals(everyAction, decoded(full).profile.preferences.keyBindings)
        val fullRecords = GameAction.entries.map { it.name }.sorted()
        assertEquals(fullRecords, ACTION_ID.findAll(full).map { it.groupValues[1] }.toList())
        val lastRecord = shifted.maxBy { it.first.name }.let { (action, key) -> "{\"action\":\"${action.name}\",\"key\":\"${key.name}\"}" }
        assertEquals(
            ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            decodeRejection(full.replace("$lastRecord]", "$lastRecord,{\"action\":\"ZOOM\",\"key\":\"Y\"}]")),
        )
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
    fun currentSchemaMaximumUnlockedWeaponsMatrixRanksAndDiscoveriesRoundTripWithoutLoss() {
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
            matrix = MatrixProgress(MatrixNodeId.entries.map { ContentBounds.MAX_MATRIX_NODE_RANKS }),
            collection = PlayerCollection(
                (0 until ContentBounds.MAX_ITEMS).reversed().toSet(),
                discoveredRelicIds = RelicId.entries.reversed().toSet(),
                discoveredReactionIds = ReactionId.entries.toSet(),
                discoveredPairingIds = RelicPairingId.entries.toSet(),
                heirloomRelicIds = RelicId.entries.reversed().take(MAX_HEIRLOOM_RELICS),
            ),
            rebirthProgress = RebirthProgress(7, 6),
        )
        val snapshot = testSnapshot(profile, revision = 42L)

        val encoded = requireEncoded(snapshot)
        val decoded = assertIs<ProfileDecodeResult.Decoded>(
            ProfileCodec.decode(encoded),
        )

        assertEquals(snapshot, decoded.snapshot)
        assertEquals(ContentBounds.MAX_WEAPONS, decoded.snapshot.profile.loadout.unlockedWeapons.size)
        assertEquals(MatrixNodeId.entries.size, decoded.snapshot.profile.matrix.ownedNodeCount)
        assertEquals(ContentBounds.MAX_ITEMS, decoded.snapshot.profile.collection.discoveredItemIds.size)
        assertEquals(
            WeaponId.entries.map(WeaponId::name).sorted(),
            encoded.extractStringArray("unlockedWeaponIds"),
        )
        assertEquals((0 until ContentBounds.MAX_ITEMS).toList(), encoded.extractIntArray("discoveredItemIds"))
        assertEquals(RelicId.entries.map(RelicId::name).sorted(), encoded.extractStringArray("discoveredRelicIds"))
        assertEquals(ReactionId.entries.size, decoded.snapshot.profile.collection.discoveredReactionIds.size)
        assertEquals(RelicPairingId.entries.size, decoded.snapshot.profile.collection.discoveredPairingIds.size)
        assertEquals(
            RelicId.entries.reversed().take(MAX_HEIRLOOM_RELICS).map(RelicId::name),
            encoded.extractStringArray("heirloomRelicIds"),
        )
        assertEquals(
            MatrixNodeId.entries.map { it.name }.sorted(),
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
    fun anAppendedWeaponIdRoundTripsByItsOwnWireId() {
        val payload = DEFAULT_GOLDEN.replace(
            "\"selectedWeaponId\":\"FLUX_WAKE\",\"unlockedWeaponIds\":[\"FLUX_WAKE\"]",
            "\"selectedWeaponId\":\"TETHER_ARC\",\"unlockedWeaponIds\":[\"FLUX_WAKE\",\"TETHER_ARC\"]",
        )
        assertNotEquals(DEFAULT_GOLDEN, payload)

        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload))

        assertEquals(WeaponId.TETHER_ARC, decoded.snapshot.profile.loadout.selectedWeapon)
        assertEquals(setOf(WeaponId.FLUX_WAKE, WeaponId.TETHER_ARC), decoded.snapshot.profile.loadout.unlockedWeapons)
        assertEquals(payload, requireEncoded(decoded.snapshot))
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
    fun aLoweredRebirthTierRoundTripsByteExactAndATierAboveTheRankIsRejected() {
        val representative = "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}"
        assertTrue(REPRESENTATIVE_GOLDEN.contains(representative))
        val lowered = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":{\"level\":0,\"highestCleared\":4}")
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(lowered)).snapshot
        assertEquals(RebirthProgress(level = 0, highestCleared = 4), decoded.profile.rebirthProgress)
        assertEquals(lowered, requireEncoded(decoded))
        val atRank = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":{\"level\":5,\"highestCleared\":4}")
        assertEquals(atRank, requireEncoded(assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(atRank)).snapshot))

        val aboveRank = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":{\"level\":6,\"highestCleared\":4}")
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(aboveRank))
        val clearedPastMaximum = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":{\"level\":10,\"highestCleared\":11}")
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, decodeRejection(clearedPastMaximum))
        assertIs<ProfileEncodeResult.Rejected>(
            ProfileCodec.encode(decoded.copy(profile = decoded.profile.copy(rebirthProgress = RebirthProgress(level = 6, highestCleared = 4)))),
        )
    }

    @Test
    fun ascendantCharactersAreWrittenOnlyOnceOneClearsTheFinalTier() {
        val representative = "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}"
        fun with(progress: String) = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":$progress")
        // Older saves (no field) keep their exact bytes and have no Ascendant character.
        val plain = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        assertTrue(plain.profile.rebirthProgress.ascendedCharacters.isEmpty())
        assertEquals(REPRESENTATIVE_GOLDEN, requireEncoded(plain))

        val ascendant = with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"ORB\",\"RING\"]}")
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(ascendant)).snapshot
        assertEquals(setOf(CoreShape.ORB, CoreShape.RING), decoded.profile.rebirthProgress.ascendedCharacters)
        assertEquals(ascendant, requireEncoded(decoded))
        // Cleared tier 10 without an Ascendant character recorded (a pre-0.4 clear) is still canonical.
        val cleared = with("{\"level\":10,\"highestCleared\":10}")
        assertEquals(cleared, requireEncoded(assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(cleared)).snapshot))

        assertEquals(ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"RING\",\"ORB\"]}")))
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"DIAMOND\"]}")))
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            decodeRejection(with("{\"level\":4,\"highestCleared\":9,\"ascendedCharacterIds\":[\"ORB\"]}")))
        assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"NONAGON\"]}")))
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[]}")))
    }

    @Test
    fun characterClearsAreOneDigitPerCharacterWrittenOnlyOnceATierIsRecorded() {
        val representative = "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}"
        fun with(progress: String) = REPRESENTATIVE_GOLDEN.replace(representative, "\"rebirthProgress\":$progress")
        // Older saves (no field) keep their exact bytes; their victories (Circle and Ring) recorded no tier.
        val plain = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot
        assertTrue(plain.profile.rebirthProgress.characterClears.all { it == -1 })
        assertEquals(REPRESENTATIVE_GOLDEN, requireEncoded(plain))

        // The tier plus one per character slot (Circle, Square, Triangle, Ring, ...): Circle cleared tier 3 and
        // Ring tier 0; the slots after Ring hold none and are dropped.
        val cleared = with("{\"level\":4,\"highestCleared\":3,\"characterClears\":\"4001\"}")
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(cleared)).snapshot
        val progress = decoded.profile.rebirthProgress
        assertEquals(listOf(3, -1, -1, 0), listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING).map(progress::highestClearedWith))
        assertEquals(cleared, requireEncoded(decoded))
        // The final tier is an Ascendant clear: digit b.
        val ascendant = with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"ORB\"],\"characterClears\":\"b\"}")
        assertEquals(ascendant, requireEncoded(assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(ascendant)).snapshot))

        mapOf(
            "\"40010\"" to ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            "\"\"" to ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            "\"A\"" to ProfileSnapshotRejection.INVALID_DECIMAL,
            "\"-1\"" to ProfileSnapshotRejection.INVALID_DECIMAL,
            "\"c\"" to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            "\"1000000000001\"" to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            // Above the highest clear.
            "\"5\"" to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            // Square never defeated the Architect.
            "\"01\"" to ProfileSnapshotRejection.INCONSISTENT_PROFILE,
        ).forEach { (digits, rejection) ->
            assertEquals(rejection, decodeRejection(with("{\"level\":4,\"highestCleared\":3,\"characterClears\":$digits}")), digits)
        }
        // A final-tier clear is exactly an Ascendant one.
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"characterClears\":\"b\"}")))
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            decodeRejection(with("{\"level\":4,\"highestCleared\":10,\"ascendedCharacterIds\":[\"ORB\"],\"characterClears\":\"a\"}")))
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
            PlayerProfile(matrix = MatrixProgress(List(MatrixNodeId.entries.size - 1) { 0 })),
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
    fun outboundMatrixRanksAndDiscoveriesRejectFirstNPlusOne() {
        assertEncodeRejection(
            ProfileSnapshotRejection.INCONSISTENT_PROFILE,
            testSnapshot(
                PlayerProfile(matrix = MatrixProgress(List(MatrixNodeId.entries.size + 1) { 0 })),
            ),
        )
        fun ranked(rank: Int) = testSnapshot(PlayerProfile(matrix = MatrixProgress(MatrixNodeId.entries.map { if (it.ordinal == 0) rank else 0 })))
        val rankAtBound = ranked(ContentBounds.MAX_MATRIX_NODE_RANKS)
        assertEquals(rankAtBound, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(requireEncoded(rankAtBound))).snapshot)
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, ranked(ContentBounds.MAX_MATRIX_NODE_RANKS + 1))
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, ranked(-1))
        assertEncodeRejection(
            ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            testSnapshot(
                PlayerProfile(collection = PlayerCollection((0..ContentBounds.MAX_ITEMS).toSet())),
            ),
        )
        val heirlooms = RelicId.entries.take(MAX_HEIRLOOM_RELICS)
        val atBound = testSnapshot(PlayerProfile(collection = PlayerCollection(emptySet(), heirloomRelicIds = heirlooms)))
        assertEquals(atBound, assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(requireEncoded(atBound))).snapshot)
        assertEncodeRejection(
            ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            testSnapshot(PlayerProfile(collection = PlayerCollection(
                emptySet(),
                heirloomRelicIds = RelicId.entries.take(MAX_HEIRLOOM_RELICS + 1),
            ))),
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
                architectVictories = 12,
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

    @Test
    fun theSixNewCharactersHaveStableWireIds() {
        val wire = mapOf(
            CoreShape.STAR to "STAR", CoreShape.SPIRAL to "SPIRAL", CoreShape.DIPOLE to "DIPOLE",
            CoreShape.PENTAGON to "PENTAGON", CoreShape.CRESCENT to "CRESCENT", CoreShape.HEXAGON to "HEXAGON",
        )
        wire.forEach { (shape, id) ->
            val encoded = requireEncoded(testSnapshot(PlayerProfile(loadout = PlayerLoadout(coreShape = shape))))
            assertTrue("\"coreShapeId\":\"$id\"" in encoded, encoded)
            assertEquals(shape, decoded(encoded).profile.loadout.coreShape)
        }
        val unknown = DEFAULT_GOLDEN.replace("\"coreShapeId\":\"ORB\"", "\"coreShapeId\":\"NONAGON\"")
        assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, decodeRejection(unknown))
    }

    @Test
    fun labEraSavesRefundTheirWholeSpendAndReEncodeWithoutTheLab() {
        val refunded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(LAB_REPRESENTATIVE_GOLDEN)).snapshot
        assertEquals(PlayerEconomy(4_321L + 387L, 98_765L), refunded.profile.economy)
        assertEquals(MatrixProgress(), refunded.profile.matrix)
        assertEquals(assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(REPRESENTATIVE_GOLDEN)).snapshot, refunded)
        assertEquals(REPRESENTATIVE_GOLDEN, requireEncoded(refunded))

        val labDefault = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(LAB_DEFAULT_GOLDEN)).snapshot
        assertEquals(testSnapshot(), labDefault)
        assertEquals(DEFAULT_GOLDEN, requireEncoded(labDefault))

        // Lifetime Matter never ends below the refunded balance, and the refund saturates.
        val lifetimeAtBalance = LAB_REPRESENTATIVE_GOLDEN.replace("\"lifetimeMatter\":\"98765\"", "\"lifetimeMatter\":\"4321\"")
        assertEquals(PlayerEconomy(4_708L, 4_708L), decoded(lifetimeAtBalance).profile.economy)
        val nearlyFull = LAB_REPRESENTATIVE_GOLDEN
            .replace("\"matter\":\"4321\"", "\"matter\":\"${Long.MAX_VALUE - 10}\"")
            .replace("\"lifetimeMatter\":\"98765\"", "\"lifetimeMatter\":\"${Long.MAX_VALUE}\"")
        assertEquals(PlayerEconomy(Long.MAX_VALUE, Long.MAX_VALUE), decoded(nearlyFull).profile.economy)

        // Every Lab rank at its maximum returns the whole 88 690 Matter the Lab ever cost.
        val maxed = LAB_DEFAULT_GOLDEN.replace(LAB_ZERO_RANKS, LAB_MAX_RANKS)
        assertEquals(PlayerEconomy(88_690L, 88_690L), decoded(maxed).profile.economy)
    }

    @Test
    fun theRetiredLabKeepsItsFrozenValidationAndNeverSharesASaveWithTheMatrix() {
        val labRanks = "{\"id\":\"ARMORY_LICENSE\",\"rank\":0}"
        listOf(
            LAB_DEFAULT_GOLDEN.replace(labRanks, "{\"id\":\"ARMORY_LICENSE\",\"rank\":13}") to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            LAB_DEFAULT_GOLDEN.replace(labRanks, "{\"id\":\"ARMORY_LICENSE\",\"rank\":-1}") to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            LAB_DEFAULT_GOLDEN.replace(labRanks, "{\"id\":\"ARMORY_LICENSES\",\"rank\":0}") to ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            LAB_DEFAULT_GOLDEN.replace("$labRanks,", "") to ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            LAB_DEFAULT_GOLDEN.replace("$labRanks,", "").replace("{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0}",
                "{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0},$labRanks") to ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
        ).forEach { (payload, expected) -> assertEquals(expected, decodeRejection(payload), payload) }
        // The rank at each frozen maximum is still accepted.
        assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(LAB_DEFAULT_GOLDEN.replace(labRanks, "{\"id\":\"ARMORY_LICENSE\",\"rank\":12}")))

        val both = LAB_DEFAULT_GOLDEN.replace(
            "\"collection\":",
            "\"matrix\":{\"ranks\":[{\"id\":\"IMPACT_DRIVER\",\"rank\":1}]},\"collection\":",
        )
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(both))
    }

    @Test
    fun matrixRanksAndCountersHaveACanonicalGoldenAndStrictIngress() {
        val snapshot = decoded(MATRIX_GOLDEN)
        val profile = snapshot.profile
        assertEquals(3, profile.matrix.rank(MatrixNodeId.IMPACT_DRIVER))
        assertEquals(1, profile.matrix.rank(MatrixNodeId.DATA_ARCHIVE))
        assertEquals(1, profile.matrix.rank(MatrixNodeId.SECOND_WIND))
        assertEquals(3, profile.matrix.ownedNodeCount)
        assertEquals(1_500L, profile.characterAchievements.totalKills)
        assertEquals(40L, profile.characterAchievements.ricochets)
        assertEquals(9_001L, profile.characterAchievements.dataPickups)
        assertEquals(MATRIX_GOLDEN, requireEncoded(snapshot))

        val ranks = "[{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"IMPACT_DRIVER\",\"rank\":3},{\"id\":\"SECOND_WIND\",\"rank\":1}]"
        listOf(
            "[{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"IMPACT_DRIVER\",\"rank\":3},{\"id\":\"SECOND_WIND\",\"rank\":1}]" to
                ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            "[{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"IMPACT_DRIVER\",\"rank\":${ContentBounds.MAX_MATRIX_NODE_RANKS + 1}},{\"id\":\"SECOND_WIND\",\"rank\":1}]" to
                ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            "[{\"id\":\"IMPACT_DRIVER\",\"rank\":3},{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"SECOND_WIND\",\"rank\":1}]" to
                ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            "[{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"SECOND_WIND\",\"rank\":1}]" to
                ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
            "[{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"IMPACT_DRIVER\",\"rank\":3},{\"id\":\"SECOND_WINDS\",\"rank\":1}]" to
                ProfileSnapshotRejection.INVALID_STABLE_ID,
            "[]" to ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
        ).forEach { (replacement, expected) ->
            assertEquals(expected, decodeRejection(MATRIX_GOLDEN.replace(ranks, replacement)), replacement)
        }
        // A zero counter is never written, so an explicit "0" is not canonical; negatives are not decimals here.
        assertEquals(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            decodeRejection(MATRIX_GOLDEN.replace("\"ricochets\":\"40\"", "\"ricochets\":\"0\"")))
        assertEquals(ProfileSnapshotRejection.INVALID_DECIMAL,
            decodeRejection(MATRIX_GOLDEN.replace("\"ricochets\":\"40\"", "\"ricochets\":\"-40\"")))
        // Saves from before the counters keep their bytes and read as zero.
        assertEquals(0L, decoded(DEFAULT_GOLDEN).profile.characterAchievements.totalKills)
    }

    @Test
    fun recentItemIdsRoundTripInAcquisitionOrderWithRepeatsAndOlderSavesReadEmpty() {
        val history = List(MAX_RECENT_ITEM_IDS) { index -> listOf(57, 12, 57, ContentBounds.MAX_ITEMS - 1)[index % 4] }
        val snapshot = testSnapshot(PlayerProfile(collection = PlayerCollection(emptySet(), recentItemIds = history)))
        val encoded = requireEncoded(snapshot)
        assertEquals(snapshot, decoded(encoded))
        assertEquals(history, encoded.extractIntArray("recentItemIds"))
        assertTrue(encoded.contains("\"discoveredItemIds\":[],\"recentItemIds\":[57,12,57,"), encoded)

        for (existing in listOf(DEFAULT_GOLDEN, REPRESENTATIVE_GOLDEN, MATRIX_GOLDEN, UPGRADES_GOLDEN)) {
            assertEquals(emptyList<Int>(), decoded(existing).profile.collection.recentItemIds)
            assertFalse(existing.contains("recentItemIds"))
        }
        // An empty history is never written.
        assertEquals(
            ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            decodeRejection(DEFAULT_GOLDEN.replace("\"discoveredItemIds\":[]}", "\"discoveredItemIds\":[],\"recentItemIds\":[]}")),
        )
        // The field follows the Heirlooms in the collection object.
        val withHeirlooms = REPRESENTATIVE_GOLDEN.replace(
            "\"heirloomRelicIds\":[\"GLASS_COMET\",\"BLINK_DRIVE\"]}",
            "\"heirloomRelicIds\":[\"GLASS_COMET\",\"BLINK_DRIVE\"],\"recentItemIds\":[42,3,42]}",
        )
        assertEquals(listOf(42, 3, 42), decoded(withHeirlooms).profile.collection.recentItemIds)
        assertEquals(withHeirlooms, requireEncoded(decoded(withHeirlooms)))
    }

    @Test
    fun recentItemIdsAcceptSixtyFourInRangeIdsAndRejectOneMoreOrAnIdOutsideTheBounds() {
        val atBound = List(MAX_RECENT_ITEM_IDS) { it }
        val payload = requireEncoded(testSnapshot(PlayerProfile(collection = PlayerCollection(emptySet(), recentItemIds = atBound))))
        val canonical = "\"recentItemIds\":[${atBound.joinToString(",")}]"
        assertTrue(payload.contains(canonical), payload)

        listOf(
            atBound + 0 to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            listOf(ContentBounds.MAX_ITEMS) to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
            listOf(-1) to ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
        ).forEach { (ids, expected) ->
            assertEquals(expected, decodeRejection(payload.replace(canonical, "\"recentItemIds\":[${ids.joinToString(",")}]")), "$ids")
        }
        assertEquals(ProfileSnapshotRejection.MALFORMED_JSON,
            decodeRejection(payload.replace(canonical, "\"recentItemIds\":[\"ORB\"]")))

        listOf(
            List(MAX_RECENT_ITEM_IDS + 1) { 0 },
            listOf(ContentBounds.MAX_ITEMS),
            listOf(-1),
        ).forEach { ids ->
            assertEncodeRejection(
                ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
                testSnapshot(PlayerProfile(collection = PlayerCollection(emptySet(), recentItemIds = ids))),
            )
        }

        // The retired catalog generation never wrote a history.
        val retired = LEGACY_DEFAULT_GOLDEN.replace(
            "\"collection\":{\"discoveredItemIds\":[]}",
            "\"collection\":{\"discoveredItemIds\":[],\"recentItemIds\":[1]}",
        )
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(retired))
    }

    @Test
    fun upgradeLevelsHaveACanonicalGoldenAndOlderSavesReadAsNothingBought() {
        val snapshot = decoded(UPGRADES_GOLDEN)
        val upgrades = snapshot.profile.upgrades
        assertEquals(3, upgrades.characterLevel(CoreShape.ORB))
        assertEquals(1, upgrades.characterLevel(CoreShape.PENTAGON))
        assertEquals(2, upgrades.weaponLevel(WeaponId.ARC_COIL))
        assertEquals(ContentBounds.MAX_UPGRADE_LEVEL, upgrades.weaponLevel(WeaponId.FLUX_WAKE))
        assertEquals(4, (upgrades.characterLevels + upgrades.weaponLevels).count { it > 0 })
        assertEquals(UPGRADES_GOLDEN, requireEncoded(snapshot))

        // Saves from before upgrades keep their bytes and read as nothing bought.
        for (existing in listOf(DEFAULT_GOLDEN, REPRESENTATIVE_GOLDEN, MATRIX_GOLDEN)) {
            val previous = decoded(existing)
            assertEquals(PermanentUpgrades(), previous.profile.upgrades)
            assertEquals(existing, requireEncoded(previous))
        }
        // One track alone writes only its own digits.
        val weaponsOnly = snapshot.copy(profile = snapshot.profile.copy(upgrades = upgrades.copy(
            characterLevels = PermanentUpgrades().characterLevels,
        )))
        val encoded = requireEncoded(weaponsOnly)
        assertTrue(encoded.endsWith("\"upgrades\":{\"w\":\"a0000002\"}}}"), encoded)
        assertEquals(weaponsOnly, decoded(encoded))
        // Nothing bought: the object is absent again.
        assertEquals(
            UPGRADES_GOLDEN.substringBefore(",\"upgrades\"") + "}}",
            requireEncoded(snapshot.copy(profile = snapshot.profile.copy(upgrades = PermanentUpgrades()))),
        )
    }

    @Test
    fun upgradeDigitsKeepTheirFrozenSlotPositions() {
        // Positions are persisted: each character and weapon owns one digit, in this order, forever.
        val characterSlots = listOf(
            "ORB", "PRISM", "SHARD", "RING", "DIAMOND", "TESSERACT", "STAR", "SPIRAL", "DIPOLE", "PENTAGON",
            "CRESCENT", "HEXAGON",
        )
        val weaponSlots = listOf(
            "FLUX_WAKE", "MORNINGSTAR", "PHASE_LATTICE", "NULL_LANCE", "GRAVITY_MINES", "ION_SWARM", "RIFT_BLADES",
            "ARC_COIL", "QUASAR_CANNON", "ENTROPY_FIELD", "SINGULARITY_SPEAR", "PRISM_RELAY", "GYRE_DISC",
            "SCATTER_BLOOM", "TETHER_ARC", "SENTRY_GRID", "HUNTER_SALVO", "CONCUSSION_ENGINE", "GLACIER_BELL",
            "EVENT_LENS", "SOLAR_SWEEP", "AEGIS_SHARDS", "HOLLOW_RIFT", "RESONANCE_DRUM",
        )
        assertEquals(CoreShape.entries.map(CoreShape::name).toSet(), characterSlots.toSet())
        assertEquals(WeaponId.entries.map(WeaponId::name).toSet(), weaponSlots.toSet())
        characterSlots.forEachIndexed { slot, name ->
            val shape = CoreShape.valueOf(name)
            val snapshot = testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
                characterLevels = CoreShape.entries.map { if (it == shape) 7 else 0 },
                weaponLevels = List(WeaponId.entries.size) { 0 },
            )))
            val encoded = requireEncoded(snapshot)
            assertTrue(encoded.endsWith("\"upgrades\":{\"c\":\"${"0".repeat(slot)}7\"}}}"), encoded)
            assertEquals(snapshot, decoded(encoded))
        }
        weaponSlots.forEachIndexed { slot, name ->
            val id = WeaponId.valueOf(name)
            val snapshot = testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
                characterLevels = List(CoreShape.entries.size) { 0 },
                weaponLevels = WeaponId.entries.map { if (it == id) 4 else 0 },
            )))
            val encoded = requireEncoded(snapshot)
            assertTrue(encoded.endsWith("\"upgrades\":{\"w\":\"${"0".repeat(slot)}4\"}}}"), encoded)
            assertEquals(snapshot, decoded(encoded))
        }
    }

    @Test
    fun upgradeDigitsRejectForeignDigitsTrailingZerosExtraSlotsAndExplicitEmptyForms() {
        val characters = "\"c\":\"3000000001\""
        val weapons = "\"w\":\"a0000002\""
        assertTrue(UPGRADES_GOLDEN.contains("\"upgrades\":{$characters,$weapons}"))
        listOf(
            // A trailing zero level is never written.
            Triple(characters, "\"c\":\"30000000010\"", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple(weapons, "\"w\":\"a00000020\"", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            // Only lowercase base-36 digits are levels.
            Triple(characters, "\"c\":\"300000000A\"", ProfileSnapshotRejection.INVALID_DECIMAL),
            Triple(characters, "\"c\":\"3-00000001\"", ProfileSnapshotRejection.INVALID_DECIMAL),
            Triple(weapons, "\"w\":\" a0000002\"", ProfileSnapshotRejection.INVALID_DECIMAL),
            // One digit per slot: 12 characters and 24 weapons.
            Triple(characters, "\"c\":\"${"1".repeat(CoreShape.entries.size + 1)}\"", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple(weapons, "\"w\":\"${"1".repeat(WeaponId.entries.size + 1)}\"", ProfileSnapshotRejection.VALUE_OUT_OF_RANGE),
            Triple(characters, "\"c\":\"\"", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple(weapons, "\"w\":\"\"", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple("{$characters,$weapons}", "{$weapons,$characters}", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple("{$characters,$weapons}", "{}", ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD),
            Triple("{$characters,$weapons}", "null", ProfileSnapshotRejection.MALFORMED_JSON),
            Triple(characters, "\"c\":3000000001", ProfileSnapshotRejection.MALFORMED_JSON),
            Triple(characters, "\"c\":null", ProfileSnapshotRejection.MALFORMED_JSON),
            Triple(characters, "\"characters\":\"3000000001\"", ProfileSnapshotRejection.MALFORMED_JSON),
        ).forEach { (canonical, invalid, expected) ->
            assertTrue(UPGRADES_GOLDEN.contains(canonical), canonical)
            assertEquals(expected, decodeRejection(UPGRADES_GOLDEN.replace(canonical, invalid)), invalid)
        }
        // Every slot filled is the longest form.
        assertEquals(
            CoreShape.entries.size,
            decoded(UPGRADES_GOLDEN.replace(characters, "\"c\":\"${"1".repeat(CoreShape.entries.size)}\""))
                .profile.upgrades.characterLevels.count { it == 1 },
        )
        // Upgrades postdate the retired Lab: a Lab-era save never carries them.
        val labWithUpgrades = LAB_DEFAULT_GOLDEN.removeSuffix("}}") + ",\"upgrades\":{$characters}}}"
        assertEquals(ProfileSnapshotRejection.INCONSISTENT_PROFILE, decodeRejection(labWithUpgrades))
    }

    @Test
    fun upgradeLevelsAcceptTenAndRejectEleven() {
        fun leveled(character: Int = 0, weapon: Int = 0) = testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
            characterLevels = CoreShape.entries.map { if (it == CoreShape.HEXAGON) character else 0 },
            weaponLevels = WeaponId.entries.map { if (it == WeaponId.RESONANCE_DRUM) weapon else 0 },
        )))
        val atBound = leveled(ContentBounds.MAX_UPGRADE_LEVEL, ContentBounds.MAX_UPGRADE_LEVEL)
        val encoded = requireEncoded(atBound)
        assertEquals(atBound, decoded(encoded))
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, leveled(character = ContentBounds.MAX_UPGRADE_LEVEL + 1))
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, leveled(weapon = ContentBounds.MAX_UPGRADE_LEVEL + 1))
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, leveled(character = -1))
        assertEncodeRejection(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, leveled(weapon = -1))

        // Level 10 is the base-36 digit "a"; "b" (11) and every later digit are above the maximum.
        val hexagon = "\"c\":\"${"0".repeat(CoreShape.entries.size - 1)}a\""
        val drum = "\"w\":\"${"0".repeat(WeaponId.entries.size - 1)}a\""
        assertTrue(encoded.contains(hexagon) && encoded.contains(drum), encoded)
        for (invalid in listOf('b', 'z')) {
            assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
                decodeRejection(encoded.replace(hexagon, hexagon.replace('a', invalid))), "$invalid")
            assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
                decodeRejection(encoded.replace(drum, drum.replace('a', invalid))), "$invalid")
        }

        // Every track at the maximum round-trips as one digit per slot; lists sized off the closed catalogs are refused.
        val maximum = testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
            characterLevels = List(CoreShape.entries.size) { ContentBounds.MAX_UPGRADE_LEVEL },
            weaponLevels = List(WeaponId.entries.size) { ContentBounds.MAX_UPGRADE_LEVEL },
        )))
        val maximumPayload = requireEncoded(maximum)
        assertEquals(maximum, decoded(maximumPayload))
        assertTrue(
            maximumPayload.endsWith("\"upgrades\":{\"c\":\"${"a".repeat(CoreShape.entries.size)}\"," +
                "\"w\":\"${"a".repeat(WeaponId.entries.size)}\"}}}"),
            maximumPayload,
        )
        assertEncodeRejection(ProfileSnapshotRejection.INCONSISTENT_PROFILE, testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
            characterLevels = List(CoreShape.entries.size + 1) { 0 },
            weaponLevels = List(WeaponId.entries.size) { 0 },
        ))))
        assertEncodeRejection(ProfileSnapshotRejection.INCONSISTENT_PROFILE, testSnapshot(PlayerProfile(upgrades = PermanentUpgrades(
            characterLevels = List(CoreShape.entries.size) { 0 },
            weaponLevels = List(WeaponId.entries.size - 1) { 0 },
        ))))
    }

    private fun decoded(payload: String) = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload)).snapshot

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

/** Every display and controls preference at a non-default choice; Dash takes Z, so Skill 1 takes Space. */
private fun everyDisplayChoice(preferences: PlayerPreferences): PlayerPreferences = preferences.copy(
    windowMode = WindowMode.BORDERLESS,
    windowSize = WindowSizePreset.W1920X1080,
    vsync = false,
    frameRateCap = 144,
    refreshRateHz = 120,
    immersive = true,
    interfaceScalePercent = 115,
    showFps = true,
    pauseOnFocusLoss = false,
    reactionEffects = ReactionEffectsLevel.MINIMAL,
    keyBindings = KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.Z).with(GameAction.APEX, BindableKey.F),
)

/** [everyDisplayChoice] on the wire: appended after the existing preferences in declaration order. */
private const val EVERY_DISPLAY_FIELD: String =
    ",\"windowModeId\":\"BORDERLESS\",\"windowSizeId\":\"W1920X1080\",\"vsyncEnabled\":false,\"frameRateCap\":144," +
        "\"refreshRateHz\":120,\"immersiveEnabled\":true,\"interfaceScalePercent\":115,\"showFps\":true," +
        "\"pauseOnFocusLoss\":false,\"reactionEffectsId\":\"MINIMAL\",\"keyBindings\":[{\"action\":\"APEX\",\"key\":\"F\"}," +
        "{\"action\":\"DASH\",\"key\":\"Z\"},{\"action\":\"SKILL_1\",\"key\":\"SPACE\"}]"

private fun PlayerPreferences.displayAndControls(): List<Any> = listOf(
    windowMode, windowSize, vsync, frameRateCap, refreshRateHz, immersive, interfaceScalePercent, showFps,
    pauseOnFocusLoss, reactionEffects, keyBindings,
)

private val ACTION_ID = Regex("\\{\\\"action\\\":\\\"([^\\\"]+)\\\",\\\"key\\\":")

private val RANK_ID = Regex("\\{\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"rank\\\":")

/** A fresh profile: the retired Lab is never written and an empty Matrix and zero counters are omitted. */
private const val DEFAULT_GOLDEN: String =
    "{\"revision\":\"0\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false}," +
        "\"economy\":{\"matter\":\"0\",\"lifetimeMatter\":\"0\"}," +
        "\"loadout\":{\"coreShapeId\":\"ORB\",\"selectedWeaponId\":\"FLUX_WAKE\"," +
        "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]}," +
        "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":0,\"highestCleared\":-1}," +
        "\"characterAchievements\":{\"eliteKills\":\"0\",\"dashHits\":\"0\",\"completedOrbits\":\"0\"," +
        "\"architectVictories\":\"0\",\"victoriousCharacterIds\":[]}}}"

/**
 * A current save with Refit (Orb 3, Pentagon 1) and Honing (Arc Coil 2, Flux Wake at the maximum 10 = "a"):
 * one base-36 digit per slot without trailing zeros, the object last.
 */
private const val UPGRADES_GOLDEN: String =
    "{\"revision\":\"77\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false}," +
        "\"economy\":{\"matter\":\"120\",\"lifetimeMatter\":\"60000\"}," +
        "\"loadout\":{\"coreShapeId\":\"PENTAGON\",\"selectedWeaponId\":\"ARC_COIL\"," +
        "\"unlockedWeaponIds\":[\"ARC_COIL\",\"FLUX_WAKE\"]}," +
        "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":3,\"highestCleared\":2}," +
        "\"characterAchievements\":{\"eliteKills\":\"0\",\"dashHits\":\"0\",\"completedOrbits\":\"0\"," +
        "\"architectVictories\":\"0\",\"victoriousCharacterIds\":[]}," +
        "\"upgrades\":{\"c\":\"3000000001\",\"w\":\"a0000002\"}}}"

/** Byte-exact default save of the current catalog written before the Core Matrix (it carries the Lab). */
private const val LAB_DEFAULT_GOLDEN: String =
    "{\"revision\":\"0\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false}," +
        "\"economy\":{\"matter\":\"0\",\"lifetimeMatter\":\"0\"}," +
        "\"loadout\":{\"coreShapeId\":\"ORB\",\"selectedWeaponId\":\"FLUX_WAKE\"," +
        "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]},\"labProgress\":{\"ranks\":[" +
        "{\"id\":\"ARMORY_LICENSE\",\"rank\":0},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":0}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":0}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":0},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0}]}," +
        "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":0,\"highestCleared\":-1}," +
        "\"characterAchievements\":{\"eliteKills\":\"0\",\"dashHits\":\"0\",\"completedOrbits\":\"0\"," +
        "\"architectVictories\":\"0\",\"victoriousCharacterIds\":[]}}}"

private const val EMPTY_CURRENT_COLLECTION: String = "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[]}"

private const val REPRESENTATIVE_COLLECTION: String =
    "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[3,7,42],\"newItemIds\":[42]," +
        "\"discoveredRelicIds\":[\"BLINK_DRIVE\",\"GLASS_COMET\",\"OUROBOROS_SEAL\"],\"newRelicIds\":[\"OUROBOROS_SEAL\"]," +
        "\"discoveredReactionIds\":[\"EMBRITTLE\",\"THERMAL_SHOCK\"],\"discoveredPairingIds\":[\"HEAVY_BLINK\"]," +
        "\"heirloomRelicIds\":[\"GLASS_COMET\",\"BLINK_DRIVE\"]}"

/** [LAB_REPRESENTATIVE_GOLDEN] after its Lab refund: the same player, 4321 + 387 = 4708 Matter, no Matrix yet. */
private const val REPRESENTATIVE_GOLDEN: String =
    "{\"revision\":\"2718\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":false,\"musicEnabled\":true,\"masterVolumePercent\":37," +
        "\"simulationSpeedPercent\":135,\"textScalePercent\":150,\"screenShake\":false," +
        "\"particleDensityId\":\"LOW\",\"damageNumbers\":true,\"damageNumberSizeId\":\"HUGE\"," +
        "\"damageNumberFormatId\":\"FULL\",\"damageNumberTierThreshold\":2500,\"languageCode\":\"en\",\"runStatisticsOnLeft\":true}," +
        "\"economy\":{\"matter\":\"4708\",\"lifetimeMatter\":\"98765\"}," +
        "\"loadout\":{\"coreShapeId\":\"RING\",\"selectedWeaponId\":\"ARC_COIL\"," +
        "\"unlockedWeaponIds\":[\"ARC_COIL\",\"FLUX_WAKE\",\"MORNINGSTAR\"]}," +
        REPRESENTATIVE_COLLECTION + "," +
        "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}," +
        "\"characterAchievements\":{\"eliteKills\":\"12\",\"dashHits\":\"340\",\"completedOrbits\":\"5\"," +
        "\"architectVictories\":\"2\",\"victoriousCharacterIds\":[\"ORB\",\"RING\"]}}}"

/** A current save with Matrix ranks (sorted by wire id, owned nodes only) and the cumulative counters. */
private const val MATRIX_GOLDEN: String =
    "{\"revision\":\"31\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false}," +
        "\"economy\":{\"matter\":\"55\",\"lifetimeMatter\":\"2000\"}," +
        "\"loadout\":{\"coreShapeId\":\"ORB\",\"selectedWeaponId\":\"FLUX_WAKE\"," +
        "\"unlockedWeaponIds\":[\"FLUX_WAKE\"]}," +
        "\"matrix\":{\"ranks\":[{\"id\":\"DATA_ARCHIVE\",\"rank\":1},{\"id\":\"IMPACT_DRIVER\",\"rank\":3}," +
        "{\"id\":\"SECOND_WIND\",\"rank\":1}]}," +
        "\"collection\":{\"itemCatalogEpoch\":2,\"discoveredItemIds\":[]}," +
        "\"rebirthProgress\":{\"level\":1,\"highestCleared\":0}," +
        "\"characterAchievements\":{\"eliteKills\":\"0\",\"dashHits\":\"0\",\"completedOrbits\":\"0\"," +
        "\"architectVictories\":\"0\",\"victoriousCharacterIds\":[],\"totalKills\":\"1500\",\"ricochets\":\"40\"," +
        "\"dataPickups\":\"9001\"}}}"

/**
 * Byte-exact current-catalog save written before the Core Matrix and before Color vision. Its Lab
 * ranks (Armory 1, Dash 1, Kinetic 1, Magnetic 2, Salvage 2) refund 45 + 30 + 22 + 120 + 170 = 387 Matter.
 */
private const val LAB_REPRESENTATIVE_GOLDEN: String =
    "{\"revision\":\"2718\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":false,\"musicEnabled\":true,\"masterVolumePercent\":37," +
        "\"simulationSpeedPercent\":135,\"textScalePercent\":150,\"screenShake\":false," +
        "\"particleDensityId\":\"LOW\",\"damageNumbers\":true,\"damageNumberSizeId\":\"HUGE\"," +
        "\"damageNumberFormatId\":\"FULL\",\"damageNumberTierThreshold\":2500,\"languageCode\":\"en\",\"runStatisticsOnLeft\":true}," +
        "\"economy\":{\"matter\":\"4321\",\"lifetimeMatter\":\"98765\"}," +
        "\"loadout\":{\"coreShapeId\":\"RING\",\"selectedWeaponId\":\"ARC_COIL\"," +
        "\"unlockedWeaponIds\":[\"ARC_COIL\",\"FLUX_WAKE\",\"MORNINGSTAR\"]},\"labProgress\":{\"ranks\":[" +
        "{\"id\":\"ARMORY_LICENSE\",\"rank\":1},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":1}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":1}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":2},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":2}]}," +
        REPRESENTATIVE_COLLECTION + "," +
        "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}," +
        "\"characterAchievements\":{\"eliteKills\":\"12\",\"dashHits\":\"340\",\"completedOrbits\":\"5\"," +
        "\"architectVictories\":\"2\",\"victoriousCharacterIds\":[\"ORB\",\"RING\"]}}}"

/** Byte-exact default save of the retired 400-item catalog, written before the catalog epoch existed. */
private const val LEGACY_DEFAULT_GOLDEN: String =
    "{\"revision\":\"0\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":true,\"musicEnabled\":true,\"masterVolumePercent\":65," +
        "\"simulationSpeedPercent\":100,\"textScalePercent\":125,\"screenShake\":true," +
        "\"particleDensityId\":\"NORMAL\",\"damageNumbers\":true,\"damageNumberSizeId\":\"NORMAL\"," +
        "\"damageNumberFormatId\":\"COMPACT\",\"damageNumberTierThreshold\":50,\"languageCode\":\"ru\",\"runStatisticsOnLeft\":false}," +
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

private const val LEGACY_REPRESENTATIVE_COLLECTION: String =
    "\"collection\":{\"discoveredItemIds\":[3,7,42],\"newItemIds\":[42]," +
        "\"discoveredRelicIds\":[\"GHOST_VECTOR\",\"MASS_ECHO\"],\"newRelicIds\":[\"MASS_ECHO\"]}"

/** Byte-exact save of the retired catalog generation, produced before Color vision existed. */
private const val LEGACY_REPRESENTATIVE_GOLDEN: String =
    "{\"revision\":\"2718\",\"profile\":{" +
        "\"preferences\":{\"soundEnabled\":false,\"musicEnabled\":true,\"masterVolumePercent\":37," +
        "\"simulationSpeedPercent\":135,\"textScalePercent\":150,\"screenShake\":false," +
        "\"particleDensityId\":\"LOW\",\"damageNumbers\":true,\"damageNumberSizeId\":\"HUGE\"," +
        "\"damageNumberFormatId\":\"FULL\",\"damageNumberTierThreshold\":2500,\"languageCode\":\"en\",\"runStatisticsOnLeft\":true}," +
        "\"economy\":{\"matter\":\"4321\",\"lifetimeMatter\":\"98765\"}," +
        "\"loadout\":{\"coreShapeId\":\"RING\",\"selectedWeaponId\":\"ARC_COIL\"," +
        "\"unlockedWeaponIds\":[\"ARC_COIL\",\"FLUX_WAKE\",\"MORNINGSTAR\"]},\"labProgress\":{\"ranks\":[" +
        "{\"id\":\"ARMORY_LICENSE\",\"rank\":1},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":1}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":1}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":2},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":2}]}," +
        "\"collection\":{\"discoveredItemIds\":[3,7,42],\"newItemIds\":[42]," +
        "\"discoveredRelicIds\":[\"GHOST_VECTOR\",\"MASS_ECHO\"],\"newRelicIds\":[\"MASS_ECHO\"]}," +
        "\"rebirthProgress\":{\"level\":4,\"highestCleared\":3}," +
        "\"characterAchievements\":{\"eliteKills\":\"12\",\"dashHits\":\"340\",\"completedOrbits\":\"5\"," +
        "\"architectVictories\":\"2\",\"victoriousCharacterIds\":[\"ORB\",\"RING\"]}}}"

/** Every Relic name of the retired 40-Relic catalog, sorted as that codec wrote them. */
private val RETIRED_RELIC_IDS: List<String> = listOf(
    "AGONY_SCEPTER", "BORROWED_MOMENT", "BRAKEPOINT_MEMORY", "CHROMA_FEEDBACK", "CIRCUIT_BREAKER",
    "CROWN_OF_FOUR_WINDS", "CRUSH_DEPTH", "DEVOURERS_TOLL", "DOOM_CLOCK", "ECHO_CHAMBER",
    "ENGINE_OF_PARADOX", "EVENTIDE_ANCHOR", "FRACTURE_GATE", "FRACTURE_LENS", "GHOST_VECTOR",
    "GLASS_WITNESS", "HARDLIGHT_EDGE", "HEAT_DEBT", "ION_DEBT", "KINETIC_FLYWHEEL",
    "LAST_LIGHT", "MASS_ECHO", "MIRROR_CUT", "MIRROR_OF_THE_HUNT", "ORBITAL_NAIL",
    "OVERTAKE_PROTOCOL", "PALIMPSEST_ROUND", "PERIAPSIS_HOOK", "POLARITY_SLING", "QUIETUS_BLOOM",
    "RETURN_CIRCUIT", "SCAR_TISSUE", "SECOND_HAND", "SLIPSTREAM_RELAY", "SPECTRAL_FAN",
    "SPLIT_HORIZON", "STATIC_CHORUS", "STORM_INDEX", "TIDAL_LOCK", "VOLTAIC_FILAMENT",
)

private const val LAB_ZERO_RANKS: String =
    "{\"id\":\"ARMORY_LICENSE\",\"rank\":0},{\"id\":\"CORE_INTEGRITY\",\"rank\":0}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":0},{\"id\":\"DASH_CAPACITOR\",\"rank\":0}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":0},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":0}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":0},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":0}"

private const val LAB_MAX_RANKS: String =
    "{\"id\":\"ARMORY_LICENSE\",\"rank\":12},{\"id\":\"CORE_INTEGRITY\",\"rank\":10}," +
        "{\"id\":\"CRYO_VENTS\",\"rank\":8},{\"id\":\"DASH_CAPACITOR\",\"rank\":8}," +
        "{\"id\":\"DATA_ARCHIVE\",\"rank\":10},{\"id\":\"KINETIC_AMPLIFIER\",\"rank\":10}," +
        "{\"id\":\"MAGNETIC_RESONANCE\",\"rank\":8},{\"id\":\"SALVAGE_PROTOCOL\",\"rank\":10}"
