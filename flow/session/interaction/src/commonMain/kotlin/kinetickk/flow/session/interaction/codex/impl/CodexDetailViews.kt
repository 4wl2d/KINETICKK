// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.RuleLine
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
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
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.kkHatch
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.monoStyle

/**
 * The body of an entry's detail between its title and its facts: rule lines for items, the rank
 * upsides and cost for relics, inputs and residue for reactions, keys for pairings, tiers for
 * tags. [residue] and [onResidue] drive the reaction chain highlight in the matrix; [onOpen]
 * opens another entry by key (the reactions a residue feeds).
 */
@Composable
internal fun CodexDetailBody(
    entry: CodexEntry,
    catalog: UiCatalogSnapshot,
    ui: Float,
    panel: Color,
    residue: StatusElement?,
    onResidue: (StatusElement?) -> Unit,
    onOpen: (String) -> Unit = {},
) {
    when (val detail = entry.detail) {
        is CodexDetail.Item -> CodexItemDetail(detail, ui, panel)
        is CodexDetail.Relic -> CodexRelicDetail(entry, detail, catalog, ui, panel)
        is CodexDetail.Reaction -> CodexReactionDetail(entry, detail, ui, panel, residue, onResidue, onOpen)
        is CodexDetail.Break -> CodexBreakDetail(entry, detail, catalog, ui)
        is CodexDetail.Pairing -> CodexPairingDetail(entry, detail, catalog, ui, panel)
        is CodexDetail.Conflict -> CodexConflictDetail(entry, detail, catalog, ui)
        is CodexDetail.Tag -> CodexTierDetail(entry.description.substringBefore("\n\n"), detail.tiers, detail.thresholds, detail.points, Kk.Bone,
            detail.tag.glyph(), ui, panel)
        is CodexDetail.Affinity -> CodexTierDetail(entry.description.substringBefore("\n\n"), detail.tiers, detail.thresholds, detail.affinity,
            LocalKkRolePalette.current.of(detail.element), detail.element.glyph(), ui, panel, dominant = detail.dominant)
        is CodexDetail.Key -> CodexKeyDetail(detail, catalog, ui)
        is CodexDetail.Weapon -> CodexWeaponDetail(detail, ui, panel)
        null -> CodexPlainDetail(entry, ui, panel)
    }
}

/**
 * A weapon's sheet: its element, tags and Relic key as chips (the run's tags lit), its description,
 * the three mastery forms by level, the signature character and twist (lit when active), the Relics
 * that carry its key and its tag resonance.
 */
