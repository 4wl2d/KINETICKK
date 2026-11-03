// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.compactFpsReadoutBounds
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

/**
 * Running controls: Dash and Brake slabs, pause and performance icon buttons, and the Z/X/C skill row
 * ([drawSkillBar]). The Apex control is the level badge itself.
 */
internal fun DrawScope.drawControls(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    renderTime: Float = 0f,
    memory: HudPresentationMemory? = null,
    controls: RunningControlPressLog? = null,
) {
    val roles = textMeasurer.roles
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val regular = frame.regular
    forEachRunningControlBounds(size.width, size.height, density, RunningControlMask.of(engine)) { target, left, top, right, bottom ->
        when (target) {
            RunningControlTarget.DASH -> drawTouchButton(
                textMeasurer, frame, HudPath.DASH, HudText.DASH_LABEL, left, top, right, bottom, KkIcon.SYSTEM_DASH,
                dashLabel(textMeasurer.language), regular,
                face = when {
                    engine.dashPhaseTime > DASH_PHASE_SECONDS - 0.05f -> Kk.Bone
                    engine.overheated -> Kk.Ink3
                    else -> roles.you
                },
                foreground = if (engine.overheated && engine.dashPhaseTime <= 0f) Kk.Mute2 else Kk.Ink,
                outline = Color.Unspecified,
                heat = engine.heat / GameplayRenderModel.MAX_HEAT,
                heatColor = if (engine.overheated) roles.threat else roles.heat,
            )
            RunningControlTarget.BRAKE -> drawTouchButton(
                textMeasurer, frame, HudPath.BRAKE, HudText.BRAKE_LABEL, left, top, right, bottom, KkIcon.SYSTEM_BRAKE,
                brakeLabel(textMeasurer.language), regular,
                face = if (engine.braking) Kk.Bone else Kk.Ink3,
                foreground = if (engine.braking) Kk.Ink else Kk.Bone,
                outline = if (engine.braking) Color.Unspecified else Kk.Line2,
                heat = -1f,
                heatColor = Color.Unspecified,
            )
            RunningControlTarget.PAUSE -> drawHudIconButton(frame, HudPath.PAUSE, left, top, right, bottom, regular) { center, glyph ->
                drawPauseGlyph(center, glyph)
            }
            // Show FPS is available in every build; Debug also uses its hit area to open the panel.
            RunningControlTarget.PERFORMANCE -> drawHudIconButton(frame, HudPath.PERFORMANCE, left, top, right, bottom, regular) { center, _ ->
                drawFpsCounter(textMeasurer, frame, center, controls?.fps ?: GameplayFpsCounter.NO_VALUE)
            }
            // The skill row draws as one (its entrance moves all three); the Apex is the level badge.
            RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3, RunningControlTarget.APEX -> Unit
        }
    }
    if (frame.portrait && engine.settings.showFps) {
        val fps = controls?.fps ?: GameplayFpsCounter.NO_VALUE
        compactFpsReadoutBounds(size.width, density, overlay = false) { left, top, right, bottom ->
            drawCompactFpsReadout(textMeasurer, left, top, right, bottom, fps)
        }
    }
    drawSkillBar(engine, textMeasurer, frame, renderTime, memory, controls)
}

private fun dashLabel(language: AppLanguage): String =
    HudScratch.dashLabel.of(language, 0L) { language.text(GameplayText.DashDescription) }

private fun brakeLabel(language: AppLanguage): String =
    HudScratch.brakeLabel.of(language, 0L) { language.text(GameplayText.BrakeDescription) }

/** Slab touch button: icon beside the label on desktop, icon above the label on phones. */
private fun DrawScope.drawTouchButton(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    text: HudText,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    icon: KkIcon,
    label: String,
    regular: Boolean,
    face: Color,
    foreground: Color,
    outline: Color,
    heat: Float,
    heatColor: Color,
) {
    val cut = if (regular) frame.u(12f) else d(14f)
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, bottom, cut), face)
    if (outline != Color.Unspecified) {
        val inset = d(0.75f)
        val outlinePath = if (path == HudPath.BRAKE) HudPath.BRAKE_OUTLINE else HudPath.DASH_OUTLINE
        drawPath(HudDrawCache.paths.slab(outlinePath.ordinal, left + inset, top + inset, right - inset, bottom - inset, cut), outline,
            style = kkStroke(d(1.5f)))
    }
    val width = right - left
    val height = bottom - top
    val fontSize = if (regular) frame.t(22f) else if (path == HudPath.DASH) 20f else 17f
    val iconSize = if (regular) frame.u(20f) else d(if (path == HudPath.DASH) 24f else 20f)
    val iconGap = frame.u(8f)
    // The label is UI text: it grows with the text size and shrinks to fit inside the slab (a
    // parallelogram: `width - 2 cut` clears both slanted edges at any height) with padding.
    val room = width - cut * 2f - d(TOUCH_LABEL_PADDING_DP) * 2f - if (regular) iconSize + iconGap else 0f
    val layout = HudDrawCache.fitted(text, HudMeasurers.ui(measurer), label, fontSize, room, uppercase = true) { size ->
        measurer.typography.condStyle(size, lineHeightEm = 1f)
    }
    val block = if (path == HudPath.DASH) HudBlock.DASH_LABEL else HudBlock.BRAKE_LABEL
    if (regular) {
        val contentWidth = iconSize + iconGap + layout.size.width
        val x = left + (width - contentWidth) * 0.5f
        val cy = top + height * 0.5f - if (heat >= 0f) frame.u(2f) else 0f
        drawKkIcon(icon, Offset(x + iconSize * 0.5f, cy), iconSize, foreground)
        drawKkText(layout, x + iconSize + iconGap, cy, foreground, valign = KkVAlign.CENTER)
        HudLayoutProbe.record(block, x + iconSize + iconGap, cy - layout.kkBoxHeight * 0.5f, x + contentWidth,
            cy + layout.kkBoxHeight * 0.5f)
    } else {
        val gap = d(2f)
        val contentHeight = iconSize + gap + layout.kkBoxHeight
        val y = top + (height - contentHeight) * 0.5f - if (heat >= 0f) d(3f) else 0f
        drawKkIcon(icon, Offset(left + width * 0.5f, y + iconSize * 0.5f), iconSize, foreground)
        drawKkText(layout, left + width * 0.5f, y + iconSize + gap, foreground, KkAlign.CENTER)
        HudLayoutProbe.record(block, left + (width - layout.size.width) * 0.5f, y + iconSize + gap,
            left + (width + layout.size.width) * 0.5f, y + contentHeight)
    }
    if (heat >= 0f) {
        val barLeft = left + cut
        val barRight = right - cut
        val barBottom = bottom - d(6f)
        val barHeight = d(4f)
        drawRect(Kk.Ink.copy(alpha = 0.25f), Offset(barLeft, barBottom - barHeight), Size(barRight - barLeft, barHeight))
        val fill = heat.coerceIn(0f, 1f)
        if (fill > 0f) drawRect(heatColor, Offset(barLeft, barBottom - barHeight), Size((barRight - barLeft) * fill, barHeight))
    }
}

