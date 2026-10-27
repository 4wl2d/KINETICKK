// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.OfferLanePolicy
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs of one character no longer repeat their builds (items.md §5.4). The no-POI bot (skills and Apex cast, its
 * archetype the character's) plays a fresh rank-0 profile without a Matrix to 600 s or its fall, on the fixed seeds
 * 7,919 × k + 101 (k = 1..12) with an Orb, a Prism, a Star and a Hexagon (Drive, Anchor, Ram, Arsenal). A run's build is
 * the set of distinct items it acquired; two runs overlap by their Jaccard index |A ∩ B| / |A ∪ B|.
 * V1: isolated runs (empty history) overlap on average ≤ 0.30 per character, never more than 0.55.
 * V2: the draft deck and the softened lanes matter — V1's mean is at most a per-character share of the same seeds' mean
 * with the whole catalog dealt and the 0.4.0 lanes, judged over a population ([theDeckAndTheSoftenedLanesCutTheOverlap]).
 * V3: eight consecutive runs per character, each booted with the history of the ones before (append, keep the newest 64),
 * overlap ≤ 0.22 on average between consecutive runs.
 * V4: across the 48 isolated runs at least 90 % of the rank-0 items unlocking by level 16 appear on some card.
 */
class ItemVarietyTest {
    @Test
    fun runsOfOneCharacterDrawDifferentBuildsAndHistoryPushesThemApart() {
        val isolated = Characters.associateWith { shape -> Seeds.map { seed -> fly(shape, seed, canonicalGameplayContent) } }
        val chained = Characters.associateWith { shape ->
            var history = emptyList<Int>()
            Seeds.take(CHAIN).map { seed ->
                fly(shape, seed, canonicalGameplayContent, history).also { history = (history + it.order).takeLast(MAX_RECENT_ITEM_IDS) }
            }
        }
        val failures = ArrayList<String>()
        for (shape in Characters) {
            val v1 = pairwise(isolated.getValue(shape).map { it.items })
            val v3 = chained.getValue(shape).zipWithNext { a, b -> jaccard(a.items, b.items) }
            val mean1 = v1.average()
            val mean3 = v3.average()
            println("VARIETY $shape V1 mean ${"%.3f".format(mean1)} max ${"%.3f".format(v1.max())} " +
                "V3 chained mean ${"%.3f".format(mean3)} " +
                "items/run ${"%.1f".format(isolated.getValue(shape).map { it.items.size }.average())} " +
                "seconds ${isolated.getValue(shape).map { it.seconds.toInt() }}")
            if (mean1 > 0.30) failures += "$shape V1 mean $mean1 > 0.30"
            if (v1.max() > 0.55) failures += "$shape V1 max ${v1.max()} > 0.55"
            if (mean3 > 0.22) failures += "$shape V3 mean $mean3 > 0.22"
        }
        val eligible = canonicalGameplayContent.items.filter { it.minimumRebirth == 0 && it.unlockLevel <= 16 }.map { it.id }.toSet()
        val shown = isolated.values.flatten().flatMap { it.shown }.toSet()
        val coverage = eligible.count { it in shown }.toDouble() / eligible.size
        println("VARIETY V4 shown ${eligible.count { it in shown }} of ${eligible.size} (${"%.3f".format(coverage)})")
        if (coverage < 0.90) failures += "V4 coverage $coverage < 0.90: never shown ${eligible.filter { it !in shown }.map { canonicalGameplayContent.items[it].name }}"
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * V2 over a population (lead Addendum C5, 0.5.0 active items). On the 05885fa9 base the twelve fixed seeds kept the
     * ratio within 0.75 with probability 0.55 (Orb), 0.51 (Prism), 0.50 (Star) and 0.30 (Hexagon), so the gate re-rolled
     * with the reward stream. Over the 300 runs per character on [VarietyPopulationSeeds] (each pair of runs counted, as
     * on the twelve seeds), the ratio of the isolated runs' mean overlap to the whole-catalog, 0.4.0-lane runs' mean
     * overlap on the same seeds is at most [MaxBaselineRatio]: the base ratio plus two jackknife standard errors, rounded
     * up, and never above the 0.5.0 accepted 0.81. Base ratios (SE): Orb 0.735 (0.019), Prism 0.752 (0.017), Star 0.743
     * (0.022), Hexagon 0.807 (0.018); the Hexagon's 0.85 is capped at 0.81.
     */
    @Test
    fun theDeckAndTheSoftenedLanesCutTheOverlap() {
        val legacy = contentWith(DraftDeckPolicy.FULL, OfferLanePolicy.RELEASE_040)
        val failures = ArrayList<String>()
        for (shape in Characters) {
            val isolated = populationOf(VarietyPopulationSeeds) { seed -> fly(shape, seed, canonicalGameplayContent).items }
            val baseline = populationOf(VarietyPopulationSeeds) { seed -> fly(shape, seed, legacy).items }
            val mean1 = pairwise(isolated).average()
            val mean2 = pairwise(baseline).average()
            val ratio = mean1 / mean2
            println("VARIETY $shape population V1 mean ${"%.4f".format(mean1)} V2 baseline mean ${"%.4f".format(mean2)} " +
                "ratio ${"%.3f".format(ratio)} (at most ${MaxBaselineRatio.getValue(shape)}) runs ${isolated.size}")
            if (ratio > MaxBaselineRatio.getValue(shape)) {
                failures += "$shape V1 mean $mean1 > ${MaxBaselineRatio.getValue(shape)} × baseline $mean2 (ratio $ratio)"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** One run's distinct items (acquisition order kept for the history) and every item card it was shown. */
    private class Flight(val order: List<Int>, val shown: Set<Int>, val seconds: Float) {
        val items: Set<Int> = order.toSet()
    }

    private fun fly(shape: CoreShape, seed: Int, content: GameplayContentSnapshot, history: List<Int> = emptyList()): Flight {
        val state = MutableGameState(content, seed = seed, bootstrapProgress = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(coreShape = shape),
            matrix = MatrixProgress(),
            collection = PlayerCollection(discoveredItemIds = emptySet(), recentItemIds = history),
            rebirthProgress = RebirthProgress(level = 0),
        ))
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.startRun()
        val bot = NoPoiBot(archetypeTag = shape.characterTag())
        val shown = HashSet<Int>()
        while (state.elapsed < SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) {
                state.choices.forEach { choice -> choice.itemId?.let(shown::add) }
                bot.choose(state)
            } else {
                bot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        // The acquisition order also records relic binds and skill gains (negative entries); a build is its items.
        return Flight(state.acquisitionOrder.filter { it >= 0 }, shown, state.elapsed)
    }

    private fun jaccard(a: Set<Int>, b: Set<Int>): Double {
        val union = (a + b).size
        return if (union == 0) 0.0 else a.intersect(b).size.toDouble() / union
    }

    private fun pairwise(builds: List<Set<Int>>): List<Double> =
        builds.indices.flatMap { i -> (i + 1 until builds.size).map { j -> jaccard(builds[i], builds[j]) } }

    private fun contentWith(deck: DraftDeckPolicy, lanes: OfferLanePolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = deck, offerLanes = lanes),
        ))

    private companion object {
        val Characters = listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.STAR, CoreShape.HEXAGON)
        val Seeds = (1..12).map { 7_919 * it + 101 }

        /** V2's population: the same seed form for k = 1,001..1,300, disjoint from [Seeds]. */
        val VarietyPopulationSeeds = (1_001..1_300).map { 7_919 * it + 101 }

        /** V2's per-character bound on the population (base + 2 SE rounded up, at most 0.81). */
        val MaxBaselineRatio = mapOf(CoreShape.ORB to 0.78, CoreShape.PRISM to 0.79, CoreShape.STAR to 0.79, CoreShape.HEXAGON to 0.81)
        const val CHAIN = 8
        const val SECONDS = 600f
    }
}
