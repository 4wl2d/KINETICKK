// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The structure render fact: the window's structures with their room progress, every wall with its kind, the entrances
 * of unspent structures and the dungeon's panel facts, projected from the run without writing it and reused while
 * nothing it reads changed.
 */
class StructureRenderMapperTest {
    private val step = MutableGameState.FIXED_STEP

    /** A run whose far cells all hold [template]; the Core rests at the centre of the one in cell (12, −9). */
    private class Site(template: StructureTemplateId) {
        val run = buildTestState(content = forcedStructureContent(template), seed = 44)
        val placement = run.structureAtForTesting(12, -9)
        val template = run.content.structures.template(template)

        init {
            run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
            run.enemies.clear()
            run.pickups.clear()
        }

        val index: Int get() = run.structureField.indexOf(placement.id)

        fun local(localX: Float, localY: Float): Pair<Float, Float> {
            val structure = run.structureField.structures[index]
            return structure.x + rotateQuarterX(localX, localY, structure.rotation) to
                structure.y + rotateQuarterY(localX, localY, structure.rotation)
        }

        fun moveCore(point: Pair<Float, Float>) {
            run.coreX = point.first
            run.coreY = point.second
            run.previousCoreX = point.first
            run.previousCoreY = point.second
        }

        fun enterRoom(room: Int) {
            val spec = template.rooms[room]
            moveCore(local((spec.minX + spec.maxX) / 2f, (spec.minY + spec.maxY) / 2f))
            run.updateStructures(MutableGameState.FIXED_STEP)
            run.updateStructures(MutableGameState.FIXED_STEP)
        }

        fun clear(room: Int) {
            enterRoom(room)
            check(run.dungeon?.engagedRoom == room) { "room $room did not seal" }
            run.killGarrisonForTesting()
            run.updateStructures(MutableGameState.FIXED_STEP)
        }
    }

    @Test
    fun aSealedRoomProjectsItsStructureWallsSignalsAndPanelFacts() {
        val site = Site(StructureTemplateId.DERELICT_STATION)
        val run = site.run
        site.enterRoom(1)
        val dungeon = assertNotNull(run.dungeon)
        assertEquals(1, dungeon.engagedRoom)
        val projected = run.toRenderModel().structures

        // Every placed structure in window order; only the dungeon's carries its sealed room.
        val field = run.structureField
        assertEquals(field.structures.map { it.id }, projected.structures.map { it.id })
        projected.structures.forEachIndexed { index, structure ->
            val placed = field.structures[index]
            assertEquals(placed.template, structure.template)
            assertEquals(placed.rotation, structure.rotation)
            assertEquals(placed.x, structure.x)
            assertEquals(placed.y, structure.y)
            assertEquals(placed.halfWidth, structure.halfWidth)
            assertEquals(placed.wallStart to placed.wallEnd, structure.wallStart to structure.wallEnd)
            assertEquals(if (placed.id == site.placement.id) 1 else -1, structure.engagedRoom)
            assertEquals(0, structure.clearedRooms)
        }
        // Every wall with its kind: the hall's two doors are sealed, the airlock's outer door and the reactor's open.
        assertEquals(field.walls.size, projected.walls.size)
        field.walls.forEachIndexed { index, wall ->
            val drawn = projected.walls[index]
            assertEquals(wall.id, drawn.id)
            assertEquals(wall.kind, drawn.kind)
            assertEquals(listOf(wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness), listOf(drawn.ax, drawn.ay, drawn.bx, drawn.by, drawn.halfThickness))
            assertEquals(wall.door, drawn.door)
        }
        val structure = projected.structures[site.index]
        val doors = projected.walls.subList(structure.wallStart, structure.wallEnd).filter { it.door >= 0 }.sortedBy { it.door }
        assertEquals(listOf(WallKind.DOOR_OPEN, WallKind.DOOR_SEALED, WallKind.DOOR_SEALED, WallKind.DOOR_OPEN), doors.map { it.kind })
        // Signals carry the entrance (the outer door's centre), nearest first, for unspent structures.
        assertEquals(field.signals.map { it.id }, projected.signals.map { it.id })
        field.signals.forEachIndexed { index, signal ->
            assertEquals(signal.entranceX to signal.entranceY, projected.signals[index].x to projected.signals[index].y)
        }
        val own = projected.signals.single { it.id == site.placement.id }
        val entrance = site.template.doors[site.template.entranceDoor]
        assertEquals(WallAxis.VERTICAL, entrance.axis)
        val expected = site.local(entrance.line, entrance.center)
        assertEquals(expected.first, own.x, 0.01f)
        assertEquals(expected.second, own.y, 0.01f)

        // The panel: four rooms count (each has a garrison), none clear, the garrison alive out of those spawned.
        val panel = assertNotNull(projected.dungeon)
        assertEquals(site.placement.id, panel.structureId)
        assertEquals(StructureTemplateId.DERELICT_STATION, panel.template)
        assertEquals(4, panel.roomsToClear)
        assertEquals(0, panel.roomsCleared)
        assertEquals(1, panel.engagedRoom)
        assertTrue(dungeon.garrisonTotal > 0)
        assertEquals(dungeon.garrisonTotal, panel.garrisonTotal)
        assertEquals(dungeon.garrisonTotal, panel.garrisonAlive)
        assertTrue(panel.panel)

        // A member falling counts at once, before the dungeon step forgets it; the run is only read.
        val fallen = run.enemies.first { it.id in dungeon.garrisonIds }
        fallen.hp = 0f
        val after = run.toRenderModel().structures
        assertEquals(dungeon.garrisonTotal - 1, after.dungeon?.garrisonAlive)
        assertSame(dungeon, run.dungeon)
    }

