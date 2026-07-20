// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.core.design.Acid
import kinetickk.core.design.Cyan
import kinetickk.core.design.Muted
import kinetickk.core.design.TextMeasurer
import kinetickk.core.design.White
import kinetickk.core.design.d
import kinetickk.core.design.drawLabel
import kinetickk.core.design.drawLabFooter
import kinetickk.core.design.drawOverlayFrame
import kinetickk.core.design.formatCompact
import kinetickk.core.design.overlayBounds
import kinetickk.feature.lab.api.LabRenderModel
import kinetickk.feature.lab.api.LabUpgradeRenderModel

internal fun DrawScope.drawLab(model: LabRenderModel, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Acid)
    drawLabel(textMeasurer, "KINETIC LAB", bounds.left + d(25f), bounds.top + d(24f), 20f, Acid, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "PERMANENT RESEARCH // MATTER ${formatCompact(model.matter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(88f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = d(105f)
    model.upgrades.forEachIndexed { index, upgrade ->
        val column = index % 2
        val row = index / 2
        val left = bounds.left + d(25f) + columnWidth * column
        val top = contentTop + rowHeight * row
        drawMetaCard(textMeasurer, upgrade, left, top, columnWidth, rowHeight)
    }
    drawLabFooter(textMeasurer, bounds, Acid)
}

private fun DrawScope.drawMetaCard(
    textMeasurer: TextMeasurer,
    upgrade: LabUpgradeRenderModel,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    val accent = if (upgrade.isMaxed) Acid else if (upgrade.isAffordable) Cyan else Muted
    drawRect(Color(0x99101225), Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.7f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, upgrade.name.uppercase(), x + d(14f), y + d(12f), 9f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "RANK ${upgrade.rank}/${upgrade.maxRanks}", x + width - d(14f), y + d(12f), 8f, White, alignRight = true)
    drawLabel(textMeasurer, upgrade.description, x + d(14f), y + d(36f), 7f, Muted, maxWidth = width - d(28f), maxLines = 2)
    drawLabel(textMeasurer, if (upgrade.isMaxed) "MAXIMUM SYNCHRONY" else "BUY ${formatCompact(upgrade.nextCost)} MATTER", x + width - d(14f), y + height - d(24f), 8f, accent, alignRight = true, weight = FontWeight.Bold)
}
