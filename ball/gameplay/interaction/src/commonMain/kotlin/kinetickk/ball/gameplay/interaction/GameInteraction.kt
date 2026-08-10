// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GamePhase
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.isHudControlPosition
import kinetickk.ball.gameplay.interaction.input.resolveGameplayPress

@Composable
fun GameplayContent(
    component: GameplayInteractionPort,
    inputEnabled: Boolean,
    onOutput: (GameplayInteractionOutput) -> Unit,
) {
    val focusRequester = remember(component) { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current.density
    val interactionValidator = remember(component) { GameInteractionValidator() }
    var renderModelValue by remember(component) {
        mutableStateOf(requireNotNull(component.query(GameplayQuery.GetRender).renderModel))
    }
    var visualFxProjectionValue by remember(component) {
        mutableStateOf(component.visualFxSnapshot())
    }
    var renderTimeSecondsValue by remember(component) { mutableFloatStateOf(0f) }

    fun dispatch(pulse: GameplayInteractionPulse) {
        when (component.accept(pulse)) {
            is GameplayAcceptance.Accepted -> {
                renderModelValue = requireNotNull(
                    component.query(GameplayQuery.GetRender).renderModel,
                )
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
    }

    fun dispatchValidated(result: InteractionValidationResult<GameplayInteractionPulse>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
        }
    }

    fun dispatchInput(input: GameplayInput) {
        when (input) {
            is GameplayInput.Action -> dispatch(input.action)
            GameplayInput.OpenSettings -> onOutput(GameplayInteractionOutput.OpenSettings)
            GameplayInput.OpenRebirth -> onOutput(GameplayInteractionOutput.OpenRebirth)
            GameplayInput.ExitToHome -> onOutput(GameplayInteractionOutput.ExitToHome)
            GameplayInput.RestartRun -> onOutput(GameplayInteractionOutput.RestartRun)
        }
    }

    LaunchedEffect(component, inputEnabled) {
        if (inputEnabled) focusRequester.requestFocus()
    }

    LaunchedEffect(component) {
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val delta = (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame
            when (val result = interactionValidator.frameElapsed(delta)) {
                is InteractionValidationResult.Valid -> {
                    renderTimeSecondsValue += result.intent.realDeltaSeconds.coerceAtMost(0.1f)
                    dispatch(result.intent)
                }
                is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
            }
        }
    }

    val textMeasurer = CanvasTextMeasurer(
        delegate = composeTextMeasurer,
        scale = renderModelValue.settings.textScale,
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050610))
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { size ->
                dispatchValidated(
                    interactionValidator.viewportChanged(
                        rawWidthPx = size.width.toFloat(),
                        rawHeightPx = size.height.toFloat(),
                        rawDensity = density,
                    ),
                )
            }
            .onKeyEvent { event ->
                if (!inputEnabled) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    dispatch(GameplayInteractionPulse.UserGestureObserved)
                }
                when (event.key) {
                    Key.Spacebar -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.DashRequested)
                    }
                    Key.ShiftLeft, Key.ShiftRight -> {
                        dispatch(
                            GameplayInteractionPulse.BrakeChanged(
                                source = BrakeSource.KEYBOARD,
                                active = event.type == KeyEventType.KeyDown,
                            ),
                        )
                        true
                    }
                    Key.P, Key.Escape -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.PauseToggled)
                    }
                    Key.Q -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.ChoicesRerolled)
                    }
                    Key.One -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.ChoiceSelected(0))
                    }
                    Key.Two -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.ChoiceSelected(1))
                    }
                    Key.Three -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.ChoiceSelected(2))
                    }
                    Key.Four -> keyDown(event.type) {
                        dispatch(GameplayInteractionPulse.ChoiceSelected(3))
                    }
                    Key.Enter -> keyDown(event.type) {
                        when (renderModelValue.phase) {
                            GamePhase.PAUSED -> dispatch(GameplayInteractionPulse.PauseToggled)
                            GamePhase.GAME_OVER,
                            GamePhase.VICTORY,
                            -> onOutput(GameplayInteractionOutput.RestartRun)
                            GamePhase.RUNNING,
                            GamePhase.CHOICE,
                            -> Unit
                        }
                    }
                    Key.R -> keyDown(event.type) {
                        if (renderModelValue.phase == GamePhase.GAME_OVER ||
                            renderModelValue.phase == GamePhase.VICTORY
                        ) {
                            onOutput(GameplayInteractionOutput.RestartRun)
                        }
                    }
                    else -> false
                }
            }
            .pointerInput(component, inputEnabled) {
                if (!inputEnabled) return@pointerInput
                awaitPointerEventScope {
                    var wasPressedValue = false
                    var hudGestureActiveValue = false
                    var secondaryBrakeValue = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val position = event.changes.firstOrNull()?.position
                        val pressed = event.changes.any { it.pressed }
                        val currentRenderModel = requireNotNull(
                            component.query(GameplayQuery.GetRender).renderModel,
                        )
                        val validatedMove = position?.let { pointerPosition ->
                            when (
                                val result = interactionValidator.pointerMoved(
                                    pointerPosition.x,
                                    pointerPosition.y,
                                )
                            ) {
                                is InteractionValidationResult.Valid -> result.intent
                                is InteractionValidationResult.Invalid -> {
                                    reportInvalidInteractionInput(result.failure)
                                    null
                                }
                            }
                        }
                        if (pressed && !wasPressedValue && validatedMove != null) {
                            hudGestureActiveValue = currentRenderModel.isHudControlPosition(
                                validatedMove.x,
                                validatedMove.y,
                            )
                        }
                        if (
                            validatedMove != null &&
                            currentRenderModel.phase == GamePhase.RUNNING &&
                            !hudGestureActiveValue &&
                            !currentRenderModel.isHudControlPosition(validatedMove.x, validatedMove.y)
                        ) {
                            dispatch(validatedMove)
                        }
                        if (pressed && !wasPressedValue && validatedMove != null) {
                            dispatch(GameplayInteractionPulse.UserGestureObserved)
                            currentRenderModel.resolveGameplayPress(validatedMove.x, validatedMove.y)
                                ?.let(::dispatchInput)
                        }
                        if (!pressed && wasPressedValue) {
                            dispatch(
                                GameplayInteractionPulse.BrakeChanged(
                                    BrakeSource.TOUCH_CONTROL,
                                    active = false,
                                ),
                            )
                            hudGestureActiveValue = false
                        }
                        val secondaryPressed = event.buttons.isSecondaryPressed
                        if (secondaryPressed != secondaryBrakeValue) {
                            secondaryBrakeValue = secondaryPressed
                            dispatch(
                                GameplayInteractionPulse.BrakeChanged(
                                    BrakeSource.SECONDARY_POINTER,
                                    secondaryPressed,
                                ),
                            )
                        }
                        event.changes.forEach { change ->
                            if (change.pressed) change.consume()
                        }
                        wasPressedValue = pressed
                    }
                }
            },
    ) {
        drawGameplay(
            engine = renderModelValue,
            visualFx = visualFxProjectionValue,
            textMeasurer = textMeasurer,
            renderTime = renderTimeSecondsValue,
        )
    }
}

private inline fun keyDown(type: KeyEventType, action: () -> Unit): Boolean {
    if (type == KeyEventType.KeyDown) action()
    return true
}

private fun reportInvalidInteractionInput(failure: ValidationFailure) {
    println("KINETICKK interaction input dropped: ${failure.code}")
}
