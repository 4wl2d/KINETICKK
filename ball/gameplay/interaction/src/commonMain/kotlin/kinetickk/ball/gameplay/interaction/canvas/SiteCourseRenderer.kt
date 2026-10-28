// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawKkPips
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkShearOffset
import kinetickk.foundation.design.withKkShear
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A race gate post: a sheared plate this wide and tall, upright like the totem's plates. */
private const val GATE_POST_WIDTH = 10f
private const val GATE_POST_HEIGHT = 34f

/** The finish gate doubles each post this far along the course on either side. */
private const val FINISH_POST_SPLIT = 8f

/**
 * Order pips under a gate (the beacon's sheared pips, smaller): height, width and gap, and their top below the gate's
 * center. Seven of them stay inside the gate's mark box, so the drawn next gate is whole on screen.
 */
private const val GATE_PIP_HEIGHT = 7f
private const val GATE_PIP_WIDTH = 9f
private const val GATE_PIP_GAP = 4f
private const val GATE_PIP_TOP = 22f

/** Culling margin of a gate that is not the next one: its posts and pips. */
private const val GATE_CULL_MARGIN = 40f

/** A slalom pole: a sheared plate this wide and tall, upright like a gate post but slimmer. */
private const val POLE_WIDTH = 8f
private const val POLE_HEIGHT = 26f

/**
 * A pole's flag: a short sheared plate this wide and tall standing [FLAG_DISTANCE] out from the pole's plate on its pass
 * side, so pole and flag frame the lane the Core flies through (the next pole joins them with a dashed bar, like the next
 * race gate's posts).
 */
private const val FLAG_WIDTH = 6f
private const val FLAG_HEIGHT = 16f
private const val FLAG_DISTANCE = 110f

/** Radius of the velocity hold's progress ring at its center. */
private const val VELOCITY_PROGRESS_RADIUS = 120f

/** Untouched volley shots fly this far past their aim point, for at most this long (the nucleus's volley rule). */
private const val UNTOUCHED_SHOT_OVERSHOOT = 400f
private const val UNTOUCHED_SHOT_MAX_LIFE = 2f

/**
 * A running gate race at [center]: a faint dashed line from the start through every gate, and each gate as two sheared
 * posts across the course, [gateRadius] either side of its center along the gate's normal (perpendicular to the course
 * from the previous mark to the next; the last gate follows its own leg), with its place in the course as 1..7 pips
 * below it, never a numeral. The next gate is the player's target: volt posts and a pulsing dashed bar between them,
 * shown only when its edge marker is not ([markShown], decided in the unshaken view); passed gates fade to bone, later
 * ones stay mute, and the finish gate doubles its posts. Allocation-free.
 */
