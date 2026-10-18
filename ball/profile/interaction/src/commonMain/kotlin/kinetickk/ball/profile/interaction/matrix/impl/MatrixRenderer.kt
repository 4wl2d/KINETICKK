// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.PROFILE_SMALLEST_TEXT_SCALE
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.ProfilePanel
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.ball.profile.interaction.drawProfileSidePanel
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.fitsWhole
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.matrix.api.MatrixNodeRenderModel
import kinetickk.ball.profile.interaction.matrix.api.MatrixNodeStatus
import kinetickk.ball.profile.interaction.matrix.api.MatrixRenderModel
import kinetickk.ball.profile.interaction.profileButtonNaturalWidth
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.ball.profile.interaction.profileScrollBar
import kinetickk.ball.profile.interaction.profileScrollCue
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Latest geometry for the background pass (written during composition). */
internal class MatrixLayoutHolder {
    var layout: MatrixLayout? = null
}

/** The Matrix screen; [textScale] is the player's text-size setting (1.25 = the boards' size). */
@Composable
internal fun MatrixContent(
    state: MatrixState,
    textScale: Float,
    gridScroll: ScrollState,
    holder: MatrixLayoutHolder = remember { MatrixLayoutHolder() },
    onAction: (MatrixAction) -> Unit,
) {
    val scale = profileTextScale(textScale)
    val language = LocalAppLanguage.current
    val model = state.model
    val (owned, total) = model.nodeTotals()
    val headerMeasurer = rememberKkCanvasMeasurer(scale)
    ProfilePanel(
        title = language.text(ProfileText.MatrixTitle),
        count = language.text(ProfileScreensRedesignText.CountOf, owned, total),
        info = language.text(ProfileScreensRedesignText.MatrixInfo),
        matter = model.matter,
        scale = scale,
        tag = "profile-matrix",
        onBack = { onAction(MatrixAction.Back) },
        background = { frame -> drawMatrixBackground(frame, holder.layout) },
        headerExtra = { frame -> MatrixResetButton(frame, state, headerMeasurer, scale, language, onAction) },
        headerExtraWidth = { frame -> matrixResetWidth(headerMeasurer, frame, language) },
    ) { frame ->
        val measurer = rememberKkCanvasMeasurer(scale)
        val type = matrixType(frame.mode)
        val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
        val selected = state.selectedNode
        val layout = matrixLayout(frame, model, scale, backWidth) { width ->
            if (selected == null) 0f else matrixDescriptionLayout(measurer, frame, type, selected, language, width).kkBoxHeight
        }
        SideEffect { holder.layout = layout }
        val grid = layout.gridViewport
        val selectedIndex = model.nodes.indexOfFirst { it.id == selected?.id }
        LaunchedEffect(selectedIndex, grid.height, layout.gridContentHeight) {
            val tile = layout.tiles.getOrNull(selectedIndex) ?: return@LaunchedEffect
            val target = matrixRevealScroll(gridScroll.value.toFloat(), tile, grid.height, layout.gridScrollMax,
                matrixGridFade(frame)) ?: return@LaunchedEffect
            gridScroll.animateScrollTo(target.roundToInt().coerceAtMost(gridScroll.maxValue))
        }
        val links = remember(layout, model) { matrixLinks(layout, model) }
        Box(
            Modifier.offset { IntOffset(grid.left.roundToInt(), grid.top.roundToInt()) }
                .size(frame.dp(grid.width), frame.dp(grid.height))
                .semantics { contentDescription = language.text(ProfileScreensRedesignText.MatrixNodes) }
                .profileScrollBar(gridScroll, grid.width - matrixScrollBarWidth(frame), matrixScrollBarWidth(frame),
                    frame.d(MATRIX_GRID_PAD), Kk.Mute, Kk.Line2, "matrix.grid.thumb")
                .profileScrollCue(gridScroll, Kk.Ink, matrixGridFade(frame))
                .verticalScroll(gridScroll)
                .testTag("profile-matrix-scroll"),
        ) {
            Box(
                Modifier.fillMaxWidth().height(frame.dp(layout.gridContentHeight))
                    .drawBehind { drawMatrixGridBackdrop(measurer, frame, type, layout, model, links, language) },
            ) {
                model.nodes.forEachIndexed { index, node ->
                    MatrixTile(frame, type, layout.tiles[index], node, node.id == selected?.id,
                        state.flash?.takeIf { it.id == node.id }, scale, language, onAction)
                }
            }
        }
        if (selected != null) {
            val detail = layout.detailViewport
            val detailScroll = remember(selected.id) { ScrollState(0) }
            Box(
                Modifier.offset { IntOffset(detail.left.roundToInt(), detail.top.roundToInt()) }
                    .size(frame.dp(detail.width), frame.dp(detail.height))
                    .profileScrollCue(detailScroll, Kk.Ink1, frame.d(24f))
                    .verticalScroll(detailScroll)
                    .testTag("profile-matrix-detail"),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(frame.dp(layout.detailContentHeight))
                        .semantics {
                            contentDescription = selected.name.localizedContent(language) + ". " +
                                selected.description.localizedContent(language) + " " +
                                matrixStatusDetail(selected, model, language)
                        }
                        .drawBehind {
                            translate(-detail.left, -detail.top) { drawMatrixDetail(measurer, frame, type, layout, selected, model, language) }
                        },
                )
            }
            val buy = layout.buy
            if (layout.buyPinned) {
                Box(Modifier.offset { IntOffset(detail.left.roundToInt(), detail.bottom.roundToInt()) }
                    .size(frame.dp(detail.width), frame.dp(buy.bottom + frame.d(8f) - detail.bottom))
                    .drawBehind { drawRect(Kk.Ink1) })
            }
            val label = if (selected.isMaxed) language.text(ProfileText.MaximumSynchrony) else language.text(ProfileScreensRedesignText.BuyRank)
            val cost = if (selected.isMaxed) null else formatMatter(selected.nextCost, language)
            ProfileSlabButton(
                label = label,
                onClick = { onAction(MatrixAction.PurchaseRequested(selected.id)) },
                frame = frame,
                tag = "profile-matrix-purchase",
                size = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD,
                fontSize = type.buy,
                enabled = selected.canBuy,
                locked = selected.status == MatrixNodeStatus.REBIRTH_LOCKED || selected.status == MatrixNodeStatus.PREREQUISITE_MISSING,
                cost = cost,
                costScale = 1f,
                spread = true,
                textScale = scale,
                contentDescription = if (cost == null) label else
                    label + " " + language.text(ProfileScreensRedesignText.CostMatter, cost),
                stateDescription = matrixStatusDetail(selected, model, language),
                modifier = Modifier.offset { IntOffset(buy.left.roundToInt(), buy.top.roundToInt()) }
                    .size(frame.dp(buy.width), frame.dp(buy.height)),
            )
        }
    }
}

