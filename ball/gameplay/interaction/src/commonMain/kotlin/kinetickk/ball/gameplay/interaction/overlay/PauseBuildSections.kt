// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/** The synergy section in one row of [chip] height from ([left], [top]): its label in the first 40 % of [width], then the chips that fit. */
internal fun DrawScope.drawPauseSynergyRow(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    chip: Float,
    unit: Float,
    k: Float,
) {
    val labelBox = width * 0.4f
    val label = fitOverlayText(ui, overview.synergyLabel, KkTextRole.LABEL, 11f * k, labelBox, uppercase = true)
    drawPauseText(PauseTextKind.LABEL, label, left, top + chip * 0.5f, Kk.Mute, labelBox, valign = KkVAlign.CENTER)
    val chipsLeft = left + label.size.width + 10f * unit
    drawPauseSynergies(overview, ui, chipsLeft, top, left + width - chipsLeft, top + chip, chip, 10.5f * k, k)
}

/**
 * The relic matrix from [top]: bracket stacks ([PauseBracket], named in the regular layout), then
 * the diamonds with each relic's own rune in its element's hue, its rank as pips and the net
 * multiplier of its upside under it. Empty slots show the hatched slot.
 */
internal fun DrawScope.drawPauseRelics(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    slot: Float,
    pitch: Float,
    levelStep: Float,
    k: Float,
    names: Boolean,
    time: Float,
): Float {
    val roles = ui.roles
    val half = slot * 0.5f
    val stacks = overview.bracketLevels.coerceIn(0, MAX_BRACKET_STACKS)
    val slotTop = top + stacks * levelStep + if (stacks > 0) levelStep * 0.5f else 0f
    val centerY = slotTop + half
    val firstCenter = left + half + 6f * k * density
    val stroke = 2f * k * density
    for (index in overview.brackets.indices) {
        val bracket = overview.brackets[index]
        val color = if (bracket.interferes) roles.threat else Kk.Bone
        val ax = firstCenter + bracket.from * pitch
        val bx = firstCenter + bracket.to * pitch
        val lineY = slotTop - levelStep * 0.35f - bracket.level * levelStep
        val legBottom = if (bracket.level == 0) slotTop - 2f * k * density else lineY + levelStep - 3f * k * density
        val effect = if (bracket.discovered) null else WorldStrokes.dashEffect
        drawLine(color, Offset(ax - stroke * 0.5f, lineY), Offset(bx + stroke * 0.5f, lineY), stroke, pathEffect = effect)
        drawLine(color, Offset(ax, lineY), Offset(ax, legBottom), stroke)
        drawLine(color, Offset(bx, lineY), Offset(bx, legBottom), stroke)
        if (names) {
            val box = (bx - ax) + pitch * 0.8f
            val name = fitOverlayText(ui, bracket.name, KkTextRole.COND, 13f * k, box, uppercase = true)
            drawPauseText(PauseTextKind.SYNERGY_NAME, name, (ax + bx) * 0.5f, lineY - 3f * k * density, color, box,
                KkAlign.CENTER, KkVAlign.BASELINE)
        }
    }
    val pipHeight = (if (names) 3f else 2.5f) * k
    val pipsTop = slotTop + slot + 4f * k * density
    val multiplierTop = pipsTop + pipHeight * density + 3f * k * density
    var bottom = pipsTop + pipHeight * density
    for (index in overview.relics.indices) {
        val center = Offset(firstCenter + index * pitch, centerY)
        if (center.x + half > left + width + 1f) break
        val relic = overview.relics[index]
        if (relic == null) {
            drawKkRelicSlot(center, Color.Unspecified, sizeDp = slot / density)
            continue
        }
        val hue = relicHue(relic.definition, roles)
        val alpha = if (relic.spent) 0.45f else 1f
        drawKkRelicSlot(center, hue, sizeDp = slot / density, alpha = alpha)
        drawRelicRune(relic.definition, center, half * 0.62f, hue, alpha)
        if (relic.definition.isSovereign) {
            // Sovereign relics carry the gold of Rebirth on their rim.
            drawPath(PauseShapes.diamond(index, center.x, center.y, half + 3f * k * density), Kk.Gold, alpha = 0.7f * alpha,
                style = kkStroke(1.5f * k * density))
        }
        val pipWidth = (if (names) 7f else 5f) * k
        val pipsWidth = relic.maxRank * pipWidth * density + (relic.maxRank - 1) * 2f * k * density
        drawKkPips(Offset(center.x - pipsWidth * 0.5f, pipsTop), relic.maxRank, relic.rank, hue, sizeDp = pipHeight, widthDp = pipWidth,
            gapDp = 2f * k, emptyColor = Kk.Ink4)
        val box = pitch - 4f * k * density
        val multiplier = fitOverlayText(ui, relic.multiplier, KkTextRole.COND, (if (names) 15f else 11f) * k, box, tabular = true)
        drawPauseText(PauseTextKind.MULTIPLIER, multiplier, center.x, multiplierTop, runColor(relic.tone, roles), box, KkAlign.CENTER)
        bottom = maxOf(bottom, multiplierTop + multiplier.kkBoxHeight)
    }
    return bottom
}

