// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.PhaseScales
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

/**
 * Multi-minute director runs: the variety of the waves and the roster gates over a real run clock. Each test runs the
 * plain director (no personality, no onslaught themes) and then every personality forced on the shipped run variety.
 */
class SpawnDirectorPacingTest {
    @Test
    fun theDirectorIsDeterministicPerSeedAndSeedsReshapeTheFirstMinute() {
        fun firstMinute(seed: Int, arm: Arm): List<String> {
            val state = runningState(seed, tier = 0, arm)
            val spawned = mutableListOf<String>()
            val seen = mutableSetOf<Int>()
            state.drive(60f) {
                state.enemies.forEach { enemy ->
                    if (seen.add(enemy.id)) spawned += "${enemy.type}@${enemy.x.toInt()},${enemy.y.toInt()}@${state.elapsed.toInt()}"
                }
            }
            return spawned
        }
        for (arm in arms) {
            val first = firstMinute(seed = 7, arm)
            assertTrue(first.size > 10, "$arm: a minute of spawns: $first")
            assertEquals(first, firstMinute(seed = 7, arm), "$arm")
            assertNotEquals(first, firstMinute(seed = 8, arm), "$arm")
        }
    }

    @Test
    fun pacingPhasesOpenCalmThenLoopBuildSurgeBreather() {
        for (arm in arms) {
            val state = runningState(seed = 11, tier = 0, arm)
            assertEquals(SpawnDirectorPhase.CALM.ordinal, state.directorPhase)
            val calm = scale(arm.durations, SpawnDirectorPhase.CALM)
            assertTrue(state.directorPhaseUntil in 10f * calm..16f * calm, "$arm calm ends at ${state.directorPhaseUntil}")
            val sequence = mutableListOf(SpawnDirectorPhase.CALM)
            val starts = mutableListOf(0f)
            state.drive(150f) {
                val phase = SpawnDirectorPhase.entries[state.directorPhase]
                if (phase != sequence.last()) {
                    sequence += phase
                    starts += state.elapsed
                }
            }
            val expected = listOf(
                SpawnDirectorPhase.CALM, SpawnDirectorPhase.BUILD, SpawnDirectorPhase.SURGE, SpawnDirectorPhase.BREATHER,
                SpawnDirectorPhase.BUILD,
            )
            assertEquals(expected, sequence.take(5), "$arm")
            for (index in 0 until 4) {
                val length = starts[index + 1] - starts[index]
                val phase = sequence[index]
                val factor = scale(arm.durations, phase)
                val bounds = phase.minimumSeconds * factor - 0.05f..phase.maximumSeconds * factor + 0.05f
                assertTrue(length in bounds, "$arm $phase lasted $length")
            }
        }
    }

    @Test
    fun everyFirstFourMinutesAtTierZeroMixAtLeastThreeEventKindsAtFullStrength() {
        for (arm in arms) {
            for (seed in listOf(101, 202, 303, 404, 505)) {
                val state = runningState(seed, tier = 0, arm)
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
                assertTrue(fullKinds.size >= 3, "$arm seed=$seed fired only $fullKinds at 60 % or more in four minutes")
                assertTrue(seen >= 40, "$arm seed=$seed fired only $seen events in four minutes")
            }
        }
    }

    @Test
    fun aTierZeroRunNeverSpawnsRebirthGatedKinds() {
        for (arm in arms) {
            for (seed in listOf(11, 22, 33)) {
                val state = runningState(seed, tier = 0, arm)
                val firstSeen = mutableMapOf<EnemyType, Float>()
                state.drive(600f) { state.enemies.forEach { firstSeen.getOrPut(it.type) { state.elapsed } } }
                val types = firstSeen.keys
                assertTrue(types.none { it == EnemyType.PHANTOM || it == EnemyType.BROODMOTHER || it == EnemyType.REFLECTOR },
                    "$arm seed=$seed $types")
                assertTrue(EnemyType.HERALD !in types, "$arm seed=$seed: no Herald below tier 2")
                assertTrue(types.none { it in HABITAT_ONLY_OR_OWNER_SPAWNED },
                    "$arm seed=$seed: realspace keeps to its roster: $types")
                val roster = listOf(EnemyType.SWARMLING, EnemyType.LANCER, EnemyType.SNIPER)
                assertTrue(types.containsAll(roster), "$arm seed=$seed $types")
                // The Comet joins at two minutes and the Pylon at four and a half, never before their gates.
                assertTrue(types.containsAll(listOf(EnemyType.COMET, EnemyType.PYLON)),
                    "$arm seed=$seed: Comet and Pylon appear: $types")
                assertTrue(firstSeen.getValue(EnemyType.COMET) >= 120f,
                    "$arm seed=$seed: first Comet at ${firstSeen[EnemyType.COMET]}")
                assertTrue(firstSeen.getValue(EnemyType.PYLON) >= 270f,
                    "$arm seed=$seed: first Pylon at ${firstSeen[EnemyType.PYLON]}")
            }
        }
    }

    @Test
    fun aTierFourRunMeetsTheRebirthKindsAndItsHerald() {
        // The Broodmother (weight 3, from 7:00) is rare in one run's first ten minutes, so three consecutive
        // seeds share the roster check; each run meets its single Herald.
        val types = mutableSetOf<EnemyType>()
        for (seed in 44..46) {
            val state = runningState(seed = seed, tier = 4, arms.first())
            state.drive(600f) { state.enemies.forEach { types += it.type } }
            assertTrue(state.heraldsSpawned == 1, "seed=$seed: one Herald below tier 6")
        }
        val rebirthKinds = listOf(EnemyType.PHANTOM, EnemyType.BROODMOTHER, EnemyType.REFLECTOR, EnemyType.HERALD)
        assertTrue(types.containsAll(rebirthKinds), "$types")

        // Every personality meets its single Herald, and together they meet every Rebirth kind.
        val personalityTypes = mutableSetOf<EnemyType>()
        for (arm in arms.drop(1)) {
            val state = runningState(seed = 44, tier = 4, arm)
            state.drive(600f) { state.enemies.forEach { personalityTypes += it.type } }
            assertTrue(state.heraldsSpawned == 1, "$arm: one Herald below tier 6")
        }
        assertTrue(personalityTypes.containsAll(rebirthKinds), "$personalityTypes")
    }

    /** The plain director ([personality] null, no run variety in the director) or one personality forced. */
    private data class Arm(val personality: DirectorPersonalityId?) {
        val durations: PhaseScales? =
            personality?.let { canonicalGameplayContent.variety.director.personalities[it.ordinal].phaseDuration }

        override fun toString(): String = personality?.name ?: "PLAIN"
    }

    private val arms: List<Arm> = listOf(Arm(null)) + DirectorPersonalityId.entries.map(::Arm)

    private fun scale(scales: PhaseScales?, phase: SpawnDirectorPhase): Float = when (phase) {
        SpawnDirectorPhase.CALM -> scales?.calm
        SpawnDirectorPhase.BUILD -> scales?.build
        SpawnDirectorPhase.SURGE -> scales?.surge
        SpawnDirectorPhase.BREATHER -> scales?.breather
    } ?: 1f

    private fun runningState(seed: Int, tier: Int, arm: Arm): MutableGameState {
        val content = if (arm.personality == null) canonicalGameplayContent.withoutDirectorVariety() else canonicalGameplayContent
        return MutableGameState(content, seed = seed, initialMatter = 0, initialRebirthLevel = tier).apply {
            resize(1_280f, 720f)
            startRun()
            if (arm.personality != null) forcePersonalityForTesting(arm.personality)
            invulnerableUntil = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
        }
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
