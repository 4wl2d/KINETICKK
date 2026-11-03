// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureWallSpec
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.StructureSignal
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/*
 * The structure window, the single writer of MutableGameState.structureField. It holds the unspent structures whose
 * footprint meets the obstacle window, their capsule walls in run-local coordinates with door kinds from the dungeon
 * state, and the signals of the nearest unspent structures around the Core. It is rebuilt only when the obstacle
 * window moves to another chunk, the world layer changes or the Architect collapses the structures; a door change
 * replaces one structure's wall slice and a spent structure leaves at once (removeStructureFromWindow), so neither a
 * window move nor a layer round trip can bring it back. Disabled content keeps the empty window a run starts with.
 *
 * Skipped placements: after a layer switch, those whose footprint inflated by the obstacle clearance meets the arrival
 * keep-out of the same layer (stateless, so no arrival bookkeeping exists).
 */

/** Spent-list flag of a cleared or abandoned structure. */
internal const val SPENT_FLAG: Long = 1L

/** A structure id without its spent-list flag bits (structure ids keep their low byte clear). */
private const val STRUCTURE_ID_MASK: Long = -0x100L

/** Rebuilds the structure window around the absolute chunk when its key (chunk, layer, collapse) changed. */
internal fun MutableGameState.refreshStructureField(chunkX: Int, chunkY: Int) {
    if (!content.structures.enabled) return
    val collapsed = bossSpawned
    if (structureField.isCurrent(chunkX, chunkY, worldLayer.ordinal, collapsed)) return
    structureField = buildStructureField(chunkX, chunkY, collapsed)
}

/**
 * The structure window centred on the absolute chunk: the placements of the at most 3 × 3 cells the obstacle window
 * covers whose footprint meets it (row-major), their walls with door kinds, and the signals. Empty once [collapsed].
 */
private fun MutableGameState.buildStructureField(chunkX: Int, chunkY: Int, collapsed: Boolean): StructureField {
    val layer = worldLayer.ordinal
    if (collapsed) return StructureField.of(chunkX, chunkY, layer, true, emptyList(), emptyList(), emptyList())
    val policy = content.structures
    val tuning = content.obstacles
    val chunk = tuning.chunkSize.toDouble()
    val half = tuning.windowChunks / 2
    val minX = (chunkX - half) * chunk
    val maxX = (chunkX + half + 1) * chunk
    val minY = (chunkY - half) * chunk
    val maxY = (chunkY + half + 1) * chunk
    val size = policy.cellSize.toDouble()
    val firstCellX = floor(minX / size).toInt()
    val firstCellY = floor(minY / size).toInt()
    val endCellX = ceil(maxX / size).toInt()
    val endCellY = ceil(maxY / size).toInt()
    // Bootstrap validation guarantees this; content built past it (a test's huge chunks) fails here, not by growing.
    check(endCellX - firstCellX <= MAX_WINDOW_CELLS && endCellY - firstCellY <= MAX_WINDOW_CELLS) {
        "The obstacle window must span at most three structure cells per axis"
    }
    val structures = ArrayList<PlacedStructure>()
    val walls = ArrayList<Wall>()
    for (cellY in firstCellY until endCellY) {
        for (cellX in firstCellX until endCellX) {
            val placement = availableStructure(cellX, cellY) ?: continue
            if (isSpent(placement.id)) continue
            if (placement.absoluteX + placement.halfWidth <= minX || placement.absoluteX - placement.halfWidth >= maxX ||
                placement.absoluteY + placement.halfHeight <= minY || placement.absoluteY - placement.halfHeight >= maxY
            ) {
                continue
            }
            val template = policy.template(placement.template)
            val start = walls.size
            val specs = template.walls
            for (index in specs.indices) walls += placedWall(placement, template, index, specs[index])
            structures += PlacedStructure(
                id = placement.id,
                template = placement.template,
                rotation = placement.rotation,
                x = (placement.absoluteX - worldOriginX).toFloat(),
                y = (placement.absoluteY - worldOriginY).toFloat(),
                halfWidth = placement.halfWidth,
                halfHeight = placement.halfHeight,
                wallStart = start,
                wallEnd = walls.size,
            )
        }
    }
    return StructureField.of(chunkX, chunkY, layer, false, structures, walls, structureSignals())
}

