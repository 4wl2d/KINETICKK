// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.OmenId
import kotlin.test.Test
import kotlin.test.assertTrue

/** Omen arms C (variety.md §11.3; see [omenArmFailures]): Fault Line, Ascetic Path, Crowned Quarry and Still Water against No omen. */
class RunOmenArmsCGateTest {
    @Test
    fun theLastFourOmensAreTradesAndPayAsTheyRead() {
        val failures = omenArmFailures(OmenId.entries.subList(8, 12))
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
