// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy

/*
 * Site-owned enemies (the sites lane replaces this file): an enemy a site moves itself, such as the Golden Drifter.
 * Inert until the lane lands.
 */

/** Moves [enemy] when a site owns it and returns true, so its kind's behaviour is skipped this step. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateSiteOwnedEnemy(enemy: Enemy, delta: Float): Boolean = false
