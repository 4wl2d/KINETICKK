// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Event signals with an engaging pilot (variety.md §11.5). The [SignalSeeker] (the NoPoi bot steering to every live
 * signal and taking the spec's card per kind) flies fresh tier-0 runs on [PopulationSeeds]; the NoPoi bot, which opts
 * into nothing, flies the same seeds ([VarietyBasePopulation]). Design bands (decisions Addendum B continued): the seeker
 * touches at least [MIN_REACH] of the signals its runs spawn; its win share lies in [[MIN_WIN_SHIFT], [MAX_WIN_SHIFT]]
 * around the NoPoi runs' (a signal is a trade, not a trap nor a must); and its median banked Matter is at least
 * [MIN_MATTER_RATIO] of theirs (a Merchant's spend stays bounded). The line counts the runs whose bot's avoidance still
 * touched another site, as the NoPoi runs' does. Measurements carry
 * bootstrap standard errors (20,000 resamples of seeds, paired, RNG seed 20261027); prices and odds are calibrated on
 * the VARIETY window (i = 1,601..1,900) only; none moved (there the seeker touched 782 of 782 signals, shifted the win
 * share by +0.027 and banked 1.01 times the NoPoi Matter). Measured on these seeds with the lane tree: reach 0.996 (797
 * of 800), win shift +0.023 (SE 0.027) against the NoPoi runs' 0.693, Matter 1.00 (SE 0.014) of theirs.
 */
class EventSignalEngagementGateTest {
    @Test
    fun aSignalSeekerReachesItsSignalsAndStillWinsAndBanks() {
        val base = VarietyBasePopulation
        val seeker = populationOf(PopulationSeeds) { flyVarietyRun(it, seek = true) }
        println("SIGNAL-GATE NOPOI ${armLine(base)}")
        println("SIGNAL-GATE SEEKER ${armLine(seeker)} ${pairedLine(base, seeker)} ${signalLine(seeker)}")
        val failures = ArrayList<String>()
        val reach = seeker.reachShare()
        if (reach < MIN_REACH) failures += "the seeker touched only ${"%.3f".format(reach)} of the signals"
        val shift = seeker.winShare() - base.winShare()
        if (shift !in MIN_WIN_SHIFT..MAX_WIN_SHIFT) failures += "seeking signals moves the win share by ${"%.3f".format(shift)}"
        val matter = seeker.medianMatter() / base.medianMatter()
        if (matter < MIN_MATTER_RATIO) failures += "the seeker banks ${"%.3f".format(matter)} of the NoPoi runs' Matter"
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    private companion object {
        const val MIN_REACH = 0.85
        const val MIN_WIN_SHIFT = -0.08
        const val MAX_WIN_SHIFT = 0.12
        const val MIN_MATTER_RATIO = 0.8
    }
}
