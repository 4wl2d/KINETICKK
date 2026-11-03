// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min

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
            CodexHeader(tab, model, catalog, typography, ui, controlScale, availableWidth, wide, compactHeader, compactTabs, sheetVisible,
                collectionDiscovered, collectionTotal, onClose, ::selectTab)
            if (!compactHeader && !wide) CodexNavigationTabs(tab, controlScale, compactTabs, Modifier.fillMaxWidth().padding(top = 8.dp)) { selectTab(it) }
            Row(Modifier.padding(top = if (wide) 20.dp else 8.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (wide && tab == CodexTab.CATALOG) {
                    CodexCatalogNav(catalog, model, measurer, ui, controlScale, navWidth, categoryTitles, ::categoryCounts, category,
                        onCategory = { categoryValue = it }, search = search, sheetVisible = sheetVisible, searchFocus = searchFocus,
                        onSearch = { searchValue = codexSearchInput(it) }, filterTitles = filterTitles, filter = filter,
                        hasBuild = build != null, onFilter = { filterValue = it })
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val onSort: (CodexItemSort) -> Unit = { sortValue = it.ordinal }
                    // Wide screens: the Items matrix's sort heads the centre column, with its label where it fits.
                    if (wide && tab == CodexTab.CATALOG && category == 0) BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp)) {
                        CodexSortSegments(sort, controlScale, ui, maxWidth, withLabel = true, onSort)
                    }
                    if (tab == CodexTab.CATALOG && !wide) {
                        CodexPhoneControls(categoryTitles, category, onCategory = { categoryValue = it }, filterTitles = filterTitles,
                            filter = filter, hasBuild = build != null, onFilter = { filterValue = it }, search = search,
                            sheetVisible = sheetVisible, searchFocus = searchFocus, onSearch = { searchValue = codexSearchInput(it) },
                            sort = sort, onSort = onSort, controlScale = controlScale, ui = ui, compactHeader = compactHeader)
                    }
                    CodexGridPane(
                        Modifier.weight(1f).fillMaxWidth(), tab, category, entries, itemRows, model, catalog, progress, build, typography, ui,
                        controlScale, wide, compactHeader, sheetVisible, emptyState, labelNeed, labelFloor, refColumns, labelDensity,
                        labelFontScale, scrollHolder, slotFocus, listFocus,
                        selection = { selectionValue }, onSelection = onSelection,
                        gridFocused = { gridFocusedValue }, onGridFocused = { gridFocusedValue = it },
                        statsExpanded = { statsExpandedValue }, onStatsExpanded = { statsExpandedValue = it },
                        selectedStat = { statValue }, onSelectedStat = { statValue = it },
                        residue = residue, residueSource = residueSource,
                    )
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
            CodexDetailSheet(selected, catalog, ui, availableWidth, availableHeight, detailsScrollHolder, residue, ::setResidue,
                restoreFocus, onOpen)
        }
    }
    }
}

@Composable
internal fun codexMeasurer(): CanvasTextMeasurer = LocalCodexMeasurer.current ?: rememberKkCanvasMeasurer()
