// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.feature.game.domain.engine.GameDispatchResult
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.protocol.BrakeSource
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.presentation.canvas.drawKinetickk
import kinetickk.feature.game.presentation.canvas.GameTextMeasurer
import kinetickk.feature.game.presentation.input.GameInteractionValidator
import kinetickk.feature.game.presentation.input.InteractionValidationResult
import kinetickk.feature.game.presentation.input.isHudControlPosition
import kinetickk.feature.game.presentation.input.resolvePointerPress
import kinetickk.feature.game.presentation.input.ValidationFailure

@Composable
internal fun GameContent() {
    val component = remember { GameComponent.create() }
    val focusRequester = remember { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current.density
    val interactionValidator = remember(component) { GameInteractionValidator() }
    var projectionValue by remember(component) { mutableStateOf(component.snapshot().projection) }
    var visualFxProjectionValue by remember(component) {
        mutableStateOf(component.visualFxSnapshot())
    }
    var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }

    fun dispatch(intent: GameIntent) {
        when (val result = component.dispatch(intent)) {
            is GameDispatchResult.Committed -> {
                projectionValue = result.snapshot.projection
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameDispatchResult.Rejected -> Unit
        }
    }

    fun dispatchValidated(result: InteractionValidationResult<GameIntent>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
        }
    }

    DisposableEffect(component) {
        onDispose { component.close() }
    }

    LaunchedEffect(component) {
        focusRequester.requestFocus()
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
                is InteractionValidationResult.Invalid -> {
                    reportInvalidInteractionInput(result.failure)
                }
            }
        }
    }

    val textMeasurer = GameTextMeasurer(
        delegate = composeTextMeasurer,
        scale = projectionValue.settings.textScale,
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
                if (event.type == KeyEventType.KeyDown) {
                    dispatch(GameIntent.UserGestureObserved)
                }
                when (event.key) {
                    Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyDown) dispatch(GameIntent.DashRequested)
                        true
                    }
                    Key.ShiftLeft, Key.ShiftRight -> {
                        dispatch(
                            GameIntent.BrakeChanged(
                                source = BrakeSource.KEYBOARD,
                                active = event.type == KeyEventType.KeyDown,
                            ),
                        )
                        true
                    }
                    Key.P, Key.Escape -> {
                        if (event.type == KeyEventType.KeyDown) {
                            dispatch(
                                if (event.key == Key.Escape) {
                                    GameIntent.EscapeRequested
                                } else {
                                    GameIntent.PauseToggled
                                },
                            )
                        }
                        true
                    }
                    Key.S -> keyDown(event.type) { dispatch(GameIntent.ScreenOpenRequested(UiScreen.SETTINGS)) }
                    Key.L -> keyDown(event.type) { dispatch(GameIntent.ScreenOpenRequested(UiScreen.LAB)) }
                    Key.A -> keyDown(event.type) { dispatch(GameIntent.ScreenOpenRequested(UiScreen.ARMORY)) }
                    Key.B -> keyDown(event.type) { dispatch(GameIntent.ScreenOpenRequested(UiScreen.REBIRTH)) }
                    Key.C -> keyDown(event.type) { dispatch(GameIntent.ScreenOpenRequested(UiScreen.CODEX)) }
                    Key.M -> keyDown(event.type) { dispatch(GameIntent.MuteToggled) }
                    Key.Q -> keyDown(event.type) { dispatch(GameIntent.ChoicesRerolled) }
                    Key.Enter -> keyDown(event.type) { dispatch(GameIntent.EnterPressed) }
                    Key.One -> keyDown(event.type) { dispatch(GameIntent.ChoiceSelected(0)) }
                    Key.Two -> keyDown(event.type) { dispatch(GameIntent.ChoiceSelected(1)) }
                    Key.Three -> keyDown(event.type) { dispatch(GameIntent.ChoiceSelected(2)) }
                    Key.Four -> keyDown(event.type) { dispatch(GameIntent.ChoiceSelected(3)) }
                    Key.R -> {
                        if (
                            event.type == KeyEventType.KeyDown &&
                            component.snapshot().projection.phase in listOf(GamePhase.GAME_OVER, GamePhase.VICTORY)
                        ) {
                            dispatch(GameIntent.RunStartRequested)
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(component) {
                awaitPointerEventScope {
                    var wasPressedValue = false
                    var hudGestureActiveValue = false
                    var secondaryBrakeValue = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val position = event.changes.firstOrNull()?.position
                        val pressed = event.changes.any { it.pressed }
                        val currentProjection = component.snapshot().projection
                        val validatedMove = position?.let {
                            when (val result = interactionValidator.pointerMoved(it.x, it.y)) {
                                is InteractionValidationResult.Valid -> result.intent
                                is InteractionValidationResult.Invalid -> {
                                    reportInvalidInteractionInput(result.failure)
                                    null
                                }
                            }
                        }
                        if (pressed && !wasPressedValue && validatedMove != null) {
                            hudGestureActiveValue = currentProjection.isHudControlPosition(
                                validatedMove.x,
                                validatedMove.y,
                            )
                        }
                        if (
                            validatedMove != null &&
                            currentProjection.phase == GamePhase.RUNNING &&
                            currentProjection.screen == UiScreen.GAME &&
                            !hudGestureActiveValue &&
                            !currentProjection.isHudControlPosition(validatedMove.x, validatedMove.y)
                        ) {
                            dispatch(validatedMove)
                        }
                        if (pressed && !wasPressedValue && validatedMove != null) {
                            dispatch(GameIntent.UserGestureObserved)
                            currentProjection.resolvePointerPress(validatedMove.x, validatedMove.y)?.let(::dispatch)
                        }
                        if (!pressed && wasPressedValue) {
                            dispatch(GameIntent.BrakeChanged(BrakeSource.TOUCH_CONTROL, false))
                            hudGestureActiveValue = false
                        }
                        val secondaryPressed = event.buttons.isSecondaryPressed
                        if (secondaryPressed != secondaryBrakeValue) {
                            secondaryBrakeValue = secondaryPressed
                            dispatch(GameIntent.BrakeChanged(BrakeSource.SECONDARY_POINTER, secondaryPressed))
                        }
                        event.changes.forEach { change ->
                            if (change.pressed) change.consume()
                        }
                        wasPressedValue = pressed
                    }
                }
            },
    ) {
        drawKinetickk(
            engine = projectionValue,
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
