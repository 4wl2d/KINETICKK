// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.interaction.rewards.icon
import kinetickk.ball.gameplay.interaction.rewards.rewardKeynoteLabel
import kinetickk.ball.gameplay.interaction.rewards.sigil
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.KkTextRole
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.kkBoxHeight

// The pause build view's run Conditions (the omen taken, the director pattern, the Keynote and the world event in
// progress) and the Keynote's corner brackets on its resonance tag and element meter.

/** What a pause Conditions entry is. */
internal enum class PauseConditionKind { OMEN, PATTERN, KEYNOTE, EVENT }

/**
 * One run condition of the pause build view: its [caption] and [name] and, on the board, what it does ([detail]: the
 * omen's upside, the pattern's description) and what it costs ([liability]: the omen's). [icon] marks it (an event has
 * none); a Keynote's [element] is drawn as the element's mark in its hue.
 */
internal class PauseConditionEntry(
    val kind: PauseConditionKind,
    val caption: String,
    val name: String,
    val detail: String? = null,
    val liability: String? = null,
    val icon: KkIcon? = null,
    val element: StatusElement? = null,
)

/** The run's conditions in reading order: the omen taken, the director pattern, the Keynote, the world event in progress. */
internal fun GameplayRenderModel.pauseConditions(language: AppLanguage): List<PauseConditionEntry> = buildList {
    runOmen?.let { id ->
        val omen = content.variety.omens.definitions[id.ordinal]
        add(PauseConditionEntry(PauseConditionKind.OMEN, language.text(RunVarietyText.ConditionOmen), omen.name.localizedContent(language),
            omen.upside.localizedContent(language), omen.liability.localizedContent(language), id.icon(keynoteTag)))
    }
    directorPersonality?.let { id ->
        val personality = content.variety.director.personalities[id.ordinal]
        add(PauseConditionEntry(PauseConditionKind.PATTERN, language.text(RunVarietyText.ConditionPattern),
            personality.name.localizedContent(language), personality.description.localizedContent(language), icon = id.sigil()))
    }
    rewardKeynoteLabel(language)?.let { label ->
        add(PauseConditionEntry(PauseConditionKind.KEYNOTE, language.text(RunVarietyText.ConditionKeynote), label,
            icon = keynoteTag?.overlayIcon(), element = keynoteElement))
    }
    worldEvent?.let { event ->
        add(PauseConditionEntry(PauseConditionKind.EVENT, language.text(RunVarietyText.ConditionEvent), event.kind.displayName.localizedContent(language)))
    }
}

/**
 * The run's Conditions from [top] to [bottom] across [width]. With [detailed] (the board), when every entry fits whole,
 * each takes its rows: caption, mark and name, then what it does and what it costs. Otherwise the entries are chips
 * (mark and name) in as many rows of [chip] height as fit under the section label or, with [inlineLabel] (phones) or
 * without room for the label above them, in one row after it. A section without room for one row of [chip] height is
 * left out, never cut. Returns the bottom drawn ([top] when nothing was).
 */
internal fun DrawScope.drawPauseConditions(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    labelSize: Float,
    chip: Float,
    textSize: Float,
    k: Float,
    detailed: Boolean,
    inlineLabel: Boolean = false,
): Float {
    val entries = overview.conditions
    if (entries.isEmpty() || width <= 0f) return top
    val unit = k * density
    if (!inlineLabel) {
        val label = fitOverlayText(ui, overview.conditionsLabel, KkTextRole.LABEL, labelSize, width, uppercase = true)
        val bodyTop = top + label.kkBoxHeight + 6f * unit
        if (bodyTop + chip <= bottom + 0.5f && conditionChipFits(entries[0], left, left + width, chip, k)) {
            drawPauseText(PauseTextKind.LABEL, label, left, top, Kk.Mute, width)
            if (detailed) {
                val rows = ConditionRows.measure(entries, ui, width, textSize, k, density)
                if (bodyTop + rows.height <= bottom + 0.5f) return drawConditionRows(entries, rows, ui, left, bodyTop, k)
            }
            return drawConditionChips(entries, ui, left, bodyTop, left + width, bottom, chip, textSize, k)
        }
    }
    val labelBox = width * 0.3f
    val label = fitOverlayText(ui, overview.conditionsLabel, KkTextRole.LABEL, labelSize, labelBox, uppercase = true)
    val chipsLeft = left + label.size.width + 10f * unit
    if (top + chip > bottom + 0.5f || !conditionChipFits(entries[0], chipsLeft, left + width, chip, k)) return top
    drawPauseText(PauseTextKind.LABEL, label, left, top + chip * 0.5f, Kk.Mute, labelBox, valign = KkVAlign.CENTER)
    return drawConditionChips(entries, ui, chipsLeft, top, left + width, top + chip, chip, textSize, k)
}

