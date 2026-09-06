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
                drawHome(uiModel, textMeasurer, renderTimeSecondsValue, layout)
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
    onClick: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val description = action.target.homeContentDescription(language)
    Box(
        Modifier
            .placeInHomeBounds(action.bounds, density)
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
            .focusable(enabled = enabled),
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

private val MenuNavLabels = listOf(SessionText.NAV_LAB, SessionText.NAV_ARMORY, SessionText.NAV_REBIRTH, SessionText.NAV_CODEX, SessionText.NAV_SETTINGS)

private fun DrawScope.drawHome(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: HomeLayoutGeometry,
) {
    val language = textMeasurer.language
    if (layout.mode != HomeLayoutMode.REGULAR) {
        drawCompactHome(engine, textMeasurer, renderTime, layout)
        return
    }
    val narrow = size.width / density < 700f
    val titleSize = if (narrow) 37f else 62f
    val titleCenter = Offset(size.width * 0.5f, size.height * 0.245f)
    val orbitRadius = min(d(if (narrow) 118f else 175f), size.width * 0.29f)
    val orbitAngle = renderTime * 0.42f
    val orbitPoint = polar(titleCenter, orbitRadius, orbitAngle)
    val counterPoint = polar(titleCenter, orbitRadius * 0.72f, orbitAngle + PI.toFloat())
    drawCircle(Violet.copy(alpha = 0.055f), orbitRadius, titleCenter)
    drawCircle(Cyan.copy(alpha = 0.24f), orbitRadius, titleCenter, style = Stroke(1f, pathEffect = dashEffect))
    drawLine(Violet.copy(alpha = 0.16f), orbitPoint, counterPoint, 1f, pathEffect = dashEffect)
    drawCircle(Magenta.copy(alpha = 0.13f), d(24f), orbitPoint)
    drawCircle(SpaceBlack, d(8f), orbitPoint)
    drawCircle(Magenta, d(11f), orbitPoint, style = Stroke(d(1.5f)))
    drawCircle(Cyan.copy(alpha = 0.12f), d(22f), counterPoint)
    drawCircle(White, d(8f), counterPoint)
    drawLabel(textMeasurer, "KINETICKK", size.width * 0.5f, size.height * 0.205f, titleSize, Cyan, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, language.text(SessionText.HOME_SLOGAN), size.width * 0.5f, size.height * 0.37f, if (narrow) 9f else 12f, Muted, centered = true)
    drawLine(Violet.copy(alpha = 0.35f), Offset(size.width * 0.25f, size.height * 0.41f), Offset(size.width * 0.75f, size.height * 0.41f), 1f, pathEffect = dashEffect)
    drawLabel(textMeasurer, language.text(SessionText.HOME_INSTRUCTIONS), size.width * 0.5f, size.height * 0.45f, if (narrow) 8f else 10f, White, centered = true)
    drawLabel(textMeasurer, language.text(SessionText.SELECT_CORE), size.width * 0.5f, size.height * 0.51f, 10f, Muted, centered = true)

    layout.actions.forEach { action ->
        action.target.coreShapeOrNull()?.let { shape ->
            drawCompactCoreCard(engine, textMeasurer, shape, action.bounds)
        }
    }

    val buttonY = size.height * 0.78f
    drawRect(Cyan.copy(alpha = 0.12f), Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)))
    drawRect(Cyan, Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, language.text(SessionText.START_RUN), size.width * 0.5f, buttonY - d(12f), 15f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, language.text(SessionText.CLICK_TAP_ENTER), size.width * 0.5f, buttonY + d(14f), 8f, Cyan, centered = true)
    val navY = size.height * 0.9f
    val spacing = min(d(132f), size.width * 0.19f)
    val navStart = size.width * 0.5f - spacing * (MenuNavLabels.lastIndex * 0.5f)
    MenuNavLabels.forEachIndexed { index, label ->
        val centerX = navStart + spacing * index
        val accent = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> DarkLine
        }
        val labelColor = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> Muted
        }
        drawRect(Color(0x99101225), Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)))
        drawRect(accent, Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)), style = Stroke(d(1f)))
        drawLabel(textMeasurer, language.text(label), centerX, navY - d(5f), if (narrow) 6f else 8f, labelColor, centered = true, weight = FontWeight.Bold)
    }
    drawLabel(textMeasurer, language.text(SessionText.MATTER_REBIRTH, formatCompact(engine.totalMatter, language), engine.rebirthLevel), d(20f), d(20f), 9f, Acid)
    drawLabel(textMeasurer, language.text(SessionText.COLLECTION_COUNTS, engine.discoveredItemCount, engine.itemCount, engine.unlockedWeaponCount, engine.weaponCount), d(20f), d(39f), 7f, Muted)
    drawLabel(textMeasurer, language.text(SessionText.DIRECTIVE, engine.rebirthProfile.directive.displayName.localizedContent(language).uppercase()), d(20f), d(56f), 7f, Orange)
    drawLabel(textMeasurer, language.text(SessionText.COPYRIGHT), size.width * 0.5f, size.height - d(24f), if (narrow) 5f else 6f, Muted, centered = true)
    drawLabel(textMeasurer, language.text(SessionText.LICENSE_NOTICE), size.width * 0.5f, size.height - d(14f), if (narrow) 4f else 5f, Muted, centered = true)
    drawLabel(textMeasurer, language.text(SessionText.SOURCE_LICENSE), size.width * 0.5f, size.height - d(6f), if (narrow) 4f else 5f, Muted, centered = true)
}

