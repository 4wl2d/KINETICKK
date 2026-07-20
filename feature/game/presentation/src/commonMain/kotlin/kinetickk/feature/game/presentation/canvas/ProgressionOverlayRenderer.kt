// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.game.domain.model.MetaUpgradeCatalog
import kinetickk.feature.game.domain.model.MetaUpgradeDefinition
import kinetickk.feature.game.domain.model.RebirthProfile
import kinetickk.feature.game.domain.projection.GameProjection
import kotlin.math.max
import kotlin.math.min

internal fun DrawScope.drawLab(engine: GameProjection, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Acid)
    drawLabel(textMeasurer, "KINETIC LAB", bounds.left + d(25f), bounds.top + d(24f), 20f, Acid, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "PERMANENT RESEARCH // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(88f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = d(105f)
    MetaUpgradeCatalog.all.forEachIndexed { index, definition ->
        val column = index % 2
        val row = index / 2
        val left = bounds.left + d(25f) + columnWidth * column
        val top = contentTop + rowHeight * row
        drawMetaCard(engine, textMeasurer, definition, left, top, columnWidth, rowHeight)
    }
    drawLabFooter(textMeasurer, bounds, Acid)
}

internal fun DrawScope.drawMetaCard(engine: GameProjection, textMeasurer: TextMeasurer, definition: MetaUpgradeDefinition, x: Float, y: Float, width: Float, height: Float) {
    val level = engine.metaLevel(definition.id)
    val maxed = level >= definition.maxRanks
    val cost = if (maxed) 0L else definition.cost(level).toLong()
    val affordable = !maxed && engine.totalMatter >= cost
    val accent = if (maxed) Acid else if (affordable) Cyan else Muted
    drawRect(Color(0x99101225), Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.7f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, definition.name.uppercase(), x + d(14f), y + d(12f), 9f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "RANK $level/${definition.maxRanks}", x + width - d(14f), y + d(12f), 8f, White, alignRight = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(36f), 7f, Muted, maxWidth = width - d(28f), maxLines = 2)
    drawLabel(textMeasurer, if (maxed) "MAXIMUM SYNCHRONY" else "BUY ${formatCompact(cost)} MATTER", x + width - d(14f), y + height - d(24f), 8f, accent, alignRight = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawRebirth(engine: GameProjection, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    val current = engine.rebirthProfile
    val next = engine.nextRebirthProfile
    val maximumTier = next.tier <= current.tier
    drawOverlayFrame(bounds, Orange)
    drawLabel(textMeasurer, "REBIRTH PROTOCOL", bounds.left + d(24f), bounds.top + d(24f), 20f, Orange, weight = FontWeight.Bold)
    drawLabel(
        textMeasurer,
        "PERMANENT THREAT // TIER ${current.tier} > ${next.tier}",
        bounds.right - d(24f),
        bounds.top + d(30f),
        8f,
        White,
        alignRight = true,
    )

    val contentLeft = bounds.left + d(24f)
    val cardTop = bounds.top + d(78f)
    val cardGap = d(14f)
    val cardWidth = (bounds.width - d(62f)) * 0.5f
    val cardHeight = d(92f)
    drawRebirthTierCard(textMeasurer, current, "CURRENT CYCLE", contentLeft, cardTop, cardWidth, cardHeight, Cyan)
    drawRebirthTierCard(textMeasurer, next, "NEXT CYCLE", contentLeft + cardWidth + cardGap, cardTop, cardWidth, cardHeight, Orange)

    val statsTop = cardTop + cardHeight + d(14f)
    val statsHeight = min(d(220f), max(d(176f), bounds.bottom - d(210f) - statsTop))
    drawRebirthHostileStats(textMeasurer, current, next, contentLeft, statsTop, cardWidth, statsHeight)
    drawRebirthRewardStats(textMeasurer, current, next, contentLeft + cardWidth + cardGap, statsTop, cardWidth, statsHeight)

    drawLabel(textMeasurer, "RESET: RUN BUILD", bounds.center.x, bounds.bottom - d(194f), 9f, Orange, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "KEPT: MATTER // LAB // ARMORY // CODEX", bounds.center.x, bounds.bottom - d(172f), 9f, Acid, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "LIFETIME UNLOCKS + SETTINGS ALSO REMAIN", bounds.center.x, bounds.bottom - d(151f), 7f, Muted, centered = true)

    val actionLeft = bounds.left + d(24f)
    val actionTop = bounds.bottom - d(118f)
    val actionWidth = bounds.width - d(48f)
    val actionAccent = when {
        engine.rebirthConfirmationArmed -> Red
        engine.canRebirth && !maximumTier -> Acid
        else -> Muted
    }
    val actionLabel = when {
        maximumTier -> "MAXIMUM REBIRTH TIER"
        !engine.canRebirth -> "REBIRTH LOCKED"
        engine.rebirthConfirmationArmed -> "CONFIRM REBIRTH // ENTER TIER ${next.tier}"
        else -> "ARM REBIRTH // TIER ${current.tier} > ${next.tier}"
    }
    val actionDetail = when {
        maximumTier -> "ALL THREAT DIRECTIVES CLEARED"
        !engine.canRebirth -> "DEFEAT THE ARCHITECT ON TIER ${current.tier}"
        engine.rebirthConfirmationArmed -> "SECOND PRESS RESETS THE RUN BUILD"
        else -> "FIRST PRESS ARMS THIS TRANSITION"
    }
    drawRect(actionAccent.copy(alpha = 0.11f), Offset(actionLeft, actionTop), Size(actionWidth, d(50f)))
    drawRect(actionAccent, Offset(actionLeft, actionTop), Size(actionWidth, d(50f)), style = Stroke(d(if (engine.rebirthConfirmationArmed) 2f else 1.4f)))
    drawLabel(textMeasurer, actionLabel, bounds.center.x, actionTop + d(7f), 11f, actionAccent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, actionDetail, bounds.center.x, actionTop + d(28f), 7f, if (engine.canRebirth) White else Muted, centered = true)
    drawFooterBack(textMeasurer, bounds, Orange)
}

internal fun DrawScope.drawRebirthTierCard(
    textMeasurer: TextMeasurer,
    profile: RebirthProfile,
    eyebrow: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    accent: Color,
) {
    drawRect(Color(0x99101225), Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.75f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, eyebrow, x + d(13f), y + d(10f), 7f, Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "TIER ${profile.tier} // ${profile.directive.displayName.uppercase()}", x + d(13f), y + d(30f), 11f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, profile.directive.description, x + d(13f), y + d(55f), 7f, White, maxWidth = width - d(26f), maxLines = 2)
}

internal fun DrawScope.drawRebirthHostileStats(
    textMeasurer: TextMeasurer,
    current: RebirthProfile,
    next: RebirthProfile,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRect(Color(0x80101225), Offset(x, y), Size(width, height))
    drawRect(Red.copy(alpha = 0.45f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "HOSTILE ESCALATION", x + d(13f), y + d(11f), 8f, Red, weight = FontWeight.Bold)
    val rowY = y + d(38f)
    val rowStep = (height - d(62f)) / 5f
    drawRebirthStatRow(textMeasurer, "OPENING HOSTILES", current.openingEnemyCount.toString(), next.openingEnemyCount.toString(), x, rowY, width, Red)
    drawRebirthStatRow(textMeasurer, "ACTIVE ENEMY CAP", formatMultiplier(current.enemyCapMultiplier), formatMultiplier(next.enemyCapMultiplier), x, rowY + rowStep, width, Red)
    drawRebirthStatRow(textMeasurer, "SPAWN RATE", formatMultiplier(current.spawnRateMultiplier), formatMultiplier(next.spawnRateMultiplier), x, rowY + rowStep * 2f, width, Red)
    drawRebirthStatRow(textMeasurer, "ENEMY INTEGRITY", formatMultiplier(current.enemyHealthMultiplier), formatMultiplier(next.enemyHealthMultiplier), x, rowY + rowStep * 3f, width, Red)
    drawRebirthStatRow(textMeasurer, "ENEMY SPEED", formatMultiplier(current.enemySpeedMultiplier), formatMultiplier(next.enemySpeedMultiplier), x, rowY + rowStep * 4f, width, Red)
    drawRebirthStatRow(textMeasurer, "INCOMING DAMAGE", formatMultiplier(current.incomingDamageMultiplier), formatMultiplier(next.incomingDamageMultiplier), x, rowY + rowStep * 5f, width, Red)
}

internal fun DrawScope.drawRebirthRewardStats(
    textMeasurer: TextMeasurer,
    current: RebirthProfile,
    next: RebirthProfile,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRect(Color(0x80101225), Offset(x, y), Size(width, height))
    drawRect(Acid.copy(alpha = 0.45f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "CYCLE COMPENSATION", x + d(13f), y + d(11f), 8f, Acid, weight = FontWeight.Bold)
    val rowY = y + d(38f)
    val rowStep = (height - d(67f)) / 4f
    drawRebirthStatRow(textMeasurer, "THREAT ADVANCE", "+${current.threatTimeOffsetSeconds.toInt()}s", "+${next.threatTimeOffsetSeconds.toInt()}s", x, rowY, width, Orange)
    drawRebirthStatRow(textMeasurer, "PLAYER POWER", formatMultiplier(current.playerPowerMultiplier), formatMultiplier(next.playerPowerMultiplier), x, rowY + rowStep, width, Acid)
    drawRebirthStatRow(textMeasurer, "CORE INTEGRITY", "+${current.playerIntegrityBonus.toInt()}", "+${next.playerIntegrityBonus.toInt()}", x, rowY + rowStep * 2f, width, Acid)
    drawRebirthStatRow(textMeasurer, "KINETIC MATTER", formatMultiplier(current.matterGainMultiplier), formatMultiplier(next.matterGainMultiplier), x, rowY + rowStep * 3f, width, Acid)
    drawRebirthStatRow(textMeasurer, "BONUS REROLLS", "+${current.bonusRerolls}", "+${next.bonusRerolls}", x, rowY + rowStep * 4f, width, Acid)
}

internal fun DrawScope.drawRebirthStatRow(
    textMeasurer: TextMeasurer,
    label: String,
    current: String,
    next: String,
    x: Float,
    y: Float,
    width: Float,
    accent: Color,
) {
    drawLabel(textMeasurer, label, x + d(13f), y, 7f, Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "$current  >  $next", x + width - d(13f), y, 7f, accent, alignRight = true, weight = FontWeight.Bold)
}
