// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
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
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
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
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.DIAGNOSTICS_ENABLED
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kinetickk.ball.gameplay.interaction.layout.activeTrial
import kinetickk.ball.gameplay.interaction.canvas.hud.drawCompactFpsReadout
import kinetickk.ball.gameplay.interaction.canvas.hud.trialRules
import kinetickk.ball.gameplay.interaction.input.gameplayKeyInput
import kinetickk.ball.gameplay.interaction.input.routeGameplayPointers
import kinetickk.ball.gameplay.interaction.input.routeSecondaryBrake
import kinetickk.ball.gameplay.interaction.layout.hudHeight
import kinetickk.ball.gameplay.interaction.layout.hudScale
import kinetickk.ball.gameplay.interaction.layout.hudWidth
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.compactFpsReadoutBounds
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
import kinetickk.ball.gameplay.interaction.rewards.RewardContent
import kinetickk.ball.gameplay.interaction.rewards.RewardUiPreparation
import kinetickk.ball.gameplay.interaction.overlay.TerminalContent
import kinetickk.ball.gameplay.interaction.overlay.terminalActionsReady
import kinetickk.ball.gameplay.nucleus.render.GamePhase
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
    val hudComposeTextMeasurer = rememberHudComposeTextMeasurer(composeTextMeasurer, localDensity, hudDensity)
    val host = remember(component) { GameplayHostState(component) }
    val pulses = remember(component, host, diagnostics, performanceDiagnostics) {
        GameplayHostDispatch(component, host, diagnostics, performanceDiagnostics)
    }
    val inputs = remember(host, pulses, interfaceScaleState, onOutput) { GameplayHostInputs(host, pulses, interfaceScaleState, onOutput) }
    val rewardUiReady = host.rewardUiReady.value
    val gameplayInputEnabled = inputEnabled && rewardUiReady
    val trialDescription by remember(component, language) {
        derivedStateOf(structuralEqualityPolicy()) {
            val model = host.renderModel.value
            model.activeTrial()?.takeIf { model.phase == GamePhase.RUNNING }?.let { model.trialRules(it, language) }
        }
    }
    val facts = host.compositionFacts.value
    val phase = facts.phase
    GameplayHostEffects(component, host, pulses, inputEnabled, phase, density, diagnostics, performanceDiagnostics, rewardUiReady)
    val textMeasurer = rememberCanvasTextMeasurer(composeTextMeasurer, facts.textScale, language)
    val hudTextMeasurer = remember(textMeasurer, hudComposeTextMeasurer) {
        hudCanvasTextMeasurer(textMeasurer, hudComposeTextMeasurer, composeTextMeasurer)
    }
    val hudScale = hudScale(facts.uiScale, facts.viewportDensity, interfaceScale)
    val layoutDimensions = remember(facts.hudWidth, facts.hudHeight, hudScale) {
        GameplayLayoutDimensions(width = facts.hudWidth, height = facts.hudHeight, scale = hudScale)
    }
    val pauseLayout = rememberPauseLayout(phase, layoutDimensions)
    val choiceLayout = rememberChoiceLayout(phase, layoutDimensions, facts)
    val trialActive = trialDescription != null
    // Read in the composition that ends the trial, before its (!) node leaves: removing a focused
    // node clears focus from the whole hierarchy, so keyboard focus goes back to the gameplay root.
    val trialInfoHadFocus = host.trialInfoFocused.value
    LaunchedEffect(component, trialActive) {
        if (!trialActive) {
            host.trialInfoOpen.value = false
            host.trialInfoHovered.value = false
            if (trialInfoHadFocus && inputEnabled) host.focusRequester.requestFocus()
        }
    }
    val trialInfoShown = trialActive && (host.trialInfoFocused.value || host.trialInfoOpen.value || host.trialInfoHovered.value)
    val terminal = phase == GamePhase.GAME_OVER || phase == GamePhase.VICTORY
    val terminalStartedAt = remember(component, phase) { host.renderTimeSeconds.floatValue }
    val terminalElapsed = if (terminal) (host.renderTimeSeconds.floatValue - terminalStartedAt).coerceAtLeast(0f) else 0f
    val terminalReady = terminalActionsReady(terminalElapsed, phase == GamePhase.VICTORY)

    Box(
        modifier = gameplayRootModifier(
            component, host, pulses, inputs, language, density, gameplayInputEnabled, terminalLocked = terminal && !terminalReady,
        ),
    ) {
        GameplayCanvas(
            component, host, inputs, gameplayInputEnabled, performanceDiagnostics, textMeasurer, pauseLayout, terminalElapsed,
            trialInfoShown, hudDensity, hudTextMeasurer,
        )
        // The run's overlays and the controls' semantics lay out at the HUD's (interface-scaled) density.
        CompositionLocalProvider(LocalDensity provides hudDensity) {
            GameplayOverlays(
                component, host, pulses, inputs, onOutput, facts, phase, gameplayInputEnabled, terminal, terminalElapsed,
                choiceLayout, pauseLayout, layoutDimensions, hudDensity, hudScale, trialDescription, performanceDiagnostics,
                hudTextMeasurer, language,
            )
        }
    }
}

