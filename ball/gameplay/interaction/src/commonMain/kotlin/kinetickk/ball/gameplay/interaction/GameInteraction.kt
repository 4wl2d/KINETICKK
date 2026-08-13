// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.canvas.drawPerformanceHud
import kinetickk.ball.gameplay.interaction.canvas.shouldDrawRunningPresentation
import kinetickk.ball.gameplay.interaction.canvas.toPerformanceHudProjection
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.isHudControlPosition
import kinetickk.ball.gameplay.interaction.input.resolveGameplayPress
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.TerminalLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.terminalLayoutGeometry
import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceSnapshot
import kinetickk.ball.gameplay.interaction.performance.GameplayPerformanceTelemetry
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.math.roundToInt

internal const val MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS: Float = 0.1f

internal fun selectGameplayPresentationDelta(realDeltaSeconds: Float): Float =
    realDeltaSeconds.coerceAtMost(MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS)

@Composable
fun GameplayContent(
    component: GameplayInteractionPort,
    inputEnabled: Boolean,
    onOutput: (GameplayInteractionOutput) -> Unit,
) {
    val focusRequester = remember(component) { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val interactionValidator = remember(component) { GameInteractionValidator() }
    var renderModelValue by remember(component) {
        mutableStateOf(requireNotNull(component.renderSnapshot().renderModel))
    }
    var visualFxProjectionValue by remember(component) {
        mutableStateOf(component.visualFxSnapshot())
    }
    var renderTimeSecondsValue by remember(component) { mutableFloatStateOf(0f) }
    val performanceTelemetry = remember(component) { GameplayPerformanceTelemetry() }
    var performanceEnabledValue by remember(component) { mutableStateOf(false) }
    var performanceSnapshotValue by remember(component) {
        mutableStateOf(GameplayPerformanceSnapshot.Empty)
    }
    val performanceEnabledState = rememberUpdatedState(performanceEnabledValue)

    fun dispatch(
        pulse: GameplayInteractionPulse,
        collectPerformance: Boolean = performanceEnabledValue,
    ) {
        val dispatchStartedAt = if (collectPerformance) TimeSource.Monotonic.markNow() else null
        val acceptance = try {
            component.accept(pulse)
        } catch (failure: Throwable) {
            val committed = component.renderSnapshot()
            // A target can publish atomically and then surface a deferred side-effect fault.
            // Republishing the immutable values is also harmless for a pre-commit fault because
            // Compose suppresses equal assignments, so the hot success path needs no pre-query.
            renderModelValue = requireNotNull(committed.renderModel)
            visualFxProjectionValue = component.visualFxSnapshot()
            throw failure
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> {
                renderModelValue = requireNotNull(component.renderSnapshot().renderModel)
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
        if (dispatchStartedAt != null) {
            performanceTelemetry.recordDispatchPipelineMillis(
                dispatchStartedAt.elapsedNow().toDouble(DurationUnit.MILLISECONDS),
            )
            if (acceptance is GameplayAcceptance.Accepted) {
                performanceTelemetry.recordEntityCounts(
                    enemies = renderModelValue.enemies.size,
                    projectiles = renderModelValue.projectiles.size,
                    pickups = renderModelValue.pickups.size,
                    trailPoints = renderModelValue.trail.size,
                )
            }
        }
    }

    fun dispatchValidated(result: InteractionValidationResult<GameplayInteractionPulse>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
        }
    }

    fun togglePerformanceTelemetry() {
        val nextPerformanceEnabled = !performanceEnabledValue
        performanceTelemetry.reset()
        if (nextPerformanceEnabled) {
            performanceTelemetry.recordEntityCounts(
                enemies = renderModelValue.enemies.size,
                projectiles = renderModelValue.projectiles.size,
                pickups = renderModelValue.pickups.size,
                trailPoints = renderModelValue.trail.size,
            )
        }
        performanceSnapshotValue = performanceTelemetry.snapshot()
        performanceEnabledValue = nextPerformanceEnabled
    }

    fun dispatchInput(
        input: GameplayInput,
        collectPerformance: Boolean = performanceEnabledValue,
    ) {
        when (input) {
            is GameplayInput.Action -> dispatch(input.action, collectPerformance)
            GameplayInput.OpenSettings -> onOutput(GameplayInteractionOutput.OpenSettings)
            GameplayInput.OpenRebirth -> onOutput(GameplayInteractionOutput.OpenRebirth)
            GameplayInput.ExitToHome -> onOutput(GameplayInteractionOutput.ExitToHome)
            GameplayInput.RestartRun -> onOutput(GameplayInteractionOutput.RestartRun)
            GameplayInput.TogglePerformance -> togglePerformanceTelemetry()
        }
    }

    LaunchedEffect(component, inputEnabled, focusRequester) {
        if (inputEnabled) focusRequester.requestFocus()
    }

    LaunchedEffect(
        component,
        interactionValidator,
        performanceTelemetry,
        performanceEnabledValue,
    ) {
        val collectPerformance = performanceEnabledValue
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val frameIntervalNanos = frame - previousFrame
            val delta = frameIntervalNanos / 1_000_000_000f
            previousFrame = frame
            if (collectPerformance && frameIntervalNanos >= 0L) {
                performanceTelemetry.recordFrameIntervalMillis(frameIntervalNanos / 1_000_000.0)
            }
            when (val result = interactionValidator.frameElapsed(delta)) {
                is InteractionValidationResult.Valid -> {
                    renderTimeSecondsValue += selectGameplayPresentationDelta(
                        result.intent.realDeltaSeconds,
                    )
                    dispatch(result.intent, collectPerformance)
                }
                is InteractionValidationResult.Invalid -> reportInvalidInteractionInput(result.failure)
            }
            if (collectPerformance && performanceTelemetry.shouldPublishSnapshot(frame)) {
                performanceSnapshotValue = performanceTelemetry.snapshot()
            }
        }
    }

    val textScale = renderModelValue.settings.textScale
    val textMeasurer = remember(composeTextMeasurer, textScale) {
        CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = textScale,
        )
    }
    val layoutDimensions = remember(
        renderModelValue.screenWidth,
        renderModelValue.screenHeight,
        renderModelValue.uiScale,
    ) {
        GameplayLayoutDimensions(
            width = renderModelValue.screenWidth,
            height = renderModelValue.screenHeight,
            scale = renderModelValue.uiScale,
        )
    }
    val pauseLayout = if (renderModelValue.phase == GamePhase.PAUSED) {
        remember(layoutDimensions) {
            pauseLayoutGeometry(
                layoutDimensions.width,
                layoutDimensions.height,
                layoutDimensions.scale,
            )
        }
    } else {
        null
    }
    val choiceLayout = if (renderModelValue.phase == GamePhase.CHOICE) {
        remember(
            layoutDimensions,
            renderModelValue.choices.size,
            renderModelValue.choicesCanReroll,
        ) {
            choiceLayoutGeometry(
                layoutDimensions.width,
                layoutDimensions.height,
                layoutDimensions.scale,
                renderModelValue.choices.size,
                renderModelValue.choicesCanReroll,
            )
        }
    } else {
        null
    }
    val terminalLayout = if (
        renderModelValue.phase == GamePhase.GAME_OVER ||
        renderModelValue.phase == GamePhase.VICTORY
    ) {
        remember(layoutDimensions, renderModelValue.phase) {
            terminalLayoutGeometry(
                layoutDimensions.width,
                layoutDimensions.height,
                layoutDimensions.scale,
                victory = renderModelValue.phase == GamePhase.VICTORY,
            )
        }
    } else {
        null
    }
    val performanceHudProjection = if (performanceEnabledValue) {
        remember(performanceSnapshotValue) {
            performanceSnapshotValue.toPerformanceHudProjection()
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050610))
            .testTag(GAMEPLAY_ROOT_TAG)
            .semantics {
                contentDescription = "KINETICKK gameplay"
            }
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
                if (event.key == Key.F3) {
                    return@onKeyEvent keyDown(event.type, ::togglePerformanceTelemetry)
                }
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
                        dispatchValidated(interactionValidator.choiceSelected(0))
                    }
                    Key.Two -> keyDown(event.type) {
                        dispatchValidated(interactionValidator.choiceSelected(1))
                    }
                    Key.Three -> keyDown(event.type) {
                        dispatchValidated(interactionValidator.choiceSelected(2))
                    }
                    Key.Four -> keyDown(event.type) {
                        dispatchValidated(interactionValidator.choiceSelected(3))
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
                    var secondaryBrakeValue = false
                    try {
                        while (true) {
                            val secondaryPressed = awaitPointerEvent(PointerEventPass.Initial)
                                .buttons
                                .isSecondaryPressed
                            if (secondaryPressed != secondaryBrakeValue) {
                                secondaryBrakeValue = secondaryPressed
                                dispatch(
                                    GameplayInteractionPulse.BrakeChanged(
                                        BrakeSource.SECONDARY_POINTER,
                                        secondaryPressed,
                                    ),
                                    performanceEnabledState.value,
                                )
                            }
                        }
                    } finally {
                        if (secondaryBrakeValue) {
                            dispatch(
                                GameplayInteractionPulse.BrakeChanged(
                                    BrakeSource.SECONDARY_POINTER,
                                    active = false,
                                ),
                                performanceEnabledState.value,
                            )
                        }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    component,
                    inputEnabled,
                    interactionValidator,
                    performanceTelemetry,
                ) {
                    if (!inputEnabled) return@pointerInput
                    awaitPointerEventScope {
                        var wasPressedValue = false
                        var hudGestureActiveValue = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val collectPerformance = performanceEnabledState.value
                            val position = event.changes.firstOrNull()?.position
                            val pressed = event.changes.any { it.pressed }
                            val currentRenderModel = renderModelValue
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
                            val isHudControlPosition = validatedMove != null &&
                                currentRenderModel.phase == GamePhase.RUNNING &&
                                currentRenderModel.isHudControlPosition(
                                    validatedMove.x,
                                    validatedMove.y,
                                )
                            if (pressed && !wasPressedValue && validatedMove != null) {
                                hudGestureActiveValue = isHudControlPosition
                            }
                            if (
                                validatedMove != null &&
                                currentRenderModel.phase == GamePhase.RUNNING &&
                                !hudGestureActiveValue &&
                                !isHudControlPosition
                            ) {
                                dispatch(validatedMove, collectPerformance)
                            }
                            if (pressed && !wasPressedValue && validatedMove != null) {
                                dispatch(GameplayInteractionPulse.UserGestureObserved, collectPerformance)
                                currentRenderModel.resolveGameplayPress(validatedMove.x, validatedMove.y)
                                    ?.let { dispatchInput(it, collectPerformance) }
                            }
                            if (!pressed && wasPressedValue) {
                                dispatch(
                                    GameplayInteractionPulse.BrakeChanged(
                                        BrakeSource.TOUCH_CONTROL,
                                        active = false,
                                    ),
                                    collectPerformance,
                                )
                                hudGestureActiveValue = false
                            }
                            event.changes.forEach { change ->
                                if (change.pressed) change.consume()
                            }
                            wasPressedValue = pressed
                        }
                    }
                },
        ) {
            val drawStartedAt = if (performanceEnabledValue) TimeSource.Monotonic.markNow() else null
            drawGameplay(
                engine = renderModelValue,
                visualFx = visualFxProjectionValue,
                textMeasurer = textMeasurer,
                renderTime = renderTimeSecondsValue,
                pauseLayout = pauseLayout,
                choiceLayout = choiceLayout,
                terminalLayout = terminalLayout,
            )
            if (drawStartedAt != null) {
                // State writes can invalidate the draw scope before recomposition publishes the
                // lazily-built HUD projection. Treat that single transitional draw as HUD-free;
                // the next recomposition supplies the exact snapshot without allocating here.
                val hudProjection = performanceHudProjection
                if (hudProjection != null && shouldDrawRunningPresentation(renderModelValue.phase)) {
                    drawPerformanceHud(
                        projection = hudProjection,
                        textMeasurer = textMeasurer,
                    )
                }
                performanceTelemetry.recordCanvasDrawMillis(
                    drawStartedAt.elapsedNow().toDouble(DurationUnit.MILLISECONDS),
                )
            }
        }
        if (inputEnabled) {
            GameplaySemanticControls(
                engine = renderModelValue,
                density = localDensity,
                performanceEnabled = performanceEnabledValue,
                pauseLayout = pauseLayout,
                choiceLayout = choiceLayout,
                terminalLayout = terminalLayout,
                onInput = { input ->
                    dispatch(GameplayInteractionPulse.UserGestureObserved)
                    dispatchInput(input)
                },
                onBrakeChanged = { active ->
                    if (active) dispatch(GameplayInteractionPulse.UserGestureObserved)
                    dispatch(
                        GameplayInteractionPulse.BrakeChanged(
                            source = BrakeSource.TOUCH_CONTROL,
                            active = active,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun GameplaySemanticControls(
    engine: GameplayRenderModel,
    density: Density,
    performanceEnabled: Boolean,
    pauseLayout: PauseLayoutGeometry?,
    choiceLayout: ChoiceLayoutGeometry?,
    terminalLayout: TerminalLayoutGeometry?,
    onInput: (GameplayInput) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
) {
    when (engine.phase) {
        GamePhase.RUNNING -> forEachRunningControlBounds(
            width = engine.screenWidth,
            height = engine.screenHeight,
            scale = engine.uiScale,
        ) { target, left, top, right, bottom ->
            val bounds = Rect(left, top, right, bottom)
            when (target) {
                RunningControlTarget.BRAKE -> BrakeSemanticControl(
                    bounds = bounds,
                    density = density,
                    active = engine.braking,
                    onPressedChange = onBrakeChanged,
                )
                RunningControlTarget.DASH -> GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.dash",
                    description = "Dash",
                    state = when {
                        engine.overheated -> "offline"
                        engine.dashReady -> "ready"
                        else -> "cooling"
                    },
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.DashRequested))
                    },
                )
                RunningControlTarget.PERFORMANCE -> PerformanceSemanticAction(
                    bounds = bounds,
                    density = density,
                    enabled = performanceEnabled,
                    onClick = { onInput(GameplayInput.TogglePerformance) },
                )
                RunningControlTarget.PAUSE -> GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.pause",
                    description = "Pause game",
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
                    },
                )
            }
        }
        GamePhase.PAUSED -> requireNotNull(pauseLayout).actions.forEach { action ->
            when (action.target) {
                PauseTarget.RESUME -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.resume",
                    description = "Resume game",
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
                    },
                )
                PauseTarget.SETTINGS -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.settings",
                    description = "Open gameplay settings",
                    onClick = { onInput(GameplayInput.OpenSettings) },
                )
                PauseTarget.PERFORMANCE -> PerformanceSemanticAction(
                    bounds = action.bounds,
                    density = density,
                    enabled = performanceEnabled,
                    onClick = { onInput(GameplayInput.TogglePerformance) },
                )
                PauseTarget.EXIT -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.exit",
                    description = "Exit to home",
                    onClick = { onInput(GameplayInput.ExitToHome) },
                )
            }
        }
        GamePhase.CHOICE -> {
            val layout = requireNotNull(choiceLayout)
            engine.choices.forEachIndexed { index, choice ->
                GameplaySemanticAction(
                    bounds = layout.cards[index],
                    density = density,
                    tag = "kinetickk.gameplay.choice.${index + 1}",
                    description = "Select choice ${index + 1}: ${choice.title}",
                    onClick = {
                        onInput(
                            GameplayInput.Action(
                                GameplayInteractionPulse.ChoiceSelected.fromValidated(index),
                            ),
                        )
                    },
                )
            }
            layout.reroll?.let { bounds ->
                GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.reroll",
                    description = "Reroll choices. ${engine.rerollsRemaining} remaining",
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.ChoicesRerolled))
                    },
                )
            }
        }
        GamePhase.GAME_OVER, GamePhase.VICTORY -> {
            val layout = requireNotNull(terminalLayout)
            GameplaySemanticAction(
                bounds = layout.restart,
                density = density,
                tag = "kinetickk.gameplay.restart",
                description = "Restart run",
                onClick = { onInput(GameplayInput.RestartRun) },
            )
            layout.rebirth?.let { bounds ->
                GameplaySemanticAction(
                    bounds = bounds,
                    density = density,
                    tag = "kinetickk.gameplay.rebirth",
                    description = "Start next rebirth cycle",
                    onClick = { onInput(GameplayInput.OpenRebirth) },
                )
            }
            GameplaySemanticAction(
                bounds = layout.exit,
                density = density,
                tag = "kinetickk.gameplay.exit",
                description = "Exit to home",
                onClick = { onInput(GameplayInput.ExitToHome) },
            )
        }
    }
}

