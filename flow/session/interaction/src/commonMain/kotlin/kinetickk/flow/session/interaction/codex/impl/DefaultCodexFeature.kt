// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.ProfileCollectionVisits
import kinetickk.ball.profile.api.HomeProgressProjection
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

private enum class CodexGridContentType { NOTICE, HEADING, SLOT, STAT, SYNERGY }

private val LocalCodexInputEnabled = staticCompositionLocalOf { true }
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
    var tabValue by rememberSaveable { mutableIntStateOf(if (model.runStacks.build == null) 1 else 0) }
    var categoryValue by rememberSaveable { mutableIntStateOf(0) }
    var searchValue by rememberSaveable { mutableStateOf("") }
    var filterValue by rememberSaveable { mutableIntStateOf(0) }
    var selectionValue by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(CodexSelection()) }
    var statsExpandedValue by rememberSaveable { mutableStateOf(false) }
    var statValue by rememberSaveable { mutableStateOf<String?>(null) }
    val build = model.runStacks.build
    // A restored legacy discovery-only filter is equivalent to the collected catalog.
    val filter = if (filterValue == CodexItemFilter.IN_BUILD.ordinal) CodexItemFilter.IN_BUILD else CodexItemFilter.ALL
    val searchFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val slotFocus = remember { mutableMapOf<String, FocusRequester>() }
    var restoreRequestValue by remember { mutableIntStateOf(0) }
    var restoreTargetValue by remember { mutableStateOf<String?>(null) }
    val scrollHolder = rememberSaveableStateHolder()
    val detailsScrollHolder = rememberSaveableStateHolder()
    // Lazy grid content can run again before this composition rebuilds entries.
    // Capture its selectors with the entries so a new tab cannot render the previous tab's icons.
    val tab = tabValue
    val category = categoryValue
    val search = searchValue
    val entries = when (tab) {
        0 -> codexBuildEntries(model, catalog, progress, language)
        1 -> codexCatalogEntries(category, search, filter, model, catalog, progress, language)
        else -> catalog.synergies.filter { codexSynergyDiscovered(it, model, catalog) }.map { codexSynergyEntry(it, model, catalog, language) }
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
        else -> progress.unlockedCoreShapes.size to catalog.coreShapes.size
    }
    val (collectionDiscovered, collectionTotal) = categoryCounts(category)
    val emptyState = codexEmptyState(tab, build != null, if (tab == 1) search else "", entries.size)
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
            val target = slotFocus[restoreTargetValue] ?: if (tabValue == 1) searchFocus else listFocus
            target.requestFocus()
        }
    }
    LaunchedEffect(Unit) { listFocus.requestFocus() }
    val categoryTitles = listOf(SessionText.ITEMS_TITLE, SessionText.WEAPONS_TITLE, SessionText.RELICS_TITLE, SessionText.FORMS_TITLE)
        .map { language.text(it) }
    val filterTitles = listOf(0 to SessionText.FILTER_ALL, 2 to SessionText.FILTER_BUILD)
    CompositionLocalProvider(LocalCodexMeasurer provides measurer) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Kk.Ink).testTag("codex")) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val wide = codexUsesSidePanel(availableWidth.value, availableHeight.value)
        val compactHeader = availableHeight < 480.dp
        val selected = entries.firstOrNull { it.key == selectionValue.pinnedKey }
        val sheetVisible = !wide && selectionValue.sheetOpen && selected != null
        val side = if (wide) 44.dp else 12.dp
        val detailWidth = if (wide) min(332f, availableWidth.value * 0.3f).dp else 0.dp
        val navWidth = if (wide && tab == 1) min(250f, availableWidth.value * 0.22f).dp else 0.dp
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
            // Header: Back, title, the game's three tabs, collection count chip.
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
                if (compactHeader || wide) CodexNavigationTabs(tab, controlScale, Modifier.weight(1f)) { tabValue = it }
                if (tab == 2) KkInfoButton(language.text(SessionText.SYNERGY_HELP), Modifier.padding(horizontal = 8.dp),
                    placement = KkTooltipPlacement.BELOW, textScale = ui)
                if (tab == 1) CodexChip("$collectionDiscovered/$collectionTotal", language.text(SessionText.COLLECTION_PROGRESS, collectionDiscovered, collectionTotal), ui,
                    Modifier.padding(start = 8.dp).testTag("codex-collection-progress"))
            }
            if (!compactHeader && !wide) CodexNavigationTabs(tab, controlScale, Modifier.fillMaxWidth().padding(top = 8.dp)) { tabValue = it }
            Row(Modifier.padding(top = if (wide) 20.dp else 8.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (wide && tab == 1) {
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
                            CodexFilters(filterTitles, filter, build != null, controlScale)
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
                    if (tab == 1 && !wide) {
                        val trailingFilters = compactHeader && category == 0
                        val filtersWidth = if (trailingFilters) codexFiltersWidth(filterTitles.map { language.text(it.second) }) + 8.dp else 0.dp
                        CodexTabRow(categoryTitles, category, "codex-category-", controlScale, Modifier.fillMaxWidth(), reserved = filtersWidth,
                            trailing = {
                                if (trailingFilters) {
                                    Spacer(Modifier.width(8.dp))
                                    CodexFilters(filterTitles, filter, build != null, controlScale) { filterValue = it }
                                }
                            }) { categoryValue = it }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 8.dp))
                        CodexSearchField(search, !sheetVisible, ui, inlineLabel = true, searchFocus, dense = compactHeader) { searchValue = codexSearchInput(it) }
                        if (category == 0 && !compactHeader) {
                            Spacer(Modifier.height(8.dp))
                            CodexFilters(filterTitles, filter, build != null, controlScale) { filterValue = it }
                        }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 10.dp))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        // Cells grow with larger text, like the controls: their badges grow too.
                        val cellSize = CodexCell * max(1f, ui)
                        val columns = max(1, ((maxWidth + CodexCellGap) / (cellSize + CodexCellGap)).toInt())
                        // The Build tab's stat rows share one layout (value beside or under the name), like the stat panels.
                        val statRows = build?.stats?.map { stat ->
                            stat.name.localizedContent(language) to "${codexNumber(stat.value, language)}${stat.unit.localizedContent(language)}"
                        }.orEmpty()
                        val statsFit = rememberCodexPairFit(statRows, maxWidth - 12.dp - 28.dp, 12.dp, 16f * ui,
                            typography.wideStyle(20f * ui, tabular = true)) { size -> typography.bodyStyle(size) }
                        scrollHolder.SaveableStateProvider(if (tab == 1) "$tab/$category" else "$tab") {
                            val grid = rememberLazyGridState()
                            var priorKeysValue by rememberSaveable { mutableStateOf<String?>(null) }
                            val resultKey = entries.joinToString("|") { it.key }
                            LaunchedEffect(resultKey) {
                                if (priorKeysValue != null && priorKeysValue != resultKey) grid.scrollToItem(0)
                                priorKeysValue = resultKey
                            }
                            // Row/column highlight of the previewed cell in flat (catalog) grids.
                            val previewIndex = if (tab == 1) entries.indexOfFirst { it.key == selectionValue.previewKey } else -1
                            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().focusRequester(listFocus).focusable(enabled = !sheetVisible).testTag("codex-grid"),
                                state = grid, horizontalArrangement = Arrangement.spacedBy(CodexCellGap), verticalArrangement = Arrangement.spacedBy(CodexCellGap), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp, start = 6.dp, end = 6.dp)) {
                                if (emptyState != CodexEmptyState.NONE) item(key = "empty", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) {
                                    EmptyNotice(emptyState, ui)
                                }
                                if (tab == 0 && build != null) {
                                    fun section(key: String, title: String, sectionEntries: List<CodexEntry>, value: String? = null) {
                                        item(key = "heading/$key", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                                            // Label and value are separate texts (no separator glyph between them).
                                            Row(Modifier.padding(top = 12.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                val headingStyle = typography.labelStyle(14f * ui, color = Kk.Mute)
                                                BasicText(title.uppercase(), style = headingStyle)
                                                value?.let { BasicText(it.uppercase(), style = headingStyle) }
                                            }
                                        }
                                        items(sectionEntries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
                                            CodexSlot(entry, catalog, ui, selectionValue, slotFocus, band = false, { selectionValue = it(selectionValue) })
                                        }
                                    }
                                    section("character", language.text(SessionText.CHARACTER), entries.filter { it.icon is CodexIcon.Shape })
                                    section("weapon", language.text(SessionText.WEAPON), entries.filter { it.icon is CodexIcon.Weapon },
                                        language.text(SessionText.LEVEL_SHORT, build.weaponLevel))
                                    section("relics", language.text(SessionText.RELICS_TITLE), entries.filter { it.icon is CodexIcon.Relic || it.icon == CodexIcon.Empty },
                                        "${build.relics.size}/${catalog.relicPolicy.maxSlots}")
                                    section("items", language.text(SessionText.ITEMS), entries.filter { it.icon is CodexIcon.Item })
                                    if (entries.none { it.icon is CodexIcon.Item }) item(key = "empty-inventory", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) { EmptyNotice(CodexEmptyState.EMPTY_INVENTORY, ui) }
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
                                                    BuildStatSource.SYNERGIES -> language.text(SessionText.SYNERGIES_SOURCE)
                                                    BuildStatSource.MASTERY -> language.text(SessionText.MASTERY_SOURCE)
                                                    BuildStatSource.TEMPORARY -> language.text(SessionText.TEMPORARY_SOURCE)
                                                }
                                                BasicText("$name  ${codexNumber(contribution.amount, language)}${stat.unit.localizedContent(language)}",
                                                    style = typography.monoStyle(11f * ui, color = Kk.Bone2))
                                            }
                                        }
                                    }
                                } else if (tab == 2) {
                                    items(entries, key = { it.key }, contentType = { CodexGridContentType.SYNERGY }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                                        SynergySlot(entry, catalog, model, ui, selectionValue, slotFocus) { selectionValue = it(selectionValue) }
                                    }
                                } else itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.SLOT }) { index, entry ->
                                    val band = previewIndex >= 0 && (index / columns == previewIndex / columns || index % columns == previewIndex % columns)
                                    CodexSlot(entry, catalog, ui, selectionValue, slotFocus, band) { selectionValue = it(selectionValue) }
                                }
                            }
                        }
                    }
                }
                if (wide) Box(Modifier.width(detailWidth).fillMaxHeight().testTag("codex-side-panel")) {
                    detailsScrollHolder.SaveableStateProvider(selectionValue.previewKey ?: "placeholder") {
                        CodexDetails(entries.firstOrNull { it.key == selectionValue.previewKey }, catalog, ui, Modifier.fillMaxSize())
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
                        .drawBehind { drawRect(selected.color, Offset.Zero, Size(size.width, 3.dp.toPx())) }.testTag("codex-sheet")
                        .pointerInput(Unit) { detectTapGestures { } }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                            KkButton(language.text(SessionRedesignText.CLOSE), onClick = restoreFocus, modifier = Modifier.testTag("codex-sheet-close"),
                                variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, textScale = ui)
                        }
                        detailsScrollHolder.SaveableStateProvider(selected.key) {
                            CodexDetails(selected, catalog, ui, Modifier.fillMaxWidth().weight(1f), panel = Kk.Ink3, face = Kk.Ink2)
                        }
                    }
                }
        }
    }
    }
}

