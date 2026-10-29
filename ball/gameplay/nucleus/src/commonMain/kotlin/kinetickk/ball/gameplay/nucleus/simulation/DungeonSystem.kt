// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DUNGEON_ASPECT_SALVAGE_MATTER
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.GarrisonEntry
import kinetickk.ball.content.api.GarrisonPlacement
import kinetickk.ball.content.api.GarrisonSelector
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructureRewardKind
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The dungeon runtime, the single writer of MutableGameState.dungeon, lastAnnouncedStructureId and (through markSpent)
 * the spent list. One dungeon at a time, one sealed room at a time:
 * - entering an unspent structure's footprint holds it as the dungeon (banner once per structure, its discovery);
 * - entering a room with a garrison seals its doors and spawns the garrison as a room encounter (all or nothing inside
 *   the enemy cap; a refusal leaves the room dormant and open); a room without one clears on entry;
 * - killing every enemy the room's encounter owns clears it: its doors open and locks open once their rooms are clear;
 * - clearing the final room pays the template's rewards through existing channels and spends the structure;
 * - leaving beyond the leave radius (or into another footprint) after any fight abandons and spends it without reward,
 *   before any fight drops it silently; the Architect and a world layer switch collapse it silently.
 * Garrison kinds and positions draw only from a hash of the run seed, the structure cell and the room, never from a run
 * stream; nothing here allocates per step outside a seal, a clear or a finish.
 */

/** Salt of the garrison hash (registered in the structures lane notes). */
internal const val GARRISON_SALT: Long = 0x57C0_C7E5_5EED_0003L

/** A sealed room's encounter tag: the structure id with the room index in its free low byte. */
internal fun roomEncounterTag(structureId: Long, room: Int): Long = encounterTag(EncounterOwner.ROOM, structureId or room.toLong())

/**
 * The dungeon step, after the sites: the Architect's collapse, entry, leaving, the sealed room's upkeep, engagement. A
 * seal, or its end however it comes, switches the director's habitat, and the event it planned keeps to the new one.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateStructures(delta: Float) {
    val interior = structureInteriorActive()
    stepDungeon()
    if (structureInteriorActive() != interior) keepPendingEventInHabitat()
}

private fun MutableGameState.stepDungeon() {
    val policy = content.structures
    if (!policy.enabled || phase != GamePhase.RUNNING) return
    if (bossSpawned) {
        // The dungeon collapses on its own condition: a window that moved to another chunk earlier in this step has
        // already rebuilt itself empty, so its flag no longer says whether the collapse ran.
        dungeon?.let { dropDungeon(it, announce = false) }
        if (!structureField.bossCollapsed) collapseStructuresForArchitect()
        return
    }
    val current = dungeon
    if (current == null) {
        enterStructure()
        return
    }
    val dx = coreX - current.x
    val dy = coreY - current.y
    if (dx * dx + dy * dy > policy.leaveRadius * policy.leaveRadius || coreInsideAnotherStructure(current.structureId)) {
        dropDungeon(current, announce = true)
        return
    }
    if (current.engagedRoom >= 0) {
        upkeepGarrison(current)
        return
    }
    val template = policy.template(current.template)
    val room = roomAt(template, current.rotation, current.x, current.y, coreX, coreY, policy.entryInset)
    if (room < 0 || current.clearedRooms and (1 shl room) != 0) return
    if (template.rooms[room].garrison.isEmpty()) {
        clearRoom(current, room)
        return
    }
    if (roomMayEngage()) engageRoom(current, room)
}

/** A world layer switch collapses the dungeon silently: its garrison leaves without reward, a fought one is spent. */
internal fun MutableGameState.collapseDungeonForLayerChange() {
    val current = dungeon ?: return
    dropDungeon(current, announce = false)
}

/**
 * The step after the Architect arrives, once the dungeon collapsed silently: every structure of the window bursts at
 * its centre and the window empties (the footprints stay clearings).
 */
