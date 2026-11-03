// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dungeon disintegration: a cleared structure and one abandoned after a fight leave the window in the step they are
 * spent (walls stop colliding at once) with one presentation cue and one rumble; an unfought drop stays standing; a
 * spent structure never returns through a window move or a layer round trip; the Architect shatters every windowed
 * structure once; a full spent set empties the window.
 */
class StructureDisintegrationTest {
    private val step = MutableGameState.FIXED_STEP

    @Test
    fun aClearedStructureDisintegratesAtOnceAndItsWallsStopCollidingInTheSameStep() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(2)
        run.killGarrisonForTesting()
        val placed = dungeon.structure
        val walls = run.structureField.walls.subList(placed.wallStart, placed.wallEnd).toList()
        val wallIds = walls.map { it.id }.toSet()
        run.takeVisualFxCues()
        run.takeSoundCues()
        run.updateStructures(step)

        assertNull(run.dungeon)
        assertEquals(-1, run.structureField.indexOf(placed.id))
        assertTrue(run.structureField.walls.none { it.id in wallIds })
        for (wall in walls) {
            val midX = (wall.ax + wall.bx) / 2f
            val midY = (wall.ay + wall.by) / 2f
            run.forEachSolidWallNear(midX, midY, 10f) { _, near -> assertFalse(near.id in wallIds, "wall ${near.id} still collides") }
            var found = false
            run.nearestWall(midX, midY, 10f) { _, _, _ -> found = true }
            assertFalse(found, "no wall surface is left where the structure stood")
        }
        assertEquals(listOf(disintegrated(placed)), run.takeVisualFxCues().filterIsInstance<VisualFxCue.StructureDisintegrated>())
        assertTrue(GameplayAudioCue.STRUCTURE_COLLAPSE in run.takeSoundCues())

