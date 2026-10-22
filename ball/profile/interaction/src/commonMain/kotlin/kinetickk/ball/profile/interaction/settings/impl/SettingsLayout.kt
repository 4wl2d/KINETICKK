// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.kkTooltipRect
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
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
internal enum class SettingsTextKind { TITLE, BUTTON, TAB, SEGMENT, OUTPUT, ROW_LABEL, READOUT }

/**
 * Rendered text sizes in px for the layout's base text scale. The feature measures with the same
 * text measurer the renderer draws with; tests supply a deterministic stand-in.
 */
internal interface SettingsTextMetrics {
    fun width(text: String, kind: SettingsTextKind): Float

    /** Height of one line box of a row label (labels wrap to a second line in compact landscape). */
    fun rowLabelLineHeight(): Float
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
    /** The (!) box; zero-sized (no explanation of its own) on key binding rows. */
    val info: Rect,
    val options: List<Rect> = emptyList(),
    val toggle: Rect? = null,
    val decrease: Rect? = null,
    val track: Rect? = null,
    val increase: Rect? = null,
    val output: Rect? = null,
    val controlScale: Float = 1f,
    /** Color vision only, where no side preview shows the palette: the five role swatches under the cells. */
    val swatches: List<Rect> = emptyList(),
    /** The control this row shows on this platform (see [SettingsPlatform.control]). */
    val control: SettingsControl = row.control,
    /** Key binding rows: the key cell. */
    val key: Rect? = null,
    /** Readout rows: the value, right-aligned in this box. */
    val readout: Rect? = null,
    /** Action rows: the two-press button. */
    val action: Rect? = null,
) {
    /** Whether the row has a (!) explanation of its own. */
    val hasInfo: Boolean get() = info.width > 0f

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
    /** The title; null in compact landscape, where the selected tab names the page. */
    val title: Rect?,
    val tabs: List<SettingsGroupTab>,
    /** Label factor (≤ 1) of every tab, shrunk so the tabs fit their row, column or grid. */
    val tabScale: Float,
    val rows: List<SettingsRowLayout>,
    val pages: List<Rect>,
    val preview: Rect?,
    /** Bottom-left x of the sheared ink panel behind the preview; null when the preview is hidden. */
    val panelEdgeX: Float?,
    val rowsLeft: Float,
    val rowsRight: Float,
    val platform: SettingsPlatform = SettingsPlatform.DEFAULT,
) {
    val visibleRows: List<SettingsRow> get() = rows.map(SettingsRowLayout::row)

    fun row(row: SettingsRow): SettingsRowLayout? = rows.firstOrNull { it.row == row }

    /** Area of the Compose volume slider and its numeric editor; null when the row is not shown. */
    fun volumeBounds(): Rect? = row(SettingsRow.MASTER_VOLUME)?.sliderBounds

    /** Navigation the (!) explanations must never cover: Back, title, tabs and page pips. */
    val navigation: List<Rect> get() = listOfNotNull(back, title) + tabs.map(SettingsGroupTab::bounds) + pages

    /**
     * Where [row]'s (!) explanation of [bodyHeight] px sits: above the (!) at its end (`.tip-r`),
     * or below it when above would leave the screen or cover navigation.
     */
    fun tooltipRect(row: SettingsRow, bodyHeight: Float, widthDp: Float = 270f): Rect? {
        val anchor = row(row)?.info ?: return null
        val above = kkTooltipRect(anchor, bodyHeight, density, bounds, KkTooltipPlacement.ABOVE_END, widthDp)
        if (navigation.none { it.overlaps(above) }) return above
        val height = bodyHeight + 22f * density
        val margin = 8f * density
        val lowest = bounds.bottom - margin - height
        val below = min(anchor.bottom + 12f * density, lowest).coerceAtLeast(bounds.top + margin)
        // Otherwise the free position nearest the (!), scanning the column in 4 dp steps.
        val step = 4f * density
        val tops = generateSequence(bounds.top + margin) { it + step }.takeWhile { it <= lowest }
        val top = (sequenceOf(below) + tops.sortedBy { kotlin.math.abs(it - below) }).firstOrNull { candidate ->
            val rect = Rect(above.left, candidate, above.right, candidate + height)
            navigation.none { it.overlaps(rect) }
        } ?: below
        return Rect(above.left, top, above.right, top + height)
    }
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
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): SettingsLayout {
    val mode = settingsLayoutMode(screenWidth, screenHeight, density)
    val geometry = SettingsGeometry(screenWidth, screenHeight, density, mode, language, metrics, platform)
    return geometry.build(group, page)
}

