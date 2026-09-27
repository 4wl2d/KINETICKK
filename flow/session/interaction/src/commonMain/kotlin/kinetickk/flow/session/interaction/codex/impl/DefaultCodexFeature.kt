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

/** Grid cell size and gap (the board's matrix cells, sized up to carry each entry's glyph). */
private val CodexCell = 64.dp
private val CodexCellGap = 6.dp

/** All navigation, search, expansion and selection here belong to this local Compose lifetime. */
@Composable
internal fun CodexContent(catalog: UiCatalogSnapshot, model: CodexRenderModel, progress: HomeProgressProjection, scale: Float, onEntryViewed: (CollectionEntry) -> Unit = {}, onClose: () -> Unit) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val measurer = rememberKkCanvasMeasurer(scale)
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
        0 -> if (build == null) emptyList() else buildList {
            build.character?.let { add(codexShapeEntry(catalog.coreShape(it), model, progress, language)) }
            build.weapon?.let { add(codexWeaponEntry(catalog.weapon(it), model, progress, language)) }
            repeat(catalog.relicPolicy.maxSlots) { index ->
                val relic = build.relics.getOrNull(index)
                add(if (relic != null) codexRelicEntry(catalog.relic(relic.id), model, catalog, language) else CodexEntry(
                    "empty-relic/$index", language.text(SessionText.EMPTY_RELIC_SLOT, index + 1), "", language.text(SessionText.RELIC_SLOT), CODEX_NONE,
                    language.text(SessionText.EMPTY), CodexIcon.Empty, Kk.Mute, help = language.text(SessionText.EMPTY_RELIC_HELP),
                ))
            }
            addAll(model.items.filter { model.itemStack(it.id) > 0 }.map { codexItemEntry(it, model, language) })
        }
        1 -> codexCatalogEntries(category, search, filter, model, catalog, progress, language)
        else -> catalog.synergies.filter { codexSynergyDiscovered(it, model, catalog) }.map { synergy ->
            val owned = model.discoveredRelicIds + build?.relics?.map { it.id }.orEmpty()
            val components = codexSynergyComponents(synergy, catalog, owned)
            val summary = build?.synergies?.firstOrNull { it.id == synergy.id.name }
            val active = summary?.active == true
            val required = synergy.requiredAspect?.let { language.text(SessionText.SYNERGY_REQUIREMENT, it.displayLabel.localizedContent(language)) }
                ?: synergy.requiredRelics.joinToString(" + ") { catalog.relic(it).name.localizedContent(language) }
            CodexEntry("synergy/${synergy.id}", synergy.name.localizedContent(language), "${synergy.description.localizedContent(language)}\n\n${language.text(SessionText.REQUIRES, required)}" +
                (summary?.missingComponents?.takeIf { it.isNotEmpty() }?.let { missing -> "\n" + language.text(SessionText.MISSING, missing.joinToString { it.localizedContent(language) }) } ?: ""),
                synergy.requiredAspect?.displayLabel?.localizedContent(language) ?: language.text(SessionText.COMBINATION), CODEX_NONE,
                if (active) language.text(SessionText.ACTIVE) else if (build == null) language.text(SessionText.NO_RUN_INACTIVE) else language.text(SessionText.INACTIVE),
                CodexIcon.Synergy(components), synergy.requiredAspect?.let(::relicAspectColor) ?: Kk.Bone, active = active)
        }
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
        val controlScale = max(1f, scale / 1.25f).let { if (compactHeader) min(it, 1.2f) else it }
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
                    variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, enabled = !sheetVisible, textScale = min(scale, 1.25f))
                Spacer(Modifier.width(14.dp))
                if (!compactHeader) {
                    Box(Modifier.width(1.dp).height(28.dp).background(Kk.Line2))
                    Spacer(Modifier.width(14.dp))
                    BasicText(language.text(SessionText.CODEX).uppercase(), if (wide) Modifier else Modifier.weight(1f),
                        style = typography.condStyle(if (wide) 44f else if (availableWidth < 600.dp) 26f else 32f, color = Kk.Bone), softWrap = false, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.width(if (wide) 22.dp else 8.dp))
                }
                if (compactHeader || wide) CodexNavigationTabs(tab, controlScale, Modifier.weight(1f)) { tabValue = it }
                if (tab == 2) KkInfoButton(language.text(SessionText.SYNERGY_HELP), Modifier.padding(horizontal = 8.dp),
                    placement = KkTooltipPlacement.BELOW, textScale = min(scale, 1.25f))
                if (tab == 1) CodexChip("$collectionDiscovered/$collectionTotal", language.text(SessionText.COLLECTION_PROGRESS, collectionDiscovered, collectionTotal),
                    Modifier.padding(start = 8.dp).testTag("codex-collection-progress"))
            }
            if (!compactHeader && !wide) CodexNavigationTabs(tab, controlScale, Modifier.fillMaxWidth().padding(top = 8.dp)) { tabValue = it }
            Row(Modifier.padding(top = if (wide) 20.dp else 8.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (wide && tab == 1) {
                    // Left nav: section rows with counts, Search, the game's filters, rarity legend.
                    Column(Modifier.width(navWidth).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        categoryTitles.forEachIndexed { index, title ->
                            val (found, total) = categoryCounts(index)
                            CodexNavRow(title, "$found/$total", category == index, "codex-category-$index", controlScale) { categoryValue = index }
                        }
                        Spacer(Modifier.height(18.dp))
                        BasicText(language.text(SessionRedesignText.SEARCH).uppercase(), style = typography.labelStyle(13f * min(scale, 1.4f), color = Kk.Mute))
                        Spacer(Modifier.height(4.dp))
                        CodexSearchField(search, !sheetVisible, scale, inlineLabel = false, searchFocus) { searchValue = codexSearchInput(it) }
                        if (category == 0) {
                            Spacer(Modifier.height(10.dp))
                            CodexFilters(filterTitles, filter, build != null, controlScale)
                                { filterValue = it }
                            Spacer(Modifier.height(18.dp))
                            BasicText(language.text(SessionRedesignText.RARITY).uppercase(), style = typography.labelStyle(13f * min(scale, 1.4f), color = Kk.Mute))
                            CodexRarityLegend(catalog, model, scale)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (tab == 1 && !wide) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            categoryTitles.forEachIndexed { index, title ->
                                CodexTab(title, category == index, "codex-category-$index", controlScale) { categoryValue = index }
                            }
                            if (compactHeader && category == 0) {
                                Spacer(Modifier.width(8.dp))
                                CodexFilters(filterTitles, filter, build != null, controlScale) { filterValue = it }
                            }
                        }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 8.dp))
                        CodexSearchField(search, !sheetVisible, scale, inlineLabel = true, searchFocus, dense = compactHeader) { searchValue = codexSearchInput(it) }
                        if (category == 0 && !compactHeader) {
                            Spacer(Modifier.height(8.dp))
                            CodexFilters(filterTitles, filter, build != null, controlScale) { filterValue = it }
                        }
                        Spacer(Modifier.height(if (compactHeader) 4.dp else 10.dp))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val columns = max(1, ((maxWidth + CodexCellGap) / (CodexCell + CodexCellGap)).toInt())
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
                                    EmptyNotice(emptyState, scale)
                                }
                                if (tab == 0 && build != null) {
                                    fun section(key: String, title: String, sectionEntries: List<CodexEntry>) {
                                        item(key = "heading/$key", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                                            BasicText(title.uppercase(), Modifier.padding(top = 12.dp, bottom = 2.dp), style = typography.labelStyle(14f * min(scale, 1.4f), color = Kk.Mute))
                                        }
                                        items(sectionEntries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
                                            CodexSlot(entry, catalog, scale, selectionValue, slotFocus, band = false, { selectionValue = it(selectionValue) })
                                        }
                                    }
                                    section("character", language.text(SessionText.CHARACTER), entries.filter { it.icon is CodexIcon.Shape })
                                    section("weapon", language.text(SessionText.WEAPON_LEVEL, build.weaponLevel), entries.filter { it.icon is CodexIcon.Weapon })
                                    section("relics", language.text(SessionText.RELIC_COUNT, build.relics.size, catalog.relicPolicy.maxSlots), entries.filter { it.icon is CodexIcon.Relic || it.icon == CodexIcon.Empty })
                                    section("items", language.text(SessionText.ITEMS), entries.filter { it.icon is CodexIcon.Item })
                                    if (entries.none { it.icon is CodexIcon.Item }) item(key = "empty-inventory", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) { EmptyNotice(CodexEmptyState.EMPTY_INVENTORY, scale) }
                                    item(key = "stats-heading", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                                        Box(Modifier.padding(top = 12.dp)) {
                                            CodexToggleButton(language.text(SessionRedesignText.STATS), statsExpandedValue, "codex-stats", controlScale) { statsExpandedValue = !statsExpandedValue }
                                        }
                                    }
                                    if (statsExpandedValue) items(build.stats, key = { "stat/${it.name}" }, contentType = { CodexGridContentType.STAT }, span = { GridItemSpan(maxLineSpan) }) { stat ->
                                        CodexStatRow(stat.name.localizedContent(language), "${codexNumber(stat.value, language)}${stat.unit.localizedContent(language)}",
                                            statValue == stat.name, "codex-stat-${stat.name}", scale, { statValue = if (statValue == stat.name) null else stat.name }) {
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
                                                    style = typography.monoStyle(11f * scale, color = Kk.Bone2))
                                            }
                                        }
                                    }
                                } else if (tab == 2) {
                                    items(entries, key = { it.key }, contentType = { CodexGridContentType.SYNERGY }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                                        SynergySlot(entry, catalog, model, scale, selectionValue, slotFocus) { selectionValue = it(selectionValue) }
                                    }
                                } else itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.SLOT }) { index, entry ->
                                    val band = previewIndex >= 0 && (index / columns == previewIndex / columns || index % columns == previewIndex % columns)
                                    CodexSlot(entry, catalog, scale, selectionValue, slotFocus, band) { selectionValue = it(selectionValue) }
                                }
                            }
                        }
                    }
                }
                if (wide) Box(Modifier.width(detailWidth).fillMaxHeight().testTag("codex-side-panel")) {
                    detailsScrollHolder.SaveableStateProvider(selectionValue.previewKey ?: "placeholder") {
                        CodexDetails(entries.firstOrNull { it.key == selectionValue.previewKey }, catalog, scale, Modifier.fillMaxSize())
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
                                variant = KkButtonVariant.GHOST, size = KkButtonSize.SM, textScale = min(scale, 1.25f))
                        }
                        detailsScrollHolder.SaveableStateProvider(selected.key) {
                            CodexDetails(selected, catalog, scale, Modifier.fillMaxWidth().weight(1f), panel = Kk.Ink3)
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
    scale: Float,
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
    Box(slotModifier(entry, selection, focus, interaction, onSelection).aspectRatio(1f).drawBehind {
        val gap = CodexCellGap.toPx()
        if (band) drawRect(roles.you.copy(alpha = 0.10f), Offset(-gap * 0.5f, -gap * 0.5f), Size(size.width + gap, size.height + gap))
        drawCodexCell(entry, catalog, measurer, band, hoveredValue || focusedValue)
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
        if (entry.isNew) DiscoveryBadge(LocalAppLanguage.current.text(SessionText.NEW_DISCOVERY), min(scale, 1.25f) * 0.8f,
            Modifier.align(Alignment.TopEnd).wrapContentSize(Alignment.TopEnd, unbounded = true).offset(x = 6.dp, y = (-6).dp)
                .testTag("codex-new-${entry.key}"))
        if (badge != null) BasicText(badge, Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Kk.Ink).padding(horizontal = 3.dp),
            style = measurer.typography.monoStyle(9f * min(scale, 1.4f), color = Kk.Bone))
    }
}

