// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.simulation.MAX_FIXED_STEPS_PER_FRAME
import kinetickk.ball.gameplay.nucleus.simulation.MAX_SIMULATION_ACCUMULATOR_SECONDS
import kinetickk.ball.gameplay.nucleus.simulation.MAX_SIMULATION_RAW_DELTA_SECONDS
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.acquireRelicForTesting
import kinetickk.ball.gameplay.nucleus.simulation.addDelayedRelicHitForTesting
import kinetickk.ball.gameplay.nucleus.simulation.addEnemyForTesting
import kinetickk.ball.gameplay.nucleus.simulation.consumeFixedStepBudget
import kinetickk.ball.gameplay.nucleus.simulation.delayedRelicHitCountForTesting
import kinetickk.ball.gameplay.nucleus.simulation.nextSimulationAccumulator
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.triggerWeaponContactForTesting
import kinetickk.ball.gameplay.nucleus.simulation.update
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameplayArchitectureBoundsTest {
    @Test
    fun fixedStepWorkAcceptsFortyEightAndDefersFortyNinth() {
        val exact = runningState(seed = 801)
        exact.accumulator = MutableGameState.FIXED_STEP * (MAX_FIXED_STEPS_PER_FRAME + 0.25f)

        exact.consumeFixedStepBudget()

        assertEquals(48, exact.lastTransitionSteps)
        assertTrue(exact.accumulator < MutableGameState.FIXED_STEP)

        val overflow = runningState(seed = 802)
        overflow.accumulator = MutableGameState.FIXED_STEP * (MAX_FIXED_STEPS_PER_FRAME + 1.25f)

        overflow.consumeFixedStepBudget()

        assertEquals(48, overflow.lastTransitionSteps)
        assertTrue(overflow.accumulator >= MutableGameState.FIXED_STEP)
    }

    @Test
    fun simulationDeltaAndAccumulatorClampFirstNPlusOneToExactCaps() {
        val exactDelta = runningState(seed = 804)
        val overflowDelta = runningState(seed = 804)

        exactDelta.update(MAX_SIMULATION_RAW_DELTA_SECONDS)
        overflowDelta.update(
            Float.fromBits(MAX_SIMULATION_RAW_DELTA_SECONDS.toBits() + 1),
        )

        assertEquals(exactDelta.elapsed.toBits(), overflowDelta.elapsed.toBits())
        assertEquals(exactDelta.accumulator.toBits(), overflowDelta.accumulator.toBits())
        assertEquals(exactDelta.lastTransitionSteps, overflowDelta.lastTransitionSteps)

        assertEquals(
            MAX_SIMULATION_ACCUMULATOR_SECONDS,
            nextSimulationAccumulator(
                currentAccumulator = MAX_SIMULATION_ACCUMULATOR_SECONDS,
                rawDeltaSeconds = 0f,
                simulationSpeed = 1f,
            ),
        )
        assertEquals(
            MAX_SIMULATION_ACCUMULATOR_SECONDS,
            nextSimulationAccumulator(
                currentAccumulator = Float.fromBits(
                    MAX_SIMULATION_ACCUMULATOR_SECONDS.toBits() + 1,
                ),
                rawDeltaSeconds = 0f,
                simulationSpeed = 1f,
            ),
        )
    }

    @Test
    fun delayedRelicHitBoundAcceptsNAndRejectsNPlusOneCandidate() {
        val state = runningState(seed = 803)
        state.acquireRelicForTesting(RelicId.ECHO_CHAMBER)
        repeat(MutableGameState.MAX_DELAYED_RELIC_HITS - 1) {
            state.addDelayedRelicHitForTesting()
        }

        state.triggerWeaponContactForTesting(state.addEnemyForTesting(80f, 0f))

        assertEquals(MutableGameState.MAX_DELAYED_RELIC_HITS, state.delayedRelicHitCountForTesting())

        state.triggerWeaponContactForTesting(state.addEnemyForTesting(120f, 0f))

        assertEquals(MutableGameState.MAX_DELAYED_RELIC_HITS, state.delayedRelicHitCountForTesting())
    }

    private fun runningState(seed: Int): MutableGameState =
        MutableGameState(content = canonicalGameplayContent, seed = seed, initialMatter = 0).also { state ->
            state.startRun()
            state.enemies.clear()
        }
}
