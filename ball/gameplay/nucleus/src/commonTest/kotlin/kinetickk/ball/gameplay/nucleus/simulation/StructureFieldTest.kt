// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.StructureSignal
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StructureFieldTest {
    private fun structure(index: Int, wallStart: Int = 0, wallEnd: Int = 0) =
        PlacedStructure(index.toLong() shl 8, StructureTemplateId.VAULT, 0, 0f, 0f, 100f, 100f, wallStart, wallEnd, false)

    private fun wall(index: Int) = Wall(index.toLong(), WallKind.SOLID, 0f, 0f, 10f, 0f, 24f)

    private fun signal(index: Int) = StructureSignal(index.toLong(), StructureTemplateId.SPIRE, 0f, 0f, 1f, 1f)

    @Test
    fun aWindowAcceptsItsBoundsAndRejectsTheNextValue() {
        val structures = ContentBounds.MAX_STRUCTURES_IN_WINDOW
        val walls = ContentBounds.MAX_STRUCTURE_WALLS
        val signals = ContentBounds.MAX_STRUCTURE_SIGNALS
        val full = StructureField.of(0, 0, 0, false, List(structures) { structure(it) }, List(walls) { wall(it) }, List(signals) { signal(it) })
        assertEquals(structures, full.structures.size)
        assertEquals(walls, full.walls.size)
        assertEquals(signals, full.signals.size)
        assertFailsWith<IllegalArgumentException> { StructureField.of(0, 0, 0, false, List(structures + 1) { structure(it) }, emptyList(), emptyList()) }
        assertFailsWith<IllegalArgumentException> { StructureField.of(0, 0, 0, false, emptyList(), List(walls + 1) { wall(it) }, emptyList()) }
        assertFailsWith<IllegalArgumentException> { StructureField.of(0, 0, 0, false, emptyList(), emptyList(), List(signals + 1) { signal(it) }) }
        assertFailsWith<IllegalArgumentException> { StructureField.of(0, 0, 0, false, listOf(structure(0, 0, 2)), listOf(wall(0)), emptyList()) }
    }

    @Test
    fun aWindowShiftsReplacesSlicesAndKeepsItsKey() {
        val field = StructureField.of(3, -1, 0, false, listOf(structure(1, 0, 1), structure(2, 1, 2)), listOf(wall(0), wall(1)), listOf(signal(0)))
        assertTrue(field.isCurrent(3, -1, 0, false))
        assertTrue(!field.isCurrent(3, -1, 0, true) && !field.isCurrent(3, -1, 1, false) && !field.isCurrent(2, -1, 0, false))
        assertEquals(1, field.indexOf(2L shl 8))
        assertEquals(-1, field.indexOf(99L))

        val shifted = field.shifted(100f, -50f)
        assertEquals(-100f, shifted.structures[0].x)
        assertEquals(50f, shifted.walls[1].ay)
        assertEquals(-90f, shifted.walls[1].bx)
        assertEquals(-99f, shifted.signals[0].x)
        assertSame(StructureField.EMPTY, StructureField.EMPTY.shifted(1f, 1f))

        val opened = field.withStructure(1, field.structures[1].copy(spent = true), listOf(wall(1).copy(kind = WallKind.DOOR_OPEN)))
        assertEquals(WallKind.DOOR_OPEN, opened.walls[1].kind)
        assertEquals(WallKind.SOLID, field.walls[1].kind, "the source window is unchanged")
        assertFailsWith<IllegalArgumentException> { field.withStructure(1, field.structures[1], listOf(wall(1), wall(2))) }
    }

    @Test
    fun aNewRunClearsTheStructureStateAndARebaseMovesIt() {
        // A world without structures, so the hand-placed window is the only one and a new run leaves it empty.
        val state = MutableGameState(structuresDisabledContent).apply { startRun() }
        state.structureField = StructureField.of(0, 0, 0, false, listOf(structure(1, 0, 1)), listOf(wall(0)), emptyList())
        state.dungeon = DungeonState(1L shl 8, StructureTemplateId.VAULT, 0, 300_000f, 0f, enteredAt = 1f)
        state.spentStructureIds = listOf(5L)
        state.lastAnnouncedStructureId = 5L
        state.coreX = 300_000f
        state.rebaseWorldIfNeeded()
        assertEquals(0f, state.dungeon?.x)
        assertEquals(-300_000f, state.structureField.structures[0].x)
        state.startRun()
        assertSame(StructureField.EMPTY, state.structureField)
        assertNull(state.dungeon)
        assertTrue(state.spentStructureIds.isEmpty())
        assertEquals(0L, state.lastAnnouncedStructureId)
    }
}
