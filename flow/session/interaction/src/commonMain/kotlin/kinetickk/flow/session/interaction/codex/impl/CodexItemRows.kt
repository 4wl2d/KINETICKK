// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The Items matrix of the Catalog tab, as on the design board: labelled rows of fixed slots. Every
 * item sits in exactly one row of the chosen sort, in a slot that never moves, so discovering an item
 * fills its own hatched cell. Everything here is a pure read of the render model and the catalog.
 */

/** A row of the Items matrix under one [CodexItemSort]; [key] names it in lazy keys and test tags. */
internal sealed interface CodexItemGroup {
    val key: String

    /** The row's visible name in [language]. */
    fun name(language: AppLanguage): String

    /** Items whose first tag is [tag]; null holds the items without a tag. */
    data class Tag(val tag: BuildTag?) : CodexItemGroup {
        override val key: String = "tag/${tag?.name ?: "NONE"}"
        override fun name(language: AppLanguage): String = tag?.displayLabel?.localizedContent(language) ?: language.text(CodexText.NO_TAG)
    }

    /** Items whose first element is [element]; null holds the Kinetic items, which carry none. */
    data class Element(val element: StatusElement?) : CodexItemGroup {
        override val key: String = "element/${element?.name ?: "KINETIC"}"
        override fun name(language: AppLanguage): String = (element?.displayLabel ?: CODEX_KINETIC).localizedContent(language)
    }

    data class Rarity(val rarity: ItemRarity) : CodexItemGroup {
        override val key: String = "rarity/${rarity.name}"
        override fun name(language: AppLanguage): String = rarity.displayLabel.localizedContent(language)
    }
}

/** Content name of the damage an item without an element deals. */
private const val CODEX_KINETIC = "Kinetic"

private val CodexTagGroups: List<CodexItemGroup> = BuildTag.entries.map { CodexItemGroup.Tag(it) } + CodexItemGroup.Tag(null)
private val CodexElementGroups: List<CodexItemGroup> = StatusElement.entries.map { CodexItemGroup.Element(it) } + CodexItemGroup.Element(null)
private val CodexRarityGroups: List<CodexItemGroup> = ItemRarity.entries.map { CodexItemGroup.Rarity(it) }

/** Every row of [sort] in display order: enum order, with No tag and Kinetic last. */
internal fun codexItemGroups(sort: CodexItemSort): List<CodexItemGroup> = when (sort) {
    CodexItemSort.TAG -> CodexTagGroups
    CodexItemSort.ELEMENT -> CodexElementGroups
    CodexItemSort.RARITY -> CodexRarityGroups
}

/** Index in [codexItemGroups] of the row [item] sits in: its first tag, its first element or its rarity. */
private fun CodexItemSort.rowOf(item: ItemDefinition): Int = when (this) {
    CodexItemSort.TAG -> item.tags.firstOrNull()?.ordinal ?: BuildTag.entries.size
    CodexItemSort.ELEMENT -> item.elements.firstOrNull()?.ordinal ?: StatusElement.entries.size
    CodexItemSort.RARITY -> item.rarity.ordinal
}

/** Tag and element rows: rarity, unlock level, then id, so each rarity fills its band from the left. */
private val CodexBoardSlotOrder = Comparator<ItemDefinition> { a, b ->
    when {
        a.rarity != b.rarity -> a.rarity.compareTo(b.rarity)
        a.unlockLevel != b.unlockLevel -> a.unlockLevel.compareTo(b.unlockLevel)
        else -> a.id.compareTo(b.id)
    }
}

/** Rarity rows: first tag (untagged last), first element (Kinetic last), unlock level, then id. */
private val CodexRaritySlotOrder = Comparator<ItemDefinition> { a, b ->
    val tag = CodexItemSort.TAG.rowOf(a).compareTo(CodexItemSort.TAG.rowOf(b))
    val element = CodexItemSort.ELEMENT.rowOf(a).compareTo(CodexItemSort.ELEMENT.rowOf(b))
    when {
        tag != 0 -> tag
        element != 0 -> element
        a.unlockLevel != b.unlockLevel -> a.unlockLevel.compareTo(b.unlockLevel)
        else -> a.id.compareTo(b.id)
    }
}

/** One slot of a row: a discovered item, an anonymous undiscovered cell or a Rebirth tier. [rarity] only places it in a band. */
internal data class CodexItemCell(val entry: CodexEntry, val rarity: ItemRarity)

