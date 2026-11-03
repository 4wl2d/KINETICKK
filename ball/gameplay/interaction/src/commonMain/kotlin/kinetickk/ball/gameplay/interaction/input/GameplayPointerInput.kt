// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

/**
 * What the gameplay host gives its pointer routing: the committed render model and the applied
 * interface scale (both read at every event), the trial rules' open state, and its dispatch.
 */
internal interface GameplayPointerHost {
    val renderModel: GameplayRenderModel

    /** The applied interface scale the HUD is laid out at, in drawing and hit tests alike. */
    val interfaceScale: Float

    /** The trial panel's rules opened by a press (a toggle). */
    var trialInfoOpen: Boolean

    /** The trial panel's rules held open by a hovering mouse. */
    var trialInfoHovered: Boolean

    fun onPulse(pulse: GameplayInteractionPulse)

    fun onInput(input: GameplayInput)

    /** A running control was pressed: the HUD's press flash, then the control's input. */
    fun onControl(target: RunningControlTarget)

    /** A touch went down: the HUD hides its key caps and reward cards name slots by number. */
    fun onTouch()

    fun onInvalid(failure: ValidationFailure)
}

/**
 * Routes the secondary mouse button to the Brake until the pointer scope ends; a Brake still held
 * then is released.
 */
internal suspend fun AwaitPointerEventScope.routeSecondaryBrake(host: GameplayPointerHost) {
    var secondaryBrakeValue = false
    try {
        while (true) {
            val secondaryPressed = awaitPointerEvent(PointerEventPass.Initial)
                .buttons
                .isSecondaryPressed
            if (secondaryPressed != secondaryBrakeValue) {
                secondaryBrakeValue = secondaryPressed
                host.onPulse(GameplayInteractionPulse.BrakeChanged(BrakeSource.SECONDARY_POINTER, secondaryPressed))
            }
        }
    } finally {
        if (secondaryBrakeValue) {
            host.onPulse(GameplayInteractionPulse.BrakeChanged(BrakeSource.SECONDARY_POINTER, active = false))
        }
    }
}

/**
 * Routes the gameplay canvas's pointers until the pointer scope ends. Up to five pointers: one
 * steers, others hold or tap controls ([RunningTouchTracker]); a press on the trial panel's (!)
 * toggles its rules, a hovering mouse holds them open. Away from RUNNING a press resolves a pause
 * action ([resolveGameplayPress]), and choice and report screens handle their own clicks.
 */
internal suspend fun AwaitPointerEventScope.routeGameplayPointers(host: GameplayPointerHost, validator: GameInteractionValidator) {
    val tracker = RunningTouchTracker()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val currentRenderModel = host.renderModel
        val changes = event.changes
        val running = currentRenderModel.phase == GamePhase.RUNNING
        // Away from RUNNING a held Brake is released, and engaged again on return;
        // pointers keep their roles, so a thumb held through a choice steers again.
        if (tracker.running(running)) {
            host.onPulse(GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = running))
        }
        if (currentRenderModel.phase == GamePhase.CHOICE ||
            currentRenderModel.phase == GamePhase.GAME_OVER ||
            currentRenderModel.phase == GamePhase.VICTORY
        ) {
            // A finger lifted here is forgotten too, so no slot outlives its pointer.
            for (index in 0 until changes.size) {
                val change = changes[index]
                if (!change.pressed && change.previousPressed) tracker.up(change.id.value)
            }
            continue
        }
        var anyPressed = false
        for (index in 0 until changes.size) {
            if (changes[index].pressed) anyPressed = true
        }
        if (!anyPressed && changes.size > 0) {
            // Only a mouse hovers; a lifted finger must not keep the rules open.
            val first = changes[0]
            host.trialInfoHovered = first.type == PointerType.Mouse && event.type != PointerEventType.Exit &&
                currentRenderModel.isTrialInfoPosition(first.position.x, first.position.y, host.interfaceScale)
        }
        for (index in 0 until changes.size) {
            val change = changes[index]
            val x = change.position.x
            val y = change.position.y
            if (!x.isFinite() || !y.isFinite()) {
                // Rejected by the validator (it reports the field).
                validator.pointerMoved(x, y, density = currentRenderModel.viewportDensity)
                    .let { if (it is InteractionValidationResult.Invalid) host.onInvalid(it.failure) }
                continue
            }
            val id = change.id.value
            when {
                change.pressed && !change.previousPressed -> {
                    host.onPulse(GameplayInteractionPulse.UserGestureObserved)
                    if (change.type == PointerType.Touch) host.onTouch()
                    // A press on the (!) toggles the rules; a press anywhere else closes them.
                    val hudInterfaceScale = host.interfaceScale
                    val onTrialInfo = currentRenderModel.isTrialInfoPosition(x, y, hudInterfaceScale)
                    host.trialInfoOpen = onTrialInfo && !host.trialInfoOpen
                    if (!running) {
                        currentRenderModel.resolveGameplayPress(x, y, hudInterfaceScale)?.let(host::onInput)
                    } else {
                        val target = currentRenderModel.runningControlTargetAt(x, y, hudInterfaceScale)
                        when (tracker.down(id, target, hudPosition = onTrialInfo)) {
                            RunningTouchTracker.DOWN_CONTROL ->
                                if (target != null) host.onControl(target)
                            RunningTouchTracker.DOWN_STEER -> steerTo(host, validator, change, currentRenderModel)
                            else -> Unit
                        }
                    }
                }
                !change.pressed && change.previousPressed -> {
                    val lifted = tracker.up(id)
                    if (lifted == RunningTouchTracker.UP_BRAKE_RELEASED) {
                        host.onPulse(GameplayInteractionPulse.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = false))
                    } else if (lifted >= RunningTouchTracker.UP_STEER_PROMOTED && running) {
                        // The oldest waiting pointer takes over the steering from where it is.
                        val promoted = tracker.idAt(lifted - RunningTouchTracker.UP_STEER_PROMOTED)
                        for (other in 0 until changes.size) {
                            val candidate = changes[other]
                            if (candidate.id.value == promoted && candidate.pressed) {
                                steerTo(host, validator, candidate, currentRenderModel)
                            }
                        }
                    }
                }
                // Every pointer reports in each event; only the steering one, when it moved, steers.
                change.pressed -> if (running && tracker.move(id) && change.position != change.previousPosition) {
                    steerTo(host, validator, change, currentRenderModel)
                }
                // A hovering mouse steers (not over a control).
                change.type == PointerType.Mouse && running -> steerTo(host, validator, change, currentRenderModel)
            }
            if (change.pressed) change.consume()
        }
    }
}

/** Steers toward [change]'s position (validated) unless it lies on a HUD control. */
private fun steerTo(host: GameplayPointerHost, validator: GameInteractionValidator, change: PointerInputChange, model: GameplayRenderModel) {
    val x = change.position.x
    val y = change.position.y
    if (model.isHudControlPosition(x, y, host.interfaceScale)) return
    when (val result = validator.pointerMoved(x, y, density = model.viewportDensity)) {
        is InteractionValidationResult.Valid -> host.onPulse(result.intent)
        is InteractionValidationResult.Invalid -> host.onInvalid(result.failure)
    }
}