@Composable
private fun CodexWeaponDetail(detail: CodexDetail.Weapon, ui: Float, panel: Color) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val weapon = detail.weapon
    val body = typography.bodyStyle(15f * ui, lineHeightEm = 1.35f, color = Kk.Bone)
    FlowRow(Modifier.testTag("codex-weapon-chips"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val element = weapon.element
        if (element != null) {
            CodexGlyphChip(element.glyph(), element.displayLabel.localizedContent(language), ui, glyphColor = roles.of(element), rim = roles.of(element))
        } else {
            CodexGlyphChip(null, codexWeaponElementLabel(weapon, language), ui, rim = roles.kinetic)
        }
        weapon.tags.forEach { tag ->
            CodexGlyphChip(tag.glyph(), tag.displayLabel.localizedContent(language), ui,
                underline = if ((detail.tagPoints ?: 0) > 0) roles.you else Color.Unspecified)
        }
        CodexGlyphChip(null, weapon.key.displayLabel.localizedContent(language), ui, rim = Kk.Bone2)
        if (detail.rebirthLocked) {
            CodexGlyphChip(null, language.text(CodexText.REBORN_GATE, weapon.minimumRebirth), ui, rim = Kk.Gold, foreground = Kk.Gold)
        }
    }
    BasicText(weapon.description.localizedContent(language), style = typography.bodyStyle(15f * ui, lineHeightEm = 1.35f, color = Kk.Mute))
    Column(Modifier.fillMaxWidth().testTag("codex-weapon-forms").background(panel).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        CodexChipLabel(language.text(CodexText.WEAPON_FORMS), ui)
        weapon.masteryForms.forEachIndexed { index, form ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val level = detail.formLevels.getOrNull(index)
                BasicText(language.text(SessionText.LEVEL_SHORT, level ?: 0).uppercase(),
                    style = typography.monoStyle(11f * ui, color = if (index == weapon.masteryForms.lastIndex) Kk.RLegend else roles.you),
                    softWrap = false, maxLines = 1)
                BasicText(form.localizedContent(language), Modifier.weight(1f), style = body)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("codex-weapon-signature"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size((18f * ui).dp)) {
                drawCodexForm(weapon.signature, center, size.minDimension * 0.5f, if (detail.signatureActive) roles.you else Kk.Bone2)
            }
            BasicText(language.text(CodexText.WEAPON_SIGNATURE_OF, detail.signature).uppercase(), Modifier.weight(1f),
                style = typography.labelStyle(13f * ui, trackingEm = 0.08f, color = if (detail.signatureActive) roles.you else Kk.Bone))
        }
        BasicText(
            if (detail.signatureActive) language.text(CodexText.WEAPON_SIGNATURE_ACTIVE)
            else language.text(CodexText.WEAPON_SIGNATURE_INACTIVE, detail.signature),
            style = typography.bodyStyle(14f * ui, lineHeightEm = 1.35f, color = if (detail.signatureActive) roles.you else Kk.Mute),
        )
        CodexChipLabel(language.text(CodexText.WEAPON_TWIST), ui)
        BasicText(weapon.signatureText.localizedContent(language), style = body)
    }
    val key = weapon.key.displayLabel.localizedContent(language)
    Column(Modifier.fillMaxWidth().testTag("codex-weapon-harmony"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lit = detail.bound.isNotEmpty() || detail.heirlooms.isNotEmpty()
        CodexChipLabel(language.text(CodexText.HARMONY), ui, color = if (lit) roles.you else Kk.Mute)
        BasicText(when {
            detail.bound.isNotEmpty() -> language.text(CodexText.WEAPON_HARMONY_BOUND, key, detail.bound.joinToString(", ") { it.name.localizedContent(language) })
            detail.heirlooms.isNotEmpty() -> language.text(CodexText.WEAPON_HARMONY_HEIRLOOM, key,
                detail.heirlooms.joinToString(", ") { it.name.localizedContent(language) })
            else -> language.text(CodexText.WEAPON_HARMONY_NONE, key)
        }, style = body)
    }
    Column(Modifier.fillMaxWidth().testTag("codex-weapon-resonance"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.SECTION_RESONANCE), ui)
        val tags = weapon.tags.map { it.displayLabel.localizedContent(language) }
        BasicText(language.text(CodexText.WEAPON_RESONANCE, tags.getOrElse(0) { "" }, tags.getOrElse(1) { "" }), style = body)
        detail.tagPoints?.let { points ->
            BasicText(language.text(CodexText.WEAPON_RESONANCE_NOW, points, codexResonancePercent(points)),
                style = typography.monoStyle(11f * ui, color = if (points > 0) roles.you else Kk.Mute))
        }
    }
}

/** Weapon power (percent) the run's [points] in a weapon's two tags add: 5 % each, at most 50 %. */
internal fun codexResonancePercent(points: Int): Int = (points.coerceAtLeast(0) * 5).coerceAtMost(50)

/** Weapons, forms, empty slots, locked and unknown entries: their paragraphs, or a hatched "?". */
@Composable
private fun CodexPlainDetail(entry: CodexEntry, ui: Float, panel: Color) {
    val typography = codexMeasurer().typography
    if (!entry.discovered && entry.description.isBlank()) CodexHiddenBlock(ui, panel)
    entry.description.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
        BasicText(paragraph, style = typography.bodyStyle(16f * ui, color = Kk.Bone2))
    }
}

/** The hatched "?" plate of something not discovered yet. */
@Composable
private fun CodexHiddenBlock(ui: Float, panel: Color) {
    val typography = codexMeasurer().typography
    Box(Modifier.fillMaxWidth().height(120.dp).background(panel).kkHatch().testTag("codex-hidden"), contentAlignment = Alignment.Center) {
        BasicText("?", style = typography.condStyle(64f, color = Kk.Mute))
    }
}

