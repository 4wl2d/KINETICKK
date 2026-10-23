// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.unit.Density
import kinetickk.foundation.design.*
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.input.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.terminal.drawCoreDeath

/** Screen shake never moves the frame more than this many dp. */
internal const val MAX_SCREEN_SHAKE_DP = 6f

/** The simulation's strongest shake (a hard ram): only it reaches [MAX_SCREEN_SHAKE_DP]. */
internal const val FULL_SCREEN_SHAKE = 15f

/** Screen offset for the simulation's decaying shake [magnitude] (0..[FULL_SCREEN_SHAKE]) at run [time]. */
internal fun screenShakeOffset(magnitude: Float, time: Float, maxOffset: Float): Offset =
    kkShake(magnitude / FULL_SCREEN_SHAKE, time, maxOffset)

/**
 * The run's frame: the world at the scope's density, then the HUD, the feed and the pause overlay at
 * [hudDensity] with [hudTextMeasurer] (the applied interface scale; the scope's own density and
 * [textMeasurer] at 100 %). The pause layout must be laid out at the same HUD scale.
 */
internal fun DrawScope.drawGameplay(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    textMeasurer: TextMeasurer,
    renderTime: Float,
    pauseLayout: PauseLayoutGeometry?,
    terminalElapsed: Float = 0f,
    hudMemory: HudPresentationMemory? = null,
    trialInfoOpen: Boolean = false,
    controls: RunningControlPressLog? = null,
    hudDensity: Density? = null,
    hudTextMeasurer: TextMeasurer = textMeasurer,
) {
    val hudScreenDensity = hudDensity?.density ?: density
    drawRect(Kk.Ink)
    val shake = if (engine.settings.screenShake && engine.phase == GamePhase.RUNNING) {
        screenShakeOffset(engine.screenShake, engine.renderElapsed, d(MAX_SCREEN_SHAKE_DP))
    } else {
        Offset.Zero
    }
    val shakeX = shake.x
    val shakeY = shake.y
    drawBackdrop(engine, shakeX, shakeY, renderTime, textMeasurer.roles)

    drawWorld(engine, visualFx, shakeX, shakeY, textMeasurer, hudScreenDensity)
    if (engine.phase == GamePhase.GAME_OVER) {
        withWorldUnits(engine.worldScale) { drawCoreDeath(engine, terminalElapsed, textMeasurer.roles) }
    } else {
        drawScreenFx(engine, renderTime, textMeasurer.roles)
    }
    if (shouldDrawRunningPresentation(engine.phase)) {
        withHudDensity(hudDensity) {
            drawHud(engine, hudTextMeasurer, renderTime, shakeX, shakeY, hudMemory, trialInfoOpen, controls)
            drawHudFeed(engine, visualFx, hudTextMeasurer, renderTime, hudMemory)
            drawTrialTooltip(engine, hudTextMeasurer, trialInfoOpen)
        }
    }

    when (engine.phase) {
        GamePhase.PAUSED -> withHudDensity(hudDensity) { drawPause(engine, hudTextMeasurer, requireNotNull(pauseLayout), renderTime) }
        GamePhase.CHOICE -> Unit // Reward cards and reroll are visible Compose controls.
        GamePhase.GAME_OVER, GamePhase.VICTORY -> Unit // TerminalContent owns the animated report and actions.
        GamePhase.RUNNING -> Unit
    }
}

internal fun shouldDrawRunningPresentation(phase: GamePhase): Boolean =
    phase == GamePhase.RUNNING

/**
 * Draws [block] at [hud] (the interface-scaled density of the HUD and its overlays) on the same canvas
 * and size; the scope's own density when [hud] is null or equal to it. Allocates nothing.
 */
internal inline fun DrawScope.withHudDensity(hud: Density?, block: DrawScope.() -> Unit) {
    if (hud == null || (hud.density == density && hud.fontScale == fontScale)) {
        block()
        return
    }
    draw(hud, layoutDirection, drawContext.canvas, size, drawContext.graphicsLayer, block)
}
