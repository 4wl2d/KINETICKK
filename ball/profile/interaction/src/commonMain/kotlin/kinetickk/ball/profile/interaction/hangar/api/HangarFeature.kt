// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * One character in the Hangar roster: its content [definition], the Profile's unlock state
 * ([progress] of [target], capped) and its trait bars normalized over the roster (0..1).
 */
data class HangarShapeRenderModel(
    val definition: CoreShapeDefinition,
    val unlocked: Boolean,
    val progress: Long,
    val target: Long,
    val massBar: Float,
    val integrityBar: Float,
    val handlingBar: Float,
    val heatBar: Float,
    /** This character cleared the final Rebirth tier: its tile and details carry the gold crown. */
    val ascendant: Boolean = false,
) {
    val shape: CoreShape get() = definition.id
}

/**
 * A weapon as the Hangar's signature row shows it: whether the Profile owns it (or starts runs
 * with it), whether its Rebirth gate is above the profile's rank, and the heirloom candidates
 * that carry its Relic key ([harmonizing], in heirloom slot order).
 */
data class HangarWeaponRenderModel(
    val definition: WeaponDefinition,
    val owned: Boolean,
    val starter: Boolean,
    val rebirthLocked: Boolean,
    val harmonizing: ImmutableList<RelicDefinition> = immutableListOf(),
)

/** Small immutable payload rendered by the Hangar feature. */
data class HangarRenderModel(
    val shapes: ImmutableList<HangarShapeRenderModel>,
    /** The character the next run starts with. */
    val selected: CoreShape,
    val matter: Long,
    /** Every weapon in catalog order, for each character's signature row. */
    val weapons: ImmutableList<HangarWeaponRenderModel> = immutableListOf(),
    /** Every Rebirth tier is cleared: the header adds the Ascendant title. */
    val ascendant: Boolean = false,
) {
    val unlockedCount: Int get() = shapes.count { it.unlocked }

    fun shape(id: CoreShape): HangarShapeRenderModel? = shapes.firstOrNull { it.shape == id }

    /** The weapons whose signature character is [id] (two per character in the shipped catalog). */
    fun signatureWeapons(id: CoreShape): List<HangarWeaponRenderModel> = weapons.filter { it.definition.signature == id }
}

sealed interface HangarOutput {
    data object Back : HangarOutput
}

interface HangarFeature {
    @Composable
    fun Content(
        routeToken: Long,
        onOutput: (HangarOutput) -> Unit,
    )
}
