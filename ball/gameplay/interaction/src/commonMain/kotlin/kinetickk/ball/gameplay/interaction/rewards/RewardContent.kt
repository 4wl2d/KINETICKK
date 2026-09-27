// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.canvas.choiceOverlayScrimColor
import kinetickk.ball.gameplay.interaction.canvas.drawItemIcon
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.DiscoveryBadge
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkDeal
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkCard
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkIconPlate
import kinetickk.foundation.design.drawKkLevelBadge
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkRingBurst
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkLevelBadgeSize
import kinetickk.foundation.design.kkLoop
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kotlin.math.min

/** Milliseconds of the reward entrance choreography (rings, badge slam, deal, footer). */
internal const val RewardEntranceMs = 1_600f

@Composable
internal fun RewardContent(
    engine: GameplayRenderModel,
    layout: ChoiceLayoutGeometry,
    renderTime: Float,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
    onBuild: () -> Unit = {},
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language) { engine.rewardPresentation(language) }
    RewardContent(presentation, layout, engine.screenWidth, engine.uiScale, engine.settings.textScale, renderTime, enabled, onSelect, onReroll, onBuild)
}

/**
 * Reward overlay. Cards, rows and slots accept a choice immediately on click, tap or keys 1–4
 * (owned by the host); hover and focus only move the lifted selection that Take acts on.
 */
@Composable
internal fun RewardContent(
    presentation: RewardPresentation,
    layout: ChoiceLayoutGeometry,
    screenWidth: Float,
    uiScale: Float,
    textScale: Float,
    renderTime: Float,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
    onBuild: () -> Unit = {},
) {
    val time = rememberUpdatedState(renderTime)
    val choiceKey = presentation.cards.map { it.choice }
    var selectedValue by remember(choiceKey) { mutableStateOf(presentation.defaultSelection()) }
    val entrance = remember(choiceKey) { Animatable(0f) }
    LaunchedEffect(choiceKey) {
        entrance.animateTo(RewardEntranceMs, tween(RewardEntranceMs.toInt(), easing = LinearEasing))
    }
    val scrim = choiceOverlayScrimColor(layout.mode)
    Box(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(scrim) }
            .testTag("kinetickk.gameplay.rewards"),
    ) {
        val state = RewardOverlayState(
            presentation = presentation,
            layout = layout,
            screenWidth = screenWidth,
            textScale = textScale,
            time = time,
            entrance = { entrance.value },
            selected = selectedValue,
            onSelected = { selectedValue = it },
            enabled = enabled,
            onSelect = onSelect,
            onReroll = onReroll,
            onBuild = onBuild,
        )
        when (presentation.kind) {
            RewardLayoutKind.CARDS -> RewardCardDeal(state)
            RewardLayoutKind.TOTEM -> RewardTotem(state)
            RewardLayoutKind.RELIC_BIND -> RewardRelicBind(state)
        }
    }
}

/** Everything the three reward layouts share: data, clocks, selection and the host callbacks. */
internal class RewardOverlayState(
    val presentation: RewardPresentation,
    val layout: ChoiceLayoutGeometry,
    val screenWidth: Float,
    val textScale: Float,
    val time: State<Float>,
    val entrance: () -> Float,
    val selected: Int?,
    val onSelected: (Int) -> Unit,
    val enabled: Boolean,
    val onSelect: (Int) -> Unit,
    val onReroll: () -> Unit,
    val onBuild: () -> Unit,
) {
    val mode: GameplayLayoutMode get() = layout.mode
    val canReroll: Boolean get() = layout.reroll != null
}

/**
 * The lifted card/row at first: the middle card of a deal (as dealt on the board), the first
 * totem offering, a meld target; replacing a relic starts with no slot so nothing is armed.
 */