/** Header Reset: two presses (arm, confirm); disabled while no node is owned. */
@Composable
private fun RowScope.MatrixResetButton(
    frame: ProfileFrame,
    state: MatrixState,
    measurer: CanvasTextMeasurer,
    textScale: Float,
    language: AppLanguage,
    onAction: (MatrixAction) -> Unit,
) {
    val label = language.text(if (state.resetArmed) ProfileScreensRedesignText.Confirm else ProfileScreensRedesignText.Reset)
    val width = matrixResetWidth(measurer, frame, language) - frame.d(12f)
    val height = frame.d(if (frame.regular) 38f else 30f)
    val refund = language.text(ProfileScreensRedesignText.ResetRefund, formatMatter(state.model.refund, language))
    ProfileSlabButton(
        label = label,
        onClick = { onAction(MatrixAction.ResetRequested) },
        frame = frame,
        tag = "profile-matrix-reset",
        variant = if (state.resetArmed) KkButtonVariant.HAZARD else KkButtonVariant.GHOST,
        size = if (frame.regular) KkButtonSize.SM else KkButtonSize.XS,
        enabled = state.model.ownedNodeCount > 0,
        armed = state.resetArmed,
        textScale = textScale,
        contentDescription = label,
        stateDescription = refund,
        modifier = Modifier.size(frame.dp(width), frame.dp(height)),
    )
    androidx.compose.foundation.layout.Spacer(Modifier.size(frame.dp(frame.d(12f)), frame.dp(1f)))
}

/** Header room for Reset: the wider of its two labels plus a 12 dp gap. */
internal fun matrixResetWidth(measurer: CanvasTextMeasurer, frame: ProfileFrame, language: AppLanguage): Float {
    val size = if (frame.regular) KkButtonSize.SM else KkButtonSize.XS
    return max(
        profileButtonNaturalWidth(measurer, frame, language.text(ProfileScreensRedesignText.Reset), size),
        profileButtonNaturalWidth(measurer, frame, language.text(ProfileScreensRedesignText.Confirm), size),
    ) + frame.d(12f)
}

