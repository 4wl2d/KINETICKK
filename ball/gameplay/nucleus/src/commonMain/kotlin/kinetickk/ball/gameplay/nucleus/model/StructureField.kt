// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.render.WallKind

/** One capsule wall: the segment ([ax], [ay]) → ([bx], [by]) inflated by [halfThickness]; [door] is its door index or −1. */
internal data class Wall(
    val id: Long,
    val kind: WallKind,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val halfThickness: Float,
    val door: Int = -1,
)

/**
 * A structure placed in the window: run-local centre, rotated extents, its slice of [StructureField.walls] and the
 * Totem chest charges its final room promises (0: none; a pure hash of seed, layer and cell).
 */
internal data class PlacedStructure(
    val id: Long,
    val template: StructureTemplateId,
    val rotation: Int,
    val x: Float,
    val y: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val wallStart: Int,
    val wallEnd: Int,
    val chestCharges: Int = 0,
) {
    fun shifted(shiftX: Float, shiftY: Float): PlacedStructure = copy(x = x - shiftX, y = y - shiftY)
}

/** An unspent structure within signal range, announced to the HUD: its entrance and centre, run-local. */
internal data class StructureSignal(
    val id: Long,
    val template: StructureTemplateId,
    val entranceX: Float,
    val entranceY: Float,
    val x: Float,
    val y: Float,
) {
    fun shifted(shiftX: Float, shiftY: Float): StructureSignal =
        copy(entranceX = entranceX - shiftX, entranceY = entranceY - shiftY, x = x - shiftX, y = y - shiftY)
}

