// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.ball.content.api.WallAxis.HORIZONTAL
import kinetickk.ball.content.api.WallAxis.VERTICAL
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.hypot
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Structure content: the shipped templates compile to the walls the design's compile tool recorded, every layout
 * rule holds on them, and each template and policy bound accepts its limit and rejects the next value.
 */
class StructureContentTest {
    private val templates = defaultStructureTemplates()

    @Test
    fun shippedTemplatesCompileToTheRecordedWalls() {
        val counts = listOf(17, 17, 17, 26, 23, 34, 13)
        assertEquals(counts, templates.map { it.walls.size })
        val footprints = listOf(
            2_200f to 1_400f, 2_200f to 2_200f, 1_000f to 2_400f, 2_100f to 2_100f, 2_400f to 1_600f, 2_400f to 2_400f,
            2_176f to 1_318.2f,
        )
        assertEquals(footprints, templates.map { 2f * it.halfWidth to 2f * it.halfHeight })
        val recorded = mapOf(
            StructureTemplateId.DERELICT_STATION to listOf(
                0 to solid(-500f, -700f, 1100f, -700f),
                1 to solid(-1100f, -300f, -500f, -300f),
                6 to solid(-1100f, -300f, -1100f, -164f),
                11 to solid(400f, 564f, 400f, 700f),
                12 to solid(1100f, -700f, 1100f, 700f),
                13 to door(0, -1100f, -140f, -1100f, 140f),
                14 to door(1, -500f, -140f, -500f, 140f),
                15 to door(2, 400f, 260f, 400f, 540f),
                16 to door(3, 610f, 100f, 890f, 100f),
            ),
            StructureTemplateId.VAULT to listOf(
                0 to solid(-1100f, -1100f, 1100f, -1100f),
                1 to solid(-400f, 300f, 400f, 300f),
                6 to solid(-400f, -536f, -400f, 536f),
                10 to solid(400f, 864f, 400f, 1100f),
                11 to solid(1100f, -1100f, 1100f, 1100f),
                12 to door(0, -140f, 1100f, 140f, 1100f),
                13 to door(1, -400f, 560f, -400f, 840f),
                14 to door(2, 400f, 560f, 400f, 840f),
                15 to door(3, -400f, -840f, -400f, -560f),
                16 to door(4, 400f, -840f, 400f, -560f),
            ),
            StructureTemplateId.SPIRE to listOf(
                0 to solid(-500f, -1200f, 500f, -1200f),
                1 to solid(-500f, -600f, -414f, -600f),
                6 to solid(-86f, 600f, 500f, 600f),
                11 to solid(-150f, 300f, 150f, 300f),
                12 to solid(-150f, -300f, 150f, -300f),
                13 to door(0, -140f, 1200f, 140f, 1200f),
                14 to door(1, -390f, 600f, -110f, 600f),
                15 to door(2, 110f, 0f, 390f, 0f),
                16 to door(3, -390f, -600f, -110f, -600f),
            ),
            StructureTemplateId.LABYRINTH to listOf(
                0 to solid(-1050f, -1050f, 1050f, -1050f),
                1 to solid(-1050f, -350f, -854f, -350f),
                8 to solid(-1050f, -1050f, -1050f, 1050f),
                15 to solid(350f, 154f, 350f, 1050f),
                16 to solid(1050f, -1050f, 1050f, 1050f),
                17 to door(0, -830f, 1050f, -570f, 1050f),
                18 to door(1, -350f, 570f, -350f, 830f),
                19 to door(2, -130f, 350f, 130f, 350f),
                20 to door(3, -350f, -130f, -350f, 130f),
                21 to door(4, -830f, -350f, -570f, -350f),
                22 to door(5, -350f, -830f, -350f, -570f),
                23 to door(6, 350f, -130f, 350f, 130f),
                24 to door(7, 570f, 350f, 830f, 350f),
                25 to door(8, 350f, -830f, 350f, -570f),
            ),
            StructureTemplateId.FOUNDRY to listOf(
                0 to solid(-1200f, -800f, 700f, -800f),
                1 to solid(700f, -400f, 1200f, -400f),
                9 to solid(700f, -800f, 700f, -164f),
                17 to solid(300f, -450f, 300f, -450f, h = 70f),
                18 to solid(300f, 450f, 300f, 450f, h = 70f),
                19 to door(0, -1200f, -140f, -1200f, 140f),
                20 to door(1, -100f, -640f, -100f, -360f),
                21 to door(2, -100f, 360f, -100f, 640f),
                22 to door(3, 700f, -140f, 700f, 140f),
            ),
            StructureTemplateId.RELIQUARY to listOf(
                0 to solid(-1200f, -1200f, 1200f, -1200f),
                1 to solid(-1200f, -400f, -964f, -400f),
                10 to solid(164f, 1200f, 1200f, 1200f),
                19 to solid(400f, 964f, 400f, 1200f),
                20 to solid(1200f, -1200f, 1200f, 1200f),
                21 to door(0, -140f, 1200f, 140f, 1200f),
                22 to door(1, -400f, 660f, -400f, 940f),
                23 to door(2, 400f, 660f, 400f, 940f),
                24 to door(3, -940f, 400f, -660f, 400f),
                25 to door(4, 660f, 400f, 940f, 400f),
                26 to door(5, -940f, -400f, -660f, -400f),
                27 to door(6, 660f, -400f, 940f, -400f),
                28 to door(7, -400f, -940f, -400f, -660f),
                29 to door(8, 400f, -940f, 400f, -660f),
                30 to door(9, -140f, 400f, 140f, 400f),
                31 to door(10, -400f, -140f, -400f, 140f),
                32 to door(11, 400f, -140f, 400f, 140f),
                33 to door(12, -140f, -400f, 140f, -400f),
            ),
            StructureTemplateId.ARCHITECTS_DOODLE to listOf(
                0 to solid(659.1f, 131.1f, 558.7f, 373.3f),
                1 to solid(558.7f, 373.3f, 373.3f, 558.7f),
                6 to solid(-659.1f, -131.1f, -558.7f, -373.3f),
                11 to solid(659.1f, -131.1f, 659.1f, 131.1f),
                12 to solid(-1088f, 566.4f, 1088f, -566.4f),
            ),
        )
        recorded.forEach { (id, walls) ->
            val template = templates[id.ordinal]
            walls.forEach { (index, wall) -> assertEquals(wall, template.walls[index], "${template.name} wall $index") }
            // Solid walls first, then one door capsule per door in door order.
            val doorWalls = template.walls.filter { it.isDoor }
            assertEquals(template.doors.indices.toList(), doorWalls.map { it.door }, template.name)
            assertEquals(doorWalls, template.walls.takeLast(template.doors.size), template.name)
        }
        // Every compiled number of every template, folded over the raw float bits (the compile tool's own result).
        var fold = 17L
        templates.forEach { template ->
            template.walls.forEach { wall ->
                listOf(wall.ax, wall.ay, wall.bx, wall.by, wall.halfThickness).forEach { fold = fold * 31 + it.toRawBits() }
                fold = fold * 31 + wall.door
            }
        }
        assertEquals(2_595_552_187_804_222_925L, fold)
    }

