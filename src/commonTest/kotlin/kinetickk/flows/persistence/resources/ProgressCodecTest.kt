// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.resources

import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.model.PersistedSettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressCodecTest {
    @Test
    fun defaultProgressRoundTrips() {
        val progress = PersistedProgress()

        assertEquals(progress, ProgressCodec.decode(ProgressCodec.encode(progress)))
    }

    @Test
    fun richProgressRoundTripsWithoutLosingCatalogState() {
        val progress = PersistedProgress(
            matter = 9_876_543_210L,
            lifetimeMatter = 12_345_678_901L,
            coreShapeIndex = ProgressPersistenceSchema.CORE_SHAPE_SHARD_CODE,
            selectedWeaponIndex = ProgressPersistenceSchema.WEAPON_SINGULARITY_SPEAR_CODE,
            unlockedWeaponIndices =
                (0 until ProgressPersistenceSchema.WEAPON_CODE_COUNT).toSet(),
            metaLevels = List(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT) { code ->
                requireNotNull(ProgressPersistenceSchema.maxMetaUpgradeRank(code))
            },
            discoveredItemIds =
                (0 until ProgressPersistenceSchema.ITEM_ID_COUNT).toSet(),
            settings = PersistedSettings(
                soundEnabled = false,
                musicEnabled = false,
                masterVolume = 0.5f,
                simulationSpeed = 1.5f,
                textScale = 1.42f,
                screenShake = false,
                particleDensityCode = ProgressPersistenceSchema.PARTICLE_DENSITY_HIGH_CODE,
                damageNumbers = false,
                damageNumberSizeCode = ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_HUGE_CODE,
                damageNumberFormatCode = ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_FULL_CODE,
                damageNumberTierThreshold = 2_500,
            ),
            rebirthLevel = 7,
            highestClearedRebirth = 6,
        )

        val decoded = ProgressCodec.decode(ProgressCodec.encode(progress))

        assertEquals(progress, decoded)
        assertEquals(ProgressPersistenceSchema.ITEM_ID_COUNT, decoded?.discoveredItemIds?.size)
        assertEquals(ProgressPersistenceSchema.WEAPON_CODE_COUNT, decoded?.unlockedWeaponIndices?.size)
        assertEquals(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT, decoded?.metaLevels?.size)
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
        val progress = PersistedProgress(
            matter = -50L,
            lifetimeMatter = -100L,
            coreShapeIndex = -2,
            selectedWeaponIndex = -3,
            unlockedWeaponIndices = setOf(
                -1,
                ProgressPersistenceSchema.BASELINE_WEAPON_CODE,
                ProgressPersistenceSchema.WEAPON_CODE_COUNT - 1,
                31,
            ),
            metaLevels = listOf(-5, 2),
            discoveredItemIds = setOf(-1, 0, ProgressPersistenceSchema.ITEM_ID_COUNT - 1),
            settings = PersistedSettings(
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
        assertEquals(
            setOf(
                ProgressPersistenceSchema.BASELINE_WEAPON_CODE,
                ProgressPersistenceSchema.WEAPON_CODE_COUNT - 1,
            ),
            decoded.unlockedWeaponIndices,
        )
        assertEquals(listOf(0, 2, 0, 0, 0, 0, 0, 0), decoded.metaLevels)
        assertEquals(
            setOf(0, ProgressPersistenceSchema.ITEM_ID_COUNT - 1),
            decoded.discoveredItemIds,
        )
        assertEquals(0f, decoded.settings.masterVolume)
        assertEquals(2f, decoded.settings.simulationSpeed)
        assertEquals(1.75f, decoded.settings.textScale)
        assertEquals(
            ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
            decoded.settings.damageNumberTierThreshold,
        )
        assertTrue(decoded.settings.soundEnabled)
        assertTrue(decoded.settings.musicEnabled)
        assertEquals(ProgressPersistenceSchema.MAX_REBIRTH_LEVEL, decoded.rebirthLevel)
        assertEquals(ProgressPersistenceSchema.MAX_REBIRTH_LEVEL, decoded.highestClearedRebirth)

        val lifetimeDecoded = requireNotNull(
            ProgressCodec.decode(
                ProgressCodec.encode(PersistedProgress(matter = 75L, lifetimeMatter = 1L)),
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
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE,
            decoded.settings.damageNumberSizeCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE,
            decoded.settings.damageNumberFormatCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
            decoded.settings.damageNumberTierThreshold,
        )
    }

    @Test
    fun olderV3SettingsPayloadUsesDamageNumberDefaults() {
        val decoded = requireNotNull(
            ProgressCodec.decode(
                "3|0|0|0|0|1|0,0,0,0,0,0,0,0||1,1,65,115,1,1,1,125|0|-1",
            ),
        )

        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE,
            decoded.settings.damageNumberSizeCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE,
            decoded.settings.damageNumberFormatCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
            decoded.settings.damageNumberTierThreshold,
        )
    }

    @Test
    fun exactVersionedFieldCountsRejectMissingAndTrailingFields() {
        val malformedPayloads = listOf(
            VALID_V2_PAYLOAD.substringBeforeLast('|'),
            "$VALID_V2_PAYLOAD|trailing",
            VALID_V3_PAYLOAD.substringBeforeLast('|'),
            "$VALID_V3_PAYLOAD|trailing",
            v2Payload(settings = VALID_V2_SETTINGS.substringBeforeLast(',')),
            v2Payload(settings = "$VALID_V2_SETTINGS,125,0"),
            v3Payload(metaLevels = "0,0,0,0,0,0,0"),
            v3Payload(metaLevels = "0,0,0,0,0,0,0,0,0"),
            v3Payload(settings = VALID_V3_SETTINGS.substringBeforeLast(',')),
            v3Payload(settings = "$VALID_V3_SETTINGS,0"),
        )

        malformedPayloads.forEach(::assertMalformedPayload)
    }

    @Test
    fun malformedNumericFieldsAreRejectedInsteadOfDefaulted() {
        val malformedPayloads = listOf(
            v3Payload(matter = "not-a-long"),
            v3Payload(lifetimeMatter = "not-a-long"),
            v3Payload(coreShapeIndex = "not-an-int"),
            v3Payload(selectedWeaponIndex = "not-an-int"),
            v3Payload(weaponMask = "not-an-int"),
            v3Payload(weaponMask = "-1"),
            v3Payload(metaLevels = "0,0,0,not-an-int,0,0,0,0"),
            v3Payload(discoveries = "0,not-an-int"),
            v3Payload(settings = settingsWith(2, "not-an-int")),
            v3Payload(settings = settingsWith(3, "not-an-int")),
            v3Payload(settings = settingsWith(7, "not-an-int")),
            v3Payload(settings = settingsWith(10, "not-an-int")),
            v3Payload(rebirthLevel = "not-an-int"),
            v3Payload(highestClearedRebirth = "not-an-int"),
            v2Payload(settings = "1,1,not-an-int,115,1,1,1"),
            v2Payload(settings = "$VALID_V2_SETTINGS,not-an-int"),
        )

        malformedPayloads.forEach(::assertMalformedPayload)
    }

    @Test
    fun booleansAndPrimitiveCodesUseOnlySupportedTokens() {
        val malformedPayloads = listOf(
            v3Payload(settings = settingsWith(0, "true")),
            v3Payload(settings = settingsWith(1, "-1")),
            v3Payload(settings = settingsWith(4, "2")),
            v3Payload(settings = settingsWith(6, "yes")),
            v3Payload(
                settings = settingsWith(
                    5,
                    ProgressPersistenceSchema.PARTICLE_DENSITY_CODE_COUNT.toString(),
                ),
            ),
            v3Payload(settings = settingsWith(8, "-1")),
            v3Payload(
                settings = settingsWith(
                    9,
                    ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_CODE_COUNT.toString(),
                ),
            ),
            v2Payload(settings = "2,1,65,115,1,1,1"),
            v2Payload(settings = "1,1,65,115,1,99,1"),
        )

        malformedPayloads.forEach(::assertMalformedPayload)
    }

    @Test
    fun incompleteV3SettingsPayloadIsMalformed() {
        assertEquals(
            MALFORMED_PAYLOAD_RESULT,
            decodeProgressPayload(
                v3Payload(settings = "1,1,65,115,1,1,1,125,1"),
            ),
        )
    }

    @Test
    fun malformedAndTrailingPayloadsReturnMalformedPayloadRejection() {
        val malformedPayloads = listOf(
            "not-a-progress-payload",
            "$VALID_V3_PAYLOAD|trailing",
            v3Payload(discoveries = "0,"),
            v3Payload(settings = "$VALID_V3_SETTINGS,"),
        )

        malformedPayloads.forEach(::assertMalformedPayload)
    }

    @Test
    fun supportedBooleanAndPrimitiveCodeValuesStillDecode() {
        val decoded = requireNotNull(
            ProgressCodec.decode(
                v3Payload(
                    settings = settingsWith(
                        0 to "0",
                        1 to "0",
                        4 to "0",
                        5 to ProgressPersistenceSchema.PARTICLE_DENSITY_HIGH_CODE.toString(),
                        6 to "0",
                        8 to ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_HUGE_CODE.toString(),
                        9 to ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_FULL_CODE.toString(),
                    ),
                ),
            ),
        )

        assertEquals(false, decoded.settings.soundEnabled)
        assertEquals(false, decoded.settings.musicEnabled)
        assertEquals(false, decoded.settings.screenShake)
        assertEquals(
            ProgressPersistenceSchema.PARTICLE_DENSITY_HIGH_CODE,
            decoded.settings.particleDensityCode,
        )
        assertEquals(false, decoded.settings.damageNumbers)
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_HUGE_CODE,
            decoded.settings.damageNumberSizeCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_FULL_CODE,
            decoded.settings.damageNumberFormatCode,
        )
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
                    PersistedProgress(
                        rebirthLevel = Int.MIN_VALUE,
                        highestClearedRebirth = Int.MIN_VALUE,
                    ),
                ),
            ),
        )
        val clearanceAheadOfTier = requireNotNull(
            ProgressCodec.decode(
                ProgressCodec.encode(
                    PersistedProgress(
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

    private fun assertMalformedPayload(payload: String) {
        assertEquals(MALFORMED_PAYLOAD_RESULT, decodeProgressPayload(payload), payload)
    }

    private fun v3Payload(
        matter: String = "10",
        lifetimeMatter: String = "20",
        coreShapeIndex: String = "0",
        selectedWeaponIndex: String = "0",
        weaponMask: String = "1",
        metaLevels: String = "0,0,0,0,0,0,0,0",
        discoveries: String = "",
        settings: String = VALID_V3_SETTINGS,
        rebirthLevel: String = "0",
        highestClearedRebirth: String = "-1",
    ): String = listOf(
        "3",
        matter,
        lifetimeMatter,
        coreShapeIndex,
        selectedWeaponIndex,
        weaponMask,
        metaLevels,
        discoveries,
        settings,
        rebirthLevel,
        highestClearedRebirth,
    ).joinToString("|")

    private fun v2Payload(
        metaLevels: String = "0,0,0,0,0,0,0,0",
        discoveries: String = "",
        settings: String = VALID_V2_SETTINGS,
    ): String = listOf(
        "2",
        "10",
        "20",
        "0",
        "0",
        "1",
        metaLevels,
        discoveries,
        settings,
    ).joinToString("|")

    private fun settingsWith(vararg replacements: Pair<Int, String>): String {
        val settings = VALID_V3_SETTINGS.split(',').toMutableList()
        replacements.forEach { (index, value) -> settings[index] = value }
        return settings.joinToString(",")
    }

    private fun settingsWith(index: Int, value: String): String = settingsWith(index to value)

    private companion object {
        const val VALID_V2_SETTINGS = "1,1,65,115,1,1,1"
        const val VALID_V3_SETTINGS = "1,1,65,115,1,1,1,125,1,0,50"
        const val VALID_V2_PAYLOAD =
            "2|10|20|0|0|1|0,0,0,0,0,0,0,0||$VALID_V2_SETTINGS"
        const val VALID_V3_PAYLOAD =
            "3|10|20|0|0|1|0,0,0,0,0,0,0,0||$VALID_V3_SETTINGS|0|-1"
        val MALFORMED_PAYLOAD_RESULT =
            ProgressLoadResult.Rejected(ProgressLoadRejection.MALFORMED_PAYLOAD)
    }
}
