// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.canvas

import kinetickk.core.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.gameplay.domain.model.formatRunTime
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel

internal fun DrawScope.drawPause(textMeasurer: TextMeasurer) {
    drawRect(Color(0xC9050610))
    drawLabel(textMeasurer, "SYSTEM PAUSED", size.width * 0.5f, size.height * 0.30f, 28f, White, centered = true, weight = FontWeight.Bold)
    drawPauseButton(textMeasurer, "RESUME [P / ESC]", size.height * 0.5f, Cyan)
    drawPauseButton(textMeasurer, "SETTINGS [S]", size.height * 0.62f, Violet)
    drawPauseButton(textMeasurer, "RETURN TO MENU", size.height * 0.74f, Red)
}

internal fun DrawScope.drawPauseButton(textMeasurer: TextMeasurer, label: String, top: Float, accent: Color) {
    val left = size.width * 0.5f - d(150f)
    drawRect(accent.copy(alpha = 0.1f), Offset(left, top), Size(d(300f), d(52f)))
    drawRect(accent, Offset(left, top), Size(d(300f), d(52f)), style = Stroke(d(1.5f)))
    drawLabel(textMeasurer, label, size.width * 0.5f, top + d(17f), 11f, accent, centered = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawEnd(engine: GameplayRenderModel, textMeasurer: TextMeasurer, victory: Boolean) {
    drawRect(Color(0xDE050610))
    val color = if (victory) Acid else Red
    drawLabel(textMeasurer, if (victory) "RUN CONQUERED" else engine.message, size.width * 0.5f, size.height * 0.25f, if (size.width / density < 700f) 28f else 42f, color, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (victory) "THE ARCHITECT HAS FALLEN" else "THE SINGULARITY REMEMBERS", size.width * 0.5f, size.height * 0.36f, 10f, Muted, centered = true)
    val statY = size.height * 0.47f
    drawLabel(textMeasurer, "TIME ${formatRunTime(engine.elapsed)}", size.width * 0.5f - d(165f), statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "KILLS ${engine.kills}", size.width * 0.5f, statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "MATTER ${formatCompact(engine.runMatter)}", size.width * 0.5f + d(165f), statY, 13f, Acid, centered = true)
    drawLabel(textMeasurer, "WEAPON ${engine.currentWeaponDefinition.name.uppercase()} // LV ${engine.weaponLevel}", size.width * 0.5f, statY + d(38f), 10f, weaponColor(engine.weapon), centered = true)
    drawLabel(textMeasurer, "ITEMS ${engine.acquiredItemCount}   DISCOVERIES ${engine.discoveredItemCount}/400   PEAK ${VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)]}", size.width * 0.5f, statY + d(64f), 9f, Muted, centered = true)
    val buttonY = size.height * 0.72f
    drawRect(color.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)))
    drawRect(color, Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "RE-ENTER [R]", size.width * 0.5f, buttonY - d(10f), 15f, White, centered = true, weight = FontWeight.Bold)
    if (victory) {
        val rebirthTop = buttonY + d(50f)
        val rebirthAccent = Acid
        val rebirthLabel = "REBIRTH [B] // NEXT CYCLE"
        drawRect(rebirthAccent.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)))
        drawRect(rebirthAccent, Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)), style = Stroke(d(1.4f)))
        drawLabel(textMeasurer, rebirthLabel, size.width * 0.5f, rebirthTop + d(12f), 9f, rebirthAccent, centered = true, weight = FontWeight.Bold)
    }
    val menuHintY = buttonY + d(if (victory) 104f else 65f)
    drawLabel(textMeasurer, "TAP BELOW FOR CORE SELECT // BANK ${formatCompact(engine.totalMatter)}", size.width * 0.5f, menuHintY, 8f, Muted, centered = true)
}
