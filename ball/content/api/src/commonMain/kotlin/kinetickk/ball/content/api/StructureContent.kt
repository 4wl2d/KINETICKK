// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.hypot

/** Hand-authored structure layouts. Append-only: the ordinal is the template index a placement hashes. */
enum class StructureTemplateId { DERELICT_STATION, VAULT, SPIRE, LABYRINTH, FOUNDRY, RELIQUARY, ARCHITECTS_DOODLE }

/** The line a door sits on: HORIZONTAL is `y = line` with the opening along x, VERTICAL is `x = line`. */
enum class WallAxis { HORIZONTAL, VERTICAL }

/** How a garrison entry picks its kind: by STRUCTURE habitat weight when its room engages, or a fixed kind. */
enum class GarrisonSelector { HABITAT, FIXED }

/** Where a garrison member appears inside its room. */
enum class GarrisonPlacement { FLOOR, WALL, CENTER }

/** The room index a door names when it opens to the outside. */
const val STRUCTURE_OUTSIDE: Int = -1

/** [count] enemies of one room's garrison; [type] is set exactly for [GarrisonSelector.FIXED]. */
data class GarrisonEntry(
    val selector: GarrisonSelector,
    val count: Int,
    val type: EnemyType? = null,
    val placement: GarrisonPlacement = GarrisonPlacement.FLOOR,
) {
    init {
        require(count in 1..ContentBounds.MAX_STRUCTURE_GARRISON) {
            "Garrison count must be in 1..${ContentBounds.MAX_STRUCTURE_GARRISON}"
        }
        require((selector == GarrisonSelector.FIXED) == (type != null)) { "Exactly the fixed garrison entries name a kind" }
        require(type != EnemyType.ARCHITECT && type != EnemyType.ELITE && type != EnemyType.HERALD) {
            "A garrison never holds the Architect, an Elite or the Herald"
        }
    }
}

/** Axis-aligned room in template space with its garrison (empty = a passage that never seals). */
data class StructureRoom(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val garrison: ImmutableList<GarrisonEntry> = immutableListOf(),
) {
    init {
        require(minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite()) { "Room bounds must be finite" }
        require(maxX - minX >= MIN_ROOM_SIDE && maxY - minY >= MIN_ROOM_SIDE) { "A room spans at least 400 u per side" }
        require(garrison.size <= MAX_GARRISON_ENTRIES) { "A room garrison has at most $MAX_GARRISON_ENTRIES entries" }
        require(garrison.sumOf { it.count } <= ContentBounds.MAX_STRUCTURE_GARRISON) {
            "A room garrisons at most ${ContentBounds.MAX_STRUCTURE_GARRISON} enemies"
        }
    }

    /** The whole opening of [door] lies on one of the room's edges. */
    internal fun holdsOpening(door: StructureDoor): Boolean = when (door.axis) {
        WallAxis.HORIZONTAL -> (minY == door.line || maxY == door.line) && minX <= door.gapStart && door.gapEnd <= maxX
        WallAxis.VERTICAL -> (minX == door.line || maxX == door.line) && minY <= door.gapStart && door.gapEnd <= maxY
    }

    /** Some of the opening of [door] lies on one of the room's edges. */
    internal fun touchesOpening(door: StructureDoor): Boolean = when (door.axis) {
        WallAxis.HORIZONTAL -> (minY == door.line || maxY == door.line) && minX < door.gapEnd && door.gapStart < maxX
        WallAxis.VERTICAL -> (minX == door.line || maxX == door.line) && minY < door.gapEnd && door.gapStart < maxY
    }

    internal fun overlaps(other: StructureRoom): Boolean =
        minX < other.maxX && other.minX < maxX && minY < other.maxY && other.minY < maxY
}

/**
 * A doorway: a clear opening of [width] centred at [center] on the room boundary line [axis] = [line]
 * (HORIZONTAL: y = line, opening along x). [roomB] = [STRUCTURE_OUTSIDE] opens to the outside. While the rooms in
 * [lockRooms] (bit per room) are not all cleared the door stays sealed; a template rejects a lock that can never open
 * (one waiting on a room that only the locked door leads to).
 */
