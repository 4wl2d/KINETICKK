// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.StructureSignal
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection
import kinetickk.ball.gameplay.nucleus.render.StructureSignalProjection
import kinetickk.ball.gameplay.nucleus.render.WallProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapToImmutableList

/**
 * Structures, walls, signals and the dungeon as presentation draws them: the window's structures with their room
 * progress (cleared rooms and the sealed room from the dungeon of the same structure, every room once spent), every
 * wall with its kind, the entrances of unspent structures within signal range, and the dungeon in progress. A run with
 * none of them publishes the shared [StructureRenderProjection.EMPTY]. The same window, dungeon and spent list as the
 * [identitySource] keep [previous] whole when the dungeon reads the same; otherwise each list whose content matches
 * (raw float bits) keeps its previous instance. A pure read: nothing here writes state.
 */
internal fun MutableGameState.structureRenderProjection(
    previous: StructureRenderProjection?,
    identitySource: MutableGameState?,
): StructureRenderProjection {
    val field = structureField
    val current = dungeon
    if (field.structures.isEmpty() && field.signals.isEmpty() && current == null) return StructureRenderProjection.EMPTY
    val dungeonProjection = current?.let { dungeonProjection(it, previous?.dungeon) }
    if (previous != null && identitySource != null && field === identitySource.structureField &&
        current === identitySource.dungeon && spentStructureIds === identitySource.spentStructureIds &&
        dungeonProjection === previous.dungeon
    ) {
        return previous
    }
    val structures = structureProjections(field.structures, current, previous?.structures)
    val walls = wallProjections(field.walls, previous?.walls)
    val signals = signalProjections(field.signals, previous?.signals)
    if (previous != null && structures === previous.structures && walls === previous.walls && signals === previous.signals &&
        dungeonProjection === previous.dungeon
    ) {
        return previous
    }
    return StructureRenderProjection(structures, walls, signals, dungeonProjection)
}

/**
 * The dungeon's panel facts: rooms that count (garrisoned rooms and the final room) and how many of them are clear,
 * the sealed room, the garrison members still alive out of those spawned, and whether a panel shows (not for secret
 * templates). An equal projection keeps [previous].
 */
private fun MutableGameState.dungeonProjection(current: DungeonState, previous: DungeonProjection?): DungeonProjection {
    val template = content.structures.template(current.template)
    val counted = template.roomsToClearMask
    val ids = current.garrisonIds
    var alive = 0
    for (index in ids.indices) if (enemyById(ids[index])?.alive == true) alive++
    val projection = DungeonProjection(
        structureId = current.structureId,
        template = current.template,
        roomsToClear = counted.countOneBits(),
        roomsCleared = (current.clearedRooms and counted).countOneBits(),
        engagedRoom = current.engagedRoom,
        garrisonAlive = alive,
        garrisonTotal = current.garrisonTotal,
        panel = !template.secret,
    )
    return if (projection == previous) previous else projection
}

private fun MutableGameState.structureProjections(
    placed: List<PlacedStructure>,
    current: DungeonState?,
    reusable: ImmutableList<StructureProjection>?,
): ImmutableList<StructureProjection> {
    if (reusable != null && reusable.size == placed.size) {
        var same = true
        for (index in placed.indices) {
            val structure = placed[index]
            if (!reusable[index].matches(structure, clearedRooms(structure, current), engagedRoom(structure, current))) {
                same = false
                break
            }
        }
        if (same) return reusable
    }
    return placed.mapToImmutableList { structure ->
        StructureProjection(
            id = structure.id,
            template = structure.template,
            rotation = structure.rotation,
            x = structure.x,
            y = structure.y,
            halfWidth = structure.halfWidth,
            halfHeight = structure.halfHeight,
            wallStart = structure.wallStart,
            wallEnd = structure.wallEnd,
            spent = structure.spent,
            clearedRooms = clearedRooms(structure, current),
            engagedRoom = engagedRoom(structure, current),
        )
    }
}

/** Every room bit of a spent structure, the dungeon's cleared rooms for its own structure, none otherwise. */
private fun MutableGameState.clearedRooms(structure: PlacedStructure, current: DungeonState?): Int = when {
    structure.spent -> (1 shl content.structures.template(structure.template).rooms.size) - 1
    current != null && current.structureId == structure.id -> current.clearedRooms
    else -> 0
}

private fun engagedRoom(structure: PlacedStructure, current: DungeonState?): Int =
    if (current != null && current.structureId == structure.id) current.engagedRoom else -1

private fun StructureProjection.matches(structure: PlacedStructure, clearedRooms: Int, engagedRoom: Int): Boolean =
    id == structure.id &&
        template == structure.template &&
        rotation == structure.rotation &&
        x.sameBitsAs(structure.x) &&
        y.sameBitsAs(structure.y) &&
        halfWidth.sameBitsAs(structure.halfWidth) &&
        halfHeight.sameBitsAs(structure.halfHeight) &&
        wallStart == structure.wallStart &&
        wallEnd == structure.wallEnd &&
        spent == structure.spent &&
        this.clearedRooms == clearedRooms &&
        this.engagedRoom == engagedRoom

private fun wallProjections(walls: List<Wall>, reusable: ImmutableList<WallProjection>?): ImmutableList<WallProjection> {
    if (reusable != null && reusable.size == walls.size) {
        var same = true
        for (index in walls.indices) {
            if (!reusable[index].matches(walls[index])) {
                same = false
                break
            }
        }
        if (same) return reusable
    }
    return walls.mapToImmutableList { wall ->
        WallProjection(wall.id, wall.kind, wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness, wall.door)
    }
}

private fun WallProjection.matches(wall: Wall): Boolean =
    id == wall.id &&
        kind == wall.kind &&
        ax.sameBitsAs(wall.ax) &&
        ay.sameBitsAs(wall.ay) &&
        bx.sameBitsAs(wall.bx) &&
        by.sameBitsAs(wall.by) &&
        halfThickness.sameBitsAs(wall.halfThickness) &&
        door == wall.door

/**
 * The entrances of the window's signals whose structure is not spent: the window keeps its signals until it is rebuilt,
 * so a structure cleared or abandoned since then drops out here.
 */
private fun MutableGameState.signalProjections(
    signals: List<StructureSignal>,
    reusable: ImmutableList<StructureSignalProjection>?,
): ImmutableList<StructureSignalProjection> {
    var kept = 0
    var same = reusable != null
    for (index in signals.indices) {
        val signal = signals[index]
        if (isSpent(signal.id)) continue
        if (same && (kept >= reusable!!.size || !reusable[kept].matches(signal))) same = false
        kept++
    }
    if (same && kept == reusable!!.size) return reusable
    if (kept == signals.size) return signals.mapToImmutableList { it.projected() }
    return signals.filter { !isSpent(it.id) }.mapToImmutableList { it.projected() }
}

private fun StructureSignal.projected(): StructureSignalProjection = StructureSignalProjection(id, template, entranceX, entranceY)

private fun StructureSignalProjection.matches(signal: StructureSignal): Boolean =
    id == signal.id && template == signal.template && x.sameBitsAs(signal.entranceX) && y.sameBitsAs(signal.entranceY)
