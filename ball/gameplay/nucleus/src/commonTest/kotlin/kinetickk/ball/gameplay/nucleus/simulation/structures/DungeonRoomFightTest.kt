// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A sealed room's fight after its seal: later waves planned as the last one thins out or stands too long, their breach
 * points held through the telegraph and landed there, the final room's guardian wave, a wave refused by the caps,
 * room champions from their Rebirth tier, and the fight's lifecycle (rebase, reset, the reduction fork).
 */
class DungeonRoomFightTest {
    private val step = MutableGameState.FIXED_STEP

    @Test
    fun aThinnedWaveCallsTheNextWhichLandsWhereItsBreachPointsGlowed() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val sealed = assertNotNull(run.dungeon?.fight)
        assertEquals(2, sealed.waves)
        assertEquals(1, sealed.landed)
        assertEquals(6, sealed.landedMembers)
        assertEquals(run.elapsed, sealed.sealedAt)
        assertTrue(run.takeVisualFxCues().any { it is VisualFxCue.DungeonMoment && it.kind == DungeonMomentKind.SEALED })

        // Three of six still stand: above the trigger share (two), nothing comes.
        val garrison = dungeon.garrison(1)
        garrison.take(3).forEach { run.onEnemyKilled(it); run.enemies.remove(it) }
        run.updateStructures(step)
        assertEquals(-1f, run.dungeon?.fight?.incomingAt)

        // At two the next wave (the hall's three habitat picks) is planned a telegraph from now, inside the hall.
        garrison.drop(3).take(1).forEach { run.onEnemyKilled(it); run.enemies.remove(it) }
        run.updateStructures(step)
        val planned = assertNotNull(run.dungeon?.fight)
        assertEquals(run.elapsed + run.content.structures.waveTelegraphSeconds, planned.incomingAt)
        assertEquals(3, planned.incoming.size)
        assertEquals(6, planned.incomingPoints.size)
        for (member in planned.incoming.indices) {
            assertEquals(1, dungeon.roomOf(planned.incomingPoints[member * 2], planned.incomingPoints[member * 2 + 1]))
        }
        val before = run.enemies.size
        run.elapsed = planned.incomingAt - step
        run.updateStructures(step)
        assertEquals(before, run.enemies.size, "nothing lands before the telegraph runs out")

        run.elapsed = planned.incomingAt
        run.updateStructures(step)
        val landed = dungeon.garrison(1).takeLast(3)
        assertEquals(planned.incoming, landed.map { it.type })
        assertEquals(planned.incomingPoints, landed.flatMap { listOf(it.x, it.y) })
        assertEquals(StructureNotice.REINFORCEMENTS.key, run.message)
        val fight = assertNotNull(run.dungeon?.fight)
        assertEquals(listOf(2, 3), listOf(fight.landed, fight.landedMembers))
        assertTrue(fight.incoming.isEmpty() && fight.incomingAt == -1f)
        assertTrue(run.takeVisualFxCues().any { it is VisualFxCue.DungeonMoment && it.kind == DungeonMomentKind.WAVE })

