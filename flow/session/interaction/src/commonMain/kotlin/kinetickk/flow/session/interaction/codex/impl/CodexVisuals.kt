// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.flow.session.interaction.home.impl.formIcon
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkCardFrame
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawCanvasGlyph
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkRarityTile
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkRarityTileInset
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
import kotlin.math.max
import kotlin.math.min

/** The glyph of a status element (thermal flame, cryo asterisk, ion zigzag, gravitic ring, entropy broken ring). */
internal fun StatusElement.glyph(): KkIcon = KkIcon.Elements[ordinal]

/** The monochrome glyph of an archetype tag. */
internal fun BuildTag.glyph(): KkIcon = KkIcon.Tags[ordinal]

/** Element hue in the player's color-vision mode. */
internal fun KkRolePalette.of(element: StatusElement): Color = element(element.ordinal)

/** Numbers inside rule text: a sign, digits with group or decimal separators, and a percent sign. */
private val RuleNumber = Regex("[+−×]?\\d(?:[\\d., ]*\\d)?(?:[  ]?%)?")

/**
 * [text] with its units kept whole on one line: a word joiner after a slash between letters
 * ("u/s") and a no-break space between a number and such a unit ("600 u/s").
 */
internal fun codexKeepUnits(text: String): String {
    if ('/' !in text) return text
    val out = StringBuilder(text.length + 4)
    for (index in text.indices) {
        val char = text[index]
        if (char == ' ' && index > 0 && text[index - 1].isDigit() && codexUnitAt(text, index + 1)) {
            out.append('\u00A0')
            continue
        }
        out.append(char)
        if (char == '/' && index > 0 && text[index - 1].isLetter() && index + 1 < text.length && text[index + 1].isLetter()) out.append('\u2060')
    }
    return out.toString()
}

/** Whether a unit with a slash between letters ("u/s") starts at [start]. */
private fun codexUnitAt(text: String, start: Int): Boolean {
    var end = start
    while (end < text.length && text[end].isLetter()) end++
    return end > start && end + 1 < text.length && text[end] == '/' && text[end + 1].isLetter()
}