/** Bracket stacks reserved over the pause matrix. */
private const val MAX_BRACKET_STACKS = 2

/**
 * The cost list from [top], fitted to end by [bottom]: one entry per liability (bound relics first,
 * then the lingering cost of a replaced one, faded), each a threat stripe, the relic's rune and the
 * cost text on up to three lines. Every entry shares one text size ([PauseCostFit]), never below
 * [minScale] of [size], and is as tall as its own text; at that floor the list may run on to
 * [limit]. Costs never hide behind a toggle; should even that leave entries out, a "+N" mark
 * counts them. Returns the list's bottom.
 */
internal fun DrawScope.drawPauseCosts(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    size: Float,
    k: Float,
    maxEntries: Int = Int.MAX_VALUE,
    minScale: Float = MIN_COST_SCALE,
    limit: Float = bottom,
): Float {
    val roles = ui.roles
    val rune = 7f * k * density
    val textLeft = left + rune * 2f + 8f * k * density
    val box = left + width - textLeft
    val count = minOf(overview.costs.size, maxEntries)
    if (count == 0) return top
    val gap = 6f * k * density
    val runeHeight = rune * 2f + 2f * k * density
    val fit = PauseCostFit.of(overview.costs, count, ui, size, box, bottom - top, runeHeight, gap, minScale)
    var y = top
    var shown = 0
    for (index in 0 until count) {
        val height = fit.height(index)
        // An entry that does not fit leaves room for the count of those after it.
        val reserve = if (index < count - 1) fit.moreHeight else 0f
        if (y + height + reserve > maxOf(bottom, limit) + 1f) break
        val cost = overview.costs[index]
        val alpha = if (cost.lingering) 0.55f else 1f
        val text = fit.layout(index)
        drawRect(roles.threat, Offset(left, y + 1f * k * density), Size(2.5f * k * density, height - 2f * k * density), alpha = alpha)
        drawRelicRune(cost.definition, Offset(left + 4f * k * density + rune, y + rune + 1f * k * density), rune, relicHue(cost.definition, roles),
            alpha)
        drawPauseText(PauseTextKind.COST, text, textLeft, y, if (cost.lingering) roles.threat.copy(alpha = 0.7f) else Kk.Bone2, box)
        y += height + gap
        shown++
    }
    if (shown < count) {
        val more = fitOverlayText(ui, moreLabel(count - shown), KkTextRole.MONO, size, box)
        drawPauseText(PauseTextKind.MORE, more, textLeft, y, roles.threat, box)
        y += more.kkBoxHeight + gap
    }
    return y - gap
}