/** Inner padding between a touch button's label and its slab edges, in dp. */
internal const val TOUCH_LABEL_PADDING_DP = 2f

/** `.ibtn` visual centered in its (larger) touch target. */
private inline fun DrawScope.drawHudIconButton(
    frame: HudFrame,
    path: HudPath,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    regular: Boolean,
    glyph: DrawScope.(center: Offset, size: Float) -> Unit,
) {
    val w = if (regular) frame.u(40f) else d(44f)
    val h = if (regular) frame.u(36f) else d(40f)
    val cx = (left + right) * 0.5f
    val cy = (top + bottom) * 0.5f
    drawPath(HudDrawCache.paths.slab(path.ordinal, cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f, d(8f)), Kk.Ink3)
    glyph(Offset(cx, cy), if (regular) frame.u(18f) else d(20f))
}

/** Pause mark (two bars, `M8 4v16M16 4v16`) without an arrow-shaped play/pause glyph. */
internal fun DrawScope.drawPauseGlyph(center: Offset, size: Float, color: Color = Kk.Bone) {
    val unit = size / 24f
    val stroke = 2f * unit
    drawLine(color, Offset(center.x - 4f * unit, center.y - 8f * unit), Offset(center.x - 4f * unit, center.y + 8f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x + 4f * unit, center.y - 8f * unit), Offset(center.x + 4f * unit, center.y + 8f * unit), stroke, StrokeCap.Square)
}

/**
 * The Show FPS counter inside the PERFORMANCE slot: the measured value (mono 12, "–" until the first
 * window closes) over an "FPS" label (8). Cached per value; no allocation once a value was shown.
 */
private fun DrawScope.drawFpsCounter(measurer: TextMeasurer, frame: HudFrame, center: Offset, fps: Int) {
    val display = HudMeasurers.display(measurer)
    val value = HudScratch.fps.of(fps.toLong())
    val number = HudDrawCache.layout(HudText.FPS, display, value,
        measurer.typography.monoStyle(frame.t(12f), weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f))
    val label = HudDrawCache.layout(HudText.FPS_LABEL, display, FPS_LABEL, measurer.typography.labelStyle(frame.t(8f)), uppercase = true)
    val gap = frame.u(1f)
    val top = center.y - (number.kkBoxHeight + gap + label.kkBoxHeight) * 0.5f
    drawKkText(number, center.x, top, Kk.Bone, KkAlign.CENTER)
    drawKkText(label, center.x, top + number.kkBoxHeight + gap, Kk.Mute, KkAlign.CENTER)
    val half = max(number.size.width, label.size.width) * 0.5f
    HudLayoutProbe.record(HudBlock.FPS, center.x - half, top, center.x + half, top + number.kkBoxHeight + gap + label.kkBoxHeight)
}

/** The counter's unit, the same in every language (like the panel's own "FPS"). */
private const val FPS_LABEL = "FPS"

/** Single-line counter for narrow portrait headers and the layer above pause/reward overlays, in the given px bounds. */
internal fun DrawScope.drawCompactFpsReadout(measurer: TextMeasurer, left: Float, top: Float, right: Float, bottom: Float, fps: Int) {
    val display = HudMeasurers.display(measurer)
    val number = HudDrawCache.layout(HudText.FPS, display, HudScratch.fps.of(fps.toLong()),
        measurer.typography.monoStyle(10f, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f))
    val label = HudDrawCache.layout(HudText.FPS_LABEL, display, FPS_LABEL,
        measurer.typography.labelStyle(8f, lineHeightEm = 1f), uppercase = true)
    val gap = 4f * density
    val contentWidth = number.size.width + gap + label.size.width
    val centerY = top + (bottom - top) / 2f
    val textLeft = left + (right - left) / 2f - contentWidth * 0.5f
    val height = max(number.kkBoxHeight, label.kkBoxHeight)
    drawRect(Kk.Ink2.copy(alpha = 0.92f), Offset(left, top), Size(right - left, bottom - top))
    drawKkText(number, textLeft, centerY, Kk.Bone, valign = KkVAlign.CENTER)
    drawKkText(label, textLeft + number.size.width + gap, centerY, Kk.Mute, valign = KkVAlign.CENTER)
    HudLayoutProbe.record(HudBlock.FPS, textLeft, centerY - height * 0.5f, textLeft + contentWidth, centerY + height * 0.5f)
}
