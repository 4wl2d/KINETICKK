// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.characters.ApexArchitectFightHarness.defensiveBandBreach
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the Prism's defensive band
 * ([ApexArchitectFightHarness.defensiveBandBreach]; the Prism's and the Diamond's bands are separate tests so that a
 * 4-vCPU CI job plays only one of them, see [ApexArchitectOffenceTest]). On the fixed seed list (64 runs) it passed
 * with probability 0.99 on the 05885fa9 base; with the active items 0.94 by [PopulationSeeds] but 0.75 by the
 * calibration windows, and where the two disagree the pooled runs decide: pooled, a 64-run draw passes about 0.82 of
 * the time, below 0.90. On the Elite-resonance base its ratio is 0.744 (run-bootstrap SE 0.048; 0.808 on the tree
 * before it and 0.684 on the 0.6.0 base), inside the design's band (base ± 2 SE 0.65-0.84).
 *
 * The progression model took the tier's power away, and the full-Matrix arm without Refit and Honing rarely pushed the
 * Architect past its first phases within the 180 s (0.6 of the window fights timed out), where the facets Monolith
 * restores negate nearly every hit: 0.514. Neither Monolith's regrowth (1.5 s and 2 s: 0.467 and 0.472 on the window
 * i = 1,601..1,900) nor the incoming-damage curve (the old one: 0.390) moved it; the model's strong arm (Refit and
 * Honing 10) reads 0.638 on that window and 0.643 on the gate.
 */
class ApexArchitectPrismDefenceTest {
    @Test
    fun theApexShieldsThePrismWithinItsBand() {
        val breach = defensiveBandBreach(CoreShape.PRISM)
        assertTrue(breach == null, "Apex benefit: ${listOfNotNull(breach)}")
    }
}
