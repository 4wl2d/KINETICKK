// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.floor

/**
 * Pause board, two columns under the header: relics (brackets, runes, multipliers), their cost
 * list, the reachable reactions and four derived stats on the left; resonance ladders, element
 * meters and the owned items on the right; the six run panels across the bottom. Sections flow
 * down their column at the measured height of their text, so larger text sizes push, never
 * overlap; the cost list and the reactions share what is left. [ui] measures text that follows the
 * text-size setting, [display] the level badge.
 */
internal fun DrawScope.drawRegularBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
) {
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(4f), 40f * k, 16f * unit, k, 12f, 20f, time, weaponName = true)
    val column = 352f
    val right = 376f
    val rightWidth = width - right
    val gap = 12f * unit
    val runTop = y(640f)
    val columnBottom = runTop - gap
    // Left column: the relic matrix, its costs (never hidden), then the reactions fill the rest.
    var top = drawPauseLabel(overview.relicsLabel, ui, 15f * k, x(0f), y(58f), column * unit) + 4f * unit
    top = drawPauseRelics(overview, ui, x(0f), top + 4f * unit, column * unit, 56f * unit, 88f * unit, 22f * unit, k, names = true, time = time) + gap
    top = drawPauseLabel(overview.costLabel, ui, 15f * k, x(0f), top, column * unit) + 6f * unit
    // The costs take what they need of the column, keeping two rows of reaction chips under them and, in a run with
    // conditions, a row of their chips (after their label) at the column's foot.
    val reactionsHeight = pauseLabelHeight(overview.reactionsLabel, ui, 15f * k, column * unit) + 6f * unit + 26f * unit * 2f + 6f * unit
    val conditionChip = 24f * unit * maxOf(1f, ui.scale * 0.85f)
    val conditionsRow = pauseConditionsRow(overview, conditionChip)
    val leftBottom = columnBottom - if (conditionsRow > 0f) gap + conditionsRow else 0f
    val costBottom = drawPauseCosts(overview, ui, x(0f), top, column * unit, leftBottom - gap - reactionsHeight, 12f * k, k)
    top = drawPauseLabel(overview.reactionsLabel, ui, 15f * k, x(0f), costBottom + gap, column * unit) + 6f * unit
    val reactionsTop = top
    // Right column: resonance and element meters, the four derived stats at the foot, the items between.
    top = drawPauseLabel(overview.resonanceLabel, ui, 15f * k, x(right), y(58f), rightWidth * unit) + 6f * unit
    top = drawPauseTags(overview, ui, x(right), top, rightWidth * unit, 24f * unit * maxOf(1f, ui.scale * 0.85f), 1, 15f * k, k) + gap
    top = drawPauseLabel(overview.elementsLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
    top = drawPauseElements(overview, ui, x(right), top, rightWidth * unit, 58f * unit * maxOf(1f, ui.scale * 0.85f), k, names = true) + gap
    val statRow = 27f * unit * maxOf(1f, ui.scale * 0.9f)
    val statsTop = columnBottom - pauseLabelHeight(overview.statsLabel, ui, 15f * k, rightWidth * unit) - overview.stats.size * statRow
    // The weapon's synergies: up to two rows of chips in the right column where they leave the items a label
    // and a row of icons, else one row under the reactions where those leave room (neither ever gives way).
    val synergyChip = 24f * unit * maxOf(1f, ui.scale * 0.85f)
    val synergyLabel = pauseLabelHeight(overview.synergyLabel, ui, 15f * k, rightWidth * unit) + 6f * unit
    val itemsNeed = pauseLabelHeight(overview.itemsLabel, ui, 15f * k, rightWidth * unit) + 6f * unit + 44f * unit * 0.6f
    val synergyRoom = statsTop - gap - itemsNeed - gap - top - synergyLabel
    val synergyRows = floor((synergyRoom + 6f * unit) / (synergyChip + 6f * unit) + 0.001f).toInt().coerceIn(0, 2)
    var reactionsBottom = leftBottom
    // Where the left column's last section ends: the Conditions take the room under it.
    var leftEnd = -1f
    if (synergyRows > 0) {
        top = drawPauseLabel(overview.synergyLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
        val bottom = top + synergyRows * synergyChip + (synergyRows - 1) * 6f * unit
        top = drawPauseSynergies(overview, ui, x(right), top, rightWidth * unit, bottom, synergyChip, 13f * k, k) + gap
    } else {
        val needed = pauseReactionsBottom(overview, ui, x(0f), reactionsTop, column * unit, leftBottom, 26f * unit, 13f * k, k)
        val leftChip = 24f * unit
        val leftLabel = pauseLabelHeight(overview.synergyLabel, ui, 15f * k, column * unit) + 6f * unit
        // The weapon's synergies come before the conditions: they may take the conditions' row (which then shows where
        // room is left, if any).
        if (needed + gap + leftLabel + leftChip <= columnBottom + 0.5f) {
            reactionsBottom = needed
            val chipsTop = drawPauseLabel(overview.synergyLabel, ui, 15f * k, x(0f), needed + gap, column * unit) + 6f * unit
            leftEnd = drawPauseSynergies(overview, ui, x(0f), chipsTop, column * unit, chipsTop + leftChip, leftChip, 13f * k, k)
        }
    }
    if (leftEnd < 0f) leftEnd = pauseReactionsBottom(overview, ui, x(0f), reactionsTop, column * unit, reactionsBottom, 26f * unit, 13f * k, k)
    drawPauseReactions(overview, ui, x(0f), reactionsTop, column * unit, reactionsBottom, 26f * unit, 13f * k, k)
    drawPauseConditions(overview, ui, x(0f), leftEnd + gap, column * unit, columnBottom, 15f * k, conditionChip, 13f * k, k, detailed = true)
    top = drawPauseLabel(overview.itemsLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
    drawPauseItems(overview, ui, x(right), top, rightWidth * unit, statsTop - gap - top, 44f * unit, k)
    val rowsTop = drawPauseLabel(overview.statsLabel, ui, 15f * k, x(right), statsTop, rightWidth * unit)
    drawStatRows(overview.stats, ui, display, area, unit, right, (rowsTop - area.top) / unit, statRow / unit, 1, rightWidth, 10f, 20f * k, 15f * k)
    // Run statistics.
    val panelsTop = drawPauseLabel(overview.runLabel, ui, 15f * k, x(0f), runTop, width * unit) + 6f * unit
    drawRunPanels(overview.run, ui, x(0f), panelsTop, width * unit, 8f * unit, minOf(58f * unit * maxOf(1f, ui.scale * 0.8f), area.bottom - panelsTop),
        6, k, 9f, 20f)
}

/** A section label (label role, uppercase) fitted to [box]; returns its bottom. */
private fun DrawScope.drawPauseLabel(text: String, measurer: CanvasTextMeasurer, size: Float, x: Float, y: Float, box: Float): Float {
    val layout = fitOverlayText(measurer, text, KkTextRole.LABEL, size, box, uppercase = true)
    drawPauseText(PauseTextKind.LABEL, layout, x, y, Kk.Mute, box)
    return y + layout.kkBoxHeight
}

/** Height of a section label as [drawPauseLabel] draws it. */
private fun pauseLabelHeight(text: String, measurer: CanvasTextMeasurer, size: Float, box: Float): Float =
    fitOverlayText(measurer, text, KkTextRole.LABEL, size, box, uppercase = true).kkBoxHeight

/**
 * Mobile-Pause boards, one flow under the header: the relic matrix (with the reactions beside it
 * on landscape), the cost list, resonance in two columns with the element meters, then (portrait)
 * the reactions and (landscape) the four derived stats in two columns. A section that no longer
 * fits the area at a large text size is left out rather than cut.
 */
internal fun DrawScope.drawCompactBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
    portrait: Boolean,
) {
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    val gap = 8f * unit
    val grow = maxOf(1f, ui.scale * 0.85f)
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(2f), 28f * k, 10f * unit, k, 9f, 16f, time, weaponName = false)
    var top = y(34f)
    // Bound relics' costs only: a replaced relic's lingering cost shows in the regular view.
    val bound = overview.relics.count { it != null }
    val tagRow = 15f * unit * grow
    if (!portrait) {
        val relicsBottom = drawPauseRelics(overview, ui, x(0f), top, 196f * unit, 30f * unit, 46f * unit, 9f * unit, k, names = false, time = time)
        val chipsTop = drawPauseLabel(overview.reactionsLabel, ui, 11f * k, x(206f), top, (width - 206f) * unit) + 4f * unit
        val besideBottom = maxOf(relicsBottom, chipsTop + 50f * unit)
        // One row of synergy chips under the reactions, beside the relics, where the reactions leave room (they never give way).
        val reactionsBottom = pauseReactionsBottom(overview, ui, x(206f), chipsTop, (width - 206f) * unit, besideBottom, 22f * unit, 11f * k, k)
        val synergyChip = 18f * unit * grow
        val synergyTop = reactionsBottom + gap
        val synergyFits = synergyTop + pauseLabelHeight(overview.synergyLabel, ui, 11f * k, (width - 206f) * unit) + 4f * unit + synergyChip <=
            besideBottom + 0.5f
        drawPauseReactions(overview, ui, x(206f), chipsTop, (width - 206f) * unit, if (synergyFits) reactionsBottom else besideBottom, 22f * unit,
            11f * k, k)
        if (synergyFits) {
            val synergyChipsTop = drawPauseLabel(overview.synergyLabel, ui, 11f * k, x(206f), synergyTop, (width - 206f) * unit) + 4f * unit
            drawPauseSynergies(overview, ui, x(206f), synergyChipsTop, (width - 206f) * unit, synergyChipsTop + synergyChip, synergyChip, 10.5f * k, k)
        }
        top = besideBottom + gap
        val statRow = 22f * unit * grow
        val elementRow = 12f * unit * grow
        // The seven element meters sit in two columns, so they stand no taller than the three tag rows.
        val elementRows = (STATUS_ELEMENT_COUNT + COMPACT_ELEMENT_COLUMNS - 1) / COMPACT_ELEMENT_COLUMNS
        val resonanceHeight = pauseLabelHeight(overview.resonanceLabel, ui, 11f * k, 236f * unit) + 4f * unit +
            maxOf(3f * tagRow, elementRows * elementRow)
        // Costs come before the derived stats: at a large text size the stats give way to keep two
        // readable lines per cost.
        val costNeed = bound * (2f * 9.5f * k * ui.scale * density * COST_LINE_HEIGHT + 4f * unit)
        val showStats = top + costNeed + gap + resonanceHeight + gap + 2f * statRow <= area.bottom
        val statsTop = if (showStats) area.bottom - 2f * statRow else area.bottom + gap
        // Without room beside the relics, the synergy chips keep one row under the resonance up to the default text
        // size: the costs shrink to leave it (the stats keep their place).
        val synergyRow = if (!synergyFits && ui.scale <= 1.001f) synergyChip + gap else 0f
        top = drawPauseCosts(overview, ui, x(0f), top, width * unit, statsTop - gap - resonanceHeight - gap - synergyRow, 9.5f * k, k,
            maxEntries = bound) + gap
        val rowsTop = drawPauseLabel(overview.resonanceLabel, ui, 11f * k, x(0f), top, 236f * unit) + 4f * unit
        val tagsBottom = drawPauseTags(overview, ui, x(0f), rowsTop, 236f * unit, tagRow, 2, 10.5f * k, k, maxRows = 3)
        val meterTop = drawPauseLabel(overview.elementsLabel, ui, 11f * k, x(248f), top, (width - 248f) * unit) + 4f * unit
        drawPauseElementRows(overview, ui.roles, x(248f), meterTop, (width - 248f) * unit, elementRow, k, columns = COMPACT_ELEMENT_COLUMNS)
        // Without room beside the relics, the synergy chips take one row of the space the costs leave above the
        // stats, if any: the section label, then the chips that fit after it.
        val resonanceBottom = maxOf(tagsBottom, meterTop + elementRows * elementRow)
        val synergyLimit = if (showStats) statsTop - gap else area.bottom
        val lowerTop = resonanceBottom + gap
        var conditionsTop = lowerTop
        if (!synergyFits && lowerTop + synergyChip <= synergyLimit + 0.5f) {
            drawPauseSynergyRow(overview, ui, x(0f), lowerTop, width * unit, synergyChip, unit, k)
            conditionsTop = lowerTop + synergyChip + gap
        }
        // The run's Conditions as one row of chips where the rest leaves it (they never push a section out).
        drawPauseConditions(overview, ui, x(0f), conditionsTop, width * unit, synergyLimit, 11f * k, synergyChip, 10.5f * k, k, detailed = false,
            inlineLabel = true)
        if (showStats) {
            drawStatRows(overview.stats, ui, display, area, unit, 0f, (statsTop - area.top) / unit, statRow / unit, 2, (width - 20f) / 2f, 8f,
                15f * k, 12f * k)
        }
        return
    }
    top = drawPauseRelics(overview, ui, x(0f), top, width * unit, 36f * unit, 56f * unit, 9f * unit, k, names = false, time = time) + gap
    val synergyChip = 18f * unit * grow
    // Up to the default text size the synergy section keeps one row under the reactions (its label beside the chips
    // when it cannot stand above them): the costs and the tag rows give way to it. At a larger size it shows only
    // where the rest leaves room.
    val reserveSynergy = ui.scale <= 1.001f
    val synergyRow = if (reserveSynergy) gap + synergyChip else 0f
    // What follows the costs, without the resonance rows: the costs and up to three rows of tags share the rest.
    val rest = gap + pauseLabelHeight(overview.resonanceLabel, ui, 11f * k, width * unit) + 4f * unit + gap +
        pauseLabelHeight(overview.elementsLabel, ui, 11f * k, width * unit) + 4f * unit + 20f * unit * grow + gap +
        pauseLabelHeight(overview.reactionsLabel, ui, 11f * k, width * unit) + 4f * unit + 20f * unit + synergyRow
    // A larger text size never leaves the costs smaller than the default does: below that, the tags give up rows.
    // Up to the default size the costs keep their full size while the tags give up rows, then shrink rather than
    // take the synergy row (they never count an entry away).
    val costBottom = drawPauseCosts(overview, ui, x(0f), top, width * unit,
        area.bottom - rest - (if (reserveSynergy) 1f else 3f) * tagRow, 9.5f * k, k, maxEntries = bound,
        minScale = if (reserveSynergy) MIN_COST_SCALE else (1f / ui.scale).coerceIn(MIN_COST_SCALE, 1f), limit = area.bottom - rest - tagRow)
    val tagRows = floor((area.bottom - rest - costBottom) / tagRow + 0.001f).toInt().coerceIn(1, 3)
    top = drawPauseLabel(overview.resonanceLabel, ui, 11f * k, x(0f), costBottom + gap, width * unit) + 4f * unit
    top = drawPauseTags(overview, ui, x(0f), top, width * unit, tagRow, 2, 10.5f * k, k, maxRows = tagRows) + gap
    top = drawPauseLabel(overview.elementsLabel, ui, 11f * k, x(0f), top, width * unit) + 4f * unit
    top = drawPauseElements(overview, ui, x(0f), top, width * unit, 20f * unit * grow, k, names = false) + gap
    top = drawPauseLabel(overview.reactionsLabel, ui, 11f * k, x(0f), top, width * unit) + 4f * unit
    // One row of synergy chips under the reactions where the reactions leave room for it (they never give way).
    val reactionsBottom = pauseReactionsBottom(overview, ui, x(0f), top, width * unit, area.bottom, 20f * unit, 10.5f * k, k)
    val synergyTop = reactionsBottom + gap
    val synergyFits = synergyTop + pauseLabelHeight(overview.synergyLabel, ui, 11f * k, width * unit) + 4f * unit + synergyChip <= area.bottom + 0.5f
    val synergyBeside = !synergyFits && synergyTop + synergyChip <= area.bottom + 0.5f
    drawPauseReactions(overview, ui, x(0f), top, width * unit, if (synergyFits || synergyBeside) reactionsBottom else area.bottom, 20f * unit,
        10.5f * k, k)
    val synergyBottom = if (synergyFits) {
        val chipsTop = drawPauseLabel(overview.synergyLabel, ui, 11f * k, x(0f), synergyTop, width * unit) + 4f * unit
        drawPauseSynergies(overview, ui, x(0f), chipsTop, width * unit, chipsTop + synergyChip, synergyChip, 10.5f * k, k)
    } else if (synergyBeside) {
        drawPauseSynergyRow(overview, ui, x(0f), synergyTop, width * unit, synergyChip, unit, k)
        synergyTop + synergyChip
    } else area.bottom
    // The run's Conditions as one row of chips where the rest leaves it (they never push a section out).
    drawPauseConditions(overview, ui, x(0f), synergyBottom + gap, width * unit, area.bottom, 11f * k, synergyChip, 10.5f * k, k, detailed = false,
        inlineLabel = true)
}