/** The HUD's Compose text measurer: the plain one unless the interface scale gives the HUD its own density. */
@Composable
private fun rememberHudComposeTextMeasurer(
    composeTextMeasurer: ComposeTextMeasurer,
    localDensity: Density,
    hudDensity: Density,
): ComposeTextMeasurer {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(composeTextMeasurer, hudDensity, fontFamilyResolver, layoutDirection) {
        if (hudDensity === localDensity) composeTextMeasurer else ComposeTextMeasurer(fontFamilyResolver, hudDensity, layoutDirection, 64)
    }
}

/** The world's canvas text measurer at the run's text scale, language, typography and role palette. */
@Composable
private fun rememberCanvasTextMeasurer(composeTextMeasurer: ComposeTextMeasurer, textScale: Float, language: AppLanguage): CanvasTextMeasurer {
    val typography = kinetickk.foundation.design.rememberInterfaceTypography()
    val roles = LocalKkRolePalette.current
    return remember(composeTextMeasurer, textScale, language, typography, roles) {
        CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            typography = typography,
            scale = textScale,
            language = language,
            roles = roles,
        )
    }
}

/** The HUD's canvas text measurer: [textMeasurer] itself unless the HUD has its own Compose measurer. */
private fun hudCanvasTextMeasurer(
    textMeasurer: CanvasTextMeasurer,
    hudComposeTextMeasurer: ComposeTextMeasurer,
    composeTextMeasurer: ComposeTextMeasurer,
): CanvasTextMeasurer =
    if (hudComposeTextMeasurer === composeTextMeasurer) {
        textMeasurer
    } else {
        CanvasTextMeasurer(hudComposeTextMeasurer, textMeasurer.scale, textMeasurer.language, textMeasurer.typography, textMeasurer.roles)
    }

