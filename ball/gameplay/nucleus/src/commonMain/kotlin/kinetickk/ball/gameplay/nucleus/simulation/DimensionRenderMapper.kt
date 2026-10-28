// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.DimensionProjection

/**
 * The world layer and its visit as presentation draws them. No rift opens yet, so every run stays in realspace and
 * the projection is always the shared [DimensionProjection.REALSPACE].
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.dimensionProjection(
    previous: DimensionProjection?,
    identitySource: MutableGameState?,
): DimensionProjection = DimensionProjection.REALSPACE