@Composable
private fun MatrixTile(
    frame: ProfileFrame,
    type: MatrixType,
    tile: Rect,
    node: MatrixNodeRenderModel,
    selected: Boolean,
    flash: MatrixPurchaseFlash?,
    textScale: Float,
    language: AppLanguage,
    onAction: (MatrixAction) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var pointerPress by remember { mutableStateOf(false) }
    val selection = if (selected) 1f else 0f
    val flashProgress = remember { Animatable(1f) }
    LaunchedEffect(flash) {
        if (flash != null) {
            flashProgress.snapTo(0f)
            flashProgress.animateTo(1f, tween(MATRIX_PURCHASE_FLASH_MS, easing = LinearEasing))
        }
    }
    // Hover only highlights: the selection (which Buy rank acts on) moves on a press, a tap or keyboard focus, so
    // crossing other tiles on the way to the detail panel leaves it alone.
    val measurer = rememberKkCanvasMeasurer(textScale)
    val name = node.name.localizedContent(language)
    val rank = language.text(ProfileScreensRedesignText.RankOf, node.rank, node.maxRanks)
    val status = matrixTileStatus(node, language)
    Box(
        Modifier.offset { IntOffset(tile.left.roundToInt(), tile.top.roundToInt()) }
            .size(frame.dp(tile.width), frame.dp(tile.height))
            .testTag("profile-matrix-node-${node.id}")
            .semantics {
                this.selected = selected
                contentDescription = name
                stateDescription = "$rank. $status"
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerPress = true
                    }
                }
            }
            // Keyboard focus (arrows, Tab) moves the selection; focus that comes with a press does not,
            // so the first press only selects and a press (or Enter) on the selected tile buys.
            .onFocusChanged {
                if (it.isFocused && !pointerPress) onAction(MatrixAction.Select(node.id))
                if (!it.isFocused) pointerPress = false
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { onAction(MatrixAction.Activate(node.id)) }
            .drawBehind {
                drawMatrixTile(measurer, frame, type, node, name, status, selection, hovered, focused, flashProgress.value)
            },
    )
}

/**
 * A tile name in [width] × [height], up to three lines: the largest size (down to half) at which the
 * whole name fits, and never smaller than the name is at the smallest text setting when that fits.
 */
internal fun matrixTileNameLayout(measurer: CanvasTextMeasurer, size: Float, name: String, width: Float, height: Float): TextLayoutResult {
    fun at(fontSize: Float) = measureKkText(measurer, name, measurer.typography.condStyle(fontSize, lineHeightEm = 1.02f),
        uppercase = true, maxWidth = width.coerceAtLeast(1f), maxLines = 3)
    fun fits(layout: TextLayoutResult) = layout.fitsWhole() && layout.size.height <= height + 0.5f
    var factor = 1f
    var layout = at(size)
    while (!fits(layout) && factor > 0.5f) {
        factor = max(0.5f, factor - 0.05f)
        layout = at(size * factor)
    }
    val smallest = PROFILE_SMALLEST_TEXT_SCALE / measurer.scale
    if (smallest < 1f) {
        val floor = at(size * smallest)
        if (fits(floor) && floor.layoutInput.style.fontSize.value > layout.layoutInput.style.fontSize.value) return floor
    }
    return layout
}

/** The short lock reason or price a tile shows under its pips. */
internal fun matrixTileStatus(node: MatrixNodeRenderModel, language: AppLanguage): String = when (node.status) {
    MatrixNodeStatus.MAXED -> language.text(ProfileText.MaximumSynchrony)
    MatrixNodeStatus.REBIRTH_LOCKED -> language.text(ProfileScreensRedesignText.TileRebornLock, node.minimumRebirth)
    MatrixNodeStatus.PREREQUISITE_MISSING -> language.text(ProfileScreensRedesignText.PrerequisiteLock)
    MatrixNodeStatus.SHORT_OF_MATTER, MatrixNodeStatus.AVAILABLE ->
        language.text(ProfileScreensRedesignText.CostMatter, formatMatter(node.nextCost, language))
}

