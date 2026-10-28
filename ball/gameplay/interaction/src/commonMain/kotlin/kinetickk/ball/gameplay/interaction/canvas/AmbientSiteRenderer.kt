// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.TextMeasurer

// Ambient sites (caches, secrets). None is placed yet, so every entry point draws nothing.

/** Ambient sites in view, after the window sites. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawAmbientSites(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) = Unit

/** Edge markers of ambient sites within their reveal distance, last of the frame's markers. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.collectAmbientSiteEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) = Unit
