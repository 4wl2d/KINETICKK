// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkInfoButton
import kinetickk.foundation.design.KkTooltipPlacement
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.bodyStyle
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkHatch
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.drawKkSlab
import kinetickk.foundation.design.drawKkTab
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kotlin.math.max

/**
 * Section heading of a grid: label and value as separate texts, an optional gold [badge] (the
 * Rebirth tier) and an optional (!) beside them.
 */
@Composable
internal fun CodexSectionHeading(title: String, ui: Float, value: String? = null, info: String? = null, badge: String? = null) {
    val typography = codexMeasurer().typography
    Row(Modifier.padding(top = 12.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val headingStyle = typography.labelStyle(14f * ui, color = Kk.Mute)
        BasicText(title.uppercase(), style = headingStyle)
        value?.let { BasicText(it.uppercase(), style = headingStyle) }
        badge?.let { CodexTag(it, Kk.Gold, Kk.Ink, ui) }
        info?.let { KkInfoButton(it, placement = KkTooltipPlacement.BELOW, textScale = ui) }
    }
}

/**
 * An archetype or element row: glyph, name, a tick ladder notched at the tier thresholds and three
 * tier pips. With a run the ladder lights to the build's points; without one it shows the steps.
 */
@Composable
internal fun CodexMeterRow(
    entry: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    showDescription: Boolean,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val (glyph, tone, points, tier, thresholds, dominant) = when (val icon = entry.icon) {
        is CodexIcon.Tag -> MeterSpec(icon.tag.glyph(), Kk.Bone, icon.points, icon.tier, catalog.build.policy.resonanceThresholds, false)
        is CodexIcon.Affinity -> MeterSpec(icon.element.glyph(), roles.of(icon.element), icon.affinity, icon.tier, catalog.build.policy.affinityThresholds,
            icon.dominant)
        else -> return
    }
    val count = max(thresholds.last(), points ?: 0)
    // Rows of one grid line share a height: the minimum fits a two-line description.
    val minHeight = (if (showDescription) 80f else 58f) * max(1f, ui)
    val tick = if (showDescription) 13f else 11f
    Box(slotModifier(entry, selection, focus, interaction, onSelection).fillMaxWidth().heightIn(min = minHeight.dp)
        .drawBehind {
            drawKkSlab(Rect(Offset.Zero, size), if (hovered || focused) Kk.Ink3 else Kk.Ink2, 9.dp.toPx())
            if (tier > 0) drawKkSheared(Rect(0f, 0f, 4.dp.toPx(), size.height), tone)
            drawCodexSelection(selection.pinnedKey == entry.key, selection.previewKey == entry.key || focused, roles)
        }
        .padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(30.dp)) {
                if (dominant) drawCircle(tone, size.minDimension * 0.5f, style = kkStroke(2.dp.toPx()))
                drawKkIcon(glyph, center, size.minDimension * if (dominant) 0.62f else 0.82f, tone)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(entry.title.uppercase(), style = typography.condStyle(20f * ui, color = Kk.Bone), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (showDescription) {
                    val line = entry.description.substringBefore("\n\n")
                    BasicText(line, style = typography.bodyStyle(13f * ui, lineHeightEm = 1.25f, color = Kk.Bone2), maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.width((count * tick).dp).height(17.dp)) {
                    drawCodexLadder(Rect(Offset.Zero, size), count, points ?: 0, thresholds, tone)
                }
                Canvas(Modifier.width(48.dp).height(10.dp)) {
                    drawCodexTierPips(Offset(size.width - (3 * 10.dp.toPx() + 2 * 4.dp.toPx()), 0f), 3, tier, tone, 10.dp.toPx())
                }
            }
        }
    }
}

private data class MeterSpec(
    val glyph: KkIcon,
    val tone: Color,
    val points: Int?,
    val tier: Int,
    val thresholds: List<Int>,
    val dominant: Boolean,
)

/**
 * Relic keys as chips: opposed keys share one split plate (a threat bar between them), the others
 * stand alone. Groups sit further apart than the segments of a plate, so each group reads as one.
 * Each key is its own selectable segment.
 */
@Composable
internal fun CodexKeyChips(
    groups: List<List<CodexEntry>>,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { group ->
            val joined = group.size > 1
            Row(Modifier.drawBehind {
                drawKkSlab(Rect(Offset.Zero, size), Kk.Ink2, 7.dp.toPx())
                drawKkSlab(Rect(0.75f, 0.75f, size.width - 0.75f, size.height - 0.75f),
                    if (joined) roles.threat.copy(alpha = 0.6f) else Kk.Line2, 7.dp.toPx(), style = kkStroke(1.5.dp.toPx()))
            }, verticalAlignment = Alignment.CenterVertically) {
                group.forEachIndexed { index, entry ->
                    if (index > 0) CodexOpposedBar(ui)
                    CodexKeySegment(entry, ui, selection, focus, onSelection)
                }
            }
        }
    }
}

