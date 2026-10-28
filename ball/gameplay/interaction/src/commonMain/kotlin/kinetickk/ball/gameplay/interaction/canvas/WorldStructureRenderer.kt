// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

// Structures and dungeons. Nothing places a structure yet, so every entry point draws nothing.

/** Room floor plates of the structures in view, under the obstacles. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawStructureFloors(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit

/** Wall slabs and doors of the structures in view, right after their floors. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawStructureWalls(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit

/** The ink scrim outside the dungeon the Core is inside, over the Core and under the singularity. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawStructureScrim(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) = Unit

/** Edge markers of structure entrances off screen (at most two), after the dimension markers. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.collectStructureEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) = Unit

/** Walls, sealed doors and entrances on the minimap, inside its clip, after the obstacles. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawMinimapStructures(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) = Unit

/** The HUD vignette while the Core is inside a dungeon. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDungeonVignette(engine: GameplayRenderModel) = Unit
