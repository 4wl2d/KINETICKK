// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.interaction.audio.SessionAudioCue
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.foundation.design.*
import kinetickk.resource.audio.api.AudioService

class DefaultCodexFeature(
    private val profilePort: ProfileReadPort,
    private val uiCatalog: UiCatalogSnapshot,
    audioService: AudioService,
) : CodexFeature {
    private val audioExecutor = SessionAudioExecutor(audioService)
    private val reducer = CodexReducer(uiCatalog.items)

    @Composable
    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
        CodexContent(
            catalog = uiCatalog,
            model = reducer.renderModel(profilePort.query(ProfileQuery.GetCollection), runStacks),
            progress = profilePort.query(ProfileQuery.GetHomeProgress),
            scale = profilePort.query(ProfileQuery.GetPreferences).preferences.textScale,
            onClose = {
                audioExecutor.play(SessionAudioCue.UI_CLICK)
                onOutput(CodexOutput.Back)
            },
        )
    }
}

private enum class CodexGridContentType { NOTICE, HEADING, SLOT, STAT, SYNERGY }

private val LocalCodexInputEnabled = staticCompositionLocalOf { true }
private val CodexBackground = Color(0xFF080A17)
private val CodexPanel = Color(0xFF111A2B)
private val SelectionSaver = listSaver<CodexSelection, Any>(
    save = { listOf(it.pinnedKey.orEmpty(), it.sheetOpen) },
    restore = { CodexSelection(pinnedKey = (it[0] as String).ifEmpty { null }, sheetOpen = it[1] as Boolean) },
)

