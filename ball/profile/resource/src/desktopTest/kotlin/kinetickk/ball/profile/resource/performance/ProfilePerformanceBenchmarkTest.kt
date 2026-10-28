// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource.performance

import kinetickk.ball.profile.resource.MAX_PROFILE_PAYLOAD_BYTES
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.resource.ProfileCodec
import kinetickk.ball.profile.resource.ProfileDecodeResult
import kinetickk.ball.profile.resource.ProfileEncodeResult
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.performance.validateBenchmarkScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfilePerformanceBenchmarkTest {
    @Test
    fun suiteIdentityVersionsTheWorkloadWithoutVersioningTheSaveSchema() {
        assertEquals(
            "profile-persistence-current-schema-v6",
            PROFILE_PERSISTENCE_BENCHMARK_SUITE_VERSION,
        )
    }

    @Test
    fun businessMaximumCoversEveryBoundedProfileFactAndRoundTrips() {
        val snapshot = maximumBusinessSnapshot()
        assertEquals(AppLanguage.English, snapshot.profile.preferences.language)
        val preferences = snapshot.profile.preferences
        assertTrue(GameAction.entries.none { preferences.keyBindings.key(it) == KeyBindings.DEFAULT.key(it) })
        assertEquals(FRAME_RATE_CAP_OPTIONS.last(), preferences.frameRateCap)
        assertEquals(REFRESH_RATE_RANGE.last, preferences.refreshRateHz)
        assertEquals(INTERFACE_SCALE_OPTIONS.last(), preferences.interfaceScalePercent)
        assertEquals(CoreShape.entries.toSet(), snapshot.profile.rebirthProgress.ascendedCharacters)
        assertTrue(snapshot.profile.rebirthProgress.characterClears.all { it == ContentBounds.MAX_REBIRTH_LEVEL })
        assertTrue(
            (snapshot.profile.upgrades.characterLevels + snapshot.profile.upgrades.weaponLevels)
                .all { it == ContentBounds.MAX_UPGRADE_LEVEL },
        )
        assertEquals(
            CharacterAchievementProgress(
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                CoreShape.entries.toImmutableSet(), Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
            ),
            snapshot.profile.characterAchievements,
        )
        val collection = snapshot.profile.collection
        assertEquals(ContentBounds.MAX_ITEMS, collection.discoveredItemIds.size)
        assertEquals(collection.discoveredItemIds, collection.newItemIds)
        assertEquals(RelicId.entries.toSet(), collection.discoveredRelicIds)
        assertEquals(RelicId.entries.toSet(), collection.newRelicIds)
        assertEquals(ReactionId.entries.toSet(), collection.discoveredReactionIds)
        assertEquals(RelicPairingId.entries.toSet(), collection.discoveredPairingIds)
        assertEquals(WorldDiscovery.entries.toSet(), collection.discoveredWorldEntries)
        assertEquals(MAX_HEIRLOOM_RELICS, collection.heirloomRelicIds.size)
        assertEquals(MAX_RECENT_ITEM_IDS, collection.recentItemIds.size)
        val payload = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(snapshot)).payload
        listOf(
            "colorVisionId", "minimapEnabled", "windowModeId", "windowSizeId", "vsyncEnabled", "frameRateCap",
            "refreshRateHz", "immersiveEnabled", "interfaceScalePercent", "showFps", "pauseOnFocusLoss",
            "reactionEffectsId", "keyBindings", "recentItemIds", "ascendedCharacterIds", "characterClears", "upgrades",
            "worldDiscoveryMask",
        ).forEach { field -> assertTrue(payload.contains("\"$field\":"), field) }
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload)).snapshot
        assertEquals(snapshot, decoded)
        assertEquals(snapshotSignature(snapshot), snapshotSignature(decoded))
    }

    @Test
    fun snapshotWitnessDetectsLanguageAchievementAndReleaseFiveFactChanges() {
        val snapshot = ProfileSnapshot(ProfileRevision.ZERO, PlayerProfile())
        val profile = snapshot.profile
        val changed = listOf(
            profile.copy(preferences = profile.preferences.copy(language = AppLanguage.English)),
            profile.copy(characterAchievements = CharacterAchievementProgress(eliteKills = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(dashHits = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(completedOrbits = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(architectVictories = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(totalKills = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(ricochets = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(dataPickups = 1)),
            profile.copy(matrix = kinetickk.ball.profile.api.MatrixProgress(
                kinetickk.ball.content.api.MatrixNodeId.entries.map { if (it.ordinal == 0) 1 else 0 },
            )),
            profile.copy(characterAchievements = CharacterAchievementProgress(
                victoriousCharacters = setOf(CoreShape.SHARD).toImmutableSet(),
            )),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = setOf(1),
                newItemIds = setOf(1),
            )),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = emptySet(),
                discoveredRelicIds = setOf(RelicId.GLASS_COMET),
            )),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = emptySet(),
                discoveredReactionIds = setOf(ReactionId.entries.first()),
            )),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = emptySet(),
                discoveredPairingIds = setOf(RelicPairingId.entries.first()),
            )),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = emptySet(),
                heirloomRelicIds = listOf(RelicId.entries.first()),
            )),
            profile.copy(collection = profile.collection.copy(recentItemIds = listOf(1).toImmutableList())),
            profile.copy(collection = PlayerCollection(
                discoveredItemIds = emptySet(),
                discoveredWorldEntries = setOf(WorldDiscovery.entries.first()),
            )),
            profile.copy(rebirthProgress = profile.rebirthProgress.copy(
                ascendedCharacters = setOf(CoreShape.SHARD).toImmutableSet(),
            )),
            profile.copy(upgrades = PermanentUpgrades(
                characterLevels = CoreShape.entries.map { if (it.ordinal == 0) 1 else 0 },
                weaponLevels = WeaponId.entries.map { 0 },
            )),
            profile.copy(upgrades = PermanentUpgrades(
                characterLevels = CoreShape.entries.map { 0 },
                weaponLevels = WeaponId.entries.map { if (it.ordinal == 0) 1 else 0 },
            )),
            profile.copy(preferences = profile.preferences.copy(windowMode = WindowMode.FULLSCREEN)),
            profile.copy(preferences = profile.preferences.copy(frameRateCap = FRAME_RATE_CAP_OPTIONS[1])),
            profile.copy(preferences = profile.preferences.copy(showFps = true)),
            profile.copy(preferences = profile.preferences.copy(reactionEffects = ReactionEffectsLevel.REDUCED)),
            profile.copy(preferences = profile.preferences.copy(
                keyBindings = KeyBindings.DEFAULT.with(GameAction.SKILL_1, BindableKey.X),
            )),
        )
        changed.forEach { changedProfile ->
            assertNotEquals(snapshotSignature(snapshot), snapshotSignature(snapshot.copy(profile = changedProfile)))
        }
    }

    @Test
    fun suiteCoversCodecLimitsRejectionsAndInMemoryResourcePaths() {
        val scenarios = profileBenchmarkScenarios()
        val names = scenarios.map { scenario -> scenario.name }

        assertEquals(names.distinct(), names)
        assertEquals(
            setOf(
                "profile_harness_control",
                "profile_encode_default",
                "profile_decode_default",
                "profile_roundtrip_default",
                "profile_encode_business_maximum",
                "profile_decode_business_maximum",
                "profile_roundtrip_business_maximum",
                "profile_decode_malformed_rejection",
                "profile_decode_oversize_rejection",
                "profile_decode_unknown_field_rejection",
                "profile_decode_noncanonical_rejection",
                "profile_decode_invalid_utf8_rejection",
                "profile_resource_read_empty",
                "profile_resource_read_default",
                "profile_resource_read_business_maximum",
                "profile_resource_read_malformed_rejection",
                "profile_resource_write_readback_default",
                "profile_resource_write_readback_business_maximum",
            ),
            names.toSet(),
        )

        val logicalNames = setOf(
            "profile_encode_default",
            "profile_decode_default",
            "profile_roundtrip_default",
            "profile_encode_business_maximum",
            "profile_decode_business_maximum",
            "profile_roundtrip_business_maximum",
        )
        scenarios.filter { scenario -> scenario.name in logicalNames }.forEach { scenario ->
            assertEquals(
                "current-schema-logical-profile",
                scenario.metadata["comparisonContract"],
                scenario.name,
            )
            assertEquals("strict-current", scenario.metadata["wireFormat"], scenario.name)
            assertTrue(
                requireNotNull(scenario.metadata["payloadBytes"]).toInt() < MAX_PROFILE_PAYLOAD_BYTES,
                scenario.name,
            )
        }

        scenarios.filter { scenario -> scenario.category == "resource" }.forEach { scenario ->
            assertEquals("exact-in-memory", scenario.metadata["provider"], scenario.name)
        }
        scenarios.forEach { scenario ->
            validateBenchmarkScenario(scenario)
        }
    }
}