/** The height the Conditions need at the least: one row of [chip] height after their label. */
internal fun pauseConditionsRow(overview: PauseBuildOverview, chip: Float): Float = if (overview.conditions.isEmpty()) 0f else chip

/** The board's laid-out condition rows of the last measure (draw-thread scratch, reused while nothing changes). */
private object ConditionRows {
    private const val CAPACITY = 4
    private val captions = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private val names = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private val details = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private val liabilities = arrayOfNulls<TextLayoutResult>(CAPACITY)
    var captionWidth = 0f
        private set
    var glyph = 0f
        private set
    var rowGap = 0f
        private set
    var height = 0f
        private set
    /** The width the names and text lines had to fit, right of the captions. */
    var textWidth = 0f
        private set

    /** The room [entry]'s name has after its marks. */
    fun nameBox(entry: PauseConditionEntry, unit: Float): Float = textWidth - glyph * conditionMarks(entry) - 6f * unit

    fun caption(index: Int): TextLayoutResult = requireNotNull(captions[index])
    fun name(index: Int): TextLayoutResult = requireNotNull(names[index])
    fun detail(index: Int): TextLayoutResult? = details[index]
    fun liability(index: Int): TextLayoutResult? = liabilities[index]

    /** Lays [entries] out in [width]: captions in one column (at most 30 % of it), then mark, name and text lines. */
    fun measure(entries: List<PauseConditionEntry>, ui: CanvasTextMeasurer, width: Float, size: Float, k: Float, density: Float): ConditionRows {
        val unit = k * density
        val count = minOf(entries.size, CAPACITY)
        var widest = 0f
        for (index in 0 until count) {
            captions[index] = fitOverlayText(ui, entries[index].caption, KkTextRole.MONO, size * 0.8f, width * 0.3f, uppercase = true)
            widest = maxOf(widest, captions[index]!!.size.width.toFloat())
        }
        captionWidth = widest + 10f * unit
        glyph = 16f * unit * maxOf(1f, ui.scale * 0.85f)
        rowGap = 8f * unit
        val text = width - captionWidth
        textWidth = text
        var total = 0f
        for (index in 0 until count) {
            val entry = entries[index]
            val name = fitOverlayText(ui, entry.name, KkTextRole.COND, size, nameBox(entry, unit), uppercase = true)
            names[index] = name
            var rows = maxOf(name.kkBoxHeight, glyph)
            details[index] = entry.detail?.let { fitOverlayText(ui, it, KkTextRole.BODY, size * 0.92f, text, maxLines = 2, minScale = 0.8f) }
            details[index]?.let { rows += 3f * unit + it.size.height }
            liabilities[index] = entry.liability?.let { fitOverlayText(ui, it, KkTextRole.BODY, size * 0.92f, text, maxLines = 2, minScale = 0.8f) }
            liabilities[index]?.let { rows += 2f * unit + it.size.height }
            total += rows + if (index > 0) rowGap else 0f
        }
        for (index in count until CAPACITY) {
            captions[index] = null
            names[index] = null
            details[index] = null
            liabilities[index] = null
        }
        height = total
        return this
    }
}

/** Marks before a condition's name: its icon and a Keynote's element. */
private fun conditionMarks(entry: PauseConditionEntry): Int = (if (entry.icon != null) 1 else 0) + (if (entry.element != null) 1 else 0)

private fun DrawScope.drawConditionRows(entries: List<PauseConditionEntry>, rows: ConditionRows, ui: CanvasTextMeasurer, left: Float, top: Float, k: Float): Float {
    val unit = k * density
    val textLeft = left + rows.captionWidth
    var y = top
    for (index in 0 until minOf(entries.size, 4)) {
        val entry = entries[index]
        val name = rows.name(index)
        val line = maxOf(name.kkBoxHeight, rows.glyph)
        val middle = y + line * 0.5f
        drawPauseText(PauseTextKind.CONDITION_LABEL, rows.caption(index), left, middle, Kk.Mute, rows.captionWidth, valign = KkVAlign.CENTER)
        val marksEnd = drawConditionMarks(entry, ui, textLeft, middle, rows.glyph)
        drawPauseText(PauseTextKind.CONDITION, name, if (marksEnd > textLeft) marksEnd + 6f * unit else textLeft, middle, Kk.Bone,
            rows.nameBox(entry, unit), valign = KkVAlign.CENTER)
        y += line
        rows.detail(index)?.let { detail ->
            y += 3f * unit
            drawPauseText(PauseTextKind.CONDITION_TEXT, detail, textLeft, y, Kk.Bone2, rows.textWidth)
            y += detail.size.height
        }
        rows.liability(index)?.let { liability ->
            y += 2f * unit
            drawPauseText(PauseTextKind.CONDITION_TEXT, liability, textLeft, y, ui.roles.threat, rows.textWidth)
            y += liability.size.height
        }
        y += rows.rowGap
    }
    return y - rows.rowGap
}

