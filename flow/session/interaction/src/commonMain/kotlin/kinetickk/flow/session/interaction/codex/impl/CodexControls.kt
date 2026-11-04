// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.flow.session.interaction.localization.CodexText
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min

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
internal fun CodexScrollCue(scroll: ScrollState, face: Color, accent: Color, modifier: Modifier, barEnd: Dp = (-10).dp) {
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
internal fun CodexMaterialTag(text: String, material: KkCardMaterial, ui: Float, height: Dp = 22.dp) {
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
internal fun CodexChip(value: String, description: String, ui: Float, modifier: Modifier, label: String? = null) {
    val typography = codexMeasurer().typography
    Row(modifier.clearAndSetSemantics { contentDescription = description }
        .drawBehind { drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 8.dp.toPx()) }
        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let { BasicText(it.uppercase(), style = typography.monoStyle(10f * ui, color = Kk.Mute), softWrap = false, maxLines = 1) }
        BasicText(value, style = typography.condStyle(21f * ui, tabular = true, lineHeightEm = 1f, color = Kk.Bone), softWrap = false, maxLines = 1)
    }
}

@Composable
internal fun EmptyNotice(state: CodexEmptyState, ui: Float, dense: Boolean = false) {
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

/** Toggle button for the effective stats list (bone slab when expanded). */
@Composable
internal fun CodexToggleButton(text: String, selected: Boolean, tag: String, controlScale: Float, onClick: () -> Unit) {
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
internal fun CodexStatRow(name: String, value: String, selected: Boolean, tag: String, ui: Float, fit: CodexPairFit, onClick: () -> Unit, contributions: @Composable () -> Unit) {
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
