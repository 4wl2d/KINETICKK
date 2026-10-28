// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.lostCursorFollows
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
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

/** The Lost Cursor: a tiny grey cursor drifting on a circle around where it lies, once every [EGG_PERIOD] seconds. */
private const val LOST_CURSOR_GLYPH_SIZE = 18f
private const val LOST_CURSOR_DRIFT = 30f

/** Once found, a smaller Lost Cursor circles the Core at this distance. */
private const val LOST_CURSOR_COMPANION_SIZE = 12f
private const val LOST_CURSOR_ORBIT = 34f

/** The easter eggs' slow cycle (drift, orbit, the quiet signal's ripple). */
private const val EGG_PERIOD = 24f

/** The Quiet Signal: three faint bone rings rippling out from its centre, inside its mark. */
private const val QUIET_RINGS = 3
private const val QUIET_INNER_RADIUS = 6f
private const val QUIET_SPREAD = 32f
private const val QUIET_ALPHA = 0.35f

/** The Golden Drifter's glint: a gold rim just outside its threat outline, never stronger than this. */
private const val GOLDEN_GLINT_GAP = 4f
private const val GOLDEN_GLINT_ALPHA = 0.6f
private const val GOLDEN_GLINT_RATE = 4f

/**
 * Ambient sites in view, after the window sites: a drift cache is a bone crate bobbing gently in place, the Lost Cursor
 * a tiny grey cursor drifting around where it lies, the Quiet Signal three faint bone rings rippling outward. Within
 * its reveal distance a site and its edge marker ([collectAmbientSiteEdgeMarkers]) share one predicate in the unshaken
 * view, so exactly one of them draws; beyond it the site simply draws while on screen. The Golden Drifter's trigger is
 * never drawn.
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
            SiteKind.LOST_CURSOR -> drawLostCursor(engine, point, shakeX, shakeY)
            SiteKind.QUIET_SIGNAL -> drawQuietSignal(engine, point, shakeX, shakeY)
            // The Golden Drifter's trigger is never drawn; no other kind is ambient.
            else -> Unit
        }
    }
}

private fun DrawScope.drawDriftCache(engine: GameplayRenderModel, point: SiteProjection, shakeX: Float, shakeY: Float) {
    val center = world(engine, point.x, point.y, shakeX, shakeY)
    if (!ambientMarkDrawn(engine, point, center, shakeX, shakeY)) return
    // Each cache bobs on its own phase (from its position), so a pair never moves in step.
    val phase = (point.x + point.y) * 0.001f
    val bob = sin(engine.renderElapsed * (2f * PI.toFloat() / CACHE_BOB_PERIOD) + phase) * CACHE_BOB
    drawSiteGlyph(SiteGlyph.CACHE, Offset(center.x, center.y + bob), CACHE_GLYPH_SIZE, Kk.Bone)
    recordMark(center, AMBIENT_MARK_HALF)
}

/** The Lost Cursor drifting on its small circle (phase from its position); it never shows off-screen. */
private fun DrawScope.drawLostCursor(engine: GameplayRenderModel, point: SiteProjection, shakeX: Float, shakeY: Float) {
    val center = world(engine, point.x, point.y, shakeX, shakeY)
    if (!ambientMarkDrawn(engine, point, center, shakeX, shakeY)) return
    val angle = engine.renderElapsed * (2f * PI.toFloat() / EGG_PERIOD) + (point.x - point.y) * 0.001f
    drawSiteGlyph(
        SiteGlyph.LOST_CURSOR, Offset(center.x + cos(angle) * LOST_CURSOR_DRIFT, center.y + sin(angle) * LOST_CURSOR_DRIFT),
        LOST_CURSOR_GLYPH_SIZE, Kk.Mute,
    )
    recordMark(center, AMBIENT_MARK_HALF)
}

/** The Quiet Signal: three rings spaced a third of the cycle apart, each fading as it grows. */
private fun DrawScope.drawQuietSignal(engine: GameplayRenderModel, point: SiteProjection, shakeX: Float, shakeY: Float) {
    val center = world(engine, point.x, point.y, shakeX, shakeY)
    if (!ambientMarkDrawn(engine, point, center, shakeX, shakeY)) return
    val cycle = engine.renderElapsed / EGG_PERIOD
    for (ring in 0 until QUIET_RINGS) {
        val phase = cycle + ring.toFloat() / QUIET_RINGS
        val grown = phase - floor(phase)
        drawCircle(
            Kk.Bone.copy(alpha = QUIET_ALPHA * (1f - grown)), QUIET_INNER_RADIUS + QUIET_SPREAD * grown, center,
            style = kkStroke(1.5f),
        )
    }
    recordMark(center, AMBIENT_MARK_HALF)
}

/**
 * Whether [point]'s mark at screen [center] draws this frame: on screen, and (within its reveal distance) only where its
 * edge marker would not stand in for it.
 */
private fun DrawScope.ambientMarkDrawn(
    engine: GameplayRenderModel,
    point: SiteProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
): Boolean {
    if (!isOnScreen(center, AMBIENT_CULL_MARGIN)) return false
    return !revealed(engine, point) ||
        markShown(steady(center, shakeX, shakeY), AMBIENT_MARK_HALF, WorldOverlayScratch.keepOut, engine.worldScale)
}

/**
 * The secrets drawn over the enemies and the Core: the Golden Drifter's gold glint (the documented easter-egg
 * exception to gold meaning Rebirth) and, once found, the Lost Cursor circling the Core at [core].
 */
internal fun DrawScope.drawSecretCompanions(
    engine: GameplayRenderModel,
    core: Offset,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val goldenId = engine.goldenDrifterId
    if (goldenId >= 0) {
        val enemies = engine.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.id != goldenId || enemy.dead) continue
            val center = world(
                engine, engine.interpolated(enemy.previousX, enemy.x), engine.interpolated(enemy.previousY, enemy.y), shakeX, shakeY,
            )
            if (!isOnScreen(center, AMBIENT_CULL_MARGIN)) break
            val glint = GOLDEN_GLINT_ALPHA * (0.6f + 0.4f * sin(engine.renderElapsed * GOLDEN_GLINT_RATE))
            drawCircle(roles.gold.base.copy(alpha = glint), enemy.radius + GOLDEN_GLINT_GAP, center, style = kkStroke(2f))
            break
        }
    }
    if (engine.lostCursorFollows) {
        val angle = engine.renderElapsed * (2f * PI.toFloat() / EGG_PERIOD)
        drawSiteGlyph(
            SiteGlyph.LOST_CURSOR, Offset(core.x + cos(angle) * LOST_CURSOR_ORBIT, core.y + sin(angle) * LOST_CURSOR_ORBIT),
            LOST_CURSOR_COMPANION_SIZE, Kk.Mute,
        )
    }
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
