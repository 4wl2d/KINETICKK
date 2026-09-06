// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.SynergyId

internal enum class SynergyEffectKind { ECHO, DECAY, ANCHOR, TRAIL, GHOST_EDGE }

/** Immutable retained combat fact: every effect keeps the exact synergy that authorizes it. */
internal data class SynergyEffect(
    val synergy: SynergyId,
    val kind: SynergyEffectKind,
    val remaining: Float,
    val damage: Float = 0f,
    val enemyId: Int = -1,
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float = x,
    val endY: Float = y,
    val radius: Float = 100f,
)