@Composable
private fun codexMeasurer(): CanvasTextMeasurer = LocalCodexMeasurer.current ?: rememberKkCanvasMeasurer()

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
        if (pinned) {
            // Selected: 3 dp ink gap and a 2 dp bone ring (box-shadow 0 0 0 3px ink, 0 0 0 5px bone).
            val inner = 1.5.dp.toPx()
            drawRect(Kk.Ink, Offset(-inner, -inner), Size(size.width + inner * 2f, size.height + inner * 2f), style = kkStroke(3.dp.toPx()))
            val outer = 4.dp.toPx()
            drawRect(Kk.Bone, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(2.dp.toPx()))
        } else if (previewed || focusedValue) {
            val outer = 3.dp.toPx()
            drawRect(roles.you, Offset(-outer, -outer), Size(size.width + outer * 2f, size.height + outer * 2f), style = kkStroke(2.dp.toPx()))
        }
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

/** Cell face: rarity (items) or aspect (relics) color when discovered, hatch when not. */
private fun DrawScope.drawCodexCell(entry: CodexEntry, catalog: UiCatalogSnapshot, measurer: CanvasTextMeasurer, band: Boolean, hovered: Boolean,
    markBand: Float) {
    val rect = Rect(Offset.Zero, size)
    val (centerY, radius, centerX) = codexCellGlyph(size.minDimension, markBand, density)
    val center = Offset(centerX, centerY)
    when (val icon = entry.icon) {
        is CodexIcon.Item -> {
            drawRect(entry.color, alpha = if (band || hovered) 1f else 0.84f)
            drawItemIcon(icon.definition, center, radius, Kk.Ink, stack = icon.stack.takeIf { it > 0 })
        }
        is CodexIcon.Relic -> {
            drawRect(Kk.Ink2)
            drawRect(entry.color, style = kkStroke(2.dp.toPx()))
            // The rank is the cell's badge; the medallion's rank markers would read as stray dots.
            drawRelicIcon(icon.definition, catalog.relicPolicy, center, radius, rank = null, time = 0f)
        }
        is CodexIcon.Weapon -> {
            drawRect(if (hovered) Kk.Ink3 else Kk.Ink2)
            drawRect(if (entry.active) measurer.roles.you else Kk.Line2, style = kkStroke(if (entry.active) 2.dp.toPx() else 1.dp.toPx()))
            drawKkIcon(codexWeaponIcon(icon.id), center, radius * 1.1f, if (entry.discovered) Kk.Bone else Kk.Mute)
        }
        is CodexIcon.Shape -> {
            drawRect(if (hovered) Kk.Ink3 else Kk.Ink2)
            if (!entry.discovered) drawKkHatch(rect)
            drawRect(if (entry.active) measurer.roles.you else Kk.Line2, style = kkStroke(if (entry.active) 2.dp.toPx() else 1.dp.toPx()))
            drawKkIcon(icon.id.formIcon(), center, radius * 1.1f, if (entry.discovered) Kk.Bone else Kk.Mute)
            if (!entry.discovered) drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(size.width - 11.dp.toPx(), 11.dp.toPx()), 11.dp.toPx(), Kk.Mute)
        }
        is CodexIcon.Synergy -> Unit
        CodexIcon.Unknown -> {
            drawRect(Kk.Ink3)
            drawKkHatch(rect)
            drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.9f, Kk.Mute)
        }
        CodexIcon.Empty -> {
            drawRect(Kk.Ink2)
            drawKkHatch(rect)
            drawKkIcon(KkIcon.UI_PLUS, center, radius * 0.8f, Kk.Mute2)
        }
    }
}