@Composable
@NonRestartableComposable
private fun PerformanceSemanticAction(
    bounds: Rect,
    density: Density,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GameplaySemanticAction(
        bounds = bounds,
        density = density,
        tag = "kinetickk.gameplay.performance",
        description = "Performance metrics",
        state = if (enabled) "on" else "off",
        onClick = onClick,
    )
}

@Composable
private fun GameplaySemanticAction(
    bounds: Rect,
    density: Density,
    tag: String,
    description: String,
    state: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag(tag)
            .onKeyEvent { event ->
                activateSemanticButtonFromKey(event.key, event.type, onClick)
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                state?.let { stateDescription = it }
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .focusable(),
    )
}

@Composable
private fun BrakeSemanticControl(
    bounds: Rect,
    density: Density,
    active: Boolean,
    onPressedChange: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag("kinetickk.gameplay.brake")
            .onKeyEvent { event ->
                activateSemanticButtonFromKey(event.key, event.type) {
                    onPressedChange(gameplayBrakeSemanticToggleState(active))
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = GAMEPLAY_BRAKE_DESCRIPTION
                stateDescription = gameplayBrakeStateDescription(active)
                onClick(label = GAMEPLAY_BRAKE_SEMANTIC_ACTION_LABEL) {
                    onPressedChange(gameplayBrakeSemanticToggleState(active))
                    true
                }
            }
            .focusable(),
    )
}

