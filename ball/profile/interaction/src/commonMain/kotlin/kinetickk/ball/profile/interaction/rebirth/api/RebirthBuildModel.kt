// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.api

import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** One piece of Rebirth-gated build content: it joins the run pools from [tier] on. */
sealed interface RebirthUnlock {
    val tier: Int
    val name: String

    data class Relic(val definition: RelicDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }

    data class Item(val definition: ItemDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }
}

/**
 * What Rebirth changes for run builds: the Reborn card chance of the current and the target tier
 * (0..1), every Rebirth-gated Relic and item ordered by tier, and the Relics stored as Heirlooms at
 * the last Architect victory (in the profile's stored order).
 */
data class RebirthBuildModel(
    val currentRebornChance: Float,
    val nextRebornChance: Float,
    val unlocks: ImmutableList<RebirthUnlock> = immutableListOf(),
    val heirlooms: ImmutableList<RelicDefinition> = immutableListOf(),
) {
    init {
        require(currentRebornChance.isFinite() && currentRebornChance in 0f..1f)
        require(nextRebornChance.isFinite() && nextRebornChance in 0f..1f)
    }

    /** Gated content that joins the pools exactly at [tier]. */
    fun unlocksAt(tier: Int): List<RebirthUnlock> = unlocks.filter { it.tier == tier }
}