private class SettingsGeometry(
    val width: Float,
    val height: Float,
    val density: Float,
    val mode: SettingsLayoutMode,
    val language: AppLanguage,
    val metrics: SettingsTextMetrics,
    val platform: SettingsPlatform,
) {
    fun d(value: Float): Float = value * density

    val regular = mode == SettingsLayoutMode.REGULAR
    val portrait = mode == SettingsLayoutMode.COMPACT_PORTRAIT
    val rowGap = if (portrait) d(6f) else d(4f)
    val cellGap = d(3f)
    val cellHeight = if (regular) d(34f) else d(32f)
    val pagerHeight = d(40f)
    val swatchGap = d(5f)
    val swatchHeight = d(18f)

    var rowsLeft = 0f
    var rowsRight = 0f
    /** No side preview: Color vision carries its own role swatches. */
    var swatchStrip = false

    fun build(requestedGroup: SettingsGroup, requestedPage: Int): SettingsLayout {
        val groups = platform.groups
        val group = requestedGroup.takeIf { it in groups } ?: groups.first()
        val backWidth = metrics.width(language.text(SettingsRedesignText.Back), SettingsTextKind.BUTTON) + d(40f)
        val titleWidth = metrics.width(language.text(ProfileText.SettingsTitle), SettingsTextKind.TITLE)
        val tabTexts = groups.map { metrics.width(language.text(it.label), SettingsTextKind.TAB) }
        val tabPadding = d(40f)

        val back: Rect
        val rule: Rect?
        val title: Rect?
        val tabs: List<SettingsGroupTab>
        val tabScale: Float
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
                // One row from x 50 that must end by the rows' right edge: labels shrink in 5 % steps
                // to 80 %, then the row wraps to a second one.
                val tabGap = d(6f)
                val limit = rowsRight - d(50f)
                fun rowWidth(scale: Float, count: Int = tabTexts.size, from: Int = 0): Float =
                    (from until from + count).sumOf { (tabTexts[it] * scale + tabPadding).toDouble() }.toFloat() + tabGap * (count - 1)
                val scale = (0..4).map { 1f - it * 0.05f }.firstOrNull { rowWidth(it) <= limit } ?: REGULAR_TAB_MIN_SCALE
                tabScale = scale
                val wraps = rowWidth(scale) > limit
                var firstRowCount = tabTexts.size
                if (wraps) {
                    firstRowCount = (1..tabTexts.size).last { rowWidth(scale, it) <= limit || it == 1 }
                }
                var x = d(50f)
                tabs = groups.mapIndexed { index, tab ->
                    if (index == firstRowCount) x = d(50f)
                    val top = if (index < firstRowCount) d(104f) else d(150f)
                    val bounds = Rect(x, top, x + tabTexts[index] * scale + tabPadding, top + d(40f))
                    x = bounds.right + tabGap
                    SettingsGroupTab(tab, bounds)
                }
                rowsTop = if (wraps) d(218f) else d(172f)
                rowsBottom = height - d(24f)
                if (previewShown) {
                    preview = Rect(previewLeft, d(108f), previewLeft + d(336f), height - d(40f))
                    panelEdgeX = previewLeft - d(56f)
                }
            }
            SettingsLayoutMode.COMPACT_LANDSCAPE -> {
                // No title: the selected tab names the page (the pane title carries "Settings").
                val sideWidth = max(tabTexts.max() + tabPadding, backWidth)
                    .coerceIn(d(140f), max(d(140f), min(d(220f), width * 0.3f)))
                tabScale = ((sideWidth - tabPadding) / tabTexts.max()).coerceIn(COMPACT_TAB_MIN_SCALE, 1f)
                back = Rect(d(16f), d(12f), d(16f) + backWidth, d(50f))
                rule = null
                title = null
                val count = groups.size
                val gap = d(4f)
                val tabHeight = ((height - d(12f) - d(60f) - gap * (count - 1)) / count).coerceIn(d(32f), d(42f))
                var y = back.bottom + d(10f)
                tabs = groups.map { tab ->
                    val bounds = Rect(d(16f), y, d(16f) + sideWidth, y + tabHeight)
                    y = bounds.bottom + gap
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
                // A 3 × 2 grid; labels shrink to fit with 10 dp padding per side.
                val gap = d(6f)
                val tabWidth = (width - d(32f) - gap * (PORTRAIT_TAB_COLUMNS - 1)) / PORTRAIT_TAB_COLUMNS
                tabScale = ((tabWidth - d(20f)) / tabTexts.max()).coerceIn(COMPACT_TAB_MIN_SCALE, 1f)
                tabs = groups.mapIndexed { index, tab ->
                    val left = d(16f) + (tabWidth + gap) * (index % PORTRAIT_TAB_COLUMNS)
                    val top = d(70f) + d(46f) * (index / PORTRAIT_TAB_COLUMNS)
                    SettingsGroupTab(tab, Rect(left, top, left + tabWidth, top + d(40f)))
                }
                rowsLeft = d(24f)
                rowsRight = width - d(16f)
                rowsTop = d(176f)
                rowsBottom = height - d(16f)
            }
        }

        swatchStrip = panelEdgeX == null
        val lines = rowLines(group)
        val heights = lines.map { line -> line.maxOf { (row, column) -> rowHeight(row, column) } }
        val available = max(0f, rowsBottom - rowsTop)
        val fitsOnePage = heights.sum() + rowGap * (heights.size - 1) <= available
        val pages = if (fitsOnePage) listOf(lines.indices) else paginate(heights, available - pagerHeight)
        val maxPage = pages.lastIndex
        val visiblePage = requestedPage.coerceIn(0, maxPage)
        val pageLines = pages[visiblePage]
        // Landscape rows share the spare height (up to 12 dp each) above their own minimum.
        val grow = if (mode == SettingsLayoutMode.COMPACT_LANDSCAPE) {
            val space = available - if (maxPage > 0) pagerHeight else 0f
            val used = pageLines.sumOf { heights[it].toDouble() }.toFloat() + rowGap * (pageLines.count() - 1)
            ((space - used) / pageLines.count()).coerceIn(0f, d(12f))
        } else 0f
        var top = rowsTop
        // Reading order: a page lists its left column, then its right column, then full-width rows.
        val placed = ArrayList<Pair<Int, SettingsRowLayout>>()
        pageLines.forEach { index ->
            val lineHeight = heights[index] + grow
            lines[index].forEach { (row, column) ->
                val (left, right) = columnSpan(column)
                placed += column.order to rowLayout(row, Rect(left, top, right, top + lineHeight))
            }
            top += lineHeight + rowGap
        }
        val rows = placed.sortedBy { it.first }.map { it.second }
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
            tabScale = tabScale,
            rows = rows,
            pages = pagerRects,
            preview = preview,
            panelEdgeX = panelEdgeX,
            rowsLeft = rowsLeft,
            rowsRight = rowsRight,
            platform = platform,
        )
    }

    /** Where a row sits across the rows area; [order] sorts a page into reading order. */
    enum class Column(val order: Int) { FULL(2), LEFT(0), RIGHT(1) }

    /** Horizontal span of [column]: the whole rows area, or one of two columns 24 dp apart. */
    fun columnSpan(column: Column): Pair<Float, Float> {
        val columnWidth = (rowsRight - rowsLeft - d(CONTROLS_COLUMN_GAP_DP)) * 0.5f
        return when (column) {
            Column.FULL -> rowsLeft to rowsRight
            Column.LEFT -> rowsLeft to rowsLeft + columnWidth
            Column.RIGHT -> rowsRight - columnWidth to rowsRight
        }
    }

    /**
     * The visible rows of [group] as lines sharing one height. Controls splits into two columns
     * when the rows area is at least 600 dp wide: the first half of the key rows on the left, the
     * second half on the right, and the remaining rows (Reset keys) across both below.
     */
    fun rowLines(group: SettingsGroup): List<List<Pair<SettingsRow, Column>>> {
        val visible = platform.rows(group)
        val twoColumns = group == SettingsGroup.CONTROLS && rowsRight - rowsLeft >= d(CONTROLS_TWO_COLUMN_DP)
        if (!twoColumns) return visible.map { listOf(it to Column.FULL) }
        val keys = visible.filter { it.boundAction != null }
        val half = (keys.size + 1) / 2
        val left = keys.take(half)
        val right = keys.drop(half)
        return left.indices.map { index ->
            listOfNotNull(left[index] to Column.LEFT, right.getOrNull(index)?.let { it to Column.RIGHT })
        } + visible.filter { it.boundAction == null }.map { listOf(it to Column.FULL) }
    }

    fun controlOf(row: SettingsRow): SettingsControl = platform.control(row)

    /** Portrait stacks the segmented and slider controls under their label; the rest is one line. */
    fun twoLine(row: SettingsRow): Boolean =
        portrait && controlOf(row).let { it == SettingsControl.SEGMENTED || it == SettingsControl.SLIDER }

    fun hasSwatches(row: SettingsRow): Boolean = swatchStrip && row == SettingsRow.COLOR_VISION

    /** Height of the control line (plus the swatch strip on Color vision) of [row] in [column]. */
    fun rowHeight(row: SettingsRow, column: Column = Column.FULL): Float {
        val keyRow = controlOf(row) == SettingsControl.KEYBIND
        val line = when {
            regular -> if (keyRow) d(52f) else d(62f)
            twoLine(row) -> d(if (hasSwatches(row)) 95f else 90f)
            // A one-line portrait row whose label needs a second line gets the height for it.
            portrait -> max(d(52f), labelLines(row, column) * metrics.rowLabelLineHeight() + d(14f))
            else -> landscapeLineHeight(row, column)
        }
        return line + if (hasSwatches(row)) swatchGap + swatchHeight else 0f
    }

    /** Compact landscape: a label that needs its second line gets the height for it. */
    fun landscapeLineHeight(row: SettingsRow, column: Column = Column.FULL): Float {
        // Above a swatch strip the cells keep a 44 dp hit band between the row top and the strip.
        val minimum = if (hasSwatches(row)) d(52f) else d(SETTINGS_TOUCH_DP)
        return max(minimum, metrics.rowLabelLineHeight() * labelLines(row, column) + d(14f))
    }

    /** One or two label lines: two when the label is wider than its box on a one-line row. */
    fun labelLines(row: SettingsRow, column: Column): Int {
        val (left, right) = columnSpan(column)
        val probe = rowLayout(row, Rect(left, 0f, right, d(SETTINGS_TOUCH_DP)), strip = false)
        return if (metrics.width(row.label(language), SettingsTextKind.ROW_LABEL) <= probe.label.width) 1 else 2
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

    fun rowLayout(row: SettingsRow, bounds: Rect, strip: Boolean = hasSwatches(row)): SettingsRowLayout {
        val layout = lineLayout(row, bounds, if (strip) bounds.bottom - swatchGap - swatchHeight else bounds.bottom)
        if (!strip || layout.options.isEmpty()) return layout
        // Five sheared role chips under the cells, spanning the same width.
        val left = layout.options.first().left
        val right = layout.options.last().right
        val top = layout.options.last().bottom + swatchGap
        val gap = d(4f)
        val chip = (right - left - gap * 4f) / 5f
        val swatches = (0 until 5).map { index ->
            val x = left + (chip + gap) * index
            Rect(x, top, x + chip, top + swatchHeight)
        }
        return layout.copy(swatches = swatches)
    }

    /** Lays out a row whose control line ends at [lineEnd] (the swatch strip, if any, sits below it). */
    fun lineLayout(row: SettingsRow, bounds: Rect, lineEnd: Float): SettingsRowLayout {
        val padLeft = when (mode) {
            SettingsLayoutMode.REGULAR -> d(28f)
            SettingsLayoutMode.COMPACT_LANDSCAPE -> d(16f)
            SettingsLayoutMode.COMPACT_PORTRAIT -> d(12f)
        }
        val padRight = if (regular) d(22f) else d(12f)
        val controlGap = if (regular) d(20f) else d(12f)
        val labelLeft = bounds.left + padLeft
        val lineTop = bounds.top
        val lineBottom = if (twoLine(row)) bounds.top + d(46f) else lineEnd
        val lineCenter = (lineTop + lineBottom) * 0.5f
        // Key rows have no (!) of their own: the group's explanation is Reset keys' (!).
        val info = if (controlOf(row) == SettingsControl.KEYBIND) {
            Rect(bounds.right - padRight, lineCenter, bounds.right - padRight, lineCenter)
        } else {
            val infoLeft = bounds.right - padRight - d(26f)
            Rect(infoLeft, lineCenter - d(12f), infoLeft + d(24f), lineCenter + d(12f))
        }
        if (twoLine(row)) {
            val controlCenter = lineEnd - d(10f) - cellHeight * 0.5f
            val label = Rect(labelLeft, lineTop, info.left - controlGap, lineBottom)
            return controlLayout(row, bounds, label, info, labelLeft, bounds.right - padRight, controlCenter, labelLeft)
        }
        val controlRight = if (controlOf(row) == SettingsControl.KEYBIND) info.left else info.left - controlGap
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
        val placeholder = Rect(labelLeft, lineTop, controlRight, lineBottom)
        val layout = controlLayout(row, bounds, placeholder, info, controlMinLeft, controlRight, lineCenter, labelLeft)
        val controlLeft = layout.options.firstOrNull()?.left ?: layout.toggle?.left ?: layout.decrease?.left
            ?: layout.key?.left ?: layout.readout?.left ?: layout.action?.left ?: controlRight
        return layout.copy(label = Rect(labelLeft, lineTop, max(labelLeft, controlLeft - controlGap), lineBottom))
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
        val control = controlOf(row)
        return when (control) {
            SettingsControl.SEGMENTED -> {
                val count = row.optionCount(platform)
                val natural = (0 until count).map { option ->
                    max(if (regular) d(64f) else d(48f),
                        metrics.width(row.optionLabel(option, language, platform), SettingsTextKind.SEGMENT) + if (regular) d(28f) else d(20f))
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
                SettingsRowLayout(row, bounds, label, info, options = cells, controlScale = min(1f, scale), control = control)
            }
            SettingsControl.TOGGLE -> SettingsRowLayout(
                row, bounds, label, info,
                toggle = Rect(right - d(62f), centerY - d(14f), right, centerY + d(14f)),
                control = control,
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
                SettingsRowLayout(
                    row, bounds, label, info, decrease = decrease, track = track, increase = increase, output = outputRect,
                    control = control,
                )
            }
            SettingsControl.KEYBIND -> {
                // The cell gives up to a third of its width to a long action name (key caps are short).
                val natural = if (regular) d(132f) else d(112f)
                val labelNeed = metrics.width(row.label(language), SettingsTextKind.ROW_LABEL) / settingsType(mode).rowLabelLines + d(8f)
                val room = right - (label.left + labelNeed + (if (regular) d(20f) else d(12f)))
                val cellWidth = min(available, room.coerceIn(natural * KEY_CELL_MIN_SHARE, natural))
                SettingsRowLayout(
                    row, bounds, label, info,
                    key = Rect(right - cellWidth, centerY - cellHeight * 0.5f, right, centerY + cellHeight * 0.5f),
                    control = control,
                )
            }
            SettingsControl.READOUT -> {
                val valueWidth = min(available, readoutWidth() + d(4f))
                SettingsRowLayout(
                    row, bounds, label, info,
                    readout = Rect(right - valueWidth, centerY - cellHeight * 0.5f, right, centerY + cellHeight * 0.5f),
                    control = control,
                )
            }
            SettingsControl.ACTION -> {
                val labels = listOf(SettingsRedesignText.ResetBindingsButton, SettingsRedesignText.ResetBindingsConfirm)
                val buttonWidth = min(available, labels.maxOf { metrics.width(language.text(it), SettingsTextKind.BUTTON) } + d(40f))
                val half = d(SETTINGS_ACTION_HEIGHT_DP) * 0.5f
                SettingsRowLayout(
                    row, bounds, label, info,
                    action = Rect(right - buttonWidth, centerY - half, right, centerY + half),
                    control = control,
                )
            }
        }
    }

    /** Widest value a slider row can show, so the value column never shifts while stepping. */
    fun sliderOutputWidth(row: SettingsRow): Float {
        val samples = when (row) {
            SettingsRow.DAMAGE_COLOR_THRESHOLDS -> settingsThresholdOutputSamples(language)
            SettingsRow.WINDOW_SIZE -> WindowSizePreset.entries.map { settingsWindowSizeText(it.widthDp, it.heightDp, language) }
            SettingsRow.FRAME_RATE_CAP -> FRAME_RATE_CAP_STEPS.map { settingsFrameRateText(it, language) }
            else -> listOf("100%")
        }
        return samples.maxOf { metrics.width(it, SettingsTextKind.OUTPUT) }
    }

    /** Widest refresh readout ("360 Hz" or the unknown mark). */
    fun readoutWidth(): Float = listOf(
        refreshRateText(REFRESH_RATE_RANGE.last, language),
        language.text(SettingsRedesignText.RefreshUnknown),
    ).maxOf { metrics.width(it, SettingsTextKind.READOUT) }
}

/** A key cell keeps at least this share of its natural width. */
private const val KEY_CELL_MIN_SHARE = 0.7f

/** Smallest label factor of a single desktop tab row before it wraps. */
private const val REGULAR_TAB_MIN_SCALE = 0.8f

/** Smallest label factor of the phone tab column and grid. */
private const val COMPACT_TAB_MIN_SCALE = 0.7f

private const val PORTRAIT_TAB_COLUMNS = 3

/** Controls splits into two columns from this rows width, with this gap. */
internal const val CONTROLS_TWO_COLUMN_DP = 600f
internal const val CONTROLS_COLUMN_GAP_DP = 24f

/** Height of the Reset keys button. */
internal const val SETTINGS_ACTION_HEIGHT_DP = 38f