/** [text] with every number in [color] and bold, the rest unstyled. */
internal fun codexHighlightNumbers(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    RuleNumber.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

/**
 * The rarity material a Codex tile and its detail band wear, as the reward card does: an item's by
 * its rarity rank, every relic the one relic material ([CodexRelicMaterial]); null for the rest.
 */
internal fun CodexIcon.rarityMaterial(): KkCardMaterial? = when (this) {
    is CodexIcon.Item -> KkCardMaterial.ofRank(definition.rarity.rank)
    is CodexIcon.Relic -> CodexRelicMaterial
    else -> null
}

/** A relic has no rarity: its reward card wears chrome whatever its aspect, and so do its tile and detail band. */
internal val CodexRelicMaterial = KkCardMaterial.CHROME

/** [material]'s frame in the preset's gold and spectrum (the rarity channel, the same for every type). */
internal fun KkRolePalette.frame(material: KkCardMaterial): KkCardFrame = material.frame(gold, spectrum)

/**
 * An item tile in two channels that never mix: the type tints the face (lighter when [lit]), runs an
 * element strip inside the frame below the [marks] band (px) the cell keeps for its stamp and badge,
 * and colours the glyph; the rarity frames the tile in its material, as on the item's reward card. A
 * reborn item adds a gold rim, a suppressed one a threat hatch over all of it. Allocation-free.
 */
internal fun DrawScope.drawCodexItemTile(icon: CodexIcon.Item, roles: KkRolePalette, lit: Boolean, center: Offset, radius: Float, marks: Float = 0f) {
    val item = icon.definition
    val tone = codexItemColor(item, roles)
    val material = KkCardMaterial.ofRank(item.rarity.rank)
    drawRect(kkMix(Kk.Ink2, tone, if (lit) 0.2f else 0.1f))
    drawKkRarityTile(roles.frame(material), seed = item.id)
    val inset = kkRarityTileInset(material)
    if (item.elements.isNotEmpty()) {
        // The element strip down the right side inside the frame, clear of its corners and the marks, one segment per element.
        val width = 3.dp.toPx()
        val top = max(inset + size.height * 0.18f, marks)
        val segment = (size.height - inset - size.height * 0.18f - top) / item.elements.size
        item.elements.forEachIndexed { index, element ->
            drawRect(roles.of(element), Offset(size.width - inset - width, top + segment * index), Size(width, segment))
        }
    }
    if (icon.reborn) {
        // Reborn: a gold inner rim inside the frame.
        val rim = inset + 2.dp.toPx()
        drawRect(Kk.Gold, Offset(rim, rim), Size(size.width - rim * 2f, size.height - rim * 2f), style = kkStroke(1.5.dp.toPx()))
    }
    drawItemIcon(item, center, radius, if (icon.suppressed) Kk.Mute else tone, stack = icon.stack.takeIf { it > 0 })
    if (icon.suppressed) {
        drawRect(kkFillBrush(KkFillKind.HATCH, roles.threat.copy(alpha = 0.55f)), Offset.Zero, size)
        drawLine(roles.threat, Offset(size.width * 0.18f, size.height * 0.82f), Offset(size.width * 0.82f, size.height * 0.18f), 2.dp.toPx())
    }
}

/**
 * A relic tile: the aspect tints the face and colours the medallion (the type), the relic material
 * frames it (see [CodexRelicMaterial]). A severed relic is a broken one instead: a dashed threat rim
 * over a threat hatch and a faded medallion. A live relic's tile is allocation-free.
 */
internal fun DrawScope.drawCodexRelicTile(icon: CodexIcon.Relic, policy: RelicPolicy, roles: KkRolePalette, lit: Boolean, center: Offset,
    radius: Float) {
    if (icon.severed) {
        drawRect(Kk.Ink2)
        drawKkHatch(Rect(Offset.Zero, size), roles.threat.copy(alpha = 0.18f))
        drawRect(roles.threat, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        drawRelicIcon(icon.definition, policy, center, radius, rank = null, time = 0f, alpha = 0.45f, roles = roles)
        return
    }
    drawRect(kkMix(Kk.Ink2, relicAspectColor(icon.definition.aspect, roles), if (lit) 0.2f else 0.1f))
    drawKkRarityTile(roles.frame(CodexRelicMaterial), seed = icon.definition.id.ordinal)
    // The rank is the cell's badge; the medallion's rank markers would read as stray dots.
    drawRelicIcon(icon.definition, policy, center, radius, rank = null, time = 0f, roles = roles)
}

/** Cell face: item and relic tiles in their rarity material, hatch when locked or unknown. */
internal fun DrawScope.drawCodexCell(entry: CodexEntry, catalog: UiCatalogSnapshot, measurer: CanvasTextMeasurer, band: Boolean, hovered: Boolean,
    markBand: Float) {
    val roles = measurer.roles
    val rect = Rect(Offset.Zero, size)
    val (centerY, radius, centerX) = codexCellGlyph(size.minDimension, markBand, density)
    val center = Offset(centerX, centerY)
    when (val icon = entry.icon) {
        is CodexIcon.Item -> drawCodexItemTile(icon, roles, band || hovered, center, radius, markBand)
        is CodexIcon.Relic -> drawCodexRelicTile(icon, catalog.relicPolicy, roles, band || hovered, center, radius)
        is CodexIcon.Weapon -> {
            drawRect(if (hovered) Kk.Ink3 else Kk.Ink2)
            drawRect(if (entry.active) roles.you else Kk.Line2, style = kkStroke(if (entry.active) 2.dp.toPx() else 1.dp.toPx()))
            drawKkIcon(codexWeaponIcon(icon.id), center, radius * 1.1f, if (entry.discovered) Kk.Bone else Kk.Mute)
        }
        is CodexIcon.Shape -> {
            drawRect(if (hovered) Kk.Ink3 else Kk.Ink2)
            if (!entry.discovered) drawKkHatch(rect)
            drawRect(if (entry.active) roles.you else Kk.Line2, style = kkStroke(if (entry.active) 2.dp.toPx() else 1.dp.toPx()))
            drawKkIcon(icon.id.formIcon(), center, radius * 1.1f, if (entry.discovered) Kk.Bone else Kk.Mute)
            if (!entry.discovered) drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(size.width - 11.dp.toPx(), 11.dp.toPx()), 11.dp.toPx(), Kk.Mute)
        }
        is CodexIcon.Locked -> {
            drawRect(Kk.Ink3)
            drawKkHatch(rect, Kk.Gold.copy(alpha = 0.12f))
            drawRect(Kk.Gold.copy(alpha = 0.55f), style = kkStroke(1.dp.toPx()))
            // The lock over the tier it waits for: "Rebirth" shrunk to the cell, the tier number large under it.
            drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(center.x, center.y - radius * 0.66f), radius * 0.58f, Kk.Gold)
            val split = entry.title.lastIndexOf(' ')
            if (split > 0) {
                drawLockedLabel(measurer, entry.title.substring(0, split), center.y + radius * 0.02f, 12f)
                drawLockedLabel(measurer, entry.title.substring(split + 1), center.y + radius * 0.62f, 21f)
            } else {
                drawLockedLabel(measurer, entry.title, center.y + radius * 0.3f, 13f)
            }
        }
        CodexIcon.Unknown -> {
            drawRect(Kk.Ink3)
            drawKkHatch(rect)
            drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.9f, Kk.Mute)
        }
        CodexIcon.Empty -> {
            drawRect(Kk.Ink2)
            drawKkHatch(rect)
            drawKkIcon(KkIcon.UI_PLUS, center, radius * 0.8f, Kk.Mute2)
        }
        else -> drawRect(Kk.Ink2)
    }
}

