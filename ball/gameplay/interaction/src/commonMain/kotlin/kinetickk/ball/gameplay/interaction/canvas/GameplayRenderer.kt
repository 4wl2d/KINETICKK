// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.*
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.terminal.drawCoreDeath

/** Screen shake never moves the frame more than this many dp. */
internal const val MAX_SCREEN_SHAKE_DP = 6f

/** The simulation's strongest shake (a hard ram): only it reaches [MAX_SCREEN_SHAKE_DP]. */
internal const val FULL_SCREEN_SHAKE = 15f

/** Screen offset for the simulation's decaying shake [magnitude] (0..[FULL_SCREEN_SHAKE]) at run [time]. */
internal fun screenShakeOffset(magnitude: Float, time: Float, maxOffset: Float): Offset =
    kkShake(magnitude / FULL_SCREEN_SHAKE, time, maxOffset)

internal fun DrawScope.drawGameplay(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    pauseLayout: PauseLayoutGeometry?,
    terminalElapsed: Float = 0f,
    hudMemory: HudPresentationMemory? = null,
    trialInfoOpen: Boolean = false,
) {
    drawRect(Kk.Ink)
    val shake = if (engine.settings.screenShake && engine.phase == GamePhase.RUNNING) {
        screenShakeOffset(engine.screenShake, engine.elapsed, d(MAX_SCREEN_SHAKE_DP))
    } else {
        Offset.Zero
    }
    val shakeX = shake.x
    val shakeY = shake.y
    drawBackdrop(engine, shakeX, shakeY, renderTime, textMeasurer.roles)

    drawWorld(engine, visualFx, shakeX, shakeY, textMeasurer)
    if (engine.phase == GamePhase.GAME_OVER) drawCoreDeath(engine, terminalElapsed, textMeasurer.roles)
    else drawScreenFx(engine, renderTime, textMeasurer.roles)
    if (shouldDrawRunningPresentation(engine.phase)) {
        drawHud(engine, textMeasurer, renderTime, shakeX, shakeY, hudMemory, trialInfoOpen)
        drawHudFeed(engine, visualFx, textMeasurer, renderTime, hudMemory)
        drawTrialTooltip(engine, textMeasurer, trialInfoOpen)
    }

    when (engine.phase) {
        GamePhase.PAUSED -> drawPause(engine, textMeasurer, requireNotNull(pauseLayout), renderTime)
        GamePhase.CHOICE -> Unit // Reward cards and reroll are visible Compose controls.
        GamePhase.GAME_OVER, GamePhase.VICTORY -> Unit // TerminalContent owns the animated report and actions.
        GamePhase.RUNNING -> Unit
    }
}

internal fun shouldDrawRunningPresentation(phase: GamePhase): Boolean =
    phase == GamePhase.RUNNING