private fun DrawScope.drawCompactHome(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: HomeLayoutGeometry,
) {
    val language = textMeasurer.language
    val landscape = layout.mode == HomeLayoutMode.COMPACT_LANDSCAPE
    val firstCore = layout.bounds(HomeLayoutTarget.CORE_ORB)
    val lastCore = layout.bounds(HomeLayoutTarget.CORE_SHARD)
    val titleCenter = if (landscape) {
        Offset(firstCore.left * 0.48f, size.height * 0.31f)
    } else {
        Offset(size.width * 0.5f, size.height * 0.15f)
    }
    val orbitRadius = d(if (landscape) 58f else 76f)
    val orbitPoint = polar(titleCenter, orbitRadius, renderTime * 0.42f)
    drawCircle(Violet.copy(alpha = 0.05f), orbitRadius, titleCenter)
    drawCircle(Cyan.copy(alpha = 0.24f), orbitRadius, titleCenter, style = Stroke(d(1f), pathEffect = dashEffect))
    drawCircle(Magenta.copy(alpha = 0.16f), d(10f), orbitPoint)
    drawCircle(Magenta, d(7f), orbitPoint, style = Stroke(d(1f)))
    drawLabel(
        textMeasurer,
        "KINETICKK",
        titleCenter.x,
        titleCenter.y - d(if (landscape) 18f else 14f),
        minOf(if (landscape) 27f else 34f,
            ((if (landscape) firstCore.left else size.width) / density - 24f) / (5.8f * textMeasurer.scale)),
        Cyan,
        centered = true,
        weight = FontWeight.Bold,
    )
    if (landscape) {
        drawLabel(
            textMeasurer,
            language.text(SessionText.COMPACT_SLOGAN),
            titleCenter.x,
            titleCenter.y + d(32f),
            7f,
            Muted,
            centered = true,
            maxWidth = firstCore.left - d(28f),
            maxLines = 2,
        )
        drawLabel(textMeasurer, language.text(SessionText.MATTER, formatCompact(engine.totalMatter, language)), titleCenter.x, size.height * 0.56f, 8f, Acid, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, language.text(SessionText.REBIRTH_LEVEL, engine.rebirthLevel), titleCenter.x, size.height * 0.63f, 7f, Orange, centered = true)
        drawLabel(textMeasurer, language.text(SessionText.SELECT_CORE), (firstCore.left + lastCore.right) * 0.5f, firstCore.top - d(25f), 9f, Muted, centered = true, weight = FontWeight.Bold)
    } else {
        drawLabel(textMeasurer, language.text(SessionText.TOUCH_SLOGAN), size.width * 0.5f, size.height * 0.25f, 9f, Muted, centered = true)
        drawLabel(textMeasurer, language.text(SessionText.COMPACT_INSTRUCTIONS), size.width * 0.5f, size.height * 0.31f, 8f, White, centered = true)
        drawLabel(textMeasurer, language.text(SessionText.COMPACT_MATTER_REBIRTH, formatCompact(engine.totalMatter, language), engine.rebirthLevel), size.width * 0.5f, firstCore.top - d(63f), 8f, Acid, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, language.text(SessionText.SELECT_CORE), size.width * 0.5f, firstCore.top - d(25f), 9f, Muted, centered = true, weight = FontWeight.Bold)
    }

    listOf(
        CoreShape.ORB to HomeLayoutTarget.CORE_ORB,
        CoreShape.PRISM to HomeLayoutTarget.CORE_PRISM,
        CoreShape.SHARD to HomeLayoutTarget.CORE_SHARD,
        CoreShape.RING to HomeLayoutTarget.CORE_RING,
        CoreShape.DIAMOND to HomeLayoutTarget.CORE_DIAMOND,
        CoreShape.TESSERACT to HomeLayoutTarget.CORE_TESSERACT,
    ).forEach { (shape, target) ->
        drawCompactCoreCard(engine, textMeasurer, shape, layout.bounds(target))
    }
    drawCompactHomeButton(
        textMeasurer,
        layout.bounds(HomeLayoutTarget.START),
        language.text(SessionText.START_RUN),
        Cyan,
        prominent = true,
    )
    listOf(
        HomeLayoutTarget.LAB to language.text(SessionText.LAB),
        HomeLayoutTarget.ARMORY to language.text(SessionText.ARMORY),
        HomeLayoutTarget.REBIRTH to language.text(SessionText.REBIRTH),
        HomeLayoutTarget.CODEX to language.text(SessionText.CODEX),
        HomeLayoutTarget.SETTINGS to language.text(SessionText.SETTINGS),
    ).forEach { (target, label) ->
        val accent = when (target) {
            HomeLayoutTarget.LAB -> Violet
            HomeLayoutTarget.REBIRTH -> if (engine.canRebirth) Acid else Orange
            else -> Muted
        }
        drawCompactHomeButton(textMeasurer, layout.bounds(target), label, accent)
    }
    if (!landscape) {
        drawLabel(
            textMeasurer,
            language.text(SessionText.COMPACT_LICENSE),
            size.width * 0.5f,
            size.height - d(8f),
            4.5f,
            Muted,
            centered = true,
            maxWidth = size.width - d(16f),
        )
    }
}

