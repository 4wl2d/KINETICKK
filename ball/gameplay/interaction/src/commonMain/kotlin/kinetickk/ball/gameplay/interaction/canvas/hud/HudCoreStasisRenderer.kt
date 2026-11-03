// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

// Core Stasis on the HUD: the frost creeping in from the screen edges and the "Core freezing — move" stamp above the
// Core. Colours come from the role palette only (cryo while freezing, threat while fracturing, hatched in Mono); every
// pulse stays at 1.43 Hz or slower and nothing flashes the whole screen.

/** Frost closing in from the screen edges as the stasis level rises: the outer band first, the inner past half. */
internal fun DrawScope.drawCoreStasisFrost(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) {
    val stasis = engine.coreStasis
    if (stasis <= 0f) return
    val pulse = if (engine.coreStasisFracturing) {
        FROST_PULSE_LOW + (1f - FROST_PULSE_LOW) * kkPulse(renderTime, 1f / FROST_PULSE_HZ, KkEase.Out)
    } else 1f
    val cryo = roles.element(StatusElement.CRYO.ordinal)
    val outer = FROST_OUTER_ALPHA * (stasis / 0.5f).coerceIn(0f, 1f) * pulse
    drawScreenVignette(HudBrush.FROST_OUTER, FROST_OUTER_CLEAR_STOP, cryo, outer)
    val inner = FROST_INNER_ALPHA * ((stasis - 0.5f) / 0.5f).coerceIn(0f, 1f) * pulse
    if (inner > 0f) drawScreenVignette(HudBrush.FROST_INNER, FROST_INNER_CLEAR_STOP, cryo, inner)
}

/**
 * The warning stamp centred above the Core ([haloRadius] in px), tilted the other way from the overheat stamp: a cryo
 * plate while the Core freezes, a threat plate (hatched in Mono) while it fractures. The label shrinks to fit narrow
 * screens at large text sizes.
 */
internal fun DrawScope.drawCoreStasisStamp(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    core: Offset,
    haloRadius: Float,
    renderTime: Float,
) {
    val stasis = engine.coreStasis
    if (stasis <= 0f) return
    val fracturing = engine.coreStasisFracturing
    val language = measurer.language
    val text = HudScratch.coreStasis.of(language, 0L) { language.text(HudRedesignText.CoreStasisWarning) }
    val k = 14f / 17f
    val padX = d(22f) * k
    val room = size.width - 2f * d(12f) - padX
    val layout = HudDrawCache.fitted(HudText.CORE_STASIS, HudMeasurers.ui(measurer), text, STAMP_TEXT_SP, room,
        uppercase = true, minSize = STAMP_MIN_TEXT_SP) { sp ->
        measurer.typography.condStyle(sp, trackingEm = 0.06f, lineHeightEm = 1f)
    }
    val width = layout.size.width + padX
    val height = layout.kkBoxHeight + d(9f) * k
    val margin = d(8f)
    val left = (core.x - width * 0.5f).coerceIn(margin, kotlin.math.max(margin, size.width - margin - width))
    val top = (core.y - haloRadius * 1.2f - height).coerceIn(margin, kotlin.math.max(margin, size.height - margin - height))
    val appear = (stasis / STAMP_APPEAR_LEVEL).coerceIn(0f, 1f)
    val period = if (fracturing) STAMP_FRACTURE_PERIOD else STAMP_FREEZE_PERIOD
    val alpha = appear * (0.55f + 0.45f * (1f - kkPulse(renderTime, period, KkEase.Out)))
    val roles = measurer.roles
    val plate = if (fracturing) roles.threat else roles.element(StatusElement.CRYO.ordinal)
    rotate(STAMP_TILT_DEGREES, Offset(left + width * 0.5f, top + height * 0.5f)) {
        drawRect(Kk.Ink, Offset(left + d(3f) * k, top + d(3f) * k), Size(width, height), alpha)
        drawRect(plate, Offset(left, top), Size(width, height), alpha)
        if (fracturing && roles.hatchThreats) {
            drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, Kk.Ink), Offset(left, top), Size(width, height), alpha)
        }
        // The plate pulses; the ink label stays solid (a text alpha change repaints its paragraph).
        drawKkText(layout, left + width * 0.5f, top + height * 0.5f + d(0.5f), Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    }
    HudLayoutProbe.record(HudBlock.CORE_STASIS, left, top, left + width, top + height)
}

private const val FROST_OUTER_ALPHA = 0.30f
private const val FROST_INNER_ALPHA = 0.22f
private const val FROST_OUTER_CLEAR_STOP = 0.62f
private const val FROST_INNER_CLEAR_STOP = 0.40f

/** The fracturing frost breathes at the scorch edge's rate (1.2 Hz) between this share and full. */
private const val FROST_PULSE_LOW = 0.7f
private const val FROST_PULSE_HZ = 1.2f

private const val STAMP_TEXT_SP = 14f
private const val STAMP_MIN_TEXT_SP = 10f
private const val STAMP_TILT_DEGREES = 4f

/** The stamp fades in over the first part of the ramp, so a brief dip past the grace does not flicker it. */
private const val STAMP_APPEAR_LEVEL = 0.15f
private const val STAMP_FREEZE_PERIOD = 1.1f
private const val STAMP_FRACTURE_PERIOD = 0.7f
