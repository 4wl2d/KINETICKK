// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence.resources

import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.model.PersistedSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ProgressStoreTest {
    @Test
    fun storedProgressDefensivelyOwnsImmutableCollectionPayloads() {
        val weapons = mutableSetOf(0, 1)
        val levels = mutableListOf(1, 2, 3)
        val discoveries = mutableSetOf(4, 5)
        val progress = PersistedProgress(
            unlockedWeaponIndices = weapons,
            metaLevels = levels,
            discoveredItemIds = discoveries,
        )

        weapons.clear()
        levels.clear()
        discoveries.clear()

        assertEquals(setOf(0, 1), progress.unlockedWeaponIndices)
        assertEquals(listOf(1, 2, 3), progress.metaLevels)
        assertEquals(setOf(4, 5), progress.discoveredItemIds)
        assertFalse((progress.unlockedWeaponIndices as Any) is MutableSet<*>)
        assertFalse((progress.metaLevels as Any) is MutableList<*>)
        assertFalse((progress.discoveredItemIds as Any) is MutableSet<*>)
    }

    @Test
    fun bootstrapQuarantineUsesTheFlowOwnedSchemaContract() {
        val loaded = assertIs<ProgressLoadResult.Loaded>(
            quarantineBootstrapProgress(
                PersistedProgress(
                    matter = -10L,
                    lifetimeMatter = -20L,
                    coreShapeIndex = Int.MAX_VALUE,
                    selectedWeaponIndex = Int.MAX_VALUE,
                    unlockedWeaponIndices = setOf(-1, Int.MAX_VALUE),
                    metaLevels = listOf(Int.MAX_VALUE),
                    discoveredItemIds = setOf(-1, ProgressPersistenceSchema.ITEM_ID_COUNT),
                    settings = PersistedSettings(
                        masterVolume = -5f,
                        simulationSpeed = 99f,
                        textScale = 0.1f,
                        particleDensityCode = Int.MAX_VALUE,
                        damageNumberSizeCode = Int.MAX_VALUE,
                        damageNumberFormatCode = Int.MAX_VALUE,
                        damageNumberTierThreshold = Int.MAX_VALUE,
                    ),
                    rebirthLevel = Int.MAX_VALUE,
                    highestClearedRebirth = Int.MAX_VALUE,
                ),
            ),
        ).progress

        assertEquals(0L, loaded.matter)
        assertEquals(0L, loaded.lifetimeMatter)
        assertEquals(ProgressPersistenceSchema.CORE_SHAPE_CODE_COUNT - 1, loaded.coreShapeIndex)
        assertEquals(ProgressPersistenceSchema.BASELINE_WEAPON_CODE, loaded.selectedWeaponIndex)
        assertEquals(setOf(ProgressPersistenceSchema.BASELINE_WEAPON_CODE), loaded.unlockedWeaponIndices)
        assertEquals(
            requireNotNull(ProgressPersistenceSchema.maxMetaUpgradeRank(0)),
            loaded.metaLevels.first(),
        )
        assertEquals(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT, loaded.metaLevels.size)
        assertEquals(emptySet(), loaded.discoveredItemIds)
        assertEquals(ProgressPersistenceSchema.MIN_MASTER_VOLUME, loaded.settings.masterVolume)
        assertEquals(ProgressPersistenceSchema.MAX_SIMULATION_SPEED, loaded.settings.simulationSpeed)
        assertEquals(ProgressPersistenceSchema.MIN_TEXT_SCALE, loaded.settings.textScale)
        assertEquals(
            ProgressPersistenceSchema.PARTICLE_DENSITY_NORMAL_CODE,
            loaded.settings.particleDensityCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_SIZE_NORMAL_CODE,
            loaded.settings.damageNumberSizeCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DAMAGE_NUMBER_FORMAT_COMPACT_CODE,
            loaded.settings.damageNumberFormatCode,
        )
        assertEquals(
            ProgressPersistenceSchema.DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD,
            loaded.settings.damageNumberTierThreshold,
        )
        assertEquals(ProgressPersistenceSchema.MAX_REBIRTH_LEVEL, loaded.rebirthLevel)
        assertEquals(ProgressPersistenceSchema.MAX_REBIRTH_LEVEL, loaded.highestClearedRebirth)
    }

    @Test
    fun bootstrapQuarantineRejectsNonFinitePersistedSettings() {
        assertEquals(
            ProgressLoadResult.Rejected(ProgressLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER),
            quarantineBootstrapProgress(
                PersistedProgress(settings = PersistedSettings(simulationSpeed = Float.NaN)),
            ),
        )
    }

    @Test
    fun loadsValidatedProgressFromTheAssemblyBoundStore() {
        val expected = PersistedProgress(matter = 42, lifetimeMatter = 99)
        val store = fakeStore(progressPayload = { ProgressCodec.encode(expected) })

        assertEquals(ProgressLoadResult.Loaded(expected), store.load())
    }

    @Test
    fun absentProgressFallsBackToLegacyMatter() {
        val store = fakeStore(progressPayload = { null }, legacyMatter = { "37" })

        assertEquals(
            ProgressLoadResult.Loaded(PersistedProgress(matter = 37, lifetimeMatter = 37)),
            store.load(),
        )
    }

    @Test
    fun missingKeysAreNotFound() {
        assertEquals(ProgressLoadResult.NotFound, fakeStore().load())
    }

    @Test
    fun rawUtf8LimitIsEnforcedBeforeDecode() {
        val atLimit = decodeProgressPayload("x".repeat(MAX_PROGRESS_PAYLOAD_BYTES))
        val overLimit = decodeProgressPayload("x".repeat(MAX_PROGRESS_PAYLOAD_BYTES + 1))

        assertEquals(
            ProgressLoadResult.Rejected(ProgressLoadRejection.MALFORMED_PAYLOAD),
            atLimit,
        )
        assertEquals(
            ProgressLoadResult.Rejected(ProgressLoadRejection.PAYLOAD_TOO_LARGE),
            overLimit,
        )
    }

    @Test
    fun malformedPrimaryPayloadDoesNotFallBackToLegacyAuthority() {
        val store = fakeStore(progressPayload = { "not-a-progress-snapshot" }, legacyMatter = { "37" })

        assertEquals(
            ProgressLoadResult.Rejected(ProgressLoadRejection.MALFORMED_PAYLOAD),
            store.load(),
        )
    }

    @Test
    fun persistsEncodedProgressAndLegacyMatter() {
        var persistedProgress: String? = null
        var persistedMatter: Int? = null
        val expected = PersistedProgress(matter = 71, lifetimeMatter = 80)
        val store = fakeStore(
            writeProgress = { persistedProgress = it },
            writeMatter = { persistedMatter = it },
        )

        assertEquals(ProgressPersistResult.Persisted, store.persist(expected))
        assertEquals(expected, ProgressCodec.decode(persistedProgress))
        assertEquals(71, persistedMatter)
    }

    @Test
    fun providerExceptionsBecomeTypedUnknownOutcomes() {
        val readFailure = fakeStore(progressPayload = { error("private provider detail") })
        val writeFailure = fakeStore(writeProgress = { error("private provider detail") })

        assertEquals(
            ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED),
            readFailure.load(),
        )
        assertEquals(
            ProgressPersistResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED),
            writeFailure.persist(PersistedProgress()),
        )
    }

    @Test
    fun oversizedOutboundSnapshotIsRejectedBySafeSink() {
        val discoveries = (0..30_000).toSet()
        val result = fakeStore().persist(PersistedProgress(discoveredItemIds = discoveries))

        val unknown = assertIs<ProgressPersistResult.OutcomeUnknown>(result)
        assertEquals(ProgressResourceFailure.PAYLOAD_LIMIT_EXCEEDED, unknown.reason)
    }

    private fun fakeStore(
        progressPayload: () -> String? = { null },
        legacyMatter: () -> String? = { null },
        writeProgress: (String) -> Unit = {},
        writeMatter: (Int) -> Unit = {},
    ): ProgressStore = FixedKeyProgressStore(
        readProgressPayload = progressPayload,
        readLegacyMatter = legacyMatter,
        writeProgressPayload = writeProgress,
        writeLegacyMatter = writeMatter,
    )
}
