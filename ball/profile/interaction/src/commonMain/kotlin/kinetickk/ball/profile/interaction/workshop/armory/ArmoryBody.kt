// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.workshop.hangar.hangarFormIndex
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileButtonNaturalWidth
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.ball.profile.interaction.profileScrollCue
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopAction
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegmentStrip
import kinetickk.ball.profile.interaction.workshop.impl.segments
import kinetickk.ball.profile.interaction.workshop.impl.UpgradeTarget
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopUpgradeBlock
import kinetickk.ball.profile.interaction.workshop.impl.weaponUpgradeTrack
import kinetickk.ball.profile.interaction.workshop.impl.workshopUpgradeLayoutFor
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

/**
 * The Workshop's Weapons body inside the panel's [frame]: the weapon grid, the detail panel with
 * its [segment] (the Honing block of [upgrades], the mastery or the details) and the pinned primary
 * action. [textScale] is the player's text-size setting (1.25 = the boards' size). [gridExtra] (the
 * pager, when the landscape header has no room for it) sits at the grid's top-right corner;
 * [focusSerial] moves keyboard focus to the inspected tile when it changes; [refusalSerial] flashes
 * the Honing action after a refusal. Segment and Honing presses go to [onWorkshop].
 */
@Composable
internal fun ArmoryBody(
    frame: ProfileFrame,
    model: ArmoryRenderModel,
    weapons: List<WeaponDefinition>,
    masteries: List<WeaponMastery>,
    state: ArmoryViewState,
    segment: WorkshopSegment,
    textScale: Float,
    gridScroll: ScrollState,
    holder: ArmoryLayoutHolder,
    coreShapes: List<CoreShapeDefinition> = emptyList(),
    relics: List<RelicDefinition> = emptyList(),
    /** Where each weapon's details start scrolled (px); 0, the top, except in screenshots of the sheet. */
    detailScrollStart: Int = 0,
    focusSerial: Int = 0,
    gridExtra: (@Composable RowScope.() -> Unit)? = null,
    upgrades: UpgradePolicy = UpgradePolicy(),
    refusalSerial: Int = 0,
    onWorkshop: (WorkshopAction) -> Unit = {},
    onAction: (ArmoryAction) -> Unit,
) {
    val scale = profileTextScale(textScale)
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(scale)
    val type = armoryType(frame.mode)
    val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
    val inspected = weapons.firstOrNull { it.id == state.inspected } ?: weapons.first()
    val status = model.status(inspected)
    val notice = state.inspectedNotice
    val synergy = remember(inspected, model, coreShapes, relics) { model.synergy(inspected, coreShapes, relics) }
    val formLevels = remember(masteries) { armoryFormLevels(masteries) }
    val action = armoryActionPresentation(status, inspected, language)
    val buttonSize = if (frame.regular) KkButtonSize.LG else KkButtonSize.MD
    val actionWidth = max(
        profileButtonNaturalWidth(measurer, frame, action.label, buttonSize, type.action, action.cost),
        frame.d(if (frame.regular) 240f else 180f),
    )
    val activeTag = if (model.activeRunWeapon == inspected.id) language.text(ProfileScreensRedesignText.ActiveRun) else null
    val tagTexts = remember(inspected, language) { inspected.tags.map { it.displayLabel.localizedContent(language) } }
    val tileTexts = remember(weapons, model, language) { armoryTileTexts(weapons, model, language) }
    val track = weaponUpgradeTrack(upgrades, inspected, model.upgrades.weaponLevel(inspected.id), model.totalMatter,
        owned = inspected.id in model.unlockedWeapons)
    var sheet: ArmorySheet? = null
    val layout = armoryLayout(frame, weapons.size, scale, backWidth, actionWidth, needRoom = status.blocked || notice != null,
        masteryCount = masteries.size, segment = segment, tileText = { innerWidth -> armoryTileMetrics(measurer, frame, type, tileTexts, innerWidth) },
        upgradeHeight = { width -> workshopUpgradeLayoutFor(measurer, frame, track, language, 0f, 0f, width).height },
    ) { nameWidth, width ->
        val measured = armorySheet(measurer, frame, type, inspected, synergy, formLevels, language, width).also { sheet = it }
        ArmoryDetailMetrics(
            nameHeight = armoryDetailNameLayout(measurer, frame, type, inspected.name.localizedContent(language), nameWidth).kkBoxHeight,
            descriptionHeight = armoryDescriptionLayout(measurer, frame, type, inspected.description.localizedContent(language), width)
                .kkBoxHeight,
            tagsHeight = armoryTagsHeight(measurer, frame, tagTexts, activeTag, width),
            masterySideBySide = armoryMasterySideBySide(measurer, frame, type, masteries, language, width),
            formsHeight = measured.forms.height,
            synergyHeight = measured.synergy.height,
            factsHeight = measured.facts.height,
        )
    }
    val weaponSheet = sheet
    SideEffect { holder.layout = layout }
    // The inspected weapon's tile is scrolled into view: on opening (the starter may sit on a later
    // page) and whenever the selection moves (arrow keys, Tab, a tile half under the fold).
    val inspectedIndex = weapons.indexOfFirst { it.id == inspected.id }
    val revealHolder = remember { ArmoryRevealMemory() }
    LaunchedEffect(inspected.id) {
        val target = armoryRevealScroll(layout, inspectedIndex, gridScroll.value.toFloat()).roundToInt()
        if (target != gridScroll.value) {
            if (revealHolder.revealed) gridScroll.animateScrollTo(target) else gridScroll.scrollTo(target)
        }
        revealHolder.revealed = true
    }
    val inspectedFocus = remember { FocusRequester() }
    LaunchedEffect(focusSerial) { if (focusSerial > 0) inspectedFocus.requestFocus() }
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
                ArmoryTile(frame, type, tile, layout.tileIcon, layout.tileIconY, definition, model, definition.id == state.inspected,
                    scale, language, onAction, signatureForm = definition.signature.hangarFormIndex(),
                    focusRequester = if (definition.id == state.inspected) inspectedFocus else null, maxLevel = upgrades.maxLevel)
            }
        }
    }
    if (gridExtra != null) {
        // Landscape without header room: the extra sits in the grid's top-right corner, inset d(6).
        val inset = frame.d(6f)
        androidx.compose.foundation.layout.Row(
            Modifier.offset { IntOffset(grid.left.roundToInt(), (grid.top + inset).roundToInt()) }
                .size(frame.dp((grid.width - inset).coerceAtLeast(0f)), frame.dp(frame.d(30f))),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) { gridExtra() }
    }
    // Each weapon's details start at the top.
    val detailScroll = remember(inspected.id) { ScrollState(detailScrollStart) }
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
                    contentDescription = armorySentences(listOfNotNull(
                        inspected.name.localizedContent(language),
                        inspected.description.localizedContent(language),
                        weaponSheet?.spoken(layout),
                    ))
                }
                .drawBehind {
                    translate(-detail.left, -detail.top) {
                        drawArmoryDetail(measurer, frame, type, layout, inspected, tagTexts, activeTag, masteries, language)
                        if (weaponSheet != null) drawArmorySheet(weaponSheet, layout)
                    }
                },
        ) {
            val segments = layout.segments
            WorkshopSegmentStrip(frame, segments.translate(-detail.left, -detail.top), WorkshopTab.WEAPONS.segments, segment, scale,
                onWorkshop)
            val slot = layout.upgrade
            if (slot.height > 0f) {
                val target = UpgradeTarget.Weapon(inspected.id)
                WorkshopUpgradeBlock(frame, workshopUpgradeLayoutFor(measurer, frame, track, language, slot.left - detail.left,
                    slot.top - detail.top, slot.width), track, target, inspected.name.localizedContent(language), null, scale,
                    refusalSerial) { onWorkshop(WorkshopAction.BuyUpgrade(target, track)) }
            }
            // The mastery (!) moves with the Mastery segment.
            if (layout.mastery.height > 0f) {
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
            when {
                notice != null -> drawArmoryNeed(measurer, frame, type, layout.need,
                    armoryNoticeText(notice, inspected, model.totalMatter, language), role = "armory.notice")
                status.blocked -> drawArmoryNeed(measurer, frame, type, layout.need, armoryNeedText(status, inspected, model.totalMatter, language))
            }
        },
    )
    if (notice != null) {
        // The refusal is announced as it appears; the drawn line beside the action shows it.
        val need = layout.need
        Box(
            Modifier.offset { IntOffset(need.left.roundToInt(), need.top.roundToInt()) }
                .size(frame.dp(need.width.coerceAtLeast(1f)), frame.dp(need.height))
                .testTag("profile-armory-notice")
                // The Workshop's alert live region announces the refusal; this node only describes it.
                .semantics { contentDescription = armoryNoticeText(notice, inspected, model.totalMatter, language) },
        )
    }
    ProfileSlabButton(
                label = action.label,
                onClick = { onAction(ArmoryAction.Apply(inspected.id)) },
                frame = frame,
                tag = "profile-armory-action",
                variant = action.variant,
                size = buttonSize,
                fontSize = type.action,
                // A blocked unlock stays pressable: the press names why it is locked instead of doing nothing.
                enabled = status.actionEnabled || status.blocked,
                locked = status.blocked,
                lockIcon = false,
                cost = action.cost,
                textScale = scale,
                onLockedClick = { onAction(ArmoryAction.Apply(inspected.id)) },
                stateDescription = if (status.blocked) armoryNeedText(status, inspected, model.totalMatter, language) else null,
                contentDescription = listOfNotNull(action.label, action.costDescription).joinToString(" "),
                modifier = Modifier.offset { IntOffset(button.left.roundToInt(), button.top.roundToInt()) }
                    .size(frame.dp(button.width), frame.dp(button.height)),
            )
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
        ArmoryWeaponStatus.AFFORDABLE, ArmoryWeaponStatus.SHORT, ArmoryWeaponStatus.REBIRTH_LOCKED ->
            ArmoryActionPresentation(language.text(ProfileScreensRedesignText.Unlock), cost, costDescription, KkButtonVariant.PRIMARY)
    }
}

/** Whether the opening reveal has run: later reveals of the inspected tile animate. */
internal class ArmoryRevealMemory {
    var revealed = false
}
