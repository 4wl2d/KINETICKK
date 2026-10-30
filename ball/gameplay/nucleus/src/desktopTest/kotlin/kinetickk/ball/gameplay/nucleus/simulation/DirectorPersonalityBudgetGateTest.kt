// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Director budget neutrality, measured (variety.md §11.2; the analytic ±5 % is DirectorPersonalityTest). The director
 * flies alone at tier 0 on the shipped content ([flyDirector]: a 600 u/s circle, the Core invulnerable, enemies cleared
 * on reaching it and 6 s after they appear): the plain director and each personality forced on the same [Seeds]
 * (`i * 7_919 + 101`, i = 1..60). A personality reshapes the waves but spawns as many ordinary cap units in [60, 300] s
 * as the plain director, within [COUNT_BAND] plus two standard errors, and as much spawn integrity within
 * [INTEGRITY_BAND]; in its first four minutes it mixes at least [MIN_EVENT_KINDS] event kinds on every seed and fires at
 * least [MIN_MEAN_EVENTS] events on average; and the roll gives each personality a sixth of [MARGINAL_SEEDS] seeds
 * within [MARGINAL_TOLERANCE]. No run of the harness ever leaves RUNNING (a choice would hold the director).
 *
 * Clearing an enemy that reaches the Core is the harness's own addition to the spec's flight: without it the Core rams
 * kills, levels up, and its unanswered level choice holds the director (on the calibration window's seed i = 1,612 the
 * plain director spawned 55 cap units against about 210, the run in CHOICE for 20,435 of its 36,007 steps), which made
 * the paired ratios noisy (SE 0.04 to 0.10). Ratios carry paired bootstrap standard errors (20,000 resamples of seeds,
 * RNG seed 20261027). No `budgetScale` moved: on the window (i = 1,601..1,660) every count ratio lay within 0.964..1.000
 * (SE at most 0.0044) and every integrity ratio within 0.932..1.051 (SE at most 0.012), the means fired at least 43.5
 * events, and a budget scale 0.9 or 1.1 moved the count ratio to 0.874 or 1.097, outside the band.
 */
class DirectorPersonalityBudgetGateTest {
    @Test
    fun everyPersonalitySpawnsThePlainDirectorsBudget() {
        val identity = populationOf(Seeds) { flyDirector(it, null) }
        val failures = ArrayList<String>()
        failures += held(null, identity)
        for (id in DirectorPersonalityId.entries) {
            val runs = populationOf(Seeds) { flyDirector(it, id) }
            failures += held(id, runs)
            val pairs = identity.zip(runs)
            val (count, countSe) = bootstrap(pairs, ::countRatio)
            val (integrity, integritySe) = bootstrap(pairs, ::integrityRatio)
            println("DIRECTOR-BUDGET-GATE $id count=${"%.4f".format(count)}(SE ${"%.4f".format(countSe)}) " +
                "integrity=${"%.4f".format(integrity)}(SE ${"%.4f".format(integritySe)}) plainCount=${"%.1f".format(identity.sumOf { it.capUnits } / Seeds.size)}")
            if (abs(count - 1.0) > COUNT_BAND + 2.0 * countSe) {
                failures += "$id spawns ${"%.3f".format(count)} of the plain director's cap units (SE ${"%.3f".format(countSe)})"
            }
            if (integrity !in INTEGRITY_BAND) failures += "$id spawns ${"%.3f".format(integrity)} of the plain director's integrity"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun everyPersonalityStillMixesItsEventsInTheFirstFourMinutes() {
        val failures = ArrayList<String>()
        for (id in listOf<DirectorPersonalityId?>(null) + DirectorPersonalityId.entries) {
            val runs = populationOf(Seeds) { flyDirector(it, id) }
            failures += held(id, runs)
            val mean = runs.sumOf { it.events }.toDouble() / runs.size
            val fewest = runs.minBy { it.eventKinds.size }
            println("DIRECTOR-EVENTS-GATE ${id ?: "PLAIN"} meanEvents=${"%.2f".format(mean)} minEvents=${runs.minOf { it.events }} " +
                "minKinds=${fewest.eventKinds.size}")
            if (mean < MIN_MEAN_EVENTS) failures += "${id ?: "PLAIN"} fires ${"%.2f".format(mean)} events in four minutes on average"
            if (fewest.eventKinds.size < MIN_EVENT_KINDS) failures += "${id ?: "PLAIN"} seed ${fewest.seed} mixes only ${fewest.eventKinds}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun theRollGivesEveryPersonalityASixthOfTheRuns() {
        val director = canonicalGameplayContent.variety.director
        val counts = IntArray(DirectorPersonalityId.entries.size)
        for (seed in varietyMetricSeeds(MARGINAL_SEEDS)) counts[assertNotNull(directorPersonalityFor(seed, director)).ordinal]++
        val shares = counts.map { it.toDouble() / MARGINAL_SEEDS }
        println("DIRECTOR-MARGINALS-GATE " + DirectorPersonalityId.entries.joinToString(" ") { "$it=${"%.4f".format(shares[it.ordinal])}" })
        DirectorPersonalityId.entries.forEach { id ->
            assertTrue(abs(shares[id.ordinal] - 1.0 / counts.size) <= MARGINAL_TOLERANCE, "$id rolls ${shares[id.ordinal]} of the runs")
        }
    }

    private companion object {
        val Seeds: List<Int> = varietyMetricSeeds(60)

        /** Design (§3.3, §17 deviation 5): cap units within ±5 % (plus 2 SE), integrity within ±15 %. */
        const val COUNT_BAND = 0.05
        val INTEGRITY_BAND = 0.85..1.15

        const val MIN_EVENT_KINDS = 3
        const val MIN_MEAN_EVENTS = 40.0

        const val MARGINAL_SEEDS = 6_000
        const val MARGINAL_TOLERANCE = 0.02

        fun countRatio(pairs: List<Pair<DirectorRun, DirectorRun>>): Double = pairs.sumOf { it.second.capUnits } / pairs.sumOf { it.first.capUnits }

        fun integrityRatio(pairs: List<Pair<DirectorRun, DirectorRun>>): Double =
            pairs.sumOf { it.second.integrity } / pairs.sumOf { it.first.integrity }

        /** The harness's own validity: a run that left RUNNING measured a held director. */
        fun held(id: DirectorPersonalityId?, runs: List<DirectorRun>): List<String> =
            runs.filter { it.heldSteps > 0 }.map { "${id ?: "PLAIN"} seed ${it.seed} held the director for ${it.heldSteps} steps" }
    }
}
