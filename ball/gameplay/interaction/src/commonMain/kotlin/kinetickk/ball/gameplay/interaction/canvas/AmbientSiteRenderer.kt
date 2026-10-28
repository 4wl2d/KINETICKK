// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.TextMeasurer
import kotlin.math.sqrt

/** Half extent of an ambient site's mark (a cache crate, the quiet signal's arcs). */
private const val AMBIENT_MARK_HALF = 40f

/** Ambient sites in view, after the window sites. None is placed yet, so nothing is drawn. */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawAmbientSites(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) = Unit

/**
 * Edge markers of ambient sites within their reveal distance whose mark is not shown, last of the frame's markers:
 * the batch drops them first when it is full. Easter eggs reveal nothing.
 */
internal fun DrawScope.collectAmbientSiteEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val ambient = engine.ambientSites
    val scale = engine.worldScale
    for (index in ambient.indices) {
        val point = ambient[index]
        if (!revealed(engine, point)) continue
        val target = world(engine, point.x, point.y, 0f, 0f)
        if (markShown(target, AMBIENT_MARK_HALF, WorldOverlayScratch.keepOut, scale)) continue
        val dx = point.x - engine.coreX
        val dy = point.y - engine.coreY
        batch.add(target.x * scale, target.y * scale, sqrt(dx * dx + dy * dy), point.kind.edgeIcon())
    }
}
