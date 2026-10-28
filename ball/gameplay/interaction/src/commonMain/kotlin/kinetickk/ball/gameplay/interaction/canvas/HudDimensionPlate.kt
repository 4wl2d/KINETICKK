// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.TextMeasurer

// Dimensions on the HUD. No rift opens yet, so every entry point draws nothing.

/**
 * The layer sigil and visit timer in the Rebirth strip's slot; true when it drew (the strip then yields its slot).
 * Realspace never draws it.
 */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDimensionPlate(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame): Boolean = false

/** The slash shutter of a layer change, over the screen states. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawDimensionShutter(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) = Unit

/** The Nadir heat edge while the Core is too slow. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawScorchEdge(engine: GameplayRenderModel, roles: KkRolePalette, renderTime: Float) = Unit