/**
 * One row of the matrix: its shown [cells] in slot order, and [found] of its [total] members discovered
 * (Rebirth-gated members included), whatever the search or filter shows.
 */
internal data class CodexItemRow(val group: CodexItemGroup, val cells: List<CodexItemCell>, val found: Int, val total: Int)

/** The rows of [sort] that show at least one cell, in display order. */
internal data class CodexItemRows(val sort: CodexItemSort, val rows: List<CodexItemRow>) {
    /** Every shown cell's entry in reading order: row by row, slot by slot. */
    val entries: List<CodexEntry> = rows.flatMap { row -> row.cells.map { it.entry } }
}

/**
 * The Items matrix of [sort]. A blank search over every item ([CodexItemFilter.ALL]) shows every item
 * in its fixed slot: discovered ones in full, the others as anonymous cells naming only their row, or
 * as the Rebirth tier that gates them. A search or the In build filter shows only the matching
 * discovered items in the same slots and drops the rows left empty. Slot order never depends on
 * discovery, language or filter, so a search result is the open matrix filtered by title.
 */
internal fun codexItemRows(
    sort: CodexItemSort,
    search: String,
    filter: CodexItemFilter,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    progress: HomeProgressProjection,
    language: AppLanguage = AppLanguage.English,
): CodexItemRows {
    val groups = codexItemGroups(sort)
    val members = List(groups.size) { ArrayList<ItemDefinition>() }
    model.items.forEach { members[sort.rowOf(it)].add(it) }
    val order = if (sort == CodexItemSort.RARITY) CodexRaritySlotOrder else CodexBoardSlotOrder
    // Spaces alone are no search (as in the other categories): one query decides both what matches and what shows.
    val query = codexItemQuery(search)
    val found = codexFilteredItems(model, query, filter, language).mapTo(HashSet()) { it.id }
    val showMissing = query.isEmpty() && filter == CodexItemFilter.ALL
    val rebirth = progress.rebirthProgress.level
    val lifetimeMatter = progress.economy.lifetimeMatter
    val rows = ArrayList<CodexItemRow>(groups.size)
    groups.forEachIndexed { index, group ->
        val items = members[index]
        if (items.isEmpty()) return@forEachIndexed
        items.sortWith(order)
        val name = group.name(language)
        val cells = items.mapNotNull { item ->
            when {
                item.id in found -> codexItemEntry(item, model, lifetimeMatter, language, catalog)
                !showMissing || model.isDiscovered(item.id) -> null
                // The row and the tier only: never the name, glyph, element or description.
                item.minimumRebirth > rebirth -> codexLockedEntry("item/${item.id}", item.minimumRebirth, language).copy(spoken = name)
                else -> unknownEntry("item/${item.id}", language).copy(spoken = name)
            }?.let { CodexItemCell(it, item.rarity) }
        }
        if (cells.isNotEmpty()) rows += CodexItemRow(group, cells, items.count { model.isDiscovered(it.id) }, items.size)
    }
    return CodexItemRows(sort, rows)
}

/** The Items search [search] as the matrix reads it: empty when it holds only spaces, else as typed. */
internal fun codexItemQuery(search: String): String = if (search.isBlank()) "" else search

/**
 * Cell columns of the banded Tag matrix, measured once over the whole catalog: for each rarity, the
 * most items of that rarity in any first-tag row (the untagged items forming one row), summed.
 */
internal fun codexItemRefColumns(items: List<ItemDefinition>): Int {
    val rarities = ItemRarity.entries.size
    val counts = IntArray((BuildTag.entries.size + 1) * rarities)
    items.forEach { counts[CodexItemSort.TAG.rowOf(it) * rarities + it.rarity.ordinal]++ }
    var columns = 0
    for (rarity in 0 until rarities) {
        var widest = 0
        for (index in rarity until counts.size step rarities) widest = max(widest, counts[index])
        columns += widest
    }
    return columns
}

/** Gap between matrix columns and between its lines. */
internal const val CODEX_ITEM_GAP_DP = 4f

/** Smallest matrix cell on wide side-panel layouts (pointer input). */
internal const val CODEX_ITEM_MIN_CELL_WIDE_DP = 22f

/** Smallest matrix cell on every other layout (touch input). */
internal const val CODEX_ITEM_MIN_CELL_TOUCH_DP = 36f

/** Largest matrix cell at the default text size; it grows with larger text. */
internal const val CODEX_ITEM_MAX_CELL_DP = 40f