@Composable
private fun CodexOpposedBar(ui: Float) {
    val roles = LocalKkRolePalette.current
    Canvas(Modifier.width(6.dp).height((20f * ui).dp)) {
        drawKkSheared(Rect(size.width * 0.5f - 1.5.dp.toPx(), 0f, size.width * 0.5f + 1.5.dp.toPx(), size.height), roles.threat)
    }
}

@Composable
private fun CodexKeySegment(
    entry: CodexEntry,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    BasicText(entry.title.uppercase(),
        slotModifier(entry, selection, focus, interaction, onSelection).heightIn(min = (30f * ui).dp).drawBehind {
            // The plate behind the group is the chip; a segment only lights its own part.
            if (hovered || focused) drawRect(Kk.Ink3, Offset(3.dp.toPx(), 3.dp.toPx()), Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()))
            drawCodexSelection(selection.pinnedKey == entry.key, selection.previewKey == entry.key || focused, roles)
        }.padding(horizontal = 14.dp, vertical = 6.dp),
        style = typography.labelStyle(14f * ui, trackingEm = 0.1f, color = Kk.Bone), softWrap = false, maxLines = 1)
}

/**
 * A relic pair, or a known incompatibility: the two medallions and their link, the pairing name
 * (or "?" until discovered), the relic names and the keys that predict it.
 */
@Composable
internal fun CodexPairingRow(
    entry: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    /** Whether the row carries its kind tag: only where one list mixes kinds. */
    showKind: Boolean,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pairing = entry.icon as? CodexIcon.Pairing
    val conflict = entry.icon as? CodexIcon.Conflict
    val interferes = pairing?.kind?.interferes ?: true
    val tone = pairing?.let { codexLinkTone(it.kind, roles) } ?: roles.threat
    // Rows of one grid line share a height: the minimum fits a title and two lines of names.
    Box(slotModifier(entry, selection, focus, interaction, onSelection).fillMaxWidth().heightIn(min = (76f * max(1f, ui)).dp)
        .drawBehind {
            val rect = Rect(Offset.Zero, size)
            drawKkSlab(rect, if (hovered || focused) Kk.Ink3 else Kk.Ink2, 9.dp.toPx())
            // A faint hatch marks interference; the stripe and the link carry the kind.
            if (interferes) drawRect(kkFillBrush(KkFillKind.HATCH, roles.threat.copy(alpha = if (pairing == null || pairing.kind.named) 0.13f else 0.07f)),
                Offset.Zero, size)
            drawKkSheared(Rect(0f, 0f, if (pairing?.kind?.named != false) 4.dp.toPx() else 2.dp.toPx(), size.height), tone)
            drawCodexSelection(selection.pinnedKey == entry.key, selection.previewKey == entry.key || focused, roles)
        }
        .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pairing != null) {
                Canvas(Modifier.width(92.dp).height(44.dp)) { drawCodexPairingDiagram(pairing, catalog, measurer) }
            } else if (conflict != null) {
                Canvas(Modifier.width(92.dp).height(44.dp)) {
                    val item = conflict.item
                    val relic = conflict.relics.firstOrNull()
                    val radius = size.height * 0.42f
                    if (item != null) drawItemIcon(item, Offset(radius + 2.dp.toPx(), size.height * 0.5f), radius * 0.9f, codexItemColor(item, roles))
                    if (relic != null) drawRelicIcon(relic, catalog.relicPolicy, Offset(size.width - radius - 2.dp.toPx(), size.height * 0.5f), radius, roles = roles)
                    else drawKkIcon(KkIcon.SYSTEM_DISMANTLED, Offset(size.width - radius - 2.dp.toPx(), size.height * 0.5f), radius * 1.3f, roles.threat)
                    drawLine(roles.threat, Offset(radius * 2.2f, size.height * 0.3f), Offset(size.width - radius * 2.2f, size.height * 0.7f), 2.5.dp.toPx())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val title = if (pairing != null && pairing.kind.named && !pairing.discovered) "?" else entry.title
                BasicText(title.uppercase(), style = typography.condStyle(20f * ui, color = if (interferes) Kk.Bone else tone), maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                val names = when {
                    pairing != null -> "${pairing.first.name.localizedContent(language)} + ${pairing.second.name.localizedContent(language)}"
                    conflict != null -> conflict.relics.joinToString(" + ") { it.name.localizedContent(language) }
                    else -> ""
                }
                // Relic names wrap between words as far as they need: never cut.
                if (names.isNotBlank()) BasicText(names.uppercase(), Modifier.testTag("codex-pairing-names"),
                    style = typography.monoStyle(10f * ui, lineHeightEm = 1.35f, color = Kk.Mute))
            }
            if (showKind) CodexTag(entry.kind, if (interferes) roles.threat else Kk.Bone, Kk.Ink, ui)
        }
    }
}

