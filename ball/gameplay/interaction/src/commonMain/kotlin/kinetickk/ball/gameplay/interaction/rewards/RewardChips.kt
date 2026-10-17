// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.drawRelicIcon
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.drawKkThreatHatch
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.max
import kotlin.math.min

// Reward card chips: identity marks beside the icon (Risk, Reborn, Heirloom, tags) and the forecast
// row of what a card changes in the build. Glyphs carry identity, hues only element and role.

/** Chip metrics in dp and board sp (before the text setting). */
internal class RewardChipStyle(val heightDp: Float, val fontSize: Float)

internal val RewardChipRegular = RewardChipStyle(22f, 12f)
internal val RewardChipCompact = RewardChipStyle(18f, 10.5f)

private fun chipLabelStyle(measurer: CanvasTextMeasurer, style: RewardChipStyle) =
    measurer.typography.labelStyle(style.fontSize, trackingEm = 0.08f)

/**
 * Glyphs before the label: a tag or element mark, both elements of a reaction, the suppression
 * lock and the rune of the relic that voids the card, an attuned tag's aspect diamond and tag mark.
 */
private fun RewardChip.glyphCount(): Int = when (kind) {
    RewardChipKind.TAG, RewardChipKind.TAG_TIER -> if (tag != null) 1 else 0
    RewardChipKind.ATTUNED -> if (tag != null) 2 else 1
    RewardChipKind.REACTION -> elements.size.coerceAtMost(2)
    RewardChipKind.AFFINITY, RewardChipKind.ELEMENT -> elements.size.coerceAtMost(1)
    RewardChipKind.SIGNATURE -> if (core != null) 1 else 0
    RewardChipKind.SUPPRESSED -> if (rune != null) 2 else 1
    else -> 0
}

/** Tier pips after a TAG_TIER label, ring marks after a Reborn or Heirloom label. */
private fun RewardChip.trailingMarks(): Int = when (kind) {
    RewardChipKind.TAG_TIER -> pips.coerceIn(0, 5)
    RewardChipKind.REBORN, RewardChipKind.HEIRLOOM -> after.coerceIn(0, MaxRingMarks)
    else -> 0
}

/** Ring marks a Reborn chip shows; tiers past it fill the rings instead of adding more. */
internal const val MaxRingMarks = 5

private fun chipPad(height: Float) = height * 0.36f
private fun chipGlyph(height: Float) = height * 0.64f
private fun chipGap(height: Float) = height * 0.22f
private fun pipWidth(height: Float) = height * 0.3f
private fun ringPitch(height: Float) = height * 0.34f

/** Width in px of [chip] at [style]. */
internal fun rewardChipWidth(measurer: CanvasTextMeasurer, chip: RewardChip, style: RewardChipStyle, density: Float): Float {
    val height = style.heightDp * density
    val label = measureKkText(measurer, chip.label, chipLabelStyle(measurer, style), uppercase = true)
    val glyphs = chip.glyphCount()
    var width = chipPad(height) * 2f + label.size.width
    if (glyphs > 0) width += glyphs * chipGlyph(height) + (glyphs - 1) * height * 0.04f + chipGap(height)
    val marks = chip.trailingMarks()
    if (marks > 0) {
        width += chipGap(height) + when (chip.kind) {
            RewardChipKind.TAG_TIER -> marks * pipWidth(height) + (marks - 1) * height * 0.12f
            else -> marks * ringPitch(height)
        }
    }
    return width
}

/** Faces and inks of a chip kind (under the color-vision preset [roles]). */
private class ChipInk(val face: Color, val border: Color, val text: Color, val glyph: Color)

