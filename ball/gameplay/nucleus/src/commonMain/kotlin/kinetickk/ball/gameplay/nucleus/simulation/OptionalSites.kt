// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.EngagementSecretMask
import kinetickk.ball.gameplay.nucleus.model.SiteState

/** Most opt-in site marks [optionalSiteMarks] writes in one call. */
internal const val MAX_OPTIONAL_SITE_MARKS: Int = 64

/** Activation radius of a point-of-interest window offer (the circle `updateSites` tests). */
internal const val POI_ACTIVATION_RADIUS: Float = 65f

/**
 * Writes `(x, y, radius)` triples of every opt-in site into [out] for pilots that must not opt in, and returns how many
 * marks it wrote (at most [MAX_OPTIONAL_SITE_MARKS]): window offers, the ambient sites near the Core, the sigil, event
 * signals, structures, then dimension portals. Allocation-free.
 */
internal fun MutableGameState.optionalSiteMarks(out: FloatArray): Int {
    require(out.size >= MAX_OPTIONAL_SITE_MARKS * 3) { "An optional-site buffer holds $MAX_OPTIONAL_SITE_MARKS marks" }
    var count = appendSiteMarks(out, 0, sites)
    count = appendAmbientSiteMarks(out, count)
    if (inBaseLayer()) secretSigil?.let { count = appendSiteMark(out, count, it) }
    count = appendEventSignalMarks(out, count).coerceAtMost(MAX_OPTIONAL_SITE_MARKS)
    count = appendStructureMarks(out, count).coerceAtMost(MAX_OPTIONAL_SITE_MARKS)
    count = appendDimensionMarks(out, count).coerceAtMost(MAX_OPTIONAL_SITE_MARKS)
    return count
}

private fun MutableGameState.appendSiteMarks(out: FloatArray, count: Int, from: List<SiteState>): Int {
    var written = count
    for (index in from.indices) written = appendSiteMark(out, written, from[index])
    return written
}

/** One site's mark: its centre and the activation radius its definition gives. */
private fun MutableGameState.appendSiteMark(out: FloatArray, count: Int, site: SiteState): Int {
    if (count >= MAX_OPTIONAL_SITE_MARKS) return count
    val base = count * 3
    out[base] = site.x
    out[base + 1] = site.y
    out[base + 2] = content.sites.definition(site.kind).activationRadius
    return count + 1
}

/** True once the run opted into any optional site (sticky facts only; callers keep it sticky too). */
internal fun MutableGameState.optionalSitesTouched(): Boolean =
    activeWindowSite() != null || directedReward != null || pendingDirectedRewards.isNotEmpty() ||
        openedSiteIds.isNotEmpty() || (runSecretFlags and EngagementSecretMask) != 0 || secretSigil?.active == true ||
        eventSignalTouched() || structureTouched() || dimensionTouched()
