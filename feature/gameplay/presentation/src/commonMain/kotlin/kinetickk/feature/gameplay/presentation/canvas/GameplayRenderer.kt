// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.core.design.*
import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kinetickk.feature.gameplay.domain.renderModel.VisualFxProjection
import kotlin.math.cos
import kotlin.math.sin

internal val VelocityNames = listOf("DRIFT", "SURGE", "HYPER", "OVERDRIVE", "TRANSCENDENT")

fun DrawScope.drawGameplay(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    textMeasurer: TextMeasurer,
    renderTime: Float,
) {
    drawRect(SpaceBlack)
    val shake = if (engine.settings.screenShake) engine.screenShake else 0f
    val shakeX = if (shake > 0f) sin(engine.elapsed * 91f) * shake else 0f
    val shakeY = if (shake > 0f) cos(engine.elapsed * 77f) * shake else 0f
    drawBackdrop(engine, shakeX, shakeY, renderTime)

    drawWorld(engine, visualFx, shakeX, shakeY, textMeasurer)
    drawScreenFx(engine, renderTime)
    drawHud(engine, textMeasurer)

    when (engine.phase) {
        GamePhase.PAUSED -> drawPause(textMeasurer)
        GamePhase.CHOICE -> drawChoice(engine, textMeasurer, renderTime)
        GamePhase.GAME_OVER -> drawEnd(engine, textMeasurer, victory = false)
        GamePhase.VICTORY -> drawEnd(engine, textMeasurer, victory = true)
        GamePhase.RUNNING -> Unit
    }
}
