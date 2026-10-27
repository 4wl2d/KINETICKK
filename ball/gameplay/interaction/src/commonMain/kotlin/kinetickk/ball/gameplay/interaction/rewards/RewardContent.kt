// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.input.hudWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.canvas.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.drawActiveItemMark
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkPattern
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
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
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.KK_CARD_LABEL_CONTRAST
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkDeal
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkSlabKind
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkSpectrum
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkCardBack
import kinetickk.foundation.design.drawKkCardFrame
import kinetickk.foundation.design.drawKkCardFront
import kinetickk.foundation.design.kkLiftContrast
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkIconPlate
import kinetickk.foundation.design.KkApexState
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

/** Update the preview during input so the selected row/slot and its details draw in one frame. */
@Composable
internal fun Modifier.rewardPreviewInput(enabled: Boolean, onPreview: () -> Unit): Modifier {
    val onPreviewValue by rememberUpdatedState(onPreview)
    var touchPressedValue by remember(enabled) { mutableStateOf(false) }
    // Touch can focus a clickable on down; lifting it before native tap recognition moves the target.
    return onFocusChanged { if (enabled && it.isFocused && !touchPressedValue) onPreview() }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    touchPressedValue = event.changes.any { it.type == PointerType.Touch && it.pressed }
                    if (event.type == PointerEventType.Enter && event.changes.any { it.type != PointerType.Touch }) {
                        onPreviewValue()
                    }
                }
            }
        }
}

@Composable
internal fun RewardContent(
    engine: GameplayRenderModel,
    layout: ChoiceLayoutGeometry,
    renderTime: Float,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
    onBuild: () -> Unit = {},
    /** The player's input is touch: cards name a Z/X/C slot by number instead of its key. */
    touch: Boolean = false,
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language, touch) { engine.rewardPresentation(language, touch) }
    RewardContent(presentation, layout, engine.hudWidth, engine.uiScale, engine.settings.textScale, renderTime, enabled, onSelect, onReroll, onBuild)
}

