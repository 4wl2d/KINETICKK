// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/** Shapes the paused frame redraws in place: one path per slot, rebuilt only when its geometry moves. */
internal object PauseShapes {
    private val diamonds = KkPathCache(8)
    private val slabs = KkPathCache(16)
    private val synergySlabs = KkPathCache(6)
    private val keyCaps = KkPathCache(ContentBounds.SKILL_SLOTS)
    private val activePlates = KkPathCache(ContentBounds.SKILL_SLOTS)

    fun diamond(slot: Int, cx: Float, cy: Float, r: Float) = diamonds.diamond(slot.coerceIn(0, 7), cx, cy, r)

    fun slab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        slabs.slab(slot.coerceIn(0, 15), left, top, right, bottom, cut)

    fun synergySlab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        synergySlabs.slab(slot.coerceIn(0, 5), left, top, right, bottom, cut)

    /** The slot plate of the active in grid cell [cell] (the actives lead the grid, so one per Z/X/C slot). */
    fun activePlate(cell: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        activePlates.slab(cell.coerceIn(0, ContentBounds.SKILL_SLOTS - 1), left, top, right, bottom, cut)

    /** The key cap of the active in grid cell [cell] (the actives lead the grid, so one per Z/X/C slot). */
    fun keyCap(cell: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        keyCaps.slab(cell.coerceIn(0, ContentBounds.SKILL_SLOTS - 1), left, top, right, bottom, cut)
}

/**
 * "Build" heading at [left] and the tags right-aligned at [right] on one row: when both do not
 * fit, the tags shrink first (to 60 %), then the heading. [weaponName] names the weapon on its chip
 * (the regular board); phones show its icon and level.
 */
internal fun DrawScope.drawBuildHeader(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    left: Float,
    right: Float,
    headingTop: Float,
    tagsTop: Float,
    headingSize: Float,
    gap: Float,
    k: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
    weaponName: Boolean,
) {
    val heading = measureKkText(ui, overview.heading, ui.typography.condStyle(headingSize), uppercase = true).size.width
    val natural = buildTagsWidth(overview, ui, display, k, 1f, badgeLabel, badgeNumber, weaponName)
    val tagScale = ((right - left - heading - gap) / natural).coerceIn(0.6f, 1f)
    val tagsLeft = drawBuildTags(overview, ui, display, right, tagsTop, k, tagScale, badgeLabel, badgeNumber, time, weaponName)
    val box = tagsLeft - left - gap
    drawPauseText(PauseTextKind.HEADING, fitOverlayText(ui, overview.heading, KkTextRole.COND, headingSize, box, uppercase = true),
        left, headingTop, Kk.Bone, box)
}

private fun DrawScope.buildTagsWidth(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    weaponName: Boolean,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val fontSize = 13f * k * tagScale
    return badge.width + 8f * density + 30f * density + kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize).width +
        kkTagSize(ui, overview.form, density, tagHeight, fontSize).width + tagHeight * density * 0.8f +
        weaponChipWidth(overview, ui, tagHeight, fontSize, weaponName)
}

/** Width of the weapon chip: its icon, the name when shown, and its level. */
private fun DrawScope.weaponChipWidth(overview: PauseBuildOverview, ui: CanvasTextMeasurer, tagHeight: Float, fontSize: Float, weaponName: Boolean): Float =
    tagHeight * density * 0.9f + kkTagSize(ui, overview.weaponLevelText, density, tagHeight, fontSize).width +
        if (weaponName) kkTagSize(ui, overview.weaponName, density, tagHeight, fontSize).width - 10f * density else 0f

/**
 * One list of build stats: labels share one fitted size; values right-aligned in their column
 * ([PauseStatFit]). [base] measures at the default text size.
 */
internal fun DrawScope.drawStatRows(
    stats: List<OverlayStat>,
    ui: CanvasTextMeasurer,
    base: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    firstLeft: Float,
    top: Float,
    rowHeight: Float,
    columns: Int,
    column: Float,
    valueGap: Float,
    valueSize: Float,
    labelSize: Float,
) {
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val roles = ui.roles
    val fit = PauseStatFit.of(stats, ui, base, column * unit, valueGap * unit, valueSize, labelSize)
    for (index in stats.indices) {
        val stat = stats[index]
        val left = firstLeft + if (index % columns == 0) 0f else column + 20f
        val bottom = y(top + (index / columns + 1) * rowHeight)
        val value = fit.value(index)
        drawPauseText(PauseTextKind.STAT_VALUE, value, x(left + column), bottom - 6f * unit, if (stat.highlight) roles.you else Kk.Bone,
            fit.valueBox(index), KkAlign.END, KkVAlign.BASELINE)
        drawPauseText(PauseTextKind.STAT_LABEL, fit.label(index), x(left), bottom - 6f * unit, Kk.Bone,
            column * unit - value.size.width - valueGap * unit, valign = KkVAlign.BASELINE)
        drawRect(Kk.Line, Offset(x(left), bottom - unit), Size(column * unit, unit))
    }
}

/**
 * The fitted rows of the last drawn stat list (a paused frame redraws the same list every frame,
 * so they are fitted once). At the default text size and below, values fit 45 % of the column
 * and the labels shrink together to the rest. Above the default, labels and values grow together
 * from the default fit only as far as every row allows, so a larger setting never leaves a label
 * smaller than the default did (long Russian labels in a phone's two columns).
 */
private object PauseStatFit {
    private var stats: List<OverlayStat>? = null
    private var ui: CanvasTextMeasurer? = null
    private var base: CanvasTextMeasurer? = null
    private val key = FloatArray(4) { Float.NaN }
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)
    private var valueBoxes = FloatArray(0)

    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])
    fun valueBox(index: Int): Float = valueBoxes[index]

    fun of(
        stats: List<OverlayStat>,
        ui: CanvasTextMeasurer,
        base: CanvasTextMeasurer,
        column: Float,
        gap: Float,
        valueSize: Float,
        labelSize: Float,
    ): PauseStatFit {
        if (this.stats === stats && this.ui === ui && this.base === base && key[0] == column && key[1] == gap &&
            key[2] == valueSize && key[3] == labelSize
        ) return this
        this.stats = stats
        this.ui = ui
        this.base = base
        key[0] = column
        key[1] = gap
        key[2] = valueSize
        key[3] = labelSize
        fit(stats, if (ui.scale <= 1f) ui else base, column, gap, valueSize, labelSize)
        if (ui.scale > 1f) grow(stats, base, ui.scale, column, gap)
        return this
    }

    private fun fit(stats: List<OverlayStat>, measurer: CanvasTextMeasurer, column: Float, gap: Float, valueSize: Float, labelSize: Float) {
        labels = arrayOfNulls(stats.size)
        values = arrayOfNulls(stats.size)
        valueBoxes = FloatArray(stats.size) { column * 0.45f }
        var shared = 1f
        for (index in stats.indices) {
            val value = fitOverlayText(measurer, stats[index].value, KkTextRole.COND, valueSize, column * 0.45f, tabular = true)
            values[index] = value
            val fitted = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize, column - value.size.width - gap)
            shared = minOf(shared, fitted.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
        }
        for (index in stats.indices) {
            labels[index] = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize * shared,
                column - value(index).size.width - gap)
        }
    }

    /** Grows the default fit (measured by [base], scale 1) by up to [limit], in 5 % steps. */
    private fun grow(stats: List<OverlayStat>, base: CanvasTextMeasurer, limit: Float, column: Float, gap: Float) {
        val labelFont = label(0).layoutInput.style.fontSize.value
        val valueFonts = FloatArray(stats.size) { value(it).layoutInput.style.fontSize.value }
        val grownLabels = arrayOfNulls<TextLayoutResult>(stats.size)
        val grownValues = arrayOfNulls<TextLayoutResult>(stats.size)
        var growth = limit
        while (growth > 1.001f) {
            var fits = true
            for (index in stats.indices) {
                val label = measureKkText(base, stats[index].label, base.typography.kkStyle(KkTextRole.BODY, labelFont * growth))
                val value = measureKkText(base, stats[index].value, base.typography.kkStyle(KkTextRole.COND, valueFonts[index] * growth, tabular = true))
                if (label.size.width + gap + value.size.width > column) {
                    fits = false
                    break
                }
                grownLabels[index] = label
                grownValues[index] = value
            }
            if (fits) {
                labels = grownLabels
                values = grownValues
                for (index in stats.indices) valueBoxes[index] = column - gap - label(index).size.width
                return
            }
            growth = maxOf(1f, growth - 0.05f)
        }
    }
}