internal fun DrawScope.drawRaceCourse(
    engine: GameplayRenderModel,
    point: SiteProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    keepOut: WorldHudKeepOut,
    scale: Float,
    roles: KkRolePalette,
) {
    val course = point.course
    if (course.isEmpty()) return
    val gateRadius = engine.content.sites.race.gateRadius
    val line = Kk.Mute.copy(alpha = 0.18f)
    var fromX = center.x
    var fromY = center.y
    for (index in course.indices) {
        val toX = center.x + course[index].dx
        val toY = center.y + course[index].dy
        drawLine(line, Offset(fromX, fromY), Offset(toX, toY), 1.5f, pathEffect = WorldStrokes.dashEffect)
        fromX = toX
        fromY = toY
    }
    val pulse = 0.55f + 0.45f * kkPulse(engine.renderElapsed, 1.2f)
    for (index in course.indices) {
        val gate = Offset(center.x + course[index].dx, center.y + course[index].dy)
        val next = index == point.step
        // The next gate shares its edge marker's predicate; the others may pass the edge.
        if (next) {
            if (!markShown(steady(gate, shakeX, shakeY), RACE_GATE_MARK_HALF, keepOut, scale)) continue
        } else if (!isOnScreen(gate, gateRadius + GATE_CULL_MARGIN)) {
            continue
        }
        val color = when {
            next -> roles.you.copy(alpha = pulse)
            index < point.step -> Kk.Bone.copy(alpha = 0.3f)
            else -> Kk.Mute.copy(alpha = 0.5f)
        }
        // The course's direction through this gate: from the previous mark (the start before the first gate) to the
        // next, or along the last leg at the finish.
        val previousDx = if (index == 0) 0f else course[index - 1].dx
        val previousDy = if (index == 0) 0f else course[index - 1].dy
        val finish = index == course.lastIndex
        val alongX = if (finish) course[index].dx - previousDx else course[index + 1].dx - previousDx
        val alongY = if (finish) course[index].dy - previousDy else course[index + 1].dy - previousDy
        val length = sqrt(alongX * alongX + alongY * alongY)
        val unitX = if (length > 0f) alongX / length else 1f
        val unitY = if (length > 0f) alongY / length else 0f
        // Posts sit on the normal (−unitY, unitX).
        val postX = -unitY * gateRadius
        val postY = unitX * gateRadius
        if (next) {
            drawLine(color, Offset(gate.x - postX, gate.y - postY), Offset(gate.x + postX, gate.y + postY), 3f,
                pathEffect = WorldStrokes.dashEffect)
        }
        drawGateSide(gate.x - postX, gate.y - postY, unitX, unitY, finish, color)
        drawGateSide(gate.x + postX, gate.y + postY, unitX, unitY, finish, color)
        drawGatePips(gate, index + 1, color, record = next)
        if (next) recordMark(gate, RACE_GATE_MARK_HALF)
    }
}

/** One side of a gate at ([x], [y]): a post, or at the finish two posts split along the course ([unitX], [unitY]). */
private fun DrawScope.drawGateSide(x: Float, y: Float, unitX: Float, unitY: Float, finish: Boolean, color: Color) {
    if (!finish) {
        drawGatePost(x, y, color)
        return
    }
    drawGatePost(x - unitX * FINISH_POST_SPLIT, y - unitY * FINISH_POST_SPLIT, color)
    drawGatePost(x + unitX * FINISH_POST_SPLIT, y + unitY * FINISH_POST_SPLIT, color)
}

/** One upright sheared post centred at ([x], [y]). */
private fun DrawScope.drawGatePost(x: Float, y: Float, color: Color) {
    withKkShear(y) {
        drawRect(color, Offset(x - GATE_POST_WIDTH * 0.5f, y - GATE_POST_HEIGHT * 0.5f), Size(GATE_POST_WIDTH, GATE_POST_HEIGHT))
    }
}

/**
 * [count] filled order pips centred under [gate], drawn at a fixed origin under a translation so the pip paths stay
 * cached however the course moves on screen; the next gate's row is recorded for the HUD probes.
 */
private fun DrawScope.drawGatePips(gate: Offset, count: Int, color: Color, record: Boolean) {
    val total = count * GATE_PIP_WIDTH + (count - 1) * GATE_PIP_GAP
    val left = gate.x - total * 0.5f
    val top = gate.y + GATE_PIP_TOP
    translate(left, top) {
        drawKkPips(Offset.Zero, count, count, color,
            sizeDp = GATE_PIP_HEIGHT / density, widthDp = GATE_PIP_WIDTH / density, gapDp = GATE_PIP_GAP / density)
    }
    if (record) {
        // The pips' sheared outline reaches past the row's box by the shear travel on each side.
        val shear = abs(kkShearOffset(GATE_PIP_HEIGHT))
        WorldDrawProbe.record(WorldDrawn.BEACON_PIPS, left - shear, top, left + total + shear, top + GATE_PIP_HEIGHT)
    }
}

