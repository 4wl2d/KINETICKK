// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.layout.TerminalLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.model.formatRunTime

internal fun DrawScope.drawPause(
    textMeasurer: TextMeasurer,
    layout: kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry,
) {
    drawRect(pauseOverlayScrimColor(layout.mode))
    if (layout.mode != GameplayLayoutMode.REGULAR) {
        drawLabel(textMeasurer, "SYSTEM PAUSED", size.width * 0.5f, layout.titleY, 20f, White, centered = true, weight = FontWeight.Bold)
        for (index in layout.actions.indices) {
            val action = layout.actions[index]
            val label = when (action.target) {
                PauseTarget.RESUME -> "RESUME"
                PauseTarget.SETTINGS -> "SETTINGS"
                PauseTarget.PERFORMANCE -> "PERFORMANCE METRICS"
                PauseTarget.EXIT -> "RETURN TO HOME"
            }
            val accent = when (action.target) {
                PauseTarget.RESUME -> Cyan
                PauseTarget.SETTINGS,
                PauseTarget.PERFORMANCE,
                -> Violet
                PauseTarget.EXIT -> Red
            }
            drawActionButton(textMeasurer, action.bounds, label, accent)
        }
        return
    }
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

internal fun DrawScope.drawEnd(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    victory: Boolean,
    layout: TerminalLayoutGeometry,
) {
    val color = if (victory) Acid else Red
    drawRect(terminalOverlayScrimColor(layout.mode))
    if (layout.mode != GameplayLayoutMode.REGULAR) {
        drawCompactEnd(engine, textMeasurer, victory, color, layout)
        return
    }
    drawLabel(textMeasurer, if (victory) "RUN CONQUERED" else engine.message, size.width * 0.5f, size.height * 0.25f, if (size.width / density < 700f) 28f else 42f, color, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (victory) "THE ARCHITECT HAS FALLEN" else "THE SINGULARITY REMEMBERS", size.width * 0.5f, size.height * 0.36f, 10f, Muted, centered = true)
    val statY = size.height * 0.47f
    drawLabel(textMeasurer, "TIME ${formatRunTime(engine.elapsed)}", size.width * 0.5f - d(165f), statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "KILLS ${engine.kills}", size.width * 0.5f, statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "MATTER ${formatCompact(engine.runMatter)}", size.width * 0.5f + d(165f), statY, 13f, Acid, centered = true)
    drawLabel(textMeasurer, "WEAPON ${engine.currentWeaponDefinition.name.uppercase()} // LV ${engine.weaponLevel}", size.width * 0.5f, statY + d(38f), 10f, weaponColor(engine.weapon), centered = true)
    drawLabel(textMeasurer, "ITEMS ${engine.acquiredItemCount}   DISCOVERIES ${engine.discoveredItemCount}/${engine.content.items.size}   PEAK ${VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)]}", size.width * 0.5f, statY + d(64f), 9f, Muted, centered = true)
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

private fun DrawScope.drawCompactEnd(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    victory: Boolean,
    accent: Color,
    layout: TerminalLayoutGeometry,
) {
    val landscape = layout.mode == GameplayLayoutMode.COMPACT_LANDSCAPE
    drawLabel(
        textMeasurer,
        if (victory) "RUN CONQUERED" else engine.message,
        size.width * 0.5f,
        layout.titleY,
        if (landscape) 23f else 27f,
        accent,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = size.width - d(24f),
    )
    drawLabel(
        textMeasurer,
        if (victory) "THE ARCHITECT HAS FALLEN" else "THE SINGULARITY REMEMBERS",
        size.width * 0.5f,
        layout.subtitleY,
        8f,
        Muted,
        centered = true,
    )
    drawLabel(
        textMeasurer,
        "TIME ${formatRunTime(engine.elapsed)} // KILLS ${engine.kills} // MATTER ${formatCompact(engine.runMatter)}",
        size.width * 0.5f,
        layout.statsY,
        if (landscape) 9f else 10f,
        White,
        centered = true,
        maxWidth = size.width - d(24f),
    )
    drawLabel(
        textMeasurer,
        "${engine.currentWeaponDefinition.name.uppercase()} LV ${engine.weaponLevel} // BANK ${formatCompact(engine.totalMatter)}",
        size.width * 0.5f,
        layout.statsY + d(26f),
        7f,
        weaponColor(engine.weapon),
        centered = true,
        maxWidth = size.width - d(24f),
    )
    drawActionButton(textMeasurer, layout.restart, "RE-ENTER", accent)
    layout.rebirth?.let { drawActionButton(textMeasurer, it, "REBIRTH // NEXT CYCLE", Acid) }
    drawActionButton(textMeasurer, layout.exit, "RETURN TO HOME", Red)
}

internal fun pauseOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Color(0xC9050610) else compactStatusOverlayScrim

internal fun terminalOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Color(0xDE050610) else compactStatusOverlayScrim

private val compactStatusOverlayScrim = Color(0xF2050610)

private fun DrawScope.drawActionButton(
    textMeasurer: TextMeasurer,
    bounds: Rect,
    label: String,
    accent: Color,
) {
    drawRect(accent.copy(alpha = 0.1f), bounds.topLeft, bounds.size)
    drawRect(accent, bounds.topLeft, bounds.size, style = Stroke(d(1.3f)))
    drawLabel(textMeasurer, label, bounds.center.x, bounds.center.y - d(7f), 9f, accent, centered = true, weight = FontWeight.Bold, maxWidth = bounds.width - d(12f))
}
