// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The parts of the Items matrix (see CodexItemRows.kt): the banded header, the row labels or stacked
 * headings, the blanks and gutters that keep the columns aligned, and the cells. While a cell is
 * previewed, every part of its row and, in the banded layout, of its column paints a faint `you` band
 * reaching half a gap beyond it, so the bands run on unbroken across the gaps.
 */

/** Row name size (design sp, before the text-size factor) and the smallest it shrinks to. */
internal const val CODEX_ROW_NAME_FONT = 10f
private const val CODEX_ROW_NAME_FONT_MIN = 7f

/**
 * The size (design sp) the longest row names may shrink to so the rarity columns stay aligned (see
 * the floor pass of [codexItemGeometry]): smaller than the usual name, still easily read.
 */
internal const val CODEX_ROW_NAME_FLOOR_FONT = 8.5f

/** The found/total count under a row name (design sp). */
private const val CODEX_ROW_COUNT_FONT = 9f

/** Line height of the row name and count, and the tighter one a label takes where that keeps it within a cell. */
private const val CODEX_ROW_LINE_EM = 1.15f
private const val CODEX_ROW_LINE_TIGHT_EM = 1f

/** Height (dp) of the rule that sets the packed layout's rows apart; with the grid's gaps around it they sit 3 gaps apart. */
private const val CODEX_ITEM_RULE_DP = 4f

/** Row glyph box (dp at the design size; it grows with larger text), the gap after it and the label's end padding. */
private const val CODEX_ROW_GLYPH_DP = 14f
private const val CODEX_ROW_GLYPH_GAP_DP = 6f
private const val CODEX_ROW_END_DP = 8f

/** Header rarity names (design sp): they shrink in half steps to the minimum, or are left out. */
private const val CODEX_HEAD_FONT = 9f
private const val CODEX_HEAD_FONT_MIN = 7f

/** Alpha of the `you` band over the previewed row and column. */
private const val CODEX_BAND_ALPHA = 0.10f

/** The row band behind a matrix part of this size, or a stripe at its column [stripe] of [span]; half a gap beyond. */
private fun DrawScope.drawCodexItemBand(color: Color, rowBand: Boolean, stripe: Int, span: Int) {
    val gap = CODEX_ITEM_GAP_DP.dp.toPx()
    if (rowBand) {
        drawRect(color, Offset(-gap * 0.5f, -gap * 0.5f), Size(size.width + gap, size.height + gap))
    } else if (stripe >= 0) {
        val pitch = (size.width + gap) / span.coerceAtLeast(1)
        drawRect(color, Offset(stripe * pitch - gap * 0.5f, -gap * 0.5f), Size(pitch, size.height + gap))
    }
}

/**
 * Width (dp) a side label needs in [language] at text factor [ui]: the glyph, its gap, the widest
 * row name of every sort (rarities, tags, elements, Kinetic and No tag) at [font] or count, and the
 * end padding. Measured over all sorts, so switching the sort never changes the matrix geometry.
 */
internal fun codexItemLabelNeed(measurer: CanvasTextMeasurer, language: AppLanguage, density: Float, ui: Float, font: Float = CODEX_ROW_NAME_FONT): Float {
    val nameStyle = measurer.typography.monoStyle(font, lineHeightEm = CODEX_ROW_LINE_EM)
    var widest = measureKkText(measurer, "00/00", measurer.typography.monoStyle(CODEX_ROW_COUNT_FONT, lineHeightEm = CODEX_ROW_LINE_EM)).size.width
    CodexItemSort.entries.forEach { sort ->
        codexItemGroups(sort).forEach { group ->
            widest = max(widest, measureKkText(measurer, group.name(language), nameStyle, uppercase = true).size.width)
        }
    }
    return CODEX_ROW_GLYPH_DP * max(1f, ui) + CODEX_ROW_GLYPH_GAP_DP + widest / density + CODEX_ROW_END_DP
}

/** A side label's [height] (dp) and the line height ([lineEm]) of its name and count. */
internal data class CodexRowLabelFit(val height: Float, val lineEm: Float)

