// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
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
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.LocalProfilePanelFocus
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.ProfilePanel
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.armory.api.ArmoryRenderModel
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.ball.profile.interaction.drawProfileSidePanel
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.profileButtonNaturalWidth
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Latest geometry for the background pass and page steps (written during composition). */
internal class ArmoryLayoutHolder {
    var layout: ArmoryLayout? = null
}

private val WeaponIcons: Map<WeaponId, KkIcon?> =
    WeaponId.entries.associateWith { KkIcon.byKey("weapons." + it.name.lowercase()) }

internal fun armoryWeaponIcon(id: WeaponId): KkIcon? = WeaponIcons[id]

@Composable
internal fun ArmoryContent(
    model: ArmoryRenderModel,
    weapons: List<WeaponDefinition>,
    masteries: List<WeaponMastery>,
    state: ArmoryViewState,
    textScale: Float,
    gridScroll: ScrollState,
    holder: ArmoryLayoutHolder,
    onAction: (ArmoryAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val title = language.text(ProfileText.ArmoryTitle)
    val unlocked = weapons.count { it.id in model.unlockedWeapons }
    val count = language.text(ProfileScreensRedesignText.CountOf, unlocked, weapons.size)
    val outlineMeasurer = rememberKkCanvasMeasurer(1f)
    ProfilePanel(
        title = title,
        count = count,
        info = language.text(ProfileScreensRedesignText.ArmoryInfo),
        matter = model.totalMatter,
        scale = textScale,
        tag = "profile-armory",
        onBack = { onAction(ArmoryAction.Back) },
        background = { frame -> drawArmoryBackground(frame, holder.layout, outlineMeasurer, title) },
        headerExtra = { frame -> ArmoryPager(frame, gridScroll, holder, textScale, onAction) },
    ) { frame ->
        val measurer = rememberKkCanvasMeasurer(textScale)
        val type = armoryType(frame.mode)
        val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
        val inspected = weapons.firstOrNull { it.id == state.inspected } ?: weapons.first()
        val status = model.status(inspected)
        val action = armoryActionPresentation(status, inspected, language)
        val buttonSize = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD
        val actionWidth = max(
            profileButtonNaturalWidth(measurer, frame, action.label, buttonSize, type.action, action.cost),
            frame.d(if (frame.regular) 240f else 180f),
        )
        val layout = armoryLayout(frame, weapons.size, textScale, backWidth, actionWidth)
        SideEffect { holder.layout = layout }
        val grid = layout.gridViewport
        Box(
            Modifier.offset { IntOffset(grid.left.roundToInt(), grid.top.roundToInt()) }
                .size(frame.dp(grid.width), frame.dp(grid.height))
                .semantics { contentDescription = language.text(ProfileScreensRedesignText.WeaponsList) }
                .verticalScroll(gridScroll)
                .testTag("profile-armory-scroll"),
        ) {
            Box(Modifier.fillMaxWidth().height(frame.dp(layout.gridContentHeight))) {
                weapons.forEachIndexed { index, definition ->
                    val tile = layout.tiles[index]
                    ArmoryTile(frame, type, tile, definition, model, definition.id == state.inspected, index, textScale, language, onAction)
                }
            }
        }
        val detailScroll = rememberScrollState()
        val detail = layout.detailViewport
        Box(
            Modifier.offset { IntOffset(detail.left.roundToInt(), detail.top.roundToInt()) }
                .size(frame.dp(detail.width), frame.dp(detail.height))
                .verticalScroll(detailScroll)
                .testTag("profile-armory-detail"),
        ) {
            Box(
                Modifier.fillMaxWidth().height(frame.dp(layout.detailContentHeight))
                    .semantics(mergeDescendants = false) {
                        contentDescription = inspected.name.localizedContent(language) + ". " +
                            inspected.description.localizedContent(language)
                    }
                    .drawBehind {
                        translate(-detail.left, -detail.top) {
                            drawArmoryDetail(measurer, frame, type, layout, inspected, status,
                                model.activeRunWeapon == inspected.id, model.totalMatter, masteries, language)
                        }
                    },
            ) {
                val masteryLabelWidth = measureKkText(measurer, language.text(ProfileScreensRedesignText.Mastery),
                    measurer.typography.labelStyle(type.label * frame.k), uppercase = true).size.width
                val infoSize = frame.density * 24f
                val infoLeft = layout.mastery.left + masteryLabelWidth + frame.d(10f) - detail.left
                val infoTop = layout.mastery.center.y - infoSize * 0.5f - detail.top
                Box(Modifier.offset { IntOffset(infoLeft.roundToInt(), infoTop.roundToInt()) }) {
                    KkInfoButton(language.text(ProfileScreensRedesignText.MasteryInfo), Modifier.testTag("profile-armory-mastery-info"),
                        placement = KkTooltipPlacement.BELOW, textScale = textScale)
                }
                val button = layout.action
                ProfileSlabButton(
                    label = action.label,
                    onClick = { onAction(ArmoryAction.Apply(inspected.id)) },
                    frame = frame,
                    tag = "profile-armory-action",
                    variant = action.variant,
                    size = buttonSize,
                    fontSize = type.action,
                    enabled = status.actionEnabled,
                    locked = status == ArmoryWeaponStatus.SHORT,
                    lockIcon = false,
                    cost = action.cost,
                    textScale = textScale,
                    contentDescription = listOfNotNull(action.label, action.costDescription).joinToString(" "),
                    modifier = Modifier.offset { IntOffset((button.left - detail.left).roundToInt(), (button.top - detail.top).roundToInt()) }
                        .size(frame.dp(button.width), frame.dp(button.height)),
                )
            }
        }
    }
}

internal class ArmoryActionPresentation(
    val label: String,
    val cost: String?,
    val costDescription: String?,
    val variant: KkButtonVariant,
)

internal fun armoryActionPresentation(status: ArmoryWeaponStatus, definition: WeaponDefinition, language: AppLanguage): ArmoryActionPresentation {
    val cost = formatMatter(definition.permanentUnlockCost.toLong(), language)
    val costDescription = language.text(ProfileScreensRedesignText.CostMatter, cost)
    return when (status) {
        ArmoryWeaponStatus.STARTER -> ArmoryActionPresentation(language.text(ProfileScreensRedesignText.Starter), null, null, KkButtonVariant.GHOST)
        ArmoryWeaponStatus.OWNED -> ArmoryActionPresentation(language.text(ProfileScreensRedesignText.SetAsStarter), null, null, KkButtonVariant.GHOST)
        ArmoryWeaponStatus.AFFORDABLE, ArmoryWeaponStatus.SHORT ->
            ArmoryActionPresentation(language.text(ProfileScreensRedesignText.Unlock), cost, costDescription, KkButtonVariant.PRIMARY)
    }
}

@Composable
private fun ArmoryTile(
    frame: ProfileFrame,
    type: ArmoryType,
    tile: Rect,
    definition: WeaponDefinition,
    model: ArmoryRenderModel,
    inspected: Boolean,
    index: Int,
    textScale: Float,
    language: AppLanguage,
    onAction: (ArmoryAction) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var pointerPress by remember { mutableStateOf(false) }
    val selection by animateFloatAsState(if (inspected) 1f else 0f, tween(KkTime.Pull, easing = KkEase.Pull), label = "armoryTile")
    val measurer = rememberKkCanvasMeasurer(textScale)
    val status = model.status(definition)
    val active = model.activeRunWeapon == definition.id && status != ArmoryWeaponStatus.STARTER
    val name = definition.name.localizedContent(language)
    val statusText = armoryStatusText(status, active, definition, language)
    val costText = formatMatter(definition.permanentUnlockCost.toLong(), language)
    val tags = remember(definition, language) { definition.tags.map { it.localizedContent(language) } }
    Box(
        Modifier.offset { IntOffset(tile.left.roundToInt(), tile.top.roundToInt()) }
            .size(frame.dp(tile.width), frame.dp(tile.height))
            .testTag("profile-armory-equip-${definition.id}")
            .semantics {
                selected = inspected
                contentDescription = name
                stateDescription = statusText
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerPress = true
                    }
                }
            }
            // Keyboard focus moves the selection; focus that comes with a press does not, so the
            // first press only inspects and a press on the inspected tile confirms.
            .onFocusChanged {
                if (it.isFocused && !pointerPress) onAction(ArmoryAction.Inspect(definition.id))
                if (!it.isFocused) pointerPress = false
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                onAction(ArmoryAction.Activate(definition.id))
            }
            .drawBehind {
                drawArmoryTile(measurer, frame, type, name, tags, statusText, costText, status, active,
                    armoryWeaponIcon(definition.id), selection, hovered, focused)
            },
    )
}

