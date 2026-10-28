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
import kinetickk.ball.content.api.obstacleSeedUnit
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
 * Farthest a kind draws from its centre (culling): a gravity knot's field, a thermal vent's plume, an armed mine's
 * blast ring, otherwise an asteroid outline's reach (a gate's posts, a crystal's corners and a dormant mine's pips lie
 * within it).
 */
internal fun obstacleVisualReach(obstacle: ObstacleProjection, kinds: ObstacleKindTuning): Float = when (obstacle.kind) {
    ObstacleKind.GRAVITY_WELL -> max(kinds.wellInfluence, obstacle.radius)
    ObstacleKind.THERMAL_VENT -> obstacle.radius + kinds.ventPlumeMargin
    ObstacleKind.DERELICT_MINE ->
        if (obstacle.fuseAt > 0f) max(obstacle.radius, kinds.mineBlastRadius) else obstacle.radius * ObstaclePaths.OUTLINE_MAX_REACH
    ObstacleKind.ASTEROID, ObstacleKind.BUMPER, ObstacleKind.UNSTABLE, ObstacleKind.ACCEL_GATE, ObstacleKind.CRYSTAL,
    ObstacleKind.PHASE_SHARD,
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

// ---------------------------------------------------------------------------------------------
// Crystals.

private const val TWO_PI: Float = 6.2831855f

/** Corners of a crystal's facet hexagon. */
internal const val CRYSTAL_CORNERS: Int = 6

/**
 * A crystal's hexagon edges lie at this share of its collision radius, like an asteroid's closest edge, so its corners
 * reach 0.98 / cos 30 degrees = 1.13 radii, within the outline reach.
 */
internal const val CRYSTAL_APOTHEM: Float = 0.98f

/** The inner facet hexagon's edges, as a share of the collision radius. */
private const val CRYSTAL_INNER_APOTHEM: Float = 0.45f

/** From a hexagon's apothem to its corner reach: 1 / cos 30 degrees. */
private const val HEXAGON_CORNER_REACH: Float = 1.1547005f

private const val CRYSTAL_ANGLE_SALT: Int = 90

/** The angle of a crystal's first facet corner: from its shape seed, within one corner spacing. */
internal fun crystalFacetAngle(seed: Int): Float = obstacleSeedUnit(seed, CRYSTAL_ANGLE_SALT) * (TWO_PI / CRYSTAL_CORNERS)

/** A crystal's facet hexagon, centred at the origin (see [ObstaclePaths.crystalFacets]). */
internal fun buildCrystalFacets(path: Path, seed: Int, radius: Float) {
    addHexagon(path, crystalFacetAngle(seed), radius * CRYSTAL_APOTHEM * HEXAGON_CORNER_REACH)
}

/**
 * A crystal's facet lines: an inner hexagon and three spokes from its alternate corners out to the facet hexagon's
 * matching corners (see [ObstaclePaths.crystalFacetLines]).
 */
internal fun buildCrystalFacetLines(path: Path, seed: Int, radius: Float) {
    val angle = crystalFacetAngle(seed)
    val inner = radius * CRYSTAL_INNER_APOTHEM * HEXAGON_CORNER_REACH
    val outer = radius * CRYSTAL_APOTHEM * HEXAGON_CORNER_REACH
    addHexagon(path, angle, inner)
    for (corner in 0 until CRYSTAL_CORNERS step 2) {
        val cornerAngle = angle + corner * TWO_PI / CRYSTAL_CORNERS
        path.moveTo(cos(cornerAngle) * inner, sin(cornerAngle) * inner)
        path.lineTo(cos(cornerAngle) * outer, sin(cornerAngle) * outer)
    }
}

private fun addHexagon(path: Path, angle: Float, cornerReach: Float) {
    for (corner in 0 until CRYSTAL_CORNERS) {
        val cornerAngle = angle + corner * TWO_PI / CRYSTAL_CORNERS
        val x = cos(cornerAngle) * cornerReach
        val y = sin(cornerAngle) * cornerReach
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** The palette's element hue of Cryo (the content's element order). */
private const val CRYO_HUE: Int = 1

/**
 * A crystal: a solid ink hexagon hugging its collision circle with a Cryo rim, an inner Cryo facet and three spokes
 * out to alternate corners. It breaks into Cryo shards when struck hard.
 */
internal fun DrawScope.drawCrystal(obstacle: ObstacleProjection, center: Offset, roles: KkRolePalette) {
    val cryo = roles.element(CRYO_HUE)
    val facets = ObstaclePaths.crystalFacets(obstacle.shapeSeed, obstacle.radius)
    val lines = ObstaclePaths.crystalFacetLines(obstacle.shapeSeed, obstacle.radius)
    translate(center.x, center.y) {
        drawPath(facets, Kk.Ink2)
        drawPath(facets, cryo.copy(alpha = 0.8f), style = kkStroke(2f))
        drawPath(lines, cryo.copy(alpha = 0.45f), style = kkStroke(1f))
    }
}

/** A crystal on the minimap: a Cryo dot. */
internal fun DrawScope.drawMinimapCrystal(center: Offset, radius: Float, roles: KkRolePalette) {
    drawCircle(roles.element(CRYO_HUE).copy(alpha = 0.75f), radius, center)
}

// ---------------------------------------------------------------------------------------------
// Derelict mines.

/** Pips of a derelict mine, 120 degrees apart. */
internal const val MINE_PIPS: Int = 3

/** Radius of each pip's ink disc. */
internal const val MINE_PIP_RADIUS: Float = 8f

/** From a mine's centre to each pip's centre, as a share of its body radius. */
internal const val MINE_PIP_REACH: Float = 0.55f

/** Each pip's blunt stubs run from this far from its centre... */
private const val MINE_STUB_FROM: Float = 8f

/** ...to this far. */
internal const val MINE_STUB_TO: Float = 13f

/** Stubs around each pip. */
private const val MINE_STUBS_PER_PIP: Int = 4

/** Radius of the warning light at each pip's centre. */
private const val MINE_LIGHT_RADIUS: Float = 2.5f

/** Salt of a mine's pip rotation. */
internal const val MINE_ANGLE_SALT: Int = 95

/** Visits each pip centre of a mine with shape [seed] and body [radius], centred at the origin. */
internal inline fun forEachMinePip(seed: Int, radius: Float, action: (x: Float, y: Float, angle: Float) -> Unit) {
    val rotation = obstacleSeedUnit(seed, MINE_ANGLE_SALT) * 6.2831855f
    for (pip in 0 until MINE_PIPS) {
        val angle = rotation + pip * 6.2831855f / MINE_PIPS
        action(cos(angle) * radius * MINE_PIP_REACH, sin(angle) * radius * MINE_PIP_REACH, angle)
    }
}

/** A mine's three pip discs (see [ObstaclePaths.minePips]). */
internal fun buildMinePips(path: Path, seed: Int, radius: Float) {
    forEachMinePip(seed, radius) { x, y, _ -> path.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), MINE_PIP_RADIUS)) }
}

