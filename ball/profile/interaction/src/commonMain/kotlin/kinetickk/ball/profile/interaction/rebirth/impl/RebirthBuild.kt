// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.PROFILE_WRAPPED_LINE_HEIGHT
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkSlab
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.drawLayeredGlyph
import kinetickk.foundation.design.drawRuneMedallion
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkIntString
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.wideStyle
import kotlin.math.PI
import kotlin.math.roundToInt

/** Probe role of the build section titles (Build unlocks, Heirlooms). */
internal const val REBIRTH_BUILD_SECTION_ROLE = "rebirth.build.section"

/** Probe role of a featured unlock row's description. */
internal const val REBIRTH_UNLOCK_BLURB_ROLE = "rebirth.build.blurb"

/** Rows of the build table: Reborn cards, then each piece of content the target tier unlocks. */
internal fun RebirthRenderModel.buildRowCount(): Int = if (build == null) 0 else 1 + targetUnlocks.size

/** Rows of the Heirloom list (none without build content or stored Heirlooms). */
internal fun RebirthRenderModel.heirloomRowCount(): Int = build?.heirlooms?.size ?: 0

/** `15%`: a Reborn chance (0..1) in whole percent. */
internal fun rebirthPercent(chance: Float, language: AppLanguage): String =
    language.text(ProfileScreensRedesignText.PercentValue, (chance * 100f).roundToInt())

/** The Reborn comparison row of the build table. */
internal fun rebirthRebornRow(model: RebirthRenderModel, language: AppLanguage): RebirthRow? {
    val build = model.build ?: return null
    return RebirthRow(language.text(ProfileScreensRedesignText.RebornCards), rebirthPercent(build.currentRebornChance, language),
        rebirthPercent(build.nextRebornChance, language), rebirthChange(build.currentRebornChance, build.nextRebornChance))
}

/**
 * What an unlock does: a Relic's rule rewrite at rank 1 (its first sentence, the rule itself) or
 * an item's description line, in [language].
 */
internal fun rebirthUnlockBlurb(unlock: RebirthUnlock, language: AppLanguage): String = when (unlock) {
    is RebirthUnlock.Relic -> unlock.definition.upsideText(language, 1).firstSentence()
    is RebirthUnlock.Item -> unlock.definition.description.localizedContent(language)
}

private fun String.firstSentence(): String {
    val end = indexOf(". ")
    return if (end < 0) this else substring(0, end + 1)
}

/** The build sections read aloud: the Reborn odds, the unlocks of the target tier and the Heirlooms. */
internal fun rebirthBuildDescription(model: RebirthRenderModel, language: AppLanguage): String? {
    val reborn = rebirthRebornRow(model, language) ?: return null
    val build = model.build ?: return null
    return buildString {
        append(language.text(ProfileScreensRedesignText.BuildSummary, language.text(ProfileScreensRedesignText.BuildUnlocks),
            reborn.label, reborn.current, reborn.next))
        model.targetUnlocks.forEach { unlock ->
            append(' ').append(unlock.name.localizedContent(language)).append(", ").append(unlockKind(unlock, language)).append(". ")
            append(rebirthUnlockBlurb(unlock, language))
        }
        if (build.heirlooms.isNotEmpty()) {
            append(' ').append(language.text(ProfileScreensRedesignText.Heirlooms)).append(". ")
            append(build.heirlooms.joinToString(", ") { it.name.localizedContent(language) }).append('.')
        }
    }
}

/** The build (!) text: how Reborn cards and Heirlooms work. */
internal fun rebirthBuildInfoText(language: AppLanguage): String = language.text(ProfileScreensRedesignText.RebirthBuildInfo)

private fun unlockKind(unlock: RebirthUnlock, language: AppLanguage): String = language.text(
    when (unlock) {
        is RebirthUnlock.Relic -> ProfileScreensRedesignText.UnlockRelic
        is RebirthUnlock.Item -> ProfileScreensRedesignText.UnlockItem
    },
)

/** Left edge of a featured unlock row's name and description, right of its mark. */
internal fun rebirthUnlockTextLeft(frame: ProfileFrame, row: Rect): Float = row.left + rebirthUnlockIconSize(frame) + frame.d(12f)