/** Widest row label the board pass tries, in columns. */
private const val CODEX_ITEM_MAX_LABEL_SPAN = 8

/** Packed rows keep their labels beside them only with at least this many cell columns left. */
private const val CODEX_ITEM_MIN_PACKED_CELLS = 5

/**
 * The matrix grid: [columns] equal columns of [cellDp] with [CODEX_ITEM_GAP_DP] gaps, the first
 * [labelSpan] of them holding the row labels; 0 stacks each row's heading above its cells.
 */
internal data class CodexItemGeometry(val columns: Int, val labelSpan: Int, val cellDp: Float)

/**
 * The matrix geometry of a grid [innerDp] wide. [labelNeedDp] is the widest row label of every sort
 * and [refColumns] comes from the whole catalog ([codexItemRefColumns]), so switching the sort never
 * changes the cell size or the column count. [wide] is a side-panel layout; [ui] the text factor.
 *
 * The board pass widens the label from 1 to 8 columns beside [refColumns] cell columns until the
 * label fits, as long as cells keep the minimum size (above the maximum, more columns are added).
 * Where no width fits [labelNeedDp], the board pass runs again for [labelFloorDp], the widest label
 * once its names shrink to the smallest size they read well at, so the rarity columns stay aligned
 * at the price of smaller names. Otherwise the packed pass takes the smallest cells and gives the
 * label the columns it needs, or stacks the headings above the rows when that leaves too few cells
 * beside it.
 */
internal fun codexItemGeometry(
    innerDp: Float,
    labelNeedDp: Float,
    refColumns: Int,
    wide: Boolean,
    ui: Float,
    labelFloorDp: Float = labelNeedDp,
): CodexItemGeometry {
    val inner = if (innerDp.isFinite() && innerDp > 0f) innerDp else 0f
    val labelNeed = if (labelNeedDp.isFinite() && labelNeedDp > 0f) labelNeedDp else 0f
    val labelFloor = if (labelFloorDp.isFinite() && labelFloorDp > 0f) min(labelFloorDp, labelNeed) else labelNeed
    val gap = CODEX_ITEM_GAP_DP
    val minCell = if (wide) CODEX_ITEM_MIN_CELL_WIDE_DP else CODEX_ITEM_MIN_CELL_TOUCH_DP
    val maxCell = CODEX_ITEM_MAX_CELL_DP * (if (ui > 1f) ui else 1f)
    val reference = refColumns.coerceAtLeast(1)
    fun cell(columns: Int): Float = (inner - (columns - 1) * gap) / columns
    fun fitting(size: Float): Int = max(1, floor((inner + gap) / (size + gap)).toInt())
    fun board(need: Float): CodexItemGeometry? {
        for (span in 1..CODEX_ITEM_MAX_LABEL_SPAN) {
            var columns = span + reference
            var size = cell(columns)
            if (size < minCell) break
            if (size > maxCell) {
                columns = fitting(maxCell)
                size = cell(columns)
            }
            if (span * size + (span - 1) * gap >= need) return CodexItemGeometry(columns, span, size)
        }
        return null
    }
    board(labelNeed)?.let { return it }
    if (labelFloor < labelNeed) board(labelFloor)?.let { return it }
    val columns = fitting(minCell)
    val size = cell(columns).coerceAtLeast(0f)
    val span = ceil((labelNeed + gap) / (size + gap)).toInt()
    // Side labels need enough cells beside them and at most 0.4 of the columns (5·L ≤ 2·n, exactly).
    val beside = columns - span >= CODEX_ITEM_MIN_PACKED_CELLS && span * 5 <= columns * 2
    return CodexItemGeometry(columns, if (beside) span else 0, size)
}

/** How the matrix lays out its rows (see [codexItemGrid]). */
internal enum class CodexItemLayout { BANDED, PACKED, STACKED }

/** A rarity's fixed column range in the banded matrix: [width] columns from absolute [column]. */
internal data class CodexItemBand(val rarity: ItemRarity, val column: Int, val width: Int)

/**
 * One lazy item of the matrix: a [span]-column piece of grid [line] (0 is the matrix's first line)
 * from absolute [column]. [rowIndex] is its row in [CodexItemGrid.rows], −1 for the header.
 */
internal sealed interface CodexGridItem {
    val key: String
    val rowIndex: Int
    val line: Int
    val column: Int
    val span: Int

    /** Whether this item covers absolute [column] (the previewed column's stripe). */
    fun covers(column: Int): Boolean = column >= this.column && column < this.column + span

