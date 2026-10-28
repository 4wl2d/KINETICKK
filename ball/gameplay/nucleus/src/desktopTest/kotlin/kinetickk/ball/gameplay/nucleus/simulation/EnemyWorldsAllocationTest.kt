// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import java.lang.management.ManagementFactory
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 0.6.0 kinds' steps allocate nothing per step: Comet, Pylon, Sentry and Crawler (seek, mount, aim, lane, hug,
 * flock) and Seraph, Choir, Imp, Hound, Cipher and Mirror (weave, sing, fuse, wind-up and dash, glitch, mirror).
 */
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

    @Test
    fun sixHundredStepsOfTheDimensionKindsAllocateNothingBeyondAsManyDrifters() {
        val dimensions = arena(worldsKinds = true, kinds = DIMENSION_KINDS)
        val drifters = arena(worldsKinds = false, kinds = DIMENSION_KINDS)
        val dimensionBytes = bytesPerRound(dimensions)
        val drifterBytes = bytesPerRound(drifters)
        println("ENEMY-DIMENSION-ALLOCATION dimensions=$dimensionBytes drifters=$drifterBytes steps=$STEPS")
        val types = dimensions.enemies.map { it.type }.toSet()
        assertEquals(DIMENSION_KINDS.toSet(), types, "every dimension kind stays in the measured crowd")
        assertEquals(drifterBytes, dimensionBytes, "$STEPS steps of the dimension kinds allocated beyond as many Drifters")
    }

    /**
     * Rocks around the Core; Comets cycling their lanes, Pylons seeking and mounting, Sentries turning and aiming
     * (silenced, so no shot is ever allocated) and Crawlers hugging rocks, flocking and lunging, or as many Drifters.
     * With [DIMENSION_KINDS]: Seraphs weaving, Choirs singing, Imps closing in and Ciphers holding their range, Mirrors
     * chasing the cursor's image (all silenced: no shot, mend, burst or ember is allocated; the steps below send fuses
     * and glitches that a silenced kind lets fizzle), and Hounds flocking, winding up and dashing.
     */
    private fun arena(worldsKinds: Boolean, kinds: List<EnemyType> = WORLD_KINDS): MutableGameState = buildTestState().apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
        placeObstaclesForTesting(
            testObstacle(coreX + 420f, coreY, radius = 90f),
            testObstacle(coreX - 380f, coreY + 260f, radius = 70f),
            testObstacle(coreX + 60f, coreY - 520f, radius = 110f),
        )
        // The cursor off the Core, so Mirrors chase a mirror point away from it.
        if (kinds == DIMENSION_KINDS) updatePointer(820f, 240f)
        for (index in 0 until CROWD) {
            val type = if (worldsKinds) kinds[index % kinds.size] else EnemyType.DRIFTER
            val angle = index * 0.61f
            val distance = 160f + (index % 9) * 70f
            val enemy = spawnedEnemy(type, coreX + kotlin.math.cos(angle) * distance, coreY + kotlin.math.sin(angle) * distance,
                ownerTag = if (type == EnemyType.SENTRY && index % 2 == 0) 3L else 0L)
            if (type != EnemyType.COMET && type != EnemyType.CRAWLER && type != EnemyType.HOUND && type != EnemyType.DRIFTER) {
                enemy.silencedUntil = Float.MAX_VALUE
            }
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
                        // A silenced fuse fizzles and a glitch still reappears and aims; neither fires.
                        EnemyType.IMP -> if (enemy.mode == EnemyBehavior.IDLE) {
                            enemy.mode = EnemyBehavior.PRIMED
                            enemy.modeUntil = state.elapsed + 0.3f
                        }
                        EnemyType.CIPHER -> if (enemy.mode == EnemyBehavior.IDLE) {
                            enemy.mode = EnemyBehavior.PHASED
                            enemy.modeUntil = state.elapsed + 0.3f
                            enemy.shiftUntil = enemy.modeUntil
                        }
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
        val WORLD_KINDS = listOf(EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.CRAWLER)
        val DIMENSION_KINDS = listOf(EnemyType.SERAPH, EnemyType.CHOIR, EnemyType.IMP, EnemyType.HOUND, EnemyType.HOUND,
            EnemyType.CIPHER, EnemyType.MIRROR)
    }
}
