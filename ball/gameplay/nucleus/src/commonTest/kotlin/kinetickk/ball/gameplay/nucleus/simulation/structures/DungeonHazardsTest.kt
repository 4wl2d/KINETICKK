// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A sealed room's telegraphed hazards: when a cycle starts, where its vents and lances go, the single strike at the
 * end of the telegraph on a Core it meets (and none on a Core clear of it), the final room's alternation and extra
 * hazard, the Rebirth tier's extras, and the hash that places them.
 */
class DungeonHazardsTest {
    private val step = MutableGameState.FIXED_STEP

    @Test
    fun aCycleTelegraphsAVentUnderTheCoreAndStrikesOnceAtTheEndOfItsTelegraph() {
        val vault = DungeonFixture(StructureTemplateId.VAULT)
        val run = vault.run
        val policy = run.content.structures.hazards
        vault.enterRoom(1)
        val sealedAt = assertNotNull(run.dungeon?.fight).sealedAt
        run.elapsed = sealedAt + policy.firstDelaySeconds - step
        run.updateStructures(step)
        assertTrue(assertNotNull(run.dungeon?.fight).hazards.isEmpty(), "nothing before the first delay")

        run.elapsed = sealedAt + policy.firstDelaySeconds
        run.updateStructures(step)
        val fight = assertNotNull(run.dungeon?.fight)
        assertEquals(1, fight.hazardCycles)
        assertEquals(policy.ventsPerCycle, fight.hazards.size)
        assertTrue(fight.hazards.all { it.kind == DungeonHazardKind.VENT && it.radius == policy.ventRadius })
        assertTrue(fight.hazards.all { it.armedAt == run.elapsed && it.strikesAt == run.elapsed + policy.telegraphSeconds })
        assertEquals(run.coreX to run.coreY, fight.hazards[0].ax to fight.hazards[0].ay, "the first vent opens under the Core")
        fight.hazards.forEach { assertEquals(1, vault.roomOf(it.ax, it.ay)) }

        // The telegraph runs out: one hostile blast, then nothing more from this cycle.
        val landed = run.damageTaken + run.damageAbsorbed
        run.elapsed = fight.hazards[0].strikesAt - step
        run.updateStructures(step)
        assertEquals(landed, run.damageTaken + run.damageAbsorbed)
        val expected = run.deepCopy().apply { takeDamage(hostileDamage(null, policy.coreDamage), kind = IncomingDamage.HIT) }
        run.elapsed = fight.hazards[0].strikesAt
        run.updateStructures(step)
        assertTrue(assertNotNull(run.dungeon?.fight).hazardsStruck)
        assertEquals(expected.damageTaken + expected.damageAbsorbed, run.damageTaken + run.damageAbsorbed, 0.001)
        assertTrue(run.damageTaken + run.damageAbsorbed > landed)
        val struck = run.damageTaken + run.damageAbsorbed
        run.hurtCooldown = 0f
        run.elapsed += 0.1f
        run.updateStructures(step)
        assertEquals(struck, run.damageTaken + run.damageAbsorbed, "a hazard strikes once")
    }

    @Test
    fun aCoreClearOfEveryHazardTakesNothing() {
        val vault = DungeonFixture(StructureTemplateId.VAULT)
        val run = vault.run
        vault.enterRoom(1)
        val fight = startCycle(run)
        // Out of every vent's disc, still in the gallery.
        val room = vault.template.rooms[1]
        val spot = listOf(room.minY + 80f, room.maxY - 80f).flatMap { y -> listOf(room.minX + 80f, room.maxX - 80f).map { x -> vault.world(x, y) } }
            .first { (x, y) -> fight.hazards.all { kotlin.math.hypot(x - it.ax, y - it.ay) > it.radius + run.coreRadius + 1f } }
        vault.moveCore(spot)
        val landed = run.damageTaken + run.damageAbsorbed
        run.elapsed = fight.hazards[0].strikesAt
        run.updateStructures(step)
        assertTrue(assertNotNull(run.dungeon?.fight).hazardsStruck)
        assertEquals(landed, run.damageTaken + run.damageAbsorbed)
    }

