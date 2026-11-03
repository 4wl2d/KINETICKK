// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.WorldUnitShapes
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkPulse
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.polar
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Core Stasis frost over the Core (world units): a cryo rim, [FROST_CRYSTALS] ice shards that grow around it with the
 * stasis level in a spread order, and crack lines while the frozen Core fractures. Frost is gameplay feedback, so it
 * is a signature layer that always draws; every animation stays at 1.43 Hz or slower and nothing flashes.
 */
internal fun DrawScope.drawCoreFrost(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val stasis = engine.coreStasis
    if (stasis <= 0f) return
    val radius = engine.coreRadius
    val time = engine.renderElapsed
    val cryo = roles.element(CRYO)
    val grown = ceil(stasis * FROST_CRYSTALS).toInt().coerceIn(0, FROST_CRYSTALS)
    FxDrawBudget.take(FxPool.CORE_STATES, 1 + 2 * grown, signature = true)
    drawCircle(cryo, radius + 3f, core, alpha = 0.2f + 0.6f * stasis, style = kkStroke(1.5f))
    for (rank in 0 until grown) {
        val index = FrostOrder[rank]
        val grow = (stasis * FROST_CRYSTALS - rank).coerceIn(0f, 1f)
        val angle = index * CanvasMath.TAU / FROST_CRYSTALS + FrostJitter[index]
        val length = (6f + 10f * grow) * FrostScale[index]
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
    if (engine.coreStasisFracturing) drawCoreFrostCracks(core, radius, time, cryo)
}

/** Five three-segment cracks across the frozen Core, an ink line with a cryo glint, pulsing once per fracture tick. */
private fun DrawScope.drawCoreFrostCracks(core: Offset, radius: Float, time: Float, cryo: Color) {
    FxDrawBudget.take(FxPool.CORE_STATES, FROST_CRACK_OPS, signature = true)
    val alpha = 0.65f + 0.35f * kkPulse(time, FROST_CRACK_PERIOD, KkEase.Out)
    var point = 0
    while (point < FrostCracks.size) {
        val segmentEnd = point + FROST_CRACK_POINTS * 2
        var index = point
        while (index + 2 < segmentEnd) {
            val from = Offset(core.x + FrostCracks[index] * radius, core.y + FrostCracks[index + 1] * radius)
            val to = Offset(core.x + FrostCracks[index + 2] * radius, core.y + FrostCracks[index + 3] * radius)
            drawLine(Kk.Ink, from, to, 1.6f, alpha = alpha)
            drawLine(cryo, Offset(from.x + 0.6f, from.y - 0.6f), Offset(to.x + 0.6f, to.y - 0.6f), 0.8f, alpha = 0.6f * alpha)
            index += 2
        }
        point = segmentEnd
    }
}

private val CRYO = StatusElement.CRYO.ordinal

private const val FROST_CRYSTALS = 12

/** Shimmer rate in radians per second (0.3 Hz). */
private const val FROST_SHIMMER = 1.9f

/** One crack pulse per fracture tick (1.5 s, 0.67 Hz). */
private const val FROST_CRACK_PERIOD = 1.5f
private const val FROST_CRACK_POINTS = 4

/** Five cracks of three segments, two lines each. */
private const val FROST_CRACK_OPS = 30

/** Crystal growth order: each new shard lands opposite and between the grown ones. */
private val FrostOrder = intArrayOf(0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11)

/** Per-crystal angle jitter (radians), so the ring reads as ice rather than a gear. */
private val FrostJitter = floatArrayOf(0f, 0.21f, -0.14f, 0.09f, -0.23f, 0.17f, -0.05f, 0.26f, -0.18f, 0.12f, -0.09f, 0.19f)
private val FrostScale = floatArrayOf(1f, 0.8f, 1.15f, 0.9f, 1.2f, 0.85f, 1.05f, 0.95f, 1.1f, 0.8f, 1f, 1.2f)

/** Crack polylines in Core radii: x, y pairs, [FROST_CRACK_POINTS] points per crack. */
private val FrostCracks = floatArrayOf(
    0.10f, -0.05f, 0.45f, -0.30f, 0.70f, -0.22f, 1.05f, -0.48f,
    -0.08f, 0.06f, -0.40f, 0.18f, -0.55f, 0.50f, -0.88f, 0.62f,
    0.04f, 0.12f, 0.20f, 0.48f, 0.12f, 0.78f, 0.30f, 1.04f,
    -0.12f, -0.10f, -0.38f, -0.42f, -0.70f, -0.46f, -0.92f, -0.70f,
    0.14f, 0.04f, 0.52f, 0.10f, 0.78f, 0.36f, 1.02f, 0.30f,
)
