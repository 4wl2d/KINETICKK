// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkInfoButton
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkSlabKind
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkHalftone
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkIconPlate
import kinetickk.foundation.design.drawKkRadialFade
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberInterfaceTypography
import kinetickk.foundation.design.wideStyle
import kinetickk.foundation.design.withKkShear
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Board placement of the totem layout for one layout mode (board px, scaled by the frame). */
private class TotemSpec(
    val boardWidth: Float,
    val boardHeight: Float,
    val totem: Rect,
    val plateScale: Float,
    val titleSize: Float,
    val headingX: Float,
    val headingY: Float,
    val headingSize: Float,
    val rows: Rect,
    val rowGap: Float,
    val rowMax: Float,
    val stacked: Boolean,
    val tileWidth: Float,
    val nameSize: Float,
    val descSize: Float,
    val descLines: Int,
    val levelSize: Float,
    val tick: Size,
    val footerRight: Float,
    val footerTop: Float,
    val footerHeight: Float,
    val takeWidth: Float,
) {
    // Row interior (board px): side padding, gap between tile, text and level column.
    val rowPadStart: Float get() = if (stacked) 14f else 30f
    val rowPadEnd: Float get() = if (stacked) 14f else 34f
    val rowGapX: Float get() = if (stacked) 14f else 26f
    val levelWidth: Float get() = if (boardWidth < 1000f) 150f else 250f

    /** Board width of a row's text column; landscape rows with a level keep the level column. */
    fun textWidth(hasLevel: Boolean): Float =
        rows.width - rowPadStart - rowPadEnd - tileWidth - rowGapX - if (!stacked && hasLevel) levelWidth + rowGapX else 0f
}

/**
 * The description style every row of one totem screen shares: the largest size (down to half)
 * at which each row's description fits its text column in [TotemSpec.descLines] lines without
 * breaking inside a word.
 */
@Composable
private fun rememberTotemDescriptionStyle(
    rows: List<RewardTotemRow>,
    spec: TotemSpec,
    frame: OverlayFrame,
    typography: InterfaceTypography,
    heights: Map<Int, Float>,
): TextStyle {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current.density
    val base = typography.bodyStyle(frame.sp(spec.descSize, 11f))
    // One px under the laid-out column width absorbs dp to px rounding of the row's paddings.
    val texts = rows.mapIndexed { index, row ->
        OverlayTextBox(row.description, frame.dp(spec.textWidth(row.level != null)).value * density - 1f, heights[index] ?: Float.POSITIVE_INFINITY)
    }
    return remember(texts, base, spec.descLines) { totemDescriptionStyle(measurer, texts, base, spec.descLines) }
}

/** [totemLevelFit] for the rows of one totem screen, [rowHeight] in board px. */
@Composable
private fun rememberTotemLevelFit(
    rows: List<RewardTotemRow>,
    spec: TotemSpec,
    frame: OverlayFrame,
    typography: InterfaceTypography,
    rowHeight: Float,
): TotemLevelFit {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current.density
    val levelLabel = LocalAppLanguage.current.text(OverlayRedesignText.LevelLabel)
    val basePad = if (spec.stacked || frame.scale < 0.8f) 12f else 18f
    val masteries = rows.mapNotNull { it.mastery }
    val label = typography.labelStyle(frame.sp(14f, 10f))
    val numeral = typography.wideStyle(frame.display(spec.levelSize, 16f), tabular = true)
    val mastery = typography.monoStyle(frame.sp(11f, 9f))
    val px = frame.scale * density
    return remember(masteries, levelLabel, label, numeral, mastery, px, rowHeight, spec.stacked, spec.levelWidth, spec.tick.height, basePad) {
        if (spec.stacked) {
            TotemLevelFit(basePad * px, mastery)
        } else {
            totemLevelFit(
                measurer, levelLabel, masteries, label, numeral, mastery, spec.levelWidth * px, spec.tick.height * px, 3f * density,
                (rowHeight * px).roundToInt().toFloat(), (basePad * px).roundToInt().toFloat(), TotemRowMinPad * px,
            )
        }
    }
}

/**
 * The largest [base] size (to [TotemDescriptionMinScale]) at which every text fits its box in
 * [maxLines] lines without breaking inside a word.
 */
internal fun totemDescriptionStyle(measurer: TextMeasurer, texts: List<OverlayTextBox>, base: TextStyle, maxLines: Int): TextStyle =
    sharedFitStyle(measurer, texts, base, maxLines, TotemDescriptionMinScale)

/** The smallest share of its size a totem description shrinks to. */
internal const val TotemDescriptionMinScale = 0.5f