internal fun armoryStatusText(status: ArmoryWeaponStatus, active: Boolean, definition: WeaponDefinition, language: AppLanguage): String = when {
    active -> language.text(ProfileScreensRedesignText.ActiveRun)
    status == ArmoryWeaponStatus.STARTER -> language.text(ProfileScreensRedesignText.Starter)
    status == ArmoryWeaponStatus.OWNED -> language.text(ProfileScreensRedesignText.Owned)
    else -> language.text(ProfileScreensRedesignText.CostMatter, formatMatter(definition.permanentUnlockCost.toLong(), language))
}

private fun DrawScope.drawArmoryTile(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    name: String,
    tags: List<String>,
    statusText: String,
    costText: String,
    status: ArmoryWeaponStatus,
    active: Boolean,
    icon: KkIcon?,
    selection: Float,
    hovered: Boolean,
    focused: Boolean,
) {
    val k = frame.k
    val bounds = Rect(Offset.Zero, size)
    val locked = status.locked
    val roles = measurer.roles
    // The board's selected tile keeps its row (the entrance animation holds `transform: none`),
    // so the foundation's 8 dp selection lift is cancelled here; the echo still slides out.
    translate(0f, d(8f) * selection.coerceIn(0f, 1.2f)) {
        drawKkTile(measurer, bounds, selected = selection, hovered = hovered, locked = locked,
            cutDp = (if (frame.regular) 16f else 12f) * k, focused = focused)
    }
    val on = selection > 0.5f
    val fg = when {
        locked && on -> Kk.Bone
        locked -> Kk.Mute
        on -> Kk.Ink
        else -> Kk.Bone
    }
    val compact = !frame.regular
    val padLeft = frame.d(if (compact) 14f else 24f)
    val padRight = frame.d(if (compact) 12f else 22f)
    val padTop = frame.d(if (compact) 10f else 14f)
    val padBottom = frame.d(if (compact) 10f else 14f)
    val innerWidth = size.width - padLeft - padRight
    run {
        val statusStyle = measurer.typography.monoStyle(type.status * k)
        if (locked) {
            val gem = frame.d(if (compact) 7f else 9f)
            val costColor = when {
                status == ArmoryWeaponStatus.AFFORDABLE -> roles.you
                else -> Kk.Mute
            }
            val cost = measureKkText(measurer, costText, statusStyle, uppercase = true)
            drawKkGem(Offset(padLeft + gem * 0.5f, padTop + cost.kkBoxHeight * 0.5f), costColor, gem / density)
            drawKkText(cost, padLeft + gem + frame.d(5f), padTop, costColor)
        } else {
            val layout = measureKkText(measurer, statusText, statusStyle, uppercase = true, maxWidth = innerWidth)
            drawKkText(layout, padLeft, padTop, if (active && !on) roles.you else fg, alpha = if (active) 1f else 0.7f)
        }
        val iconColor = when {
            locked -> Kk.Mute
            on -> Kk.Ink
            else -> roles.you
        }
        val iconY = size.height * if (compact) 0.36f else 0.45f
        if (icon != null) drawKkIcon(icon, Offset(size.width * 0.5f, iconY), frame.d(type.tileIcon), iconColor)

        // Phones keep the tags in the detail panel; the regular tile lists them as words.
        var tagsTop = size.height - padBottom
        if (!compact) {
            val tagStyle = measurer.typography.monoStyle(type.tileTags * k)
            val tagHeight = measureKkText(measurer, tags.firstOrNull() ?: " ", tagStyle, uppercase = true).kkBoxHeight
            tagsTop -= tagHeight
            var x = padLeft
            for (tag in tags) {
                val layout = measureKkText(measurer, tag, tagStyle, uppercase = true)
                if (x + layout.size.width > size.width + frame.d(8f)) break
                drawKkText(layout, x, tagsTop, fg, alpha = 0.75f)
                x += layout.size.width + frame.d(8f)
            }
        }
        val nameLayout = fitKkText(measurer, name, type.tileName * k, innerWidth, maxLines = 2, minFactor = 0.6f) {
            measurer.typography.condStyle(it)
        }
        val nameTop = tagsTop - (if (compact) 0f else frame.d(3f)) - nameLayout.kkBoxHeight
        drawKkText(nameLayout, padLeft, nameTop, fg)
    }
}