/**
 * A running slalom at [center]: each pole a slim sheared plate with a short flag plate out on the side it is passed on
 * (outward from the course's axis, the side the pole stands on), so the two frame the lane to fly through. The next pole
 * is the player's target in volt with a dashed bar across its lane, shown only when its edge marker is not ([markShown],
 * decided in the unshaken view); passed poles fade to bone and later ones stay mute. Allocation-free.
 */
internal fun DrawScope.drawSlalomCourse(
    point: SiteProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    keepOut: WorldHudKeepOut,
    scale: Float,
    roles: KkRolePalette,
) {
    val course = point.course
    if (course.size < 2) return
    // Poles alternate sides, so the first two lateral offsets cancel and their sum runs along the axis.
    val axisX = course[0].dx + course[1].dx
    val axisY = course[0].dy + course[1].dy
    val axisLength = sqrt(axisX * axisX + axisY * axisY)
    val unitX = if (axisLength > 0f) axisX / axisLength else 1f
    val unitY = if (axisLength > 0f) axisY / axisLength else 0f
    for (index in course.indices) {
        val pole = Offset(center.x + course[index].dx, center.y + course[index].dy)
        val next = index == point.step
        // The next pole shares its edge marker's predicate; the others may pass the edge.
        if (next) {
            if (!markShown(steady(pole, shakeX, shakeY), SLALOM_POLE_MARK_HALF, keepOut, scale)) continue
        } else if (!isOnScreen(pole, SLALOM_POLE_MARK_HALF)) {
            continue
        }
        val color = when {
            next -> roles.you
            index < point.step -> Kk.Bone.copy(alpha = 0.3f)
            else -> Kk.Mute.copy(alpha = 0.5f)
        }
        // The pass side: bit set = left of the axis, the normal (−unitY, unitX).
        val side = if (point.sides and (1 shl index) != 0) 1f else -1f
        val flagX = -unitY * side
        val flagY = unitX * side
        // The pole plate's reach toward the pass side (its box's support), so the lane starts at the plate's edge.
        val edge = abs(flagX) * POLE_WIDTH * 0.5f + abs(flagY) * POLE_HEIGHT * 0.5f
        val flag = Offset(pole.x + flagX * (edge + FLAG_DISTANCE), pole.y + flagY * (edge + FLAG_DISTANCE))
        if (next) {
            drawLine(color, Offset(pole.x + flagX * edge, pole.y + flagY * edge), flag, 3f, pathEffect = WorldStrokes.dashEffect)
        }
        withKkShear(flag.y) {
            drawRect(color, Offset(flag.x - FLAG_WIDTH * 0.5f, flag.y - FLAG_HEIGHT * 0.5f), Size(FLAG_WIDTH, FLAG_HEIGHT))
        }
        withKkShear(pole.y) {
            drawRect(color, Offset(pole.x - POLE_WIDTH * 0.5f, pole.y - POLE_HEIGHT * 0.5f), Size(POLE_WIDTH, POLE_HEIGHT))
        }
        if (next) recordMark(pole, SLALOM_POLE_MARK_HALF)
    }
}

/**
 * The ring of a running ring challenge at [center]: the brakeless run's shrinking ring (dashed bone) with its progress
 * arc in volt, the Ram rite's faint area ring, the untouched trial's ring with its progress arc and the telegraphed lanes
 * of a coming volley in threat, and the velocity hold's wide faint zone with its progress on a small ring at the center.
 * Drawn only while the ring reaches the screen. Allocation-free.
 */
