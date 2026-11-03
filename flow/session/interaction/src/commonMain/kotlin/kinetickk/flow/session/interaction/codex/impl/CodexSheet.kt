// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.foundation.design.*
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kinetickk.flow.session.interaction.codex.api.*
import androidx.compose.runtime.saveable.SaveableStateHolder

@Composable
internal fun CodexDetails(entry: CodexEntry?, catalog: UiCatalogSnapshot, ui: Float, modifier: Modifier, panel: Color = Kk.Ink2, face: Color = Kk.Ink1,
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
internal fun rememberCodexPairFit(rows: List<Pair<String, String>>, width: Dp, gap: Dp, labelSize: Float, valueStyle: TextStyle,
    labelStyle: (Float) -> TextStyle): CodexPairFit {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer(16)
    val density = LocalDensity.current
    return remember(rows, width, gap, labelSize, valueStyle, density, textMeasurer) {
        codexPairFit(rows, with(density) { width.toPx() }, with(density) { gap.toPx() }, labelSize, labelSize * 0.6f,
            { word, size -> textMeasurer.measure(word, labelStyle(size), softWrap = false).size.width.toFloat() },
            { value -> textMeasurer.measure(value, valueStyle, softWrap = false).size.width.toFloat() })
    }
}

/** Phones: the selected entry's details in a bottom sheet over a scrim; Escape, the scrim and Close restore focus. */
@Composable
internal fun CodexDetailSheet(
    selected: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    availableWidth: Dp,
    availableHeight: Dp,
    detailsScrollHolder: SaveableStateHolder,
    residue: StatusElement?,
    setResidue: (StatusElement?, String?) -> Unit,
    restoreFocus: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
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