private fun DrawScope.drawCompactCoreCard(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    shape: CoreShape,
    bounds: Rect,
) {
    val language = textMeasurer.language
    val selected = engine.coreShape == shape
    val unlocked = engine.isCoreShapeUnlocked(shape)
    val accent = when (shape) {
        CoreShape.ORB -> Cyan
        CoreShape.PRISM -> Violet
        CoreShape.SHARD -> Magenta
        CoreShape.RING -> Acid
        CoreShape.DIAMOND -> Orange
        CoreShape.TESSERACT -> Violet
    }
    drawRect(if (selected) accent.copy(alpha = 0.17f) else Color(0x99101225), bounds.topLeft, bounds.size)
    drawRect(if (selected) accent else DarkLine, bounds.topLeft, bounds.size, style = Stroke(d(if (selected) 2f else 1f)))
    val center = Offset(bounds.center.x, bounds.top + bounds.height * 0.25f)
    when (shape) {
        CoreShape.ORB -> drawCircle(if (unlocked) accent else Muted, d(12f), center)
        CoreShape.PRISM -> drawPolygon(center, d(15f), 4, (PI / 4).toFloat(), if (unlocked) accent else Muted, Fill)
        CoreShape.SHARD -> drawPolygon(center, d(16f), 3, -(PI / 2).toFloat(), if (unlocked) accent else Muted, Fill)
        CoreShape.RING -> drawCircle(if (unlocked) accent else Muted, d(12f), center, style = Stroke(d(3f)))
        CoreShape.DIAMOND -> drawPolygon(center, d(15f), 4, 0f, if (unlocked) accent else Muted, Fill)
        CoreShape.TESSERACT -> {
            drawPolygon(center, d(15f), 4, (PI / 4).toFloat(), if (unlocked) accent else Muted, Stroke(d(1.5f)))
            drawPolygon(center, d(8f), 4, (PI / 4).toFloat(), if (unlocked) accent else Muted, Stroke(d(1.5f)))
        }
    }
    drawLabel(textMeasurer, engine.coreShape(shape).displayName.localizedContent(language).uppercase(), bounds.center.x, bounds.top + bounds.height * 0.53f, 7f, if (selected) White else Muted, centered = true, weight = FontWeight.Bold)
    drawLabel(
        textMeasurer,
        if (unlocked) if (selected) language.text(SessionText.SELECTED) else language.text(SessionText.SELECT) else shortUnlockLabel(engine.coreShape(shape), language),
        bounds.center.x,
        bounds.bottom - d(12f),
        5f,
        if (unlocked) accent else Orange,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = bounds.width - d(8f),
    )
}

private fun DrawScope.drawCompactHomeButton(
    textMeasurer: TextMeasurer,
    bounds: Rect,
    label: String,
    accent: Color,
    prominent: Boolean = false,
) {
    val language = textMeasurer.language
    drawRect(accent.copy(alpha = if (prominent) 0.15f else 0.08f), bounds.topLeft, bounds.size)
    drawRect(accent, bounds.topLeft, bounds.size, style = Stroke(d(if (prominent) 1.8f else 1f)))
    drawLabel(
        textMeasurer,
        label,
        bounds.center.x,
        if (prominent) bounds.top + d(7f) else bounds.center.y - d(6f),
        if (prominent) 13f else 7f,
        if (prominent) White else accent,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = bounds.width - d(8f),
    )
    if (prominent) {
        drawLabel(textMeasurer, language.text(SessionText.TAP_ENTER), bounds.center.x, bounds.bottom - d(13f), 6f, Cyan, centered = true)
    }
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