/**
 * Reward overlay. Cards, rows and slots accept a choice immediately on click, tap or keys 1–4
 * (owned by the host); hover and focus only move the lifted selection that Take acts on.
 * [textScale] is the player's text-size setting: UI text renders at the board size at the
 * default (see [overlayUiTextScale]); the level numeral ignores it.
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
            textScale = overlayUiTextScale(textScale),
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
    /** UI text factor ([overlayUiTextScale] of the setting): 1 renders board sizes. */
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
    // One body size for the deal: the smallest size step any card needs to show its text whole.
    val bodyFits = remember(presentation.cards, layout.cards) { mutableStateMapOf<Int, Float>() }
    val bodyFit = bodyFits.values.minOrNull() ?: 1f
    presentation.cards.forEachIndexed { index, card ->
        val bounds = layout.cards.getOrNull(index)
        if (bounds != null) key(index, card.choice) {
            val selected = state.selected == index
            val lift = if (selected) 1f else 0f
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
                bodyFit = bodyFit,
                onBodyFit = { fit -> if (bodyFits[index] != fit) bodyFits[index] = fit },
                phoneDeal = layout.compactCardContent,
                onPreview = { state.onSelected(index) },
                onSelect = { state.onSelect(index) },
            )
        }
    }
    if (layout.mode == GameplayLayoutMode.REGULAR) {
        val selectedCard = state.selected?.let(presentation.cards::getOrNull)
        val stripTop = cardsBounds.bottom + 20f * density
        // Only what the card meets in the build (owned items, bound relics): every card improves the
        // Core or the weapon, so naming them on each card would say nothing.
        val connections = selectedCard?.connections.orEmpty().filter { it.core == null && it.weapon == null }
        if (connections.isNotEmpty() && layout.take.top - stripTop >= 40f * density) {
            RewardConnections(
                connections, state.textScale,
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
            // The level numeral is display type: the text-size setting does not apply.
            RewardLevelBadge(level, language.text(OverlayRedesignText.LevelLabel), if (regular) 58f else 32f, if (regular) 20f else 13f,
                1f, state.time, slam, Modifier.testTag("kinetickk.gameplay.rewards.level"), presentation.levelCap, presentation.apex)
        } else {
            // An Heirloom offer is gold: the hue the Rebirth system carries.
            OverlayStamp(presentation.heading, Modifier.testTag("kinetickk.gameplay.rewards.heading"), KkStampVariant.YOU,
                color = if (presentation.heirloom) LocalKkRolePalette.current.gold.base else Color.Unspecified,
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
    maxLevel: Int = Int.MAX_VALUE,
    apex: KkApexState = KkApexState.NONE,
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
                drawKkLevelBadge(measurer, Offset.Zero, level, label, time.value, if (p >= 1f) 1f else p, labelSize, numberSize,
                    maxLevel = maxLevel, apex = apex)
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

    /** Board dp of the card layout: icon plate, the chips beside it, body and footer. */
    val bandHeight: Float get() = if (compact) 30f else 42f
    val plateSize: Float get() = if (compact) 46f else 88f
    val plateX: Float get() = if (compact) 22f else 34f
    val plateY: Float get() = if (compact) 38f else 56f
    val chipsGap: Float get() = if (compact) 10f else 16f
    val bodyTop: Float get() = if (compact) 92f else 162f
    val bodyBottom: Float get() = if (compact) 10f else 18f
    val footer: Float get() = if (compact) 24f else 34f
    /** Body line height: phones set their paragraphs a little tighter. */
    val lineHeight: Float get() = if (compact) 1.3f else 1.4f
    val start: Float get() = if (compact) 22f else 34f
    val end: Float get() = if (compact) 24f else 34f
    /** Chips keep a readable size: small cards (phones, a scaled-down board card) take the compact chip. */
    val chips: RewardChipStyle get() = if (compact || u < 0.85f) RewardChipCompact else RewardChipRegular
}

/**
 * Whether a card takes the phone layout: small cards, every card of a phone deal ([phoneDeal], the
 * layout's compact card content: the four-card landscape deal is 195 × 254) and squat ones (up to
 * 1.3 times as tall as wide). A board card on a short window (300 × 419 at 1280 × 720) keeps the
 * board layout, scaled.
 */
internal fun rewardCardUsesCompactLayout(widthDp: Float, heightDp: Float, phoneDeal: Boolean = false): Boolean =
    rewardCardIsCompact(widthDp, heightDp) || phoneDeal || heightDp < widthDp * 1.3f

private fun rewardCardSpec(widthDp: Float, heightDp: Float, textScale: Float, phoneDeal: Boolean): RewardCardSpec {
    val compact = rewardCardUsesCompactLayout(widthDp, heightDp, phoneDeal)
    val u = if (compact) min(widthDp / 220f, heightDp / 256f) else min(widthDp / 300f, heightDp / 450f)
    val clamped = u.coerceIn(0.6f, 1.25f)
    return RewardCardSpec(compact, clamped, clamped.coerceIn(0.8f, 1.2f) * textScale)
}

/** Forecast chips a card shows at most: three on the board, two on phones. */
internal fun rewardForecastLimit(compact: Boolean): Int = if (compact) 2 else 3

@Composable
internal fun RewardCard(
    presentation: RewardCardPresentation,
    index: Int,
    textScale: Float,
    renderTime: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit = {},
    selected: Boolean = false,
    time: State<Float>? = null,
    burst: () -> Float = { -1f },
    bodyFit: Float = 1f,
    onBodyFit: (Float) -> Unit = {},
    /** The card is dealt by a phone layout (its compact card content), whatever its shape. */
    phoneDeal: Boolean = false,
    /** The card's text plates, when the caller keeps them (they measure where the text ends). */
    facePlates: RewardFacePlates? = null,
    onSelect: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredValue by interactionSource.collectIsHoveredAsState()
    val focusedValue by interactionSource.collectIsFocusedAsState()
    val pressedValue by interactionSource.collectIsPressedAsState()
    val highlighted = enabled && (hoveredValue || focusedValue || pressedValue)
    // Keep touch previews behind clickable's press/drag arbitration; moving on raw down can cancel a tap.
    LaunchedEffect(pressedValue) { if (enabled && pressedValue) onPreview() }
    val clock = time ?: rememberUpdatedState(renderTime)
    val selection = if (selected || highlighted) 1f else 0f
    val accent = presentation.accentColor(roles)
    val iconColor = presentation.iconColor(roles)
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
            .rewardPreviewInput(enabled, onPreview)
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
        val spec = rewardCardSpec(maxWidth.value, maxHeight.value, textScale, phoneDeal)
        // Narrow cards move the band's trailing value into the body so the two labels never collide.
        val narrow = rewardCardIsNarrow(maxWidth.value)
        // The card's own look: the frame by rarity (its material), the face by type (hue and pattern by
        // element, ornament by tag, watermark and seed, the bespoke art of an epic or legendary item).
        val look = remember(presentation, roles) { rewardCardLook(presentation, roles) }
        val artCache = remember { RewardArtCache() }
        // Everything drawn behind the text (pattern, watermark, art, sparks, scanlines, sheen) stays
        // behind it: the face canvas lays a soft-edged patch of the card face under each text row, so
        // effects fade out under the text instead of being cut at a hard edge (a cut spark would read
        // as an arrowhead).
        val ownPlates = remember { RewardFacePlates() }
        val plates = if (look.needsPlates) facePlates ?: ownPlates else null
        val paths = remember { KkPathCache(1) }
        // The rarity band is card chrome of a fixed height: its labels follow the text size up to
        // the default and no further, so the two labels never collide or get cut.
        val bandMeasurer = rememberKkCanvasMeasurer(min(textScale, 1f))
        val facePaths = remember { KkPathCache(4) }
        Canvas(Modifier.fillMaxSize().onPlaced { plates?.card = it }) {
            // The face and its plates use the current selection together.
            val face = if (selection > 0.5f) Kk.Ink3 else Kk.Ink2
            // The plates are measured first: the art lays itself out in the room the text leaves.
            plates?.prepare(this, face, scrollState.value.toFloat())
            val dealt = burst()
            drawRewardCardFace(bandMeasurer, presentation, accent, clock.value, selection, dealt, facePaths, phoneDeal, look, artCache,
                plates?.textBottom ?: Float.NaN, plates?.footerTop ?: Float.NaN, frame = false)
            if (plates != null) {
                drawRewardFacePlates(plates, paths.slab(0, RewardFaceSpecs.bounds(size.width, size.height), RewardCardCutDp.dp.toPx(), KkSlabKind.SLAB))
            }
            // The rarity frame last, over the plates, so it stays whole round the card.
            drawKkCardFrame(RewardFaceSpecs.bounds(size.width, size.height), clock.value, look.style, dealt)
        }
        val plateSize = spec.dp(spec.plateSize)
        Box(
            Modifier.offset(spec.dp(spec.plateX), spec.dp(spec.plateY)).size(plateSize)
                .testTag("kinetickk.gameplay.choice.${index + 1}.${if (spec.compact) "compact" else "expanded"}")
                .drawBehind { drawRewardIcon(presentation, accent, iconColor, look.secondHue, look.plateTint, spec.compact, clock.value) },
        )
        // Identity beside the icon: a relic's keys, then an active's slot and lane, Risk, Reborn, Heirloom and tag chips.
        val chipsLeft = spec.dp(spec.plateX + spec.plateSize + spec.chipsGap)
        val chipsWidth = (maxWidth - chipsLeft - spec.dp(spec.end)).coerceAtLeast(0.dp)
        val keys = presentation.keys
        // Phone relic cards keep the keys and the Heirloom mark; the attuned tags read on the board (and in the Codex).
        val headerChips = if (spec.compact) presentation.chips.filter { it.kind != RewardChipKind.ATTUNED } else presentation.chips
        if (headerChips.isNotEmpty() || keys.isNotEmpty()) {
            Column(
                Modifier.offset(chipsLeft, spec.dp(spec.plateY)).width(chipsWidth).height(plateSize + spec.dp(6f))
                    .facePlate(plates, "chips").testTag("kinetickk.gameplay.choice.${index + 1}.chips"),
                // Centered against the icon plate, so a card with few chips leaves no empty column under them.
                verticalArrangement = Arrangement.spacedBy(spec.dp(if (spec.compact) 4f else 6f), Alignment.CenterVertically),
            ) {
                if (keys.isNotEmpty()) RewardKeyWords(keys, typography, spec.sp(if (spec.compact) 9f else 10.5f), roles)
                val rows = if (keys.isEmpty()) (if (spec.compact) 2 else 3) else (if (spec.compact) 1 else 2)
                RewardChipFlow(headerChips, spec.chips, textScale, Modifier.fillMaxWidth(), maxRows = rows,
                    gapDp = if (spec.compact) 4f else 5f)
            }
        }
        val forecast = presentation.forecast.take(rewardForecastLimit(spec.compact))
        val footerTags = if (presentation.item == null && presentation.chips.isEmpty() && !spec.compact) presentation.tags else emptyList()
        val hasFooter = footerTags.isNotEmpty() || presentation.action != null || forecast.isNotEmpty()
        val footerHeight = if (hasFooter) max(spec.dp(spec.footer).value, spec.chips.heightDp + 2f).dp else 0.dp
        val bodyTop = spec.dp(spec.bodyTop)
        val bodyBottom = spec.dp(spec.bodyBottom)
        val bodyHeight = (maxHeight - bodyTop - bodyBottom - footerHeight).coerceAtLeast(24.dp)
        val start = spec.dp(spec.start)
        // The body takes the largest size step at which its content fits, measured before it is
        // shown; only text longer than the last step scrolls. Cards dealt together share the
        // smallest step any of them needs ([bodyFit]).
        val end = spec.dp(spec.end)
        val gap = spec.dp(if (spec.compact) 3f else 6f)
        // Phone cards keep a card's mechanics (rules, a relic's upside); its numbers return when it has none.
        val mechanics = presentation.rules.any { it.kind != RuleLineKind.COST } || presentation.relic != null
        val changes = if (spec.compact && mechanics) emptyList() else presentation.changes
        val descriptions = if (spec.compact && changes.isNotEmpty()) emptyList() else presentation.descriptions
        val body: @Composable (Float, Modifier, RewardFacePlates?) -> Unit = { fit, bodyModifier, rowPlates ->
            val font = spec.font * fit
            Column(bodyModifier.padding(start = start, end = end), verticalArrangement = Arrangement.spacedBy(gap * fit)) {
                RewardCardName(presentation.title, presentation.rank, typography, (if (spec.compact) 26f else 38f) * font, clock,
                    color = if (presentation.heirloom) roles.gold.base else Kk.Bone)
                if (narrow) presentation.bandEnd?.let { bandEnd ->
                    OverlayText(bandEnd, typography.monoStyle(10f * font, color = Kk.Mute), uppercase = true)
                }
                if (!spec.compact) presentation.family?.let { family ->
                    OverlayFitText(family, typography.monoStyle(11f * font, color = Kk.Mute), uppercase = true, maxLines = 2)
                }
                Spacer(Modifier.height(spec.dp(if (spec.compact) 2f else 8f) * fit))
                // An item's values take its element hue (the preset's volt for Kinetic): rarity is the card's material, not a value color.
                val valueColor = rewardTextTone(if (presentation.item != null) iconColor else accent)
                changes.forEachIndexed { line, change ->
                    RewardStatLine(change, line == 0, valueColor, spec.compact, font, typography, roles, Modifier.facePlate(rowPlates, "stat$line"))
                }
                val bodySize = (if (spec.compact) 13f else 14.5f) * font
                if (presentation.rules.isNotEmpty()) {
                    // Trigger bars and numbers take the line's element, else the card's own type hue.
                    RewardRuleRows(presentation.rules, iconColor, bodySize, (if (spec.compact) 9f else 10.5f) * font, spec.lineHeight, typography, roles,
                        rowPlates, gap * fit * 0.4f,
                        costLabel = if (spec.compact) null else language.text(OverlayRedesignText.Cost),
                        costLabelStyle = typography.monoStyle((if (spec.compact) 8.5f else 9.5f) * font, weight = FontWeight.Bold, color = roles.threat))
                } else {
                    descriptions.forEachIndexed { line, description ->
                        val color = if (presentation.decline) Kk.Mute else Kk.Bone2
                        val number = if (presentation.relic != null) Kk.Bone else color
                        OverlayRichText(
                            rewardRichText(description, number, if (line == 0) presentation.values else emptyList(), rewardTextTone(accent)),
                            typography.bodyStyle(bodySize, lineHeightEm = spec.lineHeight, color = color),
                            Modifier.fillMaxWidth().facePlate(rowPlates, "description$line"),
                        )
                    }
                }
            }
        }
        // Costs are never hidden: a relic's liability in a threat-striped block, laid out under the body
        // as its tail. It fits with the body; when the body still overflows at its smallest size, the
        // cost keeps its whole height at the bottom and only the text above it scrolls.
        val costs: (@Composable (Float, Boolean) -> Unit)? = if (presentation.rules.isEmpty() && presentation.liabilities.isNotEmpty()) {
            { fit, probing ->
                val font = spec.font * fit
                val rowPlates = if (probing) null else plates
                Column(Modifier.fillMaxWidth().padding(start = start, end = end)
                    .then(if (probing) Modifier else Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.cost")),
                    verticalArrangement = Arrangement.spacedBy(gap * fit)) {
                    presentation.liabilities.forEachIndexed { line, liability ->
                        RewardCostBlock(
                            liability,
                            if (spec.compact) null else language.text(OverlayRedesignText.Cost),
                            typography.bodyStyle((if (spec.compact) 12.5f else 13.5f) * font, lineHeightEm = spec.lineHeight, color = roles.threat),
                            typography.monoStyle((if (spec.compact) 8.5f else 9.5f) * font, weight = FontWeight.Bold, color = roles.threat),
                            roles.threat,
                            Modifier.fillMaxWidth().facePlate(rowPlates, "liability$line"),
                        )
                    }
                }
            }
        } else {
            null
        }
        val probe = rememberTextMeasurer(cacheSize = 16)
        // Int.MAX_VALUE until the body is first measured.
        val overflows = scrollState.maxValue in 1 until Int.MAX_VALUE
        Box(Modifier.offset(y = bodyTop).fillMaxWidth().height(bodyHeight).onPlaced { plates?.viewport = it }) {
            val steps = remember(textScale) { rewardCardFitSteps(textScale) }
            RewardCardFitLayout(steps, bodyFit, probe, onBodyFit, Modifier.fillMaxSize(), probeContent = { fit -> body(fit, Modifier, null) },
                tail = costs, tailGap = { fit -> (gap * fit).roundToPx() }) { fit ->
                body(
                    fit,
                    Modifier.fillMaxSize().testTag("kinetickk.gameplay.choice.${index + 1}.text")
                        // Longer text fades out at the bottom of the text column itself: nothing is
                        // painted over the card, so its rarity glow, border and effects stay whole.
                        .then(if (overflows) Modifier.rewardScrollFade(scrollState, spec.dp(18f)) else Modifier)
                        .verticalScroll(scrollState, enabled = enabled),
                    plates,
                )
            }
            RewardScrollIndicator(scrollState, iconColor, Modifier.align(Alignment.CenterEnd)
                .padding(end = spec.dp(if (spec.compact) 14f else 18f)).fillMaxHeight().width(3.dp)
                .testTag("kinetickk.gameplay.choice.${index + 1}.scroll"))
        }
        if (hasFooter) {
            // The outcome tag and the forecast stay pinned at the bottom of the card, outside the scrolling body.
            Row(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = start - spec.dp(4f), end = end - spec.dp(6f), bottom = bodyBottom)
                    .height(footerHeight)
                    .facePlate(plates, "footer")
                    .testTag("kinetickk.gameplay.choice.${index + 1}.footer"),
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
                        heightDp = spec.chips.heightDp,
                        fontSize = if (spec.compact) 11.5f else 13f,
                        textScale = textScale,
                    )
                }
                footerTags.forEach { tag -> OverlayTag(tag, variant = KkTagVariant.LINE, textScale = textScale) }
                if (forecast.isNotEmpty()) {
                    RewardChipFlow(forecast, spec.chips, textScale,
                        Modifier.weight(1f).testTag("kinetickk.gameplay.choice.${index + 1}.forecast"), gapDp = if (spec.compact) 4f else 5f)
                }
            }
        }
        if (presentation.isNewDiscovery) {
            // On the board the stamp sits on the icon plate's lower corner, clear of the chips and the
            // name at any text size (its bottom edge is fixed; a larger stamp grows up over the icon).
            // An active item's plate keeps its lower band for the rank pips: there the stamp sits on the
            // plate's upper corner, below the band, and a larger stamp grows down over the glyph.
            // Phone cards have no room beside their small icon: the stamp rides on the top edge, its
            // bottom fixed just inside the frame and above the band's labels and pips, so a larger
            // stamp grows up and away from the card.
            if (spec.compact) {
                val overlap = spec.dp(RewardNewStampOverlapDp)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = spec.dp(14f)).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(placeable.width, 0) { placeable.place(0, overlap.roundToPx() - placeable.height) }
                    },
                ) {
                    DiscoveryBadge(
                        language.text(GameplayText.NewDiscovery), min(textScale * 0.7f, RewardNewStampMaxScale),
                        Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.new"),
                    )
                }
            } else {
                Box(
                    Modifier.offset(spec.dp(spec.plateX - 8f), spec.dp(spec.plateY)).height(spec.dp(spec.plateSize + 6f)),
                    contentAlignment = if (presentation.item?.active != null) Alignment.TopStart else Alignment.BottomStart,
                ) {
                    DiscoveryBadge(
                        language.text(GameplayText.NewDiscovery), textScale * 0.9f,
                        Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.new"),
                    )
                }
            }
        }
    }
}