    @Test
    fun theShippedTableKeepsItsWeightsDistancesRoomsAndRewards() {
        assertEquals(StructureTemplateId.entries, templates.map { it.id })
        assertEquals(listOf(30f, 22f, 16f, 14f, 12f, 6f, 3f), templates.map { it.weight })
        assertEquals(103f, templates.sumOf { it.weight.toDouble() }.toFloat())
        assertEquals(listOf(4_000f, 6_000f, 5_000f, 7_000f, 6_000f, 9_000f, 12_000f), templates.map { it.minimumDistance })
        assertEquals(listOf(4, 4, 4, 9, 3, 9, 1), templates.map { it.rooms.size })
        assertEquals(listOf(4, 5, 4, 9, 4, 13, 0), templates.map { it.doors.size })
        assertEquals(listOf(2, 3, 3, 2, 2, 4, 0), templates.map { it.finalRoom })
        assertEquals(
            listOf(
                listOf("Key", "Matter", "Data"),
                listOf("Relic", "Rare item"),
                listOf("Uncommon item", "Key", "Data"),
                listOf("Relic", "Matter"),
                listOf("Matter", "Repair", "Key"),
                listOf("Relic", "Epic item", "Key"),
                listOf("Data"),
            ),
            templates.map { template -> template.rewards.map { it.label } },
        )
        // Guarded vaults: a mini-boss holds the Vault's and the Reliquary's final rooms.
        assertEquals(EnemyType.BROOD_QUEEN, templates[StructureTemplateId.VAULT.ordinal].finalGuardian())
        assertEquals(EnemyType.WARDEN_PRIME, templates[StructureTemplateId.RELIQUARY.ordinal].finalGuardian())
        val doodle = templates[StructureTemplateId.ARCHITECTS_DOODLE.ordinal]
        assertTrue(doodle.secret && !doodle.roomWalls && doodle.rooms.all { it.garrison.isEmpty() })
        assertTrue(templates.dropLast(1).none { it.secret })
    }