/** The placement in the cell unless the arrival keep-out of the current layer covers it. */
private fun MutableGameState.availableStructure(cellX: Int, cellY: Int): StructurePlacement? {
    val placement = structurePlacement(cellX, cellY) ?: return null
    val keepOut = worldKeepOut
    if (keepOut != null) {
        val nearestX = keepOut.x.coerceIn(placement.absoluteX - placement.halfWidth, placement.absoluteX + placement.halfWidth)
        val nearestY = keepOut.y.coerceIn(placement.absoluteY - placement.halfHeight, placement.absoluteY + placement.halfHeight)
        if (keepOutSuppresses(nearestX, nearestY, content.structures.obstacleClearance)) return null
    }
    return placement
}

/** Template wall [index] turned and moved to the placement, run-local, with its door kind. */
private fun MutableGameState.placedWall(
    placement: StructurePlacement,
    template: StructureTemplate,
    index: Int,
    spec: StructureWallSpec,
): Wall {
    val rotation = placement.rotation
    val kind = if (spec.door < 0) WallKind.SOLID else doorKind(placement.id, template, spec.door)
    return Wall(
        id = placement.id or index.toLong(),
        kind = kind,
        ax = (placement.absoluteX + rotateQuarterX(spec.ax, spec.ay, rotation) - worldOriginX).toFloat(),
        ay = (placement.absoluteY + rotateQuarterY(spec.ax, spec.ay, rotation) - worldOriginY).toFloat(),
        bx = (placement.absoluteX + rotateQuarterX(spec.bx, spec.by, rotation) - worldOriginX).toFloat(),
        by = (placement.absoluteY + rotateQuarterY(spec.bx, spec.by, rotation) - worldOriginY).toFloat(),
        halfThickness = spec.halfThickness,
        door = spec.door,
    )
}

/**
 * A door is sealed while the dungeon of this structure holds its room engaged, or while its lock waits on a room that
 * is not cleared yet; open in every other case.
 */
private fun MutableGameState.doorKind(structureId: Long, template: StructureTemplate, door: Int): WallKind {
    val dungeon = dungeon?.takeIf { it.structureId == structureId }
    val spec = template.doors[door]
    val engaged = dungeon?.engagedRoom ?: -1
    if (engaged >= 0 && (spec.roomA == engaged || spec.roomB == engaged)) return WallKind.DOOR_SEALED
    val lock = spec.lockRooms
    if (lock != 0 && ((dungeon?.clearedRooms ?: 0) and lock) != lock) return WallKind.DOOR_SEALED
    return WallKind.DOOR_OPEN
}

/**
 * Replaces the wall slice of the windowed structure [structureId] after a seal or a clear, keeping every other wall and
 * the window identity when nothing changed. A structure outside the window waits for the next rebuild, which reads the
 * same dungeon.
 */
internal fun MutableGameState.applyStructureDoors(structureId: Long) {
    val field = structureField
    val index = field.indexOf(structureId)
    if (index < 0) return
    val structure = field.structures[index]
    val template = content.structures.template(structure.template)
    var changed = false
    val slice = ArrayList<Wall>(structure.wallEnd - structure.wallStart)
    for (wallIndex in structure.wallStart until structure.wallEnd) {
        val wall = field.walls[wallIndex]
        val kind = if (wall.door < 0) wall.kind else doorKind(structureId, template, wall.door)
        if (kind == wall.kind) {
            slice += wall
        } else {
            changed = true
            slice += wall.copy(kind = kind)
        }
    }
    if (!changed) return
    structureField = field.withStructure(index, structure, slice)
}

/**
 * Takes the windowed structure [structureId] out of the window with its walls and signal (it was just spent) and
 * returns it, or null when the window does not hold it (another layer's window, or already gone).
 */
internal fun MutableGameState.removeStructureFromWindow(structureId: Long): PlacedStructure? {
    val field = structures.structureField
    val index = field.indexOf(structureId)
    if (index < 0) return null
    structures.structureField = field.withoutStructure(index)
    return field.structures[index]
}

/** Rebuilds the window on its current key: after the spent set fills, every placement counts as spent. */
internal fun MutableGameState.rebuildStructureField() {
    if (!content.structures.enabled) return
    val field = structures.structureField
    if (field === StructureField.EMPTY) return
    structures.structureField = buildStructureField(field.centerChunkX, field.centerChunkY, field.bossCollapsed)
}

/**
 * The nearest unspent structures of the 5 × 5 cells around the Core's cell whose entrance (the outer door's centre;
 * a template without one, its centre) lies within the signal range of the Core, nearest first, at most
 * [ContentBounds.MAX_STRUCTURE_SIGNALS].
 */
