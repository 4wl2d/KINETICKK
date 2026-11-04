// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.variety

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Rebirth ladder's design targets for the profiles below the top (docs/balance/progression.md, P1-P3 and P6), played
 * by the input-only NoPoi bot on the gate harness ([sweepRun], the arms of [SweepArm]) over the 300 [PopulationSeeds]:
 * - P1: a fresh profile (no Matrix, no upgrades) wins tier 0 in at least [P1_MIN_WIN] of the runs;
 * - P2: a fresh profile only contests tier 2 (at most [P2_MAX_TIER_TWO_WIN]), mostly loses tier 3 (at most
 *   [P2_MAX_TIER_THREE_WIN]) and cannot clear tier 4 (at most [P2_MAX_TIER_FOUR_WIN]);
 * - P3: half the Matrix with Refit and Honing at half the maximum level contests tier 7 ([P3_TIER_SEVEN_WIN]) and cannot
 *   clear tier 9 (at most [P3_MAX_TIER_NINE_WIN]);
 * - P6: for each of these profiles the win share never rises with the tier (beyond two binomial standard errors).
 * The fully progressed profiles at tier 10 (P4, P5) and the perceptibility of each permanent family (P7) are
 * UpgradeBalanceGateTest's. These targets replace the 0.6.0 gates A-H (the owner superseded "tier 10 clears at most 3 of
 * 5": a fully progressed player now clears tier 10 comfortably), so the old fixed-seed statements are gone. Gate I keeps
 * active items from trivialising tier 0. Thresholds are the design's; the population rule of policy.md restates them on
 * the integrated base (a design target never weakens).
 */
class RebirthBalanceGateTest {
    @Test fun p1AFreshProfileWinsTierZero() {
        val share = winShare(SweepArm.FRESH, 0)
        println("GATE P1 tier=0 fresh win share ${"%.3f".format(share)} of ${PopulationSeeds.size}")
        assertTrue(share >= P1_MIN_WIN, "a fresh profile must win tier 0: ${"%.3f".format(share)} below $P1_MIN_WIN")
    }

    @Test fun p2AFreshProfileContestsTierTwoAndCannotClearTierFour() {
        val two = winShare(SweepArm.FRESH, 2)
        val three = winShare(SweepArm.FRESH, 3)
        val four = winShare(SweepArm.FRESH, 4)
        println("GATE P2 fresh win share tier=2 ${"%.3f".format(two)} tier=3 ${"%.3f".format(three)} tier=4 ${"%.3f".format(four)}")
        assertTrue(two <= P2_MAX_TIER_TWO_WIN, "tier 2 must ask for progression: a fresh profile wins ${"%.3f".format(two)}")
        assertTrue(three <= P2_MAX_TIER_THREE_WIN, "tier 3 must mostly beat a fresh profile: it wins ${"%.3f".format(three)}")
        assertTrue(four <= P2_MAX_TIER_FOUR_WIN, "tier 4 must be out of a fresh profile's reach: it wins ${"%.3f".format(four)}")
    }

    @Test fun p3HalfTheProgressionContestsTierSevenAndCannotClearTierNine() {
        val seven = winShare(SweepArm.HALF, 7)
        val nine = winShare(SweepArm.HALF, 9)
        println("GATE P3 half win share tier=7 ${"%.3f".format(seven)} tier=9 ${"%.3f".format(nine)}")
        assertTrue(seven in P3_TIER_SEVEN_WIN, "half the progression must contest tier 7: it wins ${"%.3f".format(seven)}")
        assertTrue(nine <= P3_MAX_TIER_NINE_WIN, "tier 9 must outgrow half the progression: it wins ${"%.3f".format(nine)}")
    }

    @Test fun p6AProfilesWinShareNeverRisesWithTheTier() {
        val ladders = mapOf(SweepArm.FRESH to listOf(0, 2, 3, 4), SweepArm.HALF to listOf(7, 9))
        val rises = ladders.flatMap { (arm, tiers) ->
            tiers.zipWithNext().mapNotNull { (low, high) ->
                val lower = winShare(arm, low)
                val higher = winShare(arm, high)
                val se = sqrt(lower * (1 - lower) / PopulationSeeds.size + higher * (1 - higher) / PopulationSeeds.size)
                "${arm.label} tier $high ${"%.3f".format(higher)} > tier $low ${"%.3f".format(lower)}".takeIf { higher > lower + 2 * se }
            }
        }
        assertTrue(rises.isEmpty(), "a win share rose with the tier: $rises")
    }