internal fun RewardPresentation.defaultSelection(): Int? = when {
    cards.isEmpty() -> null
    kind == RewardLayoutKind.RELIC_BIND && cards.first().choice.relicAction == RelicChoiceAction.REPLACE -> null
    kind == RewardLayoutKind.CARDS -> (cards.size - 1) / 2
    else -> 0
}

/** Resting rotation of card [index] of [count] (−4°, 0°, +3° for three). */
internal fun rewardCardRestRotation(index: Int, count: Int): Float = when (count) {
    1 -> 0f
    2 -> if (index == 0) -3f else 2f
    3 -> floatArrayOf(-4f, 0f, 3f)[index.coerceIn(0, 2)]
    4 -> floatArrayOf(-4f, -1f, 1.5f, 3f)[index.coerceIn(0, 3)]
    else -> kkLerp(-4f, 3f, index / (count - 1f))
}

/** Linear deal progress of card [index]: 120 ms after the ring, 90 ms stagger, 600/750 ms. */
internal fun rewardDealProgress(entranceMs: Float, index: Int, rank: Int): Float =
    ((entranceMs - 120f - index * KkDeal.STAGGER_MS) / (if (rank >= 4) 750f else 600f)).coerceIn(0f, 1f)

@Composable
private fun RewardCardDeal(state: RewardOverlayState) {
    val presentation = state.presentation
    val layout = state.layout
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val density = LocalDensity.current.density
    val cardsBounds = layout.cards.fold<Rect, Rect?>(null) { acc, rect -> acc?.let { Rect(min(it.left, rect.left), min(it.top, rect.top), max(it.right, rect.right), max(it.bottom, rect.bottom)) } ?: rect }
        ?: Rect(0f, 0f, 1f, 1f)
    val origin = presentation.dealOrigin ?: Offset(cardsBounds.center.x, cardsBounds.bottom + 180f * density)
    // Backdrop: faint `you` halftone behind the deal and the double ring leaving the Core.
    Canvas(Modifier.fillMaxSize()) {
        val radius = min(400f * density, max(cardsBounds.width, cardsBounds.height) * 0.62f)
        val halo = Rect(cardsBounds.center.x - radius, cardsBounds.center.y - radius, cardsBounds.center.x + radius, cardsBounds.center.y + radius)
        drawKkRadialFade(halo) { drawKkHalftone(halo, roles.you.copy(alpha = 0.10f)) }
        val ms = state.entrance()
        drawKkRingBurst(origin, 70f * density, ms / 1_200f, roles.you, 3f)
        drawKkRingBurst(origin, 110f * density, (ms - 140f) / 1_200f, roles.you, 2f)
    }
    RewardHeader(state, Modifier.overlayOffset(layout.headerLeft, layout.titleY))
    presentation.relicMatrix?.let { matrix ->
        val preview = state.selected?.let(presentation.cards::getOrNull)?.relicPreview
        RewardRelicStrip(matrix, preview, state, Modifier.overlayBounds(relicStripBounds(layout, state.screenWidth, density)))
    }
    val count = presentation.cards.size
    presentation.cards.forEachIndexed { index, card ->
        val bounds = layout.cards.getOrNull(index)
        if (bounds != null) key(index, card.choice) {
            val selected = state.selected == index
            val lift by animateFloatAsState(if (selected) 1f else 0f, tween(220, easing = KkEase.Pull), label = "cardLift")
            val rest = rewardCardRestRotation(index, count)
            RewardCard(
                presentation = card,
                index = index,
                textScale = state.textScale,
                renderTime = 0f,
                enabled = state.enabled,
                modifier = Modifier.overlayBounds(bounds).graphicsLayer {
                    val p = rewardDealProgress(state.entrance(), index, card.rank)
                    val eased = KkEase.Pull.transform(p)
                    translationX = (origin.x - bounds.center.x) * (1f - eased)
                    translationY = (origin.y - bounds.center.y) * (1f - eased) - 22f * density * lift
                    rotationZ = kkLerp(-38f, rest, eased) - 1.5f * lift
                    val s = kkLerp(0.6f, 1f, eased)
                    scaleX = s
                    scaleY = s
                    alpha = KkDeal.alpha(p)
                },
                selected = selected,
                time = state.time,
                burst = { (state.entrance() - 120f - index * KkDeal.STAGGER_MS - 450f) / 1_000f },
                onPreview = { active -> if (active) state.onSelected(index) },
                onSelect = { state.onSelect(index) },
            )
        }
    }
    if (layout.mode == GameplayLayoutMode.REGULAR) {
        val selectedCard = state.selected?.let(presentation.cards::getOrNull)
        val stripTop = cardsBounds.bottom + 20f * density
        if (selectedCard != null && selectedCard.connections.isNotEmpty() && layout.take.top - stripTop >= 40f * density) {
            RewardConnections(
                selectedCard.connections, state.textScale,
                Modifier.overlayBounds(Rect(24f * density, stripTop, state.screenWidth - 24f * density, layout.take.top - 8f * density)),
            )
        }
    }
    RewardFooter(state, language.text(OverlayRedesignText.Take), state.selected?.let(presentation.cards::getOrNull)?.title)
}