    @Test
    fun aLanceSpansItsRoomFromWallToWallThroughTheCore() {
        val station = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = station.run
        station.enterRoom(1)
        val fight = startCycle(run)
        val lance = fight.hazards.single()
        assertEquals(DungeonHazardKind.LANCE, lance.kind)
        assertEquals(run.content.structures.hazards.lanceHalfWidth, lance.radius)
        assertTrue(run.hazardMeetsCore(lance))
        // One coordinate is shared, the other runs from wall to wall (inset by the wall's half thickness).
        val room = station.template.rooms[1]
        val wall = station.template.wallHalfThickness
        val vertical = abs(lance.ax - lance.bx) < 0.01f
        val length = if (vertical) abs(lance.ay - lance.by) else abs(lance.ax - lance.bx)
        val span = if (vertical == (station.placement.rotation and 1 == 0)) room.maxY - room.minY else room.maxX - room.minX
        assertEquals(span - 2f * wall, length, 0.01f)
    }

    @Test
    fun theFinalRoomAlternatesItsHazardKindsWithOneMoreAndEachRebirthTierAddsOne() {
        val station = DungeonFixture(StructureTemplateId.DERELICT_STATION)
        val run = station.run
        for (room in listOf(0, 1, 3)) station.clear(room)
        station.enterRoom(2)
        val first = startCycle(run).hazards
        assertEquals(listOf(DungeonHazardKind.LANCE, DungeonHazardKind.LANCE), first.map { it.kind })
        val policy = run.content.structures.hazards
        run.elapsed = first[0].armedAt + policy.periodSeconds
        run.updateStructures(step)
        assertEquals(List(policy.ventsPerCycle + 1) { DungeonHazardKind.VENT }, assertNotNull(run.dungeon?.fight).hazards.map { it.kind })

        // Tier 1: one more in every room, at most the bound.
        val veteran = DungeonFixture(StructureTemplateId.VAULT, rebirth = 1)
        veteran.enterRoom(1)
        assertEquals(policy.ventsPerCycle + 1, startCycle(veteran.run).hazards.size)
        val elder = DungeonFixture(StructureTemplateId.VAULT, rebirth = 10)
        elder.enterRoom(1)
        assertEquals(kinetickk.ball.content.api.ContentBounds.MAX_DUNGEON_HAZARDS, startCycle(elder.run).hazards.size)
    }

    @Test
    fun hazardsFollowTheirHashAndTheCoreNotTheRunStreamsAndStayOffWhenDisabled() {
        val plain = DungeonFixture(StructureTemplateId.VAULT)
        plain.enterRoom(1)
        val drawn = DungeonFixture(StructureTemplateId.VAULT)
        repeat(40) { drawn.run.gameplayRandom.nextInt(); drawn.run.spawnRandom.nextInt(); drawn.run.siteRandom.nextInt() }
        drawn.enterRoom(1)
        assertEquals(startCycle(plain.run).hazards, startCycle(drawn.run).hazards)

        val off = DungeonFixture(StructureTemplateId.VAULT) { it.copy(hazards = it.hazards.copy(enabled = false)) }
        off.enterRoom(1)
        off.run.elapsed = 60f
        off.run.updateStructures(step)
        val fight = assertNotNull(off.run.dungeon?.fight)
        assertTrue(fight.hazards.isEmpty())
        assertFalse(fight.hazardsStruck)
    }

    /** Runs the sealed room's clock to its next hazard cycle and returns the fight with it telegraphing. */
    private fun startCycle(run: MutableGameState) = run.let {
        val fight = assertNotNull(it.dungeon?.fight)
        val policy = it.content.structures.hazards
        it.elapsed = fight.sealedAt + policy.firstDelaySeconds + fight.hazardCycles * policy.periodSeconds
        it.updateStructures(step)
        assertNotNull(it.dungeon?.fight).also { started -> assertEquals(fight.hazardCycles + 1, started.hazardCycles) }
    }
}
