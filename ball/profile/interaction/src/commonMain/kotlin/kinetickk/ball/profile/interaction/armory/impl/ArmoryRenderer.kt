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
import androidx.compose.ui.unit.toSize
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.LocalProfilePanelFocus
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.PROFILE_SMALLEST_TEXT_SCALE
import kinetickk.ball.profile.interaction.PROFILE_WRAPPED_LINE_HEIGHT
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.profileTextScale
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
import kinetickk.ball.profile.interaction.profileScrollCue
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

/** The Armory screen; [textScale] is the player's text-size setting (1.25 = the boards' size). */
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
    val scale = profileTextScale(textScale)
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
        scale = scale,
        tag = "profile-armory",
        onBack = { onAction(ArmoryAction.Back) },
        background = { frame -> drawArmoryBackground(frame, holder.layout, outlineMeasurer, title) },
        headerExtra = { frame -> ArmoryPager(frame, gridScroll, holder, scale, onAction) },
        headerExtraWidth = { frame -> if (gridScroll.maxValue > 0) frame.d(30f) * 2f + frame.d(8f) * 2f + frame.d(16f) +
            frame.d(34f) * scale else frame.density * 2f },
    ) { frame ->
        val measurer = rememberKkCanvasMeasurer(scale)
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
        val activeTag = if (model.activeRunWeapon == inspected.id) language.text(ProfileScreensRedesignText.ActiveRun) else null
        val tagTexts = remember(inspected, language) { inspected.tags.map { it.localizedContent(language) } }
        val layout = armoryLayout(frame, weapons.size, scale, backWidth, actionWidth, needRoom = status == ArmoryWeaponStatus.SHORT,
            masteryCount = masteries.size) { nameWidth, width ->
            ArmoryDetailMetrics(
                nameHeight = armoryDetailNameLayout(measurer, frame, type, inspected.name.localizedContent(language), nameWidth).kkBoxHeight,
                descriptionHeight = armoryDescriptionLayout(measurer, frame, type, inspected.description.localizedContent(language), width)
                    .kkBoxHeight,
                tagsHeight = armoryTagsHeight(measurer, frame, tagTexts, activeTag, width),
            )
        }
        SideEffect { holder.layout = layout }
        val grid = layout.gridViewport
        Box(
            Modifier.offset { IntOffset(grid.left.roundToInt(), grid.top.roundToInt()) }
                .size(frame.dp(grid.width), frame.dp(grid.height))
                .semantics { contentDescription = language.text(ProfileScreensRedesignText.WeaponsList) }
                .profileScrollCue(gridScroll, if (frame.portrait) Kk.Ink else Kk.Ink.copy(alpha = 0.9f), frame.d(28f))
                .verticalScroll(gridScroll)
                .testTag("profile-armory-scroll"),
        ) {
            Box(Modifier.fillMaxWidth().height(frame.dp(layout.gridContentHeight))) {
                weapons.forEachIndexed { index, definition ->
                    val tile = layout.tiles[index]
                    ArmoryTile(frame, type, tile, definition, model, definition.id == state.inspected, index, scale, language, onAction)
                }
            }
        }
        // Each weapon's details start at the top.
        val detailScroll = remember(inspected.id) { ScrollState(0) }
        val detail = layout.detailViewport
        Box(
            Modifier.offset { IntOffset(detail.left.roundToInt(), detail.top.roundToInt()) }
                .size(frame.dp(detail.width), frame.dp(detail.height))
                .profileScrollCue(detailScroll, Kk.Ink1, frame.d(28f))
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
                            drawArmoryDetail(measurer, frame, type, layout, inspected, tagTexts, activeTag, masteries, language)
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
                        placement = KkTooltipPlacement.BELOW, textScale = scale)
                }
            }
        }
        // The primary action stays on screen: it is placed outside the details' scroll and, when
        // they overflow, pinned on a band under them.
        val button = layout.action
        Box(
            Modifier.offset { IntOffset(0, 0) }.size(frame.dp(frame.width), frame.dp(frame.height)).drawBehind {
                if (layout.actionPinned) {
                    val top = layout.detailViewport.bottom
                    drawRect(Kk.Ink1, Offset(layout.detailViewport.left, top),
                        androidx.compose.ui.geometry.Size(layout.detailViewport.width, button.bottom + frame.d(8f) - top))
                }
                if (status == ArmoryWeaponStatus.SHORT) drawArmoryNeed(measurer, frame, type, layout.need, inspected, model.totalMatter, language)
            },
        )
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
                    textScale = scale,
                    contentDescription = listOfNotNull(action.label, action.costDescription).joinToString(" "),
                    modifier = Modifier.offset { IntOffset(button.left.roundToInt(), button.top.roundToInt()) }
                        .size(frame.dp(button.width), frame.dp(button.height)),
                )
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
    val statusBottom: Float
    if (locked) {
        val gem = frame.d(if (compact) 7f else 9f)
        val costColor = when {
            status == ArmoryWeaponStatus.AFFORDABLE -> roles.you
            else -> Kk.Mute
        }
        val cost = fitKkText(measurer, costText, type.status * k, innerWidth - gem - frame.d(5f), minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawKkGem(Offset(padLeft + gem * 0.5f, padTop + cost.kkBoxHeight * 0.5f), costColor, gem / density)
        drawProfileText(cost, padLeft + gem + frame.d(5f), padTop, costColor, "armory.tile.status", name)
        statusBottom = padTop + cost.kkBoxHeight
    } else {
        val layout = fitKkText(measurer, statusText, type.status * k, innerWidth, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
        drawProfileText(layout, padLeft, padTop, if (active && !on) roles.you else fg, "armory.tile.status", name,
            alpha = if (active) 1f else 0.7f)
        statusBottom = padTop + layout.kkBoxHeight
    }

    // Phones keep the tags in the detail panel; the regular tile lists them as words on one line,
    // shrinking them together (never below their size at the smallest text setting).
    var tagsTop = size.height - padBottom
    if (!compact && tags.isNotEmpty()) {
        val tagGap = frame.d(8f)
        val room = size.width + tagGap - padLeft
        fun lineWidth(font: Float) = tags.sumOf {
            measureKkText(measurer, it, measurer.typography.monoStyle(font), uppercase = true).size.width.toDouble()
        }.toFloat() + tagGap * (tags.size - 1)
        var font = type.tileTags * k
        val smallest = font * (PROFILE_SMALLEST_TEXT_SCALE / measurer.scale).coerceAtMost(1f)
        while (font > smallest && lineWidth(font) > room) font = max(smallest, font * 0.94f)
        val tagStyle = measurer.typography.monoStyle(font)
        tagsTop -= measureKkText(measurer, tags.first(), tagStyle, uppercase = true).kkBoxHeight
        var x = padLeft
        for (tag in tags) {
            val layout = measureKkText(measurer, tag, tagStyle, uppercase = true)
            if (x + layout.size.width > size.width + tagGap) break
            drawProfileText(layout, x, tagsTop, fg, "armory.tile.tag", name, alpha = 0.75f)
            x += layout.size.width + tagGap
        }
    }
    val nameLayout = armoryTileNameLayout(measurer, frame, type, name, innerWidth)
    val nameTop = tagsTop - (if (compact) 0f else frame.d(5f)) - nameLayout.kkBoxHeight
    drawProfileText(nameLayout, padLeft, nameTop, fg, "armory.tile.name", name)

    // The icon sits centred in the band between the status line and the name, shrinking when
    // large text leaves less room.
    val iconColor = when {
        locked -> Kk.Mute
        on -> Kk.Ink
        else -> roles.you
    }
    val bandTop = statusBottom + frame.d(4f)
    val bandBottom = nameTop - frame.d(4f)
    val iconSize = minOf(frame.d(type.tileIcon), bandBottom - bandTop)
    if (icon != null && iconSize > frame.d(10f)) {
        drawKkIcon(icon, Offset(size.width * 0.5f, (bandTop + bandBottom) * 0.5f), iconSize, iconColor)
    }
}

/** A tile's weapon name: two lines at most, shrunk to fit (never below its size at the smallest text setting). */
internal fun armoryTileNameLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: ArmoryType, name: String, width: Float) =
    // A full line height keeps Cyrillic diacritics (Ё, Й) clear of the line above.
    fitKkText(measurer, name, type.tileName * frame.k, width, maxLines = 2, minFactor = 0.6f) {
        measurer.typography.condStyle(it, lineHeightEm = PROFILE_WRAPPED_LINE_HEIGHT)
    }