/** Top-left level badge (level-up) or heading stamp, slammed in 100–600 ms. */
@Composable
private fun RewardHeader(state: RewardOverlayState, modifier: Modifier) {
    val presentation = state.presentation
    val language = LocalAppLanguage.current
    val regular = state.mode == GameplayLayoutMode.REGULAR
    val slam = { ((state.entrance() - 100f) / 500f).coerceIn(0f, 1f) }
    Box(modifier) {
        val level = presentation.level
        if (level != null) {
            RewardLevelBadge(level, language.text(OverlayRedesignText.LevelLabel), if (regular) 58f else 32f, if (regular) 20f else 13f,
                state.textScale, state.time, slam, Modifier.testTag("kinetickk.gameplay.rewards.level"))
        } else {
            OverlayStamp(presentation.heading, Modifier.testTag("kinetickk.gameplay.rewards.heading"), KkStampVariant.YOU,
                fontSize = if (regular) 22f else 16f, textScale = state.textScale, slam = slam)
        }
    }
}

@Composable
internal fun RewardLevelBadge(
    level: Int,
    label: String,
    numberSize: Float,
    labelSize: Float,
    textScale: Float,
    time: State<Float>,
    slam: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val size = remember(measurer, level, label, density, numberSize, labelSize) {
        kkLevelBadgeSize(measurer, level, density, label, labelSize, numberSize)
    }
    Box(
        modifier
            .size((size.width / density).dp + 8.dp, (size.height / density).dp + 8.dp)
            .semantics { text = AnnotatedString("$label $level") }
            .drawBehind {
                val p = slam()
                drawKkLevelBadge(measurer, Offset.Zero, level, label, time.value, if (p >= 1f) 1f else p, labelSize, numberSize)
            },
    )
}