private fun MutableGameState.collapseStructuresForArchitect() {
    val structures = structureField.structures
    for (index in structures.indices) {
        val structure = structures[index]
        shockwave(structure.x, structure.y, 0.6f, 300f, 3)
        burst(structure.x, structure.y, 24, 3)
    }
    refreshStructureField(obstacleField.centerChunkX, obstacleField.centerChunkY)
}

/**
 * The first structure of the window whose footprint holds the Core becomes the dungeon unless the spent list holds it
 * (one the list forgot arms again, its doors back as the lock rules say).
 */
private fun MutableGameState.enterStructure() {
    val structures = structureField.structures
    for (index in structures.indices) {
        val structure = structures[index]
        if (abs(coreX - structure.x) > structure.halfWidth || abs(coreY - structure.y) > structure.halfHeight) continue
        if (isSpent(structure.id)) continue
        dungeon = DungeonState(structure.id, structure.template, structure.rotation, structure.x, structure.y, enteredAt = elapsed)
        applyStructureDoors(structure.id)
        val template = content.structures.template(structure.template)
        if (template.secret) return
        discoverWorld(structure.template.discovery)
        if (structure.id != lastAnnouncedStructureId) {
            message = template.name.uppercase()
            messageTime = 2.2f
            lastAnnouncedStructureId = structure.id
        }
        return
    }
}

/** True when the Core stands inside the footprint of a windowed structure other than [structureId]. */
private fun MutableGameState.coreInsideAnotherStructure(structureId: Long): Boolean {
    val structures = structureField.structures
    for (index in structures.indices) {
        val structure = structures[index]
        if (structure.id == structureId) continue
        if (abs(coreX - structure.x) <= structure.halfWidth && abs(coreY - structure.y) <= structure.halfHeight) return true
    }
    return false
}

/**
 * Lets the dungeon go: a live garrison is removed without kills or reward and a structure that saw a fight is spent
 * (abandoned, with its notice when [announce]); one that never did stays armed for a later visit.
 */
private fun MutableGameState.dropDungeon(current: DungeonState, announce: Boolean) {
    if (current.engagedRoom >= 0) endEncounter(roomEncounterTag(current.structureId, current.engagedRoom), despawn = true)
    if (current.engaged) {
        markSpent(current.structureId, SPENT_FLAG)
        if (announce) {
            message = StructureNotice.STRUCTURE_ABANDONED.key
            messageTime = 1.8f
        }
    }
    dungeon = null
    applyStructureDoors(current.structureId)
}

/**
 * The sealed room's garrison: members still alive are kept for the panel; the room clears once no enemy its encounter
 * owns is alive (adds and fragments included, all held in the room by the wall pass). An encounter ended by anyone
 * else (it was abandoned) abandons the dungeon.
 */
private fun MutableGameState.upkeepGarrison(current: DungeonState) {
    val tag = roomEncounterTag(current.structureId, current.engagedRoom)
    if (encounter(tag) == null) {
        dropDungeon(current, announce = true)
        return
    }
    val ids = current.garrisonIds
    var live = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && enemy.id in ids) live++
    }
    if (live != ids.size) {
        dungeon = current.copy(garrisonIds = ids.filter { id -> enemies.any { it.id == id && it.alive } }.toImmutableList())
    }
    if (liveEncounterMembers(tag) > 0) return
    endEncounter(tag, despawn = false)
    clearRoom(current, current.engagedRoom)
}

/** Rooms engage until [kinetickk.ball.content.api.StructurePolicy.engageCutoffSeconds] before the Architect, one engagement at a time. */
private fun MutableGameState.roomMayEngage(): Boolean =
    elapsed < content.tempo.bossAtSeconds - content.structures.engageCutoffSeconds && !bossSpawned && !engagementActive()

/**
 * Spawns [room]'s garrison as one encounter and seals the room. A refused encounter (the enemy cap or the encounter
 * reserve) changes nothing: the room stays dormant and open and tries again on a later step.
 */
