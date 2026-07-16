// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.resources.progress

import kinetickk.features.game.nucleus.StoredProgress
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
        val progress = StoredProgress(
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
    fun loadsValidatedProgressWithFixedProviderIdentity() {
        val expected = StoredProgress(matter = 42, lifetimeMatter = 99)
        val store = fakeStore(progressPayload = { ProgressCodec.encode(expected) })

        assertEquals(ProgressProviderId.PLATFORM_LOCAL, store.providerId)
        assertEquals(ProgressLoadResult.Loaded(expected), store.load())
    }

    @Test
    fun absentProgressFallsBackToLegacyMatter() {
        val store = fakeStore(progressPayload = { null }, legacyMatter = { "37" })

        assertEquals(
            ProgressLoadResult.Loaded(StoredProgress(matter = 37, lifetimeMatter = 37)),
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
        val expected = StoredProgress(matter = 71, lifetimeMatter = 80)
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
            writeFailure.persist(StoredProgress()),
        )
    }

    @Test
    fun oversizedOutboundSnapshotIsRejectedBySafeSink() {
        val discoveries = (0..30_000).toSet()
        val result = fakeStore().persist(StoredProgress(discoveredItemIds = discoveries))

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
