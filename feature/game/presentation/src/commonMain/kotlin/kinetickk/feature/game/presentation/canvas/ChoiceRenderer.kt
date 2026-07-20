// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.game.domain.model.ChoiceOption
import kinetickk.feature.game.domain.model.ChoiceType
import kinetickk.feature.game.domain.model.ItemCatalog
import kinetickk.feature.game.domain.model.RelicCatalog
import kinetickk.feature.game.domain.model.RelicChoiceAction
import kinetickk.feature.game.domain.model.TotemAction
import kinetickk.feature.game.domain.model.WeaponCatalog
import kinetickk.feature.game.domain.projection.GameProjection
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawChoice(engine: GameProjection, textMeasurer: TextMeasurer, renderTime: Float) {
    drawRect(Color(0xF2050610))
    val bindAction = engine.choices.firstOrNull()?.relicAction
    val title = when (engine.choiceType) {
        ChoiceType.ITEM -> "CHOOSE AN ARTIFACT"
        ChoiceType.TOTEM -> "TOTEM RESONANCE"
        ChoiceType.WEAPON -> "WEAPON SYNCHRONIZATION"
        ChoiceType.RELIC -> "RELIC INTERCEPT"
        ChoiceType.RELIC_BIND -> if (bindAction == RelicChoiceAction.MELD_TARGET) "RELIC MELD" else "RELIC REBIND"
    }
    val subtitle = when (engine.choiceType) {
        ChoiceType.ITEM -> "TIME IS SUSPENDED"
        ChoiceType.TOTEM -> "AMPLIFY THE CURRENT SYSTEM OR RECALIBRATE"
        ChoiceType.WEAPON -> "SELECT THE NEXT RUN WEAPON"
        ChoiceType.RELIC -> if (engine.equippedRelics.size >= RelicCatalog.MAX_SLOTS) {
            "MATRIX FULL // CLAIM A SIGNAL OR MELD IT INTO THE MATRIX"
        } else {
            "ELITE SIGNAL CAPTURED // RELICS SYNCHRONIZE WITH EVERY WEAPON"
        }
        ChoiceType.RELIC_BIND -> if (bindAction == RelicChoiceAction.MELD_TARGET) {
            "SELECT A TARGET // THE CHOSEN RELIC GAINS ONE RANK"
        } else {
            "SELECT A SLOT // ITS CURRENT RELIC WILL BE REPLACED"
        }
    }
    val titleAccent = if (engine.choiceType == ChoiceType.RELIC || engine.choiceType == ChoiceType.RELIC_BIND) Gold else White
    val subtitleAccent = if (engine.choiceType == ChoiceType.RELIC || engine.choiceType == ChoiceType.RELIC_BIND) Magenta else Violet
    drawLabel(textMeasurer, title, size.width * 0.5f, size.height * 0.14f, 24f, titleAccent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, subtitle, size.width * 0.5f, size.height * 0.17f + d(36f), 9f, subtitleAccent, centered = true, maxWidth = size.width - d(30f), maxLines = 2)
    val choiceCount = engine.choices.size.coerceAtLeast(1)
    val gap = d(if (choiceCount >= 4) 10f else 18f)
    val maxCardWidth = d(when {
        choiceCount >= 4 -> 190f
        choiceCount == 3 -> 250f
        else -> 300f
    })
    val availableCardWidth = (size.width - d(30f) - gap * (choiceCount - 1)) / choiceCount
    val cardWidth = min(maxCardWidth, availableCardWidth).coerceAtLeast(d(92f))
    val total = cardWidth * choiceCount + gap * (choiceCount - 1)
    val startX = (size.width - total) * 0.5f
    val top = size.height * if (choiceCount >= 4) 0.29f else 0.31f
    val bottomReserve = d(if (engine.choicesCanReroll) 105f else 35f)
    val cardHeight = min(d(270f), size.height - bottomReserve - top).coerceAtLeast(d(170f))
    engine.choices.forEachIndexed { index, choice ->
        drawChoiceCard(engine, textMeasurer, choice, index, startX + index * (cardWidth + gap), top, cardWidth, cardHeight, renderTime)
    }
    if (engine.choicesCanReroll) {
        val rerollY = size.height - d(72f)
        val accent = if (engine.choiceType == ChoiceType.RELIC) Gold else Violet
        drawRect(accent.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(90f), rerollY - d(22f)), Size(d(180f), d(44f)))
        drawRect(accent, Offset(size.width * 0.5f - d(90f), rerollY - d(22f)), Size(d(180f), d(44f)), style = Stroke(d(1.3f)))
        drawLabel(textMeasurer, "REROLL [Q] // ${engine.rerollsRemaining}", size.width * 0.5f, rerollY - d(6f), 9f, accent, centered = true, weight = FontWeight.Bold)
    }
}