    @Test
    fun clearedRoomsCountAndASpentStructureShowsEveryRoomWithoutItsSignal() {
        val site = Site(StructureTemplateId.DERELICT_STATION)
        val run = site.run
        site.clear(0)
        site.clear(1)
        var projected = run.toRenderModel().structures
        assertEquals(0b011, projected.structures[site.index].clearedRooms)
        assertEquals(2, projected.dungeon?.roomsCleared)
        assertEquals(-1, projected.dungeon?.engagedRoom)
        assertTrue(projected.signals.any { it.id == site.placement.id })

        site.clear(3)
        site.clear(2)
        assertNull(run.dungeon)
        assertTrue(run.isSpent(site.placement.id))
        projected = run.toRenderModel().structures
        // Spent: every room bit, no dungeon, and its entrance no longer signals although the window still lists it.
        val structure = projected.structures[site.index]
        assertTrue(structure.spent)
        assertEquals(0b1111, structure.clearedRooms)
        assertNull(projected.dungeon)
        assertTrue(run.structureField.signals.any { it.id == site.placement.id })
        assertFalse(projected.signals.any { it.id == site.placement.id })
        val doors = projected.walls.subList(structure.wallStart, structure.wallEnd).filter { it.door >= 0 }
        assertTrue(doors.all { it.kind == WallKind.DOOR_OPEN })
    }

    @Test
    fun aSecretTemplateHoldsNoPanelAndCountsOnlyItsFinalRoom() {
        val site = Site(StructureTemplateId.ARCHITECTS_DOODLE)
        val run = site.run
        // Inside the planet's ring, outside its one room: the structure is held, nothing is cleared yet.
        site.moveCore(site.local(0f, 560f))
        run.updateStructures(step)
        assertNotNull(run.dungeon)
        val panel = assertNotNull(run.toRenderModel().structures.dungeon)
        assertFalse(panel.panel)
        assertEquals(1, panel.roomsToClear)
        assertEquals(0, panel.garrisonTotal)
    }

    @Test
    fun theSameWindowAndDungeonKeepThePreviousProjectionAndEachChangeReprojectsOnlyItself() {
        val site = Site(StructureTemplateId.DERELICT_STATION)
        val source = site.run
        site.moveCore(site.local(-900f, -600f))
        source.updateStructures(step)
        assertNotNull(source.dungeon)
        val retained = source.toRenderModel()
        val first = retained.structures
        assertNotSame(StructureRenderProjection.EMPTY, first)

        // A sibling sharing the window, dungeon and spent list keeps the whole projection.
        val sibling = source.copyForReduction()
        assertSame(first, sibling.toRenderModel(retained, source).structures)
        // Without an identity source, equal content keeps it too (raw float bits).
        assertSame(first, source.copyForReduction().toRenderModel(retained, null).structures)

        // Sealing a room replaces one wall slice and the room progress: walls and structures reproject, signals stay.
        site.enterRoom(1)
        val retainedSealed = source.toRenderModel(retained, sibling)
        val sealed = retainedSealed.structures
        assertNotSame(first.walls, sealed.walls)
        assertNotSame(first.structures, sealed.structures)
        assertSame(first.signals, sealed.signals)
        assertEquals(1, sealed.dungeon?.engagedRoom)

        // A garrison member falling changes only the panel facts.
        val identity = source.copyForReduction()
        source.enemies.first { it.id in source.dungeon!!.garrisonIds }.hp = 0f
        val fallen = source.toRenderModel(retainedSealed, identity).structures
        assertNotSame(sealed, fallen)
        assertSame(sealed.walls, fallen.walls)
        assertSame(sealed.structures, fallen.structures)
        assertSame(sealed.signals, fallen.signals)
        assertEquals(sealed.dungeon!!.garrisonAlive - 1, fallen.dungeon?.garrisonAlive)

        // Another structure spent while the window and the dungeon stay the same: its entrance stops signalling.
        val other = assertNotNull(source.structureField.signals.firstOrNull { it.id != site.placement.id }).id
        val unchanged = source.copyForReduction()
        val retainedFallen = source.toRenderModel(retainedSealed, identity)
        source.markSpent(other, SPENT_FLAG)
        val dropped = source.toRenderModel(retainedFallen, unchanged).structures
        assertFalse(dropped.signals.any { it.id == other })
        assertSame(retainedFallen.structures.walls, dropped.walls)
    }
}
