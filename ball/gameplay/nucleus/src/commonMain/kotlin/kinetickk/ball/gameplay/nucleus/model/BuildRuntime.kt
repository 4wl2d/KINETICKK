// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.WorldEventKind

/**
 * An immutable timed modifier granted by a clause, skill, reaction or Apex; stacks up to its bound. One
 * buff per [sourceKey] (see [BuffSource]) and stat.
 */
internal data class ActiveBuff(
    val sourceKey: Int,
    val stat: BuffStat,
    val amountPerStack: Float,
    val stacks: Int,
    val expiresAt: Float,
) {
    val amount: Float get() = amountPerStack * stacks
}

/** A replaced relic whose liability lingers until [until]. */
internal data class SeveredRelic(val id: RelicId, val until: Float)

/**
 * Keys of what granted a buff or a weapon refresh: items by id, then disjoint negative ranges (world events from
 * −300, signal options from −320; −400 and below belong to dimension boons).
 */
internal object BuffSource {
    const val APEX: Int = -200

    fun item(itemId: Int): Int = itemId
    fun skill(id: SkillId): Int = -1 - id.ordinal
    fun reaction(id: ReactionId): Int = -100 - id.ordinal
    fun worldEvent(kind: WorldEventKind): Int = -300 - kind.ordinal
    fun signal(option: SignalOption): Int = -320 - option.ordinal
}