/** A featured row's description style (muted body, a full line box for wrapping). */
private fun CanvasTextMeasurer.blurbStyle(size: Float) = typography.bodyStyle(size, lineHeightEm = REBIRTH_UNLOCK_BLURB_LINE)

private fun blurbSize(frame: ProfileFrame, type: RebirthType): Float = type.body * REBIRTH_UNLOCK_BLURB_SIZE * frame.k

/**
 * Description lines each of [blurbs] takes at its full size in a build row like [row] (the Reborn
 * row: every build row shares its edges), at most [REBIRTH_UNLOCK_MAX_LINES]. The layout sizes the
 * featured rows from these; longer text shrinks to fit that many lines.
 */
internal fun rebirthUnlockLines(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    row: Rect?,
    blurbs: List<String>,
): List<Int> {
    if (row == null || blurbs.isEmpty()) return emptyList()
    val width = (row.right - rebirthUnlockTextLeft(frame, row)).coerceAtLeast(1f)
    val style = measurer.blurbStyle(blurbSize(frame, type))
    return blurbs.map { blurb ->
        measureKkText(measurer, blurb, style, maxWidth = width, maxLines = PROFILE_DESCRIPTION_MAX_LINES).lineCount
            .coerceIn(1, REBIRTH_UNLOCK_MAX_LINES)
    }
}

/** Room the build header keeps after its title for the build (!) button. */
private fun rebirthBuildInfoRoom(frame: ProfileFrame): Float = frame.density * 24f + frame.d(10f)

/** The build table's title, fitted left of its value columns and its (!) button. */
internal fun rebirthBuildTitle(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    header: Rect,
    language: AppLanguage,
): TextLayoutResult = rebirthSectionLayout(measurer, frame, type, language.text(ProfileScreensRedesignText.BuildUnlocks),
    (header.width - rebirthBuildColumn(frame, measurer.scale) * 2f - rebirthBuildInfoRoom(frame)).coerceAtLeast(1f))

/** The build (!) button (24 dp): right of the [title], centered on its capitals. */
internal fun rebirthBuildInfoRect(frame: ProfileFrame, header: Rect, title: TextLayoutResult): Rect {
    val size = frame.density * 24f
    val centerY = header.bottom - frame.d(8f) - title.firstBaseline * 0.42f
    val left = header.left + title.size.width + frame.d(10f)
    return Rect(left, centerY - size * 0.5f, left + size, centerY + size * 0.5f)
}

/**
 * The build sections: a table of the Reborn card chance (current and target tier) and one
 * featured row per piece of content the target tier unlocks (its rune or item mark, display name,
 * gold kind tag and what it does), then the stored Heirlooms with their runes. On the regular
 * board both sit on an opaque, gold-rimmed slab under the ladder, so the orbit rings and the
 * tier 5+ band end at its edge. [blurbs] and [lines] are each unlock's description and its line
 * budget ([rebirthUnlockBlurb], [rebirthUnlockLines]).
 */
