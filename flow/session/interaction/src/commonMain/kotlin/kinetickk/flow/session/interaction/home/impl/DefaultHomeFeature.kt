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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
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
    )
    private val audioExecutor = SessionAudioExecutor(audioService)

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
        val language = LocalAppLanguage.current
        val localDensity = LocalDensity.current
        val density = localDensity.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var revisionValue by remember { mutableIntStateOf(0) }
        var viewportValue by remember { mutableStateOf(HomeViewport(0f, 0f, density)) }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        var previewShapeValue by remember { mutableStateOf<CoreShape?>(null) }
        var activeTargetValue by remember { mutableStateOf(HomeLayoutTarget.START) }
        var cursorValue by remember { mutableStateOf(Offset.Zero) }
        val menuMotion = remember { HomeMenuMotion() }
        val actionFocus = remember { HomeLayoutTarget.entries.associateWith { FocusRequester() } }
        LaunchedEffect(inputEnabled, viewportValue.width > 0f) {
            if (inputEnabled && viewportValue.width > 0f) actionFocus.getValue(activeTargetValue).requestFocus()
        }
        @Suppress("UNUSED_EXPRESSION")
        revisionValue
        val uiModel = reducer.uiModel(profilePort.query(ProfileQuery.GetHomeProgress))
        val textScale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        val typography = kinetickk.foundation.design.rememberInterfaceTypography()
        val textMeasurer = remember(composeTextMeasurer, textScale, language, typography) {
            CanvasTextMeasurer(
                delegate = composeTextMeasurer,
                typography = typography,
                scale = textScale,
                language = language,
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
                val action = resolveHomePress(viewportValue, position.x, position.y)
                if (action is HomeAction.SelectCoreShape) previewShapeValue = action.shape
                val enabled = action !is HomeAction.SelectCoreShape ||
                    uiModel.isCoreShapeUnlocked(action.shape)
                if (action != null && enabled) dispatch(action)
            }
        }

        LaunchedEffect(Unit) {
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                renderTimeSecondsValue += selectHomePresentationFrameDeltaSeconds(
                    (frame - previousFrame) / 1_000_000_000f,
                )
                menuMotion.advance(activeTargetValue, cursorValue, viewportValue, (frame - previousFrame) / 1_000_000_000f)
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
                .background(SpaceBlack)
                .testTag(HOME_ROOT_TAG)
                .onPreviewKeyEvent { event ->
                    if (!inputEnabled || event.type != KeyEventType.KeyDown ||
                        (event.key != Key.DirectionDown && event.key != Key.DirectionUp)) false
                    else {
                        val targets = listOf(HomeLayoutTarget.START, HomeLayoutTarget.LAB, HomeLayoutTarget.ARMORY,
                            HomeLayoutTarget.REBIRTH, HomeLayoutTarget.CODEX, HomeLayoutTarget.SETTINGS)
                        val step = if (event.key == Key.DirectionDown) 1 else -1
                        activeTargetValue = targets[(targets.indexOf(activeTargetValue) + step + targets.size) % targets.size]
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
                .pointerInput(inputEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (inputEnabled) event.changes.firstOrNull()?.let { cursorValue = it.position }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { position -> currentTapHandlerValue(position) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(SpaceBlack)
                drawHome(uiModel, textMeasurer, renderTimeSecondsValue, layout, previewShapeValue, activeTargetValue, menuMotion)
            }
            if (inputEnabled && viewportValue.width > 0f && viewportValue.height > 0f) {
                layout.actions.forEach { action ->
                    key(action.target) {
                        val shape = action.target.coreShapeOrNull()
                        val enabled = shape == null || uiModel.isCoreShapeUnlocked(shape)
                        HomeSemanticAction(
                            action = action,
                            density = localDensity,
                            enabled = enabled,
                            selected = shape != null && uiModel.coreShape == shape,
                            focusRequester = actionFocus.getValue(action.target),
                            onPreview = { active ->
                                if (active) {
                                    if (enabled) actionFocus.getValue(action.target).requestFocus()
                                    if (shape != null) previewShapeValue = shape
                                    else {
                                        activeTargetValue = action.target
                                        previewShapeValue = null
                                    }
                                } else if (previewShapeValue == shape) previewShapeValue = null
                            },
                            onClick = { dispatch(action.target.toHomeAction()) },
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
    enabled: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    onPreview: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = action.target.homeContentDescription(language)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(focused || hovered) { onPreview(focused || hovered) }
    Box(
        Modifier
            .placeInHomeBounds(action.bounds, density)
            .focusRequester(focusRequester)
            .drawBehind {
                if (focused || (hovered && enabled)) {
                    drawRect(White.copy(alpha = 0.06f))
                    if (focused) {
                        drawLine(White, Offset(8.dp.toPx(), size.height - 2.dp.toPx()),
                            Offset(size.width - 18.dp.toPx(), size.height - 2.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
            .hoverable(interactions)
            .testTag(action.target.homeTestTag())
            .onKeyEvent { event ->
                enabled && activateHomeSemanticButtonFromKey(
                    event.key,
                    event.type,
                    onClick,
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                if (action.target.coreShapeOrNull() != null) {
                    this.selected = selected
                    stateDescription = if (selected) language.text(SessionText.SELECTED_STATE) else if (enabled) language.text(SessionText.AVAILABLE_STATE) else language.text(SessionText.LOCKED_STATE)
                }
                if (enabled) {
                    onClick(label = description) {
                        onClick()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .focusable(enabled = enabled, interactionSource = interactions),
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
    HomeLayoutTarget.CORE_ORB -> "kinetickk.home.core.orb"
    HomeLayoutTarget.CORE_PRISM -> "kinetickk.home.core.prism"
    HomeLayoutTarget.CORE_SHARD -> "kinetickk.home.core.shard"
    HomeLayoutTarget.CORE_RING -> "kinetickk.home.core.ring"
    HomeLayoutTarget.CORE_DIAMOND -> "kinetickk.home.core.diamond"
    HomeLayoutTarget.CORE_TESSERACT -> "kinetickk.home.core.tesseract"
    HomeLayoutTarget.START -> "kinetickk.home.start"
    HomeLayoutTarget.LAB -> "kinetickk.home.lab"
    HomeLayoutTarget.ARMORY -> "kinetickk.home.armory"
    HomeLayoutTarget.REBIRTH -> "kinetickk.home.rebirth"
    HomeLayoutTarget.CODEX -> "kinetickk.home.codex"
    HomeLayoutTarget.SETTINGS -> "kinetickk.home.settings"
}

private fun HomeLayoutTarget.homeContentDescription(language: AppLanguage): String = when (this) {
    HomeLayoutTarget.CORE_ORB -> language.text(SessionText.SELECT_CIRCLE)
    HomeLayoutTarget.CORE_PRISM -> language.text(SessionText.SELECT_SQUARE)
    HomeLayoutTarget.CORE_SHARD -> language.text(SessionText.SELECT_TRIANGLE)
    HomeLayoutTarget.CORE_RING -> language.text(SessionText.SELECT_RING)
    HomeLayoutTarget.CORE_DIAMOND -> language.text(SessionText.SELECT_DIAMOND)
    HomeLayoutTarget.CORE_TESSERACT -> language.text(SessionText.SELECT_TESSERACT)
    HomeLayoutTarget.START -> language.text(SessionText.START_DESCRIPTION)
    HomeLayoutTarget.LAB -> language.text(SessionText.LAB_DESCRIPTION)
    HomeLayoutTarget.ARMORY -> language.text(SessionText.ARMORY_DESCRIPTION)
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