/**
 * How a landscape totem row fits its level column ("Lvl N", ten ticks, the mastery tier): the
 * row's vertical [pad] (px) and the [mastery] style every row shares.
 */
internal class TotemLevelFit(val pad: Float, val mastery: TextStyle)

/**
 * The regular [basePad] while the level column fits the [rowHeight] (px) between it; with larger
 * text the padding gives way (down to [minPad]) before the mastery tier shrinks to the height
 * left. One mastery style for every row: the smallest any tier name needs to fit [columnWidth].
 */
internal fun totemLevelFit(
    measurer: TextMeasurer,
    levelLabel: String,
    masteries: List<String>,
    label: TextStyle,
    numeral: TextStyle,
    mastery: TextStyle,
    columnWidth: Float,
    ticksHeight: Float,
    labelGap: Float,
    rowHeight: Float,
    basePad: Float,
    minPad: Float,
): TotemLevelFit {
    val shown = masteries.filter(String::isNotBlank).map(String::uppercase)
    var masteryStyle = shown.fold(mastery) { style, text ->
        val fitted = fitTextStyle(measurer, text, mastery, columnWidth, 1, 0.7f)
        if (fitted.fontSize.value < style.fontSize.value) fitted else style
    }
    val levelRow = max(
        measurer.measure(levelLabel.uppercase(), label, maxLines = 1).size.height + labelGap,
        measurer.measure("10", numeral, maxLines = 1).size.height.toFloat(),
    )
    fun masteryHeight(style: TextStyle) =
        if (shown.isEmpty()) 0f else measurer.measure(shown.first(), style, softWrap = false, maxLines = 1).size.height.toFloat()
    val need = levelRow + ticksHeight + masteryHeight(masteryStyle)
    // Whole pixels: the row and its padding lay out rounded.
    val pad = if (need <= rowHeight - basePad * 2f) basePad else floor((rowHeight - need) * 0.5f).coerceIn(min(minPad, basePad), basePad)
    val room = rowHeight - pad * 2f
    if (need > room && shown.isNotEmpty()) {
        val left = room - levelRow - ticksHeight
        val natural = masteryHeight(masteryStyle)
        masteryStyle = masteryStyle.copy(fontSize = masteryStyle.fontSize * (left / natural).coerceIn(0.5f, 1f) * 0.98f)
    }
    return TotemLevelFit(pad, masteryStyle)
}

/** The least vertical padding (board px) a landscape totem row keeps around its level column. */
private const val TotemRowMinPad = 10f

private fun totemSpec(mode: GameplayLayoutMode): TotemSpec = when (mode) {
    // Totem board at 1440 x 810.
    GameplayLayoutMode.REGULAR -> TotemSpec(
        1440f, 810f, Rect(90f, 110f, 430f, 670f), 1f, 30f, 520f, 70f, 52f,
        Rect(520f, 146f, 1392f, 684f), 14f, 170f, false, 120f, 44f, 15f, 2, 38f, Size(18f, 12f),
        1392f, 700f, 38f, 200f,
    )
    GameplayLayoutMode.COMPACT_LANDSCAPE -> TotemSpec(
        844f, 390f, Rect(16f, 20f, 196f, 330f), 0.5f, 14f, 216f, 12f, 30f,
        Rect(216f, 56f, 828f, 322f), 8f, 100f, false, 72f, 26f, 12f, 2, 22f, Size(10f, 8f),
        828f, 330f, 48f, 180f,
    )
    GameplayLayoutMode.COMPACT_PORTRAIT -> TotemSpec(
        390f, 844f, Rect(20f, 8f, 370f, 214f), 0.5f, 18f, 16f, 224f, 34f,
        Rect(16f, 272f, 374f, 764f), 10f, 170f, true, 72f, 26f, 13f, 2, 24f, Size(9f, 8f),
        374f, 776f, 56f, 358f,
    )
}

