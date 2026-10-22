// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpec
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val TAU = (2.0 * PI).toFloat()
private const val DEG = (PI / 180.0).toFloat()

/** Golden-ratio increment of the per-element hash (`0x9E3779B9`). */
private const val GOLDEN = -0x61c88647

/** The fixed angle the hash takes for element 0, used as a look's default heading. */
private const val HEADING = 0

/** A square's half side per unit of corner distance. */
private const val HALF_DIAGONAL = 0.70710678f

/** Plate and bar shear (−12°) as a horizontal lean per unit of height. */
private val SHEAR = tan(12f * DEG)

/**
 * A stable pseudo-random value in [0, 1) for element [index] of a look with [seed]: the 32-bit
 * finalizer of MurmurHash3 over `seed xor (index · 0x9E3779B9)`. Pure, allocation-free.
 */
internal fun fxHash(seed: Int, index: Int): Float {
    var h = seed xor (index * GOLDEN)
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    h = h xor (h ushr 16)
    return (h ushr 8) * (1f / 16_777_216f)
}

/** Draw-operation pools of the world FX ([FxDrawBudget]). */
internal enum class FxPool(val ops: Int) {
    TRANSIENT(900),
    LINKS(600),
    FIELDS(220),
    GLYPHS(480),
    CORE_STATES(60),
}

/**
 * Per-frame draw-operation budget of the world FX (hud-fx §4.11). Every primitive reports its
 * operations before drawing: a non-signature layer that does not fit its pool is skipped, a
 * signature layer always draws and is recorded. Reset by [FxFrame.begin]; draw-thread confined.
 */
internal object FxDrawBudget {
    private val used = IntArray(FxPool.entries.size)

    /** Signature operations drawn past a pool's end, per pool (tests read them). */
    private val overflow = IntArray(FxPool.entries.size)

    /** Signature layers drawn and non-signature layers skipped, per pool. */
    private val signatures = IntArray(FxPool.entries.size)
    private val skipped = IntArray(FxPool.entries.size)

    fun reset() {
        used.fill(0)
        overflow.fill(0)
        signatures.fill(0)
        skipped.fill(0)
    }

    fun used(pool: FxPool): Int = used[pool.ordinal]

    fun overflow(pool: FxPool): Int = overflow[pool.ordinal]

    fun signatures(pool: FxPool): Int = signatures[pool.ordinal]

    fun skipped(pool: FxPool): Int = skipped[pool.ordinal]

    fun remaining(pool: FxPool): Int = pool.ops - used[pool.ordinal]

    /** Takes [ops] from [pool] when they fit; a [signature] always takes them (recording any overflow). */
    fun take(pool: FxPool, ops: Int, signature: Boolean): Boolean {
        val index = pool.ordinal
        val left = pool.ops - used[index]
        if (signature) {
            signatures[index]++
            overflow[index] += max(0, ops - max(0, left))
            used[index] += ops
            return true
        }
        if (ops > left) {
            skipped[index]++
            return false
        }
        used[index] += ops
        return true
    }
}

/**
 * World FX state of the frame being drawn: how many enemies are on screen (crowd level of detail,
 * capped just past [CROWD_SIG_ONLY]) and the reaction effects setting. [begin] runs once per world
 * frame before anything draws; draw-thread confined.
 */
internal object FxFrame {
    /** Above this many on-screen enemies secondary layers go (the status patterns' limit). */
    const val CROWD_SECONDARY_OFF = STATUS_PATTERN_ENEMY_LIMIT

    /** Above this many only signature layers draw. */
    const val CROWD_SIG_ONLY = 90

    var crowd: Int = 0
        private set
    var level: ReactionEffectsLevel = ReactionEffectsLevel.FULL
        private set

    fun begin(crowd: Int, level: ReactionEffectsLevel) {
        this.crowd = crowd
        this.level = level
        FxDrawBudget.reset()
    }

    /** Peak alpha of reaction, Break, skill and Apex looks for the setting. */
    val peakAlpha: Float
        get() = when (level) {
            ReactionEffectsLevel.FULL -> 1f
            ReactionEffectsLevel.REDUCED -> 0.85f
            ReactionEffectsLevel.MINIMAL -> 0.70f
        }

    /** Element counts for the setting (at least one element survives). */
    val countScale: Float get() = if (level == ReactionEffectsLevel.FULL) 1f else 0.6f

    /** Field fills for the setting (rims never change). */
    val fillScale: Float
        get() = when (level) {
            ReactionEffectsLevel.FULL -> 1f
            ReactionEffectsLevel.REDUCED -> 0.75f
            ReactionEffectsLevel.MINIMAL -> 0.5f
        }