/** Reachable reactions of a run as chips: two-tone with the name once discovered, glyphs and "?" until then. */
@Composable
internal fun CodexReactionChips(
    entries: List<CodexEntry>,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry ->
            val icon = entry.icon as? CodexIcon.Reaction ?: return@forEach
            val interaction = remember(entry.key) { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val hovered by interaction.collectIsHoveredAsState()
            val first = roles.of(icon.id.first)
            val second = roles.of(icon.id.second)
            Row(slotModifier(entry, selection, focus, interaction, onSelection).heightIn(min = (34f * ui).dp)
                .drawBehind {
                    drawKkSlab(Rect(Offset.Zero, size), if (hovered || focused) Kk.Ink3 else Kk.Ink2, 8.dp.toPx())
                    drawCodexSelection(selection.pinnedKey == entry.key, selection.previewKey == entry.key || focused, roles)
                }.padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size((24f * ui).dp)) {
                    val half = size.width * 0.5f
                    drawRect(if (icon.discovered) first else Kk.Ink3, Offset.Zero, Size(half, size.height))
                    drawRect(if (icon.discovered) second else Kk.Ink3, Offset(half, 0f), Size(half, size.height))
                    if (!icon.discovered) drawKkHatch(Rect(Offset.Zero, size))
                    drawKkIcon(icon.id.first.glyph(), Offset(half * 0.5f, size.height * 0.5f), half * 0.9f, if (icon.discovered) Kk.Ink else first)
                    drawKkIcon(icon.id.second.glyph(), Offset(half * 1.5f, size.height * 0.5f), half * 0.9f, if (icon.discovered) Kk.Ink else second)
                }
                BasicText((if (icon.discovered) entry.title else "?").uppercase(), style = typography.condStyle(18f * ui, color = Kk.Bone),
                    softWrap = false, maxLines = 1)
            }
        }
    }
}

/** The bound relics' costs, always in view during a run: rune, name and liability on a threat stripe. */
@Composable
internal fun CodexCostBlock(build: GameplayBuildSummaryProjection, catalog: UiCatalogSnapshot, ui: Float) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val relics = build.relics.map { catalog.relic(it.id) } +
        build.severedRelicIds.filter { id -> build.relics.none { it.id == id } }.distinct().map { catalog.relic(it) }
    if (relics.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("codex-costs").drawBehind {
        drawRect(Kk.Ink2)
        drawRect(kkFillBrush(KkFillKind.HATCH, roles.threat.copy(alpha = 0.12f)), Offset.Zero, size)
        drawRect(roles.threat, Offset.Zero, Size(3.dp.toPx(), size.height))
    }.padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        relics.forEach { relic ->
            val severed = build.relics.none { it.id == relic.id }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size((26f * ui).dp)) {
                    drawRelicIcon(relic, catalog.relicPolicy, center, size.minDimension * 0.46f, alpha = if (severed) 0.5f else 1f, roles = roles)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(relic.name.localizedContent(language).uppercase(), Modifier.weight(1f, fill = false),
                            style = typography.condStyle(16f * ui, color = Kk.Bone), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (severed) CodexTag(language.text(CodexText.SEVERED), roles.threat, Kk.Ink, ui)
                    }
                    BasicText(codexKeepUnits(relic.liabilityText(language)), style = typography.bodyStyle(14f * ui, lineHeightEm = 1.3f,
                        color = kkMix(Kk.Bone2, roles.threat, 0.25f)))
                }
            }
        }
    }
}

/**
 * The reaction matrix: element glyph headers over the columns and beside the rows, the five Breaks
 * on the diagonal and the ten reactions below it. A selected residue outlines the reactions it can
 * feed and fades the rest.
 */
