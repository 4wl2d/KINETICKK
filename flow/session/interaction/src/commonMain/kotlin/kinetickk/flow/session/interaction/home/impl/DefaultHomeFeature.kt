// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.foundation.design.*
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.toggled
import kinetickk.resource.audio.api.AudioService
import kotlin.math.roundToInt

class DefaultHomeFeature(
    private val profilePort: ProfileReadPort,
    uiCatalog: UiCatalogSnapshot,
    audioService: AudioService,
    /** The host can close the game: Home shows a Quit button that emits [HomeOutput.Quit]. */
    private val quitAvailable: Boolean = false,
) : HomeFeature {
    private val reducer = HomeReducer(
        coreShapes = uiCatalog.coreShapes,
        itemCount = uiCatalog.items.size,
        weaponCount = uiCatalog.weapons.size,
        rebirthPolicy = uiCatalog.rebirth,
        weapons = uiCatalog.weapons,
        relicCount = uiCatalog.relics.size,
        matrixNodes = uiCatalog.matrixNodes,
        upgrades = uiCatalog.upgrades,
    )
    private val audioExecutor = SessionAudioExecutor(audioService)

    @Composable
    override fun Content(inputEnabled: Boolean, runMode: RunMode, onOutput: (HomeOutput) -> Unit) {
        val language = LocalAppLanguage.current
        val localDensity = LocalDensity.current
        val density = localDensity.density
        val roles = LocalKkRolePalette.current
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        val home = remember { HomeContentState(density, quitAvailable) }
        val revisionValue by home.revision
        val viewportValue by home.viewport
        val activeTargetValue by home.activeTarget
        val actionFocus = home.actionFocus
        val actionsReady = inputEnabled && viewportValue.width > 0f && viewportValue.height > 0f
        LaunchedEffect(actionsReady) {
            if (actionsReady) actionFocus.getValue(activeTargetValue).requestFocus()
        }
        @Suppress("UNUSED_EXPRESSION")
        revisionValue
        val uiModel = reducer.uiModel(
            profilePort.query(ProfileQuery.GetHomeProgress),
            profilePort.query(ProfileQuery.GetMatrixProgress).snapshot.progress,
        )
        val textScale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        val typography = rememberInterfaceTypography()
        val textMeasurer = remember(composeTextMeasurer, textScale, language, typography, roles) {
            CanvasTextMeasurer(
                delegate = composeTextMeasurer,
                typography = typography,
                // UI text at the design's size at the default text size; display type divides it out.
                scale = homeUiScale(textScale),
                language = language,
                roles = roles,
            )
        }
        val dispatcher = remember(onOutput) { HomeDispatch(reducer, audioExecutor, onOutput, home.revision) }

        val layout = remember(viewportValue) {
            homeLayoutGeometry(
                viewportValue.width,
                viewportValue.height,
                viewportValue.density,
                viewportValue.quitAvailable,
            )
        }

        val currentTapHandler = rememberUpdatedState<(Offset) -> Unit> { position ->
            if (inputEnabled) homeTap(home, dispatcher, position, runMode, layout, textMeasurer)
        }
        HomeFrameLoop(home, roles)

        Box(modifier = homeRootModifier(home, inputEnabled, language, density, quitAvailable, currentTapHandler)) {
            HomeCanvas(uiModel, textMeasurer, home, layout, textScale, runMode)
            if (actionsReady) HomeSemanticControls(layout, home, dispatcher, localDensity, uiModel, language, textScale, runMode)
        }
    }
}

/** The Home screen's state for one composition of [DefaultHomeFeature.Content]: viewport, clock, selection and tooltips. */
private class HomeContentState(density: Float, quitAvailable: Boolean) {
    val revision = mutableIntStateOf(0)
    val viewport = mutableStateOf(HomeViewport(0f, 0f, density, quitAvailable))
    val renderTimeSeconds = mutableFloatStateOf(0f)
    val activeTarget = mutableStateOf(HomeLayoutTarget.START)
    val focusedTarget = mutableStateOf<HomeLayoutTarget?>(null)
    val openInfo = mutableStateOf<HomeInfoTarget?>(null)
    val activeInfo = mutableStateOf<HomeInfoTarget?>(null)
    val quitActive = mutableStateOf(false)