internal fun DrawScope.drawRebirthBuild(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    layout: RebirthLayout,
    model: RebirthRenderModel,
    theme: RebirthTheme,
    language: AppLanguage,
    blurbs: List<String>,
    lines: List<Int>,
) {
    val build = model.build ?: return
    val header = layout.buildHeader ?: return
    val k = frame.k
    val column = rebirthBuildColumn(frame, measurer.scale)
    val bottomPad = frame.d(8f)
    if (frame.regular) drawRebirthBuildBacking(frame, layout, theme)

    val title = rebirthBuildTitle(measurer, frame, type, header, language)
    drawProfileText(title, header.left, header.bottom - bottomPad, Kk.Bone, REBIRTH_BUILD_SECTION_ROLE, valign = KkVAlign.BASELINE)
    val headStyle = measurer.typography.wideStyle(type.tableHead * k, tabular = true)
    drawProfileText(measureKkText(measurer, kkIntString(model.current.tier), headStyle), header.right - column,
        header.bottom - bottomPad, Kk.Mute, "rebirth.build.head", align = KkAlign.END, valign = KkVAlign.BASELINE)
    drawProfileText(measureKkText(measurer, kkIntString(model.targetTier), headStyle), header.right, header.bottom - bottomPad,
        theme.accent, "rebirth.build.head", align = KkAlign.END, valign = KkVAlign.BASELINE)
    drawLine(theme.gold.copy(alpha = 0.6f), Offset(header.left, header.bottom), Offset(header.right, header.bottom), frame.density)

    val reborn = rebirthRebornRow(model, language) ?: return
    val rebornRect = layout.buildRows.first()
    val icon = rebirthBuildIconSize(frame, rebornRect)
    drawRebornGlyph(Offset(rebornRect.left + icon * 0.5f, rebornRect.center.y), icon * 0.5f, theme.gold, frame)
    drawRebirthRow(measurer, frame, type, rebornRect, column, reborn,
        if (reborn.change == RebirthChange.UP) theme.gold else Kk.Mute, inset = icon + frame.d(10f), role = "rebirth.build.row")

    model.targetUnlocks.forEachIndexed { index, unlock ->
        val rect = layout.buildRows.getOrNull(index + 1) ?: return@forEachIndexed
        drawRebirthUnlockRow(measurer, frame, type, rect, unlock, blurbs.getOrElse(index) { "" },
            lines.getOrElse(index) { 1 }, theme, language)
    }

    val label = layout.heirloomLabel ?: return
    val heirloomTitle = rebirthSectionLayout(measurer, frame, type, language.text(ProfileScreensRedesignText.Heirlooms), label.width)
    drawProfileText(heirloomTitle, label.left, label.bottom - bottomPad, Kk.Bone, REBIRTH_BUILD_SECTION_ROLE, valign = KkVAlign.BASELINE)
    drawLine(theme.gold.copy(alpha = 0.6f), Offset(label.left, label.bottom), Offset(label.right, label.bottom), frame.density)
    build.heirlooms.forEachIndexed { index, relic ->
        val rect = layout.heirloomRows.getOrNull(index) ?: return@forEachIndexed
        val size = rebirthBuildIconSize(frame, rect)
        drawRebirthRelicRune(relic, Offset(rect.left + size * 0.5f, rect.center.y), size * 0.5f, theme)
        drawRebirthName(measurer, frame, type, relic.name.localizedContent(language), rect.left + size + frame.d(10f), rect.right, rect,
            "rebirth.heirloom.name", relic.id)
        drawLine(Kk.Line, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), frame.density)
    }
}

/**
 * One featured unlock row: the Relic's rune or the item's mark at [rebirthUnlockIconSize], its
 * name in display type with the gold kind tag at the row's end, and what it does in muted body
 * text under the name (up to [lines] lines). Name and description sit centered in the row.
 */
private fun DrawScope.drawRebirthUnlockRow(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    rect: Rect,
    unlock: RebirthUnlock,
    blurb: String,
    lines: Int,
    theme: RebirthTheme,
    language: AppLanguage,
) {
    val k = frame.k
    val icon = rebirthUnlockIconSize(frame)
    val center = Offset(rect.left + icon * 0.5f, rect.center.y)
    when (unlock) {
        is RebirthUnlock.Relic -> drawRebirthRelicRune(unlock.definition, center, icon * 0.5f, theme)
        is RebirthUnlock.Item -> drawRebirthItemMark(unlock.definition, center, icon * 0.5f, theme)
    }
    val textLeft = rebirthUnlockTextLeft(frame, rect)
    val kind = unlockKind(unlock, language)
    val tagHeight = 18f * k
    val tagFont = type.tag * k
    val tagSize = kkTagSize(measurer, kind, frame.density, tagHeight, tagFont)
    val name = fitKkText(measurer, unlock.name.localizedContent(language), type.body * REBIRTH_UNLOCK_NAME_SIZE * k,
        (rect.right - tagSize.width - frame.d(10f) - textLeft).coerceAtLeast(1f), minFactor = 0.55f) {
        measurer.typography.condStyle(it, lineHeightEm = PROFILE_WRAPPED_LINE_HEIGHT)
    }
    val description = if (blurb.isEmpty()) null else {
        fitKkText(measurer, blurb, blurbSize(frame, type), (rect.right - textLeft).coerceAtLeast(1f),
            maxLines = lines.coerceIn(1, REBIRTH_UNLOCK_MAX_LINES), uppercase = false, minFactor = 0.6f) { measurer.blurbStyle(it) }
    }
    val gap = rebirthUnlockGap(frame)
    val block = name.kkBoxHeight + if (description == null) 0f else gap + description.kkBoxHeight
    val top = rect.center.y - block * 0.5f
    drawProfileText(name, textLeft, top, Kk.Bone, "rebirth.build.unlock", unlock.name)
    val nameCenter = top + name.kkBoxHeight * 0.5f
    val tag = drawKkTag(measurer, kind, Offset(rect.right - tagSize.width, nameCenter - tagSize.height * 0.5f),
        heightDp = tagHeight, fontSize = tagFont, background = theme.gold, foreground = Kk.Ink)
    ProfileTextProbe.record("rebirth.build.tag", unlock.name, null, tag, theme.gold)
    if (description != null) {
        drawProfileText(description, textLeft, top + name.kkBoxHeight + gap, Kk.Mute, REBIRTH_UNLOCK_BLURB_ROLE, unlock.name)
    }
    drawLine(Kk.Line, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), frame.density)
}

