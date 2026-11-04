// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.layout.hudWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.overlay.choiceOverlayScrimColor
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkDeal
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.KkApexState
import kinetickk.foundation.design.drawKkLevelBadge
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.drawKkRingBurst
import kinetickk.foundation.design.kkLerp
import kinetickk.foundation.design.kkLevelBadgeSize
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.overlay.*

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
) = RewardContent(engine, layout, rememberUpdatedState(renderTime), enabled, onSelect, onReroll, onBuild, touch)

/** Keep the live clock in drawing/layer reads so animated cards do not recompose and refit their text. */
@Composable
internal fun RewardContent(
    engine: GameplayRenderModel,
    layout: ChoiceLayoutGeometry,
    renderTime: State<Float>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
    onBuild: () -> Unit = {},
    touch: Boolean = false,
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language, touch) { engine.rewardPresentation(language, touch) }
    RewardContent(presentation, layout, engine.hudWidth, engine.uiScale, engine.settings.textScale, 0f, enabled, onSelect, onReroll, onBuild, renderTime)
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
    time: State<Float>? = null,
) {
    val clock = time ?: rememberUpdatedState(renderTime)
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
            time = clock,
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
 * totem offering; replacing a relic starts with no slot so nothing is armed.
 */
internal fun RewardPresentation.defaultSelection(): Int? = when {
    cards.isEmpty() -> null
    kind == RewardLayoutKind.RELIC_BIND && cards.first().choice.relicAction == RelicChoiceAction.REPLACE -> null
    kind == RewardLayoutKind.CARDS -> rewardDefaultCard(cards)
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

/** How far a lifted (selected) card rises, in dp. */
internal const val RewardCardLiftDp = 22f

/**
 * Whether card [index] of the dealt [cards] rises when lifted: only when no card of the deal sits above it in its column,
 * so a lower row's lifted card never rides over the frame of the card above it.
 */
internal fun rewardCardRises(index: Int, cards: List<Rect>): Boolean {
    val card = cards.getOrNull(index) ?: return false
    return cards.indices.none { other ->
        other != index && cards[other].bottom <= card.top && cards[other].left < card.right && cards[other].right > card.left
    }
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
            // A lifted card rises only with no card above it: in the portrait 2 x 2 deck the lower row stays put.
            val rise = if (rewardCardRises(index, layout.cards)) lift else 0f
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
                    translationY = (origin.y - bounds.center.y) * (1f - eased) - RewardCardLiftDp * density * rise
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
    RewardHeadingFrame(presentation.conditions, state.textScale, beside = state.mode == GameplayLayoutMode.COMPACT_LANDSCAPE, modifier) {
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
        enabled = state.enabled && selected != null && state.presentation.cards.getOrNull(selected)?.locked != true,
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

/** The decline card's dashed inner outline: its inset from the card's edges and its slant (the face's 22 dp cut less 6 dp). */
internal const val RewardDeclineInsetDp = 7f
internal const val RewardDeclineCutDp = 16f

/** The clear gap between a decline card's outline and the plate and text inside it. */
private const val RewardDeclineClearanceDp = 5f

/**
 * The x (dp from the card's left) of the decline outline's slanted left edge at [yDp] on a card [heightDp] tall whose
 * band is [bandDp] tall: [RewardDeclineCutDp] in from the inset at the outline's top, at the inset at its bottom.
 */
internal fun rewardDeclineOutlineLeftDp(yDp: Float, heightDp: Float, bandDp: Float): Float {
    val top = bandDp + RewardDeclineInsetDp
    val bottom = heightDp - RewardDeclineInsetDp
    val down = ((yDp - top) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return RewardDeclineInsetDp + RewardDeclineCutDp * (1f - down)
}

/**
 * How far (dp) a decline card of [spec], [heightDp] tall, moves its plate and text right so both start
 * [RewardDeclineClearanceDp] clear of the outline at their tops; 0 where they already do (the board card).
 */
internal fun rewardDeclineShiftDp(spec: RewardCardSpec, heightDp: Float): Float {
    val band = spec.bandHeight * spec.u
    val plate = rewardDeclineOutlineLeftDp(spec.plateY * spec.u, heightDp, band) + RewardDeclineClearanceDp - spec.plateX * spec.u
    val text = rewardDeclineOutlineLeftDp(spec.bodyTop * spec.u, heightDp, band) + RewardDeclineClearanceDp - spec.start * spec.u
    return max(0f, max(plate, text))
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

/** Top-right relic strip placement for relic offers (label row + diamonds with brackets). */
private fun relicStripBounds(layout: ChoiceLayoutGeometry, screenWidth: Float, density: Float): Rect {
    val regular = layout.mode == GameplayLayoutMode.REGULAR
    val width = (if (regular) 300f else 190f) * density
    val height = (if (regular) 118f else 40f) * density
    val right = screenWidth - (if (regular) 56f else 16f) * density
    val top = layout.titleY - (if (regular) 6f else 2f) * density
    return Rect(right - width, top, right, top + height)
}
