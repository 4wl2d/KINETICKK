// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.sites

import kinetickk.ball.gameplay.nucleus.model.EngagementSecretMask
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

/** Most opt-in site marks [optionalSiteMarks] writes in one call. */
internal const val MAX_OPTIONAL_SITE_MARKS: Int = 64

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
    count = appendEventSignalMarks(out, count)
    count = appendStructureMarks(out, count)
    count = appendDimensionMarks(out, count)
    return count
}

/** Writes one `(x, y, radius)` mark after [count] marks and returns the new count; a full buffer is left as it is. */
internal fun writeOptionalSiteMark(out: FloatArray, count: Int, x: Float, y: Float, radius: Float): Int {
    if (count >= MAX_OPTIONAL_SITE_MARKS) return count
    val base = count * 3
    out[base] = x
    out[base + 1] = y
    out[base + 2] = radius
    return count + 1
}

private fun MutableGameState.appendSiteMarks(out: FloatArray, count: Int, from: List<SiteState>): Int {
    var written = count
    for (index in from.indices) written = appendSiteMark(out, written, from[index])
    return written
}

/** One site's mark: its centre and the activation radius its definition gives. */
private fun MutableGameState.appendSiteMark(out: FloatArray, count: Int, site: SiteState): Int =
    writeOptionalSiteMark(out, count, site.x, site.y, content.sites.definition(site.kind).activationRadius)

/** True once the run opted into any optional site (sticky facts only; callers keep it sticky too). */
internal fun MutableGameState.optionalSitesTouched(): Boolean = optionalSitesTouchedOtherThanSignals() || eventSignalTouched()

/** [optionalSitesTouched] without the event-signal clause (a pilot that engages signals by design reads this one). */
internal fun MutableGameState.optionalSitesTouchedOtherThanSignals(): Boolean =
    activeWindowSite() != null || directedReward != null || pendingDirectedRewards.isNotEmpty() ||
        openedSiteIds.isNotEmpty() || (runSecretFlags and EngagementSecretMask) != 0 || secretSigil?.active == true ||
        structureTouched() || dimensionTouched()
