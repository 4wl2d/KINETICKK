// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * Event signals as optional sites (the run variety lane replaces this file). Inert until the lane lands.
 */

/** Appends optional-site marks of live event signals; returns the new mark count. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.appendEventSignalMarks(out: FloatArray, count: Int): Int = count

/** True once the run engaged an event signal (or a prompt waits). */
internal fun MutableGameState.eventSignalTouched(): Boolean = false
