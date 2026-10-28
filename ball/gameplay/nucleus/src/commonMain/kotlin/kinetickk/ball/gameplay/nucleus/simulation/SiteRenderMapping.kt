// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapToImmutableList

/**
 * Sites change a few times per second at most: the same list at the same time, or projections that already carry
 * every derived value, keep the previous immutable list.
 */
internal fun MutableGameState.siteProjections(
    reusable: ImmutableList<SiteProjection>?,
    identitySource: MutableGameState?,
): ImmutableList<SiteProjection> {
    if (reusable != null) {
        if (sites === identitySource?.sites && elapsed.sameBitsAs(identitySource.elapsed)) return reusable
        if (reusable.size == sites.size) {
            var same = true
            for (index in sites.indices) {
                if (!siteProjectionMatches(reusable[index], sites[index])) {
                    same = false
                    break
                }
            }
            if (same) return reusable
        }
    }
    return sites.mapToImmutableList { site ->
        SiteProjection(site.kind, content.sites.definition(site.kind).name,
            site.x, site.y, site.active, siteRemaining(site), site.step, siteProgress(site),
            site.foeIds, site.warningRemaining, site.volleyAngle)
    }
}

/**
 * The trial panel's facts: the first engagement that reports one (a dungeon, then a dimension objective); an equal
 * projection keeps the previous instance.
 */
internal fun MutableGameState.trialProjection(previous: TrialProjection?): TrialProjection? {
    val trial = structureTrialProjection() ?: dimensionTrialProjection() ?: return null
    return if (trial == previous) previous else trial
}

private fun MutableGameState.siteRemaining(site: SiteState): Float =
    if (site.active) site.remaining else (site.expiresAt - elapsed).coerceAtLeast(0f)

private fun MutableGameState.siteProgress(site: SiteState): Float = when (site.kind) {
    SiteKind.RESONANT_CIRCUIT -> site.step / 3f
    SiteKind.SEALED_ANOMALY -> site.count / 3f
    SiteKind.COLLAPSING_ORBIT -> site.meter / content.sites.windows.orbitRequiredSeconds
    // Only the anomalies are offered until the site lane draws kinds by weight.
    SiteKind.GATE_RACE, SiteKind.BRAKELESS_TRIAL, SiteKind.RAM_TRIAL, SiteKind.UNTOUCHED_TRIAL,
    SiteKind.VELOCITY_TRIAL, SiteKind.SLALOM_TRIAL, SiteKind.BOUNTY_ARENA, SiteKind.DRIFT_CACHE,
    SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL, SiteKind.FAR_SIGIL,
    SiteKind.ZENITH_RIFT, SiteKind.NADIR_RIFT,
    -> 0f
}

private fun MutableGameState.siteProjectionMatches(projection: SiteProjection, site: SiteState): Boolean =
    projection.kind == site.kind &&
        projection.x.sameBitsAs(site.x) &&
        projection.y.sameBitsAs(site.y) &&
        projection.active == site.active &&
        projection.remaining.sameBitsAs(siteRemaining(site)) &&
        projection.step == site.step &&
        projection.progress.sameBitsAs(siteProgress(site)) &&
        (projection.foeIds === site.foeIds || projection.foeIds == site.foeIds) &&
        projection.warningRemaining.sameBitsAs(site.warningRemaining) &&
        projection.volleyAngle.sameBitsAs(site.volleyAngle)