    // The Baby mode toggle: hovered or focused, and whether a tap on it left its tooltip open.
    val runModeActive = mutableStateOf(false)
    val runModeTip = mutableStateOf(false)
    val menuMotion = HomeMenuMotion()
    val actionFocus = HomeLayoutTarget.entries.associateWith { FocusRequester() }
}

/** Runs a Home action through the reducer, bumps [revision] and executes its effects (audio, outputs). */
private class HomeDispatch(
    private val reducer: HomeReducer,
    private val audioExecutor: SessionAudioExecutor,
    private val onOutput: (HomeOutput) -> Unit,
    private val revision: MutableIntState,
) {
    fun dispatch(action: HomeAction) {
        val reduction = reducer.reduce(action)
        revision.intValue++
        reduction.effects.forEach { effect ->
            when (effect) {
                is HomeEffect.PlayAudio -> audioExecutor.play(effect.cue)
                is HomeEffect.Emit -> onOutput(effect.output)
            }
        }
    }
}

/** A tap on the Home screen at [position]: the run-mode toggle, its tooltip, an info button or a menu row. */
private fun homeTap(
    home: HomeContentState,
    dispatcher: HomeDispatch,
    position: Offset,
    runMode: RunMode,
    layout: HomeLayoutGeometry,
    textMeasurer: CanvasTextMeasurer,
) {
    val viewport = home.viewport.value
    val press = resolveHomePress(viewport, position.x, position.y, runMode)
    // A tap on the toggle asks Session for the other mode and leaves its tooltip open; any other tap closes it.
    val toggle = press as? HomeAction.SelectRunMode
    // The slip covers menu rows: a tap on it only closes it and presses nothing hidden under it.
    val onTip = toggle == null &&
        homeShowsRunModeTip(layout, home.openInfo.value, home.activeInfo.value, home.runModeActive.value, home.runModeTip.value) &&
        position in homeRunModeTipRect(textMeasurer, layout, viewport)
    home.runModeTip.value = toggle != null
    val info = if (toggle != null || onTip) null else resolveHomeInfoPress(viewport, position.x, position.y)
    when {
        toggle != null -> {
            home.openInfo.value = null
            dispatcher.dispatch(toggle)
        }
        onTip -> Unit
        info != null -> home.openInfo.value = if (home.openInfo.value == info) null else info
        else -> {
            home.openInfo.value = null
            press?.let(dispatcher::dispatch)
        }
    }
}

/** Advances the Home clock and the menu motion once per paced frame. */
@Composable
@NonRestartableComposable
private fun HomeFrameLoop(home: HomeContentState, roles: KkRolePalette) {
    val elementHuesValue by rememberUpdatedState(roles.elements)
    val frameRateCap = LocalKkFrameRateCap.current
    LaunchedEffect(frameRateCap) {
        val pacer = KkFramePacer(frameRateCap)
        var previousFrame = pacer.awaitFrame()
        while (true) {
            val frame = pacer.awaitFrame()
            val delta = selectHomePresentationFrameDeltaSeconds((frame - previousFrame) / 1_000_000_000f)
            home.renderTimeSeconds.floatValue += delta
            home.menuMotion.advance(home.activeTarget.value, delta, elementHuesValue)
            previousFrame = frame
        }
    }
}

/** The Home root: arrow keys walk the menu, its description, the viewport it reports and its taps. */
@Composable
private fun homeRootModifier(
    home: HomeContentState,
    inputEnabled: Boolean,
    language: AppLanguage,
    density: Float,
    quitAvailable: Boolean,
    tapHandler: State<(Offset) -> Unit>,
): Modifier {
    val actionFocus = home.actionFocus
    return Modifier
        .fillMaxSize()
        .background(Kk.Ink)
        .testTag(HOME_ROOT_TAG)
        .onPreviewKeyEvent { event ->
            if (!inputEnabled || event.type != KeyEventType.KeyDown ||
                (event.key != Key.DirectionDown && event.key != Key.DirectionUp)) false
            else {
                val step = if (event.key == Key.DirectionDown) 1 else -1
                val targets = HomeMenuTargets
                val index = targets.indexOf(home.activeTarget.value)
                home.activeTarget.value = targets[(index + step + targets.size) % targets.size]
                actionFocus.getValue(home.activeTarget.value).requestFocus()
                true
            }
        }
        .semantics {
            contentDescription = language.text(SessionText.HOME_DESCRIPTION)
        }
        .onSizeChanged { size ->
            home.viewport.value = HomeViewport(size.width.toFloat(), size.height.toFloat(), density, quitAvailable)
        }
        .pointerInput(Unit) {
            detectTapGestures { position -> tapHandler.value(position) }
        }
}