/**
 * Value column of the build table: the comparison tables' column on phones, where it stacks under
 * them and keeps their alignment; narrower on the board, where it stands alone and holds percents.
 */
internal fun rebirthBuildColumn(frame: ProfileFrame, scale: Float): Float =
    rebirthValueColumn(frame, scale) * if (frame.regular) 0.8f else 1f

/** Icon box of a build list row: most of the row height, at most 26 board px. */
internal fun rebirthBuildIconSize(frame: ProfileFrame, row: Rect): Float = minOf(row.height * 0.72f, frame.d(26f))

private fun DrawScope.drawRebirthName(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: RebirthType,
    name: String,
    left: Float,
    right: Float,
    row: Rect,
    role: String,
    owner: Any?,
) {
    val layout = fitKkText(measurer, name, type.body * frame.k, (right - left).coerceAtLeast(1f), uppercase = false,
        minFactor = 0.55f) { measurer.typography.bodyStyle(it) }
    drawProfileText(layout, left, row.center.y, Kk.Bone, role, owner, valign = KkVAlign.CENTER)
}

/** The backing slab behind the board's build columns: [rebirthBuildBackingRect] in the ground with a gold hairline. */
private fun DrawScope.drawRebirthBuildBacking(frame: ProfileFrame, layout: RebirthLayout, theme: RebirthTheme) {
    val rect = rebirthBuildBackingRect(frame, layout) ?: return
    val cut = frame.d(14f)
    drawKkSlab(rect, theme.background, cut = cut)
    drawKkSlab(rect, theme.gold.copy(alpha = 0.35f), cut = cut, style = kkStroke(frame.density))
}

/**
 * The board's build backing: an opaque slab around both build columns, so the rings and the
 * tier 5+ band visibly stop at its rim instead of crossing the text. Null without build content.
 */
internal fun rebirthBuildBackingRect(frame: ProfileFrame, layout: RebirthLayout): Rect? {
    val header = layout.buildHeader ?: return null
    var right = header.right
    var bottom = layout.buildRows.lastOrNull()?.bottom ?: header.bottom
    layout.heirloomLabel?.let { right = it.right }
    layout.heirloomRows.lastOrNull()?.let { bottom = maxOf(bottom, it.bottom) }
    val pad = frame.d(18f)
    return Rect(header.left - pad, header.top - pad * 0.6f, right + pad, bottom + pad * 0.6f)
}

/** The Reborn mark: a card with a gold rim and a ring at its heart. */
private fun DrawScope.drawRebornGlyph(center: Offset, radius: Float, color: Color, frame: ProfileFrame) {
    val width = radius * 1.3f
    val height = radius * 1.9f
    val topLeft = Offset(center.x - width * 0.5f, center.y - height * 0.5f)
    val stroke = maxOf(frame.density * 1.25f, radius * 0.14f)
    drawRect(Kk.Ink2, topLeft, Size(width, height))
    drawRect(color, topLeft, Size(width, height), style = kkStroke(stroke))
    drawCircle(color, radius * 0.36f, center, style = kkStroke(stroke * 0.85f))
    drawCircle(color, radius * 0.1f, center)
}

/**
 * A Relic's medallion as the run draws it: its rune ([toCanvasRuneStyle]) in a frame whose sides
 * name its aspect ([runeFrameSides]), in the aspect color (gold for Sovereign relics).
 */
internal fun DrawScope.drawRebirthRelicRune(relic: RelicDefinition, center: Offset, radius: Float, theme: RebirthTheme) {
    drawRuneMedallion(
        style = relic.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = rebirthAspectColor(relic.aspect, theme),
        frameSides = relic.aspect.runeFrameSides(),
        frameRotation = -PI.toFloat() * 0.5f,
    )
}

