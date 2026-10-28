// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructureCapsule
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StructurePlacementTest {
    private val shipped = canonicalGameplayContent.structures
    private val enabled = structuresEnabledContent.structures
    private val cellSize = shipped.cellSize.toDouble()

    private fun state(content: GameplayContentSnapshot, seed: Int = 5_150) = MutableGameState(content, seed = seed).apply { startRun() }

    // -----------------------------------------------------------------------------------------
    // Placement.

    @Test
    fun placementIsAPureFunctionOfSeedLayerAndCell() {
        val everyLayer = enabled.copy(layerChance = immutableListOf(1f, 1f, 1f, 1f))
        val cells = (10..29).flatMap { x -> (-15..4).map { y -> x to y } }
        val base = cells.map { (x, y) -> placeStructure(everyLayer, 731_991, WorldLayer.BASE, x, y) }
        val backwards = cells.reversed().map { (x, y) -> placeStructure(everyLayer, 731_991, WorldLayer.BASE, x, y) }.reversed()
        assertEquals(base, backwards, "the visiting order never matters")
        assertTrue(base.count { it != null } in 100..300, "about 0.4 of 400 far cells hold a structure")
        assertNotEquals(base, cells.map { (x, y) -> placeStructure(everyLayer, 731_992, WorldLayer.BASE, x, y) })

        // The run reads the same pure function whatever it did before.
        val run = state(structuresEnabledContent, seed = 731_991)
        run.teleportCoreForTesting(80_000.0, -20_000.0)
        cells.forEachIndexed { index, (x, y) -> assertEquals(base[index], run.structurePlacement(x, y)) }

        cells.forEachIndexed { index, (x, y) ->
            val placement = base[index] ?: return@forEachIndexed
            assertEquals(x, structureCellX(placement.id))
            assertEquals(y, structureCellY(placement.id))
            assertTrue(placement.id < 0L, "bit 63 marks a structure id")
            assertEquals(0L, (placement.id ushr 60) and 0x7L, "realspace layer bits")
            assertEquals(0L, placement.id and 0xFFL, "the low byte stays free for wall indices and flags")
        }
        val far = structureId(WorldLayer.ARCHIVE, -12_345_678, 23_456_789)
        assertEquals(-12_345_678, structureCellX(far))
        assertEquals(23_456_789, structureCellY(far))
        assertEquals(WorldLayer.ARCHIVE.ordinal.toLong(), (far ushr 60) and 0x7L)

        for (layer in WorldLayer.entries - WorldLayer.BASE) {
            val layered = cells.map { (x, y) -> placeStructure(everyLayer, 731_991, layer, x, y) }
            layered.filterNotNull().forEach { assertEquals(layer.ordinal.toLong(), (it.id ushr 60) and 0x7L) }
            val sameSpot = cells.indices.count { index ->
                val a = base[index]
                val b = layered[index]
                a != null && b != null && a.absoluteX == b.absoluteX && a.absoluteY == b.absoluteY
            }
            assertEquals(0, sameSpot, "$layer salts its own placements")
            assertTrue(cells.all { (x, y) -> placeStructure(enabled, 731_991, layer, x, y) == null }, "$layer ships no structures")
        }
        assertTrue(cells.all { (x, y) -> placeStructure(shipped, 731_991, WorldLayer.BASE, x, y) == null }, "disabled content")
    }

    @Test
    fun noFootprintComesWithinTheMinimumDistanceOfTheOrigin() {
        var placed = 0
        for (seed in 1..20) {
            for (cellY in -5..4) for (cellX in -5..4) {
                val placement = placeStructure(enabled, seed, WorldLayer.BASE, cellX, cellY) ?: continue
                placed++
                val nearestX = max(0.0, abs(placement.absoluteX) - placement.halfWidth)
                val nearestY = max(0.0, abs(placement.absoluteY) - placement.halfHeight)
                assertTrue(
                    hypot(nearestX, nearestY) >= enabled.minimumDistance,
                    "seed $seed cell $cellX,$cellY reaches ${hypot(nearestX, nearestY)} u from the origin",
                )
                val cellDistance = hypot((cellX + 0.5) * cellSize, (cellY + 0.5) * cellSize)
                assertTrue(enabled.template(placement.template).minimumDistance <= cellDistance, "${placement.template} below its distance")
            }
        }
        assertTrue(placed > 100, "the 20,000 u around the origin hold structures: $placed")
    }

    @Test
    fun chanceRampsToTheMaximumBeyondTheFullDistance() {
        // 2,000 cells whose every possible footprint lies beyond the full-chance distance.
        val reach = enabled.templates.maxOf { it.circumradius }
        var cells = 0
        var placed = 0
        for (cellY in 3..52) for (cellX in 3..42) {
            assertTrue(hypot(cellX * cellSize, cellY * cellSize) - reach >= enabled.fullChanceDistance)
            cells++
            if (placeStructure(enabled, 2_026, WorldLayer.BASE, cellX, cellY) != null) placed++
        }
        assertEquals(2_000, cells)
        assertEquals(enabled.maximumChance, placed / cells.toFloat(), 0.05f)

        // The four cells at the origin cannot hold a footprint 4,000 u out, so no seed ever places one there.
        for (seed in 1..300) for (cellY in -1..0) for (cellX in -1..0) {
            assertNull(placeStructure(enabled, seed, WorldLayer.BASE, cellX, cellY), "seed $seed cell $cellX,$cellY")
        }
        assertTrue((3..52).all { placeStructure(enabled.copy(maximumChance = 0f), 2_026, WorldLayer.BASE, it, it) == null })
    }

    @Test
    fun footprintsStayInsideTheirCellWithTheMargin() {
        val dense = denseStructureContent().structures
        val margin = dense.cellMargin.toDouble()
        val templates = HashSet<StructureTemplateId>()
        val rotations = HashSet<Int>()
        for (seed in 1..10) for (cellY in 2..11) for (cellX in -11..-2) {
            val placement = requireNotNull(placeStructure(dense, seed, WorldLayer.BASE, cellX, cellY))
            templates += placement.template
            rotations += placement.rotation
            val template = dense.template(placement.template)
            val turned = placement.rotation % 2 == 1
            assertEquals(if (turned) template.halfHeight else template.halfWidth, placement.halfWidth)
            assertEquals(if (turned) template.halfWidth else template.halfHeight, placement.halfHeight)
            val left = cellX * cellSize + margin
            val top = cellY * cellSize + margin
            assertTrue(placement.absoluteX - placement.halfWidth >= left - 1e-6 && placement.absoluteX + placement.halfWidth <= left - 2 * margin + cellSize + 1e-6)
            assertTrue(placement.absoluteY - placement.halfHeight >= top - 1e-6 && placement.absoluteY + placement.halfHeight <= top - 2 * margin + cellSize + 1e-6)
            template.walls.forEach { wall ->
                for ((x, y) in listOf(wall.ax to wall.ay, wall.bx to wall.by)) {
                    assertTrue(abs(rotateQuarterX(x, y, placement.rotation)) <= placement.halfWidth + 1e-3f, "wall inside the footprint")
                    assertTrue(abs(rotateQuarterY(x, y, placement.rotation)) <= placement.halfHeight + 1e-3f, "wall inside the footprint")
                }
            }
        }
        assertEquals(StructureTemplateId.entries.toSet(), templates)
        assertEquals(setOf(0, 1, 2, 3), rotations)
    }

    @Test
    fun obstaclesInsideFootprintsAreSuppressedWithoutMovingAnyOther() {
        var suppressed = 0
        for (seed in listOf(11, 12, 13)) {
            for ((x, y) in listOf(60_000.0 to 30_000.0, -45_500.0 to 71_200.0, 20_400.0 to -90_100.0)) {
                val with = state(denseStructureContent(), seed).apply { teleportCoreForTesting(x, y) }
                val without = state(canonicalGameplayContent, seed).apply { teleportCoreForTesting(x, y) }
                assertTrue(with.structureField.structures.isNotEmpty())
                // Footprints read each layout candidate's circle; a variant kind's body sits inside its candidate's.
                val candidateRadius = state(without.content.withoutObstacleVariants(), seed).apply { teleportCoreForTesting(x, y) }
                    .obstacleField.obstacles.associate { it.id to it.radius }
                val kept = with.obstacleField.obstacles.toSet()
                kept.forEach { assertTrue(it in without.obstacleField.obstacles, "an obstacle moved: $it") }
                without.obstacleField.obstacles.forEach { obstacle ->
                    val meets = with.meetsAFootprint(obstacle, candidateRadius.getValue(obstacle.id))
                    assertEquals(meets, obstacle !in kept, "only obstacles meeting an inflated footprint go: $obstacle")
                    if (meets) suppressed++
                }
            }
        }
        assertTrue(suppressed > 0, "footprints cleared some obstacles")
    }

    private fun MutableGameState.meetsAFootprint(obstacle: Obstacle, radius: Float = obstacle.radius): Boolean {
        val x = worldOriginX + obstacle.x
        val y = worldOriginY + obstacle.y
        val clearance = content.structures.obstacleClearance
        for (cellY in structureCell(y) - 1..structureCell(y) + 1) for (cellX in structureCell(x) - 1..structureCell(x) + 1) {
            val placement = structurePlacement(cellX, cellY) ?: continue
            val dx = max(0.0, abs(x - placement.absoluteX) - placement.halfWidth - clearance)
            val dy = max(0.0, abs(y - placement.absoluteY) - placement.halfHeight - clearance)
            if (sqrt(dx * dx + dy * dy) < radius) return true
        }
        return false
    }

    // -----------------------------------------------------------------------------------------
    // The structure window.

    @Test
    fun structureWindowHoldsAtMostNineStructuresAndTheirWalls() {
        // A 48-wall block filling 3,000 u cells, so the 6,000 u obstacle window can meet three cells per axis.
        val block = StructureTemplate(
            id = StructureTemplateId.ARCHITECTS_DOODLE,
            name = "Test block",
            weight = 1f,
            minimumDistance = 0f,
            rooms = immutableListOf(StructureRoom(-1_200f, -1_200f, 1_200f, 1_200f)),
            extraWalls = List(ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS) { index ->
                val x = -1_050f + 300f * (index % 8)
                val y = -1_000f + 400f * (index / 8)
                StructureCapsule(x, y, x, y, 20f)
            }.toImmutableList(),
            roomWalls = false,
            entranceRoom = 0,
            finalRoom = 0,
            rewards = immutableListOf(),
            secret = true,
        )
        assertEquals(ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS, block.walls.size)
        val templates = shipped.templates.map { if (it.id == block.id) block else it.copy(weight = 0f) }
        val run = state(denseStructureContent(templates) { it.copy(cellSize = 3_000f) })
        val chunk = run.content.obstacles.chunkSize.toDouble()
        var mostStructures = 0
        var mostWalls = 0
        for (chunkY in 40..59) for (chunkX in 40..59) {
            run.teleportCoreForTesting((chunkX + 0.5) * chunk, (chunkY + 0.5) * chunk)
            val field = run.structureField
            assertTrue(field.structures.size <= ContentBounds.MAX_STRUCTURES_IN_WINDOW)
            assertTrue(field.walls.size <= ContentBounds.MAX_STRUCTURE_WALLS)
            assertEquals(field.structures.sumOf { it.wallEnd - it.wallStart }, field.walls.size, "slices tile the walls")
            mostStructures = max(mostStructures, field.structures.size)
            mostWalls = max(mostWalls, field.walls.size)
        }
        assertEquals(ContentBounds.MAX_STRUCTURES_IN_WINDOW, mostStructures, "the window reaches its structure bound")
        assertEquals(ContentBounds.MAX_STRUCTURE_WALLS, mostWalls, "the window reaches its wall bound")
    }

    @Test
    fun rebaseShiftsWallsSignalsAndTheDungeonAndKeepsIds() {
        val run = state(forcedStructureContent(StructureTemplateId.VAULT), seed = 21)
        // The Core sits 250,010 u east of the origin, so the next rebase moves the origin under it.
        run.teleportCoreForTesting(64_000.0 - 250_010.0, 41_000.0)
        run.coreX = 250_010f
        run.refreshObstacleWindow()
        val field = run.structureField
        assertTrue(field.structures.isNotEmpty() && field.signals.isNotEmpty())
        val structure = field.structures[0]
        run.dungeon = DungeonState(structure.id, structure.template, structure.rotation, structure.x, structure.y, engagedRoom = 0, enteredAt = 1f)
        run.applyStructureDoors(structure.id)
        val before = run.structureField
        val wallsBefore = before.walls.map { it.absolute(run) }
        val signalsBefore = before.signals.map { Triple(it.id, run.worldOriginX + it.entranceX, run.worldOriginY + it.entranceY) }
        val dungeonX = run.worldOriginX + run.dungeon!!.x

        run.rebaseWorldIfNeeded()

        assertEquals(64_000.0, run.worldOriginX, 0.01)
        val after = run.structureField
        assertEquals(before.structures.map { it.id }, after.structures.map { it.id })
        after.walls.forEachIndexed { index, wall ->
            val (id, kind, points) = wallsBefore[index]
            assertEquals(id, wall.id)
            assertEquals(kind, wall.kind, "sealed doors stay sealed")
            wall.absolute(run).third.zip(points).forEach { (now, then) -> assertEquals(then, now, 0.05) }
        }
        after.signals.forEachIndexed { index, signal ->
            assertEquals(signalsBefore[index].first, signal.id)
            assertEquals(signalsBefore[index].second, run.worldOriginX + signal.entranceX, 0.05)
            assertEquals(signalsBefore[index].third, run.worldOriginY + signal.entranceY, 0.05)
        }
        assertEquals(dungeonX, run.worldOriginX + run.dungeon!!.x, 0.05)
        assertEquals(before.centerChunkX, after.centerChunkX)
        run.refreshStructureField(after.centerChunkX, after.centerChunkY)
        assertSame(after, run.structureField, "the window stays current across a rebase")
    }

    private fun Wall.absolute(run: MutableGameState): Triple<Long, WallKind, List<Double>> =
        Triple(id, kind, listOf(run.worldOriginX + ax, run.worldOriginY + ay, run.worldOriginX + bx, run.worldOriginY + by))

    @Test
    fun signalsListTheNearestFourUnspentStructuresWithinRange() {
        val run = state(forcedStructureContent(StructureTemplateId.DERELICT_STATION), seed = 33)
        val range = run.content.structures.signalRange.toDouble()
        // A spot with more than four entrances in range, so the bound and the order both matter.
        val spot = (0 until 40).map { 70_000.0 + 1_150.0 * it to -50_000.0 + 730.0 * it }
            .first { (x, y) -> run.entrancesAround(x, y).count { it.second <= range } > ContentBounds.MAX_STRUCTURE_SIGNALS }
        run.teleportCoreForTesting(spot.first, spot.second)
        val expected = run.entrancesAround(spot.first, spot.second).filter { it.second <= range }
        assertEquals(expected.take(ContentBounds.MAX_STRUCTURE_SIGNALS).map { it.first }, run.structureField.signals.map { it.id })
        run.structureField.signals.forEach { signal ->
            val placement = run.structurePlacement(structureCellX(signal.id), structureCellY(signal.id))!!
            assertEquals(placement.absoluteX, run.worldOriginX + signal.x, 0.01)
            assertEquals(placement.absoluteY, run.worldOriginY + signal.y, 0.01)
            assertTrue(hypot(signal.entranceX.toDouble(), signal.entranceY.toDouble()) <= range + 0.01)
        }

        // A spent structure stops signalling and the next nearest takes its place.
        val nearest = run.structureField.signals[0].id
        run.markSpent(nearest, SPENT_FLAG)
        run.teleportCoreForTesting(spot.first, spot.second)
        assertEquals(
            expected.filter { it.first != nearest }.take(ContentBounds.MAX_STRUCTURE_SIGNALS).map { it.first },
            run.structureField.signals.map { it.id },
        )
        val spent = run.structureField.structures.firstOrNull { it.id == nearest }
        if (spent != null) assertTrue(spent.spent && run.wallsOf(spent).all { it.door < 0 || it.kind == WallKind.DOOR_OPEN })
    }

    /** Every unspent structure's entrance around the absolute point with its distance, nearest first (7 × 7 cells). */
    private fun MutableGameState.entrancesAround(x: Double, y: Double): List<Pair<Long, Double>> {
        val found = ArrayList<Pair<Long, Double>>()
        for (cellY in structureCell(y) - 3..structureCell(y) + 3) for (cellX in structureCell(x) - 3..structureCell(x) + 3) {
            val placement = structurePlacement(cellX, cellY) ?: continue
            if (isSpent(placement.id)) continue
            val door = content.structures.template(placement.template).let { it.doors[it.entranceDoor] }
            val localX = if (door.axis == WallAxis.HORIZONTAL) door.center else door.line
            val localY = if (door.axis == WallAxis.HORIZONTAL) door.line else door.center
            val entranceX = placement.absoluteX + rotateQuarterX(localX, localY, placement.rotation)
            val entranceY = placement.absoluteY + rotateQuarterY(localX, localY, placement.rotation)
            found += placement.id to hypot(entranceX - x, entranceY - y)
        }
        return found.sortedBy { it.second }
    }

    private fun MutableGameState.wallsOf(structure: PlacedStructure): List<Wall> =
        structureField.walls.subList(structure.wallStart, structure.wallEnd)

    @Test
    fun doorsSealTheEngagedRoomAndLocksOpenOnlyOnceTheirRoomsAreCleared() {
        val run = state(forcedStructureContent(StructureTemplateId.VAULT), seed = 44)
        val placement = run.structureAtForTesting(12, -9)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        val id = placement.id
        fun doors(): List<WallKind> {
            val structure = run.structureField.structures[run.structureField.indexOf(id)]
            return run.wallsOf(structure).filter { it.door >= 0 }.sortedBy { it.door }.map { it.kind }
        }
        val open = WallKind.DOOR_OPEN
        val sealed = WallKind.DOOR_SEALED
        // The vault's two inner doors wait on both galleries (rooms 1 and 2).
        assertEquals(listOf(open, open, open, sealed, sealed), doors())

        val untouched = run.structureField
        val others = untouched.structures.filter { it.id != id }.flatMap { s -> untouched.walls.subList(s.wallStart, s.wallEnd) }
        run.dungeon = DungeonState(id, placement.template, placement.rotation, 0f, 0f, engagedRoom = 0, enteredAt = 0f)
        run.applyStructureDoors(id)
        assertEquals(listOf(sealed, sealed, sealed, sealed, sealed), doors(), "the engaged antechamber seals every door it touches")
        assertEquals(listOf(open, open, open, sealed, sealed), untouched.walls.filter { it.id and 0xFFL.inv() == id && it.door >= 0 }.sortedBy { it.door }.map { it.kind }, "the published window never changes")
        others.forEach { wall -> assertTrue(run.structureField.walls.any { it === wall }, "other structures keep their walls") }

        run.dungeon = run.dungeon!!.copy(engagedRoom = -1, clearedRooms = 0b0011)
        run.applyStructureDoors(id)
        assertEquals(listOf(open, open, open, sealed, sealed), doors(), "one gallery is not enough")
        run.dungeon = run.dungeon!!.copy(clearedRooms = 0b0111)
        run.applyStructureDoors(id)
        assertEquals(listOf(open, open, open, open, open), doors(), "both galleries open the vault")
        val settled = run.structureField
        run.applyStructureDoors(id)
        assertSame(settled, run.structureField, "an unchanged slice keeps the window")

        // A rebuild agrees with the replaced slice, and a spent structure stands open with no dungeon.
        run.dungeon = run.dungeon!!.copy(clearedRooms = 0, engagedRoom = 1)
        run.applyStructureDoors(id)
        val replaced = run.structureField.walls.toList()
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        assertEquals(replaced, run.structureField.walls.toList())
        run.dungeon = null
        run.markSpent(id, SPENT_FLAG)
        run.applyStructureDoors(id)
        assertEquals(List(5) { open }, doors())
        assertTrue(run.structureField.structures[run.structureField.indexOf(id)].spent)
        run.applyStructureDoors(structureId(WorldLayer.BASE, 999, 999))
    }

    @Test
    fun spentStructuresAreRememberedInABoundedFifo() {
        val run = state(canonicalGameplayContent)
        val bound = ContentBounds.MAX_SPENT_STRUCTURES
        val ids = List(bound + 1) { structureId(WorldLayer.BASE, 100 + it, -it) }
        ids.take(bound).forEach { run.markSpent(it, SPENT_FLAG) }
        assertEquals(bound, run.spentStructureIds.size)
        assertTrue(ids.take(bound).all { run.isSpent(it) && !run.isCollapsed(it) })

        val full = run.spentStructureIds
        run.markSpent(ids[bound], SPENT_FLAG)
        assertEquals(bound, run.spentStructureIds.size)
        assertFalse(run.isSpent(ids[0]), "the oldest is forgotten")
        assertTrue(run.isSpent(ids[1]) && run.isSpent(ids[bound]))
        assertEquals(bound, full.size, "the published list is never mutated")
        assertEquals(ids[0] or SPENT_FLAG, full[0])

        run.markSpent(ids[1], COLLAPSED_FLAG)
        assertEquals(bound, run.spentStructureIds.size, "one entry per structure")
        assertTrue(run.isCollapsed(ids[1]) && !run.isSpent(ids[1]))
        assertEquals(ids[1] or COLLAPSED_FLAG, run.spentStructureIds.last())
        run.markSpent(ids[2] or SPENT_FLAG, SPENT_FLAG)
        assertEquals(ids[2] or SPENT_FLAG, run.spentStructureIds.last(), "a flagged id names the same structure")
        assertEquals(bound, run.spentStructureIds.size)
    }

    @Test
    fun collapsedStructuresLeaveTheWindowButKeepTheirClearing() {
        val run = state(forcedStructureContent(StructureTemplateId.SPIRE), seed = 55)
        val placement = run.structureAtForTesting(-10, 14)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        assertTrue(run.structureField.indexOf(placement.id) >= 0)
        run.markSpent(placement.id, COLLAPSED_FLAG)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        assertEquals(-1, run.structureField.indexOf(placement.id))
        assertTrue(run.structureField.signals.none { it.id == placement.id })
        assertTrue(run.structureFootprintContains(placement.absoluteX, placement.absoluteY, 0f))
        run.obstacleField.obstacles.forEach { assertFalse(run.meetsAFootprint(it), "the clearing stays: $it") }
    }

    @Test
    fun aKeepOutSkipsOnlyTheStructuresItTouches() {
        val run = state(denseStructureContent(), seed = 66)
        val placement = run.structureAtForTesting(9, 9)
        val x = placement.absoluteX + 150.0
        val y = placement.absoluteY - 90.0
        run.teleportCoreForTesting(x, y)
        val open = run.structureField
        val radius = 900f
        run.worldKeepOut = WorldKeepOut(WorldLayer.BASE, x, y, radius)
        run.teleportCoreForTesting(x, y)
        val kept = run.structureField
        val clearance = run.content.structures.obstacleClearance
        val touched = open.structures.filter { structure ->
            val dx = max(0.0, abs(structure.x.toDouble()) - structure.halfWidth)
            val dy = max(0.0, abs(structure.y.toDouble()) - structure.halfHeight)
            hypot(dx, dy) < radius + clearance
        }
        assertTrue(touched.any { it.id == placement.id }, "the arrival point's own structure is skipped")
        assertEquals(open.structures.filter { it !in touched }.map { it.id }, kept.structures.map { it.id })
        kept.structures.forEach { structure ->
            val source = open.structures.first { it.id == structure.id }
            assertEquals(open.walls.subList(source.wallStart, source.wallEnd), kept.walls.subList(structure.wallStart, structure.wallEnd))
        }
        assertTrue(kept.signals.none { signal -> touched.any { it.id == signal.id } })

        run.worldKeepOut = WorldKeepOut(WorldLayer.NADIR, x, y, radius)
        run.teleportCoreForTesting(x, y)
        assertEquals(open.structures, run.structureField.structures, "another layer's keep-out skips nothing")
    }

    @Test
    fun theArchitectCollapsesTheWindowAndDisabledContentKeepsItEmpty() {
        val off = state(canonicalGameplayContent)
        off.teleportCoreForTesting(81_000.0, 79_000.0)
        assertSame(StructureField.EMPTY, off.structureField)
        assertNull(off.structurePlacement(16, 16))
        assertFalse(off.structureFootprintContains(81_000.0, 79_000.0, 5_000f))
        off.bossSpawned = true
        off.updateStructures(MutableGameState.FIXED_STEP)
        assertSame(StructureField.EMPTY, off.structureField)

        val run = state(forcedStructureContent(StructureTemplateId.FOUNDRY), seed = 77)
        val placement = run.structureAtForTesting(17, 16)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        val standing = run.structureField
        assertTrue(standing.structures.isNotEmpty() && standing.signals.isNotEmpty())
        run.updateStructures(MutableGameState.FIXED_STEP)
        assertSame(standing, run.structureField, "before the Architect nothing changes")

        run.bossSpawned = true
        run.updateStructures(MutableGameState.FIXED_STEP)
        val collapsed = run.structureField
        assertTrue(collapsed.bossCollapsed)
        assertTrue(collapsed.structures.isEmpty() && collapsed.walls.isEmpty() && collapsed.signals.isEmpty())
        assertEquals(standing.centerChunkX to standing.centerChunkY, collapsed.centerChunkX to collapsed.centerChunkY)
        run.updateStructures(MutableGameState.FIXED_STEP)
        assertSame(collapsed, run.structureField)
        run.teleportCoreForTesting(placement.absoluteX + 7_000.0, placement.absoluteY)
        assertTrue(run.structureField.bossCollapsed && run.structureField.structures.isEmpty(), "every later window stays empty")
        assertTrue(run.structureFootprintContains(placement.absoluteX, placement.absoluteY, 0f), "footprints remain clearings")
    }

    @Test
    fun theFootprintQueryCoversEveryStateWithItsMargin() {
        val run = state(forcedStructureContent(StructureTemplateId.VAULT), seed = 88)
        val placement = run.structureAtForTesting(-14, -6)
        val east = placement.absoluteX + placement.halfWidth
        val y = placement.absoluteY
        assertTrue(run.structureFootprintContains(placement.absoluteX, y, 0f))
        assertTrue(run.structureFootprintContains(east, y, 0f), "the edge belongs to the footprint")
        assertFalse(run.structureFootprintContains(east + 1.0, y, 0f))
        assertTrue(run.structureFootprintContains(east + 300.0, y, 300f))
        assertFalse(run.structureFootprintContains(east + 301.0, y, 300f))
        // Past the cell margin the query looks into the neighbouring cell.
        assertTrue(run.structureFootprintContains(east + 1_999.0, y, 2_000f))

        run.markSpent(placement.id, SPENT_FLAG)
        assertTrue(run.structureFootprintContains(placement.absoluteX, y, 0f), "spent footprints still count")
        run.worldLayer = WorldLayer.ZENITH
        assertFalse(run.structureFootprintContains(placement.absoluteX, y, 0f), "no structures in the other layers")
        run.worldLayer = WorldLayer.BASE

        // Obstacle candidates: a circle meeting the footprint inflated by the clearance goes, one just clear stays.
        val chunk = run.content.obstacles.chunkSize.toDouble()
        val clearance = run.content.structures.obstacleClearance
        fun suppresses(absoluteX: Double, radius: Float): Boolean {
            val chunkX = kotlin.math.floor(absoluteX / chunk).toInt()
            val chunkY = kotlin.math.floor(y / chunk).toInt()
            return run.structureSuppresses(chunkX, chunkY, (absoluteX - chunkX * chunk).toFloat(), (y - chunkY * chunk).toFloat(), radius)
        }
        assertTrue(suppresses(east + clearance + 79.0, 80f))
        assertFalse(suppresses(east + clearance + 81.0, 80f))
    }

    // -----------------------------------------------------------------------------------------
    // Geometry and wall queries.

    @Test
    fun capsuleSweepFindsTheEarliestEntryOnSidesAndCaps() {
        assertEquals(0.45f, sweepCapsuleEntry(0f, -100f, 0f, 100f, -50f, 0f, 50f, 0f, 10f), 1e-5f)
        assertEquals(0.47835f, sweepCapsuleEntry(-200f, 5f, 200f, 5f, 0f, 0f, 0f, -100f, 10f), 1e-5f)
        assertEquals(-1f, sweepCapsuleEntry(0f, -20f, 0f, -100f, -50f, 0f, 50f, 0f, 10f), "moving away")
        assertEquals(0f, sweepCapsuleEntry(10f, 5f, 400f, 400f, -50f, 0f, 50f, 0f, 10f), "starting inside")
        assertEquals(-1f, sweepCapsuleEntry(-100f, 20f, 100f, 20f, -50f, 0f, 50f, 0f, 10f), "parallel outside")
        assertEquals(-1f, sweepCapsuleEntry(30f, 30f, 30f, 30f, -50f, 0f, 50f, 0f, 10f), "not moving, outside")
        // The far side of the segment: the side line on the start's side is the one entered.
        assertEquals(0.45f, sweepCapsuleEntry(0f, 100f, 0f, -100f, -50f, 0f, 50f, 0f, 10f), 1e-5f)
        // A pillar is a circle; endpoint order never matters.
        val cases = listOf(
            floatArrayOf(-300f, 40f, 300f, -10f, 12f, 7f),
            floatArrayOf(50f, 200f, -20f, -260f, -5f, 3f),
            floatArrayOf(0f, 0f, 400f, 400f, 210f, 190f),
        )
        cases.forEach { (x0, y0, x1, y1, cx, cy) ->
            assertEquals(sweepEntry(x0, y0, x1, y1, cx, cy, 30f), sweepCapsuleEntry(x0, y0, x1, y1, cx, cy, cx, cy, 30f))
            assertEquals(
                sweepCapsuleEntry(x0, y0, x1, y1, cx - 60f, cy, cx + 60f, cy + 20f, 30f),
                sweepCapsuleEntry(x0, y0, x1, y1, cx + 60f, cy + 20f, cx - 60f, cy, 30f),
                1e-5f,
            )
        }
        assertEquals(0.5f, segmentNearest(0f, 30f, -50f, 0f, 50f, 0f))
        assertEquals(1f, segmentNearest(90f, 30f, -50f, 0f, 50f, 0f))
        assertEquals(0f, segmentNearest(90f, 30f, 7f, 7f, 7f, 7f))
    }

    private operator fun FloatArray.component6(): Float = this[5]

    @Test
    fun capsuleStepEntryStopsOverlappingCirclesOnlyMovingInward() {
        // A 24 u wall along y = 0 and a 10 u shot: they touch at 34 u.
        assertEquals(0f, capsuleStepEntry(0f, 30f, 0f, 20f, -50f, 0f, 50f, 0f, 24f, 10f), "overlapping, moving in")
        assertEquals(-1f, capsuleStepEntry(0f, 30f, 0f, 40f, -50f, 0f, 50f, 0f, 24f, 10f), "overlapping, moving out")
        assertEquals(-1f, capsuleStepEntry(0f, 30f, 20f, 30f, -50f, 0f, 50f, 0f, 24f, 10f), "overlapping, sliding along")
        assertEquals(0.66f, capsuleStepEntry(0f, 100f, 0f, 0f, -50f, 0f, 50f, 0f, 24f, 10f), 1e-5f)
        assertEquals(-1f, capsuleStepEntry(0f, 100f, 0f, 60f, -50f, 0f, 50f, 0f, 24f, 10f), "stops short")
        // Over a cap the overlap test uses the nearest point of the segment.
        assertEquals(0f, capsuleStepEntry(70f, 0f, 60f, 0f, -50f, 0f, 50f, 0f, 24f, 10f))
        assertEquals(-1f, capsuleStepEntry(70f, 0f, 80f, 0f, -50f, 0f, 50f, 0f, 24f, 10f))
    }

    @Test
    fun wallQueriesFollowTheRotatedTemplate() {
        val run = state(forcedStructureContent(StructureTemplateId.DERELICT_STATION), seed = 99)
        val template = run.content.structures.template(StructureTemplateId.DERELICT_STATION)
        val rotations = HashSet<Int>()
        var cell = 20
        while (rotations.size < 4) {
            val placement = run.structureAtForTesting(cell, -cell)
            cell++
            if (!rotations.add(placement.rotation)) continue
            run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
            val structure = run.structureField.structures[run.structureField.indexOf(placement.id)]
            template.rooms.forEachIndexed { index, room ->
                val cx = (room.minX + room.maxX) / 2f
                val cy = (room.minY + room.maxY) / 2f
                val x = structure.x + rotateQuarterX(cx, cy, structure.rotation)
                val y = structure.y + rotateQuarterY(cx, cy, structure.rotation)
                assertEquals(index, roomAt(template, structure.rotation, structure.x, structure.y, x, y, 80f))
                val inset = minOf(room.maxX - room.minX, room.maxY - room.minY) / 2f + 1f
                assertEquals(-1, roomAt(template, structure.rotation, structure.x, structure.y, x, y, inset))
            }
            assertEquals(-1, roomAt(template, structure.rotation, structure.x, structure.y, structure.x + 3_000f, structure.y, 0f))

            // Beside the middle of the longest solid wall.
            val walls = run.wallsOf(structure)
            val wall = walls.filter { it.door < 0 }.maxBy { hypot(it.bx - it.ax, it.by - it.ay) }
            val length = hypot(wall.bx - wall.ax, wall.by - wall.ay)
            val nx = -(wall.by - wall.ay) / length
            val ny = (wall.bx - wall.ax) / length
            val mx = (wall.ax + wall.bx) / 2f
            val my = (wall.ay + wall.by) / 2f
            val out = FloatArray(4)
            assertEquals(30f, run.nearestWallSurface(mx + nx * (wall.halfThickness + 30f), my + ny * (wall.halfThickness + 30f), 60f, out), 0.01f)
            assertEquals(nx, out[2], 1e-4f)
            assertEquals(ny, out[3], 1e-4f)
            assertEquals(mx + nx * wall.halfThickness, out[0], 0.01f)
            assertEquals(my + ny * wall.halfThickness, out[1], 0.01f)
            assertEquals(Float.POSITIVE_INFINITY, run.nearestWallSurface(mx + nx * (wall.halfThickness + 30f), my + ny * (wall.halfThickness + 30f), 20f, out))

            // An open doorway is clear space; once sealed its centre lies inside the door.
            val door = walls.first { it.door == 0 }
            assertEquals(WallKind.DOOR_OPEN, door.kind)
            val doorX = (door.ax + door.bx) / 2f
            val doorY = (door.ay + door.by) / 2f
            assertTrue(run.nearestWallSurface(doorX, doorY, 200f, out) > 0f)
            run.dungeon = DungeonState(structure.id, structure.template, structure.rotation, structure.x, structure.y, engagedRoom = 0, enteredAt = 0f)
            run.applyStructureDoors(structure.id)
            assertEquals(-door.halfThickness, run.nearestWallSurface(doorX, doorY, 200f, out), 0.01f)
            run.dungeon = null

            // The broad phase never misses a wall within reach.
            val solid = run.structureField.walls.withIndex().filter { it.value.kind != WallKind.DOOR_OPEN }
            for (step in 0 until 200) {
                val px = structure.x - 1_500f + (step * 37 % 3_000)
                val py = structure.y - 1_500f + (step * 61 % 3_000)
                val visited = HashSet<Int>()
                run.forEachSolidWallNear(px, py, 120f) { index, _ -> visited += index }
                solid.forEach { (index, near) ->
                    val along = segmentNearest(px, py, near.ax, near.ay, near.bx, near.by)
                    val gap = hypot(px - (near.ax + (near.bx - near.ax) * along), py - (near.ay + (near.by - near.ay) * along)) - near.halfThickness
                    if (gap <= 120f) assertTrue(index in visited, "wall $index at $gap u from $px, $py")
                }
                run.forEachSolidWallNear(px, py, 120f, includeOpen = false) { _, visitedWall -> assertNotEquals(WallKind.DOOR_OPEN, visitedWall.kind) }
            }
        }
    }
}
