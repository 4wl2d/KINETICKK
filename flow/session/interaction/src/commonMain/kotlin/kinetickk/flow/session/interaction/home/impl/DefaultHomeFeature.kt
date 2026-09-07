// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.ball.content.api.localizedContent
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
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
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.resource.audio.api.AudioService
import kotlin.math.PI
import kotlin.math.min
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
        @Suppress("UNUSED_EXPRESSION")
        revisionValue
        val uiModel = reducer.uiModel(profilePort.query(ProfileQuery.GetHomeProgress))
        val textScale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale
        val textMeasurer = remember(composeTextMeasurer, textScale, language) {
            CanvasTextMeasurer(
                delegate = composeTextMeasurer,
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
                drawRect(SpaceBlack)
                drawHome(uiModel, textMeasurer, renderTimeSecondsValue, layout, previewShapeValue)
            }
            if (inputEnabled && viewportValue.width > 0f && viewportValue.height > 0f) {
                layout.actions.forEach { action ->
                    val shape = action.target.coreShapeOrNull()
                    val enabled = shape == null || uiModel.isCoreShapeUnlocked(shape)
                    HomeSemanticAction(
                        action = action,
                        density = localDensity,
                        enabled = enabled,
                        selected = shape != null && uiModel.coreShape == shape,
                        onPreview = { active ->
                            if (active && shape != null) previewShapeValue = shape
                            else if (previewShapeValue == shape) previewShapeValue = null
                        },
                        onClick = { dispatch(action.target.toHomeAction()) },
                    )
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
            .drawBehind {
                if (focused || (hovered && enabled)) {
                    drawRect(White.copy(alpha = 0.06f))
                    if (focused) drawRect(White, style = Stroke(1.dp.toPx()))
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

private fun DrawScope.drawHome(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: HomeLayoutGeometry,
    previewShape: CoreShape?,
) {
    val language = textMeasurer.language
    val landscape = layout.mode == HomeLayoutMode.COMPACT_LANDSCAPE
    val regular = layout.mode == HomeLayoutMode.REGULAR
    val firstCore = layout.bounds(HomeLayoutTarget.CORE_ORB)
    val titleX = if (landscape) firstCore.left * 0.48f else size.width * 0.5f
    val titleY = size.height * if (landscape) 0.24f else if (regular) 0.19f else 0.16f
    val titleWidth = if (landscape) firstCore.left - d(24f) else size.width - d(32f)
    drawLabel(textMeasurer, "KINETICKK", titleX, titleY, minOf(if (regular) 42f else 28f, titleWidth / density / (5.8f * textMeasurer.scale)),
        White, centered = true, weight = FontWeight.Bold, maxWidth = titleWidth)

    // A small moving tether demonstrates the game's motion without a decorative hero panel.
    val orbitCenter = Offset(titleX, titleY - d(if (regular) 36f else 28f))
    val orbitPoint = polar(orbitCenter, d(22f), renderTime * 0.6f)
    drawLine(Muted.copy(alpha = 0.4f), orbitCenter, orbitPoint, d(1f))
    drawCircle(Cyan, d(4f), orbitCenter)
    drawCircle(Magenta, d(4f), orbitPoint, style = Stroke(d(1.5f)))
    drawLabel(textMeasurer, language.text(if (regular) SessionText.HOME_INSTRUCTIONS else SessionText.COMPACT_INSTRUCTIONS), titleX,
        titleY + d((if (regular) 68f else 46f) * textMeasurer.scale), 9f, Muted, centered = true,
        maxWidth = titleWidth, maxLines = if (landscape) 2 else 1)

    if (!landscape) {
        drawInterfaceGlyph(InterfaceGlyph.DIAMOND, Offset(d(25f), d(26f)), d(7f), Muted)
        drawLabel(textMeasurer, formatCompact(engine.totalMatter, language), d(40f), d(18f), 11f, White)
        if (engine.rebirthLevel > 0) {
            drawInterfaceGlyph(InterfaceGlyph.CYCLE, Offset(size.width - d(55f), d(26f)), d(7f), Muted)
            drawLabel(textMeasurer, engine.rebirthLevel.toString(), size.width - d(36f), d(18f), 11f, White)
        }
    } else {
        drawLabel(textMeasurer, language.text(SessionText.MATTER, formatCompact(engine.totalMatter, language)),
            titleX, size.height * 0.64f, 9f, Muted, centered = true, maxWidth = titleWidth)
    }

    layout.actions.forEach { action ->
        action.target.coreShapeOrNull()?.let { shape -> drawCoreCard(engine, textMeasurer, shape, action.bounds) }
    }
    val start = layout.bounds(HomeLayoutTarget.START)
    drawRect(Cyan, start.topLeft, start.size)
    drawInterfaceGlyph(InterfaceGlyph.PLAY, Offset(start.left + d(26f), start.center.y), d(8f), SpaceBlack)
    drawLabel(textMeasurer, language.text(SessionText.START_RUN), start.center.x, start.center.y - d(8f),
        13f, SpaceBlack, centered = true, weight = FontWeight.Bold, maxWidth = start.width - d(72f))
    if (regular) drawLabel(textMeasurer, "↵", start.right - d(24f), start.center.y - d(8f), 12f, SpaceBlack, centered = true)

    listOf(
        Triple(HomeLayoutTarget.LAB, SessionText.LAB, InterfaceGlyph.FLASK),
        Triple(HomeLayoutTarget.ARMORY, SessionText.ARMORY, InterfaceGlyph.TARGET),
        Triple(HomeLayoutTarget.REBIRTH, SessionText.REBIRTH, InterfaceGlyph.CYCLE),
        Triple(HomeLayoutTarget.CODEX, SessionText.CODEX, InterfaceGlyph.BOOK),
        Triple(HomeLayoutTarget.SETTINGS, SessionText.SETTINGS, InterfaceGlyph.SLIDERS),
    ).forEach { (target, label, glyph) ->
        val bounds = layout.bounds(target)
        val accent = if (target == HomeLayoutTarget.REBIRTH && engine.canRebirth) Cyan else Muted
        drawInterfaceGlyph(glyph, Offset(bounds.center.x, bounds.center.y - d(8f)), d(9f), accent)
        drawLabel(textMeasurer, language.text(label), bounds.center.x, bounds.center.y + d(9f),
            if (landscape) 6.5f else 8f, accent, centered = true, maxWidth = bounds.width - d(6f))
    }
    if (regular) {
        previewShape?.let { shape ->
            val definition = engine.coreShape(shape)
            val description = if (engine.isCoreShapeUnlocked(shape)) definition.mechanicDescription else definition.unlockDescription
            drawLabel(textMeasurer, description.localizedContent(language), size.width * 0.5f,
                firstCore.bottom + d(14f), 10f, Muted, centered = true,
                maxWidth = min(d(520f), size.width - d(48f)), maxLines = if (size.height / density < 620f) 1 else 2)
        }
        drawLabel(textMeasurer, language.text(SessionText.COPYRIGHT), size.width * 0.5f, size.height - d(26f),
            6f, Muted, centered = true, maxWidth = size.width - d(24f))
        drawLabel(textMeasurer, language.text(SessionText.LICENSE_NOTICE), size.width * 0.5f, size.height - d(15f),
            6f, Muted, centered = true, maxWidth = size.width - d(24f))
    } else if (!landscape) {
        drawLabel(textMeasurer, language.text(SessionText.COMPACT_LICENSE), size.width * 0.5f, size.height - d(10f),
            5f, Muted, centered = true, maxWidth = size.width - d(16f))
    }
}

private fun DrawScope.drawCoreCard(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    shape: CoreShape,
    bounds: Rect,
) {
    val language = textMeasurer.language
    val selected = engine.coreShape == shape
    val unlocked = engine.isCoreShapeUnlocked(shape)
    val accent = if (selected) Cyan else if (unlocked) White else Muted.copy(alpha = 0.45f)
    if (selected) {
        drawRect(Cyan.copy(alpha = 0.07f), bounds.topLeft, bounds.size)
        drawLine(Cyan, Offset(bounds.left, bounds.bottom), Offset(bounds.right, bounds.bottom), d(2f))
    }
    val center = Offset(bounds.center.x, bounds.top + bounds.height * 0.32f)
    val radius = d(12f)
    when (shape) {
        CoreShape.ORB -> drawCircle(accent, radius, center)
        CoreShape.PRISM -> drawPolygon(center, radius * 1.15f, 4, (PI / 4).toFloat(), accent, Fill)
        CoreShape.SHARD -> drawPolygon(center, radius * 1.25f, 3, -(PI / 2).toFloat(), accent, Fill)
        CoreShape.RING -> drawCircle(accent, radius, center, style = Stroke(d(2f)))
        CoreShape.DIAMOND -> drawPolygon(center, radius * 1.15f, 4, 0f, accent, Fill)
        CoreShape.TESSERACT -> {
            drawPolygon(center, radius * 1.15f, 4, (PI / 4).toFloat(), accent, Stroke(d(1.5f)))
            drawPolygon(center, radius * 0.6f, 4, (PI / 4).toFloat(), accent, Stroke(d(1.5f)))
        }
    }
    if (!unlocked) drawInterfaceGlyph(InterfaceGlyph.LOCK, Offset(bounds.right - d(10f), bounds.top + d(10f)), d(4f), Muted)
    drawLabel(textMeasurer, engine.coreShape(shape).displayName.localizedContent(language), bounds.center.x,
        bounds.top + bounds.height * 0.57f, 8f, if (selected) White else Muted, centered = true, maxWidth = bounds.width - d(8f))
    if (!unlocked) drawLabel(textMeasurer, shortUnlockLabel(engine.coreShape(shape), language),
        bounds.center.x, bounds.bottom - d(11f), 5.5f, Muted, centered = true, maxWidth = bounds.width - d(8f))
}

private fun shortUnlockLabel(definition: kinetickk.ball.content.api.CoreShapeDefinition, language: AppLanguage): String =
    when (definition.unlockRequirement) {
        kinetickk.ball.content.api.CharacterUnlockRequirement.AVAILABLE -> language.text(SessionText.AVAILABLE)
        kinetickk.ball.content.api.CharacterUnlockRequirement.ELITE_KILLS -> language.text(SessionText.ELITES_TARGET, definition.unlockTarget)
        kinetickk.ball.content.api.CharacterUnlockRequirement.DASH_HITS -> language.text(SessionText.DASH_TARGET, definition.unlockTarget)
        kinetickk.ball.content.api.CharacterUnlockRequirement.COMPLETED_ORBITS -> language.text(SessionText.ORBIT_CLEAR)
        kinetickk.ball.content.api.CharacterUnlockRequirement.ARCHITECT_VICTORIES -> language.text(SessionText.ARCHITECT_WIN)
        kinetickk.ball.content.api.CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES -> language.text(SessionText.FORMS_TARGET, definition.unlockTarget)
    }