/** One line of a locked cell's "Rebirth N" in gold, centered at [centerY], shrunk from [maxSize] until it fits the cell. */
private fun DrawScope.drawLockedLabel(measurer: CanvasTextMeasurer, label: String, centerY: Float, maxSize: Float) {
    val width = size.width - 10.dp.toPx()
    var fontSize = maxSize
    var layout = measureKkText(measurer, label, measurer.typography.condStyle(fontSize, lineHeightEm = 1f), uppercase = true)
    while (fontSize > 7f && layout.size.width > width) {
        fontSize -= 0.5f
        layout = measureKkText(measurer, label, measurer.typography.condStyle(fontSize, lineHeightEm = 1f), uppercase = true)
    }
    drawKkText(layout, size.width * 0.5f, centerY, Kk.Gold, KkAlign.CENTER, KkVAlign.CENTER)
}

/** Selection marks of a slot: pinned bone ring with an ink gap, or a `you` ring while previewed. */
internal fun DrawScope.drawCodexSelection(pinned: Boolean, previewed: Boolean, roles: KkRolePalette) {
    if (pinned) {
        // Selected: 3 dp ink gap and a 2 dp bone ring (box-shadow 0 0 0 3px ink, 0 0 0 5px bone).
        val inner = 1.5.dp.toPx()
        drawRect(Kk.Ink, Offset(-inner, -inner), Size(size.width + inner * 2f, size.height + inner * 2f), style = kkStroke(3.dp.toPx()))
        val outer = 4.dp.toPx()
        drawRect(Kk.Bone, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(2.dp.toPx()))
    } else if (previewed) {
        val outer = 3.dp.toPx()
        drawRect(roles.you, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(2.dp.toPx()))
    }
}

