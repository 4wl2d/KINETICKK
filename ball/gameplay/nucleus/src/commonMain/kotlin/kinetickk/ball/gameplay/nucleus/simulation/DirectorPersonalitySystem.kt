// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.EnemyType

/*
 * Director personalities and onslaught themes (the run variety lane replaces this file). Every scale is the
 * identity until the lane lands, so the director paces exactly as before.
 */

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.personalityDurationScale(phase: SpawnDirectorPhase): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.personalityRateScale(phase: SpawnDirectorPhase): Float = 1f

internal fun MutableGameState.personalityBudgetScale(): Float = 1f

/** Scale of the [SpawnEventKind] with ordinal [kind] in the event roll. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.personalityEventScale(kind: Int): Float = 1f

internal fun MutableGameState.personalityPackBonus(): Int = 0

internal fun MutableGameState.ambushCooldownScale(): Float = 1f

internal fun MutableGameState.encircleCooldownScale(): Float = 1f

internal fun MutableGameState.wallCooldownScale(): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.personalityTypeMultiplier(type: EnemyType): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.surgeThemeMultiplier(type: EnemyType): Float = 1f

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.onDirectorPhaseStarted(phase: SpawnDirectorPhase) = Unit
