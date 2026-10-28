// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The dungeon runtime: entering, sealing a room around its garrison, clearing rooms and locks, the final room's
 * rewards, leaving, abandoning, the Architect's and a layer switch's collapse, the Doodle, the director and isolation
 * between reduction siblings.
 */
class DungeonSystemTest {
    private val step = MutableGameState.FIXED_STEP

    /** A run with every far cell holding [template]; the Core rests at the centre of the one in cell (12, −9). */
    private class Dungeon(
        template: StructureTemplateId,
        seed: Int = 44,
        rebirth: Int = 0,
        configure: (StructurePolicy) -> StructurePolicy = { it },
    ) {
        val content = forcedStructureContent(template).let { it.copy(structures = configure(it.structures)) }
        val run = buildTestState(content = content, seed = seed, rebirth = rebirth)
        val placement = run.structureAtForTesting(12, -9)
        val template: StructureTemplate = run.content.structures.template(template)

        init {
            run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
            run.enemies.clear()
            run.pickups.clear()
        }

        val structure: PlacedStructure get() = run.structureField.structures[run.structureField.indexOf(placement.id)]

        /** Run-local point of the template-space point. */
        fun world(localX: Float, localY: Float): Pair<Float, Float> {
            val s = structure
            return s.x + rotateQuarterX(localX, localY, s.rotation) to s.y + rotateQuarterY(localX, localY, s.rotation)
        }

        fun roomCentre(room: Int): Pair<Float, Float> {
            val spec = template.rooms[room]
            return world((spec.minX + spec.maxX) / 2f, (spec.minY + spec.maxY) / 2f)
        }

        fun moveCore(point: Pair<Float, Float>) {
            run.coreX = point.first
            run.coreY = point.second
            run.previousCoreX = point.first
            run.previousCoreY = point.second
        }

        /** Puts the Core in [room] and runs the dungeon step until it holds the structure and acted on the room. */
        fun enterRoom(room: Int) {
            moveCore(roomCentre(room))
            run.updateStructures(MutableGameState.FIXED_STEP)
            run.updateStructures(MutableGameState.FIXED_STEP)
        }

        fun doors(): List<WallKind> {
            val s = structure
            return run.structureField.walls.subList(s.wallStart, s.wallEnd).filter { it.door >= 0 }.sortedBy { it.door }.map { it.kind }
        }

        fun tag(room: Int): Long = roomEncounterTag(placement.id, room)

        fun garrison(room: Int) = run.enemies.filter { it.ownerTag == tag(room) && it.alive }

        fun clear(room: Int) {
            enterRoom(room)
            check(run.dungeon?.engagedRoom == room) { "room $room did not seal" }
            run.killGarrisonForTesting()
            run.updateStructures(MutableGameState.FIXED_STEP)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Entry, sealing and the cap.

    @Test
    fun enteringAStructureAnnouncesItOnceAndRecordsItsDiscovery() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        // Above the airlock: inside the footprint, in no room.
        dungeon.moveCore(dungeon.world(-900f, -600f))
        assertEquals(-1, roomAt(dungeon.template, dungeon.structure.rotation, dungeon.structure.x, dungeon.structure.y, run.coreX, run.coreY, 0f))
        run.updateStructures(step)
        val held = assertNotNull(run.dungeon)
        assertEquals(dungeon.placement.id, held.structureId)
        assertEquals(-1, held.engagedRoom)
        assertFalse(held.engaged)
        assertEquals("DERELICT STATION", run.message)
        assertTrue(run.worldDiscoveryMask and WorldDiscovery.DERELICT_STATION.bit != 0)
        assertTrue(run.pendingWorldDiscoveryMask and WorldDiscovery.DERELICT_STATION.bit != 0)
        assertTrue(run.structureTouched())

        // Out and back in: the structure is held again without a second banner.
        dungeon.moveCore(dungeon.world(0f, 2_600f))
        run.updateStructures(step)
        assertNull(run.dungeon)
        run.message = ""
        dungeon.moveCore(dungeon.world(-900f, -600f))
        run.updateStructures(step)
        assertNotNull(run.dungeon)
        assertEquals("", run.message)
    }

    @Test
    fun enteringARoomSealsItsDoorsAndSpawnsItsGarrisonInsideTheCap() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        val open = WallKind.DOOR_OPEN
        val sealed = WallKind.DOOR_SEALED
        assertEquals(List(4) { open }, dungeon.doors())
        dungeon.enterRoom(1)

        // The hall: four habitat picks and two Sentries on its walls; doors D1 (airlock) and D2 (bay) seal.
        val held = assertNotNull(run.dungeon)
        assertEquals(1, held.engagedRoom)
        assertTrue(held.engaged)
        assertEquals(listOf(open, sealed, sealed, open), dungeon.doors())
        val garrison = dungeon.garrison(1)
        assertEquals(6, garrison.size)
        assertEquals(garrison.map { it.id }, held.garrisonIds.toList())
        assertEquals(6, held.garrisonTotal)
        assertEquals(2, garrison.count { it.type == EnemyType.SENTRY })
        assertNotNull(run.encounter(dungeon.tag(1)))
        val s = dungeon.structure
        garrison.forEach { enemy ->
            assertEquals(1, roomAt(dungeon.template, s.rotation, s.x, s.y, enemy.x, enemy.y, 0f), "${enemy.type} spawns in the hall")
            assertTrue(run.wallsClearOf(enemy.x, enemy.y, enemy.radius), "${enemy.type} spawns clear of the walls")
            assertTrue(run.isPlaceBound(enemy))
            assertTrue(run.content.enemy(enemy.type).livesIn(EnemyHabitat.STRUCTURE) || enemy.type == EnemyType.SENTRY)
        }
        assertEquals(StructureNotice.DOORS_SEALED.key, run.message)
        assertTrue(run.structureEngagementActive())
        assertTrue(run.engagementActive())

        // While sealed nothing else engages and the room spawns nothing more.
        run.updateStructures(step)
        assertEquals(6, run.enemies.count { it.ownerTag != 0L })
    }

