// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.WorldUnitShapes
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.polar
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Core Freeze frost over the Core (world units), grown continuously with the freeze: a cryo film and rim that thicken,
 * [FROST_CRYSTALS] ice shards that grow around it in a spread order, and cracks that run across the Core past
 * [CRACK_START]. While the Core thaws, meltwater beads slide off the rim. Frost is gameplay feedback, so it is a
 * signature layer that always draws; every animation stays at 1.43 Hz or slower and nothing flashes.
 */
internal fun DrawScope.drawCoreFrost(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val freeze = engine.coreStasis
    if (freeze <= 0f) return
    drawCoreFrostAt(core, engine.coreRadius, freeze, engine.renderElapsed, roles, engine.coreStasisMotion == CoreStasisMotion.THAWING)
}

/** The frost of [freeze] (0..1) around a Core of [radius] at [core]; [thawing] adds the meltwater. */
internal fun DrawScope.drawCoreFrostAt(core: Offset, radius: Float, freeze: Float, time: Float, roles: KkRolePalette, thawing: Boolean) {
    val cryo = roles.element(CRYO)
    val grown = ceil(freeze * FROST_CRYSTALS).toInt().coerceIn(0, FROST_CRYSTALS)
    val cracks = crackSegments(freeze)
    FxDrawBudget.take(FxPool.CORE_STATES, 3 + 2 * grown + 2 * cracks + if (thawing) MELT_BEADS else 0, signature = true)
    drawCircle(cryo, radius, core, alpha = FILM_ALPHA * freeze)
    // Fixed stroke widths (cached strokes); the rim thickens through its outer halo's alpha.
    drawCircle(cryo, radius + 3f, core, alpha = 0.25f + 0.65f * freeze, style = kkStroke(1.5f))
    drawCircle(cryo, radius + 4.5f, core, alpha = 0.35f * freeze, style = kkStroke(3f))
    for (rank in 0 until grown) {
        val index = FrostOrder[rank]
        val grow = (freeze * FROST_CRYSTALS - rank).coerceIn(0f, 1f)
        val angle = index * CanvasMath.TAU / FROST_CRYSTALS + FrostJitter[index]
        val length = (5f + 13f * grow) * FrostScale[index]
        val center = polar(core, radius - 3f + 0.5f * length, angle)
        val shimmer = 0.85f + 0.15f * sin(time * FROST_SHIMMER + index)
        // The shard's long axis is x, so it points outward without a quarter turn.
        withTransform({
            translate(center.x, center.y)
            rotate(angle * CanvasMath.RAD_TO_DEG, Offset.Zero)
            scale(length, length, Offset.Zero)
        }) {
            drawPath(WorldUnitShapes.shard, cryo, alpha = 0.85f * shimmer)
            if (grow > 0.5f) scale(0.55f, 0.55f, Offset.Zero) { drawPath(WorldUnitShapes.shard, Kk.Bone, alpha = 0.7f * shimmer) }
        }
    }
    if (cracks > 0) drawCoreFrostCracks(core, radius, cracks, freeze, time, cryo)
    if (thawing) drawMeltwater(core, radius, freeze, time)
}

/**
 * The encased Core of a full freeze (world units): a faceted ice block around the Core of [radius] that clouds over
 * [progress] 0..1, with every crack run and a bone glint on its upper facets.
 */
internal fun DrawScope.drawCoreEncased(core: Offset, radius: Float, progress: Float, roles: KkRolePalette, alpha: Float = 1f) {
    val cryo = roles.element(CRYO)
    val size = radius * ICE_BLOCK_SCALE
    FxDrawBudget.take(FxPool.CORE_STATES, 4 + 2 * CRACK_SEGMENTS, signature = true)
    translate(core.x, core.y) {
        scale(size, size, Offset.Zero) {
            drawPath(IceBlock, cryo, alpha = (0.22f + 0.28f * progress) * alpha)
            drawPath(IceBlock, cryo, alpha = 0.9f * alpha, style = kkStroke(2.2f / size))
            drawPath(IceGlint, Kk.Bone, alpha = 0.65f * progress * alpha, style = kkStroke(1.6f / size))
        }
    }
    drawCircle(Kk.Bone, radius + 3f, core, alpha = 0.5f * alpha, style = kkStroke(1.5f))
    drawCoreFrostCracks(core, radius * 1.5f, CRACK_SEGMENTS, 1f, 0f, cryo, alpha)
}

/** Crack segments drawn at [freeze]: none below [CRACK_START], all of them at a full freeze. */
private fun crackSegments(freeze: Float): Int =
    if (freeze <= CRACK_START) 0 else ceil((freeze - CRACK_START) / (1f - CRACK_START) * CRACK_SEGMENTS).toInt().coerceAtMost(CRACK_SEGMENTS)

/**
 * The first [segments] of the cracks across the Core, each an ink line with a cryo glint, crack by crack from the
 * centre outward; past [URGENT_FREEZE] they pulse at [CRACK_PERIOD].
 */
