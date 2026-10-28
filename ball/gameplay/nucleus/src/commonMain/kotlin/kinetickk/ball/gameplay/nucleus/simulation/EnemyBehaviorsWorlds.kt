// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy

/*
 * Behaviours of the 0.6.0 enemy kinds (the enemies lane replaces this file kind by kind).
 */

/** A 0.6.0 kind whose behaviour has not landed: it steers like a Drifter. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updatePendingKind(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
    steerEnemy(enemy, dx / distance * 78f, dy / distance * 78f, 2.1f, delta)
}
