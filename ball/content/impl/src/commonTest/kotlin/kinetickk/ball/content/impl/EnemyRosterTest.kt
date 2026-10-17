// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnemyRosterTest {
    @Test
    fun rosterFollowsTheEnemyTypeOrderAndPublishesOneSnapshotList() {
        val catalog = createContentCatalog()
        val gameplay = catalog.gameplayContent().enemies
        val ui = catalog.uiCatalog().enemies

        assertEquals(EnemyType.entries.toList(), gameplay.map { it.type })
        assertSame(gameplay, ui)
        assertEquals(19, gameplay.size)
        assertTrue(gameplay.size <= ContentBounds.MAX_ENEMY_TYPES)
    }

    @Test
    fun gatesWeightsAndThreatMatchTheRosterTable() {
        val roster = createContentCatalog().gameplayContent().enemies.associateBy { it.type }
        // type to (gate seconds, minimum Rebirth, weight, threat)
        val expected = mapOf(
            EnemyType.DRIFTER to Row(0f, 0, 22f, 1),
            EnemyType.SHOOTER to Row(38f, 0, 17f, 2),
            EnemyType.INTERCEPTOR to Row(90f, 0, 14f, 2),
            EnemyType.CHARGER to Row(150f, 0, 14f, 2),
            EnemyType.WEAVER to Row(150f, 0, 12f, 2),
            EnemyType.SPLITTER to Row(240f, 0, 12f, 3),
            EnemyType.WARDEN to Row(240f, 0, 9f, 3),
            EnemyType.SWARMLING to Row(75f, 0, 10f, 1),
            EnemyType.BOMBER to Row(200f, 0, 8f, 3),
            EnemyType.LANCER to Row(240f, 0, 7f, 3),
            EnemyType.SNIPER to Row(330f, 0, 6f, 3),
            EnemyType.AEGIS to Row(390f, 0, 4f, 3),
            EnemyType.MINELAYER to Row(450f, 0, 5f, 3),
            EnemyType.PHANTOM to Row(300f, 2, 6f, 4),
            EnemyType.BROODMOTHER to Row(420f, 3, 3f, 4),
            EnemyType.REFLECTOR to Row(360f, 4, 5f, 4),
            EnemyType.HERALD to Row(360f, 2, 0f, 5),
        )
        expected.forEach { (type, row) ->
            val definition = roster.getValue(type)
            assertEquals(row, Row(definition.timeGateSeconds, definition.minimumRebirth, definition.weight, definition.threat), type.name)
        }
        assertEquals(listOf(EnemyType.ARCHITECT), roster.values.filter { it.isBoss }.map { it.type })
        assertEquals(listOf(EnemyType.HERALD), roster.values.filter { it.isMiniBoss }.map { it.type })
        assertEquals(listOf(EnemyType.ELITE), roster.values.filter { it.isElite }.map { it.type })
        // The Drifter's share decays to 40 % by six minutes.
        val drifter = roster.getValue(EnemyType.DRIFTER)
        assertEquals(22f, drifter.baseWeight(0f))
        assertEquals(22f * 0.4f, drifter.baseWeight(360f), 1e-4f)
        assertEquals(22f * 0.4f, drifter.baseWeight(600f), 1e-4f)
    }

    @Test
    fun bootstrapRejectsAMisorderedRosterAndASecondBoss() {
        val data = defaultContentBootstrapData()

        assertRejected(data.copy(enemies = data.enemies.reversed()), "EnemyType order")
        assertRejected(data.copy(enemies = data.enemies.dropLast(1)), "EnemyType order")
        val secondBoss = data.enemies.map { if (it.type == EnemyType.HERALD) it.copy(isMiniBoss = false, isBoss = true) else it }
        assertRejected(data.copy(enemies = secondBoss), "only boss")
        val unweighted = data.enemies.map { if (it.type == EnemyType.SNIPER) it.copy(weight = 0f) else it }
        assertRejected(data.copy(enemies = unweighted), "spawn weight")
    }

    @Test
    fun definitionsRejectOutOfRangeThreatAndScheduledWeight() {
        val drifter = defaultContentBootstrapData().enemies.first()
        assertFailsWith<IllegalArgumentException> { drifter.copy(threat = 0) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(threat = 6) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(minimumRebirth = ContentBounds.MAX_REBIRTH_LEVEL + 1) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(isElite = true) }
        assertFailsWith<IllegalArgumentException> { drifter.copy(lateWeightFraction = 1.5f) }
        EnemyDefinition(EnemyType.DRIFTER, "Edge", "Bound check.", threat = 5, timeGateSeconds = 0f, minimumRebirth = 10, weight = 1f)
    }

    private data class Row(val gate: Float, val rebirth: Int, val weight: Float, val threat: Int)

    private fun assertRejected(data: ContentBootstrapData, message: String) {
        val failure = assertFailsWith<IllegalArgumentException> { createContentCatalogForTesting(data) }
        assertTrue(failure.message.orEmpty().contains(message), "Expected '$message' in '${failure.message}'")
    }
}