/** The Home screen drawn in one canvas from the presentation state. */
@Composable
@NonRestartableComposable
private fun HomeCanvas(
    uiModel: HomeUiModel,
    textMeasurer: CanvasTextMeasurer,
    home: HomeContentState,
    layout: HomeLayoutGeometry,
    textScale: Float,
    runMode: RunMode,
) {
    Canvas(Modifier.fillMaxSize()) {
        drawHome(
            uiModel, textMeasurer, home.renderTimeSeconds.floatValue, layout,
            HomeDrawState(
                activeTarget = home.activeTarget.value,
                focusedTarget = home.focusedTarget.value,
                openInfo = home.openInfo.value,
                activeInfo = home.activeInfo.value,
                textScale = textScale,
                quitActive = home.quitActive.value,
                easy = runMode == RunMode.EASY,
                runModeActive = home.runModeActive.value,
                runModeTip = home.runModeTip.value,
            ),
            home.menuMotion,
        )
    }
}

/** The menu rows, info buttons, Quit plate and run-mode toggle as focusable semantic controls over the canvas. */
@Composable
@NonRestartableComposable
private fun HomeSemanticControls(
    layout: HomeLayoutGeometry,
    home: HomeContentState,
    dispatcher: HomeDispatch,
    localDensity: Density,
    uiModel: HomeUiModel,
    language: AppLanguage,
    textScale: Float,
    runMode: RunMode,
) {
    val actionFocus = home.actionFocus
    layout.actions.forEach { action ->
        key(action.target) {
            HomeSemanticAction(
                action = action,
                density = localDensity,
                focusRequester = actionFocus.getValue(action.target),
                onFocus = { focused ->
                    if (focused) home.focusedTarget.value = action.target
                    else if (home.focusedTarget.value == action.target) home.focusedTarget.value = null
                    if (focused) home.activeTarget.value = action.target
                },
                onHover = {
                    actionFocus.getValue(action.target).requestFocus()
                    home.activeTarget.value = action.target
                },
                onClick = { dispatcher.dispatch(action.target.toHomeAction()) },
            )
        }
    }
    layout.infos.forEach { info ->
        key(info.target) {
            HomeInfoAction(
                info = info,
                density = localDensity,
                description = when (info.target) {
                    HomeInfoTarget.FACTS -> home.menuMotion.facts(uiModel, home.activeTarget.value, language, textScale).info
                    HomeInfoTarget.FORM -> homeFormInfo(uiModel, language)
                },
                onActive = { active ->
                    if (active) home.activeInfo.value = info.target
                    else if (home.activeInfo.value == info.target) home.activeInfo.value = null
                },
                onToggle = { home.openInfo.value = if (home.openInfo.value == info.target) null else info.target },
            )
        }
    }
    layout.quit?.let { quit ->
        HomeQuitAction(
            quit = quit,
            density = localDensity,
            onActive = { active -> home.quitActive.value = active },
            onClick = { dispatcher.dispatch(HomeAction.Quit) },
        )
    }
    HomeRunModeAction(
        toggle = layout.runModeToggle,
        density = localDensity,
        easy = runMode == RunMode.EASY,
        onActive = { active -> home.runModeActive.value = active },
        onClick = { dispatcher.dispatch(HomeAction.SelectRunMode(runMode.toggled())) },
    )
}

@Composable
private fun HomeSemanticAction(
    action: HomeActionBounds,
    density: Density,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = action.target.homeContentDescription(language)
    val onHoverValue by rememberUpdatedState(onHover)
    Box(
        Modifier
            .placeInHomeBounds(action.bounds, density)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocus(it.isFocused) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter && event.changes.any { it.type != PointerType.Touch }) {
                            onHoverValue()
                        }
                    }
                }
            }
            .testTag(action.target.homeTestTag())
            .onKeyEvent { event -> activateHomeSemanticButtonFromKey(event.key, event.type, onClick) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .focusable(),
    )
}