private fun MutableGameState.engageRoom(current: DungeonState, room: Int) {
    val policy = content.structures
    val template = policy.template(current.template)
    val spec = template.rooms[room]
    // The cap check first, so a dormant room retried every step plans nothing until its garrison fits.
    if (enemies.size + garrisonSize(spec) > content.rebirth.maxActiveEnemies) return
    val plan = planGarrison(current, room, template)
    val members = plan.members
    val localX = (spec.minX + spec.maxX) * 0.5f
    val localY = (spec.minY + spec.maxY) * 0.5f
    val tag = roomEncounterTag(current.structureId, room)
    val request = EncounterRequest(
        tag = tag,
        centerX = current.x + rotateQuarterX(localX, localY, current.rotation),
        centerY = current.y + rotateQuarterY(localX, localY, current.rotation),
        areaRadius = hypot(spec.maxX - spec.minX, spec.maxY - spec.minY) * 0.5f,
        members = members,
        integrityShare = policy.garrisonHealthMultiplier,
        abandonDistance = policy.leaveRadius,
        evictOrdinary = false,
        announce = false,
        positions = plan.positions,
    )
    if (!spawnEncounter(request)) return
    val ids = ArrayList<Int>(members.size)
    for (index in enemies.indices) if (enemies[index].ownerTag == tag) ids += enemies[index].id
    dungeon = current.copy(engagedRoom = room, engaged = true, garrisonIds = ids.toImmutableList(), garrisonTotal = ids.size)
    applyStructureDoors(current.structureId)
    val guardian = members.any { it.profile.rank == EnemyRank.MINI_BOSS }
    message = (if (guardian) StructureNotice.GUARDIAN_AWAKENS else StructureNotice.DOORS_SEALED).key
    messageTime = if (guardian) 2.2f else 1.6f
    val doors = template.doors
    for (index in doors.indices) {
        val door = doors[index]
        if (door.roomA != room && door.roomB != room) continue
        val doorX = if (door.axis == WallAxis.HORIZONTAL) door.center else door.line
        val doorY = if (door.axis == WallAxis.HORIZONTAL) door.line else door.center
        shockwave(
            current.x + rotateQuarterX(doorX, doorY, current.rotation),
            current.y + rotateQuarterY(doorX, doorY, current.rotation),
            0.3f, 120f, 4,
        )
    }
}

/**
 * Marks [room] cleared with its seal lifted. The final room finishes the structure; another room opens its doors and
 * announces the clear (inner doors when the clear opened a lock), a passage silently.
 */
private fun MutableGameState.clearRoom(current: DungeonState, room: Int) {
    val template = content.structures.template(current.template)
    val cleared = current.clearedRooms or (1 shl room)
    var locksOpened = false
    val doors = template.doors
    for (index in doors.indices) {
        val lock = doors[index].lockRooms
        if (lock != 0 && current.clearedRooms and lock != lock && cleared and lock == lock) locksOpened = true
    }
    val next = current.copy(clearedRooms = cleared, engagedRoom = -1, garrisonIds = immutableListOf())
    dungeon = next
    if (room == template.finalRoom) {
        finishStructure(next, template)
        return
    }
    applyStructureDoors(current.structureId)
    if (template.rooms[room].garrison.isEmpty()) return
    message = (if (locksOpened) StructureNotice.INNER_DOORS_OPEN else StructureNotice.ROOM_CLEARED).key
    messageTime = if (locksOpened) 2f else 1.4f
}

/** Pays the final room's rewards at its centre, records the Doodle, spends the structure and opens every door. */
private fun MutableGameState.finishStructure(current: DungeonState, template: StructureTemplate) {
    val room = template.rooms[template.finalRoom]
    val localX = (room.minX + room.maxX) * 0.5f
    val localY = (room.minY + room.maxY) * 0.5f
    payStructureRewards(
        template,
        current.x + rotateQuarterX(localX, localY, current.rotation),
        current.y + rotateQuarterY(localX, localY, current.rotation),
    )
    if (template.secret) {
        if (template.id == StructureTemplateId.ARCHITECTS_DOODLE) discoverDoodle() else discoverWorld(template.id.discovery)
        message = template.name.uppercase()
    } else {
        message = StructureNotice.STRUCTURE_CLEARED.key
    }
    messageTime = 2.5f
    markSpent(current.structureId, SPENT_FLAG)
    dungeon = null
    applyStructureDoors(current.structureId)
}

