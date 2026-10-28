// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy

/*
 * Elite affixes and champions (the run variety lane replaces this file). Inert until the lane lands: no enemy rolls
 * an affix, and every factor is the identity.
 */

/** Rolls a new enemy's affix or champion flag (stateless; no stream draw). */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.rollEliteVariety(enemy: Enemy) = Unit

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.affixSpeedFactor(enemy: Enemy): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.affixCadence(enemy: Enemy): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.onAffixedEnemyKilled(enemy: Enemy) = Unit