/**
 * The fill of an Items matrix cell of one rarity: its card material ([KkCardMaterial.ofRank]) over
 * the whole cell, never a type hue, so it follows the colour-vision presets as the card frames do.
 * The ladder climbs in lightness and polish: dark matte iron, a mid steel (a large lightness step
 * above iron), bright chrome cut by a dark polished stripe low on the cell (well above steel in
 * lightness, so the two never read alike), the prismatic fill the preset's own spectrum and gold its
 * foil with a dark [seam] round the edge. A gradient runs its [stops] at [positions] (evenly when
 * null). [accent] is the glyph's colour on it.
 */
internal class CodexRarityFill(val stops: List<Color>, val seam: Color?, val accent: Color, val positions: List<Float>? = null) {
    /** The fill over [rect], its gradient running from the top-left corner to the bottom-right one. */
    fun brush(rect: Rect): Brush = when {
        stops.size == 1 -> SolidColor(stops[0])
        positions != null -> Brush.linearGradient(*positions.zip(stops).toTypedArray(), start = rect.topLeft, end = rect.bottomRight)
        else -> Brush.linearGradient(stops, rect.topLeft, rect.bottomRight)
    }
}

/**
 * Where chrome's border stops sit across a matrix cell (top-left 0, bottom-right 1): bright to past the
 * glyph, then its dark polished stripe low on the cell, clear of the glyph and the NEW notch.
 */
private val CodexChromeStops = listOf(0f to 0, 0.58f to 3, 0.7f to 1, 0.78f to 2, 0.86f to 4, 1f to 5)

/** The matrix fill of rarity [rank] (1..5) in [roles]' materials: every colour comes from [KkRolePalette.frame]. */
internal fun codexRarityFill(rank: Int, roles: KkRolePalette): CodexRarityFill {
    val material = KkCardMaterial.ofRank(rank)
    val frame = roles.frame(material)
    return when (material) {
        KkCardMaterial.STEEL -> CodexRarityFill(listOf(kkMix(frame.border[1], frame.border[2], 0.5f)), null, Kk.Ink)
        KkCardMaterial.CHROME -> CodexRarityFill(CodexChromeStops.map { frame.border[it.second.coerceAtMost(frame.border.lastIndex)] }, null, Kk.Ink,
            CodexChromeStops.map { it.first })
        KkCardMaterial.PRISMATIC -> CodexRarityFill(frame.prism?.stops ?: roles.spectrum.stops, null, Kk.Ink)
        KkCardMaterial.GOLD -> (frame.gold ?: roles.gold).let { gold -> CodexRarityFill(listOf(gold.base, gold.light, gold.base), gold.seam, Kk.Ink) }
        else -> CodexRarityFill(listOf(kkMix(frame.border[0], frame.border[1], 0.5f)), null, Kk.Bone)
    }
}

/**
 * One cell of the Items matrix with its [marks] ([codexItemCellMarks] for this cell's size). A found
 * item lies on its rarity [fill] ([brush] made for the cell) with its glyph's primary mark in the fill's
 * accent (the fill and the band carry the rarity, so the inventory glyph's rank polygon and markers
 * would only crowd the mark), the element strip down the right, a gold rim when Reborn, the threat
 * hatch when suppressed, the stack bar along the bottom while in the build and the NEW notch top-left,
 * outlined in Ink so it stands off every fill. An undiscovered item is an anonymous hatch; a
 * Rebirth-gated one its gold tier numeral. [notch] is a scratch path. Allocation-free.
 */
