// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.LocalKkFrameRateCap
import kinetickk.foundation.design.LocalKkInterfaceScale
import kinetickk.foundation.design.Kk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.unit.Density
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.foundation.design.KkApexState
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kinetickk.ball.gameplay.interaction.canvas.hud.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.layout.activeTrial
import kinetickk.ball.gameplay.interaction.canvas.look.badgeState
import kinetickk.ball.gameplay.interaction.canvas.hud.drawCompactFpsReadout
import kinetickk.ball.gameplay.interaction.canvas.hud.trialRules
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.GameplayKeyInput
import kinetickk.ball.gameplay.interaction.input.GameplayPointerHost
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.canvas.hud.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.filledSkillSlots
import kinetickk.ball.gameplay.interaction.input.gameplayKeyInput
import kinetickk.ball.gameplay.interaction.input.routeGameplayPointers
import kinetickk.ball.gameplay.interaction.input.routeSecondaryBrake
import kinetickk.ball.gameplay.interaction.layout.hudHeight
import kinetickk.ball.gameplay.interaction.layout.hudScale
import kinetickk.ball.gameplay.interaction.layout.hudWidth
import kinetickk.ball.gameplay.interaction.input.runningControlInput
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.compactFpsReadoutBounds
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.skillSlot
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
import kinetickk.ball.gameplay.interaction.rewards.RewardContent
import kinetickk.ball.gameplay.interaction.rewards.RewardUiPreparation
import kinetickk.ball.gameplay.interaction.canvas.hud.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.overlay.TerminalContent
import kinetickk.ball.gameplay.interaction.overlay.terminalActionsReady
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.time.TimeSource

