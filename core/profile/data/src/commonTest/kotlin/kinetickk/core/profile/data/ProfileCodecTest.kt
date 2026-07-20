// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.core.profile.data

import kinetickk.core.content.CoreShape
import kinetickk.core.content.ItemCatalog
import kinetickk.core.content.ItemDefinition
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.content.RebirthProgression
import kinetickk.core.content.WeaponId
import kinetickk.core.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.core.profile.api.DamageNumberFormat
import kinetickk.core.profile.api.DamageNumberSize
import kinetickk.core.profile.api.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.ParticleDensity
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.core.profile.api.PlayerProfile
import kinetickk.core.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCodecTest {
    @Test
    fun defaultProfileHasStableV3GoldenAndRoundTrips() {
        val profile = PlayerProfile()
        val encoded = ProfileCodec.encode(profile)

        assertEquals(
            "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125,1,0,50|0|-1",
            encoded,
        )
        assertEquals(profile, ProfileCodec.decode(encoded))
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

        val encoded = ProfileCodec.encode(profile)

        assertEquals(
            "3|75|100|2|3|5|1,2,3,4,5,6,7,8|0,399|0,1,50,150,0,2,0,140,2,1,2500|3|2",
            encoded,
        )
        assertEquals(profile, ProfileCodec.decode(encoded))
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
            labProgress = LabProgress(MetaUpgradeCatalog.all.map { it.maxRanks }),
            collection = PlayerCollection(ItemCatalog.all.mapTo(mutableSetOf(), ItemDefinition::id)),
            rebirthProgress = RebirthProgress(7, 6),
        )

        val decoded = ProfileCodec.decode(ProfileCodec.encode(profile))

        assertEquals(profile, decoded)
        assertEquals(ItemCatalog.ITEM_COUNT, decoded?.collection?.discoveredItemIds?.size)
        assertEquals(WeaponId.entries.size, decoded?.loadout?.unlockedWeapons?.size)
        assertEquals(MetaUpgradeId.entries.size, decoded?.labProgress?.ranks?.size)
    }

    @Test
    fun blankCorruptAndWrongVersionPayloadsReturnNull() {
        assertNull(ProfileCodec.decode(null))
        assertNull(ProfileCodec.decode(""))
        assertNull(ProfileCodec.decode("   "))
        assertNull(ProfileCodec.decode("not-a-progress-payload"))
        assertNull(ProfileCodec.decode("2|0|0"))
        assertNull(ProfileCodec.decode("1|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1"))
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
            collection = PlayerCollection(setOf(-1, 0, ItemCatalog.ITEM_COUNT - 1)),
            rebirthProgress = RebirthProgress(Int.MAX_VALUE, Int.MAX_VALUE),
        )

        val decoded = requireNotNull(ProfileCodec.decode(ProfileCodec.encode(profile)))

        assertEquals(PlayerEconomy(0L, 0L), decoded.economy)
        assertEquals(listOf(0, 2, 0, 0, 0, 0, 0, 0), decoded.labProgress.ranks)
        assertEquals(setOf(0, ItemCatalog.ITEM_COUNT - 1), decoded.collection.discoveredItemIds)
        assertEquals(0f, decoded.preferences.masterVolume)
        assertEquals(2f, decoded.preferences.simulationSpeed)
        assertEquals(1.75f, decoded.preferences.textScale)
        assertEquals(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(), decoded.preferences.damageNumberTierThreshold)
        assertTrue(decoded.preferences.soundEnabled)
        assertTrue(decoded.preferences.musicEnabled)
        assertEquals(RebirthProgression.MAX_LEVEL, decoded.rebirthProgress.level)
        assertEquals(RebirthProgression.MAX_LEVEL, decoded.rebirthProgress.highestCleared)

        val lifetimeDecoded = requireNotNull(
            ProfileCodec.decode(
                ProfileCodec.encode(PlayerProfile(economy = PlayerEconomy(75L, 1L))),
            ),
        )
        assertEquals(75L, lifetimeDecoded.economy.lifetimeMatter)
    }

    @Test
    fun legacyV2PayloadRetainsOldDefaultsAndIdentities() {
        val decoded = requireNotNull(
            ProfileCodec.decode(
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
            ProfileCodec.decode(
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
            ProfileCodec.decode(
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
        assertEquals((0..399).toList(), ItemCatalog.all.map(ItemDefinition::id))
    }
}