/** A chip with a leading glyph: monochrome for tags and keys, an element rim for elements. */
@Composable
internal fun CodexGlyphChip(
    icon: KkIcon?,
    label: String,
    ui: Float,
    glyphColor: Color = Kk.Bone,
    rim: Color = Kk.Line2,
    face: Color = Color.Transparent,
    foreground: Color = Kk.Bone,
    underline: Color = Color.Unspecified,
) {
    val typography = codexMeasurer().typography
    Row(Modifier.heightIn(min = (24f * ui).dp).drawBehind {
        val rect = Rect(Offset.Zero, size)
        if (face.alpha > 0f) drawKkSlab(rect, face, 6.dp.toPx())
        drawKkSlab(Rect(0.75f, 0.75f, size.width - 0.75f, size.height - 0.75f), rim, 6.dp.toPx(), style = kkStroke(1.5.dp.toPx()))
        if (underline != Color.Unspecified) drawKkSheared(Rect(6.dp.toPx(), size.height + 2.dp.toPx(), size.width - 6.dp.toPx(), size.height + 4.dp.toPx()), underline)
    }.padding(start = if (icon != null) 6.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) Canvas(Modifier.size((15f * ui).dp)) { drawKkIcon(icon, center, size.minDimension, glyphColor) }
        BasicText(label.uppercase(), style = typography.labelStyle(13f * ui, trackingEm = 0.1f, color = foreground), softWrap = false, maxLines = 1)
    }
}

/** A short mono label in front of a row of chips. */
@Composable
private fun CodexChipLabel(text: String, ui: Float, color: Color = Kk.Mute) {
    BasicText(text.uppercase(), style = codexMeasurer().typography.monoStyle(10f * ui, color = color))
}

@Composable
private fun CodexItemDetail(detail: CodexDetail.Item, ui: Float, panel: Color) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val item = detail.item
    val tone = codexItemColor(item, roles)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.elements.forEach { element ->
            CodexGlyphChip(element.glyph(), element.displayLabel.localizedContent(language), ui, glyphColor = roles.of(element), rim = roles.of(element))
        }
        item.tags.forEach { tag -> CodexGlyphChip(tag.glyph(), tag.displayLabel.localizedContent(language), ui) }
        if (item.risk) CodexGlyphChip(null, language.text(CodexText.RISK), ui, rim = roles.threat, face = roles.threat, foreground = Kk.Ink)
        if (detail.reborn) CodexGlyphChip(null, language.text(CodexText.REBORN), ui, rim = Kk.Gold, face = Kk.Gold, foreground = Kk.Ink)
        if (detail.suppressed) CodexGlyphChip(null, language.text(CodexText.SUPPRESSED), ui, rim = roles.threat, foreground = roles.threat)
        CodexGlyphChip(null, language.text(SessionRedesignText.MAX_STACKS, item.maxStacks), ui)
        if (detail.stack > 0) CodexGlyphChip(null, language.text(SessionText.STACK_QUANTITY, "${detail.stack}/${item.maxStacks}"), ui,
            rim = roles.you, face = roles.you, foreground = Kk.Ink)
        if (item.minimumRebirth > 0) CodexGlyphChip(null, language.text(SessionText.REBIRTH_LEVEL, item.minimumRebirth), ui, rim = Kk.Gold, foreground = Kk.Gold)
    }
    BasicText(item.description.localizedContent(language), style = typography.bodyStyle(15f * ui, lineHeightEm = 1.35f, color = Kk.Mute))
    if (detail.lines.isNotEmpty()) CodexRuleLines(detail.lines, tone, detail.suppressed, ui, panel)
}

/**
 * Rule lines as a card: passive stats, then each trigger in mono caps with its effect under it on
 * an element rule, then system rules; costs come last on a threat stripe. Numbers carry the hue.
 */
