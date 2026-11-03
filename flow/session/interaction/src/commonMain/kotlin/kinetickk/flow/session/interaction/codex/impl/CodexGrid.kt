// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.rank
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.runtime.saveable.rememberSaveable
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.*
import kotlin.math.roundToInt
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import androidx.compose.runtime.saveable.SaveableStateHolder

internal enum class CodexGridContentType {
    NOTICE, HEADING, SLOT, STAT, ROW, MATRIX, CHIPS, BLOCK, ITEM_HEAD, ITEM_LABEL, ITEM_RULE, ITEM_SPACER, ITEM_CELL,
}

/** Lazy content type of a part of the Items matrix. */
internal val CodexGridItem.contentType: CodexGridContentType
    get() = when (this) {
        is CodexGridItem.Head -> CodexGridContentType.ITEM_HEAD
        is CodexGridItem.Label -> CodexGridContentType.ITEM_LABEL
        is CodexGridItem.Rule -> CodexGridContentType.ITEM_RULE
        is CodexGridItem.Spacer -> CodexGridContentType.ITEM_SPACER
        is CodexGridItem.Cell -> CodexGridContentType.ITEM_CELL
    }

/** The banded Items matrix needs a grid at least this tall (dp): below it its header would take a third of the grid and scroll away. */
internal const val CODEX_ITEM_BANDED_MIN_GRID_DP = 300f

/**
 * A keyboard move in the Items matrix still to be brought into view: the cell keyed [target] at lazy index
 * [index] (scrolled to first when it is not composed, [scroll]) and its row's reveal ([rowStart], [lines], [gapPx]).
 * Compared by identity, so every key press is a new move even when it repeats the last one.
 */
internal class CodexMatrixMove(
    val target: String,
    val index: Int,
    val scroll: Boolean,
    val rowStart: Int,
    val lines: Int,
    val gapPx: Float,
)

/**
 * After a keyboard move to the cell keyed [targetKey], brings its row's first part (lazy index
 * [rowStart], [lines] grid lines above the cell's) into view where the row's lines down to the cell fit
 * the grid together ([gapPx] apart): the row's name then stays in sight beside a wrapped line.
 */
internal suspend fun LazyGridState.revealCodexRow(rowStart: Int, targetKey: String, lines: Int, gapPx: Float) {
    if (lines <= 0) return
    withFrameNanos { }
    val info = layoutInfo
    val start = info.visibleItemsInfo.firstOrNull { it.index == rowStart }
    if (start != null && start.offset.y >= info.viewportStartOffset) return
    val target = info.visibleItemsInfo.firstOrNull { it.key == targetKey } ?: return
    val needed = lines * (target.size.height + gapPx) + target.size.height
    if (needed <= info.viewportEndOffset - info.viewportStartOffset) scrollToItem(rowStart)
}

/** The Items matrix move a key asks for: arrows, Home / End and Page Up / Page Down; null for any other key. */
internal fun codexItemMove(key: Key): CodexItemMove? = when (key) {
    Key.DirectionLeft -> CodexItemMove.LEFT
    Key.DirectionRight -> CodexItemMove.RIGHT
    Key.DirectionUp -> CodexItemMove.UP
    Key.DirectionDown -> CodexItemMove.DOWN
    Key.MoveHome -> CodexItemMove.HOME
    Key.MoveEnd -> CodexItemMove.END
    Key.PageUp -> CodexItemMove.PAGE_UP
    Key.PageDown -> CodexItemMove.PAGE_DOWN
    else -> null
}

/**
 * Grid cell size and gap (the board's matrix cells, sized up to carry each entry's glyph), the
 * same in the collection and the Build tab. A cell holds a band at its top for a NEW stamp (left)
 * and a count badge (right) beside each other, and below it a glyph as large as a 64 dp cell's
 * centered glyph, so neither mark ever covers the glyph or makes it smaller.
 */
internal val CodexCell = 82.dp
internal val CodexCellGap = 6.dp

/**
 * Span (grid cells) of row [index] in a line of rows at least [minCells] wide: the line is filled,
 * and the rows of one line differ by at most one cell.
 */
internal fun codexRowSpan(maxLineSpan: Int, minCells: Int, index: Int): Int {
    val perLine = (maxLineSpan / minCells.coerceAtLeast(1)).coerceIn(1, maxLineSpan.coerceAtLeast(1))
    val base = maxLineSpan / perLine
    return base + if (index % perLine < maxLineSpan - base * perLine) 1 else 0
}

