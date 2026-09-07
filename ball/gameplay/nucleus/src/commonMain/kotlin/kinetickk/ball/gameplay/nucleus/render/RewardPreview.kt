// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.content.api.RelicId

/** Detached reads of the offered change, in effective display units. */
data class RewardStatChange(
    val name: String,
    val before: Float,
    val after: Float,
    val unit: String,
    val lowerIsBetter: Boolean = false,
    val sourceRelic: RelicId? = null,
)

data class RewardPreview(
    val changes: ImmutableList<RewardStatChange> = immutableListOf(),
    val addedSynergies: ImmutableList<String> = immutableListOf(),
    val removedSynergies: ImmutableList<String> = immutableListOf(),
    val requiresSlot: Boolean = false,
)