data class StructureDoor(
    val axis: WallAxis,
    val line: Float,
    val center: Float,
    val width: Float,
    val roomA: Int,
    val roomB: Int,
    val lockRooms: Int = 0,
) {
    init {
        require(line.isFinite() && center.isFinite()) { "Door position must be finite" }
        require(width in MIN_DOOR_WIDTH..MAX_DOOR_WIDTH) { "Door width must be in 160..400" }
        require(roomA in 0 until ContentBounds.MAX_STRUCTURE_ROOMS) { "Door roomA must name a room" }
        require(roomB in STRUCTURE_OUTSIDE until ContentBounds.MAX_STRUCTURE_ROOMS && roomB != roomA) {
            "Door roomB must name another room or the outside"
        }
        require(lockRooms ushr ContentBounds.MAX_STRUCTURE_ROOMS == 0) { "A door locks on room bits only" }
    }

    val gapStart: Float get() = center - width / 2f
    val gapEnd: Float get() = center + width / 2f
    val leadsOutside: Boolean get() = roomB == STRUCTURE_OUTSIDE
}

/** A free-standing solid capsule (pillar, fin, ring chord) in template space. */
data class StructureCapsule(val ax: Float, val ay: Float, val bx: Float, val by: Float, val halfThickness: Float) {
    init {
        require(ax.isFinite() && ay.isFinite() && bx.isFinite() && by.isFinite()) { "Capsule ends must be finite" }
        require(halfThickness in MIN_CAPSULE_HALF_THICKNESS..MAX_CAPSULE_HALF_THICKNESS) {
            "Capsule half thickness must be in 12..80"
        }
    }
}

/** One compiled wall in template space; [door] is the door index or -1 for a solid wall. */
data class StructureWallSpec(
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val halfThickness: Float,
    val door: Int,
) {
    val isDoor: Boolean get() = door >= 0
}

enum class StructureRewardKind { RELIC, KEY, MATTER, DATA, REPAIR, ITEM_OFFER }

/** One payout of a structure's final room through an existing reward channel. */
data class StructureReward(val kind: StructureRewardKind, val amount: Int = 1, val rarityFloor: ItemRarity? = null) {
    init {
        val amounts = when (kind) {
            StructureRewardKind.RELIC, StructureRewardKind.KEY, StructureRewardKind.REPAIR -> 1..3
            StructureRewardKind.DATA -> 1..16
            StructureRewardKind.MATTER -> 1..400
            StructureRewardKind.ITEM_OFFER -> 1..1
        }
        require(amount in amounts) { "A $kind reward amount must be in $amounts" }
        if (kind == StructureRewardKind.ITEM_OFFER) {
            val floor = rarityFloor
            require(floor == ItemRarity.UNCOMMON || floor == ItemRarity.RARE || floor == ItemRarity.EPIC) {
                "An item offer floors its rarity at Uncommon, Rare or Epic"
            }
        } else {
            require(rarityFloor == null) { "Only an item offer carries a rarity floor" }
        }
    }

    /** English content phrase shown on the dungeon panel (localized at presentation). */
    val label: String
        get() = when (kind) {
            StructureRewardKind.RELIC -> "Relic"
            StructureRewardKind.KEY -> "Elite Key"
            StructureRewardKind.MATTER -> "Kinetic Matter"
            StructureRewardKind.DATA -> "Data cache"
            StructureRewardKind.REPAIR -> "Repair"
            StructureRewardKind.ITEM_OFFER -> when (rarityFloor) {
                ItemRarity.UNCOMMON -> "Uncommon item or better"
                ItemRarity.RARE -> "Rare item or better"
                ItemRarity.EPIC -> "Epic item or better"
                // Validation forbids every other floor; the branch keeps the mapping total.
                else -> "Item"
            }
        }
}

