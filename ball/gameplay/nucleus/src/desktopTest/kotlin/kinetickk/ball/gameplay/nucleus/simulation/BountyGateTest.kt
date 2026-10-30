// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in site gate S-G2 (spec §14.3): the bounty arena's duel against its mini-boss (Warden Prime at the bounty policy's
 * integrity share). The [SitePilot]'s bounty arm takes every bounty the windows offer (windows 165 to 525 s) and fights
 * the boss as the NoPoi bot fights the Architect, pulled back toward the arena's centre past 800 u; on the first
 * [GATE_RUNS] [PopulationSeeds] at 1280×800, once as the fresh tier-0 Orb and once as a tier-6 profile with the full
 * Core Matrix. A run is flown until its last window can no longer run; every bounty counts, a death during one as a
 * failure.
 *
 * Thresholds are the design targets; the records step restates them by the population rule on the final tree. Base
 * measurements with bootstrap standard errors (20,000 resamples of whole runs, RNG seed 20261027) are printed on the
 * gate lines and recorded in the SITES notes. The integrity share is calibrated on the SITES window only
 * (i = 2,201..2,300, [SiteCalibrationProbeTest]), never on these seeds. The class also flies the pilot's other duel,
 * the Cartographer's sigil, on the same seeds and records it.
 */
class BountyGateTest {
    /**
     * S-G2: the share of bounties claimed lies in [FRESH_BAND] for the fresh tier-0 Orb and in [VETERAN_BAND] for the
     * tier-6 full-Matrix profile, and the median claimed fight lasts at most [MAX_MEDIAN_FIGHT] seconds for both.
     */
    @Test
    fun bountiesAreClaimedWithinTheirBandsAndFightsStayShort() {
        val failures = ArrayList<String>()
        for ((name, runs, band) in listOf(Triple("FRESH", fresh, FRESH_BAND), Triple("VETERAN", veteran, VETERAN_BAND))) {
            val engagements = runs.judged(SiteArm.BOUNTY.kinds)
            val clear = engagements.shareWhere { it.outcome.success }
            val fight = medianFight(runs)
            println(
                "S-G2 $name n=${engagements.size} " +
                    bootstrapLine("clear", runs) { it.judged(SiteArm.BOUNTY.kinds).shareWhere { e -> e.outcome.success } } + " " +
                    bootstrapLine("medianFight", runs, ::medianFight) +
                    " outcomes=" + engagements.groupingBy { it.outcome }.eachCount().entries.sortedBy { it.key.ordinal }
                    .joinToString(",") { "${it.key}:${it.value}" },
            )
            if (engagements.size < MIN_ENGAGEMENTS) failures += "$name engaged only ${engagements.size} bounties"
            if (clear !in band) failures += "$name claimed ${"%.3f".format(clear)} of ${engagements.size} bounties; expected within $band"
            if (fight > MAX_MEDIAN_FIGHT) failures += "$name's median claimed fight lasted ${"%.1f".format(fight)} s; expected at most $MAX_MEDIAN_FIGHT s"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    /**
     * The sigil arm (no balance threshold: the secret duel's band is ENEMIES' E8): from 300 s the pilot flies straight
     * away from the spawn, touches the Cartographer's sigil far travel places and duels its boss as it fights a bounty's,
     * pulled back past 1,800 u (the duel's leash is 2,400 u). The arm must reach the summoning path in at least
     * [MIN_SUMMONED] of the runs, so a pilot that stopped flying out would fail here rather than measure nothing; the
     * duel's outcomes and its median claimed fight are printed for the records.
     */
    @Test
    fun theSigilArmSummonsTheCartographerAndRecordsItsDuel() {
        val summoned = sigil.shareOf { it.engagements.isNotEmpty() }
        val duels = sigil.flatMap { it.engagements }
        println(
            "SIGIL ARM runs=${sigil.size} duels=${duels.size} " + bootstrapLine("summoned", sigil) { it.shareOf { run -> run.engagements.isNotEmpty() } } +
                " " + bootstrapLine("broken", sigil) { it.judged(SiteArm.SIGIL.kinds).shareWhere { e -> e.outcome.success } } +
                " " + bootstrapLine("medianFight", sigil) { runs ->
                    runs.judged(SiteArm.SIGIL.kinds).filter { it.outcome.success }.map { it.seconds }.sorted()
                        .let { if (it.isEmpty()) 0.0 else it[it.size / 2].toDouble() }
                } +
                " outcomes=" + duels.groupingBy { it.outcome }.eachCount().entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key}:${it.value}" },
        )
        assertTrue(summoned >= MIN_SUMMONED, "the sigil arm summoned the Cartographer in only ${"%.3f".format(summoned)} of ${sigil.size} runs")
    }

    private companion object {
        /** Runs per arm: the first [GATE_RUNS] of [PopulationSeeds] (C42). */
        const val GATE_RUNS = 150

        /** An arm that engaged fewer bounties than this measured nothing (up to four per run). */
        const val MIN_ENGAGEMENTS = 200

        /** The sigil arm's least share of runs that reach the summoning (a pilot check; 0.91 on the SITES window). */
        const val MIN_SUMMONED = 0.5

        val FRESH_BAND = 0.40..0.85
        val VETERAN_BAND = 0.40..0.90
        const val MAX_MEDIAN_FIGHT = 75.0

        val Seeds: List<Int> = PopulationSeeds.take(GATE_RUNS)

        val fresh: List<SiteRun> by lazy {
            populationOf(Seeds) { seed -> flySiteRun(seed, SiteArm.BOUNTY, SiteRunSetup(stopAfterWindows = true)) }
        }
        val veteran: List<SiteRun> by lazy {
            populationOf(Seeds) { seed ->
                flySiteRun(seed, SiteArm.BOUNTY, SiteRunSetup(profile = gateProfile(GateMatrix.FULL, tier = 6), stopAfterWindows = true))
            }
        }

        val sigil: List<SiteRun> by lazy {
            populationOf(Seeds) { seed -> flySiteRun(seed, SiteArm.SIGIL, SiteRunSetup(stopAfterWindows = true)) }
        }

        /** The median length of the claimed fights (simulated seconds; 0 without one). */
        fun medianFight(runs: List<SiteRun>): Double =
            runs.judged(SiteArm.BOUNTY.kinds).filter { it.outcome.success }.map { it.seconds }.sorted()
                .let { if (it.isEmpty()) 0.0 else it[it.size / 2].toDouble() }
    }
}
