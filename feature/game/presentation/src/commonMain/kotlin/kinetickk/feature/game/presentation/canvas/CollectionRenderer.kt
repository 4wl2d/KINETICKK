// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.ItemDefinition
import kinetickk.feature.game.domain.model.WeaponCatalog
import kinetickk.feature.game.domain.model.WeaponDefinition
import kinetickk.feature.game.domain.projection.GameProjection
import kotlin.math.min

internal fun DrawScope.drawArmory(engine: GameProjection, textMeasurer: TextMeasurer, renderTime: Float) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Cyan)
    drawLabel(textMeasurer, "WEAPON ARMORY", bounds.left + d(25f), bounds.top + d(24f), 20f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${WeaponCatalog.all.size} SYSTEMS // ${engine.unlockedWeapons.size} UNLOCKED // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val cardWidth = min(d(245f), (bounds.width - d(80f)) / 3f)
    val gap = d(16f)
    val total = cardWidth * 3f + gap * 2f
    val startX = (size.width - total) * 0.5f
    val cardTop = bounds.top + d(118f)
    val cardBottom = bounds.bottom - d(85f)
    engine.armoryPageWeapons.forEachIndexed { index, definition ->
        drawWeaponCard(engine, textMeasurer, definition, startX + index * (cardWidth + gap), cardTop, cardWidth, cardBottom - cardTop, renderTime)
    }
    drawPagedFooter(textMeasurer, bounds, engine.armoryPage, engine.maxArmoryPage, Cyan)
}

internal fun DrawScope.drawWeaponCard(engine: GameProjection, textMeasurer: TextMeasurer, definition: WeaponDefinition, x: Float, y: Float, width: Float, height: Float, renderTime: Float) {
    val unlocked = engine.isWeaponUnlocked(definition.id)
    val equipped = engine.startingWeapon == definition.id
    val active = engine.weapon == definition.id && engine.phase != GamePhase.MENU
    val accent = if (unlocked) weaponColor(definition.id) else Muted
    drawRect(Color(0xB00B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(if (equipped) 2.2f else 1f)))
    drawRect(accent.copy(alpha = 0.12f), Offset(x, y), Size(width, d(50f)))
    drawWeaponGlyph(definition.id, Offset(x + width * 0.5f, y + d(95f)), d(28f), renderTime, accent)
    drawLabel(textMeasurer, definition.name.uppercase(), x + width * 0.5f, y + d(139f), 11f, accent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, definition.tags.joinToString(" / "), x + width * 0.5f, y + d(164f), 7f, Muted, centered = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(193f), 7f, White, maxWidth = width - d(28f), maxLines = 3)
    drawLabel(textMeasurer, WeaponMasteryProgressionLabel, x + width * 0.5f, y + d(274f), 6f, accent, centered = true, maxWidth = width - d(20f), maxLines = 2)
    drawLabel(textMeasurer, "MILESTONES BOOST DAMAGE + ACTIVATION", x + width * 0.5f, y + d(295f), 6f, Muted, centered = true)
    val state = when {
        equipped -> "EQUIPPED LOADOUT"
        active -> "ACTIVE THIS RUN"
        unlocked -> "EQUIP"
        else -> "UNLOCK ${formatCompact(definition.permanentUnlockCost.toLong())}"
    }
    drawLabel(textMeasurer, state, x + width * 0.5f, y + height - d(34f), 9f, if (equipped) Acid else accent, centered = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawCodex(engine: GameProjection, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Magenta)
    drawLabel(textMeasurer, "ARTIFACT CODEX", bounds.left + d(25f), bounds.top + d(24f), 20f, Magenta, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${engine.discoveredItemCount}/400 DISCOVERED // PAGE ${engine.codexPage + 1}/${engine.maxCodexPage + 1}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(76f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = (bounds.height - d(146f)) / 5f
    engine.codexPageItems.forEachIndexed { index, item ->
        val column = index % 2
        val row = index / 2
        val x = bounds.left + d(25f) + column * columnWidth
        val y = contentTop + row * rowHeight
        drawCodexItem(engine, textMeasurer, item, x, y, columnWidth - d(10f), rowHeight - d(8f))
    }
    drawPagedFooter(textMeasurer, bounds, engine.codexPage, engine.maxCodexPage, Magenta)
}

internal fun DrawScope.drawCodexItem(engine: GameProjection, textMeasurer: TextMeasurer, item: ItemDefinition, x: Float, y: Float, width: Float, height: Float) {
    val discovered = engine.isItemDiscovered(item.id)
    val stack = engine.itemStack(item.id)
    val accent = if (discovered) rarityColor(item.rarity) else DarkLine
    drawRect(Color(0x8A0B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "#${item.id.toString().padStart(3, '0')}", x + d(10f), y + d(9f), 7f, Muted)
    drawLabel(textMeasurer, if (discovered) item.name.uppercase() else "UNKNOWN SIGNAL", x + d(47f), y + d(9f), 8f, if (discovered) accent else Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (discovered) "${item.rarity.displayLabel.uppercase()} // STACK $stack/${item.maxStacks}" else "LOCKED // LEVEL ${item.unlockLevel}", x + width - d(10f), y + d(9f), 7f, if (discovered) White else Muted, alignRight = true)
    drawItemIcon(
        item = item,
        center = Offset(x + d(25f), y + d(43f)),
        radius = d(14f),
        accent = accent,
        stack = if (discovered) stack else null,
        obscured = !discovered,
    )
    drawLabel(
        textMeasurer,
        if (discovered) item.description else "Acquire during a run to decode this artifact.",
        x + d(49f),
        y + d(33f),
        7f,
        Muted,
        maxWidth = width - d(59f),
        maxLines = 2,
    )
}