        dungeon.enterRoom(1)
        assertNull(run.dungeon, "re-entry holds nothing")
    }

    @Test
    fun aFoughtAbandonDisintegratesWithoutReward() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val placed = dungeon.structure
        run.takeVisualFxCues()
        dungeon.moveCore(dungeon.world(0f, run.content.structures.leaveRadius + 10f))
        run.updateStructures(step)

        assertNull(run.dungeon)
        assertTrue(run.isSpent(placed.id))
        assertNull(dungeon.structureOrNull)
        assertTrue(run.pickups.isEmpty(), "no reward")
        assertEquals(listOf(disintegrated(placed)), run.takeVisualFxCues().filterIsInstance<VisualFxCue.StructureDisintegrated>())
        assertTrue(GameplayAudioCue.STRUCTURE_COLLAPSE in run.takeSoundCues())
    }

    @Test
    fun anUnfoughtDropLeavesTheStructureStandingAndArmed() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.moveCore(dungeon.world(-900f, -600f))
        run.updateStructures(step)
        assertNotNull(run.dungeon)
        val standing = run.structureField
        run.takeVisualFxCues()
        run.takeSoundCues()
        dungeon.moveCore(dungeon.world(0f, run.content.structures.leaveRadius + 10f))
        run.updateStructures(step)

        assertNull(run.dungeon)
        assertFalse(run.isSpent(dungeon.placement.id))
        assertNotNull(dungeon.structureOrNull, "the structure still stands")
        assertEquals(standing.walls.size, run.structureField.walls.size)
        assertTrue(run.takeVisualFxCues().none { it is VisualFxCue.StructureDisintegrated })
        assertFalse(GameplayAudioCue.STRUCTURE_COLLAPSE in run.takeSoundCues())
        dungeon.enterRoom(1)
        assertEquals(1, run.dungeon?.engagedRoom, "the structure is still armed")
    }

    @Test
    fun aSpentStructureNeverReturnsWhenTheWindowMovesAwayAndBack() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        clearWholeStation(dungeon)
        assertNull(dungeon.structureOrNull)

        val home = dungeon.placement
        run.teleportCoreForTesting(home.absoluteX + 30_000.0, home.absoluteY)
        assertEquals(-1, run.structureField.indexOf(home.id))
        run.teleportCoreForTesting(home.absoluteX, home.absoluteY)
        assertEquals(-1, run.structureField.indexOf(home.id), "a rebuilt window skips the spent placement")
        assertTrue(run.structureField.structures.isNotEmpty(), "its neighbours are back")
        assertTrue(run.structureField.signals.none { it.id == home.id })
        dungeon.enterRoom(1)
        assertNull(run.dungeon)
    }

    @Test
    fun aSpentStructureNeverReturnsAfterALayerRoundTrip() {
        // A fought, unsealed dungeon is spent silently by the switch: the window already belongs to the other layer.
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.clear(0)
        assertTrue(run.dungeon!!.engaged)
        run.takeVisualFxCues()
        run.takeSoundCues()
        switchLayerForTesting(run, WorldLayer.ZENITH)
        assertNull(run.dungeon)
        assertTrue(run.isSpent(dungeon.placement.id))
        assertTrue(run.takeVisualFxCues().none { it is VisualFxCue.StructureDisintegrated }, "no cue for a structure out of the window")
        assertFalse(GameplayAudioCue.STRUCTURE_COLLAPSE in run.takeSoundCues())
        switchLayerForTesting(run, WorldLayer.BASE)
        assertEquals(-1, run.structureField.indexOf(dungeon.placement.id), "never rebuilt")
        assertTrue(run.structureField.structures.isNotEmpty(), "the realspace window is back")

        // A finished structure stays gone through the same round trip.
        val cleared = DungeonFixture(StructureTemplateId.DERELICT_STATION, seed = 45)
        clearWholeStation(cleared)
        switchLayerForTesting(cleared.run, WorldLayer.ZENITH)
        switchLayerForTesting(cleared.run, WorldLayer.BASE)
        assertNull(cleared.structureOrNull)
        cleared.enterRoom(1)
        assertNull(cleared.run.dungeon)
    }

    @Test
    fun theArchitectCollapseShattersEveryWindowedStructureOnce() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val standing = run.structureField.structures.toList()
        assertTrue(standing.size > 1, "neighbours stand in the window")
        run.takeVisualFxCues()
        run.takeSoundCues()
        run.bossSpawned = true
        run.updateStructures(step)

        val cues = run.takeVisualFxCues().filterIsInstance<VisualFxCue.StructureDisintegrated>()
        assertEquals(standing.map(::disintegrated).sortedBy { it.x }, cues.sortedBy { it.x }, "one cue per structure, none twice")
        assertEquals(1, run.takeSoundCues().count { it == GameplayAudioCue.STRUCTURE_COLLAPSE })
        assertTrue(run.structureField.bossCollapsed && run.structureField.structures.isEmpty())
    }

    @Test
    fun aFullSpentSetCountsEveryStructureAsSpent() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        repeat(ContentBounds.MAX_SPENT_STRUCTURES - 1) { run.markSpent(structureId(WorldLayer.BASE, 400 + it, 400)) }
        assertFalse(run.isSpent(dungeon.placement.id))
        dungeon.clear(0)
        dungeon.clear(1)
        dungeon.clear(3)
        val standing = run.structureField.structures.toList()
        assertTrue(standing.size > 1)
        run.takeVisualFxCues()
        dungeon.clear(2)

        assertEquals(ContentBounds.MAX_SPENT_STRUCTURES, run.spentStructureIds.size)
        assertTrue(run.structureField.structures.isEmpty(), "the window rebuilds empty")
        assertTrue(run.structureField.signals.isEmpty(), "nothing signals")
        assertEquals(standing.size, run.takeVisualFxCues().count { it is VisualFxCue.StructureDisintegrated })
        val neighbour = standing.first { it.id != dungeon.placement.id }
        dungeon.moveCore(neighbour.x to neighbour.y)
        run.updateStructures(step)
        assertNull(run.dungeon, "nothing can be held")
        run.teleportCoreForTesting(dungeon.placement.absoluteX + 20_000.0, dungeon.placement.absoluteY)
        assertTrue(run.structureField.structures.isEmpty(), "every structure counts as spent")
    }

    private fun clearWholeStation(dungeon: DungeonFixture) {
        listOf(0, 1, 3, 2).forEach(dungeon::clear)
        assertNull(dungeon.run.dungeon)
        assertTrue(dungeon.run.isSpent(dungeon.placement.id))
    }

    /** The window and dungeon side of a realspace layer switch at the Core (the dimension system's own order). */
    private fun switchLayerForTesting(run: MutableGameState, target: WorldLayer) {
        run.worldLayer = target
        run.worldKeepOut = null
        run.switchWorldWindow()
        run.onWorldLayerChanged()
    }

    private fun disintegrated(structure: PlacedStructure) =
        VisualFxCue.StructureDisintegrated(structure.template, structure.rotation, structure.x, structure.y)
}
