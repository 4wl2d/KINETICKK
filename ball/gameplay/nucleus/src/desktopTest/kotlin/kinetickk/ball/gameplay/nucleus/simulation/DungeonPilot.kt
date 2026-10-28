// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.STRUCTURE_OUTSIDE
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** How a dungeon attempt ended. */
internal enum class DungeonOutcome {
    /** The final room was cleared: the structure paid its rewards. */
    CLEARED,

    /** The run ended while the Core held the dungeon. */
    DIED_INSIDE,

    /** The Core left the structure (or gave up after [DungeonPilot.GIVE_UP_SECONDS] inside). */
    ABANDONED,

    /** The Architect arrived and the structure collapsed around the Core. */
    COLLAPSED,
}

/** One dungeon attempt: the structure, when the Core entered it, how and when the attempt ended. */
internal data class DungeonAttempt(
    val structureId: Long,
    val template: StructureTemplateId,
    val rotation: Int,
    val enteredAt: Float,
    val outcome: DungeonOutcome,
    val endedAt: Float,
    /** Rooms that count (garrisoned rooms and the final room) the Core cleared. */
    val roomsCleared: Int,
) {
    /** Simulated seconds from entering the footprint to the outcome. */
    val seconds: Float get() = endedAt - enteredAt

    fun line(): String = "$template/r$rotation $outcome entered=${"%.2f".format(enteredAt)} " +
        "seconds=${"%.2f".format(seconds)} rooms=$roomsCleared"
}

/**
 * Test-only opt-in pilot of the dungeon gates (structures §11.2): the input-only NoPoi bot (its steering law, its
 * choices, its skills) that, once per run from [seekFrom] until [seekLead] seconds before the Architect, flies into the
 * nearest structure signal and fights through the dungeon. Every pointer it sets inverts a desired velocity through the
 * production steering inverse ([steeringAimFor]); all motion and damage go through `update()`, and it avoids nothing
 * the dungeon gates measure.
 *
 * - Seeking: the entrance's outer approach point (260 u out along the door's outward normal), the entrance, the
 *   entrance room's centre, rounding the footprint by its corners when it lies in the way; with no signal it flies
 *   outward from the absolute origin at 900 u/s until one appears. Rocks, other optional sites and other structures
 *   are avoided as the bot avoids them. The Architect's Doodle is a secret, not a dungeon: it is never the target.
 * - Inside a sealed room: the nearest garrison member is the target, at most 800 u/s. The bot's hunting law decides
 *   how: its orbiting weapons (Arc Coil, Ion Swarm, Prism Relay) circle the target at 220 u, every other weapon rams
 *   it. Walls within 140 u of the predicted Core push it off (gain 6, [nearestWallSurface]); a Dash only dodges a shot
 *   when its line is clear of walls and solid rocks, and never while the previous Dash phase runs.
 * - Inside with no sealed room: a breadth-first route over open doors to the nearest room still to clear (an
 *   uncleared room with a garrison, or the final room once its locks are open), through door waypoints 160 u on each
 *   side of every door centre. Held from outside the walls (a notch of the footprint), it first leaves the footprint
 *   and rounds it to the entrance.
 * - After the outcome the bot flies as before (footprint avoidance on).
 *
 * The attempt starts the moment the Core holds a dungeon and ends in one [DungeonOutcome]; [observe] reads it after
 * every simulated input frame.
 */
