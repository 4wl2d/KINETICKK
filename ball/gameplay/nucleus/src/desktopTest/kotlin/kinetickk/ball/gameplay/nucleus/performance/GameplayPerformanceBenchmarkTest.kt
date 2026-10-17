// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.performance.validateBenchmarkScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameplayPerformanceBenchmarkTest {
    @Test
    fun releaseWorkloadRetainsAllScenariosAndValidatesDeterministicOutcomes() {
        assertEquals("gameplay-core-v3", GAMEPLAY_BENCHMARK_SUITE_VERSION)
        val first = gameplayBenchmarkScenarios()
        val second = gameplayBenchmarkScenarios()
        assertEquals(21, first.size)
        assertEquals(first.map { it.name }.toSet().size, first.size)
        assertEquals(first.map { it.metadata }, second.map { it.metadata })
        first.forEach { validateBenchmarkScenario(it) }
    }

    @Test
    fun witnessDetectsNewRetainedGameplayFactsAndCopyPreservesThem() {
        val source = MutableGameState(canonicalGameplayContent).apply { startRun() }
        val original = canonicalStateFingerprint(source)
        val changes: List<MutableGameState.() -> Unit> = listOf(
            { coreShape = CoreShape.SHARD },
            { characterRuntime = characterRuntime.copy(charge = 0.5f) },
            { smoothedVelocityX = 10f },
            { turnHoldTime = 0.1f },
            { pendingEliteKills = 1 },
            { pendingDashHits = 1 },
            { pendingCompletedOrbits = 1 },
            { pendingArchitectDefeatedWith = CoreShape.ORB },
            { pendingDirectedRewards = listOf(DirectedReward.RELIC) },
            { pointsOfInterest = listOf(PointOfInterestState(
                0, PointOfInterestKind.RESONANT_CIRCUIT, 10f, 20f, 40f,
            )) },
            { rebornItems[0] = 1 },
            { clauseReadyAt[0] = 1f },
            { clauseCounters[0] = 1 },
            { statusAccumulators[0] = 0.5f },
            { buffs = listOf(ActiveBuff(0, BuffStat.DAMAGE, 0.1f, 1, 2f)) },
            { combatFields = listOf(CombatField(CombatFieldKind.FUSION, 1f, 0f, 0f, 10f, 1f)) },
            { echoHits += EchoHit(1, 0.3f, 2f, false) },
            { severedRelics = listOf(SeveredRelic(RelicId.GLASS_COMET, 30f)) },
            { reactionTokens = 3f },
            { discoveredReactionMask = 1 },
            { stasisBank = 2 },
            { overdriveBurnoutUntil = 4f },
            { offerPity = 2 },
            { rewardRandom.nextInt() },
            { enemies.first().status[StatusCell.STACKS + StatusElement.ION.ordinal] = 2f },
            { enemies.first().characterMarkTime = 1f },
            { enemies.first().lastCharacterDash = 1 },
            { elitesSpawned = 1 },
            { enemies.first().aspect = StatusElement.CRYO.ordinal },
            { enemies.first().aspectImmune = true },
            { enemies.first().phase = 1 },
            { enemies.first().shiftUntil = 3f },
            { enemies.first().escortsPending = 2 },
            { enemies.first().lastDamagedAt = 2f },
            { coreRadius = 20f },
            { enemies.first().disabledUntil = 2f },
            { enemies.first().silencedUntil = 2f },
        )
        changes.forEachIndexed { index, change ->
            val changed = source.copyForReduction().apply(change)
            assertNotEquals(original, canonicalStateFingerprint(changed), "change $index")
            assertEquals(canonicalStateFingerprint(changed), canonicalStateFingerprint(changed.copyForReduction()))
            assertEquals(original, canonicalStateFingerprint(source))
        }
    }
}
