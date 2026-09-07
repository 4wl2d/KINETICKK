// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.*

class RunStatisticsTest {
    @Test
    fun damageCountsActualIntegrityAndSeparatesAbsorptionWithoutCountingInvulnerableHits() {
        val state = engine().apply { critChance = 0f }
        val enemy = state.addEnemyForTesting(500f, 500f, hp = 30f)
        state.damageEnemy(enemy, 20f)
        state.damageEnemy(enemy, 100f)
        state.damageEnemy(enemy, 100f)
        assertEquals(30.0, state.toRenderModel().runStatistics.damageDealt)

        state.hp = 10f
        state.shield = 5f
        state.characterRuntime = state.characterRuntime.copy(barrier = 3f)
        state.takeDamage(12f)
        assertEquals(8.0, state.damageAbsorbed)
        assertEquals(4.0, state.damageTaken)
        state.takeDamage(100f)
        assertEquals(4.0, state.damageTaken, "The hurt cooldown must not inflate damage totals")
        state.hurtCooldown = 0f
        state.takeDamage(100f)
        assertEquals(10.0, state.damageTaken)
        assertEquals(GamePhase.GAME_OVER, state.phase)
    }

    @Test
    fun collectionAndBestComboSurviveSpendingLevelUpsAndDrainingProfileOutputs() {
        val state = engine()
        state.pickups += Pickup(PickupType.KEY, state.coreX, state.coreY)
        state.pickups += Pickup(PickupType.REPAIR, state.coreX, state.coreY)
        state.resolvePickupCollection()
        state.keys--
        val levelThreshold = state.nextLevelData
        state.gainData(levelThreshold.toFloat())
        assertEquals(0, state.data)
        assertEquals(levelThreshold.toLong(), state.dataCollected)
        assertEquals(1L, state.keysCollected)
        assertEquals(2L, state.pickupsCollected)
        assertEquals(0, state.keys)

        val elite = state.addEnemyForTesting(500f, 500f, type = EnemyType.ELITE)
        state.onEnemyKilled(elite)
        state.onEnemyKilled(elite)
        state.onEnemyKilled(state.addEnemyForTesting(600f, 600f))
        state.combo = 0
        state.takeProgressUpdate()
        assertEquals(1, state.eliteKills)
        assertEquals(2, state.bestCombo)
        assertEquals(2, state.kills)
    }

    @Test
    fun reductionCandidatesAndRetainedRenderSnapshotsStayIsolatedAndRestartResetsTotals() {
        val accepted = engine()
        val retained = accepted.toRenderModel()
        val candidate = accepted.copyForReduction()
        candidate.damageEnemy(candidate.addEnemyForTesting(500f, 500f, hp = 30f), 100f)
        candidate.pickups += Pickup(PickupType.KEY, candidate.coreX, candidate.coreY)
        candidate.resolvePickupCollection()
        candidate.endRun("CORE FRACTURED")
        val terminal = candidate.toRenderModel()
        repeat(20) { candidate.update(0.1f) }
        assertEquals(terminal.runStatistics, candidate.toRenderModel().runStatistics)
        assertEquals(terminal.elapsed, candidate.elapsed)
        assertEquals(RunStatistics(), retained.runStatistics)
        assertEquals(RunStatistics(), accepted.toRenderModel().runStatistics)
        candidate.startRun()
        assertEquals(RunStatistics(), candidate.toRenderModel().runStatistics)
        assertEquals(30.0, terminal.runStatistics.damageDealt)
        assertEquals(1L, terminal.runStatistics.keysCollected)
    }

    private fun engine() = MutableGameState(canonicalGameplayContent).apply { startRun() }
}