@Composable
internal fun CodexSlot(
    entry: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    band: Boolean,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focusedValue by interaction.collectIsFocusedAsState()
    val hoveredValue by interaction.collectIsHoveredAsState()
    val pinned = selection.pinnedKey == entry.key
    val previewed = selection.previewKey == entry.key
    val badge = entry.cellBadge()
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val newLabel = LocalAppLanguage.current.text(SessionText.NEW_DISCOVERY)
    val stampFont = codexNewStampFont(measurer)
    // Every cell keeps the mark band, so all glyphs of the grid share one size and centre.
    val stamp = remember(measurer, newLabel, density) { kkStampSize(measurer, newLabel, density, stampFont) }
    val badgeFont = codexBadgeFont(ui)
    val markBand = codexMarkBand(stamp, stampFont, with(localDensity) { badgeFont.sp.toPx() }, density)
    Box(slotModifier(entry, selection, focus, interaction, onSelection).aspectRatio(1f).drawBehind {
        val gap = CodexCellGap.toPx()
        if (band) drawRect(roles.you.copy(alpha = 0.10f), Offset(-gap * 0.5f, -gap * 0.5f), Size(size.width + gap, size.height + gap))
        drawCodexCell(entry, catalog, measurer, band, hoveredValue || focusedValue, markBand)
        drawCodexSelection(pinned, previewed || focusedValue, roles)
    }) {
        // The discovery stamp and the count badge share the mark band above the glyph.
        if (entry.isNew) Box(Modifier.align(Alignment.TopStart).offset(CodexMarkInset, CodexMarkInset)
            .size((stamp.width / density).dp, (stamp.height / density).dp).testTag("codex-new-${entry.key}")
            .drawBehind { drawKkStamp(measurer, newLabel, Offset.Zero, fontSize = stampFont) })
        if (badge != null) BasicText(badge, Modifier.align(Alignment.TopEnd).padding(CodexMarkInset).testTag("codex-badge-${entry.key}")
            .background(Kk.Ink).padding(horizontal = 3.dp),
            style = measurer.typography.monoStyle(badgeFont, color = Kk.Bone), softWrap = false, maxLines = 1)
    }
}

/** Amount shown on a cell: stacks or rank in the current run, or the equipped weapon's level. */
private fun CodexEntry.cellBadge(): String? = when (val icon = icon) {
    is CodexIcon.Item -> quantity.takeIf { icon.stack > 0 }
    is CodexIcon.Relic -> quantity.takeIf { icon.rank != null }
    is CodexIcon.Weapon -> quantity.takeIf { it != CODEX_NONE }
    // An enemy's threat out of five; a Rebirth-locked one shows its padlock in that corner instead (the threat is in its facts).
    is CodexIcon.Enemy -> quantity.takeUnless { icon.locked }
    else -> null
}

/** Inset of the NEW stamp (top-left) and of the count badge (top-right) from the cell's corners. */
private val CodexMarkInset = 3.dp

/** NEW stamp font: 10 px on screen whatever the text size (cells grow with the text instead). */
internal fun codexNewStampFont(measurer: CanvasTextMeasurer): Float = 10f / measurer.scale.coerceAtLeast(0.1f)

/** Count badge font (sp) for the UI text factor [ui] (see [codexUiScale]): 9 at the default text size. */
internal fun codexBadgeFont(ui: Float): Float = CODEX_BADGE_FONT * ui

private const val CODEX_BADGE_FONT = 9f

/** Mono line height of the count badge's one-line box. */
private const val CODEX_BADGE_LINE_HEIGHT_EM = 1.35f

/** sin 6°: a stamp's rotation lowers one end of it by this much of its half width. */
private const val CODEX_STAMP_TILT = 0.1045f

/**
 * Height (px) of the mark band at the top of every cell: a NEW stamp of [stamp] size (drawn at
 * [stampFont], see [codexNewStampFont]) with its inset, its lowest rotated corner and its hard
 * shadow, or a count badge with a [badgeFontPx] mono label, whichever reaches lower, and a 2 dp gap.
 */
