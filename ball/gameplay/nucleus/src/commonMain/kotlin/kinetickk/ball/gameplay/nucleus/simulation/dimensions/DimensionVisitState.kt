// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.dimensions

import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState

/**
 * The dimension run (rifts, visited layers, the visit and its rewards), the visit's heat, scorch and local clocks and
 * the layer whose reward the open choice is. Written by the dimensions domain.
 */
internal class DimensionVisitState(source: DimensionVisitState?) {
    /** Rift offers, visited and completed layers, the open glitch rift, the visit and its pending rewards; a value. */
    internal var dimensionRun: DimensionRunState = source?.dimensionRun ?: DimensionRunState.EMPTY
    /** Seconds the Core has flown below the layer's pace without a break (Nadir heat). */
    internal var dimensionSlowSeconds: Float = source?.dimensionSlowSeconds ?: 0f
    /** Seconds toward the next scorch tick once the heat grace ran out. */
    internal var dimensionScorchClock: Float = source?.dimensionScorchClock ?: 0f
    /** Visit time on the layer's own clock (the Archive's runs with the Core's speed). */
    internal var dimensionLocalClock: Float = source?.dimensionLocalClock ?: 0f
    /** The layer whose reward the open choice is (null: the open choice is no dimension reward). */
    internal var dimensionRewardLayer: WorldLayer? = source?.dimensionRewardLayer
}
