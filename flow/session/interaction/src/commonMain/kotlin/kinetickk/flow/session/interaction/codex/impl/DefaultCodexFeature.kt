// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.TextResource
import kinetickk.ball.content.api.localizedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.ProfileCollectionVisits
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.rank
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.flow.session.interaction.home.impl.formIcon
import kinetickk.foundation.design.*
import kinetickk.resource.audio.api.AudioService
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DefaultCodexFeature(
    private val profilePort: ProfileReadPort,
    private val collectionVisits: ProfileCollectionVisits,
    private val uiCatalog: UiCatalogSnapshot,
    audioService: AudioService,
) : CodexFeature {
    private val audioExecutor = SessionAudioExecutor(audioService)
    private val reducer = CodexReducer(uiCatalog.items)

    @Composable
    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
        var collectionValue by remember(profilePort) { mutableStateOf(profilePort.query(ProfileQuery.GetCollection)) }
        CodexContent(
            catalog = uiCatalog,
            model = reducer.renderModel(collectionValue, runStacks),
            progress = profilePort.query(ProfileQuery.GetHomeProgress),
            scale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale,
            onEntryViewed = { entry ->
                collectionVisits.markViewed(entry)
                collectionValue = profilePort.query(ProfileQuery.GetCollection)
            },
            onClose = {
                audioExecutor.play(SessionAudioCue.UI_CLICK)
                onOutput(CodexOutput.Back)
            },
        )
    }
}

private enum class CodexGridContentType {
    NOTICE, HEADING, SLOT, STAT, ROW, MATRIX, CHIPS, BLOCK, ITEM_HEAD, ITEM_LABEL, ITEM_RULE, ITEM_SPACER, ITEM_CELL,
}

/** Lazy content type of a part of the Items matrix. */
private val CodexGridItem.contentType: CodexGridContentType
    get() = when (this) {
        is CodexGridItem.Head -> CodexGridContentType.ITEM_HEAD
        is CodexGridItem.Label -> CodexGridContentType.ITEM_LABEL
        is CodexGridItem.Rule -> CodexGridContentType.ITEM_RULE
        is CodexGridItem.Spacer -> CodexGridContentType.ITEM_SPACER
        is CodexGridItem.Cell -> CodexGridContentType.ITEM_CELL
    }

/** The banded Items matrix needs a grid at least this tall (dp): below it its header would take a third of the grid and scroll away. */
private const val CODEX_ITEM_BANDED_MIN_GRID_DP = 300f

/**
 * A keyboard move in the Items matrix still to be brought into view: the cell keyed [target] at lazy index
 * [index] (scrolled to first when it is not composed, [scroll]) and its row's reveal ([rowStart], [lines], [gapPx]).
 * Compared by identity, so every key press is a new move even when it repeats the last one.
 */
