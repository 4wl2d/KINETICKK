// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** One capsule wall from ([ax], [ay]) to ([bx], [by]); [door] is its door index, or -1 for a solid wall. */
data class WallProjection(
    val id: Long,
    val kind: WallKind,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val halfThickness: Float,
    val door: Int,
)

/** A placed structure; its walls are [StructureRenderProjection.walls] from [wallStart] until [wallEnd]. */
data class StructureProjection(
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
    /** Room bits cleared (every room bit once spent) and the engaged room, or -1. */
    val clearedRooms: Int,
    val engagedRoom: Int,
)

/** A structure's entrance, shown as an edge marker and on the minimap within reach. */
data class StructureSignalProjection(val id: Long, val template: StructureTemplateId, val x: Float, val y: Float)

/** The dungeon in progress; [panel] is false for secret templates (no trial panel). */
data class DungeonProjection(
    val structureId: Long,
    val template: StructureTemplateId,
    val roomsToClear: Int,
    val roomsCleared: Int,
    val engagedRoom: Int,
    val garrisonAlive: Int,
    val garrisonTotal: Int,
    val panel: Boolean,
)

/** Structures around the Core; [EMPTY] when there are none. */
data class StructureRenderProjection(
    val structures: ImmutableList<StructureProjection> = immutableListOf(),
    val walls: ImmutableList<WallProjection> = immutableListOf(),
    val signals: ImmutableList<StructureSignalProjection> = immutableListOf(),
    val dungeon: DungeonProjection? = null,
) {
    companion object {
        val EMPTY: StructureRenderProjection = StructureRenderProjection()
    }
}
