// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.RoomFight
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*

/*
 * The sealed room's fight after its seal (RoomFight on the dungeon, written only here and by DungeonSystem): its later
 * waves and its guardian. The first wave lands with the seal; each later one is planned once at most the policy's
 * trigger share of the last wave lives, or the wave interval after it landed. Its breach points glow for the wave
 * telegraph, then it lands there, all or nothing within the enemy caps (a refused wave retries every step). The room
 * clears once every wave landed and nothing it owns lives. Kinds and points come from the garrison hash; nothing here
 * allocates outside planning or landing a wave.
 */

/** The fight of [room] just sealed with its first wave [plan] (its members the last of the enemy list). */
internal fun MutableGameState.openRoomFight(room: StructureRoom, plan: GarrisonPlan): RoomFight {
    val guardian = if (plan.guardian >= 0) landedMemberId(plan.members.size, plan.guardian) else -1
    return RoomFight(sealedAt = elapsed, waves = room.waves, landedMembers = plan.members.size, guardianId = guardian)
}

/**
 * The integrity share of a wave's guardian [type]: an ordinary kind's spawn integrity × the policy's guardian scale,
 * a mini-boss its own; both × the room's integrity lever.
 */
internal fun MutableGameState.guardianShare(type: EnemyType?): Float {
    val policy = content.structures
    val scale = if (type == null || type.profile.bossClass) 1f else policy.guardianIntegrityScale
    return scale * policy.garrisonHealthMultiplier
}

/** One step of the sealed room's waves; true once the room is clear (a room without a fight clears with its members). */
internal fun MutableGameState.upkeepRoomWaves(current: DungeonState, tag: Long): Boolean {
    val fight = current.fight ?: return liveEncounterMembers(tag) == 0
    if (fight.incomingAt >= 0f) {
        if (elapsed >= fight.incomingAt) landIncomingWave(current, fight, tag)
        return false
    }
    val live = liveEncounterMembers(tag)
    if (fight.landed >= fight.waves) return live == 0
    val policy = content.structures
    val trigger = (fight.landedMembers * policy.waveTriggerShare).toInt()
    if (live <= trigger || elapsed - fight.landedAt >= policy.waveIntervalSeconds) planNextWave(current, fight)
    return false
}

/** The next wave's kinds and breach points, landing a telegraph from now. */
private fun MutableGameState.planNextWave(current: DungeonState, fight: RoomFight) {
    val template = content.structures.template(current.template)
    val plan = planGarrison(current, current.engagedRoom, template, fight.landed)
    val points = ArrayList<Float>(plan.positions.size)
    for (index in plan.positions.indices) points += plan.positions[index]
    dungeon = current.copy(
        fight = fight.copy(
            incomingAt = elapsed + content.structures.waveTelegraphSeconds,
            incoming = plan.members,
            incomingPoints = points,
            incomingGuardian = plan.guardian,
        ),
    )
}

/** Lands the incoming wave where its breach points glowed (a refusal keeps it on its way for the next step). */
private fun MutableGameState.landIncomingWave(current: DungeonState, fight: RoomFight, tag: Long) {
    val members = fight.incoming
    val lead = fight.incomingGuardian
    val share = content.structures.garrisonHealthMultiplier
    if (!reinforceEncounter(tag, members, fight.incomingPoints, share, lead, guardianShare(members.getOrNull(lead)))) return
    val guardian = if (lead >= 0) landedMemberId(members.size, lead) else fight.guardianId
    dungeon = current.copy(
        fight = fight.copy(
            landed = fight.landed + 1,
            landedAt = elapsed,
            landedMembers = members.size,
            incomingAt = -1f,
            incoming = emptyList(),
            incomingPoints = emptyList(),
            incomingGuardian = -1,
            guardianId = guardian,
        ),
    )
    val points = fight.incomingPoints
    val tone = content.structures.template(current.template).element?.let(FxColorCode::elementTone) ?: 4
    for (member in members.indices) burst(points[member * 2], points[member * 2 + 1], BREACH_BURST, tone)
    message = (if (lead >= 0) StructureNotice.GUARDIAN_AWAKENS else StructureNotice.REINFORCEMENTS).key
    messageTime = if (lead >= 0) 2.2f else 1.4f
    if (lead >= 0) {
        val guardianIndex = lead * 2
        emitDungeonMoment(DungeonMomentKind.GUARDIAN, current, current.engagedRoom, points[guardianIndex], points[guardianIndex + 1])
    } else {
        emitDungeonMoment(DungeonMomentKind.WAVE, current, current.engagedRoom)
    }
}

/** The id of wave member [index] of the [members] just appended to the enemy list. */
private fun MutableGameState.landedMemberId(members: Int, index: Int): Int = enemies[enemies.size - members + index].id

/**
 * Emits [kind] for [room] of the dungeon [current]: the room turned into a run-local rect and the focus, the room's
 * centre unless given.
 */
internal fun MutableGameState.emitDungeonMoment(
    kind: DungeonMomentKind,
    current: DungeonState,
    room: Int,
    focusX: Float = Float.NaN,
    focusY: Float = Float.NaN,
) {
    val spec = content.structures.template(current.template).rooms[room]
    val localX = (spec.minX + spec.maxX) * 0.5f
    val localY = (spec.minY + spec.maxY) * 0.5f
    val x = current.x + rotateQuarterX(localX, localY, current.rotation)
    val y = current.y + rotateQuarterY(localX, localY, current.rotation)
    val halfX = (spec.maxX - spec.minX) * 0.5f
    val halfY = (spec.maxY - spec.minY) * 0.5f
    val quarter = current.rotation and 1 == 1
    emitVisualFx(
        VisualFxCue.DungeonMoment(
            kind, current.template, x, y,
            halfWidth = if (quarter) halfY else halfX,
            halfHeight = if (quarter) halfX else halfY,
            focusX = if (focusX.isNaN()) x else focusX,
            focusY = if (focusY.isNaN()) y else focusY,
        ),
    )
}

/** Particles of each breach point as its member lands. */
private const val BREACH_BURST: Int = 8