private class CodexMatrixMove(
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
private suspend fun LazyGridState.revealCodexRow(rowStart: Int, targetKey: String, lines: Int, gapPx: Float) {
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
private fun codexItemMove(key: Key): CodexItemMove? = when (key) {
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

internal val LocalCodexInputEnabled = staticCompositionLocalOf { true }
private val LocalCodexMeasurer = staticCompositionLocalOf<CanvasTextMeasurer?> { null }
private val SelectionSaver = listSaver<CodexSelection, Any>(
    save = { listOf(it.pinnedKey.orEmpty(), it.sheetOpen) },
    restore = { CodexSelection(pinnedKey = (it[0] as String).ifEmpty { null }, sheetOpen = it[1] as Boolean) },
)

/**
 * Grid cell size and gap (the board's matrix cells, sized up to carry each entry's glyph), the
 * same in the collection and the Build tab. A cell holds a band at its top for a NEW stamp (left)
 * and a count badge (right) beside each other, and below it a glyph as large as a 64 dp cell's
 * centered glyph, so neither mark ever covers the glyph or makes it smaller.
 */
internal val CodexCell = 82.dp
private val CodexCellGap = 6.dp

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
    val keys = entries.map { it.key }.toSet()
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
                            val resultKey = entries.joinToString("|") { it.key }
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

/**
 * Span (grid cells) of row [index] in a line of rows at least [minCells] wide: the line is filled,
 * and the rows of one line differ by at most one cell.
 */
internal fun codexRowSpan(maxLineSpan: Int, minCells: Int, index: Int): Int {
    val perLine = (maxLineSpan / minCells.coerceAtLeast(1)).coerceIn(1, maxLineSpan.coerceAtLeast(1))
    val base = maxLineSpan / perLine
    return base + if (index % perLine < maxLineSpan - base * perLine) 1 else 0
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

@Composable
private fun CodexSlot(
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
    // An enemy's threat out of five.
    is CodexIcon.Enemy -> quantity
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
 * Display text that wraps only between words: shrinks from [maxSize] toward [minSize] until its
 * longest word fits the width (the words themselves are never split).
 */
@Composable
internal fun CodexFitText(text: String, style: (Float) -> TextStyle, maxSize: Float, minSize: Float, modifier: Modifier = Modifier) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer(8)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val size = remember(text, maxPx, maxSize, style(maxSize)) {
            codexFittingSize(text.uppercase(), maxPx, maxSize, minSize) { word, candidate ->
                textMeasurer.measure(word, style(candidate)).size.width
            }
        }
        KkLabel(text, style(size), modifier)
    }
}

/** Largest size in [minSize]..[maxSize] (1 px steps) at which every word of [text] is at most [maxPx] wide. */
internal fun codexFittingSize(text: String, maxPx: Int, maxSize: Float, minSize: Float, width: (String, Float) -> Int): Float {
    val words = text.split(' ').filter { it.isNotEmpty() }
    var candidate = maxSize
    while (candidate > minSize && words.any { word -> width(word, candidate) > maxPx }) candidate -= 1f
    return candidate.coerceAtLeast(minSize)
}

/** Height of the fade a scroll cue lays over the cut edge of a scrolling column. */
private val CodexScrollFade = 32.dp

/**
 * Scroll cue over a scrolling column, present only while it can scroll: the content fades into
 * [face] at each edge that has more, and a 3 dp bar ([accent] thumb on a 15 % track, like the
 * reward cards' scrolling body) shows the visible part. [barEnd] is the bar's distance from the
 * right edge (negative: outside it, in the gap beside the column).
 */
@Composable
private fun CodexScrollCue(scroll: ScrollState, face: Color, accent: Color, modifier: Modifier, barEnd: Dp = (-10).dp) {
    if (scroll.maxValue <= 0) return
    Box(modifier.drawBehind {
        // The range can shrink to zero (text size change) before recomposition removes the cue.
        val max = scroll.maxValue
        if (max <= 0) return@drawBehind
        val fade = min(CodexScrollFade.toPx(), size.height * 0.25f)
        if (scroll.canScrollForward) drawRect(Brush.verticalGradient(listOf(face.copy(alpha = 0f), face), startY = size.height - fade, endY = size.height),
            Offset(0f, size.height - fade), Size(size.width, fade))
        if (scroll.canScrollBackward) drawRect(Brush.verticalGradient(listOf(face, face.copy(alpha = 0f)), startY = 0f, endY = fade),
            Offset.Zero, Size(size.width, fade))
        val bar = 3.dp.toPx()
        val left = size.width - barEnd.toPx() - bar
        drawRect(accent.copy(alpha = 0.15f), Offset(left, 0f), Size(bar, size.height))
        val thumb = (size.height * scroll.viewportSize / (scroll.viewportSize + max).toFloat()).coerceIn(min(12.dp.toPx(), size.height), size.height)
        drawRect(accent, Offset(left, (size.height - thumb) * scroll.value.coerceIn(0, max) / max), Size(bar, thumb))
    })
}

@Composable
private fun CodexDetails(entry: CodexEntry?, catalog: UiCatalogSnapshot, ui: Float, modifier: Modifier, panel: Color = Kk.Ink2, face: Color = Kk.Ink1,
    residue: StatusElement? = null, onResidue: (StatusElement?) -> Unit = {}, onOpen: (String) -> Unit = {}) {
    val roles = LocalKkRolePalette.current
    val scroll = rememberScrollState()
    Box(modifier) {
        CodexDetailsBody(entry, catalog, ui, Modifier.fillMaxSize().testTag("codex-details-scroll").verticalScroll(scroll), panel, residue, onResidue, onOpen)
        // Content below the fold fades out over the panel beside a scroll bar, never cut bare.
        CodexScrollCue(scroll, face, entry?.tone(roles) ?: Kk.Bone, Modifier.matchParentSize().testTag("codex-details-scroll-cue"), barEnd = 6.dp)
    }
}

@Composable
private fun CodexDetailsBody(entry: CodexEntry?, catalog: UiCatalogSnapshot, ui: Float, modifier: Modifier, panel: Color,
    residue: StatusElement?, onResidue: (StatusElement?) -> Unit, onOpen: (String) -> Unit) {
    val roles = LocalKkRolePalette.current
    Column(modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (entry == null) {
            Box(Modifier.fillMaxWidth().height(120.dp).kkHatch())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = entry.icon
                when {
                    entry.kind == CODEX_NONE -> Unit
                    // Rarity is a material: the tag is its band, as the reward card names an item's rarity (and a relic's aspect) on it.
                    icon is CodexIcon.Item || icon is CodexIcon.Relic -> CodexMaterialTag(entry.kind, requireNotNull(icon.rarityMaterial()), ui, height = 26.dp)
                    icon is CodexIcon.Break || icon is CodexIcon.Affinity -> CodexTag(entry.kind, entry.tone(roles), Kk.Ink, ui, height = 26.dp)
                    icon is CodexIcon.Pairing || icon is CodexIcon.Conflict -> CodexTag(entry.kind, if (icon.interferes()) roles.threat else Kk.Bone, Kk.Ink, ui, height = 26.dp)
                    else -> CodexTag(entry.kind, Color.Transparent, Kk.Bone, ui, line = true, height = 26.dp)
                }
                entry.help?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
            }
            CodexDetailTitle(entry.title, if (entry.discovered) Kk.Bone else Kk.Mute, ui)
            CodexDetailBody(entry, catalog, ui, panel, residue, onResidue, onOpen)
            // Facts: a label and its value (Lvl N, counts, status words); explanations sit behind (!).
            if (entry.facts.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("codex-facts"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.facts.forEach { fact -> CodexFactRow(fact, ui) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun CodexIcon.interferes(): Boolean = when (this) {
    is CodexIcon.Pairing -> kind.interferes
    is CodexIcon.Conflict -> true
    else -> false
}

/**
 * A fact row: mono label (mute) on the left, cond value right-aligned after it, then its (!) if
 * any. A long value (two relic names) wraps between words instead of running out of the row; a
 * number keeps the word before it. When a word of the value would not fit beside the label, the
 * value goes under the label (right-aligned), so no word is ever broken.
 */
@Composable
private fun CodexFactRow(fact: CodexFact, ui: Float) {
    val typography = codexMeasurer().typography
    val label = fact.label.uppercase()
    val value = codexGlueNumbers(fact.value.uppercase())
    val labelStyle = typography.monoStyle(11f * ui, color = Kk.Mute)
    val valueStyle = typography.condStyle(21f * ui, tabular = true, lineHeightEm = 1f, color = Kk.Bone)
        .copy(textAlign = androidx.compose.ui.text.style.TextAlign.End)
    BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag("codex-fact")) {
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer(8)
        val density = LocalDensity.current
        val gap = 10.dp
        val info = if (fact.info != null) CODEX_FACT_INFO_DP.dp + gap else 0.dp
        val stacked = remember(label, value, labelStyle, valueStyle, maxWidth, info, density) {
            with(density) {
                val labelWidth = textMeasurer.measure(label, labelStyle, softWrap = false).size.width
                val longestWord = value.split(' ').filter { it.isNotEmpty() }
                    .maxOfOrNull { textMeasurer.measure(it, valueStyle, softWrap = false).size.width } ?: 0
                labelWidth + gap.toPx() + longestWord + info.toPx() > maxWidth.toPx()
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(label, Modifier.weight(1f), style = labelStyle)
                fact.info?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
            }
            BasicText(value, Modifier.fillMaxWidth(), style = valueStyle)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
            BasicText(label, Modifier.alignByBaseline(), style = labelStyle, softWrap = false, maxLines = 1)
            BasicText(value, Modifier.weight(1f).alignByBaseline(), style = valueStyle)
            fact.info?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
        }
    }
}

/** The (!) button's width beside a fact (KkInfoButton). */
private const val CODEX_FACT_INFO_DP = 24f

/** [text] with each number bound to the word before it (a no-break space), so a line never starts with a lone number. */
internal fun codexGlueNumbers(text: String): String = CodexNumberAfterWord.replace(text) { match -> match.groupValues[1] + "\u00A0" }

private val CodexNumberAfterWord = Regex("(\\S) (?=[0-9])")

/** Wide detail title: shrinks (30 to 18 px) until its longest word fits, never breaking a word. */
@Composable
private fun CodexDetailTitle(title: String, color: Color, ui: Float) {
    val typography = codexMeasurer().typography
    CodexFitText(title, { size -> typography.wideStyle(size, lineHeightEm = 1.05f, color = color) }, 30f * ui, 18f,
        Modifier.testTag("codex-detail-title"))
}

/** How the label/value rows of one list lay out: each value [stacked] under its label or beside it, labels at [labelSize]. */
internal data class CodexPairFit(val stacked: Boolean, val labelSize: Float)

/**
 * The layout the label/value [rows] of one list share, in rows [widthPx] wide (content) with a
 * [gapPx] gap before the value: the value stays beside its label while every label's longest word
 * fits next to that label's value; otherwise every row puts its value under the label, and only a
 * word still wider than the row shrinks the labels (together, from [labelSize] toward
 * [minLabelSize]). Words are never broken, and all labels of the list keep one size.
 */
internal fun codexPairFit(
    rows: List<Pair<String, String>>,
    widthPx: Float,
    gapPx: Float,
    labelSize: Float,
    minLabelSize: Float,
    wordWidth: (String, Float) -> Float,
    valueWidth: (String) -> Float,
): CodexPairFit {
    fun longestWord(label: String) = label.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { wordWidth(it, labelSize) } ?: 0f
    if (rows.all { (label, value) -> longestWord(label) + gapPx + valueWidth(value) <= widthPx }) return CodexPairFit(false, labelSize)
    val size = codexFittingSize(rows.joinToString(" ") { it.first }, widthPx.toInt(), labelSize, minLabelSize) { word, candidate ->
        kotlin.math.ceil(wordWidth(word, candidate)).toInt()
    }
    return CodexPairFit(true, size)
}

/** [codexPairFit] for [rows] [width] wide, measured with the real fonts: labels in [labelStyle] from [labelSize], values in [valueStyle]. */
@Composable
private fun rememberCodexPairFit(rows: List<Pair<String, String>>, width: Dp, gap: Dp, labelSize: Float, valueStyle: TextStyle,
    labelStyle: (Float) -> TextStyle): CodexPairFit {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer(16)
    val density = LocalDensity.current
    return remember(rows, width, gap, labelSize, valueStyle, density, textMeasurer) {
        codexPairFit(rows, with(density) { width.toPx() }, with(density) { gap.toPx() }, labelSize, labelSize * 0.6f,
            { word, size -> textMeasurer.measure(word, labelStyle(size), softWrap = false).size.width.toFloat() },
            { value -> textMeasurer.measure(value, valueStyle, softWrap = false).size.width.toFloat() })
    }
}

/** Display text in its role's uppercase with the original wording kept for semantics. */
@Composable
internal fun KkLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, style = style)
}

/** Tag plate (`.tag`): face [background] (transparent + [line] = outlined), cond 800 label. */
@Composable
internal fun CodexTag(text: String, background: Color, foreground: Color, ui: Float, line: Boolean = false, height: Dp = 22.dp) {
    if (text.isBlank() || text == CODEX_NONE) return
    val typography = codexMeasurer().typography
    val size = if (height > 22.dp) 15f else 13f
    BasicText(
        text.uppercase(),
        Modifier.heightIn(min = height).drawBehind {
            val cut = 6.dp.toPx()
            if (background.alpha > 0f) drawKkSlab(Rect(Offset.Zero, this.size), background, cut)
            if (line) drawKkSlab(Rect(Offset(0.75f, 0.75f), Size(this.size.width - 1.5f, this.size.height - 1.5f)), Kk.Line2, cut, style = kkStroke(1.5.dp.toPx()))
        }.padding(horizontal = 10.dp, vertical = 4.dp),
        style = typography.labelStyle(size * ui, trackingEm = 0.1f, color = foreground),
        softWrap = false, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/**
 * A tag on [material]'s band (the band a card names its rarity on), its label in the material's own
 * readable colour; on the prismatic glass the label carries the whole spectrum across the word.
 */
@Composable
private fun CodexMaterialTag(text: String, material: KkCardMaterial, ui: Float, height: Dp = 22.dp) {
    if (text.isBlank() || text == CODEX_NONE) return
    val roles = LocalKkRolePalette.current
    val frame = roles.frame(material)
    val typography = codexMeasurer().typography
    val style = typography.labelStyle((if (height > 22.dp) 15f else 13f) * ui, trackingEm = 0.1f, color = frame.label[0])
    val spectrum = remember(frame) { if (frame.prism != null) Brush.horizontalGradient(frame.label) else null }
    BasicText(
        text.uppercase(),
        Modifier.testTag("codex-rarity-tag").heightIn(min = height).drawBehind { drawKkRarityBand(frame, cut = 6.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = if (spectrum != null) style.copy(brush = spectrum) else style,
        softWrap = false, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
private fun CodexChip(value: String, description: String, ui: Float, modifier: Modifier, label: String? = null) {
    val typography = codexMeasurer().typography
    Row(modifier.clearAndSetSemantics { contentDescription = description }
        .drawBehind { drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 8.dp.toPx()) }
        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let { BasicText(it.uppercase(), style = typography.monoStyle(10f * ui, color = Kk.Mute), softWrap = false, maxLines = 1) }
        BasicText(value, style = typography.condStyle(21f * ui, tabular = true, lineHeightEm = 1f, color = Kk.Bone), softWrap = false, maxLines = 1)
    }
}

@Composable
private fun EmptyNotice(state: CodexEmptyState, ui: Float, dense: Boolean = false) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val (title, description) = when (state) {
        CodexEmptyState.NO_RUN -> language.text(SessionText.NO_RUN) to language.text(SessionText.NO_RUN_HELP)
        CodexEmptyState.EMPTY_SEARCH -> language.text(SessionText.NO_MATCHES) to language.text(SessionText.NO_MATCHES_HELP)
        CodexEmptyState.NO_PAIRINGS -> language.text(CodexText.NO_PAIRINGS) to language.text(CodexText.NO_PAIRINGS_HELP)
        else -> language.text(SessionText.EMPTY_INVENTORY) to language.text(SessionText.EMPTY_INVENTORY_HELP)
    }
    // Where height is scarce (landscape phones) the notice keeps to a slim line above the content.
    Row(Modifier.fillMaxWidth().background(Kk.Ink2).kkHatch().padding(if (dense) 10.dp else 20.dp).testTag("codex-empty-${state.name}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title.uppercase(), Modifier.weight(1f, fill = false), style = typography.condStyle((if (dense) 20f else 26f) * ui, color = Kk.Bone))
        KkInfoButton(description, placement = KkTooltipPlacement.BELOW, textScale = ui)
    }
}

/** Full title, short label and glyph of each Codex tab. */
private val CodexTab.title: TextResource
    get() = when (this) {
        CodexTab.BUILD -> SessionText.TAB_BUILD
        CodexTab.CATALOG -> SessionText.TAB_CATALOG
        CodexTab.REACTIONS -> CodexText.TAB_REACTIONS
        CodexTab.PAIRINGS -> CodexText.TAB_PAIRINGS
        CodexTab.TAGS -> CodexText.TAB_TAGS
    }

private val CodexTab.short: TextResource
    get() = when (this) {
        CodexTab.BUILD -> CodexText.TAB_BUILD_SHORT
        CodexTab.CATALOG -> CodexText.TAB_CATALOG_SHORT
        CodexTab.REACTIONS -> CodexText.TAB_REACTIONS_SHORT
        CodexTab.PAIRINGS -> CodexText.TAB_PAIRINGS_SHORT
        CodexTab.TAGS -> CodexText.TAB_TAGS_SHORT
    }

private val CodexTab.icon: KkIcon
    get() = when (this) {
        CodexTab.BUILD -> KkIcon.FORMS_CIRCLE
        CodexTab.CATALOG -> KkIcon.SYSTEM_DATA
        CodexTab.REACTIONS -> KkIcon.SYSTEM_ANOMALY
        CodexTab.PAIRINGS -> KkIcon.SYSTEM_POLARITY
        CodexTab.TAGS -> KkIcon.SYSTEM_KEY
    }

/** The Codex tabs: worded tabs, or (below 600 dp) a glyph over a short label. */
@Composable
private fun CodexNavigationTabs(tab: CodexTab, controlScale: Float, compact: Boolean, modifier: Modifier, onSelect: (CodexTab) -> Unit) {
    val language = LocalAppLanguage.current
    if (!compact) {
        val titles = CodexTab.entries.map { language.text(it.title) }
        CodexTabRow(titles, tab.ordinal, "codex-tab-", controlScale, modifier) { onSelect(CodexTab.entries[it]) }
        return
    }
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    val gap = 4.dp
    BoxWithConstraints(modifier) {
        val shorts = CodexTab.entries.map { language.text(it.short) }
        val natural = remember(measurer, shorts, density) { shorts.map { codexGlyphTabWidth(measurer, it, density) / density } }
        val available = maxWidth.value - gap.value * (shorts.size - 1)
        val tabScale = codexTabScale(natural, available)
        val scrolls = natural.sum() * tabScale > available + 0.5f
        val scroll = rememberScrollState()
        Row(Modifier.testTag("codex-tab-row").then(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            CodexTab.entries.forEachIndexed { index, entry ->
                CodexGlyphTab(language.text(entry.title), shorts[index], entry.icon, entry == tab, "codex-tab-$index", controlScale, tabScale) { onSelect(entry) }
            }
        }
    }
}

/**
 * Smallest scale tabs shrink to before their row scrolls instead (their labels stay near 14 px): the six
 * categories fit every phone up to 150 %; at 175 % a 360 or 390 dp phone scrolls them (English fits 390 dp).
 */
internal const val CODEX_TAB_MIN_SCALE = 0.45f

/** Common scale for a row of tabs of [natural] widths (dp) so they fit [available] dp (1 when they fit). */
internal fun codexTabScale(natural: List<Float>, available: Float): Float =
    if (natural.isEmpty()) 1f else (available / natural.sum()).coerceIn(CODEX_TAB_MIN_SCALE, 1f)

/**
 * A row of tabs that fits its width: all tabs shrink together (to 55 %) so none is clipped at
 * rest. Only if that is still too wide does the row scroll, with a fade at the edge that has more.
 */
@Composable
private fun CodexTabRow(
    titles: List<String>,
    selected: Int,
    tagPrefix: String,
    controlScale: Float,
    modifier: Modifier,
    reserved: Dp = 0.dp,
    trailing: @Composable () -> Unit = {},
    onSelect: (Int) -> Unit,
) {
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    val gap = 6.dp
    BoxWithConstraints(modifier) {
        val natural = remember(measurer, titles, density) { titles.map { kkTabWidth(measurer, it, density) / density } }
        val available = maxWidth.value - reserved.value - gap.value * (titles.size - 1).coerceAtLeast(0)
        val tabScale = codexTabScale(natural, available)
        val scrolls = natural.sum() * tabScale > available + 0.5f
        val scroll = rememberScrollState()
        Row(
            Modifier.testTag("${tagPrefix}row").then(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier)
                .drawWithContent {
                    drawContent()
                    val fade = 28.dp.toPx()
                    if (scrolls && scroll.canScrollForward) {
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Kk.Ink), startX = size.width - fade, endX = size.width),
                            Offset(size.width - fade, 0f), Size(fade, size.height))
                    }
                    if (scrolls && scroll.canScrollBackward) {
                        drawRect(Brush.horizontalGradient(listOf(Kk.Ink, Color.Transparent), startX = 0f, endX = fade), Offset.Zero, Size(fade, size.height))
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.forEachIndexed { index, title ->
                CodexTab(title, selected == index, "$tagPrefix$index", controlScale, tabScale) { onSelect(index) }
            }
            trailing()
        }
    }
}

/** Tab (`.tab`), drawn at [tabScale]: uppercase; semantics keep the game's wording, role Tab and selection. */
@Composable
private fun CodexTab(text: String, selected: Boolean, tag: String, controlScale: Float, tabScale: Float = 1f, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val density = LocalDensity.current.density
    val width = remember(measurer, text, density) { kkTabWidth(measurer, text, density) / density }
    val height = 40f * controlScale
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, role = Role.Tab, interactionSource = interactions, indication = null, onClick = onClick)
        .size((width * tabScale).dp, (height * tabScale + 8f).dp)
        .drawBehind {
            withTransform({ scale(tabScale, tabScale, Offset.Zero) }) {
                drawKkTab(measurer, Rect(0f, 0f, width * density, height * density), text, selected, hovered && inputEnabled, focused && inputEnabled)
            }
        })
}

/** Natural width of the filter segments (plus their 3 dp gaps). */
@Composable
private fun codexFiltersWidth(labels: List<String>): Dp {
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    return remember(measurer, labels, density) {
        (labels.sumOf { (kkSegmentWidth(measurer, it, density) / density).toDouble() }.toFloat() + 3f * (labels.size - 1)).dp
    }
}

/** Title size (px at the design size, before the text-size factor) of the collection's section rows. */
private const val CODEX_NAV_TITLE = 24f
private const val CODEX_NAV_TITLE_MIN = 12f

/**
 * One title size for the collection's section rows: the largest (0.5 px steps from 24 down to 12)
 * at which every title of [titles] fits its row beside its count of [counts], laid out as
 * [drawKkListRow] does in a row [rowPx] wide (18 dp padding each side, a 14 dp gap before the count).
 * The titles shrink together, so the rows keep one size and none is cut.
 */
internal fun codexNavTitleSize(measurer: CanvasTextMeasurer, titles: List<String>, counts: List<String>, rowPx: Float, density: Float): Float {
    val available = titles.indices.map { index ->
        rowPx - 36f * density - measureKkText(measurer, counts[index], measurer.typography.monoStyle(), uppercase = true).size.width - 14f * density
    }
    fun fits(size: Float) = titles.indices.all { index ->
        measureKkText(measurer, titles[index], measurer.typography.condStyle(size), uppercase = true).size.width <= available[index]
    }
    var size = CODEX_NAV_TITLE
    while (size > CODEX_NAV_TITLE_MIN && !fits(size)) size -= 0.5f
    return size
}

/**
 * Section row height (dp) for the [controlScale]: 46 dp at the design size, growing far more slowly
 * than the text so the whole nav (six rows, search, filters, legend) fits a 810 dp tall window up to
 * 150 %; at 175 % it runs less than one row past and scrolls with its cue.
 */
internal fun codexNavRowHeight(controlScale: Float): Float = 46f + 2f * (controlScale - 1f)

/** Section row (`.lrow`) with a mono count, as a tab of the collection, its title at the shared [titleSize]. */
@Composable
private fun CodexNavRow(text: String, count: String, titleSize: Float, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val selection = if (selected) 1f else 0f
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, role = Role.Tab, interactionSource = interactions, indication = null, onClick = onClick)
        .fillMaxWidth().height(codexNavRowHeight(controlScale).dp)
        .drawBehind {
            drawKkListRow(measurer, Rect(Offset.Zero, size), text, count, selection, hovered || focused, titleSize = titleSize)
            if (focused) {
                // Around the drawn slab, which a selected row shifts left.
                val ring = codexNavFocusRing(size, selection, density)
                drawRect(Kk.Bone, ring.topLeft, ring.size, style = kkStroke(2.dp.toPx()))
            }
        })
}

/** Focus ring of a nav row of [size] px: 3 dp outside its slab, following the selected shift. */
internal fun codexNavFocusRing(size: Size, selection: Float, density: Float): Rect {
    val shift = KK_LIST_ROW_SELECTED_SHIFT_DP * density * selection.coerceIn(0f, 1.2f)
    val out = 3f * density
    return Rect(shift - out, -out, size.width + shift + out, size.height + out)
}

/**
 * The game's filters (All / In build / Active) as a segmented control; In build waits for a run. Like the sort, the
 * segments shrink together to fit [available] dp ([codexSortFit]), so none is cut at the column's edge.
 */
@Composable
private fun CodexFilters(
    filters: List<Pair<Int, SessionText>>,
    filter: CodexItemFilter,
    hasBuild: Boolean,
    controlScale: Float,
    available: Dp = Dp.Infinity,
    onSelect: (Int) -> Unit,
) {
    val language = LocalAppLanguage.current
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    val titles = filters.map { language.text(it.second) }
    val natural = remember(measurer, titles, density) { titles.map { kkSegmentWidth(measurer, it, density) / density } }
    val scale = if (available == Dp.Infinity) 1f else codexSortFit(natural, null, available.value).scale
    Row(horizontalArrangement = Arrangement.spacedBy(CODEX_SORT_GAP_DP.dp)) {
        filters.forEachIndexed { position, (index, _) ->
            CodexSegment(titles[position], filter.ordinal == index, index != 2 || hasBuild, "codex-filter-$index", controlScale, scale) { onSelect(index) }
        }
    }
}

/** Segment (`.segctl` cell), drawn at [scale] of its natural size (a row of segments shrinks together to fit). */
@Composable
private fun CodexSegment(text: String, selected: Boolean, enabled: Boolean, tag: String, controlScale: Float, scale: Float = 1f, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = enabled && LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val density = LocalDensity.current.density
    val width = remember(measurer, text, density) { kkSegmentWidth(measurer, text, density) / density }
    val height = 34f * controlScale
    val factor = scale
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, onClick = onClick)
        .size((width * factor).dp, (height * factor).dp)
        .drawBehind {
            val alpha = if (enabled) 1f else 0.4f
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
            withTransform({ scale(factor, factor, Offset.Zero) }) {
                drawKkSegment(measurer, Rect(0f, 0f, width * density, height * density), text, selected, hovered && inputEnabled, focused && inputEnabled)
            }
            drawContext.canvas.restore()
        })
}

/** How the Items sort control fits its width: with its visible label or not, at [scale] of its natural size. */
internal data class CodexSortFit(val label: Boolean, val scale: Float)

/** Gap (dp) between the sort segments (and between the filters). */
private const val CODEX_SORT_GAP_DP = 3f

/**
 * The fit of segments of [natural] widths (dp), [CODEX_SORT_GAP_DP] apart, in [available] dp: at their
 * natural size after a visible label of [labelWidth] dp (label and its gap) when one is wanted and
 * fits, else without it, else all shrunk together ([codexTabScale], down to [CODEX_TAB_MIN_SCALE]).
 * The segments never scroll.
 */
internal fun codexSortFit(natural: List<Float>, labelWidth: Float?, available: Float): CodexSortFit {
    val gaps = CODEX_SORT_GAP_DP * (natural.size - 1).coerceAtLeast(0)
    val width = natural.sum() + gaps
    return when {
        labelWidth != null && labelWidth + width <= available + 0.5f -> CodexSortFit(true, 1f)
        width <= available + 0.5f -> CodexSortFit(false, 1f)
        else -> CodexSortFit(false, codexTabScale(natural, available - gaps))
    }
}

/** The visible name of an Items sort. */
private val CodexItemSort.title: TextResource
    get() = when (this) {
        CodexItemSort.TAG -> CodexText.SORT_TAG
        CodexItemSort.ELEMENT -> CodexText.SORT_ELEMENT
        CodexItemSort.RARITY -> SessionRedesignText.RARITY
    }

/**
 * The Items matrix's sort: Tag, Element and Rarity segments ('codex-sort-0..2') in [available] dp,
 * after a visible "Sort" label where [withLabel] wants one and it fits (see [codexSortFit]). The control
 * announces its purpose whether or not the label shows.
 */
@Composable
private fun CodexSortSegments(sort: CodexItemSort, controlScale: Float, ui: Float, available: Dp, withLabel: Boolean, onSelect: (CodexItemSort) -> Unit) {
    val language = LocalAppLanguage.current
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    val titles = CodexItemSort.entries.map { language.text(it.title) }
    val heading = language.text(CodexText.SORT)
    val natural = remember(measurer, titles, density) { titles.map { kkSegmentWidth(measurer, it, density) / density } }
    val labelWidth = remember(measurer, heading, density) {
        measureKkText(measurer, heading, measurer.typography.labelStyle(13f), uppercase = true).size.width / density + 10f
    }
    val fit = codexSortFit(natural, labelWidth.takeIf { withLabel }, available.value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fit.label) {
            // Silent: the segments' container announces the sort.
            BasicText(heading.uppercase(), Modifier.clearAndSetSemantics { testTag = "codex-sort-label" },
                style = measurer.typography.labelStyle(13f * ui, color = Kk.Mute), softWrap = false, maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        Row(Modifier.testTag("codex-sort").semantics { contentDescription = heading }, horizontalArrangement = Arrangement.spacedBy(CODEX_SORT_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CodexItemSort.entries.forEachIndexed { index, entry ->
                CodexSegment(titles[index], entry == sort, true, "codex-sort-$index", controlScale, fit.scale) { onSelect(entry) }
            }
        }
    }
}

/** Toggle button for the effective stats list (bone slab when expanded). */
@Composable
private fun CodexToggleButton(text: String, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val density = LocalDensity.current.density
    val width = remember(measurer, text, density) { kkTabWidth(measurer, text, density) / density + 28f }
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, onClick = onClick)
        .size(width.dp, (40f * controlScale).dp)
        .drawBehind {
            val tabWidth = size.width - 28.dp.toPx()
            drawKkTab(measurer, Rect(0f, 0f, tabWidth, size.height), text, selected, hovered && inputEnabled, focused && inputEnabled)
            // Expanded state as a plus / minus mark (no chevrons).
            val cx = size.width - 12.dp.toPx()
            val cy = size.height * 0.5f
            val half = 5.dp.toPx()
            drawLine(Kk.Bone, Offset(cx - half, cy), Offset(cx + half, cy), 2.dp.toPx())
            if (!selected) drawLine(Kk.Bone, Offset(cx, cy - half), Offset(cx, cy + half), 2.dp.toPx())
        })
}

/** A Build-tab stat row: name left, value right, or the value under the name when [fit] is stacked. */
@Composable
private fun CodexStatRow(name: String, value: String, selected: Boolean, tag: String, ui: Float, fit: CodexPairFit, onClick: () -> Unit, contributions: @Composable () -> Unit) {
    val typography = codexMeasurer().typography
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth().background(if (selected) Kk.Ink3 else Kk.Ink2)) {
        val row = Modifier.fillMaxWidth().testTag(tag).semantics { this.selected = selected }
            .hoverable(interactions, enabled = inputEnabled)
            .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind { if (hovered || focused) drawRect(Kk.Bone.copy(alpha = 0.06f)) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
        val nameStyle = typography.bodyStyle(fit.labelSize, color = Kk.Bone)
        val valueStyle = typography.wideStyle(20f * ui, tabular = true, color = Kk.Bone)
        if (fit.stacked) Column(row, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(name, style = nameStyle)
            BasicText(value, Modifier.align(Alignment.End), style = valueStyle)
        } else Row(row, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(name, Modifier.weight(1f), style = nameStyle)
            BasicText(value, style = valueStyle)
        }
        if (selected) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contributions() }
    }
}

@Composable
private fun CodexSearchField(value: String, enabled: Boolean, ui: Float, inlineLabel: Boolean, focusRequester: FocusRequester, dense: Boolean = false, onValueChange: (String) -> Unit) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    var focusedValue by remember { mutableStateOf(false) }
    BasicTextField(enabled = enabled, value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = typography.bodyStyle(15f * ui, FontWeight.Medium, color = Kk.Bone), cursorBrush = SolidColor(roles.you),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focusedValue = it.isFocused }
            .testTag("codex-search").semantics { contentDescription = language.text(SessionText.SEARCH_DESCRIPTION) }
            .drawBehind {
                drawKkField(Rect(Offset.Zero, size), focusedValue, roles)
                val center = Offset(20.dp.toPx(), size.height * 0.5f - 1.dp.toPx())
                val r = 5.5.dp.toPx()
                drawCircle(Kk.Mute, r, center, style = kkStroke(1.5.dp.toPx()))
                drawLine(Kk.Mute, Offset(center.x + r * 0.7f, center.y + r * 0.7f), Offset(center.x + r * 1.6f, center.y + r * 1.6f), 1.5.dp.toPx())
            }
            .padding(start = 36.dp, end = 12.dp, top = if (dense) 7.dp else 11.dp, bottom = if (dense) 7.dp else 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && inlineLabel) BasicText(language.text(SessionRedesignText.SEARCH).uppercase(),
                    style = typography.labelStyle(14f * ui, color = Kk.Mute2))
                inner()
            }
        })
}