/** Footer: Reroll with the rerolls left, Take on the lifted choice, Build opening the Codex. */
@Composable
internal fun RewardFooter(state: RewardOverlayState, takeLabel: String, selectedTitle: String?) {
    val language = LocalAppLanguage.current
    val layout = state.layout
    val compact = layout.mode != GameplayLayoutMode.REGULAR
    val size = if (compact) KkButtonSize.SM else KkButtonSize.SM
    val fade = Modifier.graphicsLayer {
        val p = ((state.entrance() - 500f) / 300f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - KkEase.Out.transform(p)) * 24f * density
    }
    layout.reroll?.let { bounds ->
        OverlayButton(
            label = language.text(OverlayRedesignText.Reroll),
            onClick = state.onReroll,
            modifier = Modifier.overlayBounds(bounds).then(fade).testTag("kinetickk.gameplay.reroll"),
            variant = KkButtonVariant.GHOST,
            size = size,
            enabled = state.enabled,
            icon = KkIcon.SYSTEM_REROLL,
            count = state.presentation.rerollsRemaining.toString(),
            textScale = state.textScale,
            contentDescription = language.text(OverlayRedesignText.Reroll) + " " + state.presentation.rerollsRemaining,
        )
    }
    val selected = state.selected
    OverlayButton(
        label = takeLabel,
        onClick = { selected?.let(state.onSelect) },
        modifier = Modifier.overlayBounds(layout.take).then(fade).testTag("kinetickk.gameplay.take"),
        variant = KkButtonVariant.PRIMARY,
        size = size,
        enabled = state.enabled && selected != null,
        textScale = state.textScale,
        stateDescription = selectedTitle,
    )
    OverlayButton(
        label = language.text(OverlayRedesignText.Build),
        onClick = state.onBuild,
        modifier = Modifier.overlayBounds(layout.build).then(fade).testTag("kinetickk.gameplay.reward-build"),
        variant = KkButtonVariant.GHOST,
        size = size,
        enabled = state.enabled,
        textScale = state.textScale,
    )
}

/** Card content dimensions derived from the card's own size (board 300×450 or phone 220×256). */
private class RewardCardSpec(val compact: Boolean, val u: Float, val font: Float) {
    fun dp(value: Float): Dp = (value * u).dp
    fun sp(value: Float): Float = value * font
}

private fun rewardCardSpec(widthDp: Float, heightDp: Float, textScale: Float): RewardCardSpec {
    val compact = rewardCardIsCompact(widthDp, heightDp) || heightDp < widthDp * 1.3f
    val u = if (compact) min(widthDp / 220f, heightDp / 256f) else min(widthDp / 300f, heightDp / 450f)
    val clamped = u.coerceIn(0.6f, 1.25f)
    return RewardCardSpec(compact, clamped, clamped.coerceIn(0.8f, 1.2f) * textScale)
}