    /** The banded header line: the rotated rarity names over [CodexItemGrid.bands]. */
    data class Head(override val span: Int) : CodexGridItem {
        override val key: String get() = "items/head"
        override val rowIndex: Int get() = -1
        override val line: Int get() = 0
        override val column: Int get() = 0
    }

    /** A row's label beside its first line, or its full-width heading above its cells when [stacked]. */
    data class Label(
        val row: CodexItemRow,
        override val rowIndex: Int,
        override val line: Int,
        override val span: Int,
        val stacked: Boolean,
    ) : CodexGridItem {
        override val key: String = "items/label/${row.group.key}"
        override val column: Int get() = 0
    }

    /** A thin full-width line before a row of the packed layout whose rows wrap: it sets the rows' groups of lines apart. */
    data class Rule(override val key: String, override val line: Int, override val span: Int) : CodexGridItem {
        override val rowIndex: Int get() = -1
        override val column: Int get() = 0
    }

    /** Empty space, never focusable: a gutter under a label, a band's blank or a line's end. */
    data class Spacer(
        override val key: String,
        override val rowIndex: Int,
        override val line: Int,
        override val column: Int,
        override val span: Int,
    ) : CodexGridItem

    /** One slot, keyed by its entry (`item/<id>`). */
    data class Cell(val cell: CodexItemCell, override val rowIndex: Int, override val line: Int, override val column: Int) : CodexGridItem {
        override val key: String get() = cell.entry.key
        override val span: Int get() = 1
    }
}

/** Where a cell sits: row, grid line, absolute column, index among [CodexItemGrid.items] and in reading [order]. */
internal data class CodexItemPosition(val key: String, val rowIndex: Int, val line: Int, val column: Int, val gridIndex: Int, val order: Int)

/**
 * The matrix as lazy grid items for one [geometry] (the effective one). Every line's [items] span
 * exactly [CodexItemGeometry.columns]; [bands] are set in the banded layout only; [cells] lists every
 * cell in reading order and [positions] finds one by key. [labelled] holds the lines a side label
 * shares with cells (the label may be taller than a cell).
 */
internal data class CodexItemGrid(
    val layout: CodexItemLayout,
    val geometry: CodexItemGeometry,
    val rows: List<CodexItemRow>,
    val items: List<CodexGridItem>,
    val bands: List<CodexItemBand>,
    val cells: List<CodexItemPosition>,
) {
    val positions: Map<String, CodexItemPosition> = cells.associateBy { it.key }
    val labelled: Set<Int> = items.filter { it is CodexGridItem.Label && !it.stacked }.mapTo(HashSet()) { it.line }

    /** Index in [items] of row [rowIndex]'s first part (its label or heading), or −1 for no such row. */
    fun rowStart(rowIndex: Int): Int = items.indexOfFirst { it.rowIndex == rowIndex }
}

/**
 * Lays [rows] out for [geometry]. Banded (side labels, Tag or Element sort, [banded] allowed and every
 * rarity's widest row fitting beside the labels): a header line, then one line per row in which each
 * rarity owns a fixed column range, blanks keeping the ranges aligned. Packed (side labels
 * otherwise): each row wraps onto continuation lines behind a gutter, and where any row wraps a rule
 * line sets each row's group of lines apart. Stacked (no label columns): a full-width heading above
 * each row's wrapping cells. The last line of a row is padded with a blank, and rows without a cell
 * are dropped.
 */