/** The pause overlay's geometry while the run is paused. */
@Composable
private fun rememberPauseLayout(phase: GamePhase, layoutDimensions: GameplayLayoutDimensions) =
    if (phase == GamePhase.PAUSED) {
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

/** The reward overlay's geometry while a choice is open. */
@Composable
private fun rememberChoiceLayout(phase: GamePhase, layoutDimensions: GameplayLayoutDimensions, facts: GameplayCompositionFacts) =
    if (phase == GamePhase.CHOICE) {
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

/** Focus, the frame loop, pause on focus loss and the viewport's density: the host's effects, in its own scope. */
@Composable
@NonRestartableComposable
private fun GameplayHostEffects(
    component: GameplayInteractionPort,
    host: GameplayHostState,
    pulses: GameplayHostDispatch,
    inputEnabled: Boolean,
    phase: GamePhase,
    density: Float,
    diagnostics: CrashDiagnostics,
    performanceDiagnostics: PerformanceDiagnostics?,
    rewardUiReady: Boolean,
) {
    val interactionValidator = host.interactionValidator
    SideEffect(component, inputEnabled, phase) {
        if (inputEnabled) host.focusRequester.requestFocus()
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
            renderModel = host.renderModel,
            renderTimeSeconds = host.renderTimeSeconds,
            fpsCounter = host.fpsCounter,
            fpsReadout = host.fpsReadout,
            controlLog = host.controlLog,
            dispatch = pulses::dispatch,
            reportInvalid = pulses::reportInvalidInteractionInput,
        )
    }

    // Pause when unfocused: the window losing focus pauses a running run (an edge, never a level).
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val windowFocus = host.windowFocus
    LaunchedEffect(component, windowFocused) {
        val model = host.renderModel.value
        val pause = pausesOnFocusLoss(windowFocus.focused, windowFocused, model.phase, model.settings.pauseOnFocusLoss, inputEnabled)
        windowFocus.focused = windowFocused
        if (pause) pulses.dispatch(GameplayInteractionPulse.PauseToggled)
    }

    // The Nucleus receives the viewport in dp, so a density change at an unchanged pixel size (a
    // display scale change) resends it; onSizeChanged covers every pixel size change.
    val viewport = host.viewport
    LaunchedEffect(component, density) {
        if (viewport.width > 0 && viewport.height > 0 && viewport.density != density) {
            viewport.density = density
            pulses.dispatchValidated(
                interactionValidator.viewportChanged(
                    rawWidthPx = viewport.width.toFloat(),
                    rawHeightPx = viewport.height.toFloat(),
                    rawDensity = density,
                ),
            )
        }
    }
}

/** The gameplay root: its semantics and focus, the viewport it reports, its keys and the secondary-button brake. */
@Composable
private fun gameplayRootModifier(
    component: GameplayInteractionPort,
    host: GameplayHostState,
    pulses: GameplayHostDispatch,
    inputs: GameplayHostInputs,
    language: AppLanguage,
    density: Float,
    gameplayInputEnabled: Boolean,
    terminalLocked: Boolean,
): Modifier {
    val viewport = host.viewport
    val interactionValidator = host.interactionValidator
    return Modifier
        .fillMaxSize()
        .background(Kk.Ink)
        .testTag(GAMEPLAY_ROOT_TAG)
        .semantics {
            contentDescription = language.text(GameplayText.Gameplay)
        }
        .focusRequester(host.focusRequester)
        .focusable()
        .onSizeChanged { size ->
            viewport.width = size.width
            viewport.height = size.height
            viewport.density = density
            pulses.dispatchValidated(
                interactionValidator.viewportChanged(
                    rawWidthPx = size.width.toFloat(),
                    rawHeightPx = size.height.toFloat(),
                    rawDensity = density,
                ),
            )
        }
        .onKeyEvent { event ->
            inputs.applyKeyInput(
                gameplayKeyInput(event.key, event.type, host.renderModel.value, gameplayInputEnabled, terminalLocked = terminalLocked),
            )
        }
        .pointerInput(component, gameplayInputEnabled) {
            if (!gameplayInputEnabled) return@pointerInput
            awaitPointerEventScope { routeSecondaryBrake(inputs) }
        }
}

/** The world, HUD and pause canvas with the gameplay pointer routes. */
@Composable
@NonRestartableComposable
private fun GameplayCanvas(
    component: GameplayInteractionPort,
    host: GameplayHostState,
    inputs: GameplayHostInputs,
    gameplayInputEnabled: Boolean,
    performanceDiagnostics: PerformanceDiagnostics?,
    textMeasurer: CanvasTextMeasurer,
    pauseLayout: PauseLayoutGeometry?,
    terminalElapsed: Float,
    trialInfoShown: Boolean,
    hudDensity: Density,
    hudTextMeasurer: CanvasTextMeasurer,
) {
    val interactionValidator = host.interactionValidator
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
                awaitPointerEventScope { routeGameplayPointers(inputs, interactionValidator) }
            },
    ) {
        val drawStartedAt = if (DIAGNOSTICS_ENABLED && performanceDiagnostics?.capturesPerformance == true) TimeSource.Monotonic.markNow() else null
        drawGameplay(
            engine = host.renderModel.value,
            visualFx = host.visualFxProjection.value,
            textMeasurer = textMeasurer,
            renderTime = host.renderTimeSeconds.floatValue,
            pauseLayout = pauseLayout,
            terminalElapsed = terminalElapsed,
            hudMemory = host.hudMemory,
            trialInfoOpen = trialInfoShown,
            controls = host.controlLog,
            hudDensity = hudDensity,
            hudTextMeasurer = hudTextMeasurer,
        )
        if (DIAGNOSTICS_ENABLED && drawStartedAt != null) {
            performanceDiagnostics?.phase(DiagnosticPhase.CanvasDraw, drawStartedAt.elapsedNow().inWholeNanoseconds)
        }
    }
}

