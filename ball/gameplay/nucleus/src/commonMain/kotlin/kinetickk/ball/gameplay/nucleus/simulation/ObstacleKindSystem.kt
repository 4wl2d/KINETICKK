// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ObstacleMix
import kinetickk.ball.content.api.WorldLayer

/** The obstacle kind mix of the run's current world layer (density and bumper scales included). */
internal fun MutableGameState.currentObstacleMix(): ObstacleMix {
    val kinds = content.obstacles.kinds
    return when (worldLayer) {
        WorldLayer.BASE -> kinds.space
        WorldLayer.ZENITH -> kinds.zenith
        WorldLayer.NADIR -> kinds.nadir
        WorldLayer.ARCHIVE -> kinds.archive
    }
}

/** No obstacle (obstacle ids are non-negative). */
internal const val NO_OBSTACLE_ID: Long = -1L