/** Amount shown on a cell: stacks or rank in the current run, or the equipped weapon's level. */
private fun CodexEntry.cellBadge(): String? = when (val icon = icon) {
    is CodexIcon.Item -> quantity.takeIf { icon.stack > 0 }
    is CodexIcon.Relic -> quantity.takeIf { icon.rank != null }
    is CodexIcon.Weapon -> quantity.takeIf { it != CODEX_NONE }
    else -> null
}

/** Cell face: rarity (items) or aspect (relics) color when discovered, hatch when not. */
private fun DrawScope.drawCodexCell(entry: CodexEntry, catalog: UiCatalogSnapshot, measurer: CanvasTextMeasurer, band: Boolean, hovered: Boolean) {
    val rect = Rect(Offset.Zero, size)
    val center = rect.center
    val radius = size.minDimension * 0.36f
    when (val icon = entry.icon) {
        is CodexIcon.Item -> {
            drawRect(entry.color, alpha = if (band || hovered) 1f else 0.84f)
            drawItemIcon(icon.definition, center, radius, Kk.Ink, stack = icon.stack.takeIf { it > 0 })
        }
        is CodexIcon.Relic -> {
            drawRect(Kk.Ink2)
            drawRect(entry.color, style = kkStroke(2.dp.toPx()))
            drawRelicIcon(icon.definition, catalog.relicPolicy, center, radius, rank = icon.rank, time = 0f)
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
private fun SynergySlot(entry: CodexEntry, catalog: UiCatalogSnapshot, model: CodexRenderModel, scale: Float, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, onSelection: ((CodexSelection) -> CodexSelection) -> Unit) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focusedValue by interaction.collectIsFocusedAsState()
    val hoveredValue by interaction.collectIsHoveredAsState()
    val highlighted = selection.previewKey == entry.key || selection.pinnedKey == entry.key
    val components = (entry.icon as CodexIcon.Synergy).components
    Row(slotModifier(entry, selection, focus, interaction, onSelection).fillMaxWidth()
        .drawBehind {
            drawKkListRowBackground(Rect(Offset.Zero, size), roles, if (highlighted) 1f else 0f, hoveredValue || focusedValue)
            if (!highlighted) drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 9.dp.toPx())
        }
        .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val fg = if (highlighted) Kk.Ink else Kk.Bone
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(entry.title.uppercase(), style = measurer.typography.condStyle(24f * min(scale, 1.4f), color = fg))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CodexTag(entry.kind, if (highlighted) Kk.Ink else entry.color, if (highlighted) Kk.Bone else Kk.Ink, scale)
                CodexTag(entry.availability, if (entry.active) roles.you else Color.Transparent, if (entry.active) Kk.Ink else kkListRowSecondary(if (highlighted) 1f else 0f), scale)
            }
        }
        // Components as relic diamonds joined by a link bar; the ones in the run are ringed.
        Canvas(Modifier.size(width = 150.dp, height = 56.dp)) {
            val left = Offset(size.width * 0.2f, size.height * 0.5f)
            val right = Offset(size.width * 0.8f, size.height * 0.5f)
            drawKkSynergyLink(left, right, if (entry.active) roles.you else Kk.Line2)
            components.forEachIndexed { index, id ->
                val center = if (index == 0) left else right
                val relic = id?.let(catalog::relic)
                val present = id != null && model.runStacks.build?.relics?.any { it.id == id } == true
                drawKkRelicSlot(center, relic?.let { relicAspectColor(it.aspect) } ?: Color.Unspecified,
                    icon = relic?.let { KkIcon.Aspects[it.aspect.ordinal] }, sizeDp = 44f,
                    ring = if (present) roles.you else Color.Unspecified)
            }
        }
    }
}