internal fun codexItemGrid(rows: List<CodexItemRow>, geometry: CodexItemGeometry, sort: CodexItemSort, banded: Boolean = true): CodexItemGrid {
    val shown = rows.filter { it.cells.isNotEmpty() }
    val columns = max(1, geometry.columns)
    val label = geometry.labelSpan.coerceIn(0, columns - 1)
    val bands = if (banded && label > 0 && sort != CodexItemSort.RARITY) codexItemBands(shown, label) else emptyList()
    val layout = when {
        label == 0 -> CodexItemLayout.STACKED
        bands.isNotEmpty() && bands.last().let { it.column + it.width } <= columns -> CodexItemLayout.BANDED
        else -> CodexItemLayout.PACKED
    }
    val items = ArrayList<CodexGridItem>()
    val cells = ArrayList<CodexItemPosition>()
    var line = 0
    fun cell(cell: CodexItemCell, rowIndex: Int, column: Int) {
        cells += CodexItemPosition(cell.entry.key, rowIndex, line, column, items.size, cells.size)
        items += CodexGridItem.Cell(cell, rowIndex, line, column)
    }
    fun blank(row: CodexItemRow, rowIndex: Int, local: Int, column: Int, end: Int) {
        if (column < end) items += CodexGridItem.Spacer("items/blank/${row.group.key}/$local/$column", rowIndex, line, column, end - column)
    }
    if (layout == CodexItemLayout.BANDED) {
        items += CodexGridItem.Head(columns)
        line++
        shown.forEachIndexed { rowIndex, row ->
            items += CodexGridItem.Label(row, rowIndex, line, label, stacked = false)
            bands.forEach { band ->
                var column = band.column
                row.cells.forEach { if (it.rarity == band.rarity) cell(it, rowIndex, column++) }
                blank(row, rowIndex, 0, column, band.column + band.width)
            }
            blank(row, rowIndex, 0, bands.last().let { it.column + it.width }, columns)
            line++
        }
    } else {
        val perLine = columns - label
        // Wrapped rows would run into each other: a rule line before every row but the first keeps them apart.
        val ruled = label > 0 && shown.any { it.cells.size > perLine }
        shown.forEachIndexed { rowIndex, row ->
            if (ruled && rowIndex > 0) {
                items += CodexGridItem.Rule("items/rule/${row.group.key}", line, columns)
                line++
            }
            var local = 0
            if (label == 0) {
                items += CodexGridItem.Label(row, rowIndex, line, columns, stacked = true)
                line++
                local++
            }
            var next = 0
            while (next < row.cells.size) {
                if (label > 0) items += when (next) {
                    0 -> CodexGridItem.Label(row, rowIndex, line, label, stacked = false)
                    else -> CodexGridItem.Spacer("items/gutter/${row.group.key}/$local", rowIndex, line, 0, label)
                }
                val count = min(perLine, row.cells.size - next)
                for (slot in 0 until count) cell(row.cells[next + slot], rowIndex, label + slot)
                next += count
                blank(row, rowIndex, local, label + count, columns)
                line++
                local++
            }
        }
    }
    return CodexItemGrid(layout, CodexItemGeometry(columns, label, geometry.cellDp), shown, items,
        if (layout == CodexItemLayout.BANDED) bands else emptyList(), cells)
}

/** Each rarity's band: as wide as its most cells in any row, in rarity order after [label] columns; empty ones drop. */
private fun codexItemBands(rows: List<CodexItemRow>, label: Int): List<CodexItemBand> {
    val widths = IntArray(ItemRarity.entries.size)
    val counts = IntArray(ItemRarity.entries.size)
    rows.forEach { row ->
        counts.fill(0)
        row.cells.forEach { counts[it.rarity.ordinal]++ }
        for (rarity in counts.indices) widths[rarity] = max(widths[rarity], counts[rarity])
    }
    var column = label
    return ItemRarity.entries.mapNotNull { rarity ->
        val width = widths[rarity.ordinal]
        if (width == 0) null else CodexItemBand(rarity, column, width).also { column += width }
    }
}

/** A keyboard move in the Items matrix. */
internal enum class CodexItemMove { LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN }

/**
 * The cell [move] reaches from cell [fromKey], or null at an edge (the key event then stays
 * unconsumed). Left and Right step through the reading order across lines and rows. Up and Down go
 * to the nearest line holding a cell, to the cell in the same column or else the nearest one (the
 * left one on a tie), skipping blanks. Home and End reach the first and last cell of the row; Page Up
 * and Page Down the first cell of the previous and the next row.
 */
internal fun codexItemNeighbour(grid: CodexItemGrid, fromKey: String, move: CodexItemMove): String? {
    val from = grid.positions[fromKey] ?: return null
    val cells = grid.cells
    val target = when (move) {
        CodexItemMove.LEFT -> cells.getOrNull(from.order - 1)
        CodexItemMove.RIGHT -> cells.getOrNull(from.order + 1)
        CodexItemMove.UP -> codexNearestOnLine(cells, from, -1)
        CodexItemMove.DOWN -> codexNearestOnLine(cells, from, 1)
        CodexItemMove.HOME -> cells.firstOrNull { it.rowIndex == from.rowIndex }
        CodexItemMove.END -> cells.lastOrNull { it.rowIndex == from.rowIndex }
        CodexItemMove.PAGE_UP -> cells.firstOrNull { it.rowIndex == from.rowIndex - 1 }
        CodexItemMove.PAGE_DOWN -> cells.firstOrNull { it.rowIndex == from.rowIndex + 1 }
    }
    return target?.key?.takeIf { it != fromKey }
}

