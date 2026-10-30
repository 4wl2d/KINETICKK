// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldLayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a dimension visit does to a whole run (spec §12.3), flown by [DimensionPilot] to 900 s. A fresh tier-0 run sent
 * through a Zenith (or Nadir) rift at 165 s, playing the objective its visit rolls and taking the first card of its
 * reward, wins neither much less nor much more often than the same seed with no rift: a dimension is neither a trap nor
 * a must. A strong tier-10 build that visits a dimension at 285 s still clears tier 10 in at most three of the five
 * [EconomyGateSeeds] (the spirit of gate D). The first gate plays the first 120 [PopulationSeeds] (i = 1,001..1,120).
 *
 * Measured on the 0.6.0 lane tree: on the window i = 2,401..2,500 the Zenith moves the win share by +0.02 and the Nadir
 * by +0.06 (control 0.78); on the gate seeds by +0.025 (paired SE 0.038) and -0.008 (0.046) against the control's 88 of
 * 120. The strong build won none of its five seeds with either visit.
 */
class DimensionRunOutcomeGateTest {
    /**
     * The win share with a Zenith visit, and with a Nadir visit, minus the control's win share on the same seeds lies in
     * [[MIN_WIN_SHIFT], [MAX_WIN_SHIFT]]; every run alive when its rift opened flew through it.
     */
    @Test fun aWindowDimensionIsNeitherATrapNorAMust() {
        val control = populationOf(GateSeeds) { seed ->
            flyDimensionRun(seed, DimensionProfile.FRESH_TIER_0, null, FRESH_RIFT_AT, runToEnd = true)
        }
        for (target in listOf(WorldLayer.ZENITH, WorldLayer.NADIR)) {
            val visited = populationOf(GateSeeds) { seed ->
                flyDimensionRun(seed, DimensionProfile.FRESH_TIER_0, target, FRESH_RIFT_AT, runToEnd = true)
            }
            val unentered = visited.filter { !it.entered && !it.openedAt.isNaN() }
            assertTrue(unentered.isEmpty(), "$target: rifts left unentered on ${unentered.map { it.seed }}")
            val (shift, se) = pairedShift(control, visited)
            println("DIMENSION OUTCOME $target wins ${visited.count { it.won }} of ${visited.size} " +
                "(entered ${visited.count { it.entered }}, completed ${visited.count { it.completed }}) against the control's " +
                "${control.count { it.won }}: shift ${"%.4f".format(shift)}(SE ${"%.4f".format(se)})")
            assertTrue(shift in MIN_WIN_SHIFT..MAX_WIN_SHIFT,
                "$target visit moves the fresh win share by ${"%.3f".format(shift)}, outside [$MIN_WIN_SHIFT, $MAX_WIN_SHIFT]")
        }
    }

    /** A strong tier-10 build with a Zenith visit, and with a Nadir visit, at 285 s clears at most three of five seeds. */
    @Test fun aStrongBuildWithAVisitStillRarelyClearsTierTen() {
        for (target in listOf(WorldLayer.ZENITH, WorldLayer.NADIR)) {
            val runs = EconomyGateSeeds.map { seed ->
                flyDimensionRun(seed, DimensionProfile.STRONG_TIER_10, target, STRONG_RIFT_AT, runToEnd = true)
            }
            println("DIMENSION OUTCOME strong-t10 $target ${runs.joinToString(" ") { "${it.seed}:${it.phase}@${it.seconds.toInt()}s(entered=${it.entered})" }}")
            assertTrue(runs.count { it.won } <= MAX_STRONG_WINS, "tier 10 must stay an achievement with a $target visit: $runs")
        }
    }

    private companion object {
        val GateSeeds: List<Int> = PopulationSeeds.take(120)

        /** Spec §12.3: a visit moves the fresh tier-0 win share by at least this much and at most the other. */
        const val MIN_WIN_SHIFT = -0.06
        const val MAX_WIN_SHIFT = 0.15

        /** The strong arms' rift time (the pilot gate's Z10 and N10) and gate D's win bound on its five seeds. */
        const val STRONG_RIFT_AT = 285f
        const val MAX_STRONG_WINS = 3

        /** The visit arm's win share minus the control's over the same seeds, with its paired bootstrap SE. */
        fun pairedShift(control: List<DimensionVisitRun>, visited: List<DimensionVisitRun>): Pair<Double, Double> {
            val pairs = control.zip(visited) { a, b -> (if (b.won) 1.0 else 0.0) - (if (a.won) 1.0 else 0.0) }
            val random = Random(DIMENSION_BOOTSTRAP_SEED)
            val values = DoubleArray(DIMENSION_BOOTSTRAP_RESAMPLES) { List(pairs.size) { pairs[random.nextInt(pairs.size)] }.average() }
            val mean = values.average()
            return pairs.average() to kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
        }
    }
}
