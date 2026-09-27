// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal enum class SettingsLayoutMode { REGULAR, COMPACT_LANDSCAPE, COMPACT_PORTRAIT }

internal fun settingsLayoutMode(width: Float, height: Float, density: Float): SettingsLayoutMode {
    val scale = density.coerceAtLeast(0.1f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    return when {
        logicalWidth >= 900f && logicalHeight >= 560f -> SettingsLayoutMode.REGULAR
        logicalWidth > logicalHeight -> SettingsLayoutMode.COMPACT_LANDSCAPE
        else -> SettingsLayoutMode.COMPACT_PORTRAIT
    }
}

/** Text families whose rendered widths drive the geometry (see [SettingsTextMetrics]). */
internal enum class SettingsTextKind { TITLE, BUTTON, TAB, SEGMENT, OUTPUT, ROW_LABEL }

/**
 * Rendered text width in px for the layout's base text scale. The feature measures with the same
 * text measurer the renderer draws with; tests supply a deterministic stand-in.
 */
internal fun interface SettingsTextMetrics {
    fun width(text: String, kind: SettingsTextKind): Float
}

/** Mode-dependent type sizes in sp (before the text-size scale). */
internal class SettingsType(val title: Float, val rowLabel: Float, val rowLabelLines: Int)

internal fun settingsType(mode: SettingsLayoutMode): SettingsType = when (mode) {
    SettingsLayoutMode.REGULAR -> SettingsType(title = 44f, rowLabel = 27f, rowLabelLines = 1)
    SettingsLayoutMode.COMPACT_LANDSCAPE -> SettingsType(title = 28f, rowLabel = 21f, rowLabelLines = 2)
    SettingsLayoutMode.COMPACT_PORTRAIT -> SettingsType(title = 32f, rowLabel = 21f, rowLabelLines = 1)
}

/** Share of the text-size setting applied to control text; phones keep the compact sizes readable. */
internal fun settingsModeTextFactor(mode: SettingsLayoutMode): Float =
    if (mode == SettingsLayoutMode.REGULAR) 1f else 0.875f

internal data class SettingsGroupTab(val group: SettingsGroup, val bounds: Rect)

/**
 * One row: the row slab, the label box, the (!) info box and the parts of its control.
 * [options] are the segmented cells, [toggle] the switch, and the slider parts are
 * [decrease], [track], [increase] and the value [output]. [controlScale] (≤ 1) shrinks the
 * control text when its natural width does not fit.
 */
internal data class SettingsRowLayout(
    val row: SettingsRow,
    val bounds: Rect,
    val label: Rect,
    val info: Rect,
    val options: List<Rect> = emptyList(),
    val toggle: Rect? = null,
    val decrease: Rect? = null,
    val track: Rect? = null,
    val increase: Rect? = null,
    val output: Rect? = null,
    val controlScale: Float = 1f,
) {
    /** The slider's full control strip (steppers, track and value), vertically centered on the control. */
    val sliderBounds: Rect?
        get() {
            val decrease = decrease ?: return null
            val output = output ?: return null
            return Rect(decrease.left, decrease.top, output.right, decrease.bottom)
        }
}

/** One geometry for canvas drawing, pointer targets and accessible controls. */
internal data class SettingsLayout(
    val mode: SettingsLayoutMode,
    val bounds: Rect,
    val density: Float,
    val group: SettingsGroup,
    val page: Int,
    val maxPage: Int,
    val back: Rect,
    val rule: Rect?,
    val title: Rect,
    val tabs: List<SettingsGroupTab>,
    val rows: List<SettingsRowLayout>,
    val pages: List<Rect>,
    val preview: Rect?,
    /** Bottom-left x of the sheared ink panel behind the preview; null when the preview is hidden. */
    val panelEdgeX: Float?,
    val rowsLeft: Float,
    val rowsRight: Float,
) {
    val visibleRows: List<SettingsRow> get() = rows.map(SettingsRowLayout::row)

    fun row(row: SettingsRow): SettingsRowLayout? = rows.firstOrNull { it.row == row }

    /** Area of the Compose volume slider and its numeric editor; null when the row is not shown. */
    fun volumeBounds(): Rect? = row(SettingsRow.MASTER_VOLUME)?.sliderBounds
}

/** Minimum touch target in dp (Adaptive board: menus 44 px minimum). */
internal const val SETTINGS_TOUCH_DP = 44f

internal fun Rect.settingsTouch(density: Float, minDp: Float = SETTINGS_TOUCH_DP): Rect {
    val min = minDp * density
    val extraX = max(0f, (min - width) * 0.5f)
    val extraY = max(0f, (min - height) * 0.5f)
    return Rect(left - extraX, top - extraY, right + extraX, bottom + extraY)
}

internal fun settingsLayout(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    group: SettingsGroup,
    page: Int,
    language: AppLanguage,
    metrics: SettingsTextMetrics,
): SettingsLayout {
    val mode = settingsLayoutMode(screenWidth, screenHeight, density)
    val geometry = SettingsGeometry(screenWidth, screenHeight, density, mode, language, metrics)
    return geometry.build(group, page)
}

private class SettingsGeometry(
    val width: Float,
    val height: Float,
    val density: Float,
    val mode: SettingsLayoutMode,
    val language: AppLanguage,
    val metrics: SettingsTextMetrics,
) {
    fun d(value: Float): Float = value * density

    val regular = mode == SettingsLayoutMode.REGULAR
    val portrait = mode == SettingsLayoutMode.COMPACT_PORTRAIT
    val rowGap = if (portrait) d(6f) else d(4f)
    val cellGap = d(3f)
    val cellHeight = if (regular) d(34f) else d(32f)
    val pagerHeight = d(40f)

    fun build(group: SettingsGroup, requestedPage: Int): SettingsLayout {
        val backWidth = metrics.width(language.text(SettingsRedesignText.Back), SettingsTextKind.BUTTON) + d(40f)
        val titleWidth = metrics.width(language.text(ProfileText.SettingsTitle), SettingsTextKind.TITLE)
        val tabWidths = SettingsGroup.entries.map { metrics.width(language.text(it.label), SettingsTextKind.TAB) + d(40f) }

        val back: Rect
        val rule: Rect?
        val title: Rect
        val tabs: List<SettingsGroupTab>
        val rowsLeft: Float
        val rowsRight: Float
        val rowsTop: Float
        val rowsBottom: Float
        var preview: Rect? = null
        var panelEdgeX: Float? = null
        when (mode) {
            SettingsLayoutMode.REGULAR -> {
                val previewShown = width >= d(1180f) && height >= d(600f)
                val previewLeft = width - d(48f) - d(336f)
                rowsLeft = d(44f)
                rowsRight = min(rowsLeft + d(920f), if (previewShown) previewLeft - d(48f) else width - d(48f))
                back = Rect(d(56f), d(23f), d(56f) + backWidth, d(61f))
                rule = Rect(back.right + d(18f), d(28f), back.right + d(19f), d(56f))
                title = Rect(rule.right + d(18f), 0f, min(width - d(48f), rule.right + d(18f) + titleWidth + d(8f)), d(84f))
                var x = d(50f)
                tabs = SettingsGroup.entries.mapIndexed { index, tab ->
                    val bounds = Rect(x, d(104f), x + tabWidths[index], d(144f))
                    x = bounds.right + d(6f)
                    SettingsGroupTab(tab, bounds)
                }
                rowsTop = d(172f)
                rowsBottom = height - d(24f)
                if (previewShown) {
                    preview = Rect(previewLeft, d(108f), previewLeft + d(336f), height - d(40f))
                    panelEdgeX = previewLeft - d(56f)
                }
            }
            SettingsLayoutMode.COMPACT_LANDSCAPE -> {
                val sideWidth = max(max(tabWidths.max(), backWidth), titleWidth)
                    .coerceIn(d(140f), max(d(140f), min(d(220f), width * 0.3f)))
                back = Rect(d(16f), d(12f), d(16f) + backWidth, d(50f))
                rule = null
                title = Rect(d(16f), back.bottom + d(8f), d(16f) + sideWidth, back.bottom + d(42f))
                var y = title.bottom + d(10f)
                tabs = SettingsGroup.entries.map { tab ->
                    val bounds = Rect(d(16f), y, d(16f) + sideWidth, y + d(42f))
                    y = bounds.bottom + d(6f)
                    SettingsGroupTab(tab, bounds)
                }
                rowsLeft = d(16f) + sideWidth + d(30f)
                rowsRight = width - d(16f)
                rowsTop = d(12f)
                rowsBottom = height - d(12f)
            }
            SettingsLayoutMode.COMPACT_PORTRAIT -> {
                back = Rect(d(16f), d(16f), d(16f) + backWidth, d(54f))
                rule = Rect(back.right + d(14f), d(22f), back.right + d(15f), d(48f))
                title = Rect(rule.right + d(14f), d(8f), width - d(16f), d(62f))
                val tabWidth = (width - d(32f) - d(6f)) * 0.5f
                tabs = SettingsGroup.entries.mapIndexed { index, tab ->
                    val left = d(16f) + (tabWidth + d(6f)) * (index % 2)
                    val top = d(70f) + d(50f) * (index / 2)
                    SettingsGroupTab(tab, Rect(left, top, left + tabWidth, top + d(44f)))
                }
                rowsLeft = d(24f)
                rowsRight = width - d(16f)
                rowsTop = d(182f)
                rowsBottom = height - d(16f)
            }
        }

        val heights = group.rows.map(::rowHeight)
        val available = max(0f, rowsBottom - rowsTop)
        val fitsOnePage = heights.sum() + rowGap * (heights.size - 1) <= available
        val pages = if (fitsOnePage) listOf(group.rows.indices) else paginate(heights, available - pagerHeight)
        val maxPage = pages.lastIndex
        val visiblePage = requestedPage.coerceIn(0, maxPage)
        val pageRows = pages[visiblePage]
        // Landscape rows share the height evenly (never below the touch minimum).
        val stretched = if (mode == SettingsLayoutMode.COMPACT_LANDSCAPE) {
            val space = available - if (maxPage > 0) pagerHeight else 0f
            ((space + rowGap) / pageRows.count() - rowGap).coerceIn(d(SETTINGS_TOUCH_DP), d(56f))
        } else null
        var top = rowsTop
        val rows = pageRows.map { index ->
            val row = group.rows[index]
            val rowHeight = stretched ?: heights[index]
            val layout = rowLayout(row, Rect(rowsLeft, top, rowsRight, top + rowHeight))
            top += rowHeight + rowGap
            layout
        }
        val rowsEnd = top - rowGap
        val pagerRects = if (maxPage > 0) {
            val pipWidth = d(28f)
            val pipGap = d(18f)
            val total = pipWidth * pages.size + pipGap * (pages.size - 1)
            val left = (rowsLeft + rowsRight - total) * 0.5f
            val centerY = rowsBottom - pagerHeight * 0.5f
            pages.indices.map { index ->
                val x = left + (pipWidth + pipGap) * index
                Rect(x, centerY - d(4f), x + pipWidth, centerY + d(4f))
            }
        } else emptyList()
        // Without a side column the preview stacks under the rows when a short tab leaves room.
        if (preview == null && maxPage == 0) {
            val previewTop = rowsEnd + d(20f)
            if (rowsBottom - previewTop >= d(150f)) {
                val left = if (portrait) d(16f) else rowsLeft
                val right = if (portrait) width - d(16f) else min(rowsRight, rowsLeft + d(336f))
                preview = Rect(left, previewTop, right, min(rowsBottom, previewTop + d(360f)))
            }
        }
        return SettingsLayout(
            mode = mode,
            bounds = Rect(0f, 0f, width, height),
            density = density,
            group = group,
            page = visiblePage,
            maxPage = maxPage,
            back = back,
            rule = rule,
            title = title,
            tabs = tabs,
            rows = rows,
            pages = pagerRects,
            preview = preview,
            panelEdgeX = panelEdgeX,
            rowsLeft = rowsLeft,
            rowsRight = rowsRight,
        )
    }

    /** Portrait stacks the segmented and slider controls under their label; the rest is one line. */
    fun twoLine(row: SettingsRow): Boolean = portrait && row.control != SettingsControl.TOGGLE

    fun rowHeight(row: SettingsRow): Float = when {
        regular -> d(62f)
        twoLine(row) -> d(88f)
        portrait -> d(52f)
        else -> d(SETTINGS_TOUCH_DP)
    }

    fun paginate(heights: List<Float>, available: Float): List<IntRange> {
        val pages = mutableListOf<IntRange>()
        var start = 0
        var used = 0f
        for (index in heights.indices) {
            val next = if (index == start) heights[index] else used + rowGap + heights[index]
            if (index > start && next > available) {
                pages += start until index
                start = index
                used = heights[index]
            } else {
                used = next
            }
        }
        pages += start until heights.size
        return pages
    }

    fun rowLayout(row: SettingsRow, bounds: Rect): SettingsRowLayout {
        val padLeft = when (mode) {
            SettingsLayoutMode.REGULAR -> d(28f)
            SettingsLayoutMode.COMPACT_LANDSCAPE -> d(16f)
            SettingsLayoutMode.COMPACT_PORTRAIT -> d(12f)
        }
        val padRight = if (regular) d(22f) else d(12f)
        val controlGap = if (regular) d(20f) else d(12f)
        val labelLeft = bounds.left + padLeft
        val lineTop = bounds.top
        val lineBottom = if (twoLine(row)) bounds.top + d(46f) else bounds.bottom
        val lineCenter = (lineTop + lineBottom) * 0.5f
        val infoLeft = bounds.right - padRight - d(26f)
        val info = Rect(infoLeft, lineCenter - d(12f), infoLeft + d(24f), lineCenter + d(12f))
        if (twoLine(row)) {
            val controlCenter = bounds.bottom - d(10f) - cellHeight * 0.5f
            val label = Rect(labelLeft, lineTop, info.left - controlGap, lineBottom)
            return controlLayout(row, bounds, label, info, labelLeft, bounds.right - padRight, controlCenter, labelLeft)
        }
        val controlRight = info.left - controlGap
        // The label keeps its natural width (up to 45 % of the row); the control shrinks first.
        val modeMinimum = when (mode) {
            SettingsLayoutMode.REGULAR -> d(160f)
            SettingsLayoutMode.COMPACT_LANDSCAPE -> d(96f)
            SettingsLayoutMode.COMPACT_PORTRAIT -> d(120f)
        }
        val label = row.label(language)
        val lines = settingsType(mode).rowLabelLines
        val longestWord = label.split(' ').maxOf { metrics.width(it, SettingsTextKind.ROW_LABEL) }
        val labelWidth = max(longestWord, metrics.width(label, SettingsTextKind.ROW_LABEL) / lines * 1.1f) + d(8f)
        val minLabel = max(modeMinimum, min(labelWidth, (controlRight - labelLeft) * 0.45f))
        val controlMinLeft = labelLeft + minLabel + controlGap
        val placeholder = Rect(labelLeft, bounds.top, controlRight, bounds.bottom)
        val layout = controlLayout(row, bounds, placeholder, info, controlMinLeft, controlRight, lineCenter, labelLeft)
        val controlLeft = layout.options.firstOrNull()?.left ?: layout.toggle?.left ?: layout.decrease?.left ?: controlRight
        return layout.copy(label = Rect(labelLeft, bounds.top, max(labelLeft, controlLeft - controlGap), bounds.bottom))
    }

    fun controlLayout(
        row: SettingsRow,
        bounds: Rect,
        label: Rect,
        info: Rect,
        minLeft: Float,
        right: Float,
        centerY: Float,
        stripLeft: Float,
    ): SettingsRowLayout {
        val available = max(0f, right - minLeft)
        return when (row.control) {
            SettingsControl.SEGMENTED -> {
                val count = row.optionCount()
                val natural = (0 until count).map { option ->
                    max(if (regular) d(64f) else d(48f),
                        metrics.width(row.optionLabel(option, language), SettingsTextKind.SEGMENT) + if (regular) d(28f) else d(20f))
                }
                val gaps = cellGap * (count - 1)
                val naturalTotal = natural.sum() + gaps
                // Portrait spreads the cells across the whole strip; elsewhere they hug their text.
                val scale = when {
                    naturalTotal > available -> max(0.1f, (available - gaps) / natural.sum())
                    twoLine(row) -> (available - gaps) / natural.sum()
                    else -> 1f
                }
                val widths = natural.map { it * scale }
                var x = right - (widths.sum() + gaps)
                val cells = widths.map { cellWidth ->
                    val cell = Rect(x, centerY - cellHeight * 0.5f, x + cellWidth, centerY + cellHeight * 0.5f)
                    x = cell.right + cellGap
                    cell
                }
                SettingsRowLayout(row, bounds, label, info, options = cells, controlScale = min(1f, scale))
            }
            SettingsControl.TOGGLE -> SettingsRowLayout(
                row, bounds, label, info,
                toggle = Rect(right - d(62f), centerY - d(14f), right, centerY + d(14f)),
            )
            SettingsControl.SLIDER -> {
                val stepper = if (regular) d(34f) else d(32f)
                val gap = if (regular) d(14f) else d(10f)
                val output = max(if (regular) d(60f) else d(52f), sliderOutputWidth(row) + d(4f))
                val natural = if (regular) d(380f) else if (twoLine(row)) right - stripLeft else d(300f)
                val minimum = stepper * 2f + gap * 3f + output + d(60f)
                val strip = max(minimum, min(natural, available))
                val left = right - strip
                val half = stepper * 0.5f
                val decrease = Rect(left, centerY - half, left + stepper, centerY + half)
                val outputRect = Rect(right - output, centerY - half, right, centerY + half)
                val increase = Rect(outputRect.left - gap - stepper, centerY - half, outputRect.left - gap, centerY + half)
                val track = Rect(decrease.right + gap, centerY - d(13f), increase.left - gap, centerY + d(13f))
                SettingsRowLayout(row, bounds, label, info, decrease = decrease, track = track, increase = increase, output = outputRect)
            }
        }
    }

    /** Widest value a slider row can show, so the value column never shifts while stepping. */
    fun sliderOutputWidth(row: SettingsRow): Float {
        val samples = when (row) {
            SettingsRow.DAMAGE_COLOR_THRESHOLDS -> settingsThresholdOutputSamples(language)
            else -> listOf("100%")
        }
        return samples.maxOf { metrics.width(it, SettingsTextKind.OUTPUT) }
    }
}
