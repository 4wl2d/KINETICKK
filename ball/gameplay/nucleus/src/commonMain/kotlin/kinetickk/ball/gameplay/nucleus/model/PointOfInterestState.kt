// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

internal data class WorldPoint(val x: Float, val y: Float)

/** Value-only runtime; reducers may safely retain prior snapshots. */
internal data class PointOfInterestState(
    val offerId: Int,
    val kind: PointOfInterestKind,
    val x: Float,
    val y: Float,
    val expiresAt: Float,
    val active: Boolean = false,
    val remaining: Float = 25f,
    val nextBeacon: Int = 1,
    val orbitSeconds: Float = 0f,
    val defenderIds: ImmutableList<Int> = immutableListOf(),
    val defeatedDefenders: Int = 0,
    val volleyClock: Float = 2f,
    val warningRemaining: Float = 0f,
    val volleyAngle: Float = 0f,
) {
    fun beacon(index: Int): WorldPoint = when (index) {
        1 -> WorldPoint(x + 230f, y + 200f)
        2 -> WorldPoint(x - 230f, y + 200f)
        else -> WorldPoint(x, y)
    }
}