/** How far (board dp) a phone card's New stamp reaches into the card: the frame, never the band labels. */
internal const val RewardNewStampOverlapDp = 3f

/** The largest phone New stamp (DiscoveryBadge scale), so it stays a sticker on the edge at any text size. */
private const val RewardNewStampMaxScale = 0.85f

/** A relic's keys as mono words: shared with a bound relic in bone, opposed by one underlined in the threat role. */
@Composable
private fun RewardKeyWords(keys: List<RewardRelicKey>, typography: InterfaceTypography, fontSize: Float, roles: KkRolePalette) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        keys.forEach { key ->
            val color = when (key.state) {
                RewardKeyState.OPPOSED -> roles.threat
                RewardKeyState.SHARED -> Kk.Bone
                RewardKeyState.NEUTRAL -> Kk.Mute
            }
            val style = typography.monoStyle(fontSize, weight = FontWeight.Bold, color = color).let {
                if (key.state == RewardKeyState.OPPOSED) it.copy(textDecoration = TextDecoration.Underline) else it
            }
            OverlayFitText(key.label, style, uppercase = true, maxLines = 1, minScale = 0.6f)
        }
    }
}

/**
 * An item's mechanics: each TRIGGER line (mono caps behind a bar in its hue, [RewardRuleLine.hue])
 * heads its EFFECT line (body, numbers in that hue); rules read like effects. The COST lines close
 * the list in one block behind a threat stripe, under [costLabel] ("Cost") when there is room. A
 * suppressed line is struck through in the threat role.
 */