/** Run notices the dungeon runtime sends (English keys localized at presentation; a test iterates them). */
enum class StructureNotice(val key: String) {
    DOORS_SEALED("DOORS SEALED"),
    GUARDIAN_AWAKENS("GUARDIAN AWAKENS"),
    ROOM_CLEARED("ROOM CLEARED"),
    INNER_DOORS_OPEN("INNER DOORS OPEN"),
    STRUCTURE_CLEARED("STRUCTURE CLEARED"),
    STRUCTURE_ABANDONED("STRUCTURE ABANDONED"),
}

/**
 * One structure layout in template space (origin at the footprint centre, +x east, +y south). Construction
 * compiles [walls] and validates the layout: rooms are disjoint and reachable from [entranceRoom], every door sits
 * on the boundary its rooms share, and the footprint, walls, doors, rooms, garrisons and rewards stay within
 * [ContentBounds].
 */
data class StructureTemplate(
    val id: StructureTemplateId,
    val name: String,
    val weight: Float,
    /** Smallest cell-centre distance from the absolute origin at which the template may be picked. */
    val minimumDistance: Float,
    val rooms: ImmutableList<StructureRoom>,
    val doors: ImmutableList<StructureDoor> = immutableListOf(),
    val extraWalls: ImmutableList<StructureCapsule> = immutableListOf(),
    /** False: no walls are derived from rooms (the Doodle); only [extraWalls]. */
    val roomWalls: Boolean = true,
    val entranceRoom: Int,
    val finalRoom: Int,
    val rewards: ImmutableList<StructureReward>,
    /** An easter egg: no garrison, no entry banner, no panel or scrim; reaching the final room records its secret. */
    val secret: Boolean = false,
    val wallHalfThickness: Float = 24f,
) {
    /** Compiled walls: solid room walls, then [extraWalls], then one capsule per door in door order. */
    val walls: ImmutableList<StructureWallSpec>

    /** Footprint half extents (bbox of room boundaries and wall centre lines; centred on the origin). */
    val halfWidth: Float
    val halfHeight: Float

    /** Rooms that count on the panel: rooms with a garrison, plus the final room (bit per room). */
    val roomsToClearMask: Int

    /** Index of the outer door, or -1 (Doodle). */
    val entranceDoor: Int

    init {
        validateLayout()
        walls = compileStructureWalls(rooms, doors, extraWalls, roomWalls, wallHalfThickness)
        require(walls.size <= ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS) {
            "$name compiles ${walls.size} walls; at most ${ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS}"
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        rooms.forEach { room ->
            minX = minOf(minX, room.minX)
            minY = minOf(minY, room.minY)
            maxX = maxOf(maxX, room.maxX)
            maxY = maxOf(maxY, room.maxY)
        }
        walls.forEach { wall ->
            minX = minOf(minX, wall.ax, wall.bx)
            minY = minOf(minY, wall.ay, wall.by)
            maxX = maxOf(maxX, wall.ax, wall.bx)
            maxY = maxOf(maxY, wall.ay, wall.by)
        }
        val footprint = ContentBounds.MAX_STRUCTURE_FOOTPRINT
        require(maxX - minX <= footprint && maxY - minY <= footprint) {
            "$name footprint ${maxX - minX} x ${maxY - minY} exceeds ${ContentBounds.MAX_STRUCTURE_FOOTPRINT}"
        }
        require(abs(minX + maxX) <= FOOTPRINT_CENTRE_TOLERANCE && abs(minY + maxY) <= FOOTPRINT_CENTRE_TOLERANCE) {
            "$name footprint must be centred on the template origin"
        }
        halfWidth = (maxX - minX) / 2f
        halfHeight = (maxY - minY) / 2f
        var clear = 1 shl finalRoom
        rooms.forEachIndexed { index, room -> if (room.garrison.isNotEmpty()) clear = clear or (1 shl index) }
        roomsToClearMask = clear
        entranceDoor = doors.indexOfFirst { it.leadsOutside }
    }

    /** Radius of the smallest circle about the template origin holding the footprint in every rotation. */
    val circumradius: Float get() = hypot(halfWidth, halfHeight)

    private fun validateLayout() {
        require(name.isNotBlank() && name.length <= MAX_TEMPLATE_NAME_LENGTH) { "Template name must be 1..32 characters" }
        require(weight.isFinite() && weight >= 0f) { "$name weight must be finite and non-negative" }
        require(minimumDistance.isFinite() && minimumDistance >= 0f) { "$name minimum distance must be non-negative" }
        require(wallHalfThickness in MIN_WALL_HALF_THICKNESS..MAX_WALL_HALF_THICKNESS) {
            "$name wall half thickness must be in 12..60"
        }
        require(rooms.size in 1..ContentBounds.MAX_STRUCTURE_ROOMS) {
            "$name has ${rooms.size} rooms; 1..${ContentBounds.MAX_STRUCTURE_ROOMS} allowed"
        }
        require(doors.size <= ContentBounds.MAX_STRUCTURE_DOORS) {
            "$name has ${doors.size} doors; at most ${ContentBounds.MAX_STRUCTURE_DOORS}"
        }
        require(rewards.size <= ContentBounds.MAX_STRUCTURE_REWARDS) {
            "$name has ${rewards.size} rewards; at most ${ContentBounds.MAX_STRUCTURE_REWARDS}"
        }
        require(entranceRoom in rooms.indices && finalRoom in rooms.indices) { "$name entrance and final rooms must exist" }
        require(secret || finalRoom != entranceRoom) { "$name final room must differ from its entrance" }
        require(!secret || rooms.all { it.garrison.isEmpty() }) { "$name is secret and holds no garrison" }
        require(roomWalls || doors.isEmpty()) { "$name without room walls has no doors" }
        for (first in rooms.indices) {
            for (second in first + 1 until rooms.size) {
                require(!rooms[first].overlaps(rooms[second])) { "$name rooms $first and $second overlap" }
            }
        }
        doors.forEachIndexed { index, door ->
            require(door.roomA < rooms.size && door.roomB < rooms.size) { "$name door $index names a missing room" }
            require(door.lockRooms ushr rooms.size == 0) { "$name door $index locks on a missing room" }
            val room = rooms[door.roomA]
            if (door.leadsOutside) {
                val shared = rooms.indices.any { it != door.roomA && rooms[it].touchesOpening(door) }
                require(room.holdsOpening(door) && !shared) {
                    "$name door $index must open from its room to the outside"
                }
            } else {
                require(room.holdsOpening(door) && rooms[door.roomB].holdsOpening(door)) {
                    "$name door $index must sit on the boundary its rooms share"
                }
            }
        }
        if (!roomWalls) return
        require(doors.count { it.leadsOutside } == 1 && doors.single { it.leadsOutside }.roomA == entranceRoom) {
            "$name needs exactly one outer door, on its entrance room"
        }
        require(reachableRooms() == (1 shl rooms.size) - 1) { "$name has rooms no door path reaches from the entrance" }
    }

    /** Rooms reachable from the entrance when a locked door opens once every room it waits on is reached. */
    private fun reachableRooms(): Int {
        var reached = 1 shl entranceRoom
        do {
            val before = reached
            doors.forEach { door ->
                if (door.roomB >= 0 && door.lockRooms and reached == door.lockRooms) {
                    val a = 1 shl door.roomA
                    val b = 1 shl door.roomB
                    if (reached and a != 0 || reached and b != 0) reached = reached or a or b
                }
            }
        } while (reached != before)
        return reached
    }
}

/**
 * Structures and dungeons far from the start: placement over [cellSize] cells, the dungeon runtime's knobs and the
 * templates in [StructureTemplateId] order. Shipped disabled until the dungeon runtime turns structures on.
 */
data class StructurePolicy(
    val enabled: Boolean = false,
    val cellSize: Float = 4_800f,
    val cellMargin: Float = 300f,
    val minimumDistance: Float = 4_000f,
    val fullChanceDistance: Float = 10_000f,
    val maximumChance: Float = 0.4f,
    /** Chance scale per [WorldLayer] ordinal (BASE, ZENITH, NADIR, ARCHIVE). */
    val layerChance: ImmutableList<Float> = immutableListOf(1f, 0f, 0f, 0f),
    val obstacleClearance: Float = 160f,
    val signalRange: Float = 6_000f,
    val leaveRadius: Float = 2_400f,
    val entryInset: Float = 80f,
    val engageCutoffSeconds: Float = 45f,
    val garrisonGateLeadSeconds: Float = 90f,
    val garrisonExtraPerTiers: Int = 4,
    val garrisonScale: Float = 1f,
    val directorRateWhileSealed: Float = 0.35f,
    val wallDamageScale: Float = 1f,
    val templates: ImmutableList<StructureTemplate> = defaultStructureTemplates(),
) {
    fun template(id: StructureTemplateId): StructureTemplate = templates[id.ordinal]

    init {
        require(StructureTemplateId.entries.size <= ContentBounds.MAX_STRUCTURE_TEMPLATES) {
            "At most ${ContentBounds.MAX_STRUCTURE_TEMPLATES} structure templates"
        }
        require(templates.map { it.id } == StructureTemplateId.entries) {
            "Structure templates follow StructureTemplateId order"
        }
        require(cellMargin.isFinite() && cellMargin >= 0f) { "Structure cell margin must be non-negative" }
        val largestHalfExtent = templates.maxOf { maxOf(it.halfWidth, it.halfHeight) }
        require(cellSize.isFinite() && cellSize >= 2f * cellMargin + 2f * largestHalfExtent) {
            "Structure cells must hold the largest footprint inside their margins"
        }
        require(fullChanceDistance.isFinite() && minimumDistance >= 0f && minimumDistance < fullChanceDistance) {
            "Structure distances must satisfy 0 <= minimum < full chance"
        }
        require(maximumChance in 0f..1f) { "Structure maximum chance must be in 0..1" }
        require(layerChance.size == WorldLayer.entries.size && layerChance.all { it in 0f..1f }) {
            "Structure layer chances name every layer, each in 0..1"
        }
        require(obstacleClearance in 0f..MAX_OBSTACLE_CLEARANCE) { "Structure obstacle clearance must be in 0..600" }
        require(signalRange in 0f..minOf(2f * cellSize, MAX_SIGNAL_RANGE)) {
            "Structure signals reach at most two cells and ${MAX_SIGNAL_RANGE.toInt()} u"
        }
        require(leaveRadius.isFinite() && leaveRadius >= templates.maxOf { it.circumradius } + LEAVE_MARGIN) {
            "The leave radius must clear every footprint by ${LEAVE_MARGIN.toInt()} u"
        }
        require(entryInset in MIN_ENTRY_INSET..MAX_ENTRY_INSET) { "Structure entry inset must be in 48..200" }
        require(engageCutoffSeconds in 0f..MAX_ENGAGE_CUTOFF_SECONDS) { "Structure engage cutoff must be in 0..120 s" }
        require(garrisonGateLeadSeconds in 0f..MAX_GARRISON_GATE_LEAD_SECONDS) { "Garrison gate lead must be in 0..300 s" }
        require(garrisonExtraPerTiers >= 1) { "Garrison extras need at least one tier per enemy" }
        require(garrisonScale in MIN_GARRISON_SCALE..MAX_GARRISON_SCALE) { "Garrison scale must be in 0.25..3" }
        require(directorRateWhileSealed in 0f..1f) { "Sealed director rate must be in 0..1" }
        require(wallDamageScale in 0f..MAX_WALL_DAMAGE_SCALE) { "Wall damage scale must be in 0..2" }
        require(templates.any { it.weight > 0f && it.minimumDistance <= minimumDistance + cellSize }) {
            "At least one weighted template must be placeable within a cell of the minimum distance"
        }
    }
}

/** Rotation helpers shared by Nucleus and Interaction (pure): quarter [quarter] turns clockwise on screen. */
fun rotateQuarterX(x: Float, y: Float, quarter: Int): Float = when (quarter and 3) {
    0 -> x
    1 -> -y
    2 -> -x
    else -> y
}

fun rotateQuarterY(x: Float, y: Float, quarter: Int): Float = when (quarter and 3) {
    0 -> y
    1 -> x
    2 -> -y
    else -> -x
}

/**
 * Bootstrap cross-check against the roster and the obstacle window: every fixed garrison kind is an ordinary kind or
 * a mini-boss other than the Herald, an ordinary STRUCTURE kind opens at tier 0 from the first second, the obstacle
 * window spans at most three cells per axis (so at most nine structures) and keeps the dungeon's structure inside it.
 */
fun validateStructurePolicy(policy: StructurePolicy, enemies: List<EnemyDefinition>, obstacles: ObstacleTuning) {
    policy.templates.forEach { template ->
        template.rooms.forEach { room ->
            room.garrison.forEach { entry ->
                val type = entry.type ?: return@forEach
                val definition = enemies.firstOrNull { it.type == type }
                requireNotNull(definition) { "${template.name} garrisons $type, which the roster does not define" }
                require(
                    type != EnemyType.HERALD &&
                        (definition.rank == EnemyRank.ORDINARY || definition.rank == EnemyRank.MINI_BOSS),
                ) {
                    "${template.name} garrisons $type: a fixed garrison kind must be ordinary or a mini-boss"
                }
            }
        }
    }
    require(
        enemies.any {
            it.rank == EnemyRank.ORDINARY && it.livesIn(EnemyHabitat.STRUCTURE) &&
                it.weightIn(EnemyHabitat.STRUCTURE) > 0f && it.timeGateSeconds == 0f &&
                it.minimumRebirth <= ContentBounds.MIN_REBIRTH_LEVEL
        },
    ) {
        "A structure garrison needs an ordinary STRUCTURE kind open at tier 0 from the start"
    }
    require(obstacles.windowChunks * obstacles.chunkSize <= 2f * policy.cellSize) {
        "The obstacle window must span at most three structure cells per axis"
    }
    require(policy.leaveRadius <= (obstacles.windowChunks / 2) * obstacles.chunkSize) {
        "The leave radius must stay inside the obstacle window's margin"
    }
}

/**
 * Compiles a template's capsule walls. Room edges merge per boundary line (HORIZONTAL lines by y ascending, then
 * VERTICAL by x ascending; touching intervals join); each door cuts the piece holding its opening, leaving jambs that
 * end [halfThickness] short of the opening so the clear width equals the door width. Output: the SOLID pieces in line
 * then position order, then [extraWalls], then one door capsule per door spanning its opening.
 */
private fun compileStructureWalls(
    rooms: List<StructureRoom>,
    doors: List<StructureDoor>,
    extraWalls: List<StructureCapsule>,
    roomWalls: Boolean,
    halfThickness: Float,
): ImmutableList<StructureWallSpec> {
    val walls = ArrayList<StructureWallSpec>()
    if (roomWalls) {
        val edges = ArrayList<BoundaryEdge>(rooms.size * 4)
        rooms.forEach { room ->
            edges += BoundaryEdge(WallAxis.HORIZONTAL, room.minY, room.minX, room.maxX)
            edges += BoundaryEdge(WallAxis.HORIZONTAL, room.maxY, room.minX, room.maxX)
            edges += BoundaryEdge(WallAxis.VERTICAL, room.minX, room.minY, room.maxY)
            edges += BoundaryEdge(WallAxis.VERTICAL, room.maxX, room.minY, room.maxY)
        }
        val cutDoors = BooleanArray(doors.size)
        val lines = edges.map { it.axis to it.line }.distinct().sortedWith(compareBy({ it.first.ordinal }, { it.second }))
        lines.forEach { (axis, line) ->
            val cuts = doors.indices.filter { doors[it].axis == axis && doors[it].line == line }
                .sortedBy { doors[it].gapStart }
            mergedIntervals(edges.filter { it.axis == axis && it.line == line }).forEach { (lo, hi) ->
                var pieces = listOf(lo to hi)
                cuts.forEach { index ->
                    val door = doors[index]
                    pieces = pieces.flatMap { (start, end) ->
                        if (door.gapStart >= start && door.gapEnd <= end) {
                            require(!cutDoors[index]) { "Door $index opens on two wall pieces" }
                            cutDoors[index] = true
                            val before = start to door.gapStart - halfThickness
                            val after = door.gapEnd + halfThickness to end
                            require(before.second - before.first >= MIN_JAMB && after.second - after.first >= MIN_JAMB) {
                                "Door $index leaves a jamb shorter than ${MIN_JAMB.toInt()} u"
                            }
                            listOf(before, after)
                        } else {
                            listOf(start to end)
                        }
                    }
                }
                pieces.forEach { (start, end) ->
                    walls += when (axis) {
                        WallAxis.HORIZONTAL -> StructureWallSpec(start, line, end, line, halfThickness, door = -1)
                        WallAxis.VERTICAL -> StructureWallSpec(line, start, line, end, halfThickness, door = -1)
                    }
                }
            }
        }
        require(cutDoors.all { it }) { "Every door must open inside one wall piece, clear of other doors" }
    }
    extraWalls.forEach { walls += StructureWallSpec(it.ax, it.ay, it.bx, it.by, it.halfThickness, door = -1) }
    doors.forEachIndexed { index, door ->
        walls += when (door.axis) {
            WallAxis.HORIZONTAL -> StructureWallSpec(door.gapStart, door.line, door.gapEnd, door.line, halfThickness, index)
            WallAxis.VERTICAL -> StructureWallSpec(door.line, door.gapStart, door.line, door.gapEnd, halfThickness, index)
        }
    }
    return walls.toImmutableList()
}

private class BoundaryEdge(val axis: WallAxis, val line: Float, val lo: Float, val hi: Float)

/** Sorts [edges] by start and joins each interval into the previous one when it starts at or before its end. */
private fun mergedIntervals(edges: List<BoundaryEdge>): List<Pair<Float, Float>> {
    val merged = ArrayList<Pair<Float, Float>>()
    edges.sortedWith(compareBy({ it.lo }, { it.hi })).forEach { edge ->
        val last = merged.lastOrNull()
        if (last != null && edge.lo <= last.second) {
            merged[merged.lastIndex] = last.first to maxOf(last.second, edge.hi)
        } else {
            merged += edge.lo to edge.hi
        }
    }
    return merged
}

private const val MIN_ROOM_SIDE: Float = 400f
private const val MAX_GARRISON_ENTRIES: Int = 4
private const val MIN_DOOR_WIDTH: Float = 160f
private const val MAX_DOOR_WIDTH: Float = 400f
private const val MIN_JAMB: Float = 40f
private const val MIN_WALL_HALF_THICKNESS: Float = 12f
private const val MAX_WALL_HALF_THICKNESS: Float = 60f
private const val MIN_CAPSULE_HALF_THICKNESS: Float = 12f
private const val MAX_CAPSULE_HALF_THICKNESS: Float = 80f
private const val MAX_TEMPLATE_NAME_LENGTH: Int = 32
private const val FOOTPRINT_CENTRE_TOLERANCE: Float = 1f
private const val MAX_OBSTACLE_CLEARANCE: Float = 600f

/** The edge-marker distance readout shows at most four digits. */
private const val MAX_SIGNAL_RANGE: Float = 9_990f
private const val LEAVE_MARGIN: Float = 300f
private const val MIN_ENTRY_INSET: Float = 48f
private const val MAX_ENTRY_INSET: Float = 200f
private const val MAX_ENGAGE_CUTOFF_SECONDS: Float = 120f
private const val MAX_GARRISON_GATE_LEAD_SECONDS: Float = 300f
private const val MIN_GARRISON_SCALE: Float = 0.25f
private const val MAX_GARRISON_SCALE: Float = 3f
private const val MAX_WALL_DAMAGE_SCALE: Float = 2f