@Composable
internal fun RewardTotem(state: RewardOverlayState) {
    val presentation = state.presentation
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = rememberInterfaceTypography()
    val spec = totemSpec(state.mode)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = overlayFrame(maxWidth.value, maxHeight.value, spec.boardWidth, spec.boardHeight, state.textScale)
        val totemTitle = presentation.totem?.title ?: presentation.heading
        TotemIllustration(
            state,
            Modifier.offset(frame.x(spec.totem.left), frame.y(spec.totem.top))
                .size(frame.dp(spec.totem.width), frame.dp(spec.totem.height - spec.titleSize * 1.6f)),
        )
        OverlayFitText(
            totemTitle, typography.wideStyle(frame.sp(spec.titleSize, 12f), color = Kk.Bone),
            Modifier.offset(frame.x(spec.totem.left), frame.y(spec.totem.bottom - spec.titleSize * 1.2f))
                .width(frame.dp(spec.totem.width)),
            uppercase = true, align = TextAlign.Center, maxLines = 1, minScale = 0.5f,
        )
        Row(
            Modifier.offset(frame.x(spec.headingX), frame.y(spec.headingY)),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayText(language.text(OverlayRedesignText.Offerings), typography.condStyle(frame.sp(spec.headingSize, 20f), color = Kk.Bone),
                Modifier.testTag("kinetickk.gameplay.rewards.heading"), uppercase = true)
            presentation.totem?.let { totem ->
                KkInfoButton(totem.masteryInfo, Modifier.testTag("kinetickk.gameplay.rewards.info"), KkTooltipPlacement.BELOW, state.textScale)
            }
        }
        val count = presentation.cards.size.coerceAtLeast(1)
        // Rows grow with larger text while the offerings area has room for them.
        val rowHeight = min(spec.rowMax * max(1f, state.textScale), (spec.rows.height - spec.rowGap * (count - 1)) / count)
        // One description size for the whole list: the largest at which every row's text fits its
        // own width in the line budget and the height its row leaves it.
        val descriptionRoom = remember(presentation, spec.descLines) { mutableStateMapOf<Int, Float>() }
        val descriptionStyle = rememberTotemDescriptionStyle(presentation.cards.mapNotNull { it.totemRow }, spec, frame, typography, descriptionRoom)
        val levelFit = rememberTotemLevelFit(presentation.cards.mapNotNull { it.totemRow }, spec, frame, typography, rowHeight)
        Column(
            Modifier.offset(frame.x(spec.rows.left), frame.y(spec.rows.top)).width(frame.dp(spec.rows.width)),
            verticalArrangement = Arrangement.spacedBy(frame.dp(spec.rowGap)),
        ) {
            presentation.cards.forEachIndexed { index, card ->
                val row = card.totemRow
                if (row != null) key(index, card.choice) {
                    TotemRow(
                        row, index, state.selected == index, state.enabled, spec, frame, rowHeight, typography, roles, descriptionStyle, levelFit,
                        onDescriptionRoom = { height -> descriptionRoom[index] = height },
                        Modifier.graphicsLayer {
                            val p = ((state.entrance() - 100f - index * 70f) / 500f).coerceIn(0f, 1f)
                            val e = KkEase.Pull.transform(p)
                            alpha = (p / 0.6f).coerceIn(0f, 1f)
                            translationX = (1f - e) * 110f * density
                        },
                        onPreview = { state.onSelected(index) },
                        onSelect = { state.onSelect(index) },
                    )
                }
            }
        }
        val selectedTitle = state.selected?.let(presentation.cards::getOrNull)?.totemRow?.name
        val take = language.text(OverlayRedesignText.Take)
        Row(
            Modifier.offset(frame.x(spec.footerRight - spec.takeWidth - (if (state.canReroll) spec.takeWidth * 0.9f + 12f else 0f)),
                frame.y(spec.footerTop)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.canReroll) {
                OverlayButton(
                    language.text(OverlayRedesignText.Reroll), state.onReroll,
                    Modifier.size(frame.dp(spec.takeWidth * 0.9f), frame.dp(spec.footerHeight).coerceAtLeast(38.dp))
                        .testTag("kinetickk.gameplay.reroll"),
                    KkButtonVariant.GHOST, KkButtonSize.SM, state.enabled, KkIcon.SYSTEM_REROLL,
                    presentation.rerollsRemaining.toString(), state.textScale,
                )
            }
            OverlayButton(
                take, { state.selected?.let(state.onSelect) },
                Modifier.size(frame.dp(spec.takeWidth), frame.dp(spec.footerHeight).coerceAtLeast(38.dp)).testTag("kinetickk.gameplay.take"),
                KkButtonVariant.PRIMARY, KkButtonSize.SM, state.enabled && state.selected != null, textScale = state.textScale,
                stateDescription = selectedTitle,
            )
        }
    }
}

