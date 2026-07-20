// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.impl

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
import kinetickk.core.design.CanvasTextMeasurer
import kinetickk.feature.gameplay.api.GameplayOutput
import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.protocol.BrakeSource
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.presentation.canvas.drawGameplay
import kinetickk.feature.gameplay.presentation.input.GameInteractionValidator
import kinetickk.feature.gameplay.presentation.input.GameplayInput
import kinetickk.feature.gameplay.presentation.input.InteractionValidationResult
import kinetickk.feature.gameplay.presentation.input.ValidationFailure
import kinetickk.feature.gameplay.presentation.input.isHudControlPosition
import kinetickk.feature.gameplay.presentation.input.resolveGameplayPress

@Composable
internal fun GameplayContent(
    component: GameComponent,
    inputEnabled: Boolean,
    onShellOutput: (GameplayOutput) -> Unit,
) {
    val focusRequester = remember(component) { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current.density
    val interactionValidator = remember(component) { GameInteractionValidator() }
    var renderModelValue by remember(component) { mutableStateOf(component.snapshot().renderModel) }
    var visualFxProjectionValue by remember(component) {
        mutableStateOf(component.visualFxSnapshot())
    }
    var renderTimeSecondsValue by remember(component) { mutableFloatStateOf(0f) }

    fun dispatch(action: GameplayAction) {
        when (val result = component.dispatch(action)) {
            is GameDispatchResult.Committed -> {
                renderModelValue = result.snapshot.renderModel
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameDispatchResult.Rejected -> Unit
        }
    }

    fun dispatchValidated(result: InteractionValidationResult<GameplayAction>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
        }
    }

    fun dispatchInput(input: GameplayInput) {
        when (input) {
            is GameplayInput.Action -> dispatch(input.action)
            GameplayInput.OpenSettings -> onShellOutput(GameplayOutput.OpenSettings)
            GameplayInput.OpenRebirth -> onShellOutput(GameplayOutput.OpenRebirth)
            GameplayInput.ExitToHome -> {
                dispatch(GameplayAction.ExitRunRequested)
                onShellOutput(GameplayOutput.ExitToHome)
            }
            GameplayInput.RestartRun -> onShellOutput(GameplayOutput.RestartRun)
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
                    dispatch(GameplayAction.UserGestureObserved)
                }
                when (event.key) {
                    Key.Spacebar -> keyDown(event.type) { dispatch(GameplayAction.DashRequested) }
                    Key.ShiftLeft, Key.ShiftRight -> {
                        dispatch(
                            GameplayAction.BrakeChanged(
                                source = BrakeSource.KEYBOARD,
                                active = event.type == KeyEventType.KeyDown,
                            ),
                        )
                        true
                    }
                    Key.P, Key.Escape -> keyDown(event.type) { dispatch(GameplayAction.PauseToggled) }
                    Key.Q -> keyDown(event.type) { dispatch(GameplayAction.ChoicesRerolled) }
                    Key.One -> keyDown(event.type) { dispatch(GameplayAction.ChoiceSelected(0)) }
                    Key.Two -> keyDown(event.type) { dispatch(GameplayAction.ChoiceSelected(1)) }
                    Key.Three -> keyDown(event.type) { dispatch(GameplayAction.ChoiceSelected(2)) }
                    Key.Four -> keyDown(event.type) { dispatch(GameplayAction.ChoiceSelected(3)) }
                    Key.Enter -> keyDown(event.type) {
                        when (renderModelValue.phase) {
                            GamePhase.PAUSED -> dispatch(GameplayAction.PauseToggled)
                            GamePhase.GAME_OVER,
                            GamePhase.VICTORY,
                            -> onShellOutput(GameplayOutput.RestartRun)
                            GamePhase.RUNNING,
                            GamePhase.CHOICE,
                            -> Unit
                        }
                    }
                    Key.R -> keyDown(event.type) {
                        if (renderModelValue.phase == GamePhase.GAME_OVER ||
                            renderModelValue.phase == GamePhase.VICTORY
                        ) {
                            onShellOutput(GameplayOutput.RestartRun)
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
                        val currentRenderModel = component.snapshot().renderModel
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
                            dispatch(GameplayAction.UserGestureObserved)
                            currentRenderModel.resolveGameplayPress(validatedMove.x, validatedMove.y)
                                ?.let(::dispatchInput)
                        }
                        if (!pressed && wasPressedValue) {
                            dispatch(GameplayAction.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = false))
                            hudGestureActiveValue = false
                        }
                        val secondaryPressed = event.buttons.isSecondaryPressed
                        if (secondaryPressed != secondaryBrakeValue) {
                            secondaryBrakeValue = secondaryPressed
                            dispatch(
                                GameplayAction.BrakeChanged(
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