internal fun DrawScope.drawChoiceCard(engine: GameProjection, textMeasurer: TextMeasurer, choice: ChoiceOption, index: Int, x: Float, y: Float, width: Float, height: Float, renderTime: Float) {
    if (choice.type == ChoiceType.RELIC || choice.type == ChoiceType.RELIC_BIND) {
        drawRelicChoiceCard(engine, textMeasurer, choice, index, x, y, width, height, renderTime)
        return
    }
    val item = choice.itemId?.let(ItemCatalog::byId)
    val weapon = choice.weaponId?.let(WeaponCatalog::byId)
    val accent = item?.let { rarityColor(it.rarity) } ?: weapon?.let { weaponColor(it.id) } ?: ParticleColors[index.coerceIn(0, 2)]
    drawRect(OverlayPanel, Offset(x, y), Size(width, height))
    val pulse = (sin(renderTime * 2.4f + index * 1.6f) + 1f) * 0.5f
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), Offset(x, y), Size(width, height), style = Stroke(d(1.5f + pulse * 0.5f)))
    drawRect(accent.copy(alpha = 0.16f), Offset(x, y), Size(width, d(43f)))
    val tag = if (choice.type == ChoiceType.TOTEM) choice.tag else weapon?.tags?.joinToString(" / ") ?: choice.tag
    drawLabel(textMeasurer, "0${index + 1} // $tag", x + d(16f), y + d(15f), 8f, accent, weight = FontWeight.Bold)
    val glyphCenter = Offset(x + width * 0.5f, y + d(91f))
    when {
        weapon != null -> drawWeaponGlyph(weapon.id, glyphCenter, d(25f), renderTime, accent)
        item != null -> drawItemIcon(
            item = item,
            center = glyphCenter,
            radius = d(26f),
            accent = accent,
            stack = engine.itemStack(item.id) + 1,
        )
        choice.type == ChoiceType.TOTEM -> {
            drawCircle(accent.copy(alpha = 0.16f), d(28f), glyphCenter)
            drawCircle(accent, d(24f), glyphCenter, style = Stroke(d(2f), pathEffect = dashEffect))
            drawPolygon(glyphCenter, d(13f), 6, renderTime * 0.8f, White, Stroke(d(1.6f)))
            drawLine(accent, Offset(glyphCenter.x - d(19f), glyphCenter.y), Offset(glyphCenter.x + d(19f), glyphCenter.y), d(2f), StrokeCap.Round)
        }
        else -> drawPolygon(glyphCenter, d(25f), index + 4, renderTime * 0.32f + index * 0.7f, accent, Stroke(d(2f)))
    }
    drawLabel(textMeasurer, choice.title.uppercase(), x + width * 0.5f, y + d(132f), if (width / density < 200f) 10f else 12f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, choice.description, x + width * 0.5f, y + d(159f), if (width / density < 200f) 7f else 8f, Muted, centered = true, maxWidth = width - d(28f), maxLines = 3)
    val footer = when {
        item != null -> "STACK ${engine.itemStack(item.id) + 1}/${item.maxStacks}"
        choice.type == ChoiceType.TOTEM && choice.totemAction == TotemAction.AMPLIFY_CURRENT ->
            "CURRENT WEAPON // LV ${engine.weaponLevel} > ${engine.weaponLevel + 1}"
        choice.type == ChoiceType.TOTEM -> "OPEN WEAPON PICKER"
        weapon != null -> "RUN WEAPON // ${weapon.tags.first()}"
        else -> choice.tag
    }
    drawLabel(textMeasurer, footer, x + width * 0.5f, y + d(191f), 8f, accent, centered = true)
    drawLabel(textMeasurer, "SELECT [${index + 1}]", x + width * 0.5f, y + height - d(30f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawRelicChoiceCard(
    engine: GameProjection,
    textMeasurer: TextMeasurer,
    choice: ChoiceOption,
    index: Int,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    renderTime: Float,
) {
    val slotRelic = choice.relicSlot?.let(engine.equippedRelics::getOrNull)
    val optionRelicId = choice.relicId ?: slotRelic?.id
    val displayRelicId = if (choice.relicAction == RelicChoiceAction.REPLACE) slotRelic?.id ?: optionRelicId else optionRelicId
    val relic = displayRelicId?.let(RelicCatalog::byId)
    val replacementRelic = if (choice.relicAction == RelicChoiceAction.REPLACE) choice.relicId?.let(RelicCatalog::byId) else null
    val accent = relic?.let { relicAspectColor(it.aspect) } ?: Gold
    val pulse = (sin(renderTime * 2.1f + index * 1.45f) + 1f) * 0.5f
    drawRect(OverlayPanel, Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), Offset(x, y), Size(width, height), style = Stroke(d(1.4f + pulse * 0.45f)))
    drawRect(accent.copy(alpha = 0.13f), Offset(x, y), Size(width, d(39f)))

    val slotLabel = choice.relicSlot?.let { "SLOT ${it + 1}" }
    val tag = when {
        choice.tag.isNotBlank() && (slotLabel == null || choice.tag.contains(slotLabel)) -> choice.tag
        else -> listOfNotNull(slotLabel, choice.tag.takeIf { it.isNotBlank() }).joinToString(" // ")
    }
    drawLabel(
        textMeasurer,
        "0${index + 1} // $tag",
        x + d(10f),
        y + d(13f),
        if (width / density < 175f) 6f else 7f,
        accent,
        weight = FontWeight.Bold,
        maxWidth = width - d(20f),
    )

    val glyphCenter = Offset(x + width * 0.5f, y + d(78f))
    val previewRank = when (choice.relicAction) {
        RelicChoiceAction.ACQUIRE -> optionRelicId?.let { (engine.relicRank(it) + 1).coerceIn(1, RelicCatalog.MAX_RANK) }
        RelicChoiceAction.REPLACE -> slotRelic?.rank
        RelicChoiceAction.MELD_TARGET -> slotRelic?.rank?.plus(1)?.coerceAtMost(RelicCatalog.MAX_RANK)
        RelicChoiceAction.MELD, null -> null
    }
    if (displayRelicId != null) {
        drawRelicIcon(displayRelicId, glyphCenter, d(if (width / density < 175f) 21f else 24f), previewRank, renderTime)
        replacementRelic?.let { incoming ->
            val incomingCenter = Offset(glyphCenter.x + d(23f), glyphCenter.y + d(13f))
            drawCircle(SpaceBlack.copy(alpha = 0.92f), d(12f), incomingCenter)
            drawRelicIcon(incoming.id, incomingCenter, d(9f), rank = 1, time = renderTime)
            drawLabel(textMeasurer, "→", glyphCenter.x + d(13f), glyphCenter.y - d(2f), 7f, Gold, centered = true, weight = FontWeight.Bold)
        }
    } else {
        drawUnresolvedRelicIcon(glyphCenter, d(23f), renderTime)
        drawLabel(textMeasurer, "+", glyphCenter.x + d(23f), glyphCenter.y - d(9f), 12f, White, centered = true, weight = FontWeight.Bold)
    }

    drawLabel(
        textMeasurer,
        choice.title.uppercase(),
        x + width * 0.5f,
        y + d(112f),
        if (width / density < 175f) 8f else 10f,
        White,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = width - d(18f),
        maxLines = 2,
    )
    drawLabel(
        textMeasurer,
        choice.description,
        x + width * 0.5f,
        y + d(143f),
        if (width / density < 175f) 6f else 7f,
        Muted,
        centered = true,
        maxWidth = width - d(20f),
        maxLines = 3,
    )
    relic?.let {
        drawLabel(
            textMeasurer,
            it.rankEffect,
            x + width * 0.5f,
            y + height - d(76f),
            if (width / density < 175f) 5.5f else 6.5f,
            accent,
            centered = true,
            maxWidth = width - d(20f),
            maxLines = 2,
        )
    }
    val actionLabel = when (choice.relicAction) {
        RelicChoiceAction.ACQUIRE -> when {
            optionRelicId != null && engine.relicRank(optionRelicId) >= RelicCatalog.MAX_RANK -> "SALVAGE RESONANCE"
            optionRelicId != null && engine.relicRank(optionRelicId) > 0 ->
                "MELD // R${engine.relicRank(optionRelicId)} > R${previewRank ?: engine.relicRank(optionRelicId)}"
            else -> "BIND TO MATRIX"
        }
        RelicChoiceAction.MELD -> "MELD SIGNAL INTO A SLOT"
        RelicChoiceAction.REPLACE -> "REPLACE SLOT ${(choice.relicSlot ?: index) + 1}"
        RelicChoiceAction.MELD_TARGET -> if ((slotRelic?.rank ?: 1) >= RelicCatalog.MAX_RANK) {
            "SALVAGE EXCESS"
        } else {
            "MELD // R${slotRelic?.rank ?: 1} > R${previewRank ?: slotRelic?.rank ?: 1}"
        }
        null -> choice.tag
    }
    drawLabel(textMeasurer, actionLabel, x + width * 0.5f, y + height - d(45f), if (width / density < 175f) 6f else 7f, accent, centered = true, weight = FontWeight.Bold, maxWidth = width - d(14f))
    drawLabel(textMeasurer, "SELECT [${index + 1}]", x + width * 0.5f, y + height - d(24f), 8f, accent, centered = true, weight = FontWeight.Bold)
}