        // Every wave landed: the last kills clear the hall.
        run.killGarrisonForTesting(laterWaves = false)
        run.updateStructures(step)
        assertEquals(-1, run.dungeon?.engagedRoom)
        assertNull(run.dungeon?.fight)
        assertTrue(run.takeVisualFxCues().any { it is VisualFxCue.DungeonMoment && it.kind == DungeonMomentKind.ROOM_CLEARED })
    }

    @Test
    fun aWaveStandingForTheIntervalCallsTheNextAnyway() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        val sealedAt = assertNotNull(run.dungeon?.fight).sealedAt
        run.elapsed = sealedAt + run.content.structures.waveIntervalSeconds - step
        run.updateStructures(step)
        assertEquals(-1f, run.dungeon?.fight?.incomingAt)
        run.elapsed = sealedAt + run.content.structures.waveIntervalSeconds
        run.updateStructures(step)
        assertTrue(assertNotNull(run.dungeon?.fight).incomingAt > run.elapsed)
        assertEquals(6, dungeon.garrison(1).size, "the standing wave stays")
    }

    @Test
    fun theFinalRoomsLastWaveIsItsGuardianWithEscorts() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        for (room in listOf(0, 1, 3)) dungeon.clear(room)
        dungeon.enterRoom(2)
        assertEquals(3, run.dungeon?.fight?.waves)
        assertEquals(-1, run.dungeon?.fight?.guardianId)
        run.takeVisualFxCues()
        run.fightToLastWaveForTesting()
        val fight = assertNotNull(run.dungeon?.fight)
        assertEquals(3, fight.landed)
        val guardian = assertNotNull(run.enemies.firstOrNull { it.id == fight.guardianId })
        assertEquals(EnemyType.LANCER, guardian.type)
        assertEquals(run.spawnIntegrity(EnemyType.LANCER) * run.content.structures.guardianIntegrityScale, guardian.maxHp, 0.01f)
        assertTrue(dungeon.garrison(2).count { it.id != guardian.id } >= 2, "escorts come with the guardian")
        assertEquals(StructureNotice.GUARDIAN_AWAKENS.key, run.message)
        val entrance = run.takeVisualFxCues().filterIsInstance<VisualFxCue.DungeonMoment>().single { it.kind == DungeonMomentKind.GUARDIAN }
        assertEquals(guardian.x to guardian.y, entrance.focusX to entrance.focusY)
        assertEquals(guardian.id, run.toRenderModel().structures.dungeon?.guardianId)

        // The guardian's fall clears nothing while its escorts stand; their fall finishes the structure.
        run.onEnemyKilled(guardian)
        run.enemies.remove(guardian)
        run.updateStructures(step)
        assertEquals(-1, run.toRenderModel().structures.dungeon?.guardianId, "a fallen guardian is no longer shown")
        assertEquals(2, run.dungeon?.engagedRoom)
        run.killGarrisonForTesting(laterWaves = false)
        run.updateStructures(step)
        assertNull(run.dungeon)
        assertTrue(run.takeVisualFxCues().any { it is VisualFxCue.DungeonMoment && it.kind == DungeonMomentKind.STRUCTURE_CLEARED })
    }

    @Test
    fun aWaveTheCapsRefuseWaitsAtItsBreachPointsUntilItFits() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        run.killGarrisonForTesting(laterWaves = false)
        run.updateStructures(step)
        val planned = assertNotNull(run.dungeon?.fight)
        // Elites outside fill the hard cap: none of them may be evicted for the wave.
        repeat(run.content.rebirth.maxActiveEnemies - run.enemies.size) {
            run.addEnemyForTesting(dungeon.structure.x + 3_000f, dungeon.structure.y + it, type = EnemyType.ELITE)
        }
        run.elapsed = planned.incomingAt
        repeat(3) { run.updateStructures(step) }
        assertEquals(planned, run.dungeon?.fight, "the refused wave stays on its way")
        repeat(3) { run.enemies.removeAt(run.enemies.lastIndex) }
        run.updateStructures(step)
        assertEquals(2, run.dungeon?.fight?.landed)
        assertEquals(3, dungeon.garrison(1).size)
    }

    @Test
    fun roomMembersAreChampionsOnlyFromTheirRebirthTier() {
        fun champions(tier: Int): Int = (44..51).sumOf { seed ->
            val dungeon = DungeonFixture(StructureTemplateId.FOUNDRY, seed, rebirth = tier)
            dungeon.run.elapsed = 200f
            dungeon.enterRoom(0)
            dungeon.garrison(0).count { it.champion }
        }
        assertEquals(0, champions(1))
        assertTrue(champions(10) > 0)
        // A champion member takes the champions' integrity, through the shared spawn.
        val dungeon = DungeonFixture(StructureTemplateId.FOUNDRY, 46, rebirth = 10)
        dungeon.run.elapsed = 200f
        dungeon.enterRoom(0)
        dungeon.garrison(0).forEach { enemy ->
            val scale = if (enemy.champion) dungeon.run.content.variety.champions.integrityScale else 1f
            assertEquals(dungeon.run.spawnIntegrity(enemy.type) * scale, enemy.maxHp, 0.01f)
        }
        assertEquals(0.4f, dungeon.run.roomChampionChance(), 0.0001f)
    }

    @Test
    fun theFightRebasesResetsAndForksWithTheDungeon() {
        val dungeon = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = dungeon.run
        dungeon.enterRoom(1)
        run.killGarrisonForTesting(laterWaves = false)
        run.updateStructures(step)
        val planned = assertNotNull(run.dungeon?.fight)
        val shifted = assertNotNull(run.dungeon).shifted(100f, -50f).fight!!
        assertEquals(planned.incomingPoints.mapIndexed { index, value -> if (index % 2 == 0) value - 100f else value + 50f }, shifted.incomingPoints)

        // A reduction sibling changes only its own fight.
        val sibling = run.deepCopy()
        sibling.elapsed = planned.incomingAt
        sibling.updateStructures(step)
        assertEquals(2, sibling.dungeon?.fight?.landed)
        assertEquals(planned, run.dungeon?.fight)

        // A new run holds no dungeon and so no fight.
        run.startRun()
        assertNull(run.dungeon)
        assertFalse(run.structureEngagementActive())
    }
}