/** The detail panel's weapon name: two lines at most, shrunk to fit [width]. */
internal fun armoryDetailNameLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: ArmoryType, name: String, width: Float) =
    fitKkText(measurer, name, type.name * frame.k, width, maxLines = 2, minFactor = 0.55f) {
        measurer.typography.wideStyle(it, lineHeightEm = PROFILE_WRAPPED_LINE_HEIGHT)
    }

/** The weapon's description wrapped to [width] (every line; the details scroll). */
internal fun armoryDescriptionLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: ArmoryType, text: String, width: Float) =
    measureKkText(measurer, text, measurer.typography.bodyStyle(type.body * frame.k), maxWidth = width,
        maxLines = PROFILE_DESCRIPTION_MAX_LINES)

/**
 * Lays out the detail tags (the in-run tag first) in rows of [width]: a tag that does not fit
 * the row starts the next one, so no tag is dropped. Calls [place] with each tag's variant, top
 * left, chip height (dp) and font size; returns the rows' height in px.
 */
private inline fun armoryTagFlow(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    tags: List<String>,
    activeTag: String?,
    left: Float,
    top: Float,
    width: Float,
    place: (text: String, variant: KkTagVariant, topLeft: Offset, heightDp: Float, fontSize: Float) -> Unit,
): Float {
    val height = armoryTagHeight(frame, measurer.scale)
    val heightDp = height / frame.density
    val gap = frame.d(6f)
    val baseFont = armoryType(frame.mode).tag * frame.k
    var x = left
    var y = top
    var count = 0
    val total = tags.size + if (activeTag != null) 1 else 0
    for (index in 0 until total) {
        val text = if (activeTag != null) (if (index == 0) activeTag else tags[index - 1]) else tags[index]
        val variant = if (activeTag != null && index == 0) KkTagVariant.YOU else KkTagVariant.LINE
        // A single tag wider than the panel shrinks to the panel's width.
        var font = baseFont
        while (kkTagSize(measurer, text, frame.density, heightDp, font).width > width && font > baseFont * 0.5f) font *= 0.92f
        val tagWidth = kkTagSize(measurer, text, frame.density, heightDp, font).width
        if (count > 0 && x + tagWidth > left + width) {
            x = left
            y += height + gap
        }
        place(text, variant, Offset(x, y), heightDp, font)
        x += tagWidth + gap
        count++
    }
    return if (count == 0) height else y + height - top
}

