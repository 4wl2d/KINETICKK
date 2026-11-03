// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * One cell per element across [width] from [top]: the glyph (ringed on the dominant element), then
 * (with [names]) the element's name under it across the cell, and a meter of one pip per affinity
 * point notched at the affinity tiers. The names share one size, the size the longest of them
 * needs, and sit clear of the ring.
 */
internal fun DrawScope.drawPauseElements(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    k: Float,
    names: Boolean,
): Float {
    val roles = ui.roles
    val cells = overview.elements.size.coerceAtLeast(1)
    val cell = width / cells
    val glyph = (height * if (names) 0.36f else 0.62f).coerceAtMost(18f * k * density)
    val ringStroke = 1.5f * k * density
    val ring = glyph * DOMINANT_RING
    val box = cell - 6f * k * density
    val nameSize = if (names) sharedElementNameSize(overview, ui, 12f * k, box) else 0f
    for (index in overview.elements.indices) {
        val element = overview.elements[index]
        val color = roles.element(element.element)
        val cellLeft = left + index * cell
        // The ring stays inside the cell.
        val glyphCenter = if (names) Offset(cellLeft + ring + ringStroke, top + ring + ringStroke) else
            Offset(cellLeft + glyph * 0.6f, top + height * 0.5f)
        val shown = element.affinity > 0
        drawKkIcon(elementIcon(element.element), glyphCenter, glyph, if (shown) color else Kk.Ink4)
        if (element.dominant) drawCircle(color, ring, glyphCenter, style = kkStroke(ringStroke))
        if (element.keynote) drawPauseKeynoteBrackets(cellLeft, top, cellLeft + box, top + height, 5f * k * density, 1.5f * k * density)
        if (names) {
            val nameTop = glyphCenter.y + ring + ringStroke + 3f * k * density
            val name = fitOverlayText(ui, element.label, KkTextRole.COND, nameSize, box, uppercase = true)
            drawPauseText(PauseTextKind.ELEMENT_NAME, name, cellLeft, nameTop, if (shown) Kk.Bone else Kk.Mute, box)
            drawElementMeter(overview, element, color, cellLeft, nameTop + name.kkBoxHeight + 4f * k * density, box, k)
        } else {
            val meterLeft = cellLeft + glyph * 1.4f
            drawElementMeter(overview, element, color, meterLeft, top + height * 0.5f - 2.5f * k * density, cell - (meterLeft - cellLeft) - 6f * k * density, k)
        }
    }
    return top + height
}

/** The dominant element's ring, as a share of its glyph's size. */
private const val DOMINANT_RING = 0.78f

/** The size (before the text scale) at which every element name fits [box]: the one the longest needs. */
private fun sharedElementNameSize(overview: PauseBuildOverview, ui: CanvasTextMeasurer, size: Float, box: Float): Float {
    var shared = 1f
    for (index in overview.elements.indices) {
        val fitted = fitOverlayText(ui, overview.elements[index].label, KkTextRole.COND, size, box, uppercase = true)
        shared = minOf(shared, fitted.layoutInput.style.fontSize.value / (size * ui.scale))
    }
    return size * shared
}

/** Element meters as rows (compact landscape): glyph then the meter, one element per row. */
internal fun DrawScope.drawPauseElementRows(
    overview: PauseBuildOverview,
    roles: KkRolePalette,
    left: Float,
    top: Float,
    width: Float,
    row: Float,
    k: Float,
    columns: Int = 1,
) {
    val glyph = row * 0.72f
    val rows = (overview.elements.size + columns - 1) / columns
    val columnGap = if (columns > 1) 8f * k * density else 0f
    val columnWidth = (width - columnGap * (columns - 1)) / columns
    for (index in overview.elements.indices) {
        val element = overview.elements[index]
        val color = roles.element(element.element)
        // Column-major, so element order reads down the first column, then the next.
        val columnLeft = left + (index / rows) * (columnWidth + columnGap)
        val middle = top + (index % rows) * row + row * 0.5f
        val center = Offset(columnLeft + glyph * 0.6f, middle)
        drawKkIcon(elementIcon(element.element), center, glyph, if (element.affinity > 0) color else Kk.Ink4)
        if (element.dominant) drawCircle(color, glyph * DOMINANT_RING, center, style = kkStroke(1.2f * k * density))
        if (element.keynote) drawPauseKeynoteBrackets(columnLeft, middle - row * 0.5f, columnLeft + columnWidth, middle + row * 0.5f,
            4f * k * density, 1.2f * k * density)
        drawElementMeter(overview, element, color, columnLeft + glyph * 1.6f, middle - 2.5f * k * density, columnWidth - glyph * 1.6f, k)
    }
}

/** Columns of the landscape compact pause's element meters. */
internal const val COMPACT_ELEMENT_COLUMNS = 2

