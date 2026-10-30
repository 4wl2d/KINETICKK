// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in site gates S-G1, S-G3 and S-G4 (spec §14.3). The [SitePilot] composes the NoPoi bot and plays every window that
 * offers its arm's family (the family takes the window weight against the anomalies, so it is offered in every window it
 * is eligible for), on the first [GATE_RUNS] [PopulationSeeds] with the fresh tier-0 Orb at 1280×800 (the race also on a
 * 412×915 phone); a race or challenge run is flown until its last window can no longer run. For S-G4 the pilot's mixed
 * arm plays the shipped window weights and engages the first-drawn site of every window (anomaly, gate race, challenge
 * or bounty) to the run's end, and the NoPoi bot flies the same seeds and viewport opting into nothing. Every engagement
 * counts, a death during one as a failure.
 *
 * Values use the spec's units ([siteRewardValue]: directed reward 1.0, plain item offer 0.75, rarity-floor offer 1.1,
 * Key 0.6, Relic pickup 0.9, Matter 0.01, repair 0.1, Data 0.02), a bounty's including its boss's own kill payout
 * ([engagementValue]); a failed engagement is worth nothing.
 *
 * Thresholds are the design targets; the records step restates them by the population rule on the final tree (floors
 * `min(design, base − 2 SE)`, ceilings `max(design, base + 2 SE)`). Base measurements with bootstrap standard errors
 * (20,000 resamples of whole runs, a seed's mixed and NoPoi runs resampled together, RNG seed 20261027) are printed on
 * the gate lines and recorded in the SITES notes. Knobs (race factors, par reach, challenge requirements) are calibrated
 * on the SITES window only (i = 2,201..2,300, [SiteCalibrationProbeTest]), never on these seeds.
 */
class SiteEngagementGateTest {
    /**
     * S-G1: the race's medal tiers are reachable. Over race engagements on the board at least [MIN_BRONZE] earn bronze
     * or better and [MIN_SILVER] silver or better, and the gold share lies in [GOLD_BAND]; on a phone at least
     * [MIN_PHONE_BRONZE] earn bronze or better.
     */
    @Test
    fun raceMedalTiersAreReachableOnBoardAndPhone() {
        val race = setOf(SiteKind.GATE_RACE)
        val board = raceBoard.judged(race)
        val phone = racePhone.judged(race)
        val bronze = board.shareWhere { it.outcome.medal >= 1 }
        val silver = board.shareWhere { it.outcome.medal >= 2 }
        val gold = board.shareWhere { it.outcome.medal >= 3 }
        val phoneBronze = phone.shareWhere { it.outcome.medal >= 1 }
        println(
            "S-G1 board n=${board.size} " +
                bootstrapLine("bronzeUp", raceBoard) { it.judged(race).shareWhere { e -> e.outcome.medal >= 1 } } + " " +
                bootstrapLine("silverUp", raceBoard) { it.judged(race).shareWhere { e -> e.outcome.medal >= 2 } } + " " +
                bootstrapLine("gold", raceBoard) { it.judged(race).shareWhere { e -> e.outcome.medal >= 3 } } +
                " outcomes=${outcomes(board)}",
        )
        println(
            "S-G1 phone n=${phone.size} " +
                bootstrapLine("bronzeUp", racePhone) { it.judged(race).shareWhere { e -> e.outcome.medal >= 1 } } +
                " outcomes=${outcomes(phone)}",
        )
        assertTrue(board.size >= MIN_ENGAGEMENTS && phone.size >= MIN_ENGAGEMENTS, "the race arms engaged ${board.size} and ${phone.size} races")
        assertTrue(bronze >= MIN_BRONZE, "only ${"%.3f".format(bronze)} of ${board.size} races earned bronze or better; expected at least $MIN_BRONZE")
        assertTrue(silver >= MIN_SILVER, "only ${"%.3f".format(silver)} of ${board.size} races earned silver or better; expected at least $MIN_SILVER")
        assertTrue(gold in GOLD_BAND, "${"%.3f".format(gold)} of ${board.size} races earned gold; expected within $GOLD_BAND")
        assertTrue(
            phoneBronze >= MIN_PHONE_BRONZE,
            "only ${"%.3f".format(phoneBronze)} of ${phone.size} phone races earned bronze or better; expected at least $MIN_PHONE_BRONZE",
        )
    }

    /** S-G3: each of the five challenges completes in a share of its engagements within [COMPLETION_BAND]. */
    @Test
    fun everyChallengeCompletesWithinItsBand() {
        val failures = ArrayList<String>()
        for ((arm, runs) in challenges) {
            val engagements = runs.judged(arm.kinds)
            val completion = engagements.shareWhere { it.outcome.success }
            println(
                "S-G3 $arm n=${engagements.size} " +
                    bootstrapLine("completion", runs) { it.judged(arm.kinds).shareWhere { e -> e.outcome.success } } +
                    " outcomes=${outcomes(engagements)}",
            )
            if (engagements.size < MIN_ENGAGEMENTS) failures += "$arm engaged only ${engagements.size} challenges"
            if (completion !in COMPLETION_BAND) {
                failures += "$arm completed ${"%.3f".format(completion)} of ${engagements.size}; expected within $COMPLETION_BAND"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    /**
     * S-G4: per engagement, a race and a challenge pay [RACE_CHALLENGE_BAND] times an anomaly's expected value and a
     * bounty [BOUNTY_BAND] times; the share of site-engaging runs that win is at least the NoPoi runs' share of wins
     * without a site minus [MAX_WIN_SHARE_LOSS].
     */
    @Test
    fun sitesPayLikeAnAnomalyAndEngagingRunsStillWin() {
        val anomaly = mixed.judged(kindsOf(SiteFamily.ANOMALY)).meanValue()
        val failures = ArrayList<String>()
        for ((family, band) in listOf(
            SiteFamily.RACE to RACE_CHALLENGE_BAND,
            SiteFamily.CHALLENGE to RACE_CHALLENGE_BAND,
            SiteFamily.BOUNTY to BOUNTY_BAND,
        )) {
            val kinds = kindsOf(family)
            val engagements = mixed.judged(kinds)
            val ratio = engagements.meanValue() / anomaly
            println(
                "S-G4 $family n=${engagements.size} " +
                    bootstrapLine("valueRatio", mixed) { it.judged(kinds).meanValue() / it.judged(kindsOf(SiteFamily.ANOMALY)).meanValue() } +
                    " " + bootstrapLine("value", mixed) { it.judged(kinds).meanValue() } +
                    " " + bootstrapLine("success", mixed) { it.judged(kinds).shareWhere { e -> e.outcome.success } },
            )
            if (engagements.size < MIN_FAMILY_ENGAGEMENTS) failures += "the mixed arm engaged only ${engagements.size} ${family}s"
            if (ratio !in band) failures += "$family pays ${"%.3f".format(ratio)} anomalies per engagement; expected within $band"
        }
        val anomalies = mixed.judged(kindsOf(SiteFamily.ANOMALY))
        println(
            "S-G4 ANOMALY n=${anomalies.size} " + bootstrapLine("value", mixed) { it.judged(kindsOf(SiteFamily.ANOMALY)).meanValue() } +
                " " + bootstrapLine("success", mixed) { it.judged(kindsOf(SiteFamily.ANOMALY)).shareWhere { e -> e.outcome.success } },
        )
        val pairs = mixed.zip(noPoi)
        val engaging = engagingWinShare(pairs)
        val withoutSites = noPoiWinShare(pairs)
        println(
            "S-G4 wins engaging=${mixed.count { it.engagements.isNotEmpty() }} " + bootstrapLine("engagingWinShare", pairs, ::engagingWinShare) +
                " " + bootstrapLine("noPoiWinShare", pairs, ::noPoiWinShare) +
                " " + bootstrapLine("difference", pairs) { engagingWinShare(it) - noPoiWinShare(it) },
        )
        if (engaging < withoutSites - MAX_WIN_SHARE_LOSS) {
            failures += "site-engaging runs won ${"%.3f".format(engaging)} against ${"%.3f".format(withoutSites)} without a site; " +
                "expected at most $MAX_WIN_SHARE_LOSS less"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    private companion object {
        /** Runs per arm: the first [GATE_RUNS] of [PopulationSeeds] (C42). */
        const val GATE_RUNS = 150

        /** An arm that engaged fewer than this many sites measured nothing (every arm is offered up to six per run). */
        const val MIN_ENGAGEMENTS = 300

        const val MIN_BRONZE = 0.75
        const val MIN_SILVER = 0.35
        val GOLD_BAND = 0.10..0.45
        const val MIN_PHONE_BRONZE = 0.65
        val COMPLETION_BAND = 0.30..0.90

        /** A family the mixed arm engaged fewer times than this measured nothing. */
        const val MIN_FAMILY_ENGAGEMENTS = 40
        val RACE_CHALLENGE_BAND = 0.75..1.5
        val BOUNTY_BAND = 1.0..2.2
        const val MAX_WIN_SHARE_LOSS = 0.10

        val Seeds: List<Int> = PopulationSeeds.take(GATE_RUNS)

        val raceBoard: List<SiteRun> by lazy {
            populationOf(Seeds) { seed -> flySiteRun(seed, SiteArm.RACE, SiteRunSetup(stopAfterWindows = true)) }
        }
        val racePhone: List<SiteRun> by lazy {
            populationOf(Seeds) { seed -> flySiteRun(seed, SiteArm.RACE, SiteRunSetup(412f, 915f, stopAfterWindows = true)) }
        }
        val challenges: List<Pair<SiteArm, List<SiteRun>>> by lazy {
            listOf(SiteArm.BRAKELESS, SiteArm.RAM, SiteArm.UNTOUCHED, SiteArm.VELOCITY, SiteArm.SLALOM).map { arm ->
                arm to populationOf(Seeds) { seed -> flySiteRun(seed, arm, SiteRunSetup(stopAfterWindows = true)) }
            }
        }

        val mixed: List<SiteRun> by lazy { populationOf(Seeds) { seed -> flySiteRun(seed, SiteArm.MIXED) } }
        val noPoi: List<SiteRun> by lazy { populationOf(Seeds) { seed -> flyNoPoiRun(seed, width = 1_280f, height = 800f) } }

        fun kindsOf(family: SiteFamily): Set<SiteKind> = SiteArm.MIXED.kinds.filter { it.family == family }.toSet()

        /** Of the mixed runs that engaged a site, the share that won. */
        fun engagingWinShare(pairs: List<Pair<SiteRun, SiteRun>>): Double =
            pairs.map { it.first }.filter { it.engagements.isNotEmpty() }.shareOf { it.won }

        /** Of the NoPoi runs, the share that won without opting into a site. */
        fun noPoiWinShare(pairs: List<Pair<SiteRun, SiteRun>>): Double = pairs.map { it.second }.shareOf { it.noPoiWin }

        fun outcomes(engagements: List<SiteEngagement>): String =
            engagements.groupingBy { it.outcome }.eachCount().entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key}:${it.value}" }
    }
}