private fun RewardChip.ink(roles: KkRolePalette): ChipInk = when (kind) {
    RewardChipKind.TAG -> if (active) ChipInk(Kk.Ink3, Kk.Bone2.copy(alpha = 0.55f), Kk.Bone, Kk.Bone)
        else ChipInk(Color.Transparent, Kk.Line2, Kk.Bone2, Kk.Mute)
    RewardChipKind.ATTUNED -> {
        // The relic's attunement, not the card's own tag: outlined in the aspect hue.
        val hue = aspect?.overlayColor(roles) ?: Kk.Bone2
        if (active) ChipInk(hue.copy(alpha = 0.16f), hue, Kk.Bone, Kk.Bone) else ChipInk(Color.Transparent, hue.copy(alpha = 0.55f), Kk.Bone2, Kk.Mute)
    }
    RewardChipKind.RISK -> ChipInk(roles.threat, Color.Transparent, Kk.Ink, Kk.Ink)
    RewardChipKind.REBORN -> ChipInk(roles.gold.base.copy(alpha = 0.14f), roles.gold.base, roles.gold.base, roles.gold.base)
    RewardChipKind.HEIRLOOM -> ChipInk(roles.gold.base, Color.Transparent, Kk.Ink, Kk.Ink)
    RewardChipKind.SUPPRESSED -> ChipInk(roles.threat.copy(alpha = 0.12f), roles.threat, roles.threat, roles.threat)
    RewardChipKind.REACTION -> ChipInk(Kk.Ink3, if (discovered) Kk.Bone2.copy(alpha = 0.5f) else Kk.Line2, Kk.Bone, Kk.Bone)
    RewardChipKind.TAG_TIER -> ChipInk(Kk.Ink3, roles.you.copy(alpha = 0.7f), Kk.Bone, Kk.Bone)
    RewardChipKind.AFFINITY -> {
        val hue = elements.firstOrNull()?.overlayColor(roles) ?: Kk.Bone
        if (active) ChipInk(hue.copy(alpha = 0.2f), hue, Kk.Bone, hue) else ChipInk(Kk.Ink3, hue.copy(alpha = 0.55f), Kk.Bone2, hue)
    }
    RewardChipKind.CONCORD -> ChipInk(if (discovered) Kk.Bone else Kk.Ink3, if (discovered) Color.Transparent else Kk.Bone2,
        if (discovered) Kk.Ink else Kk.Bone, Kk.Ink)
    RewardChipKind.DISCORD -> ChipInk(roles.threat.copy(alpha = if (discovered) 0.22f else 0.1f), roles.threat, roles.threat, roles.threat)
    RewardChipKind.HARMONY -> ChipInk(Kk.Ink3, Kk.Mute.copy(alpha = 0.6f), Kk.Bone2, Kk.Bone2)
    RewardChipKind.DISSONANCE -> ChipInk(Kk.Ink3, roles.threat.copy(alpha = 0.6f), roles.threat, roles.threat)
    // The run character's own weapon: a filled `you` plate with the character's form.
    RewardChipKind.SIGNATURE -> ChipInk(roles.you, Color.Transparent, Kk.Ink, Kk.Ink)
    // A weapon's element: outlined in its hue (Kinetic in the preset's volt).
    RewardChipKind.ELEMENT -> {
        val hue = elements.firstOrNull()?.overlayColor(roles) ?: roles.kinetic
        ChipInk(hue.copy(alpha = 0.14f), hue, Kk.Bone, hue)
    }
}

/** Dashed chip outline of an unmet pairing, cached per stroke width (draw-thread confined). */
private object ChipStrokes {
    private var width = -1f
    private var dashed: Stroke? = null

    fun dashed(strokeWidth: Float, density: Float): Stroke {
        dashed?.takeIf { width == strokeWidth }?.let { return it }
        width = strokeWidth
        return Stroke(strokeWidth, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density)))
            .also { dashed = it }
    }
}

/**
 * Draws [chip] at [topLeft] ([width] px from [rewardChipWidth]). [paths] slot 0 holds the plate.
 * Unmet reactions and discords are hatched, an unmet concord is outlined dashed; a reaction's
 * plate is split in its two element hues.
 */