/** One pip per affinity point (eight shown), notched at each affinity tier; reached notches in the element's hue. */
private fun DrawScope.drawElementMeter(overview: PauseBuildOverview, element: PauseElementEntry, color: Color, left: Float, top: Float, width: Float, k: Float) {
    val count = AFFINITY_PIPS
    val gap = 2f * k * density
    val pip = ((width - gap * (count - 1)) / count).coerceIn(2f, 8f * k * density)
    val height = 5f * k * density
    drawKkPips(Offset(left, top), count, element.affinity.coerceAtMost(count), color, sizeDp = height / density, widthDp = pip / density,
        gapDp = gap / density, emptyColor = Kk.Ink4)
    for (threshold in overview.affinityThresholds) {
        if (threshold > count) continue
        val x = left + threshold * (pip + gap) - gap * 0.5f
        drawRect(if (element.affinity >= threshold) color else Kk.Mute, Offset(x - 0.6f * k * density, top - 2f * k * density),
            Size(1.2f * k * density, height + 4f * k * density))
    }
}

private const val AFFINITY_PIPS = 8

/**
 * Reaction chips flowing from [top] to [bottom]: each a plate with both elements' glyphs and the
 * reaction's name, or "?" over a hatch while it is still unknown. Chips that find no room leave a
 * "+N" mark in their place. Without a reachable pair, a muted line says so.
 */
