// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructureRewardKind
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WallKind
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dungeon template tour (structures §11.3): every dungeon template in every quarter rotation, placed in every cell
 * by a forced-placement content in a quiet arena without rocks or ambient enemies, flown from 500 u outside its
 * entrance by [DungeonPilot] through `update()`. Each sealed room's garrison is killed through
 * [killGarrisonForTesting] (no combat variance), so the tour reads the dungeon itself: the doors on every input frame
 * (a room's doors seal while it is engaged, every other door stands open unless its lock waits on an uncleared room),
 * every room the route reaches sealed once and the final room last, the final room's rewards through their channels at
 * its centre, the structure spent with every door open, and a route that never hurts the Core on a wall.
 */
class DungeonTemplateTourTest {
    @Test fun derelictStationSealsClearsAndPaysInEveryRotation() = tourEveryRotation(StructureTemplateId.DERELICT_STATION)

    @Test fun vaultOpensItsLockedVaultOnlyAfterBothGalleriesInEveryRotation() = tourEveryRotation(StructureTemplateId.VAULT)

    @Test fun spireClimbsItsFourHallsPastTheFinsInEveryRotation() = tourEveryRotation(StructureTemplateId.SPIRE)

    @Test fun labyrinthRoutesThroughItsPassagesAndSideRoomsInEveryRotation() = tourEveryRotation(StructureTemplateId.LABYRINTH)

    @Test fun foundryCrossesBothHallsBetweenItsPillarsInEveryRotation() = tourEveryRotation(StructureTemplateId.FOUNDRY)

    @Test fun reliquaryOpensItsSanctumOnlyAfterTheFourChapelsInEveryRotation() = tourEveryRotation(StructureTemplateId.RELIQUARY)

    /**
     * The Architect's Doodle is a secret, not a dungeon: reaching its one room records the secret once (the flag, the
     * World discovery, the Doodle's own Matter) and pays the template's 12 Data at the Core; the spent structure never
     * pays again.
     */
    @Test fun theArchitectsDoodleRecordsItsSecretOnceAndPaysItsData() {
        val run = tourState(StructureTemplateId.ARCHITECTS_DOODLE)
        val placement = run.structureAtForTesting(FIRST_CELL_X, TOUR_CELL_Y)
        val template = run.content.structures.template(StructureTemplateId.ARCHITECTS_DOODLE)
        // Inside the inscribed square, clear of the accent slash through the planet.
        val insideX = placement.absoluteX + rotateQuarterX(-200f, -200f, placement.rotation)
        val insideY = placement.absoluteY + rotateQuarterY(-200f, -200f, placement.rotation)
        run.teleportCoreForTesting(insideX, insideY)
        run.quietArenaForTesting()
        val matter = run.runMatter + run.matterFraction.toDouble()
        val before = run.pickups.toList()
        repeat(5) { run.update(0.02f) }
        assertTrue(run.hasSecret(RunSecretFlag.DOODLE_FOUND))
        assertTrue(run.worldDiscoveryMask and WorldDiscovery.ARCHITECTS_DOODLE.bit != 0)
        assertTrue(run.isSpent(placement.id))
        assertNull(run.dungeon)
        val data = template.rewards.filter { it.kind == StructureRewardKind.DATA }.sumOf { it.amount }
        assertEquals(12, data)
        assertEquals(data, run.pickups.count { it.type == PickupType.DATA && before.none { old -> old === it } })
        val doodleMatter = run.content.sites.secrets.doodleReward.matter
        assertEquals(doodleMatter * run.matterGain.toDouble(), run.runMatter + run.matterFraction.toDouble() - matter, 0.01)

        // Out of the leave radius and back in: nothing more.
        val paid = run.runMatter + run.matterFraction.toDouble()
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY + 3_000.0)
        repeat(5) { run.update(0.02f) }
        val after = run.pickups.toList()
        run.teleportCoreForTesting(insideX, insideY)
        repeat(5) { run.update(0.02f) }
        assertNull(run.dungeon)
        assertEquals(paid, run.runMatter + run.matterFraction.toDouble(), 0.01)
        assertEquals(0, run.pickups.count { it.type == PickupType.DATA && after.none { old -> old === it } })
    }

    private fun tourEveryRotation(id: StructureTemplateId) {
        val seen = BooleanArray(4)
        var cellX = FIRST_CELL_X
        while (!seen.all { it }) {
            check(cellX < FIRST_CELL_X + MAX_CELL_SCAN) { "no cell of $id found for every rotation" }
            val placement = tourState(id).structureAtForTesting(cellX, TOUR_CELL_Y)
            if (!seen[placement.rotation]) {
                seen[placement.rotation] = true
                tour(id, cellX)
            }
            cellX++
        }
    }

    /** Flies the structure in ([cellX], [TOUR_CELL_Y]) from outside its entrance to its clear and checks every frame. */
    private fun tour(id: StructureTemplateId, cellX: Int) {
        val run = tourState(id)
        val placement = run.structureAtForTesting(cellX, TOUR_CELL_Y)
        val label = "$id/r${placement.rotation}"
        val template = run.content.structures.template(id)
        val (startX, startY) = outsideEntrance(template, placement.rotation, START_OFFSET)
        run.teleportCoreForTesting(placement.absoluteX + startX, placement.absoluteY + startY)
        run.quietArenaForTesting()
        val pilot = DungeonPilot(NoPoiBot(), seekFrom = 0f)
        val hitReadyAt = run.obstacleHitReadyAt
        val sealed = mutableListOf<Int>()
        var rewardsChecked = false
        val stopAt = run.elapsed + TOUR_SECONDS
        while (pilot.attempt == null && run.elapsed < stopAt) {
            assertTrue(run.phase == GamePhase.RUNNING || run.phase == GamePhase.CHOICE, "$label ended at ${run.elapsed}: ${run.message}")
            if (run.phase == GamePhase.CHOICE) {
                pilot.choose(run)
                continue
            }
            val matter = run.runMatter + run.matterFraction.toDouble()
            val pickups = IdentityHashMap<Pickup, Unit>().apply { run.pickups.forEach { put(it, Unit) } }
            val wasHeld = run.dungeon
            pilot.control(run)
            run.update(0.02f)
            run.enemies.removeAll { it.ownerTag == 0L }
            pilot.observe(run)
            val held = run.dungeon
            if (held != null) {
                assertEquals(placement.id, held.structureId, "$label holds the toured structure")
                assertDoorsFollowTheRooms(run, label, template, held.engagedRoom, held.clearedRooms)
                if (held.engagedRoom >= 0) {
                    assertTrue(held.engagedRoom !in sealed, "$label room ${held.engagedRoom} seals once")
                    sealed += held.engagedRoom
                    run.killGarrisonForTesting()
                }
            } else if (wasHeld != null) {
                // The finish: rewards through their channels at the final room's centre, the structure spent.
                assertEquals(template.finalRoom, wasHeld.engagedRoom, "$label finished in its final room")
                assertFinalRewards(run, label, template, placement.rotation, placement.absoluteX, placement.absoluteY, pickups, matter)
                assertTrue(run.isSpent(placement.id), label)
                val index = run.structureField.indexOf(placement.id)
                val structure = run.structureField.structures[index]
                assertTrue(structure.spent, label)
                assertTrue(
                    (structure.wallStart until structure.wallEnd).map { run.structureField.walls[it] }
                        .filter { it.door >= 0 }.all { it.kind == WallKind.DOOR_OPEN },
                    "$label: every door of a spent structure stands open",
                )
                rewardsChecked = true
            }
        }
        val attempt = assertNotNull(pilot.attempt, "$label: no outcome within $TOUR_SECONDS s")
        assertEquals(DungeonOutcome.CLEARED, attempt.outcome, "$label ${attempt.line()}")
        assertTrue(rewardsChecked, label)
        assertEquals(template.finalRoom, sealed.last(), "$label seals its final room last: $sealed")
        // Locks open only once their rooms are clear, so every locking room was sealed before the room it guards.
        for (door in template.doors) {
            if (door.lockRooms == 0) continue
            val guarded = sealed.indexOf(template.finalRoom)
            for (room in template.rooms.indices) {
                if (door.lockRooms and (1 shl room) == 0) continue
                val at = sealed.indexOf(room)
                assertTrue(at in 0 until guarded, "$label: lock room $room cleared before the final room ($sealed)")
            }
        }
        assertEquals(hitReadyAt, run.obstacleHitReadyAt, "$label: the route never hurts the Core on a wall")
    }

    /**
     * Each door of the toured structure on this frame: sealed while a room it joins is engaged or while its lock waits
     * on a room not cleared yet, open otherwise.
     */
    private fun assertDoorsFollowTheRooms(run: MutableGameState, label: String, template: StructureTemplate, engaged: Int, cleared: Int) {
        val structure = run.structureField.structures.single { it.id == run.dungeon!!.structureId }
        for (index in structure.wallStart until structure.wallEnd) {
            val wall = run.structureField.walls[index]
            if (wall.door < 0) continue
            val door = template.doors[wall.door]
            val sealedByFight = engaged >= 0 && (door.roomA == engaged || door.roomB == engaged)
            val sealedByLock = door.lockRooms != 0 && cleared and door.lockRooms != door.lockRooms
            val expected = if (sealedByFight || sealedByLock) WallKind.DOOR_SEALED else WallKind.DOOR_OPEN
            assertEquals(expected, wall.kind, "$label door ${wall.door} at ${run.elapsed} (engaged $engaged, cleared $cleared)")
        }
    }

    private fun assertFinalRewards(
        run: MutableGameState,
        label: String,
        template: StructureTemplate,
        rotation: Int,
        centreX: Double,
        centreY: Double,
        before: IdentityHashMap<Pickup, Unit>,
        matterBefore: Double,
    ) {
        val room = template.rooms[template.finalRoom]
        val localX = (room.minX + room.maxX) * 0.5f
        val localY = (room.minY + room.maxY) * 0.5f
        val roomX = (centreX - run.worldOriginX).toFloat() + rotateQuarterX(localX, localY, rotation)
        val roomY = (centreY - run.worldOriginY).toFloat() + rotateQuarterY(localX, localY, rotation)
        val fresh = run.pickups.filter { it !in before }
        assertTrue(
            fresh.all { abs(it.x - roomX) < REWARD_SPREAD && abs(it.y - roomY) < REWARD_SPREAD },
            "$label rewards at the final room's centre ($roomX, $roomY): ${fresh.map { "${it.type}@${it.x},${it.y}" }} core ${run.coreX},${run.coreY}",
        )
        var matter = 0
        for (reward in template.rewards) {
            when (reward.kind) {
                StructureRewardKind.RELIC -> assertEquals(reward.amount, fresh.count { it.type == PickupType.RELIC }, "$label Relic")
                StructureRewardKind.KEY -> assertEquals(reward.amount, fresh.count { it.type == PickupType.KEY }, "$label Key")
                StructureRewardKind.REPAIR -> assertEquals(reward.amount, fresh.count { it.type == PickupType.REPAIR }, "$label repair")
                StructureRewardKind.DATA -> assertEquals(reward.amount, fresh.count { it.type == PickupType.DATA }, "$label Data")
                StructureRewardKind.MATTER -> matter += reward.amount
                StructureRewardKind.ASPECT -> assertEquals(1, run.runBoons.count { it == reward.boonId }, "$label grants its unique aspect")
                StructureRewardKind.ITEM_OFFER -> assertTrue(
                    reward.rarityFloor in run.pendingFloorOffers || run.offerRarityFloor == reward.rarityFloor,
                    "$label queues a ${reward.rarityFloor} item offer",
                )
            }
        }
        assertEquals(matter * run.matterGain.toDouble(), run.runMatter + run.matterFraction.toDouble() - matterBefore, 0.01, "$label Matter")
    }

    private fun tourState(id: StructureTemplateId): MutableGameState {
        val forced = forcedStructureContent(id)
        return buildTestState(content = forced.copy(obstacles = forced.obstacles.copy(meanPerChunk = 0f)), seed = TOUR_SEED)
    }

    /** The template-space offset turned by [rotation]: [distance] out from the entrance door along its outward normal. */
    private fun outsideEntrance(template: StructureTemplate, rotation: Int, distance: Float): Pair<Float, Float> {
        val door = template.doors[template.entranceDoor]
        val room = template.rooms[template.entranceRoom]
        val horizontal = door.axis == WallAxis.HORIZONTAL
        val x = if (horizontal) door.center else door.line
        val y = if (horizontal) door.line else door.center
        val outward = if (horizontal) (if (door.line > (room.minY + room.maxY) * 0.5f) 1f else -1f) else (if (door.line > (room.minX + room.maxX) * 0.5f) 1f else -1f)
        val localX = if (horizontal) x else x + outward * distance
        val localY = if (horizontal) y + outward * distance else y
        return rotateQuarterX(localX, localY, rotation) to rotateQuarterY(localX, localY, rotation)
    }

    private companion object {
        const val TOUR_SEED = 4_404
        const val FIRST_CELL_X = 3
        const val TOUR_CELL_Y = -4
        const val MAX_CELL_SCAN = 64
        const val START_OFFSET = 500f
        const val TOUR_SECONDS = 150f
        /** The reward row spans 30 u, and a magnet may pull a pickup toward a Core close by within the frame. */
        const val REWARD_SPREAD = 80f
    }
}