@Composable
private fun CodexDetails(entry: CodexEntry?, catalog: UiCatalogSnapshot, scale: Float, modifier: Modifier, panel: Color = Kk.Ink2) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    val scroll = rememberScrollState()
    Column(modifier.testTag("codex-details-scroll").verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (entry == null) {
            Box(Modifier.fillMaxWidth().height(120.dp).kkHatch())
        } else {
            val itemIcon = entry.icon as? CodexIcon.Item
            val item = itemIcon?.definition
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    !entry.discovered && entry.kind == CODEX_NONE -> CodexTag(language.text(SessionText.UNDISCOVERED), Color.Transparent, Kk.Mute, scale, line = true)
                    item != null || entry.icon is CodexIcon.Relic -> CodexTag(entry.kind, entry.color, Kk.Ink, scale, height = 26.dp)
                    else -> CodexTag(entry.kind, Color.Transparent, Kk.Bone, scale, line = true, height = 26.dp)
                }
                if (entry.active) CodexTag(language.text(SessionText.ACTIVE), roles.you, Kk.Ink, scale, height = 26.dp)
                entry.help?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = min(scale, 1.25f)) }
            }
            CodexDetailTitle(entry.title, if (entry.discovered) Kk.Bone else Kk.Mute, scale)
            if (item != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodexTag(item.family.localizedContent(language), Color.Transparent, Kk.Bone, scale, line = true)
                    CodexTag(language.text(SessionRedesignText.MAX_STACKS, item.maxStacks), Color.Transparent, Kk.Bone, scale, line = true)
                    if (itemIcon.stack > 0) CodexTag(language.text(SessionText.STACK_QUANTITY, entry.quantity), roles.you, Kk.Ink, scale)
                }
                CodexStatPanel(item.primary.effect.displayLabel.localizedContent(language), codexModifierValue(item.primary, language), roles.you, scale, panel)
                CodexStatPanel(item.secondary.effect.displayLabel.localizedContent(language), codexModifierValue(item.secondary, language), Kk.Bone, scale, panel)
            } else {
                val amount = when (entry.icon) {
                    is CodexIcon.Relic -> language.text(SessionText.RANK_QUANTITY, entry.quantity)
                    else -> entry.quantity.takeIf { it != CODEX_NONE && it.isNotBlank() }
                }
                if (amount != null && entry.discovered) CodexTag(amount, Color.Transparent, Kk.Bone, scale, line = true)
                if (!entry.discovered && entry.description.isBlank()) {
                    Box(Modifier.fillMaxWidth().height(120.dp).background(panel).kkHatch(), contentAlignment = Alignment.Center) {
                        BasicText("?", style = typography.condStyle(64f, color = Kk.Mute))
                    }
                }
                entry.description.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    BasicText(paragraph, style = typography.bodyStyle(16f * scale, color = Kk.Bone2))
                }
            }
            entry.availability.split("\n").filter { it.isNotBlank() }.forEach { line ->
                BasicText(line, style = typography.monoStyle(11f * scale, color = Kk.Mute))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Wide detail title: shrinks (30 to 18 px) until its longest word fits, never breaking a word. */
@Composable
private fun CodexDetailTitle(title: String, color: Color, scale: Float) {
    val typography = codexMeasurer().typography
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer(8)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val size = remember(title, maxPx, scale, typography) {
            val words = title.uppercase().split(' ').filter { it.isNotEmpty() }
            var candidate = 30f * min(scale, 1.25f)
            while (candidate > 18f && words.any { word ->
                    textMeasurer.measure(word, typography.wideStyle(candidate)).size.width > maxPx
                }) candidate -= 1f
            candidate
        }
        KkLabel(title, typography.wideStyle(size, lineHeightEm = 1.05f, color = color), Modifier.testTag("codex-detail-title"))
    }
}

