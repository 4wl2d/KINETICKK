// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Capsule geometry and the wall queries of the structure window. A wall is the segment A–B inflated by its half
 * thickness (a zero-length segment is a pillar). Sealed doors block like solid walls; open doors only when a query
 * asks for them. Every query walks the at most nine windowed structures by their footprint first, then the walls of
 * the overlapped ones by their own bounds, allocation-free.
 */

/**
 * How far a wall reaches past its structure's footprint: the footprint bounds wall centre lines, and no wall or
 * free-standing capsule is thicker than the content's 80 u half thickness.
 */
internal const val STRUCTURE_WALL_OVERHANG: Float = 80f

/**
 * Fraction t ∈ [0, 1] of P0 → P1 at which a point first comes within [radius] of the segment A–B; 0 when it starts
 * within it, −1 when it never does (moving away or parallel outside). Computed in Double, like [sweepEntry].
 */
internal fun sweepCapsuleEntry(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    radius: Float,
): Float {
    val r = radius.toDouble()
    val startX = x0.toDouble()
    val startY = y0.toDouble()
    val segmentX = bx.toDouble() - ax
    val segmentY = by.toDouble() - ay
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val along = if (lengthSquared > 0.0) (((startX - ax) * segmentX + (startY - ay) * segmentY) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
    val offsetX = startX - (ax + segmentX * along)
    val offsetY = startY - (ay + segmentY * along)
    if (offsetX * offsetX + offsetY * offsetY <= r * r) return 0f
    if (x0 == x1 && y0 == y1) return -1f
    var best = 2.0
    val capA = sweepEntry(x0, y0, x1, y1, ax, ay, radius)
    if (capA >= 0f) best = min(best, capA.toDouble())
    val capB = sweepEntry(x0, y0, x1, y1, bx, by, radius)
    if (capB >= 0f) best = min(best, capB.toDouble())
    val length = sqrt(lengthSquared)
    if (length > MIN_SEGMENT_LENGTH) {
        val ux = segmentX / length
        val uy = segmentY / length
        // The side line on P0's side, offset by the radius.
        val s0 = -uy * (startX - ax) + ux * (startY - ay)
        val s1 = -uy * (x1.toDouble() - ax) + ux * (y1.toDouble() - ay)
        val target = if (s0 >= 0.0) r else -r
        if ((s0 - target) * (s1 - target) <= 0.0 && s0 != s1) {
            val t = (s0 - target) / (s0 - s1)
            if (t in 0.0..1.0) {
                val crossX = startX + (x1.toDouble() - startX) * t
                val crossY = startY + (y1.toDouble() - startY) * t
                val projection = ux * (crossX - ax) + uy * (crossY - ay)
                if (projection in 0.0..length) best = min(best, t)
            }
        }
    }
    return if (best <= 1.0) best.toFloat() else -1f
}

/**
 * Fraction (0..1) of a shot's or weapon entity's step P0 → P1 at which a circle of [radius] runs into the wall A–B of
 * [wallHalfThickness], or −1. A circle that starts overlapping the wall is stopped only when it moves inward
 * ([obstacleStepEntry] semantics).
 */
internal fun capsuleStepEntry(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    wallHalfThickness: Float,
    radius: Float,
): Float {
    val reach = wallHalfThickness + radius
    val along = segmentNearest(x0, y0, ax, ay, bx, by)
    val fx = x0 - (ax + (bx - ax) * along)
    val fy = y0 - (ay + (by - ay) * along)
    if (fx * fx + fy * fy <= reach * reach) return if (fx * (x1 - x0) + fy * (y1 - y0) < 0f) 0f else -1f
    return sweepCapsuleEntry(x0, y0, x1, y1, ax, ay, bx, by, reach)
}

/** Parameter s ∈ [0, 1] of the point of the segment A–B nearest ([px], [py]); 0 for a pillar. */
internal fun segmentNearest(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0f) return 0f
    return (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
}

/**
 * Visits the walls of the window whose bounds inflated by their half thickness and [reach] contain ([x], [y]): solid
 * walls and sealed doors, plus open doors when [includeOpen]. At most nine footprint tests, then at most 48 wall
 * bound tests per overlapped structure; allocation-free.
 */
internal inline fun MutableGameState.forEachSolidWallNear(
    x: Float,
    y: Float,
    reach: Float,
    includeOpen: Boolean = false,
    action: (index: Int, wall: Wall) -> Unit,
) {
    val field = structureField
    val structures = field.structures
    if (structures.isEmpty()) return
    val walls = field.walls
    val margin = reach + STRUCTURE_WALL_OVERHANG
    for (structureIndex in structures.indices) {
        val structure = structures[structureIndex]
        if (abs(x - structure.x) > structure.halfWidth + margin || abs(y - structure.y) > structure.halfHeight + margin) continue
        for (index in structure.wallStart until structure.wallEnd) {
            val wall = walls[index]
            if (!includeOpen && wall.kind == WallKind.DOOR_OPEN) continue
            val inflate = wall.halfThickness + reach
            if (x < min(wall.ax, wall.bx) - inflate || x > max(wall.ax, wall.bx) + inflate) continue
            if (y < min(wall.ay, wall.by) - inflate || y > max(wall.ay, wall.by) + inflate) continue
            action(index, wall)
        }
    }
}

/**
 * The nearest solid wall surface (sealed doors included) within [reach] of ([x], [y]): returns the gap to it
 * (negative inside a wall) or +∞ when none is that close, and writes the surface point and the outward normal into
 * [out] as (x, y, nx, ny) (four slots). A point on a wall's centre line takes the segment's left normal; on a
 * pillar's centre, +x.
 */
internal fun MutableGameState.nearestWallSurface(x: Float, y: Float, reach: Float, out: FloatArray): Float {
    var best = Float.POSITIVE_INFINITY
    forEachSolidWallNear(x, y, reach) { _, wall ->
        val along = segmentNearest(x, y, wall.ax, wall.ay, wall.bx, wall.by)
        val nearestX = wall.ax + (wall.bx - wall.ax) * along
        val nearestY = wall.ay + (wall.by - wall.ay) * along
        val dx = x - nearestX
        val dy = y - nearestY
        val distance = sqrt(dx * dx + dy * dy)
        val gap = distance - wall.halfThickness
        if (gap > reach || gap >= best) return@forEachSolidWallNear
        val normalX: Float
        val normalY: Float
        if (distance >= MIN_NORMAL_LENGTH) {
            normalX = dx / distance
            normalY = dy / distance
        } else {
            val segmentX = wall.bx - wall.ax
            val segmentY = wall.by - wall.ay
            val length = hypot(segmentX, segmentY)
            if (length > 0f) {
                normalX = -segmentY / length
                normalY = segmentX / length
            } else {
                normalX = 1f
                normalY = 0f
            }
        }
        best = gap
        out[0] = nearestX + normalX * wall.halfThickness
        out[1] = nearestY + normalY * wall.halfThickness
        out[2] = normalX
        out[3] = normalY
    }
    return best
}

/**
 * Index of the room of [template] (placed at ([centerX], [centerY]) with quarter [rotation]) holding ([x], [y]) at
 * least [inset] inside its bounds, or −1. The point is turned back into template space, where rooms are
 * axis-aligned.
 */
internal fun roomAt(template: StructureTemplate, rotation: Int, centerX: Float, centerY: Float, x: Float, y: Float, inset: Float): Int {
    val inverse = (4 - rotation) and 3
    val dx = x - centerX
    val dy = y - centerY
    val localX = rotateQuarterX(dx, dy, inverse)
    val localY = rotateQuarterY(dx, dy, inverse)
    val rooms = template.rooms
    for (index in rooms.indices) {
        val room = rooms[index]
        if (localX >= room.minX + inset && localX <= room.maxX - inset && localY >= room.minY + inset && localY <= room.maxY - inset) {
            return index
        }
    }
    return -1
}

private const val MIN_SEGMENT_LENGTH: Double = 1e-6

/** Below this distance from a wall's centre line the contact normal falls back to the segment's own normal. */
private const val MIN_NORMAL_LENGTH: Float = 0.001f
