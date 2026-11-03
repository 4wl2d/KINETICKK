// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.TextResource
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.*
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import androidx.compose.foundation.text.BasicText
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.flow.session.interaction.codex.api.*

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
internal fun CodexNavigationTabs(tab: CodexTab, controlScale: Float, compact: Boolean, modifier: Modifier, onSelect: (CodexTab) -> Unit) {
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
internal fun CodexTabRow(
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
internal fun CodexNavRow(text: String, count: String, titleSize: Float, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
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

/** Wide screens' left nav: section rows with counts, Search, the game's filters, rarity legend. */
@Composable
internal fun CodexCatalogNav(
    catalog: UiCatalogSnapshot,
    model: CodexRenderModel,
    measurer: CanvasTextMeasurer,
    ui: Float,
    controlScale: Float,
    navWidth: Dp,
    categoryTitles: List<String>,
    categoryCounts: (Int) -> Pair<Int, Int>,
    category: Int,
    onCategory: (Int) -> Unit,
    search: String,
    sheetVisible: Boolean,
    searchFocus: FocusRequester,
    onSearch: (String) -> Unit,
    filterTitles: List<Pair<Int, SessionText>>,
    filter: CodexItemFilter,
    hasBuild: Boolean,
    onFilter: (Int) -> Unit,
) {
    val language = LocalAppLanguage.current
    val typography = measurer.typography
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
                CodexNavRow(title, navCounts[index], navTitleSize, category == index, "codex-category-$index", controlScale) { onCategory(index) }
            }
            Spacer(Modifier.height(18.dp))
            BasicText(language.text(SessionRedesignText.SEARCH).uppercase(), style = typography.labelStyle(13f * ui, color = Kk.Mute))
            Spacer(Modifier.height(4.dp))
            CodexSearchField(search, !sheetVisible, ui, inlineLabel = false, searchFocus) { onSearch(it) }
            if (category == 0) {
                Spacer(Modifier.height(10.dp))
                CodexFilters(filterTitles, filter, hasBuild, controlScale, available = navWidth) { onFilter(it) }
                Spacer(Modifier.height(18.dp))
                BasicText(language.text(SessionRedesignText.RARITY).uppercase(), style = typography.labelStyle(13f * ui, color = Kk.Mute))
                CodexRarityLegend(catalog, model, ui)
            }
        }
        // Where the nav is taller than the screen, its cut edge fades out beside a scroll bar.
        CodexScrollCue(navScroll, Kk.Ink, Kk.Bone, Modifier.matchParentSize().testTag("codex-nav-scroll-cue"))
    }
}