/**
 * The side label beside [cellDp] cells at text factor [ui] ([fontScale] turns sp into dp): its name over
 * its count at 1.15 em, tightened to 1 em where the looser lines would stand taller than a cell, and never
 * shorter than a cell. A label still taller than a cell sets its line's height; the cells and blanks on
 * that line centre in it, so the gaps and the previewed row's band stay even.
 */
internal fun codexRowLabelFit(cellDp: Float, ui: Float, fontScale: Float): CodexRowLabelFit {
    val text = (CODEX_ROW_NAME_FONT + CODEX_ROW_COUNT_FONT) * ui * fontScale
    val em = if (text * CODEX_ROW_LINE_EM <= cellDp) CODEX_ROW_LINE_EM else CODEX_ROW_LINE_TIGHT_EM
    return CodexRowLabelFit(max(cellDp, text * em), em)
}

/** A Rarity row's glyph: a square of its rarity's fill, its [brush] made for its [rect]. */
private class CodexRowSwatch(val rect: Rect, val brush: Brush)

/** The swatch of a Rarity row ([fill] null for the other sorts) in the glyph box of [glyph] px at [center]: 12 of its 14 dp. */
private fun codexRowSwatch(fill: CodexRarityFill?, center: Offset, glyph: Float): CodexRowSwatch? = fill?.let {
    val side = glyph * 12f / CODEX_ROW_GLYPH_DP
    val rect = Rect(center.x - side * 0.5f, center.y - side * 0.5f, center.x + side * 0.5f, center.y + side * 0.5f)
    CodexRowSwatch(rect, it.brush(rect))
}

/** The banded header's [height] (dp) and the size of its rarity names ([font], design sp; NaN leaves them out). */
internal data class CodexItemsHeadFit(val height: Float, val font: Float)

/**
 * The banded header in a grid [gridHeight] dp tall, for rarity names [widest] dp long at a size: as tall
 * as the longest name at 9 sp and 10 dp more, 30 to 96 dp and at most 0.3 of the grid, the names
 * shrinking in half steps to 7 sp to fit it. Where even 7 sp names do not fit a third of the grid, the
 * header grows for them (to 96 dp at most) rather than leaving the bands unnamed; past that the names
 * are left out.
 */
internal fun codexItemsHeadFit(gridHeight: Float, widest: (Float) -> Float): CodexItemsHeadFit {
    val height = (widest(CODEX_HEAD_FONT) + 10f).coerceIn(30f, max(30f, min(96f, gridHeight * 0.3f)))
    var font = CODEX_HEAD_FONT
    while (font > CODEX_HEAD_FONT_MIN && widest(font) + 10f > height) font -= 0.5f
    if (widest(font) + 10f <= height) return CodexItemsHeadFit(height, font)
    val grown = widest(CODEX_HEAD_FONT_MIN) + 10f
    return if (grown <= 96f) CodexItemsHeadFit(max(30f, grown), CODEX_HEAD_FONT_MIN) else CodexItemsHeadFit(height, Float.NaN)
}

/**
 * The banded matrix's header line over its [columns] columns: each band's rarity name in uppercase
 * mono, rotated to read bottom-up over the band's first column, above a 2 dp rule in the rarity's fill
 * across the band. The names shrink together to fit the header, or are left out where even the
 * smallest does not fit. The previewed column's stripe runs through it and [previewRarity]'s name
 * turns `you`. The corner over the labels stays empty.
 */
