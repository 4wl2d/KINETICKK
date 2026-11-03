// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.fx

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.interaction.fx.FxColor
import kinetickk.ball.gameplay.interaction.fx.FxEase
import kinetickk.ball.gameplay.interaction.fx.FxFlag
import kinetickk.ball.gameplay.interaction.fx.FxLayer
import kinetickk.ball.gameplay.interaction.fx.FxPrimitive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.*

private const val TAU = CanvasMath.TAU
private const val DEG = CanvasMath.DEG_TO_RAD

/** A square's half side per unit of corner distance. */
private const val HALF_DIAGONAL = 0.70710678f

/** Plate and bar shear (−12°) as a horizontal lean per unit of height. */
private val SHEAR = tan(12f * DEG)

private fun FxEase.of(t: Float): Float = when (this) {
    FxEase.L -> t
    FxEase.O -> KkEase.Out.transform(t)
    FxEase.I -> KkEase.In.transform(t)
    FxEase.S -> KkEase.Snap.transform(t)
    FxEase.P -> KkEase.Pull.transform(t)
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * One layer of the look in [FxContext] with [elements] elements: the shared fade (discs and
 * wipes fade as the square of what is left, the rest hold to half way and fall linearly), the
 * radius on the reach (or on the Core, or in world units), then the primitive. Allocation-free.
 */
internal fun DrawScope.drawFxLayer(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val span = layer.t1 - layer.t0
    val t = if (span <= 0f) 1f else ((ctx.progress - layer.t0) / span).coerceIn(0f, 1f)
    val e = layer.ease.of(t)
    val fade = when (layer.primitive) {
        FxPrimitive.DISC, FxPrimitive.WIPE -> (1f - t) * (1f - t)
        else -> if (t < 0.5f) 1f else (1f - t) * 2f
    }
    val alpha = (layer.alpha * fade * ctx.alpha).coerceIn(0f, 1f)
    if (alpha <= 0.004f) return
    val onCore = layer.has(FxFlag.CORE)
    val world = onCore || layer.has(FxFlag.WORLD)
    val cx = if (onCore) ctx.coreX else ctx.x
    val cy = if (onCore) ctx.coreY else ctx.y
    val reach = ctx.reach
    val base = if (onCore) ctx.coreRadius else 0f
    val scale = if (world) 1f else reach
    val r = max(0.5f, base + lerp(layer.r0, layer.r1, e) * scale)
    val inner = base + layer.r0 * scale
    val length = layer.param * scale
    val theta = ctx.theta
    val spin = layer.spin * e * DEG
    val width = layer.width
    val center = Offset(cx, cy)
    val draw = FxLayerDraw
    draw.t = t
    draw.e = e
    draw.alpha = alpha
    draw.onCore = onCore
    draw.world = world
    draw.cx = cx
    draw.cy = cy
    draw.reach = reach
    draw.r = r
    draw.inner = inner
    draw.length = length
    draw.theta = theta
    draw.spin = spin
    draw.width = width
    draw.center = center
    when (layer.primitive) {
        FxPrimitive.DISC -> drawFxDisc(layer)
        FxPrimitive.RING -> drawFxRing(layer)
        FxPrimitive.ARCS -> drawFxArcs(layer, elements)
        FxPrimitive.SPLIT_RING -> drawFxSplitRing(layer)
        FxPrimitive.SPOKES -> drawFxSpokes(layer, elements)
        FxPrimitive.STREAKS_IN, FxPrimitive.STREAKS_OUT -> drawFxStreaks(layer, elements)
        FxPrimitive.SPIRAL -> drawFxSpiral(layer, elements)
        FxPrimitive.DOTS -> drawFxDots(layer, elements)
        FxPrimitive.DOTS_IN -> drawFxDotsIn(layer, elements)
        FxPrimitive.SHARDS -> drawFxShards(layer, elements)
        FxPrimitive.FORKS -> drawFxForks(layer, elements)
        FxPrimitive.HEX -> drawFxHex(layer)
        FxPrimitive.PLATE -> drawFxPlate(layer)
        FxPrimitive.CRACKS -> drawFxCracks(layer, elements)
        FxPrimitive.FLAMES -> drawFxFlames(layer, elements)
        FxPrimitive.TICKS -> drawFxTicks(layer, elements)
        FxPrimitive.BARS -> drawFxBars(layer, elements)
        FxPrimitive.QUAKE -> drawFxQuake(layer)
        FxPrimitive.WAVES -> drawFxWaves(layer, elements)
        FxPrimitive.PUNCH -> drawFxPunch(layer)
        FxPrimitive.WIPE -> drawFxWipe(layer)
        FxPrimitive.LANES -> drawFxLanes(layer, elements)
    }
}

/**
 * The shared values of the layer [drawFxLayer] is drawing, which its primitive reads: the layer's
 * time [t] and eased time [e], its [alpha], whether it sits [onCore] or in [world] units, its centre
 * ([cx], [cy], [center]), the look's [reach], the radius [r] and [inner] radius, the streak [length],
 * the heading [theta] and the layer's [spin], and the stroke [width]. Refilled per layer; draw-thread
 * confined, nothing allocates.
 */
private object FxLayerDraw {
    var t = 0f
    var e = 0f
    var alpha = 0f
    var onCore = false
    var world = false
    var cx = 0f
    var cy = 0f
    var reach = 0f
    var r = 0f
    var inner = 0f
    var length = 0f
    var theta = 0f
    var spin = 0f
    var width = 0f
    var center = Offset.Zero
}

private fun DrawScope.drawFxDisc(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val world = FxLayerDraw.world
    val reach = FxLayerDraw.reach
    val r = FxLayerDraw.r
    val center = FxLayerDraw.center
    // Overdraw guard: a disc never passes 0.8 of the reach or 0.35 alpha.
    val radius = if (world) r else minOf(r, reach * 0.8f)
    drawCircle(ctx.color(layer.color, 0), radius, center, alpha = minOf(alpha, 0.35f))
}

private fun DrawScope.drawFxRing(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    drawCircle(ctx.color(layer.color, 0), r, center, alpha = alpha,
        style = if (layer.has(FxFlag.DOTTED)) WorldStrokes.dotted else kkStroke(width))
}

private fun DrawScope.drawFxArcs(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val sweep = layer.param
    val start = (theta + spin) / DEG
    val topLeft = Offset(cx - r, cy - r)
    val size = Size(r * 2f, r * 2f)
    for (i in 0 until elements) {
        drawArc(ctx.color(layer.color, i), start + i * 360f / elements - sweep * 0.5f, sweep, false, topLeft, size, alpha = alpha,
            style = kkStroke(width))
    }
}

private fun DrawScope.drawFxSplitRing(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val start = (theta + spin) / DEG
    val topLeft = Offset(cx - r, cy - r)
    val size = Size(r * 2f, r * 2f)
    drawArc(ctx.color(if (layer.color == FxColor.AB) FxColor.A else layer.color, 0), start, layer.param, false, topLeft, size,
        alpha = alpha, style = kkStroke(width))
    drawArc(ctx.color(if (layer.color == FxColor.AB) FxColor.B else layer.color, 1), start + 180f, layer.param, false, topLeft, size,
        alpha = alpha, style = kkStroke(width))
}

private fun DrawScope.drawFxSpokes(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val inner = FxLayerDraw.inner
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    for (i in 0 until elements) {
        val angle = fanAngle(layer, theta, i, elements) + spin + (fxHash(ctx.seed, i + 1) - 0.5f) * layer.param
        drawLine(ctx.color(layer.color, i), polar(center, inner, angle), polar(center, r, angle), width, alpha = alpha)
    }
}

private fun DrawScope.drawFxStreaks(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val length = FxLayerDraw.length
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    for (i in 0 until elements) {
        val angle = fanAngle(layer, theta, i, elements) + spin
        drawLine(ctx.color(layer.color, i), polar(center, r, angle), polar(center, r + length, angle), width, alpha = alpha)
    }
}

private fun DrawScope.drawFxSpiral(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val sweep = layer.param * DEG
    for (i in 0 until elements) {
        val start = theta + i * TAU / elements + spin
        var previous = polar(center, r, start)
        for (k in 1..3) {
            val next = polar(center, r * (1f - 0.22f * k), start + sweep * k / 3f)
            drawLine(ctx.color(layer.color, i), previous, next, width, alpha = alpha)
            previous = next
        }
    }
}

private fun DrawScope.drawFxDots(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val t = FxLayerDraw.t
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val reach = FxLayerDraw.reach
    val theta = FxLayerDraw.theta
    val center = FxLayerDraw.center
    val rise = if (layer.has(FxFlag.RISE)) 18f * t else 0f
    val cone = layer.param2 * DEG
    for (i in 0 until elements) {
        if (t < 0.5f * fxHash(ctx.seed, 3 * i + 7)) continue
        val roll = fxHash(ctx.seed, 2 * i + 2)
        val angle = if (layer.has(FxFlag.DIR) && cone > 0f) theta + (roll - 0.5f) * 2f * cone else roll * TAU
        val distance = reach * lerp(layer.r0, layer.r1, fxHash(ctx.seed, 2 * i + 3)) * lerp(0.8f, 1f, e)
        val point = polar(center, distance, angle)
        drawCircle(ctx.color(layer.color, i), layer.param, Offset(point.x, point.y - rise), alpha = alpha)
    }
}

private fun DrawScope.drawFxDotsIn(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val spin = FxLayerDraw.spin
    val center = FxLayerDraw.center
    for (i in 0 until elements) {
        val angle = fxHash(ctx.seed, 2 * i + 2) * TAU + spin
        drawCircle(ctx.color(layer.color, i), layer.param, polar(center, r, angle), alpha = alpha)
    }
}

private fun DrawScope.drawFxShards(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val center = FxLayerDraw.center
    val shard = layer.param
    for (i in 0 until elements) {
        val angle = fanAngle(layer, theta, i, elements)
        val point = if (layer.has(FxFlag.CORE_TARGET)) {
            // To the Core's guard orbit slot (radius Core + 74, turning with run time).
            val slot = polar(Offset(ctx.coreX, ctx.coreY), ctx.coreRadius + 74f, ctx.elapsed * 3f + i * TAU / elements)
            Offset(lerp(cx, slot.x, e), lerp(cy, slot.y, e))
        } else polar(center, r, angle)
        withTransform({
            translate(point.x, point.y)
            rotate(angle / DEG, Offset.Zero)
            scale(shard, shard, Offset.Zero)
        }) {
            drawPath(WorldUnitShapes.shard, ctx.color(layer.color, i), alpha = alpha)
        }
    }
}

private fun DrawScope.drawFxForks(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val reach = FxLayerDraw.reach
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val jitter = layer.param * reach
    for (i in 0 until elements) {
        val angle = theta + i * TAU / elements + spin
        val dx = cos(angle)
        val dy = sin(angle)
        var previous = center
        for (k in 1..3) {
            val along = r * k / 3f
            val side = if (k == 3) 0f else jitter * (fxHash(ctx.seed, 10 * i + k) - 0.5f)
            val next = Offset(cx + dx * along - dy * side, cy + dy * along + dx * side)
            drawLine(ctx.color(layer.color, i), previous, next, width, alpha = alpha)
            previous = next
        }
    }
}

private fun DrawScope.drawFxHex(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val color = ctx.color(layer.color, 0)
    val turn = theta + spin
    for (side in 0 until 6) {
        val from = polar(center, r, turn + side * TAU / 6f)
        val to = polar(center, r, turn + (side + 1) * TAU / 6f)
        drawLine(color, from, to, width, alpha = alpha)
    }
}

private fun DrawScope.drawFxPlate(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    // r(p) is the square's corner distance; the shear leans its top and bottom edges.
    val color = ctx.color(layer.color, 0)
    val half = r * HALF_DIAGONAL
    val lean = half * SHEAR
    rotate((theta + spin) / DEG, center) {
        val a = Offset(cx - half + lean, cy - half)
        val b = Offset(cx + half + lean, cy - half)
        val c = Offset(cx + half - lean, cy + half)
        val d = Offset(cx - half - lean, cy + half)
        drawLine(color, a, b, width, alpha = alpha)
        drawLine(color, b, c, width, alpha = alpha)
        drawLine(color, c, d, width, alpha = alpha)
        drawLine(color, d, a, width, alpha = alpha)
    }
}

private fun DrawScope.drawFxCracks(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val inner = FxLayerDraw.inner
    val theta = FxLayerDraw.theta
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val jitter = layer.param
    for (pass in 0..1) {
        val color = if (pass == 0) Kk.Ink else ctx.color(layer.color, 0)
        val stroke = if (pass == 0) width + 1.5f else width
        for (i in 0 until elements) {
            val angle = theta + i * TAU / elements + (fxHash(ctx.seed, 20 + i) - 0.5f) * 0.5f
            var previous = polar(center, inner, angle)
            for (k in 1..3) {
                val bend = (fxHash(ctx.seed, 40 + 4 * i + k) - 0.5f) * jitter
                val next = polar(center, lerp(inner, r, k / 3f), angle + bend)
                drawLine(if (pass == 0) color else ctx.color(layer.color, i), previous, next, stroke, alpha = alpha)
                previous = next
            }
        }
    }
}

private fun DrawScope.drawFxFlames(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val length = FxLayerDraw.length
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val center = FxLayerDraw.center
    for (i in 0 until elements) {
        val angle = theta + i * TAU / elements + spin
        val point = polar(center, r, angle)
        val flicker = 0.7f + 0.3f * sin(ctx.age * 25f + 2f * i)
        val size = length * flicker
        withTransform({
            translate(point.x, point.y)
            rotate(angle / DEG, Offset.Zero)
            scale(size, size, Offset.Zero)
        }) {
            drawPath(WorldUnitShapes.flame, ctx.color(layer.color, i), alpha = alpha)
            if (layer.has(FxFlag.INK_CORE)) scale(0.65f, 0.65f, Offset.Zero) { drawPath(WorldUnitShapes.flame, Kk.Ink, alpha = alpha) }
        }
    }
}

private fun DrawScope.drawFxTicks(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val t = FxLayerDraw.t
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val length = FxLayerDraw.length
    val theta = FxLayerDraw.theta
    val spin = FxLayerDraw.spin
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    // The spin settles to a stop by param2 of the layer (a clock that freezes).
    val settle = if (layer.param2 > 0f) KkEase.Out.transform((t / layer.param2).coerceIn(0f, 1f)) else e
    val turn = theta + layer.spin * settle * DEG
    val half = length * 0.5f
    for (i in 0 until elements) {
        val angle = turn + i * TAU / elements
        val point = polar(center, r, angle)
        val color = ctx.color(layer.color, i)
        if (layer.has(FxFlag.TANGENT)) {
            val tx = -sin(angle) * half
            val ty = cos(angle) * half
            drawLine(color, Offset(point.x - tx, point.y - ty), Offset(point.x + tx, point.y + ty), width, alpha = alpha)
        } else {
            drawLine(color, polar(center, r - half, angle), polar(center, r + half, angle), width, alpha = alpha)
        }
    }
}

private fun DrawScope.drawFxBars(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val reach = FxLayerDraw.reach
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val center = FxLayerDraw.center
    val gap = layer.param * reach * (1f - e)
    val thickness = reach * 0.07f
    rotate(theta / DEG, center) {
        for (i in 0 until elements) {
            val offset = (i - (elements - 1) * 0.5f) * gap
            withTransform({
                translate(cx, cy + offset)
                scale(r * 2f, thickness / 0.22f, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.bar, ctx.color(layer.color, i), alpha = alpha)
            }
        }
    }
}

private fun DrawScope.drawFxQuake(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val color = ctx.color(layer.color, 0)
    var previous = Offset.Zero
    var first = Offset.Zero
    for (i in 0..QUAKE_VERTICES) {
        val vertex = i % QUAKE_VERTICES
        val distance = r * (1f + 0.24f * (fxHash(ctx.seed, 60 + vertex) - 0.5f))
        val point = polar(center, distance, theta + vertex * TAU / QUAKE_VERTICES)
        if (i == 0) first = point else drawLine(color, previous, if (vertex == 0) first else point, width, alpha = alpha)
        previous = point
    }
}

private fun DrawScope.drawFxWaves(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val t = FxLayerDraw.t
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val reach = FxLayerDraw.reach
    val theta = FxLayerDraw.theta
    val width = FxLayerDraw.width
    val stacks = if (layer.has(FxFlag.TRI)) 3 else 1
    val sweep = layer.param
    for (stack in 0 until stacks) {
        val facing = (theta + stack * TAU / 3f) / DEG
        for (i in 0 until elements) {
            val lag = (t - 0.08f * i).coerceIn(0f, 1f)
            if (lag <= 0f) continue
            val radius = max(0.5f, reach * lerp(layer.r0, layer.r1, layer.ease.of(lag)) + i * layer.param2 * reach)
            drawArc(ctx.color(layer.color, i), facing - sweep * 0.5f, sweep, false, Offset(cx - radius, cy - radius),
                Size(radius * 2f, radius * 2f), alpha = alpha, style = kkStroke(width))
        }
    }
}

private fun DrawScope.drawFxPunch(layer: FxLayer) {
    val ctx = FxContext
    val alpha = FxLayerDraw.alpha
    val r = FxLayerDraw.r
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val color = ctx.color(layer.color, 0)
    drawCircle(Kk.Ink, r, center, alpha = alpha * 0.85f)
    drawCircle(color, r, center, alpha = alpha, style = kkStroke(width))
    drawCircle(color, max(0.5f, r * 0.6f), center, alpha = alpha, style = kkStroke(KkStrokeWidth.Line2))
}

private fun DrawScope.drawFxWipe(layer: FxLayer) {
    val ctx = FxContext
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val reach = FxLayerDraw.reach
    val center = FxLayerDraw.center
    val slab = layer.param * reach
    val offset = lerp(-reach, reach, e)
    rotate(KkShape.Slash, center) {
        drawRect(ctx.color(layer.color, 0), Offset(cx + offset - slab * 0.5f, cy - reach * 0.45f), Size(slab, reach * 0.9f), alpha = alpha)
    }
}

private fun DrawScope.drawFxLanes(layer: FxLayer, elements: Int) {
    val ctx = FxContext
    val e = FxLayerDraw.e
    val alpha = FxLayerDraw.alpha
    val onCore = FxLayerDraw.onCore
    val world = FxLayerDraw.world
    val cx = FxLayerDraw.cx
    val cy = FxLayerDraw.cy
    val r = FxLayerDraw.r
    val theta = FxLayerDraw.theta
    val width = FxLayerDraw.width
    val center = FxLayerDraw.center
    val angle = if (layer.has(FxFlag.BACK)) theta + PI.toFloat() else theta
    val dx = cos(angle)
    val dy = sin(angle)
    val start = if (onCore) Offset(cx + dx * ctx.coreRadius, cy + dy * ctx.coreRadius) else center
    val run = if (world) lerp(layer.r0, layer.r1, e) else r
    for (i in 0 until elements) {
        val side = (i - (elements - 1) * 0.5f) * layer.param2
        val from = Offset(start.x - dy * side, start.y + dx * side)
        val to = Offset(from.x + dx * run, from.y + dy * run)
        val color = ctx.color(layer.color, i)
        if (layer.has(FxFlag.INK_CORE)) {
            // An ink core under two colour rims (the Void Step blink path).
            val rim = width + 1.25f
            drawLine(Kk.Ink, from, to, width + 2.5f, alpha = alpha)
            drawLine(color, Offset(from.x - dy * rim, from.y + dx * rim), Offset(to.x - dy * rim, to.y + dx * rim), width, alpha = alpha)
            drawLine(color, Offset(from.x + dy * rim, from.y - dx * rim), Offset(to.x + dy * rim, to.y - dx * rim), width, alpha = alpha)
        } else {
            drawLine(color, from, to, width, alpha = alpha)
        }
    }
}

/** Element [i]'s angle: a fan [FxLayer.param2]° apart around [theta], or evenly round the circle. */
private fun fanAngle(layer: FxLayer, theta: Float, i: Int, elements: Int): Float {
    val spread = layer.param2
    if (spread > 0f && layer.primitive != FxPrimitive.DOTS) return theta + (i - (elements - 1) * 0.5f) * spread * DEG
    return theta + i * TAU / elements
}
