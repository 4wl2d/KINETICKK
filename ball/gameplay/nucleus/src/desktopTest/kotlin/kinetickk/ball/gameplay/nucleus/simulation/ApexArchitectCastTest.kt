// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.firstRuns
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.fight
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the bot casts its Apex in every one of 312
 * population fights, a population rule over [PopulationSeeds]. The fights are [ApexArchitectFightHarness]'s; the
 * contract is [ApexArchitectFightTest]'s and the benefit bands [ApexArchitectBenefitTest]'s.
 */
class ApexArchitectCastTest {
    /**
     * The no-POI bot casts its Apex in at least [MIN_CAST_SHARE] of the fights of [CAST_FIGHTS_PER_CASE] runs per
     * character and tier along [PopulationSeeds] (312 fights). A population rule (0.5.0 active items): the contract
     * once required a cast in every Apex arm of its 72 fights, and the 05885fa9 base left 3 of 1,816 fights without a
     * cast (the first 50 to 128 runs per character and tier along [PopulationSeeds]; Orb tier 10 once, Dipole tier 10
     * twice), so the 72 fights passed with probability 0.83, and 0.5.0 bounded the share at 0.99 (that base's 0.9983
     * minus two standard errors at 312 fights). On the 0.6.0 base the bot casts in all 312 fights (binomial SE 0), so by
     * the population threshold rule the bound is the lower of the seeded "every fight" and the base share minus two
     * standard errors: every fight casts.
     */
    @Test
    fun theBotCastsItsApexInNearlyEveryArchitectFight() {
        // The cases look for their runs at once, so the fight pool never waits on one case's slowest batch.
        val cases = CoreShape.entries.flatMap { shape -> listOf(0, 10).map { tier -> shape to tier } }
        val fights = populationOf(cases) { (shape, tier) ->
            firstRuns(shape, tier, CAST_FIGHTS_PER_CASE, PopulationSeeds) { _, state, bot ->
                fight(state.copyForReduction(), bot.copy(), apex = true, windows = emptyList()).casts
            }.map { (seed, casts) -> Triple("$shape tier $tier", seed, casts) }
        }.flatten()
        val silent = fights.filter { it.third == 0 }
        val share = fights.shareOf { it.third > 0 }
        println("APEX-CASTS fights=${fights.size} cast=${"%.4f".format(share)} without a cast: " +
            silent.joinToString { (case, seed) -> "$case seed $seed" })
        assertTrue(share >= MIN_CAST_SHARE, "the bot cast its Apex in only ${"%.4f".format(share)} of ${fights.size} fights: " +
            silent.joinToString { (case, seed) -> "$case seed $seed" })
    }

    private companion object {
        /** The cast rule's runs per character and tier along [PopulationSeeds]. */
        const val CAST_FIGHTS_PER_CASE = 13
        /** The least share of those fights in which the bot casts its Apex (0.6.0 base 1.0000, SE 0 at 312 fights). */
        const val MIN_CAST_SHARE = 1.0
    }
}