internal fun DrawScope.drawCodexItemRowCell(
    entry: CodexEntry,
    fill: CodexRarityFill?,
    brush: Brush?,
    marks: CodexItemCellMarks,
    measurer: CanvasTextMeasurer,
    notch: Path,
) {
    val roles = measurer.roles
    val hairline = marks.hairline
    when (val icon = entry.icon) {
        is CodexIcon.Item -> {
            val item = icon.definition
            if (brush != null) drawRect(brush) else drawRect(Kk.Ink2)
            val gilded = fill?.seam != null
            fill?.seam?.let { drawCodexCellOutline(it, hairline) }
            if (icon.reborn) drawRect(if (gilded) Kk.Ink else Kk.Gold, marks.rim.topLeft, marks.rim.size, style = kkStroke(marks.rimWidth))
            drawCanvasGlyph(item.primaryGlyphStyle(), marks.glyphCenter, marks.glyphRadius, fill?.accent ?: Kk.Bone)
            if (icon.suppressed) {
                drawRect(kkFillBrush(KkFillKind.HATCH, roles.threat.copy(alpha = 0.55f)), Offset.Zero, size)
                drawLine(roles.threat, Offset(size.width * 0.18f, size.height * 0.82f), Offset(size.width * 0.82f, size.height * 0.18f), 2.dp.toPx())
            }
            if (item.elements.isNotEmpty()) {
                // The element strip: an Ink line on its left, then one segment per element, an Ink line apart.
                val strip = marks.strip
                drawRect(Kk.Ink, strip.topLeft, Size(hairline, strip.height))
                item.elements.forEachIndexed { index, element ->
                    val segment = marks.stripSegment(index, item.elements.size)
                    drawRect(roles.of(element), segment.topLeft, segment.size)
                    if (index > 0) drawRect(Kk.Ink, Offset(segment.left, segment.top - hairline), Size(segment.width, hairline))
                }
            }
            if (icon.stack > 0) {
                // The stack bar: an Ink line on top, a segment per possible stack (lit up to the stack), Ink lines between.
                val bar = marks.bar
                val count = item.maxStacks.coerceAtLeast(1)
                drawRect(Kk.Ink, bar.topLeft, Size(bar.width, hairline))
                for (index in 0 until count) {
                    val segment = marks.barSegment(index, count)
                    drawRect(if (index < icon.stack) roles.you else Kk.Ink.copy(alpha = 0.45f), segment.topLeft, segment.size)
                    if (index > 0) drawRect(Kk.Ink, Offset(segment.left - hairline, segment.top), Size(hairline, segment.height))
                }
            }
            if (entry.isNew) {
                // NEW: a `you` corner triangle (the top-left half of its box) edged in Ink on all three sides, so it
                // stands off the gold and the spectrum, whose corners are as light as `you`: the legs' edges just
                // outside the box (in the inset), the hypotenuse across its line.
                val box = marks.notch
                notch.reset()
                notch.moveTo(box.left, box.top); notch.lineTo(box.right, box.top); notch.lineTo(box.left, box.bottom); notch.close()
                drawPath(notch, roles.you)
                drawRect(Kk.Ink, Offset(box.left - hairline, box.top - hairline), Size(box.width + hairline, hairline))
                drawRect(Kk.Ink, Offset(box.left - hairline, box.top), Size(hairline, box.height))
                drawLine(Kk.Ink, Offset(box.right, box.top), Offset(box.left, box.bottom), hairline)
            }
        }
        is CodexIcon.Locked -> {
            drawRect(Kk.Ink3)
            drawKkHatch(Rect(Offset.Zero, size), Kk.Gold.copy(alpha = 0.12f))
            drawCodexCellOutline(Kk.Gold.copy(alpha = 0.55f), hairline)
            // The tier the item waits for, half the cell tall; the lock beside it where the cell has room.
            val fontSize = (size.minDimension * 0.5f / density / measurer.scale.coerceAtLeast(0.1f) * 2f).toInt() / 2f
            val numeral = measureKkText(measurer, icon.rebirth.toString(), measurer.typography.condStyle(fontSize, lineHeightEm = 1f))
            drawKkText(numeral, size.width * 0.5f, size.height * 0.5f, Kk.Gold, KkAlign.CENTER, KkVAlign.CENTER)
            if (size.minDimension >= 30.dp.toPx()) {
                val lock = size.minDimension * 0.22f
                drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(marks.inset + lock * 0.5f, marks.inset + lock * 0.5f), lock, Kk.Gold)
            }
        }
        else -> {
            // Undiscovered: no glyph, so nothing tells which item waits here.
            drawRect(Kk.Ink3)
            drawKkHatch(Rect(Offset.Zero, size))
            drawCodexCellOutline(Kk.Line2, hairline)
        }
    }
}

