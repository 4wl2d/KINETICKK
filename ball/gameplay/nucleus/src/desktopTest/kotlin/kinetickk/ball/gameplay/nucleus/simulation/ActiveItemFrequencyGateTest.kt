// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.lifetimeMatterOfferLevel
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.foundation.random.CloneableXorWowRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Active items are few in the item pool and still reach a run (0.5.0 active items, D2 as amended by R1.7). The NoPoi bot,
 * its archetype the character's (as in ItemVarietyTest), plays every one of the twelve characters on the gate harness to the
 * run's end or 900 s, on two arms of [POPULATION] runs each: the first [POPULATION] [PopulationSeeds] dealt to the
 * characters in their order, 25 disjoint seeds each, chosen by that definition and never by outcome. A population gate
 * (policy.md, "Balance gate populations"): it once played 48 seeds per arm (i = 1..48), and during calibration seven
 * disjoint 48-run windows from i = 1,001 read FRESH (c) 0.79–0.94 (then judged over all 48 runs) and (b)'s highest
 * level, level 7 in each, 0.158–0.244, with 3 of the 7 passing, so the seeded gate re-rolled with the reward stream.
 * It is a new gate without a base: its thresholds are the design's.
 * - FRESH: tier 0, no Matrix, no lifetime Matter.
 * - VETERAN: RunLevelCalibrationGateTest's DATA_MAX profile (the full Matrix, its price and at least 3,160 as lifetime
 *   Matter, so catalog level 80 offers every passive item from level 1); active items still unlock by the run level.
 *
 * An item card is a card of an item offer (level and Elite rewards; the slot prompt's cards name held items and are not
 * item cards) on a screen the bot was shown, a rerolled screen included. An unowned-active card offers an active item the
 * run does not own (a first acquisition); a card ranking up an owned one is reported, not bounded.
 * - (a) unowned-active cards are 0.04–0.10 of all item cards, pooled over the arm's runs ("few in the pool");
 * - (b) at every level 2..10, pooled over the arm's runs, they are at most 0.20 of that level's item cards (no early flood);
 * - (c) at least 0.80 of the fresh runs that reach level 10 are shown an active card by then; (c') at least 0.80 of the
 *   veteran runs that reach level 16; a run that ends before that level says nothing about its offers and is not judged
 *   (on the 48 seeds every fresh run left unshown had died at level 3–6 within 98 s);
 * - (d) the median run of each arm holds 1–3 active items at 720 s (a run that ends earlier: what it held at its end).
 * (a) and (b) hold on both arms: the pool must stay thin and the early levels unflooded for a veteran too.
 * [pointOfInterestItemRewardsKeepActivesToAFifthOfTheirCards] samples the directed item rewards of the same runs.
 *
 * Calibration moves the unlock level per active rarity and the lane policy's active factors on other populations
 * (`KK_ACTIVE_FIRST_PROBE` starts the probe window at another i), never by filtering against these seeds.
 */
class ActiveItemFrequencyGateTest {
    @Test
    fun unownedActiveCardsAreFewOverARunAndNeverFloodTheFirstTenLevels() {
        val failures = ArrayList<String>()
        for ((arm, flights) in listOf(Arm.FRESH to fresh, Arm.VETERAN to veteran)) {
            val cards = flights.sumOf { it.cards }
            val unowned = flights.sumOf { it.unownedActiveCards }
            val share = unowned.toDouble() / cards
            val perRun = flights.map { it.unownedActiveCards.toDouble() / it.cards }.sorted()
            println("ACTIVE-FREQ $arm (a) unowned ${"%.3f".format(share)} ($unowned of $cards cards) rank-ups " +
                "${"%.3f".format(flights.sumOf { it.rankUpCards }.toDouble() / cards)} per run q10 ${"%.3f".format(perRun[perRun.size / 10])} " +
                "median ${"%.3f".format(perRun[perRun.size / 2])} q90 ${"%.3f".format(perRun[perRun.size * 9 / 10])}")
            if (share !in MIN_UNOWNED_SHARE..MAX_UNOWNED_SHARE) {
                failures += "$arm (a) unowned-active share ${"%.3f".format(share)} outside [$MIN_UNOWNED_SHARE, $MAX_UNOWNED_SHARE]"
            }
            val levels = EARLY_LEVELS.map { level ->
                val levelCards = flights.sumOf { it.cardsByLevel[level] }
                level to flights.sumOf { it.unownedByLevel[level] }.toDouble() / levelCards.coerceAtLeast(1)
            }
            println("ACTIVE-FREQ $arm (b) " + levels.joinToString { (level, levelShare) -> "L$level ${"%.3f".format(levelShare)}" })
            levels.filter { it.second > MAX_EARLY_LEVEL_SHARE }.forEach { (level, levelShare) ->
                failures += "$arm (b) level $level unowned-active share ${"%.3f".format(levelShare)} > $MAX_EARLY_LEVEL_SHARE"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun freshRunsAreShownAnActiveByLevelTenAndVeteranRunsByLevelSixteen() {
        val failures = ArrayList<String>()
        for ((arm, flights) in listOf(Arm.FRESH to fresh, Arm.VETERAN to veteran)) {
            val reached = flights.filter { it.level >= arm.shownByLevel }
            val shown = reached.filter { flight -> flight.firstActiveCardLevel?.let { it <= arm.shownByLevel } == true }
            val share = shown.size.toDouble() / reached.size.coerceAtLeast(1)
            println("ACTIVE-FREQ $arm (c) shown an active by level ${arm.shownByLevel}: ${shown.size} of the ${reached.size} runs " +
                "that reached it (${"%.3f".format(share)}), ${flights.size - reached.size} ended before; not: " +
                (reached - shown.toSet()).joinToString { it.label() })
            if (reached.isEmpty()) failures += "$arm (c) no run reached level ${arm.shownByLevel}"
            if (share < MIN_SHOWN_SHARE) failures += "$arm (c) ${"%.3f".format(share)} of runs shown an active by level ${arm.shownByLevel} < $MIN_SHOWN_SHARE"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theMedianRunHoldsOneToThreeActivesAtTwelveMinutes() {
        val failures = ArrayList<String>()
        for ((arm, flights) in listOf(Arm.FRESH to fresh, Arm.VETERAN to veteran)) {
            val held = flights.map { it.activesAtTwelveMinutes }.sorted()
            val median = held[held.size / 2]
            println("ACTIVE-FREQ $arm (d) actives held at 720 s: median $median, counts " +
                (0..SkillRuntime.SLOTS).joinToString { count -> "$count:${held.count { it == count }}" } +
                ", ended before 720 s ${flights.count { it.seconds < 720f }}")
            if (median !in MIN_MEDIAN_HELD..MAX_MEDIAN_HELD) failures += "$arm (d) median actives held at 720 s $median outside [$MIN_MEDIAN_HELD, $MAX_MEDIAN_HELD]"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * POI item-and-repair rewards (R1.2, R1.7) stay a uniform shuffle of the focus pool with at most one active card. At
     * every level 5..30 a run of either arm reaches, its running state is forked [POI_SAMPLES] times per focus, each fork
     * with its own reward seed, and the reward is opened and the focus chosen on the fork; the run itself is untouched
     * (its first fresh run flown again without sampling plays the same). Per arm and focus, active cards are at most 0.20
     * of the sampled cards.
     */
    @Test
    fun pointOfInterestItemRewardsKeepActivesToAFifthOfTheirCards() {
        val first = fresh.first()
        val unsampled = fly(first.arm, first.shape, first.seed, sampleRewards = false)
        assertEquals(first.label() + " ${first.cards}", unsampled.label() + " ${unsampled.cards}", "sampling forks leave the run untouched")
        val failures = ArrayList<String>()
        for ((arm, flights) in listOf(Arm.FRESH to fresh, Arm.VETERAN to veteran)) {
            for (focus in PoiFocuses) {
                val cards = flights.sumOf { it.poiCards[focus.ordinal] }
                val actives = flights.sumOf { it.poiActiveCards[focus.ordinal] }
                val share = actives.toDouble() / cards.coerceAtLeast(1)
                println("ACTIVE-POI $arm $focus active ${"%.3f".format(share)} ($actives of $cards cards)")
                if (cards == 0) failures += "$arm $focus sampled no cards"
                if (share > MAX_POI_ACTIVE_SHARE) failures += "$arm $focus active share ${"%.3f".format(share)} > $MAX_POI_ACTIVE_SHARE"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private enum class Arm(
        /** (c): the level by which a run must have been shown an active card. */
        val shownByLevel: Int,
    ) { FRESH(10), VETERAN(16) }

    /** What one run was shown and held; the per-level arrays are indexed by run level. */
    private class Flight(
        val arm: Arm,
        val shape: CoreShape,
        val seed: Int,
        val cards: Int,
        val unownedActiveCards: Int,
        val rankUpCards: Int,
        val cardsByLevel: IntArray,
        val unownedByLevel: IntArray,
        /** The run level of the first screen holding an active card, or null when none was shown. */
        val firstActiveCardLevel: Int?,
        val activesAtTwelveMinutes: Int,
        val seconds: Float,
        val level: Int,
        val poiCards: IntArray,
        val poiActiveCards: IntArray,
    ) {
        fun label(): String = "$shape/$seed(L$level@${seconds.toInt()}s first=${firstActiveCardLevel ?: "-"})"
    }

    private companion object {
        /** Runs per arm: the first [POPULATION] of [PopulationSeeds]. */
        const val POPULATION = 300
        const val MIN_UNOWNED_SHARE = 0.04
        const val MAX_UNOWNED_SHARE = 0.10
        const val MAX_EARLY_LEVEL_SHARE = 0.20
        const val MIN_SHOWN_SHARE = 0.80
        const val MIN_MEDIAN_HELD = 1
        const val MAX_MEDIAN_HELD = 3
        const val MAX_POI_ACTIVE_SHARE = 0.20
        const val POI_SAMPLES = 4
        val EARLY_LEVELS: IntRange = 2..10
        val POI_LEVELS: IntRange = 5..30
        val PoiFocuses: List<RewardFocus> = listOf(RewardFocus.OFFENSE, RewardFocus.DEFENSE, RewardFocus.ECONOMY)

        /**
         * The population's seeds: the first [POPULATION] [PopulationSeeds], unless `KK_ACTIVE_FIRST_PROBE` names another
         * first probe index i of `i * 7_919 + 13` (calibration windows).
         */
        val Seeds: List<Int> = System.getenv("KK_ACTIVE_FIRST_PROBE")?.let { value ->
            val first = requireNotNull(value.trim().toIntOrNull()?.takeIf { it >= 1 }) { "KK_ACTIVE_FIRST_PROBE '$value' is not a probe index" }
            (first until first + POPULATION).map { it * 7_919 + 13 }
        } ?: PopulationSeeds.take(POPULATION)

        val fresh: List<Flight> by lazy { fly(Arm.FRESH) }
        val veteran: List<Flight> by lazy { fly(Arm.VETERAN) }

        fun fly(arm: Arm): List<Flight> = populationOf(Seeds.indices.toList()) { run ->
            fly(arm, CoreShape.entries[run % CoreShape.entries.size], Seeds[run])
        }

        fun profile(arm: Arm, shape: CoreShape): GameplayProfileSnapshot = when (arm) {
            Arm.FRESH -> gateProfile(GateMatrix.NONE, tier = 0, shape = shape)
            Arm.VETERAN -> {
                val lifetime = maxOf(fullMatrixPrice(), VETERAN_LIFETIME_MATTER)
                check(lifetimeMatterOfferLevel(lifetime) == VETERAN_CATALOG_LEVEL) { "a veteran's catalog level is $VETERAN_CATALOG_LEVEL" }
                gateProfile(GateMatrix.FULL, tier = 0, shape = shape, economy = PlayerEconomy(0L, lifetime))
            }
        }

        /** One run of [arm]; [sampleRewards] forks it for the POI reward samples at levels 5..30. */
        fun fly(arm: Arm, shape: CoreShape, seed: Int, sampleRewards: Boolean = true): Flight {
            val content = canonicalGameplayContent
            val levels = content.tempo.maximumLevel + 2
            val cardsByLevel = IntArray(levels)
            val unownedByLevel = IntArray(levels)
            val poiCards = IntArray(RewardFocus.entries.size)
            val poiActiveCards = IntArray(RewardFocus.entries.size)
            var cards = 0
            var unowned = 0
            var rankUps = 0
            var firstActiveCardLevel: Int? = null
            var heldAtTwelveMinutes: Int? = null
            var sampledLevel = 0
            fun held(run: MutableGameState) = (0 until SkillRuntime.SLOTS).count { !run.skillLoadout.isEmpty(it) }
            val state = playGateRun(seed, profile(arm, shape), NoPoiBot(archetypeTag = shape.characterTag()), shown = shown@{ s ->
                if (s.choiceType != ChoiceType.ITEM) return@shown
                val level = s.level.coerceAtMost(levels - 1)
                for (choice in s.choices) {
                    val item = choice.itemId?.let(s.content::item) ?: continue
                    cards++
                    cardsByLevel[level]++
                    if (!item.isActive) continue
                    if (firstActiveCardLevel == null) firstActiveCardLevel = s.level
                    if (s.itemStacks[item.id] == 0) {
                        unowned++
                        unownedByLevel[level]++
                    } else rankUps++
                }
            }) { s ->
                if (heldAtTwelveMinutes == null && s.elapsed >= 720f) heldAtTwelveMinutes = held(s)
                if (sampleRewards && s.phase == GamePhase.RUNNING && s.level in POI_LEVELS && s.level > sampledLevel) {
                    sampledLevel = s.level
                    // Forks only: the run itself never opens the reward, so its stream and outcome are untouched.
                    for (focus in PoiFocuses) repeat(POI_SAMPLES) { sample ->
                        val fork = s.copyForReduction()
                        fork.rewardRandom = CloneableXorWowRandom(seed * 31 + s.level * 1_009 + focus.ordinal * 97 + sample)
                        fork.openDirectedReward(DirectedReward.ITEM_AND_REPAIR)
                        fork.selectRewardFocus(focus)
                        if (fork.choiceType != ChoiceType.ITEM) return@repeat
                        for (choice in fork.choices) {
                            val item = choice.itemId?.let(fork.content::item) ?: continue
                            poiCards[focus.ordinal]++
                            if (item.isActive) poiActiveCards[focus.ordinal]++
                        }
                    }
                }
            }
            val flight = Flight(arm, shape, seed, cards, unowned, rankUps, cardsByLevel, unownedByLevel, firstActiveCardLevel,
                heldAtTwelveMinutes ?: held(state), state.elapsed, state.level, poiCards, poiActiveCards)
            println("ACTIVE-RUN $arm ${flight.label()} ${state.phase} cards=$cards unowned=$unowned rankUps=$rankUps " +
                "held720=${flight.activesAtTwelveMinutes} loadout=${state.skillLoadout} " +
                "poi=${PoiFocuses.joinToString { "${it.name.lowercase()} ${poiActiveCards[it.ordinal]}/${poiCards[it.ordinal]}" }}")
            return flight
        }

        const val VETERAN_LIFETIME_MATTER = 3_160L
        const val VETERAN_CATALOG_LEVEL = 80
    }
}
