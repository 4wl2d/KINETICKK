// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.WorldStrings
import kinetickk.ball.gameplay.interaction.canvas.WorldUnitShapes
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.max

// Core Freeze on the HUD: frost creeping in from the screen edges and corners, a ring meter around the Core that fills
// as the freeze grows (one tick per second of the freeze), and a stamp above the Core with the seconds left. Colours
// come from the role palette only: cryo while the Core freezes, threat over the last stretch before a lethal freeze
// (hatched in Mono; never in Easy mode, which cannot die of it), bone while it thaws. Every pulse stays at 1.43 Hz or
// slower and nothing flashes the whole screen.

/**
 * Frost closing in as the freeze grows: the outer band first, the inner past half, and ice shards reaching in from the
 * four corners. Past [URGENT_FREEZE] a freezing Core's frost breathes. The run's end draws it too, at a full freeze.
 */
internal fun DrawScope.drawCoreStasisFrost(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) {
    val freeze = engine.coreStasis
    if (freeze <= 0f) return
    val pulse = if (engine.coreStasisMotion == CoreStasisMotion.FREEZING && freeze >= URGENT_FREEZE) {
        FROST_PULSE_LOW + (1f - FROST_PULSE_LOW) * kkPulse(renderTime, 1f / FROST_PULSE_HZ, KkEase.Out)
    } else 1f
    val cryo = roles.element(StatusElement.CRYO.ordinal)
    val outer = FROST_OUTER_ALPHA * (freeze / 0.5f).coerceIn(0f, 1f) * pulse
    drawScreenVignette(HudBrush.FROST_OUTER, FROST_OUTER_CLEAR_STOP, cryo, outer)
    val inner = FROST_INNER_ALPHA * ((freeze - 0.5f) / 0.5f).coerceIn(0f, 1f) * pulse
    if (inner > 0f) drawScreenVignette(HudBrush.FROST_INNER, FROST_INNER_CLEAR_STOP, cryo, inner)
    drawCornerFrost(freeze, cryo)
}