@Composable
fun GameplayContent(
    component: GameplayInteractionPort,
    inputEnabled: Boolean,
    onOutput: (GameplayInteractionOutput) -> Unit,
) {
    val language = LocalAppLanguage.current
    val diagnostics = LocalCrashDiagnostics.current
    val performanceDiagnostics = if (DIAGNOSTICS_ENABLED) diagnostics as? PerformanceDiagnostics else null
    fun reportInvalidInteractionInput(failure: ValidationFailure) {
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.event("warning.input", "Dropped input: ${failure.code}")
    }
    val focusRequester = remember(component) { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val localDensity = LocalDensity.current
    val density = localDensity.density
    // The world keeps the raw density; the HUD, the running controls and the run's overlays lay out at the
    // applied interface scale (meta-ui §3.6), in drawing and hit tests alike.
    val interfaceScale = LocalKkInterfaceScale.current
    val interfaceScaleState = rememberUpdatedState(interfaceScale)
    val hudDensity = remember(localDensity, interfaceScale) {
        if (interfaceScale == 1f) localDensity else Density(localDensity.density * interfaceScale, localDensity.fontScale)
    }
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val hudComposeTextMeasurer = remember(composeTextMeasurer, hudDensity, fontFamilyResolver, layoutDirection) {
        if (hudDensity === localDensity) composeTextMeasurer else ComposeTextMeasurer(fontFamilyResolver, hudDensity, layoutDirection, 64)
    }
    val interactionValidator = remember(component) { GameInteractionValidator() }
    // The owner republishes the same snapshot instances while nothing changed, so a reference check
    // stands in for a structural comparison of the whole model on every frame.
    val renderModel = remember(component) {
        mutableStateOf(requireNotNull(component.renderSnapshot().renderModel), referentialEqualityPolicy())
    }
    var renderModelValue by renderModel
    var visualFxProjectionValue by remember(component) {
        mutableStateOf(component.visualFxSnapshot(), referentialEqualityPolicy())
    }
    val renderTimeSeconds = remember(component) { mutableFloatStateOf(0f) }
    var renderTimeSecondsValue by renderTimeSeconds
    val hudMemory = remember(component) { HudPresentationMemory() }
    val controlLog = remember(component) { RunningControlPressLog() }
    val fpsCounter = remember(component) { GameplayFpsCounter() }
    val fpsReadout = remember(component) { mutableIntStateOf(GameplayFpsCounter.NO_VALUE) }
    // Names in the skill and Apex semantics; a component plays one content snapshot.
    val content = remember(component) { requireNotNull(component.renderSnapshot().renderModel).content }
    var rewardUiReady by remember(component) { mutableStateOf(false) }
    val gameplayInputEnabled = inputEnabled && rewardUiReady
    // The trial panel's (!) opens by keyboard focus, a tap/click (toggle) or a hovering mouse.
    var trialInfoFocusedValue by remember(component) { mutableStateOf(false) }
    var trialInfoOpenValue by remember(component) { mutableStateOf(false) }
    var trialInfoHoveredValue by remember(component) { mutableStateOf(false) }

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
                renderModelValue = requireNotNull(committed.renderModel)
                visualFxProjectionValue = component.visualFxSnapshot()
                if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.context("gameplay.committed") { committed.crashContext() }
            } catch (snapshotFailure: Throwable) {
                if (snapshotFailure !== failure) failure.addSuppressed(snapshotFailure)
            }
            if (DIAGNOSTICS_ENABLED && diagnostics.enabled) diagnostics.event("gameplay.failure", failure.toString())
            throw failure
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> {
                renderModelValue = requireNotNull(component.renderSnapshot().renderModel)
                visualFxProjectionValue = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
        if (DIAGNOSTICS_ENABLED && diagnostics.enabled && pulse !is GameplayInteractionPulse.FrameElapsed) {
            val committed = component.renderSnapshot()
            diagnostics.context("gameplay.committed") { committed.crashContext() }
        }
        if (DIAGNOSTICS_ENABLED && dispatchStartedAt != null) {
            performanceDiagnostics?.phase(DiagnosticPhase.Dispatch, dispatchStartedAt.elapsedNow().inWholeNanoseconds)
            if (acceptance is GameplayAcceptance.Accepted) {
                performanceDiagnostics?.workload(
                    enemies = renderModelValue.enemies.size,
                    projectiles = renderModelValue.projectiles.size,
                    pickups = renderModelValue.pickups.size,
                    effects = with(visualFxProjectionValue) {
                        particles.size + motionEchoes.size + shockwaves.size + damageNumbers.size +
                            weaponArcs.size + elementBursts.size + reactionStamps.size + skillBursts.size + apexBursts.size
                    },
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
        performanceDiagnostics?.toggleOverlay()
    }

    fun dispatchInput(input: GameplayInput) {
        when (input) {
            is GameplayInput.Action -> dispatch(input.action)
            GameplayInput.OpenSettings -> onOutput(GameplayInteractionOutput.OpenSettings)
            GameplayInput.OpenWorkshop -> onOutput(GameplayInteractionOutput.OpenWorkshop)
            GameplayInput.OpenRebirth -> onOutput(GameplayInteractionOutput.OpenRebirth)
            GameplayInput.OpenCodex -> onOutput(GameplayInteractionOutput.OpenCodex)
            GameplayInput.ExitToHome -> onOutput(GameplayInteractionOutput.ExitToHome)
            GameplayInput.RestartRun -> onOutput(GameplayInteractionOutput.RestartRun)
            GameplayInput.TogglePerformance -> togglePerformanceTelemetry()
        }
    }

    /**
     * A running control was pressed (touch, mouse, key or accessibility): the HUD's press flash, the
     * Apex refusal shake when it is cooling, then the control's input. An empty skill slot is a HUD
     * position that does nothing.
     */
    fun pressRunningControl(target: RunningControlTarget) {
        val model = renderModelValue
        val skillSlots = model.filledSkillSlots()
        val slot = target.skillSlot
        if (slot >= 0 && skillSlots and (1 shl slot) == 0) return
        controlLog.press(target, renderTimeSecondsValue)
        if (target == RunningControlTarget.APEX && model.apex.badgeState() == KkApexState.COOLING) {
            controlLog.refuseApex(renderTimeSecondsValue)
        }
        if (slot >= 0) {
            dispatchValidated(interactionValidator.skillRequested(slot))
            return
        }
        runningControlInput(target, skillSlots)?.let(::dispatchInput)
    }

    /** Applies a key event's [GameplayKeyInput] in its documented order and says whether it is consumed. */
    fun applyKeyInput(key: GameplayKeyInput): Boolean {
        if (key.keyboardPlay) {
            controlLog.keyHints = true
            controlLog.touchInput = false
        }
        if (key.gesture) dispatch(GameplayInteractionPulse.UserGestureObserved)
        key.control?.let(::pressRunningControl)
        if (key.choice != GameplayKeyInput.NO_CHOICE) dispatchValidated(interactionValidator.choiceSelected(key.choice))
        key.input?.let(::dispatchInput)
        return key.consumed
    }

    // Read at every pointer event: the latest model, interface scale and trial rules state.
    val pointerHost = object : GameplayPointerHost {
        override val renderModel: GameplayRenderModel get() = renderModelValue
        override val interfaceScale: Float get() = interfaceScaleState.value
        override var trialInfoOpen: Boolean
            get() = trialInfoOpenValue
            set(value) { trialInfoOpenValue = value }
        override var trialInfoHovered: Boolean
            get() = trialInfoHoveredValue
            set(value) { trialInfoHoveredValue = value }
        override fun onPulse(pulse: GameplayInteractionPulse) = dispatch(pulse)
        override fun onInput(input: GameplayInput) = dispatchInput(input)
        override fun onControl(target: RunningControlTarget) = pressRunningControl(target)
        override fun onTouch() {
            controlLog.keyHints = false
            controlLog.touchInput = true
        }
        override fun onInvalid(failure: ValidationFailure) = reportInvalidInteractionInput(failure)
    }

    // The composition reads only these facts; the Canvas draws the full model every frame, so a
    // running frame invalidates the draw without recomposing the gameplay host.
    val compositionFacts by remember(component) {
        derivedStateOf(structuralEqualityPolicy()) { renderModelValue.compositionFacts() }
    }
    val trialDescription by remember(component, language) {
        derivedStateOf(structuralEqualityPolicy()) {
            val model = renderModelValue
            model.activeTrial()?.takeIf { model.phase == GamePhase.RUNNING }?.let { model.trialRules(it, language) }
        }
    }
    val facts = compositionFacts
    val phase = facts.phase

    SideEffect(component, inputEnabled, phase) {
        if (inputEnabled) focusRequester.requestFocus()
    }

    // The frame limit paces the loop; the fixed-step simulation plays the same at any frame rate.
    val frameRateCap = LocalKkFrameRateCap.current
    LaunchedEffect(
        component,
        interactionValidator,
        performanceDiagnostics,
        frameRateCap,
        rewardUiReady,
    ) {
        if (!rewardUiReady) return@LaunchedEffect
        runGameplayFrameLoop(
            component = component,
            validator = interactionValidator,
            frameRateCap = frameRateCap,
            diagnostics = diagnostics,
            performanceDiagnostics = performanceDiagnostics,
            renderModel = renderModel,
            renderTimeSeconds = renderTimeSeconds,
            fpsCounter = fpsCounter,
            fpsReadout = fpsReadout,
            controlLog = controlLog,
            dispatch = ::dispatch,
            reportInvalid = ::reportInvalidInteractionInput,
        )
    }

    // Pause when unfocused: the window losing focus pauses a running run (an edge, never a level).
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val windowFocus = remember(component) { GameplayWindowFocus() }
    LaunchedEffect(component, windowFocused) {
        val model = renderModelValue
        val pause = pausesOnFocusLoss(windowFocus.focused, windowFocused, model.phase, model.settings.pauseOnFocusLoss, inputEnabled)
        windowFocus.focused = windowFocused
        if (pause) dispatch(GameplayInteractionPulse.PauseToggled)
    }

    // The Nucleus receives the viewport in dp, so a density change at an unchanged pixel size (a
    // display scale change) resends it; onSizeChanged covers every pixel size change.
    val viewport = remember(component) { GameplayViewportPixels() }
    LaunchedEffect(component, density) {
        if (viewport.width > 0 && viewport.height > 0 && viewport.density != density) {
            viewport.density = density
            dispatchValidated(
                interactionValidator.viewportChanged(
                    rawWidthPx = viewport.width.toFloat(),
                    rawHeightPx = viewport.height.toFloat(),
                    rawDensity = density,
                ),
            )
        }
    }

    val textScale = facts.textScale
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
    val hudTextMeasurer = remember(textMeasurer, hudComposeTextMeasurer) {
        if (hudComposeTextMeasurer === composeTextMeasurer) {
            textMeasurer
        } else {
            CanvasTextMeasurer(hudComposeTextMeasurer, textMeasurer.scale, textMeasurer.language, textMeasurer.typography, textMeasurer.roles)
        }
    }
    val hudScale = hudScale(facts.uiScale, facts.viewportDensity, interfaceScale)
    val layoutDimensions = remember(facts.hudWidth, facts.hudHeight, hudScale) {
        GameplayLayoutDimensions(width = facts.hudWidth, height = facts.hudHeight, scale = hudScale)
    }
    val pauseLayout = if (phase == GamePhase.PAUSED) {
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
    val choiceLayout = if (phase == GamePhase.CHOICE) {
        remember(
            layoutDimensions,
            facts.choiceCount,
            facts.choicesCanReroll,
        ) {
            choiceLayoutGeometry(
                layoutDimensions.width,
                layoutDimensions.height,
                layoutDimensions.scale,
                facts.choiceCount,
                facts.choicesCanReroll,
            )
        }
    } else {
        null
    }
    val trialActive = trialDescription != null
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
    val terminal = phase == GamePhase.GAME_OVER || phase == GamePhase.VICTORY
    val terminalStartedAt = remember(component, phase) { renderTimeSecondsValue }
    val terminalElapsed = if (terminal) (renderTimeSecondsValue - terminalStartedAt).coerceAtLeast(0f) else 0f
    val terminalReady = terminalActionsReady(terminalElapsed, phase == GamePhase.VICTORY)

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
                viewport.width = size.width
                viewport.height = size.height
                viewport.density = density
                dispatchValidated(
                    interactionValidator.viewportChanged(
                        rawWidthPx = size.width.toFloat(),
                        rawHeightPx = size.height.toFloat(),
                        rawDensity = density,
                    ),
                )
            }
            .onKeyEvent { event ->
                applyKeyInput(
                    gameplayKeyInput(event.key, event.type, renderModelValue, gameplayInputEnabled, terminalLocked = terminal && !terminalReady),
                )
            }
            .pointerInput(component, gameplayInputEnabled) {
                if (!gameplayInputEnabled) return@pointerInput
                awaitPointerEventScope { routeSecondaryBrake(pointerHost) }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    component,
                    gameplayInputEnabled,
                    interactionValidator,
                    performanceDiagnostics,
                ) {
                    if (!gameplayInputEnabled) return@pointerInput
                    awaitPointerEventScope { routeGameplayPointers(pointerHost, interactionValidator) }
                },
        ) {
            val drawStartedAt = if (DIAGNOSTICS_ENABLED && performanceDiagnostics?.capturesPerformance == true) TimeSource.Monotonic.markNow() else null
            drawGameplay(
                engine = renderModelValue,
                visualFx = visualFxProjectionValue,
                textMeasurer = textMeasurer,
                renderTime = renderTimeSecondsValue,
                pauseLayout = pauseLayout,
                terminalElapsed = terminalElapsed,
                hudMemory = hudMemory,
                trialInfoOpen = trialInfoShown,
                controls = controlLog,
                hudDensity = hudDensity,
                hudTextMeasurer = hudTextMeasurer,
            )
            if (DIAGNOSTICS_ENABLED && drawStartedAt != null) {
                performanceDiagnostics?.phase(DiagnosticPhase.CanvasDraw, drawStartedAt.elapsedNow().inWholeNanoseconds)
            }
        }
        // The run's overlays and the controls' semantics lay out at the HUD's (interface-scaled) density.
        CompositionLocalProvider(LocalDensity provides hudDensity) {
            if (!rewardUiReady) key(component) {
                RewardUiPreparation(renderModelValue) { rewardUiReady = true }
            }
            if (terminal) {
                TerminalContent(
                    renderModelValue,
                    terminalElapsed,
                    gameplayInputEnabled,
                    reactionsThisRun = visualFxProjectionValue.runReactionMask,
                    discoveredThisRun = visualFxProjectionValue.runDiscoveryMask,
                ) { input ->
                    dispatch(GameplayInteractionPulse.UserGestureObserved)
                    dispatchInput(input)
                }
            }
            if (phase == GamePhase.CHOICE && rewardUiReady) {
                RewardContent(
                    engine = renderModelValue,
                    layout = requireNotNull(choiceLayout),
                    renderTime = renderTimeSeconds,
                    enabled = gameplayInputEnabled,
                    onSelect = { index ->
                        dispatch(GameplayInteractionPulse.UserGestureObserved)
                        dispatchValidated(interactionValidator.choiceSelected(index))
                    },
                    onReroll = {
                        dispatch(GameplayInteractionPulse.UserGestureObserved)
                        dispatch(GameplayInteractionPulse.ChoicesRerolled)
                    },
                    onBuild = { onOutput(GameplayInteractionOutput.OpenCodex) },
                    touch = controlLog.touchInput,
                )
            }
            // Pause and reward overlays present Codex as their own menu item and button.
            if (gameplayInputEnabled && phase == GamePhase.RUNNING) {
                val buildBounds = remember(layoutDimensions) {
                    runningBuildButtonBounds(layoutDimensions.width, layoutDimensions.height, layoutDimensions.scale)
                }
                BuildButton(
                    modifier = Modifier.placeInGameplayBounds(buildBounds, hudDensity),
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
            if (gameplayInputEnabled) {
                GameplaySemanticControls(
                    facts = facts,
                    content = content,
                    trialDescription = trialDescription,
                    density = hudDensity,
                    hudScale = hudScale,
                    performanceEnabled = DIAGNOSTICS_ENABLED && performanceDiagnostics?.overlayVisible == true,
                    fps = fpsReadout,
                    pauseLayout = pauseLayout,
                    onTrialInfoFocusChanged = { trialInfoFocusedValue = it },
                    onTrialInfoToggle = { trialInfoOpenValue = !trialInfoOpenValue },
                    onInput = { input ->
                        dispatch(GameplayInteractionPulse.UserGestureObserved)
                        dispatchInput(input)
                    },
                    onControl = { target ->
                        dispatch(GameplayInteractionPulse.UserGestureObserved)
                        pressRunningControl(target)
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
            val counterAboveOverlay = phase != GamePhase.RUNNING
            if (facts.controlMask and RunningControlMask.FPS != 0 &&
                (counterAboveOverlay || gameplayLayoutMode(layoutDimensions.width, layoutDimensions.height, hudScale) == GameplayLayoutMode.COMPACT_PORTRAIT)
            ) {
                val bounds = remember(layoutDimensions, hudScale, counterAboveOverlay) {
                    compactFpsReadoutBounds(layoutDimensions.width, hudScale, counterAboveOverlay)
                }
                val modifier = Modifier.placeInGameplayBounds(bounds, hudDensity).fpsReadoutSemantics(language, fpsReadout)
                if (counterAboveOverlay) {
                    // Draw after the reward/pause content so its scrim cannot hide the numeric readout.
                    Canvas(modifier) { drawCompactFpsReadout(hudTextMeasurer, 0f, 0f, size.width, size.height, fpsReadout.intValue) }
                } else {
                    Box(modifier) // The portrait HUD already draws the same strip under its clock.
                }
            }
        }
    }
}

private const val GAMEPLAY_ROOT_TAG = "kinetickk.gameplay"
