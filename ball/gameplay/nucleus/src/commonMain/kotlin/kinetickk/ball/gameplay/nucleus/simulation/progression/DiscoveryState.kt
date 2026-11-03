// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.RunMutableSet
import kinetickk.ball.profile.api.GameplayProfileSnapshot

/**
 * Discoveries known at bootstrap plus this run's (items, Relics, reactions, pairings, world entries) and the ones not
 * yet published to the Profile. Each kind is discovered by its own domain; progression publishes and clears the pending
 * ones.
 */
internal class DiscoveryState(
    content: GameplayContentSnapshot,
    source: DiscoveryState?,
    bootstrapProgress: GameplayProfileSnapshot?,
) {
    internal val discoveredItemIds: RunMutableSet<Int> = source?.discoveredItemIds?.copy()
        ?: RunMutableSet(
            bootstrapProgress?.collection?.discoveredItemIds
                ?.filterTo(mutableSetOf()) { content.item(it) != null }
                ?: mutableSetOf(),
        )
    internal var discoveredRelicMask: Long = source?.discoveredRelicMask
        ?: bootstrapProgress?.collection?.discoveredRelicIds?.fold(0L) { mask, id -> mask or (1L shl id.ordinal) } ?: 0L
    internal var pendingDiscoveredRelicMask: Long = source?.pendingDiscoveredRelicMask ?: 0L
    internal var pendingDiscoveredItemIdStorage: RunMutableSet<Int>? =
        source?.pendingDiscoveredItemIdStorage?.copy()
    internal var discoveredReactionMask: Int = source?.discoveredReactionMask
        ?: bootstrapProgress?.collection?.discoveredReactionIds?.fold(0) { mask, id -> mask or (1 shl id.ordinal) } ?: 0
    internal var pendingDiscoveredReactionMask: Int = source?.pendingDiscoveredReactionMask ?: 0
    internal var discoveredPairingMask: Int = source?.discoveredPairingMask
        ?: bootstrapProgress?.collection?.discoveredPairingIds?.fold(0) { mask, id -> mask or (1 shl id.ordinal) } ?: 0
    internal var pendingDiscoveredPairingMask: Int = source?.pendingDiscoveredPairingMask ?: 0
    /** World discoveries known at start plus this run's; the pending mask holds the ones not yet published. */
    internal var worldDiscoveryMask: Int = source?.worldDiscoveryMask
        ?: bootstrapProgress?.collection?.discoveredWorldEntries?.fold(0) { mask, id -> mask or id.bit } ?: 0
    internal var pendingWorldDiscoveryMask: Int = source?.pendingWorldDiscoveryMask ?: 0
}