/** The full status sentence of the detail panel: why the node is locked, what is missing, or that it is ready. */
internal fun matrixStatusDetail(node: MatrixNodeRenderModel, model: MatrixRenderModel, language: AppLanguage): String = when (node.status) {
    MatrixNodeStatus.MAXED -> language.text(ProfileScreensRedesignText.NodeMaxed)
    MatrixNodeStatus.REBIRTH_LOCKED -> language.text(ProfileScreensRedesignText.RebornLockDetail, node.minimumRebirth)
    MatrixNodeStatus.PREREQUISITE_MISSING -> {
        val names = node.prerequisites.mapNotNull { id -> model.node(id)?.name?.localizedContent(language) }
        if (names.size >= 2) language.text(ProfileScreensRedesignText.RequiresEitherNode, names[0], names[1])
        else language.text(ProfileScreensRedesignText.RequiresNode, names.firstOrNull().orEmpty())
    }
    MatrixNodeStatus.SHORT_OF_MATTER ->
        language.text(ProfileScreensRedesignText.NeedMore, formatMatter(node.nextCost - model.matter, language))
    MatrixNodeStatus.AVAILABLE -> language.text(ProfileScreensRedesignText.NodeAvailable)
}

/** The colour of a branch (role colours, so the Color vision setting remaps them). */
internal fun matrixBranchColor(branch: MatrixBranch, roles: KkRolePalette): Color = when (branch) {
    MatrixBranch.KINETIC -> roles.kinetic
    MatrixBranch.BULWARK -> roles.shield
    MatrixBranch.SYSTEMS -> roles.pol
    MatrixBranch.FORTUNE -> roles.gold.base
}

private fun matrixBranchIcon(branch: MatrixBranch): KkIcon = when (branch) {
    MatrixBranch.KINETIC -> KkIcon.TAG_RAM
    MatrixBranch.BULWARK -> KkIcon.TAG_BULWARK
    MatrixBranch.SYSTEMS -> KkIcon.SYSTEM_DASH
    MatrixBranch.FORTUNE -> KkIcon.TAG_FORTUNE
}

/** Nudge of the purchase feedback: out to 8 px by 30 % and back by 100 % (Pull), in px. */
internal fun matrixFlashNudge(progress: Float, distance: Float): Float = when {
    progress <= 0f || progress >= 1f -> 0f
    progress < 0.3f -> distance * KkEase.Pull.transform(progress / 0.3f)
    else -> distance * (1f - KkEase.Pull.transform((progress - 0.3f) / 0.7f))
}

