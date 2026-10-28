// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

/*
 * Ambient world events and event signals per step (the run variety lane replaces this file). Inert until the lane
 * lands.
 */

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateRunVariety(delta: Float) = Unit

/** A world layer switch ends the event in progress silently. */
internal fun MutableGameState.endWorldEventForLayerChange() = Unit