/**
 * The fitted cost list of the last drawn frame (a paused frame redraws the same list): one text
 * size for every entry, the largest from the requested size down to its floor (a share of it) at
 * which all of them, each on up to [COST_LINES] lines without breaking inside a word, fit the
 * room; at the floor the list is as tall as its text. Fitted once per list, measurer, box and room.
 */
private object PauseCostFit {
    private var costs: List<PauseCostLine>? = null
    private var measurer: CanvasTextMeasurer? = null
    private val key = FloatArray(7) { Float.NaN }
    private val layouts = arrayOfNulls<TextLayoutResult>(MAX_COST_LINES)
    private val heights = FloatArray(MAX_COST_LINES)

    /** Height of the "+N" mark of entries left out. */
    var moreHeight = 0f
        private set

    fun layout(index: Int): TextLayoutResult = requireNotNull(layouts[index])

    fun height(index: Int): Float = heights[index]

    fun of(
        costs: List<PauseCostLine>,
        count: Int,
        measurer: CanvasTextMeasurer,
        size: Float,
        box: Float,
        room: Float,
        minHeight: Float,
        gap: Float,
        minScale: Float,
    ): PauseCostFit {
        if (this.costs === costs && this.measurer === measurer && key[0] == count.toFloat() && key[1] == size && key[2] == box &&
            key[3] == room && key[4] == minHeight && key[5] == gap && key[6] == minScale
        ) return this
        this.costs = costs
        this.measurer = measurer
        key[0] = count.toFloat()
        key[1] = size
        key[2] = box
        key[3] = room
        key[4] = minHeight
        key[5] = gap
        key[6] = minScale
        var scale = 1f
        while (true) {
            val last = scale <= minScale + 0.001f
            var total = -gap
            var whole = true
            for (index in 0 until count) {
                val layout = measureKkText(measurer, costs[index].text, measurer.typography.kkStyle(KkTextRole.BODY, size * scale),
                    maxWidth = box, maxLines = COST_LINES)
                whole = whole && layout.fitsWithoutCuts()
                layouts[index] = layout
                heights[index] = maxOf(layout.kkBoxHeight, minHeight)
                total += heights[index] + gap
            }
            if ((whole && total <= room) || last) break
            scale = maxOf(minScale, scale - 0.05f)
        }
        moreHeight = measureKkText(measurer, "+9", measurer.typography.kkStyle(KkTextRole.MONO, size)).kkBoxHeight + gap
        return this
    }
}

/** "+N", the count of entries a full list leaves out (cached: the paused frame redraws it). */
internal fun moreLabel(count: Int): String = MoreLabels[count.coerceIn(0, MoreLabels.lastIndex)]

private val MoreLabels = Array(64) { "+$it" }

/** The cost list's smallest text, as a share of its size (see [drawPauseCosts]), and the lines an entry may take. */
internal const val MIN_COST_SCALE = 0.6f
private const val COST_LINES = 3

/** A number keeps its unit on its line ("30 %" and "600 ед/с" never break before the unit). */
internal fun String.keepUnitsWithNumbers(): String = UnitAfterNumber.replace(this) { it.groupValues[1] + "\u00A0" + it.groupValues[2] }

/**
 * A space between a number and its unit. Units end where no letter follows (an explicit class:
 * `\b` sees only ASCII letters on some platforms, so it never ends a Cyrillic unit).
 */
private val UnitAfterNumber = Regex("([0-9]) (%|ед/с(?![А-Яа-яЁё])|ед(?![А-Яа-яЁё])|u/s(?![A-Za-z])|u(?![A-Za-z])|s(?![A-Za-z])|с(?![А-Яа-яЁё]))")

/** Line height of the body role the costs use, with a little room for glyph overhang. */
internal const val COST_LINE_HEIGHT = 1.46f

/**
 * Resonance from [top]: one row per tag with points, in [columns] columns (row-major): the tag's
 * glyph and name, then a ladder of ticks for its points with a notch at each tier threshold, lit
 * in the you color once reached. [maxRows] caps the rows per column; tags past the cap leave a
 * "+N" mark in the last cell.
 */