private fun Modifier.placeInGameplayBounds(bounds: Rect, density: Density): Modifier =
    this
        .offset {
            IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
        }
        .requiredSize(
            width = with(density) { bounds.width.toDp() },
            height = with(density) { bounds.height.toDp() },
        )

private const val GAMEPLAY_ROOT_TAG = "kinetickk.gameplay"
internal const val GAMEPLAY_BRAKE_DESCRIPTION = "Brake"
internal const val GAMEPLAY_BRAKE_SEMANTIC_ACTION_LABEL = "Toggle brake"

internal fun gameplayBrakeStateDescription(active: Boolean): String =
    if (active) "pressed" else "released"

/** Accessibility activation is a latch: only another explicit action releases it. */
internal fun gameplayBrakeSemanticToggleState(active: Boolean): Boolean = !active

private class GameplayLayoutDimensions(
    val width: Float,
    val height: Float,
    val scale: Float,
)

private inline fun activateSemanticButtonFromKey(
    key: Key,
    type: KeyEventType,
    onClick: () -> Unit,
): Boolean {
    if (key != Key.Enter && key != Key.NumPadEnter && key != Key.Spacebar) return false
    return when (type) {
        KeyEventType.KeyDown -> true
        KeyEventType.KeyUp -> {
            onClick()
            true
        }
        else -> false
    }
}

private inline fun keyDown(type: KeyEventType, action: () -> Unit): Boolean {
    if (type == KeyEventType.KeyDown) action()
    return true
}

private fun reportInvalidInteractionInput(failure: ValidationFailure) {
    println("KINETICKK interaction input dropped: ${failure.code}")
}
