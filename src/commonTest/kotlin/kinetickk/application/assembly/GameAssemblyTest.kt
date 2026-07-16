// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.ItemCatalog
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.GameDispatchResult
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.SoundCue
import kinetickk.features.game.resources.audio.AudioResource
import kinetickk.features.game.resources.progress.ProgressLoadRejection
import kinetickk.features.game.resources.progress.ProgressLoadResult
import kinetickk.features.game.resources.progress.ProgressPersistResult
import kinetickk.features.game.resources.progress.ProgressProviderId
import kinetickk.features.game.resources.progress.ProgressResourceFailure
import kinetickk.features.game.resources.progress.ProgressStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameAssemblyTest {
    @Test
    fun interactionOwnsAndAppliesTheUnstampedVisualAttachment() {
        val assembly = GameAssembly.create(
            progressStore = NotFoundProgressStore,
            audioResource = NoOpAudioResource,
            seed = 2,
        )

        fun dispatch(intent: GameIntent): GameDispatchResult.Committed {
            val committed = assertIs<GameDispatchResult.Committed>(assembly.game.dispatch(intent))
            assembly.applyVisualFx(committed.visualFxCues)
            return committed
        }

        dispatch(GameIntent.RunStartRequested)
        dispatch(GameIntent.DashRequested)
        dispatch(GameIntent.FrameElapsed(0.1f))

        val visualFx = assembly.visualFxSnapshot()
        assertTrue(visualFx.particles.isNotEmpty())
        assertTrue(visualFx.shockwaves.isNotEmpty())

        dispatch(GameIntent.RunStartRequested)
        val cleared = assembly.visualFxSnapshot()
        assertTrue(
            listOf(
                cleared.particles,
                cleared.motionEchoes,
                cleared.shockwaves,
                cleared.damageNumbers,
                cleared.weaponArcs,
            ).all { it.isEmpty() },
        )
        assembly.close()
    }

    @Test
    fun bootstrapResourceExceptionBecomesTypedOutcomeUnknown() {
        listOf(ThrowingLoadProgressStore, ThrowingProviderIdentityProgressStore).forEach { store ->
            val assembly = GameAssembly.create(
                progressStore = store,
                audioResource = NoOpAudioResource,
                seed = 1,
            )

            val status = assertIs<BootstrapProgressStatus.OutcomeUnknown>(
                assembly.bootstrapProgressStatus,
            )
            assertEquals(ProgressResourceFailure.PROVIDER_READ_FAILED, status.reason)
            assembly.close()
        }
    }

    @Test
    fun bootstrapResourceValueIsNormalizedBeforeRevisionZero() {
        val assembly = GameAssembly.create(
            progressStore = LoadedProgressStore(
                StoredProgress(
                    matter = -10,
                    lifetimeMatter = -20,
                    coreShapeIndex = Int.MAX_VALUE,
                    selectedWeaponIndex = Int.MAX_VALUE,
                    unlockedWeaponIndices = setOf(-1, Int.MAX_VALUE),
                    metaLevels = listOf(Int.MAX_VALUE),
                    discoveredItemIds = setOf(-1, ItemCatalog.ITEM_COUNT),
                    settings = GameSettings(
                        masterVolume = -5f,
                        simulationSpeed = 99f,
                        textScale = 0.1f,
                        damageNumberTierThreshold = Int.MAX_VALUE,
                    ),
                    rebirthLevel = Int.MAX_VALUE,
                    highestClearedRebirth = Int.MAX_VALUE,
                ),
            ),
            audioResource = NoOpAudioResource,
            seed = 3,
        )

        assertEquals(BootstrapProgressStatus.Loaded, assembly.bootstrapProgressStatus)
        val projection = assertIs<GameQueryResult.Projection>(
            assembly.game.query(GameQuery.GetGameProjection),
        ).value.payload
        assertEquals(0L, projection.totalMatter)
        assertEquals(0f, projection.settings.masterVolume)
        assertEquals(2f, projection.settings.simulationSpeed)
        assertEquals(1f, projection.settings.textScale)
        assertEquals(0, projection.discoveredItemCount)
        assembly.close()
    }

    @Test
    fun nonFiniteOrOversizedBootstrapResourceValueIsRejectedBeforeRevisionZero() {
        val nonFinite = GameAssembly.create(
            progressStore = LoadedProgressStore(
                StoredProgress(settings = GameSettings(simulationSpeed = Float.NaN)),
            ),
            audioResource = NoOpAudioResource,
            seed = 4,
        )
        assertEquals(
            BootstrapProgressStatus.Rejected(ProgressLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER),
            nonFinite.bootstrapProgressStatus,
        )
        val defaultProjection = assertIs<GameQueryResult.Projection>(
            nonFinite.game.query(GameQuery.GetGameProjection),
        ).value.payload
        assertEquals(GameSettings(), defaultProjection.settings)
        nonFinite.close()

        val oversized = GameAssembly.create(
            progressStore = LoadedProgressStore(
                StoredProgress(discoveredItemIds = (0..ItemCatalog.ITEM_COUNT).toSet()),
            ),
            audioResource = NoOpAudioResource,
            seed = 5,
        )
        assertEquals(
            BootstrapProgressStatus.Rejected(
                ProgressLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
            ),
            oversized.bootstrapProgressStatus,
        )
        oversized.close()
    }
}

private object ThrowingLoadProgressStore : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL

    override fun load() = error("provider unavailable")

    override fun persist(progress: StoredProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private object ThrowingProviderIdentityProgressStore : ProgressStore {
    override val providerId: ProgressProviderId
        get() = error("provider identity unavailable")

    override fun load() = error("must not be called")

    override fun persist(progress: StoredProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private object NotFoundProgressStore : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL
    override fun load() = kinetickk.features.game.resources.progress.ProgressLoadResult.NotFound
    override fun persist(progress: StoredProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private class LoadedProgressStore(
    private val progress: StoredProgress,
) : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL
    override fun load() = ProgressLoadResult.Loaded(progress)
    override fun persist(progress: StoredProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private object NoOpAudioResource : AudioResource {
    override fun advance(settings: GameSettings, realDelta: Float, cues: List<SoundCue>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
