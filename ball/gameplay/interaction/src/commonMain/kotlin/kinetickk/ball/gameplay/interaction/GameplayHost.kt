// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.focus.FocusRequester
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hud.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.canvas.hud.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.canvas.hud.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.canvas.look.badgeState
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.GameplayKeyInput
import kinetickk.ball.gameplay.interaction.input.GameplayPointerHost
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.filledSkillSlots
import kinetickk.ball.gameplay.interaction.input.runningControlInput
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.skillSlot
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkApexState
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kotlin.time.TimeSource

/**
 * What [GameplayContent] keeps for one [component]: the published render model and FX projection, the render clock,
 * the HUD and control memories, the FPS readout, the trial panel's open states, window focus and viewport tracking.
 * Remembered per component; every value here is presentation state of the host.
 */
internal class GameplayHostState(component: GameplayInteractionPort) {
    val focusRequester = FocusRequester()
    val interactionValidator = GameInteractionValidator()

    // The owner republishes the same snapshot instances while nothing changed, so a reference check
    // stands in for a structural comparison of the whole model on every frame.
    val renderModel = mutableStateOf(requireNotNull(component.renderSnapshot().renderModel), referentialEqualityPolicy())
    val visualFxProjection = mutableStateOf(component.visualFxSnapshot(), referentialEqualityPolicy())
    val renderTimeSeconds = mutableFloatStateOf(0f)
    val hudMemory = HudPresentationMemory()
    val controlLog = RunningControlPressLog()
    val fpsCounter = GameplayFpsCounter()
    val fpsReadout = mutableIntStateOf(GameplayFpsCounter.NO_VALUE)

    // Names in the skill and Apex semantics; a component plays one content snapshot.
    val content = requireNotNull(component.renderSnapshot().renderModel).content
    val rewardUiReady = mutableStateOf(false)

    // The trial panel's (!) opens by keyboard focus, a tap/click (toggle) or a hovering mouse.
    val trialInfoFocused = mutableStateOf(false)
    val trialInfoOpen = mutableStateOf(false)
    val trialInfoHovered = mutableStateOf(false)

    // The composition reads only these facts; the Canvas draws the full model every frame, so a
    // running frame invalidates the draw without recomposing the gameplay host.
    val compositionFacts: State<GameplayCompositionFacts> =
        derivedStateOf(structuralEqualityPolicy()) { renderModel.value.compositionFacts() }
    val windowFocus = GameplayWindowFocus()
    val viewport = GameplayViewportPixels()
}

/**
 * The gameplay host's pulse route for one [host]: every pulse goes through [dispatch] (with the crash context and the
 * dispatch timing), validated pulses through [dispatchValidated]. Remembered per host and diagnostics, as the
 * composition's route was.
 */
internal class GameplayHostDispatch(
    private val component: GameplayInteractionPort,
    private val host: GameplayHostState,
    private val diagnostics: CrashDiagnostics,
    private val performanceDiagnostics: PerformanceDiagnostics?,
) {
    fun reportInvalidInteractionInput(failure: ValidationFailure) {
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.event("warning.input", "Dropped input: ${failure.code}")
    }

    fun dispatch(pulse: GameplayInteractionPulse) {
        val dispatchStartedAt =
            if (DIAGNOSTICS_ENABLED && performanceDiagnostics?.capturesPerformance == true) TimeSource.Monotonic.markNow() else null
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled && pulse !is GameplayInteractionPulse.FrameElapsed) {
            val before = component.renderSnapshot()
            diagnostics.context("gameplay.before-input") { before.crashContext() }
            diagnostics.context("gameplay.input") { pulse.crashDescription() }
            diagnostics.event("gameplay.input", pulse.crashDescription(), highFrequency = pulse is GameplayInteractionPulse.PointerMoved)
        }
        val acceptance = try {
            component.accept(pulse)
        } catch (failure: Throwable) {
            if (DIAGNOSTICS_ENABLED && diagnostics.enabled && pulse is GameplayInteractionPulse.FrameElapsed) {
                diagnostics.context("gameplay.input") { pulse.crashDescription() }
            }
            // A target can publish atomically and then surface a deferred side-effect fault.
            // Republishing the immutable values is also harmless for a pre-commit fault because
            // Compose suppresses assigning the same instance, so the hot success path needs no pre-query.
            try {
                val committed = component.renderSnapshot()
                host.renderModel.value = requireNotNull(committed.renderModel)
                host.visualFxProjection.value = component.visualFxSnapshot()
                if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.context("gameplay.committed") { committed.crashContext() }
            } catch (snapshotFailure: Throwable) {
                if (snapshotFailure !== failure) failure.addSuppressed(snapshotFailure)
            }
            if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.event("gameplay.failure", failure.toString())
            throw failure
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> {
                host.renderModel.value = requireNotNull(component.renderSnapshot().renderModel)
                host.visualFxProjection.value = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled && pulse !is GameplayInteractionPulse.FrameElapsed) {
            val committed = component.renderSnapshot()
            diagnostics.context("gameplay.committed") { committed.crashContext() }
        }
        if (DIAGNOSTICS_ENABLED && dispatchStartedAt != null) {
            performanceDiagnostics?.phase(DiagnosticPhase.Dispatch, dispatchStartedAt.elapsedNow().inWholeNanoseconds)
            if (acceptance is GameplayAcceptance.Accepted) reportWorkload()
        }
    }

    private fun reportWorkload() {
        val model = host.renderModel.value
        performanceDiagnostics?.workload(
            enemies = model.enemies.size,
            projectiles = model.projectiles.size,
            pickups = model.pickups.size,
            effects = with(host.visualFxProjection.value) {
                particles.size + motionEchoes.size + shockwaves.size + damageNumbers.size +
                    weaponArcs.size + elementBursts.size + reactionStamps.size + skillBursts.size + apexBursts.size
            },
        )
    }

    fun dispatchValidated(result: InteractionValidationResult<GameplayInteractionPulse>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
        }
    }

    fun togglePerformanceTelemetry() {
        performanceDiagnostics?.toggleOverlay()
    }
}

