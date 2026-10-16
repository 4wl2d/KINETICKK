// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.StatusElement

internal enum class CombatFieldKind { FUSION, ROT_WELL, DARK_STAR, ECHO_RING }

/**
 * Immutable lingering area effect. Fields pull their occupants and pulse damage (and optionally a status)
 * every [TICK_SECONDS]; the list is bounded by the build policy.
 */
internal data class CombatField(
    val kind: CombatFieldKind,
    val remaining: Float,
    val x: Float,
    val y: Float,
    val radius: Float,
    val damagePerSecond: Float,
    val element: StatusElement? = null,
    val stacksPerTick: Int = 0,
    val generation: Int = 1,
    val pull: Float = 0f,
    val tickClock: Float = 0f,
) {
    companion object {
        const val TICK_SECONDS: Float = 0.4f
    }
}