    @Test
    fun templatesPublishTheirClearMaskEntranceDoorAndCircumradius() {
        // Rooms with a garrison plus the final room; empty passages never count.
        assertEquals(
            listOf(0b1111, 0b1111, 0b1111, 0b1_0111_0111, 0b111, 0b1_0101_0101, 0b1),
            templates.map { it.roomsToClearMask },
        )
        assertEquals(listOf(0, 0, 0, 0, 0, 0, -1), templates.map { it.entranceDoor })
        templates.forEach { assertEquals(hypot(it.halfWidth, it.halfHeight), it.circumradius) }
        assertEquals(1_697.0563f, templates.maxOf { it.circumradius }, 0.001f)
    }

    @Test
    fun everyTemplateConnectsAllRoomsFromItsEntrance() {
        templates.filter { it.roomWalls }.forEach { template ->
            val outer = template.doors[template.entranceDoor]
            assertEquals(template.entranceRoom, outer.roomA, template.name)
            assertEquals(STRUCTURE_OUTSIDE, outer.roomB, template.name)
            var reached = setOf(template.entranceRoom)
            repeat(template.rooms.size) {
                reached = reached + template.doors.filter { it.roomB >= 0 && (it.roomA in reached || it.roomB in reached) }
                    .flatMap { listOf(it.roomA, it.roomB) }
            }
            assertEquals(template.rooms.indices.toSet(), reached, template.name)
        }
        val station = templates[StructureTemplateId.DERELICT_STATION.ordinal]
        // Without the bay-to-reactor door the reactor is cut off.
        assertRejected("no door path") { station.copy(doors = station.doors.take(3).toImmutableList()) }
        // A lock never opens when it waits on a room that lies behind the locked door.
        assertRejected("no door path") { station.copy(doors = station.doors.locked(door = 3, rooms = 1 shl 2)) }
        assertRejected("no door path") { station.copy(doors = station.doors.locked(door = 2, rooms = 1 shl 2)) }
        // The Vault's inner doors wait on both galleries, the door's own gallery included, and the galleries open first.
        val vault = templates[StructureTemplateId.VAULT.ordinal]
        assertEquals(listOf(0, 0, 0, 0b0110, 0b0110), vault.doors.map { it.lockRooms })
        // Nor when two locks wait on each other's rooms.
        val crossed = vault.doors.locked(door = 1, rooms = 0b0100).locked(door = 2, rooms = 0b0010)
        assertRejected("no door path") { vault.copy(doors = crossed) }
    }

