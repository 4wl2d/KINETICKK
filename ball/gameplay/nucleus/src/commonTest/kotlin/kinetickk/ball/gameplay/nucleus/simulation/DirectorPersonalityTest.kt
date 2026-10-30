// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityDefinition
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.PhaseScales
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Director personalities and onslaught themes (variety.md §3): the stateless roll, every scale a personality applies to
 * the director, the analytic loop budget, the Elemental Storm's aspect, the surge themes and the run-start banner. The
 * empirical budget gate is DirectorPersonalityBudgetGateTest; the multi-minute pacing is SpawnDirectorPacingTest.
 */
class DirectorPersonalityTest {
    private val content = canonicalGameplayContent
    private val director = content.variety.director
    private val plain = content.withoutDirectorVariety()

    /** The plain director (null) and every shipped personality. */
    private val arms: List<DirectorPersonalityId?> = listOf<DirectorPersonalityId?>(null) + DirectorPersonalityId.entries

    @Test
    fun everyPersonalityKeepsTheLoopBudgetWithinFivePercent() {
        val identity = loopRate(null)
        val ratios = DirectorPersonalityId.entries.map { id -> id to loopRate(director.personalities[id.ordinal]) / identity }
        val line = ratios.joinToString { (id, ratio) -> "$id ${rounded(ratio, 4)}" }
        println("DIRECTOR-BUDGET identity ${rounded(identity, 6)} $line")
        ratios.forEach { (id, ratio) -> assertTrue(abs(ratio - 1.0) <= 0.05, "$id loop budget ratio $ratio") }
    }

