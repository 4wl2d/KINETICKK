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
 * V2: the draft deck and the softened lanes matter — V1's mean is ≤ 0.75 × the same seeds' mean with the whole catalog
 * dealt and the 0.4.0 lanes.
 * V3: eight consecutive runs per character, each booted with the history of the ones before (append, keep the newest 64),
 * overlap ≤ 0.22 on average between consecutive runs.
 * V4: across the 48 isolated runs at least 90 % of the rank-0 items unlocking by level 16 appear on some card.
 */
class ItemVarietyTest {
    @Test
    fun runsOfOneCharacterDrawDifferentBuildsAndHistoryPushesThemApart() {
        val isolated = Characters.associateWith { shape -> Seeds.map { seed -> fly(shape, seed, canonicalGameplayContent) } }
        val legacy = contentWith(DraftDeckPolicy.FULL, OfferLanePolicy.RELEASE_040)
        val baseline = Characters.associateWith { shape -> Seeds.map { seed -> fly(shape, seed, legacy) } }
        val chained = Characters.associateWith { shape ->
            var history = emptyList<Int>()
            Seeds.take(CHAIN).map { seed ->
                fly(shape, seed, canonicalGameplayContent, history).also { history = (history + it.order).takeLast(MAX_RECENT_ITEM_IDS) }
            }
        }
        val failures = ArrayList<String>()
        for (shape in Characters) {
            val v1 = pairwise(isolated.getValue(shape))
            val v2 = pairwise(baseline.getValue(shape))
            val v3 = chained.getValue(shape).zipWithNext { a, b -> jaccard(a.items, b.items) }
            val mean1 = v1.average()
            val mean2 = v2.average()
            val mean3 = v3.average()
            println("VARIETY $shape V1 mean ${"%.3f".format(mean1)} max ${"%.3f".format(v1.max())} " +
                "V2 baseline mean ${"%.3f".format(mean2)} ratio ${"%.3f".format(mean1 / mean2)} V3 chained mean ${"%.3f".format(mean3)} " +
                "items/run ${"%.1f".format(isolated.getValue(shape).map { it.items.size }.average())} " +
                "seconds ${isolated.getValue(shape).map { it.seconds.toInt() }}")
            if (mean1 > 0.30) failures += "$shape V1 mean $mean1 > 0.30"
            if (v1.max() > 0.55) failures += "$shape V1 max ${v1.max()} > 0.55"
            if (mean1 > 0.75 * mean2) failures += "$shape V1 mean $mean1 > 0.75 × baseline $mean2"
            if (mean3 > 0.22) failures += "$shape V3 mean $mean3 > 0.22"
        }
        val eligible = canonicalGameplayContent.items.filter { it.minimumRebirth == 0 && it.unlockLevel <= 16 }.map { it.id }.toSet()
        val shown = isolated.values.flatten().flatMap { it.shown }.toSet()
        val coverage = eligible.count { it in shown }.toDouble() / eligible.size
        println("VARIETY V4 shown ${eligible.count { it in shown }} of ${eligible.size} (${"%.3f".format(coverage)})")
        if (coverage < 0.90) failures += "V4 coverage $coverage < 0.90: never shown ${eligible.filter { it !in shown }.map { canonicalGameplayContent.items[it].name }}"
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

    private fun pairwise(flights: List<Flight>): List<Double> =
        flights.indices.flatMap { i -> (i + 1 until flights.size).map { j -> jaccard(flights[i].items, flights[j].items) } }

    private fun contentWith(deck: DraftDeckPolicy, lanes: OfferLanePolicy): GameplayContentSnapshot =
        canonicalGameplayContent.copy(build = canonicalGameplayContent.build.copy(
            policy = canonicalGameplayContent.build.policy.copy(draftDeck = deck, offerLanes = lanes),
        ))

    private companion object {
        val Characters = listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.STAR, CoreShape.HEXAGON)
        val Seeds = (1..12).map { 7_919 * it + 101 }
        const val CHAIN = 8
        const val SECONDS = 600f
    }
}
