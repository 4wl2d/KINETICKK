// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.min

internal fun DrawScope.drawPause(textMeasurer: TextMeasurer, layout: PauseLayoutGeometry) {
    drawRect(pauseOverlayScrimColor(layout.mode))
    drawKineticOrbits(Offset(size.width * 0.78f, size.height * 0.36f), size.minDimension * 0.22f, 0f, KineticAccent.copy(alpha = 0.45f))
    val titleSize = min(54f, ((layout.actions.firstOrNull()?.bounds?.top ?: size.height) - layout.titleY - d(12f)) / density / (textMeasurer.scale * 1.3f))
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.SystemPaused).uppercase(), size.width * 0.5f,
        layout.titleY, titleSize, White, centered = true, display = true)
    layout.actions.forEach { action ->
        val label = when (action.target) {
            PauseTarget.RESUME -> GameplayText.Resume
            PauseTarget.SETTINGS -> GameplayText.Settings
            PauseTarget.PERFORMANCE -> GameplayText.PerformanceMetrics
            PauseTarget.EXIT -> GameplayText.ReturnHome
        }
        drawActionButton(textMeasurer, action.bounds, textMeasurer.language.text(label), prominent = action.target == PauseTarget.RESUME)
    }
}

internal fun pauseOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Color(0xED0B0D11) else compactStatusOverlayScrim

internal fun terminalOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Color(0xF50B0D11) else compactStatusOverlayScrim

private val compactStatusOverlayScrim = Color(0xFA0B0D11)

private fun DrawScope.drawActionButton(textMeasurer: TextMeasurer, bounds: Rect, label: String, prominent: Boolean = false) {
    drawKineticRibbon(bounds, if (prominent) KineticAccent else OverlayPanel)
    drawLabel(textMeasurer, label.uppercase(), bounds.center.x, bounds.center.y - d(12f * textMeasurer.scale), 18f,
        if (prominent) SpaceBlack else White, centered = true, weight = if (prominent) FontWeight.Medium else FontWeight.Normal,
        maxWidth = bounds.width - d(24f), display = true)
}
