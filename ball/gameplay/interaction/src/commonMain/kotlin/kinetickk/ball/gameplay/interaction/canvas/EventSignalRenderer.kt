// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.TextMeasurer

// Event signals. None is placed yet, so every entry point draws nothing.

/** Live event signals in the world, after the world event's ground look. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawEventSignals(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) = Unit

/** The nearest live signal's edge marker, after the Totem's. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.collectSignalEdgeMarker(engine: GameplayRenderModel, batch: EdgeMarkerBatch) = Unit