/** Immutable window value; reduction siblings share it; every change builds a new one. */
internal class StructureField private constructor(
    val centerChunkX: Int,
    val centerChunkY: Int,
    val layer: Int,
    val bossCollapsed: Boolean,
    /** At most [ContentBounds.MAX_STRUCTURES_IN_WINDOW], row-major cell order. */
    val structures: List<PlacedStructure>,
    /** At most [ContentBounds.MAX_STRUCTURE_WALLS], sliced per structure. */
    val walls: List<Wall>,
    /** At most [ContentBounds.MAX_STRUCTURE_SIGNALS], nearest first. */
    val signals: List<StructureSignal>,
) {
    fun isCurrent(chunkX: Int, chunkY: Int, layer: Int, bossCollapsed: Boolean): Boolean =
        centerChunkX == chunkX && centerChunkY == chunkY && this.layer == layer && this.bossCollapsed == bossCollapsed

    /** The same window with every position moved by −([shiftX], [shiftY]). */
    fun shifted(shiftX: Float, shiftY: Float): StructureField {
        if (structures.isEmpty() && signals.isEmpty()) return this
        return StructureField(
            centerChunkX, centerChunkY, layer, bossCollapsed,
            structures.map { it.shifted(shiftX, shiftY) },
            walls.map { wall ->
                wall.copy(ax = wall.ax - shiftX, ay = wall.ay - shiftY, bx = wall.bx - shiftX, by = wall.by - shiftY)
            },
            signals.map { it.shifted(shiftX, shiftY) },
        )
    }

    /** Index of the placed structure with [structureId], or −1 (a linear scan over at most nine). */
    fun indexOf(structureId: Long): Int {
        for (index in structures.indices) if (structures[index].id == structureId) return index
        return -1
    }

    /** The same window with structure [index] replaced and its wall slice replaced (same length). */
    fun withStructure(index: Int, structure: PlacedStructure, slice: List<Wall>): StructureField {
        val current = structures[index]
        require(structure.wallStart == current.wallStart && structure.wallEnd == current.wallEnd) {
            "A replaced structure keeps its wall slice"
        }
        require(slice.size == current.wallEnd - current.wallStart) { "A replaced wall slice keeps its length" }
        val nextWalls = walls.toMutableList()
        for (offset in slice.indices) nextWalls[current.wallStart + offset] = slice[offset]
        val nextStructures = structures.toMutableList()
        nextStructures[index] = structure
        return StructureField(centerChunkX, centerChunkY, layer, bossCollapsed, nextStructures, nextWalls, signals)
    }

    /**
     * The same window key (chunk, layer, collapse) without structure [index]: its wall slice and its signal leave and
     * the slices after it shift down, so the next refresh rebuilds only when the key changes.
     */
    fun withoutStructure(index: Int): StructureField {
        val removed = structures[index]
        val length = removed.wallEnd - removed.wallStart
        val nextStructures = ArrayList<PlacedStructure>(structures.size - 1)
        for (at in structures.indices) {
            val structure = structures[at]
            if (at < index) nextStructures += structure
            if (at > index) nextStructures += structure.copy(wallStart = structure.wallStart - length, wallEnd = structure.wallEnd - length)
        }
        val nextWalls = ArrayList<Wall>(walls.size - length)
        for (at in walls.indices) if (at < removed.wallStart || at >= removed.wallEnd) nextWalls += walls[at]
        val nextSignals = ArrayList<StructureSignal>(signals.size)
        for (at in signals.indices) if (signals[at].id != removed.id) nextSignals += signals[at]
        return StructureField(centerChunkX, centerChunkY, layer, bossCollapsed, nextStructures, nextWalls, nextSignals)
    }

    companion object {
        val EMPTY: StructureField = StructureField(Int.MIN_VALUE, Int.MIN_VALUE, -1, false, emptyList(), emptyList(), emptyList())

        fun of(
            centerChunkX: Int,
            centerChunkY: Int,
            layer: Int,
            bossCollapsed: Boolean,
            structures: List<PlacedStructure>,
            walls: List<Wall>,
            signals: List<StructureSignal>,
        ): StructureField {
            require(structures.size <= ContentBounds.MAX_STRUCTURES_IN_WINDOW) {
                "A structure window holds at most ${ContentBounds.MAX_STRUCTURES_IN_WINDOW} structures"
            }
            require(walls.size <= ContentBounds.MAX_STRUCTURE_WALLS) {
                "A structure window holds at most ${ContentBounds.MAX_STRUCTURE_WALLS} walls"
            }
            require(signals.size <= ContentBounds.MAX_STRUCTURE_SIGNALS) {
                "A structure window announces at most ${ContentBounds.MAX_STRUCTURE_SIGNALS} signals"
            }
            for (structure in structures) {
                require(structure.wallStart in 0..structure.wallEnd && structure.wallEnd <= walls.size) {
                    "A structure's wall slice lies inside the window's walls"
                }
            }
            return StructureField(
                centerChunkX, centerChunkY, layer, bossCollapsed, structures.toList(), walls.toList(), signals.toList(),
            )
        }
    }
}

/** The structure the Core is inside (or was, within the leave radius). Value-only. */
internal data class DungeonState(
    val structureId: Long,
    val template: StructureTemplateId,
    val rotation: Int,
    /** Run-local centre (rebased). */
    val x: Float,
    val y: Float,
    /** Bit per room. */
    val clearedRooms: Int = 0,
    /** −1: no sealed room. */
    val engagedRoom: Int = -1,
    /** A garrison fight ever started (leaving marks it spent and it disintegrates). */
    val engaged: Boolean = false,
    /** Totem chest charges the final room pays (copied from the placed structure at entry; 0: none). */
    val chestCharges: Int = 0,
) {
    fun shifted(shiftX: Float, shiftY: Float): DungeonState = copy(x = x - shiftX, y = y - shiftY)
}

/** A dungeon's Totem chest waiting in realspace. Value-only: every change replaces the list that holds it. */
internal data class TotemChest(
    /** The structure that paid it (unique per run: a structure finishes once). */
    val structureId: Long,
    /** Run-local centre (rebased). */
    val x: Float,
    val y: Float,
    /** Charges left, 1..[ContentBounds.MAX_TOTEM_CHEST_CHARGES]. */
    val charges: Int,
    /** Run time it materialised. */
    val bornAt: Float,
    /** Run time from which contact opens a charge (the materialise delay, then each re-arm). */
    val armedAt: Float,
) {
    fun shifted(shiftX: Float, shiftY: Float): TotemChest = copy(x = x - shiftX, y = y - shiftY)
}
