// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.SitePolicy

/** Cross-checks of the site policy against the rest of the catalog (each policy validates itself on construction). */
internal fun validateSites(sites: SitePolicy, enemies: List<EnemyDefinition>) {
    val boss = enemies.firstOrNull { it.type == sites.bounty.bossType }
    require(boss != null && boss.isMiniBoss) { "The bounty target ${sites.bounty.bossType} must be a mini-boss" }
    // The bounty boss is an encounter member, and the Herald keeps its own schedule outside every encounter.
    require(boss.type != EnemyType.HERALD) { "The bounty target must not be the Herald, which is never an encounter boss" }
}
