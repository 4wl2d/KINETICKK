// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.ProfilePanel
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.ball.profile.interaction.drawProfileSidePanel
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.lab.api.LabUpgradeRenderModel
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

/** Latest geometry for the background pass (written during composition). */
internal class LabLayoutHolder {
    var layout: LabLayout? = null
}

@Composable
internal fun LabContent(
    state: LabState,
    scale: Float,
    listScroll: ScrollState,
    onAction: (LabAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val model = state.model
    val holder = remember { LabLayoutHolder() }
    val (owned, total) = model.rankTotals()
    ProfilePanel(
        title = language.text(ProfileText.LabTitle),
        count = language.text(ProfileScreensRedesignText.CountOf, owned, total),
        info = language.text(ProfileScreensRedesignText.LabInfo),
        matter = model.matter,
        scale = scale,
        tag = "profile-lab",
        onBack = { onAction(LabAction.Back) },
        background = { frame -> drawLabBackground(frame, holder.layout) },
    ) { frame ->
        val measurer = rememberKkCanvasMeasurer(scale)
        val type = labType(frame.mode)
        val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
        val layout = labLayout(frame, model.upgrades.size, model.upgrades.maxOfOrNull { it.maxRanks } ?: 1, scale, backWidth)
        SideEffect { holder.layout = layout }
        val selected = state.selectedUpgrade
        val list = layout.listViewport
        Box(
            Modifier.offset { IntOffset(list.left.roundToInt(), list.top.roundToInt()) }
                .size(frame.dp(list.width), frame.dp(list.height))
                .semantics { contentDescription = language.text(ProfileScreensRedesignText.UpgradesList) }
                .verticalScroll(listScroll)
                .testTag("profile-lab-scroll"),
        ) {
            Box(Modifier.fillMaxWidth().height(frame.dp(layout.listContentHeight))) {
                model.upgrades.forEachIndexed { index, upgrade ->
                    LabRow(frame, type, layout, layout.rows[index], upgrade, upgrade.id == selected?.id,
                        state.flash?.takeIf { it.id == upgrade.id }, scale, language, onAction)
                }
            }
        }
        if (selected != null) {
            val detail = layout.detailViewport
            val detailScroll = rememberScrollState()
            Box(
                Modifier.offset { IntOffset(detail.left.roundToInt(), detail.top.roundToInt()) }
                    .size(frame.dp(detail.width), frame.dp(detail.height))
                    .verticalScroll(detailScroll)
                    .testTag("profile-lab-detail"),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(frame.dp(layout.detailContentHeight))
                        .semantics {
                            contentDescription = selected.name.localizedContent(language) + ". " +
                                selected.description.localizedContent(language)
                        }
                        .drawBehind {
                            translate(-detail.left, -detail.top) { drawLabDetail(measurer, frame, type, layout, selected, language) }
                        },
                ) {
                    val buy = layout.buy
                    val label = if (selected.isMaxed) language.text(ProfileText.MaximumSynchrony) else language.text(ProfileScreensRedesignText.BuyRank)
                    ProfileSlabButton(
                        label = label,
                        onClick = { onAction(LabAction.PurchaseRequested(selected.id)) },
                        frame = frame,
                        tag = "profile-lab-purchase",
                        size = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD,
                        fontSize = type.buy,
                        enabled = selected.isAffordable && !selected.isMaxed,
                        cost = if (selected.isMaxed) null else formatMatter(selected.nextCost, language),
                        costScale = 1f,
                        spread = true,
                        textScale = scale,
                        contentDescription = if (selected.isMaxed) label else
                            label + " " + language.text(ProfileScreensRedesignText.CostMatter, formatMatter(selected.nextCost, language)),
                        modifier = Modifier.offset { IntOffset((buy.left - detail.left).roundToInt(), (buy.top - detail.top).roundToInt()) }
                            .size(frame.dp(buy.width), frame.dp(buy.height)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabRow(
    frame: ProfileFrame,
    type: LabType,
    layout: LabLayout,
    row: Rect,
    upgrade: LabUpgradeRenderModel,
    selected: Boolean,
    flash: LabPurchaseFlash?,
    textScale: Float,
    language: AppLanguage,
    onAction: (LabAction) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var pointerPress by remember { mutableStateOf(false) }
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(200, easing = KkEase.Pull), label = "labRow")
    val flashProgress = remember { Animatable(1f) }
    LaunchedEffect(flash) {
        if (flash != null) {
            flashProgress.snapTo(0f)
            flashProgress.animateTo(1f, tween(LAB_PURCHASE_FLASH_MS, easing = LinearEasing))
        }
    }
    // Hover moves the selection (a click then confirms); touch has no hover, so its first tap selects.
    LaunchedEffect(hovered) { if (hovered) onAction(LabAction.Select(upgrade.id)) }
    val measurer = rememberKkCanvasMeasurer(textScale)
    val name = upgrade.name.localizedContent(language)
    val rank = language.text(ProfileScreensRedesignText.RankOf, upgrade.rank, upgrade.maxRanks)
    val cost = formatMatter(upgrade.nextCost, language)
    val value = labRankValue(upgrade.modifierPerRank, upgrade.rank, language) ?: language.text(ProfileScreensRedesignText.NoValue)
    val stamp = language.text(ProfileText.MaximumSynchrony)
    Box(
        Modifier.offset { IntOffset(row.left.roundToInt(), row.top.roundToInt()) }
            .size(frame.dp(row.width), frame.dp(row.height))
            .testTag("profile-lab-buy-${upgrade.id}")
            .semantics {
                this.selected = selected
                contentDescription = name
                stateDescription = if (upgrade.isMaxed) "$rank $stamp" else "$rank ${language.text(ProfileScreensRedesignText.CostMatter, cost)}"
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerPress = true
                    }
                }
            }
            .onFocusChanged {
                if (it.isFocused && !pointerPress) onAction(LabAction.Select(upgrade.id))
                if (!it.isFocused) pointerPress = false
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { onAction(LabAction.Activate(upgrade.id)) }
            .drawBehind {
                drawLabRow(measurer, frame, type, layout.columns, upgrade, name, value, cost, stamp, selection, hovered, focused,
                    flashProgress.value)
            },
    )
}

/** Nudge of the purchase feedback: out to 8 px by 30 % and back by 100 % (Pull), in px. */
internal fun labFlashNudge(progress: Float, distance: Float): Float = when {
    progress <= 0f || progress >= 1f -> 0f
    progress < 0.3f -> distance * KkEase.Pull.transform(progress / 0.3f)
    else -> distance * (1f - KkEase.Pull.transform((progress - 0.3f) / 0.7f))
}

/** The one inverted frame of the purchase feedback (first 8 % of the animation). */
internal fun labFlashInverted(progress: Float): Boolean = progress >= 0f && progress < 0.08f

private fun DrawScope.drawLabRow(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: LabType,
    columns: LabRowColumns,
    upgrade: LabUpgradeRenderModel,
    name: String,
    value: String,
    cost: String,
    stamp: String,
    selection: Float,
    hovered: Boolean,
    focused: Boolean,
    flash: Float,
) {
    val k = frame.k
    val roles = measurer.roles
    val bounds = Rect(Offset.Zero, size)
    translate(labFlashNudge(flash, frame.d(8f)), 0f) {
        val fg = drawKkListRowBackground(bounds, roles, selected = selection, hovered = hovered)
        val on = selection > 0.5f
        translate(KK_LIST_ROW_SELECTED_SHIFT_DP * density * selection.coerceIn(0f, 1.2f), 0f) {
            val line1 = columns.firstLineY
            val line2 = columns.secondLineY
            drawLabIcon(upgrade.id, Offset(columns.iconCenterX, line1), columns.iconSize, fg)
            val nameLayout = fitKkText(measurer, name, type.rowName * k, columns.nameWidth, minFactor = 0.7f) {
                measurer.typography.condStyle(it, lineHeightEm = 1f)
            }
            drawKkText(nameLayout, columns.nameLeft, line1, fg, valign = KkVAlign.CENTER)
            val pipColor = if (on) Kk.Ink else roles.you
            drawKkPips(
                Offset(columns.pipsLeft, line2 - columns.pipHeight * 0.5f), upgrade.maxRanks, upgrade.rank, pipColor,
                sizeDp = columns.pipHeight / density, widthDp = columns.pipWidth / density, gapDp = columns.pipGap / density,
                emptyColor = if (on) Color(0xFF9C9B92) else Kk.Line2, nextOutlined = !upgrade.isMaxed, emptyOutlined = true,
            )
            val valueStyle = measurer.typography.condStyle(type.rowValue * k, tabular = true, lineHeightEm = 1f)
            val valueLayout = measureKkText(measurer, value, valueStyle, uppercase = true, maxWidth = columns.valueWidth)
            if (columns.twoLines) {
                drawKkText(valueLayout, columns.costRight, line2, fg, align = KkAlign.END, valign = KkVAlign.CENTER)
            } else {
                drawKkText(valueLayout, columns.valueLeft, line1, fg, valign = KkVAlign.CENTER)
            }
            if (upgrade.isMaxed) {
                val stampSize = kkStampSize(measurer, stamp, density, type.stamp * k)
                drawKkStamp(measurer, stamp, Offset(columns.costRight - stampSize.width, line1 - stampSize.height * 0.5f),
                    fontSize = type.stamp * k)
            } else {
                val costLayout = measureKkText(measurer, cost, valueStyle, uppercase = true)
                val gemSize = 11f * k
                val gemColor = when {
                    !upgrade.isAffordable -> Kk.Mute
                    on -> Kk.Ink
                    else -> roles.you
                }
                val costLeft = columns.costRight - costLayout.size.width
                drawKkGem(Offset(costLeft - frame.d(6f) - gemSize * density * 0.5f, line1), gemColor, gemSize)
                drawKkText(costLayout, costLeft, line1, fg, valign = KkVAlign.CENTER)
            }
        }
        if (labFlashInverted(flash)) drawRect(Color.White, blendMode = BlendMode.Difference)
        if (focused) drawRect(Kk.Bone, Offset(-density * 5f, -density * 5f), Size(size.width + density * 10f, size.height + density * 10f),
            style = kkStroke(density * 2f))
    }
}

private fun DrawScope.drawLabDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: LabType,
    layout: LabLayout,
    upgrade: LabUpgradeRenderModel,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    drawLabIcon(upgrade.id, layout.icon.center, layout.icon.width, roles.you)
    val name = fitKkText(measurer, upgrade.name.localizedContent(language), type.name * k, layout.name.width, minFactor = 0.55f) {
        measurer.typography.wideStyle(it, lineHeightEm = 1.05f)
    }
    drawKkText(name, layout.name.left, layout.name.center.y, Kk.Bone, valign = KkVAlign.CENTER)
    val descriptionLines = (layout.description.height / (frame.d(type.body) * 1.4f * measurer.scale)).toInt().coerceAtLeast(1)
    val description = measureKkText(measurer, upgrade.description.localizedContent(language),
        measurer.typography.bodyStyle(type.body * k), maxWidth = layout.description.width, maxLines = descriptionLines)
    drawKkText(description, layout.description.left, layout.description.top, Kk.Bone)
    val empty = language.text(ProfileScreensRedesignText.NoValue)
    val now = labRankValue(upgrade.modifierPerRank, upgrade.rank, language) ?: empty
    val next = if (upgrade.isMaxed) language.text(ProfileText.MaximumSynchrony) else
        labRankValue(upgrade.modifierPerRank, upgrade.rank + 1, language) ?: empty
    drawLabPanel(measurer, frame, type, layout.now, language.text(ProfileScreensRedesignText.Now), now, Kk.Mute, Kk.Bone, null)
    drawLabPanel(measurer, frame, type, layout.next, language.text(ProfileScreensRedesignText.NextRank), next, roles.you, roles.you, roles.you)
    drawKkText(measurer, language.text(ProfileScreensRedesignText.RankOf, upgrade.rank, upgrade.maxRanks),
        measurer.typography.monoStyle(type.mono * k), layout.rank.left, layout.rank.top, Kk.Mute, uppercase = true)
}

private fun DrawScope.drawLabPanel(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: LabType,
    rect: Rect,
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    underline: Color?,
) {
    val k = frame.k
    drawRect(Kk.Ink2, rect.topLeft, rect.size)
    if (underline != null) drawRect(underline, Offset(rect.left, rect.bottom - frame.d(3f)), Size(rect.width, frame.d(3f)))
    val padX = frame.d(if (frame.regular) 14f else 10f)
    val padY = frame.d(if (frame.regular) 12f else 8f)
    val labelLayout = measureKkText(measurer, label, measurer.typography.monoStyle(type.mono * k), uppercase = true,
        maxWidth = rect.width - padX * 2f)
    drawKkText(labelLayout, rect.left + padX, rect.top + padY, labelColor)
    val valueLayout = fitKkText(measurer, value, type.panelValue * k, rect.width - padX * 2f, minFactor = 0.6f) {
        measurer.typography.wideStyle(it, tabular = true)
    }
    drawKkText(valueLayout, rect.left + padX, rect.bottom - padY - valueLayout.kkBoxHeight, valueColor)
}

private fun DrawScope.drawLabBackground(frame: ProfileFrame, layout: LabLayout?) {
    drawRect(Kk.Ink)
    drawProfileGrid(frame)
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> drawProfileSidePanel(frame.x(1010f))
        ProfileLayoutMode.COMPACT_LANDSCAPE -> if (layout != null) {
            drawProfileSidePanel(layout.detailViewport.left - size.height * KkShape.ShearRatio * 0.5f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> if (layout != null) {
            val top = layout.detailViewport.top
            drawRect(Kk.Ink1, Offset(0f, top), Size(size.width, size.height - top))
            drawLine(Kk.Line, Offset(0f, top), Offset(size.width, top), density)
        }
    }
}

/**
 * Lab upgrade icons from the Lab board (24-unit grid, stroke 2, square caps, miter joins; the
 * `f` layer is filled). `icons.json` has no Lab set, so the screen keeps these privately.
 */
private val LabIconData: Map<MetaUpgradeId, Pair<String, String?>> = mapOf(
    MetaUpgradeId.CORE_INTEGRITY to ("M12 2 21 7v10l-9 5-9-5V7Z" to null),
    MetaUpgradeId.KINETIC_AMPLIFIER to ("M3 8h9v8H3ZM16 4v16M19 7v10" to null),
    MetaUpgradeId.MAGNETIC_RESONANCE to ("M5 3v9a7 7 0 0 0 14 0V3h-4v9a3 3 0 0 1-6 0V3Z" to null),
    MetaUpgradeId.CRYO_VENTS to ("M12 2v20M3.5 7l17 10M20.5 7l-17 10" to null),
    MetaUpgradeId.DASH_CAPACITOR to ("M2 7h7M2 12h9M2 17h7M13 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0" to null),
    MetaUpgradeId.SALVAGE_PROTOCOL to ("M12 2 20 7v10l-8 5-8-5V7ZM4 7l8 5 8-5M12 12v10" to null),
    MetaUpgradeId.DATA_ARCHIVE to ("M12 3 20 12 12 21 4 12Z" to "M12 8 16 12 12 16 8 12Z"),
    MetaUpgradeId.ARMORY_LICENSE to ("M12 2v6M12 16v6M2 12h6M16 12h6M7 12a5 5 0 1 0 10 0a5 5 0 1 0-10 0" to null),
)

/** Parsed once per icon on first draw (draw-thread confined). */
private object LabIconPaths {
    private val strokes = arrayOfNulls<Path>(MetaUpgradeId.entries.size)
    private val fills = arrayOfNulls<Path>(MetaUpgradeId.entries.size)
    private val parsed = BooleanArray(MetaUpgradeId.entries.size)
    val stroke = Stroke(2f, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter)

    fun stroke(id: MetaUpgradeId): Path? { parse(id); return strokes[id.ordinal] }
    fun fill(id: MetaUpgradeId): Path? { parse(id); return fills[id.ordinal] }

    private fun parse(id: MetaUpgradeId) {
        if (parsed[id.ordinal]) return
        val data = LabIconData[id]
        strokes[id.ordinal] = data?.first?.let { PathParser().parsePathString(it).toPath() }
        fills[id.ordinal] = data?.second?.let { PathParser().parsePathString(it).toPath() }
        parsed[id.ordinal] = true
    }
}

internal fun DrawScope.drawLabIcon(id: MetaUpgradeId, center: Offset, size: Float, color: Color) {
    if (size <= 0f) return
    val iconScale = size / KK_ICON_GRID
    translate(center.x - size * 0.5f, center.y - size * 0.5f) {
        scale(iconScale, iconScale, Offset.Zero) {
            LabIconPaths.stroke(id)?.let { drawPath(it, color, style = LabIconPaths.stroke) }
            LabIconPaths.fill(id)?.let { drawPath(it, color, style = Fill) }
        }
    }
}