/** A [width] px outline just inside the cell's edge. */
private fun DrawScope.drawCodexCellOutline(color: Color, width: Float) {
    drawRect(color, Offset(width * 0.5f, width * 0.5f), Size(size.width - width, size.height - width), style = kkStroke(width))
}

/** Selection rings of a matrix cell, inside the 4 dp gap: pinned an Ink then a Bone ring (to 3 dp out), previewed a `you` ring. */
internal fun DrawScope.drawCodexMatrixSelection(pinned: Boolean, previewed: Boolean, roles: KkRolePalette) {
    val ring = 1.5.dp.toPx()
    if (pinned) {
        drawRect(Kk.Ink, Offset(-ring * 0.5f, -ring * 0.5f), Size(size.width + ring, size.height + ring), style = kkStroke(ring))
        val outer = ring * 1.5f
        drawRect(Kk.Bone, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(ring))
    } else if (previewed) {
        val outer = 0.5.dp.toPx() + ring * 0.5f
        drawRect(roles.you, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(ring))
    }
}

/**
 * One reaction-matrix cell. The diagonal is the row element's Break; a discovered reaction is split
 * in its two element hues with its name; an undiscovered one is hatched with both glyphs and "?".
 * [feeds] outlines a cell a selected residue can trigger next; [dimmed] fades the others.
 */