/** Four short radial stubs around each pip, diagonal to its direction from the centre (see [ObstaclePaths.mineStubs]). */
internal fun buildMineStubs(path: Path, seed: Int, radius: Float) {
    forEachMinePip(seed, radius) { x, y, angle ->
        for (stub in 0 until MINE_STUBS_PER_PIP) {
            val stubAngle = angle + TWO_PI / 8f + stub * TWO_PI / MINE_STUBS_PER_PIP
            val dx = cos(stubAngle)
            val dy = sin(stubAngle)
            path.moveTo(x + dx * MINE_STUB_FROM, y + dy * MINE_STUB_FROM)
            path.lineTo(x + dx * MINE_STUB_TO, y + dy * MINE_STUB_TO)
        }
    }
}

/** A warning light at each pip's centre (see [ObstaclePaths.mineLights]). */
internal fun buildMineLights(path: Path, seed: Int, radius: Float) {
    forEachMinePip(seed, radius) { x, y, _ -> path.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), MINE_LIGHT_RADIUS)) }
}

/** Blinks per second of an armed mine's lights as its fuse starts and as it runs out. */
private const val MINE_BLINK_FIRST: Float = 6f
private const val MINE_BLINK_LAST: Float = 24f

/**
 * A derelict mine: three small ink pips with threat stubs and lights, no body (nothing of it collides). Dormant, its
 * lights breathe slowly; armed, they blink faster as the fuse runs out, its stubs brighten and a threat ring marks the
 * blast's edge, firmer as the blast nears.
 */
internal fun DrawScope.drawDerelictMine(
    obstacle: ObstacleProjection,
    center: Offset,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    val threat = roles.threat
    val armed = obstacle.fuseAt > 0f
    val progress = if (armed) (1f - (obstacle.fuseAt - time) / kinds.mineFuseSeconds).coerceIn(0f, 1f) else 0f
    if (armed) drawCircle(threat.copy(alpha = 0.15f + 0.45f * progress), kinds.mineBlastRadius, center, style = kkStroke(2f))
    val lights = if (armed) {
        val blinks = time * (MINE_BLINK_FIRST + (MINE_BLINK_LAST - MINE_BLINK_FIRST) * progress)
        if (blinks - floor(blinks) < 0.5f) 0.95f else 0.15f
    } else {
        0.3f + 0.3f * (sin(time * 2f) + 1f) * 0.5f
    }
    val seed = obstacle.shapeSeed
    val radius = obstacle.radius
    val pips = ObstaclePaths.minePips(seed, radius)
    val stubs = ObstaclePaths.mineStubs(seed, radius)
    val glow = ObstaclePaths.mineLights(seed, radius)
    translate(center.x, center.y) {
        drawPath(pips, Kk.Ink2)
        drawPath(stubs, threat.copy(alpha = if (armed) 0.9f else 0.55f), style = kkStroke(2f))
        drawPath(glow, threat.copy(alpha = lights))
    }
}

/** A derelict mine on the minimap: a threat dot, and once armed its blast's edge as a threat ring. */
internal fun DrawScope.drawMinimapDerelictMine(
    obstacle: ObstacleProjection,
    center: Offset,
    radius: Float,
    scale: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    drawCircle(roles.threat.copy(alpha = 0.85f), radius, center)
    if (obstacle.fuseAt > 0f) drawCircle(roles.threat.copy(alpha = 0.5f), kinds.mineBlastRadius * scale, center, style = kkStroke(1f))
}
