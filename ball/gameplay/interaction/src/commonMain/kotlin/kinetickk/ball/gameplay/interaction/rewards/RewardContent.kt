// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.gameplay.interaction.canvas.drawItemIcon
import kinetickk.ball.gameplay.interaction.canvas.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.drawUnresolvedRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.weaponGlyphStyle
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.Violet
import kinetickk.foundation.design.White
import kinetickk.foundation.design.drawPolygon
import kinetickk.foundation.design.drawSystemGlyph
import kotlin.math.roundToInt

private val RewardPanel = Color(0xFF111B2D)
private val RewardBackground = Color(0xFF050610)
private val RewardShape = RoundedCornerShape(12.dp)

/** Visible controls use the original pixel rectangles without changing mobile spacing. */
@Composable
internal fun RewardContent(
    engine: GameplayRenderModel,
    layout: ChoiceLayoutGeometry,
    renderTime: Float,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onReroll: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val presentation = remember(engine, language) { engine.rewardPresentation(language) }
    RewardContent(presentation, layout, engine.screenWidth, engine.uiScale, engine.settings.textScale, renderTime, enabled, onSelect, onReroll)
}

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
) {
    val language = LocalAppLanguage.current
    val scale = textScale
    Box(Modifier.fillMaxSize().background(RewardBackground).testTag("kinetickk.gameplay.rewards")) {
        val headerBottom = layout.cards.minOfOrNull { it.top } ?: layout.subtitleY
        Column(
            Modifier.rewardBounds(Rect(12f * uiScale, layout.titleY, screenWidth - 12f * uiScale, headerBottom - 8f * uiScale))
                .verticalScroll(rememberScrollState(), enabled = enabled),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RewardText(
                presentation.heading,
                presentation.titleAccent,
                (if (layout.mode == GameplayLayoutMode.REGULAR) 24f else 14f) * scale,
                bold = true,
                centered = true,
            )
            RewardText(presentation.subtitle, Violet, (if (layout.mode == GameplayLayoutMode.REGULAR) 9f else 8f) * scale, centered = true)
        }
        presentation.cards.forEachIndexed { index, card ->
            key(index, card.choice) {
                RewardCard(
                    presentation = card,
                    index = index,
                    textScale = scale,
                    renderTime = renderTime,
                    enabled = enabled,
                    modifier = Modifier.rewardBounds(layout.cards[index]),
                    onSelect = { onSelect(index) },
                )
            }
        }
        layout.reroll?.let { bounds ->
            val accent = presentation.rerollAccent
            Box(
                Modifier.rewardBounds(bounds)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RewardPanel)
                    .border(2.dp, accent, RoundedCornerShape(8.dp))
                    .testTag("kinetickk.gameplay.reroll")
                    .clickable(enabled = enabled, role = Role.Button, onClick = onReroll)
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                RewardText(language.text(GameplayText.Reroll, presentation.rerollsRemaining), accent, 10f * scale, bold = true, centered = true)
            }
        }
    }
}

@Composable
internal fun RewardCard(
    presentation: RewardCardPresentation,
    index: Int,
    textScale: Float,
    renderTime: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredValue by interactionSource.collectIsHoveredAsState()
    val focusedValue by interactionSource.collectIsFocusedAsState()
    val pressedValue by interactionSource.collectIsPressedAsState()
    val highlighted = hoveredValue || focusedValue || pressedValue
    val accent = presentation.accent
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier.clip(RewardShape)
            .background(RewardPanel)
            .border(if (highlighted) 3.dp else 1.5.dp, accent.copy(alpha = if (highlighted) 1f else 0.8f), RewardShape)
            .testTag("kinetickk.gameplay.choice.${index + 1}")
            .rewardDragGestures(enabled, scrollState)
            // The body consumes its own scroll first. Header/footer drags and wheel
            // input reach this parent, moving the same text viewport once.
            .scrollable(scrollState, Orientation.Vertical, enabled = enabled, reverseDirection = true)
            // A single activation surface. The nested scrollable cancels this click on drag;
            // wheel events never synthesize activation. The footer is a visible part of it.
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = language.text(GameplayText.SelectChoice, index + 1, presentation.title),
                onClick = onSelect,
            ),
    ) {
        val compact = rewardCardIsCompact(maxWidth.value, maxHeight.value)
        Column(Modifier.fillMaxSize()) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).background(accent.copy(alpha = if (highlighted) 0.32f else 0.16f))
                        .padding(horizontal = 8.dp).testTag("kinetickk.gameplay.choice.${index + 1}.compact"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RewardIcon(presentation, renderTime, Modifier.size(40.dp))
                    RewardText("0${index + 1}", accent, 12f * textScale, bold = true)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(86.dp).background(accent.copy(alpha = if (highlighted) 0.28f else 0.12f))
                        .testTag("kinetickk.gameplay.choice.${index + 1}.expanded"),
                    contentAlignment = Alignment.Center,
                ) {
                    RewardIcon(presentation, renderTime, Modifier.size(80.dp))
                    BasicText(
                        "0${index + 1}",
                        Modifier.align(Alignment.TopStart).padding(10.dp),
                        TextStyle(color = accent, fontSize = (10f * textScale).sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().testTag("kinetickk.gameplay.choice.${index + 1}.text")
                        .verticalScroll(scrollState, enabled = enabled).padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    RewardText(presentation.tag, accent, 9f * textScale, bold = true)
                    RewardText(presentation.title, White, 15f * textScale, bold = true)
                    presentation.descriptions.forEach { description ->
                        RewardText(description, Muted, 12f * textScale)
                    }
                    RewardText(presentation.operation, accent, 11f * textScale, bold = true)
                }
                RewardScrollIndicator(scrollState, accent, Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight().width(4.dp).testTag("kinetickk.gameplay.choice.${index + 1}.scroll"))
            }
            Box(
                Modifier.fillMaxWidth().background(accent.copy(alpha = if (highlighted) 0.32f else 0.18f))
                    .testTag("kinetickk.gameplay.choice.${index + 1}.action")
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                RewardText(language.text(GameplayText.Select, index + 1), accent, 11f * textScale, bold = true, centered = true)
            }
        }
    }
}