@Composable
private fun RewardRuleRows(
    lines: List<RewardRuleLine>,
    typeHue: Color,
    bodySize: Float,
    triggerSize: Float,
    lineHeight: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    plates: RewardFacePlates?,
    innerGap: Dp,
    costLabel: String?,
    costLabelStyle: TextStyle,
) {
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val next = lines.getOrNull(index + 1)
        if (line.kind == RuleLineKind.COST) {
            index++
            continue
        }
        if (line.kind == RuleLineKind.TRIGGER && next != null && next.kind == RuleLineKind.EFFECT) {
            Column(Modifier.fillMaxWidth().facePlate(plates, "rule$index"), verticalArrangement = Arrangement.spacedBy(innerGap)) {
                RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
                RewardRuleLineText(next, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
            }
            index += 2
        } else {
            Box(Modifier.fillMaxWidth().facePlate(plates, "rule$index")) {
                RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles)
            }
            index++
        }
    }
    val costs = lines.filter { it.kind == RuleLineKind.COST }
    if (costs.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().facePlate(plates, "cost").testTag(RewardCostTag)
                .drawBehind { drawRect(roles.threat, size = Size(3.dp.toPx(), size.height)) }.padding(start = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            costLabel?.let { OverlayText(it, costLabelStyle, uppercase = true) }
            costs.forEach { line -> RewardRuleLineText(line, typeHue, bodySize, triggerSize, lineHeight, typography, roles) }
        }
    }
}

/**
 * The hue of a rule line's trigger bar and numbers on a card of type hue [typeHue]: the element the
 * line names, else the card's own type. A line naming no element on an element card stays in that
 * element's hue: Kinetic's volt appears only on a Kinetic card.
 */
internal fun RewardRuleLine.hue(roles: KkRolePalette, typeHue: Color): Color = element?.overlayColor(roles) ?: typeHue

@Composable
private fun RewardRuleLineText(
    line: RewardRuleLine,
    typeHue: Color,
    bodySize: Float,
    triggerSize: Float,
    lineHeight: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
) {
    val hue = line.hue(roles, typeHue)
    val struck = line.suppressed
    when (line.kind) {
        RuleLineKind.TRIGGER -> {
            val color = if (struck) roles.threat.copy(alpha = 0.8f) else Kk.Bone2
            val style = typography.monoStyle(triggerSize, weight = FontWeight.Bold, color = color)
                .let { if (struck) it.copy(textDecoration = TextDecoration.LineThrough) else it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size((triggerSize * 0.55f).dp, (triggerSize * 1.25f).dp)) {
                    // A sheared bar in the clause's element hue heads the trigger.
                    drawKkSheared(Rect(Offset.Zero, size), if (struck) roles.threat else hue)
                }
                Spacer(Modifier.width(7.dp))
                OverlayFitText(line.text, style, Modifier.weight(1f), uppercase = true, maxLines = 3, minScale = 0.8f)
            }
        }
        RuleLineKind.COST -> {
            // The cost block around it carries the stripe and the label.
            val style = typography.bodyStyle(bodySize * 0.95f, lineHeightEm = lineHeight, color = roles.threat)
                .let { if (struck) it.copy(textDecoration = TextDecoration.LineThrough) else it }
            OverlayRichText(rewardRichText(line.text, roles.threat), style, Modifier.fillMaxWidth())
        }
        else -> {
            if (struck) {
                val style = typography.bodyStyle(bodySize, lineHeightEm = lineHeight, color = roles.threat.copy(alpha = 0.8f))
                    .copy(textDecoration = TextDecoration.LineThrough)
                OverlayRichText(AnnotatedString(rewardKeepUnits(line.text)), style)
            } else {
                OverlayRichText(rewardRichText(line.text, rewardTextTone(hue)), typography.bodyStyle(bodySize, lineHeightEm = lineHeight, color = Kk.Bone2))
            }
        }
    }
}

/**
 * [color] as card text: an element hue (the numbers of a card's lines) lifted toward bone until it
 * reads at 4.5:1 on the card face, selected or not (a dark Mono or Ion hue otherwise sinks into
 * the ink). Marks and patterns keep the element hue itself.
 */
internal fun rewardTextTone(color: Color): Color = kkLiftContrast(color, Kk.Ink3, KK_CARD_LABEL_CONTRAST)

/** Test tag of a card's cost block (its threat stripe is drawn on purpose beside the text). */
internal const val RewardCostTag = "kinetickk.gameplay.reward-cost"

/** A mandatory cost: a threat stripe on the left edge, the [label] ("Cost") and the text. */
@Composable
private fun RewardCostBlock(text: String, label: String?, style: TextStyle, labelStyle: TextStyle, threat: Color, modifier: Modifier) {
    Column(
        modifier.testTag(RewardCostTag).drawBehind { drawRect(threat, size = Size(3.dp.toPx(), size.height)) }.padding(start = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        label?.let { OverlayText(it, labelStyle, uppercase = true) }
        OverlayRichText(rewardRichText(text, threat), style)
    }
}

/**
 * The icon plate, the type channel's anchor: rimmed in the type hue (an item's second element takes
 * the rim's right and bottom edges, [secondHue]) over the element's pattern (the Kinetic slash for an
 * item or relic without one), the icon in the type hue. A specified [tint] (a type hue that tells
 * little apart by colour, [RewardCardLook.plateTint]) tints the plate in the hue and strengthens its
 * pattern, so the type still reads off the metal frame.
 */
private fun DrawScope.drawRewardIcon(
    presentation: RewardCardPresentation,
    accent: Color,
    iconColor: Color,
    secondHue: Color,
    tint: Color,
    compact: Boolean,
    time: Float,
) {
    val bounds = Rect(Offset.Zero, size)
    val item = presentation.item
    val relic = presentation.relic
    val policy = presentation.relicPolicy
    val cut = if (compact) 8f else 12f
    val cue = tint.isSpecified
    val plate = if (cue) tint else Kk.Ink3
    val pattern = plateCuePattern(cue)
    when {
        item != null -> {
            drawKkIconPlate(bounds, null, iconColor, cut, rim = iconColor, rimSecond = if (secondHue != iconColor) secondHue else Color.Unspecified,
                fill = plate)
            // The plate carries the type's pattern: identity without hue (color vision).
            drawRadialPattern(bounds, item.elements.firstOrNull()?.let { elementFill(it.ordinal) } ?: KineticFill, iconColor, pattern)
            val effect = item.active
            if (effect != null) drawActiveItemMark(effect, Offset(bounds.center.x, bounds.top + bounds.minDimension * 0.5f), bounds.minDimension,
                iconColor, presentation.itemStack ?: 0, item.maxStacks, plate)
            else drawItemIcon(item, bounds.center, size.minDimension * 0.34f, iconColor, presentation.itemStack)
        }
        relic != null && policy != null -> {
            drawKkIconPlate(bounds, null, accent, cut, rim = accent, fill = plate)
            val element = relic.aspect.element
            when {
                element != null -> drawRadialPattern(bounds, elementFill(element.ordinal), accent, pattern)
                relic.aspect == RelicAspect.KINETIC -> drawRadialPattern(bounds, KineticFill, accent, pattern)
            }
            // The band names the rank; the rune keeps the plate's hue under the color-vision preset.
            drawRelicIcon(relic, policy, bounds.center, size.minDimension * 0.34f, time = time, accent = accent)
        }
        presentation.decline -> {
            drawKkIconPlate(bounds, null, accent, cut)
            drawKkHatch(bounds.deflate(4f * density))
            drawKkIcon(presentation.icon ?: KkIcon.SYSTEM_MATTER, bounds.center, size.minDimension * 0.5f, Kk.Mute)
        }
        else -> drawKkIconPlate(bounds, presentation.icon, accent, cut, size.minDimension / density * 0.55f, rim = accent)
    }
}

/** [kind] in [color] at [alpha] over the plate, fading out toward its edges. */
private fun DrawScope.drawRadialPattern(bounds: Rect, kind: KkFillKind, color: Color, alpha: Float) {
    drawKkRadialFade(bounds) { drawKkPattern(bounds, kind, color, alpha) }
}
/**
 * Card name: cond 900 italic in bone; epic adds a prismatic glow (the rarity's spectrum, never the
 * type hue); legendary the moving gold foil.
 */
@Composable
private fun RewardCardName(
    text: String,
    rank: Int,
    typography: InterfaceTypography,
    fontSize: Float,
    time: State<Float>,
    color: Color = Kk.Bone,
) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val shown = text.uppercase()
        val base = typography.condStyle(fontSize, color = color)
        val width = constraints.maxWidth.toFloat()
        val style = remember(shown, base, width) { fitTextStyle(measurer, shown, base, width, 2, 0.75f) }
        RewardCardNameText(text, rank, style, time)
    }
}

