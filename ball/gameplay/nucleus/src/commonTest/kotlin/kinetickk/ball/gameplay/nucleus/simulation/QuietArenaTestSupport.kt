// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/**
 * Silences every optional and involuntary world system an isolated encounter must not meet; nothing else
 * changes. Parking the window index ends the offer schedule before it advances.
 */
internal fun MutableGameState.quietArenaForTesting() {
    pointsOfInterest = emptyList()
    nextPointOfferIndex = Int.MAX_VALUE / 2
}