/** Stat panel (`.panel`): effect label left (body), value right (wide, tabular). */
@Composable
private fun CodexStatPanel(label: String, value: String, valueColor: Color, scale: Float, panel: Color) {
    val typography = codexMeasurer().typography
    Row(Modifier.fillMaxWidth().background(panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(label, Modifier.weight(1f), style = typography.bodyStyle(16f * scale, color = Kk.Bone))
        BasicText(value, style = typography.wideStyle(22f * min(scale, 1.4f), tabular = true, color = valueColor))
    }
}

/** Display text in its role's uppercase with the original wording kept for semantics. */
@Composable
private fun KkLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, style = style)
}

/** Tag plate (`.tag`): face [background] (transparent + [line] = outlined), cond 800 label. */
@Composable
private fun CodexTag(text: String, background: Color, foreground: Color, scale: Float, line: Boolean = false, height: Dp = 22.dp) {
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
        style = typography.labelStyle(size * min(scale, 1.4f), trackingEm = 0.1f, color = foreground),
    )
}

@Composable
private fun CodexChip(value: String, description: String, modifier: Modifier) {
    val typography = codexMeasurer().typography
    BasicText(value, modifier.clearAndSetSemantics { contentDescription = description }
        .drawBehind { drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 8.dp.toPx()) }
        .padding(horizontal = 14.dp, vertical = 6.dp),
        style = typography.condStyle(21f, tabular = true, lineHeightEm = 1f, color = Kk.Bone), softWrap = false, maxLines = 1)
}