internal fun codexMarkBand(stamp: Size, stampFont: Float, badgeFontPx: Float, density: Float): Float {
    val inset = CodexMarkInset.value * density
    val stampBottom = inset + stamp.height + stamp.width * 0.5f * CODEX_STAMP_TILT + 3f * density * stampFont / 17f
    val badgeBottom = inset + badgeFontPx * CODEX_BADGE_LINE_HEIGHT_EM
    return max(stampBottom, badgeBottom) + 2f * density
}

/** The glyph's stack ring, frame and faint halo reach this far out, as a multiple of its radius. */
internal const val CODEX_GLYPH_REACH = 1.15f

/** A cell glyph (px from the cell's top-left): its center and radius. */
internal data class CodexGlyph(val centerY: Float, val radius: Float, val centerX: Float)

/**
 * The glyph circle of every cell of a grid of [cellPx] cells: one size and centre for all of them,
 * whether a cell carries a NEW stamp, a count badge, both or neither. It fills the cell below the
 * [bandPx] mark band (see [codexMarkBand]) down to a 3 dp margin, centered across, and is never
 * larger than a centered glyph (0.36 of the cell).
 */
internal fun codexCellGlyph(cellPx: Float, bandPx: Float, density: Float): CodexGlyph {
    val margin = 3f * density
    val reach = min(cellPx * 0.36f * CODEX_GLYPH_REACH, min(cellPx - margin - bandPx, cellPx - 2f * margin) * 0.5f).coerceAtLeast(0f)
    return CodexGlyph((bandPx + cellPx - margin) * 0.5f, reach / CODEX_GLYPH_REACH, cellPx * 0.5f)
}

@Composable
internal fun slotModifier(
    entry: CodexEntry,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    interaction: MutableInteractionSource,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
    description: String = entry.summary,
): Modifier {
    val inputEnabled = LocalCodexInputEnabled.current
    val requester = remember(entry.key) { FocusRequester() }
    val hoveredValue by interaction.collectIsHoveredAsState()
    DisposableEffect(entry.key) {
        focus[entry.key] = requester
        onDispose {
            focus.remove(entry.key)
            onSelection { current -> current.copy(
                hoverKey = current.hoverKey?.takeUnless { it == entry.key },
                focusKey = current.focusKey?.takeUnless { it == entry.key },
            ) }
        }
    }
    LaunchedEffect(hoveredValue) {
        onSelection { current ->
            if (hoveredValue) current.copy(hoverKey = entry.key)
            else if (current.hoverKey == entry.key) current.copy(hoverKey = null) else current
        }
    }
    return Modifier.testTag("codex-slot-${entry.key}").semantics { contentDescription = description; selected = selection.pinnedKey == entry.key }
        .focusRequester(requester).focusProperties { canFocus = inputEnabled }.onFocusChanged { state ->
            onSelection { current ->
                if (state.isFocused) current.copy(focusKey = entry.key, hoverKey = null)
                else if (current.focusKey == entry.key) current.copy(focusKey = null) else current
            }
        }.hoverable(interaction, enabled = inputEnabled).clickable(enabled = inputEnabled, interactionSource = interaction, indication = null, role = Role.Button) { onSelection { it.activate(entry.key) } }
}

/**
 * The Codex grid of the current tab: the Items matrix, the flat catalog grids, the Build tab's sections and the
 * list tabs, with keyboard walking in the Items matrix. Mutable screen state is read through getters, so each
 * value is read where it is used, as the screen reads it.
 */