    @Test
    fun theRollIsAStatelessHashOfTheSeedThatLeavesTheOpeningAndEveryStreamAlone() {
        val counts = IntArray(DirectorPersonalityId.entries.size)
        for (seed in 1..600) {
            val id = assertNotNull(directorPersonalityFor(seed, director))
            assertEquals(id, directorPersonalityFor(seed, director), "Seed $seed rolls the same personality again")
            counts[id.ordinal]++
        }
        counts.forEachIndexed { ordinal, count ->
            assertEquals(1f / counts.size, count / 600f, 0.05f, "${DirectorPersonalityId.entries[ordinal]} share")
        }
        assertNull(directorPersonalityFor(7, director.copy(personalitiesEnabled = false)))
        val heavyTide = director.copy(
            personalities = director.personalities.map { if (it.id == DirectorPersonalityId.TIDE) it.copy(weight = 5f) else it }
                .toImmutableList(),
        )
        assertEquals(0.5f, (1..600).count { directorPersonalityFor(it, heavyTide) == DirectorPersonalityId.TIDE } / 600f, 0.06f)

        for (seed in 1..6) {
            val varied = startedState(seed, content)
            val off = startedState(seed, plain)
            val id = assertNotNull(varied.directorPersonality)
            assertEquals(directorPersonalityFor(seed, director), id)
            assertNull(off.directorPersonality)
            assertEquals(off.enemies.map { "${it.type}@${it.x},${it.y}" }, varied.enemies.map { "${it.type}@${it.x},${it.y}" },
                "Seed $seed opening packs")
            assertEquals(off.spawnRandom.snapshot(), varied.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertEquals(off.gameplayRandom.snapshot(), varied.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(off.rewardRandom.snapshot(), varied.rewardRandom.snapshot(), "Seed $seed reward stream")
            // The roll precedes the director's reset, so the opening calm already takes the personality's length.
            val calm = scale(director.personalities[id.ordinal].phaseDuration, SpawnDirectorPhase.CALM)
            assertEquals(off.directorPhaseUntil * calm, varied.directorPhaseUntil, 1e-4f, "Seed $seed opening calm")
            val model = varied.toRenderModel()
            assertEquals(id, model.directorPersonality)
            assertEquals(varied.featuredAspect, model.featuredAspect)
        }
    }

    @Test
    fun eachPersonalityScalesTheBudgetAccrualAndTheLengthOfEveryPhase() {
        for (id in arms) {
            val definition = definitionOf(id)
            val base = forced(seed = 5, id = null)
            val state = forced(seed = 5, id = id)
            for (phase in SpawnDirectorPhase.entries) {
                for (run in listOf(base, state)) {
                    run.elapsed = 200f
                    run.directorPhase = phase.ordinal
                    run.directorPhaseUntil = 1_000f
                    run.spawnBudget = 0f
                    run.spawnWave(0.5f)
                }
                val expected = base.spawnBudget * scale(definition?.phaseRate, phase) * (definition?.budgetScale ?: 1f)
                assertEquals(expected, state.spawnBudget, 1e-6f, "$id accrual in $phase")
            }

            // Both arms draw the same phase rolls, so each phase's length differs by the personality's scale alone.
            val lengths = forced(seed = 9, id = null) to forced(seed = 9, id = id)
            assertEquals(lengths.first.directorPhaseUntil * scale(definition?.phaseDuration, SpawnDirectorPhase.CALM),
                lengths.second.directorPhaseUntil, 1e-4f, "$id calm")
            repeat(4) {
                for (run in listOf(lengths.first, lengths.second)) {
                    run.elapsed = run.directorPhaseUntil
                    run.spawnWave(STEP)
                }
                val phase = SpawnDirectorPhase.entries[lengths.second.directorPhase]
                assertEquals(lengths.first.directorPhase, lengths.second.directorPhase)
                val baseLength = lengths.first.directorPhaseUntil - lengths.first.elapsed
                val length = lengths.second.directorPhaseUntil - lengths.second.elapsed
                assertEquals(baseLength * scale(definition?.phaseDuration, phase), length, 1e-3f, "$id $phase length")
            }
        }
    }

    @Test
    fun eachPersonalityReweightsTheEventRollOfEveryPhase() {
        for (id in arms) {
            val weights = definitionOf(id)?.eventWeights
            val scales = floatArrayOf(
                weights?.single ?: 1f, weights?.pack ?: 1f, weights?.flank ?: 1f,
                weights?.swarm ?: 1f, weights?.wall ?: 1f, weights?.encircle ?: 1f,
            )
            val state = forced(seed = 17, id = id).apply {
                elapsed = 600f
                wallReadyAt = 0f
                encircleReadyAt = 0f
            }
            assertFalse(state.runMutatorActive(RebirthMutator.PACK_HUNTERS))
            val swarmReady = state.typeWeight(content.enemy(EnemyType.SWARMLING), weakOnly = false, recency = false) > 0f
            assertTrue(swarmReady)
            val seen = mutableSetOf<SpawnEventKind>()
            for (phase in listOf(SpawnDirectorPhase.CALM, SpawnDirectorPhase.BUILD, SpawnDirectorPhase.SURGE)) {
                repeat(150) { round ->
                    state.directorPhase = phase.ordinal
                    val roll = state.spawnRandom.copy().nextFloat()
                    val expected = expectedEventKind(phase, roll, scales)
                    state.planSpawnEvent()
                    val observed = SpawnEventKind.entries[state.pendingEventKind]
                    seen += observed
                    val promotable = expected == SpawnEventKind.SINGLE || expected == SpawnEventKind.PACK
                    if (observed == SpawnEventKind.SWARM && promotable) {
                        // A Swarmling pick turns a single or a pack into a swarm.
                        assertEquals(EnemyType.SWARMLING.ordinal, state.pendingEventType, "$id $phase round $round")
                    } else {
                        assertEquals(expected, observed, "$id $phase round $round roll $roll")
                    }
                }
            }
            assertEquals(SpawnEventKind.entries.toSet() - SpawnEventKind.AMBUSH, seen, "$id covered every rolled kind")
        }
    }

    @Test
    fun eachPersonalityAddsItsPackBonusAndScalesItsCooldowns() {
        for (id in arms) {
            val definition = definitionOf(id)
            val bonus = definition?.packSizeBonus ?: 0
            val packs = forced(seed = 43, id = id).apply { elapsed = 30f }
            var smallest = Int.MAX_VALUE
            var largest = 0
            repeat(800) {
                packs.directorPhase = SpawnDirectorPhase.CALM.ordinal
                packs.planSpawnEvent()
                if (packs.pendingEventKind == SpawnEventKind.PACK.ordinal) {
                    smallest = minOf(smallest, packs.pendingEventSize)
                    largest = maxOf(largest, packs.pendingEventSize)
                }
            }
            assertEquals(3 + bonus..6 + bonus, smallest..largest, "$id pack sizes")

            val walls = forced(seed = 31, id = id).apply { elapsed = 300f }
            walls.plan(SpawnEventKind.WALL, size = 5, type = EnemyType.DRIFTER)
            walls.fireNow()
            assertEquals(SpawnEventKind.WALL.ordinal, walls.lastSpawnEventKind)
            val wallCooldown = WALL_COOLDOWN_SECONDS * (definition?.wallCooldownScale ?: 1f)
            assertEquals(300f + wallCooldown, walls.wallReadyAt, 1e-3f, "$id wall")

            val rings = forced(seed = 31, id = id).apply { elapsed = 300f }
            rings.plan(SpawnEventKind.ENCIRCLE, size = 8, type = null)
            rings.fireNow()
            assertEquals(SpawnEventKind.ENCIRCLE.ordinal, rings.lastSpawnEventKind)
            val encircleCooldown = ENCIRCLE_COOLDOWN_SECONDS * (definition?.encircleCooldownScale ?: 1f)
            assertEquals(300f + encircleCooldown, rings.encircleReadyAt, 1e-3f, "$id encircle")

            val fast = forced(seed = 19, id = id).apply {
                elapsed = 100f
                ambushReadyAt = 0f
                spawnBudget = 0f
                spawnClock = 0f
                setVelocityForTesting(obstacleRicochetSpeed() * AMBUSH_SPEED_FRACTION + 40f, 0f)
            }
            var plannedAt = -1f
            repeat(((AMBUSH_HOLD_SECONDS + 1f) / STEP).toInt()) {
                if (plannedAt >= 0f) return@repeat
                fast.elapsed += STEP
                fast.spawnWave(STEP)
                if (fast.pendingEventKind == SpawnEventKind.AMBUSH.ordinal) plannedAt = fast.elapsed
            }
            assertTrue(plannedAt > 0f, "$id planned an ambush")
            val ambushCooldown = AMBUSH_COOLDOWN_SECONDS * (definition?.ambushCooldownScale ?: 1f)
            assertEquals(plannedAt + ambushCooldown, fast.ambushReadyAt, 1e-3f, "$id ambush")
        }
    }

    @Test
    fun eachPersonalityWeighsItsKindsAndTheStormItsFeaturedAspect() {
        for (id in arms) {
            val definition = definitionOf(id)
            val state = forced(seed = 29, id = id).apply { elapsed = 600f }
            var featured = 0
            for (enemy in content.enemies) {
                val base = state.typeWeight(enemy, weakOnly = false, recency = false, personality = false)
                val bias = definition?.typeBias?.firstOrNull { it.type == enemy.type }?.multiplier ?: 1f
                val aspect = definition?.featuresAspect == true && enemy.type.profile.aspect == state.featuredAspect
                if (aspect && base > 0f) featured++
                val expected = base * bias * if (aspect) definition.aspectWeight else 1f
                assertEquals(expected, state.typeWeight(enemy, weakOnly = false, recency = false), 1e-5f * max(1f, expected),
                    "$id ${enemy.type}")
            }
            if (definition?.featuresAspect == true) assertTrue(featured > 0, "$id features an eligible kind")
        }
    }

    @Test
    fun theElementalStormFeaturesAnAspectThatOrdinaryKindsCarry() {
        val carried = STORM_ASPECTS.map { it.ordinal }
        carried.forEach { aspect ->
            val carriers = content.enemies.filter { !it.isScheduled && it.minimumRebirth == 0 && it.weight > 0f }
            assertTrue(carriers.any { it.type.profile.aspect == aspect }, "An ordinary tier-0 kind carries aspect $aspect")
        }
        var kept = 0
        var replaced = 0
        for (seed in 1..24) {
            val state = startedState(seed, content)
            val keynoteAspect = weakAspectFor(assertNotNull(state.keynoteElement))
            state.forcePersonalityForTesting(DirectorPersonalityId.ELEMENTAL_STORM)
            if (keynoteAspect in carried) {
                assertEquals(keynoteAspect, state.featuredAspect, "Seed $seed keeps the Keynote's aspect")
                kept++
            } else {
                assertTrue(state.featuredAspect in carried, "Seed $seed replaces aspect $keynoteAspect")
                val again = startedState(seed, content)
                again.forcePersonalityForTesting(DirectorPersonalityId.ELEMENTAL_STORM)
                assertEquals(state.featuredAspect, again.featuredAspect, "Seed $seed replaces it the same way")
                replaced++
            }
            for (other in DirectorPersonalityId.entries - DirectorPersonalityId.ELEMENTAL_STORM) {
                state.forcePersonalityForTesting(other)
                assertEquals(keynoteAspect, state.featuredAspect, "Seed $seed $other keeps the Keynote's aspect")
            }
        }
        assertTrue(kept > 0 && replaced > 0, "kept $kept, replaced $replaced")

        val bare = startedState(5, content.withoutKeynote())
        bare.forcePersonalityForTesting(DirectorPersonalityId.ELEMENTAL_STORM)
        assertTrue(bare.featuredAspect in carried, "Without a Keynote the Storm still features an aspect")
        bare.forcePersonalityForTesting(DirectorPersonalityId.TIDE)
        assertEquals(-1, bare.featuredAspect)
    }

    @Test
    fun aSurgeFeaturesOneEligibleOrdinaryKindAtItsThemeWeightUntilItEnds() {
        val state = forced(seed = 61, id = null).apply {
            elapsed = 600f
            directorPhase = SpawnDirectorPhase.BUILD.ordinal
            directorPhaseUntil = 600f
            message = ""
        }
        val spawn = state.spawnRandom.copy()
        state.spawnWave(STEP)
        spawn.nextFloat()
        assertEquals(spawn.snapshot(), state.spawnRandom.snapshot(), "The surge drew its length and nothing else")
        assertEquals(SpawnDirectorPhase.SURGE.ordinal, state.directorPhase)
        assertEquals(1, state.surgeIndex)
        val theme = content.enemies.single { it.type.ordinal == state.surgeThemeType }
        assertFalse(theme.isScheduled)
        assertTrue(state.plainWeight(theme) > 0f)
        assertEquals("ONSLAUGHT // ${theme.name.uppercase()}", state.message)
        assertEquals(ONSLAUGHT_BANNER_SECONDS, state.messageTime)
        for (enemy in content.enemies) {
            val expected = state.plainWeight(enemy) * if (enemy === theme) director.surgeThemeWeight else 1f
            val weight = state.typeWeight(enemy, weakOnly = false, recency = false)
            assertEquals(expected, weight, 1e-5f * max(1f, expected), "${enemy.type}")
        }

        state.elapsed = state.directorPhaseUntil
        state.spawnWave(STEP)
        assertEquals(SpawnDirectorPhase.BREATHER.ordinal, state.directorPhase)
        assertEquals(-1, state.surgeThemeType)
        assertEquals(1, state.surgeIndex)
        assertEquals(state.plainWeight(theme), state.typeWeight(theme, weakOnly = false, recency = false))
    }

    @Test
    fun theArchitectsFightHasNoOnslaughtThemeOrBanner() {
        // A surge of the fight counts itself but features no kind and announces nothing.
        val fight = forced(seed = 61, id = null).apply {
            elapsed = 600f
            bossSpawned = true
            message = ""
        }
        repeat(6) { fight.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE) }
        assertEquals(-1, fight.surgeThemeType)
        assertEquals("", fight.message)
        assertEquals(6, fight.surgeIndex)
        // A theme the surge picked before the Architect arrived weighs nothing once the fight starts.
        val before = forced(seed = 61, id = null).apply { elapsed = 600f }
        before.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE)
        val theme = content.enemies.single { it.type.ordinal == before.surgeThemeType }
        assertTrue(before.typeWeight(theme, weakOnly = false, recency = false) > before.plainWeight(theme))
        before.bossSpawned = true
        assertEquals(before.plainWeight(theme), before.typeWeight(theme, weakOnly = false, recency = false))
        // Outside the fight the same surges are themed and announced.
        val plain = forced(seed = 61, id = null).apply {
            elapsed = 600f
            message = ""
        }
        plain.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE)
        assertTrue(plain.surgeThemeType >= 0)
        assertTrue(plain.message.startsWith("ONSLAUGHT // "))
    }

    @Test
    fun themesAreAStatelessHashOfTheSurgeIndexOverTheEligibleKinds() {
        fun themes(seed: Int, id: DirectorPersonalityId?): List<Int> {
            val state = forced(seed, id).apply { elapsed = 600f }
            val streams = state.spawnRandom.snapshot()
            val picks = List(48) {
                state.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE)
                val theme = state.surgeThemeType
                assertTrue(theme >= 0)
                val definition = content.enemy(EnemyType.entries[theme])
                assertFalse(definition.isScheduled)
                assertTrue(state.typeWeight(definition, weakOnly = false, recency = false) > 0f)
                theme
            }
            assertEquals(48, state.surgeIndex)
            assertEquals(streams, state.spawnRandom.snapshot())
            state.onDirectorPhaseStarted(SpawnDirectorPhase.BREATHER)
            assertEquals(-1, state.surgeThemeType)
            assertEquals(48, state.surgeIndex, "Only surges count")
            return picks
        }
        val first = themes(61, null)
        assertEquals(first, themes(61, null))
        assertNotEquals(first, themes(62, null))
        assertTrue(first.toSet().size >= 4, "Surges feature different kinds: $first")
        // A personality's weights steer the pick: the Siege features its heavy kinds more often than the plain director.
        val heavy = setOf(EnemyType.WARDEN, EnemyType.AEGIS, EnemyType.SPLITTER).map { it.ordinal }
        val plainHeavy = (61..66).sumOf { seed -> themes(seed, null).count { it in heavy } }
        val siegeHeavy = (61..66).sumOf { seed -> themes(seed, DirectorPersonalityId.SIEGE).count { it in heavy } }
        assertTrue(siegeHeavy > plainHeavy, "Siege $siegeHeavy, plain $plainHeavy heavy themes")

        val themesOff = content.copy(variety = content.variety.copy(director = director.copy(surgeThemesEnabled = false)))
        val off = startedState(61, themesOff).apply { elapsed = 600f }
        off.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE)
        assertEquals(-1, off.surgeThemeType)
        assertEquals(1, off.surgeIndex)
    }

    @Test
    fun aSurgeIsThemedOnlyWithItsMinimumOfEligibleKinds() {
        val probe = forced(seed = 61, id = null)
        fun eligibleAt(seconds: Float): Int {
            probe.elapsed = seconds
            return content.enemies.count { probe.typeWeight(it, weakOnly = false, recency = false) > 0f }
        }
        fun themedAt(seconds: Float, minimum: Int): Boolean {
            val state = forced(seed = 61, id = null, content = withThemeMinimum(minimum)).apply {
                elapsed = seconds
                message = ""
            }
            state.onDirectorPhaseStarted(SpawnDirectorPhase.SURGE)
            assertEquals(1, state.surgeIndex)
            assertEquals(state.surgeThemeType >= 0, state.message.isNotEmpty())
            return state.surgeThemeType >= 0
        }
        val minimum = director.surgeThemeMinimumKinds
        var below = 0
        var atOrAbove = 0
        for (second in 0..120 step 4) {
            val eligible = eligibleAt(second.toFloat())
            assertEquals(eligible >= minimum, themedAt(second.toFloat(), minimum), "At $second s with $eligible eligible kinds")
            if (eligible >= minimum) atOrAbove++ else below++
        }
        assertTrue(below > 0 && atOrAbove > 0, "The scan crossed the minimum")
        // The bound itself: as many eligible kinds as the minimum theme a surge, one fewer does not.
        val eligible = eligibleAt(100f)
        assertTrue(eligible in 1..7)
        assertTrue(themedAt(100f, eligible))
        assertFalse(themedAt(100f, eligible + 1))
    }

    @Test
    fun theHostilePatternBannerNamesThePersonalityOnceAtItsTimeBeforeTheKeynote() {
        val at = director.announceAtSeconds
        for (id in DirectorPersonalityId.entries) {
            val state = buildTestState(seed = 31).apply {
                forcePersonalityForTesting(id)
                elapsed = at - 0.05f
                message = ""
            }
            repeat(2) { state.simulateStep(STEP) }
            assertTrue(state.elapsed < at)
            assertEquals("", state.message)
            assertEquals(0, state.varietyAnnouncement)
            while (state.varietyAnnouncement == 0) state.simulateStep(STEP)
            assertTrue(state.elapsed >= at && state.elapsed - STEP < at, "At the first step past $at: ${state.elapsed}")
            assertEquals("HOSTILE PATTERN // ${director.personalities[id.ordinal].name.uppercase()}", state.message)
            assertEquals(HOSTILE_PATTERN_BANNER_SECONDS, state.messageTime)
            assertEquals(HOSTILE_PATTERN_ANNOUNCEMENT, state.varietyAnnouncement)
            // Once per run; the Keynote banner is the next stage.
            state.message = "LATER"
            while (state.elapsed < director.keynoteAnnounceAtSeconds - STEP) state.simulateStep(STEP)
            assertEquals("LATER", state.message)
            while (state.varietyAnnouncement < KEYNOTE_ANNOUNCEMENT) state.simulateStep(STEP)
            assertTrue(state.message.startsWith("KEYNOTE // "), state.message)
        }

        // Without a personality the stage passes silently.
        val quiet = buildTestState(seed = 31, content = plain).apply {
            elapsed = at - 0.01f
            message = ""
        }
        repeat(3) { quiet.simulateStep(STEP) }
        assertEquals(HOSTILE_PATTERN_ANNOUNCEMENT, quiet.varietyAnnouncement)
        assertEquals("", quiet.message)
    }

    /** The loop's budget per second (§3.3): BUILD, SURGE and BREATHER at their mean lengths; the opening calm is left out. */
    private fun loopRate(definition: DirectorPersonalityDefinition?): Double {
        var accrued = 0.0
        var length = 0.0
        for (phase in SpawnDirectorPhase.entries - SpawnDirectorPhase.CALM) {
            val mean = (phase.minimumSeconds + phase.maximumSeconds) / 2.0 * scale(definition?.phaseDuration, phase)
            accrued += phase.rate * scale(definition?.phaseRate, phase) * mean
            length += mean
        }
        return (definition?.budgetScale ?: 1f) * accrued / length
    }

    private fun scale(scales: PhaseScales?, phase: SpawnDirectorPhase): Float = when (phase) {
        SpawnDirectorPhase.CALM -> scales?.calm
        SpawnDirectorPhase.BUILD -> scales?.build
        SpawnDirectorPhase.SURGE -> scales?.surge
        SpawnDirectorPhase.BREATHER -> scales?.breather
    } ?: 1f

    /** The event kind the plain table (§3.2) times [scales] picks for [roll], every kind ready. */
    private fun expectedEventKind(phase: SpawnDirectorPhase, roll: Float, scales: FloatArray): SpawnEventKind {
        val base = when (phase) {
            SpawnDirectorPhase.CALM, SpawnDirectorPhase.BREATHER -> floatArrayOf(70f, 30f, 0f, 0f, 0f, 0f)
            SpawnDirectorPhase.BUILD -> floatArrayOf(35f, 30f, 15f, 10f, 10f, 0f)
            SpawnDirectorPhase.SURGE -> floatArrayOf(0f, 25f, 20f, 20f, 15f, 20f)
        }
        val weights = FloatArray(6) { base[it] * scales[it] }
        var remaining = roll * (weights[0] + weights[1] + weights[2] + weights[3] + weights[4] + weights[5])
        for (kind in 0 until 5) {
            remaining -= weights[kind]
            if (remaining < 0f) return SpawnEventKind.entries[kind]
        }
        return if (weights[5] > 0f) SpawnEventKind.ENCIRCLE else SpawnEventKind.PACK
    }

    private fun definitionOf(id: DirectorPersonalityId?): DirectorPersonalityDefinition? =
        id?.let { director.personalities[it.ordinal] }

    private fun withThemeMinimum(minimum: Int): GameplayContentSnapshot =
        content.copy(variety = content.variety.copy(director = director.copy(surgeThemeMinimumKinds = minimum)))

    /** A kind's weight without the personality and the theme. */
    private fun MutableGameState.plainWeight(definition: EnemyDefinition): Float =
        typeWeight(definition, weakOnly = false, recency = false, personality = false)

    private fun startedState(seed: Int, content: GameplayContentSnapshot): MutableGameState =
        MutableGameState(content, seed = seed, initialMatter = 0).apply {
            resize(1_280f, 720f)
            startRun()
        }

    /** A running arena whose Core cannot die, with [id] forced as the personality; nothing spawns unless a test plans it. */
    private fun forced(
        seed: Int,
        id: DirectorPersonalityId?,
        content: GameplayContentSnapshot = this.content,
    ): MutableGameState = startedState(seed, content).apply {
        enemies.clear()
        invulnerableUntil = Float.MAX_VALUE
        runGrace = Float.MAX_VALUE
        spawnClock = Float.MAX_VALUE
        nextEliteAt = Float.MAX_VALUE
        forcePersonalityForTesting(id)
        takeVisualFxCues()
        takeSoundCues()
    }

    private fun MutableGameState.plan(kind: SpawnEventKind, size: Int, type: EnemyType?) {
        pendingEventKind = kind.ordinal
        pendingEventSize = size
        pendingEventType = type?.ordinal ?: -1
    }

    private fun MutableGameState.fireNow() {
        spawnBudget = MAX_SPAWN_BUDGET
        spawnClock = 0f
        ambushReadyAt = Float.MAX_VALUE
        spawnWave(STEP)
    }

    private fun rounded(value: Double, digits: Int): Double {
        var factor = 1.0
        repeat(digits) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
    }
}