/** All navigation, search, expansion and selection here belong to this local Compose lifetime. */
@Composable
internal fun CodexContent(catalog: UiCatalogSnapshot, model: CodexRenderModel, progress: HomeProgressProjection, scale: Float, onClose: () -> Unit) {
    val language = LocalAppLanguage.current
    var tabValue by rememberSaveable { mutableIntStateOf(if (model.runStacks.build == null) 1 else 0) }
    var categoryValue by rememberSaveable { mutableIntStateOf(0) }
    var searchValue by rememberSaveable { mutableStateOf("") }
    var filterValue by rememberSaveable { mutableIntStateOf(0) }
    var selectionValue by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(CodexSelection()) }
    var statsExpandedValue by rememberSaveable { mutableStateOf(false) }
    var statValue by rememberSaveable { mutableStateOf<String?>(null) }
    val build = model.runStacks.build
    val filter = CodexItemFilter.entries[filterValue]
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
                    "empty-relic/$index", language.text(SessionText.EMPTY_RELIC_SLOT, index + 1), language.text(SessionText.EMPTY_RELIC_HELP), language.text(SessionText.RELIC_SLOT), "—", language.text(SessionText.EMPTY), CodexIcon.Empty, Muted,
                ))
            }
            addAll(model.items.filter { model.itemStack(it.id) > 0 }.map { codexItemEntry(it, model, language) })
        }
        1 -> codexCatalogEntries(category, search, filter, model, catalog, progress, language)
        else -> catalog.synergies.map { synergy ->
            val owned = build?.relics?.map { it.id }?.toSet().orEmpty()
            val components = codexSynergyComponents(synergy, catalog, owned)
            val summary = build?.synergies?.firstOrNull { it.id == synergy.id.name }
            val active = summary?.active == true
            val required = synergy.requiredAspect?.let { language.text(SessionText.SYNERGY_REQUIREMENT, it.displayLabel.localizedContent(language)) }
                ?: synergy.requiredRelics.joinToString(" + ") { catalog.relic(it).name.localizedContent(language) }
            CodexEntry("synergy/${synergy.id}", synergy.name.localizedContent(language), "${synergy.description.localizedContent(language)}\n\n${language.text(SessionText.REQUIRES, required)}" +
                (summary?.missingComponents?.takeIf { it.isNotEmpty() }?.let { missing -> "\n" + language.text(SessionText.MISSING, missing.joinToString { it.localizedContent(language) }) } ?: ""),
                synergy.requiredAspect?.displayLabel?.localizedContent(language) ?: language.text(SessionText.COMBINATION), if (active) "✓" else "—",
                if (active) language.text(SessionText.ACTIVE) else if (build == null) language.text(SessionText.NO_RUN_INACTIVE) else language.text(SessionText.INACTIVE),
                CodexIcon.Synergy(components), if (active) Acid else synergy.requiredAspect?.let(::relicAspectColor) ?: Violet)
        }
    }
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
    BoxWithConstraints(Modifier.fillMaxSize().background(CodexBackground).testTag("codex")) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val wide = codexUsesSidePanel(availableWidth.value, availableHeight.value)
        val compactHeader = availableHeight < 480.dp
        val selected = entries.firstOrNull { it.key == selectionValue.pinnedKey }
        val sheetVisible = !wide && selectionValue.sheetOpen && selected != null
        CompositionLocalProvider(LocalCodexInputEnabled provides !sheetVisible) {
        Column(Modifier.fillMaxSize().padding(12.dp).then(if (sheetVisible) Modifier.clearAndSetSemantics { } else Modifier).onPreviewKeyEvent {
            if (it.key == Key.Escape) {
                if (it.type == KeyEventType.KeyDown) onClose()
                true
            } else false
        }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (compactHeader) CodexNavigationTabs(tab, scale, Modifier.weight(1f)) { tabValue = it }
                else CodexLabel(language.text(SessionText.CODEX), scale, Cyan, bold = true)
                CodexButton(language.text(SessionText.CLOSE), scale, "codex-close", onClick = onClose)
            }
            if (!compactHeader) CodexNavigationTabs(tab, scale, Modifier.fillMaxWidth()) { tabValue = it }
            if (tab == 1) {
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(language.text(SessionText.ITEMS_TITLE), language.text(SessionText.WEAPONS_TITLE), language.text(SessionText.RELICS_TITLE), language.text(SessionText.FORMS_TITLE)).forEachIndexed { index, title ->
                        CodexButton(title, scale, "codex-category-$index", category == index, role = Role.Tab) { categoryValue = index }
                    }
                    if (compactHeader && category == 0) {
                        Spacer(Modifier.width(8.dp))
                        listOf(SessionText.FILTER_ALL, SessionText.FILTER_DISCOVERED, SessionText.FILTER_BUILD).map { language.text(it) }.forEachIndexed { index, title ->
                            CodexButton(title, scale, "codex-filter-$index", filterValue == index, enabled = index != 2 || build != null) { filterValue = index }
                        }
                    }
                }
                BasicTextField(enabled = !sheetVisible, value = search, onValueChange = { searchValue = codexSearchInput(it) }, singleLine = true,
                    textStyle = TextStyle(color = White, fontSize = (13f * scale).sp), cursorBrush = SolidColor(Cyan),
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(CodexPanel).border(1.dp, DarkLine)
                        .focusRequester(searchFocus).testTag("codex-search").semantics { contentDescription = language.text(SessionText.SEARCH_DESCRIPTION) }.padding(12.dp),
                    decorationBox = { inner -> Box { if (search.isEmpty()) CodexLabel(language.text(SessionText.SEARCH_PLACEHOLDER), scale, Muted); inner() } })
                if (category == 0 && !compactHeader) Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(SessionText.FILTER_ALL, SessionText.FILTER_DISCOVERED, SessionText.FILTER_BUILD).map { language.text(it) }.forEachIndexed { index, title ->
                        CodexButton(title, scale, "codex-filter-$index", filterValue == index, enabled = index != 2 || build != null) { filterValue = index }
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    scrollHolder.SaveableStateProvider(if (tab == 1) "$tab/$category" else "$tab") {
                        val grid = rememberLazyGridState()
                        var priorKeysValue by rememberSaveable { mutableStateOf<String?>(null) }
                        val resultKey = entries.joinToString("|") { it.key }
                        LaunchedEffect(resultKey) {
                            if (priorKeysValue != null && priorKeysValue != resultKey) grid.scrollToItem(0)
                            priorKeysValue = resultKey
                        }
                        LazyVerticalGrid(GridCells.Adaptive(80.dp), Modifier.fillMaxSize().focusRequester(listFocus).focusable(enabled = !sheetVisible).testTag("codex-grid"),
                            state = grid, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            if (emptyState != CodexEmptyState.NONE) item(key = "empty", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) {
                                EmptyNotice(emptyState, scale)
                            }
                            if (tab == 0 && build != null) {
                                fun section(key: String, title: String, sectionEntries: List<CodexEntry>) {
                                    item(key = "heading/$key", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) { CodexLabel(title, scale, Cyan, bold = true, modifier = Modifier.padding(top = 10.dp)) }
                                    items(sectionEntries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
                                        CodexSlot(entry, catalog, scale, selectionValue, slotFocus, { selectionValue = it(selectionValue) })
                                    }
                                }
                                section("character", language.text(SessionText.CHARACTER), entries.filter { it.icon is CodexIcon.Shape })
                                section("weapon", language.text(SessionText.WEAPON_LEVEL, build.weaponLevel), entries.filter { it.icon is CodexIcon.Weapon })
                                section("relics", language.text(SessionText.RELIC_COUNT, build.relics.size, catalog.relicPolicy.maxSlots), entries.filter { it.icon is CodexIcon.Relic || it.icon == CodexIcon.Empty })
                                section("items", language.text(SessionText.ITEMS), entries.filter { it.icon is CodexIcon.Item })
                                if (entries.none { it.icon is CodexIcon.Item }) item(key = "empty-inventory", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) { EmptyNotice(CodexEmptyState.EMPTY_INVENTORY, scale) }
                                item(key = "stats-heading", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
                                    CodexButton(language.text(SessionText.EFFECTIVE_STATS, if (statsExpandedValue) "▾" else "▸"), scale, "codex-stats", statsExpandedValue) { statsExpandedValue = !statsExpandedValue }
                                }
                                if (statsExpandedValue) items(build.stats, key = { "stat/${it.name}" }, contentType = { CodexGridContentType.STAT }, span = { GridItemSpan(maxLineSpan) }) { stat ->
                                    Column(Modifier.fillMaxWidth().background(CodexPanel).padding(8.dp)) {
                                        CodexButton("${stat.name.localizedContent(language)}  ${codexNumber(stat.value, language)}${stat.unit.localizedContent(language)}", scale, "codex-stat-${stat.name}", statValue == stat.name) { statValue = if (statValue == stat.name) null else stat.name }
                                        if (statValue == stat.name) stat.contributions.forEach { contribution ->
                                            val name = when (contribution.source) {
                                                BuildStatSource.CHARACTER -> language.text(SessionText.CHARACTER_SOURCE)
                                                BuildStatSource.LAB -> language.text(SessionText.LAB_SOURCE)
                                                BuildStatSource.ITEMS -> language.text(SessionText.ITEMS_TITLE)
                                                BuildStatSource.RELICS -> language.text(SessionText.RELICS_TITLE)
                                                BuildStatSource.SYNERGIES -> language.text(SessionText.SYNERGIES_SOURCE)
                                                BuildStatSource.MASTERY -> language.text(SessionText.MASTERY_SOURCE)
                                                BuildStatSource.TEMPORARY -> language.text(SessionText.TEMPORARY_SOURCE)
                                            }
                                            CodexLabel("$name  ${codexNumber(contribution.amount, language)}${stat.unit.localizedContent(language)}", scale)
                                        }
                                    }
                                }
                            } else if (tab == 2) {
                                item(key = "synergy-help", contentType = CodexGridContentType.NOTICE, span = { GridItemSpan(maxLineSpan) }) { CodexLabel(language.text(SessionText.SYNERGY_HELP), scale, Muted) }
                                items(entries, key = { it.key }, contentType = { CodexGridContentType.SYNERGY }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                                    SynergySlot(entry, catalog, model, scale, selectionValue, slotFocus) { selectionValue = it(selectionValue) }
                                }
                            } else items(entries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
                                CodexSlot(entry, catalog, scale, selectionValue, slotFocus) { selectionValue = it(selectionValue) }
                            }
                        }
                    }
                }
                if (wide) Box(Modifier.width(330.dp).fillMaxHeight().background(CodexPanel).border(1.dp, DarkLine).testTag("codex-side-panel")) {
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
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).testTag("codex-scrim")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = restoreFocus))
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(availableHeight * 0.85f).background(CodexPanel).border(2.dp, selected.color).testTag("codex-sheet")
                        .pointerInput(Unit) { detectTapGestures { } }) {
                        Row(Modifier.fillMaxWidth().background(CodexBackground).padding(8.dp), horizontalArrangement = Arrangement.End) {
                            CodexButton(language.text(SessionText.CLOSE), scale, "codex-sheet-close", onClick = restoreFocus)
                        }
                        detailsScrollHolder.SaveableStateProvider(selected.key) {
                            CodexDetails(selected, catalog, scale, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
        }
    }
}

