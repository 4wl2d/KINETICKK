// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.api

import androidx.compose.runtime.Composable
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.foundation.collections.ImmutableList

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
) {
    val shape: CoreShape get() = definition.id
}

/** Small immutable payload rendered by the Hangar feature. */
data class HangarRenderModel(
    val shapes: ImmutableList<HangarShapeRenderModel>,
    /** The character the next run starts with. */
    val selected: CoreShape,
    val matter: Long,
) {
    val unlockedCount: Int get() = shapes.count { it.unlocked }

    fun shape(id: CoreShape): HangarShapeRenderModel? = shapes.firstOrNull { it.shape == id }
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