/** Draws [entry]'s marks from [left] centred on [middle]; returns where they end. */
private fun DrawScope.drawConditionMarks(entry: PauseConditionEntry, ui: CanvasTextMeasurer, left: Float, middle: Float, glyph: Float): Float {
    var x = left
    entry.icon?.let { icon ->
        drawKkIcon(icon, Offset(x + glyph * 0.5f, middle), glyph, Kk.Bone)
        x += glyph
    }
    entry.element?.let { element ->
        drawKkIcon(element.overlayIcon(), Offset(x + glyph * 0.5f, middle), glyph, element.overlayColor(ui.roles))
        x += glyph
    }
    return x
}

/** Whether [entry]'s chip fits between [left] and [right]: its padding and marks leave its name some room. */
private fun DrawScope.conditionChipFits(entry: PauseConditionEntry, left: Float, right: Float, chip: Float, k: Float): Boolean =
    right - left - 16f * k * density - conditionMarks(entry) * chip * 0.66f >= 24f * k * density

/**
 * The entries as chips flowing in rows from ([left], [top]) up to [right] and [bottom]: an Ink plate with a bone foot,
 * the marks and the name. Chips that find no room are left out (the earlier ones read first).
 */
private fun DrawScope.drawConditionChips(
    entries: List<PauseConditionEntry>,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
): Float {
    val unit = k * density
    val gap = 6f * unit
    val pad = 8f * unit
    val glyph = chip * 0.66f
    var x = left
    var y = top
    var drawn = top
    for (index in 0 until minOf(entries.size, ConditionShapes.CAPACITY)) {
        val entry = entries[index]
        val marks = conditionMarks(entry) * glyph
        val nameBox = (right - left - pad * 2f - marks).coerceAtLeast(1f)
        val name = fitOverlayText(ui, entry.name, KkTextRole.COND, size, nameBox, uppercase = true)
        val chipWidth = pad * 2f + marks + name.size.width
        if (x > left && x + chipWidth > right + 0.5f) {
            x = left
            y += chip + gap
        }
        if (y + chip > bottom + 0.5f || x + chipWidth > right + 0.5f) continue
        drawPath(ConditionShapes.slab(index, x, y, x + chipWidth, y + chip, 6f * unit), Kk.Ink2)
        drawRect(Kk.Bone2, Offset(x + 6f * unit, y + chip - 2f * unit), Size(chipWidth - 12f * unit, 2f * unit))
        val middle = y + chip * 0.5f
        val nameLeft = drawConditionMarks(entry, ui, x + pad, middle, glyph)
        drawPauseText(PauseTextKind.CONDITION, name, nameLeft, middle, Kk.Bone, nameBox, valign = KkVAlign.CENTER)
        drawn = y + chip
        x += chipWidth + gap
    }
    return drawn
}

private object ConditionShapes {
    const val CAPACITY = 4
    private val slabs = KkPathCache(CAPACITY)

    fun slab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        slabs.slab(slot.coerceIn(0, CAPACITY - 1), left, top, right, bottom, cut)
}

/**
 * Bone corner brackets round [rect] (the Keynote's resonance tag and element meter): four short L marks of [arm] length,
 * nothing along the edges between them. Allocation-free.
 */
internal fun DrawScope.drawPauseKeynoteBrackets(rect: Rect, arm: Float, stroke: Float, color: Color = Kk.Bone) {
    val l = rect.left
    val t = rect.top
    val r = rect.right
    val b = rect.bottom
    drawLine(color, Offset(l, t), Offset(l + arm, t), stroke)
    drawLine(color, Offset(l, t), Offset(l, t + arm), stroke)
    drawLine(color, Offset(r, t), Offset(r - arm, t), stroke)
    drawLine(color, Offset(r, t), Offset(r, t + arm), stroke)
    drawLine(color, Offset(l, b), Offset(l + arm, b), stroke)
    drawLine(color, Offset(l, b), Offset(l, b - arm), stroke)
    drawLine(color, Offset(r, b), Offset(r - arm, b), stroke)
    drawLine(color, Offset(r, b), Offset(r, b - arm), stroke)
}
