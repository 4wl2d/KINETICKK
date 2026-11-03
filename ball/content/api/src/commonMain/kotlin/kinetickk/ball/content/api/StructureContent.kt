// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
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

/** A sealed room's telegraphed hazard: VENT, a floor vent erupting in a disc; LANCE, a line spanning the room. */
enum class DungeonHazardKind { VENT, LANCE }

/** The room index a door names when it opens to the outside. */
const val STRUCTURE_OUTSIDE: Int = -1

/**
 * [count] enemies of one room's garrison arriving with [wave] (0 lands with the seal, each later wave once the one
 * before thins out); [type] is set exactly for [GarrisonSelector.FIXED]. A [guardian] entry is one fixed ordinary
 * enemy that leads its wave with the policy's guardian integrity (a mini-boss entry is its room's guardian anyway).
 */
data class GarrisonEntry(
    val selector: GarrisonSelector,
    val count: Int,
    val type: EnemyType? = null,
    val placement: GarrisonPlacement = GarrisonPlacement.FLOOR,
    val wave: Int = 0,
    val guardian: Boolean = false,
) {
    init {
        require(count in 1..ContentBounds.MAX_STRUCTURE_GARRISON) {
            "Garrison count must be in 1..${ContentBounds.MAX_STRUCTURE_GARRISON}"
        }
        require((selector == GarrisonSelector.FIXED) == (type != null)) { "Exactly the fixed garrison entries name a kind" }
        require(type != EnemyType.ARCHITECT && type != EnemyType.ELITE && type != EnemyType.HERALD) {
            "A garrison never holds the Architect, an Elite or the Herald"
        }
        require(wave in 0 until ContentBounds.MAX_DUNGEON_WAVES) { "A garrison wave must be in 0..${ContentBounds.MAX_DUNGEON_WAVES - 1}" }
        require(!guardian || (type != null && count == 1)) { "A guardian entry is one fixed enemy" }
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
        for (wave in 0 until ContentBounds.MAX_DUNGEON_WAVES) {
            require(garrison.sumOf { if (it.wave == wave) it.count else 0 } <= ContentBounds.MAX_STRUCTURE_GARRISON) {
                "A room wave garrisons at most ${ContentBounds.MAX_STRUCTURE_GARRISON} enemies"
            }
        }
        require((0 until waves).all { wave -> garrison.any { it.wave == wave } }) { "A room's waves follow each other from wave 0" }
        require(garrison.count { it.guardian } <= 1) { "A room has at most one guardian entry" }
    }

    /** Waves the garrison arrives in (0 for a passage). */
    val waves: Int get() = if (garrison.isEmpty()) 0 else garrison.maxOf { it.wave } + 1

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
        require(halfThickness in MIN_CAPSULE_HALF_THICKNESS..ContentBounds.MAX_STRUCTURE_WALL_HALF_THICKNESS) {
            "Capsule half thickness must be in 12..${ContentBounds.MAX_STRUCTURE_WALL_HALF_THICKNESS.toInt()}"
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

enum class StructureRewardKind { RELIC, KEY, MATTER, DATA, REPAIR, ITEM_OFFER, ASPECT }

/** Base Matter replacing an already owned aspect or an aspect refused by the run's boon cap. */
const val DUNGEON_ASPECT_SALVAGE_MATTER: Int = 40

/** One payout of a structure's final room through an existing reward channel. */
data class StructureReward(
    val kind: StructureRewardKind,
    val amount: Int = 1,
    val rarityFloor: ItemRarity? = null,
    val boonId: Int? = null,
) {
    init {
        val amounts = when (kind) {
            StructureRewardKind.RELIC, StructureRewardKind.KEY, StructureRewardKind.REPAIR -> 1..3
            StructureRewardKind.DATA -> 1..16
            StructureRewardKind.MATTER -> 1..400
            StructureRewardKind.ITEM_OFFER, StructureRewardKind.ASPECT -> 1..1
        }
        require(amount in amounts) { "A $kind reward amount must be in $amounts" }
        require(if (kind == StructureRewardKind.ASPECT) boonId in RunBoonDefinition.ASPECT_IDS else boonId == null) {
            "Only an aspect reward names a dungeon aspect in 64..69"
        }
        if (kind == StructureRewardKind.ITEM_OFFER) {
            val floor = rarityFloor
            require(floor == ItemRarity.UNCOMMON || floor == ItemRarity.RARE || floor == ItemRarity.EPIC) {
                "An item offer floors its rarity at Uncommon, Rare or Epic"
            }
        } else {
            require(rarityFloor == null) { "Only an item offer carries a rarity floor" }
        }
    }

    /**
     * English content phrase shown on the dungeon panel (localized at presentation), short like the HUD's other reward
     * words: the panel joins up to six on one line, which must stay legible on a phone. An item offer names its floor.
     */
    val label: String
        get() = when (kind) {
            StructureRewardKind.RELIC -> "Relic"
            StructureRewardKind.KEY -> "Key"
            StructureRewardKind.MATTER -> "Matter"
            StructureRewardKind.DATA -> "Data"
            StructureRewardKind.REPAIR -> "Repair"
            StructureRewardKind.ASPECT -> dungeonAspectName(checkNotNull(boonId))
            StructureRewardKind.ITEM_OFFER -> when (rarityFloor) {
                ItemRarity.UNCOMMON -> "Uncommon item"
                ItemRarity.RARE -> "Rare item"
                ItemRarity.EPIC -> "Epic item"
                // Validation forbids every other floor; the branch keeps the mapping total.
                else -> "Item"
            }
        }
}

/** Run notices the dungeon runtime sends (English keys localized at presentation; a test iterates them). */
enum class StructureNotice(val key: String) {
    DOORS_SEALED("DOORS SEALED"),
    GUARDIAN_AWAKENS("GUARDIAN AWAKENS"),
    REINFORCEMENTS("REINFORCEMENTS"),
    ROOM_CLEARED("ROOM CLEARED"),
    INNER_DOORS_OPEN("INNER DOORS OPEN"),
    STRUCTURE_CLEARED("STRUCTURE CLEARED"),
    STRUCTURE_ABANDONED("STRUCTURE ABANDONED"),
    TOTEM_CHEST_SALVAGED("TOTEM CHEST SALVAGED"),
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
    /** Shared affinity for the palette, trial and its lasting reward. */
    val element: StatusElement? = null,
    val trialDescription: String = "",
    /** Themed alternatives for habitat garrison entries. Fixed guardians and room counts are preserved. */
    val garrisonTypes: ImmutableList<EnemyType> = immutableListOf(),
    /** Chance (0..1) that a placed structure of this template rolls a Totem chest; 0 for a secret template. */
    val totemChestChance: Float = 0f,
    /** Fewest charges its chest rolls (1..[ContentBounds.MAX_TOTEM_CHEST_CHARGES]): a lower roll is raised to this. */
    val totemChestMinCharges: Int = 1,
    /** The hazard its sealed rooms telegraph (the final room alternates it with the other kind); null: none. */
    val hazard: DungeonHazardKind? = null,
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
        require(trialDescription.length <= 240) { "A dungeon trial description must stay short" }
        require(garrisonTypes.size <= 4 && garrisonTypes.distinct().size == garrisonTypes.size) { "A dungeon names at most four distinct garrison kinds" }
        require(garrisonTypes.none { it == EnemyType.ARCHITECT || it == EnemyType.ELITE || it == EnemyType.HERALD }) { "Themed garrisons cannot select a boss" }
        require(weight.isFinite() && weight >= 0f) { "$name weight must be finite and non-negative" }
        require(minimumDistance.isFinite() && minimumDistance >= 0f) { "$name minimum distance must be non-negative" }
        require(totemChestChance.isFinite() && totemChestChance in 0f..1f) { "$name Totem chest chance must be in 0..1" }
        require(totemChestMinCharges in 1..ContentBounds.MAX_TOTEM_CHEST_CHARGES) {
            "$name Totem chest minimum charges must be in 1..${ContentBounds.MAX_TOTEM_CHEST_CHARGES}"
        }
        require(!secret || (totemChestChance == 0f && hazard == null)) { "$name is secret: it pays no Totem chest and telegraphs no hazard" }
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
 * templates in [StructureTemplateId] order. [enabled] false plays a world without structures (the harness arms).
 */
data class StructurePolicy(
    val enabled: Boolean = true,
    val cellSize: Float = 4_800f,
    val cellMargin: Float = 300f,
    val minimumDistance: Float = 6_000f,
    val fullChanceDistance: Float = 14_000f,
    val maximumChance: Float = 0.12f,
    /** A seed chooses this many weighted, non-secret templates for the entire run, before distance gates. */
    val templatesPerRun: Int = 3,
    /** Each secret template exists only in this share of seeds; its placement still uses its ordinary weight. */
    val secretTemplateChance: Float = 0.2f,
    /** Chance scale per [WorldLayer] ordinal (BASE, ZENITH, NADIR, ARCHIVE). */
    val layerChance: ImmutableList<Float> = immutableListOf(1f, 0f, 0f, 0f),
    val obstacleClearance: Float = 160f,
    val signalRange: Float = 6_000f,
    val leaveRadius: Float = 2_400f,
    val entryInset: Float = 80f,
    val engageCutoffSeconds: Float = 45f,
    val garrisonGateLeadSeconds: Float = 90f,
    /** Ordinary entries' counts (× the Rebirth tier's enemy-cap multiplier, like the open world's cap). */
    val garrisonScale: Float = 1f,
    /** Room enemies take the open world's integrity and damage; these levers stay for calibration. */
    val garrisonHealthMultiplier: Float = 1f,
    /** Damage dealt by room-owned enemies, including their adds; world and site enemies keep their own balance. */
    val garrisonDamageMultiplier: Float = 1f,
    /** The next wave comes once at most this share of the last wave's members lives, or [waveIntervalSeconds] after it. */
    val waveTriggerShare: Float = 0.34f,
    val waveIntervalSeconds: Float = 18f,
    /** Seconds the next wave's breach points glow before it lands. */
    val waveTelegraphSeconds: Float = 1.2f,
    /** Integrity of an ordinary guardian entry (× its kind's spawn integrity); a mini-boss keeps its own. */
    val guardianIntegrityScale: Float = 6f,
    /** From this Rebirth tier a room member is a champion with `perTier × tier` chance. */
    val garrisonChampionFromTier: Int = 2,
    val garrisonChampionChancePerTier: Float = 0.04f,
    val directorRateWhileSealed: Float = 0.35f,
    val wallDamageScale: Float = 1f,
    val hazards: DungeonHazardPolicy = DungeonHazardPolicy(),
    val totemChest: TotemChestPolicy = TotemChestPolicy(),
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
        require(templatesPerRun in 1..ContentBounds.MAX_STRUCTURE_TEMPLATES) { "A run must select a bounded template pool" }
        require(secretTemplateChance in 0f..1f) { "Secret template chance must be in 0..1" }
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
        require(garrisonScale in MIN_GARRISON_SCALE..MAX_GARRISON_SCALE) { "Garrison scale must be in 0.25..3" }
        require(waveTriggerShare in 0f..1f) { "Wave trigger share must be in 0..1" }
        require(waveIntervalSeconds in 4f..120f && waveTelegraphSeconds in 0.4f..3f) { "Wave times must be in 4..120 s and 0.4..3 s" }
        require(guardianIntegrityScale in 1f..12f) { "Guardian integrity scale must be in 1..12" }
        val topChampions = garrisonChampionChancePerTier * ContentBounds.MAX_REBIRTH_LEVEL
        require(garrisonChampionFromTier >= 0 && topChampions in 0f..0.6f) { "Room champions reach at most 0.6 at the top tier" }
        require(garrisonHealthMultiplier in 0.5f..2f) { "Garrison health multiplier must be in 0.5..2" }
        require(garrisonDamageMultiplier in 0.5f..1.5f) { "Garrison damage multiplier must be in 0.5..1.5" }
        require(directorRateWhileSealed in 0f..1f) { "Sealed director rate must be in 0..1" }
        require(wallDamageScale in 0f..MAX_WALL_DAMAGE_SCALE) { "Wall damage scale must be in 0..2" }
        require(templates.any { it.weight > 0f && it.minimumDistance <= minimumDistance + cellSize }) {
            "At least one weighted template must be placeable within a cell of the minimum distance"
        }
    }
}

/**
 * The sealed rooms' hazards: [firstDelaySeconds] after the seal and then every [periodSeconds] a cycle telegraphs its
 * vents (one under the Core, the rest by the room's hash) or lances (one through the Core) for [telegraphSeconds];
 * then each strikes once, a hostile blast of [coreDamage] (hostile run growth and Rebirth apply) on a Core it meets,
 * and shows its strike for [strikeSeconds]. The final room adds one hazard per cycle, and so does every
 * [extraPerTiers]th Rebirth tier (at most [ContentBounds.MAX_DUNGEON_HAZARDS]). [enabled] false: none anywhere.
 */
data class DungeonHazardPolicy(
    val enabled: Boolean = true,
    val firstDelaySeconds: Float = 1.5f,
    val periodSeconds: Float = 3.5f,
    val telegraphSeconds: Float = 1.2f,
    val strikeSeconds: Float = 0.35f,
    val coreDamage: Float = 12f,
    val ventRadius: Float = 120f,
    val ventsPerCycle: Int = 2,
    val lanceHalfWidth: Float = 36f,
    val lancesPerCycle: Int = 1,
    val extraPerTiers: Int = 1,
) {
    init {
        require(firstDelaySeconds in 0f..30f && telegraphSeconds in 0.4f..3f && strikeSeconds in 0.05f..1f && extraPerTiers >= 1) {
            "Hazard delay, telegraph and strike times must be in 0..30, 0.4..3 and 0.05..1 s, extras need a tier"
        }
        require(periodSeconds in telegraphSeconds + strikeSeconds..30f) { "A hazard cycle holds its telegraph and strike" }
        require(coreDamage in 0f..100f && ventRadius in 40f..240f && lanceHalfWidth in 12f..80f) { "Hazard damage and sizes out of range" }
        val most = ContentBounds.MAX_DUNGEON_HAZARDS - 1
        require(ventsPerCycle in 1..most && lancesPerCycle in 1..most) { "A cycle holds 1..$most hazards before the final room's extra one" }
    }
}

/**
 * The dungeon reward chest: its charge roll, contact, re-arm and salvage. [enabled] false rolls no chest anywhere
 * (measurement harnesses switch it off with `structures.copy(totemChest = TotemChestPolicy(enabled = false))`).
 */
data class TotemChestPolicy(
    val enabled: Boolean = true,
    /** Weights of 1, 2 and 3 charges (index + 1), before a template's minimum. */
    val chargeWeights: ImmutableList<Float> = immutableListOf(55f, 33f, 12f),
    /** Radius the Core's swept path must meet to open a charge (the plates at 2.2 scale are 66 u wide). */
    val contactRadius: Float = 64f,
    /** Run seconds after a choice closes before the next charge can open. */
    val rearmSeconds: Float = 0.35f,
    /** Run seconds after it materialises before a chest can open (its entrance). */
    val materializeSeconds: Float = 0.6f,
    /** Base Matter per charge when a chest finds the list full (Matter bonuses apply). */
    val salvageMatterPerCharge: Int = 40,
) {
    init {
        require(chargeWeights.size == ContentBounds.MAX_TOTEM_CHEST_CHARGES) {
            "Totem chest charge weights name 1..${ContentBounds.MAX_TOTEM_CHEST_CHARGES} charges"
        }
        require(chargeWeights.all { it.isFinite() && it >= 0f } && chargeWeights.sum() > 0f) {
            "Totem chest charge weights must be finite, non-negative and not all zero"
        }
        require(contactRadius in 24f..160f) { "Totem chest contact radius must be in 24..160" }
        require(rearmSeconds in 0.1f..2f) { "Totem chest re-arm must be in 0.1..2 s" }
        require(materializeSeconds in 0f..2f) { "Totem chest materialise delay must be in 0..2 s" }
        require(salvageMatterPerCharge in 0..400) { "Totem chest salvage must be in 0..400 Matter per charge" }
    }
}

/**
 * Bootstrap cross-check against the roster and the obstacle window: every fixed garrison kind is an ordinary kind or
 * a mini-boss other than the Herald, a room garrisons at most one mini-boss (its garrison is one encounter, which
 * holds at most one boss), an ordinary STRUCTURE kind opens at tier 0 from the first second, the obstacle window spans
 * at most three cells per axis (so at most nine structures) and keeps the dungeon's structure inside it.
 */
fun validateStructurePolicy(policy: StructurePolicy, enemies: List<EnemyDefinition>, obstacles: ObstacleTuning) {
    policy.templates.forEach { template ->
        template.garrisonTypes.forEach { type ->
            require(enemies.firstOrNull { it.type == type }?.rank == EnemyRank.ORDINARY) {
                "${template.name} themed garrison kinds must be defined ordinary enemies"
            }
        }
        template.rooms.forEach { room ->
            val miniBosses = room.garrison.sumOf { entry ->
                if (enemies.firstOrNull { it.type == entry.type }?.rank == EnemyRank.MINI_BOSS) entry.count else 0
            }
            require(miniBosses <= 1) { "${template.name} garrisons $miniBosses mini-bosses in one room; at most one mini-boss" }
            require(miniBosses == 0 || room.garrison.none { it.guardian }) { "${template.name} has a mini-boss and a guardian in one room" }
            room.garrison.forEach { entry ->
                val type = entry.type ?: return@forEach
                val definition = enemies.firstOrNull { it.type == type }
                requireNotNull(definition) { "${template.name} garrisons $type, which the roster does not define" }
                require(!entry.guardian || definition.rank == EnemyRank.ORDINARY) { "${template.name} guardian entries are ordinary kinds" }
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

private const val MIN_ROOM_SIDE: Float = 400f
private const val MAX_GARRISON_ENTRIES: Int = 8
private const val MIN_DOOR_WIDTH: Float = 160f
private const val MAX_DOOR_WIDTH: Float = 400f
private const val MIN_WALL_HALF_THICKNESS: Float = 12f
private const val MAX_WALL_HALF_THICKNESS: Float = 60f
private const val MIN_CAPSULE_HALF_THICKNESS: Float = 12f
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
