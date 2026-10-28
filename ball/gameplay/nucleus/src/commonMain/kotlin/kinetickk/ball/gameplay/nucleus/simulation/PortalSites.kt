// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.SiteState

/*
 * Rifts offered by POI windows (the dimensions lane replaces this file). Inert until the lane lands: no window is
 * ever claimed for a rift.
 */

/** A window whose [side] becomes the rift into [target]. */
internal data class PortalClaim(val target: WorldLayer, val side: Int)

/** The rift a due window offers, if the window is claimed for one (stateless; no stream draw). */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.claimPortalForWindow(offerId: Int, scheduledAt: Float): PortalClaim? = null

/** The Core entered an offered rift: true when the visit starts. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.activatePortalSite(site: SiteState): Boolean = false

/** True while a rift kind may not be entered. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.portalLocked(kind: SiteKind): Boolean = false

/** Opens the glitch rift into the Archive at ([x], [y]); true when it opened. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.openArchiveRiftAt(x: Float, y: Float): Boolean = false
