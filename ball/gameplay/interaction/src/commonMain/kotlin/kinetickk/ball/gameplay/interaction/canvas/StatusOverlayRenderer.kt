// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.layout.PauseTarget
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/**
 * Pause (Pause / Mobile-Pause boards): world dimmed by the scrim, a skewed ink panel on the left
 * with "Paused" and the frozen run timer, the game's pause targets as menu items (Resume
 * selected) and the build overview on the right. [renderTime] drives the selected item's speed
 * lines; the committed frame supplies every value shown.
 */
internal fun DrawScope.drawPause(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    layout: PauseLayoutGeometry,
    renderTime: Float = 0f,
) {
    drawRect(pauseOverlayScrimColor(layout.mode))
    drawPausePanel(layout)
    val scale = layout.unit / density
    val typography = textMeasurer.typography
    val language = textMeasurer.language
    val title = drawKkText(textMeasurer, language.text(GameplayText.SystemPaused), typography.wideStyle(layout.titleSize * scale, lineHeightEm = 0.85f),
        layout.titleX, layout.titleY, Kk.Bone, uppercase = true)
    val timer = PauseTimerMemo.text(engine.elapsed)
    drawKkText(textMeasurer, timer, typography.wideStyle(layout.timerSize * scale, tabular = true), layout.titleX + 2f * layout.unit,
        layout.titleY + title.kkBoxHeight + (if (layout.mode == GameplayLayoutMode.REGULAR) 14f else 6f) * layout.unit, Kk.Mute)
    clipRect(layout.navClip.left, layout.navClip.top, layout.navClip.right, layout.navClip.bottom) {
        for (index in layout.actions.indices) {
            val action = layout.actions[index]
            val label = when (action.target) {
                PauseTarget.RESUME -> language.text(GameplayText.Resume)
                PauseTarget.SETTINGS -> language.text(GameplayText.Settings)
                PauseTarget.CODEX -> language.text(OverlayRedesignText.Codex)
                PauseTarget.PERFORMANCE -> language.text(GameplayText.PerformanceMetrics)
                PauseTarget.EXIT -> language.text(GameplayText.ReturnHome)
            }
            drawKkMenuItem(
                textMeasurer,
                left = action.bounds.left,
                centerY = action.bounds.center.y,
                label = label,
                selection = if (action.target == PauseTarget.RESUME) 1f else 0f,
                time = renderTime,
                fontSize = layout.menuFontSize * scale,
                dim = action.target == PauseTarget.EXIT,
                edgeRight = layout.navClip.right,
            )
        }
    }
    drawPauseBuild(engine, textMeasurer, layout, renderTime)
}

/** Skewed ink panel behind the title and menu (−12°, anchored at the board's bottom edge). */
private fun DrawScope.drawPausePanel(layout: PauseLayoutGeometry) {
    val portrait = layout.mode == GameplayLayoutMode.COMPACT_PORTRAIT
    val bottom = if (portrait) layout.panelBottom else size.height
    val ratio = KkShape.ShearRatio
    val topRight = layout.panelRight + ratio * layout.panelBottom
    val bottomRight = layout.panelRight + ratio * (layout.panelBottom - bottom)
    drawPath(PausePanelPath.of(topRight, bottomRight, bottom), if (layout.mode == GameplayLayoutMode.REGULAR) PanelInk else Kk.Ink1)
}

private val PanelInk = Kk.Ink.copy(alpha = 0.82f)

/** The panel quad, rebuilt only when the viewport geometry changes. */
private object PausePanelPath {
    private val path = Path()
    private val key = FloatArray(3) { Float.NaN }

    fun of(topRight: Float, bottomRight: Float, bottom: Float): Path {
        if (key[0] != topRight || key[1] != bottomRight || key[2] != bottom) {
            key[0] = topRight
            key[1] = bottomRight
            key[2] = bottom
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(topRight, 0f)
            path.lineTo(bottomRight, bottom)
            path.lineTo(0f, bottom)
            path.close()
        }
        return path
    }
}

/** The frozen timer string, formatted once per displayed second. */
private object PauseTimerMemo {
    private var seconds = -1
    private var value = ""

    fun text(elapsed: Float): String {
        val whole = elapsed.toInt()
        if (whole != seconds) {
            seconds = whole
            value = formatRunTime(elapsed)
        }
        return value
    }
}

/** Ink scrim over the frozen world (grayscale backdrop of the boards; no real-time blur). */
internal fun pauseOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Kk.Ink.copy(alpha = 0.84f) else compactStatusOverlayScrim

/** The run report covers the world completely. */
internal fun terminalOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Kk.Ink else Kk.Ink

/** Reward overlays dim the world like pause (ink 84 %) so the dealt cards own the screen. */
internal fun choiceOverlayScrimColor(mode: GameplayLayoutMode): Color =
    if (mode == GameplayLayoutMode.REGULAR) Kk.Ink.copy(alpha = 0.84f) else compactStatusOverlayScrim

private val compactStatusOverlayScrim = Kk.Ink.copy(alpha = 0.88f)
