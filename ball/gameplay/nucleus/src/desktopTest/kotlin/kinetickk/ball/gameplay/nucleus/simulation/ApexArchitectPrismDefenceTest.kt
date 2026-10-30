// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.defensiveBandBreach
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the Prism's defensive band
 * ([ApexArchitectFightHarness.defensiveBandBreach]; the Prism's and the Diamond's bands are separate tests so that a
 * 4-vCPU CI job plays only one of them, see [ApexArchitectOffenceTest]). On the fixed seed list (64 runs) it passed
 * with probability 0.99 on the 05885fa9 base; with the active items 0.94 by [PopulationSeeds] but 0.75 by the
 * calibration windows, and where the two disagree the pooled runs decide: pooled, a 64-run draw passes about 0.82 of
 * the time, below 0.90. On the 0.6.0 base its ratio is 0.684 (run-bootstrap SE 0.052), inside the design's band
 * (base ± 2 SE 0.58-0.79).
 */
class ApexArchitectPrismDefenceTest {
    @Test
    fun theApexShieldsThePrismWithinItsBand() {
        val breach = defensiveBandBreach(CoreShape.PRISM)
        assertTrue(breach == null, "Apex benefit: ${listOfNotNull(breach)}")
    }
}