@Composable
private fun slotModifier(entry: CodexEntry, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, interaction: MutableInteractionSource, onSelection: ((CodexSelection) -> CodexSelection) -> Unit): Modifier {
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
    return Modifier.testTag("codex-slot-${entry.key}").semantics { contentDescription = entry.summary; selected = selection.pinnedKey == entry.key }
        .focusRequester(requester).focusProperties { canFocus = inputEnabled }.onFocusChanged { state ->
            onSelection { current ->
                if (state.isFocused) current.copy(focusKey = entry.key, hoverKey = null)
                else if (current.focusKey == entry.key) current.copy(focusKey = null) else current
            }
        }.hoverable(interaction, enabled = inputEnabled).clickable(enabled = inputEnabled, interactionSource = interaction, indication = null, role = Role.Button) { onSelection { it.activate(entry.key) } }
}

@Composable
private fun SynergySlot(entry: CodexEntry, catalog: UiCatalogSnapshot, model: CodexRenderModel, ui: Float, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, onSelection: ((CodexSelection) -> CodexSelection) -> Unit) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focusedValue by interaction.collectIsFocusedAsState()
    val hoveredValue by interaction.collectIsHoveredAsState()
    val highlighted = selection.previewKey == entry.key || selection.pinnedKey == entry.key
    val components = (entry.icon as CodexIcon.Synergy).components
    val fg = if (highlighted) Kk.Ink else Kk.Bone
    val titleSize = 24f * ui
    BoxWithConstraints(slotModifier(entry, selection, focus, interaction, onSelection).fillMaxWidth()
        .drawBehind {
            drawKkListRowBackground(Rect(Offset.Zero, size), roles, if (highlighted) 1f else 0f, hoveredValue || focusedValue)
            if (!highlighted) drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 9.dp.toPx())
        }
        .padding(horizontal = 18.dp, vertical = 10.dp)) {
        // Narrow rows (phones) put smaller diamonds beside the title and the tags under both,
        // so neither the title nor a tag is ever squeezed into breaking inside a word.
        val narrow = maxWidth < SynergyStackBelow
        val diagramWidth = if (narrow) 96.dp else 150.dp
        val tags: @Composable () -> Unit = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CodexTag(entry.kind, if (highlighted) Kk.Ink else entry.color, if (highlighted) Kk.Bone else Kk.Ink, ui)
                entry.status?.let { status ->
                    CodexTag(status, if (entry.active) roles.you else Color.Transparent,
                        if (entry.active) Kk.Ink else kkListRowSecondary(if (highlighted) 1f else 0f), ui)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodexFitText(entry.title, { size -> measurer.typography.condStyle(size, color = fg) }, titleSize, 16f)
                    if (!narrow) tags()
                }
                SynergyDiagram(components, catalog, model, entry.active, diagramWidth, if (narrow) 34f else 44f)
            }
            if (narrow) tags()
        }
    }
}