private fun DrawScope.drawMatrixTile(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: MatrixType,
    node: MatrixNodeRenderModel,
    name: String,
    status: String,
    selection: Float,
    hovered: Boolean,
    focused: Boolean,
    flash: Float,
) {
    val k = frame.k
    val roles = measurer.roles
    val accent = matrixBranchColor(node.branch, roles)
    val locked = node.status == MatrixNodeStatus.REBIRTH_LOCKED || node.status == MatrixNodeStatus.PREREQUISITE_MISSING
    val inverted = flash >= 0f && flash < 0.08f
    val on = selection > 0.5f
    val cutDp = (if (frame.regular) 12f else 9f) * k
    translate(matrixFlashNudge(flash, frame.d(6f)), 0f) {
        val bounds = Rect(Offset.Zero, size)
        // The selected tile keeps its row so the prerequisite lines stay attached; the echo still slides out.
        translate(0f, d(8f) * selection.coerceIn(0f, 1.2f)) {
            drawKkTile(measurer, bounds, selected = if (inverted) 1f else selection, hovered = hovered, locked = locked,
                cutDp = cutDp, focused = focused)
        }
        val fg = when {
            locked && on -> Kk.Bone
            locked -> Kk.Mute
            on || inverted -> Kk.Ink
            else -> Kk.Bone
        }
        val pad = frame.d(if (frame.regular) 10f else 7f)
        // Branch marks invert to ink on a selected light face; a locked tile keeps its dark face, so they keep the branch colour.
        val markInk = if (on && !locked) Kk.Ink else accent
        // Owned nodes carry a branch-coloured bar; keystones add a diamond.
        if (node.owned) drawRect(markInk, Offset(pad, frame.d(4f)), Size(size.width - pad * 2f, frame.d(3f)))
        if (node.keystone) {
            val gem = frame.d(if (frame.regular) 9f else 7f)
            drawKkGem(Offset(size.width - pad - gem * 0.5f, pad + gem * 0.6f), markInk, gem / density)
        }
        val nameRight = size.width - pad - if (node.keystone) frame.d(14f) else 0f
        val iconSize = frame.d(if (frame.regular) 12f else 10f)
        val statusX = if (node.status == MatrixNodeStatus.MAXED) pad else pad + iconSize + frame.d(5f)
        // The status fits where it is drawn: from after its icon to the padding inside the slab's slanted right edge,
        // which leans in by the cut toward the bottom.
        val statusWidth = size.width - statusX - pad - d(cutDp) * (1f - pad / size.height)
        val statusLayout = fitKkText(measurer, status, type.tileStatus * k, statusWidth, minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        val statusY = size.height - pad - statusLayout.kkBoxHeight
        val pipHeight = frame.d(if (frame.regular) 9f else 7f)
        val pipWidth = frame.d(if (frame.regular) 16f else 11f)
        val pipsTop = statusY - frame.d(6f) - pipHeight
        val nameTop = pad + frame.d(3f)
        val nameLayout = matrixTileNameLayout(measurer, type.tileName * k, name, nameRight - pad, pipsTop - frame.d(4f) - nameTop)
        drawProfileText(nameLayout, pad, nameTop, fg, "matrix.tile.name", node.id)
        val statusColor = when (node.status) {
            MatrixNodeStatus.AVAILABLE -> if (on) Kk.Ink else roles.you
            MatrixNodeStatus.MAXED -> if (on) Kk.Ink else accent
            MatrixNodeStatus.SHORT_OF_MATTER -> if (on) Kk.Ink else Kk.Mute
            else -> fg
        }
        when (node.status) {
            MatrixNodeStatus.REBIRTH_LOCKED, MatrixNodeStatus.PREREQUISITE_MISSING ->
                drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(pad + iconSize * 0.5f, statusY + statusLayout.kkBoxHeight * 0.5f), iconSize, statusColor)
            MatrixNodeStatus.SHORT_OF_MATTER, MatrixNodeStatus.AVAILABLE ->
                drawKkGem(Offset(pad + iconSize * 0.5f, statusY + statusLayout.kkBoxHeight * 0.5f), statusColor, iconSize / density)
            MatrixNodeStatus.MAXED -> Unit
        }
        drawProfileText(statusLayout, statusX, statusY, statusColor, "matrix.tile.status", node.id)
        val pipsWidth = drawKkPips(
            Offset(pad, pipsTop), node.maxRanks, node.rank,
            markInk,
            sizeDp = pipHeight / density, widthDp = pipWidth / density, gapDp = 3f,
            emptyColor = if (on) Color(0xFF9C9B92) else Kk.Line2, nextOutlined = node.canBuy, emptyOutlined = true,
        )
        ProfileTextProbe.record("matrix.tile.pips", node.id, null, Rect(pad, pipsTop, pad + pipsWidth, pipsTop + pipHeight))
        ProfileTextProbe.record("matrix.tile", node.id, null, bounds)
    }
}

