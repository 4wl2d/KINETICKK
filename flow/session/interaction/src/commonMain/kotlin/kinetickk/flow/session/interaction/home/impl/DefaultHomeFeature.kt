// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.foundation.design.*
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.resource.audio.api.AudioService
import kotlin.math.roundToInt

class DefaultHomeFeature(
    private val profilePort: ProfileReadPort,
    uiCatalog: UiCatalogSnapshot,
    audioService: AudioService,
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
    override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
        val language = LocalAppLanguage.current
        val localDensity = LocalDensity.current
        val density = localDensity.density
        val roles = LocalKkRolePalette.current
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var revisionValue by remember { mutableIntStateOf(0) }
        var viewportValue by remember { mutableStateOf(HomeViewport(0f, 0f, density)) }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        var activeTargetValue by remember { mutableStateOf(HomeLayoutTarget.START) }
        var focusedTargetValue by remember { mutableStateOf<HomeLayoutTarget?>(null) }
        var openInfoValue by remember { mutableStateOf<HomeInfoTarget?>(null) }
        var activeInfoValue by remember { mutableStateOf<HomeInfoTarget?>(null) }
        val menuMotion = remember { HomeMenuMotion() }
        val actionFocus = remember { HomeLayoutTarget.entries.associateWith { FocusRequester() } }
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

        fun dispatch(action: HomeAction) {
            val reduction = reducer.reduce(action)
            revisionValue++
            reduction.effects.forEach { effect ->
                when (effect) {
                    is HomeEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is HomeEffect.Emit -> onOutput(effect.output)
                }
            }
        }

        val currentTapHandlerValue by rememberUpdatedState<(Offset) -> Unit> { position ->
            if (inputEnabled) {
                val info = resolveHomeInfoPress(viewportValue, position.x, position.y)
                if (info != null) {
                    openInfoValue = if (openInfoValue == info) null else info
                } else {
                    openInfoValue = null
                    val action = resolveHomePress(viewportValue, position.x, position.y)
                    if (action != null) dispatch(action)
                }
            }
        }

        val elementHuesValue by rememberUpdatedState(roles.elements)
        LaunchedEffect(Unit) {
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val delta = selectHomePresentationFrameDeltaSeconds((frame - previousFrame) / 1_000_000_000f)
                renderTimeSecondsValue += delta
                menuMotion.advance(activeTargetValue, delta, elementHuesValue)
                previousFrame = frame
            }
        }

        val layout = remember(viewportValue) {
            homeLayoutGeometry(
                viewportValue.width,
                viewportValue.height,
                viewportValue.density,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Kk.Ink)
                .testTag(HOME_ROOT_TAG)
                .onPreviewKeyEvent { event ->
                    if (!inputEnabled || event.type != KeyEventType.KeyDown ||
                        (event.key != Key.DirectionDown && event.key != Key.DirectionUp)) false
                    else {
                        val step = if (event.key == Key.DirectionDown) 1 else -1
                        val targets = HomeNavigationTargets
                        val index = targets.indexOf(activeTargetValue)
                        activeTargetValue = targets[(index + step + targets.size) % targets.size]
                        actionFocus.getValue(activeTargetValue).requestFocus()
                        true
                    }
                }
                .semantics {
                    contentDescription = language.text(SessionText.HOME_DESCRIPTION)
                }
                .onSizeChanged { size ->
                    viewportValue = HomeViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(Unit) {
                    detectTapGestures { position -> currentTapHandlerValue(position) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawHome(
                    uiModel, textMeasurer, renderTimeSecondsValue, layout,
                    HomeDrawState(
                        activeTarget = activeTargetValue,
                        focusedTarget = focusedTargetValue,
                        openInfo = openInfoValue,
                        activeInfo = activeInfoValue,
                        textScale = textScale,
                    ),
                    menuMotion,
                )
            }
            if (actionsReady) {
                layout.actions.forEach { action ->
                    key(action.target) {
                        HomeSemanticAction(
                            action = action,
                            density = localDensity,
                            characterName = uiModel.coreShape(uiModel.coreShape).displayName.localizedContent(language),
                            focusRequester = actionFocus.getValue(action.target),
                            onFocus = { focused ->
                                if (focused) focusedTargetValue = action.target
                                else if (focusedTargetValue == action.target) focusedTargetValue = null
                                if (focused) activeTargetValue = action.target
                            },
                            onHover = {
                                actionFocus.getValue(action.target).requestFocus()
                                activeTargetValue = action.target
                            },
                            onClick = { dispatch(action.target.toHomeAction()) },
                        )
                    }
                }
                layout.infos.forEach { info ->
                    key(info.target) {
                        HomeInfoAction(
                            info = info,
                            density = localDensity,
                            description = when (info.target) {
                                HomeInfoTarget.FACTS -> menuMotion.facts(uiModel, activeTargetValue, language, textScale).info
                                HomeInfoTarget.FORM -> homeFormInfo(uiModel, language)
                            },
                            onActive = { active ->
                                if (active) activeInfoValue = info.target
                                else if (activeInfoValue == info.target) activeInfoValue = null
                            },
                            onToggle = { openInfoValue = if (openInfoValue == info.target) null else info.target },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSemanticAction(
    action: HomeActionBounds,
    density: Density,
    characterName: String,
    focusRequester: FocusRequester,
    onFocus: (Boolean) -> Unit,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = action.target.homeContentDescription(language, characterName)
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
    HomeLayoutTarget.HANGAR -> "kinetickk.home.hangar"
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

private fun HomeLayoutTarget.homeContentDescription(language: AppLanguage, characterName: String): String = when (this) {
    HomeLayoutTarget.HANGAR -> language.text(SessionText.HANGAR_DESCRIPTION, characterName)
    HomeLayoutTarget.START -> language.text(SessionText.START_DESCRIPTION)
    HomeLayoutTarget.WORKSHOP -> language.text(SessionText.WORKSHOP_DESCRIPTION)
    HomeLayoutTarget.REBIRTH -> language.text(SessionText.REBIRTH_DESCRIPTION)
    HomeLayoutTarget.CODEX -> language.text(SessionText.CODEX_DESCRIPTION)
    HomeLayoutTarget.SETTINGS -> language.text(SessionText.SETTINGS_DESCRIPTION)
}

private const val HOME_ROOT_TAG = "kinetickk.home"

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