private fun MutableGameState.structureSignals(): List<StructureSignal> {
    val policy = content.structures
    val coreAbsoluteX = worldOriginX + coreX
    val coreAbsoluteY = worldOriginY + coreY
    val centreX = structureCell(coreAbsoluteX)
    val centreY = structureCell(coreAbsoluteY)
    val range = policy.signalRange.toDouble()
    val signals = ArrayList<StructureSignal>()
    val distances = ArrayList<Double>()
    for (cellY in centreY - SIGNAL_CELL_REACH..centreY + SIGNAL_CELL_REACH) {
        for (cellX in centreX - SIGNAL_CELL_REACH..centreX + SIGNAL_CELL_REACH) {
            val placement = availableStructure(cellX, cellY) ?: continue
            if (isSpent(placement.id)) continue
            val template = policy.template(placement.template)
            val entrance = if (template.entranceDoor >= 0) template.doors[template.entranceDoor] else null
            val localX = when {
                entrance == null -> 0f
                entrance.axis == WallAxis.HORIZONTAL -> entrance.center
                else -> entrance.line
            }
            val localY = when {
                entrance == null -> 0f
                entrance.axis == WallAxis.HORIZONTAL -> entrance.line
                else -> entrance.center
            }
            val entranceX = placement.absoluteX + rotateQuarterX(localX, localY, placement.rotation)
            val entranceY = placement.absoluteY + rotateQuarterY(localX, localY, placement.rotation)
            val distance = hypot(entranceX - coreAbsoluteX, entranceY - coreAbsoluteY)
            if (distance > range) continue
            var at = distances.size
            while (at > 0 && distances[at - 1] > distance) at--
            distances.add(at, distance)
            signals.add(
                at,
                StructureSignal(
                    id = placement.id,
                    template = placement.template,
                    entranceX = (entranceX - worldOriginX).toFloat(),
                    entranceY = (entranceY - worldOriginY).toFloat(),
                    x = (placement.absoluteX - worldOriginX).toFloat(),
                    y = (placement.absoluteY - worldOriginY).toFloat(),
                ),
            )
        }
    }
    return if (signals.size > ContentBounds.MAX_STRUCTURE_SIGNALS) signals.subList(0, ContentBounds.MAX_STRUCTURE_SIGNALS) else signals
}

/** Structure cells the obstacle window spans per axis at most (validateStructurePolicy). */
private const val MAX_WINDOW_CELLS: Int = 3

/** Cells on each side of the Core's cell a signal may come from (the range reaches at most two cells). */
private const val SIGNAL_CELL_REACH: Int = 2

// ---------------------------------------------------------------------------------------------
// Spent list: cleared or abandoned structures, oldest first, value-only.

/**
 * Records [structureId] with [SPENT_FLAG] for the rest of the run. An id already recorded, or a list already at
 * [ContentBounds.MAX_SPENT_STRUCTURES] (which counts every structure as spent), keeps the list as it is; nothing is
 * ever forgotten, so a structure never arms twice in a run.
 */
internal fun MutableGameState.markSpent(structureId: Long) {
    if (isSpent(structureId)) return
    val current = structures.spentStructureIds
    val next = ArrayList<Long>(current.size + 1)
    next += current
    next += (structureId and STRUCTURE_ID_MASK) or SPENT_FLAG
    structures.spentStructureIds = next
}

/** True when the run spent [structureId] (cleared or abandoned), or the spent list is full (everything is spent). */
internal fun MutableGameState.isSpent(structureId: Long): Boolean {
    if (spentStructureSetFull()) return true
    val entry = (structureId and STRUCTURE_ID_MASK) or SPENT_FLAG
    val spent = structures.spentStructureIds
    for (index in spent.indices) if (spent[index] == entry) return true
    return false
}

/** The conservative full-set rule: at [ContentBounds.MAX_SPENT_STRUCTURES] entries every structure counts as spent. */
internal fun MutableGameState.spentStructureSetFull(): Boolean =
    structures.spentStructureIds.size >= ContentBounds.MAX_SPENT_STRUCTURES

// ---------------------------------------------------------------------------------------------
// Run lifecycle.

/** A new run: no structure window, no dungeon, nothing spent (the obstacle reset builds the window). */
internal fun MutableGameState.resetStructures() {
    structureField = StructureField.EMPTY
    dungeon = null
    spentStructureIds = emptyList()
    lastAnnouncedStructureId = 0L
}

/** Positions follow a rebase; ids and absolute cells stay. */
internal fun MutableGameState.rebaseStructures(dx: Float, dy: Float) {
    structureField = structureField.shifted(dx, dy)
    dungeon = dungeon?.shifted(dx, dy)
}
