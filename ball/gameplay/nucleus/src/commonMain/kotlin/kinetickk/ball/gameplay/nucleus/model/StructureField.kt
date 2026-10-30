// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

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

/** A structure placed in the window: run-local centre, rotated extents and its slice of [StructureField.walls]. */
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
    val spent: Boolean,
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
    /** At most [ContentBounds.MAX_STRUCTURE_GARRISON]. */
    val garrisonIds: ImmutableList<Int> = immutableListOf(),
    val garrisonTotal: Int = 0,
    /** A garrison fight ever started (leaving marks it spent). */
    val engaged: Boolean = false,
    val enteredAt: Float,
) {
    fun shifted(shiftX: Float, shiftY: Float): DungeonState = copy(x = x - shiftX, y = y - shiftY)
}
