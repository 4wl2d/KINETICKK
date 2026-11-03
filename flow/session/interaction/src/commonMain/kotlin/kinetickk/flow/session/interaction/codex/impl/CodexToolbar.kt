// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.TextResource
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.*

/** Natural width of the filter segments (plus their 3 dp gaps). */
@Composable
internal fun codexFiltersWidth(labels: List<String>): Dp {
    val measurer = codexMeasurer()
    val density = LocalDensity.current.density
    return remember(measurer, labels, density) {
        (labels.sumOf { (kkSegmentWidth(measurer, it, density) / density).toDouble() }.toFloat() + 3f * (labels.size - 1)).dp
    }
}

/**
 * The game's filters (All / In build / Active) as a segmented control; In build waits for a run. Like the sort, the
 * segments shrink together to fit [available] dp ([codexSortFit]), so none is cut at the column's edge.
 */
@Composable
internal fun CodexFilters(
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
internal fun CodexSortSegments(sort: CodexItemSort, controlScale: Float, ui: Float, available: Dp, withLabel: Boolean, onSelect: (CodexItemSort) -> Unit) {
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

@Composable
internal fun CodexSearchField(value: String, enabled: Boolean, ui: Float, inlineLabel: Boolean, focusRequester: FocusRequester, dense: Boolean = false, onValueChange: (String) -> Unit) {
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
