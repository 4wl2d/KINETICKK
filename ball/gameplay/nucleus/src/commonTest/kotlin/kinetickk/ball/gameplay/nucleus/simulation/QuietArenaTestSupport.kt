// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.foundation.collections.immutableListOf

/**
 * Silences every optional and involuntary world system an isolated encounter must not meet; nothing else
 * changes. Parking the window index ends the offer schedule before it advances, and parking the signal slot ends
 * the signal schedule.
 */
internal fun MutableGameState.quietArenaForTesting() {
    sites = emptyList()
    nextSiteWindowIndex = Int.MAX_VALUE / 2
    ambientSites = AmbientSiteWindow.EMPTY
    secretSigil = null
    goldenDrifterId = -1
    worldEventPlans = immutableListOf()
    worldEvent = null
    eventSignals = immutableListOf()
    nextSignalSlot = Int.MAX_VALUE / 2
    pendingSignalPrompts = immutableListOf()
    endAllEncounters(despawn = true)
    check(worldLayer == WorldLayer.BASE) { "quiet arenas run in realspace" }
}