/** The cell nearest [from]'s column on the next line holding a cell in direction [step] (−1 up, 1 down). */
private fun codexNearestOnLine(cells: List<CodexItemPosition>, from: CodexItemPosition, step: Int): CodexItemPosition? {
    var index = from.order
    while (index in cells.indices && cells[index].line == from.line) index += step
    val line = cells.getOrNull(index)?.line ?: return null
    var best = cells[index]
    while (index in cells.indices && cells[index].line == line) {
        val candidate = cells[index]
        val distance = abs(candidate.column - from.column)
        val bestDistance = abs(best.column - from.column)
        if (distance < bestDistance || distance == bestDistance && candidate.column < best.column) best = candidate
        index += step
    }
    return best
}

/**
 * Geometry (px from the cell's top-left) of one Items matrix cell: the glyph and the marks around it.
 * The glyph of [glyphRadius] at [glyphCenter] reaches [CODEX_GLYPH_REACH] times as far; the NEW
 * [notch], the element [strip] and the stack [bar] stay clear of that reach and of each other.
 */
internal data class CodexItemCellMarks(
    /** 2 dp: the marks sit inside it. */
    val inset: Float,
    val glyphCenter: Offset,
    val glyphRadius: Float,
    /**
     * The NEW notch's box: its triangle is the top-left half, cut by the diagonal from top-right to
     * bottom-left; the Ink edge of its legs lies a hairline outside it, in the inset.
     */
    val notch: Rect,
    /** The element strip down the right edge, its 1 dp Ink line on the left included. */
    val strip: Rect,
    /** The in-build stack bar along the bottom: its Ink underlay, a 1 dp Ink line on top included. */
    val bar: Rect,
    /** The Reborn rim's stroke centre line; the stroke is [rimWidth] wide, its outer edge at inset + 1 dp. */
    val rim: Rect,
    val rimWidth: Float,
    /** 1 dp: the Ink lines and the gaps between segments. */
    val hairline: Float,
) {
    /** Element segment [index] of [count], top to bottom, right of the strip's Ink line and [hairline] apart. */
    fun stripSegment(index: Int, count: Int): Rect {
        val segments = count.coerceAtLeast(1)
        val height = (strip.height - (segments - 1) * hairline) / segments
        val top = strip.top + index * (height + hairline)
        return Rect(strip.left + hairline, top, strip.right, top + height)
    }

    /** Stack segment [index] of [count], left to right, under the bar's Ink line and [hairline] apart. */
    fun barSegment(index: Int, count: Int): Rect {
        val segments = count.coerceAtLeast(1)
        val width = (bar.width - (segments - 1) * hairline) / segments
        val left = bar.left + index * (width + hairline)
        return Rect(left, bar.top + hairline, left + width, bar.bottom)
    }
}

/**
 * The marks of a [cellPx] matrix cell at [density]: the glyph at (0.5, 0.47) of the cell with a 0.27
 * radius; a NEW notch with max(6 dp, 0.22) legs in the top-left corner; an element strip max(2 dp,
 * 0.08) wide on the right edge, ending 1 dp above a stack bar max(2 dp, 0.08) tall along the bottom,
 * all inside a 2 dp inset. For 22–56 dp cells every mark stays clear of the glyph's reach.
 */
internal fun codexItemCellMarks(cellPx: Float, density: Float): CodexItemCellMarks {
    val inset = 2f * density
    val hairline = density
    val legs = max(6f * density, 0.22f * cellPx)
    val stripWidth = max(2f * density, 0.08f * cellPx)
    val barHeight = max(2f * density, 0.08f * cellPx)
    val bar = Rect(inset, cellPx - inset - barHeight, cellPx - inset, cellPx - inset)
    val rimWidth = 1.5f * density
    val rim = inset + hairline + rimWidth * 0.5f
    return CodexItemCellMarks(
        inset = inset,
        glyphCenter = Offset(0.5f * cellPx, 0.47f * cellPx),
        glyphRadius = 0.27f * cellPx,
        notch = Rect(inset, inset, inset + legs, inset + legs),
        strip = Rect(cellPx - inset - stripWidth, inset, cellPx - inset, bar.top - hairline),
        bar = bar,
        rim = Rect(rim, rim, cellPx - rim, cellPx - rim),
        rimWidth = rimWidth,
        hairline = hairline,
    )
}
