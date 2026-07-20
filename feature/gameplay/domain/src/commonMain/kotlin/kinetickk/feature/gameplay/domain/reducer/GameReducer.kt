// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.reducer

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.immutableListOf
import kinetickk.core.collections.toImmutableList
import kinetickk.core.profile.api.GameplayProfileSnapshot
import kinetickk.feature.gameplay.domain.protocol.BrakeSource
import kinetickk.feature.gameplay.domain.protocol.GameEffect
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.protocol.GameRejection
import kinetickk.feature.gameplay.domain.simulation.*

internal data class EngineState(
    val model: MutableGameState,
)

internal fun initialEngineState(
    seed: Int,
    bootstrapProgress: GameplayProfileSnapshot?,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
): EngineState {
    val model = MutableGameState(
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
    )
    model.startRun()
    model.takeSoundCues()
    model.takeVisualFxCues()
    return EngineState(model)
}

internal data class GameReduction(
    val state: EngineState,
    val effects: ImmutableList<GameEffect>,
)

internal sealed interface GameReductionResult {
    data class Accepted(val value: GameReduction) : GameReductionResult
    data class Rejected(val reason: GameRejection) : GameReductionResult
}

/** Purely coordinates a synchronous state transition; effect execution belongs to feature-impl. */
internal class GameReducer {
    fun reduce(state: EngineState, intent: GameplayAction): GameReductionResult {
        validate(state, intent)?.let { return GameReductionResult.Rejected(it) }

        if (intent == GameplayAction.UserGestureObserved) {
            return GameReductionResult.Accepted(
                GameReduction(
                    state = state,
                    effects = immutableListOf(GameEffect.EnsureAudioUnlocked),
                ),
            )
        }

        val candidate = state.model.copyForReduction()
        applyIntent(candidate, intent)
        val effects = buildList<GameEffect> {
            candidate.takeVisualFxCues()
                .takeIf { it.isNotEmpty() }
                ?.let { add(GameEffect.EmitVisualFx(it.toImmutableList())) }
            candidate.takeProgressUpdate()?.let { update ->
                add(GameEffect.PublishProgress(update))
            }
            val soundCues = candidate.takeSoundCues()
            if (intent is GameplayAction.FrameElapsed || soundCues.isNotEmpty()) {
                add(
                    GameEffect.AdvanceAudio(
                        realDeltaSeconds = (intent as? GameplayAction.FrameElapsed)?.realDeltaSeconds ?: 0f,
                        cues = soundCues.toImmutableList(),
                    ),
                )
            }
        }.toImmutableList()

        return GameReductionResult.Accepted(
            GameReduction(
                state = EngineState(candidate),
                effects = effects,
            ),
        )
    }

    private fun applyIntent(state: MutableGameState, intent: GameplayAction) {
        when (intent) {
            is GameplayAction.FrameElapsed -> state.update(intent.realDeltaSeconds)
            is GameplayAction.ViewportChanged -> state.resize(intent.width, intent.height, intent.density)
            is GameplayAction.PointerMoved -> state.updatePointer(intent.x, intent.y, intent.active)
            is GameplayAction.BrakeChanged -> when (intent.source) {
                BrakeSource.KEYBOARD -> state.setBrake(intent.active)
                BrakeSource.SECONDARY_POINTER -> state.setSecondaryBrake(intent.active)
                BrakeSource.TOUCH_CONTROL -> state.setTouchBrake(intent.active)
            }
            GameplayAction.DashRequested -> state.requestDash()
            GameplayAction.PauseToggled -> state.togglePause()
            GameplayAction.PauseForOverlay -> state.pauseForOverlay()
            GameplayAction.ExitRunRequested -> state.exitRun()
            is GameplayAction.PreferencesChanged -> state.applyPreferences(intent.preferences)
            is GameplayAction.ChoiceSelected -> state.choose(intent.index)
            GameplayAction.ChoicesRerolled -> state.rerollChoices()
            GameplayAction.UserGestureObserved -> error("handled before state cloning")
        }
    }

    private fun validate(state: EngineState, intent: GameplayAction): GameRejection.InvalidInput? =
        when (intent) {
            is GameplayAction.FrameElapsed -> bounded(
                field = "realDeltaSeconds",
                value = intent.realDeltaSeconds,
                minimum = MIN_FRAME_DELTA_SECONDS,
                maximum = MAX_FRAME_DELTA_SECONDS,
            )
            is GameplayAction.ViewportChanged ->
                bounded("width", intent.width, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded("height", intent.height, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded("density", intent.density, MIN_DENSITY, MAX_DENSITY)
            is GameplayAction.PointerMoved ->
                bounded("x", intent.x, 0f, state.model.screenWidth)
                    ?: bounded("y", intent.y, 0f, state.model.screenHeight)
            is GameplayAction.ChoiceSelected -> if (intent.index in 0..3) {
                null
            } else {
                GameRejection.InvalidInput("index", "must be in 0..3")
            }
            else -> null
        }

    private fun bounded(
        field: String,
        value: Float,
        minimum: Float,
        maximum: Float,
    ): GameRejection.InvalidInput? = when {
        !value.isFinite() -> GameRejection.InvalidInput(field, "must be finite")
        value < minimum || value > maximum ->
            GameRejection.InvalidInput(field, "must be in [$minimum, $maximum]")
        else -> null
    }

    private companion object {
        const val MIN_FRAME_DELTA_SECONDS = 0f
        const val MAX_FRAME_DELTA_SECONDS = 1f
        const val MIN_VIEWPORT_DIMENSION = 1f
        const val MAX_VIEWPORT_DIMENSION = 32_768f
        const val MIN_DENSITY = 0.5f
        const val MAX_DENSITY = 8f
    }
}
