// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.core.content.RebirthProfile
import kinetickk.core.design.Acid
import kinetickk.core.design.Cyan
import kinetickk.core.design.Muted
import kinetickk.core.design.Orange
import kinetickk.core.design.Red
import kinetickk.core.design.TextMeasurer
import kinetickk.core.design.White
import kinetickk.core.design.d
import kinetickk.core.design.drawFooterBack
import kinetickk.core.design.drawLabel
import kinetickk.core.design.drawOverlayFrame
import kinetickk.core.design.formatMultiplier
import kinetickk.core.design.overlayBounds
import kinetickk.feature.rebirth.api.RebirthRenderModel
import kotlin.math.max
import kotlin.math.min

internal fun DrawScope.drawRebirth(
    model: RebirthRenderModel,
    confirmationArmed: Boolean,
    textMeasurer: TextMeasurer,
) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    val current = model.current
    val next = model.next
    val maximumTier = model.isMaximumTier
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
        confirmationArmed -> Red
        model.canAdvance && !maximumTier -> Acid
        else -> Muted
    }
    val actionLabel = when {
        maximumTier -> "MAXIMUM REBIRTH TIER"
        !model.canAdvance -> "REBIRTH LOCKED"
        confirmationArmed -> "CONFIRM REBIRTH // ENTER TIER ${next.tier}"
        else -> "ARM REBIRTH // TIER ${current.tier} > ${next.tier}"
    }
    val actionDetail = when {
        maximumTier -> "ALL THREAT DIRECTIVES CLEARED"
        !model.canAdvance -> "DEFEAT THE ARCHITECT ON TIER ${current.tier}"
        confirmationArmed -> "SECOND PRESS RESETS THE RUN BUILD"
        else -> "FIRST PRESS ARMS THIS TRANSITION"
    }
    drawRect(actionAccent.copy(alpha = 0.11f), Offset(actionLeft, actionTop), Size(actionWidth, d(50f)))
    drawRect(actionAccent, Offset(actionLeft, actionTop), Size(actionWidth, d(50f)), style = Stroke(d(if (confirmationArmed) 2f else 1.4f)))
    drawLabel(textMeasurer, actionLabel, bounds.center.x, actionTop + d(7f), 11f, actionAccent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, actionDetail, bounds.center.x, actionTop + d(28f), 7f, if (model.canAdvance) White else Muted, centered = true)
    drawFooterBack(textMeasurer, bounds, Orange)
}

private fun DrawScope.drawRebirthTierCard(
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

private fun DrawScope.drawRebirthHostileStats(
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

private fun DrawScope.drawRebirthRewardStats(
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

private fun DrawScope.drawRebirthStatRow(
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
