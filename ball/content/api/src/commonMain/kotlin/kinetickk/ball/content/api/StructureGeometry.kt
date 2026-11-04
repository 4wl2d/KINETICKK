// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** Rotation helpers shared by Nucleus and Interaction (pure): quarter [quarter] turns clockwise on screen. */
fun rotateQuarterX(x: Float, y: Float, quarter: Int): Float = when (quarter and 3) {
    0 -> x
    1 -> -y
    2 -> -x
    else -> y
}

fun rotateQuarterY(x: Float, y: Float, quarter: Int): Float = when (quarter and 3) {
    0 -> y
    1 -> x
    2 -> -y
    else -> -x
}

/**
 * Index of the room holding the offset ([dx], [dy]) from the centre of this template turned by [rotation], at least
 * [inset] inside its bounds, or [STRUCTURE_OUTSIDE]. The offset is turned back into template space, where rooms are
 * axis-aligned. Shared by Nucleus (which room seals) and Interaction (what the scrim and door hints show).
 */
fun StructureTemplate.roomAt(dx: Float, dy: Float, rotation: Int, inset: Float = 0f): Int {
    val inverse = (4 - rotation) and 3
    val localX = rotateQuarterX(dx, dy, inverse)
    val localY = rotateQuarterY(dx, dy, inverse)
    for (index in rooms.indices) {
        val room = rooms[index]
        if (localX >= room.minX + inset && localX <= room.maxX - inset && localY >= room.minY + inset && localY <= room.maxY - inset) {
            return index
        }
    }
    return STRUCTURE_OUTSIDE
}

/**
 * Compiles a template's capsule walls. Room edges merge per boundary line (HORIZONTAL lines by y ascending, then
 * VERTICAL by x ascending; touching intervals join); each door cuts the piece holding its opening, leaving jambs that
 * end [halfThickness] short of the opening so the clear width equals the door width. Output: the SOLID pieces in line
 * then position order, then [extraWalls], then one door capsule per door spanning its opening.
 */
internal fun compileStructureWalls(
    rooms: List<StructureRoom>,
    doors: List<StructureDoor>,
    extraWalls: List<StructureCapsule>,
    roomWalls: Boolean,
    halfThickness: Float,
): ImmutableList<StructureWallSpec> {
    val walls = ArrayList<StructureWallSpec>()
    if (roomWalls) {
        val edges = ArrayList<BoundaryEdge>(rooms.size * 4)
        rooms.forEach { room ->
            edges += BoundaryEdge(WallAxis.HORIZONTAL, room.minY, room.minX, room.maxX)
            edges += BoundaryEdge(WallAxis.HORIZONTAL, room.maxY, room.minX, room.maxX)
            edges += BoundaryEdge(WallAxis.VERTICAL, room.minX, room.minY, room.maxY)
            edges += BoundaryEdge(WallAxis.VERTICAL, room.maxX, room.minY, room.maxY)
        }
        val cutDoors = BooleanArray(doors.size)
        val lines = edges.map { it.axis to it.line }.distinct().sortedWith(compareBy({ it.first.ordinal }, { it.second }))
        lines.forEach { (axis, line) ->
            val cuts = doors.indices.filter { doors[it].axis == axis && doors[it].line == line }
                .sortedBy { doors[it].gapStart }
            mergedIntervals(edges.filter { it.axis == axis && it.line == line }).forEach { (lo, hi) ->
                var pieces = listOf(lo to hi)
                cuts.forEach { index ->
                    val door = doors[index]
                    pieces = pieces.flatMap { (start, end) ->
                        if (door.gapStart >= start && door.gapEnd <= end) {
                            require(!cutDoors[index]) { "Door $index opens on two wall pieces" }
                            cutDoors[index] = true
                            val before = start to door.gapStart - halfThickness
                            val after = door.gapEnd + halfThickness to end
                            require(before.second - before.first >= MIN_JAMB && after.second - after.first >= MIN_JAMB) {
                                "Door $index leaves a jamb shorter than ${MIN_JAMB.toInt()} u"
                            }
                            listOf(before, after)
                        } else {
                            listOf(start to end)
                        }
                    }
                }
                pieces.forEach { (start, end) ->
                    walls += when (axis) {
                        WallAxis.HORIZONTAL -> StructureWallSpec(start, line, end, line, halfThickness, door = -1)
                        WallAxis.VERTICAL -> StructureWallSpec(line, start, line, end, halfThickness, door = -1)
                    }
                }
            }
        }
        require(cutDoors.all { it }) { "Every door must open inside one wall piece, clear of other doors" }
    }
    extraWalls.forEach { walls += StructureWallSpec(it.ax, it.ay, it.bx, it.by, it.halfThickness, door = -1) }
    doors.forEachIndexed { index, door ->
        walls += when (door.axis) {
            WallAxis.HORIZONTAL -> StructureWallSpec(door.gapStart, door.line, door.gapEnd, door.line, halfThickness, index)
            WallAxis.VERTICAL -> StructureWallSpec(door.line, door.gapStart, door.line, door.gapEnd, halfThickness, index)
        }
    }
    return walls.toImmutableList()
}

private class BoundaryEdge(val axis: WallAxis, val line: Float, val lo: Float, val hi: Float)

/** Sorts [edges] by start and joins each interval into the previous one when it starts at or before its end. */
private fun mergedIntervals(edges: List<BoundaryEdge>): List<Pair<Float, Float>> {
    val merged = ArrayList<Pair<Float, Float>>()
    edges.sortedWith(compareBy({ it.lo }, { it.hi })).forEach { edge ->
        val last = merged.lastOrNull()
        if (last != null && edge.lo <= last.second) {
            merged[merged.lastIndex] = last.first to maxOf(last.second, edge.hi)
        } else {
            merged += edge.lo to edge.hi
        }
    }
    return merged
}

private const val MIN_JAMB: Float = 40f
