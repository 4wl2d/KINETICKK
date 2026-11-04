// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Brings [boss] to the next gate with a hit, starting its next phase (and its shift). */
internal fun MutableGameState.crossGate(boss: Enemy) {
    val gate = bossGate(boss, assertNotNull(encounterSpec(boss)))
    boss.hp = gate + 5f
    damageEnemy(boss, 20f)
    assertTrue(isShifting(boss), "${boss.type} reached its gate")
}

/** Enemy steps with the encounter step after them (phase starts, enrage notices), as the run orders them. */
internal fun MutableGameState.runFor(seconds: Float) {
    var left = seconds
    while (left > 1e-6f) {
        elapsed += MutableGameState.FIXED_STEP
        updateEnemies(MutableGameState.FIXED_STEP)
        updateBossEncounter()
        left -= MutableGameState.FIXED_STEP
    }
}

/** Steps until [done] holds, at most [limit] seconds. */
internal fun MutableGameState.runUntil(limit: Float = 5f, done: () -> Boolean) {
    var seconds = 0f
    while (!done() && seconds < limit) {
        runFor(MutableGameState.FIXED_STEP)
        seconds += MutableGameState.FIXED_STEP
    }
    assertTrue(done(), "reached within $limit seconds")
}