@Composable
internal fun CodexRuleLines(lines: List<RuleLine>, tone: Color, struck: Boolean, ui: Float, panel: Color) {
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val decoration = if (struck) TextDecoration.LineThrough else null
    Column(Modifier.fillMaxWidth().testTag("codex-rule-lines").background(panel).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEachIndexed { index, line ->
            val hue = if (struck) Kk.Mute else line.element?.let(roles::of) ?: tone
            val body = typography.bodyStyle(15f * ui, lineHeightEm = 1.32f, color = if (struck) Kk.Mute else Kk.Bone).copy(textDecoration = decoration)
            when (line.kind) {
                RuleLineKind.TRIGGER -> Row(Modifier.padding(top = if (index > 0) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.width(10.dp).height(10.dp)) { drawKkSheared(Rect(Offset.Zero, size), hue) }
                    BasicText(codexKeepUnits(line.text).uppercase(), style = typography.monoStyle(11f * ui, trackingEm = 0.06f, color = if (struck) Kk.Mute else Kk.Bone)
                        .copy(textDecoration = decoration))
                }
                RuleLineKind.EFFECT -> BasicText(codexHighlightNumbers(codexKeepUnits(line.text), hue), Modifier.padding(start = 4.dp).drawBehind {
                    drawRect(hue, Offset(-4.dp.toPx(), 2.dp.toPx()), Size(2.dp.toPx(), size.height - 4.dp.toPx()))
                }.padding(start = 10.dp), style = body)
                RuleLineKind.COST -> Row(Modifier.fillMaxWidth().padding(top = 4.dp).drawBehind {
                    drawRect(roles.threat, Offset.Zero, Size(3.dp.toPx(), size.height))
                }.padding(start = 12.dp)) {
                    BasicText(codexHighlightNumbers(codexKeepUnits(line.text), roles.threat), style = body.copy(color = kkMix(Kk.Bone, roles.threat, 0.35f)))
                }
                RuleLineKind.PASSIVE, RuleLineKind.RULE -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.padding(top = (7f * ui).dp).width(6.dp).height(6.dp)) {
                        drawKkSheared(Rect(Offset.Zero, size), if (line.kind == RuleLineKind.PASSIVE) hue else Kk.Mute)
                    }
                    BasicText(codexHighlightNumbers(codexKeepUnits(line.text), if (line.negative) roles.threat else hue), style = body)
                }
            }
        }
    }
}

@Composable
private fun CodexRelicDetail(entry: CodexEntry, detail: CodexDetail.Relic, catalog: UiCatalogSnapshot, ui: Float, panel: Color) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val relic = detail.relic
    val aspect = relicAspectColor(relic.aspect, roles)
    var rankValue by remember(entry.key) { mutableIntStateOf(detail.rank) }
    val rank = rankValue.coerceIn(1, detail.upsides.size)
    if (detail.ranked) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.testTag("codex-relic-ranks")) {
        (1..detail.upsides.size).forEach { value ->
            CodexRankSegment(language.text(CodexText.RANK_N, value), value == rank, "codex-rank-$value", ui) { rankValue = value }
        }
    }
    BasicText(codexHighlightNumbers(codexKeepUnits(detail.upsides[rank - 1]), aspect), Modifier.testTag("codex-relic-upside"),
        style = typography.bodyStyle(16f * ui, lineHeightEm = 1.38f, color = Kk.Bone))
    // The cost never changes with rank.
    Column(Modifier.fillMaxWidth().testTag("codex-relic-cost").background(panel).drawBehind {
        drawRect(kkFillBrush(KkFillKind.HATCH, roles.threat.copy(alpha = 0.14f)), Offset.Zero, size)
        drawRect(roles.threat, Offset.Zero, Size(3.dp.toPx(), size.height))
    }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CodexChipLabel(language.text(CodexText.COST), ui, roles.threat)
        BasicText(codexHighlightNumbers(codexKeepUnits(detail.liability), roles.threat), style = typography.bodyStyle(15f * ui, lineHeightEm = 1.32f, color = Kk.Bone2))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.KEYS), ui)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            relic.keys.forEach { key ->
                val opposed = key in detail.opposedBound
                CodexGlyphChip(null, key.displayLabel.localizedContent(language), ui, rim = if (opposed) roles.threat else Kk.Line2,
                    underline = if (opposed) roles.threat else Color.Unspecified)
            }
        }
    }
    if (relic.attunedTags.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CodexChipLabel(language.text(CodexText.ATTUNED), ui)
            KkInfoButton(language.text(CodexText.ATTUNED_HELP, (catalog.build.policy.attunementBonus * 100f).toInt()),
                placement = KkTooltipPlacement.BELOW, textScale = ui)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            relic.attunedTags.forEach { tag -> CodexGlyphChip(tag.glyph(), tag.displayLabel.localizedContent(language), ui) }
        }
    }
    if (detail.pairings.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.PAIRINGS_PROGRESS_LABEL), ui)
        detail.pairings.forEach { note ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size((24f * ui).dp)) { drawRelicIcon(note.partner, catalog.relicPolicy, center, size.minDimension * 0.46f, roles = roles) }
                Column(Modifier.weight(1f)) {
                    BasicText((note.name ?: "?").uppercase(), style = typography.condStyle(17f * ui, color = codexLinkTone(note.kind, roles)))
                    BasicText(note.partner.name.localizedContent(language).uppercase(), style = typography.monoStyle(10f * ui, color = Kk.Mute))
                }
                BasicText(language.text(note.kind.label).uppercase(), style = typography.labelStyle(11f * ui, color = codexLinkTone(note.kind, roles)))
            }
        }
    }
}

