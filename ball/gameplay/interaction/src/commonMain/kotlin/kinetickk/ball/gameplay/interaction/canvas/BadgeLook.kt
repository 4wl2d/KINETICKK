// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.foundation.design.*

/** The run's Apex as the level badge shows it (NONE until it unlocks at the cap). */
internal fun ApexProjection.badgeState(): KkApexState = when {
    !unlocked -> KkApexState.NONE
    active01 > 0f -> KkApexState.ACTIVE
    ready -> KkApexState.READY
    else -> KkApexState.COOLING
}