    @Test
    fun aRoomRefusesToEngageWhenTheGarrisonWouldPassTheHardCap() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        val cap = run.content.rebirth.maxActiveEnemies
        // The hall garrisons six: with cap − 5 enemies alive it does not fit.
        repeat(cap - 5) { run.addEnemyForTesting(dungeon.structure.x + 3_000f, dungeon.structure.y + it) }
        dungeon.moveCore(dungeon.roomCentre(1))
        run.updateStructures(step)
        val entered = assertNotNull(run.dungeon)
        val field = run.structureField
        val message = run.message
        repeat(3) { run.updateStructures(step) }
        assertEquals(entered, run.dungeon, "the room stays dormant")
        assertSame(field, run.structureField, "no door seals")
        assertEquals(cap - 5, run.enemies.size)
        assertTrue(run.encounters.isEmpty())
        assertEquals(message, run.message)
        assertFalse(run.structureEngagementActive())

        // At cap − 6 the garrison fits exactly and the room seals on the next step.
        run.enemies.removeAt(run.enemies.lastIndex)
        run.updateStructures(step)
        assertEquals(1, run.dungeon?.engagedRoom)
        assertEquals(cap, run.enemies.size)
    }

    @Test
    fun passagesClearOnEntryWithoutSealing() {
        val dungeon = Dungeon(StructureTemplateId.LABYRINTH)
        val run = dungeon.run
        val before = dungeon.doors()
        dungeon.enterRoom(7)
        val held = assertNotNull(run.dungeon)
        assertEquals(1 shl 7, held.clearedRooms)
        assertEquals(-1, held.engagedRoom)
        assertFalse(held.engaged, "a passage is no fight")
        assertEquals(before, dungeon.doors())
        assertTrue(run.enemies.isEmpty())
        assertEquals("LABYRINTH", run.message, "a passage clears silently")

        // Leaving after passages only forgets them; the structure stays armed.
        dungeon.moveCore(dungeon.world(0f, 2_600f))
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertFalse(run.isSpent(dungeon.placement.id))
    }

    @Test
    fun clearingOpensTheDoorsAndLocksOpenWhenAllTheirRoomsAreCleared() {
        val dungeon = Dungeon(StructureTemplateId.VAULT)
        val run = dungeon.run
        val open = WallKind.DOOR_OPEN
        val sealed = WallKind.DOOR_SEALED
        assertEquals(listOf(open, open, open, sealed, sealed), dungeon.doors())

        dungeon.enterRoom(1)
        assertEquals(listOf(open, sealed, open, sealed, sealed), dungeon.doors(), "the west gallery seals its own doors")
        run.killGarrisonForTesting()
        run.updateStructures(step)
        assertEquals(StructureNotice.ROOM_CLEARED.key, run.message)
        assertEquals(listOf(open, open, open, sealed, sealed), dungeon.doors(), "one gallery is not enough")
        assertNull(run.encounter(dungeon.tag(1)))
        assertFalse(run.structureEngagementActive())

        dungeon.clear(2)
        assertEquals(StructureNotice.INNER_DOORS_OPEN.key, run.message)
        assertEquals(List(5) { open }, dungeon.doors(), "both galleries open the vault")
        assertEquals(0b0110, run.dungeon!!.clearedRooms and 0b0110)

        // A cleared room never seals again.
        dungeon.enterRoom(1)
        assertEquals(-1, run.dungeon!!.engagedRoom)
    }

    @Test
    fun aRoomClearsOnlyOnceEveryEnemyItsEncounterOwnsIsGone() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val garrison = dungeon.garrison(1)
        // Ordinary enemies shut in the room do not hold the clear; a tagged straggler (an add or a fragment) does.
        run.addEnemyForTesting(garrison[0].x, garrison[0].y)
        val straggler = run.addEnemyForTesting(garrison[0].x, garrison[0].y).apply { ownerTag = dungeon.tag(1) }
        garrison.forEach { run.onEnemyKilled(it); run.enemies.remove(it) }
        run.updateStructures(step)
        assertEquals(1, run.dungeon!!.engagedRoom)
        assertTrue(run.dungeon!!.garrisonIds.isEmpty(), "the panel counts the members still alive")
        run.onEnemyKilled(straggler)
        run.enemies.remove(straggler)
        run.updateStructures(step)
        assertEquals(-1, run.dungeon!!.engagedRoom)
        assertTrue(run.dungeon!!.clearedRooms and (1 shl 1) != 0)
    }

    @Test
    fun garrisonsAreLeashExemptNeverEvictedAndClampedToTheirRoom() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val garrison = dungeon.garrison(1)
        garrison.forEach { assertTrue(run.leashExempt(it), "${it.type} is leash-exempt") }

        // Eviction at the cap takes ordinary strangers, never a member.
        repeat(run.content.rebirth.maxActiveEnemies - run.enemies.size) { run.addEnemyForTesting(run.coreX + 2_000f, run.coreY + it) }
        repeat(20) { run.evictOldestOrdinaryEnemy() }
        assertEquals(garrison.map { it.id }.toSet(), run.enemies.filter { it.ownerTag == dungeon.tag(1) }.map { it.id }.toSet())

        // A member knocked out of the hall is held in it.
        val member = garrison.first { it.type != EnemyType.SENTRY }
        val (outsideX, outsideY) = dungeon.world(dungeon.template.rooms[1].maxX + 300f, 300f)
        member.x = outsideX
        member.y = outsideY
        run.resolveEnemyObstacles()
        val s = dungeon.structure
        assertEquals(1, roomAt(dungeon.template, s.rotation, s.x, s.y, member.x, member.y, 0f))
    }

    // -----------------------------------------------------------------------------------------
    // Rewards, leaving and collapse.

    @Test
    fun theFinalRoomPaysItsRewardsOnceThroughExistingChannels() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(2)
        assertEquals(2, run.dungeon!!.engagedRoom)
        run.killGarrisonForTesting()
        run.pickups.clear()
        val matter = run.runMatter + run.matterFraction.toDouble()
        val kills = run.kills
        run.updateStructures(step)

        // Key, 60 Matter and 8 Data at the reactor's centre.
        val (centreX, centreY) = dungeon.roomCentre(2)
        assertEquals(1, run.pickups.count { it.type == PickupType.KEY })
        assertEquals(8, run.pickups.count { it.type == PickupType.DATA })
        assertEquals(9, run.pickups.size)
        assertTrue(run.pickups.all { abs(it.x - centreX) < 20f && abs(it.y - centreY) < 20f })
        assertEquals(60.0 * run.matterGain, run.runMatter + run.matterFraction.toDouble() - matter, 0.01)
        assertEquals(kills, run.kills)
        assertEquals(StructureNotice.STRUCTURE_CLEARED.key, run.message)
        assertNull(run.dungeon)
        assertTrue(run.isSpent(dungeon.placement.id))
        assertTrue(dungeon.structure.spent)
        assertEquals(List(4) { WallKind.DOOR_OPEN }, dungeon.doors())

        // A spent structure is never held again and pays nothing more.
        run.pickups.clear()
        dungeon.enterRoom(2)
        dungeon.enterRoom(1)
        assertNull(run.dungeon)
        assertTrue(run.pickups.isEmpty())
        assertTrue(run.enemies.isEmpty())
    }

    @Test
    fun aVaultPaysARelicAndAFlooredItemOffer() {
        val dungeon = Dungeon(StructureTemplateId.VAULT)
        val run = dungeon.run
        dungeon.clear(1)
        dungeon.clear(2)
        // The vault: a Brood Queen guards it.
        dungeon.enterRoom(3)
        assertEquals(StructureNotice.GUARDIAN_AWAKENS.key, run.message)
        assertEquals(1, dungeon.garrison(3).count { it.type == EnemyType.BROOD_QUEEN })
        run.killGarrisonForTesting()
        run.pickups.clear()
        run.updateStructures(step)
        assertEquals(1, run.pickups.count { it.type == PickupType.RELIC })
        assertEquals(listOf(ItemRarity.RARE), run.pendingFloorOffers.toList())
        assertTrue(run.isSpent(dungeon.placement.id))
    }

    @Test
    fun leavingAfterAFightAbandonsWithoutRewardAndNeverRearms() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val kills = run.kills
        // Thrown out past the leave radius (a sealed room cannot be walked out of).
        dungeon.moveCore(dungeon.world(0f, run.content.structures.leaveRadius + 10f))
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.enemies.none { it.ownerTag == dungeon.tag(1) }, "the garrison leaves")
        assertTrue(run.encounters.isEmpty())
        assertEquals(kills, run.kills, "no kills")
        assertTrue(run.pickups.isEmpty(), "no reward")
        assertEquals(StructureNotice.STRUCTURE_ABANDONED.key, run.message)
        assertTrue(run.isSpent(dungeon.placement.id))
        assertEquals(List(4) { WallKind.DOOR_OPEN }, dungeon.doors())

        dungeon.enterRoom(1)
        assertNull(run.dungeon, "an abandoned structure never re-arms")
        assertTrue(run.enemies.isEmpty())
    }

    @Test
    fun leavingBeforeAnyFightLeavesTheStructureArmed() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.moveCore(dungeon.world(-900f, -600f))
        run.updateStructures(step)
        assertNotNull(run.dungeon)
        run.message = ""
        dungeon.moveCore(dungeon.world(0f, run.content.structures.leaveRadius + 10f))
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertFalse(run.isSpent(dungeon.placement.id))
        assertEquals("", run.message, "nothing to announce")

        dungeon.enterRoom(1)
        assertEquals(1, run.dungeon?.engagedRoom, "the structure is still armed")
    }

    @Test
    fun enteringAnotherFootprintLetsTheFirstStructureGo() {
        // Cells of 3,000 u and a leave radius of 4,000 u put a neighbour's footprint inside the leave radius.
        val content = denseStructureContent(
            canonicalGameplayContent.structures.templates.map { if (it.id == StructureTemplateId.DERELICT_STATION) it else it.copy(weight = 0f) },
        ) { it.copy(cellSize = 3_000f, leaveRadius = 4_000f) }
        val run = buildTestState(content = content, seed = 44)
        val first = run.structureAtForTesting(20, -15)
        val neighbour = run.structureAtForTesting(21, -15)
        run.teleportCoreForTesting(first.absoluteX, first.absoluteY)
        run.enemies.clear()
        val template = run.content.structures.template(StructureTemplateId.DERELICT_STATION)
        val placed = run.structureField.structures.single { it.id == first.id }
        require(run.structureField.indexOf(neighbour.id) >= 0) { "the neighbour lies in the window" }
        val hall = template.rooms[1]
        val hallX = (hall.minX + hall.maxX) / 2f
        val hallY = (hall.minY + hall.maxY) / 2f
        run.coreX = placed.x + rotateQuarterX(hallX, hallY, placed.rotation)
        run.coreY = placed.y + rotateQuarterY(hallX, hallY, placed.rotation)
        run.updateStructures(step)
        run.updateStructures(step)
        assertEquals(1, run.dungeon?.engagedRoom)

        run.coreX = (neighbour.absoluteX - run.worldOriginX).toFloat()
        run.coreY = (neighbour.absoluteY - run.worldOriginY).toFloat()
        val held = run.dungeon!!
        assertTrue(kotlin.math.hypot(run.coreX - held.x, run.coreY - held.y) < 4_000f, "still inside the leave radius")
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.isSpent(first.id), "the fought structure is abandoned")
        assertTrue(run.enemies.none { it.ownerTag != 0L })
        run.updateStructures(step)
        assertEquals(neighbour.id, run.dungeon?.structureId, "then the neighbour is held")
    }

    @Test
    fun theArchitectCollapsesWallsAndRemovesTheGarrisonWithoutReward() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val hall = dungeon.roomCentre(1)
        val reactor = dungeon.roomCentre(2)
        val kills = run.kills
        val matter = run.runMatter
        run.message = ""
        run.bossSpawned = true
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.enemies.none { it.ownerTag != 0L })
        assertTrue(run.encounters.isEmpty())
        assertEquals(kills, run.kills)
        assertEquals(matter, run.runMatter)
        assertTrue(run.pickups.isEmpty())
        assertEquals("", run.message, "the collapse is silent")
        assertTrue(run.isSpent(dungeon.placement.id), "the fought structure is spent")
        assertTrue(run.structureField.bossCollapsed)
        assertTrue(run.structureField.structures.isEmpty() && run.structureField.walls.isEmpty() && run.structureField.signals.isEmpty())
        assertFalse(run.wallBlocksPoint(hall.first, hall.second, 20f))

        // Nothing is held or engaged afterwards.
        dungeon.moveCore(reactor)
        run.updateStructures(step)
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.enemies.isEmpty())
    }

    @Test
    fun noRoomEngagesInTheLastFortyFiveSecondsBeforeTheArchitect() {
        val cutoff = Dungeon(StructureTemplateId.DERELICT_STATION)
        val bossAt = cutoff.run.content.tempo.bossAtSeconds
        val lead = cutoff.run.content.structures.engageCutoffSeconds
        assertEquals(45f, lead)
        cutoff.run.elapsed = bossAt - lead
        cutoff.enterRoom(1)
        assertEquals(-1, cutoff.run.dungeon!!.engagedRoom)
        assertTrue(cutoff.run.enemies.isEmpty())

        val before = Dungeon(StructureTemplateId.DERELICT_STATION)
        // The float just below the cutoff (stepped through its bits; positive, so one bit down is the next float down).
        before.run.elapsed = Float.fromBits((bossAt - lead).toRawBits() - 1)
        before.enterRoom(1)
        assertEquals(1, before.run.dungeon!!.engagedRoom)
    }

    @Test
    fun anotherEngagementKeepsTheRoomDormant() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        // A window site's trial runs elsewhere: one engagement at a time.
        run.sites = listOf(SiteState(0L, SiteKind.entries.first(), run.coreX + 5_000f, run.coreY, Float.POSITIVE_INFINITY, active = true))
        dungeon.enterRoom(1)
        repeat(3) { run.updateStructures(step) }
        assertEquals(-1, run.dungeon!!.engagedRoom)
        assertTrue(run.enemies.isEmpty())
        run.sites = emptyList()
        run.updateStructures(step)
        assertEquals(1, run.dungeon!!.engagedRoom)
    }

    @Test
    fun aLayerChangeCollapsesTheDungeonAndStructuresAtTheArrival() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val kills = run.kills
        run.message = ""
        run.onWorldLayerChanged(WorldLayer.BASE, WorldLayer.ZENITH)
        assertNull(run.dungeon)
        assertTrue(run.enemies.none { it.ownerTag != 0L })
        assertEquals(kills, run.kills)
        assertEquals("", run.message)
        assertTrue(run.isSpent(dungeon.placement.id))

        // A dungeon held without a fight leaves no mark.
        val calm = Dungeon(StructureTemplateId.DERELICT_STATION)
        calm.moveCore(calm.world(-900f, -600f))
        calm.run.updateStructures(step)
        calm.run.onWorldLayerChanged(WorldLayer.BASE, WorldLayer.NADIR)
        assertNull(calm.run.dungeon)
        assertFalse(calm.run.isSpent(calm.placement.id))
    }

    @Test
    fun anEncounterEndedElsewhereAbandonsTheDungeon() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        run.endAllEncounters(despawn = true)
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.isSpent(dungeon.placement.id), "a garrison taken away is never a clear")
        assertEquals(StructureNotice.STRUCTURE_ABANDONED.key, run.message)
        assertTrue(run.pickups.isEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Spent list, composition, the Doodle.

    @Test
    fun spentStructuresAreRememberedInABoundedFifo() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.clear(2)
        assertTrue(run.isSpent(dungeon.placement.id))
        val bound = ContentBounds.MAX_SPENT_STRUCTURES
        // Thirty-one later structures keep it spent; the thirty-second evicts it and it arms again.
        repeat(bound - 1) { run.markSpent(structureId(WorldLayer.BASE, 200 + it, 200), SPENT_FLAG) }
        assertEquals(bound, run.spentStructureIds.size)
        dungeon.enterRoom(1)
        assertNull(run.dungeon)
        run.markSpent(structureId(WorldLayer.BASE, 300, 300), SPENT_FLAG)
        assertEquals(bound, run.spentStructureIds.size)
        assertFalse(run.isSpent(dungeon.placement.id))
        dungeon.enterRoom(1)
        assertEquals(1, run.dungeon?.engagedRoom, "an evicted structure arms again")
        assertFalse(dungeon.structure.spent)
        assertEquals(listOf(WallKind.DOOR_OPEN, WallKind.DOOR_SEALED, WallKind.DOOR_SEALED, WallKind.DOOR_OPEN), dungeon.doors())
    }

    @Test
    fun garrisonCompositionDrawsNothingFromTheRunStreams() {
        fun sealed(seed: Int): Pair<Dungeon, List<EnemyType>> {
            val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION, seed = seed)
            val run = dungeon.run
            val streams = listOf(run.gameplayRandom.snapshot(), run.rewardRandom.snapshot(), run.spawnRandom.snapshot(), run.siteRandom.snapshot())
            dungeon.enterRoom(1)
            assertEquals(1, run.dungeon?.engagedRoom)
            assertEquals(streams, listOf(run.gameplayRandom.snapshot(), run.rewardRandom.snapshot(), run.spawnRandom.snapshot(), run.siteRandom.snapshot()))
            return dungeon to dungeon.garrison(1).map { it.type }
        }
        val (first, kinds) = sealed(44)
        val (again, sameKinds) = sealed(44)
        assertEquals(kinds, sameKinds, "the same room at the same time yields the same garrison")
        assertEquals(first.garrison(1).map { it.x to it.y }, again.garrison(1).map { it.x to it.y })

        // The garrison follows the hash of the run seed and the room, not the run's history.
        val drawn = Dungeon(StructureTemplateId.DERELICT_STATION, seed = 44)
        repeat(50) { drawn.run.gameplayRandom.nextInt(); drawn.run.spawnRandom.nextInt(); drawn.run.rewardRandom.nextInt() }
        drawn.enterRoom(1)
        assertEquals(kinds, drawn.garrison(1).map { it.type })
    }

    @Test
    fun rebirthTiersAddMembersToTheFirstHabitatEntry() {
        val fresh = Dungeon(StructureTemplateId.DERELICT_STATION, rebirth = 0)
        fresh.enterRoom(1)
        val veteran = Dungeon(StructureTemplateId.DERELICT_STATION, rebirth = 8)
        veteran.enterRoom(1)
        // One extra per four tiers on the hall's habitat entry; the Sentries stay two.
        assertEquals(6, fresh.garrison(1).size)
        assertEquals(8, veteran.garrison(1).size)
        assertEquals(2, veteran.garrison(1).count { it.type == EnemyType.SENTRY })
    }

    @Test
    fun garrisonsSpawnAwayFromTheCoreInsideTheirRoom() {
        // 260 u from a Core at the room's centre; the 600 u airlock has no lattice point that far and takes its
        // farthest (a corner, 226 u for a Shooter).
        val rooms = listOf(StructureTemplateId.DERELICT_STATION to listOf(0, 1, 2, 3), StructureTemplateId.VAULT to listOf(0, 1, 2))
        for ((template, indices) in rooms) for (room in indices) {
            val clearance = if (template == StructureTemplateId.DERELICT_STATION && room == 0) 200f else 260f
            val dungeon = Dungeon(template)
            val run = dungeon.run
            dungeon.enterRoom(room)
            val garrison = dungeon.garrison(room)
            assertTrue(garrison.isNotEmpty(), "$template room $room")
            val s = dungeon.structure
            garrison.forEach { enemy ->
                assertEquals(room, roomAt(dungeon.template, s.rotation, s.x, s.y, enemy.x, enemy.y, 0f), "$template room $room ${enemy.type}")
                val apart = kotlin.math.hypot(enemy.x - run.coreX, enemy.y - run.coreY)
                assertTrue(apart >= clearance - 0.01f, "$template room $room ${enemy.type} spawned $apart from the Core")
            }
        }
    }

    @Test
    fun garrisonScaleScalesOrdinaryEntriesButNeverAMiniBossAndStopsAtTwelve() {
        // The vault: one Brood Queen and three Crawlers; at scale 2 the Crawlers double and the Queen stays one.
        val doubled = Dungeon(StructureTemplateId.VAULT) { it.copy(garrisonScale = 2f) }
        doubled.clear(1)
        doubled.clear(2)
        doubled.enterRoom(3)
        val vault = doubled.garrison(3)
        assertEquals(1, vault.count { it.type == EnemyType.BROOD_QUEEN })
        assertEquals(6, vault.count { it.type == EnemyType.CRAWLER })

        // The west gallery at scale 3: twelve habitat picks and six Sentries stop at twelve.
        val tripled = Dungeon(StructureTemplateId.VAULT) { it.copy(garrisonScale = 3f) }
        tripled.enterRoom(1)
        assertEquals(ContentBounds.MAX_STRUCTURE_GARRISON, tripled.garrison(1).size)
        assertEquals(ContentBounds.MAX_STRUCTURE_GARRISON, tripled.run.dungeon!!.garrisonTotal)
    }

    @Test
    fun theArchitectsDoodleRecordsItsSecretWithoutAGarrison() {
        val dungeon = Dungeon(StructureTemplateId.ARCHITECTS_DOODLE)
        val run = dungeon.run
        run.message = ""
        dungeon.moveCore(dungeon.roomCentre(0))
        run.updateStructures(step)
        assertNotNull(run.dungeon)
        assertEquals("", run.message, "a secret shows no entry banner")
        assertEquals(0, run.worldDiscoveryMask)
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.enemies.isEmpty())
        assertTrue(run.encounters.isEmpty())
        assertEquals(12, run.pickups.count { it.type == PickupType.DATA })
        assertEquals(WorldDiscovery.ARCHITECTS_DOODLE.bit, run.worldDiscoveryMask)
        assertEquals(WorldDiscovery.ARCHITECTS_DOODLE.bit, run.pendingWorldDiscoveryMask)
        assertEquals("THE ARCHITECT'S DOODLE", run.message)
        assertTrue(run.isSpent(dungeon.placement.id))

        run.pickups.clear()
        run.updateStructures(step)
        run.updateStructures(step)
        assertTrue(run.pickups.isEmpty(), "found once")
    }

    // -----------------------------------------------------------------------------------------
    // Director, pilots and isolation.

    @Test
    fun theDirectorSlowsWhileARoomIsSealed() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.moveCore(dungeon.world(-900f, -600f))
        run.updateStructures(step)
        assertEquals(1f, run.structureDirectorScale())
        assertEquals(WorldLayer.BASE.habitat, run.ambientHabitat())
        assertFalse(run.structureInteriorActive())
        dungeon.enterRoom(1)
        assertEquals(run.content.structures.directorRateWhileSealed, run.structureDirectorScale())
        assertEquals(0.35f, run.structureDirectorScale())
        assertEquals(EnemyHabitat.STRUCTURE, run.ambientHabitat())
        run.killGarrisonForTesting()
        run.updateStructures(step)
        assertEquals(1f, run.structureDirectorScale())
        assertEquals(WorldLayer.BASE.habitat, run.ambientHabitat())
    }

    @Test
    fun whileARoomIsSealedTheDirectorSpawnsOnlyStructureKinds() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        run.elapsed = 150f
        run.invulnerableUntil = Float.MAX_VALUE
        run.runGrace = Float.MAX_VALUE
        dungeon.enterRoom(1)
        // A garrison that cannot fall keeps the room sealed while the director runs.
        dungeon.garrison(1).forEach { it.hp = 1e9f }
        run.spawnClock = 0f
        val kinds = mutableSetOf<EnemyType>()
        repeat((40f / step).toInt()) {
            run.simulateStep(step)
            assertTrue(run.structureInteriorActive(), "the room stays sealed")
            run.enemies.filter { it.ownerTag == 0L }.forEach { enemy ->
                if (run.content.enemy(enemy.type).rank == EnemyRank.ORDINARY) kinds += enemy.type
            }
            run.enemies.removeAll { it.ownerTag == 0L }
            run.pickups.clear()
            run.takeVisualFxCues()
            run.takeSoundCues()
        }
        assertTrue(kinds.isNotEmpty(), "the director keeps spawning, slower")
        assertTrue(STRUCTURE_ROSTER.containsAll(kinds), "only STRUCTURE kinds: ${kinds - STRUCTURE_ROSTER}")
    }

    @Test
    fun pilotsSeeEveryStructureOfTheWindowAsOneCircle() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        val structures = run.structureField.structures
        assertTrue(structures.isNotEmpty())
        val out = FloatArray(MAX_OPTIONAL_SITE_MARKS * 3)
        assertEquals(structures.size, run.appendStructureMarks(out, 0))
        structures.forEachIndexed { index, s ->
            assertEquals(s.x, out[index * 3])
            assertEquals(s.y, out[index * 3 + 1])
            assertEquals(kotlin.math.hypot(s.halfWidth, s.halfHeight), out[index * 3 + 2])
        }
        assertEquals(MAX_OPTIONAL_SITE_MARKS, run.appendStructureMarks(out, MAX_OPTIONAL_SITE_MARKS), "a full buffer takes no more")
        assertEquals(MAX_OPTIONAL_SITE_MARKS, run.appendStructureMarks(out, MAX_OPTIONAL_SITE_MARKS - 1))
        assertFalse(run.structureTouched())
        assertFalse(run.optionalSitesTouched())
        dungeon.moveCore(dungeon.world(-900f, -600f))
        run.updateStructures(step)
        assertTrue(run.optionalSitesTouched())
    }

    @Test
    fun dungeonAndStructureFieldAreIsolatedBetweenReductionSiblings() {
        val dungeon = Dungeon(StructureTemplateId.DERELICT_STATION)
        val source = dungeon.run
        dungeon.moveCore(dungeon.roomCentre(1))
        source.updateStructures(step)
        val field = source.structureField
        val walls = field.walls.toList()
        val held = source.dungeon
        val enemies = source.enemies.size

        val candidate = source.copyForReduction()
        candidate.updateStructures(step)
        assertEquals(1, candidate.dungeon?.engagedRoom)
        assertSame(field, source.structureField, "the source keeps its window")
        assertEquals(walls, source.structureField.walls.toList())
        assertEquals(held, source.dungeon)
        assertEquals(enemies, source.enemies.size)
        assertTrue(source.encounters.isEmpty())

        candidate.killGarrisonForTesting()
        candidate.updateStructures(step)
        assertEquals(-1, candidate.dungeon?.engagedRoom)
        assertSame(field, source.structureField)
        assertEquals(held, source.dungeon)
    }

    private companion object {
        /** The ordinary kinds ENEMIES gives the STRUCTURE habitat. */
        val STRUCTURE_ROSTER = setOf(
            EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.WEAVER, EnemyType.SPLITTER, EnemyType.SWARMLING,
            EnemyType.BOMBER, EnemyType.LANCER, EnemyType.AEGIS, EnemyType.MINELAYER, EnemyType.PHANTOM, EnemyType.REFLECTOR,
            EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CRAWLER,
        )
    }
}