@Composable
internal fun RewardCard(
    presentation: RewardCardPresentation,
    index: Int,
    textScale: Float,
    renderTime: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPreview: (Boolean) -> Unit = {},
    selected: Boolean = false,
    time: State<Float>? = null,
    burst: () -> Float = { -1f },
    onSelect: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredValue by interactionSource.collectIsHoveredAsState()
    val focusedValue by interactionSource.collectIsFocusedAsState()
    val pressedValue by interactionSource.collectIsPressedAsState()
    val highlighted = enabled && (hoveredValue || focusedValue || pressedValue)
    LaunchedEffect(highlighted) { onPreview(highlighted) }
    val clock = time ?: rememberUpdatedState(renderTime)
    val selection by animateFloatAsState(if (selected || highlighted) 1f else 0f, tween(220, easing = KkEase.Pull), label = "cardSelection")
    val accent = presentation.tone.color(roles, presentation.accent)
    val measurer = rememberKkCanvasMeasurer(textScale)
    val typography = rememberInterfaceTypography()
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier
            .testTag("kinetickk.gameplay.choice.${index + 1}")
            .semantics { stateDescription = listOfNotNull(presentation.bandStart, presentation.bandEnd).joinToString(", ") }
            .rewardDragGestures(enabled, scrollState)
            // The body consumes its own scroll first. Header drags and wheel input reach this
            // parent, moving the same text viewport once.
            .scrollable(scrollState, Orientation.Vertical, enabled = enabled, reverseDirection = true)
            // A single activation surface. The nested scrollable cancels this click on drag;
            // wheel events never synthesize activation.
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = language.text(GameplayText.SelectChoice, index + 1, presentation.title),
                onClick = onSelect,
            ),
    ) {
        val spec = rewardCardSpec(maxWidth.value, maxHeight.value, textScale)
        val bandHeight = if (spec.compact) 30f * spec.u else 42f * spec.u
        // Narrow cards move the band's trailing value into the body so the two labels never collide.
        val narrow = maxWidth.value < 200f
        val bandEnd = if (narrow) null else presentation.bandEnd
        Canvas(Modifier.fillMaxSize()) {
            drawKkCard(
                measurer, Rect(Offset.Zero, size), presentation.rank, clock.value, selection,
                presentation.bandStart, bandEnd, bandHeight, burst(), accent,
            )
        }
        val plateSize = spec.dp(if (spec.compact) 60f else 116f)
        val plateX = spec.dp(if (spec.compact) 28f else 40f)
        val plateY = spec.dp(if (spec.compact) 44f else 66f)
        Box(
            Modifier.offset(plateX, plateY).size(plateSize)
                .testTag("kinetickk.gameplay.choice.${index + 1}.${if (spec.compact) "compact" else "expanded"}")
                .drawBehind { drawRewardIcon(presentation, accent, spec.compact, clock.value) },
        )
        val bodyTop = spec.dp(if (spec.compact) 112f else 200f)
        val bodyBottom = spec.dp(if (spec.compact) 14f else 22f)
        val bodyHeight = (maxHeight - bodyTop - bodyBottom).coerceAtLeast(24.dp)
        Box(Modifier.offset(y = bodyTop).fillMaxWidth().height(bodyHeight)) {
            Column(
                Modifier.fillMaxSize().testTag("kinetickk.gameplay.choice.${index + 1}.text")
                    .verticalScroll(scrollState, enabled = enabled)
                    .padding(start = spec.dp(if (spec.compact) 24f else 34f), end = spec.dp(if (spec.compact) 22f else 32f)),
            ) {
                Column(Modifier.fillMaxWidth().heightIn(min = bodyHeight), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(spec.dp(if (spec.compact) 3f else 6f))) {
                        RewardCardName(presentation.title, presentation.rank, typography, spec.sp(if (spec.compact) 26f else 40f), clock)
                        if (narrow) presentation.bandEnd?.let { end ->
                            OverlayText(end, typography.monoStyle(spec.sp(10f), color = Kk.Mute), uppercase = true)
                        }
                        if (!spec.compact) presentation.family?.let { family ->
                            OverlayText(family, typography.monoStyle(spec.sp(11f), color = Kk.Mute), uppercase = true)
                        }
                        Spacer(Modifier.height(spec.dp(if (spec.compact) 4f else 18f)))
                        presentation.changes.forEachIndexed { line, change ->
                            RewardStatLine(change, line == 0, accent, spec.compact, spec.font, typography, roles, stacked = narrow)
                        }
                        presentation.descriptions.forEach { description ->
                            OverlayText(description, typography.bodyStyle(spec.sp(if (spec.compact) 13f else 14f), color = Kk.Mute))
                        }
                    }
                    val tags = if (spec.compact) emptyList() else presentation.tags
                    if (tags.isNotEmpty() || presentation.action != null) {
                        Row(
                            Modifier.padding(top = spec.dp(10f)),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            presentation.action?.let { action ->
                                OverlayTag(
                                    action.label,
                                    variant = when (action.tone) {
                                        RewardTone.THREAT -> KkTagVariant.THREAT
                                        RewardTone.YOU -> KkTagVariant.YOU
                                        else -> KkTagVariant.LINE
                                    },
                                    textScale = textScale,
                                )
                            }
                            tags.forEach { tag -> OverlayTag(tag, variant = KkTagVariant.LINE, textScale = textScale) }
                        }
                    }
                }
            }
            RewardScrollIndicator(scrollState, accent, Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight().width(3.dp).testTag("kinetickk.gameplay.choice.${index + 1}.scroll"))
        }
        if (presentation.isNewDiscovery) {
            DiscoveryBadge(
                language.text(GameplayText.NewDiscovery), textScale * (if (spec.compact) 0.8f else 1f),
                Modifier.offset(spec.dp(if (spec.compact) 104f else 172f), spec.dp(if (spec.compact) 54f else 88f))
                    .testTag("kinetickk.gameplay.choice.${index + 1}.new"),
            )
        }
    }
}