/** Rows narrower than this stack their tags under the title (phones). */
internal val SynergyStackBelow = 420.dp

/** The two components as relic diamonds joined by a link bar; the ones in the run are ringed. */
@Composable
private fun SynergyDiagram(components: List<kinetickk.ball.content.api.RelicId?>, catalog: UiCatalogSnapshot, model: CodexRenderModel, active: Boolean, width: Dp, slotDp: Float) {
    val roles = LocalKkRolePalette.current
    Canvas(Modifier.size(width = width, height = (slotDp + 14f).dp)) {
        val left = Offset(size.width * 0.2f, size.height * 0.5f)
        val right = Offset(size.width * 0.8f, size.height * 0.5f)
        drawKkSynergyLink(left, right, if (active) roles.you else Kk.Line2)
        components.forEachIndexed { index, id ->
            val center = if (index == 0) left else right
            val relic = id?.let(catalog::relic)
            val present = id != null && model.runStacks.build?.relics?.any { it.id == id } == true
            drawKkRelicSlot(center, relic?.let { relicAspectColor(it.aspect) } ?: Color.Unspecified,
                icon = relic?.let { KkIcon.Aspects[it.aspect.ordinal] }, sizeDp = slotDp,
                ring = if (present) roles.you else Color.Unspecified)
        }
    }
}