@Composable
internal fun CodexItemsHead(bands: List<CodexItemBand>, columns: Int, gridHeight: Dp, stripeColumn: Int, previewRarity: ItemRarity?) {
    val measurer = codexMeasurer()
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val density = LocalDensity.current.density
    val names = remember(language) { ItemRarity.entries.map { it.displayLabel.localizedContent(language) } }
    val (height, font) = remember(measurer, names, density, gridHeight) {
        codexItemsHeadFit(gridHeight.value) { size ->
            names.maxOf { measureKkText(measurer, it, measurer.typography.monoStyle(size), uppercase = true).size.width } / density
        }
    }
    val fills = remember(roles) { ItemRarity.entries.map { codexRarityFill(it.rank, roles) } }
    Box(Modifier.fillMaxWidth().height(height.dp).testTag("codex-items-head").clearAndSetSemantics { }.drawWithCache {
        val gap = CODEX_ITEM_GAP_DP.dp.toPx()
        val pitch = (size.width + gap) / columns.coerceAtLeast(1)
        val rule = 2.dp.toPx()
        val rules = bands.map { band ->
            val rect = Rect(band.column * pitch, size.height - rule, (band.column + band.width) * pitch - gap, size.height)
            rect to fills[band.rarity.ordinal].brush(rect)
        }
        val style = if (font.isNaN()) null else measurer.typography.monoStyle(font)
        val labels = bands.map { band -> style?.let { measureKkText(measurer, names[band.rarity.ordinal], it, uppercase = true) } }
        onDrawBehind {
            if (stripeColumn >= 0) {
                val left = stripeColumn * pitch - gap * 0.5f
                drawRect(roles.you.copy(alpha = CODEX_BAND_ALPHA), Offset(left, 0f), Size(pitch, size.height + gap * 0.5f))
            }
            bands.forEachIndexed { index, band ->
                val (rect, brush) = rules[index]
                drawRect(brush, rect.topLeft, rect.size)
                val label = labels[index] ?: return@forEachIndexed
                translate(band.column * pitch + (pitch - gap) * 0.5f, size.height - 6.dp.toPx()) {
                    rotate(-90f, Offset.Zero) {
                        drawKkText(label, 0f, 0f, if (band.rarity == previewRarity) roles.you else Kk.Mute, KkAlign.START, KkVAlign.CENTER)
                    }
                }
            }
        }
    })
}

/**
 * The glyph a row is named by: its tag's in bone, its element's in the element hue (Kinetic's in the
 * kinetic one), or a swatch of its rarity's fill.
 */
private fun DrawScope.drawCodexRowGlyph(group: CodexItemGroup, center: Offset, glyph: Float, roles: KkRolePalette, swatch: CodexRowSwatch?) {
    when (group) {
        is CodexItemGroup.Tag -> group.tag?.let { drawKkIcon(it.glyph(), center, glyph, Kk.Bone) }
        is CodexItemGroup.Element ->
            drawKkIcon(group.element?.glyph() ?: KkIcon.ASPECT_KINETIC, center, glyph, group.element?.let(roles::of) ?: roles.kinetic)
        is CodexItemGroup.Rarity -> if (swatch != null) drawRect(swatch.brush, swatch.rect.topLeft, swatch.rect.size)
    }
}

/** Heading semantics of a row: its name and how many of its items are found, e.g. "Ram, Discovered 5 / 7". */
private fun Modifier.codexRowSemantics(row: CodexItemRow, name: String, language: AppLanguage): Modifier {
    val description = "$name, ${language.text(SessionText.COLLECTION_PROGRESS, row.found, row.total)}"
    return testTag("codex-items-row-${row.group.key}").semantics(mergeDescendants = true) {
        heading()
        contentDescription = description
    }
}

/**
 * A row's label beside its first line: the row glyph, then the name right-aligned in uppercase mono
 * (shrinking to fit, never cut) over the found/total count, which turns bone once the row is complete.
 * The name turns `you` while the row holds the previewed cell. As tall as [fit] (at least one cell).
 */
