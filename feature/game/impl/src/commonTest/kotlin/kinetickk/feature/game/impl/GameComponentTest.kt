// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.impl

import kinetickk.feature.game.domain.engine.GameDispatchResult
import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.model.ItemCatalog
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.port.audio.AudioResource
import kinetickk.feature.game.domain.port.progress.ProgressLoadRejection
import kinetickk.feature.game.domain.port.progress.ProgressLoadResult
import kinetickk.feature.game.domain.port.progress.ProgressPersistResult
import kinetickk.feature.game.domain.port.progress.ProgressProviderId
import kinetickk.feature.game.domain.port.progress.ProgressResourceFailure
import kinetickk.feature.game.domain.port.progress.ProgressStore
import kinetickk.feature.game.domain.protocol.BrakeSource
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.domain.protocol.SoundCue
import kinetickk.feature.game.presentation.input.isHudControlPosition
import kinetickk.feature.game.presentation.input.resolvePointerPress
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.Test

class GameComponentTest {
    @Test
    fun componentExecutesDomainEffectsAfterCommit() {
        val progress = RecordingProgressStore()
        val audio = RecordingAudioResource()
        val component = GameComponent.create(progress, audio, seed = 2)

        val started = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameIntent.RunStartRequested),
        )
        assertEquals(started.snapshot, component.snapshot())
        assertEquals(1, audio.advanceCalls)
        component.dispatch(GameIntent.DashRequested)
        component.dispatch(GameIntent.FrameElapsed(0.1f))
        assertTrue(component.visualFxSnapshot().shockwaves.isNotEmpty())

        component.dispatch(GameIntent.UserGestureObserved)
        assertEquals(1, audio.unlockCalls)

        component.dispatch(GameIntent.MuteToggled)
        assertEquals(1, progress.persisted.size)

        component.close()
        assertEquals(1, audio.closeCalls)
    }

    @Test
    fun resourceFailuresDoNotRollBackCommittedState() {
        val component = GameComponent.create(
            progressStore = RecordingProgressStore(throwOnPersist = true),
            audioResource = RecordingAudioResource(throwOnAdvance = true),
            seed = 3,
        )

        val started = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameIntent.RunStartRequested),
        )
        assertEquals(started.snapshot, component.snapshot())
        val muted = assertIs<GameDispatchResult.Committed>(
            component.dispatch(GameIntent.MuteToggled),
        )
        assertEquals(2uL, muted.snapshot.revision)
    }

    @Test
    fun pointerResolverProducesSemanticActionsForComponentProjections() {
        val menu = component(seed = 4)
        menu.dispatch(GameIntent.ViewportChanged(1_280f, 720f, 1f))
        val menuProjection = menu.snapshot().projection

        val rebirth = menuProjection.resolvePointerPress(640f, 720f * 0.9f)
        assertEquals(GameIntent.ScreenOpenRequested(UiScreen.REBIRTH), rebirth)
        menu.dispatch(requireNotNull(rebirth))
        assertEquals(UiScreen.REBIRTH, menu.snapshot().projection.screen)

        val settings = component(seed = 5)
        settings.dispatch(GameIntent.ViewportChanged(1_280f, 720f, 1f))
        settings.dispatch(GameIntent.ScreenOpenRequested(UiScreen.SETTINGS))
        val incrementMasterVolume = settings.snapshot().projection.resolvePointerPress(
            x = 910f,
            y = 230f,
        )
        assertEquals(
            GameIntent.SettingAdjusted(SettingsRow.MASTER_VOLUME, direction = 1),
            incrementMasterVolume,
        )
        settings.dispatch(requireNotNull(incrementMasterVolume))
        assertEquals(0.66f, settings.snapshot().projection.settings.masterVolume)

        val running = component(seed = 6)
        running.dispatch(GameIntent.ViewportChanged(1_280f, 720f, 1f))
        running.dispatch(GameIntent.RunStartRequested)
        running.dispatch(GameIntent.PointerMoved(120f, 360f))
        val hudProjection = running.snapshot().projection
        val dashX = 1_280f - 82f
        val dashY = 720f - 88f
        assertTrue(hudProjection.isHudControlPosition(dashX, dashY))
        assertEquals(GameIntent.DashRequested, hudProjection.resolvePointerPress(dashX, dashY))
        running.dispatch(GameIntent.DashRequested)
        running.dispatch(GameIntent.FrameElapsed(1f / 60f))
        assertTrue(running.snapshot().projection.velocityX < -500f)

        running.dispatch(GameIntent.BrakeChanged(BrakeSource.KEYBOARD, true))
        val brakeIntent = running.snapshot().projection.resolvePointerPress(
            x = 1_280f - 190f,
            y = 720f - 67f,
        )
        assertEquals(GameIntent.BrakeChanged(BrakeSource.TOUCH_CONTROL, true), brakeIntent)
    }

    @Test
    fun bootstrapResourceExceptionBecomesTypedOutcomeUnknown() {
        listOf(ThrowingLoadProgressStore, ThrowingProviderIdentityProgressStore).forEach { store ->
            val component = GameComponent.create(
                progressStore = store,
                audioResource = RecordingAudioResource(),
                seed = 7,
            )

            val status = assertIs<BootstrapProgressStatus.OutcomeUnknown>(
                component.bootstrapProgressStatus,
            )
            assertEquals(ProgressResourceFailure.PROVIDER_READ_FAILED, status.reason)
            component.close()
        }
    }

    @Test
    fun bootstrapResourceValueIsNormalizedBeforeRevisionZero() {
        val component = GameComponent.create(
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
            audioResource = RecordingAudioResource(),
            seed = 8,
        )

        assertEquals(BootstrapProgressStatus.Loaded, component.bootstrapProgressStatus)
        val projection = component.snapshot().projection
        assertEquals(0uL, component.snapshot().revision)
        assertEquals(0L, projection.totalMatter)
        assertEquals(0f, projection.settings.masterVolume)
        assertEquals(2f, projection.settings.simulationSpeed)
        assertEquals(1f, projection.settings.textScale)
        assertEquals(0, projection.discoveredItemCount)
    }

    @Test
    fun invalidBootstrapResourceValueUsesDefaults() {
        val component = GameComponent.create(
            progressStore = LoadedProgressStore(
                StoredProgress(settings = GameSettings(simulationSpeed = Float.NaN)),
            ),
            audioResource = RecordingAudioResource(),
            seed = 9,
        )

        assertEquals(
            BootstrapProgressStatus.Rejected(ProgressLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER),
            component.bootstrapProgressStatus,
        )
        assertEquals(GameSettings(), component.snapshot().projection.settings)
    }

    private fun component(seed: Int): GameComponent = GameComponent.create(
        progressStore = RecordingProgressStore(),
        audioResource = RecordingAudioResource(),
        seed = seed,
    )
}

