// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DUNGEON_ASPECT_SALVAGE_MATTER
import kinetickk.ball.content.api.SiteReward
import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructureRewardKind
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.roomAt
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The dungeon runtime, the single writer of MutableGameState.dungeon (with DungeonRoomFight.kt and DungeonHazards.kt for
 * the sealed room's fight), lastAnnouncedStructureId and (through markSpent) the run-long spent list. One dungeon at a
 * time, one sealed room at a time:
 * - entering an unspent structure's footprint holds it as the dungeon (banner once per structure, its discovery);
 * - entering a room with a garrison seals its doors and spawns its first wave as a room encounter (all or nothing inside
 *   the enemy cap; a refusal leaves the room dormant and open); a room without one clears on entry;
 * - while sealed, later waves land (the final room's last one with its guardian) and the room's hazards strike; once
 *   every wave landed and no enemy the room's encounter owns lives it clears: its doors open and locks open once their
 *   rooms are clear;
 * - clearing the final room pays the template's rewards through existing channels, spends the structure and
 *   disintegrates it (StructureDisintegration);
 * - leaving beyond the leave radius (or into another footprint) after any fight abandons, spends and disintegrates it
 *   without reward, before any fight drops it silently; the Architect and a world layer switch collapse it silently.
 * Garrison kinds and positions (DungeonGarrison.kt) draw only from a hash of the run seed, the structure cell, the room
 * and the wave, never from a run stream; nothing here allocates per step outside a seal, a wave, a clear or a finish.
 */

/** A sealed room's encounter tag: the structure id with the room index in its free low byte. */
internal fun roomEncounterTag(structureId: Long, room: Int): Long = encounterTag(EncounterOwner.ROOM, structureId or room.toLong())

/**
 * The dungeon step, after the sites: the Architect's collapse, entry, leaving, the sealed room's upkeep, engagement. A
 * seal, or its end however it comes, switches the director's habitat, and the event it planned keeps to the new one.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateStructures(delta: Float) {
    val interior = structureEngagementActive()
    stepDungeon()
    if (structureEngagementActive() != interior) keepPendingEventInHabitat()
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
    val room = template.roomAt(coreX - current.x, coreY - current.y, current.rotation, policy.entryInset)
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
 * The step after the Architect arrives, once the dungeon collapsed silently: every structure of the window shatters
 * and the window empties (the footprints stay clearings).
 */
private fun MutableGameState.collapseStructuresForArchitect() {
    disintegrateWindowStructures()
    refreshStructureField(obstacleField.centerChunkX, obstacleField.centerChunkY)
}

/** The first structure of the window whose footprint holds the Core becomes the dungeon unless the run spent it. */
private fun MutableGameState.enterStructure() {
    val structures = structureField.structures
    for (index in structures.indices) {
        val structure = structures[index]
        if (abs(coreX - structure.x) > structure.halfWidth || abs(coreY - structure.y) > structure.halfHeight) continue
        if (isSpent(structure.id)) continue
        dungeon = DungeonState(structure.id, structure.template, structure.rotation, structure.x, structure.y, chestCharges = structure.chestCharges)
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
 * and disintegrates (abandoned, with its notice when [announce]); one that never did stays armed for a later visit.
 */
private fun MutableGameState.dropDungeon(current: DungeonState, announce: Boolean) {
    if (current.engagedRoom >= 0) endEncounter(roomEncounterTag(current.structureId, current.engagedRoom), despawn = true)
    if (current.engaged) {
        markSpent(current.structureId)
        if (announce) {
            message = StructureNotice.STRUCTURE_ABANDONED.key
            messageTime = 1.8f
        }
    }
    dungeon = null
    if (current.engaged) retireSpentStructure(current.structureId) else applyStructureDoors(current.structureId)
}

/**
 * The sealed room's fight: its hazards, then its waves; the room clears once every wave landed and no enemy its
 * encounter owns is alive (adds and fragments included, all held in the room by the wall pass). An encounter ended by
 * anyone else (it was abandoned) abandons the dungeon.
 */
private fun MutableGameState.upkeepGarrison(current: DungeonState) {
    val tag = roomEncounterTag(current.structureId, current.engagedRoom)
    if (encounter(tag) == null) {
        dropDungeon(current, announce = true)
        return
    }
    updateRoomHazards(current)
    val held = dungeon ?: return
    if (!upkeepRoomWaves(held, tag)) return
    endEncounter(tag, despawn = false)
    clearRoom(held, held.engagedRoom)
}

/** Rooms engage until [kinetickk.ball.content.api.StructurePolicy.engageCutoffSeconds] before the Architect, one engagement at a time. */
private fun MutableGameState.roomMayEngage(): Boolean =
    elapsed < content.tempo.bossAtSeconds - content.structures.engageCutoffSeconds && !bossSpawned && !engagementActive()

/**
 * Spawns the first wave of [room]'s garrison as one encounter and seals the room. A refused encounter (the enemy cap
 * or the encounter reserve) changes nothing: the room stays dormant and open and tries again on a later step.
 */
private fun MutableGameState.engageRoom(current: DungeonState, room: Int) {
    val policy = content.structures
    val template = policy.template(current.template)
    val spec = template.rooms[room]
    // The cap check first, so a dormant room retried every step plans nothing until its garrison fits.
    if (enemies.size + garrisonSize(spec, wave = 0) > content.rebirth.maxActiveEnemies) return
    val plan = planGarrison(current, room, template, wave = 0)
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
        leadIndex = plan.guardian,
        leadShare = guardianShare(members.getOrNull(plan.guardian)),
    )
    if (!spawnEncounter(request)) return
    val sealed = current.copy(engagedRoom = room, engaged = true, fight = openRoomFight(spec, plan))
    dungeon = sealed
    applyStructureDoors(current.structureId)
    val guardian = plan.guardian >= 0
    message = (if (guardian) StructureNotice.GUARDIAN_AWAKENS else StructureNotice.DOORS_SEALED).key
    messageTime = if (guardian) 2.2f else 1.6f
    emitDungeonMoment(DungeonMomentKind.SEALED, sealed, room)
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
    val next = current.copy(clearedRooms = cleared, engagedRoom = -1, fight = null)
    dungeon = next
    val fought = template.rooms[room].garrison.isNotEmpty()
    if (fought) emitDungeonMoment(if (room == template.finalRoom) DungeonMomentKind.STRUCTURE_CLEARED else DungeonMomentKind.ROOM_CLEARED, next, room)
    if (room == template.finalRoom) {
        finishStructure(next, template)
        return
    }
    applyStructureDoors(current.structureId)
    if (!fought) return
    message = (if (locksOpened) StructureNotice.INNER_DOORS_OPEN else StructureNotice.ROOM_CLEARED).key
    messageTime = if (locksOpened) 2f else 1.4f
}

/**
 * Pays the final room's rewards at its centre, records the Doodle, spends the structure, materialises its Totem chest
 * there and disintegrates the structure.
 */
private fun MutableGameState.finishStructure(current: DungeonState, template: StructureTemplate) {
    val room = template.rooms[template.finalRoom]
    val localX = (room.minX + room.maxX) * 0.5f
    val localY = (room.minY + room.maxY) * 0.5f
    val centreX = current.x + rotateQuarterX(localX, localY, current.rotation)
    val centreY = current.y + rotateQuarterY(localX, localY, current.rotation)
    payStructureRewards(template, centreX, centreY)
    if (template.secret) {
        if (template.id == StructureTemplateId.ARCHITECTS_DOODLE) discoverDoodle() else discoverWorld(template.id.discovery)
        message = template.name.uppercase()
    } else {
        message = StructureNotice.STRUCTURE_CLEARED.key
    }
    messageTime = 2.5f
    markSpent(current.structureId)
    dungeon = null
    materializeTotemChest(current.structureId, centreX, centreY, current.chestCharges)
    retireSpentStructure(current.structureId)
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
