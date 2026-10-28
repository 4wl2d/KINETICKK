// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.SiteState

/** One engagement at a time: a window trial, a secret duel, a dungeon or a dimension visit. */
internal fun MutableGameState.engagementActive(): Boolean =
    activeWindowSite() != null || secretSigil?.active == true || structureEngagementActive() || dimensionVisitActive()

/** The window site whose trial runs, or null. */
internal fun MutableGameState.activeWindowSite(): SiteState? {
    for (index in sites.indices) if (sites[index].active) return sites[index]
    return null
}

/** Enemies an active site engagement owns: leash-exempt, never evicted. */
internal fun MutableGameState.engagementFoeIds(): List<Int>? =
    activeWindowSite()?.foeIds ?: secretSigil?.takeIf { it.active }?.foeIds

/** True when [enemy] belongs to a place (an encounter or an active site) and stays there. */
internal fun MutableGameState.isPlaceBound(enemy: Enemy): Boolean =
    enemy.ownerTag != 0L || engagementFoeIds()?.contains(enemy.id) == true