internal fun DrawScope.drawRewardChip(
    measurer: CanvasTextMeasurer,
    chip: RewardChip,
    topLeft: Offset,
    width: Float,
    style: RewardChipStyle,
    paths: KkPathCache,
) {
    val roles = measurer.roles
    val height = style.heightDp * density
    val rect = Rect(topLeft, Size(width, height))
    val plate = paths.slab(0, rect, height * 0.27f)
    val ink = chip.ink(roles)
    if (ink.face.alpha > 0f) drawPath(plate, ink.face)
    if (chip.kind == RewardChipKind.REACTION && chip.elements.size == 2) {
        clipPath(plate) {
            drawRect(chip.elements[0].overlayColor(roles).copy(alpha = 0.22f), rect.topLeft, Size(rect.width * 0.5f, rect.height))
            drawRect(chip.elements[1].overlayColor(roles).copy(alpha = 0.22f), Offset(rect.center.x, rect.top), Size(rect.width * 0.5f, rect.height))
        }
    }
    val hatched = !chip.discovered && (chip.kind == RewardChipKind.REACTION || chip.kind == RewardChipKind.DISCORD)
    if (hatched) {
        // A faint hatch: the label on top stays legible where hatching carries the threat (color vision).
        if (chip.kind == RewardChipKind.DISCORD) drawKkThreatHatch(plate, roles, roles.threat.copy(alpha = 0.3f)) else drawKkHatch(plate, Kk.Bone.copy(alpha = 0.12f))
    }
    if (chip.kind == RewardChipKind.RISK && roles.hatchThreats) drawKkThreatHatch(plate, roles, Kk.Ink)
    if (ink.border.alpha > 0f) {
        val inset = density * 0.75f
        val outline = paths.slab(1, rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, height * 0.27f)
        val stroke = if (chip.kind == RewardChipKind.CONCORD && !chip.discovered) ChipStrokes.dashed(1.5f * density, density) else kkStroke(1.5f * density)
        drawPath(outline, ink.border, style = stroke)
    }
    val cy = rect.center.y
    var x = rect.left + chipPad(height)
    val glyph = chipGlyph(height)
    repeat(chip.glyphCount()) { index ->
        val center = Offset(x + glyph * 0.5f, cy)
        val rune = chip.rune
        when {
            chip.kind == RewardChipKind.SUPPRESSED && index == 1 && rune != null ->
                // The rune of the bound relic whose liability voids the card, in its aspect hue.
                drawRelicIcon(rune, ChipRunePolicy, center, glyph * 0.5f, accent = rune.aspect.overlayColor(roles))
            chip.kind == RewardChipKind.ATTUNED && index == 0 -> {
                val half = glyph * 0.28f
                rotate(45f, center) {
                    drawRect(chip.aspect?.overlayColor(roles) ?: Kk.Bone2, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f))
                }
            }
            else -> {
                val icon = when (chip.kind) {
                    RewardChipKind.SUPPRESSED -> KkIcon.SYSTEM_LOCKED
                    RewardChipKind.TAG, RewardChipKind.TAG_TIER, RewardChipKind.ATTUNED -> chip.tag?.overlayIcon() ?: KkIcon.UI_PLUS
                    RewardChipKind.SIGNATURE -> chip.core?.let { KkIcon.Forms.getOrNull(it.ordinal) } ?: KkIcon.UI_PLUS
                    else -> chip.elements[index].overlayIcon()
                }
                val color = when (chip.kind) {
                    RewardChipKind.REACTION -> chip.elements[index].overlayColor(roles)
                    else -> ink.glyph
                }
                drawKkIcon(icon, center, glyph, color)
            }
        }
        x += glyph + height * 0.04f
    }
    if (chip.glyphCount() > 0) x += chipGap(height) - height * 0.04f
    val label = measureKkText(measurer, chip.label, chipLabelStyle(measurer, style), uppercase = true)
    drawKkText(label, x, cy, ink.text, KkAlign.START, KkVAlign.CENTER)
    x += label.size.width
    val marks = chip.trailingMarks()
    if (marks <= 0) return
    x += chipGap(height)
    when (chip.kind) {
        RewardChipKind.TAG_TIER -> {
            // Sheared tier pips: reached before in bone, gained by this card in `you`, the rest empty.
            val pipHeight = height * 0.42f
            val top = cy - pipHeight * 0.5f
            val gap = height * 0.12f / density
            drawKkPips(Offset(x, top), marks, chip.after.coerceIn(0, marks), roles.you, sizeDp = pipHeight / density,
                widthDp = pipWidth(height) / density, gapDp = gap, emptyColor = Kk.Line2)
            if (chip.before > 0) drawKkPips(Offset(x, top), chip.before.coerceAtMost(marks), chip.before.coerceAtMost(marks), Kk.Bone2,
                sizeDp = pipHeight / density, widthDp = pipWidth(height) / density, gapDp = gap)
        }
        else -> {
            // Tier ring marks; tiers past the visible marks fill them from the left.
            val ring = height * 0.11f
            val filled = (chip.after - MaxRingMarks).coerceIn(0, marks)
            for (index in 0 until marks) {
                val center = Offset(x + ringPitch(height) * (index + 0.5f), cy)
                if (index < filled) drawCircle(ink.text, ring, center) else drawCircle(ink.text, ring, center, style = kkStroke(1.2f * density))
            }
        }
    }
}

/**
 * Chips laid out left to right in up to [maxRows] rows of the available width: a chip that does
 * not fit is left out and the next one tried, so earlier chips (the more important) always show.
 */
