// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.BENEFIT_RUNS
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.DEFENSIVE
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.DEFENSIVE_RUNS
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.DEFENSIVE_TIER
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.FORKS
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.OFFENSIVE
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.benefitPairs
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.median
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the benefit bands. The fights are
 * [ApexArchitectFightHarness]'s; the contract is [ApexArchitectFightTest]'s and the cast rule
 * [ApexArchitectCastTest]'s.
 *
 * The benefit is judged on many paired fights, because one fight's time-to-kill moves by ±15 % when the Core starts
 * 0.01 u elsewhere: each run is forked into [FORKS] pairs whose two arms both start with the Core moved by k/100 u
 * (k = 0 is the unmoved fight). Offensive characters (tier 0, the first [BENEFIT_RUNS] runs each along
 * [PopulationSeeds]): per character the median over its pairs of the with/without time-to-kill ratio; the median of the
 * ten lies in [0.75, 0.95] and each character's benefit within ±40 % of that median benefit. The Prism and the Diamond
 * (tier 10, where the Architect's fire and the arena put real damage on them; at tier 0 their facets and parries leave
 * almost nothing to defend; the first [DEFENSIVE_RUNS] runs of each along [PopulationSeeds]): the damage taken during
 * the Apex arm's windows, summed over the pairs, is [0.55, 0.85] of what the no-Apex arm takes in the same windows.
 *
 * The offence band and both defensive bands are population rules (0.5.0 active items; policy.md, "Balance gate
 * populations"). On the fixed seed list (48 offensive runs per character, 64 defensive) the offence passed with
 * probability 0.97 on the 05885fa9 base but 0.83 with the active items, through its ±40 % clause (the median band
 * passed every time, and the clause is measured against it), and the Diamond passed with probability 0.99 and 0.58. The
 * Prism passed with probability 0.99 on the base; with the active items 0.94 by [PopulationSeeds] but 0.75 by the
 * calibration windows, and where the two disagree the pooled runs decide: pooled, a 64-run draw passes about 0.82 of
 * the time, below 0.90. Their populations are the ones the base was measured on, 96 offensive and 128 defensive runs
 * per character with four forks each: fewer than 300 runs, since these fights already set the suite's runtime. On the
 * 0.6.0 base the offence median is 0.850 (bootstrap SE 0.005, resampling whole runs with their forks), each character's
 * benefit 0.87 (Ring) to 1.39 (Crescent) of the median benefit (SE 0.05-0.11), the Diamond's ratio 0.695 (run-bootstrap
 * SE 0.035) and the Prism's 0.684 (SE 0.052). By the population threshold rule (the wider of the design's band and the
 * base ± two standard errors, rounded outward) the median band and both defensive bands stay the design's (Prism
 * 0.58-0.79, Diamond 0.62-0.77, inside [0.55, 0.85]). The ±40 % clause is the design's character band and never
 * weakens, although Crescent's and Shard's base + 2 SE reach 1.56 of the median benefit; Crescent passes by 0.13 SE.
 *
 * In 0.5.0, 128 runs did not settle the defensive bands either: a fresh 128-run draw (four forks each) from the 256
 * calibration-window runs landed in [0.55, 0.85] with probability about 0.82 for the Prism (window ratio 0.814) and
 * 0.84 for the Diamond (0.589); 0.6.0's single Mirror Stance parry moved the Diamond's pooled window reading to 0.701.
 * baseline.md records this as a trade-off for the owner, not a reason to restate again.
 */
class ApexArchitectBenefitTest {
    @Test
    fun theApexShortensOffensiveFightsAndShieldsTheDefensiveCharactersWithinItsBand() {
        val outside = mutableListOf<String>()
        val pairs = OFFENSIVE.associateWith { shape -> benefitPairs(shape, 0) }
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
        // The Prism and the Diamond look for their runs at once, so the fight pool never waits on one's slowest batch.
        val defensive = DEFENSIVE.zip(populationOf(DEFENSIVE) { shape -> benefitPairs(shape, DEFENSIVE_TIER) })
        for ((shape, list) in defensive) {
            val taken = list.sumOf { it.apexWindowDamage }
            val without = list.sumOf { it.plainWindowDamage }
            println("APEX-DEFENSE $shape tier=$DEFENSIVE_TIER pairs=${list.size} taken=${"%.0f".format(taken)} " +
                "without=${"%.0f".format(without)} ratio=${"%.3f".format(taken / without)}")
            if (!(without > 0.0) || (taken / without).toFloat() !in 0.55f..0.85f) outside += "$shape took $taken against $without"
        }
        assertTrue(outside.isEmpty(), "Apex benefit: $outside")
    }
}
