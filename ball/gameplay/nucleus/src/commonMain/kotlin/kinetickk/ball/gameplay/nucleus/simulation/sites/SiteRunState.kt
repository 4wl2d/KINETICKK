// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.sites

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState

/**
 * Window, ambient and secret sites, queued directed rewards and floor offers, run secrets and the Golden Drifter and
 * Spin Cycle trackers. Written by the sites domain (directed rewards also resolve in progression); positions follow a
 * rebase.
 */
internal class SiteRunState(source: SiteRunState?) {
    /** Window sites: the two offers of a window, or the one active trial. Immutable, replaced on change. */
    internal var sites: List<SiteState> = source?.sites ?: emptyList()
    internal var nextSiteWindowIndex: Int = source?.nextSiteWindowIndex ?: 0
    internal var pendingDirectedRewards: List<kinetickk.ball.content.api.DirectedReward> = source?.pendingDirectedRewards ?: emptyList()
    internal var directedReward: kinetickk.ball.content.api.DirectedReward? = source?.directedReward
    internal var selectedRewardFocus: kinetickk.ball.content.api.RewardFocus? = source?.selectedRewardFocus
    internal var ambientSites: AmbientSiteWindow = source?.ambientSites ?: AmbientSiteWindow.EMPTY
    internal var secretSigil: SiteState? = source?.secretSigil
    /** Ambient sites opened this run, oldest first, at most [ContentBounds.MAX_OPENED_SITES]. */
    internal var openedSiteIds: List<Long> = source?.openedSiteIds ?: emptyList()
    /** [RunSecretFlag] bits of this run. */
    internal var runSecretFlags: Int = source?.runSecretFlags ?: 0
    /** Rarity-floor item offers waiting, at most the site policy's bound. */
    internal var pendingFloorOffers: List<ItemRarity> = source?.pendingFloorOffers ?: emptyList()
    /** The Golden Drifter alive (−1: none) and the run second it escapes. */
    internal var goldenDrifterId: Int = source?.goldenDrifterId ?: -1
    internal var goldenDrifterUntil: Float = source?.goldenDrifterUntil ?: 0f
    /** Spin Cycle: completed one-second buckets of signed rotation, the running bucket, its clock and last angle. */
    internal var spinBuckets: List<Float> = source?.spinBuckets ?: emptyList()
    internal var spinCurrent: Float = source?.spinCurrent ?: 0f
    internal var spinClock: Float = source?.spinClock ?: 0f
    internal var spinLastAngle: Float = source?.spinLastAngle ?: 0f
    internal var spinPrimed: Boolean = source?.spinPrimed ?: false
}
