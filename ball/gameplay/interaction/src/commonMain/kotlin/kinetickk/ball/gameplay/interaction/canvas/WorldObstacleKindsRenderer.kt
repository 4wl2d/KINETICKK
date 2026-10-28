// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * The 0.6.0 obstacle kinds in the world and on the minimap. Each look is object-local, drawn from cached strokes and
 * palette roles, with no text and no direction mark; what a kind draws filled is what collides. Allocation-free per
 * frame.
 */

/** Farthest a kind draws from its centre (culling): a gravity knot's field, otherwise an asteroid outline's reach. */
internal fun obstacleVisualReach(obstacle: ObstacleProjection, kinds: ObstacleKindTuning): Float = when (obstacle.kind) {
    ObstacleKind.GRAVITY_WELL -> max(kinds.wellInfluence, obstacle.radius)
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.ACCEL_GATE, ObstacleKind.CRYSTAL,
    ObstacleKind.DERELICT_MINE, ObstacleKind.PHASE_SHARD, ObstacleKind.THERMAL_VENT,
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
