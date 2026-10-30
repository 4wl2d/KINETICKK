// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.SiteState

/*
 * Rifts offered by POI windows. The sites lane draws a window's two kinds first, then asks here whether a rift claims
 * one side; the claim is a stateless hash of the run seed and the window, so it never draws from the site stream.
 */

/** A window whose [side] becomes the rift into [target]. */
internal data class PortalClaim(val target: WorldLayer, val side: Int)

/**
 * The rift a due window offers, if any. A window may host one while rifts are enabled, the run is in realspace, the
 * window is not before the policy's first rift window, a visit entered as its offer closes still ends the exit margin
 * before the boss, and the run has claimed fewer than the policy's rifts. Then the window's hash decides by the rift
 * chance; the target is a window dimension not entered yet (the hash picks when both are open) and the side comes
 * from another bit of the same hash. A claim counts toward the run's rift offers.
 */
internal fun MutableGameState.claimPortalForWindow(offerId: Int, scheduledAt: Float): PortalClaim? {
    val policy = content.dimensions
    if (!policy.enabled || worldLayer != WorldLayer.BASE) return null
    if (!policy.windowMayHostRift(offerId, scheduledAt, content.sites.windows.offerLifetime, content.tempo.bossAtSeconds)) return null
    if (dimensionRun.portalOffers >= policy.maxPortalOffersPerRun) return null
    val hash = worldHash(runSeed, WorldLayer.BASE, offerId, 0, PORTAL_WINDOW_SALT)
    if (hashUnit(hash) >= policy.portalWindowChance) return null
    val zenithOpen = dimensionRun.visitedMask and WorldLayer.ZENITH.bit == 0
    val nadirOpen = dimensionRun.visitedMask and WorldLayer.NADIR.bit == 0
    val target = when {
        zenithOpen && nadirOpen -> if ((hash ushr 33) and 1L == 0L) WorldLayer.ZENITH else WorldLayer.NADIR
        zenithOpen -> WorldLayer.ZENITH
        nadirOpen -> WorldLayer.NADIR
        else -> return null
    }
    dimensionRun = dimensionRun.copy(portalOffers = dimensionRun.portalOffers + 1)
    return PortalClaim(target, ((hash ushr 34) and 1L).toInt())
}

/** The Core flew through an offered rift: true when the visit started; a refused rift stays offered. */
internal fun MutableGameState.activatePortalSite(site: SiteState): Boolean {
    val target = site.kind.riftTarget() ?: return false
    return enterDimension(target)
}

/** True while a rift kind may not be entered (its HATCH look); false for every other kind. */
internal fun MutableGameState.portalLocked(kind: SiteKind): Boolean {
    val target = kind.riftTarget() ?: return false
    return !canEnterDimension(target)
}

/** The dimension a window rift kind leads into, or null for a kind that is no rift. */
internal fun SiteKind.riftTarget(): WorldLayer? = when (this) {
    SiteKind.ZENITH_RIFT -> WorldLayer.ZENITH
    SiteKind.NADIR_RIFT -> WorldLayer.NADIR
    SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT, SiteKind.GATE_RACE,
    SiteKind.BRAKELESS_TRIAL, SiteKind.RAM_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL,
    SiteKind.SLALOM_TRIAL, SiteKind.BOUNTY_ARENA, SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER,
    SiteKind.QUIET_SIGNAL, SiteKind.FAR_SIGIL,
    -> null
}
