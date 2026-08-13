// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

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
        val textMeasurer = remember(composeTextMeasurer, textScale) {
            CanvasTextMeasurer(
                delegate = composeTextMeasurer,
                scale = textScale,
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
                    contentDescription = "KINETICKK home"
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
    val description = action.target.homeContentDescription()
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
                    stateDescription = if (selected) "Selected" else if (enabled) "Available" else "Locked"
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
    HomeLayoutTarget.START -> "kinetickk.home.start"
    HomeLayoutTarget.LAB -> "kinetickk.home.lab"
    HomeLayoutTarget.ARMORY -> "kinetickk.home.armory"
    HomeLayoutTarget.REBIRTH -> "kinetickk.home.rebirth"
    HomeLayoutTarget.CODEX -> "kinetickk.home.codex"
    HomeLayoutTarget.SETTINGS -> "kinetickk.home.settings"
}

private fun HomeLayoutTarget.homeContentDescription(): String = when (this) {
    HomeLayoutTarget.CORE_ORB -> "Select Orb core"
    HomeLayoutTarget.CORE_PRISM -> "Select Prism core"
    HomeLayoutTarget.CORE_SHARD -> "Select Shard core"
    HomeLayoutTarget.START -> "Start run"
    HomeLayoutTarget.LAB -> "Open Kinetic Lab"
    HomeLayoutTarget.ARMORY -> "Open Armory"
    HomeLayoutTarget.REBIRTH -> "Open Rebirth"
    HomeLayoutTarget.CODEX -> "Open Codex"
    HomeLayoutTarget.SETTINGS -> "Open Settings"
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

private val MenuNavLabels = listOf("LAB [L]", "ARMORY [A]", "REBIRTH [B]", "CODEX [C]", "SETTINGS [S]")

private fun DrawScope.drawHome(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: HomeLayoutGeometry,
) {
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
    drawLabel(textMeasurer, "YOUR MOVEMENT IS THE WEAPON. YOUR CURSOR IS THE THREAT.", size.width * 0.5f, size.height * 0.37f, if (narrow) 9f else 12f, Muted, centered = true)
    drawLine(Violet.copy(alpha = 0.35f), Offset(size.width * 0.25f, size.height * 0.41f), Offset(size.width * 0.75f, size.height * 0.41f), 1f, pathEffect = dashEffect)
    drawLabel(textMeasurer, "LEAD THE CORE  //  BUILD MOMENTUM  //  NEVER TOUCH THE SINGULARITY", size.width * 0.5f, size.height * 0.45f, if (narrow) 8f else 10f, White, centered = true)
    drawLabel(textMeasurer, "SELECT CORE", size.width * 0.5f, size.height * 0.51f, 10f, Muted, centered = true)

    val centers = listOf(size.width * 0.5f - d(130f), size.width * 0.5f, size.width * 0.5f + d(130f))
    engine.coreShapes.forEachIndexed { index, definition ->
        val shape = definition.id
        val cardCenter = Offset(centers[index], size.height * 0.62f)
        val selected = engine.coreShape == shape
        val unlocked = engine.isCoreShapeUnlocked(shape)
        drawRect(if (selected) CyanSoft else Color(0x88101225), Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)))
        drawRect(if (selected) Cyan else DarkLine, Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)), style = Stroke(d(if (selected) 2f else 1f)))
        when (shape) {
            CoreShape.ORB -> drawCircle(if (unlocked) Cyan else Muted, d(13f), Offset(cardCenter.x, cardCenter.y - d(12f)))
            CoreShape.PRISM -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(17f), 4, (PI / 4).toFloat(), if (unlocked) Violet else Muted, Fill)
            CoreShape.SHARD -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(18f), 3, -(PI / 2).toFloat(), if (unlocked) Magenta else Muted, Fill)
        }
        drawLabel(textMeasurer, shape.name, cardCenter.x, cardCenter.y + d(17f), 9f, if (selected) White else Muted, centered = true, weight = FontWeight.Bold)
        if (!unlocked) drawLabel(textMeasurer, "${formatCompact(definition.unlockLifetimeMatter)} LIFETIME", cardCenter.x, cardCenter.y + d(34f), 7f, Orange, centered = true)
    }

    val buttonY = size.height * 0.78f
    drawRect(Cyan.copy(alpha = 0.12f), Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)))
    drawRect(Cyan, Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "START RUN", size.width * 0.5f, buttonY - d(12f), 15f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "CLICK / TAP / ENTER", size.width * 0.5f, buttonY + d(14f), 8f, Cyan, centered = true)
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
        drawLabel(textMeasurer, label, centerX, navY - d(5f), if (narrow) 6f else 8f, labelColor, centered = true, weight = FontWeight.Bold)
    }
    drawLabel(textMeasurer, "KINETIC MATTER ${formatCompact(engine.totalMatter)} // REBIRTH ${engine.rebirthLevel}", d(20f), d(20f), 9f, Acid)
    drawLabel(textMeasurer, "DISCOVERED ${engine.discoveredItemCount}/${engine.itemCount}  //  WEAPONS ${engine.unlockedWeaponCount}/${engine.weaponCount}", d(20f), d(39f), 7f, Muted)
    drawLabel(textMeasurer, "DIRECTIVE ${engine.rebirthProfile.directive.displayName.uppercase()}", d(20f), d(56f), 7f, Orange)
    drawLabel(textMeasurer, "KINETICKK 0.1.0 // COPYRIGHT (C) 2026 VLADISLAV TOMILOV // GNU GPL V3+", size.width * 0.5f, size.height - d(24f), if (narrow) 5f else 6f, Muted, centered = true)
    drawLabel(textMeasurer, "YOU MAY REDISTRIBUTE UNDER GPL V3+ // NO WARRANTY", size.width * 0.5f, size.height - d(14f), if (narrow) 4f else 5f, Muted, centered = true)
    drawLabel(textMeasurer, "SOURCE + LICENSE: GITHUB.COM/4WL2D/KINETICKK", size.width * 0.5f, size.height - d(6f), if (narrow) 4f else 5f, Muted, centered = true)
}