internal fun DrawScope.drawCodexMatrixCell(
    cell: CodexMatrixCell,
    entry: CodexEntry,
    measurer: CanvasTextMeasurer,
    path: Path,
    hovered: Boolean,
    feeds: StatusElement?,
    dimmed: Boolean,
) {
    val roles = measurer.roles
    val alpha = if (dimmed) 0.32f else 1f
    val roomy = size.minDimension >= 76.dp.toPx()
    val glyph = min(size.minDimension * 0.26f, 26.dp.toPx())
    val pad = 6.dp.toPx()
    if (cell.isBreak) {
        val tone = roles.of(cell.row)
        drawRect(kkMix(Kk.Ink2, tone, if (hovered) 0.32f else 0.22f), alpha = alpha)
        // A sheared band across the top in the element hue.
        drawKkSheared(Rect(pad, pad, size.width - pad, pad + 4.dp.toPx()), tone.copy(alpha = alpha))
        drawRect(tone.copy(alpha = 0.7f * alpha), style = kkStroke(1.5.dp.toPx()))
        val glyphCenter = if (roomy) Offset(size.width * 0.5f, size.height * 0.42f) else Offset(size.width * 0.5f, size.height * 0.52f)
        drawKkIcon(cell.row.glyph(), glyphCenter, glyph * 1.15f, tone, alpha = alpha)
        if (roomy) drawCellName(measurer, entry.title, tone, alpha, bottom = true)
    } else {
        val reaction = requireNotNull(cell.reaction)
        val first = roles.of(reaction.first)
        val second = roles.of(reaction.second)
        val discovered = (entry.icon as? CodexIcon.Reaction)?.discovered == true
        if (discovered) {
            // Two-tone: the first element above the anti-diagonal, the second below it.
            path.reset()
            path.moveTo(0f, 0f); path.lineTo(size.width, 0f); path.lineTo(0f, size.height); path.close()
            drawPath(path, kkMix(first, Kk.Ink, if (hovered) 0.08f else 0.18f), alpha = alpha)
            path.reset()
            path.moveTo(size.width, 0f); path.lineTo(size.width, size.height); path.lineTo(0f, size.height); path.close()
            drawPath(path, kkMix(second, Kk.Ink, if (hovered) 0.08f else 0.18f), alpha = alpha)
            drawKkIcon(reaction.first.glyph(), Offset(pad + glyph * 0.5f, pad + glyph * 0.5f), glyph * 0.8f, Kk.Ink.copy(alpha = 0.75f), alpha = alpha)
            drawKkIcon(reaction.second.glyph(), Offset(size.width - pad - glyph * 0.5f, size.height - pad - glyph * 0.5f), glyph * 0.8f,
                Kk.Ink.copy(alpha = 0.75f), alpha = alpha)
            if (roomy) drawCellName(measurer, entry.title, Kk.Ink, alpha, bottom = false)
        } else {
            drawRect(if (hovered) Kk.Ink3 else kkMix(Kk.Ink2, Kk.Ink3, 0.5f), alpha = alpha)
            drawKkHatch(Rect(Offset.Zero, size))
            drawRect(Kk.Line2.copy(alpha = alpha), style = kkStroke(1.dp.toPx()))
            drawKkIcon(reaction.first.glyph(), Offset(pad + glyph * 0.5f, pad + glyph * 0.5f), glyph * 0.85f, first, alpha = alpha)
            drawKkIcon(reaction.second.glyph(), Offset(size.width - pad - glyph * 0.5f, size.height - pad - glyph * 0.5f), glyph * 0.85f, second, alpha = alpha)
            val mark = measureKkText(measurer, "?", measurer.typography.condStyle(if (roomy) 40f else 24f))
            drawKkText(mark, size.width * 0.5f, size.height * 0.5f, Kk.Mute, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        }
        if ((entry.icon as? CodexIcon.Reaction)?.reachable == true) {
            // Reachable in this run: a `you` corner notch.
            val notch = 11.dp.toPx()
            path.reset()
            path.moveTo(size.width - notch, 0f); path.lineTo(size.width, 0f); path.lineTo(size.width, notch); path.close()
            drawPath(path, roles.you, alpha = alpha)
        }
    }
    if (feeds != null && !dimmed) {
        val stroke = 3.dp.toPx()
        drawRect(roles.of(feeds), Offset(stroke * 0.5f, stroke * 0.5f), Size(size.width - stroke, size.height - stroke), style = kkStroke(stroke))
    }
}

/** A cell's name, wrapped to two lines and shrunk until it fits the cell's width. */
private fun DrawScope.drawCellName(measurer: CanvasTextMeasurer, name: String, color: Color, alpha: Float, bottom: Boolean) {
    val width = size.width - 12.dp.toPx()
    var fontSize = 17f
    var layout = measureKkText(measurer, name, measurer.typography.condStyle(fontSize, lineHeightEm = 0.95f), uppercase = true, maxWidth = width, maxLines = 2)
    while (fontSize > 9f && (layout.multiParagraph.didExceedMaxLines || longestWordWidth(measurer, name, fontSize) > width)) {
        fontSize -= 1f
        layout = measureKkText(measurer, name, measurer.typography.condStyle(fontSize, lineHeightEm = 0.95f), uppercase = true, maxWidth = width, maxLines = 2)
    }
    val y = if (bottom) size.height - 8.dp.toPx() - layout.size.height else (size.height - layout.size.height) * 0.5f
    drawKkText(layout, size.width * 0.5f, y + layout.size.height * 0.5f, color, KkAlign.CENTER, KkVAlign.CENTER, alpha)
}

private fun longestWordWidth(measurer: CanvasTextMeasurer, text: String, fontSize: Float): Float =
    text.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { word ->
        measureKkText(measurer, word, measurer.typography.condStyle(fontSize, lineHeightEm = 0.95f), uppercase = true).size.width.toFloat()
    } ?: 0f

/**
 * A pairing diagram: two relic medallions joined by a link in the link's tone. Concords are solid
 * bone, harmony a muted line, discords solid threat, dissonance a dashed threat line; an unknown
 * named pairing is dashed with a "?" on the link.
 */
internal fun DrawScope.drawCodexPairingDiagram(icon: CodexIcon.Pairing, catalog: UiCatalogSnapshot, measurer: CanvasTextMeasurer) {
    val roles = measurer.roles
    val radius = min(size.height * 0.42f, size.width * 0.2f)
    val left = Offset(radius + 2.dp.toPx(), size.height * 0.5f)
    val right = Offset(size.width - radius - 2.dp.toPx(), size.height * 0.5f)
    val tone = codexLinkTone(icon.kind, roles)
    val dashed = !icon.discovered || icon.kind == CodexLinkKind.DISSONANCE || icon.kind == CodexLinkKind.HARMONY
    val width = if (icon.kind.named) 3.dp.toPx() else 2.dp.toPx()
    drawLine(tone, Offset(left.x + radius * 0.9f, left.y), Offset(right.x - radius * 0.9f, right.y), width,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null)
    if (icon.bound) drawCircle(roles.you, 3.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.5f))
    drawRelicIcon(icon.first, catalog.relicPolicy, left, radius, rank = null, roles = roles)
    drawRelicIcon(icon.second, catalog.relicPolicy, right, radius, rank = null, roles = roles)
    if (icon.kind.named && !icon.discovered) {
        val mark = measureKkText(measurer, "?", measurer.typography.condStyle(18f))
        drawKkText(mark, size.width * 0.5f, size.height * 0.5f - 9.dp.toPx(), tone, KkAlign.CENTER, KkVAlign.CENTER)
    }
}

