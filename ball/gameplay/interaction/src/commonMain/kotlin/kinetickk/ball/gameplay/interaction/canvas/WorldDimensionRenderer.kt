// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

// Dimensions in the world. No rift opens yet, so every run stays in realspace and every entry point draws nothing.

/** The ground of a dimension, drawn instead of the realspace grid while the Core is in one. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDimensionBackdrop(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) = Unit

/** Rifts, halos and embers of the current layer, after the sites. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDimensionWorld(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) = Unit

/** One rift ring at [center] leading to [target]; the site renderer's rift kinds draw through it too. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawPortal(center: Offset, target: WorldLayer, locked: Boolean, glitch: Boolean, time: Float, roles: KkRolePalette) =
    Unit

/** Edge markers of the exit and glitch rifts and the next halo, after the event signal. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.collectDimensionEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) = Unit

/** Halos, embers and rifts on the minimap, inside its clip, after the structures. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDimensionMinimapMarks(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) = Unit
