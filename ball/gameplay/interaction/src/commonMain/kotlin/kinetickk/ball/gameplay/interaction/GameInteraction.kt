// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.InterfaceGlyph
import kinetickk.foundation.design.drawInterfaceGlyph
import kinetickk.foundation.design.White
import kinetickk.foundation.design.Muted

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
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
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.CrashDiagnostics
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
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.rewards.RewardContent
import kinetickk.ball.gameplay.interaction.terminal.TerminalContent
import kinetickk.ball.gameplay.interaction.terminal.terminalActionsReady
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
    val language = LocalAppLanguage.current
    val diagnostics = LocalCrashDiagnostics.current
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
        if (diagnostics !== CrashDiagnostics.None) {
            val before = component.renderSnapshot()
            diagnostics.context("gameplay.before-input") { before.crashContext() }
            diagnostics.context("gameplay.input") { pulse.crashDescription() }
            diagnostics.event("gameplay.input", pulse.crashDescription(),
                highFrequency = pulse is GameplayInteractionPulse.FrameElapsed ||
                    pulse is GameplayInteractionPulse.PointerMoved)
        }
        val acceptance = try {
            component.accept(pulse)
        } catch (failure: Throwable) {
            // A target can publish atomically and then surface a deferred side-effect fault.
            // Republishing the immutable values is also harmless for a pre-commit fault because
            // Compose suppresses equal assignments, so the hot success path needs no pre-query.
            try {
                val committed = component.renderSnapshot()
                renderModelValue = requireNotNull(committed.renderModel)
                visualFxProjectionValue = component.visualFxSnapshot()
                diagnostics.context("gameplay.committed") { committed.crashContext() }
            } catch (snapshotFailure: Throwable) {
                if (snapshotFailure !== failure) failure.addSuppressed(snapshotFailure)
            }
            diagnostics.event("gameplay.failure", failure.toString())
            throw failure
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> {
                renderModelValue = requireNotNull(component.renderSnapshot().renderModel)
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
        if (diagnostics !== CrashDiagnostics.None) {
            val committed = component.renderSnapshot()
            diagnostics.context("gameplay.committed") { committed.crashContext() }
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

    SideEffect(component, inputEnabled, renderModelValue.phase) {
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
    val textMeasurer = remember(composeTextMeasurer, textScale, language) {
        CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = textScale,
            language = language,
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
    val terminal = renderModelValue.phase == GamePhase.GAME_OVER || renderModelValue.phase == GamePhase.VICTORY
    val terminalStartedAt = remember(component, renderModelValue.phase) { renderTimeSecondsValue }
    val terminalElapsed = if (terminal) (renderTimeSecondsValue - terminalStartedAt).coerceAtLeast(0f) else 0f
    val terminalReady = terminalActionsReady(terminalElapsed, renderModelValue.phase == GamePhase.VICTORY)
    val performanceHudProjection = if (performanceEnabledValue) {
        remember(performanceSnapshotValue, language) {
            performanceSnapshotValue.toPerformanceHudProjection(language)
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
                contentDescription = language.text(GameplayText.Gameplay)
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
                if (terminal && !terminalReady) return@onKeyEvent true
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
                            if (currentRenderModel.phase == GamePhase.CHOICE ||
                                currentRenderModel.phase == GamePhase.GAME_OVER ||
                                currentRenderModel.phase == GamePhase.VICTORY
                            ) {
                                wasPressedValue = false
                                hudGestureActiveValue = false
                                continue
                            }
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
                terminalElapsed = terminalElapsed,
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
        if (terminal) {
            TerminalContent(renderModelValue, terminalElapsed, inputEnabled) { input ->
                dispatch(GameplayInteractionPulse.UserGestureObserved)
                dispatchInput(input)
            }
        }
        if (renderModelValue.phase == GamePhase.CHOICE) {
            RewardContent(
                engine = renderModelValue,
                layout = requireNotNull(choiceLayout),
                renderTime = renderTimeSecondsValue,
                enabled = inputEnabled,
                onSelect = { index ->
                    dispatch(GameplayInteractionPulse.UserGestureObserved)
                    dispatchValidated(interactionValidator.choiceSelected(index))
                },
                onReroll = {
                    dispatch(GameplayInteractionPulse.UserGestureObserved)
                    dispatch(GameplayInteractionPulse.ChoicesRerolled)
                },
            )
        }
        if (inputEnabled && (renderModelValue.phase == GamePhase.RUNNING || renderModelValue.phase == GamePhase.PAUSED || renderModelValue.phase == GamePhase.CHOICE)) {
            BuildButton(
                modifier = Modifier.align(when {
                    renderModelValue.phase != GamePhase.RUNNING -> Alignment.TopStart
                    kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode(renderModelValue.screenWidth, renderModelValue.screenHeight, density) == kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode.REGULAR -> Alignment.BottomStart
                    else -> Alignment.BottomCenter
                }).padding(12.dp),
                textScale = renderModelValue.settings.textScale,
                onClick = { onOutput(GameplayInteractionOutput.OpenCodex) },
            )
        }
        if (inputEnabled) {
            GameplaySemanticControls(
                engine = renderModelValue,
                density = localDensity,
                performanceEnabled = performanceEnabledValue,
                pauseLayout = pauseLayout,
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
    onInput: (GameplayInput) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
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
                    description = language.text(GameplayText.DashDescription),
                    state = when {
                        engine.overheated -> language.text(GameplayText.OfflineState)
                        engine.dashReady -> language.text(GameplayText.ReadyState)
                        else -> language.text(GameplayText.CoolingState)
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
                    description = language.text(GameplayText.PauseDescription),
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
                    description = language.text(GameplayText.ResumeDescription),
                    onClick = {
                        onInput(GameplayInput.Action(GameplayInteractionPulse.PauseToggled))
                    },
                )
                PauseTarget.SETTINGS -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.settings",
                    description = language.text(GameplayText.SettingsDescription),
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
                    description = language.text(GameplayText.ExitDescription),
                    onClick = { onInput(GameplayInput.ExitToHome) },
                )
            }
        }
        GamePhase.CHOICE -> Unit // RewardContent owns the visible Compose actions.
        GamePhase.GAME_OVER, GamePhase.VICTORY -> Unit // TerminalContent owns native buttons.

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
    val language = LocalAppLanguage.current
    GameplaySemanticAction(
        bounds = bounds,
        density = density,
        tag = "kinetickk.gameplay.performance",
        description = language.text(GameplayText.PerformanceDescription),
        state = if (enabled) language.text(GameplayText.OnState) else language.text(GameplayText.OffState),
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
    val language = LocalAppLanguage.current
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
                contentDescription = language.text(GameplayText.BrakeDescription)
                stateDescription = gameplayBrakeStateDescription(active, language)
                onClick(label = language.text(GameplayText.BrakeAction)) {
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
internal val GAMEPLAY_BRAKE_DESCRIPTION = GameplayText.BrakeDescription.english
internal val GAMEPLAY_BRAKE_SEMANTIC_ACTION_LABEL = GameplayText.BrakeAction.english

internal fun gameplayBrakeStateDescription(active: Boolean, language: AppLanguage = AppLanguage.English): String =
    if (active) language.text(GameplayText.PressedState) else language.text(GameplayText.ReleasedState)

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

@Composable
private fun BuildButton(modifier: Modifier, textScale: Float, onClick: () -> Unit) {
    val label = LocalAppLanguage.current.text(GameplayText.Build)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    Box(modifier) {
        Box(Modifier.size(48.dp).background(if (focused || hovered) Color(0xFF292D34) else Color(0xE6101216))
            .testTag("kinetickk.gameplay.build")
            .semantics { contentDescription = label }
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)) {
            Canvas(Modifier.fillMaxSize().padding(13.dp)) {
                drawInterfaceGlyph(InterfaceGlyph.LAYERS, center.copy(y = center.y - 3.dp.toPx()), 9.dp.toPx(), White)
            }
            BasicText("I", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), TextStyle(color = Muted, fontSize = 8.sp))
        }
        if (focused || hovered) {
            BasicText(label, Modifier.offset(x = 54.dp).background(Color(0xFF191C22)).padding(10.dp),
                TextStyle(color = White, fontSize = (11f * textScale).sp))
        }
    }
}