@Composable
internal fun RewardChipFlow(
    chips: List<RewardChip>,
    style: RewardChipStyle,
    textScale: Float,
    modifier: Modifier = Modifier,
    maxRows: Int = 1,
    gapDp: Float = 6f,
    maxCount: Int = Int.MAX_VALUE,
) {
    if (chips.isEmpty()) return
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val roles = LocalKkRolePalette.current
    val language = LocalAppLanguage.current
    val widths = remember(chips, measurer, density, style, roles) {
        FloatArray(chips.size) { rewardChipWidth(measurer, chips[it], style, density) }
    }
    Layout(
        content = {
            chips.forEachIndexed { index, chip -> key(index, chip) {
                val paths = remember { KkPathCache(2) }
                Box(
                    Modifier.size((widths[index] / density).dp, style.heightDp.dp)
                        .semantics {
                            text = AnnotatedString(chip.label)
                            if (chip.elements.isNotEmpty()) {
                                contentDescription = chip.elements.joinToString(" ") { it.displayLabel.localizedContent(language) } + " " + chip.label
                            }
                        }
                        .drawBehind { drawRewardChip(measurer, chip, Offset.Zero, size.width, style, paths) },
                )
            } }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = (gapDp * density).toInt()
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        val positions = arrayOfNulls<androidx.compose.ui.unit.IntOffset>(placeables.size)
        // Never more rows than the height given: chips past them are left out like those too wide.
        val rowsAllowed = if (constraints.hasBoundedHeight && rowHeight > 0) {
            min(maxRows, ((constraints.maxHeight + gap) / (rowHeight + gap)).coerceAtLeast(1))
        } else maxRows
        var row = 0
        var x = 0
        var placed = 0
        var widest = 0
        for (index in placeables.indices) {
            if (placed >= maxCount) break
            val width = placeables[index].width
            if (width > maxWidth) continue
            if (x > 0 && x + gap + width > maxWidth) {
                if (row + 1 >= rowsAllowed) continue
                row++
                x = 0
            }
            val left = if (x == 0) 0 else x + gap
            positions[index] = androidx.compose.ui.unit.IntOffset(left, row * (rowHeight + gap))
            x = left + width
            widest = max(widest, x)
            placed++
        }
        val rows = if (placed == 0) 0 else positions.filterNotNull().maxOf { it.y } / (rowHeight + gap).coerceAtLeast(1) + 1
        val height = if (rows == 0) 0 else rows * rowHeight + (rows - 1) * gap
        // Chips start at the leading edge: a parent asking for a minimum width gets it as empty space after them.
        layout(min(widest, maxWidth).coerceAtLeast(constraints.minWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable -> positions[index]?.let { placeable.place(it) } }
        }
    }
}

/** A chip draws a relic's rune without rank markers. */
private val ChipRunePolicy = RelicPolicy(1, 1)

/** Numbers in rule and upside text: signed, with group or decimal separators and an optional unit mark. */
private val RewardNumber = Regex("[−+×]?\\d+(?:[.,  ]\\d+)*(?:[   ]?%)?")

/** Word joiner: keeps the two sides of a unit's slash on one line ("ед/с", "u/s"). */
private const val WordJoiner = '\u2060'
private const val NoBreakSpace = '\u00A0'

/** Unit marks that follow a number and must stay on its line. */
private val UnitMarks = arrayOf("%", "u/s", "ед/с", "ед", "s", "с")

private fun unitStartsAt(text: String, index: Int): Boolean = UnitMarks.any { mark ->
    text.startsWith(mark, index) && (index + mark.length >= text.length || !text[index + mark.length].isLetter())
}

/**
 * [text] with its units kept whole and on their number's line: the space between a number and its
 * unit becomes a no-break space, and a slash between two marks ("ед/с", "u/s", "3/4") is held by
 * word joiners, so the line breaker never splits "40 %" or "ед/с". [offsets] (text length + 1),
 * when given, receives where each original character landed.
 */
internal fun rewardKeepUnits(text: String, offsets: IntArray? = null): String {
    val out = StringBuilder(text.length + 4)
    for (index in text.indices) {
        offsets?.set(index, out.length)
        val char = text[index]
        when {
            char == ' ' && index > 0 && text[index - 1].isDigit() && unitStartsAt(text, index + 1) -> out.append(NoBreakSpace)
            char == '/' && index > 0 && index < text.lastIndex && !text[index - 1].isWhitespace() && !text[index + 1].isWhitespace() ->
                out.append(WordJoiner).append('/').append(WordJoiner)
            else -> out.append(char)
        }
    }
    offsets?.set(text.length, out.length)
    return out.toString()
}

/**
 * [text] with its numbers in [number] (semi-bold) and the [emphasis] ranges (a relic's rank
 * values) in [emphasisColor] bold; the rest keeps the paragraph color.
 */
internal fun rewardRichText(
    text: String,
    number: Color,
    emphasis: List<IntRange> = emptyList(),
    emphasisColor: Color = number,
): AnnotatedString = buildAnnotatedString {
    val offsets = IntArray(text.length + 1)
    val shown = rewardKeepUnits(text, offsets)
    append(shown)
    RewardNumber.findAll(shown).forEach { match ->
        addStyle(SpanStyle(color = number, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
    }
    emphasis.forEach { range ->
        if (range.first in text.indices && range.last in text.indices) {
            addStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold), offsets[range.first], offsets[range.last + 1])
        }
    }
}
