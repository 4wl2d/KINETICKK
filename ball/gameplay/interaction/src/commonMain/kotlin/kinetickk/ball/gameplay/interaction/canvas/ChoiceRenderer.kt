// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.ChoiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawChoice(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    layout: ChoiceLayoutGeometry,
) {
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
        ChoiceType.RELIC -> if (engine.equippedRelics.size >= engine.content.relicPolicy.maxSlots) {
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
    val compact = layout.mode != GameplayLayoutMode.REGULAR
    drawLabel(textMeasurer, title, size.width * 0.5f, layout.titleY, if (compact) 17f else 24f, titleAccent, centered = true, weight = FontWeight.Bold, maxWidth = size.width - d(24f))
    drawLabel(textMeasurer, subtitle, size.width * 0.5f, layout.subtitleY, if (compact) 7f else 9f, subtitleAccent, centered = true, maxWidth = size.width - d(30f), maxLines = 2)
    engine.choices.forEachIndexed { index, choice ->
        val bounds = layout.cards[index]
        if (layout.compactCardContent) {
            drawCompactChoiceCard(engine, textMeasurer, choice, index, bounds.topLeft, bounds.size, renderTime)
        } else {
            drawChoiceCard(engine, textMeasurer, choice, index, bounds.left, bounds.top, bounds.width, bounds.height, renderTime)
        }
    }
    layout.reroll?.let { bounds ->
        val accent = if (engine.choiceType == ChoiceType.RELIC) Gold else Violet
        drawRect(accent.copy(alpha = 0.1f), bounds.topLeft, bounds.size)
        drawRect(accent, bounds.topLeft, bounds.size, style = Stroke(d(1.3f)))
        drawLabel(textMeasurer, if (compact) "REROLL // ${engine.rerollsRemaining}" else "REROLL [Q] // ${engine.rerollsRemaining}", bounds.center.x, bounds.center.y - d(7f), 9f, accent, centered = true, weight = FontWeight.Bold)
    }
}

private fun DrawScope.drawCompactChoiceCard(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    choice: ChoiceOption,
    index: Int,
    topLeft: Offset,
    cardSize: Size,
    renderTime: Float,
) {
    val item = choice.itemId?.let(engine.content::item)
    val weapon = choice.weaponId?.let(engine.content::weapon)
    val relicId = choice.relicId ?: choice.relicSlot?.let(engine.equippedRelics::getOrNull)?.id
    val relic = relicId?.let(engine.content::relic)
    val accent = when {
        relic != null -> relicAspectColor(relic.aspect)
        item != null -> rarityColor(item.rarity)
        weapon != null -> weaponColor(weapon.id)
        else -> ParticleColors[index.coerceIn(ParticleColors.indices)]
    }
    val pulse = (sin(renderTime * 2.4f + index * 1.6f) + 1f) * 0.5f
    drawRect(OverlayPanel, topLeft, cardSize)
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), topLeft, cardSize, style = Stroke(d(1.4f)))
    drawRect(accent.copy(alpha = 0.14f), topLeft, Size(cardSize.width, d(34f)))
    drawLabel(textMeasurer, "0${index + 1} // ${choice.tag}", topLeft.x + d(10f), topLeft.y + d(10f), 6f, accent, weight = FontWeight.Bold, maxWidth = cardSize.width - d(20f))
    drawLabel(textMeasurer, choice.title.uppercase(), topLeft.x + cardSize.width * 0.5f, topLeft.y + d(42f), 8f, White, centered = true, weight = FontWeight.Bold, maxWidth = cardSize.width - d(16f), maxLines = 2)
    drawLabel(textMeasurer, choice.description, topLeft.x + cardSize.width * 0.5f, topLeft.y + d(68f), 5.5f, Muted, centered = true, maxWidth = cardSize.width - d(18f), maxLines = 2)
    val footer = when {
        relic != null -> relic.rankEffect
        item != null -> "STACK ${engine.itemStack(item.id) + 1}/${item.maxStacks}"
        weapon != null -> weapon.tags.firstOrNull().orEmpty()
        else -> choice.tag
    }
    drawLabel(textMeasurer, footer, topLeft.x + cardSize.width * 0.5f, topLeft.y + cardSize.height - d(38f), 5.5f, accent, centered = true, maxWidth = cardSize.width - d(18f), maxLines = 1)
    drawLabel(textMeasurer, "TAP TO SELECT", topLeft.x + cardSize.width * 0.5f, topLeft.y + cardSize.height - d(16f), 7f, accent, centered = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawChoiceCard(engine: GameplayRenderModel, textMeasurer: TextMeasurer, choice: ChoiceOption, index: Int, x: Float, y: Float, width: Float, height: Float, renderTime: Float) {
    if (choice.type == ChoiceType.RELIC || choice.type == ChoiceType.RELIC_BIND) {
        drawRelicChoiceCard(engine, textMeasurer, choice, index, x, y, width, height, renderTime)
        return
    }
    val item = choice.itemId?.let(engine.content::item)
    val weapon = choice.weaponId?.let(engine.content::weapon)
    val accent = item?.let { rarityColor(it.rarity) } ?: weapon?.let { weaponColor(it.id) } ?: ParticleColors[index.coerceIn(0, 2)]
    drawRect(OverlayPanel, Offset(x, y), Size(width, height))
    val pulse = (sin(renderTime * 2.4f + index * 1.6f) + 1f) * 0.5f
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), Offset(x, y), Size(width, height), style = Stroke(d(1.5f + pulse * 0.5f)))
    drawRect(accent.copy(alpha = 0.16f), Offset(x, y), Size(width, d(43f)))
    val tag = if (choice.type == ChoiceType.TOTEM) choice.tag else weapon?.tags?.joinToString(" / ") ?: choice.tag
    drawLabel(textMeasurer, "0${index + 1} // $tag", x + d(16f), y + d(15f), 8f, accent, weight = FontWeight.Bold)
    val glyphCenter = Offset(x + width * 0.5f, y + d(91f))
    when {
        weapon != null -> drawSystemGlyph(
            weaponGlyphStyle(weapon.id),
            glyphCenter,
            d(25f),
            renderTime,
            accent,
        )
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
    engine: GameplayRenderModel,
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
    val relic = displayRelicId?.let(engine.content::relic)
    val replacementRelic = if (choice.relicAction == RelicChoiceAction.REPLACE) {
        choice.relicId?.let(engine.content::relic)
    } else {
        null
    }
    val relicPolicy = engine.content.relicPolicy
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
        RelicChoiceAction.ACQUIRE -> optionRelicId?.let { (engine.relicRank(it) + 1).coerceIn(1, relicPolicy.maxRank) }
        RelicChoiceAction.REPLACE -> slotRelic?.rank
        RelicChoiceAction.MELD_TARGET -> slotRelic?.rank?.plus(1)?.coerceAtMost(relicPolicy.maxRank)
        RelicChoiceAction.MELD, null -> null
    }
    if (relic != null) {
        drawRelicIcon(
            definition = relic,
            policy = relicPolicy,
            center = glyphCenter,
            radius = d(if (width / density < 175f) 21f else 24f),
            rank = previewRank,
            time = renderTime,
        )
        replacementRelic?.let { incoming ->
            val incomingCenter = Offset(glyphCenter.x + d(23f), glyphCenter.y + d(13f))
            drawCircle(SpaceBlack.copy(alpha = 0.92f), d(12f), incomingCenter)
            drawRelicIcon(
                definition = incoming,
                policy = relicPolicy,
                center = incomingCenter,
                radius = d(9f),
                rank = 1,
                time = renderTime,
            )
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
            optionRelicId != null && engine.relicRank(optionRelicId) >= relicPolicy.maxRank -> "SALVAGE RESONANCE"
            optionRelicId != null && engine.relicRank(optionRelicId) > 0 ->
                "MELD // R${engine.relicRank(optionRelicId)} > R${previewRank ?: engine.relicRank(optionRelicId)}"
            else -> "BIND TO MATRIX"
        }
        RelicChoiceAction.MELD -> "MELD SIGNAL INTO A SLOT"
        RelicChoiceAction.REPLACE -> "REPLACE SLOT ${(choice.relicSlot ?: index) + 1}"
        RelicChoiceAction.MELD_TARGET -> if ((slotRelic?.rank ?: 1) >= relicPolicy.maxRank) {
            "SALVAGE EXCESS"
        } else {
            "MELD // R${slotRelic?.rank ?: 1} > R${previewRank ?: slotRelic?.rank ?: 1}"
        }
        null -> choice.tag
    }
    drawLabel(textMeasurer, actionLabel, x + width * 0.5f, y + height - d(45f), if (width / density < 175f) 6f else 7f, accent, centered = true, weight = FontWeight.Bold, maxWidth = width - d(14f))
    drawLabel(textMeasurer, "SELECT [${index + 1}]", x + width * 0.5f, y + height - d(24f), 8f, accent, centered = true, weight = FontWeight.Bold)
}