private fun DrawScope.drawArmoryDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    layout: ArmoryLayout,
    definition: WeaponDefinition,
    status: ArmoryWeaponStatus,
    active: Boolean,
    matter: Long,
    masteries: List<WeaponMastery>,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    drawKkIconPlate(layout.plate, armoryWeaponIcon(definition.id), roles.you, cutDp = 12f * k,
        iconSizeDp = (if (frame.regular) 54f else 32f) * k)
    val name = fitKkText(measurer, definition.name.localizedContent(language), type.name * k, layout.name.width, maxLines = 2,
        minFactor = 0.55f) { measurer.typography.wideStyle(it, lineHeightEm = 1f) }
    drawKkText(name, layout.name.left, layout.name.center.y, Kk.Bone, valign = KkVAlign.CENTER)
    val descriptionStyle = measurer.typography.bodyStyle(type.body * k)
    val descriptionLines = (layout.description.height / (frame.d(type.body) * 1.4f * measurer.scale)).toInt().coerceAtLeast(1)
    val description = measureKkText(measurer, definition.description.localizedContent(language), descriptionStyle,
        maxWidth = layout.description.width, maxLines = descriptionLines)
    drawKkText(description, layout.description.left, layout.description.top, Kk.Bone)

    var tagX = layout.tags.left
    val tagHeight = (if (frame.regular) 22f else 20f) * k
    if (active) {
        val text = language.text(ProfileScreensRedesignText.ActiveRun)
        if (tagX + kkTagSize(measurer, text, density, tagHeight, type.tag * k).width <= layout.tags.right) {
            tagX = drawKkTag(measurer, text, Offset(tagX, layout.tags.top), KkTagVariant.YOU, heightDp = tagHeight,
                fontSize = type.tag * k).right + frame.d(6f)
        }
    }
    for (tag in definition.tags) {
        val text = tag.localizedContent(language)
        if (tagX + kkTagSize(measurer, text, density, tagHeight, type.tag * k).width > layout.tags.right) break
        tagX = drawKkTag(measurer, text, Offset(tagX, layout.tags.top), KkTagVariant.LINE, heightDp = tagHeight,
            fontSize = type.tag * k).right + frame.d(6f)
    }
    drawKkText(measurer, language.text(ProfileScreensRedesignText.Mastery), measurer.typography.labelStyle(type.label * k),
        layout.mastery.left, layout.mastery.center.y, Kk.Mute, valign = KkVAlign.CENTER, uppercase = true)
    drawMasteryLadder(measurer, frame, type, layout.ladder, masteries, language)

    if (status == ArmoryWeaponStatus.SHORT) {
        val need = (definition.permanentUnlockCost - matter).coerceAtLeast(0L)
        val text = language.text(ProfileScreensRedesignText.NeedMore, formatMatter(need, language))
        val needLayout = measureKkText(measurer, text, measurer.typography.monoStyle(type.ladderMono * k, lineHeightEm = 1.6f),
            uppercase = true, maxWidth = layout.need.width.coerceAtLeast(frame.d(60f)), maxLines = 3)
        drawKkText(needLayout, layout.need.left, layout.need.center.y, roles.threat, valign = KkVAlign.CENTER)
    }
}