@Composable
private fun EmptyNotice(state: CodexEmptyState, scale: Float) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val (title, description) = when (state) {
        CodexEmptyState.NO_RUN -> language.text(SessionText.NO_RUN) to language.text(SessionText.NO_RUN_HELP)
        CodexEmptyState.EMPTY_SEARCH -> language.text(SessionText.NO_MATCHES) to language.text(SessionText.NO_MATCHES_HELP)
        else -> language.text(SessionText.EMPTY_INVENTORY) to language.text(SessionText.EMPTY_INVENTORY_HELP)
    }
    Row(Modifier.fillMaxWidth().background(Kk.Ink2).kkHatch().padding(20.dp).testTag("codex-empty-${state.name}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title.uppercase(), Modifier.weight(1f, fill = false), style = typography.condStyle(26f * min(scale, 1.4f), color = Kk.Bone))
        KkInfoButton(description, placement = KkTooltipPlacement.BELOW, textScale = min(scale, 1.25f))
    }
}

@Composable
private fun CodexNavigationTabs(tab: Int, controlScale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val language = LocalAppLanguage.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(SessionText.TAB_BUILD, SessionText.TAB_CATALOG, SessionText.TAB_SYNERGIES).map { language.text(it) }.forEachIndexed { index, title ->
            CodexTab(title, tab == index, "codex-tab-$index", controlScale) { onSelect(index) }
        }
    }
}

