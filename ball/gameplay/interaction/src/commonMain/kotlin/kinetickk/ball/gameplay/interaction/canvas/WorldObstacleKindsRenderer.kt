// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.gateAngle
import kinetickk.ball.content.api.gateHalfSpan
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventEruptionEnd
import kinetickk.ball.content.api.ventSecondsToEruption
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * The 0.6.0 obstacle kinds in the world and on the minimap. Each look is object-local, drawn from cached strokes and
 * palette roles, with no text and no direction mark; what a kind draws filled is what collides. Allocation-free per
 * frame.
 */

/**
 * Farthest a kind draws from its centre (culling): a gravity knot's field, a thermal vent's plume, otherwise an
 * asteroid outline's reach (a gate's posts lie inside its body).
 */
internal fun obstacleVisualReach(obstacle: ObstacleProjection, kinds: ObstacleKindTuning): Float = when (obstacle.kind) {
    ObstacleKind.GRAVITY_WELL -> max(kinds.wellInfluence, obstacle.radius)
    ObstacleKind.THERMAL_VENT -> obstacle.radius + kinds.ventPlumeMargin
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.ACCEL_GATE, ObstacleKind.CRYSTAL,
    ObstacleKind.DERELICT_MINE, ObstacleKind.PHASE_SHARD,
    -> obstacle.radius * ObstaclePaths.OUTLINE_MAX_REACH
}

/** Rings drifting through a knot's field. */
private const val WELL_RINGS: Int = 3

/** Field passes per second each ring makes (inward for a Mass Knot, outward for a Repulsor). */
private const val WELL_RING_RATE: Float = 0.25f

/**
 * A gravity knot: a faint Gravitic boundary at its field's edge, three rings drifting through the field (contracting
 * onto a Mass Knot, spreading from a Repulsor) and the solid knot itself, an ink disc with a Gravitic rim. A Mass Knot
 * holds a filled Gravitic core; a Repulsor an open Gravitic ring around a bone point.
 */
internal fun DrawScope.drawGravityWell(
    obstacle: ObstacleProjection,
    center: Offset,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val gravitic = roles.element(GRAVITIC_HUE)
    val body = obstacle.radius
    val influence = max(kinds.wellInfluence, body)
    drawCircle(gravitic.copy(alpha = 0.10f), influence, center, style = kkStroke(1f))
    val drift = time * WELL_RING_RATE * (if (obstacle.repels) 1f else -1f)
    for (ring in 0 until WELL_RINGS) {
        val shifted = ring.toFloat() / WELL_RINGS + drift
        val phase = shifted - floor(shifted)
        val alpha = 0.22f * sin(PI.toFloat() * phase)
        if (alpha <= 0.004f) continue
        drawCircle(gravitic.copy(alpha = alpha), body + (influence - body) * phase, center, style = kkStroke(1.5f))
    }
    drawCircle(Kk.Ink2, body, center)
    drawCircle(gravitic.copy(alpha = 0.85f), body, center, style = kkStroke(3f))
    if (obstacle.repels) {
        drawCircle(gravitic.copy(alpha = 0.6f), body * 0.55f, center, style = kkStroke(2f))
        drawCircle(Kk.Bone, body * 0.15f, center)
    } else {
        drawCircle(gravitic.copy(alpha = 0.9f), body * 0.35f, center)
    }
}

/**
 * A gravity knot on the minimap: a Gravitic dot of the knot's size (at least [dot] × 0.9), with its field's edge as a
 * faint ring once that ring is at least two dots across from the dot's centre.
 */
internal fun DrawScope.drawMinimapGravityWell(
    obstacle: ObstacleProjection,
    center: Offset,
    scale: Float,
    dot: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val gravitic = roles.element(GRAVITIC_HUE)
    drawCircle(gravitic.copy(alpha = 0.85f), max(dot * 0.9f, obstacle.radius * scale), center)
    val influence = kinds.wellInfluence * scale
    if (influence >= 2f * dot) drawCircle(gravitic.copy(alpha = 0.2f), influence, center, style = kkStroke(1f))
}

/** The palette's element hue of Gravitic (the content's element order). */
private const val GRAVITIC_HUE: Int = 3

// ---------------------------------------------------------------------------------------------
// Accelerator gates.

/** Bars across a gate's line, as lateral offsets from its centre in shares of the half-span: symmetric about it. */
internal val GateBarOffsets: FloatArray = floatArrayOf(-0.6f, -0.2f, 0.2f, 0.6f)

/** Half a gate bar's length along the gate normal, as a share of the half-span. */
internal const val GATE_BAR_HALF_LENGTH: Float = 0.2f

/**
 * Visits each bar of a gate whose normal points along [angle], centred at the origin: a straight tick parallel to the
 * normal, centred on the gate's line between the posts ([halfSpan] either side). A gate works both ways, so the bars
 * carry no direction: the set is the same mirrored through the centre and mirrored across the line.
 */
internal inline fun forEachGateBar(
    angle: Float,
    halfSpan: Float,
    action: (x0: Float, y0: Float, x1: Float, y1: Float) -> Unit,
) {
    val normalX = cos(angle)
    val normalY = sin(angle)
    val reach = halfSpan * GATE_BAR_HALF_LENGTH
    for (index in GateBarOffsets.indices) {
        val lateral = GateBarOffsets[index] * halfSpan
        val centerX = -normalY * lateral
        val centerY = normalX * lateral
        action(centerX - normalX * reach, centerY - normalY * reach, centerX + normalX * reach, centerY + normalY * reach)
    }
}

