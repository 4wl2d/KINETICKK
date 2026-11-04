// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** The ambient world event in progress (telegraph included); the nucleus stores this immutable value directly. */
data class WorldEventProjection(
    val kind: WorldEventKind,
    val telegraphAt: Float,
    val startsAt: Float,
    val endsAt: Float,
    /** Gravity storm: wind direction and its 0..1 ramp. */
    val windAngle: Float = 0f,
    val windStrength: Float = 0f,
    /** Solar flare: when the current sweep started and its angle. */
    val sweepStartedAt: Float = Float.NEGATIVE_INFINITY,
    val sweepAngle: Float = 0f,
    /** At most MAX_EVENT_STRIKES telegraphed strikes. */
    val strikes: ImmutableList<EventStrikeProjection> = immutableListOf(),
)

data class EventStrikeProjection(val x: Float, val y: Float, val radius: Float, val armedAt: Float, val strikesAt: Float)

data class EventSignalProjection(val id: Int, val kind: SignalKind, val x: Float, val y: Float, val expiresAt: Float)