/**
 * Mastery ladder: one sheared cell per weapon level up to the last milestone, milestone cells lit
 * (you, you, bone, legendary for the last), "Lvl N" over each milestone and its name with the
 * damage and activation bonuses under it.
 */
private fun DrawScope.drawMasteryLadder(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    rect: Rect,
    masteries: List<WeaponMastery>,
    language: AppLanguage,
) {
    if (masteries.isEmpty()) return
    val k = frame.k
    val t = measurer.scale.coerceIn(0.75f, 2f)
    val roles = measurer.roles
    val cells = masteries.maxOf { it.minimumLevel }.coerceIn(1, 40)
    val gap = frame.d(4f)
    val cellWidth = (rect.width - gap * (cells - 1)) / cells
    val cellTop = rect.top + frame.d(if (frame.regular) 30f else 22f) * t
    val cellHeight = frame.d(if (frame.regular) 12f else 10f)
    val monoStyle = measurer.typography.monoStyle(type.ladderMono * k)
    val namesTop = cellTop + cellHeight + frame.d(if (frame.regular) 10f else 8f)
    fun milestoneColor(index: Int): Color = when {
        index == masteries.lastIndex -> Kk.RLegend
        index < 2 -> roles.you
        else -> Kk.Bone
    }
    for (cell in 0 until cells) {
        val level = cell + 1
        val milestone = masteries.indexOfFirst { it.minimumLevel == level }
        val left = rect.left + cell * (cellWidth + gap)
        val color = if (milestone >= 0) milestoneColor(milestone) else Kk.Ink4
        drawPath(LadderPaths.sheared(cell % LADDER_SLOTS, left, cellTop, left + cellWidth, cellTop + cellHeight), color)
    }
    var previousRight = rect.left
    masteries.forEachIndexed { index, mastery ->
        val last = index == masteries.lastIndex
        val cellLeft = rect.left + (mastery.minimumLevel - 1).coerceAtLeast(0) * (cellWidth + gap)
        val nextLeft = masteries.getOrNull(index + 1)?.let { rect.left + (it.minimumLevel - 1) * (cellWidth + gap) } ?: rect.right
        val room = if (last) rect.right - previousRight - frame.d(8f) else nextLeft - cellLeft - frame.d(6f)
        val align = if (last) KkAlign.END else KkAlign.START
        val x = if (last) rect.right else cellLeft
        val color = milestoneColor(index)
        val levelLayout = measureKkText(measurer, language.text(ProfileScreensRedesignText.MasteryLevel, kkIntString(mastery.minimumLevel)),
            monoStyle, uppercase = true)
        drawKkText(levelLayout, x, rect.top, if (index < 2 || last) color else Kk.Bone, align = align)
        val nameLayout = fitKkText(measurer, mastery.displayLabel.localizedContent(language), type.ladderName * k,
            max(room, frame.d(40f)), minFactor = 0.7f) { measurer.typography.labelStyle(it) }
        drawKkText(nameLayout, x, namesTop, if (last) Kk.RLegend else Kk.Bone, align = align)
        previousRight = cellLeft + nameLayout.size.width
        val bonus = if (mastery.damageBonus <= 0f && mastery.activationSpeedBonus <= 0f) {
            language.text(ProfileScreensRedesignText.MasteryBase)
        } else {
            "+${percent(mastery.damageBonus)}%  +${percent(mastery.activationSpeedBonus)}%"
        }
        val bonusLayout = measureKkText(measurer, bonus, monoStyle, uppercase = true, maxWidth = max(room, frame.d(40f)))
        drawKkText(bonusLayout, x, namesTop + nameLayout.kkBoxHeight + frame.d(4f), Kk.Mute, align = align)
        previousRight = max(previousRight, cellLeft + bonusLayout.size.width)
    }
}

