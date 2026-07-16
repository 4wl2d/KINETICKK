// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.features.game.GameFeatureBall
import kinetickk.features.game.interaction.fx.InteractionFxReducer
import kinetickk.features.game.nucleus.projection.VisualFxProjection
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.features.game.resources.audio.AudioResource
import kinetickk.features.game.resources.audio.GameAudioResource
import kinetickk.features.game.resources.progress.ProgressLoadResult
import kinetickk.features.game.resources.progress.ProgressLoadRejection
import kinetickk.features.game.resources.progress.ProgressProviderId
import kinetickk.features.game.resources.progress.ProgressResourceFailure
import kinetickk.features.game.resources.progress.ProgressStore
import kinetickk.features.game.resources.progress.createPlatformProgressStore
import kinetickk.features.game.resources.progress.quarantineBootstrapProgress

sealed interface BootstrapProgressStatus {
    data object Loaded : BootstrapProgressStatus
    data object NotFound : BootstrapProgressStatus
    data class Rejected(val reason: ProgressLoadRejection) : BootstrapProgressStatus
    data class OutcomeUnknown(val reason: ProgressResourceFailure) : BootstrapProgressStatus
}

/** Explicit static composition root for the one LocalGame Ball. */
class GameAssembly private constructor(
    val game: GameFeatureBall,
    val bootstrapProgressStatus: BootstrapProgressStatus,
    private val interactionFxReducer: InteractionFxReducer,
) {
    fun close() = game.close()

    internal fun applyVisualFx(cues: Iterable<VisualFxCue>) = interactionFxReducer.apply(cues)

    internal fun visualFxSnapshot(): VisualFxProjection = interactionFxReducer.snapshot()

    companion object {
        fun create(
            progressStore: ProgressStore = createPlatformProgressStore(),
            audioResource: AudioResource = GameAudioResource(),
            seed: Int = 731_991,
        ): GameAssembly {
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
            return GameAssembly(
                game = GameFeatureBall.create(
                    progressStore = progressStore,
                    audioResource = audioResource,
                    bootstrapProgress = bootstrap,
                    seed = seed,
                ),
                bootstrapProgressStatus = status,
                interactionFxReducer = InteractionFxReducer(seed),
            )
        }
    }
}
