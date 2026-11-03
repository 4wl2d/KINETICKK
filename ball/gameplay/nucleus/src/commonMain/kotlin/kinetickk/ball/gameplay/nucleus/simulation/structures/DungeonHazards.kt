// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonHazard
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.RoomFight
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

/*
 * A sealed room's hazards (the template's DungeonHazardKind; the final room alternates it with the other kind). The
 * policy's first delay after the seal and then every period a cycle telegraphs its hazards, one more in the final room
 * and per the policy's Rebirth tiers: vents (the first under the Core, the rest by the room's hash) or lances across
 * the room (the first through the Core). They all strike once
 * at the end of the telegraph: a hostile blast (Dash phases and parries cancel it, like a meteor) on a Core they meet.
 * Places come from the hazard hash (run seed, structure cell, room, cycle) and the Core's position at the cycle's
 * start, never from a run stream; a cycle replaces the fight once, so a quiet step allocates nothing.
 */

/** Salt of the hazard hash, after the chest's in the structure family. */
internal const val HAZARD_SALT: Long = 0x57C0_C7E5_5EED_0006L

/** One step of the sealed room's hazards: a due cycle telegraphs, a telegraph run out strikes. */
internal fun MutableGameState.updateRoomHazards(current: DungeonState) {
    val policy = content.structures.hazards
    val template = content.structures.template(current.template)
    val primary = template.hazard
    val fight = current.fight
    if (!policy.enabled || primary == null || fight == null) return
    var next = fight
    val due = fight.sealedAt + policy.firstDelaySeconds + fight.hazardCycles * policy.periodSeconds
    if (elapsed >= due) next = hazardCycle(current, fight, template, primary)
    val hazards = next.hazards
    if (!next.hazardsStruck && hazards.isNotEmpty() && elapsed >= hazards[0].strikesAt) {
        next = next.copy(hazardsStruck = true)
        for (index in hazards.indices) strike(hazards[index], template)
    }
    if (next !== fight) dungeon = current.copy(fight = next)
}

/** The fight with cycle `hazardCycles` telegraphing from now in [current]'s sealed room. */
private fun MutableGameState.hazardCycle(current: DungeonState, fight: RoomFight, template: StructureTemplate, primary: DungeonHazardKind): RoomFight {
    val policy = content.structures.hazards
    val room = current.engagedRoom
    val spec = template.rooms[room]
    val cycle = fight.hazardCycles
    val final = room == template.finalRoom
    val kind = if (final && cycle % 2 == 1) primary.other() else primary
    val base = if (kind == DungeonHazardKind.VENT) policy.ventsPerCycle else policy.lancesPerCycle
    val count = minOf(base + (if (final) 1 else 0) + rebirthLevel / policy.extraPerTiers, ContentBounds.MAX_DUNGEON_HAZARDS)
    val random = HashRandom(
        splitMix64(
            worldHash(runSeed, worldLayer, structureCellX(current.structureId), structureCellY(current.structureId), HAZARD_SALT) xor
                (room.toLong() xor (cycle.toLong() shl 8)),
        ),
    )
    val inverse = (4 - current.rotation) and 3
    val coreLocalX = rotateQuarterX(coreX - current.x, coreY - current.y, inverse)
    val coreLocalY = rotateQuarterY(coreX - current.x, coreY - current.y, inverse)
    val strikesAt = elapsed + policy.telegraphSeconds
    val hazards = ArrayList<DungeonHazard>(count)
    for (index in 0 until count) {
        hazards += when (kind) {
            DungeonHazardKind.VENT -> {
                val inset = template.wallHalfThickness + policy.ventRadius * VENT_INSET_SHARE
                val x = if (index == 0) clampIntoSpan(coreLocalX, spec.minX + inset, spec.maxX - inset) else spanPoint(spec.minX, spec.maxX, inset, random)
                val y = if (index == 0) clampIntoSpan(coreLocalY, spec.minY + inset, spec.maxY - inset) else spanPoint(spec.minY, spec.maxY, inset, random)
                placed(current, kind, x, y, x, y, policy.ventRadius, strikesAt)
            }
            DungeonHazardKind.LANCE -> lance(current, spec, template, index, random, coreLocalX, coreLocalY, strikesAt)
        }
    }
    return fight.copy(hazardCycles = cycle + 1, hazards = hazards, hazardsStruck = false)
}

/**
 * Lance [index] of a cycle across [spec] from wall to wall: along x or y by the hash, the first through the Core's
 * template-space position, the others at a hashed offset.
 */
