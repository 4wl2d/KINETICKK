// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.api

import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * One piece of content the Rebirth ladder opens at [tier], derived from the content gates: weapons,
 * characters, Relics, items and Matrix nodes open at that Rebirth rank; enemy types join the roster when
 * that tier is selected.
 */
sealed interface RebirthUnlock {
    val tier: Int
    val name: String

    data class Weapon(val definition: WeaponDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }

    /** A character whose unlock target is a Reborn rank. */
    data class Character(val definition: CoreShapeDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.unlockTarget
        override val name: String get() = definition.displayName
    }

    data class Relic(val definition: RelicDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }

    data class Item(val definition: ItemDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }

    data class MatrixNode(val definition: MatrixNodeDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }

    data class Enemy(val definition: EnemyDefinition) : RebirthUnlock {
        override val tier: Int get() = definition.minimumRebirth
        override val name: String get() = definition.name
    }
}

/**
 * What Rebirth changes for run builds: the Reborn card chance of the selected and the focused tier
 * (0..1), every Rebirth-gated piece of content ordered by tier, and the Relics stored as Heirlooms at
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

    /** Gated content that opens exactly at [tier]. */
    fun unlocksAt(tier: Int): List<RebirthUnlock> = unlocks.filter { it.tier == tier }
}
