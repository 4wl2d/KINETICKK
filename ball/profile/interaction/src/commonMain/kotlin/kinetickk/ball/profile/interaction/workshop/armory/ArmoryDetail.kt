// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.PROFILE_WRAPPED_LINE_HEIGHT
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.workshop.hangar.drawHangarForm
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.roundToInt

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

internal fun DrawScope.drawArmoryDetail(
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
    drawKkIconPlate(layout.plate, armoryWeaponIcon(definition.id), armoryElementColor(measurer, definition), cutDp = 12f * k,
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
    // The mastery label and ladder show on the Mastery segment only.
    if (layout.mastery.height <= 0f) return
    val mastery = measureKkText(measurer, language.text(ProfileScreensRedesignText.Mastery), measurer.typography.labelStyle(type.label * k),
        uppercase = true)
    drawProfileText(mastery, layout.mastery.left, layout.mastery.center.y, Kk.Mute, "armory.detail.mastery", valign = KkVAlign.CENTER)
    drawMasteryLadder(measurer, frame, type, layout.ladder, layout.ladderStacked, masteries, language)
}

/** Why a [ArmoryWeaponStatus.blocked] weapon cannot be unlocked yet: its Rebirth gate, or the Matter still missing. */
internal fun armoryNeedText(status: ArmoryWeaponStatus, definition: WeaponDefinition, matter: Long, language: AppLanguage): String =
    if (status == ArmoryWeaponStatus.REBIRTH_LOCKED) {
        language.text(ProfileScreensRedesignText.RebornLock, definition.minimumRebirth)
    } else {
        language.text(ProfileScreensRedesignText.NeedMore, formatMatter((definition.permanentUnlockCost - matter).coerceAtLeast(0L), language))
    }

/** The [armoryNeedText] ("Reborn N" or "Need N more matter") beside a locked Unlock button, in the threat role. */
internal fun DrawScope.drawArmoryNeed(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    rect: Rect,
    text: String,
    role: String = "armory.need",
) {
    val needLayout = fitKkText(measurer, text, type.ladderMono * frame.k, rect.width.coerceAtLeast(frame.d(60f)), maxLines = 3,
        minFactor = 0.6f) { measurer.typography.monoStyle(it, lineHeightEm = 1.6f) }
    drawProfileText(needLayout, rect.left, rect.center.y, measurer.roles.threat, role, valign = KkVAlign.CENTER)
}

/** What a refused unlock of [definition] tells the player beside the action ([ArmoryNotice]). */
internal fun armoryNoticeText(notice: ArmoryNotice, definition: WeaponDefinition, matter: Long, language: AppLanguage): String =
    when (notice.refusal) {
        ArmoryRefusal.INSUFFICIENT_MATTER -> language.text(ProfileScreensRedesignText.ArmoryRefusedMatter,
            formatMatter((definition.permanentUnlockCost - matter).coerceAtLeast(0L), language))
        ArmoryRefusal.REBIRTH_LOCKED -> language.text(ProfileScreensRedesignText.ArmoryRefusedRebirth, definition.minimumRebirth)
        ArmoryRefusal.REFUSED -> language.text(ProfileScreensRedesignText.ArmoryRefused)
    }

/** A milestone's bonus group: "Base" for the first milestone, else its damage and activation-speed values. */
private fun masteryBonusParts(mastery: WeaponMastery, language: AppLanguage): List<String> =
    if (mastery.damageBonus <= 0f && mastery.activationSpeedBonus <= 0f) {
        listOf(language.text(ProfileScreensRedesignText.MasteryBase))
    } else {
        listOf("+${percent(mastery.damageBonus)}%", "+${percent(mastery.activationSpeedBonus)}%")
    }

/** Gap (px) between the two values of a bonus group: the board's 8 px beside its 11 px values, following the text size. */
private fun armoryBonusGap(measurer: CanvasTextMeasurer, frame: ProfileFrame, type: ArmoryType): Float =
    frame.d(8f * type.ladderMono / 11f) * measurer.scale

/** A milestone's bonus values measured at the ladder's full size (side by side they never shrink). */
private fun armoryBonusLayouts(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    mastery: WeaponMastery,
    language: AppLanguage,
): List<TextLayoutResult> {
    val style = measurer.typography.monoStyle(type.ladderMono * frame.k)
    return masteryBonusParts(mastery, language).map { measureKkText(measurer, it, style, uppercase = true) }
}

/** Width (px) of a bonus group whose values sit [gap] apart. */
private fun List<TextLayoutResult>.groupWidth(gap: Float): Float =
    sumOf { it.size.width.toDouble() }.toFloat() + gap * (size - 1).coerceAtLeast(0)

/** Draws a bonus group's values [gap] apart from [left], each reported as a ladder bonus of milestone [index]. */
private fun DrawScope.drawBonusGroup(parts: List<TextLayoutResult>, left: Float, y: Float, gap: Float, index: Int, valign: KkVAlign) {
    var x = left
    for (part in parts) {
        drawProfileText(part, x, y, Kk.Mute, "armory.ladder.bonus", index, valign = valign)
        x += part.size.width + gap
    }
}

/** Whether the mastery milestones fit side by side on a ladder [width] px wide ([armoryMasteryFitsSideBySide]). */
internal fun armoryMasterySideBySide(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    masteries: List<WeaponMastery>,
    language: AppLanguage,
    width: Float,
): Boolean {
    val gap = armoryBonusGap(measurer, frame, type)
    return armoryMasteryFitsSideBySide(frame, masteries.map { it.minimumLevel },
        masteries.map { armoryBonusLayouts(measurer, frame, type, it, language).groupWidth(gap) }, gap, width)
}

/**
 * Mastery ladder: one sheared cell per weapon level up to the last milestone, milestone cells lit
 * (you, you, bone, legendary for the last), "Lvl N" over each milestone and its name with the
 * damage and activation bonuses under it; [stacked] lists the milestones one per line instead.
 */
private fun DrawScope.drawMasteryLadder(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    rect: Rect,
    stacked: Boolean,
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
    val cellTop = rect.top + armoryLadderCellsTop(frame, stacked, t)
    val cellHeight = frame.d(if (frame.regular) 12f else 10f)
    val monoStyle = measurer.typography.monoStyle(type.ladderMono * k)
    val namesTop = cellTop + cellHeight + frame.d(if (frame.regular) 10f else 8f)
    val bonusGap = armoryBonusGap(measurer, frame, type)
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
            val bonus = armoryBonusLayouts(measurer, frame, type, mastery, language)
            val bonusWidth = bonus.groupWidth(bonusGap)
            drawBonusGroup(bonus, rect.right - bonusWidth, y, bonusGap, index, KkVAlign.CENTER)
            val nameRoom = rect.width - levelWidth - bonusWidth - frame.d(10f)
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
        // The layout keeps these ladders to panels where every bonus group fits its slot at full
        // size, clearly apart from its neighbours ([armoryMasteryFitsSideBySide]).
        val bonus = armoryBonusLayouts(measurer, frame, type, mastery, language)
        val bonusWidth = bonus.groupWidth(bonusGap)
        drawBonusGroup(bonus, if (last) rect.right - bonusWidth else cellLeft, namesTop + nameLayout.kkBoxHeight + frame.d(4f),
            bonusGap, index, KkVAlign.TOP)
        previousRight = max(previousRight, cellLeft + bonusWidth)
    }
}

private fun percent(value: Float): Int = (value * 100f).roundToInt()

private const val LADDER_SLOTS = 40
private val LadderPaths = KkPathCache(LADDER_SLOTS)

internal fun DrawScope.drawArmorySheet(sheet: ArmorySheet, layout: ArmoryLayout) {
    for ((section, rect) in listOf(sheet.forms to layout.forms, sheet.synergy to layout.synergy, sheet.facts to layout.facts)) {
        // The sections of a hidden segment take no room and draw nothing.
        if (rect.height <= 0f) continue
        section.glyph?.let { glyph ->
            val center = Offset(rect.left + glyph.center.x, rect.top + glyph.center.y)
            glyph.ring?.let { drawCircle(it.copy(alpha = 0.35f), glyph.radius / 0.78f, center, style = kkStroke(density * 1.5f)) }
            drawHangarForm(glyph.formIndex, center, glyph.radius, glyph.fill, Kk.Ink, 0f)
        }
        for (text in section.texts) {
            drawProfileText(text.layout, rect.left + text.x, rect.top + text.y, text.color, text.role, text.owner, align = text.align)
        }
    }
}