@Composable
private fun CodexRankSegment(text: String, selected: Boolean, tag: String, ui: Float, onClick: () -> Unit) {
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    BasicText(text.uppercase(), Modifier.testTag(tag).semantics { this.selected = selected }
        .hoverable(interactions).clickable(interactionSource = interactions, indication = null, role = Role.Tab, onClick = onClick)
        .heightIn(min = (30f * ui).dp)
        .drawBehind {
            drawKkSheared(Rect(Offset.Zero, size), when {
                selected -> roles.you
                hovered || focused -> Kk.Ink4
                else -> Kk.Ink3
            })
        }.padding(horizontal = 12.dp, vertical = 6.dp),
        style = typography.labelStyle(13f * ui, trackingEm = 0.08f, color = if (selected) Kk.Ink else Kk.Bone), softWrap = false, maxLines = 1)
}

@Composable
private fun CodexReactionDetail(
    entry: CodexEntry,
    detail: CodexDetail.Reaction,
    ui: Float,
    panel: Color,
    residue: StatusElement?,
    onResidue: (StatusElement?) -> Unit,
    onOpen: (String) -> Unit,
) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.INPUTS), ui)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(detail.id.first, detail.id.second).forEachIndexed { index, element ->
                if (index > 0) BasicText("+", style = typography.condStyle(20f * ui, color = Kk.Mute))
                CodexGlyphChip(element.glyph(), element.displayLabel.localizedContent(language), ui, glyphColor = roles.of(element), rim = roles.of(element))
            }
        }
    }
    if (!detail.discovered) {
        CodexHiddenBlock(ui, panel)
        return
    }
    BasicText(entry.description, style = typography.bodyStyle(16f * ui, color = Kk.Bone2))
    val element = detail.residue ?: return
    val active = residue == element
    val tone = roles.of(element)
    val interactions = remember(entry.key) { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    // The residue chip shows which reactions this one can feed next (the multi-stage chain).
    Row(Modifier.testTag("codex-residue").semantics { selected = active }
        .hoverable(interactions).clickable(interactionSource = interactions, indication = null, role = Role.Button) { onResidue(if (active) null else element) }
        .heightIn(min = (32f * ui).dp)
        .drawBehind {
            val rect = Rect(Offset.Zero, size)
            drawKkSlab(rect, if (active) tone else if (hovered) Kk.Ink3 else Kk.Ink2, 7.dp.toPx())
            drawKkSlab(Rect(0.75f, 0.75f, size.width - 0.75f, size.height - 0.75f), tone, 7.dp.toPx(), style = kkStroke(1.5.dp.toPx()))
        }.padding(start = 8.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size((18f * ui).dp)) { drawKkIcon(element.glyph(), center, size.minDimension, if (active) Kk.Ink else tone) }
        BasicText(language.text(CodexText.LEAVES, element.displayLabel.localizedContent(language)).uppercase(),
            style = typography.labelStyle(14f * ui, trackingEm = 0.08f, color = if (active) Kk.Ink else Kk.Bone), softWrap = false, maxLines = 1)
    }
    // The chain in the detail itself: the reactions the residue can feed next, named once found.
    if (detail.feeds.isNotEmpty()) Column(Modifier.testTag("codex-feeds"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.FEEDS), ui)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.feeds.forEach { feed -> CodexFeedChip(feed, element, ui, onOpen) }
        }
    }
}