private fun DrawScope.drawCoreFrostCracks(core: Offset, radius: Float, segments: Int, freeze: Float, time: Float, cryo: Color, alpha: Float = 1f) {
    val pulse = if (freeze >= URGENT_FREEZE && freeze < 1f) 0.65f + 0.35f * kkPulse(time, CRACK_PERIOD, KkEase.Out) else 1f
    val shade = pulse * alpha
    var drawn = 0
    // Segment k of every crack before segment k + 1 of any: all five run outward together.
    for (step in 0 until CRACK_POINTS - 1) {
        for (crack in 0 until CRACK_COUNT) {
            if (drawn == segments) return
            val index = (crack * CRACK_POINTS + step) * 2
            val from = Offset(core.x + FrostCracks[index] * radius, core.y + FrostCracks[index + 1] * radius)
            val to = Offset(core.x + FrostCracks[index + 2] * radius, core.y + FrostCracks[index + 3] * radius)
            drawLine(Kk.Ink, from, to, 1.6f, alpha = 0.85f * shade)
            drawLine(cryo, Offset(from.x + 0.6f, from.y - 0.6f), Offset(to.x + 0.6f, to.y - 0.6f), 0.8f, alpha = 0.7f * shade)
            drawn++
        }
    }
}

/** Meltwater beads sliding off the lower rim of a thawing Core, fewer and fainter as the frost goes. */
private fun DrawScope.drawMeltwater(core: Offset, radius: Float, freeze: Float, time: Float) {
    for (bead in 0 until MELT_BEADS) {
        val fall = ((time * MELT_RATE + bead * 0.37f) % 1f)
        val angle = MeltAngles[bead]
        val x = core.x + cos(angle) * (radius + 1f)
        val y = core.y + sin(angle) * (radius + 1f) + fall * MELT_DROP
        drawCircle(Kk.Bone, 1.3f + 0.6f * freeze, Offset(x, y), alpha = (0.75f - 0.6f * fall) * (0.4f + 0.6f * freeze))
    }
}

private val CRYO = StatusElement.CRYO.ordinal

private const val FROST_CRYSTALS = 12

/** The cryo film over the Core at a full freeze. */
private const val FILM_ALPHA = 0.3f

/** Shimmer rate in radians per second (0.3 Hz). */
private const val FROST_SHIMMER = 1.9f

/** Cracks start to run at this freeze and reach the Core's edge at a full freeze. */
private const val CRACK_START = 0.45f

/** Past this freeze the cracks pulse (0.9 s, 1.1 Hz): the last second or so. */
private const val URGENT_FREEZE = 0.78f
private const val CRACK_PERIOD = 0.9f
private const val CRACK_COUNT = 5
private const val CRACK_POINTS = 4
private const val CRACK_SEGMENTS = CRACK_COUNT * (CRACK_POINTS - 1)

/** Meltwater beads: their number, fall per second (0.8 Hz) and drop (world units). */
private const val MELT_BEADS = 3
private const val MELT_RATE = 0.8f
private const val MELT_DROP = 9f
private val MeltAngles = floatArrayOf(1.2f, 1.75f, 2.3f)

/** Crystal growth order: each new shard lands opposite and between the grown ones. */
private val FrostOrder = intArrayOf(0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11)

/** Per-crystal angle jitter (radians), so the ring reads as ice rather than a gear. */
private val FrostJitter = floatArrayOf(0f, 0.21f, -0.14f, 0.09f, -0.23f, 0.17f, -0.05f, 0.26f, -0.18f, 0.12f, -0.09f, 0.19f)
private val FrostScale = floatArrayOf(1f, 0.8f, 1.15f, 0.9f, 1.2f, 0.85f, 1.05f, 0.95f, 1.1f, 0.8f, 1f, 1.2f)

/** Crack polylines in Core radii: x, y pairs, [CRACK_POINTS] points per crack. */
private val FrostCracks = floatArrayOf(
    0.10f, -0.05f, 0.45f, -0.30f, 0.70f, -0.22f, 1.05f, -0.48f,
    -0.08f, 0.06f, -0.40f, 0.18f, -0.55f, 0.50f, -0.88f, 0.62f,
    0.04f, 0.12f, 0.20f, 0.48f, 0.12f, 0.78f, 0.30f, 1.04f,
    -0.12f, -0.10f, -0.38f, -0.42f, -0.70f, -0.46f, -0.92f, -0.70f,
    0.14f, 0.04f, 0.52f, 0.10f, 0.78f, 0.36f, 1.02f, 0.30f,
)

/** The ice block in units of its size: an irregular hexagon, a little taller than wide, tilted off the axes. */
private val IceBlockPoints = floatArrayOf(0.08f, -1.1f, 0.95f, -0.52f, 0.9f, 0.6f, -0.05f, 1.08f, -0.96f, 0.5f, -0.88f, -0.62f)

private val IceBlock: Path by lazy { closedPath(IceBlockPoints) }

/** The block's size in Core radii (a constant, so its outline keeps one cached stroke per Core radius). */
private const val ICE_BLOCK_SCALE = 2.1f

/** Two glints along the block's upper left facets. */
private val IceGlint: Path by lazy {
    Path().apply {
        moveTo(-0.72f, -0.5f)
        lineTo(-0.02f, -0.88f)
        moveTo(-0.7f, -0.28f)
        lineTo(-0.66f, 0.2f)
    }
}

private fun closedPath(points: FloatArray): Path = Path().apply {
    moveTo(points[0], points[1])
    var index = 2
    while (index < points.size) {
        lineTo(points[index], points[index + 1])
        index += 2
    }
    close()
}
