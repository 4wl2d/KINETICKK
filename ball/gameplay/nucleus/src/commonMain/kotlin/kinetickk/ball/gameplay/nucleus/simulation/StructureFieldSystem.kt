// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/*
 * The structure window, the single writer of MutableGameState.structureField. It holds the structures whose footprint
 * meets the obstacle window, their capsule walls in run-local coordinates with door kinds from the dungeon state and
 * the spent list, and the signals of the nearest structures around the Core. It is rebuilt only when the obstacle
 * window moves to another chunk, the world layer changes or the Architect collapses the structures; a door change
 * replaces one structure's wall slice. Disabled content keeps the empty window a run starts with.
 *
 * Skipped placements: collapsed ones (spent list) and, after a layer switch, those whose footprint inflated by the
 * obstacle clearance meets the arrival keep-out of the same layer (stateless, so no arrival bookkeeping exists).
 */

/** Spent-list flag of a cleared or abandoned structure. */
internal const val SPENT_FLAG: Long = 1L

/** Spent-list flag of a structure taken out of the window for good. */
internal const val COLLAPSED_FLAG: Long = 2L

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
internal fun MutableGameState.buildStructureField(chunkX: Int, chunkY: Int, collapsed: Boolean): StructureField {
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
            if (placement.absoluteX + placement.halfWidth <= minX || placement.absoluteX - placement.halfWidth >= maxX ||
                placement.absoluteY + placement.halfHeight <= minY || placement.absoluteY - placement.halfHeight >= maxY
            ) {
                continue
            }
            val template = policy.template(placement.template)
            val spent = isSpent(placement.id)
            val start = walls.size
            val specs = template.walls
            for (index in specs.indices) walls += placedWall(placement, template, index, specs[index], spent)
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
                spent = spent,
            )
        }
    }
    return StructureField.of(chunkX, chunkY, layer, false, structures, walls, structureSignals())
}

/** The placement in the cell unless it collapsed or the arrival keep-out of the current layer covers it. */
private fun MutableGameState.availableStructure(cellX: Int, cellY: Int): StructurePlacement? {
    val placement = structurePlacement(cellX, cellY) ?: return null
    if (isCollapsed(placement.id)) return null
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
    spent: Boolean,
): Wall {
    val rotation = placement.rotation
    val kind = if (spec.door < 0) WallKind.SOLID else doorKind(placement.id, template, spec.door, spent)
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
 * A door of a spent structure stands open. Otherwise it is sealed while the dungeon of this structure holds its room
 * engaged, or while its lock waits on a room that is not cleared yet; open in every other case.
 */
private fun MutableGameState.doorKind(structureId: Long, template: StructureTemplate, door: Int, spent: Boolean): WallKind {
    if (spent) return WallKind.DOOR_OPEN
    val dungeon = dungeon?.takeIf { it.structureId == structureId }
    val spec = template.doors[door]
    val engaged = dungeon?.engagedRoom ?: -1
    if (engaged >= 0 && (spec.roomA == engaged || spec.roomB == engaged)) return WallKind.DOOR_SEALED
    val lock = spec.lockRooms
    if (lock != 0 && ((dungeon?.clearedRooms ?: 0) and lock) != lock) return WallKind.DOOR_SEALED
    return WallKind.DOOR_OPEN
}

/**
 * Replaces the wall slice of the windowed structure [structureId] after a seal, a clear or a spent change, keeping
 * every other wall and the window identity when nothing changed. A structure outside the window waits for the next
 * rebuild, which reads the same dungeon and spent list.
 */
internal fun MutableGameState.applyStructureDoors(structureId: Long) {
    val field = structureField
    val index = field.indexOf(structureId)
    if (index < 0) return
    val structure = field.structures[index]
    val template = content.structures.template(structure.template)
    val spent = isSpent(structureId)
    var changed = structure.spent != spent
    val slice = ArrayList<Wall>(structure.wallEnd - structure.wallStart)
    for (wallIndex in structure.wallStart until structure.wallEnd) {
        val wall = field.walls[wallIndex]
        val kind = if (wall.door < 0) wall.kind else doorKind(structureId, template, wall.door, spent)
        if (kind == wall.kind) {
            slice += wall
        } else {
            changed = true
            slice += wall.copy(kind = kind)
        }
    }
    if (!changed) return
    structureField = field.withStructure(index, structure.copy(spent = spent), slice)
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
// Spent list: cleared, abandoned or collapsed structures, oldest first, value-only.

/**
 * Records [structureId] with [flag] at the end of the spent list, replacing an earlier entry of the same structure;
 * past [ContentBounds.MAX_SPENT_STRUCTURES] entries the oldest is forgotten (that structure can arm again).
 */
internal fun MutableGameState.markSpent(structureId: Long, flag: Long) {
    val id = structureId and STRUCTURE_ID_MASK
    val current = spentStructureIds
    val next = ArrayList<Long>(current.size + 1)
    for (index in current.indices) {
        val entry = current[index]
        if (entry and STRUCTURE_ID_MASK != id) next += entry
    }
    next += id or flag
    val excess = next.size - ContentBounds.MAX_SPENT_STRUCTURES
    spentStructureIds = if (excess > 0) next.subList(excess, next.size).toList() else next
}

/** True when the spent list holds [structureId] as cleared or abandoned. */
internal fun MutableGameState.isSpent(structureId: Long): Boolean = spentEntryIs(structureId, SPENT_FLAG)

/** True when the spent list holds [structureId] as collapsed. */
internal fun MutableGameState.isCollapsed(structureId: Long): Boolean = spentEntryIs(structureId, COLLAPSED_FLAG)

private fun MutableGameState.spentEntryIs(structureId: Long, flag: Long): Boolean {
    val entry = (structureId and STRUCTURE_ID_MASK) or flag
    val spent = spentStructureIds
    for (index in spent.indices) if (spent[index] == entry) return true
    return false
}

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
