// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.fx.CoreRibbonProjection
import kinetickk.ball.gameplay.interaction.fx.RIBBON_MAX_AGE
import kinetickk.ball.gameplay.interaction.fx.RIBBON_MIN_SPEED
import kinetickk.ball.gameplay.interaction.fx.ribbonLength
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Width at the Core (about the old wedge's 9.6 px base); ×1.25 in Overdrive. */
private const val RIBBON_HEAD_WIDTH = 10f

/** The width tapers as `(1 − u)^0.8` along the drawn length (u = 0 at the Core, 1 at the end). */
private const val RIBBON_WIDTH_EXPONENT = 0.8f

/** Samples recorded in a Dash draw this much brighter. */
private const val RIBBON_DASH_BOOST = 1.3f

/** The bone rails run over this share of the drawn length (the old lines' 60 + 180 r of 80 + 260 r). */
private const val RIBBON_RAIL_SHARE = 0.7f

/** The rails' distance either side of the path (world units). */
private const val RIBBON_RAIL_OFFSET = 13f

/** How much of the ribbon's light a fully straight course takes away: it glows while carving, cools on straights. */
private const val RIBBON_STRAIGHT_COOLING = 0.3f

/** The Dash phase (CharacterSystem's 0.24 s): the ghosts fade with what is left of it. */
private const val DASH_GHOST_PHASE_SECONDS = 0.24f

/** Path distances of the three Dash ghosts (the old afterimages' 60 × (1 + 0.4 i)). */
private val DashGhostDistance = floatArrayOf(84f, 108f, 132f)

/**
 * The Core's ribbon: tapered segments from the drawn Core back along its recorded path (never along the velocity), as
 * long as [ribbonLength] at the current speed, ending blunt; two bone rails over its first 70 %; the three Dash ghosts
 * on the path while a Dash runs. The newest sample is the start of the last step, so the head joint keeps the ribbon on
 * the interpolated Core at every frame rate; no segment joins a sample that starts a chain to an older one. A pastel
 * volt in Baby mode, translucent everywhere, drawn without allocating (value colours and offsets, a cached stroke).
 */
internal fun DrawScope.drawCoreRibbon(
    engine: GameplayRenderModel,
    ribbon: CoreRibbonProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    if (engine.phase == GamePhase.GAME_OVER || engine.speed <= RIBBON_MIN_SPEED || ribbon.size == 0) return
    val overdrive = engine.overdriveTime > 0f
    val dashing = engine.dashPhaseTime > 0f
    val tone = if (engine.easyMode) easyPastelYou(roles) else roles.you
    val railTone = if (engine.easyMode) easyPastelLilac(roles) else Kk.Bone
    val cooling = 1f - RIBBON_STRAIGHT_COOLING * engine.coreStraightness.coerceIn(0f, 1f)
    val base = (if (overdrive || dashing) 0.85f else 0.55f) * cooling
    val headWidth = RIBBON_HEAD_WIDTH * if (overdrive) 1.25f else 1f
    val budget = ribbonLength(engine.speed)
    val railBudget = budget * RIBBON_RAIL_SHARE
    val rails = engine.settings.particleDensity != ParticleDensity.LOW
    val head = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    var hx = head.x
    var hy = head.y
    var travelled = 0f
    var railAX = Float.NaN
    var railAY = 0f
    var railBX = 0f
    var railBY = 0f
    for (index in 0 until ribbon.size) {
        val point = world(engine, ribbon.x(index), ribbon.y(index), shakeX, shakeY)
        val dx = point.x - hx
        val dy = point.y - hy
        val length = sqrt(dx * dx + dy * dy)
        if (length > MIN_SEGMENT) {
            val take = min(length, budget - travelled)
            val ex = hx + dx * (take / length)
            val ey = hy + dy * (take / length)
            val u = (travelled + take * 0.5f) / budget
            val ageFade = (1f - ribbon.age(index) / RIBBON_MAX_AGE).coerceIn(0f, 1f)
            val boost = if (ribbon.dash(index)) RIBBON_DASH_BOOST else 1f
            val alpha = min(1f, base * (1f - u) * ageFade * boost)
            val width = max(1f, headWidth * (1f - u).pow(RIBBON_WIDTH_EXPONENT))
            if (alpha > 0.01f && (isOnScreen(Offset(hx, hy), 40f) || isOnScreen(Offset(ex, ey), 40f))) {
                drawLine(tone.copy(alpha = alpha), Offset(hx, hy), Offset(ex, ey), width, StrokeCap.Butt)
            }
            if (rails && travelled < railBudget) {
                // The old frame's sides: rail A (brighter) left of the backward direction, rail B right of it.
                val nx = -dy / length
                val ny = dx / length
                val fade = (1f - ((travelled + take) / railBudget).coerceAtMost(1f)) * ageFade
                if (railAX.isNaN()) {
                    railAX = hx - nx * RIBBON_RAIL_OFFSET
                    railAY = hy - ny * RIBBON_RAIL_OFFSET
                    railBX = hx + nx * RIBBON_RAIL_OFFSET
                    railBY = hy + ny * RIBBON_RAIL_OFFSET
                }
                val aX = ex - nx * RIBBON_RAIL_OFFSET
                val aY = ey - ny * RIBBON_RAIL_OFFSET
                val bX = ex + nx * RIBBON_RAIL_OFFSET
                val bY = ey + ny * RIBBON_RAIL_OFFSET
                if (fade > 0.01f) {
                    drawLine(railTone.copy(alpha = 0.5f * fade), Offset(railAX, railAY), Offset(aX, aY), 2f, StrokeCap.Butt)
                    drawLine(railTone.copy(alpha = 0.35f * fade), Offset(railBX, railBY), Offset(bX, bY), 2f, StrokeCap.Butt)
                }
                // Each rail segment starts where the last ended: no gaps on curves.
                railAX = aX
                railAY = aY
                railBX = bX
                railBY = bY
            }
            travelled += take
            hx = ex
            hy = ey
            if (travelled >= budget) break
        }
        if (ribbon.startsChain(index)) break
    }
    if (dashing) drawDashGhosts(engine, ribbon, shakeX, shakeY, tone, budget)
}

/** The three Dash ghosts (Core-sized rings) at their distances along the drawn ribbon, fading with the Dash phase. */
private fun DrawScope.drawDashGhosts(
    engine: GameplayRenderModel,
    ribbon: CoreRibbonProjection,
    shakeX: Float,
    shakeY: Float,
    tone: Color,
    budget: Float,
) {
    val phase = (engine.dashPhaseTime / DASH_GHOST_PHASE_SECONDS).coerceIn(0f, 1f)
    val point = CoreRibbonScratch.point
    for (ghost in DashGhostDistance.indices) {
        val distance = DashGhostDistance[ghost]
        if (distance > budget || !CoreRibbonGeometry.pointAlong(ribbon, engine.renderCoreX, engine.renderCoreY, distance, point)) return
        val center = world(engine, point[0], point[1], shakeX, shakeY)
        drawCircle(tone.copy(alpha = (0.5f - ghost * 0.2f) * phase), 18f, center, style = kkStroke(2f))
    }
}

/** Shorter segments (repeated positions) join nothing. */
private const val MIN_SEGMENT = 0.01f

/** Pure ribbon geometry in world units. */
internal object CoreRibbonGeometry {
    /**
     * The point [distance] world units along the ribbon from the head ([headX], [headY]) through the samples newest
     * first, written to [out] (x, y). False when the chain (up to its first [CoreRibbonProjection.startsChain] sample)
     * is shorter, or for a negative distance. Repeated positions add no length.
     */
    fun pointAlong(ribbon: CoreRibbonProjection, headX: Float, headY: Float, distance: Float, out: FloatArray): Boolean {
        if (distance < 0f) return false
        var hx = headX
        var hy = headY
        var travelled = 0f
        for (index in 0 until ribbon.size) {
            val dx = ribbon.x(index) - hx
            val dy = ribbon.y(index) - hy
            val length = sqrt(dx * dx + dy * dy)
            if (length > MIN_SEGMENT) {
                if (travelled + length >= distance) {
                    val t = (distance - travelled) / length
                    out[0] = hx + dx * t
                    out[1] = hy + dy * t
                    return true
                }
                travelled += length
                hx = ribbon.x(index)
                hy = ribbon.y(index)
            }
            if (ribbon.startsChain(index)) return false
        }
        return false
    }
}

/** The draw thread's reused ghost position. */
private object CoreRibbonScratch {
    val point = FloatArray(2)
}