/** Tab (`.tab`): drawn uppercase; semantics keep the game's wording, role Tab and selection. */
@Composable
private fun CodexTab(text: String, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
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
        .size(width.dp, (height + 8f).dp)
        .drawBehind {
            drawKkTab(measurer, Rect(0f, 0f, size.width, height * density), text, selected, hovered && inputEnabled, focused && inputEnabled)
        })
}

/** Section row (`.lrow`) with a mono count, as a tab of the collection. */
@Composable
private fun CodexNavRow(text: String, count: String, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(KkTime.Pull, easing = KkEase.Pull), label = "codexNavRow")
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(text) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, role = Role.Tab, interactionSource = interactions, indication = null, onClick = onClick)
        .fillMaxWidth().height((46f * controlScale).dp)
        .drawBehind {
            drawKkListRow(measurer, Rect(Offset.Zero, size), text, count, selection, hovered || focused, titleSize = 24f / controlScale.coerceAtLeast(1f))
            if (focused) drawRect(Kk.Bone, style = kkStroke(2.dp.toPx()))
        })
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

@Composable
private fun CodexStatRow(name: String, value: String, selected: Boolean, tag: String, scale: Float, onClick: () -> Unit, contributions: @Composable () -> Unit) {
    val typography = codexMeasurer().typography
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth().background(if (selected) Kk.Ink3 else Kk.Ink2)) {
        Row(Modifier.fillMaxWidth().testTag(tag).semantics { this.selected = selected }
            .hoverable(interactions, enabled = inputEnabled)
            .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind { if (hovered || focused) drawRect(Kk.Bone.copy(alpha = 0.06f)) }
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(name, Modifier.weight(1f), style = typography.bodyStyle(16f * scale, color = Kk.Bone))
            BasicText(value, style = typography.wideStyle(20f * min(scale, 1.4f), tabular = true, color = Kk.Bone))
        }
        if (selected) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contributions() }
    }
}

@Composable
private fun CodexSearchField(value: String, enabled: Boolean, scale: Float, inlineLabel: Boolean, focusRequester: FocusRequester, dense: Boolean = false, onValueChange: (String) -> Unit) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    var focusedValue by remember { mutableStateOf(false) }
    BasicTextField(enabled = enabled, value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = typography.bodyStyle(15f * scale, FontWeight.Medium, color = Kk.Bone), cursorBrush = SolidColor(roles.you),
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
                    style = typography.labelStyle(14f * min(scale, 1.4f), color = Kk.Mute2))
                inner()
            }
        })
}

@Composable
private fun CodexRarityLegend(catalog: UiCatalogSnapshot, model: CodexRenderModel, scale: Float) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val counts = remember(catalog, model) {
        IntArray(ItemRarity.entries.size).also { counts ->
            catalog.items.forEach { item -> if (model.isDiscovered(item.id)) counts[item.rarity.ordinal]++ }
        }
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemRarity.entries.forEach { rarity ->
            LegendRow(rarity.displayLabel.localizedContent(language), counts[rarity.ordinal], Kk.rarity(rarity.rank), Kk.Bone, typography, scale)
        }
        LegendRow(language.text(SessionText.UNDISCOVERED), catalog.items.size - counts.sum(), null, Kk.Mute, typography, scale)
    }
}

@Composable
private fun LegendRow(label: String, count: Int, swatch: Color?, color: Color, typography: InterfaceTypography, scale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(14.dp).drawBehind {
            if (swatch != null) drawKkSheared(Rect(Offset.Zero, size), swatch)
            else {
                drawKkSheared(Rect(Offset.Zero, size), Kk.Ink3)
                drawKkHatch(Rect(Offset.Zero, size))
            }
        })
        BasicText(label, Modifier.weight(1f), style = typography.bodyStyle(15f * min(scale, 1.4f), color = color))
        BasicText(count.toString(), style = typography.monoStyle(11f * min(scale, 1.4f), color = Kk.Mute))
    }
}
