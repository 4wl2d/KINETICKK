// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Multi-minute director runs: the variety of the waves and the roster gates over a real run clock. */
class SpawnDirectorPacingTest {
    @Test
    fun theDirectorIsDeterministicPerSeedAndSeedsReshapeTheFirstMinute() {
        fun firstMinute(seed: Int): List<String> {
            val state = runningState(seed, tier = 0)
            val spawned = mutableListOf<String>()
            val seen = mutableSetOf<Int>()
            state.drive(60f) {
                state.enemies.forEach { enemy ->
                    if (seen.add(enemy.id)) spawned += "${enemy.type}@${enemy.x.toInt()},${enemy.y.toInt()}@${state.elapsed.toInt()}"
                }
            }
            return spawned
        }
        val first = firstMinute(seed = 7)
        assertTrue(first.size > 10, "A minute of spawns: $first")
        assertEquals(first, firstMinute(seed = 7))
        assertNotEquals(first, firstMinute(seed = 8))
    }

    @Test
    fun pacingPhasesOpenCalmThenLoopBuildSurgeBreather() {
        val state = runningState(seed = 11, tier = 0)
        assertEquals(SpawnDirectorPhase.CALM.ordinal, state.directorPhase)
        assertTrue(state.directorPhaseUntil in 10f..16f)
        val sequence = mutableListOf(SpawnDirectorPhase.CALM)
        val starts = mutableListOf(0f)
        state.drive(150f) {
            val phase = SpawnDirectorPhase.entries[state.directorPhase]
            if (phase != sequence.last()) {
                sequence += phase
                starts += state.elapsed
            }
        }
        val expected = listOf(SpawnDirectorPhase.CALM, SpawnDirectorPhase.BUILD, SpawnDirectorPhase.SURGE, SpawnDirectorPhase.BREATHER, SpawnDirectorPhase.BUILD)
        assertEquals(expected, sequence.take(5))
        for (index in 0 until 4) {
            val length = starts[index + 1] - starts[index]
            val phase = sequence[index]
            assertTrue(length in phase.minimumSeconds - 0.05f..phase.maximumSeconds + 0.05f, "$phase lasted $length")
        }
    }

    @Test
    fun everyFirstFourMinutesAtTierZeroMixAtLeastThreeEventKindsAtFullStrength() {
        for (seed in listOf(101, 202, 303, 404, 505)) {
            val state = runningState(seed, tier = 0)
            val fullKinds = mutableSetOf<SpawnEventKind>()
            var seen = 0
            state.drive(240f) {
                if (state.spawnEventCount != seen) {
                    seen = state.spawnEventCount
                    val kind = SpawnEventKind.entries[state.lastSpawnEventKind]
                    if (state.lastSpawnEventSpawned >= ceil(state.lastSpawnEventPlanned * MINIMUM_EVENT_SHARE).toInt()) {
                        fullKinds += kind
                    }
                }
            }
            assertTrue(fullKinds.size >= 3, "seed=$seed fired only $fullKinds at 60 % or more in four minutes")
            assertTrue(seen >= 40, "seed=$seed fired only $seen events in four minutes")
        }
    }

    @Test
    fun aTierZeroRunNeverSpawnsRebirthGatedKinds() {
        for (seed in listOf(11, 22, 33)) {
            val state = runningState(seed, tier = 0)
            val firstSeen = mutableMapOf<EnemyType, Float>()
            state.drive(600f) { state.enemies.forEach { firstSeen.getOrPut(it.type) { state.elapsed } } }
            val types = firstSeen.keys
            assertTrue(types.none { it == EnemyType.PHANTOM || it == EnemyType.BROODMOTHER || it == EnemyType.REFLECTOR }, "seed=$seed $types")
            assertTrue(EnemyType.HERALD !in types, "seed=$seed: no Herald below tier 2")
            assertTrue(types.none { it in HABITAT_ONLY_OR_OWNER_SPAWNED }, "seed=$seed: realspace keeps to its roster: $types")
            assertTrue(types.containsAll(listOf(EnemyType.SWARMLING, EnemyType.LANCER, EnemyType.SNIPER)), "seed=$seed $types")
            // The Comet joins at two minutes and the Pylon at four and a half, never before their gates.
            assertTrue(types.containsAll(listOf(EnemyType.COMET, EnemyType.PYLON)), "seed=$seed: Comet and Pylon appear: $types")
            assertTrue(firstSeen.getValue(EnemyType.COMET) >= 120f, "seed=$seed: first Comet at ${firstSeen[EnemyType.COMET]}")
            assertTrue(firstSeen.getValue(EnemyType.PYLON) >= 270f, "seed=$seed: first Pylon at ${firstSeen[EnemyType.PYLON]}")
        }
    }

    @Test
    fun aTierFourRunMeetsTheRebirthKindsAndItsHerald() {
        // The Broodmother (weight 3, from 7:00) is rare in one run's first ten minutes, so three consecutive
        // seeds share the roster check; each run meets its single Herald.
        val types = mutableSetOf<EnemyType>()
        for (seed in 44..46) {
            val state = runningState(seed = seed, tier = 4)
            state.drive(600f) { state.enemies.forEach { types += it.type } }
            assertTrue(state.heraldsSpawned == 1, "seed=$seed: one Herald below tier 6")
        }
        assertTrue(types.containsAll(listOf(EnemyType.PHANTOM, EnemyType.BROODMOTHER, EnemyType.REFLECTOR, EnemyType.HERALD)), "$types")
    }

    private fun runningState(seed: Int, tier: Int): MutableGameState =
        MutableGameState(canonicalGameplayContent, seed = seed, initialMatter = 0, initialRebirthLevel = tier).apply {
            resize(1_280f, 720f)
            startRun()
            invulnerableUntil = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
        }

    /** The Core circles; enemies reaching it are cleared like kills, so the caps keep room for events. */
    private fun MutableGameState.drive(seconds: Float, observe: () -> Unit) {
        val end = elapsed + seconds
        while (elapsed < end) {
            val angle = elapsed * 0.6f
            updatePointer(640f + cos(angle) * 260f, 360f + sin(angle) * 260f)
            simulateStep(MutableGameState.FIXED_STEP)
            observe()
            enemies.removeAll { enemy -> abs(enemy.x - coreX) + abs(enemy.y - coreY) < 320f && !enemy.isMiniBoss }
            pickups.clear()
            quietArenaForTesting()
            takeVisualFxCues()
            takeSoundCues()
        }
    }

    private companion object {
        /** Kinds of the structure and dimension habitats, and the mini and secret bosses their owners spawn. */
        val HABITAT_ONLY_OR_OWNER_SPAWNED = setOf(
            EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.SERAPH, EnemyType.CHOIR, EnemyType.IMP, EnemyType.HOUND,
            EnemyType.CIPHER, EnemyType.MIRROR, EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE,
            EnemyType.PYRE_HERALD, EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT,
        )
    }
}