/**
 * The template's rewards at ([x], [y]) through existing channels: Relic, Key and repair pickups in a row, Data on a
 * golden-angle ring, Matter through the run's Matter gain, a floored item offer through the floor-offer queue (a full
 * queue salvages Matter), and unique lasting Aspects. An owned Aspect or a full boon roster salvages 40 base Matter.
 * No stream draw.
 */
private fun MutableGameState.payStructureRewards(template: StructureTemplate, x: Float, y: Float) {
    val rewards = template.rewards
    for (index in rewards.indices) {
        val reward = rewards[index]
        when (reward.kind) {
            StructureRewardKind.RELIC -> repeat(reward.amount) { k -> addPickup(Pickup(PickupType.RELIC, x + 15f * k, y - 8f)) }
            StructureRewardKind.KEY -> repeat(reward.amount) { k -> addPickup(Pickup(PickupType.KEY, x - 15f * k, y + 8f)) }
            StructureRewardKind.REPAIR -> repeat(reward.amount) { k -> addPickup(Pickup(PickupType.REPAIR, x + 30f * k, y + 20f)) }
            StructureRewardKind.DATA -> repeat(reward.amount) { k ->
                val angle = k * GARRISON_GOLDEN_ANGLE
                val speed = 60f + 10f * (k % 4)
                addPickup(Pickup(PickupType.DATA, x, y, cos(angle) * speed, sin(angle) * speed))
            }
            StructureRewardKind.MATTER -> grantMatter(reward.amount.toFloat())
            StructureRewardKind.ITEM_OFFER -> grantSiteReward(SiteReward(itemOfferFloor = reward.rarityFloor), x, y, 0L)
            StructureRewardKind.ASPECT -> {
                val id = checkNotNull(reward.boonId)
                if (id in runBoons || runBoons.size >= ContentBounds.MAX_RUN_BOONS || content.boon(id) == null) {
                    grantMatter(DUNGEON_ASPECT_SALVAGE_MATTER.toFloat())
                } else {
                    applyRunBoon(id)
                }
            }
        }
    }
    shockwave(x, y, 0.8f, 300f, 3)
    burst(x, y, 40, 3)
}

// ---------------------------------------------------------------------------------------------
// Garrison composition (kinds and positions from the garrison hash only).

/** A room's garrison: kinds in entry order and one run-local (x, y) per member. */
private class GarrisonPlan(val members: List<EnemyType>, val positions: FloatArray)

/**
 * [room]'s garrison. Counts: an ordinary entry's count scaled by the policy (at least one), the Rebirth extras on the
 * first habitat entry, a mini-boss entry unscaled; at most [MAX_GARRISON] in all, the last ones dropped. Habitat
 * members pick a template's themed kind, or a STRUCTURE kind at the run's threat time plus the gate lead for a template
 * without a theme. Positions: FLOOR members take points of
 * a 3 × 3 lattice inset into the room, four points apart per member from a hashed start, skipping points near the
 * Core; WALL members take the inner corners (diagonals first), then side midpoints clear of doors, then fall back to
 * FLOOR; a CENTER member takes the room centre, or a point beyond it from a Core standing close; each point is nudged
 * off walls. Every draw comes from the garrison hash (the lattice start first, then one per habitat member).
 */
