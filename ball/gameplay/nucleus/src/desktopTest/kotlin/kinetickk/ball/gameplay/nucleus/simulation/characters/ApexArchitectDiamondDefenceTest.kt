// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.characters.ApexArchitectFightHarness.defensiveBandBreach
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the Diamond's defensive band
 * ([ApexArchitectFightHarness.defensiveBandBreach]; the Prism's and the Diamond's bands are separate tests so that a
 * 4-vCPU CI job plays only one of them, see [ApexArchitectOffenceTest]). On the fixed seed list (64 runs) it passed
 * with probability 0.99 on the 05885fa9 base and 0.58 with the active items. On the Elite-resonance base its ratio is
 * 0.567 (run-bootstrap SE 0.032; 0.600 on the tree before it and 0.695 on the 0.6.0 base), inside the design's band
 * by half a standard error (base ± 2 SE 0.50-0.63; a resampled population lands in the band with probability 0.71).
 * The band is a design target and never weakens.
 */
class ApexArchitectDiamondDefenceTest {
    @Test
    fun theApexShieldsTheDiamondWithinItsBand() {
        val breach = defensiveBandBreach(CoreShape.DIAMOND)
        assertTrue(breach == null, "Apex benefit: ${listOfNotNull(breach)}")
    }
}
