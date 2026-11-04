// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.GarrisonEntry
import kinetickk.ball.content.api.GarrisonPlacement
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Garrison composition of a dungeon room's waves: kinds and positions from the garrison hash only (the run seed, the
 * structure cell, the room and the wave), never from a run stream. Planned when a wave is due (the seal for the first,
 * the telegraph's start for the others), so the Core's position then keeps members off it.
 */

/** Salt of the garrison hash. */
internal const val GARRISON_SALT: Long = 0x57C0_C7E5_5EED_0003L

/** One wave's garrison: kinds in entry order, one run-local (x, y) per member and its guardian's index (−1: none). */
internal class GarrisonPlan(val members: List<EnemyType>, val positions: FloatArray, val guardian: Int)

/**
 * [wave] of [room]'s garrison. Counts: an ordinary entry's count × the policy's scale × the Rebirth tier's enemy-cap
 * multiplier (at least one), a mini-boss or guardian entry as written; at most [MAX_GARRISON] in all, the last ones
 * dropped. Habitat members pick a template's themed kind, or a STRUCTURE kind at the run's threat time plus the gate
 * lead for a template without a theme. Positions: FLOOR members take points of a 3 × 3 lattice inset into the room,
 * four points apart per member from a hashed start, skipping points near the Core; WALL members take the inner corners
 * (diagonals first), then side midpoints clear of doors, then fall back to FLOOR; a CENTER member takes the room
 * centre, or a point beyond it from a Core standing close; each point is nudged off walls. Every draw comes from the
 * garrison hash (the lattice start first, then one per habitat member); the first wave keeps the hash of a room alone.
 */
internal fun MutableGameState.planGarrison(current: DungeonState, room: Int, template: StructureTemplate, wave: Int): GarrisonPlan {
    val policy = content.structures
    val spec = template.rooms[room]
    val key = room.toLong() xor (wave.toLong() shl 8)
    val random = HashRandom(
        splitMix64(worldHash(runSeed, worldLayer, structureCellX(current.structureId), structureCellY(current.structureId), GARRISON_SALT) xor key),
    )
    val start = (random.nextInt() ushr 1) % LATTICE_POINTS
    val threat = threatElapsed + policy.garrisonGateLeadSeconds
    val inverse = (4 - current.rotation) and 3
    val coreLocalX = rotateQuarterX(coreX - current.x, coreY - current.y, inverse)
    val coreLocalY = rotateQuarterY(coreX - current.x, coreY - current.y, inverse)
    val members = ArrayList<EnemyType>(MAX_GARRISON)
    val points = FloatArray(MAX_GARRISON * 2)
    var guardian = -1
    var wallMembers = 0
    val entries = spec.garrison
    for (entryIndex in entries.indices) {
        val entry = entries[entryIndex]
        if (entry.wave != wave) continue
        val fixed = entry.type
        repeat(entryCount(entry)) { slot ->
            if (members.size >= MAX_GARRISON) return@repeat
            val member = members.size
            // A themed lead alternates with ordinary habitat supports. Filling every slot with a themed ranged or
            // heavy native crowds out the weak members that keep early rooms approachable.
            val type = fixed ?: if (slot % 2 == 0) pickThemedGarrisonType(template, random, threat)
            else pickHabitatType(EnemyHabitat.STRUCTURE, random.nextFloat(), threatSeconds = threat)
            if (entry.guardian || type.profile.rank == EnemyRank.MINI_BOSS) guardian = member
            val radius = type.profile.radius
            var point: SteeringVector? = when (entry.placement) {
                GarrisonPlacement.WALL -> wallPoint(spec, template, wallMembers++, radius)
                GarrisonPlacement.CENTER -> centerPoint(spec, template.wallHalfThickness, radius, coreLocalX, coreLocalY)
                GarrisonPlacement.FLOOR -> null
            }
            if (point == null) point = floorPoint(spec, start, member, radius, coreLocalX, coreLocalY)
            val placed = garrisonPointClearOfWalls(current, point, template.wallHalfThickness + radius + NUDGE_MARGIN, radius)
            members += type
            points[member * 2] = placed.x
            points[member * 2 + 1] = placed.y
        }
    }
    return GarrisonPlan(members, points.copyOf(members.size * 2), guardian)
}

/** Members [wave] of [room] spawns: the planned counts, at most [MAX_GARRISON]. */
internal fun MutableGameState.garrisonSize(room: StructureRoom, wave: Int): Int {
    var total = 0
    for (index in room.garrison.indices) {
        val entry = room.garrison[index]
        if (entry.wave == wave) total += entryCount(entry)
    }
    return minOf(total, MAX_GARRISON)
}

/** Explicit themed natives may cross habitats, but keep their time and Rebirth gates like ordinary habitat picks. */
private fun MutableGameState.pickThemedGarrisonType(template: StructureTemplate, random: HashRandom, threat: Float): EnemyType {
    val eligible = template.garrisonTypes.filter { type ->
        val definition = content.enemy(type)
        definition.minimumRebirth <= rebirthLevel && definition.timeGateSeconds <= threat
    }
    return if (eligible.isEmpty()) {
        pickHabitatType(EnemyHabitat.STRUCTURE, random.nextFloat(), threatSeconds = threat)
    } else {
        eligible[random.nextIndex(eligible.size)]
    }
}

/**
 * One entry's members: a mini-boss or guardian entry as written, any other scaled by the policy and by the Rebirth
 * tier's enemy-cap multiplier, the open world's own crowd growth (at least one).
 */
private fun MutableGameState.entryCount(entry: GarrisonEntry): Int {
    val fixed = entry.type
    if (entry.guardian || (fixed != null && fixed.profile.rank == EnemyRank.MINI_BOSS)) return entry.count
    return max(1, (entry.count * content.structures.garrisonScale * rebirthProfile.enemyCapMultiplier).roundToInt())
}

/**
 * The [index]th wall point of [room] for a member of [radius] (template space), or null past the room's points: the
 * four inner corners inset by the radius, the wall half thickness and a gap (opposite corners first), then the side
 * midpoints that keep clear of every door on their side.
 */
private fun wallPoint(room: StructureRoom, template: StructureTemplate, index: Int, radius: Float): SteeringVector? {
    val inset = radius + template.wallHalfThickness + WALL_POINT_GAP
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    when (index) {
        0 -> return steeringVector(left, top)
        1 -> return steeringVector(right, bottom)
        2 -> return steeringVector(right, top)
        3 -> return steeringVector(left, bottom)
    }
    val midX = (room.minX + room.maxX) * 0.5f
    val midY = (room.minY + room.maxY) * 0.5f
    var free = index - 4
    for (side in 0 until 4) {
        val axis = if (side % 2 == 0) WallAxis.HORIZONTAL else WallAxis.VERTICAL
        val line = when (side) {
            0 -> room.minY
            1 -> room.maxX
            2 -> room.maxY
            else -> room.minX
        }
        val along = if (axis == WallAxis.HORIZONTAL) midX else midY
        val blocked = template.doors.any { door ->
            door.axis == axis && door.line == line && abs(door.center - along) < door.width * 0.5f + radius + DOOR_POINT_GAP
        }
        if (blocked) continue
        if (free-- > 0) continue
        return when (side) {
            0 -> steeringVector(midX, top)
            1 -> steeringVector(right, midY)
            2 -> steeringVector(midX, bottom)
            else -> steeringVector(left, midY)
        }
    }
    return null
}

/** The room centre, or a point [CENTER_CORE_DISTANCE] beyond a Core standing closer than that, held inside the room. */
private fun centerPoint(room: StructureRoom, wallHalfThickness: Float, radius: Float, coreX: Float, coreY: Float): SteeringVector {
    val midX = (room.minX + room.maxX) * 0.5f
    val midY = (room.minY + room.maxY) * 0.5f
    val dx = midX - coreX
    val dy = midY - coreY
    val distance = hypot(dx, dy)
    if (distance >= CENTER_CORE_DISTANCE) return steeringVector(midX, midY)
    val ux = if (distance > 0.001f) dx / distance else 1f
    val uy = if (distance > 0.001f) dy / distance else 0f
    val inset = radius + wallHalfThickness + WALL_POINT_GAP
    return steeringVector(
        clampIntoSpan(coreX + ux * CENTER_CORE_DISTANCE, room.minX + inset, room.maxX - inset),
        clampIntoSpan(coreY + uy * CENTER_CORE_DISTANCE, room.minY + inset, room.maxY - inset),
    )
}

/**
 * Lattice point (start + 4 × [member] + k) mod 9 for the first k whose point keeps [FLOOR_CORE_CLEARANCE] from the
 * Core (the farthest of the nine when none does), pushed out on a golden-angle ring per round of nine members.
 */
private fun floorPoint(room: StructureRoom, start: Int, member: Int, radius: Float, coreX: Float, coreY: Float): SteeringVector {
    val inset = LATTICE_INSET + radius
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    var chosen = -1
    var farthest = -1
    var farthestDistance = -1f
    for (step in 0 until LATTICE_POINTS) {
        val point = (start + LATTICE_STRIDE * member + step) % LATTICE_POINTS
        val px = latticeCoordinate(left, right, point % 3)
        val py = latticeCoordinate(top, bottom, point / 3)
        val distance = hypot(px - coreX, py - coreY)
        if (distance >= FLOOR_CORE_CLEARANCE) {
            chosen = point
            break
        }
        if (distance > farthestDistance) {
            farthestDistance = distance
            farthest = point
        }
    }
    if (chosen < 0) chosen = farthest
    val ring = RING_SPACING * (member / LATTICE_POINTS)
    val angle = member * GARRISON_GOLDEN_ANGLE
    return steeringVector(
        latticeCoordinate(left, right, chosen % 3) + cos(angle) * ring,
        latticeCoordinate(top, bottom, chosen / 3) + sin(angle) * ring,
    )
}

/** Column or row [index] (0, 1, 2) of a lattice spanning [low]..[high]; the middle when the span is empty. */
private fun latticeCoordinate(low: Float, high: Float, index: Int): Float {
    if (low > high) return (low + high) * 0.5f
    return when (index) {
        0 -> low
        1 -> (low + high) * 0.5f
        else -> high
    }
}

/**
 * The template-space [point] placed in the world, nudged by [step] along ±y then ±x (template space) to the first
 * spot no wall or rock blocks for a member of [radius]; the point itself when every spot is blocked.
 */
private fun MutableGameState.garrisonPointClearOfWalls(current: DungeonState, point: SteeringVector, step: Float, radius: Float): SteeringVector {
    for (nudge in 0 until NUDGES) {
        val localX = point.x + when (nudge) {
            3 -> step
            4 -> -step
            else -> 0f
        }
        val localY = point.y + when (nudge) {
            1 -> step
            2 -> -step
            else -> 0f
        }
        val x = current.x + rotateQuarterX(localX, localY, current.rotation)
        val y = current.y + rotateQuarterY(localX, localY, current.rotation)
        if (!isSpawnPointBlocked(x, y, radius)) return steeringVector(x, y)
    }
    return steeringVector(current.x + rotateQuarterX(point.x, point.y, current.rotation), current.y + rotateQuarterY(point.x, point.y, current.rotation))
}

private const val MAX_GARRISON: Int = ContentBounds.MAX_STRUCTURE_GARRISON
private const val LATTICE_POINTS: Int = 9

/** Coprime with nine, so nine members visit every lattice point once. */
private const val LATTICE_STRIDE: Int = 4
private const val LATTICE_INSET: Float = 120f
private const val FLOOR_CORE_CLEARANCE: Float = 260f
private const val RING_SPACING: Float = 40f
private const val WALL_POINT_GAP: Float = 30f
private const val DOOR_POINT_GAP: Float = 20f
private const val CENTER_CORE_DISTANCE: Float = 300f
private const val NUDGE_MARGIN: Float = 40f
private const val NUDGES: Int = 5

/** Golden angle (rad): the Data ring of a structure's rewards and the garrison's outer lattice rings. */
internal const val GARRISON_GOLDEN_ANGLE: Float = 2.399963f
