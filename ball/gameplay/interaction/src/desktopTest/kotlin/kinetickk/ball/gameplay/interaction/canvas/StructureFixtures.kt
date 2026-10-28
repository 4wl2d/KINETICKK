// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection
import kinetickk.ball.gameplay.nucleus.render.StructureSignalProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.render.WallProjection
import kinetickk.foundation.collections.toImmutableList

/**
 * One structure of a render fixture: [template] (the model content's, so the renderer's path cache keys match) centred
 * at run-local ([x], [y]) and turned by [rotation], with its room progress. Doors follow the runtime's rules: open once
 * [spent]; sealed while their room is [engagedRoom] or while their lock waits on a room not in [clearedRooms].
 */
internal class StructureFixture(
    val template: StructureTemplate,
    val x: Float,
    val y: Float,
    val rotation: Int = 0,
    val clearedRooms: Int = 0,
    val engagedRoom: Int = -1,
    val spent: Boolean = false,
    val id: Long = (template.id.ordinal + 1).toLong() shl 8,
) {
    /** The run-local point of the template-space point ([localX], [localY]). */
    fun at(localX: Float, localY: Float): Pair<Float, Float> =
        x + rotateQuarterX(localX, localY, rotation) to y + rotateQuarterY(localX, localY, rotation)

    /** The run-local centre of [room]. */
    fun roomCenter(room: Int): Pair<Float, Float> {
        val spec = template.rooms[room]
        return at((spec.minX + spec.maxX) / 2f, (spec.minY + spec.maxY) / 2f)
    }

    /** The run-local entrance (the outer door's centre; the centre for a template without one). */
    fun entrance(): Pair<Float, Float> {
        val door = template.doors.getOrNull(template.entranceDoor) ?: return x to y
        return if (door.axis == WallAxis.HORIZONTAL) at(door.center, door.line) else at(door.line, door.center)
    }

    fun doorKind(door: Int): WallKind {
        if (spent) return WallKind.DOOR_OPEN
        val spec = template.doors[door]
        if (engagedRoom >= 0 && (spec.roomA == engagedRoom || spec.roomB == engagedRoom)) return WallKind.DOOR_SEALED
        if (spec.lockRooms != 0 && clearedRooms and spec.lockRooms != spec.lockRooms) return WallKind.DOOR_SEALED
        return WallKind.DOOR_OPEN
    }
}

/**
 * The render fact of [structures] as the mapper publishes it: their walls in order, a signal at the entrance of each of
 * [signals], and the dungeon of [dungeon] (one of [structures]) with its panel facts.
 */
internal fun structureProjection(
    vararg structures: StructureFixture,
    signals: List<StructureFixture> = emptyList(),
    dungeon: StructureFixture? = null,
    garrisonAlive: Int = 0,
    garrisonTotal: Int = 0,
): StructureRenderProjection {
    val walls = ArrayList<WallProjection>()
    val placed = structures.map { fixture ->
        val start = walls.size
        fixture.template.walls.forEachIndexed { index, wall ->
            val (ax, ay) = fixture.at(wall.ax, wall.ay)
            val (bx, by) = fixture.at(wall.bx, wall.by)
            val kind = if (wall.isDoor) fixture.doorKind(wall.door) else WallKind.SOLID
            walls += WallProjection(fixture.id or index.toLong(), kind, ax, ay, bx, by, wall.halfThickness, wall.door)
        }
        val turned = fixture.rotation and 1 == 1
        StructureProjection(
            id = fixture.id,
            template = fixture.template.id,
            rotation = fixture.rotation,
            x = fixture.x,
            y = fixture.y,
            halfWidth = if (turned) fixture.template.halfHeight else fixture.template.halfWidth,
            halfHeight = if (turned) fixture.template.halfWidth else fixture.template.halfHeight,
            wallStart = start,
            wallEnd = walls.size,
            spent = fixture.spent,
            clearedRooms = if (fixture.spent) (1 shl fixture.template.rooms.size) - 1 else fixture.clearedRooms,
            engagedRoom = fixture.engagedRoom,
        )
    }
    val held = dungeon?.let { fixture ->
        val counted = fixture.template.roomsToClearMask
        DungeonProjection(
            structureId = fixture.id,
            template = fixture.template.id,
            roomsToClear = counted.countOneBits(),
            roomsCleared = (fixture.clearedRooms and counted).countOneBits(),
            engagedRoom = fixture.engagedRoom,
            garrisonAlive = garrisonAlive,
            garrisonTotal = garrisonTotal,
            panel = !fixture.template.secret,
        )
    }
    return StructureRenderProjection(
        structures = placed.toImmutableList(),
        walls = walls.toImmutableList(),
        signals = signals.map { fixture ->
            val (x, y) = fixture.entrance()
            StructureSignalProjection(fixture.id, fixture.template.id, x, y)
        }.toImmutableList(),
        dungeon = held,
    )
}
