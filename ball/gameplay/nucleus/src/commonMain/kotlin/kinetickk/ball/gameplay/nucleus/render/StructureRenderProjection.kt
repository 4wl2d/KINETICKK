// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.DungeonHazardKind
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

/**
 * A placed structure; its walls are [StructureRenderProjection.walls] from [wallStart] until [wallEnd]; [chestCharges]
 * is the Totem chest its final room promises (0: none).
 */
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
    /** Room bits cleared and the engaged room, or -1 (a spent structure has left the window). */
    val clearedRooms: Int,
    val engagedRoom: Int,
    val chestCharges: Int = 0,
)

/** A structure's entrance, shown as an edge marker and on the minimap within reach. */
data class StructureSignalProjection(val id: Long, val template: StructureTemplateId, val x: Float, val y: Float)

/**
 * The dungeon in progress: its structure, the sealed room or -1, [panel], false for secret templates (no trial panel),
 * the Totem chest charges its final room pays (0: none) and the sealed room's fight.
 */
data class DungeonProjection(
    val structureId: Long,
    val engagedRoom: Int,
    val panel: Boolean,
    val chestCharges: Int = 0,
    /** Run time the sealed room sealed (−1 without one): its doors slam shut from then. */
    val sealedAt: Float = -1f,
    /** The sealed room's waves and how many of them landed. */
    val waves: Int = 0,
    val wavesLanded: Int = 0,
    /** Enemies the sealed room's encounter owns that live. */
    val liveGarrison: Int = 0,
    /** The sealed room's guardian while it lives (its enemy id), or -1. */
    val guardianId: Int = -1,
    /** Run time the incoming wave lands (−1: none on its way) and where its members land. */
    val incomingAt: Float = -1f,
    val breaches: ImmutableList<DungeonBreachProjection> = immutableListOf(),
    /** The sealed room's telegraphed hazards (the latest cycle's). */
    val hazards: ImmutableList<DungeonHazardProjection> = immutableListOf(),
)

/** Where a member of the incoming wave lands, its radius, and whether it is the room's guardian. */
data class DungeonBreachProjection(val x: Float, val y: Float, val radius: Float, val guardian: Boolean)

/**
 * A sealed room's hazard: a VENT disc of [radius] at ([ax], [ay]) or a LANCE from ([ax], [ay]) to ([bx], [by]) of half
 * width [radius]; it glows from [armedAt] and strikes at [strikesAt].
 */
data class DungeonHazardProjection(
    val kind: DungeonHazardKind,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val radius: Float,
    val armedAt: Float,
    val strikesAt: Float,
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