    @Test
    fun doorsSitOnSharedBoundariesAndLockOnExistingRooms() {
        templates.forEach { template ->
            template.doors.forEachIndexed { index, door ->
                val wall = template.walls.single { it.door == index }
                assertEquals(door.width, (wall.bx - wall.ax) + (wall.by - wall.ay), "${template.name} door $index")
                val joined = listOfNotNull(template.rooms[door.roomA], template.rooms.getOrNull(door.roomB))
                joined.forEach { room -> assertTrue(room.edgeHolds(wall), "${template.name} door $index") }
                assertEquals(0, door.lockRooms ushr template.rooms.size, template.name)
            }
        }
        val base = pair()
        // Off every boundary, or on one room's boundary only.
        val outer = base.doors[0]
        val inner = base.doors[1]
        assertRejected("must sit on the boundary") { base.withDoors(outer, StructureDoor(VERTICAL, 100f, 0f, 280f, 0, 1)) }
        assertRejected("must sit on the boundary") { base.withDoors(outer, StructureDoor(VERTICAL, -500f, 0f, 280f, 1, 0)) }
        // An outer door must open to the outside, from the entrance, and only once.
        assertRejected("to the outside") { base.withDoors(StructureDoor(VERTICAL, 0f, 0f, 280f, 0, STRUCTURE_OUTSIDE), inner) }
        assertRejected("exactly one outer door") {
            base.withDoors(outer, inner, StructureDoor(VERTICAL, 500f, 0f, 280f, 1, STRUCTURE_OUTSIDE))
        }
        assertRejected("exactly one outer door") { base.copy(entranceRoom = 1, finalRoom = 0) }
        // Locks name rooms that exist.
        val allRooms = (1 shl ContentBounds.MAX_STRUCTURE_ROOMS) - 1
        inner.copy(lockRooms = allRooms)
        assertFailsWith<IllegalArgumentException> { inner.copy(lockRooms = allRooms + 1) }
        assertFailsWith<IllegalArgumentException> { inner.copy(lockRooms = -1) }
        assertRejected("locks on a missing room") { base.withDoors(outer, inner.copy(lockRooms = 0b100)) }
        // Two doors on one opening, or a door whose jamb is shorter than 40 u.
        assertRejected("clear of other doors") { base.withDoors(outer, inner, inner.copy(center = 100f)) }
        jambed(center = -56f)
        assertRejected("jamb shorter") { jambed(center = -57f) }
        // Door widths stay within 160..400.
        StructureDoor(VERTICAL, 0f, 0f, 160f, 0, 1)
        StructureDoor(VERTICAL, 0f, 0f, 400f, 0, 1)
        assertFailsWith<IllegalArgumentException> { StructureDoor(VERTICAL, 0f, 0f, 160f.nextDown(), 0, 1) }
        assertFailsWith<IllegalArgumentException> { StructureDoor(VERTICAL, 0f, 0f, 400f.nextUp(), 0, 1) }
    }

    @Test
    fun templateBoundsAcceptTheLimitAndRejectTheNext() {
        // Walls: the pair compiles 9, extra pillars bring it to the bound.
        assertEquals(9, pair().walls.size)
        assertEquals(ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS, pair(pillars = 39).walls.size)
        assertRejected("compiles 49 walls") { pair(pillars = 40) }
        // Rooms.
        assertEquals(ContentBounds.MAX_STRUCTURE_ROOMS, grid(columns = 4, rows = 3, count = 12).rooms.size)
        assertRejected("13 rooms") { grid(columns = 5, rows = 3, count = 13, connected = 12) }
        // Doors.
        assertEquals(ContentBounds.MAX_STRUCTURE_DOORS, grid(columns = 4, rows = 3, count = 12, extraDoors = 4).doors.size)
        assertRejected("17 doors") { grid(columns = 4, rows = 3, count = 12, extraDoors = 5) }
        // Garrison per room: twelve enemies over at most four entries.
        val habitat = { count: Int -> GarrisonEntry(GarrisonSelector.HABITAT, count) }
        room(habitat(12))
        room(habitat(6), habitat(6))
        room(habitat(3), habitat(3), habitat(3), habitat(3))
        assertFailsWith<IllegalArgumentException> { habitat(13) }
        assertFailsWith<IllegalArgumentException> { habitat(0) }
        assertFailsWith<IllegalArgumentException> { room(habitat(6), habitat(7)) }
        assertFailsWith<IllegalArgumentException> { room(habitat(1), habitat(1), habitat(1), habitat(1), habitat(1)) }
        // Footprint per axis, centred on the origin.
        assertEquals(1_200f, pair(halfWidth = 1_200f).halfWidth)
        assertRejected("exceeds") { pair(halfWidth = 1_200.5f) }
        pair(offsetX = 0.5f)
        assertRejected("centred") { pair(offsetX = 0.75f) }
        // Rewards.
        val data = StructureReward(StructureRewardKind.DATA, 2)
        pair().copy(rewards = List(ContentBounds.MAX_STRUCTURE_REWARDS) { data }.toImmutableList())
        assertRejected("7 rewards") {
            pair().copy(rewards = List(ContentBounds.MAX_STRUCTURE_REWARDS + 1) { data }.toImmutableList())
        }
        // Rooms, names and wall thicknesses.
        StructureRoom(0f, 0f, 400f, 400f)
        assertFailsWith<IllegalArgumentException> { StructureRoom(0f, 0f, 400f.nextDown(), 400f) }
        pair().copy(name = "N".repeat(32))
        assertRejected("1..32 characters") { pair().copy(name = "N".repeat(33)) }
        assertRejected("1..32 characters") { pair().copy(name = " ") }
        pair().copy(wallHalfThickness = 12f)
        pair().copy(wallHalfThickness = 60f)
        assertRejected("half thickness") { pair().copy(wallHalfThickness = 60f.nextUp()) }
        assertRejected("half thickness") { pair().copy(wallHalfThickness = 12f.nextDown()) }
        StructureCapsule(0f, 0f, 0f, 0f, 80f)
        assertFailsWith<IllegalArgumentException> { StructureCapsule(0f, 0f, 0f, 0f, 80f.nextUp()) }
        assertFailsWith<IllegalArgumentException> { StructureCapsule(0f, 0f, 0f, 0f, 12f.nextDown()) }
    }

