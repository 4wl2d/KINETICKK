// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.OmenId
import kotlin.test.Test
import kotlin.test.assertTrue

/** Omen arms A (variety.md §11.3; see [omenArmFailures]): Glass Sun, Iron Tide, Hungry Dark and Crowded Sky against No omen. */
class RunOmenArmsGateTest {
    @Test
    fun theFirstFourOmensAreTradesAndPayAsTheyRead() {
        val failures = omenArmFailures(OmenId.entries.subList(0, 4))
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