/** Aspect hue: the element's color (in the player's color-vision palette), the player's volt for Kinetic, gold for Sovereign. */
internal fun rebirthAspectColor(aspect: RelicAspect, theme: RebirthTheme): Color {
    val element = aspect.element
    return when {
        element != null -> theme.element(element.ordinal)
        aspect == RelicAspect.SOVEREIGN -> theme.gold
        else -> theme.kinetic
    }
}

/**
 * An item's mark as the run and the Codex draw it: its element glyph over its first tag glyph
 * (a sunburst without either) in a frame of rarity rank + 3 sides with rank markers, in its
 * element's color (Kinetic volt without an element).
 */
private fun DrawScope.drawRebirthItemMark(item: ItemDefinition, center: Offset, radius: Float, theme: RebirthTheme) {
    val rank = item.rarity.rank.coerceIn(1, 5)
    val element = item.elements.firstOrNull()
    drawLayeredGlyph(
        primaryStyle = item.primaryGlyphStyle(),
        secondaryStyle = item.secondaryGlyphStyle(),
        center = center,
        radius = radius,
        accent = if (element != null) theme.element(element.ordinal) else theme.kinetic,
        frameSides = rank + 3,
        markerCount = rank,
    )
}

/** The item's first element when it has one, otherwise its first tag (the run's `primaryGlyphStyle`). */
internal fun ItemDefinition.primaryGlyphStyle(): CanvasGlyphStyle =
    elements.firstOrNull()?.toCanvasGlyphStyle() ?: tags.firstOrNull()?.toCanvasGlyphStyle() ?: CanvasGlyphStyle.SUNBURST

/** The first tag behind an element, else the second tag or element; a lone mark repeats itself. */
internal fun ItemDefinition.secondaryGlyphStyle(): CanvasGlyphStyle = when {
    elements.isNotEmpty() -> tags.firstOrNull()?.toCanvasGlyphStyle() ?: elements.getOrNull(1)?.toCanvasGlyphStyle()
    else -> tags.getOrNull(1)?.toCanvasGlyphStyle()
} ?: primaryGlyphStyle()

/** Element glyphs, the same table as the run's item icons. */
internal fun StatusElement.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    StatusElement.THERMAL -> CanvasGlyphStyle.SUNBURST
    StatusElement.CRYO -> CanvasGlyphStyle.SNOWFLAKE
    StatusElement.ION -> CanvasGlyphStyle.BOLT
    StatusElement.GRAVITIC -> CanvasGlyphStyle.CONCENTRIC_ORB
    StatusElement.ENTROPY -> CanvasGlyphStyle.LEAF
    StatusElement.SONIC -> CanvasGlyphStyle.SOUND_WAVES
    StatusElement.VOID -> CanvasGlyphStyle.HOLLOW_RING
}

/** Tag glyphs, the same table as the run's item icons. */
internal fun BuildTag.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    BuildTag.RAM -> CanvasGlyphStyle.TRIPLE_ARROW
    BuildTag.DRIVE -> CanvasGlyphStyle.DOUBLE_CHEVRON
    BuildTag.ANCHOR -> CanvasGlyphStyle.HORSESHOE
    BuildTag.ARSENAL -> CanvasGlyphStyle.SLASH_BARS
    BuildTag.PRECISION -> CanvasGlyphStyle.RETICLE
    BuildTag.BULWARK -> CanvasGlyphStyle.SHIELD
    BuildTag.OVERCLOCK -> CanvasGlyphStyle.THREE_BLADE
    BuildTag.CHAIN -> CanvasGlyphStyle.INTERLOCKING_RINGS
    BuildTag.FORTUNE -> CanvasGlyphStyle.FOUR_LEAF
}

/**
 * Gold diamonds over the ladder cells of tiers that unlock build content (one per unlock): gold
 * for the target tier, faint gold for later tiers and muted for tiers already reached.
 */
