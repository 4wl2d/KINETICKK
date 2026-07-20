// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.impl

import kinetickk.core.profile.api.GameplayProfileSnapshot
import kinetickk.core.profile.api.GameplayProgressCapability
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.core.profile.api.PlayerLoadout
import kinetickk.core.profile.api.RebirthProgress
import kinetickk.feature.gameplay.api.GameplayOutput
import kinetickk.feature.gameplay.api.RunConfiguration
import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.engine.GameEngine
import kinetickk.feature.gameplay.domain.engine.GameSnapshot
import kinetickk.feature.gameplay.domain.protocol.GameEffect
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.renderModel.VisualFxProjection
import kinetickk.feature.gameplay.presentation.fx.InteractionFxReducer

/** Executes gameplay effects while exposing only the gameplay progress capability. */
internal class GameComponent private constructor(
    private val engine: GameEngine,
    private val progressCapability: GameplayProgressCapability,
    private val onOutput: (GameplayOutput) -> Unit,
    private val interactionFxReducer: InteractionFxReducer,
) {
    fun dispatch(action: GameplayAction): GameDispatchResult =
        engine.dispatch(action).also { result ->
            if (result is GameDispatchResult.Committed) {
                result.effects.forEach(::execute)
            }
        }

    fun snapshot(): GameSnapshot = engine.snapshot()

    fun visualFxSnapshot(): VisualFxProjection = interactionFxReducer.snapshot()

    private fun execute(effect: GameEffect) {
        when (effect) {
            is GameEffect.AdvanceAudio -> runCatching {
                onOutput(
                    GameplayOutput.AudioFrame(
                        realDeltaSeconds = effect.realDeltaSeconds,
                        cues = effect.cues,
                    ),
                )
            }
            GameEffect.EnsureAudioUnlocked -> runCatching {
                onOutput(GameplayOutput.UserGestureObserved)
            }
            is GameEffect.PublishProgress -> runCatching {
                progressCapability.applyGameplayProgress(effect.update)
            }
            is GameEffect.EmitVisualFx -> interactionFxReducer.apply(effect.cues)
        }
    }

    companion object {
        fun create(
            configuration: RunConfiguration,
            progressCapability: GameplayProgressCapability,
            seed: Int = 731_991,
            onOutput: (GameplayOutput) -> Unit = {},
        ): GameComponent = GameComponent(
            engine = GameEngine.create(
                bootstrapProgress = configuration.toGameplayProfileSnapshot(),
                seed = seed,
            ),
            progressCapability = progressCapability,
            onOutput = onOutput,
            interactionFxReducer = InteractionFxReducer(seed),
        )
    }
}

private fun RunConfiguration.toGameplayProfileSnapshot(): GameplayProfileSnapshot {
    val matter = matterAtStart.coerceAtLeast(0L)
    return GameplayProfileSnapshot(
        preferences = preferences.normalized(),
        economy = PlayerEconomy(
            matter = matter,
            lifetimeMatter = lifetimeMatterAtStart.coerceAtLeast(matter),
        ),
        loadout = PlayerLoadout(
            coreShape = coreShape,
            selectedWeapon = startingWeapon,
            unlockedWeapons = unlockedWeapons,
        ),
        labProgress = LabProgress(metaRanks),
        collection = PlayerCollection(knownItemIds),
        rebirthProgress = RebirthProgress(level = rebirthLevel, highestCleared = -1),
    )
}
