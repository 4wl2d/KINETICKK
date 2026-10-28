// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.OmenId
import kotlin.math.abs

/*
 * The omen arms (variety.md §11.3), split over RunOmenArmsGateTest, RunOmenArmsBGateTest and RunOmenArmsCGateTest (four
 * omens each) so each class stays well under eight minutes (one arm of 300 runs takes 35 to 70 s, the shared base as
 * long). Every arm forces its omen at the start of a fresh tier-0 run on
 * [PopulationSeeds] flown by the NoPoi bot ([flyVarietyRun]); the base is No omen on the same seeds
 * ([VarietyBasePopulation]). Per arm: win share W, median survival S, median banked Matter M and mean level at 600 s L,
 * each with a bootstrap standard error (20,000 resamples, paired by seed against the base, RNG seed 20261027).
 *
 * Design bands (decisions Addendum B continued: new 0.6.0 gates are met by content): every omen is a trade, neither a
 * trap nor an upgrade, so its win share lies within [MAX_WIN_SHIFT] of the base's; Rich Veins banks at least
 * [RICH_VEINS_MATTER] times the base's median Matter, Iron Tide [IRON_TIDE_MATTER] times, Ascetic Path at most
 * [ASCETIC_PATH_MATTER] times; Hungry Dark's Data reaches at least the base's level at 600 s. Magnitudes are calibrated
 * on the VARIETY window (i = 1,601..1,900) only, where every shift lay within -0.077..+0.060. Measured on these seeds
 * with the lane tree: the base wins 0.693 (SE 0.027) and banks a median 2,334 Matter; the shifts lie within -0.077
 * (Rich Veins) and +0.117 (Still Water, SE 0.030, the arm closest to its bound); Rich Veins banks 1.43, Iron Tide 1.26
 * and Ascetic Path 0.62 times the base, and Hungry Dark gains 1.85 levels at 600 s.
 */

/** An omen's win share differs from No omen's by at most this much (spec §11.3). */
internal const val MAX_WIN_SHIFT = 0.12
internal const val RICH_VEINS_MATTER = 1.30
internal const val IRON_TIDE_MATTER = 1.15
internal const val ASCETIC_PATH_MATTER = 0.75

/** Flies [omens] against the base, prints each arm's line and returns the failed clauses. */
internal fun omenArmFailures(omens: List<OmenId>): List<String> {
    val base = VarietyBasePopulation
    println("OMEN-ARMS BASE ${armLine(base)}")
    val failures = ArrayList<String>()
    for (omen in omens) {
        val runs = populationOf(PopulationSeeds) { flyVarietyRun(it, omen = omen) }
        println("OMEN-ARMS $omen ${armLine(runs)} ${pairedLine(base, runs)}")
        val shift = runs.winShare() - base.winShare()
        if (abs(shift) > MAX_WIN_SHIFT) failures += "$omen moves the win share by ${"%.3f".format(shift)}"
        val matter = runs.medianMatter() / base.medianMatter()
        when (omen) {
            OmenId.RICH_VEINS -> if (matter < RICH_VEINS_MATTER) failures += "$omen banks ${"%.3f".format(matter)} of the base's Matter"
            OmenId.IRON_TIDE -> if (matter < IRON_TIDE_MATTER) failures += "$omen banks ${"%.3f".format(matter)} of the base's Matter"
            OmenId.ASCETIC_PATH -> if (matter > ASCETIC_PATH_MATTER) failures += "$omen banks ${"%.3f".format(matter)} of the base's Matter"
            OmenId.HUNGRY_DARK -> if (runs.meanLevelAt600() < base.meanLevelAt600()) {
                failures += "$omen reaches level ${"%.2f".format(runs.meanLevelAt600())} at 600 s against ${"%.2f".format(base.meanLevelAt600())}"
            }
            else -> Unit
        }
    }
    return failures
}
