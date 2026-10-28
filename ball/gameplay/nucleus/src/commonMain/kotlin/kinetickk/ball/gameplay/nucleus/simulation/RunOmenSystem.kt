// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * Run omens (the run variety lane replaces this file). Inert until the lane lands: no run offers an omen.
 */

/** Opens the omen choice; until omens land the pending flag is simply consumed. */
internal fun MutableGameState.openOmenChoice() {
    omenPending = false
}
