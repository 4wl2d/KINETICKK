package kinetickk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressCodecTest {
    @Test
    fun defaultProgressRoundTrips() {
        val progress = StoredProgress()

        assertEquals(progress, ProgressCodec.decode(ProgressCodec.encode(progress)))
    }

    @Test
    fun richProgressRoundTripsWithoutLosingCatalogState() {
        val progress = StoredProgress(
            matter = 9_876_543_210L,
            lifetimeMatter = 12_345_678_901L,
            coreShapeIndex = CoreShape.SHARD.ordinal,
            selectedWeaponIndex = WeaponId.SINGULARITY_SPEAR.ordinal,
            unlockedWeaponIndices = WeaponId.entries.indices.toSet(),
            metaLevels = MetaUpgradeCatalog.all.map { it.maxRanks },
            discoveredItemIds = ItemCatalog.all.mapTo(mutableSetOf(), ItemDefinition::id),
            settings = GameSettings(
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
            rebirthLevel = 7,
            highestClearedRebirth = 6,
        )

        val decoded = ProgressCodec.decode(ProgressCodec.encode(progress))

        assertEquals(progress, decoded)
        assertEquals(ItemCatalog.ITEM_COUNT, decoded?.discoveredItemIds?.size)
        assertEquals(WeaponId.entries.size, decoded?.unlockedWeaponIndices?.size)
        assertEquals(MetaUpgradeId.entries.size, decoded?.metaLevels?.size)
    }

    @Test
    fun blankCorruptAndWrongVersionPayloadsReturnNull() {
        assertNull(ProgressCodec.decode(null))
        assertNull(ProgressCodec.decode(""))
        assertNull(ProgressCodec.decode("   "))
        assertNull(ProgressCodec.decode("not-a-progress-payload"))
        assertNull(ProgressCodec.decode("2|0|0"))
        assertNull(ProgressCodec.decode("1|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1"))
    }

    @Test
    fun encodingNormalizesProgressAndClampsSettings() {
        val progress = StoredProgress(
            matter = -50L,
            lifetimeMatter = -100L,
            coreShapeIndex = -2,
            selectedWeaponIndex = -3,
            unlockedWeaponIndices = setOf(-1, 0, WeaponId.entries.lastIndex, 31),
            metaLevels = listOf(-5, 2),
            discoveredItemIds = setOf(-1, 0, ItemCatalog.ITEM_COUNT - 1),
            settings = GameSettings(
                masterVolume = -4f,
                simulationSpeed = 9f,
                textScale = 9f,
                damageNumberTierThreshold = Int.MAX_VALUE,
            ),
            rebirthLevel = Int.MAX_VALUE,
            highestClearedRebirth = Int.MAX_VALUE,
        )

        val decoded = requireNotNull(ProgressCodec.decode(ProgressCodec.encode(progress)))

        assertEquals(0L, decoded.matter)
        assertEquals(0L, decoded.lifetimeMatter)
        assertEquals(0, decoded.coreShapeIndex)
        assertEquals(0, decoded.selectedWeaponIndex)
        assertEquals(setOf(0, WeaponId.entries.lastIndex), decoded.unlockedWeaponIndices)
        assertEquals(listOf(0, 2, 0, 0, 0, 0, 0, 0), decoded.metaLevels)
        assertEquals(setOf(0, ItemCatalog.ITEM_COUNT - 1), decoded.discoveredItemIds)
        assertEquals(0f, decoded.settings.masterVolume)
        assertEquals(2f, decoded.settings.simulationSpeed)
        assertEquals(1.75f, decoded.settings.textScale)
        assertEquals(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(), decoded.settings.damageNumberTierThreshold)
        assertTrue(decoded.settings.soundEnabled)
        assertTrue(decoded.settings.musicEnabled)
        assertEquals(RebirthProgression.MAX_LEVEL, decoded.rebirthLevel)
        assertEquals(RebirthProgression.MAX_LEVEL, decoded.highestClearedRebirth)

        val lifetimeDecoded = requireNotNull(
            ProgressCodec.decode(
                ProgressCodec.encode(StoredProgress(matter = 75L, lifetimeMatter = 1L)),
            ),
        )
        assertEquals(75L, lifetimeDecoded.lifetimeMatter)
    }

    @Test
    fun olderSettingsPayloadUsesReadableTextScaleDefault() {
        val decoded = requireNotNull(
            ProgressCodec.decode("2|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1"),
        )

        assertEquals(1.25f, decoded.settings.textScale)
        assertEquals(DamageNumberSize.NORMAL, decoded.settings.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.settings.damageNumberFormat)
        assertEquals(DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD, decoded.settings.damageNumberTierThreshold)
    }

    @Test
    fun olderV3SettingsPayloadUsesDamageNumberDefaults() {
        val decoded = requireNotNull(
            ProgressCodec.decode(
                "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125|0|-1",
            ),
        )

        assertEquals(DamageNumberSize.NORMAL, decoded.settings.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.settings.damageNumberFormat)
        assertEquals(DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD, decoded.settings.damageNumberTierThreshold)
    }

    @Test
    fun invalidDamageNumberEnumOrdinalsFallBackSafely() {
        val decoded = requireNotNull(
            ProgressCodec.decode(
                "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125,999,-1,50|0|-1",
            ),
        )

        assertEquals(DamageNumberSize.NORMAL, decoded.settings.damageNumberSize)
        assertEquals(DamageNumberFormat.COMPACT, decoded.settings.damageNumberFormat)
    }

    @Test
    fun legacyV2PayloadDefaultsToUnclearedBaselineRebirth() {
        val decoded = requireNotNull(
            ProgressCodec.decode(
                "2|75|100|2|3|5|1,2,3,4,5,6,7,8|0,399|0,1,50,150,0,2,0,140",
            ),
        )

        assertEquals(75L, decoded.matter)
        assertEquals(100L, decoded.lifetimeMatter)
        assertEquals(setOf(0, 2), decoded.unlockedWeaponIndices)
        assertEquals(0, decoded.rebirthLevel)
        assertEquals(-1, decoded.highestClearedRebirth)
    }

    @Test
    fun v3RebirthValuesClampToSupportedAndInternallyConsistentRanges() {
        val belowRange = requireNotNull(
            ProgressCodec.decode(
                ProgressCodec.encode(
                    StoredProgress(
                        rebirthLevel = Int.MIN_VALUE,
                        highestClearedRebirth = Int.MIN_VALUE,
                    ),
                ),
            ),
        )
        val clearanceAheadOfTier = requireNotNull(
            ProgressCodec.decode(
                ProgressCodec.encode(
                    StoredProgress(
                        rebirthLevel = 3,
                        highestClearedRebirth = Int.MAX_VALUE,
                    ),
                ),
            ),
        )

        assertEquals(0, belowRange.rebirthLevel)
        assertEquals(-1, belowRange.highestClearedRebirth)
        assertEquals(3, clearanceAheadOfTier.rebirthLevel)
        assertEquals(3, clearanceAheadOfTier.highestClearedRebirth)
    }
}