internal fun DrawScope.drawPauseReactions(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
) {
    val roles = ui.roles
    val reactions = overview.reactions
    if (reactions.isEmpty()) {
        val text = fitOverlayText(ui, overview.noReactions, KkTextRole.MONO, size * 0.8f, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + chip * 0.5f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return
    }
    val gap = 6f * k * density
    val glyph = chip * 0.5f
    val lead = glyph * 2f + 10f * k * density
    val maxText = width - lead - 10f * k * density
    val count = minOf(reactions.size, ReactionChips.CAPACITY)
    // Place every chip first; when some find no room, the last shown ones give way to the "+N" mark.
    var shown = ReactionChips.place(reactions, count, ui, left, top, width, bottom, chip, gap, lead, maxText, size, k, density)
    var more: TextLayoutResult? = null
    var moreX = 0f
    var moreY = 0f
    while (shown < count) {
        val mark = fitOverlayText(ui, moreLabel(count - shown), KkTextRole.COND, size, maxText)
        val markWidth = mark.size.width + 12f * k * density
        val afterX = if (shown == 0) left else ReactionChips.x(shown - 1) + ReactionChips.width(shown - 1) + gap
        val afterY = if (shown == 0) top else ReactionChips.y(shown - 1)
        if (afterX + markWidth <= left + width + 0.5f && afterY + chip <= bottom + 0.5f) {
            more = mark
            moreX = afterX
            moreY = afterY
            break
        }
        if (afterY + chip + gap + chip <= bottom + 0.5f) {
            more = mark
            moreX = left
            moreY = afterY + chip + gap
            break
        }
        if (shown == 0) break
        shown--
    }
    for (index in 0 until shown) {
        val reaction = reactions[index]
        val x = ReactionChips.x(index)
        val y = ReactionChips.y(index)
        val chipWidth = ReactionChips.width(index)
        val first = roles.element(reaction.id.first.ordinal)
        val second = roles.element(reaction.id.second.ordinal)
        val plate = PauseShapes.slab(index, x, y, x + chipWidth, y + chip, 6f * k * density)
        drawPath(plate, Kk.Ink2)
        if (!reaction.discovered) drawKkHatch(plate)
        // Both elements' glyphs, and a two-tone edge along the plate's foot.
        drawKkIcon(elementIcon(reaction.id.first.ordinal), Offset(x + 6f * k * density + glyph * 0.5f, y + chip * 0.5f), glyph, first)
        drawKkIcon(elementIcon(reaction.id.second.ordinal), Offset(x + 8f * k * density + glyph * 1.5f, y + chip * 0.5f), glyph, second)
        drawRect(first, Offset(x + 6f * k * density, y + chip - 2f * k * density), Size(chipWidth * 0.5f - 6f * k * density, 2f * k * density))
        drawRect(second, Offset(x + chipWidth * 0.5f, y + chip - 2f * k * density), Size(chipWidth * 0.5f - 10f * k * density, 2f * k * density))
        drawPauseText(PauseTextKind.REACTION, ReactionChips.name(index), x + lead, y + chip * 0.5f,
            if (reaction.discovered) Kk.Bone else Kk.Mute, maxText, valign = KkVAlign.CENTER)
    }
    if (more != null) drawPauseText(PauseTextKind.MORE, more, moreX + 6f * k * density, moreY + chip * 0.5f, Kk.Bone2, maxText, valign = KkVAlign.CENTER)
}

/**
 * The weapon's synergy chips flowing from [top] while they fit above [bottom]: a plate with the chip's text
 * and an accent edge (the player's color, the infused element's hue, the harmonizing Relic's aspect).
 * Without any, a muted line says so. Returns the bottom of what it drew.
 */
internal fun DrawScope.drawPauseSynergies(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
): Float {
    val roles = ui.roles
    val chips = overview.synergies
    if (chips.isEmpty()) {
        val text = fitOverlayText(ui, overview.noSynergy, KkTextRole.MONO, size * 0.8f, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + chip * 0.5f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return top + chip
    }
    val gap = 6f * k * density
    val pad = 10f * k * density
    val maxText = width - pad * 2f
    var x = left
    var y = top
    var drawnBottom = top
    for (index in chips.indices) {
        val entry = chips[index]
        val text = fitOverlayText(ui, entry.text, KkTextRole.COND, size, maxText, uppercase = true)
        val chipWidth = text.size.width + pad * 2f
        if (x > left && x + chipWidth > left + width + 0.5f) {
            x = left
            y += chip + gap
        }
        if (y + chip > bottom + 0.5f) break
        val accent = when (entry.accent) {
            PauseSynergyAccent.YOU -> roles.you
            PauseSynergyAccent.ELEMENT -> overview.synergyElement?.let { roles.element(it.ordinal) } ?: roles.you
            PauseSynergyAccent.RELIC -> overview.synergyRelic?.let { relicHue(it, roles) } ?: roles.you
        }
        val plate = PauseShapes.synergySlab(index, x, y, x + chipWidth, y + chip, 6f * k * density)
        drawPath(plate, Kk.Ink2)
        drawRect(accent, Offset(x + 6f * k * density, y + chip - 2f * k * density), Size(chipWidth - 12f * k * density, 2f * k * density))
        drawPauseText(PauseTextKind.SYNERGY, text, x + pad, y + chip * 0.5f, Kk.Bone, maxText, valign = KkVAlign.CENTER)
        drawnBottom = y + chip
        x += chipWidth + gap
    }
    return drawnBottom
}

/** The bottom of the reaction chips [drawPauseReactions] would draw from [top]: all of [bottom] when some find no room. */
internal fun DrawScope.pauseReactionsBottom(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
): Float {
    val reactions = overview.reactions
    if (reactions.isEmpty()) return top + chip
    val gap = 6f * k * density
    val lead = chip * 0.5f * 2f + 10f * k * density
    val maxText = width - lead - 10f * k * density
    val count = minOf(reactions.size, ReactionChips.CAPACITY)
    val shown = ReactionChips.place(reactions, count, ui, left, top, width, bottom, chip, gap, lead, maxText, size, k, density)
    return if (shown < count || shown == 0) bottom else ReactionChips.y(shown - 1) + chip
}

/** Where the reaction chips of the last drawn list sit (draw-thread scratch, no allocation). */
private object ReactionChips {
    val CAPACITY = ReactionId.entries.size
    private val xs = FloatArray(CAPACITY)
    private val ys = FloatArray(CAPACITY)
    private val widths = FloatArray(CAPACITY)
    private val names = arrayOfNulls<TextLayoutResult>(CAPACITY)

    fun x(index: Int) = xs[index]
    fun y(index: Int) = ys[index]
    fun width(index: Int) = widths[index]
    fun name(index: Int): TextLayoutResult = requireNotNull(names[index])

    /** Flows [count] chips in rows from [left], [top]; returns how many fit above [bottom]. */
    fun place(
        reactions: List<PauseReactionEntry>,
        count: Int,
        ui: CanvasTextMeasurer,
        left: Float,
        top: Float,
        width: Float,
        bottom: Float,
        chip: Float,
        gap: Float,
        lead: Float,
        maxText: Float,
        size: Float,
        k: Float,
        density: Float,
    ): Int {
        var x = left
        var y = top
        for (index in 0 until count) {
            val name = fitOverlayText(ui, reactions[index].name, KkTextRole.COND, size, maxText, uppercase = true)
            val chipWidth = lead + name.size.width + 10f * k * density
            if (x > left && x + chipWidth > left + width + 0.5f) {
                x = left
                y += chip + gap
            }
            if (y + chip > bottom + 0.5f) return index
            xs[index] = x
            ys[index] = y
            widths[index] = chipWidth
            names[index] = name
            x += chipWidth + gap
        }
        return count
    }
}

/**
 * The owned items as an icon grid from [top] within [height]: the largest pitch up to [pitch]
 * that fits them all (never below 60 % of it), stacks as each icon's arc, a threat hatch over items
 * the bound relics disable and a gold rim on Reborn copies. An active item draws as its Z/X/C slot in
 * small (a sheared plate with its effect's mark and rank pips, as its card's plate shows them) with
 * its slot's key on a bone key cap at the cell's top-right corner (its card's Active chip). Items
 * past the grid leave a "+N" mark.
 */
internal fun DrawScope.drawPauseItems(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    pitch: Float,
    k: Float,
) {
    val roles = ui.roles
    val items = overview.items
    if (items.isEmpty()) {
        val text = fitOverlayText(ui, overview.noItems, KkTextRole.MONO, 11f * k, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + pitch * 0.4f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return
    }
    var cell = pitch
    while (cell > pitch * 0.6f) {
        val perRow = floor(width / cell).toInt().coerceAtLeast(1)
        if (ceil(items.size / perRow.toFloat()) * cell <= height) break
        cell -= 2f * k * density
    }
    val perRow = floor(width / cell).toInt().coerceAtLeast(1)
    val rows = floor(height / cell).toInt().coerceAtLeast(1)
    // Items past the grid leave a "+N" mark in its last cell.
    val shown = if (items.size > perRow * rows) perRow * rows - 1 else items.size
    if (shown < items.size) {
        val center = Offset(left + (shown % perRow) * cell + cell * 0.5f, top + (shown / perRow) * cell + cell * 0.5f)
        val more = fitOverlayText(ui, moreLabel(items.size - shown), KkTextRole.COND, 15f * k, cell)
        drawPauseText(PauseTextKind.MORE, more, center.x, center.y, Kk.Bone2, cell, KkAlign.CENTER, KkVAlign.CENTER)
    }
    for (index in 0 until shown) {
        val item = items[index]
        val center = Offset(left + (index % perRow) * cell + cell * 0.5f, top + (index / perRow) * cell + cell * 0.5f)
        val radius = cell * 0.36f
        val hue = item.item.elements.firstOrNull()?.let { roles.element(it.ordinal) } ?: roles.kinetic
        val effect = item.active
        if (effect != null) {
            // Its Z/X/C slot in small: a sheared Ink2 plate edged in its hue, the effect's mark and rank pips on it.
            val half = cell * ACTIVE_PLATE_SHARE * 0.5f
            val plate = PauseShapes.activePlate(index, center.x - half, center.y - half, center.x + half, center.y + half, half * 0.34f)
            drawPath(plate, Kk.Ink2)
            drawPath(plate, hue, alpha = 0.6f, style = kkStroke(1.2f * k * density))
            drawActiveItemMark(effect, center, half * 2f, hue, item.stacks, item.item.maxStacks, plate = Kk.Ink2)
        } else {
            drawItemImage(item.item, center, radius, hue, item.stacks)
        }
        if (item.reborn) drawCircle(Kk.Gold, radius * 1.18f, center, style = kkStroke(1.5f * k * density))
        if (item.suppressed) {
            drawCircle(kkFillBrush(KkFillKind.HATCH, roles.threat), radius * 1.12f, center)
            drawCircle(roles.threat, radius * 1.12f, center, alpha = 0.8f, style = kkStroke(1.2f * k * density))
        }
        item.slotKey?.let { key -> drawPauseKeyCap(index, key, ui, center.x + cell * 0.5f, center.y - cell * 0.5f, cell, k) }
    }
}

/**
 * The key of an active item's slot on a bone key cap (ink mono, at least square) in the top-right corner of its
 * [cell] whose top-right point is ([right], [top]). The cap keeps to a third of the cell like the grid's icons (the
 * label follows the text size up to the cap's height); a long key name shrinks to the cell's width.
 */
private fun DrawScope.drawPauseKeyCap(index: Int, key: String, ui: CanvasTextMeasurer, right: Float, top: Float, cell: Float, k: Float) {
    val inset = cell * 0.04f
    val pad = cell * 0.06f
    val box = cell - inset * 2f - pad * 2f
    val height = cell * KEY_CAP_SHARE
    val size = min(KEY_CAP_SP * k, height / density * 0.62f)
    val label = fitOverlayText(ui, key, KkTextRole.MONO, size, box, uppercase = true, minScale = 0.3f)
    val width = max(label.size.width + pad * 2f, height)
    val left = right - inset - width
    val capTop = top + inset
    drawPath(PauseShapes.keyCap(index, left, capTop, left + width, capTop + height, height * 0.22f), Kk.Bone)
    drawPauseText(PauseTextKind.KEY, label, left + width * 0.5f, capTop + height * 0.5f, Kk.Ink, box, KkAlign.CENTER, KkVAlign.CENTER)
}

/** An active's key cap label in the pause grid (board sp, before the text factor). */
private const val KEY_CAP_SP = 10f

/** An active's key cap is this share of its grid cell tall. */
private const val KEY_CAP_SHARE = 0.32f

/** An active's slot plate spans this share of its grid cell. */
private const val ACTIVE_PLATE_SHARE = 0.8f
