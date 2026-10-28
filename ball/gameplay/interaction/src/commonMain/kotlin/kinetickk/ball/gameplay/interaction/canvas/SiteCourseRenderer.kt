// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