/** Branch headers and prerequisite lines, drawn under the tiles in grid content coordinates. */
private fun DrawScope.drawMatrixGridBackdrop(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: MatrixType,
    layout: MatrixLayout,
    model: MatrixRenderModel,
    links: List<MatrixLink>,
    language: AppLanguage,
) {
    val roles = measurer.roles
    MatrixBranch.entries.forEach { branch ->
        val header = layout.branchHeaders[branch.ordinal]
        val accent = matrixBranchColor(branch, roles)
        val iconSize = header.height * 0.62f
        drawKkIcon(matrixBranchIcon(branch), Offset(header.left + iconSize * 0.5f, header.center.y), iconSize, accent)
        val inBranch = model.nodes.filter { it.branch == branch }
        val count = language.text(ProfileScreensRedesignText.CountOf, inBranch.count { it.owned }, inBranch.size)
        val countLayout = fitKkText(measurer, count, type.tileStatus * frame.k, header.width * 0.3f, minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawProfileText(countLayout, header.right, header.center.y, Kk.Mute, "matrix.branch.count", branch, align = KkAlign.END,
            valign = KkVAlign.CENTER)
        val labelLeft = header.left + iconSize + frame.d(8f)
        val label = fitKkText(measurer, branch.displayLabel.localizedContent(language), type.branch * frame.k,
            header.right - countLayout.size.width - frame.d(8f) - labelLeft, minFactor = 0.5f) {
            measurer.typography.condStyle(it, lineHeightEm = 1f)
        }
        drawProfileText(label, labelLeft, header.center.y, Kk.Bone, "matrix.branch.label", branch, valign = KkVAlign.CENTER)
        drawLine(kkMix(Kk.Ink, accent, 0.6f), Offset(header.left, header.bottom), Offset(header.right, header.bottom), frame.density * 2f)
    }
    val stroke = frame.d(if (frame.regular) 2f else 1.5f)
    links.forEach { link ->
        val color = if (link.owned) Kk.Bone2 else Kk.Line2
        // An elbow: down from the prerequisite, across in the gap above the node, then down into it.
        drawLine(color, link.from, Offset(link.from.x, link.bendY), stroke)
        drawLine(color, Offset(link.from.x, link.bendY), Offset(link.to.x, link.bendY), stroke)
        drawLine(color, Offset(link.to.x, link.bendY), link.to, stroke)
    }
}

/** The selected node's description wrapped to [width] (every line; the details scroll). */
internal fun matrixDescriptionLayout(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: MatrixType,
    node: MatrixNodeRenderModel,
    language: AppLanguage,
    width: Float,
) = measureKkText(measurer, node.description.localizedContent(language), measurer.typography.bodyStyle(type.body * frame.k),
    maxWidth = width, maxLines = PROFILE_DESCRIPTION_MAX_LINES)

private fun DrawScope.drawMatrixDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: MatrixType,
    layout: MatrixLayout,
    node: MatrixNodeRenderModel,
    model: MatrixRenderModel,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    val accent = matrixBranchColor(node.branch, roles)
    val tagText = buildString {
        append(node.branch.displayLabel.localizedContent(language))
        if (node.keystone) append(" / ").append(language.text(ProfileScreensRedesignText.Keystone))
        if (node.minimumRebirth > 0) append(" / ").append(language.text(ProfileScreensRedesignText.RebornLock, node.minimumRebirth))
    }
    val tag = fitKkText(measurer, tagText, type.mono * k, layout.tag.width, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(tag, layout.tag.left, layout.tag.top, accent, "matrix.detail.tag")
    val name = fitKkText(measurer, node.name.localizedContent(language), type.name * k, layout.name.width, minFactor = 0.4f) {
        measurer.typography.wideStyle(it, lineHeightEm = 1.05f)
    }
    drawProfileText(name, layout.name.left, layout.name.center.y, Kk.Bone, "matrix.detail.name", valign = KkVAlign.CENTER)
    drawProfileText(matrixDescriptionLayout(measurer, frame, type, node, language, layout.description.width),
        layout.description.left, layout.description.top, Kk.Bone, "matrix.detail.description")
    val rankText = fitKkText(measurer, language.text(ProfileScreensRedesignText.RankOf, node.rank, node.maxRanks), type.mono * k,
        layout.rank.width * 0.45f, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(rankText, layout.rank.left, layout.rank.center.y, Kk.Mute, "matrix.detail.rank", valign = KkVAlign.CENTER)
    val pipHeight = frame.d(if (frame.regular) 16f else 11f)
    drawKkPips(
        Offset(layout.rank.left + rankText.size.width + frame.d(12f), layout.rank.center.y - pipHeight * 0.5f),
        node.maxRanks, node.rank, accent, sizeDp = pipHeight / density, widthDp = pipHeight * 1.3f / density,
        nextOutlined = node.canBuy, emptyOutlined = true,
    )
    val statusColor = when (node.status) {
        MatrixNodeStatus.AVAILABLE -> roles.you
        MatrixNodeStatus.MAXED -> accent
        MatrixNodeStatus.SHORT_OF_MATTER -> Kk.Mute
        MatrixNodeStatus.REBIRTH_LOCKED, MatrixNodeStatus.PREREQUISITE_MISSING -> roles.threat
    }
    val status = fitKkText(measurer, matrixStatusDetail(node, model, language), type.body * k, layout.status.width, maxLines = 2,
        minFactor = 0.6f) { measurer.typography.bodyStyle(it) }
    drawProfileText(status, layout.status.left, layout.status.top, statusColor, "matrix.detail.status")
}

private fun DrawScope.drawMatrixBackground(frame: ProfileFrame, layout: MatrixLayout?) {
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
