// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.reducer

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayInputField
import kinetickk.ball.gameplay.api.GameplayInputReason
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutput
import kinetickk.ball.gameplay.nucleus.simulation.*

class EngineState internal constructor(
    internal val model: MutableGameState,
)

internal fun initialEngineState(
    content: GameplayContentSnapshot,
    seed: Int,
    bootstrapProgress: GameplayProfileSnapshot?,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
): EngineState {
    val model = MutableGameState(
        content = content,
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
    )
    return EngineState(model)
}

internal data class GameReduction(
    val state: EngineState,
    val outputs: ImmutableList<SimulationOutput>,
)

internal sealed interface GameReductionResult {
    data class Accepted(val value: GameReduction) : GameReductionResult
    data class Rejected(val reason: GameplayRejection) : GameReductionResult
}

/** Purely coordinates a synchronous state transition; effect execution belongs to feature-impl. */
internal class GameReducer {
    fun reduce(state: EngineState, intent: GameplayInteractionPulse): GameReductionResult {
        validate(state, intent)?.let { return GameReductionResult.Rejected(it) }

        if (intent == GameplayInteractionPulse.UserGestureObserved) {
            return GameReductionResult.Accepted(
                GameReduction(
                    state = state,
                    outputs = immutableListOf(SimulationOutput.EnsureAudioUnlocked),
                ),
            )
        }

        val candidate = state.model.copyForReduction()
        applyIntent(candidate, intent)
        val outputs = buildList<SimulationOutput> {
            candidate.takeVisualFxCues()
                .takeIf { it.isNotEmpty() }
                ?.let { add(SimulationOutput.EmitVisualFx(it.toImmutableList())) }
            candidate.takeProgressUpdate()?.let { update ->
                add(SimulationOutput.PublishProgress(update))
            }
            val soundCues = candidate.takeSoundCues()
            if (intent is GameplayInteractionPulse.FrameElapsed || soundCues.isNotEmpty()) {
                add(
                    SimulationOutput.AdvanceAudio(
                        realDeltaSeconds = (intent as? GameplayInteractionPulse.FrameElapsed)?.realDeltaSeconds ?: 0f,
                        cues = soundCues.toImmutableList(),
                    ),
                )
            }
        }.toImmutableList()

        return GameReductionResult.Accepted(
            GameReduction(
                state = EngineState(candidate),
                    outputs = outputs,
            ),
        )
    }

    private fun applyIntent(state: MutableGameState, intent: GameplayInteractionPulse) {
        when (intent) {
            is GameplayInteractionPulse.FrameElapsed -> state.update(intent.realDeltaSeconds)
            is GameplayInteractionPulse.ViewportChanged -> state.resize(intent.width, intent.height, intent.density)
            is GameplayInteractionPulse.PointerMoved -> state.updatePointer(intent.x, intent.y, intent.active)
            is GameplayInteractionPulse.BrakeChanged -> when (intent.source) {
                BrakeSource.KEYBOARD -> state.setBrake(intent.active)
                BrakeSource.SECONDARY_POINTER -> state.setSecondaryBrake(intent.active)
                BrakeSource.TOUCH_CONTROL -> state.setTouchBrake(intent.active)
            }
            GameplayInteractionPulse.DashRequested -> state.requestDash()
            GameplayInteractionPulse.PauseToggled -> state.togglePause()
            GameplayInteractionPulse.PauseForOverlay -> state.pauseForOverlay()
            GameplayInteractionPulse.ExitRunRequested -> state.exitRun()
            is GameplayInteractionPulse.PreferencesChanged -> state.applyPreferences(intent.preferences)
            is GameplayInteractionPulse.ChoiceSelected -> state.choose(intent.index)
            GameplayInteractionPulse.ChoicesRerolled -> state.rerollChoices()
            GameplayInteractionPulse.UserGestureObserved -> error("handled before state cloning")
        }
    }

    private fun validate(state: EngineState, intent: GameplayInteractionPulse): GameplayRejection.InvalidInput? =
        when (intent) {
            is GameplayInteractionPulse.FrameElapsed -> bounded(
                field = GameplayInputField.FRAME_DELTA_SECONDS,
                value = intent.realDeltaSeconds,
                minimum = MIN_FRAME_DELTA_SECONDS,
                maximum = MAX_FRAME_DELTA_SECONDS,
            )
            is GameplayInteractionPulse.ViewportChanged ->
                bounded(GameplayInputField.VIEWPORT_WIDTH, intent.width, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded(GameplayInputField.VIEWPORT_HEIGHT, intent.height, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded(GameplayInputField.DENSITY, intent.density, MIN_DENSITY, MAX_DENSITY)
            is GameplayInteractionPulse.PointerMoved ->
                bounded(GameplayInputField.POINTER_X, intent.x, 0f, state.model.screenWidth)
                    ?: bounded(GameplayInputField.POINTER_Y, intent.y, 0f, state.model.screenHeight)
            is GameplayInteractionPulse.ChoiceSelected -> when {
                intent.index < 0 -> GameplayRejection.InvalidInput(
                    GameplayInputField.CHOICE_INDEX,
                    GameplayInputReason.BELOW_MINIMUM,
                )
                intent.index >= MutableGameState.MAX_CHOICES -> GameplayRejection.InvalidInput(
                    GameplayInputField.CHOICE_INDEX,
                    GameplayInputReason.ABOVE_MAXIMUM,
                )
                else -> null
            }
            else -> null
        }

    private fun bounded(
        field: GameplayInputField,
        value: Float,
        minimum: Float,
        maximum: Float,
    ): GameplayRejection.InvalidInput? = when {
        !value.isFinite() -> GameplayRejection.InvalidInput(field, GameplayInputReason.NON_FINITE)
        value < minimum -> GameplayRejection.InvalidInput(field, GameplayInputReason.BELOW_MINIMUM)
        value > maximum -> GameplayRejection.InvalidInput(field, GameplayInputReason.ABOVE_MAXIMUM)
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
