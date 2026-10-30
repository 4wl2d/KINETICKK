// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.OmenId
import kotlin.test.Test
import kotlin.test.assertTrue

/** Omen arms B (variety.md §11.3; see [omenArmFailures]): Quickened Host, Rich Veins, Amplified Keynote and Volatile Pact against No omen. */
class RunOmenArmsBGateTest {
    @Test
    fun theMiddleFourOmensAreTradesAndPayAsTheyRead() {
        val failures = omenArmFailures(OmenId.entries.subList(4, 8))
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
