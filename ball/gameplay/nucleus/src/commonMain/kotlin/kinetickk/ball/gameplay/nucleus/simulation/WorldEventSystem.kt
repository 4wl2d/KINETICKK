// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * The run variety's step (the run variety lane fills this file): the run-start announcements, then ambient world
 * events and event signals (inert until they land).
 */

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateRunVariety(delta: Float) {
    announceKeynote()
}

/** A world layer switch ends the event in progress silently. */
internal fun MutableGameState.endWorldEventForLayerChange() = Unit
