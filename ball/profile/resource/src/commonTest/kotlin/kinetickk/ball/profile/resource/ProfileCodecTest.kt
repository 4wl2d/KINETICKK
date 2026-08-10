// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCodecTest {
    @Test
    fun defaultProfileHasStableV3GoldenAndRoundTrips() {
        val profile = PlayerProfile()
        val encoded = encodeProfile(profile)

        assertEquals(
            "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125,1,0,50|0|-1",
            encoded,
        )
        assertEquals(profile, decodeProfile(encoded))
    }

    @Test
    fun representativeProfileHasStableV3Golden() {
        val profile = PlayerProfile(
            preferences = PlayerPreferences(
                soundEnabled = false,
                musicEnabled = true,
                masterVolume = 0.5f,
                simulationSpeed = 1.5f,
                textScale = 1.4f,
                screenShake = false,
                particleDensity = ParticleDensity.HIGH,
                damageNumbers = false,
                damageNumberSize = DamageNumberSize.LARGE,
                damageNumberFormat = DamageNumberFormat.FULL,
                damageNumberTierThreshold = 2_500,
            ),
            economy = PlayerEconomy(75L, 100L),
            loadout = PlayerLoadout(
                CoreShape.SHARD,
                WeaponId.NULL_LANCE,
                setOf(WeaponId.FLUX_WAKE, WeaponId.PHASE_LATTICE),
            ),
            labProgress = LabProgress((1..8).toList()),
            collection = PlayerCollection(setOf(399, 0)),
            rebirthProgress = RebirthProgress(3, 2),
        )

        val encoded = encodeProfile(profile)

        assertEquals(
            "3|75|100|2|3|5|1,2,3,4,5,6,7,8|0,399|0,1,50,150,0,2,0,140,2,1,2500|3|2",
            encoded,
        )
        assertEquals(profile, decodeProfile(encoded))
    }

    @Test
    fun richProfileRoundTripsWithoutLosingCatalogState() {
        val profile = PlayerProfile(
            preferences = PlayerPreferences(
                soundEnabled = false,
                musicEnabled = false,
                masterVolume = 0.5f,
                simulationSpeed = 1.5f,
                textScale = 1.42f,
                screenShake = false,
                particleDensity = ParticleDensity.HIGH,
                damageNumbers = false,
                damageNumberSize = DamageNumberSize.HUGE,
                damageNumberFormat = DamageNumberFormat.FULL,
                damageNumberTierThreshold = 2_500,
            ),
            economy = PlayerEconomy(9_876_543_210L, 12_345_678_901L),
            loadout = PlayerLoadout(
                CoreShape.SHARD,
                WeaponId.SINGULARITY_SPEAR,
                WeaponId.entries.toSet(),
            ),
            labProgress = LabProgress(TestProfilePolicy.metaUpgrades.map { it.maxRanks }),
            collection = PlayerCollection((0 until TestProfilePolicy.itemCount).toSet()),
            rebirthProgress = RebirthProgress(7, 6),
        )

        val decoded = decodeProfile(encodeProfile(profile))

        assertEquals(profile, decoded)
        assertEquals(TestProfilePolicy.itemCount, decoded?.collection?.discoveredItemIds?.size)
        assertEquals(WeaponId.entries.size, decoded?.loadout?.unlockedWeapons?.size)
        assertEquals(MetaUpgradeId.entries.size, decoded?.labProgress?.ranks?.size)
    }

    @Test
    fun blankCorruptAndWrongVersionPayloadsReturnNull() {
        assertNull(decodeProfile(null))
        assertNull(decodeProfile(""))
        assertNull(decodeProfile("   "))
        assertNull(decodeProfile("not-a-progress-payload"))
        assertNull(decodeProfile("2|0|0"))
        assertNull(decodeProfile("1|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1"))
    }

    @Test
    fun encodingNormalizesProfileAndClampsPreferences() {
        val profile = PlayerProfile(
            preferences = PlayerPreferences(
                masterVolume = -4f,
                simulationSpeed = 9f,
                textScale = 9f,
                damageNumberTierThreshold = Int.MAX_VALUE,
            ),
            economy = PlayerEconomy(-50L, -100L),
            labProgress = LabProgress(listOf(-5, 2)),
            collection = PlayerCollection(setOf(-1, 0, TestProfilePolicy.itemCount - 1)),
            rebirthProgress = RebirthProgress(Int.MAX_VALUE, Int.MAX_VALUE),
        )

        val decoded = requireNotNull(decodeProfile(encodeProfile(profile)))

        assertEquals(PlayerEconomy(0L, 0L), decoded.economy)
        assertEquals(listOf(0, 2, 0, 0, 0, 0, 0, 0), decoded.labProgress.ranks)
        assertEquals(setOf(0, TestProfilePolicy.itemCount - 1), decoded.collection.discoveredItemIds)
        assertEquals(0f, decoded.preferences.masterVolume)
        assertEquals(2f, decoded.preferences.simulationSpeed)
        assertEquals(1.75f, decoded.preferences.textScale)
        assertEquals(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(), decoded.preferences.damageNumberTierThreshold)
        assertTrue(decoded.preferences.soundEnabled)
        assertTrue(decoded.preferences.musicEnabled)
        assertEquals(TestProfilePolicy.rebirth.maximumLevel, decoded.rebirthProgress.level)
        assertEquals(TestProfilePolicy.rebirth.maximumLevel, decoded.rebirthProgress.highestCleared)

        val lifetimeDecoded = requireNotNull(
            decodeProfile(
                encodeProfile(PlayerProfile(economy = PlayerEconomy(75L, 1L))),
            ),
        )
        assertEquals(75L, lifetimeDecoded.economy.lifetimeMatter)
    }

    @Test
    fun legacyV2PayloadRetainsOldDefaultsAndIdentities() {
        val decoded = requireNotNull(
            decodeProfile(
                "2|75|100|2|3|5|1,2,3,4,5,6,7,8|0,399|0,1,50,150,0,2,0,140",
            ),
        )

        assertEquals(PlayerEconomy(75L, 100L), decoded.economy)
        assertEquals(CoreShape.SHARD, decoded.loadout.coreShape)
        assertEquals(WeaponId.NULL_LANCE, decoded.loadout.selectedWeapon)
        assertEquals(setOf(WeaponId.FLUX_WAKE, WeaponId.PHASE_LATTICE), decoded.loadout.unlockedWeapons)
        assertEquals(1.4f, decoded.preferences.textScale)
        assertEquals(DamageNumberSize.NORMAL, decoded.preferences.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.preferences.damageNumberFormat)
        assertEquals(DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD, decoded.preferences.damageNumberTierThreshold)
        assertEquals(RebirthProgress(0, -1), decoded.rebirthProgress)
    }

    @Test
    fun olderV3SettingsPayloadUsesDamageNumberDefaults() {
        val decoded = requireNotNull(
            decodeProfile(
                "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125|0|-1",
            ),
        )

        assertEquals(DamageNumberSize.NORMAL, decoded.preferences.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.preferences.damageNumberFormat)
        assertEquals(DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD, decoded.preferences.damageNumberTierThreshold)
    }

    @Test
    fun invalidPersistentOrdinalsFallBackOrClampSafely() {
        val decoded = requireNotNull(
            decodeProfile(
                "3|0|0|999|999|1|0,0,0,0,0,0,0,0||1,1,65,115,1,999,1,125,999,-1,50|0|-1",
            ),
        )

        assertEquals(CoreShape.SHARD, decoded.loadout.coreShape)
        assertEquals(WeaponId.PRISM_RELAY, decoded.loadout.selectedWeapon)
        assertEquals(ParticleDensity.NORMAL, decoded.preferences.particleDensity)
        assertEquals(DamageNumberSize.NORMAL, decoded.preferences.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.preferences.damageNumberFormat)
    }

    @Test
    fun legacyDecodeUsesCapturedWeaponAndRebirthPolicyFallbacks() {
        val customPolicy = TestProfilePolicy.copy(
            weapons = (
                TestProfilePolicy.weapons.drop(1) + TestProfilePolicy.weapons.first()
                ).toImmutableList(),
            rebirth = TestProfilePolicy.rebirth.copy(
                minimumLevel = 2,
                maximumLevel = 3,
                profiles = TestProfilePolicy.rebirth.profiles.drop(2).take(2).toImmutableList(),
            ),
        )

        val decoded = requireNotNull(
            ProfileCodec.decode(
                "3|0|0|0|0|0|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125,1,0,50|99|99",
                customPolicy,
            ),
        )

        assertEquals(setOf(WeaponId.MORNINGSTAR), decoded.loadout.unlockedWeapons)
        assertEquals(RebirthProgress(level = 3, highestCleared = 3), decoded.rebirthProgress)
    }

    @Test
    fun persistentIdentityOrderingIsGolden() {
        assertEquals(listOf("ORB", "PRISM", "SHARD"), CoreShape.entries.map { it.name })
        assertEquals(
            listOf(
                "FLUX_WAKE", "MORNINGSTAR", "PHASE_LATTICE", "NULL_LANCE",
                "GRAVITY_MINES", "ION_SWARM", "RIFT_BLADES", "ARC_COIL",
                "QUASAR_CANNON", "ENTROPY_FIELD", "SINGULARITY_SPEAR", "PRISM_RELAY",
            ),
            WeaponId.entries.map { it.name },
        )
        assertEquals(
            listOf(
                "CORE_INTEGRITY", "KINETIC_AMPLIFIER", "MAGNETIC_RESONANCE", "CRYO_VENTS",
                "DASH_CAPACITOR", "SALVAGE_PROTOCOL", "DATA_ARCHIVE", "ARMORY_LICENSE",
            ),
            MetaUpgradeId.entries.map { it.name },
        )
        assertEquals(listOf("LOW", "NORMAL", "HIGH"), ParticleDensity.entries.map { it.name })
        assertEquals(listOf("SMALL", "NORMAL", "LARGE", "HUGE"), DamageNumberSize.entries.map { it.name })
        assertEquals(listOf("COMPACT", "FULL"), DamageNumberFormat.entries.map { it.name })
        assertEquals((0..399).toList(), (0 until TestProfilePolicy.itemCount).toList())
    }
}