/** Focusable (!) node over a drawn info button: announces its text; hover/focus/tap show it. */
@Composable
private fun HomeInfoAction(
    info: HomeInfoBounds,
    density: Density,
    description: String,
    onActive: (Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(focused || hovered) { onActive(focused || hovered) }
    Box(
        Modifier
            .placeInHomeBounds(info.touch, density)
            .hoverable(interactions)
            .testTag(info.target.homeInfoTestTag())
            .onKeyEvent { event -> activateHomeSemanticButtonFromKey(event.key, event.type, onToggle) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onToggle()
                    true
                }
            }
            .focusable(interactionSource = interactions),
    )
}

/** Focusable Quit node over the drawn button: hover/focus light it, Enter, Space or a click quit. */
@Composable
private fun HomeQuitAction(
    quit: HomeQuitBounds,
    density: Density,
    onActive: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = language.text(SessionRedesignText.QUIT_DESCRIPTION)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(focused || hovered) { onActive(focused || hovered) }
    Box(
        Modifier
            .placeInHomeBounds(quit.touch, density)
            .hoverable(interactions)
            .testTag(HOME_QUIT_TAG)
            .onKeyEvent { event -> activateHomeSemanticButtonFromKey(event.key, event.type, onClick) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .focusable(interactionSource = interactions),
    )
}

/**
 * Focusable Baby mode switch over the drawn toggle: it announces its name and state, hover/focus light it and show its
 * tooltip, and Enter, Space or a click ask Session for the other mode. Tab reaches it; the arrow keys keep the menu rows.
 */
@Composable
private fun HomeRunModeAction(
    toggle: HomeToggleBounds,
    density: Density,
    easy: Boolean,
    onActive: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val label = language.text(SessionRedesignText.BABY_MODE)
    val state = language.text(if (easy) SessionRedesignText.BABY_MODE_ON else SessionRedesignText.BABY_MODE_OFF)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(focused || hovered) { onActive(focused || hovered) }
    Box(
        Modifier
            .placeInHomeBounds(toggle.touch, density)
            .hoverable(interactions)
            .testTag(HOME_RUN_MODE_TAG)
            .onKeyEvent { event -> activateHomeSemanticButtonFromKey(event.key, event.type, onClick) }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(easy)
                contentDescription = label
                stateDescription = state
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .focusable(interactionSource = interactions),
    )
}

private fun Modifier.placeInHomeBounds(bounds: Rect, density: Density): Modifier =
    this
        .offset {
            IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
        }
        .requiredSize(
            width = with(density) { bounds.width.toDp() },
            height = with(density) { bounds.height.toDp() },
        )

private fun HomeLayoutTarget.homeTestTag(): String = when (this) {
    HomeLayoutTarget.START -> "kinetickk.home.start"
    HomeLayoutTarget.WORKSHOP -> "kinetickk.home.workshop"
    HomeLayoutTarget.REBIRTH -> "kinetickk.home.rebirth"
    HomeLayoutTarget.CODEX -> "kinetickk.home.codex"
    HomeLayoutTarget.SETTINGS -> "kinetickk.home.settings"
}

private fun HomeInfoTarget.homeInfoTestTag(): String = when (this) {
    HomeInfoTarget.FACTS -> "kinetickk.home.info.facts"
    HomeInfoTarget.FORM -> "kinetickk.home.info.form"
}

private fun HomeLayoutTarget.homeContentDescription(language: AppLanguage): String = when (this) {
    HomeLayoutTarget.START -> language.text(SessionText.START_DESCRIPTION)
    HomeLayoutTarget.WORKSHOP -> language.text(SessionText.WORKSHOP_DESCRIPTION)
    HomeLayoutTarget.REBIRTH -> language.text(SessionText.REBIRTH_DESCRIPTION)
    HomeLayoutTarget.CODEX -> language.text(SessionText.CODEX_DESCRIPTION)
    HomeLayoutTarget.SETTINGS -> language.text(SessionText.SETTINGS_DESCRIPTION)
}

private const val HOME_ROOT_TAG = "kinetickk.home"
internal const val HOME_QUIT_TAG = "kinetickk.home.quit"
internal const val HOME_RUN_MODE_TAG = "kinetickk.home.run-mode"

private inline fun activateHomeSemanticButtonFromKey(
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

internal const val MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS: Float = 0.1f

internal fun selectHomePresentationFrameDeltaSeconds(frameDeltaSeconds: Float): Float =
    frameDeltaSeconds.coerceAtMost(MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS)
