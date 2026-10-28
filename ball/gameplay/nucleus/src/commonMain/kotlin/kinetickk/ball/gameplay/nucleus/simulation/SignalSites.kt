// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * Event signals as optional sites: pilots that must not opt in steer around live signals, and a run that touched one
 * counts as engaged with optional content.
 */

/** Appends the live signal's mark (its centre and touch radius); returns the new mark count. Allocation-free. */
internal fun MutableGameState.appendEventSignalMarks(out: FloatArray, count: Int): Int {
    var written = count
    val radius = content.variety.signals.touchRadius
    for (index in eventSignals.indices) {
        if (written >= MAX_OPTIONAL_SITE_MARKS) return written
        val signal = eventSignals[index]
        val base = written * 3
        out[base] = signal.x
        out[base + 1] = signal.y
        out[base + 2] = radius
        written++
    }
    return written
}

/** True once the run touched an event signal or a signal prompt waits. */
internal fun MutableGameState.eventSignalTouched(): Boolean = signalsEngaged > 0 || pendingSignalPrompts.isNotEmpty()