private fun percent(value: Float): Int = (value * 100f).roundToInt()

private const val LADDER_SLOTS = 40
private val LadderPaths = KkPathCache(LADDER_SLOTS)

private fun DrawScope.drawArmoryBackground(
    frame: ProfileFrame,
    layout: ArmoryLayout?,
    measurer: CanvasTextMeasurer,
    title: String,
) {
    drawRect(Kk.Ink)
    drawProfileGrid(frame)
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            drawProfileSidePanel(frame.x(900f))
            val word = measureKkText(measurer, title, measurer.typography.wideStyle(230f * frame.k, lineHeightEm = 1f), uppercase = true)
            drawText(word, Color(0xFF1F231F), Offset(frame.x(40f), frame.d(600f) - word.kkBoxTop), drawStyle = OutlineStroke.of(1.5f * density))
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> if (layout != null) {
            drawProfileSidePanel(layout.detailViewport.left - size.height * KkShape.ShearRatio * 0.5f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> if (layout != null) {
            val top = layout.detailViewport.top
            drawRect(Kk.Ink1, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
            drawLine(Kk.Line, Offset(0f, top), Offset(size.width, top), density)
        }
    }
}

/** Reused outline stroke for the big background word. */
private object OutlineStroke {
    private var width = -1f
    private var stroke: Stroke? = null

    fun of(px: Float): Stroke = stroke?.takeIf { width == px } ?: Stroke(px).also { stroke = it; width = px }
}

/**
 * Page steps for the weapon grid: shown only while the grid scrolls (phones, large text); the
 * previous/next nodes stay in the tree, disabled, when every weapon fits.
 */
@Composable
private fun RowScope.ArmoryPager(
    frame: ProfileFrame,
    gridScroll: ScrollState,
    holder: ArmoryLayoutHolder,
    textScale: Float,
    onAction: (ArmoryAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val layout = holder.layout
    val visible = gridScroll.maxValue > 0
    val (pages, page) = if (layout == null) 1 to 1 else armoryGridPages(gridScroll.value.toFloat(),
        gridScroll.maxValue.toFloat(), layout.gridViewport.height, layout.rowPitch)
    val typography = rememberInterfaceTypography()
    val previousEnabled = visible && gridScroll.value > 0
    val nextEnabled = visible && gridScroll.value < gridScroll.maxValue
    val pageText = language.text(ProfileScreensRedesignText.PageStep, page, pages)
    // When every weapon fits, the steps collapse to disabled, invisible 1 dp nodes.
    val step = if (visible) frame.d(30f) else frame.density
    Box(Modifier.alpha(if (visible) 1f else 0f)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ArmoryStepButton(frame, step, plus = false, enabled = previousEnabled, tag = "profile-armory-previous",
                description = language.text(ProfileText.PreviousPage), state = pageText) { onAction(ArmoryAction.PreviousPage) }
            if (visible) {
                Spacer(Modifier.width(frame.dp(frame.d(8f))))
                BasicText(language.text(ProfileScreensRedesignText.CountOf, page, pages),
                    style = typography.monoStyle(11f * frame.k * textScale, color = Kk.Mute, lineHeightEm = 1f))
                Spacer(Modifier.width(frame.dp(frame.d(8f))))
            }
            ArmoryStepButton(frame, step, plus = true, enabled = nextEnabled, tag = "profile-armory-next",
                description = language.text(ProfileText.NextPage), state = pageText) { onAction(ArmoryAction.NextPage) }
        }
    }
    if (visible) Spacer(Modifier.width(frame.dp(frame.d(16f))))
}

@Composable
private fun ArmoryStepButton(
    frame: ProfileFrame,
    size: Float,
    plus: Boolean,
    enabled: Boolean,
    tag: String,
    description: String,
    state: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val panelFocus = LocalProfilePanelFocus.current
    Box(
        Modifier.size(frame.dp(size))
            .testTag(tag)
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                // The last step disables this button; keep keyboard routing on the panel.
                panelFocus?.requestFocus()
                onClick()
            }
            .drawBehind { drawKkStepButton(Rect(Offset.Zero, this.size), plus, hovered, enabled) },
    )
}
