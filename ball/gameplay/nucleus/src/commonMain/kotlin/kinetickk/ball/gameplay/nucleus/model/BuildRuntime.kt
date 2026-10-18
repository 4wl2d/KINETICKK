// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.RelicId

/** An immutable timed modifier granted by a clause; stacks up to the clause's bound. */
internal data class ActiveBuff(
    val sourceItem: Int,
    val stat: BuffStat,
    val amountPerStack: Float,
    val stacks: Int,
    val expiresAt: Float,
) {
    val amount: Float get() = amountPerStack * stacks
}

/** A replaced relic whose liability lingers until [until]. */
internal data class SeveredRelic(val id: RelicId, val until: Float)
