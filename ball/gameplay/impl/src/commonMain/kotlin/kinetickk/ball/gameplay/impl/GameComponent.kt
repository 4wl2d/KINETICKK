// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressCapability
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.nucleus.engine.GameDispatchResult
import kinetickk.ball.gameplay.nucleus.engine.GameEngine
import kinetickk.ball.gameplay.nucleus.engine.GameSnapshot
import kinetickk.ball.gameplay.nucleus.protocol.GameEffect
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAction
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer

/** Executes gameplay effects while exposing only the gameplay progress capability. */
internal class GameComponent private constructor(
    private val engine: GameEngine,
    private val progressCapability: GameplayProgressCapability,
    private val audioExecutor: GameplayAudioExecutor,
    private val interactionFxReducer: InteractionFxReducer,
) : GameplayInteractionPort {
    override fun dispatch(action: GameplayAction): GameDispatchResult =
        engine.dispatch(action).also { result ->
            if (result is GameDispatchResult.Committed) {
                result.effects.forEach(::execute)
            }
        }

    override fun snapshot(): GameSnapshot = engine.snapshot()

    override fun visualFxSnapshot(): VisualFxProjection = interactionFxReducer.snapshot()

    private fun execute(effect: GameEffect) {
        when (effect) {
            is GameEffect.AdvanceAudio -> runCatching {
                audioExecutor.advance(effect.realDeltaSeconds, effect.cues)
            }
            GameEffect.EnsureAudioUnlocked -> runCatching {
                audioExecutor.ensureUnlocked()
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
            audioExecutor: GameplayAudioExecutor,
            seed: Int = 731_991,
        ): GameComponent = GameComponent(
            engine = GameEngine.create(
                content = configuration.content,
                bootstrapProgress = configuration.toGameplayProfileSnapshot(),
                seed = seed,
            ),
            progressCapability = progressCapability,
            audioExecutor = audioExecutor,
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