private fun MutableGameState.planGarrison(current: DungeonState, room: Int, template: StructureTemplate): GarrisonPlan {
    val policy = content.structures
    val spec = template.rooms[room]
    val random = HashRandom(
        splitMix64(
            worldHash(runSeed, worldLayer, structureCellX(current.structureId), structureCellY(current.structureId), GARRISON_SALT) xor
                room.toLong(),
        ),
    )
    val start = (random.nextInt() ushr 1) % LATTICE_POINTS
    val extra = garrisonExtra()
    val threat = threatElapsed + policy.garrisonGateLeadSeconds
    val inverse = (4 - current.rotation) and 3
    val coreLocalX = rotateQuarterX(coreX - current.x, coreY - current.y, inverse)
    val coreLocalY = rotateQuarterY(coreX - current.x, coreY - current.y, inverse)
    val members = ArrayList<EnemyType>(MAX_GARRISON)
    val points = FloatArray(MAX_GARRISON * 2)
    val firstHabitat = spec.garrison.indexOfFirst { it.selector == GarrisonSelector.HABITAT }
    var wallMembers = 0
    val entries = spec.garrison
    for (entryIndex in entries.indices) {
        val entry = entries[entryIndex]
        val fixed = entry.type
        repeat(entryCount(entry, if (entryIndex == firstHabitat) extra else 0)) { slot ->
            if (members.size >= MAX_GARRISON) return@repeat
            val member = members.size
            // A themed lead alternates with ordinary habitat supports. Filling every slot with a themed ranged or
            // heavy native crowds out the weak members that keep early rooms approachable.
            val type = fixed ?: if (slot % 2 == 0) pickThemedGarrisonType(template, random, threat)
            else pickHabitatType(EnemyHabitat.STRUCTURE, random.nextFloat(), threatSeconds = threat)
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
    return GarrisonPlan(members, points.copyOf(members.size * 2))
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

/** Members [room] spawns: the planned counts, at most [MAX_GARRISON]. */
private fun MutableGameState.garrisonSize(room: StructureRoom): Int {
    val extra = garrisonExtra()
    val firstHabitat = room.garrison.indexOfFirst { it.selector == GarrisonSelector.HABITAT }
    var total = 0
    for (index in room.garrison.indices) total += entryCount(room.garrison[index], if (index == firstHabitat) extra else 0)
    return minOf(total, MAX_GARRISON)
}

/** Rebirth extras a room's first habitat entry gains: one per [kinetickk.ball.content.api.StructurePolicy.garrisonExtraPerTiers] tiers. */
private fun MutableGameState.garrisonExtra(): Int = rebirthLevel / content.structures.garrisonExtraPerTiers

/** One entry's members: a mini-boss entry as written, any other scaled by the policy (at least one), plus [extra]. */
private fun MutableGameState.entryCount(entry: GarrisonEntry, extra: Int): Int {
    val fixed = entry.type
    if (fixed != null && fixed.profile.rank == EnemyRank.MINI_BOSS) return entry.count + extra
    return max(1, (entry.count * content.structures.garrisonScale).roundToInt()) + extra
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
        spanClamp(coreX + ux * CENTER_CORE_DISTANCE, room.minX + inset, room.maxX - inset),
        spanClamp(coreY + uy * CENTER_CORE_DISTANCE, room.minY + inset, room.maxY - inset),
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

private fun spanClamp(value: Float, low: Float, high: Float): Float =
    if (low > high) (low + high) * 0.5f else value.coerceIn(low, high)

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

/** The Codex discovery of a structure template. */
private val StructureTemplateId.discovery: WorldDiscovery
    get() = when (this) {
        StructureTemplateId.DERELICT_STATION -> WorldDiscovery.DERELICT_STATION
        StructureTemplateId.VAULT -> WorldDiscovery.VAULT
        StructureTemplateId.SPIRE -> WorldDiscovery.SPIRE
        StructureTemplateId.LABYRINTH -> WorldDiscovery.LABYRINTH
        StructureTemplateId.FOUNDRY -> WorldDiscovery.FOUNDRY
        StructureTemplateId.RELIQUARY -> WorldDiscovery.RELIQUARY
        StructureTemplateId.ARCHITECTS_DOODLE -> WorldDiscovery.ARCHITECTS_DOODLE
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
private const val GARRISON_GOLDEN_ANGLE: Float = 2.399963f