private fun DrawScope.drawRewardIcon(presentation: RewardCardPresentation, accent: Color, compact: Boolean, time: Float) {
    val bounds = Rect(Offset.Zero, size)
    val item = presentation.item
    if (item != null) {
        drawKkIconPlate(bounds, null, accent, if (compact) 8f else 12f)
        drawItemIcon(item, bounds.center, size.minDimension * 0.28f, accent, presentation.itemStack)
    } else {
        drawKkIconPlate(bounds, presentation.icon, accent, if (compact) 8f else 12f, if (compact) 32f * size.width / (60f * density) else 62f * size.width / (116f * density))
    }
}

/** Card name: cond 900 italic; epic adds a stacked glow, legendary the moving foil. */
@Composable
private fun RewardCardName(text: String, rank: Int, typography: InterfaceTypography, fontSize: Float, time: State<Float>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val style = rememberWordFitStyle(text, typography.condStyle(fontSize, color = Kk.Bone), constraints.maxWidth.toFloat())
        RewardCardNameText(text, rank, style, time)
    }
}

@Composable
private fun RewardCardNameText(text: String, rank: Int, style: androidx.compose.ui.text.TextStyle, time: State<Float>) {
    when (rank.coerceIn(1, 5)) {
        5 -> BasicText(
            text.uppercase(),
            Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
                val tile = max(size.width * 2.6f, 1f)
                val foil = Brush.linearGradient(
                    0.2f to Kk.RLegend, 0.42f to Kk.RLegendFoil, 0.64f to Kk.RLegend,
                    start = Offset.Zero, end = Offset(tile, 0f), tileMode = TileMode.Repeated,
                )
                onDrawWithContent {
                    drawContent()
                    val shift = -kkLoop(time.value, 2.4f) * tile
                    translate(shift, 0f) { drawRect(foil, Offset(-shift, 0f), size, blendMode = BlendMode.SrcIn) }
                }
            },
            style = style,
        )
        4 -> Box {
            // Name glow (text-shadow 0 0 18px epic @55 %) as rings of faint offset copies.
            BasicText(
                text.uppercase(),
                Modifier.drawWithCache {
                    val o1 = 4.dp.toPx()
                    onDrawWithContent {
                        for (ring in 1..2) for (step in 0 until 8) {
                            val a = step * 0.7853982f
                            translate(kotlin.math.cos(a) * o1 * ring, kotlin.math.sin(a) * o1 * ring) { this@onDrawWithContent.drawContent() }
                        }
                    }
                },
                style = style.copy(color = Kk.REpic.copy(alpha = 0.08f)),
            )
            BasicText(text.uppercase(), style = style)
        }
        else -> BasicText(text.uppercase(), style = style)
    }
}