/**
 * The gameplay host's input routes: host inputs through [dispatchInput], running controls through
 * [pressRunningControl] and key events through [applyKeyInput]; it is also the pointer routes' host. Remembered per
 * pulse route and output, as the composition's routes were.
 */
internal class GameplayHostInputs(
    private val host: GameplayHostState,
    private val pulses: GameplayHostDispatch,
    private val interfaceScaleState: State<Float>,
    private val onOutput: (GameplayInteractionOutput) -> Unit,
) : GameplayPointerHost {
    fun dispatchInput(input: GameplayInput) {
        when (input) {
            is GameplayInput.Action -> pulses.dispatch(input.action)
            GameplayInput.OpenSettings -> onOutput(GameplayInteractionOutput.OpenSettings)
            GameplayInput.OpenWorkshop -> onOutput(GameplayInteractionOutput.OpenWorkshop)
            GameplayInput.OpenRebirth -> onOutput(GameplayInteractionOutput.OpenRebirth)
            GameplayInput.OpenCodex -> onOutput(GameplayInteractionOutput.OpenCodex)
            GameplayInput.ExitToHome -> onOutput(GameplayInteractionOutput.ExitToHome)
            GameplayInput.RestartRun -> onOutput(GameplayInteractionOutput.RestartRun)
            GameplayInput.TogglePerformance -> pulses.togglePerformanceTelemetry()
        }
    }

    /**
     * A running control was pressed (touch, mouse, key or accessibility): the HUD's press flash, the
     * Apex refusal shake when it is cooling, then the control's input. An empty skill slot is a HUD
     * position that does nothing.
     */
    fun pressRunningControl(target: RunningControlTarget) {
        val model = host.renderModel.value
        val skillSlots = model.filledSkillSlots()
        val slot = target.skillSlot
        if (slot >= 0 && skillSlots and (1 shl slot) == 0) return
        host.controlLog.press(target, host.renderTimeSeconds.floatValue)
        if (target == RunningControlTarget.APEX && model.apex.badgeState() == KkApexState.COOLING) {
            host.controlLog.refuseApex(host.renderTimeSeconds.floatValue)
        }
        if (slot >= 0) {
            pulses.dispatchValidated(host.interactionValidator.skillRequested(slot))
            return
        }
        runningControlInput(target, skillSlots)?.let(::dispatchInput)
    }

    /** Applies a key event's [GameplayKeyInput] in its documented order and says whether it is consumed. */
    fun applyKeyInput(key: GameplayKeyInput): Boolean {
        if (key.keyboardPlay) {
            host.controlLog.keyHints = true
            host.controlLog.touchInput = false
        }
        if (key.gesture) pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
        key.control?.let(::pressRunningControl)
        if (key.choice != GameplayKeyInput.NO_CHOICE) pulses.dispatchValidated(host.interactionValidator.choiceSelected(key.choice))
        key.input?.let(::dispatchInput)
        return key.consumed
    }

    // Read at every pointer event: the latest model, interface scale and trial rules state.
    override val renderModel: GameplayRenderModel get() = host.renderModel.value
    override val interfaceScale: Float get() = interfaceScaleState.value
    override var trialInfoOpen: Boolean
        get() = host.trialInfoOpen.value
        set(value) { host.trialInfoOpen.value = value }
    override var trialInfoHovered: Boolean
        get() = host.trialInfoHovered.value
        set(value) { host.trialInfoHovered.value = value }
    override fun onPulse(pulse: GameplayInteractionPulse) = pulses.dispatch(pulse)
    override fun onInput(input: GameplayInput) = dispatchInput(input)
    override fun onControl(target: RunningControlTarget) = pressRunningControl(target)
    override fun onTouch() {
        host.controlLog.keyHints = false
        host.controlLog.touchInput = true
    }
    override fun onInvalid(failure: ValidationFailure) = pulses.reportInvalidInteractionInput(failure)
}