    /**
     * Gate I (0.5.0 actives): active items help a fresh tier-0 run but do not trivialise it. The same bot on the
     * catalog without the active items ([NoActives], a run as before they existed) clears the Architect no more than a
     * fifth slower than with the actives its offers dealt and it cast. A run that dies counts as never clearing.
     *
     * A population gate over [PopulationSeeds]. On the Elite-resonance base the population's median clear was 94.48 s with
     * active items against 114.57 s without (ratio 0.825, bootstrap SE 0.024 paired by seed, n 300 per arm; 0.852 on the
     * tree before it and 0.904 on the 0.6.0 base). The ratio must be at least [I_MIN_CLEAR_RATIO]: by the population
     * threshold rule the lower of the seeded 0.8 and the base minus two standard errors (0.777), rounded down.
     */
    @Test fun skillsHelpAFreshTierZeroRunWithoutTrivialisingIt() {
        val skilled = runs(SweepArm.FRESH, 0).medianClear()
        val unskilled = populationOf(PopulationSeeds) { seed -> sweepRun(seed, SweepArm.FRESH.profile(0, NoActives), emptyList(), NoActives) }
            .medianClear()
        println("GATE I tier=0 fresh population median clear skills=$skilled no-skills=$unskilled ratio=${skilled / unskilled}")
        assertTrue(skilled >= unskilled * I_MIN_CLEAR_RATIO,
            "skills must not trivialise tier 0: population median clear $skilled s against $unskilled s without skills, " +
                "below $I_MIN_CLEAR_RATIO of it")
    }

    /** Seconds from the Architect's arrival to the Victory, the upper middle over the runs; a loss never clears. */
    private fun List<SweepRun>.medianClear(): Float = map { it.clear ?: Float.POSITIVE_INFINITY }.sorted()[size / 2]

    private companion object {
        /** P1: the least tier-0 win share of a fresh profile. */
        const val P1_MIN_WIN = 0.75

        /** P2: the largest tier-2 win share of a fresh profile. */
        const val P2_MAX_TIER_TWO_WIN = 0.65

        /** P2: the largest tier-3 win share of a fresh profile. */
        const val P2_MAX_TIER_THREE_WIN = 0.30

        /** P2: the largest tier-4 win share of a fresh profile. */
        const val P2_MAX_TIER_FOUR_WIN = 0.05

        /** P3: the tier-7 win share band of half the Matrix with half the upgrades. */
        val P3_TIER_SEVEN_WIN = 0.30..0.85

        /** P3: the largest tier-9 win share of half the Matrix with half the upgrades. */
        const val P3_MAX_TIER_NINE_WIN = 0.10

        /**
         * Gate I: the least ratio of the population's median clear with active items to the median clear without them
         * (seeded 0.8; Elite-resonance base 0.825, paired SE 0.024, n 300; the population threshold rule).
         */
        const val I_MIN_CLEAR_RATIO = 0.77f

        /** The shipped content without its active items: no run here owns one, and its deck and offers are the 0.5.0 ones. */
        val NoActives: GameplayContentSnapshot = withoutActiveItemCatalog()

        /** Each arm and tier's [PopulationSeeds] runs, played once per test JVM and shared by the gates that read them. */
        private val played = HashMap<Pair<SweepArm, Int>, List<SweepRun>>()

        @Synchronized
        fun runs(arm: SweepArm, tier: Int): List<SweepRun> = played.getOrPut(arm to tier) {
            populationOf(PopulationSeeds) { seed ->
                sweepRun(seed, arm.profile(tier, canonicalGameplayContent), arm.relics, canonicalGameplayContent)
            }
        }

        fun winShare(arm: SweepArm, tier: Int): Double = runs(arm, tier).shareOf { it.won }
    }
}