    @Test
    fun garrisonsSecretsAndRoomsFollowTheLayoutRules() {
        // A fixed entry names its kind; a habitat entry never does; no Architect, Elite or Herald guards a room.
        GarrisonEntry(GarrisonSelector.FIXED, 1, EnemyType.WARDEN)
        assertFailsWith<IllegalArgumentException> { GarrisonEntry(GarrisonSelector.FIXED, 1) }
        assertFailsWith<IllegalArgumentException> { GarrisonEntry(GarrisonSelector.HABITAT, 1, EnemyType.DRIFTER) }
        listOf(EnemyType.ARCHITECT, EnemyType.ELITE, EnemyType.HERALD).forEach { type ->
            assertFailsWith<IllegalArgumentException> { GarrisonEntry(GarrisonSelector.FIXED, 1, type) }
        }
        val doodle = templates[StructureTemplateId.ARCHITECTS_DOODLE.ordinal]
        val sentinel = immutableListOf(GarrisonEntry(GarrisonSelector.HABITAT, 1))
        val guarded = immutableListOf(doodle.rooms[0].copy(garrison = sentinel))
        assertRejected("is secret") { doodle.copy(rooms = guarded) }
        assertRejected("final room must differ") { doodle.copy(secret = false) }
        assertRejected("without room walls has no doors") {
            doodle.withDoors(StructureDoor(HORIZONTAL, 441f, 0f, 280f, 0, STRUCTURE_OUTSIDE))
        }
        val base = pair()
        assertRejected("final room must differ") { base.copy(finalRoom = 0) }
        assertRejected("must exist") { base.copy(finalRoom = 2) }
        assertRejected("overlap") { base.copy(rooms = immutableListOf(base.rooms[0], base.rooms[1].copy(minX = -1f))) }
        assertRejected("1..12 allowed") { base.copy(rooms = immutableListOf(), doors = immutableListOf()) }
        assertRejected("weight") { base.copy(weight = -1f) }
        assertRejected("minimum distance") { base.copy(minimumDistance = Float.NaN) }
    }

    @Test
    fun rewardsStayWithinTheirChannelAmounts() {
        val limits = listOf(
            StructureRewardKind.RELIC to 3, StructureRewardKind.KEY to 3, StructureRewardKind.REPAIR to 3,
            StructureRewardKind.DATA to 16, StructureRewardKind.MATTER to 400,
        )
        limits.forEach { (kind, limit) ->
            StructureReward(kind, limit)
            assertFailsWith<IllegalArgumentException> { StructureReward(kind, limit + 1) }
            assertFailsWith<IllegalArgumentException> { StructureReward(kind, 0) }
            assertFailsWith<IllegalArgumentException> { StructureReward(kind, 1, ItemRarity.RARE) }
        }
        listOf(ItemRarity.UNCOMMON, ItemRarity.RARE, ItemRarity.EPIC).forEach {
            StructureReward(StructureRewardKind.ITEM_OFFER, 1, it)
        }
        listOf(null, ItemRarity.COMMON, ItemRarity.LEGENDARY).forEach { floor ->
            assertFailsWith<IllegalArgumentException> { StructureReward(StructureRewardKind.ITEM_OFFER, 1, floor) }
        }
        assertFailsWith<IllegalArgumentException> { StructureReward(StructureRewardKind.ITEM_OFFER, 2, ItemRarity.RARE) }
    }

