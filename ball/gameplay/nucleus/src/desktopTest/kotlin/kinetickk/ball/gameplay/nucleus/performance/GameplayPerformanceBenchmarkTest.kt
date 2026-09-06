// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.SynergyId
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.SynergyEffect
import kinetickk.ball.gameplay.nucleus.model.SynergyEffectKind
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
            { synergyEffects = listOf(SynergyEffect(SynergyId.RIFT_ECHO, SynergyEffectKind.ECHO, 1f)) },
            { synergyCooldowns[SynergyId.RIFT_ECHO.ordinal] = 1f },
            { ghostDashPending = true },
            { enemies.first().characterMarkTime = 1f },
            { enemies.first().lastCharacterDash = 1 },
        )
        changes.forEachIndexed { index, change ->
            val changed = source.copyForReduction().apply(change)
            assertNotEquals(original, canonicalStateFingerprint(changed), "change $index")
            assertEquals(canonicalStateFingerprint(changed), canonicalStateFingerprint(changed.copyForReduction()))
            assertEquals(original, canonicalStateFingerprint(source))
        }
    }
}
