// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS

/**
 * Profile progress awaiting publication: banked Matter, the cleared Rebirth tier, achievement counters, first
 * acquisitions and the Matter already banked this run. Drained by the progression domain's progress update.
 */
internal class ProgressOutbox(source: ProgressOutbox?) {
    internal var pendingBankedMatter: Long = source?.pendingBankedMatter ?: 0L
    internal var pendingClearedRebirthLevel: Int? = source?.pendingClearedRebirthLevel
    internal var pendingEliteKills: Int = source?.pendingEliteKills ?: 0
    internal var pendingDashHits: Int = source?.pendingDashHits ?: 0
    internal var pendingCompletedOrbits: Int = source?.pendingCompletedOrbits ?: 0
    internal var pendingArchitectDefeatedWith: CoreShape? = source?.pendingArchitectDefeatedWith
    /**
     * Cumulative achievement counters awaiting publication. They ride along with any other progress
     * update and are otherwise published once the run banks (exit, death, victory), so a kill or a
     * pickup never forces a Profile write on its own.
     */
    internal var pendingTotalKills: Int = source?.pendingTotalKills ?: 0
    /** Hard obstacle bounces awaiting publication; the obstacle system counts them. */
    internal var pendingRicochets: Int = source?.pendingRicochets ?: 0
    internal var pendingDataPickups: Int = source?.pendingDataPickups ?: 0
    /** The run banked, so the next progress update also publishes the deferred counters. */
    internal var achievementFlushDue: Boolean = source?.achievementFlushDue ?: false
    /**
     * First acquisitions not yet reported to the Profile's recent-item history, oldest first; immutable and replaced on
     * change, at most [MAX_RECENT_ITEM_IDS] (the oldest drop first).
     */
    internal var pendingAcquiredItemIds: List<Int> = source?.pendingAcquiredItemIds ?: emptyList()
    /** Run Matter already banked; later grants in the same run bank only the difference. */
    internal var bankedRunMatter: Long = source?.bankedRunMatter ?: 0L
}