/** A reaction a residue feeds: both input glyphs on their hues (hatched until found) and its name or "?"; opens that reaction. */
@Composable
private fun CodexFeedChip(feed: CodexFeed, residue: StatusElement, ui: Float, onOpen: (String) -> Unit) {
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val inputEnabled = LocalCodexInputEnabled.current
    val key = codexReactionKey(feed.id)
    val interactions = remember(key) { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val known = feed.name != null
    Row(Modifier.testTag("codex-feed-${feed.id}")
        .hoverable(interactions, enabled = inputEnabled)
        .clickable(enabled = inputEnabled, interactionSource = interactions, indication = null, role = Role.Button) { onOpen(key) }
        .heightIn(min = (28f * ui).dp)
        .drawBehind {
            drawKkSlab(Rect(Offset.Zero, size), if (hovered || focused) Kk.Ink3 else Kk.Ink2, 6.dp.toPx())
            drawKkSlab(Rect(0.75f, 0.75f, size.width - 0.75f, size.height - 0.75f), if (focused) Kk.Bone else roles.of(residue).copy(alpha = 0.7f),
                6.dp.toPx(), style = kkStroke(1.5.dp.toPx()))
        }.padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size((18f * ui).dp)) {
            val half = size.width * 0.5f
            val first = roles.of(feed.id.first)
            val second = roles.of(feed.id.second)
            drawRect(if (known) first else Kk.Ink3, Offset.Zero, Size(half, size.height))
            drawRect(if (known) second else Kk.Ink3, Offset(half, 0f), Size(half, size.height))
            if (!known) drawKkHatch(Rect(Offset.Zero, size))
            drawKkIcon(feed.id.first.glyph(), Offset(half * 0.5f, size.height * 0.5f), half, if (known) Kk.Ink.copy(alpha = 0.85f) else first)
            drawKkIcon(feed.id.second.glyph(), Offset(half * 1.5f, size.height * 0.5f), half, if (known) Kk.Ink.copy(alpha = 0.85f) else second)
        }
        BasicText((feed.name ?: "?").uppercase(), style = typography.condStyle(15f * ui, color = if (known) Kk.Bone else Kk.Mute),
            softWrap = false, maxLines = 1)
    }
}

@Composable
private fun CodexBreakDetail(entry: CodexEntry, detail: CodexDetail.Break, catalog: UiCatalogSnapshot, ui: Float) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    val tone = roles.of(detail.element)
    val definition = catalog.build.element(detail.element)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CodexGlyphChip(detail.element.glyph(), detail.element.displayLabel.localizedContent(language), ui, glyphColor = tone, rim = tone)
        // The threshold as a full ladder of stacks, the overflow beyond it outlined.
        Canvas(Modifier.width((definition.stackCap * 9f).dp).height(14.dp)) {
            drawCodexLadder(Rect(Offset.Zero, size), definition.stackCap, definition.breakThreshold, listOf(definition.breakThreshold), tone)
        }
    }
    entry.description.split("\n\n").filter { it.isNotBlank() }.forEachIndexed { index, paragraph ->
        BasicText(paragraph, style = typography.bodyStyle(16f * ui, color = if (index == 0) Kk.Bone2 else Kk.Bone))
    }
}

@Composable
private fun CodexPairingDetail(entry: CodexEntry, detail: CodexDetail.Pairing, catalog: UiCatalogSnapshot, ui: Float, panel: Color) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val measurer = codexMeasurer()
    val typography = measurer.typography
    val icon = entry.icon as? CodexIcon.Pairing ?: return
    Canvas(Modifier.widthIn(max = 300.dp).fillMaxWidth().height((64f * ui).dp)) { drawCodexPairingDiagram(icon, catalog, measurer) }
    Row(Modifier.widthIn(max = 300.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(detail.first.name.localizedContent(language).uppercase(), Modifier.weight(1f),
            style = typography.monoStyle(10f * ui, color = Kk.Bone2))
        BasicText(detail.second.name.localizedContent(language).uppercase(), Modifier.weight(1f),
            style = typography.monoStyle(10f * ui, color = Kk.Bone2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End))
    }
    if (detail.keys.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.KEYS), ui)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.keys.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEachIndexed { index, key ->
                        if (index > 0) Canvas(Modifier.width(8.dp).height((22f * ui).dp)) {
                            drawKkSheared(Rect(size.width * 0.5f - 1.5.dp.toPx(), 0f, size.width * 0.5f + 1.5.dp.toPx(), size.height), roles.threat)
                        }
                        CodexGlyphChip(null, key.displayLabel.localizedContent(language), ui, rim = if (group.size > 1) roles.threat else Kk.Line2)
                    }
                }
            }
        }
    }
    if (!detail.discovered) CodexHiddenBlock(ui, panel)
    else BasicText(codexHighlightNumbers(entry.description, codexLinkTone(detail.kind, roles)), style = typography.bodyStyle(16f * ui, color = Kk.Bone))
}