/** Builds the bars of the gate with shape [seed] and [halfSpan] into [path] (see [ObstaclePaths.gateBars]). */
internal fun buildGateBars(path: Path, seed: Int, halfSpan: Float) {
    forEachGateBar(gateAngle(seed), halfSpan) { x0, y0, x1, y1 ->
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
    }
}

/** Angular rate (radians per second) of the pulse of a gate's bars. */
private const val GATE_PULSE_RATE: Float = 2.4f

/**
 * An accelerator gate: a faint bone line between its two posts, four short blunt volt bars across it pulsing gently
 * (symmetric: a gate works both ways), and the posts, small ink discs with a bone rim around a volt point. The posts
 * are emitters, not bodies: nothing of a gate collides.
 */
internal fun DrawScope.drawAccelGate(
    obstacle: ObstacleProjection,
    center: Offset,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val halfSpan = kinds.gateHalfSpan(obstacle.radius)
    val angle = gateAngle(obstacle.shapeSeed)
    val postX = -sin(angle) * halfSpan
    val postY = cos(angle) * halfSpan
    val first = Offset(center.x + postX, center.y + postY)
    val second = Offset(center.x - postX, center.y - postY)
    drawLine(Kk.Bone.copy(alpha = 0.18f), first, second, strokeWidth = 1f)
    val pulse = (sin(time * GATE_PULSE_RATE + obstacle.shapeSeed % 5) + 1f) * 0.5f
    val bars = ObstaclePaths.gateBars(obstacle.shapeSeed, halfSpan)
    translate(center.x, center.y) {
        drawPath(bars, roles.you.copy(alpha = 0.35f + 0.25f * pulse), style = kkStroke(2f))
    }
    drawGatePost(first, kinds.gatePostRadius, roles)
    drawGatePost(second, kinds.gatePostRadius, roles)
}

private fun DrawScope.drawGatePost(center: Offset, radius: Float, roles: KkRolePalette) {
    drawCircle(Kk.Ink2, radius, center)
    drawCircle(Kk.Bone.copy(alpha = 0.7f), radius, center, style = kkStroke(2f))
    drawCircle(roles.you.copy(alpha = 0.8f), radius * 0.35f, center)
}

/** An accelerator gate on the minimap: a volt line from post to post (at least a dot either side of its centre). */
internal fun DrawScope.drawMinimapAccelGate(
    obstacle: ObstacleProjection,
    center: Offset,
    scale: Float,
    dot: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val angle = gateAngle(obstacle.shapeSeed)
    val half = max(kinds.gateHalfSpan(obstacle.radius) * scale, dot * 0.9f)
    val postX = -sin(angle) * half
    val postY = cos(angle) * half
    val first = Offset(center.x + postX, center.y + postY)
    val second = Offset(center.x - postX, center.y - postY)
    drawLine(roles.you.copy(alpha = 0.7f), first, second, strokeWidth = max(1f, dot * 0.6f))
}

// ---------------------------------------------------------------------------------------------
// Thermal vents.

/** Rings rising through an erupting plume. */
private const val VENT_RINGS: Int = 2

/**
 * A thermal vent: a dark ground disc with a heat rim, heat cracks and an ember at its centre. Before it erupts an inner
 * glow swells; while it erupts its plume flickers in heat beyond the body and two rings rise from the body to the
 * plume's edge. The plume is not solid: it heats and lifts.
 */
internal fun DrawScope.drawThermalVent(
    obstacle: ObstacleProjection,
    center: Offset,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val body = obstacle.radius
    val seed = obstacle.shapeSeed
    val heat = roles.heat
    val erupting = kinds.ventErupting(time, seed)
    val plume = body + kinds.ventPlumeMargin
    if (erupting) {
        val flicker = (sin(time * 18f + seed) + 1f) * 0.5f
        drawCircle(heat.copy(alpha = 0.16f + 0.1f * flicker), plume, center)
    }
    drawCircle(Kk.Ink1, body, center)
    drawCircle(heat.copy(alpha = 0.45f), body, center, style = kkStroke(1.5f))
    val cracks = ObstaclePaths.cracks(seed, body)
    translate(center.x, center.y) {
        drawPath(cracks, heat.copy(alpha = 0.5f), style = kkStroke(1.5f))
    }
    drawCircle(heat.copy(alpha = 0.4f), body * 0.12f, center)
    if (erupting) {
        val progress = 1f - (kinds.ventEruptionEnd(time, seed) - time) / kinds.ventEruptSeconds
        for (ring in 0 until VENT_RINGS) {
            val shifted = progress + ring.toFloat() / VENT_RINGS
            val phase = shifted - floor(shifted)
            val alpha = 0.5f * (1f - phase)
            if (alpha <= 0.004f) continue
            drawCircle(heat.copy(alpha = alpha), body + (plume - body) * phase, center, style = kkStroke(2f))
        }
        return
    }
    val telegraph = kinds.ventTelegraphSeconds
    val untilEruption = kinds.ventSecondsToEruption(time, seed)
    if (telegraph > 0f && untilEruption <= telegraph) {
        drawCircle(heat.copy(alpha = 0.35f * (1f - untilEruption / telegraph)), body * 0.6f, center)
    }
}

/** A thermal vent on the minimap: a heat ring, filled while it erupts. */
internal fun DrawScope.drawMinimapThermalVent(
    obstacle: ObstacleProjection,
    center: Offset,
    radius: Float,
    dot: Float,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    if (kinds.ventErupting(time, obstacle.shapeSeed)) drawCircle(roles.heat.copy(alpha = 0.5f), radius, center)
    drawCircle(roles.heat.copy(alpha = 0.6f), radius, center, style = kkStroke(max(1f, dot * 0.6f)))
}
