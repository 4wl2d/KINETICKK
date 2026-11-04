// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.WorldEventKind

/** One ambient world event planned at run start: telegraphed, then active from [startsAt] until [endsAt]. */
internal data class WorldEventPlan(val kind: WorldEventKind, val telegraphAt: Float, val startsAt: Float, val endsAt: Float)

/** An event signal the Core touched, waiting for its prompt to open. */
internal data class SignalPrompt(val kind: SignalKind, val signalIndex: Int)