internal fun DrawScope.drawPauseTags(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    row: Float,
    columns: Int,
    size: Float,
    k: Float,
    maxRows: Int = Int.MAX_VALUE,
): Float {
    val roles = ui.roles
    val tags = overview.tags
    if (tags.isEmpty()) return top
    val gap = 12f * k * density
    val cell = (width - gap * (columns - 1)) / columns
    val thresholds = overview.tagThresholds
    val ticks = (thresholds.lastOrNull() ?: 7).coerceIn(3, 9)
    val glyph = row * 0.72f
    // The name keeps about two fifths of the cell; the ladder takes the rest, its ticks up to 16 dp wide.
    val ladderRoom = (cell - glyph - 6f * k * density - 8f * k * density) * 0.58f
    val tick = (ladderRoom / (ticks + (ticks - 1) * 0.45f)).coerceIn(3f, 16f * k * density)
    val tickGap = tick * 0.45f
    val ladder = ticks * tick + (ticks - 1) * tickGap
    val pipHeightPx = (row * 0.34f).coerceAtMost(tick * 1.2f).coerceAtLeast(3f)
    val nameBox = (cell - glyph - 6f * k * density - ladder - 8f * k * density).coerceAtLeast(10f)
    val capacity = if (maxRows == Int.MAX_VALUE) Int.MAX_VALUE else maxRows * columns
    val shown = if (tags.size > capacity) capacity - 1 else tags.size
    if (shown < tags.size) {
        val x = left + (shown % columns) * (cell + gap)
        val more = fitOverlayText(ui, moreLabel(tags.size - shown), KkTextRole.COND, size, cell)
        drawPauseText(PauseTextKind.MORE, more, x, top + (shown / columns) * row + row * 0.5f, Kk.Bone2, cell, valign = KkVAlign.CENTER)
    }
    for (index in 0 until shown) {
        val rowIndex = index / columns
        val tag = tags[index]
        val x = left + (index % columns) * (cell + gap)
        val y = top + rowIndex * row
        val middle = y + row * 0.5f
        drawKkIcon(KkIcon.Tags[tag.tag.ordinal], Offset(x + glyph * 0.5f, middle), glyph, if (tag.tier > 0) Kk.Bone else Kk.Mute)
        val name = fitOverlayText(ui, tag.label, KkTextRole.COND, size, nameBox, uppercase = true)
        // The Keynote tag is listed without points as a muted ghost; it is bracketed at any count.
        drawPauseText(PauseTextKind.TAG_NAME, name, x + glyph + 6f * k * density, middle, when {
            tag.tier > 0 -> Kk.Bone
            tag.points > 0 -> Kk.Bone2
            else -> Kk.Mute
        }, nameBox, valign = KkVAlign.CENTER)
        if (tag.keynote) drawPauseKeynoteBrackets(x - 3f * k * density, y + 1f * k * density, x + cell + 3f * k * density, y + row - 1f * k * density,
            5f * k * density, 1.5f * k * density)
        val ladderLeft = x + cell - ladder
        val pipTop = middle - pipHeightPx * 0.5f
        drawKkPips(Offset(ladderLeft, pipTop), ticks, tag.points.coerceAtMost(ticks), Kk.Bone, sizeDp = pipHeightPx / density,
            widthDp = tick / density, gapDp = tickGap / density, emptyColor = Kk.Ink4)
        for (threshold in thresholds) {
            if (threshold > ticks) continue
            val notchX = ladderLeft + threshold * (tick + tickGap) - tickGap * 0.5f
            val reached = tag.points >= threshold
            drawRect(if (reached) roles.you else Kk.Mute, Offset(notchX - 0.75f * k * density, pipTop - pipHeightPx * 0.45f),
                Size(1.5f * k * density, pipHeightPx * 1.9f))
        }
    }
    val rows = minOf(maxRows, (tags.size + columns - 1) / columns)
    return top + rows * row
}