@Composable
internal fun CodexReactionMatrix(
    entries: List<CodexEntry>,
    viewportHeight: Dp,
    ui: Float,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    residue: StatusElement?,
    /** The reaction whose residue is shown: it stays lit while the others fade. */
    residueSource: String?,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val language = LocalAppLanguage.current
    val measurer = codexMeasurer()
    val byKey = entries.associateBy { it.key }
    val feeds = residue?.let(::codexResidueFeeds).orEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("codex-matrix"), contentAlignment = Alignment.TopCenter) {
        val available = maxWidth
        val gap = 6.dp
        // Five cells and a narrower header column across, five rows and a header row down.
        val byWidth = (maxWidth - gap * 5) / 5.6f
        val byHeight = (viewportHeight - 40.dp - gap * 5) / 5.6f
        val cell = min(min(byWidth, max(byHeight.value, 44f).dp), 132.dp)
        val header = cell * 0.6f
        val labels = cell >= 76.dp
        val matrixWidth = header + (cell + gap) * 5
        // Cells too small for names list them beside the matrix where there is room, else below it.
        val sideLegend = !labels && available - matrixWidth >= 240.dp
        val matrix: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.width(header).height(header))
                    StatusElement.entries.forEach { element ->
                        Canvas(Modifier.width(cell).height(header)) {
                            val tone = roles.of(element)
                            drawKkIcon(element.glyph(), Offset(size.width * 0.5f, size.height * if (labels) 0.36f else 0.5f), size.height * 0.42f, tone)
                            if (labels) drawKkText(measurer, element.displayLabel.localizedContent(language), measurer.typography.labelStyle(12f),
                                size.width * 0.5f, size.height * 0.82f, tone, KkAlign.CENTER, KkVAlign.CENTER, uppercase = true, maxWidth = size.width)
                            drawRect(tone, Offset(size.width * 0.2f, size.height - 2.dp.toPx()), Size(size.width * 0.6f, 2.dp.toPx()))
                        }
                    }
                }
                CodexMatrix.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.width(header).height(cell)) {
                            val tone = roles.of(row.first().row)
                            drawKkIcon(row.first().row.glyph(), Offset(size.width * 0.5f, size.height * 0.5f), size.width * 0.5f, tone)
                            drawRect(tone, Offset(size.width - 2.dp.toPx(), size.height * 0.2f), Size(2.dp.toPx(), size.height * 0.6f))
                        }
                        row.forEach { cellSpec ->
                            val entry = byKey[cellSpec.key] ?: return@forEach
                            // A residue feeds every reaction that takes its element, and that element's own Break.
                            val fed = residue != null && (cellSpec.reaction?.let { it in feeds } ?: (cellSpec.row == residue))
                            CodexMatrixSlot(cellSpec, entry, cell, selection, focus, if (fed) residue else null,
                                residue != null && !fed && cellSpec.key != residueSource, onSelection)
                        }
                    }
                }
            }
        }
        when {
            sideLegend -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                matrix()
                val legendWidth = available - matrixWidth - 18.dp
                CodexMatrixLegend(entries, legendWidth, if (legendWidth >= 420.dp) 2 else 1, ui, selection, residue, feeds, onSelection)
            }
            labels -> matrix()
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                matrix()
                Spacer(Modifier.height(14.dp))
                CodexMatrixLegend(entries, available, 2, ui, selection, residue, feeds, onSelection)
            }
        }
    }
}