internal fun DrawScope.drawRebirthLadderUnlocks(frame: ProfileFrame, layout: RebirthLayout, model: RebirthRenderModel, theme: RebirthTheme) {
    val build = model.build ?: return
    val half = frame.d(if (frame.regular) 4.5f else 3f)
    val gap = half * 2.8f
    val lift = half + frame.d(if (frame.regular) 6f else 4f)
    layout.cells.forEachIndexed { index, cell ->
        val tier = model.minimumTier + index
        var count = 0
        for (unlock in build.unlocks) if (unlock.tier == tier) count++
        if (count == 0) return@forEachIndexed
        val color = when (model.tierCell(tier)) {
            RebirthTierCell.CLEARED, RebirthTierCell.CURRENT -> Kk.Mute
            RebirthTierCell.NEXT -> theme.gold
            RebirthTierCell.LATER -> theme.gold.copy(alpha = 0.45f)
        }
        val y = cell.top - lift
        val start = cell.center.x - (count - 1) * gap * 0.5f
        for (pip in 0 until count) {
            val center = Offset(start + pip * gap, y)
            rotate(45f, center) { drawRect(color, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
        }
        ProfileTextProbe.record("rebirth.ladder.unlock", tier, null,
            Rect(start - half * 1.42f, y - half * 1.42f, start + (count - 1) * gap + half * 1.42f, y + half * 1.42f), color)
    }
}

/** Frame sides of a Relic medallion per aspect, as the run and the Codex draw them. */
internal fun RelicAspect.runeFrameSides(): Int = when (this) {
    RelicAspect.KINETIC -> 3
    RelicAspect.THERMAL -> 6
    RelicAspect.CRYO -> 8
    RelicAspect.ION -> 4
    RelicAspect.GRAVITIC -> 5
    RelicAspect.ENTROPY -> 7
    RelicAspect.SONIC -> 9
    RelicAspect.VOID -> 12
    RelicAspect.SOVEREIGN -> 10
}

/**
 * Each Relic's rune: the same table as the run's relic icons (gameplay `RelicIconRenderer`), so a
 * Relic reads the same on this screen, in the run and in the Codex.
 */
internal fun RelicId.toCanvasRuneStyle(): CanvasRuneStyle = when (this) {
    RelicId.PERPETUAL_VECTOR -> CanvasRuneStyle.RADIAL_WHEEL
    RelicId.BLINK_DRIVE -> CanvasRuneStyle.OFFSET_CHEVRONS
    RelicId.GYRE_ENGINE -> CanvasRuneStyle.FOUR_SWIRL_ARMS
    RelicId.GLASS_COMET -> CanvasRuneStyle.CRACKED_RING
    RelicId.EXECUTIONERS_PRISM -> CanvasRuneStyle.SLASH_BLADE
    RelicId.ECHO_CHAMBER -> CanvasRuneStyle.NESTED_DIAMONDS
    RelicId.FURNACE_COVENANT -> CanvasRuneStyle.DIAMOND_FLAME
    RelicId.CINDER_CROWN -> CanvasRuneStyle.FOUR_DOT_CROWN
    RelicId.OVERDRIVE_CASCADE -> CanvasRuneStyle.COUNTER_ROTATING_ARCS
    RelicId.ABSOLUTE_ZERO -> CanvasRuneStyle.SIX_PETAL_ROSETTE
    RelicId.STASIS_ANCHOR -> CanvasRuneStyle.RINGED_ANCHOR
    RelicId.STORMHEART_CAPACITOR -> CanvasRuneStyle.BOLT_AND_DOT
    RelicId.AEGIS_INVERSION -> CanvasRuneStyle.CROSSED_DIAMOND
    RelicId.NEUTRON_HEART -> CanvasRuneStyle.OFFSET_RINGS
    RelicId.EVENT_HORIZON -> CanvasRuneStyle.INWARD_CHEVRONS
    RelicId.VERDANT_ROT -> CanvasRuneStyle.STITCHED_DIAGONAL
    RelicId.PANDEMIC_CODEX -> CanvasRuneStyle.FIVE_RAY_FAN
    RelicId.AGONY_SCEPTER -> CanvasRuneStyle.RADIATING_STAFF
    RelicId.HOLLOW_CROWN -> CanvasRuneStyle.OPEN_DIAL
    RelicId.OUROBOROS_SEAL -> CanvasRuneStyle.RETURN_ARROW
    RelicId.THUNDER_CHOIR -> CanvasRuneStyle.SPLIT_ARCS
    RelicId.NULL_COVENANT -> CanvasRuneStyle.OPEN_ELLIPSE_DOT
}