    @Test
    fun policyRejectsSignalsPastTwoCellsCellsTooSmallAndALeaveRadiusInsideTheFootprint() {
        val policy = StructurePolicy()
        assertEquals(true, policy.enabled, "structures ship on")
        // Signals reach at most two cells and the four-digit marker readout.
        StructurePolicy(signalRange = 9_600f)
        assertRejected("two cells") { StructurePolicy(signalRange = 9_600f.nextUp()) }
        StructurePolicy(cellSize = 6_000f, signalRange = 9_990f)
        assertRejected("two cells") { StructurePolicy(cellSize = 6_000f, signalRange = 9_990f.nextUp()) }
        // A cell holds the largest footprint (2,400) inside its two margins (300).
        StructurePolicy(cellSize = 3_000f, signalRange = 5_000f)
        assertRejected("largest footprint") { StructurePolicy(cellSize = 3_000f.nextDown(), signalRange = 5_000f) }
        StructurePolicy(cellSize = 3_200f, cellMargin = 400f, signalRange = 5_000f)
        assertRejected("largest footprint") { StructurePolicy(cellSize = 3_200f, cellMargin = 400.25f, signalRange = 5_000f) }
        // Leaving means 300 u past the Reliquary's circumradius.
        val leave = policy.templates.maxOf { it.circumradius } + 300f
        StructurePolicy(leaveRadius = leave)
        assertRejected("leave radius") { StructurePolicy(leaveRadius = leave.nextDown()) }
        // Every other knob at its bounds and just past them.
        StructurePolicy(entryInset = 48f)
        StructurePolicy(entryInset = 200f)
        assertRejected("entry inset") { StructurePolicy(entryInset = 48f.nextDown()) }
        assertRejected("entry inset") { StructurePolicy(entryInset = 200f.nextUp()) }
        StructurePolicy(engageCutoffSeconds = 120f, garrisonGateLeadSeconds = 300f, obstacleClearance = 600f)
        assertRejected("engage cutoff") { StructurePolicy(engageCutoffSeconds = 120f.nextUp()) }
        assertRejected("gate lead") { StructurePolicy(garrisonGateLeadSeconds = 300f.nextUp()) }
        assertRejected("obstacle clearance") { StructurePolicy(obstacleClearance = 600f.nextUp()) }
        StructurePolicy(garrisonExtraPerTiers = 1)
        assertRejected("at least one tier") { StructurePolicy(garrisonExtraPerTiers = 0) }
        StructurePolicy(garrisonScale = 0.25f)
        StructurePolicy(garrisonScale = 3f)
        assertRejected("Garrison scale") { StructurePolicy(garrisonScale = 0.25f.nextDown()) }
        assertRejected("Garrison scale") { StructurePolicy(garrisonScale = 3f.nextUp()) }
        StructurePolicy(directorRateWhileSealed = 1f, wallDamageScale = 2f, maximumChance = 1f)
        assertRejected("director rate") { StructurePolicy(directorRateWhileSealed = 1f.nextUp()) }
        assertRejected("Wall damage") { StructurePolicy(wallDamageScale = 2f.nextUp()) }
        assertRejected("maximum chance") { StructurePolicy(maximumChance = 1f.nextUp()) }
        assertRejected("distances") { StructurePolicy(minimumDistance = 10_000f) }
        assertRejected("distances") { StructurePolicy(minimumDistance = -1f) }
        // One chance per world layer.
        StructurePolicy(layerChance = immutableListOf(1f, 1f, 1f, 1f))
        assertRejected("every layer") { StructurePolicy(layerChance = immutableListOf(1f, 0f, 0f)) }
        assertRejected("every layer") { StructurePolicy(layerChance = immutableListOf(1f, 0f, 0f, 1f.nextUp())) }
        // Templates follow their ids, and one weighted template is placeable within a cell of the minimum distance.
        assertRejected("StructureTemplateId order") { StructurePolicy(templates = policy.templates.reversed().toImmutableList()) }
        val doodleOnly = policy.templates.map { if (it.secret) it else it.copy(weight = 0f) }.toImmutableList()
        StructurePolicy(minimumDistance = 7_200f, templates = doodleOnly)
        assertRejected("placeable within a cell") { StructurePolicy(minimumDistance = 7_199f, templates = doodleOnly) }
    }

