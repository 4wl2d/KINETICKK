// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.gameplay.api.RunId
import kotlin.test.Test
import kotlin.test.assertTrue

class GameplaySeedSourceTest {
    @Test
    fun compositionHandsRunsVaryingSeeds() {
        val seeds = (0L until 16L).map { varyingGameplaySeed(RunId(it)) }.toSet()
        assertTrue(seeds.size > 8, "Runs should not share one seed: $seeds")
    }
}
