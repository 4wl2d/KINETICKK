// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import java.lang.management.ManagementFactory
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Comet, Pylon, Sentry and Crawler steps (seek, mount, aim, lane, hug, flock) allocate nothing per step. */
class EnemyWorldsAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun sixHundredStepsOfTheSpaceAndStructureKindsAllocateNothingBeyondAsManyDrifters() {
        val worlds = arena(worldsKinds = true)
        val drifters = arena(worldsKinds = false)
        val worldsBytes = bytesPerRound(worlds)
        val drifterBytes = bytesPerRound(drifters)
        println("ENEMY-WORLDS-ALLOCATION worlds=$worldsBytes drifters=$drifterBytes steps=$STEPS")
        val modes = worlds.enemies.map { it.mode }.toSet()
        assertTrue(EnemyBehavior.ANCHORED in modes, "mounted kinds are in the measured crowd: $modes")
        assertEquals(drifterBytes, worldsBytes, "$STEPS steps of the new kinds allocated beyond as many Drifters")
    }

    /**
     * Rocks around the Core; Comets cycling their lanes, Pylons seeking and mounting, Sentries turning and aiming
     * (silenced, so no shot is ever allocated) and Crawlers hugging rocks, flocking and lunging, or as many Drifters.
     */
    private fun arena(worldsKinds: Boolean): MutableGameState = buildTestState().apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
        placeObstaclesForTesting(
            testObstacle(coreX + 420f, coreY, radius = 90f),
            testObstacle(coreX - 380f, coreY + 260f, radius = 70f),
            testObstacle(coreX + 60f, coreY - 520f, radius = 110f),
        )
        val kinds = listOf(EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.CRAWLER)
        for (index in 0 until CROWD) {
            val type = if (worldsKinds) kinds[index % kinds.size] else EnemyType.DRIFTER
            val angle = index * 0.61f
            val distance = 160f + (index % 9) * 70f
            val enemy = spawnedEnemy(type, coreX + kotlin.math.cos(angle) * distance, coreY + kotlin.math.sin(angle) * distance,
                ownerTag = if (type == EnemyType.SENTRY && index % 2 == 0) 3L else 0L)
            if (type == EnemyType.PYLON || type == EnemyType.SENTRY) enemy.silencedUntil = Float.MAX_VALUE
            enemies += enemy
        }
    }

    private fun steps(state: MutableGameState) {
        for (step in 0 until STEPS) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateEnemies(MutableGameState.FIXED_STEP)
            // Turrets show their sight lines (a silenced one never fires) and Comets are sent again.
            if (step % RESTART_EVERY == 0) {
                for (index in state.enemies.indices) {
                    val enemy = state.enemies[index]
                    when (enemy.type) {
                        EnemyType.PYLON, EnemyType.SENTRY -> if (enemy.mode == EnemyBehavior.ANCHORED) {
                            enemy.mode = EnemyBehavior.TELEGRAPH
                            enemy.modeUntil = state.elapsed + 0.3f
                        }
                        EnemyType.COMET -> enemy.abilityAt = 0f
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun bytesPerRound(state: MutableGameState): Long {
        repeat(WARMUP_ROUNDS) { steps(state) }
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        repeat(ROUNDS) { steps(state) }
        return (threads.getThreadAllocatedBytes(thread) - before) / ROUNDS
    }

    private companion object {
        const val CROWD = 60
        const val STEPS = 600
        const val RESTART_EVERY = 90
        const val WARMUP_ROUNDS = 10
        const val ROUNDS = 5
    }
}
