// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.ball.content.api.localizedContent
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.foundation.design.*

@Composable
internal fun CodexRarityLegend(catalog: UiCatalogSnapshot, model: CodexRenderModel, ui: Float) {
    val language = LocalAppLanguage.current
    val typography = codexMeasurer().typography
    val counts = remember(catalog, model) {
        IntArray(ItemRarity.entries.size).also { counts ->
            catalog.items.forEach { item -> if (model.isDiscovered(item.id)) counts[item.rarity.ordinal]++ }
        }
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ItemRarity.entries.forEach { rarity ->
            LegendRow(rarity.displayLabel.localizedContent(language), counts[rarity.ordinal], rarity.rank, Kk.Bone, typography, ui)
        }
        LegendRow(language.text(SessionText.UNDISCOVERED), catalog.items.size - counts.sum(), null, Kk.Mute, typography, ui)
    }
}

/** A legend row: the rarity's fill as a small swatch (the fill its cells in the Items matrix wear), name and count. */
@Composable
private fun LegendRow(label: String, count: Int, rank: Int?, color: Color, typography: InterfaceTypography, ui: Float) {
    val roles = LocalKkRolePalette.current
    val fill = remember(rank, roles) { rank?.let { codexRarityFill(it, roles) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).drawWithCache {
            val rect = Rect(Offset.Zero, size)
            val brush = fill?.brush(rect)
            onDrawBehind {
                if (brush != null) drawRect(brush) else {
                    // Undiscovered: the matrix's hatch cell.
                    drawRect(Kk.Ink3)
                    drawKkHatch(rect)
                }
                fill?.seam?.let { seam ->
                    val line = 1.dp.toPx()
                    drawRect(seam, Offset(line * 0.5f, line * 0.5f), Size(size.width - line, size.height - line), style = kkStroke(line))
                }
            }
        })
        BasicText(label, Modifier.weight(1f), style = typography.bodyStyle(15f * ui, color = color))
        BasicText(count.toString(), style = typography.monoStyle(11f * ui, color = Kk.Mute))
    }
}