/** The run's overlays over the canvas: reward preparation, the terminal report, the reward choice and the running controls. */
@Composable
@NonRestartableComposable
private fun GameplayOverlays(
    component: GameplayInteractionPort,
    host: GameplayHostState,
    pulses: GameplayHostDispatch,
    inputs: GameplayHostInputs,
    onOutput: (GameplayInteractionOutput) -> Unit,
    facts: GameplayCompositionFacts,
    phase: GamePhase,
    gameplayInputEnabled: Boolean,
    terminal: Boolean,
    terminalElapsed: Float,
    choiceLayout: ChoiceLayoutGeometry?,
    pauseLayout: PauseLayoutGeometry?,
    layoutDimensions: GameplayLayoutDimensions,
    hudDensity: Density,
    hudScale: Float,
    trialDescription: String?,
    performanceDiagnostics: PerformanceDiagnostics?,
    hudTextMeasurer: CanvasTextMeasurer,
    language: AppLanguage,
) {
    val rewardUiReady = host.rewardUiReady.value
    if (!rewardUiReady) key(component) {
        RewardUiPreparation(host.renderModel.value) { host.rewardUiReady.value = true }
    }
    if (terminal) {
        TerminalContent(
            host.renderModel.value,
            terminalElapsed,
            gameplayInputEnabled,
            reactionsThisRun = host.visualFxProjection.value.runReactionMask,
            discoveredThisRun = host.visualFxProjection.value.runDiscoveryMask,
        ) { input ->
            pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
            inputs.dispatchInput(input)
        }
    }
    if (phase == GamePhase.CHOICE && rewardUiReady) {
        RewardContent(
            engine = host.renderModel.value,
            layout = requireNotNull(choiceLayout),
            renderTime = host.renderTimeSeconds,
            enabled = gameplayInputEnabled,
            onSelect = { index ->
                pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
                pulses.dispatchValidated(host.interactionValidator.choiceSelected(index))
            },
            onReroll = {
                pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
                pulses.dispatch(GameplayInteractionPulse.ChoicesRerolled)
            },
            onBuild = { onOutput(GameplayInteractionOutput.OpenCodex) },
            touch = host.controlLog.touchInput,
        )
    }
    GameplayRunningControls(
        host, pulses, inputs, onOutput, facts, phase, gameplayInputEnabled, pauseLayout, layoutDimensions, hudDensity, hudScale,
        trialDescription, performanceDiagnostics, hudTextMeasurer, language,
    )
}

/** The build button, the controls' semantics and the FPS readout above or beside the HUD. */
@Composable
@NonRestartableComposable
private fun GameplayRunningControls(
    host: GameplayHostState,
    pulses: GameplayHostDispatch,
    inputs: GameplayHostInputs,
    onOutput: (GameplayInteractionOutput) -> Unit,
    facts: GameplayCompositionFacts,
    phase: GamePhase,
    gameplayInputEnabled: Boolean,
    pauseLayout: PauseLayoutGeometry?,
    layoutDimensions: GameplayLayoutDimensions,
    hudDensity: Density,
    hudScale: Float,
    trialDescription: String?,
    performanceDiagnostics: PerformanceDiagnostics?,
    hudTextMeasurer: CanvasTextMeasurer,
    language: AppLanguage,
) {
    val fpsReadout = host.fpsReadout
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
            content = host.content,
            trialDescription = trialDescription,
            density = hudDensity,
            hudScale = hudScale,
            performanceEnabled = DIAGNOSTICS_ENABLED && performanceDiagnostics?.overlayVisible == true,
            fps = fpsReadout,
            pauseLayout = pauseLayout,
            onTrialInfoFocusChanged = { host.trialInfoFocused.value = it },
            onTrialInfoToggle = { host.trialInfoOpen.value = !host.trialInfoOpen.value },
            onInput = { input ->
                pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
                inputs.dispatchInput(input)
            },
            onControl = { target ->
                pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
                inputs.pressRunningControl(target)
            },
            onBrakeChanged = { active ->
                if (active) pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
                pulses.dispatch(
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

private const val GAMEPLAY_ROOT_TAG = "kinetickk.gameplay"
