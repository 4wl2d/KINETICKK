// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.ApexArchitectFightHarness.defensiveBandBreach
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"): the Diamond's defensive band
 * ([ApexArchitectFightHarness.defensiveBandBreach]; the Prism's and the Diamond's bands are separate tests so that a
 * 4-vCPU CI job plays only one of them, see [ApexArchitectOffenceTest]). On the fixed seed list (64 runs) it passed
 * with probability 0.99 on the 05885fa9 base and 0.58 with the active items. On the 0.6.0 base its ratio is 0.695
 * (run-bootstrap SE 0.035), inside the design's band (base ± 2 SE 0.62-0.77).
 */
class ApexArchitectDiamondDefenceTest {
    @Test
    fun theApexShieldsTheDiamondWithinItsBand() {
        val breach = defensiveBandBreach(CoreShape.DIAMOND)
        assertTrue(breach == null, "Apex benefit: ${listOfNotNull(breach)}")
    }
}
