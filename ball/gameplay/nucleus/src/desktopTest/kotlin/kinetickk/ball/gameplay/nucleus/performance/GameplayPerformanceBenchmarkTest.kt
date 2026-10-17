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
            { characterRuntime.charge = 0.5f },
            { characterRuntime.ghostPath[0] = 3f },
            { smoothedVelocityX = 10f },
            { turnHoldTime = 0.1f },
            { pendingEliteKills = 1 },
            { pendingDashHits = 1 },
            { pendingCompletedOrbits = 1 },
            { pendingArchitectDefeatedWith = CoreShape.ORB },
            { pendingTotalKills = 1 },
            { pendingRicochets = 1 },
            { pendingDataPickups = 1 },
            { achievementFlushDue = true },
            { itemOffersOpened = 1 },
            { secondWindSpent = true },
            { matrixFreeDashReadyAt = 2f },
            { matrixRanks[0] = 1 },
            { matrix = matrix.copy(eliteBounty = true) },
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
            // 0.4.0 wave A: the spawn director, the new enemy behaviors, Null Covenant and frame pacing.
            { spawnRandom.nextInt() },
            { heraldsSpawned = 1 },
            { directorPhase = 2 },
            { spawnBudget = 3f },
            { pendingEventKind = 1 },
            { recentEventTypes = 4 },
            { ambushReadyAt = 5f },
            { capOverflowReadyAt = 5f },
            { spawnEventCount = 3 },
            { lastSpawnEventKind = 2 },
            { enemies.first().mode = 2 },
            { enemies.first().facing = 1f },
            { enemies.first().linkedBy = 7 },
            { enemies.first().shieldUntil = 3f },
            { enemies.first().abilityAt = 3f },
            { integrityLiabilityCut = 9f },
            { accumulator = 0.004f },
            { previousCameraX = 5f },
            { previousMorningstarX = 5f },
            { viewportDensity = 2f },
            { interpolationPrimed = !interpolationPrimed },
        )
        changes.forEachIndexed { index, change ->
            val changed = source.copyForReduction().apply(change)
            assertNotEquals(original, canonicalStateFingerprint(changed), "change $index")
            assertEquals(canonicalStateFingerprint(changed), canonicalStateFingerprint(changed.copyForReduction()))
            assertEquals(original, canonicalStateFingerprint(source))
        }
    }
}