@Composable
private fun CodexRarityLegend(catalog: UiCatalogSnapshot, model: CodexRenderModel, ui: Float) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val counts = remember(catalog, model) {
        IntArray(ItemRarity.entries.size).also { counts ->
            catalog.items.forEach { item -> if (model.isDiscovered(item.id)) counts[item.rarity.ordinal]++ }
        }
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemRarity.entries.forEach { rarity ->
            LegendRow(rarity.displayLabel.localizedContent(language), counts[rarity.ordinal], rarity.rank, Kk.Bone, typography, ui)
        }
        LegendRow(language.text(SessionText.UNDISCOVERED), catalog.items.size - counts.sum(), null, Kk.Mute, typography, ui)
    }
}

/** A legend row: the rarity's fill as a small swatch (the fill its cells in the Items matrix wear), name and count. */
@Composable
private fun LegendRow(label: String, count: Int, rank: Int?, color: Color, typography: InterfaceTypography, ui: Float) {
    val roles = LocalKkRolePalette.current
    val fill = remember(rank, roles) { rank?.let { codexRarityFill(it, roles) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).drawWithCache {
            val rect = Rect(Offset.Zero, size)
            val brush = fill?.brush(rect)
            onDrawBehind {
                if (brush != null) drawRect(brush) else {
                    // Undiscovered: the matrix's hatch cell.
                    drawRect(Kk.Ink3)
                    drawKkHatch(rect)
                }
                fill?.seam?.let { seam ->
                    val line = 1.dp.toPx()
                    drawRect(seam, Offset(line * 0.5f, line * 0.5f), Size(size.width - line, size.height - line), style = kkStroke(line))
                }
            }
        })
        BasicText(label, Modifier.weight(1f), style = typography.bodyStyle(15f * ui, color = color))
        BasicText(count.toString(), style = typography.monoStyle(11f * ui, color = Kk.Mute))
    }
}
