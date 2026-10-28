// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.foundation.design.*
import kotlin.math.cos
import kotlin.math.sin

/** Depth of the event's edge band (dp), as deep as the polarity edge band. */
private const val EVENT_EDGE_BAND_DP = 60f

/** Below one 8-bit step an edge would change no pixel (a wind along an axis leaves the side edges dark). */
private const val MIN_VISIBLE_ALPHA = 1f / 255f

/** Edge strength at the end of the telegraph, while the event runs, and the fade at its end. */
internal const val EVENT_EDGE_TELEGRAPH = 0.45f
internal const val EVENT_EDGE_ACTIVE = 0.30f

/**
 * The screen edge a world event tints, the only HUD sign of it besides the feed's banners (no text, no top block): every
 * edge in the kind's colour (meteors and the flare heat, the data storm bone, the ion storm the Ion hue), the gravity
 * storm only its downwind edges, each by the wind's share along it. [worldEventEdgeIntensity] gives the strength: a ramp
 * over the telegraph, a steady glow while the event runs and a fade over its last seconds (the end cue). The four bands
 * are cached gradients ([HudBrush.EVENT_LEFT] … [HudBrush.EVENT_BOTTOM], keyed by colour and size) drawn at that alpha.
 */
internal fun DrawScope.drawWorldEventEdge(
    engine: GameplayRenderModel,
    roles: KkRolePalette,
    @Suppress("UNUSED_PARAMETER") renderTime: Float,
) {
    val event = engine.worldEvent ?: return
    val intensity = worldEventEdgeIntensity(event, engine.renderElapsed)
    if (intensity <= 0f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val color = worldEventEdgeColor(event.kind, roles)
    val band = d(EVENT_EDGE_BAND_DP)
    if (event.kind == WorldEventKind.GRAVITY_STORM) {
        val windX = cos(event.windAngle)
        val windY = sin(event.windAngle)
        if (windX > 0f) drawEventEdge(HudBrush.EVENT_RIGHT, w, h, band, color, intensity * windX)
        if (windX < 0f) drawEventEdge(HudBrush.EVENT_LEFT, w, h, band, color, -intensity * windX)
        if (windY > 0f) drawEventEdge(HudBrush.EVENT_BOTTOM, w, h, band, color, intensity * windY)
        if (windY < 0f) drawEventEdge(HudBrush.EVENT_TOP, w, h, band, color, -intensity * windY)
        return
    }
    drawEventEdge(HudBrush.EVENT_LEFT, w, h, band, color, intensity)
    drawEventEdge(HudBrush.EVENT_RIGHT, w, h, band, color, intensity)
    drawEventEdge(HudBrush.EVENT_TOP, w, h, band, color, intensity)
    drawEventEdge(HudBrush.EVENT_BOTTOM, w, h, band, color, intensity)
}

/**
 * The edge band's strength at [now]: from 0 up to [EVENT_EDGE_TELEGRAPH] over the telegraph, [EVENT_EDGE_ACTIVE] while
 * the event runs, fading to 0 over its last [WORLD_EVENT_FADE_SECONDS]; 0 outside the event.
 */
internal fun worldEventEdgeIntensity(event: WorldEventProjection, now: Float): Float {
    if (now < event.telegraphAt || now >= event.endsAt) return 0f
    if (now < event.startsAt) {
        val telegraph = (event.startsAt - event.telegraphAt).coerceAtLeast(0.01f)
        return EVENT_EDGE_TELEGRAPH * ((now - event.telegraphAt) / telegraph).coerceIn(0f, 1f)
    }
    return EVENT_EDGE_ACTIVE * ((event.endsAt - now) / WORLD_EVENT_FADE_SECONDS).coerceIn(0f, 1f)
}

/** The edge colour of [kind]: heat for meteors and the flare, bone for data, polarity for gravity, the Ion hue for ions. */
internal fun worldEventEdgeColor(kind: WorldEventKind, roles: KkRolePalette): Color = when (kind) {
    WorldEventKind.METEOR_SHOWER, WorldEventKind.SOLAR_FLARE -> roles.heat
    WorldEventKind.DATA_STORM -> Kk.Bone
    WorldEventKind.GRAVITY_STORM -> roles.pol
    WorldEventKind.ION_STORM -> roles.element(StatusElement.ION.ordinal)
}

/** One band hugging the [slot]'s edge: [color] at the edge, transparent [band] inside, drawn at [alpha]. */
private fun DrawScope.drawEventEdge(slot: HudBrush, w: Float, h: Float, band: Float, color: Color, alpha: Float) {
    if (alpha < MIN_VISIBLE_ALPHA) return
    when (slot) {
        HudBrush.EVENT_LEFT, HudBrush.EVENT_RIGHT -> {
            val right = slot == HudBrush.EVENT_RIGHT
            val left = if (right) w - band else 0f
            val brush = HudDrawCache.brush(slot, left, band, 0f, 0f, color) {
                val stops = if (right) arrayOf(0f to Color.Transparent, 0.55f to color.copy(alpha = 0.35f), 1f to color)
                else arrayOf(0f to color, 0.45f to color.copy(alpha = 0.35f), 1f to Color.Transparent)
                Brush.horizontalGradient(*stops, startX = left, endX = left + band)
            }
            drawRect(brush, Offset(left, 0f), Size(band, h), alpha)
        }
        else -> {
            val bottom = slot == HudBrush.EVENT_BOTTOM
            val top = if (bottom) h - band else 0f
            val brush = HudDrawCache.brush(slot, top, band, 0f, 0f, color) {
                val stops = if (bottom) arrayOf(0f to Color.Transparent, 0.55f to color.copy(alpha = 0.35f), 1f to color)
                else arrayOf(0f to color, 0.45f to color.copy(alpha = 0.35f), 1f to Color.Transparent)
                Brush.verticalGradient(*stops, startY = top, endY = top + band)
            }
            drawRect(brush, Offset(0f, top), Size(w, band), alpha)
        }
    }
}