private class RecordingProgressStore(
    private val throwOnPersist: Boolean = false,
) : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL
    val persisted = mutableListOf<StoredProgress>()

    override fun load(): ProgressLoadResult = ProgressLoadResult.NotFound

    override fun persist(progress: StoredProgress): ProgressPersistResult {
        persisted += progress
        if (throwOnPersist) error("provider unavailable")
        return ProgressPersistResult.Persisted
    }
}

private class RecordingAudioResource(
    private val throwOnAdvance: Boolean = false,
) : AudioResource {
    var advanceCalls = 0
    var unlockCalls = 0
    var closeCalls = 0

    override fun advance(settings: GameSettings, realDelta: Float, cues: List<SoundCue>) {
        advanceCalls++
        if (throwOnAdvance) error("audio unavailable")
    }

    override fun ensureUnlocked() {
        unlockCalls++
    }

    override fun close() {
        closeCalls++
    }
}

private object ThrowingLoadProgressStore : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL
    override fun load(): ProgressLoadResult = error("provider unavailable")
    override fun persist(progress: StoredProgress) = ProgressPersistResult.Persisted
}

private object ThrowingProviderIdentityProgressStore : ProgressStore {
    override val providerId: ProgressProviderId
        get() = error("provider identity unavailable")
    override fun load(): ProgressLoadResult = error("must not be called")
    override fun persist(progress: StoredProgress) = ProgressPersistResult.Persisted
}

private class LoadedProgressStore(
    private val progress: StoredProgress,
) : ProgressStore {
    override val providerId = ProgressProviderId.PLATFORM_LOCAL
    override fun load() = ProgressLoadResult.Loaded(progress)
    override fun persist(progress: StoredProgress) = ProgressPersistResult.Persisted
}