/**
 * Display text that wraps only between words: shrinks from [maxSize] toward [minSize] until its
 * longest word fits the width (the words themselves are never split).
 */
@Composable
private fun CodexFitText(text: String, style: (Float) -> TextStyle, maxSize: Float, minSize: Float, modifier: Modifier = Modifier) {
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
private fun CodexDetails(entry: CodexEntry?, catalog: UiCatalogSnapshot, ui: Float, modifier: Modifier, panel: Color = Kk.Ink2, face: Color = Kk.Ink1) {
    val scroll = rememberScrollState()
    Box(modifier) {
        CodexDetailsBody(entry, catalog, ui, Modifier.fillMaxSize().testTag("codex-details-scroll").verticalScroll(scroll), panel)
        // Content below the fold fades out over the panel beside a scroll bar, never cut bare.
        CodexScrollCue(scroll, face, entry?.color ?: Kk.Bone, Modifier.matchParentSize().testTag("codex-details-scroll-cue"), barEnd = 6.dp)
    }
}

@Composable
private fun CodexDetailsBody(entry: CodexEntry?, catalog: UiCatalogSnapshot, ui: Float, modifier: Modifier, panel: Color) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    Column(modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (entry == null) {
            Box(Modifier.fillMaxWidth().height(120.dp).kkHatch())
        } else {
            val itemIcon = entry.icon as? CodexIcon.Item
            val item = itemIcon?.definition
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    entry.kind == CODEX_NONE -> Unit
                    item != null || entry.icon is CodexIcon.Relic -> CodexTag(entry.kind, entry.color, Kk.Ink, ui, height = 26.dp)
                    else -> CodexTag(entry.kind, Color.Transparent, Kk.Bone, ui, line = true, height = 26.dp)
                }
                entry.help?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
            }
            CodexDetailTitle(entry.title, if (entry.discovered) Kk.Bone else Kk.Mute, ui)
            if (item != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodexTag(item.family.localizedContent(language), Color.Transparent, Kk.Bone, ui, line = true)
                    CodexTag(language.text(SessionRedesignText.MAX_STACKS, item.maxStacks), Color.Transparent, Kk.Bone, ui, line = true)
                    if (itemIcon.stack > 0) CodexTag(language.text(SessionText.STACK_QUANTITY, entry.quantity), roles.you, Kk.Ink, ui)
                }
                val stats = listOf(item.primary to roles.you, item.secondary to Kk.Bone).map { (stat, color) ->
                    Triple(stat.effect.displayLabel.localizedContent(language), codexModifierValue(stat, language), color)
                }
                // Both panels share one layout: the value beside the label, or under it where a word would not fit beside it.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fit = rememberCodexPairFit(stats.map { it.first to it.second }, maxWidth - 28.dp, 12.dp, 16f * ui,
                        typography.wideStyle(22f * ui, tabular = true)) { size -> typography.bodyStyle(size) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stats.forEach { (label, value, color) -> CodexStatPanel(label, value, color, ui, panel, fit) }
                    }
                }
            } else {
                if (!entry.discovered && entry.description.isBlank()) {
                    Box(Modifier.fillMaxWidth().height(120.dp).background(panel).kkHatch(), contentAlignment = Alignment.Center) {
                        BasicText("?", style = typography.condStyle(64f, color = Kk.Mute))
                    }
                }
                entry.description.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    BasicText(paragraph, style = typography.bodyStyle(16f * ui, color = Kk.Bone2))
                }
            }
            // Facts: a label and its value (Lvl N, counts, status words); explanations sit behind (!).
            if (entry.facts.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("codex-facts"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.facts.forEach { fact -> CodexFactRow(fact, ui) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * A fact row: mono label (mute) on the left, cond value right-aligned after it, then its (!) if
 * any. A long value (two relic names) wraps between words instead of running out of the row.
 */
@Composable
private fun CodexFactRow(fact: CodexFact, ui: Float) {
    val typography = codexMeasurer().typography
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag("codex-fact"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(fact.label.uppercase(), Modifier.alignByBaseline(), style = typography.monoStyle(11f * ui, color = Kk.Mute),
            softWrap = false, maxLines = 1)
        BasicText(fact.value.uppercase(), Modifier.weight(1f).alignByBaseline(),
            style = typography.condStyle(21f * ui, tabular = true, lineHeightEm = 1f, color = Kk.Bone)
                .copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
        fact.info?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
    }
}

/** Wide detail title: shrinks (30 to 18 px) until its longest word fits, never breaking a word. */
@Composable
private fun CodexDetailTitle(title: String, color: Color, ui: Float) {
    val typography = codexMeasurer().typography
    CodexFitText(title, { size -> typography.wideStyle(size, lineHeightEm = 1.05f, color = color) }, 30f * ui, 18f,
        Modifier.testTag("codex-detail-title"))
}

/**
 * Stat panel (`.panel`): effect label left (body), value right (wide, tabular); with [fit] stacked,
 * the value sits right-aligned under the label so no word of the label is broken.
 */
@Composable
private fun CodexStatPanel(label: String, value: String, valueColor: Color, ui: Float, panel: Color, fit: CodexPairFit) {
    val typography = codexMeasurer().typography
    val labelStyle = typography.bodyStyle(fit.labelSize, color = Kk.Bone)
    val valueStyle = typography.wideStyle(22f * ui, tabular = true, color = valueColor)
    if (fit.stacked) Column(Modifier.fillMaxWidth().background(panel).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = labelStyle)
        BasicText(value, Modifier.align(Alignment.End), style = valueStyle)
    } else Row(Modifier.fillMaxWidth().background(panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(label, Modifier.weight(1f), style = labelStyle)
        BasicText(value, style = valueStyle)
    }
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
private fun KkLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, style = style)
}

/** Tag plate (`.tag`): face [background] (transparent + [line] = outlined), cond 800 label. */
@Composable
private fun CodexTag(text: String, background: Color, foreground: Color, ui: Float, line: Boolean = false, height: Dp = 22.dp) {
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

@Composable
private fun CodexChip(value: String, description: String, ui: Float, modifier: Modifier) {
    val typography = codexMeasurer().typography
    BasicText(value, modifier.clearAndSetSemantics { contentDescription = description }
        .drawBehind { drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 8.dp.toPx()) }
        .padding(horizontal = 14.dp, vertical = 6.dp),
        style = typography.condStyle(21f * ui, tabular = true, lineHeightEm = 1f, color = Kk.Bone), softWrap = false, maxLines = 1)
}

@Composable
private fun EmptyNotice(state: CodexEmptyState, ui: Float) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val (title, description) = when (state) {
        CodexEmptyState.NO_RUN -> language.text(SessionText.NO_RUN) to language.text(SessionText.NO_RUN_HELP)
        CodexEmptyState.EMPTY_SEARCH -> language.text(SessionText.NO_MATCHES) to language.text(SessionText.NO_MATCHES_HELP)
        else -> language.text(SessionText.EMPTY_INVENTORY) to language.text(SessionText.EMPTY_INVENTORY_HELP)
    }
    Row(Modifier.fillMaxWidth().background(Kk.Ink2).kkHatch().padding(20.dp).testTag("codex-empty-${state.name}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title.uppercase(), Modifier.weight(1f, fill = false), style = typography.condStyle(26f * ui, color = Kk.Bone))
        KkInfoButton(description, placement = KkTooltipPlacement.BELOW, textScale = ui)
    }
}

@Composable
private fun CodexNavigationTabs(tab: Int, controlScale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val language = LocalAppLanguage.current
    val titles = listOf(SessionText.TAB_BUILD, SessionText.TAB_CATALOG, SessionText.TAB_SYNERGIES).map { language.text(it) }
    CodexTabRow(titles, tab, "codex-tab-", controlScale, modifier, onSelect = onSelect)
}

/**
 * Smallest scale tabs shrink to before their row scrolls instead: Russian categories at 175 % still
 * fit a 360 dp phone (their labels stay above 16 px).
 */
internal const val CODEX_TAB_MIN_SCALE = 0.55f

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
 * Section row height (dp) for the [controlScale]: 46 dp at the design size, growing more slowly
 * than the text so the whole nav (rows, search, filters, legend) fits a 810 dp tall window at 175 %.
 */
internal fun codexNavRowHeight(controlScale: Float): Float = 46f + 16f * (controlScale - 1f)

/** Section row (`.lrow`) with a mono count, as a tab of the collection, its title at the shared [titleSize]. */
@Composable
private fun CodexNavRow(text: String, count: String, titleSize: Float, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(KkTime.Pull, easing = KkEase.Pull), label = "codexNavRow")
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

/** The game's filters (All / In build) as a segmented control. */
@Composable
private fun CodexFilters(filters: List<Pair<Int, SessionText>>, filter: CodexItemFilter, hasBuild: Boolean, controlScale: Float, onSelect: (Int) -> Unit) {
    val language = LocalAppLanguage.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        filters.forEach { (index, label) ->
            CodexSegment(language.text(label), filter.ordinal == index, index != 2 || hasBuild, "codex-filter-$index", controlScale) { onSelect(index) }
        }
    }
}

@Composable
private fun CodexSegment(text: String, selected: Boolean, enabled: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = enabled && LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val density = LocalDensity.current.density
    val width = remember(measurer, text, density) { kkSegmentWidth(measurer, text, density) / density }
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, onClick = onClick)
        .size(width.dp, (34f * controlScale).dp)
        .drawBehind {
            val alpha = if (enabled) 1f else 0.4f
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
            drawKkSegment(measurer, Rect(Offset.Zero, size), text, selected, hovered && inputEnabled, focused && inputEnabled)
            drawContext.canvas.restore()
        })
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
            LegendRow(rarity.displayLabel.localizedContent(language), counts[rarity.ordinal], Kk.rarity(rarity.rank), Kk.Bone, typography, ui)
        }
        LegendRow(language.text(SessionText.UNDISCOVERED), catalog.items.size - counts.sum(), null, Kk.Mute, typography, ui)
    }
}

@Composable
private fun LegendRow(label: String, count: Int, swatch: Color?, color: Color, typography: InterfaceTypography, ui: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(14.dp).drawBehind {
            if (swatch != null) drawKkSheared(Rect(Offset.Zero, size), swatch)
            else {
                drawKkSheared(Rect(Offset.Zero, size), Kk.Ink3)
                drawKkHatch(Rect(Offset.Zero, size))
            }
        })
        BasicText(label, Modifier.weight(1f), style = typography.bodyStyle(15f * ui, color = color))
        BasicText(count.toString(), style = typography.monoStyle(11f * ui, color = Kk.Mute))
    }
}