@Composable
private fun RewardCardNameText(text: String, rank: Int, style: TextStyle, time: State<Float>) {
    LocalOverlayTextProbe.current?.let { probe ->
        OverlayProbeText(probe, text.uppercase(), style, Modifier)
        return
    }
    when (rank.coerceIn(1, 5)) {
        5 -> {
            // The legendary foil in the preset's gold (the deep gold where its volt reads as the bright one).
            val gold = LocalKkRolePalette.current.gold
            BasicText(
                text.uppercase(),
                Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
                    val tile = max(size.width * 2.6f, 1f)
                    val foil = Brush.linearGradient(
                        0.2f to gold.base, 0.42f to gold.light, 0.64f to gold.base,
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
        }
        4 -> {
            // The prismatic glow: two rings of faint copies in the spectrum round the name (text-shadow
            // 0 0 18px @55 %), their reach scaled with the type, so a phone-sized name glows instead of ghosting.
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val reach = with(LocalDensity.current) { style.fontSize.toPx() } * RewardNameGlowReach
            val glow = LocalKkRolePalette.current.spectrum
            BasicText(
                text.uppercase(),
                Modifier.drawBehind {
                    val laid = layout ?: return@drawBehind
                    val spectrum = RewardNameSpectrum.brush(laid.size.width.toFloat(), glow)
                    for (ring in 1..2) for (index in 0 until 8) {
                        val angle = index * 0.7853982f
                        drawText(laid, spectrum, Offset(kotlin.math.cos(angle) * reach * ring, kotlin.math.sin(angle) * reach * ring),
                            alpha = if (ring == 1) 0.13f else 0.065f)
                    }
                },
                style = style,
                onTextLayout = { layout = it },
            )
        }
        else -> BasicText(text.uppercase(), style = style)
    }
}

/**
 * A stat line: name (and condition) left; before value muted, after value in the accent. When
 * the name does not fit beside the values it takes its own line and the values sit under it.
 */
@Composable
private fun RewardStatLine(
    change: RewardStatPresentation,
    first: Boolean,
    accent: Color,
    compact: Boolean,
    font: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    modifier: Modifier,
) {
    val nameSize = (if (compact) 14f else if (first) 16f else 15f) * font
    val valueSize = (if (compact) (if (first) 16f else 12f) else if (first) 22f else 16f) * font
    // A stat a bound relic voids is struck through in the threat role, name and values alike.
    val struck = change.suppressed
    val afterColor = when {
        struck -> roles.threat.copy(alpha = 0.8f)
        !change.improved -> roles.threat
        change.before.isNotEmpty() || first -> accent
        else -> Kk.Bone
    }
    val strike: (TextStyle) -> TextStyle = { if (struck) it.copy(textDecoration = TextDecoration.LineThrough) else it }
    val nameStyle = strike(typography.bodyStyle(nameSize, color = if (struck) roles.threat.copy(alpha = 0.8f) else if (first) Kk.Bone else Kk.Mute))
    val beforeStyle = strike(typography.wideStyle(valueSize * 0.62f, tabular = true, color = Kk.Mute2))
    val afterStyle = strike(typography.wideStyle(valueSize, tabular = true, color = afterColor))
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 8f * density
        val stacked = remember(change, nameStyle, afterStyle, constraints.maxWidth) {
            val values = measurer.measure(change.after, afterStyle).size.width +
                (if (change.before.isNotEmpty()) measurer.measure(change.before, beforeStyle).size.width + gap else 0f)
            val name = measurer.measure(change.name, nameStyle, softWrap = false).size.width
            name + gap + values > constraints.maxWidth
        }
        val label: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                change.source?.let { OverlayFitText(it, typography.monoStyle(9f * font, color = Kk.Mute2), uppercase = true, maxLines = 1) }
                OverlayFitText(change.name, nameStyle, maxLines = if (stacked) 2 else 1)
                change.condition?.let { OverlayFitText(it, typography.monoStyle(9f * font, color = Kk.Mute), uppercase = true, maxLines = 2) }
            }
        }
        val values: @Composable () -> Unit = {
            if (change.before.isNotEmpty()) {
                OverlayText(change.before, beforeStyle, Modifier.padding(bottom = 2.dp))
                Spacer(Modifier.width(8.dp))
            }
            OverlayText(change.after, afterStyle)
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
}

/** Reach of each ring of the epic name glow as a share of the name's font size. */
private const val RewardNameGlowReach = 0.085f

/** The preset's spectrum across an epic name (one brush per name width and spectrum, a few at once). */
private object RewardNameSpectrum {
    private val widths = FloatArray(4) { -1f }
    private val spectra = arrayOfNulls<KkSpectrum>(4)
    private val brushes = arrayOfNulls<Brush>(4)
    private var next = 0

    fun brush(width: Float, spectrum: KkSpectrum): Brush {
        for (index in 0 until 4) if (widths[index] == width && spectra[index] === spectrum) return brushes[index]!!
        val created = Brush.horizontalGradient(spectrum.stops, startX = 0f, endX = max(width, 1f))
        widths[next] = width
        spectra[next] = spectrum
        brushes[next] = created
        next = (next + 1) % 4
        return created
    }
}

/** Card width (dp) below which the band's trailing value moves into the body. */
internal fun rewardCardIsNarrow(widthDp: Float): Boolean = widthDp < 200f

/**
 * The reward card at the canvas size, in its two channels: the rarity frame (the material's border,
 * band, echo, glow and effects) and the type face every card text sits on (the hue's pattern,
 * ornament, watermark, type pips and art). [look] (memoized per presentation and palette) carries the
 * style and the art; [artCache] keeps the art's geometry per card. Allocation-free for a steady card.
 */
internal fun DrawScope.drawRewardCardFace(
    measurer: CanvasTextMeasurer,
    presentation: RewardCardPresentation,
    @Suppress("UNUSED_PARAMETER") accent: Color,
    time: Float,
    selection: Float,
    burst: Float,
    paths: KkPathCache = RewardFacePaths,
    phoneDeal: Boolean = false,
    look: RewardCardLook = rewardCardLook(presentation, measurer.roles),
    artCache: RewardArtCache = RewardFaceArtCache,
    /** The bottom of the card's text in px (the face plates measure it); NaN for the default layout. */
    textBottom: Float = Float.NaN,
    /** The top of the card's footer in px, NaN when it has none (or for the default layout). */
    footerTop: Float = Float.NaN,
    /** Draw the rarity frame too; a caller that lays plates over the face draws it after them ([drawKkCardFrame]). */
    frame: Boolean = true,
) {
    val spec = RewardFaceSpecs.of(size.width / density, size.height / density, phoneDeal)
    val bandHeight = spec.bandHeight * spec.u
    val bandEnd = if (rewardCardIsNarrow(size.width / density)) null else presentation.bandEnd
    val bounds = RewardFaceSpecs.bounds(size.width, size.height)
    val style = look.style
    drawKkCardBack(bounds, time, selection, style, textBottom)
    look.art?.let { art -> drawRewardCardArt(art, rewardArtFrame(artCache, look, measurer.roles, spec, textBottom, footerTop), time) }
    // The text column's left edge and the text's bottom: ornaments and facets keep clear of the text.
    drawKkCardFront(measurer, bounds, time, selection, presentation.bandStart, bandEnd, bandHeight, burst, style,
        textLeft = spec.start * spec.u * density, textBottom = textBottom)
    if (presentation.elements.isNotEmpty() || presentation.item != null) drawBandElementPips(measurer, presentation, bandEnd, bandHeight, spec.compact)
    when {
        presentation.reborn || presentation.heirloom -> drawGoldRim(paths, bounds, presentation.rebirthTier, spec.compact, measurer.roles.gold.base)
        presentation.decline -> {
            // A calm card: no effects, a dashed inner outline in the line color.
            val inset = 7f * density
            val outline = paths.slab(1, inset, bandHeight * density + inset, size.width - inset, size.height - inset, (RewardCardCutDp - 6f) * density)
            drawPath(outline, Kk.Line2, style = RewardCardStrokes.dashed(1.5f * density, density))
        }
    }
    if (frame) drawKkCardFrame(bounds, time, style, burst)
}

/**
 * The art frame of [look] at this card's size and text, rebuilt only when the size, look, palette or
 * the text's bottom ([textBottom], whole px) changes. The clear region starts below the text (never
 * above the default share of the face) and keeps at least [RewardArtMinClear] board dp; a card whose
 * text leaves less than that is crowded, and its art moves to the room that is left.
 */
private fun DrawScope.rewardArtFrame(
    cache: RewardArtCache,
    look: RewardCardLook,
    roles: KkRolePalette,
    spec: RewardCardSpec,
    textBottom: Float,
    footerTop: Float,
): RewardArtFrame {
    val text = if (textBottom.isNaN()) Float.NaN else kotlin.math.round(textBottom)
    val footer = if (footerTop.isNaN()) Float.NaN else kotlin.math.round(footerTop)
    cache.current(look, roles, size.width, size.height, density, spec.compact, text, footer)?.let { return it }
    val unit = spec.u * density
    val w = size.width
    val h = size.height
    // Above the footer when the card has one; a measured card without one lends the art its footer room.
    val bottom = when {
        !footer.isNaN() -> footer - 4f * unit
        !text.isNaN() -> h - (spec.bodyBottom + 4f) * unit
        else -> h - (spec.bodyBottom + spec.footer) * unit
    }
    val defaultTop = h * (if (spec.compact) 0.62f else 0.56f)
    val floor = if (text.isNaN()) defaultTop else text + RewardArtTextGap * unit
    val minClear = RewardArtMinClear * unit * (if (spec.compact) 0.7f else 1f)
    val clearTop = min(max(defaultTop, floor), bottom - minClear)
    return cache.store(
        RewardArtFrame(
            bounds = Rect(0f, 0f, w, h),
            face = Rect(0f, spec.bandHeight * unit, w, h),
            clear = Rect(spec.start * unit, clearTop, w - spec.end * unit, max(bottom, clearTop + 24f * unit)),
            textFloor = if (text.isNaN()) clearTop else floor,
            crowded = floor > bottom - minClear,
            textBottom = text,
            footerTop = footer,
            icon = Rect(spec.plateX * unit, spec.plateY * unit, (spec.plateX + spec.plateSize) * unit, (spec.plateY + spec.plateSize) * unit),
            hue = look.hue,
            secondHue = look.secondHue,
            roles = roles,
            density = density,
            seed = look.style.seed,
            compact = spec.compact,
            unit = unit,
            cache = cache,
            look = look,
        ),
    )
}

/** The gap (board dp) the art keeps below the last text row. */
private const val RewardArtTextGap = 8f

/** The least height (board dp) of the art's clear region. */
private const val RewardArtMinClear = 72f

/** Card specs and face bounds of the face canvases drawn this frame, so a steady face allocates none. */
private object RewardFaceSpecs {
    private const val CAPACITY = 8
    private val keys = FloatArray(CAPACITY * 3) { Float.NaN }
    private val specs = arrayOfNulls<RewardCardSpec>(CAPACITY)
    private var next = 0
    private val boundKeys = FloatArray(CAPACITY * 2) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(CAPACITY)
    private var nextRect = 0

    fun of(widthDp: Float, heightDp: Float, phoneDeal: Boolean): RewardCardSpec {
        val deal = if (phoneDeal) 1f else 0f
        for (index in 0 until CAPACITY) {
            val base = index * 3
            if (keys[base] == widthDp && keys[base + 1] == heightDp && keys[base + 2] == deal) return specs[index]!!
        }
        val spec = rewardCardSpec(widthDp, heightDp, 1f, phoneDeal)
        val base = next * 3
        keys[base] = widthDp
        keys[base + 1] = heightDp
        keys[base + 2] = deal
        specs[next] = spec
        next = (next + 1) % CAPACITY
        return spec
    }

    fun bounds(width: Float, height: Float): Rect {
        for (index in 0 until CAPACITY) {
            if (boundKeys[index * 2] == width && boundKeys[index * 2 + 1] == height) return rects[index]!!
        }
        val rect = Rect(0f, 0f, width, height)
        boundKeys[nextRect * 2] = width
        boundKeys[nextRect * 2 + 1] = height
        rects[nextRect] = rect
        nextRect = (nextRect + 1) % CAPACITY
        return rect
    }
}

/** Art cache for faces drawn outside a card composable (tests, previews). Draw-thread confined. */
private val RewardFaceArtCache = RewardArtCache()

/**
 * Type pips on the band's trailing end, before the stack count: a diamond per element with its
 * glyph, or one volt Kinetic pip for an item without an element (Kinetic is a type like the others).
 */
private fun DrawScope.drawBandElementPips(
    measurer: CanvasTextMeasurer,
    presentation: RewardCardPresentation,
    bandEnd: String?,
    bandHeight: Float,
    compact: Boolean,
) {
    val band = bandHeight * density
    val endWidth = bandEnd?.let { measureKkText(measurer, it, measurer.typography.monoStyle(9f, weight = FontWeight.Bold), uppercase = true).size.width.toFloat() } ?: 0f
    val half = (if (compact) 7f else 9f) * density
    val pitch = half * 1.7f
    var cx = size.width - 22f * density - endWidth - (if (bandEnd != null) 12f * density else 0f) - half
    val cy = band * 0.5f
    val count = max(presentation.elements.size, 1)
    for (index in count - 1 downTo 0) {
        val element = presentation.elements.getOrNull(index)
        val hue = element?.overlayColor(measurer.roles) ?: measurer.roles.kinetic
        rotate(45f, Offset(cx, cy)) {
            val side = half * 1.414f
            drawRect(Kk.Ink, Offset(cx - side * 0.5f - density, cy - side * 0.5f - density), Size(side + 2f * density, side + 2f * density))
            drawRect(hue, Offset(cx - side * 0.5f, cy - side * 0.5f), Size(side, side))
        }
        drawKkIcon(element?.overlayIcon() ?: KkIcon.ASPECT_KINETIC, Offset(cx, cy), half * 1.15f, Kk.Ink, strokeWidth = 2.6f)
        cx -= pitch
    }
}

/**
 * Gold rim of a Reborn or Heirloom card, inside the rarity frame (which keeps the card's rarity),
 * with one ring mark per Rebirth tier along the bottom edge.
 */
private fun DrawScope.drawGoldRim(paths: KkPathCache, bounds: Rect, tier: Int, compact: Boolean, gold: Color) {
    val cut = RewardCardCutDp * density
    val k = if (compact) 0.75f else 1f
    val first = 6.5f * k * density
    val outer = paths.slab(2, bounds.left + first, bounds.top + first, bounds.right - first, bounds.bottom - first, cut - first * 0.1f)
    drawPath(outer, gold, style = kkStroke(2f * density))
    val inset = first + 4f * k * density
    val inner = paths.slab(3, bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset, cut - inset * 0.1f)
    drawPath(inner, gold, alpha = 0.35f, style = kkStroke(density))
    val marks = tier.coerceIn(0, 10)
    if (marks == 0) return
    val radius = (if (compact) 2.4f else 3f) * density
    val pitch = radius * 3.2f
    val y = bounds.bottom - first - 2f * k * density
    var x = bounds.center.x - (marks - 1) * pitch * 0.5f
    repeat(marks) {
        drawCircle(Kk.Ink2, radius + 1.5f * density, Offset(x, y))
        drawCircle(gold, radius, Offset(x, y), style = kkStroke(1.3f * density))
        x += pitch
    }
}

/** Shared face paths for faces drawn outside a card composable (tests, previews). Draw-thread confined. */
private val RewardFacePaths = KkPathCache(4)

private object RewardCardStrokes {
    private var width = -1f
    private var stroke: Stroke? = null

    fun dashed(strokeWidth: Float, density: Float): Stroke {
        stroke?.takeIf { width == strokeWidth }?.let { return it }
        width = strokeWidth
        return Stroke(strokeWidth, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 5f * density)))
            .also { stroke = it }
    }
}

