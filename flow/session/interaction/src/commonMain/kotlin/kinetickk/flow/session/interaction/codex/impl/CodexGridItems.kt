// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.foundation.design.*
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.profile.api.HomeProgressProjection
import kotlin.math.roundToInt
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection

/**
 * The lazy items of a Codex grid: section headings, cells, meter and pairing rows, and each tab's sections. Mutable
 * screen state is read through [selection] where an item uses it.
 */
internal class CodexGridItems(
    private val language: AppLanguage,
    private val catalog: UiCatalogSnapshot,
    private val ui: Float,
    private val listTab: Boolean,
    private val meterCells: Int,
    private val pairingCells: Int,
    private val describeMeters: Boolean,
    private val slotFocus: MutableMap<String, FocusRequester>,
    private val selection: () -> CodexSelection,
    private val onSelection: ((CodexSelection) -> CodexSelection) -> Unit,
) {
    private val policy = catalog.build.policy

    fun LazyGridScope.heading(key: String, title: String, value: String? = null, info: String? = null, badge: String? = null) {
        item(key = "heading/$key", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
            CodexSectionHeading(title, ui, value, info, badge)
        }
    }
    fun LazyGridScope.cells(sectionEntries: List<CodexEntry>) {
        items(sectionEntries, key = { it.key }, contentType = { CodexGridContentType.SLOT }) { entry ->
            CodexSlot(entry, catalog, ui, selection(), slotFocus, band = false, onSelection)
        }
    }
    fun LazyGridScope.meters(sectionEntries: List<CodexEntry>) {
        itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
            span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, meterCells, index)) }) { _, entry ->
            CodexMeterRow(entry, catalog, ui, selection(), slotFocus, describeMeters, onSelection)
        }
    }
    fun LazyGridScope.pairings(sectionEntries: List<CodexEntry>, showKind: (CodexEntry) -> Boolean) {
        itemsIndexed(sectionEntries, key = { _, entry -> entry.key }, contentType = { _, _ -> CodexGridContentType.ROW },
            span = { index, _ -> GridItemSpan(if (listTab) 1 else codexRowSpan(maxLineSpan, pairingCells, index)) }) { _, entry ->
            CodexPairingRow(entry, catalog, ui, selection(), slotFocus, showKind(entry), onSelection)
        }
    }

    /** The Build tab: loadout, relics, costs, items, resonance, affinity, links, reactions and the stat rows. */
    fun LazyGridScope.buildSections(
        entries: List<CodexEntry>, build: GameplayBuildSummaryProjection, progress: HomeProgressProjection,
        typography: InterfaceTypography, controlScale: Float, statsExpanded: () -> Boolean, onStatsExpanded: (Boolean) -> Unit,
        selectedStat: () -> String?, onSelectedStat: (String?) -> Unit, statRows: List<Pair<String, String>>, statsFit: CodexPairFit,
    ) {
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
                CodexReactionChips(reactions, ui, selection(), slotFocus, onSelection)
            }
        }
        item(key = "stats-heading", contentType = CodexGridContentType.HEADING, span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(top = 12.dp)) {
                CodexToggleButton(language.text(SessionRedesignText.STATS), statsExpanded(), "codex-stats", controlScale) { onStatsExpanded(!statsExpanded()) }
            }
        }
        if (statsExpanded()) itemsIndexed(build.stats, key = { _, stat -> "stat/${stat.name}" }, contentType = { _, _ -> CodexGridContentType.STAT }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, stat ->
            val (name, value) = statRows[index]
            CodexStatRow(name, value, selectedStat() == stat.name, "codex-stat-${stat.name}", ui, statsFit, { onSelectedStat(if (selectedStat() == stat.name) null else stat.name) }) {
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

    /** The Catalog tab: the Items matrix, or a flat grid with the previewed cell's row and column banded. */
    fun LazyGridScope.catalogSection(
        entries: List<CodexEntry>, itemGrid: CodexItemGrid?, labelFit: CodexRowLabelFit?, columns: Int, gridHeight: Dp,
        previewIndex: Int, previewRow: Int, previewColumn: Int, previewRarity: ItemRarity?,
    ) {
        if (itemGrid != null && labelFit != null) {
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
                        CodexItemRowSlot(part.cell.entry, selection(), slotFocus, rowBand || stripe >= 0, lineDp, onSelection)
                }
            }
        } else itemsIndexed(entries, key = { _, entry -> entry.key },
            contentType = { _, _ -> CodexGridContentType.SLOT }) { index, entry ->
            val band = previewIndex >= 0 && (index / columns == previewIndex / columns || index % columns == previewIndex % columns)
            CodexSlot(entry, catalog, ui, selection(), slotFocus, band, onSelection)
        }
    }

    /** The Reactions tab: the reaction matrix. */
    fun LazyGridScope.reactionMatrix(entries: List<CodexEntry>, gridHeight: Dp, residue: StatusElement?, residueSource: String?) {
        item(key = "matrix", contentType = CodexGridContentType.MATRIX, span = { GridItemSpan(maxLineSpan) }) {
            CodexReactionMatrix(entries, gridHeight, ui, selection(), slotFocus, residue, residueSource, onSelection)
        }
    }

    /** The Pairings tab: one section per pairing kind. */
    fun LazyGridScope.pairingSections(entries: List<CodexEntry>) {
        CodexPairingSection.entries.forEach { section ->
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
    }

    /** The Tags tab: resonance and affinity meters, then the keys. */
    fun LazyGridScope.tagSections(entries: List<CodexEntry>) {
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
            CodexKeyChips(codexKeyGroups().map { group -> group.mapNotNull { byKey["key/$it"] } }, ui, selection(), slotFocus, onSelection)
        }
    }
}

private val CodexPairingSection.title: CodexText
    get() = when (this) {
        CodexPairingSection.CONCORDS -> CodexText.SECTION_CONCORDS
        CodexPairingSection.HARMONY -> CodexText.SECTION_HARMONY
        CodexPairingSection.DISSONANCE -> CodexText.SECTION_DISSONANCE
        CodexPairingSection.INCOMPATIBLE -> CodexText.SECTION_INCOMPATIBLE
    }