/** Height in px of the detail tag rows for [width]. */
internal fun armoryTagsHeight(measurer: CanvasTextMeasurer, frame: ProfileFrame, tags: List<String>, activeTag: String?, width: Float): Float =
    armoryTagFlow(measurer, frame, tags, activeTag, 0f, 0f, width) { _, _, _, _, _ -> }

private fun DrawScope.drawArmoryDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    layout: ArmoryLayout,
    definition: WeaponDefinition,
    tags: List<String>,
    activeTag: String?,
    masteries: List<WeaponMastery>,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    drawKkIconPlate(layout.plate, armoryWeaponIcon(definition.id), roles.you, cutDp = 12f * k,
        iconSizeDp = (if (frame.regular) 54f else 32f) * k)
    val name = armoryDetailNameLayout(measurer, frame, type, definition.name.localizedContent(language), layout.name.width)
    drawProfileText(name, layout.name.left, layout.name.center.y, Kk.Bone, "armory.detail.name", valign = KkVAlign.CENTER)
    val description = armoryDescriptionLayout(measurer, frame, type, definition.description.localizedContent(language),
        layout.description.width)
    drawProfileText(description, layout.description.left, layout.description.top, Kk.Bone, "armory.detail.description")
    armoryTagFlow(measurer, frame, tags, activeTag, layout.tags.left, layout.tags.top, layout.tags.width) { text, variant, topLeft, heightDp, font ->
        val rect = drawKkTag(measurer, text, topLeft, variant, heightDp = heightDp, fontSize = font)
        ProfileTextProbe.record("armory.detail.tag", text, null, rect)
    }
    val mastery = measureKkText(measurer, language.text(ProfileScreensRedesignText.Mastery), measurer.typography.labelStyle(type.label * k),
        uppercase = true)
    drawProfileText(mastery, layout.mastery.left, layout.mastery.center.y, Kk.Mute, "armory.detail.mastery", valign = KkVAlign.CENTER)
    drawMasteryLadder(measurer, frame, type, layout.ladder, masteries, language)
}