/** The card face cut (`.card` clip-path, 22 dp). */
private const val RewardCardCutDp = 22f

/**
 * Width (dp) over which the face plates fade out around the text: about three times the largest
 * card spark (7 dp), so a spark dims as a whole diamond and no spark, halftone dot or sheen band
 * ever ends at an edge.
 */
internal const val RewardPlateFadeDp = 20f

/** How much of the card effects the face plates hide under the text (the rest shows through). */
internal const val RewardPlateOpacity = 0.75f

/** Composites the plates as one layer, so overlapping rings never add up to a darker band. */
private val RewardPlateLayer by lazy { Paint().apply { alpha = RewardPlateOpacity } }

/**
 * The text rows of one card: the face canvas draws a soft plate under each. The plates' geometry and
 * edge brushes are built once per layout ([prepare]) and only drawn per frame, so an animating card
 * allocates nothing for them.
 */
internal class RewardFacePlates {
    var card: LayoutCoordinates? = null
    var viewport: LayoutCoordinates? = null
    val rows = LinkedHashMap<String, LayoutCoordinates>()
    private val positions = HashMap<String, Offset>()

    /** Bumped when a row moves (layout, scroll), so the face canvas redraws its plates. */
    var version by mutableIntStateOf(0)

    /** The bottom (card px) of the card's text, as if unscrolled and cut at the viewport; NaN until measured. */
    var textBottom = Float.NaN
        private set

