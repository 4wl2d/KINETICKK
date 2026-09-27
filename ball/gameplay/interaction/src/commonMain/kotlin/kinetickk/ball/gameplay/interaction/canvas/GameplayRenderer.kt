// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.*
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.terminal.drawCoreDeath
import kotlin.math.cos
import kotlin.math.sin

/** Screen shake never moves the frame more than this many dp. */
internal const val MAX_SCREEN_SHAKE_DP = 6f

internal fun DrawScope.drawGameplay(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    pauseLayout: PauseLayoutGeometry?,
    terminalElapsed: Float = 0f,
    hudMemory: HudPresentationMemory? = null,
    trialInfoFocused: Boolean = false,
) {
    drawRect(Kk.Ink)
    val shake = if (engine.settings.screenShake && engine.phase == GamePhase.RUNNING) {
        engine.screenShake.coerceAtMost(d(MAX_SCREEN_SHAKE_DP))
    } else {
        0f
    }
    val shakeX = if (shake > 0f) sin(engine.elapsed * 91f) * shake else 0f
    val shakeY = if (shake > 0f) cos(engine.elapsed * 77f) * shake else 0f
    drawBackdrop(engine, shakeX, shakeY, renderTime, textMeasurer.roles)

    drawWorld(engine, visualFx, shakeX, shakeY, textMeasurer)
    if (engine.phase == GamePhase.GAME_OVER) drawCoreDeath(engine, terminalElapsed, textMeasurer.roles)
    else drawScreenFx(engine, renderTime, textMeasurer.roles)
    if (shouldDrawRunningPresentation(engine.phase)) {
        drawHud(engine, textMeasurer, renderTime, shakeX, shakeY, hudMemory, trialInfoFocused)
        drawHudFeed(engine, visualFx, textMeasurer, renderTime, hudMemory)
    }

    when (engine.phase) {
        GamePhase.PAUSED -> drawPause(engine, textMeasurer, requireNotNull(pauseLayout))
        GamePhase.CHOICE -> Unit // Reward cards and reroll are visible Compose controls.
        GamePhase.GAME_OVER, GamePhase.VICTORY -> Unit // TerminalContent owns the animated report and actions.
        GamePhase.RUNNING -> Unit
    }
}

internal fun shouldDrawRunningPresentation(phase: GamePhase): Boolean =
    phase == GamePhase.RUNNING