@Composable
internal fun CodexItemRowLabel(row: CodexItemRow, previewed: Boolean, fit: CodexRowLabelFit, ui: Float) {
    val measurer = codexMeasurer()
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val name = row.group.name(language)
    val count = "${row.found}/${row.total}"
    val fill = remember(row.group, roles) { (row.group as? CodexItemGroup.Rarity)?.let { codexRarityFill(it.rarity.rank, roles) } }
    Box(Modifier.fillMaxWidth().height(fit.height.dp).codexRowSemantics(row, name, language).drawWithCache {
        val glyph = CODEX_ROW_GLYPH_DP.dp.toPx() * max(1f, ui)
        val start = glyph + CODEX_ROW_GLYPH_GAP_DP.dp.toPx()
        val end = size.width - CODEX_ROW_END_DP.dp.toPx()
        var font = CODEX_ROW_NAME_FONT
        var title = measureKkText(measurer, name, measurer.typography.monoStyle(font, lineHeightEm = fit.lineEm), uppercase = true)
        while (font > CODEX_ROW_NAME_FONT_MIN && title.size.width > end - start) {
            font -= 0.5f
            title = measureKkText(measurer, name, measurer.typography.monoStyle(font, lineHeightEm = fit.lineEm), uppercase = true)
        }
        val counter = measureKkText(measurer, count, measurer.typography.monoStyle(CODEX_ROW_COUNT_FONT, lineHeightEm = fit.lineEm))
        val top = (size.height - title.size.height - counter.size.height) * 0.5f
        val center = Offset(glyph * 0.5f, size.height * 0.5f)
        val swatch = codexRowSwatch(fill, center, glyph)
        onDrawBehind {
            drawCodexItemBand(roles.you.copy(alpha = CODEX_BAND_ALPHA), previewed, -1, 1)
            drawCodexRowGlyph(row.group, center, glyph, roles, swatch)
            drawKkText(title, end, top, if (previewed) roles.you else Kk.Bone, KkAlign.END)
            drawKkText(counter, end, top + title.size.height, if (row.found == row.total) Kk.Bone else Kk.Mute, KkAlign.END)
        }
    })
}

/**
 * A row's full-width heading above its cells where no label column fits: the row glyph, the name in
 * uppercase mono and the found/total count after it, the name `you` while the row is previewed.
 */
@Composable
internal fun CodexItemRowHeading(row: CodexItemRow, previewed: Boolean, ui: Float) {
    val measurer = codexMeasurer()
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val name = row.group.name(language)
    val count = "${row.found}/${row.total}"
    val line = with(LocalDensity.current) { (CODEX_ROW_NAME_FONT * CODEX_ROW_LINE_EM * ui).sp.toDp() }
    val fill = remember(row.group, roles) { (row.group as? CodexItemGroup.Rarity)?.let { codexRarityFill(it.rarity.rank, roles) } }
    Box(Modifier.fillMaxWidth().height(maxOf(24.dp * max(1f, ui), line + 8.dp)).codexRowSemantics(row, name, language).drawWithCache {
        val glyph = CODEX_ROW_GLYPH_DP.dp.toPx() * max(1f, ui)
        val start = glyph + CODEX_ROW_GLYPH_GAP_DP.dp.toPx()
        val counter = measureKkText(measurer, count, measurer.typography.monoStyle(CODEX_ROW_COUNT_FONT, lineHeightEm = CODEX_ROW_LINE_EM))
        val room = size.width - start - CODEX_ROW_END_DP.dp.toPx() - counter.size.width
        val nameStyle = measurer.typography.monoStyle(CODEX_ROW_NAME_FONT, lineHeightEm = CODEX_ROW_LINE_EM)
        val title = measureKkText(measurer, name, nameStyle, uppercase = true, maxWidth = room.coerceAtLeast(1f))
        val center = Offset(glyph * 0.5f, size.height * 0.5f)
        val swatch = codexRowSwatch(fill, center, glyph)
        onDrawBehind {
            drawCodexItemBand(roles.you.copy(alpha = CODEX_BAND_ALPHA), previewed, -1, 1)
            drawCodexRowGlyph(row.group, center, glyph, roles, swatch)
            drawKkText(title, start, size.height * 0.5f, if (previewed) roles.you else Kk.Bone, KkAlign.START, KkVAlign.CENTER)
            drawKkText(counter, start + title.size.width + CODEX_ROW_END_DP.dp.toPx(), size.height * 0.5f,
                if (row.found == row.total) Kk.Bone else Kk.Mute, KkAlign.START, KkVAlign.CENTER)
        }
    })
}

/**
 * Empty matrix space of [span] columns, as tall as its line ([lineDp]): a gutter under a label or a
 * blank that keeps a band's columns aligned. Never focusable and silent; it carries the row band and the
 * previewed column's [stripe] (its column within this space, −1 for none).
 */
@Composable
internal fun CodexItemSpacer(span: Int, lineDp: Float, rowBand: Boolean, stripe: Int) {
    val roles = LocalKkRolePalette.current
    Box(Modifier.fillMaxWidth().height(lineDp.dp).clearAndSetSemantics { }.then(
        if (rowBand || stripe >= 0) {
            Modifier.drawBehind { drawCodexItemBand(roles.you.copy(alpha = CODEX_BAND_ALPHA), rowBand, stripe, span) }
        } else Modifier,
    ))
}

