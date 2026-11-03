// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.reducer

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPointerAxis
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutputs
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*

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
    runMode: RunMode = RunMode.STANDARD,
    offerRunOmen: Boolean = false,
): EngineState {
    val model = MutableGameState(
        content = content,
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
        runMode = runMode,
        offerRunOmen = offerRunOmen,
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

/** What one reduction does to its model; the pure copy path and the in-place path share this classification. */
internal enum class ReductionOutcome {
    /** Rejected before any mutation; [ReductionSink.rejection] holds the reason. */
    REJECTED,

    /** A user gesture: nothing changes; the owner unlocks audio. */
    AUDIO_UNLOCKED,

    /** A frame of a stopped run with nothing pending: nothing changes; music advances by the frame. */
    IDLE_FRAME,

    /** A running frame shorter than the remaining fixed step: only the accumulator advances. */
    SUB_STEP,

    /** An input that changes nothing. */
    UNCHANGED,

    /** A fixed-step frame or a state-changing input runs; its outputs are drained into the sink. */
    STEPPED,
}

/** Drain target of one reduction. Its owner may reuse it; the values are valid until the next reduction. */
internal class ReductionSink {
    var rejection: GameplayRejection? = null
    /** A running frame admitted its real time: [admittedAccumulator] is the accumulator it runs with. */
    var admitsFrame: Boolean = false
    var admittedAccumulator: Float = 0f
    var visualFxCues: ImmutableList<VisualFxCue> = immutableListOf()
    var progressUpdate: GameplayProgressUpdate? = null
    var soundCues: ImmutableList<GameplayAudioCue> = immutableListOf()

    fun reset() {
        rejection = null
        admitsFrame = false
        admittedAccumulator = 0f
        visualFxCues = immutableListOf()
        progressUpdate = null
        soundCues = immutableListOf()
    }
}

/** Purely coordinates a synchronous state transition; effect execution belongs to feature-impl. */
internal class GameReducer {
    /** The pure entry: the committed [state] is never mutated; a changing outcome runs on an isolated copy. */
    fun reduce(state: EngineState, intent: GameplayInteractionPulse): GameReductionResult {
        val sink = ReductionSink()
        return when (val outcome = admit(state.model, intent, sink)) {
            ReductionOutcome.REJECTED -> GameReductionResult.Rejected(checkNotNull(sink.rejection))
            ReductionOutcome.AUDIO_UNLOCKED -> GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.EnsureAudioUnlocked,
            )
            ReductionOutcome.IDLE_FRAME -> GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.create(
                    advanceAudio = true,
                    audioRealDeltaSeconds = (intent as GameplayInteractionPulse.FrameElapsed).realDeltaSeconds,
                ),
            )
            ReductionOutcome.UNCHANGED -> GameReductionResult.Accepted(
                state = state,
                outputs = SimulationOutputs.Empty,
            )
            ReductionOutcome.SUB_STEP -> {
                val candidate = state.model.deepCopy()
                reduceInPlace(candidate, intent, outcome, sink)
                GameReductionResult.Accepted(
                    state = EngineState(candidate),
                    outputs = SimulationOutputs.create(
                        advanceAudio = true,
                        audioRealDeltaSeconds = (intent as GameplayInteractionPulse.FrameElapsed).realDeltaSeconds,
                    ),
                )
            }
            ReductionOutcome.STEPPED -> {
                val candidate = state.model.deepCopy()
                reduceInPlace(candidate, intent, outcome, sink)
                val frameIntent = intent as? GameplayInteractionPulse.FrameElapsed
                val advanceAudio = frameIntent != null || sink.soundCues.isNotEmpty()
                GameReductionResult.Accepted(
                    state = EngineState(candidate),
                    outputs = SimulationOutputs.create(
                        visualFxCues = sink.visualFxCues,
                        progressUpdate = sink.progressUpdate,
                        advanceAudio = advanceAudio,
                        audioRealDeltaSeconds = frameIntent?.realDeltaSeconds ?: 0f,
                        audioCues = sink.soundCues,
                    ),
                )
            }
        }
    }

    /** Classifies [intent] against [model] without mutating it; a running frame's admitted accumulator goes to [sink]. */
    fun admit(model: MutableGameState, intent: GameplayInteractionPulse, sink: ReductionSink): ReductionOutcome {
        sink.reset()
        validate(model, intent)?.let { reason ->
            sink.rejection = reason
            return ReductionOutcome.REJECTED
        }

        if (intent == GameplayInteractionPulse.UserGestureObserved) return ReductionOutcome.AUDIO_UNLOCKED

        if (
            intent is GameplayInteractionPulse.FrameElapsed &&
            model.phase != GamePhase.RUNNING &&
            model.accumulator == 0f &&
            model.lastTransitionSteps == 0 &&
            !model.hasPendingReductionOutputs()
        ) {
            return ReductionOutcome.IDLE_FRAME
        }

        // A running frame admits its real time into the accumulator exactly once.
        if (intent is GameplayInteractionPulse.FrameElapsed && model.phase == GamePhase.RUNNING) {
            sink.admitsFrame = true
            sink.admittedAccumulator = nextSimulationAccumulator(
                currentAccumulator = model.accumulator,
                rawDeltaSeconds = intent.realDeltaSeconds,
                simulationSpeed = model.settings.simulationSpeed,
            )
        }
        if (
            sink.admitsFrame &&
            !fixedStepIsDue(sink.admittedAccumulator) &&
            !model.hasPendingReductionOutputs()
        ) {
            return ReductionOutcome.SUB_STEP
        }

        if (
            intent.leavesStateUnchanged(model) &&
            !model.hasPendingReductionOutputs()
        ) {
            return ReductionOutcome.UNCHANGED
        }
        return ReductionOutcome.STEPPED
    }

    /**
     * The shared in-place core: applies an [outcome] that [admit] returned for [intent] to [model], with no copy. A
     * sub-step frame only advances the accumulator; a stepped one runs and drains its outputs into [sink]. Every other
     * outcome leaves [model] untouched.
     */
    fun reduceInPlace(
        model: MutableGameState,
        intent: GameplayInteractionPulse,
        outcome: ReductionOutcome,
        sink: ReductionSink,
    ) {
        when (outcome) {
            ReductionOutcome.SUB_STEP -> {
                model.accumulator = sink.admittedAccumulator
                model.lastTransitionSteps = 0
                check(model.lastTransitionSteps == 0 && !fixedStepIsDue(model.accumulator)) {
                    "A sub-step frame cannot execute a fixed step"
                }
            }
            ReductionOutcome.STEPPED -> {
                if (sink.admitsFrame) {
                    model.runAdmittedFrame(sink.admittedAccumulator)
                } else {
                    applyIntent(model, intent)
                }
                sink.visualFxCues = model.takeVisualFxCues()
                sink.progressUpdate = model.takeProgressUpdate()
                sink.soundCues = model.takeSoundCues()
            }
            ReductionOutcome.REJECTED,
            ReductionOutcome.AUDIO_UNLOCKED,
            ReductionOutcome.IDLE_FRAME,
            ReductionOutcome.UNCHANGED,
            -> Unit
        }
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
            is GameplayInteractionPulse.SkillRequested -> state.requestSkill(intent.slot)
            GameplayInteractionPulse.ApexRequested -> state.requestApex()
        }
    }

    private fun validate(model: MutableGameState, intent: GameplayInteractionPulse): GameplayRejection? =
        when (intent) {
            is GameplayInteractionPulse.PointerMoved -> when {
                intent.x < 0f || intent.x > model.screenWidth ->
                    GameplayRejection.PointerOutsideViewport(GameplayPointerAxis.HORIZONTAL)
                intent.y < 0f || intent.y > model.screenHeight ->
                    GameplayRejection.PointerOutsideViewport(GameplayPointerAxis.VERTICAL)
                else -> null
            }
            else -> null
        }

    private fun GameplayInteractionPulse.leavesStateUnchanged(state: MutableGameState): Boolean =
        when (this) {
            is GameplayInteractionPulse.ViewportChanged -> {
                val nextWidth = max(1f, width)
                val nextHeight = max(1f, height)
                val nextUiScale = max(1f, density)
                nextWidth.sameBitsAs(state.screenWidth) &&
                    nextHeight.sameBitsAs(state.screenHeight) &&
                    nextUiScale.sameBitsAs(state.uiScale) &&
                    density.sameBitsAs(state.viewportDensity)
            }
            is GameplayInteractionPulse.PointerMoved -> state.pointerInputIsUnchanged(this)
            is GameplayInteractionPulse.BrakeChanged -> state.brakeInputIsUnchanged(this)
            GameplayInteractionPulse.DashRequested ->
                state.phase != GamePhase.RUNNING ||
                    state.dashBufferTime.sameBitsAs(MutableGameState.DASH_INPUT_BUFFER_SECONDS)
            // An empty slot or a stopped run buffers nothing; a request already fresh in the buffer is a repeat.
            is GameplayInteractionPulse.SkillRequested ->
                state.phase != GamePhase.RUNNING ||
                    state.skillLoadout.isEmpty(slot) ||
                    (state.skillBufferMask and (1 shl slot) != 0 &&
                        state.skillBufferTime.sameBitsAs(MutableGameState.SKILL_INPUT_BUFFER_SECONDS))
            // A locked Apex or a stopped run buffers nothing; a request already fresh in the buffer is a repeat.
            GameplayInteractionPulse.ApexRequested ->
                state.phase != GamePhase.RUNNING ||
                    !state.apexUnlocked ||
                    state.apexBufferTime.sameBitsAs(MutableGameState.SKILL_INPUT_BUFFER_SECONDS)
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
            hasPublishableProgress()
}