internal class DungeonPilot(
    private val bot: NoPoiBot,
    private val seekFrom: Float = 90f,
    private val seekLead: Float = 150f,
) {
    /** The finished attempt, or null while none ended. */
    var attempt: DungeonAttempt? = null
        private set

    /** True while the Core holds a dungeon the pilot is attempting. */
    val inside: Boolean get() = holding && attempt == null

    private var holding = false
    private var heldId = 0L
    private var heldTemplate = StructureTemplateId.DERELICT_STATION
    private var heldRotation = 0
    private var enteredAt = 0f
    private var lastDungeon: DungeonState? = null
    private var lastRoom = STRUCTURE_OUTSIDE
    private var targetId = 0L
    private var targeting = false
    private var approachStage = 0
    private var stageDoor = -1
    private var doorStage = 0
    private var braking = false
    private val wall = FloatArray(4)
    private val corner = FloatArray(2)
    private val siteMarks = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
    private val queue = IntArray(ROUTE_NODES)
    private val parentDoor = IntArray(ROUTE_NODES)

    /** The pilot's steering until the attempt ends, then the bot's. */
    fun control(s: MutableGameState) {
        val held = s.dungeon
        when {
            attempt != null -> bot.control(s)
            holding && held != null && held.structureId == heldId -> dungeonControl(s, held)
            s.elapsed >= seekFrom && s.elapsed < s.content.tempo.bossAtSeconds - seekLead -> seekControl(s)
            else -> bot.control(s)
        }
    }

    /** The bot's choices: it opts into nothing a choice offers and builds as the bot does. */
    fun choose(s: MutableGameState) = bot.choose(s)

    /** Reads the attempt after a simulated input frame (and after the run ended): entry, outcome, give-up. */
    fun observe(s: MutableGameState) {
        if (attempt != null) return
        val held = s.dungeon
        if (!holding) {
            if (held == null || s.phase == GamePhase.GAME_OVER) return
            holding = true
            heldId = held.structureId
            heldTemplate = held.template
            heldRotation = held.rotation
            enteredAt = s.elapsed
            lastDungeon = held
            lastRoom = STRUCTURE_OUTSIDE
            return
        }
        when {
            s.phase == GamePhase.GAME_OVER -> end(s, DungeonOutcome.DIED_INSIDE)
            held != null && held.structureId == heldId -> {
                lastDungeon = held
                if (s.elapsed - enteredAt > GIVE_UP_SECONDS) end(s, DungeonOutcome.ABANDONED)
            }
            s.bossSpawned -> end(s, DungeonOutcome.COLLAPSED)
            s.isSpent(heldId) && finishedHere(s) -> end(s, DungeonOutcome.CLEARED)
            else -> end(s, DungeonOutcome.ABANDONED)
        }
    }

    /**
     * True when the held structure was let go because its final room cleared: the clear notice, or (should a later
     * step of the same frame have replaced it) the final room was the one sealed or needed no fight.
     */
    private fun finishedHere(s: MutableGameState): Boolean {
        val template = s.content.structures.template(heldTemplate)
        if (template.secret || s.message == StructureNotice.STRUCTURE_CLEARED.key) return true
        if (s.message == StructureNotice.STRUCTURE_ABANDONED.key) return false
        return lastDungeon?.engagedRoom == template.finalRoom || template.rooms[template.finalRoom].garrison.isEmpty()
    }

    private fun end(s: MutableGameState, outcome: DungeonOutcome) {
        val template = s.content.structures.template(heldTemplate)
        var cleared = (lastDungeon?.clearedRooms ?: 0) and template.roomsToClearMask
        if (outcome == DungeonOutcome.CLEARED) cleared = cleared or (1 shl template.finalRoom)
        attempt = DungeonAttempt(heldId, heldTemplate, heldRotation, enteredAt, outcome, s.elapsed, cleared.countOneBits())
    }

    // -----------------------------------------------------------------------------------------
    // Seeking a structure.

    private fun seekControl(s: MutableGameState) {
        val signal = targetSignal(s)
        var waypointX: Float
        var waypointY: Float
        var desiredX: Float
        var desiredY: Float
        if (signal == null) {
            // No signal yet: outward from the absolute origin, along the Core's absolute position.
            val absoluteX = (s.worldOriginX + s.coreX).toFloat()
            val absoluteY = (s.worldOriginY + s.coreY).toFloat()
            val distance = length(absoluteX, absoluteY)
            val ux = if (distance > 1f) absoluteX / distance else 1f
            val uy = if (distance > 1f) absoluteY / distance else 0f
            desiredX = ux * SEARCH_SPEED
            desiredY = uy * SEARCH_SPEED
            waypointX = s.coreX + ux * 1_000f
            waypointY = s.coreY + uy * 1_000f
        } else {
            val placement = requireNotNull(s.structurePlacement(structureCellX(signal.id), structureCellY(signal.id)))
            val template = s.content.structures.template(signal.template)
            val (normalX, normalY) = doorNormal(template, template.entranceDoor, template.entranceRoom, placement.rotation)
            // The entrance room's side of the door is −normal, so outward is +normal.
            val outerX = signal.entranceX + normalX * APPROACH_OFFSET
            val outerY = signal.entranceY + normalY * APPROACH_OFFSET
            val room = template.rooms[template.entranceRoom]
            val roomLocalX = (room.minX + room.maxX) * 0.5f
            val roomLocalY = (room.minY + room.maxY) * 0.5f
            val roomX = signal.x + rotateQuarterX(roomLocalX, roomLocalY, placement.rotation)
            val roomY = signal.y + rotateQuarterY(roomLocalX, roomLocalY, placement.rotation)
            if (approachStage == 0 && distanceSquared(s.coreX, s.coreY, outerX, outerY) < APPROACH_REACHED * APPROACH_REACHED) approachStage = 1
            if (approachStage == 1 && distanceSquared(s.coreX, s.coreY, signal.entranceX, signal.entranceY) < DOOR_REACHED * DOOR_REACHED) approachStage = 2
            when (approachStage) {
                0 -> { waypointX = outerX; waypointY = outerY }
                1 -> { waypointX = signal.entranceX; waypointY = signal.entranceY }
                else -> { waypointX = roomX; waypointY = roomY }
            }
            // From another side, round the footprint by its corners rather than press against its outer walls.
            var detour = false
            if (approachStage == 0) {
                val odd = placement.rotation and 1 == 1
                val halfX = if (odd) template.halfHeight else template.halfWidth
                val halfY = if (odd) template.halfWidth else template.halfHeight
                if (detourCorner(s.coreX, s.coreY, outerX, outerY, signal.x, signal.y, halfX, halfY)) {
                    waypointX = corner[0]
                    waypointY = corner[1]
                    detour = true
                }
            }
            val passing = approachStage > 0
            val arrival = if (passing) DOOR_SPEED else if (detour) DETOUR_SPEED else APPROACH_SPEED
            val (vx, vy) = arrive(s, waypointX, waypointY, if (passing) DOOR_SPEED else SEARCH_SPEED, arrival)
            desiredX = vx
            desiredY = vy
        }
        // Skipped optional sites (other structures included) stay outside the path, unless the next waypoint lies in
        // their clearance (a neighbour whose circle overlaps the target's entrance); the target's own mark never counts.
        val siteCount = s.optionalSiteMarks(siteMarks)
        for (mark in 0 until siteCount) {
            val base = mark * 3
            if (signal != null && distanceSquared(signal.x, signal.y, siteMarks[base], siteMarks[base + 1]) < 1f) continue
            val clearance = siteMarks[base + 2] + SITE_CLEARANCE_MARGIN
            if (distanceSquared(waypointX, waypointY, siteMarks[base], siteMarks[base + 1]) < clearance * clearance) continue
            val px = s.coreX + s.velocityX * 0.35f - siteMarks[base]
            val py = s.coreY + s.velocityY * 0.35f - siteMarks[base + 1]
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 8f
                desiredY += py / pd * (clearance - pd) * 8f
            }
        }
        val avoided = avoidRocks(s, desiredX, desiredY)
        val walled = repelWalls(s, avoided.first, avoided.second)
        val dodged = dodgeShots(s, walled.first, walled.second)
        steer(s, dodged.first, dodged.second, brakeAbove = SEEK_BRAKE_ON, releaseBelow = SEEK_BRAKE_OFF)
        dashOnThreat(s)
        bot.castSkills(s)
    }

    /**
     * When the segment from ([x], [y]) to ([toX], [toY]) crosses the footprint centred at ([centreX], [centreY]) with
     * half extents [halfX] × [halfY] (inflated by [FOOTPRINT_BLOCK]), writes into [corner] the first corner (of the
     * footprint inflated by [FOOTPRINT_CORNER]) of the shortest way round it, one corner or two adjacent ones, and
     * returns true.
     */
    private fun detourCorner(
        x: Float, y: Float, toX: Float, toY: Float, centreX: Float, centreY: Float, halfX: Float, halfY: Float,
    ): Boolean {
        val blockX = halfX + FOOTPRINT_BLOCK
        val blockY = halfY + FOOTPRINT_BLOCK
        if (!segmentCrossesBox(x, y, toX, toY, centreX, centreY, blockX, blockY)) return false
        val cornerX = halfX + FOOTPRINT_CORNER
        val cornerY = halfY + FOOTPRINT_CORNER
        var best = Float.POSITIVE_INFINITY
        for (first in 0 until 4) {
            val ax = centreX + CORNER_SIGNS_X[first] * cornerX
            val ay = centreY + CORNER_SIGNS_Y[first] * cornerY
            if (segmentCrossesBox(x, y, ax, ay, centreX, centreY, blockX, blockY)) continue
            val lead = length(ax - x, ay - y)
            if (!segmentCrossesBox(ax, ay, toX, toY, centreX, centreY, blockX, blockY)) {
                val total = lead + length(toX - ax, toY - ay)
                if (total < best) { best = total; corner[0] = ax; corner[1] = ay }
                continue
            }
            for (step in intArrayOf(1, 3)) {
                val second = (first + step) and 3
                val bx = centreX + CORNER_SIGNS_X[second] * cornerX
                val by = centreY + CORNER_SIGNS_Y[second] * cornerY
                if (segmentCrossesBox(bx, by, toX, toY, centreX, centreY, blockX, blockY)) continue
                val total = lead + length(bx - ax, by - ay) + length(toX - bx, toY - by)
                if (total < best) { best = total; corner[0] = ax; corner[1] = ay }
            }
        }
        return best < Float.POSITIVE_INFINITY
    }

    /** True when the segment meets the axis-aligned box centred at ([cx], [cy]) with half extents [hx] × [hy] (slab test). */
    private fun segmentCrossesBox(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, hx: Float, hy: Float): Boolean {
        var low = 0f
        var high = 1f
        val dx = x1 - x0
        val dy = y1 - y0
        for (axis in 0 until 2) {
            val origin = if (axis == 0) x0 - cx else y0 - cy
            val direction = if (axis == 0) dx else dy
            val half = if (axis == 0) hx else hy
            if (kotlin.math.abs(direction) < 1e-6f) {
                if (origin < -half || origin > half) return false
                continue
            }
            var t0 = (-half - origin) / direction
            var t1 = (half - origin) / direction
            if (t0 > t1) { val swap = t0; t0 = t1; t1 = swap }
            low = max(low, t0)
            high = min(high, t1)
            if (low > high) return false
        }
        return true
    }

    /** The structure the pilot flies to: kept while it is still signalled, else the nearest unspent dungeon signal. */
    private fun targetSignal(s: MutableGameState) = s.structureField.signals.let { signals ->
        signals.firstOrNull { targeting && it.id == targetId } ?: signals.firstOrNull { !s.content.structures.template(it.template).secret }
            ?.also {
                targeting = true
                targetId = it.id
                approachStage = 0
            }
    }

    // -----------------------------------------------------------------------------------------
    // Inside the dungeon.

    private fun dungeonControl(s: MutableGameState, held: DungeonState) {
        val template = s.content.structures.template(held.template)
        val room = roomAt(template, held.rotation, held.x, held.y, s.coreX, s.coreY, 0f)
        if (room >= 0) lastRoom = room
        val (desiredX, desiredY) = if (held.engagedRoom >= 0) fightVelocity(s, held, template) else routeVelocity(s, held, template)
        val walled = repelWalls(s, desiredX, desiredY)
        val dodged = dodgeShots(s, walled.first, walled.second)
        steer(s, dodged.first, dodged.second, brakeAbove = INSIDE_BRAKE_ON, releaseBelow = INSIDE_BRAKE_OFF)
        dashOnThreat(s)
        bot.castSkills(s)
    }

    /** The bot's hunting law on the sealed room's garrison: orbit or ram the nearest member, at most 800 u/s. */
    private fun fightVelocity(s: MutableGameState, held: DungeonState, template: StructureTemplate): Pair<Float, Float> {
        val tag = roomEncounterTag(held.structureId, held.engagedRoom)
        var targetX: Float
        var targetY: Float
        var best = Float.POSITIVE_INFINITY
        val room = template.rooms[held.engagedRoom]
        val roomLocalX = (room.minX + room.maxX) * 0.5f
        val roomLocalY = (room.minY + room.maxY) * 0.5f
        targetX = held.x + rotateQuarterX(roomLocalX, roomLocalY, held.rotation)
        targetY = held.y + rotateQuarterY(roomLocalX, roomLocalY, held.rotation)
        var radius = 0f
        for (enemy in s.enemies) {
            if (enemy.ownerTag != tag || !enemy.alive) continue
            val distance = distanceSquared(s.coreX, s.coreY, enemy.x, enemy.y)
            if (distance < best) {
                best = distance
                targetX = enemy.x + enemy.vx * 0.3f
                targetY = enemy.y + enemy.vy * 0.3f
                radius = if (s.weapon in ORBITING_WEAPONS) ORBIT_RADIUS else 0f
            }
        }
        val dx = s.coreX - targetX
        val dy = s.coreY - targetY
        val distance = max(1f, length(dx, dy))
        val nx = if (distance > 2f) dx / distance else 1f
        val ny = if (distance > 2f) dy / distance else 0f
        var desiredX: Float
        var desiredY: Float
        if (radius < 50f) {
            desiredX = -nx * RAM_SPEED
            desiredY = -ny * RAM_SPEED
        } else {
            val radial = ((radius - distance) * 2.4f).coerceIn(-480f, 480f)
            desiredX = -ny * ORBIT_SPEED + nx * radial
            desiredY = nx * ORBIT_SPEED + ny * radial
        }
        val speed = length(desiredX, desiredY)
        if (speed > INSIDE_SPEED) {
            desiredX *= INSIDE_SPEED / speed
            desiredY *= INSIDE_SPEED / speed
        }
        return desiredX to desiredY
    }

    /** Toward the next door waypoint of the route to the nearest room still to clear (or that room's centre). */
    private fun routeVelocity(s: MutableGameState, held: DungeonState, template: StructureTemplate): Pair<Float, Float> {
        val firstDoor = route(template, held, lastRoom)
        if (firstDoor == ROUTE_NONE || firstDoor == ROUTE_HERE) {
            // In the room to clear but short of its entry inset, or nowhere to go: its centre.
            val room = template.rooms[if (lastRoom == STRUCTURE_OUTSIDE) template.entranceRoom else lastRoom]
            val localX = (room.minX + room.maxX) * 0.5f
            val localY = (room.minY + room.maxY) * 0.5f
            val x = held.x + rotateQuarterX(localX, localY, held.rotation)
            val y = held.y + rotateQuarterY(localX, localY, held.rotation)
            return arrive(s, x, y, ROOM_SPEED, ROOM_SPEED)
        }
        val door = template.doors[firstDoor]
        val doorLocalX = if (door.axis == WallAxis.HORIZONTAL) door.center else door.line
        val doorLocalY = if (door.axis == WallAxis.HORIZONTAL) door.line else door.center
        val doorX = held.x + rotateQuarterX(doorLocalX, doorLocalY, held.rotation)
        val doorY = held.y + rotateQuarterY(doorLocalX, doorLocalY, held.rotation)
        val (normalX, normalY) = doorNormal(template, firstDoor, lastRoom, held.rotation)
        if (stageDoor != firstDoor || stageRoom != lastRoom) {
            stageDoor = firstDoor
            stageRoom = lastRoom
            doorStage = 0
        }
        val nearX = doorX - normalX * DOOR_WAYPOINT
        val nearY = doorY - normalY * DOOR_WAYPOINT
        if (doorStage == 0 && distanceSquared(s.coreX, s.coreY, nearX, nearY) < DOOR_REACHED * DOOR_REACHED) doorStage = 1
        if (lastRoom == STRUCTURE_OUTSIDE && doorStage == 0) {
            // Held from outside the walls: leave a notch of the footprint first, then round it to the entrance.
            val odd = held.rotation and 1 == 1
            val halfX = if (odd) template.halfHeight else template.halfWidth
            val halfY = if (odd) template.halfWidth else template.halfHeight
            val dx = s.coreX - held.x
            val dy = s.coreY - held.y
            val farFromDoor = distanceSquared(s.coreX, s.coreY, doorX, doorY) > NOTCH_DOOR_REACH * NOTCH_DOOR_REACH
            if (farFromDoor && kotlin.math.abs(dx) < halfX + FOOTPRINT_BLOCK && kotlin.math.abs(dy) < halfY + FOOTPRINT_BLOCK) {
                return if (halfX - kotlin.math.abs(dx) < halfY - kotlin.math.abs(dy)) {
                    (if (dx >= 0f) ROOM_SPEED else -ROOM_SPEED) to 0f
                } else {
                    0f to (if (dy >= 0f) ROOM_SPEED else -ROOM_SPEED)
                }
            }
            if (detourCorner(s.coreX, s.coreY, nearX, nearY, held.x, held.y, halfX, halfY)) {
                return arrive(s, corner[0], corner[1], ROOM_SPEED, DETOUR_SPEED)
            }
        }
        return if (doorStage == 0) {
            arrive(s, nearX, nearY, ROOM_SPEED, DOOR_SPEED)
        } else {
            arrive(s, doorX + normalX * DOOR_WAYPOINT, doorY + normalY * DOOR_WAYPOINT, DOOR_SPEED, DOOR_SPEED)
        }
    }

    private var stageRoom = STRUCTURE_OUTSIDE

    /**
     * The first door of a breadth-first route over open doors from [from] (a room, or outside) to the nearest room
     * still to clear: [ROUTE_HERE] when [from] is that room, [ROUTE_NONE] when none can be reached. Rooms on the way
     * are cleared ones or rooms without a garrison (those clear on entry).
     */
    private fun route(template: StructureTemplate, held: DungeonState, from: Int): Int {
        val rooms = template.rooms
        val outside = rooms.size
        val start = if (from == STRUCTURE_OUTSIDE) outside else from
        if (start != outside && toClear(template, held, start)) return ROUTE_HERE
        parentDoor.fill(-1)
        var visited = 1 shl start
        var head = 0
        var tail = 0
        queue[tail++] = start
        while (head < tail) {
            val node = queue[head++]
            for (index in template.doors.indices) {
                val door = template.doors[index]
                val roomA = if (door.roomA == STRUCTURE_OUTSIDE) outside else door.roomA
                val roomB = if (door.roomB == STRUCTURE_OUTSIDE) outside else door.roomB
                val next = when (node) {
                    roomA -> roomB
                    roomB -> roomA
                    else -> continue
                }
                if (next == outside || visited and (1 shl next) != 0 || !doorOpen(door.lockRooms, held)) continue
                visited = visited or (1 shl next)
                parentDoor[next] = index
                if (toClear(template, held, next)) return firstDoorTo(template, next, start, outside)
                if (held.clearedRooms and (1 shl next) != 0 || rooms[next].garrison.isEmpty()) queue[tail++] = next
            }
        }
        return ROUTE_NONE
    }

    private fun firstDoorTo(template: StructureTemplate, goal: Int, start: Int, outside: Int): Int {
        var node = goal
        while (true) {
            val door = template.doors[parentDoor[node]]
            val roomA = if (door.roomA == STRUCTURE_OUTSIDE) outside else door.roomA
            val previous = if (roomA == node) (if (door.roomB == STRUCTURE_OUTSIDE) outside else door.roomB) else roomA
            if (previous == start) return parentDoor[node]
            node = previous
        }
    }

    private fun toClear(template: StructureTemplate, held: DungeonState, room: Int): Boolean =
        held.clearedRooms and (1 shl room) == 0 && (template.rooms[room].garrison.isNotEmpty() || room == template.finalRoom)

    private fun doorOpen(lock: Int, held: DungeonState): Boolean = lock == 0 || held.clearedRooms and lock == lock

    /**
     * The run-local unit normal of door [door] pointing from [from] (a room index, or outside) through it, for a
     * structure turned by [rotation] quarters.
     */
    private fun doorNormal(template: StructureTemplate, door: Int, from: Int, rotation: Int): Pair<Float, Float> {
        val spec = template.doors[door]
        val inner = if (from == STRUCTURE_OUTSIDE) (if (spec.roomA == STRUCTURE_OUTSIDE) spec.roomB else spec.roomA) else from
        val room = template.rooms[inner]
        val centre = if (spec.axis == WallAxis.HORIZONTAL) (room.minY + room.maxY) * 0.5f else (room.minX + room.maxX) * 0.5f
        var sign = if (spec.line > centre) 1f else -1f
        if (from == STRUCTURE_OUTSIDE) sign = -sign
        val localX = if (spec.axis == WallAxis.VERTICAL) sign else 0f
        val localY = if (spec.axis == WallAxis.HORIZONTAL) sign else 0f
        return rotateQuarterX(localX, localY, rotation) to rotateQuarterY(localX, localY, rotation)
    }

    // -----------------------------------------------------------------------------------------
    // Shared steering terms.

    /** Toward ([x], [y]) at up to [cruise], slowing so the Core reaches it at about [arrival]. */
    private fun arrive(s: MutableGameState, x: Float, y: Float, cruise: Float, arrival: Float): Pair<Float, Float> {
        val dx = x - s.coreX
        val dy = y - s.coreY
        val distance = length(dx, dy)
        if (distance < 1f) return 0f to 0f
        val speed = min(cruise, sqrt(arrival * arrival + 2f * ARRIVE_DECELERATION * distance))
        return dx / distance * speed to dy / distance * speed
    }

    /** The bot's rock avoidance from where momentum carries the Core. */
    private fun avoidRocks(s: MutableGameState, x: Float, y: Float): Pair<Float, Float> {
        var desiredX = x
        var desiredY = y
        for (obstacle in s.obstacleField.obstacles) {
            val reach = s.obstacleHazardReach(obstacle)
            if (reach <= 0f) continue
            val px = s.coreX + s.velocityX * 0.3f - obstacle.x
            val py = s.coreY + s.velocityY * 0.3f - obstacle.y
            val clearance = reach + s.coreRadius + if (obstacle.kind.solid) 110f else 40f
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 6f
                desiredY += py / pd * (clearance - pd) * 6f
            }
        }
        return desiredX to desiredY
    }

    /** Pushes the desired velocity off solid walls (sealed doors included) within [WALL_REACH] of the predicted Core. */
    private fun repelWalls(s: MutableGameState, x: Float, y: Float): Pair<Float, Float> {
        val px = s.coreX + s.velocityX * 0.3f
        val py = s.coreY + s.velocityY * 0.3f
        val gap = s.nearestWallSurface(px, py, WALL_REACH, wall)
        if (gap >= WALL_REACH) return x to y
        return x + wall[2] * (WALL_REACH - gap) * WALL_GAIN to y + wall[3] * (WALL_REACH - gap) * WALL_GAIN
    }

    /** The bot's side-step from hostile shots closing within 0.45 s. */
    private fun dodgeShots(s: MutableGameState, x: Float, y: Float): Pair<Float, Float> {
        var desiredX = x
        var desiredY = y
        for (shot in s.projectiles) if (shot.hostile) {
            val px = shot.x - s.coreX
            val py = shot.y - s.coreY
            val vx = shot.vx - s.velocityX
            val vy = shot.vy - s.velocityY
            val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
            if (time in 0f..0.45f && length(px + vx * time, py + vy * time) < 80f) {
                val length = max(1f, length(vx, vy))
                val side = if (px * -vy + py * vx > 0f) -1f else 1f
                desiredX += -vy / length * side * 380f
                desiredY += vx / length * side * 380f
            }
        }
        return desiredX to desiredY
    }

    /**
     * Points the singularity so the Core's acceleration turns its velocity toward ([desiredX], [desiredY]): the
     * bot's velocity law inverted through the production steering inverse, the pointer turned at most 0.45 rad per
     * input frame from where it is and held inside the normal steering area. Brakes above [brakeAbove] u/s until the
     * speed falls below [releaseBelow].
     */
    private fun steer(s: MutableGameState, desiredX: Float, desiredY: Float, brakeAbove: Float, releaseBelow: Float) {
        val ax = (desiredX - s.velocityX) * 5f + s.velocityX * s.dragCoefficient
        val ay = (desiredY - s.velocityY) * 5f + s.velocityY * s.dragCoefficient
        val aim = s.steeringAimFor(ax, ay)
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val pointerAngle = atan2(s.pointerY - s.screenHeight * 0.5f - offsetY, s.pointerX - s.screenWidth * 0.5f - offsetX)
        val difference = atan2(sin(aim.heading - pointerAngle), cos(aim.heading - pointerAngle))
        val angle = pointerAngle + difference.coerceIn(-0.45f, 0.45f)
        val ux = cos(angle)
        val uy = sin(angle)
        val normalHalfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val normalHalfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val availableX = if (ux > 0f) (normalHalfWidth - offsetX) / ux else if (ux < 0f) (-normalHalfWidth - offsetX) / ux else 10_000f
        val availableY = if (uy > 0f) (normalHalfHeight - offsetY) / uy else if (uy < 0f) (-normalHalfHeight - offsetY) / uy else 10_000f
        val tether = min(aim.distance.coerceIn(65f, 400f), min(availableX, availableY)).coerceAtLeast(65f)
        s.updatePointer(s.screenWidth * 0.5f + offsetX + ux * tether, s.screenHeight * 0.5f + offsetY + uy * tether)
        if (s.speed > brakeAbove) braking = true
        if (s.speed < releaseBelow) braking = false
        s.setBrake(braking)
    }

    /**
     * The bot's threat-dodge Dash at a human cadence: never while the previous Dash phase runs, and only when the
     * Dash line (toward the singularity) is clear of walls and solid rocks.
     */
    private fun dashOnThreat(s: MutableGameState) {
        if (!s.dashReady || s.heat >= 60f || s.dashPhaseTime > 0f) return
        val threat = s.projectiles.any {
            if (!it.hostile) false else {
                val px = it.x - s.coreX
                val py = it.y - s.coreY
                val vx = it.vx - s.velocityX
                val vy = it.vy - s.velocityY
                val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
                time in 0f..0.20f && length(px + vx * time, py + vy * time) < 32f
            }
        }
        if (!threat) return
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val angle = atan2(s.pointerY - s.screenHeight * 0.5f - offsetY, s.pointerX - s.screenWidth * 0.5f - offsetX)
        val endX = s.coreX + cos(angle) * DASH_CHECK
        val endY = s.coreY + sin(angle) * DASH_CHECK
        if (s.wallBlocksSegment(s.coreX, s.coreY, endX, endY, s.coreRadius)) return
        for (obstacle in s.obstacleField.obstacles) {
            if (obstacle.kind.solid && segmentCircleIntersects(s.coreX, s.coreY, endX, endY, obstacle.x, obstacle.y, obstacle.radius + s.coreRadius)) return
        }
        s.requestDash()
    }

    companion object {
        /** Seconds inside after which the pilot gives the attempt up as abandoned. */
        const val GIVE_UP_SECONDS = 300f

        private const val ROUTE_NODES = 17
        private const val ROUTE_NONE = -1
        private const val ROUTE_HERE = -2
        private const val SEARCH_SPEED = 900f
        private const val APPROACH_SPEED = 350f
        private const val DETOUR_SPEED = 600f
        private const val FOOTPRINT_BLOCK = 120f
        private const val FOOTPRINT_CORNER = 360f
        private const val NOTCH_DOOR_REACH = 300f
        private val CORNER_SIGNS_X = floatArrayOf(-1f, 1f, 1f, -1f)
        private val CORNER_SIGNS_Y = floatArrayOf(-1f, -1f, 1f, 1f)
        private const val DOOR_SPEED = 380f
        private const val ROOM_SPEED = 620f
        private const val INSIDE_SPEED = 800f
        private const val ORBIT_SPEED = 460f
        private const val ORBIT_RADIUS = 220f
        private const val RAM_SPEED = 650f
        private const val ARRIVE_DECELERATION = 900f
        private const val APPROACH_OFFSET = 260f
        private const val APPROACH_REACHED = 120f
        private const val DOOR_WAYPOINT = 160f
        private const val DOOR_REACHED = 90f
        private const val WALL_REACH = 140f
        private const val WALL_GAIN = 6f
        private const val DASH_CHECK = 320f
        private const val SITE_CLEARANCE_MARGIN = 295f
        private const val SEEK_BRAKE_ON = 1_050f
        private const val SEEK_BRAKE_OFF = 780f
        private const val INSIDE_BRAKE_ON = 850f
        private const val INSIDE_BRAKE_OFF = 600f
        /** The weapons the NoPoi bot orbits its target with; it rams with every other one. */
        private val ORBITING_WEAPONS = setOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.PRISM_RELAY)
    }
}