@Composable
private fun TotemRow(
    row: RewardTotemRow,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    spec: TotemSpec,
    frame: OverlayFrame,
    heightBoard: Float,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    descriptionStyle: TextStyle,
    levelFit: TotemLevelFit,
    onDescriptionRoom: (Float) -> Unit,
    modifier: Modifier,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val highlighted = enabled && (hovered || focused)
    LaunchedEffect(highlighted) { if (highlighted) onPreview() }
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(200, easing = KkEase.Pull), label = "totemRow")
    val fg = if (selected) Kk.Ink else Kk.Bone
    val paths = remember { KkPathCache(2) }
    Box(
        modifier
            .fillMaxWidth()
            .height(frame.dp(heightBoard))
            .graphicsLayer { translationX = -10f * density * on }
            .testTag("kinetickk.gameplay.choice.${index + 1}")
            .semantics { stateDescription = listOfNotNull(row.kind, row.meta, row.level?.let { language.text(OverlayRedesignText.LevelLabel) + " " + it }, row.mastery).joinToString(", ") }
            .clickable(
                interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
                onClickLabel = language.text(GameplayText.SelectChoice, index + 1, row.name), onClick = onSelect,
            )
            .drawBehind {
                val face = when {
                    selected -> Kk.Bone
                    hovered || focused -> Kk.Ink3
                    else -> Kk.Ink2.copy(alpha = 0.72f)
                }
                drawPath(paths.slab(0, Rect(Offset.Zero, size), 9.dp.toPx(), KkSlabKind.SLAB), face)
            },
    ) {
        // The level column keeps its whole height: larger text takes some of the row's padding.
        val pad = with(LocalDensity.current) { levelFit.pad.toDp() }
        Row(
            Modifier.fillMaxSize().padding(start = frame.dp(spec.rowPadStart), end = frame.dp(spec.rowPadEnd), top = pad, bottom = pad),
            horizontalArrangement = Arrangement.spacedBy(frame.dp(spec.rowGapX)),
        ) {
            Box(
                Modifier.width(frame.dp(spec.tileWidth)).fillMaxHeight().drawBehind {
                    if (selected) {
                        drawPath(paths.slab(1, Rect(Offset.Zero, size), 12f * frame.scale * density, KkSlabKind.SLAB), Kk.Ink)
                        drawKkIcon(row.icon, center, spec.tileWidth * 0.48f * frame.scale * density, roles.you)
                    } else {
                        drawKkIconPlate(Rect(Offset.Zero, size), row.icon, roles.you, 12f * frame.scale, spec.tileWidth * 0.48f * frame.scale)
                    }
                },
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OverlayTag(
                        row.kind,
                        variant = when {
                            row.kindTone == RewardTone.YOU -> KkTagVariant.YOU
                            selected -> KkTagVariant.DEFAULT
                            else -> KkTagVariant.BONE
                        },
                        textScale = frame.textScale,
                    )
                    row.meta?.let { OverlayFitText(it, typography.monoStyle(frame.sp(11f, 9f), color = fg.copy(alpha = 0.7f)), uppercase = true, maxLines = 1) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // The offering name is large display type (44 px on the board): the text-size
                    // setting does not apply, which leaves the row's height to its description.
                    val nameStyle = rememberWordFitStyle(row.name, typography.condStyle(frame.display(spec.nameSize, 18f), color = fg),
                        constraints.maxWidth.toFloat(), wholeLine = true)
                    OverlayText(row.name, nameStyle, uppercase = true, maxLines = 1)
                }
                if (heightBoard * frame.scale >= 104f || spec.stacked) {
                    // The description takes the height the row leaves it (reported for the shared size).
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = if (spec.stacked) Alignment.CenterStart else Alignment.BottomStart,
                    ) {
                        val room = constraints.maxHeight.toFloat()
                        LaunchedEffect(room) { onDescriptionRoom(room) }
                        OverlayText(row.description, descriptionStyle.copy(color = fg.copy(alpha = 0.75f)),
                            Modifier.testTag("kinetickk.gameplay.choice.${index + 1}.description"))
                    }
                }
                if (spec.stacked) TotemLevel(row, selected, spec, frame, typography, roles, levelFit.mastery, stacked = true)
            }
            if (!spec.stacked && row.level != null) {
                Box(Modifier.width(frame.dp(spec.levelWidth)).fillMaxHeight().testTag("kinetickk.gameplay.choice.${index + 1}.level")) {
                    TotemLevel(row, selected, spec, frame, typography, roles, levelFit.mastery, stacked = false)
                }
            }
        }
    }
}