/** A stat line: name (and condition) left; before value muted, after value in the accent. */
@Composable
private fun RewardStatLine(
    change: RewardStatPresentation,
    first: Boolean,
    accent: Color,
    compact: Boolean,
    font: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    stacked: Boolean = false,
) {
    val nameSize = (if (compact) 14f else if (first) 16f else 15f) * font
    val valueSize = (if (compact) (if (first) 16f else 12f) else if (first) 22f else 16f) * font
    val afterColor = when {
        !change.improved -> roles.threat
        change.before.isNotEmpty() || first -> accent
        else -> Kk.Bone
    }
    val label: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            change.source?.let { OverlayText(it, typography.monoStyle(9f * font, color = Kk.Mute2), uppercase = true) }
            OverlayText(change.name, typography.bodyStyle(nameSize, color = if (first) Kk.Bone else Kk.Mute))
            change.condition?.let { OverlayText(it, typography.monoStyle(9f * font, color = Kk.Mute), uppercase = true) }
        }
    }
    val values: @Composable () -> Unit = {
        if (change.before.isNotEmpty()) {
            OverlayText(change.before, typography.wideStyle(valueSize * 0.62f, tabular = true, color = Kk.Mute2),
                Modifier.padding(bottom = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
        OverlayText(change.after, typography.wideStyle(valueSize, tabular = true, color = afterColor))
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth()) {
            label(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) { values() }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            label(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            values()
        }
    }
}

/** What the lifted card touches, grouped by reason (Improves, Synergy, …) as tag rows. */
@Composable
private fun RewardConnections(connections: List<RewardConnection>, textScale: Float, modifier: Modifier) {
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val groups = connections.groupBy { it.reason }
    Row(
        modifier.testTag("kinetickk.gameplay.reward-connections"),
        horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        groups.forEach { (reason, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OverlayText(reason, typography.labelStyle(12f * textScale, color = Kk.Mute), uppercase = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(4).forEach { connection ->
                        OverlayTag(
                            connection.name,
                            Modifier.semantics {
                                contentDescription = language.text(OverlayRedesignText.Interactions) + ": " + reason + ", " + connection.name
                            },
                            background = Kk.Ink3,
                            foreground = connectionColor(connection),
                            textScale = textScale,
                        )
                    }
                }
            }
        }
    }
}

private fun connectionColor(connection: RewardConnection): Color = when {
    connection.relic != null -> Kk.aspect(connection.relic.aspect.ordinal)
    connection.item != null -> Kk.rarity(connection.item.rarity.rank)
    else -> Kk.Bone
}

@Composable
internal fun RewardScrollIndicator(scrollState: ScrollState, accent: Color, modifier: Modifier) {
    if (scrollState.maxValue <= 0) return
    val language = LocalAppLanguage.current
    Canvas(modifier.semantics { stateDescription = language.text(GameplayText.ScrollableRewards) }) {
        // The range can shrink to zero (text size change) before recomposition removes this bar.
        val max = scrollState.maxValue
        if (max <= 0) return@Canvas
        drawRect(accent.copy(alpha = 0.15f))
        val contentHeight = scrollState.viewportSize + max
        val fraction = if (contentHeight > 0) scrollState.viewportSize.toFloat() / contentHeight else 1f
        val thumbHeight = (size.height * fraction).coerceAtLeast(12.dp.toPx()).coerceAtMost(size.height)
        val top = (size.height - thumbHeight) * scrollState.value.coerceIn(0, max) / max
        drawRect(accent, Offset(0f, top), Size(size.width, thumbHeight))
    }
}

/** Native scrolling handles touch; mouse content dragging is adapted here because
 * desktop scrollable intentionally leaves mouse drags to its child. One clickable
 * remains responsible for activation, with every drag cancelling its release. */
internal fun Modifier.rewardDragGestures(enabled: Boolean, scrollState: ScrollState): Modifier = pointerInput(enabled, scrollState) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val press = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragged = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id }
                ?: break
            if ((change.position - press.position).getDistance() > viewConfiguration.touchSlop) dragged = true
            if (dragged && change.pressed && change.type == PointerType.Mouse) {
                val delta = change.position.y - change.previousPosition.y
                if (delta != 0f) {
                    scrollState.dispatchRawDelta(-delta)
                    change.consume()
                }
            }
            if (!change.pressed) {
                if (dragged) change.consume()
                break
            }
        }
    }
}

/** Top-right relic strip placement for relic offers (label row + diamonds with brackets). */
private fun relicStripBounds(layout: ChoiceLayoutGeometry, screenWidth: Float, density: Float): Rect {
    val regular = layout.mode == GameplayLayoutMode.REGULAR
    val width = (if (regular) 300f else 190f) * density
    val height = (if (regular) 118f else 40f) * density
    val right = screenWidth - (if (regular) 56f else 16f) * density
    val top = layout.titleY - (if (regular) 6f else 2f) * density
    return Rect(right - width, top, right, top + height)
}
