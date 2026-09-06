// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource.performance

import kinetickk.ball.profile.resource.MAX_PROFILE_PAYLOAD_BYTES
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.resource.ProfileCodec
import kinetickk.ball.profile.resource.ProfileDecodeResult
import kinetickk.ball.profile.resource.ProfileEncodeResult
import kinetickk.foundation.collections.toImmutableSet
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
            "profile-persistence-current-schema-v3",
            PROFILE_PERSISTENCE_BENCHMARK_SUITE_VERSION,
        )
    }

    @Test
    fun businessMaximumCoversLanguageAndEveryCharacterAchievementAndRoundTrips() {
        val snapshot = maximumBusinessSnapshot()
        assertEquals(AppLanguage.English, snapshot.profile.preferences.language)
        assertEquals(
            CharacterAchievementProgress(
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                CoreShape.entries.toImmutableSet(),
            ),
            snapshot.profile.characterAchievements,
        )
        val payload = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(snapshot)).payload
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(payload)).snapshot
        assertEquals(snapshot, decoded)
        assertEquals(snapshotSignature(snapshot), snapshotSignature(decoded))
    }

    @Test
    fun snapshotWitnessDetectsLanguageAndIndividualAchievementChanges() {
        val snapshot = ProfileSnapshot(ProfileRevision.ZERO, PlayerProfile())
        val profile = snapshot.profile
        val changed = listOf(
            profile.copy(preferences = profile.preferences.copy(language = AppLanguage.English)),
            profile.copy(characterAchievements = CharacterAchievementProgress(eliteKills = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(dashHits = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(completedOrbits = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(architectVictories = 1)),
            profile.copy(characterAchievements = CharacterAchievementProgress(
                victoriousCharacters = setOf(CoreShape.SHARD).toImmutableSet(),
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
