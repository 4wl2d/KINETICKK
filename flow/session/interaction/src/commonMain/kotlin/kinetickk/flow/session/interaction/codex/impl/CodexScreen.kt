// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal val LocalCodexInputEnabled = staticCompositionLocalOf { true }
private val LocalCodexMeasurer = staticCompositionLocalOf<CanvasTextMeasurer?> { null }
private val SelectionSaver = listSaver<CodexSelection, Any>(
    save = { listOf(it.pinnedKey.orEmpty(), it.sheetOpen) },
    restore = { CodexSelection(pinnedKey = (it[0] as String).ifEmpty { null }, sheetOpen = it[1] as Boolean) },
)

/**
 * Factor for Codex UI text at the player's text size [textScale]: the design's reference size at
 * the default 125 % (0.8 at 100 %, 1.4 at 175 %). The Codex title is display type and ignores it.
 */
internal fun codexUiScale(textScale: Float): Float = textScale / 1.25f

/** All navigation, search, expansion and selection here belong to this local Compose lifetime. */
@Composable
internal fun CodexContent(catalog: UiCatalogSnapshot, model: CodexRenderModel, progress: HomeProgressProjection, scale: Float, onEntryViewed: (CollectionEntry) -> Unit = {}, onClose: () -> Unit) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    // UI text follows the text size with the design size at the default 125 %.
    val ui = codexUiScale(scale)
    val measurer = rememberKkCanvasMeasurer(ui)
    val typography = measurer.typography
    var tabValue by rememberSaveable { mutableIntStateOf((if (model.runStacks.build == null) CodexTab.CATALOG else CodexTab.BUILD).ordinal) }
    var categoryValue by rememberSaveable { mutableIntStateOf(0) }
    var searchValue by rememberSaveable { mutableStateOf("") }
    var filterValue by rememberSaveable { mutableIntStateOf(0) }
    // The Items matrix's rows: by tag (the default), element or rarity; each Codex opening starts by tag.
    var sortValue by rememberSaveable { mutableIntStateOf(CodexItemSort.TAG.ordinal) }
    var selectionValue by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(CodexSelection()) }
    var statsExpandedValue by rememberSaveable { mutableStateOf(false) }
    var statValue by rememberSaveable { mutableStateOf<String?>(null) }
    // The residue chip of a reaction lights the reactions it can feed next, while that reaction is
    // the one in view; choosing another entry or tab drops it.
    var residueValue by rememberSaveable { mutableStateOf<String?>(null) }
    var residueSourceValue by rememberSaveable { mutableStateOf<String?>(null) }
    val build = model.runStacks.build
    // A restored legacy discovery-only filter is equivalent to the collected catalog.
    val filter = CodexItemFilter.entries.getOrNull(filterValue)?.takeIf { it != CodexItemFilter.DISCOVERED } ?: CodexItemFilter.ALL
    val searchFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val slotFocus = remember { mutableMapOf<String, FocusRequester>() }
    var restoreRequestValue by remember { mutableIntStateOf(0) }
    var restoreTargetValue by remember { mutableStateOf<String?>(null) }
    val scrollHolder = rememberSaveableStateHolder()
    val detailsScrollHolder = rememberSaveableStateHolder()
    // Lazy grid content can run again before this composition rebuilds entries.
    // Capture its selectors with the entries so a new tab cannot render the previous tab's icons.
    val tab = CodexTab.entries.getOrElse(tabValue) { CodexTab.CATALOG }
    val category = categoryValue
    val search = searchValue
    val sort = CodexItemSort.entries.getOrElse(sortValue) { CodexItemSort.TAG }
    // The Items matrix (Catalog, Items) is laid out from its rows; its entries are their cells in reading order.
    val itemRows = remember(tab, category, search, filter, sort, model, catalog, progress, language) {
        if (tab == CodexTab.CATALOG && category == 0) codexItemRows(sort, search, filter, model, catalog, progress, language) else null
    }
    val entries = remember(tab, category, search, filter, sort, itemRows, model, catalog, progress, language) {
        itemRows?.entries ?: when (tab) {
            CodexTab.BUILD -> codexBuildEntries(model, catalog, progress, language)
            CodexTab.CATALOG -> codexCatalogEntries(category, search, filter, model, catalog, progress, language, sort)
            CodexTab.REACTIONS -> codexReactionEntries(model, catalog, language)
            CodexTab.PAIRINGS -> codexPairingEntries(model, catalog, language)
            CodexTab.TAGS -> codexTagsEntries(model, catalog, language)
        }
    }
    val residueElement = residueValue?.let { name -> StatusElement.entries.firstOrNull { it.name == name } }.takeIf { tab == CodexTab.REACTIONS }
    fun setResidue(element: StatusElement?, source: String?) {
        residueValue = element?.name
        residueSourceValue = source.takeIf { element != null }
    }
    fun selectTab(next: CodexTab) {
        if (next != tab) setResidue(null, null)
        tabValue = next.ordinal
    }
    // Entry the wide side panel shows while nothing is hovered, focused or pinned (never the catalog's).
    val lead = when (tab) {
        CodexTab.CATALOG -> null
        CodexTab.BUILD -> entries.firstOrNull { it.icon is CodexIcon.Relic } ?: entries.firstOrNull()
        CodexTab.REACTIONS -> entries.firstOrNull { (it.icon as? CodexIcon.Reaction)?.discovered == true } ?: entries.firstOrNull()
        CodexTab.PAIRINGS, CodexTab.TAGS -> entries.firstOrNull { it.active } ?: entries.firstOrNull()
    }
    LaunchedEffect(selectionValue.pinnedKey, selectionValue.sheetOpen) {
        val entry = entries.firstOrNull { it.key == selectionValue.pinnedKey }
        if (selectionValue.sheetOpen && entry?.isNew == true) {
            when (val icon = entry.icon) {
                is CodexIcon.Item -> onEntryViewed(CollectionEntry.Item(icon.definition.id))
                is CodexIcon.Relic -> onEntryViewed(CollectionEntry.Relic(icon.definition.id))
                else -> Unit
            }
        }
    }
    fun categoryCounts(index: Int): Pair<Int, Int> = when (index) {
        0 -> catalog.items.count { model.isDiscovered(it.id) } to catalog.items.size
        1 -> catalog.weapons.count { it.id in progress.loadout.unlockedWeapons } to catalog.weapons.size
        2 -> catalog.relics.count { model.isRelicDiscovered(it.id) } to catalog.relics.size
        3 -> progress.unlockedCoreShapes.size to catalog.coreShapes.size
        // Enemies the current Rebirth rank lets into runs (hidden secret bosses aside), out of the whole roster.
        CODEX_ENEMIES_CATEGORY -> codexEnemyCounts(model, catalog, progress)
        // Places and secrets found, out of every one the World lists.
        CODEX_WORLD_CATEGORY -> codexWorldCounts(model, catalog)
        else -> error("No Codex catalog category $index")
    }
    val (collectionDiscovered, collectionTotal) = categoryCounts(category)
    // Rebirth locks alone leave the collection empty: the notice shows above them (and above the Items matrix's hatches).
    val shown = if (itemRows != null) entries.count { it.icon is CodexIcon.Item } else entries.count { it.icon !is CodexIcon.Locked }
    // The Items matrix reads a search of spaces alone as none, so it shows no "No matches" over the open matrix.
    val shownSearch = when {
        tab != CodexTab.CATALOG -> ""
        itemRows != null -> codexItemQuery(search)
        else -> search
    }
    val emptyState = codexEmptyState(tab, build != null, shownSearch, shown)
    val refColumns = remember(model.items) { codexItemRefColumns(model.items) }
    val labelDensity = LocalDensity.current.density
    val labelFontScale = LocalDensity.current.fontScale
    val labelNeed = remember(measurer, language, labelDensity, ui) { codexItemLabelNeed(measurer, language, labelDensity, ui) }
    val labelFloor = remember(measurer, language, labelDensity, ui) {
        codexItemLabelNeed(measurer, language, labelDensity, ui, CODEX_ROW_NAME_FLOOR_FONT)
    }
    var gridFocusedValue by remember { mutableStateOf(false) }
    val keys = remember(entries) { entries.map { it.key }.toSet() }
    LaunchedEffect(keys) {
        val retained = selectionValue.retain(keys)
        if (selectionValue.sheetOpen && !retained.sheetOpen) {
            restoreTargetValue = selectionValue.pinnedKey
            restoreRequestValue++
        }
        selectionValue = retained
    }
    val restoreFocus: () -> Unit = {
        restoreTargetValue = selectionValue.pinnedKey
        selectionValue = selectionValue.closeSheet()
        restoreRequestValue++
    }
    LaunchedEffect(restoreRequestValue) {
        if (restoreRequestValue > 0) {
            withFrameNanos { }
            val target = slotFocus[restoreTargetValue] ?: if (tabValue == CodexTab.CATALOG.ordinal) searchFocus else listFocus
            target.requestFocus()
        }
    }
    LaunchedEffect(Unit) { listFocus.requestFocus() }
    val categoryTitles = listOf(SessionText.ITEMS_TITLE, SessionText.WEAPONS_TITLE, SessionText.RELICS_TITLE, SessionText.FORMS_TITLE)
        .map { language.text(it) } + language.text(CodexText.ENEMIES_TITLE) + language.text(CodexText.WORLD_TITLE)
    val filterTitles = listOf(0 to SessionText.FILTER_ALL, 2 to SessionText.FILTER_BUILD, 3 to SessionText.FILTER_ACTIVE)
    val policy = catalog.build.policy
    val onSelection: ((CodexSelection) -> CodexSelection) -> Unit = { change ->
        val next = change(selectionValue)
        // A residue belongs to the reaction it was read from.
        if (next.pinnedKey != selectionValue.pinnedKey) setResidue(null, null)
        selectionValue = next
    }
    val onOpen: (String) -> Unit = { key -> onSelection { it.activate(key).copy(hoverKey = null, focusKey = null) } }
    CompositionLocalProvider(LocalCodexMeasurer provides measurer) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Kk.Ink).testTag("codex")) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val wide = codexUsesSidePanel(availableWidth.value, availableHeight.value)
        val compactHeader = availableHeight < 480.dp
        val compactTabs = availableWidth.value < CODEX_COMPACT_TABS_DP
        val selected = entries.firstOrNull { it.key == selectionValue.pinnedKey }
        val sheetVisible = !wide && selectionValue.sheetOpen && selected != null
        val panelKey = selectionValue.previewKey ?: lead?.key?.takeIf { wide }
        val panelEntry = entries.firstOrNull { it.key == panelKey }
        val residueSource = residueSourceValue
        val residue = residueElement?.takeIf { residueSource != null && residueSource == panelKey }
        val side = if (wide) 44.dp else 12.dp
        val detailWidth = if (wide) min(332f, availableWidth.value * 0.3f).dp else 0.dp
        val navWidth = if (wide && tab == CodexTab.CATALOG) min(250f, availableWidth.value * 0.22f).dp else 0.dp
        // Controls grow with the text size, less so where height is scarce.
        val controlScale = max(1f, ui).let { if (compactHeader) min(it, 1.2f) else it }
        // Background: 48 dp grid and, on wide screens, the sheared ink-1 detail panel.
        Box(Modifier.fillMaxSize().drawBehind {
            drawKkGrid(Rect(Offset.Zero, size), Kk.Bone.copy(alpha = 0.045f), 48f)
            if (wide) {
                val left = size.width - side.toPx() - detailWidth.toPx() - 50.dp.toPx()
                withKkShear(size.height) {
                    drawRect(Kk.Ink1, Offset(left, 0f), Size(size.width - left + size.height, size.height))
                }
                withKkShear(size.height) {
                    drawRect(Kk.Line, Offset(left, 0f), Size(1.dp.toPx(), size.height))
                }
            }
        })
        CompositionLocalProvider(LocalCodexInputEnabled provides !sheetVisible) {
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = 12.dp).then(if (sheetVisible) Modifier.clearAndSetSemantics { } else Modifier).onPreviewKeyEvent {
            if (it.key == Key.Escape) {
                if (it.type == KeyEventType.KeyDown) onClose()
                true
            } else false
        }) {
            // Header: Back, title, the Codex tabs, then the tab's progress chip (Catalog: phones only) and rule (!).
            Row(Modifier.fillMaxWidth().heightIn(min = if (wide) 60.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                KkButton(language.text(SessionRedesignText.BACK), onClick = onClose, modifier = Modifier.testTag("codex-close"),
                    variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, enabled = !sheetVisible, textScale = ui)
                Spacer(Modifier.width(14.dp))
                if (!compactHeader) {
                    Box(Modifier.width(1.dp).height(28.dp).background(Kk.Line2))
                    Spacer(Modifier.width(14.dp))
                    val titleSize = if (wide) 44f else if (availableWidth < 600.dp) 26f else 32f
                    if (wide) BasicText(language.text(SessionText.CODEX).uppercase(), style = typography.condStyle(titleSize, color = Kk.Bone), softWrap = false, maxLines = 1)
                    // Phones: larger text grows the controls around the title, so it shrinks to fit whole.
                    else Box(Modifier.weight(1f)) {
                        CodexFitText(language.text(SessionText.CODEX), { size -> typography.condStyle(size, color = Kk.Bone) }, titleSize, 14f)
                    }
                    Spacer(Modifier.width(if (wide) 22.dp else 8.dp))
                }
                if (compactHeader || wide) CodexNavigationTabs(tab, controlScale, compactTabs, Modifier.weight(1f)) { selectTab(it) }
                when (tab) {
                    // Wide screens list every section's count in the left nav; the header repeats it only on phones.
                    CodexTab.CATALOG -> if (!wide) {
                        CodexChip("$collectionDiscovered/$collectionTotal", language.text(SessionText.COLLECTION_PROGRESS, collectionDiscovered, collectionTotal), ui,
                            Modifier.padding(start = 8.dp).testTag("codex-collection-progress"))
                    }
                    CodexTab.REACTIONS -> {
                        KkInfoButton(language.text(CodexText.REACTION_RULE), Modifier.padding(horizontal = 8.dp), placement = KkTooltipPlacement.BELOW, textScale = ui)
                        val found = ReactionId.entries.count(model::isReactionDiscovered)
                        CodexChip("$found/${ReactionId.entries.size}", language.text(CodexText.REACTIONS_PROGRESS, found, ReactionId.entries.size), ui,
                            Modifier.testTag("codex-reactions-progress"), label = if (wide) language.text(CodexText.REACTIONS_PROGRESS_LABEL) else null)
                    }
                    CodexTab.PAIRINGS -> {
                        KkInfoButton(language.text(CodexText.PAIRING_RULE, ((policy.harmonyScale - 1f) * 100f).roundToInt(), ((1f - policy.dissonanceScale) * 100f).roundToInt()),
                            Modifier.padding(horizontal = 8.dp), placement = KkTooltipPlacement.BELOW, textScale = ui)
                        val found = catalog.build.pairings.count { model.isPairingDiscovered(it.id) }
                        CodexChip("$found/${catalog.build.pairings.size}", language.text(CodexText.PAIRINGS_PROGRESS, found, catalog.build.pairings.size), ui,
                            Modifier.testTag("codex-pairings-progress"), label = if (wide) language.text(CodexText.PAIRINGS_PROGRESS_LABEL) else null)
                    }
                    // Tags: each section carries its own rule.
                    CodexTab.TAGS, CodexTab.BUILD -> Unit
                }
            }
            if (!compactHeader && !wide) CodexNavigationTabs(tab, controlScale, compactTabs, Modifier.fillMaxWidth().padding(top = 8.dp)) { selectTab(it) }
            Row(Modifier.padding(top = if (wide) 20.dp else 8.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (wide && tab == CodexTab.CATALOG) {
                    // Left nav: section rows with counts, Search, the game's filters, rarity legend.
                    val navScroll = rememberScrollState()
                    val navCounts = categoryTitles.indices.map { index -> categoryCounts(index).let { (found, total) -> "$found/$total" } }
                    val density = LocalDensity.current.density
                    // One title size for all section rows: the largest at which every title fits beside its count.
                    val navTitleSize = remember(measurer, categoryTitles, navCounts, navWidth, density) {
                        codexNavTitleSize(measurer, categoryTitles, navCounts, navWidth.value * density, density)
                    }
                    Box(Modifier.width(navWidth).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().verticalScroll(navScroll).testTag("codex-nav"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        categoryTitles.forEachIndexed { index, title ->
                            CodexNavRow(title, navCounts[index], navTitleSize, category == index, "codex-category-$index", controlScale) { categoryValue = index }
                        }
                        Spacer(Modifier.height(18.dp))
                        BasicText(language.text(SessionRedesignText.SEARCH).uppercase(), style = typography.labelStyle(13f * ui, color = Kk.Mute))
                        Spacer(Modifier.height(4.dp))
                        CodexSearchField(search, !sheetVisible, ui, inlineLabel = false, searchFocus) { searchValue = codexSearchInput(it) }
                        if (category == 0) {
                            Spacer(Modifier.height(10.dp))
                            CodexFilters(filterTitles, filter, build != null, controlScale, available = navWidth)
                                { filterValue = it }
                            Spacer(Modifier.height(18.dp))
                            BasicText(language.text(SessionRedesignText.RARITY).uppercase(), style = typography.labelStyle(13f * ui, color = Kk.Mute))
                            CodexRarityLegend(catalog, model, ui)
                        }
                    }
                    // Where the nav is taller than the screen, its cut edge fades out beside a scroll bar.
                    CodexScrollCue(navScroll, Kk.Ink, Kk.Bone, Modifier.matchParentSize().testTag("codex-nav-scroll-cue"))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val onSort: (CodexItemSort) -> Unit = { sortValue = it.ordinal }
                    // Wide screens: the Items matrix's sort heads the centre column, with its label where it fits.
                    if (wide && tab == CodexTab.CATALOG && category == 0) BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp)) {
                        CodexSortSegments(sort, controlScale, ui, maxWidth, withLabel = true, onSort)
                    }
                    if (tab == CodexTab.CATALOG && !wide) {
                        val trailingFilters = compactHeader && category == 0
                        // The filters, their 8 dp spacer and the tab row's two 6 dp gaps around that spacer.
                        val filtersWidth = if (trailingFilters) codexFiltersWidth(filterTitles.map { language.text(it.second) }) + 20.dp else 0.dp
                        CodexTabRow(categoryTitles, category, "codex-category-", controlScale, Modifier.fillMaxWidth(), reserved = filtersWidth,
                            trailing = {
                                if (trailingFilters) {
                                    Spacer(Modifier.width(8.dp))
                                    CodexFilters(filterTitles, filter, build != null, controlScale) { filterValue = it }
                                }
                            }) { categoryValue = it }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 8.dp))
                        // Landscape phones keep the search line dense: the sort (and its label, where it fits) sits beside
                        // the search, which keeps 200 dp.
                        if (compactHeader && category == 0) BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val sortWidth = maxWidth - 208.dp
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    CodexSearchField(search, !sheetVisible, ui, inlineLabel = true, searchFocus, dense = true) {
                                        searchValue = codexSearchInput(it)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                CodexSortSegments(sort, controlScale, ui, sortWidth, withLabel = true, onSort)
                            }
                        } else CodexSearchField(search, !sheetVisible, ui, inlineLabel = true, searchFocus, dense = compactHeader) {
                            searchValue = codexSearchInput(it)
                        }
                        if (category == 0 && !compactHeader) {
                            Spacer(Modifier.height(8.dp))
                            // The filters, then the sort, wrapping to a second line where both do not fit. The sort's
                            // label (where it fits) tells its segments from the filters' look-alike pills.
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val rowWidth = maxWidth
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CodexFilters(filterTitles, filter, build != null, controlScale, available = rowWidth) { filterValue = it }
                                    CodexSortSegments(sort, controlScale, ui, rowWidth, withLabel = true, onSort)
                                }
                            }
                        }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 10.dp))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                                entries.indexOfFirst { it.key == selectionValue.previewKey }
                            } else -1
                            // In the Items matrix: the previewed cell's row, and in the banded layout its column and rarity.
                            val previewPosition = itemGrid?.positions?.get(selectionValue.previewKey)
                            val previewRow = previewPosition?.rowIndex ?: -1
                            val previewColumn = if (itemGrid?.layout == CodexItemLayout.BANDED) previewPosition?.column ?: -1 else -1
                            val previewRarity = itemGrid?.bands?.firstOrNull { previewColumn >= it.column && previewColumn < it.column + it.width }?.rarity
                            val noticeOffset = if (emptyState != CodexEmptyState.NONE) 1 else 0
                            // Arrows, Home / End and Page Up / Page Down walk the matrix; at an edge the key goes on unconsumed.
                            val matrixKeys = Modifier.onPreviewKeyEvent { event ->
                                val matrix = itemGrid
                                if (matrix == null || sheetVisible || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val move = codexItemMove(event.key) ?: return@onPreviewKeyEvent false
                                val from = selectionValue.focusKey?.takeIf { it in matrix.positions }
                                val target = when {
                                    from != null -> codexItemNeighbour(matrix, from, move)
                                    gridFocusedValue -> selectionValue.pinnedKey?.takeIf { it in matrix.positions } ?: matrix.cells.firstOrNull()?.key
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
                                .onFocusChanged { gridFocusedValue = it.isFocused }.focusable(enabled = !sheetVisible).testTag("codex-grid"),
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
                                        CodexSlot(entry, catalog, ui, selectionValue, slotFocus, band = false, onSelection)
                                    }
                                }
                                fun meters(sectionEntries: List<CodexEntry>) {
                                    itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
                                        span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, meterCells, index)) }) { _, entry ->
                                        CodexMeterRow(entry, catalog, ui, selectionValue, slotFocus, describeMeters, onSelection)
                                    }
                                }
                                fun pairings(sectionEntries: List<CodexEntry>, showKind: (CodexEntry) -> Boolean) {
                                    itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
                                        span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, pairingCells, index)) }) { _, entry ->
                                        CodexPairingRow(entry, catalog, ui, selectionValue, slotFocus, showKind(entry), onSelection)
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
                                                CodexReactionChips(reactions, ui, selectionValue, slotFocus, onSelection)
                                            }
                                        }
                                        item(key = "stats-heading", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                                            Box(Modifier.padding(top = 12.dp)) {
                                                CodexToggleButton(language.text(SessionRedesignText.STATS), statsExpandedValue, "codex-stats", controlScale) { statsExpandedValue = !statsExpandedValue }
                                            }
                                        }
                                        if (statsExpandedValue) itemsIndexed(build.stats, key = { _, stat -> "stat/${stat.name}" }, contentType = { _, _ -> CodexGridContentType.STAT }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, stat ->
                                            val (name, value) = statRows[index]
                                            CodexStatRow(name, value, statValue == stat.name, "codex-stat-${stat.name}", ui, statsFit, { statValue = if (statValue == stat.name) null else stat.name }) {
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
                                                    CodexItemRowSlot(part.cell.entry, selectionValue, slotFocus, rowBand || stripe >= 0, lineDp, onSelection)
                                            }
                                        }
                                    } else itemsIndexed(entries, key = { _, entry -> entry.key },
                                        contentType = { _, _ -> CodexGridContentType.SLOT }) { index, entry ->
                                        val band = previewIndex >= 0 && (index / columns == previewIndex / columns || index % columns == previewIndex % columns)
                                        CodexSlot(entry, catalog, ui, selectionValue, slotFocus, band, onSelection)
                                    }
                                    CodexTab.REACTIONS -> item(key = "matrix", contentType = CodexGridContentType.MATRIX, span = { GridItemSpan(maxLineSpan) }) {
                                        CodexReactionMatrix(entries, gridHeight, ui, selectionValue, slotFocus, residue, residueSource, onSelection)
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
                                            CodexKeyChips(codexKeyGroups().map { group -> group.mapNotNull { byKey["key/$it"] } }, ui, selectionValue, slotFocus, onSelection)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide) Box(Modifier.width(detailWidth).fillMaxHeight().testTag("codex-side-panel")) {
                    detailsScrollHolder.SaveableStateProvider(panelKey ?: "placeholder") {
                        CodexDetails(panelEntry, catalog, ui, Modifier.fillMaxSize(), residue = residue,
                            onResidue = { setResidue(it, panelEntry?.key) }, onOpen = onOpen)
                    }
                }
            }
        }
        }
        if (sheetVisible) {
                val sheetFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { sheetFocus.requestFocus() }
                Box(Modifier.width(availableWidth).height(availableHeight).testTag("codex-sheet-modal")
                    .focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
                    .focusRequester(sheetFocus).onPreviewKeyEvent {
                    if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyDown) restoreFocus(); true } else false
                }.focusable()) {
                    Box(Modifier.fillMaxSize().background(Kk.Ink.copy(alpha = 0.82f)).testTag("codex-scrim")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = restoreFocus))
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(availableHeight * 0.85f).background(Kk.Ink2)
                        .drawBehind { drawRect(selected.tone(roles), Offset.Zero, Size(size.width, 3.dp.toPx())) }.testTag("codex-sheet")
                        .pointerInput(Unit) { detectTapGestures { } }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                            KkButton(language.text(SessionRedesignText.CLOSE), onClick = restoreFocus, modifier = Modifier.testTag("codex-sheet-close"),
                                variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, textScale = ui)
                        }
                        detailsScrollHolder.SaveableStateProvider(selected.key) {
                            // Reading a residue closes the sheet, so the chain it lights in the matrix is in view.
                            CodexDetails(selected, catalog, ui, Modifier.fillMaxWidth().weight(1f), panel = Kk.Ink3, face = Kk.Ink2,
                                residue = residue, onResidue = { element ->
                                    setResidue(element, selected.key)
                                    if (element != null) restoreFocus()
                                }, onOpen = onOpen)
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

@Composable
internal fun codexMeasurer(): CanvasTextMeasurer = LocalCodexMeasurer.current ?: rememberKkCanvasMeasurer()