@Composable
private fun CodexSlot(entry: CodexEntry, catalog: UiCatalogSnapshot, scale: Float, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, onSelection: ((CodexSelection) -> CodexSelection) -> Unit) {
    Box(slotModifier(entry, selection, focus, onSelection).aspectRatio(1f).padding(6.dp)) {
        CodexIcon(entry.icon, catalog, entry.color, Modifier.align(Alignment.Center).fillMaxSize().padding(8.dp))
        if (entry.rarity > 0) BasicText("•".repeat(entry.rarity), Modifier.align(Alignment.TopStart), style = TextStyle(color = entry.color, fontSize = 10.sp))
        if (!entry.discovered) BasicText("?", Modifier.align(Alignment.TopEnd), style = TextStyle(color = White, fontSize = 12.sp))
        BasicText(entry.quantity, Modifier.align(Alignment.BottomEnd).background(CodexBackground).padding(horizontal = 2.dp), style = TextStyle(color = White, fontSize = (10f * scale).sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun slotModifier(entry: CodexEntry, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, onSelection: ((CodexSelection) -> CodexSelection) -> Unit): Modifier {
    val inputEnabled = LocalCodexInputEnabled.current
    val requester = remember(entry.key) { FocusRequester() }
    val interaction = remember(entry.key) { MutableInteractionSource() }
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
    val highlighted = selection.previewKey == entry.key || selection.pinnedKey == entry.key
    return Modifier.background(if (highlighted) Color(0xFF25344B) else CodexPanel)
        .border(if (highlighted) 2.dp else 1.dp, if (highlighted) entry.color else entry.color.copy(alpha = 0.5f))
        .testTag("codex-slot-${entry.key}").semantics { contentDescription = "${entry.title} · ${entry.kind} · ${entry.quantity} · ${entry.availability}"; selected = selection.pinnedKey == entry.key }
        .focusRequester(requester).focusProperties { canFocus = inputEnabled }.onFocusChanged { state ->
            onSelection { current ->
                if (state.isFocused) current.copy(focusKey = entry.key, hoverKey = null)
                else if (current.focusKey == entry.key) current.copy(focusKey = null) else current
            }
        }.hoverable(interaction, enabled = inputEnabled).clickable(enabled = inputEnabled, interactionSource = interaction, indication = null, role = Role.Button) { onSelection { it.activate(entry.key) } }
}

@Composable
private fun SynergySlot(entry: CodexEntry, catalog: UiCatalogSnapshot, model: CodexRenderModel, scale: Float, selection: CodexSelection, focus: MutableMap<String, FocusRequester>, onSelection: ((CodexSelection) -> CodexSelection) -> Unit) {
    Column(slotModifier(entry, selection, focus, onSelection).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CodexLabel(entry.title, scale, entry.color, bold = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            (entry.icon as CodexIcon.Synergy).components.forEachIndexed { index, id ->
                if (index != 0) CodexLabel("+", scale, Muted)
                val relic = id?.let(catalog::relic)
                val present = id != null && model.runStacks.build?.relics?.any { it.id == id } == true
                Box(Modifier.size(64.dp).background(CodexBackground).border(1.dp, if (present) Acid else DarkLine)) {
                    CodexIcon(relic?.let { CodexIcon.Relic(it, null) } ?: CodexIcon.Empty, catalog, relic?.let { relicAspectColor(it.aspect) } ?: entry.color, Modifier.fillMaxSize().padding(10.dp))
                    BasicText(if (present) "✓" else "—", Modifier.align(Alignment.BottomEnd).padding(3.dp), style = TextStyle(color = if (present) Acid else Muted, fontSize = 12.sp))
                }
            }
        }
        CodexLabel(entry.availability, scale, entry.color)
    }
}

@Composable
private fun CodexDetails(entry: CodexEntry?, catalog: UiCatalogSnapshot, scale: Float, modifier: Modifier) {
    val language = LocalAppLanguage.current
    val scroll = rememberScrollState()
    Column(modifier.testTag("codex-details-scroll").verticalScroll(scroll).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (entry == null) {
            CodexLabel(language.text(SessionText.SELECT_SLOT), scale, Cyan, bold = true)
            CodexLabel(language.text(SessionText.SELECT_SLOT_HELP), scale, Muted)
        } else {
            CodexIcon(entry.icon, catalog, entry.color, Modifier.align(Alignment.CenterHorizontally).size(160.dp))
            CodexLabel(entry.title, scale, entry.color, bold = true, modifier = Modifier.testTag("codex-detail-title"))
            val quantity = when (entry.icon) {
                is CodexIcon.Relic -> language.text(SessionText.RANK_QUANTITY, entry.quantity)
                is CodexIcon.Item -> language.text(SessionText.STACK_QUANTITY, entry.quantity)
                else -> entry.quantity
            }
            CodexLabel("${entry.kind} · $quantity", scale, entry.color)
            CodexLabel(entry.availability, scale, White)
            CodexLabel(entry.description, scale, White)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CodexIcon(icon: CodexIcon, catalog: UiCatalogSnapshot, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.38f
        when (icon) {
            is CodexIcon.Item -> drawItemIcon(icon.definition, center, radius, color, stack = icon.stack)
            is CodexIcon.Relic -> drawRelicIcon(icon.definition, catalog.relicPolicy, center, radius, rank = icon.rank, time = 0f)
            is CodexIcon.Weapon -> drawSystemGlyph(SystemGlyphStyle.entries[icon.id.ordinal], center, radius, 0f, color)
            is CodexIcon.Shape -> when (icon.id) {
                CoreShape.ORB -> drawCircle(color, radius, center, style = Stroke(radius * 0.1f))
                CoreShape.RING -> { drawCircle(color, radius, center, style = Stroke(radius * 0.14f)); drawCircle(White, radius * 0.6f, center, style = Stroke(radius * 0.05f)) }
                CoreShape.PRISM -> drawPolygon(center, radius, 4, 0.7853982f, color, Stroke(radius * 0.1f))
                CoreShape.SHARD -> drawPolygon(center, radius, 3, -1.5707964f, color, Stroke(radius * 0.1f))
                CoreShape.DIAMOND -> drawPolygon(center, radius, 4, 0f, color, Stroke(radius * 0.1f))
                CoreShape.TESSERACT -> { drawPolygon(center, radius, 4, 0.7853982f, color, Stroke(radius * 0.1f)); drawPolygon(center, radius * 0.62f, 4, 0.7853982f, White, Stroke(radius * 0.06f)) }
            }
            is CodexIcon.Synergy -> icon.components.forEachIndexed { index, id ->
                val point = center.copy(x = size.width * (if (index == 0) 0.26f else 0.74f))
                if (id == null) drawCircle(color, radius * 0.53f, point, style = Stroke(radius * 0.07f))
                else drawRelicIcon(catalog.relic(id), catalog.relicPolicy, point, radius * 0.53f, time = 0f)
            }
            CodexIcon.Empty -> { drawCircle(color.copy(alpha = 0.35f), radius, center, style = Stroke(radius * 0.06f)); drawLine(color, center.copy(x = center.x - radius * 0.4f), center.copy(x = center.x + radius * 0.4f), radius * 0.06f) }
        }
    }
}

@Composable
private fun EmptyNotice(state: CodexEmptyState, scale: Float) {
    val language = LocalAppLanguage.current
    val (title, description) = when (state) {
        CodexEmptyState.NO_RUN -> language.text(SessionText.NO_RUN) to language.text(SessionText.NO_RUN_HELP)
        CodexEmptyState.EMPTY_SEARCH -> language.text(SessionText.NO_MATCHES) to language.text(SessionText.NO_MATCHES_HELP)
        else -> language.text(SessionText.EMPTY_INVENTORY) to language.text(SessionText.EMPTY_INVENTORY_HELP)
    }
    Column(Modifier.fillMaxWidth().background(CodexPanel).padding(20.dp).testTag("codex-empty-${state.name}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CodexLabel(title, scale, Cyan, bold = true)
        CodexLabel(description, scale, Muted)
    }
}

@Composable
private fun CodexNavigationTabs(tab: Int, scale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val language = LocalAppLanguage.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(SessionText.TAB_BUILD, SessionText.TAB_CATALOG, SessionText.TAB_SYNERGIES).map { language.text(it) }.forEachIndexed { index, title ->
            CodexButton(title, scale, "codex-tab-$index", tab == index, role = Role.Tab) { onSelect(index) }
        }
    }
}

@Composable
private fun CodexButton(text: String, scale: Float, tag: String, selected: Boolean = false, enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit) {
    val inputEnabled = enabled && LocalCodexInputEnabled.current
    BasicText(text, Modifier.testTag(tag).semantics { this.selected = selected }.background(if (selected) Color(0xFF263A51) else CodexPanel)
        .border(1.dp, if (selected) Cyan else DarkLine).clickable(enabled = inputEnabled, role = role, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp), style = TextStyle(color = if (!inputEnabled) Muted.copy(alpha = 0.5f) else if (selected) Cyan else White, fontSize = (12f * scale).sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun CodexLabel(text: String, scale: Float, color: Color = White, bold: Boolean = false, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = ((if (bold) 14f else 12f) * scale).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
}