    /** The top (card px) of the card's footer, NaN when it has none. */
    var footerTop = Float.NaN
        private set

    private var builtVersion = -1
    private var builtFace = Color.Unspecified
    private var builtWidth = -1f
    private var builtHeight = -1f
    private var count = 0
    private var cores = FloatArray(4 * 8)
    private var edges = arrayOfNulls<Brush>(8 * 8)
    private var built = arrayOfNulls<LayoutCoordinates>(8)

    fun place(key: String, coordinates: LayoutCoordinates) {
        rows[key] = coordinates
        val position = coordinates.positionInRoot()
        if (positions.put(key, position) != position) version++
    }

    /**
     * Measures the rows against the card and the text viewport and builds the plates in [face], when
     * a row moved, the canvas size or the face colour changed, or a row left the layout. [scroll] is
     * the body's scroll offset in px (the text bottom ignores it, so scrolling never moves the art).
     */
    fun prepare(scope: DrawScope, face: Color, scroll: Float) {
        val stamp = version
        val width = scope.size.width
        val height = scope.size.height
        if (stamp == builtVersion && face == builtFace && width == builtWidth && height == builtHeight && allAttached()) return
        val card = card?.takeIf { it.isAttached } ?: return
        val viewport = viewport?.takeIf { it.isAttached }?.let { card.localBoundingBoxOf(it, clipBounds = false) } ?: return
        val measured = ArrayList<Triple<String, Rect, LayoutCoordinates?>>(rows.size)
        val iterator = rows.entries.iterator()
        while (iterator.hasNext()) {
            val (key, row) = iterator.next()
            if (!row.isAttached) {
                iterator.remove()
                positions.remove(key)
                continue
            }
            measured += Triple(key, card.localBoundingBoxOf(row, clipBounds = false), row)
        }
        build(scope, face, scroll, viewport, measured, stamp)
    }

    /**
     * Builds the plates from the rows' bounds in card px (key, bounds, the row's coordinates), cut
     * at [viewport]; a row that did not move keeps its brushes. [stamp] is the [version] they belong to.
     */
    internal fun build(scope: DrawScope, face: Color, scroll: Float, viewport: Rect, measured: List<Triple<String, Rect, LayoutCoordinates?>>, stamp: Int = version) {
        val fade = with(scope) { RewardPlateFadeDp.dp.toPx() }
        val padX = with(scope) { 2.dp.toPx() }
        val padY = with(scope) { 1.dp.toPx() }
        val previous = cores.copyOf(count * 4)
        val sameFace = face == builtFace
        var next = 0
        var bottom = Float.NaN
        var footer = Float.NaN
        for ((key, bounds, row) in measured) {
            if (key == "footer") footer = bounds.top
            if (key != "chips" && key != "footer") {
                val unscrolled = min(bounds.bottom + scroll, viewport.bottom)
                bottom = if (bottom.isNaN()) unscrolled else max(bottom, unscrolled)
            }
            val core = Rect(bounds.left - padX, bounds.top - padY, bounds.right + padX, bounds.bottom + padY).intersect(viewport)
            if (core.width <= 0f || core.height <= 0f) continue
            ensure(next + 1)
            val base = next * 4
            cores[base] = core.left
            cores[base + 1] = core.top
            cores[base + 2] = core.right
            cores[base + 3] = core.bottom
            built[next] = row
            // A row that did not move keeps its brushes.
            val kept = sameFace && base + 3 < previous.size && previous[base] == core.left && previous[base + 1] == core.top &&
                previous[base + 2] == core.right && previous[base + 3] == core.bottom && edges[next * 8] != null
            if (!kept) softEdges(core, face, fade, next * 8)
            next++
        }
        for (index in next until built.size) built[index] = null
        count = next
        textBottom = bottom
        footerTop = footer
        builtVersion = stamp
        builtFace = face
        builtWidth = scope.size.width
        builtHeight = scope.size.height
    }

    private fun allAttached(): Boolean {
        for (index in 0 until count) {
            val row = built[index]
            if (row != null && !row.isAttached) return false
        }
        return true
    }

    private fun ensure(rows: Int) {
        if (rows <= built.size) return
        val capacity = max(rows, built.size * 2)
        cores = cores.copyOf(capacity * 4)
        edges = edges.copyOf(capacity * 8)
        built = built.copyOf(capacity)
    }

    /** The four edge and four corner fades of [core] from [color] to clear over [fade] px. */
    private fun softEdges(core: Rect, color: Color, fade: Float, at: Int) {
        val stops = arrayOf(0f to color, 1f to color.copy(alpha = 0f))
        edges[at] = Brush.verticalGradient(*stops, startY = core.top, endY = core.top - fade)
        edges[at + 1] = Brush.verticalGradient(*stops, startY = core.bottom, endY = core.bottom + fade)
        edges[at + 2] = Brush.horizontalGradient(*stops, startX = core.left, endX = core.left - fade)
        edges[at + 3] = Brush.horizontalGradient(*stops, startX = core.right, endX = core.right + fade)
        edges[at + 4] = Brush.radialGradient(*stops, center = core.topLeft, radius = fade)
        edges[at + 5] = Brush.radialGradient(*stops, center = core.topRight, radius = fade)
        edges[at + 6] = Brush.radialGradient(*stops, center = core.bottomLeft, radius = fade)
        edges[at + 7] = Brush.radialGradient(*stops, center = core.bottomRight, radius = fade)
    }

