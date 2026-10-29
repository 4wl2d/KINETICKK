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
 * flock), Seraph, Choir, Imp, Hound, Cipher and Mirror (weave, sing, fuse, wind-up and dash, glitch, mirror), the
 * mini-bosses (well cycle, turning arcs and their guard, judgement telegraph, attack clocks) and the secret bosses
 * (orbit, Survey marks, rock jumps and their search, turning arcs and their guard, attack clocks).
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

    @Test
    fun sixHundredStepsOfTheMiniBossesAllocateNothingBeyondAsManyDrifters() {
        val bosses = arena(worldsKinds = true, kinds = MINI_BOSS_KINDS)
        val drifters = arena(worldsKinds = false, kinds = MINI_BOSS_KINDS)
        val bossBytes = bytesPerRound(bosses)
        val drifterBytes = bytesPerRound(drifters)
        println("ENEMY-MINI-BOSS-ALLOCATION bosses=$bossBytes drifters=$drifterBytes steps=$STEPS")
        val modes = bosses.enemies.map { it.mode }.toSet()
        assertTrue(EnemyBehavior.PRIMED in modes || EnemyBehavior.TELEGRAPH in modes, "pushing wells or judging Thrones: $modes")
        assertEquals(MINI_BOSS_KINDS.toSet(), bosses.enemies.map { it.type }.toSet(), "every mini-boss stays in the measured crowd")
        assertEquals(drifterBytes, bossBytes, "$STEPS steps of the mini-bosses allocated beyond as many Drifters")
    }

    @Test
    fun sixHundredStepsOfTheSecretBossesAllocateNothingBeyondAsManyDrifters() {
        val bosses = arena(worldsKinds = true, kinds = SECRET_BOSS_KINDS)
        val drifters = arena(worldsKinds = false, kinds = SECRET_BOSS_KINDS)
        val bossBytes = bytesPerRound(bosses)
        val drifterBytes = bytesPerRound(drifters)
        println("ENEMY-SECRET-BOSS-ALLOCATION bosses=$bossBytes drifters=$drifterBytes steps=$STEPS")
        val modes = bosses.enemies.map { it.mode }.toSet()
        assertTrue(EnemyBehavior.TELEGRAPH in modes || EnemyBehavior.PHASED in modes, "marking or jumping Cartographers: $modes")
        assertEquals(SECRET_BOSS_KINDS.toSet(), bosses.enemies.map { it.type }.toSet(), "every secret boss stays in the measured crowd")
        assertEquals(drifterBytes, bossBytes, "$STEPS steps of the secret bosses allocated beyond as many Drifters")
    }

    /**
     * Rocks around the Core; Comets cycling their lanes, Pylons seeking and mounting, Sentries turning and aiming
     * (silenced, so no shot is ever allocated) and Crawlers hugging rocks, flocking and lunging, or as many Drifters.
     * With [DIMENSION_KINDS]: Seraphs weaving, Choirs singing, Imps closing in and Ciphers holding their range, Mirrors
     * chasing the cursor's image (all silenced: no shot, mend, burst or ember is allocated; the steps below send fuses
     * and glitches that a silenced kind lets fizzle), and Hounds flocking, winding up and dashing. With [MINI_BOSS_KINDS]
     * (silenced, gated so no hit lands, the Pyre Herald's shield held off): Warden Prime cycling its well in its second
     * phase, Thrones turning their arcs (and judging in their final phase) and Brood Queens and Pyre Heralds running
     * their clocks. With [SECRET_BOSS_KINDS] (silenced, the Antecedent's shield held off): Cartographers orbiting and
     * marking lanes in their first phases and jumping between the rocks in their final one, and Antecedents in each of
     * their four phases, turning their arcs in the third.
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
            when (type) {
                EnemyType.WARDEN_PRIME -> enemy.phase = 1
                EnemyType.THRONE -> enemy.phase = index % 3
                EnemyType.PYRE_HERALD -> enemy.abilityAt = Float.MAX_VALUE
                EnemyType.CARTOGRAPHER -> enemy.phase = index % 3
                EnemyType.ANTECEDENT -> {
                    enemy.phase = index % 4
                    enemy.charges = if (enemy.phase == 2) ANTECEDENT_ARCS else 0
                    enemy.phaseStartPending = false
                    enemy.abilityAt = Float.MAX_VALUE
                }
                else -> Unit
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
                        // A judgement shows (a silenced Throne passes none).
                        EnemyType.THRONE -> if (enemy.phase == 2 && enemy.mode == EnemyBehavior.IDLE) {
                            enemy.mode = EnemyBehavior.TELEGRAPH
                            enemy.modeUntil = state.elapsed + 0.3f
                        }
                        // Lanes are marked in the first phases and a silenced jump still finds its rock and aims.
                        EnemyType.CARTOGRAPHER -> if (enemy.mode == EnemyBehavior.IDLE) {
                            enemy.mode = if (enemy.phase == 2) EnemyBehavior.PHASED else EnemyBehavior.TELEGRAPH
                            enemy.modeUntil = state.elapsed + 0.3f
                            if (enemy.phase == 2) enemy.shiftUntil = enemy.modeUntil
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * The warm-up outlasts the background JIT: when a stepped method's final tier is installed, the JVM allocates a few
     * hundred bytes of strings on this thread once (not a per-step allocation), and with ten rounds that install could
     * still land inside the measured rounds.
     */
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
        const val WARMUP_ROUNDS = 60
        const val ROUNDS = 5
        val WORLD_KINDS = listOf(EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.CRAWLER)
        val DIMENSION_KINDS = listOf(EnemyType.SERAPH, EnemyType.CHOIR, EnemyType.IMP, EnemyType.HOUND, EnemyType.HOUND,
            EnemyType.CIPHER, EnemyType.MIRROR)
        val MINI_BOSS_KINDS = listOf(EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD)
        val SECRET_BOSS_KINDS = listOf(EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT)
    }
}