private fun MutableGameState.lance(
    current: DungeonState,
    spec: StructureRoom,
    template: StructureTemplate,
    index: Int,
    random: HashRandom,
    coreLocalX: Float,
    coreLocalY: Float,
    strikesAt: Float,
): DungeonHazard {
    val halfWidth = content.structures.hazards.lanceHalfWidth
    val wall = template.wallHalfThickness
    val inset = wall + halfWidth
    val alongX = random.nextFloat() < 0.5f
    return if (alongX) {
        val y = if (index == 0) clampIntoSpan(coreLocalY, spec.minY + inset, spec.maxY - inset) else spanPoint(spec.minY, spec.maxY, inset, random)
        placed(current, DungeonHazardKind.LANCE, spec.minX + wall, y, spec.maxX - wall, y, halfWidth, strikesAt)
    } else {
        val x = if (index == 0) clampIntoSpan(coreLocalX, spec.minX + inset, spec.maxX - inset) else spanPoint(spec.minX, spec.maxX, inset, random)
        placed(current, DungeonHazardKind.LANCE, x, spec.minY + wall, x, spec.maxY - wall, halfWidth, strikesAt)
    }
}

/** A hashed point of [low]..[high] inset by [inset] from both ends (the middle of a span too narrow). */
private fun spanPoint(low: Float, high: Float, inset: Float, random: HashRandom): Float {
    val from = low + inset
    val to = high - inset
    return if (from >= to) (low + high) * 0.5f else from + (to - from) * random.nextFloat()
}

/** The template-space hazard turned and placed in the world, glowing from now. */
private fun MutableGameState.placed(
    current: DungeonState,
    kind: DungeonHazardKind,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    radius: Float,
    strikesAt: Float,
): DungeonHazard = DungeonHazard(
    kind,
    current.x + rotateQuarterX(ax, ay, current.rotation), current.y + rotateQuarterY(ax, ay, current.rotation),
    current.x + rotateQuarterX(bx, by, current.rotation), current.y + rotateQuarterY(bx, by, current.rotation),
    radius, armedAt = elapsed, strikesAt = strikesAt,
)

/** [hazard] strikes: its element's flash, and a hostile blast on a running Core it meets. */
private fun MutableGameState.strike(hazard: DungeonHazard, template: StructureTemplate) {
    val tone = template.element?.let(FxColorCode::elementTone) ?: 4
    val midX = (hazard.ax + hazard.bx) * 0.5f
    val midY = (hazard.ay + hazard.by) * 0.5f
    when (hazard.kind) {
        DungeonHazardKind.VENT -> {
            shockwave(hazard.ax, hazard.ay, STRIKE_RING_LIFE, hazard.radius * STRIKE_RING_REACH, tone)
            burst(hazard.ax, hazard.ay, STRIKE_BURST, tone)
        }
        DungeonHazardKind.LANCE -> {
            burst(hazard.ax, hazard.ay, STRIKE_BURST / 2, tone)
            burst(midX, midY, STRIKE_BURST, tone)
            burst(hazard.bx, hazard.by, STRIKE_BURST / 2, tone)
        }
    }
    if (phase != GamePhase.RUNNING || !hazardMeetsCore(hazard)) return
    hurtByHostileBlast(midX, midY, hostileDamage(null, content.structures.hazards.coreDamage), IncomingDamage.HIT)
}

/** True when the Core's disc meets [hazard]'s disc or line. */
internal fun MutableGameState.hazardMeetsCore(hazard: DungeonHazard): Boolean {
    val reach = hazard.radius + coreRadius
    val along = segmentNearest(coreX, coreY, hazard.ax, hazard.ay, hazard.bx, hazard.by)
    val x = hazard.ax + (hazard.bx - hazard.ax) * along
    val y = hazard.ay + (hazard.by - hazard.ay) * along
    return distanceSquared(coreX, coreY, x, y) <= reach * reach
}

private fun DungeonHazardKind.other(): DungeonHazardKind =
    if (this == DungeonHazardKind.VENT) DungeonHazardKind.LANCE else DungeonHazardKind.VENT

/** A vent's centre keeps this share of its radius (plus the wall) off the room's walls. */
private const val VENT_INSET_SHARE: Float = 0.6f
private const val STRIKE_RING_LIFE: Float = 0.4f
private const val STRIKE_RING_REACH: Float = 1.25f
private const val STRIKE_BURST: Int = 12
