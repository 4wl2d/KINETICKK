// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.isOnScreen
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Half extent of an ambient site's mark (a cache crate, the quiet signal's arcs). */
internal const val AMBIENT_MARK_HALF = 40f

/** Size of a drift cache's crate glyph. */
private const val CACHE_GLYPH_SIZE = 44f

/** A drift cache bobs this far up and down, once every [CACHE_BOB_PERIOD] seconds. */
private const val CACHE_BOB = 3f
private const val CACHE_BOB_PERIOD = 24f

/** Ambient marks are drawn while their center lies within this margin of the screen. */
private const val AMBIENT_CULL_MARGIN = 80f

/**
 * Ambient sites in view, after the window sites: a drift cache is a bone crate bobbing gently in place. Within its
 * reveal distance a cache and its edge marker ([collectAmbientSiteEdgeMarkers]) share one predicate in the unshaken
 * view, so exactly one of them draws; beyond it the crate simply draws while on screen.
 */
internal fun DrawScope.drawAmbientSites(
    engine: GameplayRenderModel,
    shakeX: Float,
    shakeY: Float,
    @Suppress("UNUSED_PARAMETER") textMeasurer: TextMeasurer,
) {
    val ambient = engine.ambientSites
    for (index in ambient.indices) {
        val point = ambient[index]
        when (point.kind) {
            SiteKind.DRIFT_CACHE -> drawDriftCache(engine, point, shakeX, shakeY)
            // The easter eggs keep no look of their own here; the Golden Drifter's trigger is never drawn.
            else -> Unit
        }
    }
}

private fun DrawScope.drawDriftCache(engine: GameplayRenderModel, point: SiteProjection, shakeX: Float, shakeY: Float) {
    val center = world(engine, point.x, point.y, shakeX, shakeY)
    if (!isOnScreen(center, AMBIENT_CULL_MARGIN)) return
    if (revealed(engine, point) &&
        !markShown(steady(center, shakeX, shakeY), AMBIENT_MARK_HALF, WorldOverlayScratch.keepOut, engine.worldScale)
    ) return
    // Each cache bobs on its own phase (from its position), so a pair never moves in step.
    val phase = (point.x + point.y) * 0.001f
    val bob = sin(engine.renderElapsed * (2f * PI.toFloat() / CACHE_BOB_PERIOD) + phase) * CACHE_BOB
    drawSiteGlyph(SiteGlyph.CACHE, Offset(center.x, center.y + bob), CACHE_GLYPH_SIZE, Kk.Bone)
    recordMark(center, AMBIENT_MARK_HALF)
}

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
