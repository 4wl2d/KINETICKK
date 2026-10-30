// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.BENEFIT_RUNS
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.FORKS
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.OFFENSIVE
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.benefitPairs
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.median
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the offence band. [ApexArchitectFightHarness] plays
 * the fights; [ApexArchitectPrismDefenceTest] and [ApexArchitectDiamondDefenceTest] hold the defensive bands,
 * [ApexArchitectFightTest] the contract and [ApexArchitectCastTest] the cast rule. The offence and both defensive bands
 * were one test; they are three, with the same populations, forks, bands and messages, because together they play
 * about 3,000 CPU-seconds of fights, more than one 45-minute job of the 4-vCPU CI runner holds.
 *
 * The benefit is judged on many paired fights, because one fight's time-to-kill moves by ±15 % when the Core starts
 * 0.01 u elsewhere: each run is forked into [FORKS] pairs whose two arms both start with the Core moved by k/100 u
 * (k = 0 is the unmoved fight). Offensive characters (tier 0, the first [BENEFIT_RUNS] runs each along
 * [PopulationSeeds]): per character the median over its pairs of the with/without time-to-kill ratio; the median of the
 * ten lies in [0.75, 0.95] and each character's benefit within ±40 % of that median benefit.
 *
 * The offence band is a population rule (0.5.0 active items; policy.md, "Balance gate populations"). On the fixed seed
 * list (48 runs per character) it passed with probability 0.97 on the 05885fa9 base but 0.83 with the active items,
 * through its ±40 % clause (the median band passed every time, and the clause is measured against it). Its population
 * is the one the base was measured on, 96 runs per character with four forks each: fewer than 300 runs, since these
 * fights already set the suite's runtime. On the 0.6.0 base the offence median is 0.850 (bootstrap SE 0.005,
 * resampling whole runs with their forks) and each character's benefit 0.87 (Ring) to 1.39 (Crescent) of the median
 * benefit (SE 0.05-0.11). By the population threshold rule (the wider of the design's band and the base ± two standard
 * errors, rounded outward) the median band stays the design's. The ±40 % clause is the design's character band and
 * never weakens, although Crescent's and Shard's base + 2 SE reach 1.56 of the median benefit; Crescent passes by
 * 0.13 SE.
 */
class ApexArchitectOffenceTest {
    @Test
    fun theApexShortensOffensiveFightsWithinItsBand() {
        val outside = mutableListOf<String>()
        // The characters look for their runs at once, so the fight pool never waits on one character's slowest batch.
        val pairs = OFFENSIVE.zip(populationOf(OFFENSIVE) { shape -> benefitPairs(shape, 0) }).toMap()
        val ratios = pairs.mapValues { (_, list) -> median(list.map { it.apexKill / it.plainKill }) }
        val median = median(ratios.values.toList())
        val benefit = 1f - median
        println("APEX-BENEFIT tier=0 pairs=${pairs.values.sumOf { it.size }} median=${"%.3f".format(median)} " +
            ratios.entries.joinToString { (shape, ratio) ->
                val list = pairs.getValue(shape)
                "$shape=${"%.3f".format(ratio)} (medians ${"%.3f".format(median(list.map { it.apexKill }) / median(list.map { it.plainKill }))})"
            })
        if (median !in 0.75f..0.95f) outside += "median time-to-kill ratio $median"
        ratios.forEach { (shape, ratio) ->
            val own = 1f - ratio
            if (own !in min(benefit * 0.6f, benefit * 1.4f)..max(benefit * 0.6f, benefit * 1.4f)) {
                outside += "$shape benefit $own (median $benefit)"
            }
        }
        assertTrue(outside.isEmpty(), "Apex benefit: $outside")
    }
}