@Composable
private fun RewardScrollIndicator(scrollState: ScrollState, accent: Color, modifier: Modifier) {
    if (scrollState.maxValue <= 0) return
    val language = LocalAppLanguage.current
    Canvas(modifier.semantics { stateDescription = language.text(GameplayText.ScrollableRewards) }) {
        drawRect(accent.copy(alpha = 0.15f))
        val contentHeight = scrollState.viewportSize + scrollState.maxValue
        val fraction = if (contentHeight > 0) scrollState.viewportSize.toFloat() / contentHeight else 1f
        val thumbHeight = (size.height * fraction).coerceAtLeast(12.dp.toPx()).coerceAtMost(size.height)
        val top = (size.height - thumbHeight) * scrollState.value / scrollState.maxValue
        drawRect(accent, Offset(0f, top), Size(size.width, thumbHeight))
    }
}

@Composable
private fun RewardIcon(presentation: RewardCardPresentation, time: Float, modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.34f
        val center = center
        when {
            presentation.relic != null -> {
                val policy = requireNotNull(presentation.relicPolicy)
                drawRelicIcon(presentation.relic, policy, center, radius, presentation.relicRank, time)
                presentation.incomingRelic?.let { incoming ->
                    val incomingCenter = center + Offset(radius * 0.88f, radius * 0.55f)
                    drawCircle(RewardBackground, radius * 0.6f, incomingCenter)
                    drawRelicIcon(incoming, policy, incomingCenter, radius * 0.45f, 1, time)
                }
            }
            presentation.item != null -> drawItemIcon(presentation.item, center, radius, presentation.accent, presentation.itemStack)
            presentation.weapon != null -> drawSystemGlyph(weaponGlyphStyle(presentation.weapon.id), center, radius, time, presentation.accent)
            presentation.choice.type == ChoiceType.RELIC -> drawUnresolvedRelicIcon(center, radius, time)
            else -> {
                drawCircle(presentation.accent.copy(alpha = 0.2f), radius, center)
                drawPolygon(center, radius * 0.8f, 6, time * 0.4f, presentation.accent, Stroke(radius * 0.08f))
                drawCircle(White, radius * 0.24f, center)
            }
        }
    }
}

@Composable
private fun RewardText(text: String, color: Color, size: Float, bold: Boolean = false, centered: Boolean = false) {
    BasicText(
        text,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        ),
    )
}

@Composable
private fun Modifier.rewardBounds(bounds: Rect): Modifier {
    val density = LocalDensity.current
    return offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }.requiredSize(
        with(density) { bounds.width.toDp() },
        with(density) { bounds.height.coerceAtLeast(1f).toDp() },
    )
}

/** Native scrolling handles touch; mouse content dragging is adapted here because
 * desktop scrollable intentionally leaves mouse drags to its child. One clickable
 * remains responsible for activation, with every drag cancelling its release. */
private fun Modifier.rewardDragGestures(enabled: Boolean, scrollState: ScrollState): Modifier = pointerInput(enabled, scrollState) {
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