    /** Styled links draw as one line at the minimal setting. */
    val plainLinks: Boolean get() = level == ReactionEffectsLevel.MINIMAL
}

/**
 * Scratch for drawing one look: its centre and the Core in layer units, the reach, the burst's
 * progress and age, the seed, heading and colours, and which layers it shows. One instance,
 * refilled per burst; nothing allocates.
 */
internal object FxContext {
    var x = 0f
    var y = 0f
    var coreX = 0f
    var coreY = 0f
    var coreRadius = 16f
    var reach = 1f
    var progress = 0f
    var age = 0f
    var elapsed = 0f
    var seed = 0
    var theta = 0f
    var a: Color = Color.White
    var b: Color = Color.White
    var you: Color = Color.White
    var gold: Color = Color.White
    var goldLight: Color = Color.White
    var alpha = 1f
    var rank = 1
    var verb = false
    var keystone = false
    var coreInside = false
    var signatureOnly = false
    var secondaryOff = false

    /** The heading: [direction] when it is a number, else one hashed from the seed. */
    fun heading(direction: Float, seed: Int): Float = if (direction.isNaN()) fxHash(seed, HEADING) * TAU else direction

    fun color(color: FxColor, index: Int): Color = when (color) {
        FxColor.A -> a
        FxColor.B -> b
        FxColor.BONE -> Kk.Bone
        FxColor.INK -> Kk.Ink
        FxColor.AB -> if (index % 2 == 0) a else b
        FxColor.YOU -> you
        FxColor.GOLD -> gold
        FxColor.GOLD_LIGHT -> goldLight
    }

    /** Whether [layer] belongs to this burst's form and level of detail (before the budget). */
    fun shows(layer: FxLayer): Boolean {
        val flags = layer.flags
        if (flags and FxFlag.KEY != 0 && !keystone) return false
        if (flags and FxFlag.BASE != 0 && keystone) return false
        val forced = keystone && flags and FxFlag.KEY_ALWAYS != 0
        if (flags and FxFlag.VERB != 0 && !verb && !forced) return false
        if (flags and FxFlag.NOT_VERB != 0 && verb && !forced) return false
        if (flags and FxFlag.RANK3 != 0 && rank < 3) return false
        if (flags and FxFlag.CORE_INSIDE != 0 && !coreInside) return false
        val signature = flags and FxFlag.SIG != 0
        if (signatureOnly && !signature) return false
        if (secondaryOff && flags and FxFlag.SEC != 0) return false
        return true
    }
}

/** Draws every layer of [spec] the context shows, within the [pool] budget. */
internal fun DrawScope.drawFxSpec(spec: ReactionFxSpec, pool: FxPool) {
    val layers = spec.layers
    val scale = FxFrame.countScale
    for (index in layers.indices) {
        val layer = layers[index]
        if (!FxContext.shows(layer)) continue
        val progress = FxContext.progress
        if (progress < layer.t0 || progress > layer.t1) continue
        val elements = elementsOf(layer, scale)
        if (!FxDrawBudget.take(pool, layer.ops(elements), layer.has(FxFlag.SIG))) continue
        drawFxLayer(layer, elements)
    }
}

