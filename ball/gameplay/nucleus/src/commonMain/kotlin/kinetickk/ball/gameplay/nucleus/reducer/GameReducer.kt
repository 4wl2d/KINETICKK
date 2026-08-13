// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.reducer

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutputs
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.max

class EngineState internal constructor(
    internal val model: MutableGameState,
    /** Lightweight provenance retained by render snapshots without retaining the mutable model. */
    internal val renderProjectionIdentity: Any = Any(),
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
    val outputs: SimulationOutputs,
)

internal sealed interface GameReductionResult {
    data class Accepted(
        val state: EngineState,
        val outputs: SimulationOutputs,
    ) : GameReductionResult

    data class Rejected(val reason: GameplayRejection) : GameReductionResult
}

/** Purely coordinates a synchronous state transition; effect execution belongs to feature-impl. */
internal class GameReducer {
    fun reduce(state: EngineState, intent: GameplayInteractionPulse): GameReductionResult {
        validate(state, intent)?.let { return GameReductionResult.Rejected(it) }

        if (intent == GameplayInteractionPulse.UserGestureObserved) {
            return GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.EnsureAudioUnlocked,
            )
        }

        if (
            intent is GameplayInteractionPulse.FrameElapsed &&
            state.model.phase != GamePhase.RUNNING &&
            state.model.accumulator == 0f &&
            state.model.lastTransitionSteps == 0 &&
            !state.model.hasPendingReductionOutputs()
        ) {
            return GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.create(
                    advanceAudio = true,
                    audioRealDeltaSeconds = intent.realDeltaSeconds,
                ),
            )
        }

        if (
            intent.leavesStateUnchanged(state.model) &&
            !state.model.hasPendingReductionOutputs()
        ) {
            return GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.Empty,
            )
        }

        val candidate = if (intent.preservesStableSimulationStorage()) {
            state.model.copyForScalarInputReduction()
        } else {
            state.model.copyForReduction()
        }
        applyIntent(candidate, intent)
        val visualFxCues = candidate.takeVisualFxCues()
        val progressUpdate = candidate.takeProgressUpdate()
        val soundCues = candidate.takeSoundCues()
        val frameIntent = intent as? GameplayInteractionPulse.FrameElapsed
        val advanceAudio = frameIntent != null || soundCues.isNotEmpty()
        val outputs = SimulationOutputs.create(
            visualFxCues = visualFxCues,
            progressUpdate = progressUpdate,
            advanceAudio = advanceAudio,
            audioRealDeltaSeconds = frameIntent?.realDeltaSeconds ?: 0f,
            audioCues = soundCues,
        )

        return GameReductionResult.Accepted(
            state = EngineState(candidate),
            outputs = outputs,
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
            is GameplayInteractionPulse.ChoiceSelected -> state.choose(intent.index)
            GameplayInteractionPulse.ChoicesRerolled -> state.rerollChoices()
            GameplayInteractionPulse.UserGestureObserved -> error("handled before state cloning")
        }
    }

    private fun validate(state: EngineState, intent: GameplayInteractionPulse): GameplayRejection? =
        when (intent) {
            is GameplayInteractionPulse.PointerMoved -> when {
                intent.x < 0f || intent.x > state.model.screenWidth ->
                    GameplayRejection.PointerOutsideViewport(GameplayPointerAxis.HORIZONTAL)
                intent.y < 0f || intent.y > state.model.screenHeight ->
                    GameplayRejection.PointerOutsideViewport(GameplayPointerAxis.VERTICAL)
                else -> null
            }
            else -> null
        }

    private fun GameplayInteractionPulse.preservesStableSimulationStorage(): Boolean = when (this) {
        is GameplayInteractionPulse.ViewportChanged,
        is GameplayInteractionPulse.PointerMoved,
        is GameplayInteractionPulse.BrakeChanged,
        GameplayInteractionPulse.DashRequested,
        GameplayInteractionPulse.PauseToggled,
        -> true
        is GameplayInteractionPulse.FrameElapsed,
        is GameplayInteractionPulse.ChoiceSelected,
        GameplayInteractionPulse.ChoicesRerolled,
        GameplayInteractionPulse.UserGestureObserved,
        -> false
    }

    private fun GameplayInteractionPulse.leavesStateUnchanged(state: MutableGameState): Boolean =
        when (this) {
            is GameplayInteractionPulse.ViewportChanged -> {
                val nextWidth = max(1f, width)
                val nextHeight = max(1f, height)
                val nextUiScale = max(1f, density)
                nextWidth.sameBitsAs(state.screenWidth) &&
                    nextHeight.sameBitsAs(state.screenHeight) &&
                    nextUiScale.sameBitsAs(state.uiScale)
            }
            is GameplayInteractionPulse.PointerMoved -> state.pointerInputIsUnchanged(this)
            is GameplayInteractionPulse.BrakeChanged -> state.brakeInputIsUnchanged(this)
            GameplayInteractionPulse.DashRequested ->
                state.phase != GamePhase.RUNNING ||
                    state.dashBufferTime.sameBitsAs(MutableGameState.DASH_INPUT_BUFFER_SECONDS)
            is GameplayInteractionPulse.FrameElapsed,
            GameplayInteractionPulse.PauseToggled,
            is GameplayInteractionPulse.ChoiceSelected,
            GameplayInteractionPulse.ChoicesRerolled,
            GameplayInteractionPulse.UserGestureObserved,
            -> false
        }

    private fun MutableGameState.pointerInputIsUnchanged(
        intent: GameplayInteractionPulse.PointerMoved,
    ): Boolean {
        val nextPointerX = clamp(intent.x, 0f, screenWidth)
        val nextPointerY = clamp(intent.y, 0f, screenHeight)
        if (
            !nextPointerX.sameBitsAs(pointerX) ||
            !nextPointerY.sameBitsAs(pointerY) ||
            intent.active != pointerActive
        ) {
            return false
        }

        val targetX = cameraX + nextPointerX - screenWidth * 0.5f
        val targetY = cameraY + nextPointerY - screenHeight * 0.5f
        val dx = targetX - coreX
        val dy = targetY - coreY
        val distance = length(dx, dy)
        if (!(distance > 24f)) return true
        return (dx / distance).sameBitsAs(lastAimDirectionX) &&
            (dy / distance).sameBitsAs(lastAimDirectionY)
    }

    private fun MutableGameState.brakeInputIsUnchanged(
        intent: GameplayInteractionPulse.BrakeChanged,
    ): Boolean {
        val sourceUnchanged = when (intent.source) {
            BrakeSource.KEYBOARD -> keyboardBrakeActive == intent.active
            BrakeSource.SECONDARY_POINTER -> secondaryBrakeActive == intent.active
            BrakeSource.TOUCH_CONTROL -> touchBrakeActive == intent.active
        }
        val expectedBraking = phase == GamePhase.RUNNING &&
            (keyboardBrakeActive || secondaryBrakeActive || touchBrakeActive)
        return sourceUnchanged && braking == expectedBraking
    }

    private fun MutableGameState.hasPendingReductionOutputs(): Boolean =
        soundCues.isNotEmpty() ||
            !visualFxCues.isEmpty() ||
            pendingBankedMatter != 0L ||
            pendingDiscoveredItemIds.isNotEmpty() ||
            pendingClearedRebirthLevel != null

    private fun Float.sameBitsAs(other: Float): Boolean = toBits() == other.toBits()
}