private fun DrawScope.drawCompactHome(
    engine: HomeUiModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: HomeLayoutGeometry,
) {
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
        if (landscape) 27f else 34f,
        Cyan,
        centered = true,
        weight = FontWeight.Bold,
    )
    if (landscape) {
        drawLabel(
            textMeasurer,
            "MOVE THE SINGULARITY. BUILD MOMENTUM.",
            titleCenter.x,
            titleCenter.y + d(32f),
            7f,
            Muted,
            centered = true,
            maxWidth = firstCore.left - d(28f),
            maxLines = 2,
        )
        drawLabel(textMeasurer, "MATTER ${formatCompact(engine.totalMatter)}", titleCenter.x, size.height * 0.56f, 8f, Acid, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, "REBIRTH ${engine.rebirthLevel}", titleCenter.x, size.height * 0.63f, 7f, Orange, centered = true)
        drawLabel(textMeasurer, "SELECT CORE", (firstCore.left + lastCore.right) * 0.5f, firstCore.top - d(25f), 9f, Muted, centered = true, weight = FontWeight.Bold)
    } else {
        drawLabel(textMeasurer, "YOUR TOUCH IS THE THREAT", size.width * 0.5f, size.height * 0.25f, 9f, Muted, centered = true)
        drawLabel(textMeasurer, "LEAD THE CORE // BUILD MOMENTUM", size.width * 0.5f, size.height * 0.31f, 8f, White, centered = true)
        drawLabel(textMeasurer, "MATTER ${formatCompact(engine.totalMatter)} // REBIRTH ${engine.rebirthLevel}", size.width * 0.5f, size.height * 0.39f, 8f, Acid, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, "SELECT CORE", size.width * 0.5f, firstCore.top - d(25f), 9f, Muted, centered = true, weight = FontWeight.Bold)
    }

    listOf(
        CoreShape.ORB to HomeLayoutTarget.CORE_ORB,
        CoreShape.PRISM to HomeLayoutTarget.CORE_PRISM,
        CoreShape.SHARD to HomeLayoutTarget.CORE_SHARD,
    ).forEach { (shape, target) ->
        drawCompactCoreCard(engine, textMeasurer, shape, layout.bounds(target))
    }
    drawCompactHomeButton(
        textMeasurer,
        layout.bounds(HomeLayoutTarget.START),
        "START RUN",
        Cyan,
        prominent = true,
    )
    listOf(
        HomeLayoutTarget.LAB to "LAB",
        HomeLayoutTarget.ARMORY to "ARMORY",
        HomeLayoutTarget.REBIRTH to "REBIRTH",
        HomeLayoutTarget.CODEX to "CODEX",
        HomeLayoutTarget.SETTINGS to "SETTINGS",
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
            "GPL-3.0+ // SOURCE: GITHUB.COM/4WL2D/KINETICKK",
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
    val selected = engine.coreShape == shape
    val unlocked = engine.isCoreShapeUnlocked(shape)
    val accent = when (shape) {
        CoreShape.ORB -> Cyan
        CoreShape.PRISM -> Violet
        CoreShape.SHARD -> Magenta
    }
    drawRect(if (selected) accent.copy(alpha = 0.17f) else Color(0x99101225), bounds.topLeft, bounds.size)
    drawRect(if (selected) accent else DarkLine, bounds.topLeft, bounds.size, style = Stroke(d(if (selected) 2f else 1f)))
    val center = Offset(bounds.center.x, bounds.top + bounds.height * 0.38f)
    when (shape) {
        CoreShape.ORB -> drawCircle(if (unlocked) accent else Muted, d(12f), center)
        CoreShape.PRISM -> drawPolygon(center, d(15f), 4, (PI / 4).toFloat(), if (unlocked) accent else Muted, Fill)
        CoreShape.SHARD -> drawPolygon(center, d(16f), 3, -(PI / 2).toFloat(), if (unlocked) accent else Muted, Fill)
    }
    drawLabel(textMeasurer, shape.name, bounds.center.x, bounds.top + bounds.height * 0.61f, 8f, if (selected) White else Muted, centered = true, weight = FontWeight.Bold)
    drawLabel(
        textMeasurer,
        if (unlocked) if (selected) "SELECTED" else "SELECT" else "LOCKED",
        bounds.center.x,
        bounds.bottom - d(19f),
        6f,
        if (unlocked) accent else Orange,
        centered = true,
        weight = FontWeight.Bold,
    )
}

private fun DrawScope.drawCompactHomeButton(
    textMeasurer: TextMeasurer,
    bounds: Rect,
    label: String,
    accent: Color,
    prominent: Boolean = false,
) {
    drawRect(accent.copy(alpha = if (prominent) 0.15f else 0.08f), bounds.topLeft, bounds.size)
    drawRect(accent, bounds.topLeft, bounds.size, style = Stroke(d(if (prominent) 1.8f else 1f)))
    drawLabel(
        textMeasurer,
        label,
        bounds.center.x,
        bounds.center.y - d(if (prominent) 9f else 6f),
        if (prominent) 13f else 7f,
        if (prominent) White else accent,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = bounds.width - d(8f),
    )
    if (prominent) {
        drawLabel(textMeasurer, "TAP TO ENTER", bounds.center.x, bounds.center.y + d(11f), 6f, Cyan, centered = true)
    }
}
