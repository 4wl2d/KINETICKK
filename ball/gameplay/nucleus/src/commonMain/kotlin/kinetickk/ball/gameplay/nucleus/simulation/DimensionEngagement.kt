// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.foundation.collections.immutableListOf

/*
 * Dimension visits as an engagement (the dimensions lane replaces this file). Inert until the lane lands: the run
 * never leaves realspace.
 */

/** True while the Core is inside a dimension visit. */
internal fun MutableGameState.dimensionVisitActive(): Boolean = false

/** True once the run entered any dimension. */
internal fun MutableGameState.dimensionTouched(): Boolean = false

/** Appends optional-site marks of non-window portals; returns the new mark count. */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.appendDimensionMarks(out: FloatArray, count: Int): Int = count

/** The trial panel of an active dimension objective, or null. */
internal fun MutableGameState.dimensionTrialProjection(): TrialProjection? = null

/** A new run starts in realspace without boons. */
internal fun MutableGameState.resetDimensions() {
    runBoons = immutableListOf()
}

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.rebaseDimensions(dx: Float, dy: Float) = Unit

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.updateDimension(delta: Float) = Unit

/** Returns the Core to realspace before the Architect arrives. */
internal fun MutableGameState.forceRealspaceBeforeArchitect() = Unit

@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.onDimensionEnemyKilled(enemy: Enemy) = Unit

internal fun MutableGameState.hasPendingDimensionReward(): Boolean = false

internal fun MutableGameState.openPendingDimensionReward() = Unit

/** True while the open choice is a dimension reward (it cannot be rerolled). */
internal fun MutableGameState.dimensionRewardOpen(): Boolean = false
