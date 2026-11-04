// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EmberProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkLayerHues
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * A visit's objective marks in the world: the Zenith's halo rings (gold light around each, the ring to fly through
 * next in the player's colour with its split) and the Nadir's embers (a bright core inside its catch ring, the ring
 * emptying with the ember's life). Radii come from the projection, so a retuned catch radius moves the look with it.
 */

/** The ring to fly through next stands this far outside its halo, and its marks reach this far for culling. */
private const val NEXT_RING_GAP = 14f
private const val HALO_GLOW_REACH = 1.45f

/** Halo rings of the visit: gold light, the ring and its glints; the course ring after next waits in grey. */
internal fun DrawScope.drawHaloRings(engine: GameplayRenderModel, dimension: DimensionProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val halos = dimension.halos
    if (halos.isEmpty()) return
    val radius = dimension.haloRadius
    val time = engine.renderElapsed
    val slow = engine.speed < dimension.pace
    // The share of the split to the next ring still left (the whole ring while no split runs).
    val split = if (dimension.splitDuration > 0f) (dimension.splitRemaining / dimension.splitDuration).coerceIn(0f, 1f) else 1f
    for (index in halos.indices) {
        val halo = halos[index]
        val center = world(engine, halo.x, halo.y, shakeX, shakeY)
        if (!isOnScreen(center, radius * HALO_GLOW_REACH + NEXT_RING_GAP)) continue
        val following = !dimension.completed && halo.courseIndex >= 0 && halo.courseIndex == dimension.progress + 1
        if (following) drawCircle(Kk.Mute.copy(alpha = 0.7f), radius, center, style = kkStroke(KkStrokeWidth.Heavy))
        else drawHalo(center, radius, time + halo.id * 1.3f, roles)
        if (halo.id == dimension.nextHaloId) drawNextHaloRing(center, radius + NEXT_RING_GAP, time, slow, split, roles)
    }
}

/** A halo ring: a gold glow, the gold ring with a bright inner rim and eight glints turning once per 40 s. */
private fun DrawScope.drawHalo(center: Offset, radius: Float, time: Float, roles: KkRolePalette) {
    val gold = roles.gold.base
    drawSoftDisc(DimensionGlows.of(gold.copy(alpha = 0.26f), 0.62f), center.x, center.y, radius * HALO_GLOW_REACH)
    drawCircle(gold.copy(alpha = 0.95f), radius, center, style = kkStroke(KkStrokeWidth.Heavy))
    drawCircle(roles.gold.light.copy(alpha = 0.8f), radius - 4f, center, style = kkStroke(KkStrokeWidth.Line))
    val glint = roles.gold.light.copy(alpha = 0.7f)
    rotate(time * 9f, center) {
        for (tick in 0 until HALO_GLINTS) {
            val dx = HaloGlintDirections[tick * 2]
            val dy = HaloGlintDirections[tick * 2 + 1]
            val reach = if (tick % 2 == 0) 14f else 8f
            drawLine(glint, Offset(center.x + dx * (radius + 5f), center.y + dy * (radius + 5f)),
                Offset(center.x + dx * (radius + 5f + reach), center.y + dy * (radius + 5f + reach)), 2f)
        }
    }
}

private const val HALO_GLINTS = 8
private val HaloGlintDirections = FloatArray(HALO_GLINTS * 2) { index ->
    val angle = (index / 2) * TWO_PI / HALO_GLINTS
    if (index % 2 == 0) cos(angle) else sin(angle)
}

/** Alpha of the grey block under each hatched dash of a too-slow ring. */
private const val HATCHED_DASH_BASE_ALPHA = 0.35f

/**
 * The ring to fly through next, [radius] across: a dashed ring in the player's colour turning at 15° per second, grey
 * with hatched dashes while the Core flies below the layer's pace (a slow pass would not count). Each hatched dash is a
 * wide grey block under its stripes with no line across it, so a dash reads as one hatched block. While the split to it
 * runs, the ring is an arc from the top over the [split] share still left, emptying clockwise as the split runs out.
 */
internal fun DrawScope.drawNextHaloRing(center: Offset, radius: Float, time: Float, slow: Boolean, split: Float, roles: KkRolePalette) {
    if (split < 1f) {
        val sweep = 360f * split
        val topLeft = Offset(center.x - radius, center.y - radius)
        val size = Size(radius * 2f, radius * 2f)
        if (slow) {
            drawArc(Kk.Mute.copy(alpha = HATCHED_DASH_BASE_ALPHA), -90f, sweep, useCenter = false, topLeft = topLeft, size = size,
                style = MarkStrokes.hatchedRing)
            drawArc(kkFillBrush(KkFillKind.HATCH, Kk.Mute), -90f, sweep, useCenter = false, topLeft = topLeft, size = size,
                style = MarkStrokes.hatchedRing)
        } else {
            drawArc(roles.you, -90f, sweep, useCenter = false, topLeft = topLeft, size = size, style = MarkStrokes.ring)
        }
        return
    }
    rotate(time * 15f, center) {
        if (slow) {
            drawCircle(Kk.Mute.copy(alpha = HATCHED_DASH_BASE_ALPHA), radius, center, style = MarkStrokes.hatchedRing)
            drawCircle(kkFillBrush(KkFillKind.HATCH, Kk.Mute), radius, center, style = MarkStrokes.hatchedRing)
        } else {
            drawCircle(roles.you, radius, center, style = MarkStrokes.ring)
        }
    }
}

/** An ember's core diamond and its glow (world units), and the share of its life its throw streak and its fade take. */
private const val EMBER_SIDE = 10f
private const val EMBER_GLOW_RADIUS = 26f
private const val EMBER_THROW_SHARE = 0.2f
private const val EMBER_FADE_SHARE = 0.2f

/**
 * The visit's embers: each a bright core in a heat glow, inside a dashed catch ring of the projected radius (a pass
 * that touches the ring collects it) whose bright arc empties clockwise over the ember's life; a new ember trails a
 * streak from its kill for the first fifth of its life and the last fifth fades it out.
 */
internal fun DrawScope.drawHarvestEmbers(engine: GameplayRenderModel, dimension: DimensionProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val embers = dimension.embers
    if (embers.isEmpty()) return
    val catch = dimension.emberRadius
    for (index in embers.indices) {
        val ember = embers[index]
        val center = world(engine, ember.x, ember.y, shakeX, shakeY)
        if (!isOnScreen(center, catch + 4f)) continue
        drawEmber(engine, ember, center, catch, shakeX, shakeY, roles)
    }
}

private fun DrawScope.drawEmber(
    engine: GameplayRenderModel,
    ember: EmberProjection,
    center: Offset,
    catch: Float,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val life = (ember.age + ember.remaining).coerceAtLeast(0.01f)
    val left = (ember.remaining / life).coerceIn(0f, 1f)
    val alpha = min(1f, left / EMBER_FADE_SHARE)
    val throwShare = ember.age / life
    if (throwShare < EMBER_THROW_SHARE && (ember.fromX != ember.x || ember.fromY != ember.y)) {
        // The throw: a heat streak from the kill whose tail catches up with the ember.
        val flown = throwShare / EMBER_THROW_SHARE
        val from = world(engine, ember.fromX, ember.fromY, shakeX, shakeY)
        val tail = Offset(from.x + (center.x - from.x) * flown, from.y + (center.y - from.y) * flown)
        drawLine(roles.heat.copy(alpha = 0.7f * (1f - flown)), tail, center, 3f)
    }
    drawCircle(Kk.Bone.copy(alpha = 0.22f * alpha), catch, center, style = MarkStrokes.catchRing)
    val topLeft = Offset(center.x - catch, center.y - catch)
    drawArc(KkLayerHues.MagmaCore.copy(alpha = 0.85f * alpha), -90f, 360f * left, useCenter = false, topLeft = topLeft,
        size = Size(catch * 2f, catch * 2f), style = kkStroke(KkStrokeWidth.Icon))
    drawSoftDisc(DimensionGlows.of(roles.heat.copy(alpha = 0.5f), 0.3f), center.x, center.y, EMBER_GLOW_RADIUS, alpha = alpha)
    rotate(45f, center) {
        val half = EMBER_SIDE * 0.5f
        drawRect(KkLayerHues.MagmaCore.copy(alpha = alpha), Offset(center.x - half, center.y - half), Size(EMBER_SIDE, EMBER_SIDE))
        drawRect(roles.heat.copy(alpha = alpha), Offset(center.x - half, center.y - half), Size(EMBER_SIDE, EMBER_SIDE),
            style = kkStroke(KkStrokeWidth.Line2))
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** Dashed strokes of the marks (created once): rift and next-ring dashes, the too-slow hatch, the ember's catch ring. */
internal object MarkStrokes {
    val ring: Stroke by lazy { Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))) }
    val hatchedRing: Stroke by lazy { Stroke(11f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f))) }
    val catchRing: Stroke by lazy { Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))) }
}