@Composable
internal fun CodexGridPane(
    modifier: Modifier,
    tab: CodexTab,
    category: Int,
    entries: List<CodexEntry>,
    itemRows: CodexItemRows?,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    progress: HomeProgressProjection,
    build: GameplayBuildSummaryProjection?,
    typography: InterfaceTypography,
    ui: Float,
    controlScale: Float,
    wide: Boolean,
    compactHeader: Boolean,
    sheetVisible: Boolean,
    emptyState: CodexEmptyState,
    labelNeed: Float,
    labelFloor: Float,
    refColumns: Int,
    labelDensity: Float,
    labelFontScale: Float,
    scrollHolder: SaveableStateHolder,
    slotFocus: MutableMap<String, FocusRequester>,
    listFocus: FocusRequester,
    selection: () -> CodexSelection,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
    gridFocused: () -> Boolean,
    onGridFocused: (Boolean) -> Unit,
    statsExpanded: () -> Boolean,
    onStatsExpanded: (Boolean) -> Unit,
    selectedStat: () -> String?,
    onSelectedStat: (String?) -> Unit,
    residue: StatusElement?,
    residueSource: String?,
) {
    val language = LocalAppLanguage.current
    val policy = catalog.build.policy
    BoxWithConstraints(modifier) {
        val gridHeight = maxHeight
        // Cells grow with larger text, like the controls: their badges grow too.
        val cellSize = CodexCell * max(1f, ui)
        val cellColumns = max(1, ((maxWidth + CodexCellGap) / (cellSize + CodexCellGap)).toInt())
        // Rows of the list tabs need room to stay readable: the list tabs lay them out in equal
        // columns; the Build tab spans them over its cell columns.
        val pairingWidth = 380.dp * max(1f, ui)
        val meterWidth = (if (maxWidth >= 520.dp) 360.dp else 280.dp) * max(1f, ui)
        val pairingCells = max(1, ((pairingWidth + CodexCellGap) / (cellSize + CodexCellGap)).toInt())
        val meterCells = max(1, ((meterWidth + CodexCellGap) / (cellSize + CodexCellGap)).toInt())
        val listColumns = max(1, ((maxWidth + CodexCellGap) / ((if (tab == CodexTab.PAIRINGS) pairingWidth else meterWidth) + CodexCellGap)).toInt())
        val listTab = tab == CodexTab.PAIRINGS || tab == CodexTab.TAGS
        // The Items matrix sizes its cells once for the whole catalog and every sort (see codexItemGeometry).
        val itemGeometry = remember(maxWidth, labelNeed, labelFloor, refColumns, wide, ui) {
            codexItemGeometry(maxWidth.value - 12f, labelNeed, refColumns, wide, ui, labelFloor)
        }
        // A short grid (a landscape phone) packs the rows: a banded header there would cost a third of it.
        val bandedHeight = gridHeight.value >= CODEX_ITEM_BANDED_MIN_GRID_DP
        val itemGrid = remember(itemRows, itemGeometry, bandedHeight) {
            itemRows?.let { codexItemGrid(it.rows, itemGeometry, it.sort, bandedHeight) }
        }
        val labelFit = itemGrid?.let { codexRowLabelFit(it.geometry.cellDp, ui, labelFontScale) }
        val columns = itemGrid?.geometry?.columns ?: if (listTab) listColumns else cellColumns
        val gap = if (itemGrid != null) CODEX_ITEM_GAP_DP.dp else CodexCellGap
        val describeMeters = maxWidth >= 520.dp
        // The Build tab's stat rows share one layout (value beside or under the name), like the stat panels.
        val statRows = build?.stats?.map { stat ->
            stat.name.localizedContent(language) to "${codexNumber(stat.value, language)}${stat.unit.localizedContent(language)}"
        }.orEmpty()
        val statsFit = rememberCodexPairFit(statRows, maxWidth - 12.dp - 28.dp, 12.dp, 16f * ui,
            typography.wideStyle(20f * ui, tabular = true)) { size -> typography.bodyStyle(size) }
        scrollHolder.SaveableStateProvider(if (tab == CodexTab.CATALOG) "${tab.ordinal}/$category" else "${tab.ordinal}") {
            val grid = rememberLazyGridState()
            var priorKeysValue by rememberSaveable { mutableStateOf<String?>(null) }
            val resultKey = remember(entries) { entries.joinToString("|") { it.key } }
            LaunchedEffect(resultKey) {
                if (priorKeysValue != null && priorKeysValue != resultKey) grid.scrollToItem(0)
                priorKeysValue = resultKey
            }
            // The latest keyboard move in the Items matrix; it lives with this grid, so a revisited tab replays none.
            var matrixMoveValue by remember { mutableStateOf<CodexMatrixMove?>(null) }
            LaunchedEffect(matrixMoveValue) {
                val move = matrixMoveValue ?: return@LaunchedEffect
                if (move.scroll) {
                    // Not composed: bring it in first, then focus it once it is.
                    grid.scrollToItem(move.index)
                    withFrameNanos { }
                    slotFocus[move.target]?.requestFocus()
                }
                // A wrapped line keeps its row's name in view where the row fits.
                grid.revealCodexRow(move.rowStart, move.target, move.lines, move.gapPx)
            }
            // Row/column highlight of the previewed cell in flat (catalog) grids.
            val previewIndex = if (tab == CodexTab.CATALOG && itemGrid == null) {
                entries.indexOfFirst { it.key == selection().previewKey }
            } else -1
            // In the Items matrix: the previewed cell's row, and in the banded layout its column and rarity.
            val previewPosition = itemGrid?.positions?.get(selection().previewKey)
            val previewRow = previewPosition?.rowIndex ?: -1
            val previewColumn = if (itemGrid?.layout == CodexItemLayout.BANDED) previewPosition?.column ?: -1 else -1
            val previewRarity = itemGrid?.bands?.firstOrNull { previewColumn >= it.column && previewColumn < it.column + it.width }?.rarity
            val noticeOffset = if (emptyState != CodexEmptyState.NONE) 1 else 0
            // Arrows, Home / End and Page Up / Page Down walk the matrix; at an edge the key goes on unconsumed.
            val matrixKeys = Modifier.onPreviewKeyEvent { event ->
                val matrix = itemGrid
                if (matrix == null || sheetVisible || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val move = codexItemMove(event.key) ?: return@onPreviewKeyEvent false
                val from = selection().focusKey?.takeIf { it in matrix.positions }
                val target = when {
                    from != null -> codexItemNeighbour(matrix, from, move)
                    gridFocused() -> selection().pinnedKey?.takeIf { it in matrix.positions } ?: matrix.cells.firstOrNull()?.key
                    else -> null
                } ?: return@onPreviewKeyEvent false
                val position = matrix.positions[target] ?: return@onPreviewKeyEvent false
                val requester = slotFocus[target]
                requester?.requestFocus()
                val rowStart = matrix.rowStart(position.rowIndex)
                val lines = position.line - matrix.items[rowStart].line
                val gapPx = CODEX_ITEM_GAP_DP * labelDensity
                matrixMoveValue = CodexMatrixMove(target, noticeOffset + position.gridIndex, requester == null,
                    noticeOffset + rowStart, lines, gapPx)
                true
            }
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().then(matrixKeys).focusRequester(listFocus)
                .onFocusChanged { onGridFocused(it.isFocused) }.focusable(enabled = !sheetVisible).testTag("codex-grid"),
                state = grid, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp, start = 6.dp, end = 6.dp)) {
                if (emptyState != CodexEmptyState.NONE) item(key = "empty", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) {
                    EmptyNotice(emptyState, ui, dense = compactHeader)
                }
                fun heading(key: String, title: String, value: String? = null, info: String? = null, badge: String? = null) {
                    item(key = "heading/$key", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                        CodexSectionHeading(title, ui, value, info, badge)
                    }
                }
                fun cells(sectionEntries: List<CodexEntry>) {
                    items(sectionEntries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
                        CodexSlot(entry, catalog, ui, selection(), slotFocus, band = false, onSelection)
                    }
                }
                fun meters(sectionEntries: List<CodexEntry>) {
                    itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
                        span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, meterCells, index)) }) { _, entry ->
                        CodexMeterRow(entry, catalog, ui, selection(), slotFocus, describeMeters, onSelection)
                    }
                }
                fun pairings(sectionEntries: List<CodexEntry>, showKind: (CodexEntry) -> Boolean) {
                    itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
                        span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, pairingCells, index)) }) { _, entry ->
                        CodexPairingRow(entry, catalog, ui, selection(), slotFocus, showKind(entry), onSelection)
                    }
                }
                when (tab) {
                    CodexTab.BUILD -> if (build != null) {
                        // Form and weapon share one line; the run's Rebirth tier rides beside them in gold.
                        heading("loadout", language.text(CodexText.SECTION_LOADOUT),
                            badge = progress.rebirthProgress.level.takeIf { it > 0 }?.let { language.text(SessionText.REBIRTH_LEVEL, it) })
                        cells(entries.filter { it.icon is CodexIcon.Shape || it.icon is CodexIcon.Weapon })
                        heading("relics", language.text(SessionText.RELICS_TITLE), "${build.relics.size}/${catalog.relicPolicy.maxSlots}")
                        cells(entries.filter { it.icon is CodexIcon.Relic || it.icon == CodexIcon.Empty })
                        if (build.relics.isNotEmpty() || build.severedRelicIds.isNotEmpty()) {
                            heading("costs", language.text(CodexText.SECTION_COSTS))
                            item(key = "costs", contentType = CodexGridContentType.BLOCK, span = { GridItemSpan(maxLineSpan) }) {
                                CodexCostBlock(build, catalog, ui)
                            }
                        }
                        heading("items", language.text(SessionText.ITEMS))
                        cells(entries.filter { it.icon is CodexIcon.Item })
                        if (entries.none { it.icon is CodexIcon.Item }) item(key = "empty-inventory", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) { EmptyNotice(CodexEmptyState.EMPTY_INVENTORY, ui) }
                        entries.filter { it.icon is CodexIcon.Tag }.takeIf { it.isNotEmpty() }?.let { tags ->
                            heading("resonance", language.text(CodexText.SECTION_RESONANCE), info = language.text(CodexText.TAGS_RULE,
                                policy.resonanceThresholds[0], policy.resonanceThresholds[1], policy.resonanceThresholds[2]))
                            meters(tags)
                        }
                        entries.filter { it.icon is CodexIcon.Affinity }.takeIf { it.isNotEmpty() }?.let { affinity ->
                            heading("affinity", language.text(CodexText.SECTION_AFFINITY), info = language.text(CodexText.AFFINITY_RULE,
                                policy.affinityThresholds[0], policy.affinityThresholds[1], policy.affinityThresholds[2]))
                            meters(affinity)
                        }
                        entries.filter { it.icon is CodexIcon.Pairing }.takeIf { it.isNotEmpty() }?.let { links ->
                            heading("links", language.text(CodexText.SECTION_LINKS))
                            pairings(links) { true }
                        }
                        entries.filter { it.icon is CodexIcon.Reaction }.takeIf { it.isNotEmpty() }?.let { reactions ->
                            heading("reactions", language.text(CodexText.SECTION_REACTIONS), info = language.text(CodexText.REACTION_RULE))
                            item(key = "build-reactions", contentType = CodexGridContentType.CHIPS, span = { GridItemSpan(maxLineSpan) }) {
                                CodexReactionChips(reactions, ui, selection(), slotFocus, onSelection)
                            }
                        }
                        item(key = "stats-heading", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.padding(top = 12.dp)) {
                                CodexToggleButton(language.text(SessionRedesignText.STATS), statsExpanded(), "codex-stats", controlScale) { onStatsExpanded(!statsExpanded()) }
                            }
                        }
                        if (statsExpanded()) itemsIndexed(build.stats, key = { _, stat -> "stat/${stat.name}" }, contentType = { _, _ -> CodexGridContentType.STAT }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, stat ->
                            val (name, value) = statRows[index]
                            CodexStatRow(name, value, selectedStat() == stat.name, "codex-stat-${stat.name}", ui, statsFit, { onSelectedStat(if (selectedStat() == stat.name) null else stat.name) }) {
                                stat.contributions.forEach { contribution ->
                                    val name = when (contribution.source) {
                                        BuildStatSource.CHARACTER -> language.text(SessionText.CHARACTER_SOURCE)
                                        BuildStatSource.LAB -> language.text(SessionText.LAB_SOURCE)
                                        BuildStatSource.ITEMS -> language.text(SessionText.ITEMS_TITLE)
                                        BuildStatSource.RELICS -> language.text(SessionText.RELICS_TITLE)
                                        BuildStatSource.SYNERGIES -> language.text(CodexText.RESONANCE_SOURCE)
                                        BuildStatSource.MASTERY -> language.text(SessionText.MASTERY_SOURCE)
                                        BuildStatSource.TEMPORARY -> language.text(SessionText.TEMPORARY_SOURCE)
                                    }
                                    BasicText("$name  ${codexNumber(contribution.amount, language)}${stat.unit.localizedContent(language)}",
                                        style = typography.monoStyle(11f * ui, color = Kk.Bone2))
                                }
                            }
                        }
                    }
                    CodexTab.CATALOG -> if (itemGrid != null && labelFit != null) {
                        // The Items matrix: one lazy item per header, label, rule, spacer and cell, every line spanning the grid.
                        val parts = itemGrid.items
                        items(parts.size, key = { parts[it].key }, span = { GridItemSpan(parts[it].span) },
                            contentType = { parts[it].contentType }) { index ->
                            val part = parts[index]
                            val rowBand = previewRow >= 0 && part.rowIndex == previewRow
                            val stripe = if (previewColumn >= 0 && part.covers(previewColumn)) previewColumn - part.column else -1
                            // A line holding a side label is as tall as the label; its cells and blanks centre in it.
                            val lineDp = if (part.line in itemGrid.labelled) labelFit.height else itemGrid.geometry.cellDp
                            when (part) {
                                is CodexGridItem.Head -> CodexItemsHead(itemGrid.bands, part.span, gridHeight, previewColumn, previewRarity)
                                is CodexGridItem.Label -> if (part.stacked) CodexItemRowHeading(part.row, rowBand, ui)
                                    else CodexItemRowLabel(part.row, rowBand, labelFit, ui)
                                is CodexGridItem.Rule -> CodexItemRule()
                                is CodexGridItem.Spacer -> CodexItemSpacer(part.span, lineDp, rowBand, stripe)
                                is CodexGridItem.Cell ->
                                    CodexItemRowSlot(part.cell.entry, selection(), slotFocus, rowBand || stripe >= 0, lineDp, onSelection)
                            }
                        }
                    } else itemsIndexed(entries, key = { _, entry -> entry.key },
                        contentType = { _, _ -> CodexGridContentType.SLOT }) { index, entry ->
                        val band = previewIndex >= 0 && (index / columns == previewIndex / columns || index % columns == previewIndex % columns)
                        CodexSlot(entry, catalog, ui, selection(), slotFocus, band, onSelection)
                    }
                    CodexTab.REACTIONS -> item(key = "matrix", contentType = CodexGridContentType.MATRIX, span = { GridItemSpan(maxLineSpan) }) {
                        CodexReactionMatrix(entries, gridHeight, ui, selection(), slotFocus, residue, residueSource, onSelection)
                    }
                    CodexTab.PAIRINGS -> CodexPairingSection.entries.forEach { section ->
                        val sectionEntries = entries.filter { it.pairingSection() == section }
                        if (sectionEntries.isEmpty()) return@forEach
                        heading("pairings-$section", language.text(section.title), sectionEntries.size.toString())
                        // A section of one kind needs no kind tags: only rows of another kind keep theirs.
                        pairings(sectionEntries) { entry ->
                            when (section) {
                                CodexPairingSection.CONCORDS, CodexPairingSection.HARMONY -> false
                                CodexPairingSection.DISSONANCE -> (entry.icon as? CodexIcon.Pairing)?.kind?.named == true
                                CodexPairingSection.INCOMPATIBLE -> true
                            }
                        }
                    }
                    CodexTab.TAGS -> {
                        heading("resonance", language.text(CodexText.SECTION_RESONANCE), info = language.text(CodexText.TAGS_RULE,
                            policy.resonanceThresholds[0], policy.resonanceThresholds[1], policy.resonanceThresholds[2]))
                        meters(entries.filter { it.icon is CodexIcon.Tag })
                        heading("affinity", language.text(CodexText.SECTION_AFFINITY), info = language.text(CodexText.AFFINITY_RULE,
                            policy.affinityThresholds[0], policy.affinityThresholds[1], policy.affinityThresholds[2]))
                        meters(entries.filter { it.icon is CodexIcon.Affinity })
                        heading("keys", language.text(CodexText.SECTION_KEYS), info = language.text(CodexText.KEYS_RULE,
                            ((policy.harmonyScale - 1f) * 100f).roundToInt(), ((1f - policy.dissonanceScale) * 100f).roundToInt()))
                        item(key = "keys", contentType = CodexGridContentType.CHIPS, span = { GridItemSpan(maxLineSpan) }) {
                            val byKey = entries.associateBy { it.key }
                            CodexKeyChips(codexKeyGroups().map { group -> group.mapNotNull { byKey["key/$it"] } }, ui, selection(), slotFocus, onSelection)
                        }
                    }
                }
            }
        }
    }
}

private val CodexPairingSection.title: CodexText
    get() = when (this) {
        CodexPairingSection.CONCORDS -> CodexText.SECTION_CONCORDS
        CodexPairingSection.HARMONY -> CodexText.SECTION_HARMONY
        CodexPairingSection.DISSONANCE -> CodexText.SECTION_DISSONANCE
        CodexPairingSection.INCOMPATIBLE -> CodexText.SECTION_INCOMPATIBLE
    }