/** The names of the matrix cells (the Breaks, then the reactions) for screens whose cells hold glyphs only. */
@Composable
private fun CodexMatrixLegend(
    entries: List<CodexEntry>,
    width: Dp,
    columns: Int,
    ui: Float,
    selection: CodexSelection,
    residue: StatusElement?,
    feeds: Set<kinetickk.ball.content.api.ReactionId>,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val inputEnabled = LocalCodexInputEnabled.current
    val ordered = entries.filter { it.icon is CodexIcon.Break } + entries.filter { it.icon is CodexIcon.Reaction }
    val columnWidth = (width - 8.dp * (columns - 1)) / columns
    FlowRow(Modifier.width(width).testTag("codex-matrix-legend"), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { entry ->
            val (first, second, known) = when (val icon = entry.icon) {
                is CodexIcon.Break -> Triple(icon.element, icon.element, true)
                is CodexIcon.Reaction -> Triple(icon.id.first, icon.id.second, icon.discovered)
                else -> return@forEach
            }
            val fed = residue != null && when (val icon = entry.icon) {
                is CodexIcon.Reaction -> icon.id in feeds
                is CodexIcon.Break -> icon.element == residue
            }
            val pinned = selection.pinnedKey == entry.key
            Row(Modifier.width(columnWidth).heightIn(min = (28f * ui).dp)
                .clickable(enabled = inputEnabled, interactionSource = remember(entry.key) { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    onSelection { it.activate(entry.key) }
                }
                .drawBehind {
                    drawKkSlab(Rect(Offset.Zero, size), if (pinned) Kk.Ink3 else Kk.Ink2, 6.dp.toPx())
                    if (fed) drawKkSlab(Rect(Offset.Zero, size), roles.of(residue), 6.dp.toPx(), style = kkStroke(1.5.dp.toPx()))
                }.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.size((18f * ui).dp)) {
                    val half = size.width * 0.5f
                    if (first == second) {
                        drawRect(kkMix(Kk.Ink2, roles.of(first), 0.3f))
                        drawKkIcon(first.glyph(), center, size.width * 0.8f, roles.of(first))
                    } else {
                        drawRect(if (known) roles.of(first) else Kk.Ink3, Offset.Zero, Size(half, size.height))
                        drawRect(if (known) roles.of(second) else Kk.Ink3, Offset(half, 0f), Size(half, size.height))
                        // Both glyphs always: in the grey ramps the hues alone cannot tell reactions apart.
                        val glyphTone = if (known) Kk.Ink.copy(alpha = 0.85f) else null
                        drawKkIcon(first.glyph(), Offset(half * 0.5f, size.height * 0.5f), half, glyphTone ?: roles.of(first))
                        drawKkIcon(second.glyph(), Offset(half * 1.5f, size.height * 0.5f), half, glyphTone ?: roles.of(second))
                    }
                }
                // Names read in bone: element hues (the dark grey ramp steps above all) are no text color.
                BasicText((if (known) entry.title else "?").uppercase(), style = typography.condStyle(15f * ui, color = if (known) Kk.Bone else Kk.Mute),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CodexMatrixSlot(
    cell: CodexMatrixCell,
    entry: CodexEntry,
    size: Dp,
    selection: CodexSelection,
    focus: MutableMap<String, FocusRequester>,
    feeds: StatusElement?,
    dimmed: Boolean,
    onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val interaction = remember(entry.key) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val path = remember { Path() }
    Box(slotModifier(entry, selection, focus, interaction, onSelection).size(size).drawBehind {
        drawCodexMatrixCell(cell, entry, measurer, path, hovered || focused, feeds, dimmed)
        drawCodexSelection(selection.pinnedKey == entry.key, selection.previewKey == entry.key || focused, roles)
    })
}

/**
 * Tabs below [CODEX_COMPACT_TABS_DP]: a glyph over a short label, on the tab slab. Semantics keep
 * the tab's full name.
 */
@Composable
internal fun CodexGlyphTab(
    title: String,
    short: String,
    icon: KkIcon,
    selected: Boolean,
    tag: String,
    controlScale: Float,
    tabScale: Float,
    onClick: () -> Unit,
) {
    val measurer = codexMeasurer()
    val inputEnabled = LocalCodexInputEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val width = remember(measurer, short, density) { codexGlyphTabWidth(measurer, short, density) / density }
    val height = 52f * controlScale
    Box(Modifier.testTag(tag).semantics { this.selected = selected; this.text = AnnotatedString(title) }
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, role = Role.Tab, interactionSource = interactions, indication = null, onClick = onClick)
        .size((width * tabScale).dp, (height * tabScale + 8f).dp)
        .drawBehind {
            val k = tabScale
            val bounds = Rect(0f, 0f, width * density * k, height * density * k)
            scale(k, k, Offset.Zero) {
                drawKkTab(measurer, Rect(0f, 0f, width * density, height * density), "", selected, hovered && inputEnabled, focused && inputEnabled)
            }
            val color = when {
                selected -> Kk.Ink
                hovered && inputEnabled -> Kk.Bone
                else -> Kk.Mute
            }
            drawKkIcon(icon, Offset(bounds.center.x, bounds.top + bounds.height * 0.34f), 17.dp.toPx() * k * measurer.scale.coerceAtLeast(1f), color)
            val label = measureKkText(measurer, short, measurer.typography.condStyle(15f, trackingEm = 0.03f), uppercase = true)
            scale(k, k, Offset(bounds.center.x, bounds.top + bounds.height * 0.74f)) {
                drawKkText(label, bounds.center.x, bounds.top + bounds.height * 0.74f, color, KkAlign.CENTER, KkVAlign.CENTER)
            }
        })
}

/** Natural width (px) of a glyph tab: its short label or glyph, whichever is wider, and padding. */
internal fun codexGlyphTabWidth(measurer: kinetickk.foundation.design.CanvasTextMeasurer, short: String, density: Float): Float =
    max(measureKkText(measurer, short, measurer.typography.condStyle(15f, trackingEm = 0.03f), uppercase = true).size.width.toFloat(),
        24f * density) + 22f * density
