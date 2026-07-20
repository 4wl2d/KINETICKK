// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.impl

import kinetickk.feature.game.data.audio.GameAudioResource
import kinetickk.feature.game.data.progress.createPlatformProgressStore
import kinetickk.feature.game.data.progress.quarantineBootstrapProgress
import kinetickk.feature.game.domain.engine.GameDispatchResult
import kinetickk.feature.game.domain.engine.GameEngine
import kinetickk.feature.game.domain.engine.GameSnapshot
import kinetickk.feature.game.domain.port.audio.AudioResource
import kinetickk.feature.game.domain.port.progress.ProgressLoadRejection
import kinetickk.feature.game.domain.port.progress.ProgressLoadResult
import kinetickk.feature.game.domain.port.progress.ProgressProviderId
import kinetickk.feature.game.domain.port.progress.ProgressResourceFailure
import kinetickk.feature.game.domain.port.progress.ProgressStore
import kinetickk.feature.game.domain.projection.VisualFxProjection
import kinetickk.feature.game.domain.protocol.GameEffect
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.presentation.fx.InteractionFxReducer

internal sealed interface BootstrapProgressStatus {
    data object Loaded : BootstrapProgressStatus
    data object NotFound : BootstrapProgressStatus
    data class Rejected(val reason: ProgressLoadRejection) : BootstrapProgressStatus
    data class OutcomeUnknown(val reason: ProgressResourceFailure) : BootstrapProgressStatus
}

/** Internal feature composition root and the only place that executes domain effects. */
internal class GameComponent private constructor(
    private val engine: GameEngine,
    private val progressStore: ProgressStore,
    private val audioResource: AudioResource,
    private val interactionFxReducer: InteractionFxReducer,
    val bootstrapProgressStatus: BootstrapProgressStatus,
) {
    fun dispatch(intent: GameIntent): GameDispatchResult =
        engine.dispatch(intent).also { result ->
            if (result is GameDispatchResult.Committed) {
                result.effects.forEach(::execute)
            }
        }

    fun snapshot(): GameSnapshot = engine.snapshot()

    fun visualFxSnapshot(): VisualFxProjection = interactionFxReducer.snapshot()

    fun close() {
        runCatching(audioResource::close)
    }

    private fun execute(effect: GameEffect) {
        when (effect) {
            is GameEffect.AdvanceAudio -> runCatching {
                audioResource.advance(
                    settings = effect.settings,
                    realDelta = effect.realDeltaSeconds,
                    cues = effect.cues,
                )
            }
            GameEffect.EnsureAudioUnlocked -> runCatching(audioResource::ensureUnlocked)
            is GameEffect.PersistProgress -> executePersistence(effect)
            is GameEffect.EmitVisualFx -> interactionFxReducer.apply(effect.cues)
        }
    }

    private fun executePersistence(effect: GameEffect.PersistProgress) {
        val providerAccepted = runCatching {
            progressStore.providerId == ProgressProviderId.PLATFORM_LOCAL
        }.getOrDefault(false)
        if (providerAccepted) {
            runCatching { progressStore.persist(effect.snapshot) }
        }
    }

    companion object {
        fun create(
            progressStore: ProgressStore = createPlatformProgressStore(),
            audioResource: AudioResource = GameAudioResource(),
            seed: Int = 731_991,
        ): GameComponent {
            val providerAccepted = runCatching {
                progressStore.providerId == ProgressProviderId.PLATFORM_LOCAL
            }.getOrDefault(false)
            val rawLoadResult = if (providerAccepted) {
                runCatching(progressStore::load).getOrElse {
                    ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED)
                }
            } else {
                ProgressLoadResult.OutcomeUnknown(ProgressResourceFailure.PROVIDER_READ_FAILED)
            }
            val loadResult = when (rawLoadResult) {
                is ProgressLoadResult.Loaded -> quarantineBootstrapProgress(rawLoadResult.progress)
                ProgressLoadResult.NotFound -> ProgressLoadResult.NotFound
                is ProgressLoadResult.Rejected -> rawLoadResult
                is ProgressLoadResult.OutcomeUnknown -> rawLoadResult
            }
            val bootstrap = (loadResult as? ProgressLoadResult.Loaded)?.progress
            val status = when (loadResult) {
                is ProgressLoadResult.Loaded -> BootstrapProgressStatus.Loaded
                ProgressLoadResult.NotFound -> BootstrapProgressStatus.NotFound
                is ProgressLoadResult.Rejected -> BootstrapProgressStatus.Rejected(loadResult.reason)
                is ProgressLoadResult.OutcomeUnknown -> BootstrapProgressStatus.OutcomeUnknown(loadResult.reason)
            }
            return GameComponent(
                engine = GameEngine.create(
                    bootstrapProgress = bootstrap,
                    seed = seed,
                ),
                progressStore = progressStore,
                audioResource = audioResource,
                interactionFxReducer = InteractionFxReducer(seed),
                bootstrapProgressStatus = status,
            )
        }
    }
}