/** [CORNER_SHARDS] ice shards fanning in from each screen corner, each growing in over its own share of the freeze. */
private fun DrawScope.drawCornerFrost(freeze: Float, cryo: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val reach = d(CORNER_REACH_DP) * kotlin.math.min(1f, kotlin.math.min(w, h) / d(CORNER_SCREEN_DP))
    for (corner in 0 until 4) {
        val x = if (corner == 1 || corner == 2) w else 0f
        val y = if (corner >= 2) h else 0f
        // The quadrant's inward bisector: 45°, 135°, 225° and 315° from +x, clockwise on screen.
        val bisector = CORNER_BISECTORS[corner]
        for (shard in 0 until CORNER_SHARDS) {
            val grow = ((freeze - CornerStart[shard]) / CORNER_GROW).coerceIn(0f, 1f)
            if (grow <= 0f) continue
            val length = reach * CornerLength[shard] * grow
            val angle = bisector + CornerSpread[shard]
            withTransform({
                translate(x, y)
                rotate(angle * CanvasMath.RAD_TO_DEG, Offset.Zero)
                translate(length * 0.5f, 0f)
                scale(length, length * 0.55f, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.shard, cryo, alpha = 0.55f * grow)
                scale(0.6f, 0.5f, Offset.Zero) { drawPath(WorldUnitShapes.shard, Kk.Bone, alpha = 0.35f * grow) }
            }
        }
    }
}

/**
 * The freeze's ring meter around the Core (world units, outside the Core halo of [haloRadius]): a cryo track, the
 * freeze filled clockwise from the top (bone while thawing) and an ink tick at every whole second of the freeze, so the
 * unfilled arc reads as the seconds left.
 */
internal fun DrawScope.drawCoreStasisRing(engine: GameplayRenderModel, roles: KkRolePalette, core: Offset, haloRadius: Float) {
    val freeze = engine.coreStasis
    if (freeze <= 0f) return
    val motion = engine.coreStasisMotion
    val cryo = roles.element(StatusElement.CRYO.ordinal)
    val radius = haloRadius * RING_RATIO
    val topLeft = Offset(core.x - radius, core.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val appear = (freeze / STAMP_APPEAR_LEVEL).coerceIn(0f, 1f)
    val stroke = kkStroke(RING_STROKE)
    drawCircle(cryo, radius, core, alpha = 0.22f * appear, style = stroke)
    val fill = when {
        motion == CoreStasisMotion.THAWING -> Kk.Bone
        urgent(engine) -> roles.threat
        else -> cryo
    }
    drawArc(fill, -90f, 360f * freeze, false, topLeft, arcSize, alpha = if (motion == CoreStasisMotion.HOLDING) 0.6f else 0.95f, style = stroke)
    val seconds = engine.content.coreStasis.freezeSeconds
    var tick = 1
    while (tick < seconds) {
        val angle = tick / seconds * CanvasMath.TAU - CanvasMath.TAU * 0.25f
        drawLine(Kk.Ink, polar(core, radius - RING_STROKE, angle), polar(core, radius + RING_STROKE, angle), 1.5f, alpha = appear)
        tick++
    }
}

/**
 * The warning stamp centred above the Core ([haloRadius] in px), tilted the other way from the overheat stamp: "Core
 * freezing — move" with the seconds left on a cryo plate (threat over a lethal freeze's last stretch, hatched in Mono),
 * or "Core thawing" on a bone plate while it thaws. The label shrinks to fit narrow screens at large text sizes; the
 * seconds draw from cached digits.
 */
internal fun DrawScope.drawCoreStasisStamp(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    core: Offset,
    haloRadius: Float,
    renderTime: Float,
) {
    val freeze = engine.coreStasis
    if (freeze <= 0f) return
    val thawing = engine.coreStasisMotion == CoreStasisMotion.THAWING
    val urgent = urgent(engine)
    val language = measurer.language
    val text = if (thawing) {
        HudScratch.coreThaw.of(language, 0L) { language.text(HudRedesignText.CoreStasisThawing) }
    } else {
        HudScratch.coreStasis.of(language, 0L) { language.text(HudRedesignText.CoreStasisWarning) }
    }
    val k = 14f / 17f
    val padX = d(22f) * k
    val gap = d(8f) * k
    val tenths = ceil((1f - freeze) * engine.content.coreStasis.freezeSeconds * 10f - 1e-3f).toLong().coerceAtLeast(0L)
    val separator = WorldStrings.decimalSeparator(language)
    val unit = WorldStrings.secondsSuffix(language)
    val baseNumber = if (thawing) 0f else secondsWidth(measurer, measurer.typography.condStyle(STAMP_TEXT_SP, tabular = true), tenths, separator, unit) + gap
    val room = size.width - 2f * d(12f) - padX - baseNumber
    val slot = if (thawing) HudText.CORE_THAW else HudText.CORE_STASIS
    val ui = HudMeasurers.ui(measurer)
    val layout = HudDrawCache.fitted(slot, ui, text, STAMP_TEXT_SP, room, uppercase = true, minSize = STAMP_MIN_TEXT_SP) { sp ->
        measurer.typography.condStyle(sp, trackingEm = 0.06f, lineHeightEm = 1f)
    }
    val numberStyle = measurer.typography.condStyle(HudDrawCache.fittedSize(slot, ui, text, STAMP_TEXT_SP, room, true), tabular = true)
    val number = if (thawing) 0f else secondsWidth(measurer, numberStyle, tenths, separator, unit) + gap
    val width = layout.size.width + number + padX
    val height = layout.kkBoxHeight + d(9f) * k
    val margin = d(8f)
    val left = (core.x - width * 0.5f).coerceIn(margin, max(margin, size.width - margin - width))
    val top = (core.y - haloRadius * 1.2f - height).coerceIn(margin, max(margin, size.height - margin - height))
    val appear = (freeze / STAMP_APPEAR_LEVEL).coerceIn(0f, 1f)
    val period = if (urgent) STAMP_URGENT_PERIOD else STAMP_FREEZE_PERIOD
    // A thawing stamp stands still: the danger is receding.
    val alpha = if (thawing) appear else appear * (0.55f + 0.45f * (1f - kkPulse(renderTime, period, KkEase.Out)))
    val roles = measurer.roles
    val plate = when {
        thawing -> Kk.Bone
        urgent -> roles.threat
        else -> roles.element(StatusElement.CRYO.ordinal)
    }
    rotate(STAMP_TILT_DEGREES, Offset(left + width * 0.5f, top + height * 0.5f)) {
        drawRect(Kk.Ink, Offset(left + d(3f) * k, top + d(3f) * k), Size(width, height), alpha)
        drawRect(plate, Offset(left, top), Size(width, height), alpha)
        if (urgent && roles.hatchThreats) {
            drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, Kk.Ink), Offset(left, top), Size(width, height), alpha)
        }
        // The plate pulses; the ink label and seconds stay solid (a text alpha change repaints its paragraph).
        val labelLeft = left + padX * 0.5f
        val middle = top + height * 0.5f + d(0.5f)
        drawKkText(layout, labelLeft, middle, Kk.Ink, KkAlign.START, KkVAlign.CENTER)
        if (!thawing) {
            val x = labelLeft + layout.size.width + gap
            val whole = drawKkTabularNumber(measurer, tenths / 10L, numberStyle, x, middle, Kk.Ink, KkAlign.START, KkVAlign.CENTER, suffix = separator)
            drawKkTabularNumber(measurer, tenths % 10L, numberStyle, x + whole, middle, Kk.Ink, KkAlign.START, KkVAlign.CENTER, suffix = unit)
        }
    }
    HudLayoutProbe.record(HudBlock.CORE_STASIS, left, top, left + width, top + height)
}