@Composable
private fun CodexConflictDetail(entry: CodexEntry, detail: CodexDetail.Conflict, catalog: UiCatalogSnapshot, ui: Float) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        detail.item?.let { item ->
            Canvas(Modifier.size((44f * ui).dp)) { drawItemIcon(item, center, size.minDimension * 0.42f, codexItemColor(item, roles)) }
        }
        detail.relics.forEach { relic ->
            Canvas(Modifier.size((44f * ui).dp)) { drawRelicIcon(relic, catalog.relicPolicy, center, size.minDimension * 0.42f, roles = roles) }
            BasicText(relic.name.localizedContent(language).uppercase(), Modifier.weight(1f), style = typography.monoStyle(10f * ui, color = Kk.Bone2))
        }
    }
    Box(Modifier.fillMaxWidth().drawBehind { drawRect(roles.threat, Offset.Zero, Size(3.dp.toPx(), size.height)) }.padding(start = 12.dp)) {
        BasicText(codexHighlightNumbers(entry.description, roles.threat), style = typography.bodyStyle(16f * ui, color = Kk.Bone))
    }
}

/** Archetype or affinity tiers: a pip strip per tier (filled up to its threshold) and its text, lit once reached. */
@Composable
private fun CodexTierDetail(
    description: String,
    tiers: List<String>,
    thresholds: List<Int>,
    points: Int?,
    tone: Color,
    glyph: KkIcon,
    ui: Float,
    panel: Color,
    dominant: Boolean = false,
) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size((40f * ui).dp)) {
            if (dominant) drawCircle(tone, size.minDimension * 0.5f, style = kkStroke(2.dp.toPx()))
            drawKkIcon(glyph, center, size.minDimension * if (dominant) 0.6f else 0.85f, tone)
        }
        BasicText(description, Modifier.weight(1f), style = typography.bodyStyle(15f * ui, lineHeightEm = 1.32f, color = Kk.Bone2))
    }
    if (dominant) CodexGlyphChip(null, language.text(CodexText.DOMINANT), ui, rim = tone, face = tone, foreground = Kk.Ink)
    val top = thresholds.lastOrNull() ?: 0
    Column(Modifier.fillMaxWidth().testTag("codex-tiers").background(panel).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiers.forEachIndexed { index, text ->
            val threshold = thresholds.getOrElse(index) { top }
            val reached = points != null && points >= threshold
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.width((top * 11f).dp).height(9.dp)) {
                    val pitch = size.width / top
                    for (pip in 0 until top) {
                        val rect = Rect(pip * pitch, 0f, pip * pitch + pitch - 3.dp.toPx(), size.height)
                        when {
                            pip < threshold && reached -> drawKkSheared(rect, tone)
                            pip < threshold -> drawKkSheared(rect, kkMix(Kk.Ink4, tone, 0.35f))
                            else -> drawKkSheared(rect, Kk.Line2, kkStroke(1.dp.toPx()))
                        }
                    }
                }
                BasicText(codexHighlightNumbers(text, tone), style = typography.bodyStyle(15f * ui, lineHeightEm = 1.3f,
                    color = if (reached || points == null) Kk.Bone else Kk.Mute))
            }
        }
    }
}

@Composable
private fun CodexKeyDetail(detail: CodexDetail.Key, catalog: UiCatalogSnapshot, ui: Float) {
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    val typography = codexMeasurer().typography
    if (detail.opposed.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CodexChipLabel(language.text(CodexText.OPPOSES), ui)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.opposed.forEach { CodexGlyphChip(null, it.displayLabel.localizedContent(language), ui, rim = roles.threat) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CodexChipLabel(language.text(CodexText.CARRIED_BY), ui)
        detail.relics.forEach { relic ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size((26f * ui).dp)) { drawRelicIcon(relic, catalog.relicPolicy, center, size.minDimension * 0.46f, roles = roles) }
                BasicText(relic.name.localizedContent(language).uppercase(), style = typography.condStyle(17f * ui, color = Kk.Bone))
            }
        }
        repeat(detail.hidden) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size((26f * ui).dp)) {
                    drawRect(Kk.Ink3)
                    drawKkHatch(Rect(Offset.Zero, size))
                }
                BasicText("?", style = typography.condStyle(17f * ui, color = Kk.Mute))
            }
        }
    }
}