/** Link tone: named concord bone, harmony mute, interference threat. */
internal fun codexLinkTone(kind: CodexLinkKind, roles: KkRolePalette): Color = when (kind) {
    CodexLinkKind.CONCORD -> Kk.Bone
    CodexLinkKind.HARMONY -> Kk.Mute
    CodexLinkKind.DISCORD, CodexLinkKind.DISSONANCE -> roles.threat
}

/**
 * Tick ladder of [count] sheared ticks: the first [lit] in [color], the rest ink-4; ticks at the
 * [thresholds] stand taller so the tier steps read without numbers. Returns nothing; allocation-free.
 */
internal fun DrawScope.drawCodexLadder(rect: Rect, count: Int, lit: Int, thresholds: List<Int>, color: Color) {
    if (count <= 0) return
    val gap = 2.dp.toPx()
    val pitch = (rect.width + gap) / count
    for (index in 0 until count) {
        val step = (index + 1) in thresholds
        val top = if (step) rect.top else rect.top + rect.height * 0.35f
        val tick = Rect(rect.left + index * pitch, top, rect.left + index * pitch + pitch - gap, rect.bottom)
        drawKkSheared(tick, if (index < lit) color else if (step) Kk.Line2 else Kk.Ink4)
    }
}

/** [count] sheared tier pips at [topLeft], the first [filled] in [color]. */
internal fun DrawScope.drawCodexTierPips(topLeft: Offset, count: Int, filled: Int, color: Color, sizePx: Float) {
    val gap = sizePx * 0.4f
    for (index in 0 until count) {
        val x = topLeft.x + index * (sizePx + gap)
        val rect = Rect(x, topLeft.y, x + sizePx, topLeft.y + sizePx)
        if (index < filled) drawKkSheared(rect, color) else drawKkSheared(rect, Kk.Line2, kkStroke(max(1f, 1.dp.toPx())))
    }
}

/** A relic key's tone: threat when a bound relic opposes it, bone otherwise. */
internal fun codexKeyTone(key: RelicKey, opposedBound: Set<RelicKey>, roles: KkRolePalette): Color =
    if (key in opposedBound) roles.threat else Kk.Bone