/** The width of "[tenths / 10][separator][tenths % 10][unit]" in [style]. */
private fun secondsWidth(measurer: TextMeasurer, style: androidx.compose.ui.text.TextStyle, tenths: Long, separator: String, unit: String): Float =
    kkTabularNumberWidth(measurer, tenths / 10L, style, suffix = separator) + kkTabularNumberWidth(measurer, tenths % 10L, style, suffix = unit)

/** A freeze that can end the run is in its last stretch (never in Easy mode, never while it thaws). */
private fun urgent(engine: GameplayRenderModel): Boolean =
    !engine.easyMode && engine.coreStasis >= URGENT_FREEZE && engine.coreStasisMotion != CoreStasisMotion.THAWING

/** The last stretch of the freeze: about the last 1.3 s at the shipped 4.2 s. */
private const val URGENT_FREEZE = 0.7f

private const val FROST_OUTER_ALPHA = 0.34f
private const val FROST_INNER_ALPHA = 0.28f
private const val FROST_OUTER_CLEAR_STOP = 0.6f
private const val FROST_INNER_CLEAR_STOP = 0.38f

/** The urgent frost breathes at the scorch edge's rate (1.2 Hz) between this share and full. */
private const val FROST_PULSE_LOW = 0.7f
private const val FROST_PULSE_HZ = 1.2f

/** Corner shards: how far the longest reaches (dp, on a screen at least [CORNER_SCREEN_DP] on its short side). */
private const val CORNER_REACH_DP = 150f
private const val CORNER_SCREEN_DP = 600f
private const val CORNER_SHARDS = 5
private const val CORNER_GROW = 0.35f
private val CORNER_BISECTORS = floatArrayOf(0.785f, 2.356f, 3.927f, 5.498f)
private val CornerStart = floatArrayOf(0f, 0.12f, 0.24f, 0.4f, 0.55f)
private val CornerLength = floatArrayOf(0.62f, 0.45f, 0.5f, 0.85f, 1f)
private val CornerSpread = floatArrayOf(0f, -0.42f, 0.4f, -0.18f, 0.16f)

/** The ring meter: its radius in Core halo radii and its stroke (world units). */
private const val RING_RATIO = 1.36f
private const val RING_STROKE = 3f

private const val STAMP_TEXT_SP = 14f
private const val STAMP_MIN_TEXT_SP = 10f
private const val STAMP_TILT_DEGREES = 4f

/** The stamp and ring fade in over the first part of the freeze, so a brief dip does not flicker them. */
private const val STAMP_APPEAR_LEVEL = 0.12f
private const val STAMP_FREEZE_PERIOD = 1.1f
private const val STAMP_URGENT_PERIOD = 0.75f