internal fun DrawScope.drawChallengeRing(engine: GameplayRenderModel, point: SiteProjection, center: Offset, roles: KkRolePalette) {
    val radius = point.radius
    val progress = point.progress.coerceIn(0f, 1f)
    when (point.kind) {
        SiteKind.BRAKELESS_TRIAL -> if (isOnScreen(center, radius + 20f)) {
            drawCircle(Kk.Bone.copy(alpha = 0.45f), radius, center, style = WorldStrokes.dashedMedium)
            drawProgressArc(center, radius, progress, roles.you)
        }
        SiteKind.RAM_TRIAL -> if (isOnScreen(center, radius + 20f)) {
            drawCircle(Kk.Bone.copy(alpha = 0.12f), radius, center, style = WorldStrokes.dashedThin)
        }
        SiteKind.UNTOUCHED_TRIAL -> {
            if (point.warningRemaining > 0f) drawUntouchedTelegraph(engine, point, center, roles)
            if (isOnScreen(center, radius + 20f)) {
                drawCircle(Kk.Bone.copy(alpha = 0.3f), radius, center, style = WorldStrokes.dashedThin)
                drawProgressArc(center, radius, progress, roles.you)
            }
        }
        SiteKind.VELOCITY_TRIAL -> {
            if (isOnScreen(center, radius + 20f)) {
                drawCircle(Kk.Mute.copy(alpha = 0.15f), radius, center, style = WorldStrokes.dashedMedium)
            }
            if (isOnScreen(center, VELOCITY_PROGRESS_RADIUS + 20f)) {
                drawCircle(roles.you.copy(alpha = 0.18f), VELOCITY_PROGRESS_RADIUS, center, style = WorldStrokes.arc6)
                drawProgressArc(center, VELOCITY_PROGRESS_RADIUS, progress, roles.you)
            }
        }
        else -> Unit
    }
}

/**
 * A running bounty's arena at [center]: its edge as a dashed threat ring at the arena radius ([SiteProjection.radius])
 * and, faint and mute, the exit band past which leaving forfeits the bounty (the arena plus its exit margin). Each ring is
 * drawn only while it reaches the screen. Allocation-free.
 */
internal fun DrawScope.drawBountyArena(engine: GameplayRenderModel, point: SiteProjection, center: Offset, roles: KkRolePalette) {
    val radius = point.radius
    val exit = radius + engine.content.sites.bounty.exitMargin
    if (isOnScreen(center, exit + 20f)) drawCircle(Kk.Mute.copy(alpha = 0.12f), exit, center, style = WorldStrokes.arc6)
    if (isOnScreen(center, radius + 20f)) drawCircle(roles.threat.copy(alpha = 0.35f), radius, center, style = WorldStrokes.dashedMedium)
}

/** A clockwise arc of [progress] of a full turn from the top of a ring of [radius] at [center]. */
private fun DrawScope.drawProgressArc(center: Offset, radius: Float, progress: Float, color: Color) {
    if (progress <= 0f) return
    drawArc(color, -90f, 360f * progress, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
        style = WorldStrokes.arc6)
}

/**
 * The untouched trial's coming volley: three dashed threat lanes from the center along the warned aim, as far as the
 * shots will fly (past the aim point by the volley's overshoot, within its longest flight), brighter as it nears.
 */
private fun DrawScope.drawUntouchedTelegraph(engine: GameplayRenderModel, point: SiteProjection, center: Offset, roles: KkRolePalette) {
    val cosine = cos(point.volleyAngle)
    val sine = sin(point.volleyAngle)
    val speed = engine.content.sites.challenges.untouchedShotSpeed
    val reach = min(speed * UNTOUCHED_SHOT_MAX_LIFE, sqrt(point.aimDx * point.aimDx + point.aimDy * point.aimDy) + UNTOUCHED_SHOT_OVERSHOOT)
    val alpha = min(1f, 0.35f + point.warningRemaining)
    for (lane in 0 until 3) {
        val lateral = (lane - 1) * 60f
        val offsetX = -sine * lateral
        val offsetY = cosine * lateral
        drawLine(
            roles.threat.copy(alpha = 0.9f * alpha),
            Offset(center.x + offsetX, center.y + offsetY),
            Offset(center.x + offsetX + cosine * reach, center.y + offsetY + sine * reach),
            3f,
            pathEffect = WorldStrokes.dashEffect,
        )
    }
}