/** "Need N more matter" beside a locked Unlock button, in the threat role. */
private fun DrawScope.drawArmoryNeed(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    rect: Rect,
    definition: WeaponDefinition,
    matter: Long,
    language: AppLanguage,
) {
    val need = (definition.permanentUnlockCost - matter).coerceAtLeast(0L)
    val text = language.text(ProfileScreensRedesignText.NeedMore, formatMatter(need, language))
    val needLayout = fitKkText(measurer, text, type.ladderMono * frame.k, rect.width.coerceAtLeast(frame.d(60f)), maxLines = 3,
        minFactor = 0.6f) { measurer.typography.monoStyle(it, lineHeightEm = 1.6f) }
    drawProfileText(needLayout, rect.left, rect.center.y, measurer.roles.threat, "armory.need", valign = KkVAlign.CENTER)
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
    val cellTop = rect.top + armoryLadderCellsTop(frame, t)
    val cellHeight = frame.d(if (frame.regular) 12f else 10f)
    val monoStyle = measurer.typography.monoStyle(type.ladderMono * k)
    val namesTop = cellTop + cellHeight + frame.d(if (frame.regular) 10f else 8f)
    fun milestoneColor(index: Int): Color = when {
        index == masteries.lastIndex -> Kk.RLegend
        index < 2 -> roles.you
        else -> Kk.Bone
    }
    val stacked = armoryMasteryStacked(t)
    for (cell in 0 until cells) {
        val level = cell + 1
        val milestone = masteries.indexOfFirst { it.minimumLevel == level }
        val left = rect.left + cell * (cellWidth + gap)
        val color = if (milestone >= 0) milestoneColor(milestone) else Kk.Ink4
        drawPath(LadderPaths.sheared(cell % LADDER_SLOTS, left, cellTop, left + cellWidth, cellTop + cellHeight), color)
    }
    if (stacked) {
        // One milestone per line: level, name, then the bonuses at the right edge.
        val levelTexts = masteries.map { language.text(ProfileScreensRedesignText.MasteryLevel, kkIntString(it.minimumLevel)) }
        val levelWidth = levelTexts.maxOf { measureKkText(measurer, it, monoStyle, uppercase = true).size.width.toFloat() } + frame.d(10f)
        val line = frame.d(type.ladderName) * ARMORY_STACKED_LINE * t
        masteries.forEachIndexed { index, mastery ->
            val y = namesTop + index * line + line * 0.5f
            val color = milestoneColor(index)
            drawProfileText(measureKkText(measurer, levelTexts[index], monoStyle, uppercase = true), rect.left, y, color,
                "armory.ladder.level", index, valign = KkVAlign.CENTER)
            val bonus = masteryBonus(mastery, language)
            val bonusLayout = measureKkText(measurer, bonus, monoStyle, uppercase = true)
            drawProfileText(bonusLayout, rect.right, y, Kk.Mute, "armory.ladder.bonus", index, align = KkAlign.END, valign = KkVAlign.CENTER)
            val nameRoom = rect.width - levelWidth - bonusLayout.size.width - frame.d(10f)
            val nameLayout = fitKkText(measurer, mastery.displayLabel.localizedContent(language), type.ladderName * k, nameRoom,
                minFactor = 0.5f) { measurer.typography.labelStyle(it) }
            drawProfileText(nameLayout, rect.left + levelWidth, y, if (index == masteries.lastIndex) Kk.RLegend else Kk.Bone,
                "armory.ladder.name", index, valign = KkVAlign.CENTER)
        }
        return
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
        drawProfileText(levelLayout, x, rect.top, if (index < 2 || last) color else Kk.Bone, "armory.ladder.level", index, align = align)
        val nameLayout = fitKkText(measurer, mastery.displayLabel.localizedContent(language), type.ladderName * k,
            max(room, frame.d(40f)), minFactor = 0.7f) { measurer.typography.labelStyle(it) }
        drawProfileText(nameLayout, x, namesTop, if (last) Kk.RLegend else Kk.Bone, "armory.ladder.name", index, align = align)
        previousRight = cellLeft + nameLayout.size.width
        val bonus = masteryBonus(mastery, language)
        val bonusLayout = fitKkText(measurer, bonus, type.ladderMono * k, max(room, frame.d(40f)), minFactor = 0.6f) {
            measurer.typography.monoStyle(it)
        }
        drawProfileText(bonusLayout, x, namesTop + nameLayout.kkBoxHeight + frame.d(4f), Kk.Mute, "armory.ladder.bonus", index, align = align)
        previousRight = max(previousRight, cellLeft + bonusLayout.size.width)
    }
}

private fun percent(value: Float): Int = (value * 100f).roundToInt()

/** "Base" for the first milestone, else the damage and activation-speed bonuses. */
private fun masteryBonus(mastery: WeaponMastery, language: AppLanguage): String =
    if (mastery.damageBonus <= 0f && mastery.activationSpeedBonus <= 0f) {
        language.text(ProfileScreensRedesignText.MasteryBase)
    } else {
        "+${percent(mastery.damageBonus)}%  +${percent(mastery.activationSpeedBonus)}%"
    }

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
                    style = typography.monoStyle(11f * frame.k * textScale, color = Kk.Mute, lineHeightEm = 1f), maxLines = 1, softWrap = false,
                    onTextLayout = { ProfileTextProbe.record("armory.pager", null, it, Rect(Offset.Zero, it.size.toSize())) })
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