    @Test
    fun templateIdsStayWithinTheBound() {
        assertTrue(StructureTemplateId.entries.size <= ContentBounds.MAX_STRUCTURE_TEMPLATES)
        val policy = StructurePolicy()
        StructureTemplateId.entries.forEach { assertEquals(it, policy.template(it).id) }
        assertTrue(templates.all { it.walls.size <= ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS })
        assertTrue(templates.all { it.rooms.size <= ContentBounds.MAX_STRUCTURE_ROOMS })
        assertTrue(templates.all { it.doors.size <= ContentBounds.MAX_STRUCTURE_DOORS })
    }

    @Test
    fun quarterTurnsRotateClockwiseOnScreenAndInvert() {
        // +y points down the screen, so a point east and a little north turns south and a little east.
        assertEquals(1f to 2f, rotateQuarterX(2f, -1f, 1) to rotateQuarterY(2f, -1f, 1))
        assertEquals(-2f to 1f, rotateQuarterX(2f, -1f, 2) to rotateQuarterY(2f, -1f, 2))
        assertEquals(-1f to -2f, rotateQuarterX(2f, -1f, 3) to rotateQuarterY(2f, -1f, 3))
        val point = 300f to -700f
        (0..3).forEach { quarter ->
            val x = rotateQuarterX(point.first, point.second, quarter)
            val y = rotateQuarterY(point.first, point.second, quarter)
            val inverse = (4 - quarter) and 3
            assertEquals(point, rotateQuarterX(x, y, inverse) to rotateQuarterY(x, y, inverse))
            // Only the quarter's low two bits count.
            assertEquals(x, rotateQuarterX(point.first, point.second, quarter + 4))
            assertEquals(y, rotateQuarterY(point.first, point.second, quarter - 4))
        }
    }

    private fun List<StructureDoor>.locked(door: Int, rooms: Int): ImmutableList<StructureDoor> =
        mapIndexed { index, value -> if (index == door) value.copy(lockRooms = rooms) else value }.toImmutableList()

    private fun StructureTemplate.withDoors(vararg doors: StructureDoor): StructureTemplate =
        copy(doors = doors.toList().toImmutableList())

    private fun StructureTemplate.finalGuardian(): EnemyType? =
        rooms[finalRoom].garrison.firstOrNull { it.placement == GarrisonPlacement.CENTER }?.type

    /** The wall lies on one of the room's edges, inside its span. */
    private fun StructureRoom.edgeHolds(wall: StructureWallSpec): Boolean =
        if (wall.ay == wall.by) {
            (wall.ay == minY || wall.ay == maxY) && minX <= wall.ax && wall.bx <= maxX
        } else {
            (wall.ax == minX || wall.ax == maxX) && minY <= wall.ay && wall.by <= maxY
        }

    private fun solid(ax: Float, ay: Float, bx: Float, by: Float, h: Float = 24f) = StructureWallSpec(ax, ay, bx, by, h, -1)

    private fun door(index: Int, ax: Float, ay: Float, bx: Float, by: Float) = StructureWallSpec(ax, ay, bx, by, 24f, index)

    private fun room(vararg garrison: GarrisonEntry) = StructureRoom(0f, 0f, 500f, 500f, garrison.toList().toImmutableList())