    /** Draws the prepared plates (allocation-free). */
    fun draw(scope: DrawScope) = with(scope) {
        val fade = RewardPlateFadeDp.dp.toPx()
        for (index in 0 until count) {
            val base = index * 4
            val left = cores[base]
            val top = cores[base + 1]
            val right = cores[base + 2]
            val bottom = cores[base + 3]
            val width = right - left
            val height = bottom - top
            val e = index * 8
            drawRect(builtFace, Offset(left, top), Size(width, height))
            drawRect(edges[e]!!, Offset(left, top - fade), Size(width, fade))
            drawRect(edges[e + 1]!!, Offset(left, bottom), Size(width, fade))
            drawRect(edges[e + 2]!!, Offset(left - fade, top), Size(fade, height))
            drawRect(edges[e + 3]!!, Offset(right, top), Size(fade, height))
            drawRect(edges[e + 4]!!, Offset(left - fade, top - fade), Size(fade, fade))
            drawRect(edges[e + 5]!!, Offset(right, top - fade), Size(fade, fade))
            drawRect(edges[e + 6]!!, Offset(left - fade, bottom), Size(fade, fade))
            drawRect(edges[e + 7]!!, Offset(right, bottom), Size(fade, fade))
        }
    }
}

/** Registers this text row with the card's soft face plates. */
private fun Modifier.facePlate(plates: RewardFacePlates?, key: String): Modifier =
    if (plates == null) this else onPlaced { plates.place(key, it) }

/**
 * Soft plates of the card face color under every registered text row (built by
 * [RewardFacePlates.prepare]), clipped to the card face ([facePath]) and to the text viewport: solid
 * under the row (plus 2 dp), fading to nothing over [RewardPlateFadeDp] around it, composited as one
 * layer at [RewardPlateOpacity].
 */
internal fun DrawScope.drawRewardFacePlates(plates: RewardFacePlates, facePath: Path) {
    plates.version
    clipPath(facePath) {
        drawContext.canvas.saveLayer(RewardFaceSpecs.bounds(size.width, size.height), RewardPlateLayer)
        plates.draw(this)
        drawContext.canvas.restore()
    }
}

/** The smallest share of its font size a card body shrinks to at the default text size before its text scrolls. */
internal const val RewardCardMinFit = 0.72f

/**
 * The size steps a card body tries, largest first: down to [RewardCardMinFit], and at a larger
 * text setting ([textScale] above the default) further down to the size the smallest setting
 * (100 %) gives it, so a long text still shows whole on a desktop card.
 */
internal fun rewardCardFitSteps(textScale: Float): FloatArray {
    val floor = min(RewardCardMinFit, RewardCardSmallestTextScale / textScale)
    return FloatArray(((1f - floor) / RewardCardFitStep + 0.001f).toInt() + 1) { 1f - it * RewardCardFitStep }
}

private const val RewardCardFitStep = 0.07f

/** The UI text factor of the smallest text setting (100 % against the 125 % default). */
private const val RewardCardSmallestTextScale = 0.8f

private object RewardCardBodySlot

private object RewardCardTailSlot

/** Subcompose keys of the measured-only body at a step, and of the tail. */
private data class RewardCardProbeSlot(val step: Float)

private data class RewardCardTailProbeSlot(val step: Float)

/**
 * Lays out a card body at the largest of [steps] whose [probeContent] (the same body, measured
 * only) fits the height, capped by [cap] (the deal's shared step). Reports the step this card
 * needs through [onFit].
 *
 * A [tail] (a relic's cost; its second argument is true while it is only measured) is laid out
 * [tailGap] px under the body at the same step and fits with it. It always shows whole: when the
 * body overflows even at the smallest step, the body keeps the height the tail leaves (and scrolls)
 * and the tail sits at the bottom.
 */
@Composable
private fun RewardCardFitLayout(
    steps: FloatArray,
    cap: Float,
    probe: TextMeasurer,
    onFit: (Float) -> Unit,
    modifier: Modifier,
    probeContent: @Composable (Float) -> Unit,
    tail: (@Composable (Float, Boolean) -> Unit)? = null,
    tailGap: Density.(Float) -> Int = { 0 },
    content: @Composable (Float) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        var need = steps.last()
        val width = Constraints.fixedWidth(constraints.maxWidth)
        // Each step is measured once per pass (a slot is composed once), so its heights are kept.
        val bodies = HashMap<Float, Int>()
        val tailHeights = HashMap<Float, Int>()
        fun bodyHeight(step: Float): Int = bodies.getOrPut(step) {
            subcompose(RewardCardProbeSlot(step)) { CompositionLocalProvider(LocalOverlayTextProbe provides probe) { probeContent(step) } }
                .maxOfOrNull { it.measure(width).height } ?: 0
        }
        fun tailHeight(step: Float): Int = if (tail == null) 0 else tailHeights.getOrPut(step) {
            val measured = subcompose(RewardCardTailProbeSlot(step)) { CompositionLocalProvider(LocalOverlayTextProbe provides probe) { tail(step, true) } }
                .maxOfOrNull { it.measure(width).height } ?: 0
            measured + tailGap(step)
        }
        for (step in steps) {
            if (bodyHeight(step) + tailHeight(step) <= constraints.maxHeight) {
                need = step
                break
            }
        }
        onFit(need)
        val fit = min(need, cap)
        if (tail == null) {
            val placeables = subcompose(RewardCardBodySlot) { content(fit) }.map { it.measure(constraints) }
            return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
        }
        val tails = subcompose(RewardCardTailSlot) { tail(fit, false) }.map { it.measure(width.copy(maxHeight = constraints.maxHeight)) }
        // A pixel of slack under the measured body, so a rounding step never makes it scroll.
        val tailTop = min(bodyHeight(fit) + 1, (constraints.maxHeight - (tails.maxOfOrNull { it.height } ?: 0) - tailGap(fit)).coerceAtLeast(0))
        val body = subcompose(RewardCardBodySlot) { content(fit) }.map { it.measure(constraints.copy(minHeight = 0, maxHeight = tailTop)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.forEach { it.place(0, 0) }
            tails.forEach { it.place(0, tailTop + tailGap(fit)) }
        }
    }
}

/**
 * Fades the bottom [height] of a scrolling text column out while more text follows below. It
 * masks the text layer only, so nothing is painted over the card under it.
 */
private fun Modifier.rewardScrollFade(scrollState: ScrollState, height: Dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        if (scrollState.canScrollForward) {
            val fade = height.toPx()
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** What the lifted card touches, grouped by reason (Improves, Synergy, …) as tag rows. */
@Composable
private fun RewardConnections(connections: List<RewardConnection>, textScale: Float, modifier: Modifier) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
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
                            foreground = connectionColor(connection, roles),
                            textScale = textScale,
                        )
                    }
                }
            }
        }
    }
}

/** Hue means element: an item in its first element (Kinetic without one), a relic in its aspect, the rest in bone. */
private fun connectionColor(connection: RewardConnection, roles: KkRolePalette): Color = when {
    connection.relic != null -> connection.relic.aspect.overlayColor(roles)
    connection.item != null -> connection.item.elements.firstOrNull()?.overlayColor(roles) ?: roles.kinetic
    else -> Kk.Bone
}

@Composable
internal fun RewardScrollIndicator(
    scrollState: ScrollState,
    accent: Color,
    modifier: Modifier,
    description: String = LocalAppLanguage.current.text(GameplayText.ScrollableRewards),
) {
    if (scrollState.maxValue <= 0) return
    Canvas(modifier.semantics { stateDescription = description }) {
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
