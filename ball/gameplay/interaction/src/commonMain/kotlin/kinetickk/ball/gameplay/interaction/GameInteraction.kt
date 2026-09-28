// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawKkSlab
import kinetickk.foundation.design.kkStroke

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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
import kinetickk.ball.gameplay.interaction.canvas.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.canvas.HudTrialPanelLayout
import kinetickk.ball.gameplay.interaction.canvas.activeTrial
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.canvas.drawPerformanceHud
import kinetickk.ball.gameplay.interaction.canvas.shouldDrawRunningPresentation
import kinetickk.ball.gameplay.interaction.canvas.toPerformanceHudProjection
import kinetickk.ball.gameplay.interaction.canvas.trialRules
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.isHudControlPosition
import kinetickk.ball.gameplay.interaction.input.isTrialInfoPosition
import kinetickk.ball.gameplay.interaction.input.resolveGameplayPress
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
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
    val hudMemory = remember(component) { HudPresentationMemory() }
    // The trial panel's (!) opens by keyboard focus, a tap/click (toggle) or a hovering mouse.
    var trialInfoFocusedValue by remember(component) { mutableStateOf(false) }
    var trialInfoOpenValue by remember(component) { mutableStateOf(false) }
    var trialInfoHoveredValue by remember(component) { mutableStateOf(false) }

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
            GameplayInput.OpenCodex -> onOutput(GameplayInteractionOutput.OpenCodex)
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
    val typography = kinetickk.foundation.design.rememberInterfaceTypography()
    val roles = LocalKkRolePalette.current
    val textMeasurer = remember(composeTextMeasurer, textScale, language, typography, roles) {
        CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            typography = typography,
            scale = textScale,
            language = language,
            roles = roles,
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
    val trialActive = renderModelValue.phase == GamePhase.RUNNING && renderModelValue.activeTrial() != null
    // Read in the composition that ends the trial, before its (!) node leaves: removing a focused
    // node clears focus from the whole hierarchy, so keyboard focus goes back to the gameplay root.
    val trialInfoHadFocus = trialInfoFocusedValue
    LaunchedEffect(component, trialActive) {
        if (!trialActive) {
            trialInfoOpenValue = false
            trialInfoHoveredValue = false
            if (trialInfoHadFocus && inputEnabled) focusRequester.requestFocus()
        }
    }
    val trialInfoShown = trialActive && (trialInfoFocusedValue || trialInfoOpenValue || trialInfoHoveredValue)
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
            .background(Kk.Ink)
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
                            val onTrialInfo = validatedMove != null &&
                                currentRenderModel.isTrialInfoPosition(validatedMove.x, validatedMove.y)
                            if (!pressed) {
                                // Only a mouse hovers; a lifted finger must not keep the rules open.
                                trialInfoHoveredValue = onTrialInfo && event.type != PointerEventType.Exit &&
                                    event.changes.firstOrNull()?.type == PointerType.Mouse
                            }
                            if (pressed && !wasPressedValue && validatedMove != null) {
                                hudGestureActiveValue = isHudControlPosition
                                // A press on the (!) toggles the rules; a press anywhere else closes them.
                                trialInfoOpenValue = onTrialInfo && !trialInfoOpenValue
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
                hudMemory = hudMemory,
                trialInfoOpen = trialInfoShown,
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
                onBuild = { onOutput(GameplayInteractionOutput.OpenCodex) },
            )
        }
        // Pause and reward overlays present Codex as their own menu item and button.
        if (inputEnabled && renderModelValue.phase == GamePhase.RUNNING) {
            val buildBounds = remember(layoutDimensions) {
                runningBuildButtonBounds(layoutDimensions.width, layoutDimensions.height, layoutDimensions.scale)
            }
            BuildButton(
                modifier = Modifier.placeInGameplayBounds(buildBounds, localDensity),
                regularUnit = if (gameplayLayoutMode(layoutDimensions.width, layoutDimensions.height, layoutDimensions.scale) ==
                    GameplayLayoutMode.REGULAR
                ) {
                    regularHudUnit(layoutDimensions.width, layoutDimensions.scale)
                } else {
                    0f
                },
                onClick = { onOutput(GameplayInteractionOutput.OpenCodex) },
            )
        }
        if (inputEnabled) {
            GameplaySemanticControls(
                engine = renderModelValue,
                density = localDensity,
                performanceEnabled = performanceEnabledValue,
                pauseLayout = pauseLayout,
                onTrialInfoFocusChanged = { trialInfoFocusedValue = it },
                onTrialInfoToggle = { trialInfoOpenValue = !trialInfoOpenValue },
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
    onTrialInfoFocusChanged: (Boolean) -> Unit,
    onTrialInfoToggle: () -> Unit,
    onInput: (GameplayInput) -> Unit,
    onBrakeChanged: (Boolean) -> Unit,
) {
    val language = LocalAppLanguage.current
    if (engine.phase == GamePhase.RUNNING) {
        val trial = engine.activeTrial()
        if (trial != null) {
            TrialInfoSemanticNode(
                engine = engine,
                density = density,
                description = engine.trialRules(trial, language),
                onFocusChanged = onTrialInfoFocusChanged,
                onToggle = onTrialInfoToggle,
            )
        }
    }
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
                PauseTarget.CODEX -> GameplaySemanticAction(
                    bounds = action.bounds,
                    density = density,
                    tag = "kinetickk.gameplay.codex",
                    description = language.text(OverlayRedesignText.Codex),
                    onClick = { onInput(GameplayInput.OpenCodex) },
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

/**
 * The trial panel's (!) is drawn on the Canvas, where the pointer steers the singularity; its rules
 * are exposed as a focusable node whose description is the rule text. Focus shows the tooltip.
 */
@Composable
private fun TrialInfoSemanticNode(
    engine: GameplayRenderModel,
    density: Density,
    description: String,
    onFocusChanged: (Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    val bounds = remember(engine.screenWidth, engine.screenHeight, engine.uiScale, engine.settings.textScale) {
        HudTrialPanelLayout()
            .update(engine.screenWidth, engine.screenHeight, engine.uiScale, engine.settings.textScale)
            .infoTarget(engine.uiScale)
    }
    val currentOnFocusChanged = rememberUpdatedState(onFocusChanged)
    DisposableEffect(Unit) {
        onDispose { currentOnFocusChanged.value(false) }
    }
    Box(
        Modifier
            .placeInGameplayBounds(bounds, density)
            .testTag(GAMEPLAY_TRIAL_INFO_TAG)
            .onFocusChanged { currentOnFocusChanged.value(it.isFocused) }
            .onKeyEvent { event -> activateSemanticButtonFromKey(event.key, event.type, onToggle) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onToggle()
                    true
                }
            }
            .focusable(),
    )
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
internal const val GAMEPLAY_TRIAL_INFO_TAG = "kinetickk.gameplay.trial-info"
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

/** Ghost icon button (`.ibtn`) opening the build overview (Codex) during a run; no key letter. */
@Composable
private fun BuildButton(modifier: Modifier, regularUnit: Float, onClick: () -> Unit) {
    val label = LocalAppLanguage.current.text(GameplayText.Build)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        modifier
            .testTag("kinetickk.gameplay.build")
            .semantics { contentDescription = label }
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = if (regularUnit > 0f) regularUnit else density
            val width = (if (regularUnit > 0f) 40f else 44f) * unit
            val height = (if (regularUnit > 0f) 36f else 40f) * unit
            val left = center.x - width * 0.5f
            val top = center.y - height * 0.5f
            val active = focused || hovered
            drawBuildButton(Rect(left, top, left + width, top + height), active, focused, unit)
        }
    }
}

/** Ink-3 slab (bone when hovered/focused) with a stacked-plates mark; focus adds the bone outline. */
private fun DrawScope.drawBuildButton(bounds: Rect, active: Boolean, focused: Boolean, unit: Float) {
    val cut = 8f * density
    drawKkSlab(bounds, if (active) Kk.Bone else Kk.Ink3, cut)
    val ink = if (active) Kk.Ink else Kk.Bone
    val plateWidth = 16f * unit
    val plateHeight = 3f * unit
    for (index in 0 until 3) {
        val y = bounds.center.y - 7f * unit + index * 6f * unit
        val x = bounds.center.x - plateWidth * 0.5f + (1 - index) * 2f * unit
        drawRect(ink, Offset(x, y), androidx.compose.ui.geometry.Size(plateWidth, plateHeight))
    }
    if (focused) {
        val gap = 4f * density
        drawRect(Kk.Bone, Offset(bounds.left - gap, bounds.top - gap),
            androidx.compose.ui.geometry.Size(bounds.width + gap * 2f, bounds.height + gap * 2f),
            style = kkStroke(2f * density))
    }
}