    /**
     * Two side-by-side rooms 500 u tall, entered through the west wall and joined at x = [offsetX]: seven solid pieces
     * and two door capsules, plus [pillars] extra capsules.
     */
    private fun pair(halfWidth: Float = 500f, offsetX: Float = 0f, pillars: Int = 0): StructureTemplate = StructureTemplate(
        id = StructureTemplateId.DERELICT_STATION,
        name = "Pair",
        weight = 1f,
        minimumDistance = 0f,
        rooms = immutableListOf(
            StructureRoom(-halfWidth + offsetX, -250f, offsetX, 250f),
            StructureRoom(offsetX, -250f, halfWidth + offsetX, 250f),
        ),
        doors = immutableListOf(
            StructureDoor(VERTICAL, -halfWidth + offsetX, 0f, 280f, 0, STRUCTURE_OUTSIDE),
            StructureDoor(VERTICAL, offsetX, 0f, 280f, 0, 1),
        ),
        extraWalls = List(pillars) { StructureCapsule(-250f, 0f, -250f, 0f, 30f) }.toImmutableList(),
        entranceRoom = 0,
        finalRoom = 1,
        rewards = immutableListOf(StructureReward(StructureRewardKind.DATA, 4)),
    )

    /** [pair] with 400 u rooms and a 160 u inner door at [center]: the north jamb is `center + 96` long. */
    private fun jambed(center: Float): StructureTemplate {
        val base = pair()
        val rooms = immutableListOf(StructureRoom(-400f, -200f, 0f, 200f), StructureRoom(0f, -200f, 400f, 200f))
        val doors = immutableListOf(
            StructureDoor(VERTICAL, -400f, 0f, 160f, 0, STRUCTURE_OUTSIDE),
            StructureDoor(VERTICAL, 0f, center, 160f, 0, 1),
        )
        return base.copy(rooms = rooms, doors = doors)
    }

    /**
     * [count] rooms of 400 u laid row by row in a serpentine over a [columns] x [rows] grid centred on the origin,
     * entered through room 0's north wall; the first [connected] rooms are joined along the serpentine and
     * [extraDoors] more doors join vertically adjacent rooms the serpentine does not.
     */
    private fun grid(columns: Int, rows: Int, count: Int, connected: Int = count, extraDoors: Int = 0): StructureTemplate {
        val size = 400f
        val left = -columns * size / 2f
        val top = -rows * size / 2f
        fun column(index: Int): Int = if (index / columns % 2 == 0) index % columns else columns - 1 - index % columns
        fun row(index: Int): Int = index / columns
        val rooms = (0 until count).map { index ->
            val x = left + column(index) * size
            val y = top + row(index) * size
            StructureRoom(x, y, x + size, y + size)
        }
        val doors = mutableListOf(StructureDoor(HORIZONTAL, top, left + size / 2f, 160f, 0, STRUCTURE_OUTSIDE))
        val joined = mutableSetOf<Pair<Int, Int>>()
        fun join(a: Int, b: Int) {
            joined += a to b
            doors += if (row(a) == row(b)) {
                StructureDoor(VERTICAL, left + maxOf(column(a), column(b)) * size, top + row(a) * size + size / 2f, 160f, a, b)
            } else {
                StructureDoor(HORIZONTAL, top + maxOf(row(a), row(b)) * size, left + column(a) * size + size / 2f, 160f, a, b)
            }
        }
        (0 until connected - 1).forEach { join(it, it + 1) }
        var added = 0
        for (a in 0 until connected) {
            for (b in a + 1 until connected) {
                if (added < extraDoors && column(a) == column(b) && row(b) == row(a) + 1 && (a to b) !in joined) {
                    join(a, b)
                    added++
                }
            }
        }
        check(added == extraDoors) { "The grid has no room for $extraDoors extra doors" }
        return StructureTemplate(
            id = StructureTemplateId.LABYRINTH, name = "Grid", weight = 1f, minimumDistance = 0f,
            rooms = rooms.toImmutableList(), doors = doors.toImmutableList(), entranceRoom = 0, finalRoom = count - 1,
            rewards = immutableListOf(),
        )
    }

    private fun assertRejected(message: String, build: () -> Any) {
        val failure = assertFailsWith<IllegalArgumentException> { build() }
        assertTrue(failure.message.orEmpty().contains(message), "Expected '$message' in '${failure.message}'")
    }
}
