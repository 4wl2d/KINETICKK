// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SpawnDirectorTest {
    @Test
    fun theDirectorIsDeterministicPerSeedAndSeedsReshapeTheFirstMinute() {
        fun firstMinute(seed: Int): List<String> {
            val state = directorState(seed)
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
    fun spawningDrawsOnlyFromItsOwnStream() {
        val state = directorState(seed = 3)
        val gameplay = state.gameplayRandom.snapshot()
        val reward = state.rewardRandom.snapshot()
        val spawn = state.spawnRandom.snapshot()
        repeat(40) { state.planSpawnEvent() }
        state.spawnOpeningPacks()
        assertEquals(gameplay, state.gameplayRandom.snapshot())
        assertEquals(reward, state.rewardRandom.snapshot())
        assertNotEquals(spawn, state.spawnRandom.snapshot())
    }

    @Test
    fun pacingPhasesOpenCalmThenLoopBuildSurgeBreather() {
        val state = directorState(seed = 11)
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
    fun budgetAccruesAtThePhaseRateOverTheTempoInterval() {
        val state = directorState(seed = 5)
        state.elapsed = 200f
        state.directorPhase = SpawnDirectorPhase.SURGE.ordinal
        state.directorPhaseUntil = 1_000f
        state.spawnBudget = 0f
        state.spawnClock = Float.MAX_VALUE
        state.spawnWave(0.5f)
        val expected = 0.5f * SpawnDirectorPhase.SURGE.rate * DIRECTOR_RATE_SCALE *
            state.rebirthProfile.spawnRateMultiplier / state.content.tempo.spawnIntervalSeconds(200f)
        assertEquals(expected, state.spawnBudget, 1e-5f)
        state.spawnBudget = MAX_SPAWN_BUDGET
        state.spawnWave(10f)
        assertEquals(MAX_SPAWN_BUDGET, state.spawnBudget, "The bank is bounded")
    }

    @Test
    fun anEventNeverFiresBelowSixtyPercentOfItsSize() {
        val state = directorState(seed = 13)
        state.elapsed = 30f
        val cap = state.ordinaryEnemyCap().toInt()
        state.fillOrdinaryCap(cap - 3)
        state.plan(SpawnEventKind.PACK, size = 6, type = EnemyType.DRIFTER)
        state.capOverflowReadyAt = Float.MAX_VALUE
        state.fireNow()
        assertEquals(cap - 3, state.enemies.size, "Three of six is below 60 %")
        assertEquals(SpawnEventKind.SINGLE.ordinal, state.pendingEventKind, "A smaller kind replaces it")

        val roomy = directorState(seed = 13)
        roomy.elapsed = 30f
        roomy.fillOrdinaryCap(roomy.ordinaryEnemyCap().toInt() - 4)
        roomy.plan(SpawnEventKind.PACK, size = 6, type = EnemyType.DRIFTER)
        roomy.capOverflowReadyAt = Float.MAX_VALUE
        roomy.fireNow()
        assertEquals(4, roomy.lastSpawnEventSpawned, "Four of six is enough")
        assertTrue(roomy.lastSpawnEventSpawned >= ceil(6 * MINIMUM_EVENT_SHARE).toInt())

        // Once per 20 s an event may exceed the ordinary cap by its own size.
        val overflow = directorState(seed = 13)
        overflow.elapsed = 30f
        val overflowCap = overflow.ordinaryEnemyCap().toInt()
        overflow.fillOrdinaryCap(overflowCap)
        overflow.plan(SpawnEventKind.PACK, size = 5, type = EnemyType.DRIFTER)
        overflow.fireNow()
        assertEquals(overflowCap + 5, overflow.enemies.size)
        assertEquals(30f + CAP_OVERFLOW_COOLDOWN_SECONDS, overflow.capOverflowReadyAt)
        overflow.plan(SpawnEventKind.PACK, size = 5, type = EnemyType.DRIFTER)
        overflow.fireNow()
        assertEquals(overflowCap + 5, overflow.enemies.size, "The overflow is spent")
    }

    @Test
    fun swarmlingPicksBecomeSwarmsAndSwarmlingsCostAThirdOfABudgetSlot() {
        val state = directorState(seed = 17)
        state.elapsed = 600f
        repeat(400) {
            state.directorPhase = SpawnDirectorPhase.BUILD.ordinal
            state.planSpawnEvent()
            val kind = SpawnEventKind.entries[state.pendingEventKind]
            if (kind == SpawnEventKind.SINGLE || kind == SpawnEventKind.PACK) {
                assertNotEquals(EnemyType.SWARMLING.ordinal, state.pendingEventType)
            }
            if (kind == SpawnEventKind.SWARM) {
                assertEquals(EnemyType.SWARMLING.ordinal, state.pendingEventType)
                assertTrue(state.pendingEventSize in 6..10)
            }
        }
        state.plan(SpawnEventKind.SWARM, size = 9, type = EnemyType.SWARMLING)
        state.spawnBudget = 3f
        state.fireNow(budget = 3f)
        assertEquals(9, state.lastSpawnEventSpawned, "Nine Swarmlings cost three")
        assertEquals(0f, state.spawnBudget, 0.05f, "Only the step's own accrual is left")
    }

    @Test
    fun aFastCoreDrawsAnAmbushAheadOncePerTwentySeconds() {
        val state = directorState(seed = 19)
        state.elapsed = 100f
        state.ambushReadyAt = 0f
        state.setVelocityForTesting(800f, 0f)
        state.spawnBudget = 0f
        repeat(((AMBUSH_HOLD_SECONDS + 0.5f) / STEP).toInt()) {
            state.elapsed += STEP
            state.spawnWave(STEP)
        }
        assertEquals(SpawnEventKind.AMBUSH.ordinal, state.pendingEventKind)
        assertTrue(state.pendingEventSize in 3..5)
        assertEquals(state.elapsed, state.ambushReadyAt - AMBUSH_COOLDOWN_SECONDS, 1f)
        state.enemies.clear()
        state.spawnBudget = MAX_SPAWN_BUDGET
        state.spawnClock = 0f
        state.spawnWave(STEP)
        assertEquals(SpawnEventKind.AMBUSH.ordinal, state.lastSpawnEventKind)
        val heading = 0f
        state.enemies.forEach { enemy ->
            val angle = atan2(enemy.y - state.coreY, enemy.x - state.coreX)
            assertTrue(abs(angle - heading) < 0.7f, "An ambush lands ahead: $angle")
        }
    }

    @Test
    fun spawnsLandBeyondTheViewAndFastCoresSeeNoneFromBehind() {
        val state = directorState(seed = 23)
        state.setVelocityForTesting(800f, 0f)
        repeat(2_000) {
            val offset = state.sectorOffset()
            assertTrue(abs(offset) <= 130f * (kotlin.math.PI / 180.0).toFloat() + 1e-4f, "Behind at speed: $offset")
        }
        val slow = directorState(seed = 23)
        val offsets = List(2_000) { slow.sectorOffset() }
        val ahead = offsets.count { abs(it) <= 50f * (kotlin.math.PI / 180.0).toFloat() } / 2_000f
        val behind = offsets.count { abs(it) > 130f * (kotlin.math.PI / 180.0).toFloat() } / 2_000f
        assertEquals(0.35f, ahead, 0.04f)
        assertEquals(0.20f, behind, 0.04f)
    }

    @Test
    fun theOpeningIsTwoOrThreeSmallPacksOfFirstSecondTypes() {
        listOf(1, 2, 3, 4).forEach { seed ->
            val state = MutableGameState(canonicalGameplayContent, seed = seed, initialMatter = 0).apply {
                resize(1_280f, 720f)
                startRun()
            }
            assertEquals(state.rebirthProfile.openingEnemyCount, state.enemies.size)
            assertTrue(state.enemies.all { it.type == EnemyType.DRIFTER }, "Tier 0 opens with Drifters")
            // Members of one pack sit within a pack's spread of each other.
            val groups = mutableListOf<MutableList<Pair<Float, Float>>>()
            state.enemies.forEach { enemy ->
                val group = groups.firstOrNull { group -> group.any { (x, y) -> abs(x - enemy.x) + abs(y - enemy.y) < 160f } }
                if (group != null) group += enemy.x to enemy.y else groups += mutableListOf(enemy.x to enemy.y)
            }
            assertTrue(groups.size in 1..3, "seed=$seed packs=${groups.size}")
        }
    }

    @Test
    fun rebirthGatedKindsNeverSpawnAtTierZero() {
        val state = directorState(seed = 29)
        state.elapsed = 600f
        repeat(300) { state.pickEnemyType() }
        val types = List(1_000) { state.pickEnemyType() }.toSet()
        assertFalse(EnemyType.PHANTOM in types || EnemyType.BROODMOTHER in types || EnemyType.REFLECTOR in types)
        assertFalse(EnemyType.HERALD in types || EnemyType.ELITE in types || EnemyType.ARCHITECT in types)
    }

    @Test
    fun heraldsArriveOnTheirTiersAtSixAndNineMinutes() {
        fun heraldsBy(tier: Int, seconds: Float): Int {
            val state = directorState(seed = 31, tier = tier)
            state.nextEliteAt = Float.MAX_VALUE
            state.elapsed = seconds - 0.5f
            state.drive(1f) {}
            return state.enemies.count { it.type == EnemyType.HERALD }
        }
        assertEquals(0, heraldsBy(tier = 1, seconds = FIRST_HERALD_AT_SECONDS))
        assertEquals(1, heraldsBy(tier = 2, seconds = FIRST_HERALD_AT_SECONDS))
        assertEquals(0, heraldsBy(tier = 2, seconds = FIRST_HERALD_AT_SECONDS - 1f))

        val sixth = directorState(seed = 31, tier = 6)
        sixth.nextEliteAt = Float.MAX_VALUE
        sixth.elapsed = FIRST_HERALD_AT_SECONDS
        sixth.drive(0.2f) {}
        assertEquals("HERALD APPROACHES", sixth.message)
        assertEquals(1, sixth.heraldsSpawned)
        sixth.elapsed = SECOND_HERALD_AT_SECONDS
        sixth.drive(0.2f) {}
        assertEquals(2, sixth.heraldsSpawned)
        sixth.elapsed = 700f
        sixth.drive(0.2f) {}
        assertEquals(2, sixth.heraldsSpawned, "Two at most")
        // The Herald calls its aspect escorts the step after it arrives.
        assertTrue(sixth.enemies.count { it.type != EnemyType.HERALD } >= 2)
    }

    @Test
    fun hardenedElitesCarryMoreIntegrityAndEscortsThatShareTheirAspect() {
        val plain = directorState(seed = 37, tier = 1)
        plain.elapsed = 300f
        val ordinary = plain.spawnedEnemy(EnemyType.ELITE, 500f, 0f)
        assertEquals(0, ordinary.escortsPending)

        val hardened = directorState(seed = 37, tier = 2)
        assertTrue(hardened.rebirthProfile.has(RebirthMutator.HARDENED_ELITES))
        hardened.elapsed = 300f
        val elite = hardened.spawnedEnemy(EnemyType.ELITE, 500f, 0f)
        assertEquals(hardened.spawnIntegrity(EnemyType.ELITE) * HARDENED_ELITE_INTEGRITY, elite.maxHp, 1e-2f)
        assertEquals(HARDENED_ELITE_ESCORTS, elite.escortsPending)
        assignAspect(elite, kinetickk.ball.content.api.StatusElement.THERMAL.ordinal, immune = true)
        hardened.enemies += elite
        hardened.spawnPendingEscorts()
        val escorts = hardened.enemies.filter { it !== elite }
        assertEquals(2, escorts.size)
        escorts.forEach { escort ->
            assertEquals(kinetickk.ball.content.api.StatusElement.THERMAL.ordinal, escort.type.profile.aspect)
            assertFalse(escort.type.profile.aspectImmune)
        }
    }

    @Test
    fun hunterProtocolOpensSniperAndLancerGatesEarlierAndOverclockedSwarmAttacksFaster() {
        val tierTwo = directorState(seed = 41, tier = 2)
        val tierThree = directorState(seed = 41, tier = 3)
        tierTwo.elapsed = 250f - tierTwo.rebirthProfile.threatTimeOffsetSeconds
        tierThree.elapsed = 250f - tierThree.rebirthProfile.threatTimeOffsetSeconds
        val sniper = canonicalGameplayContent.enemy(EnemyType.SNIPER)
        assertEquals(0f, tierTwo.typeWeight(sniper, weakOnly = false, recency = false))
        assertTrue(tierThree.typeWeight(sniper, weakOnly = false, recency = false) > 0f)

        val five = directorState(seed = 41, tier = 5)
        val six = directorState(seed = 41, tier = 6)
        assertEquals(1f, five.hostileAttackRate() / five.content.tempo.attackRateMultiplier(five.threatElapsed), 1e-5f)
        assertEquals(OVERCLOCKED_ATTACK_RATE, six.hostileAttackRate() / six.content.tempo.attackRateMultiplier(six.threatElapsed), 1e-5f)
    }

    @Test
    fun packHuntersEnlargeAndFavorPacks() {
        fun packs(tier: Int): Pair<Int, IntRange> {
            val state = directorState(seed = 43, tier = tier)
            state.elapsed = 30f
            var count = 0
            var smallest = Int.MAX_VALUE
            var largest = 0
            repeat(2_000) {
                state.directorPhase = SpawnDirectorPhase.CALM.ordinal
                state.planSpawnEvent()
                if (state.pendingEventKind == SpawnEventKind.PACK.ordinal) {
                    count++
                    smallest = minOf(smallest, state.pendingEventSize)
                    largest = maxOf(largest, state.pendingEventSize)
                }
            }
            return count to smallest..largest
        }
        val (baseCount, baseSizes) = packs(tier = 0)
        val (hunterCount, hunterSizes) = packs(tier = 1)
        assertEquals(3..6, baseSizes)
        assertEquals(4..7, hunterSizes)
        assertTrue(hunterCount > baseCount * 1.15f, "PACK weight x1.5: $baseCount -> $hunterCount")
    }

    @Test
    fun eliteIntervalsJitterAroundTheirSchedule() {
        val state = directorState(seed = 47)
        val ratios = mutableListOf<Float>()
        state.nextEliteAt = 100f
        repeat(40) {
            val due = state.nextEliteAt
            state.elapsed = due
            state.enemies.clear()
            state.spawnWave(STEP)
            val base = state.rebirthProfile.eliteInterval(state.content.tempo.eliteIntervalSeconds(due))
            ratios += (state.nextEliteAt - due) / base
        }
        assertTrue(ratios.all { it in 0.75f - 1e-4f..1.25f + 1e-4f }, "Within the jitter band: $ratios")
        assertTrue(ratios.minOrNull()!! < 0.85f && ratios.maxOrNull()!! > 1.15f, "Intervals vary: $ratios")
    }

    /** A running tier [tier] arena whose Core cannot die and whose director runs on its own. */
    private fun directorState(seed: Int, tier: Int = 0): MutableGameState =
        MutableGameState(canonicalGameplayContent, seed = seed, initialMatter = 0, initialRebirthLevel = tier).apply {
            resize(1_280f, 720f)
            startRun()
            enemies.clear()
            invulnerableUntil = Float.MAX_VALUE
            runGrace = Float.MAX_VALUE
            takeVisualFxCues()
            takeSoundCues()
        }

    /** Steps the run for [seconds] with the Core circling; enemies that reach it are cleared like kills. */
    private fun MutableGameState.drive(seconds: Float, observe: () -> Unit) {
        val end = elapsed + seconds
        while (elapsed < end) {
            val angle = elapsed * 0.6f
            updatePointer(640f + cos(angle) * 220f, 360f + sin(angle) * 220f)
            simulateStep(STEP)
            observe()
            enemies.removeAll { enemy -> abs(enemy.x - coreX) + abs(enemy.y - coreY) < 260f && !enemy.isMiniBoss }
            pickups.clear()
            pointsOfInterest = emptyList()
            takeVisualFxCues()
            takeSoundCues()
        }
    }

    private fun MutableGameState.fillOrdinaryCap(count: Int) {
        repeat(count) { index -> addEnemyForTesting(2_000f + index * 40f, 900f) }
    }

    private fun MutableGameState.plan(kind: SpawnEventKind, size: Int, type: EnemyType) {
        pendingEventKind = kind.ordinal
        pendingEventSize = size
        pendingEventType = type.ordinal
    }

    private fun MutableGameState.fireNow(budget: Float = MAX_SPAWN_BUDGET) {
        spawnBudget = budget
        spawnClock = 0f
        ambushReadyAt = Float.MAX_VALUE
        spawnWave(STEP)
    }

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
    }
}