private fun elementsOf(layer: FxLayer, scale: Float): Int {
    val count = layer.elements(FxContext.rank)
    return if (scale >= 1f) count else max(1, (count * scale).roundToInt())
}

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
    when (layer.primitive) {
        FxPrimitive.DISC -> {
            // Overdraw guard: a disc never passes 0.8 of the reach or 0.35 alpha.
            val radius = if (world) r else minOf(r, reach * 0.8f)
            drawCircle(ctx.color(layer.color, 0), radius, center, alpha = minOf(alpha, 0.35f))
        }
        FxPrimitive.RING -> drawCircle(ctx.color(layer.color, 0), r, center, alpha = alpha,
            style = if (layer.has(FxFlag.DOTTED)) WorldStrokes.dotted else kkStroke(width))
        FxPrimitive.ARCS -> {
            val sweep = layer.param
            val start = (theta + spin) / DEG
            val topLeft = Offset(cx - r, cy - r)
            val size = Size(r * 2f, r * 2f)
            for (i in 0 until elements) {
                drawArc(ctx.color(layer.color, i), start + i * 360f / elements - sweep * 0.5f, sweep, false, topLeft, size, alpha = alpha,
                    style = kkStroke(width))
            }
        }
        FxPrimitive.SPLIT_RING -> {
            val start = (theta + spin) / DEG
            val topLeft = Offset(cx - r, cy - r)
            val size = Size(r * 2f, r * 2f)
            drawArc(ctx.color(if (layer.color == FxColor.AB) FxColor.A else layer.color, 0), start, layer.param, false, topLeft, size,
                alpha = alpha, style = kkStroke(width))
            drawArc(ctx.color(if (layer.color == FxColor.AB) FxColor.B else layer.color, 1), start + 180f, layer.param, false, topLeft, size,
                alpha = alpha, style = kkStroke(width))
        }
        FxPrimitive.SPOKES -> for (i in 0 until elements) {
            val angle = fanAngle(layer, theta, i, elements) + spin + (fxHash(ctx.seed, i + 1) - 0.5f) * layer.param
            drawLine(ctx.color(layer.color, i), polar(center, inner, angle), polar(center, r, angle), width, alpha = alpha)
        }
        FxPrimitive.STREAKS_IN, FxPrimitive.STREAKS_OUT -> for (i in 0 until elements) {
            val angle = fanAngle(layer, theta, i, elements) + spin
            drawLine(ctx.color(layer.color, i), polar(center, r, angle), polar(center, r + length, angle), width, alpha = alpha)
        }
        FxPrimitive.SPIRAL -> {
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
        FxPrimitive.DOTS -> {
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
        FxPrimitive.DOTS_IN -> for (i in 0 until elements) {
            val angle = fxHash(ctx.seed, 2 * i + 2) * TAU + spin
            drawCircle(ctx.color(layer.color, i), layer.param, polar(center, r, angle), alpha = alpha)
        }
        FxPrimitive.SHARDS -> {
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
        FxPrimitive.FORKS -> {
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
        FxPrimitive.HEX -> {
            val color = ctx.color(layer.color, 0)
            val turn = theta + spin
            for (side in 0 until 6) {
                val from = polar(center, r, turn + side * TAU / 6f)
                val to = polar(center, r, turn + (side + 1) * TAU / 6f)
                drawLine(color, from, to, width, alpha = alpha)
            }
        }
        FxPrimitive.PLATE -> {
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
        FxPrimitive.CRACKS -> {
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
        FxPrimitive.FLAMES -> for (i in 0 until elements) {
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
        FxPrimitive.TICKS -> {
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
        FxPrimitive.BARS -> {
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
        FxPrimitive.QUAKE -> {
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
        FxPrimitive.WAVES -> {
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
        FxPrimitive.PUNCH -> {
            val color = ctx.color(layer.color, 0)
            drawCircle(Kk.Ink, r, center, alpha = alpha * 0.85f)
            drawCircle(color, r, center, alpha = alpha, style = kkStroke(width))
            drawCircle(color, max(0.5f, r * 0.6f), center, alpha = alpha, style = kkStroke(1.5f))
        }
        FxPrimitive.WIPE -> {
            val slab = layer.param * reach
            val offset = lerp(-reach, reach, e)
            rotate(KkShape.Slash, center) {
                drawRect(ctx.color(layer.color, 0), Offset(cx + offset - slab * 0.5f, cy - reach * 0.45f), Size(slab, reach * 0.9f), alpha = alpha)
            }
        }
        FxPrimitive.LANES -> {
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
    }
}

/** Vertices of a [FxPrimitive.QUAKE] ring. */
internal const val QUAKE_VERTICES = 12

/** Element [i]'s angle: a fan [FxLayer.param2]° apart around [theta], or evenly round the circle. */
private fun fanAngle(layer: FxLayer, theta: Float, i: Int, elements: Int): Float {
    val spread = layer.param2
    if (spread > 0f && layer.primitive != FxPrimitive.DOTS) return theta + (i - (elements - 1) * 0.5f) * spread * DEG
    return theta + i * TAU / elements
}

/** A jagged gravitic ring (the quake shockwaves, colour indices 16..22) with [seed]'s vertices. */
internal fun DrawScope.drawQuakeRing(center: Offset, radius: Float, seed: Int, color: Color, alpha: Float, width: Float) {
    var previous = Offset.Zero
    var first = Offset.Zero
    for (i in 0..QUAKE_VERTICES) {
        val vertex = i % QUAKE_VERTICES
        val distance = radius * (1f + 0.24f * (fxHash(seed, 60 + vertex) - 0.5f))
        val point = polar(center, distance, vertex * TAU / QUAKE_VERTICES)
        if (i == 0) first = point else drawLine(color, previous, if (vertex == 0) first else point, width, alpha = alpha)
        previous = point
    }
}