/** "Lvl N", ten mastery ticks (owned, gained, empty) and the mastery tier name. */
@Composable
private fun TotemLevel(
    row: RewardTotemRow,
    selected: Boolean,
    spec: TotemSpec,
    frame: OverlayFrame,
    typography: InterfaceTypography,
    roles: KkRolePalette,
    masteryStyle: TextStyle,
    stacked: Boolean,
) {
    val level = row.level ?: return
    val language = LocalAppLanguage.current
    val fg = if (selected) Kk.Ink else Kk.Bone
    val levelRow: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            OverlayText(language.text(OverlayRedesignText.LevelLabel), typography.labelStyle(frame.sp(14f, 10f), color = fg.copy(alpha = 0.7f)),
                Modifier.padding(bottom = 3.dp), uppercase = true)
            // The level numeral is display type: the text-size setting does not apply.
            OverlayText(level.toString(), typography.wideStyle(frame.display(spec.levelSize, 16f), tabular = true, color = fg))
        }
    }
    val ticks: @Composable () -> Unit = {
        val tickW = spec.tick.width * frame.scale
        val tickH = spec.tick.height * frame.scale
        val gap = spec.tick.width * 0.22f * frame.scale
        Canvas(Modifier.size((tickW * 10f + gap * 9f).dp, tickH.dp)) {
            val w = tickW * density
            val h = tickH * density
            withKkShear(h * 0.5f) {
                for (k in 0 until 10) {
                    val color = when {
                        k < row.fromLevel -> if (selected) Kk.Ink else Kk.Bone
                        k < row.toLevel -> if (selected) kkMix(roles.you, Kk.Ink, 0.45f) else roles.you
                        else -> if (selected) Kk.RCommon else Kk.Ink4
                    }
                    drawRect(color, Offset(k * (w + gap * density), 0f), Size(w, h))
                }
            }
        }
    }
    val mastery: @Composable () -> Unit = {
        row.mastery?.let { OverlayFitText(it, masteryStyle.copy(color = fg.copy(alpha = 0.8f)), uppercase = true, maxLines = 1) }
    }
    if (stacked) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            levelRow(); ticks()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { mastery() }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            levelRow(); ticks(); mastery()
        }
    }
}

/**
 * Weapon totem: stacked sheared plates around the `you` key block, a dashed orbit and a halftone
 * halo. Plates drop in, the key block slams (entrance clock).
 */
@Composable
private fun TotemIllustration(state: RewardOverlayState, modifier: Modifier) {
    val roles = LocalKkRolePalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val dash = remember(density) {
        Stroke(1.5f * density, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 5f * density)))
    }
    Canvas(modifier) {
        val scale = min(size.width / 340f, size.height / 470f)
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val halo = Rect(cx - size.width * 0.8f, cy - size.height * 0.62f, cx + size.width * 0.8f, cy + size.height * 0.62f)
        drawKkRadialFade(halo) { drawKkHalftone(halo, roles.you.copy(alpha = 0.12f)) }
        val ringRadius = 150f * scale
        rotate(state.time.value * 360f / 16f, Offset(cx, cy)) {
            drawCircle(roles.you.copy(alpha = 0.45f), ringRadius, Offset(cx, cy), style = dash)
        }
        drawTotemPlates(state.entrance(), Offset(cx, cy), scale, roles)
    }
}

private val PlateHeights = floatArrayOf(60f, 80f, 110f, 80f, 60f)
private val PlateDelays = floatArrayOf(50f, 100f, 250f, 100f, 50f)

private fun DrawScope.drawTotemPlates(entranceMs: Float, center: Offset, scale: Float, roles: KkRolePalette) {
    val gap = 8f * scale
    val width = 120f * scale
    val total = PlateHeights.sum() * scale + gap * 4f
    var top = center.y - total * 0.5f
    for (index in PlateHeights.indices) {
        val h = PlateHeights[index] * scale
        val p = ((entranceMs - PlateDelays[index]) / 500f).coerceIn(0f, 1f)
        val key = index == 2
        val rect = Rect(center.x - width * 0.5f, top, center.x + width * 0.5f, top + h)
        if (key) {
            val slam = if (p >= 1f) 1f else p
            val s = KkSlam.scale(slam)
            scale(s, s, rect.center) {
                withKkShear(rect.center.y) { drawRect(roles.you, rect.topLeft, rect.size, alpha = KkSlam.alpha(slam)) }
                drawKkIcon(KkIcon.SYSTEM_KEY, rect.center, 54f * scale, Kk.Ink, KkSlam.alpha(slam))
            }
        } else {
            val dy = (1f - KkEase.Out.transform(p)) * 46f * scale * (if (index < 2) -1f else 1f)
            withKkShear(rect.center.y) {
                drawRect(Kk.Ink3, Offset(rect.left, rect.top + dy), rect.size, alpha = p)
                val lineY = if (index < 2) rect.bottom - 3f * scale else rect.top
                drawRect(roles.you, Offset(rect.left, lineY + dy), Size(rect.width, 3f * scale), alpha = p)
            }
        }
        top += h + gap
    }
}