/** The rule before a row of the packed layout whose rows wrap: a hairline across the grid. Silent. */
@Composable
internal fun CodexItemRule() {
    Box(Modifier.fillMaxWidth().height(CODEX_ITEM_RULE_DP.dp).clearAndSetSemantics { }.drawBehind {
        val line = 1.dp.toPx()
        drawRect(Kk.Line2, Offset(0f, (size.height - line) * 0.5f), Size(size.width, line))
    })
}

/** This part, square, centred in a line at least [lineDp] tall (a taller label sets it), never shrunk to fit it. */
private fun Modifier.codexItemLine(lineDp: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = max(placeable.height, lineDp.dp.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

/**
 * One matrix cell: a found item, an anonymous undiscovered slot or a Rebirth tier, focusable and
 * clickable like every Codex slot ([slotModifier]); its description names a NEW discovery, which the
 * notch shows without a word. It centres in its line ([lineDp]). [band] puts it in the previewed row or
 * column: the band runs behind it and across its gaps, and a faint wash of it over its face (all but the
 * previewed cell's own), so the row and the column read through a matrix of opaque cells.
 */
@Composable
internal fun CodexItemRowSlot(
    entry: CodexEntry,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    band: Boolean,
    lineDp: Float,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val measurer = codexMeasurer()
    val roles = LocalKkRolePalette.current
    val language = LocalAppLanguage.current
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focusedValue by interaction.collectIsFocusedAsState()
    val pinned = selection.pinnedKey == entry.key
    val previewed = selection.previewKey == entry.key
    val item = entry.icon as? CodexIcon.Item
    val fill = remember(item?.definition?.rarity, roles) { item?.let { codexRarityFill(it.definition.rarity.rank, roles) } }
    val description = if (entry.isNew) "${entry.summary}, ${language.text(SessionText.NEW_DISCOVERY)}" else entry.summary
    val notch = remember { Path() }
    val wash = band && !previewed
    Box(Modifier.fillMaxWidth().then(
        if (band) Modifier.drawBehind { drawCodexItemBand(roles.you.copy(alpha = CODEX_BAND_ALPHA), true, -1, 1) } else Modifier,
    ).codexItemLine(lineDp)) {
        Box(slotModifier(entry, selection, focus, interaction, onSelection, description).aspectRatio(1f).drawWithCache {
            val marks = codexItemCellMarks(size.minDimension, density)
            val brush = fill?.brush(Rect(Offset.Zero, size))
            onDrawBehind {
                drawCodexItemRowCell(entry, fill, brush, marks, measurer, notch)
                if (wash) drawRect(roles.you.copy(alpha = CODEX_BAND_ALPHA))
                drawCodexMatrixSelection(pinned, previewed || focusedValue, roles)
            }
        }) {
            val stacked = (item?.stack ?: 0) > 0
            val active = item?.definition?.isActive == true
            if ((item != null && entry.isNew) || stacked || active) CodexItemCellTags(entry.key, item != null && entry.isNew, stacked, active)
        }
    }
}

/**
 * Tagged boxes over a cell's NEW notch (`codex-new-<key>`), stack bar (`codex-badge-<key>`) and ACTIVE notch
 * (`codex-active-<key>`), placed by [codexItemCellMarks].
 */
@Composable
private fun CodexItemCellTags(key: String, isNew: Boolean, stacked: Boolean, active: Boolean) {
    Layout(content = {
        if (isNew) Box(Modifier.testTag("codex-new-$key"))
        if (stacked) Box(Modifier.testTag("codex-badge-$key"))
        if (active) Box(Modifier.testTag("codex-active-$key"))
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val marks = codexItemCellMarks(constraints.maxWidth.toFloat(), density)
        val rects = listOfNotNull(marks.notch.takeIf { isNew }, marks.bar.takeIf { stacked }, marks.activeNotch.takeIf { active })
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = rects[index]
            measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0))) to rect
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (placeable, rect) -> placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
        }
    }
}