/** Level badge (display type), rebirth tag and form tag, right-aligned at [right]; returns their left edge. */
private fun DrawScope.drawBuildTags(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    right: Float,
    top: Float,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
    weaponName: Boolean,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    var x = right - badge.width - 8f * density
    drawKkLevelBadge(display, Offset(x, top), overview.level, overview.levelLabel, time, 1f, badgeLabel, badgeNumber,
        maxLevel = overview.levelCap, apex = overview.apex, apexActive = overview.apexActive)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val tagTop = top + (badge.height - tagHeight * density) * 0.5f
    val fontSize = 13f * k * tagScale
    val rebirth = kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize)
    x -= 10f * density + rebirth.width
    // Gold means Rebirth: the tier chip turns gold from the first Rebirth on.
    if (overview.rebirthLevel > 0) {
        drawKkTag(ui, overview.rebirth, Offset(x, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize, background = Kk.Gold, foreground = Kk.Ink)
    } else {
        drawKkTag(ui, overview.rebirth, Offset(x, tagTop), KkTagVariant.LINE, tagHeight, fontSize)
    }
    recordPauseTag(ui, overview.rebirth, x, tagTop, rebirth)
    val form = kkTagSize(ui, overview.form, density, tagHeight, fontSize)
    val iconSpace = tagHeight * density * 0.8f
    x -= 10f * density + form.width + iconSpace
    val formRect = PauseRects.of(2, x, tagTop, x + form.width + iconSpace, tagTop + tagHeight * density)
    drawKkSlab(formRect, Kk.Bone, 6f * density)
    drawKkIcon(overview.formIcon, Offset(formRect.left + 6f * density + iconSpace * 0.45f, formRect.center.y), iconSpace * 0.62f, Kk.Ink)
    drawKkTag(ui, overview.form, Offset(x + iconSpace, tagTop), KkTagVariant.BONE, tagHeight, fontSize, background = Color.Transparent)
    recordPauseTag(ui, overview.form, x, tagTop, Size(form.width + iconSpace, form.height))
    // The run's weapon: its icon in the you color, its name (regular board) and its level, gold-lit
    // at the last mastery.
    val chip = weaponChipWidth(overview, ui, tagHeight, fontSize, weaponName)
    x -= 10f * density + chip
    val chipRect = PauseRects.of(1, x, tagTop, x + chip, tagTop + tagHeight * density)
    drawKkSlab(chipRect, Kk.Ink3, 6f * density)
    val weaponIcon = tagHeight * density * 0.9f
    drawKkIcon(overview.weaponIcon, Offset(chipRect.left + weaponIcon * 0.6f, chipRect.center.y), weaponIcon * 0.62f, ui.roles.you)
    var textX = x + weaponIcon - 4f * density
    if (weaponName) {
        val name = drawKkTag(ui, overview.weaponName, Offset(textX, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize,
            background = Color.Transparent, foreground = Kk.Bone)
        textX = name.right - 10f * density
    }
    val levelColor = if (overview.masteryTier == WeaponMastery.entries.last()) Kk.RLegend else Kk.Bone2
    drawKkTag(ui, overview.weaponLevelText, Offset(textX, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize, background = Color.Transparent,
        foreground = levelColor)
    recordPauseTag(ui, if (weaponName) overview.weaponName else overview.weaponLevelText, x, tagTop, Size(chip, tagHeight * density))
    return x
}

/** Tags are drawn (and sized to their label) by the foundation; the probe records their plates. */
private fun recordPauseTag(measurer: CanvasTextMeasurer, text: String, x: Float, y: Float, size: Size) {
    val records = PauseTextProbe.records ?: return
    records += PauseTextRecord(PauseTextKind.TAG, measureKkText(measurer, text, measurer.typography.labelStyle(13f)), Rect(Offset(x, y), size),
        Float.POSITIVE_INFINITY)
}

/**
 * Run statistic panels in one row ([PauseRunFit]): labels share one fitted size, as do values;
 * panels are equal unless a value or label needs more room.
 */
internal fun DrawScope.drawRunPanels(
    run: List<PauseRunStat>,
    measurer: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    gap: Float,
    height: Float,
    columns: Int,
    k: Float,
    labelSize: Float,
    valueSize: Float,
) {
    val roles = measurer.roles
    val pad = 10f * k * density
    val fit = PauseRunFit.of(run, measurer, width, gap, pad, minOf(columns, run.size), labelSize * k, valueSize * k)
    var x = left
    for (index in 0 until fit.count) {
        val stat = run[index]
        val cell = fit.width(index)
        drawRect(Kk.Ink2, Offset(x, top), Size(cell, height))
        val room = cell - pad * 2f
        drawPauseText(PauseTextKind.RUN_LABEL, fit.label(index), x + pad, top + pad * 0.9f, Kk.Mute, room)
        drawPauseText(PauseTextKind.RUN_VALUE, fit.value(index), x + pad, top + height - pad * 0.9f, runColor(stat.tone, roles), room,
            valign = KkVAlign.BASELINE)
        x += cell + gap
    }
}

/**
 * The fitted run panels of the last drawn row. Panels share the row equally while every label and
 * value fits its panel; a panel whose text needs more (a long Russian label such as "Лучшая серия"
 * at a large text size) widens and the others give way evenly
 * ([overlayRowWidths]). Labels then share one fitted size and values another, so no panel's text
 * is smaller than its neighbours'.
 */
private object PauseRunFit {
    private var run: List<PauseRunStat>? = null
    private var measurer: CanvasTextMeasurer? = null
    private val key = FloatArray(5) { Float.NaN }
    var count = 0
        private set
    private var widths = FloatArray(0)
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)

    fun width(index: Int): Float = widths[index]
    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])

    fun of(run: List<PauseRunStat>, measurer: CanvasTextMeasurer, width: Float, gap: Float, pad: Float, count: Int, labelSize: Float, valueSize: Float): PauseRunFit {
        if (this.run === run && this.measurer === measurer && this.count == count && key[0] == width && key[1] == gap && key[2] == pad &&
            key[3] == labelSize && key[4] == valueSize
        ) return this
        this.run = run
        this.measurer = measurer
        this.count = count
        key[0] = width
        key[1] = gap
        key[2] = pad
        key[3] = labelSize
        key[4] = valueSize
        val typography = measurer.typography
        val content = FloatArray(count) { index ->
            maxOf(
                measureKkText(measurer, run[index].label, typography.kkStyle(KkTextRole.MONO, labelSize), uppercase = true).size.width,
                measureKkText(measurer, run[index].value, typography.kkStyle(KkTextRole.WIDE, valueSize, tabular = true)).size.width,
            ).toFloat()
        }
        widths = overlayRowWidths(content, FloatArray(count) { pad * 2f }, FloatArray(count) { 1f }, width - gap * (count - 1)).first
        var labelScale = 1f
        var valueScale = 1f
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            val label = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize, room, uppercase = true, minScale = 0.65f)
            labelScale = minOf(labelScale, label.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
            val value = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize, room, tabular = true, minScale = 0.3f)
            valueScale = minOf(valueScale, value.layoutInput.style.fontSize.value / (valueSize * measurer.scale))
        }
        labels = arrayOfNulls(count)
        values = arrayOfNulls(count)
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            labels[index] = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize * labelScale, room, uppercase = true, minScale = 0.65f)
            values[index] = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize * valueScale, room, tabular = true, minScale = 0.3f)
        }
        return this
    }
}

/** A few reusable rects for the paused frame (redrawn every frame with the same geometry). */
private object PauseRects {
    private val rects = arrayOfNulls<Rect>(3)

    fun of(slot: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val cached = rects[slot]
        if (cached != null && cached.left == left && cached.top == top && cached.right == right && cached.bottom == bottom) return cached
        return Rect(left, top, right, bottom).also { rects[slot] = it }
    }
}
