// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.kkLerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Board box of the shatter (Report board: 420 x 420, center 210, drawn at 80 %). */
internal const val ShatterBox = 420f
internal const val ShatterCenter = 210f

/** One wedge shard: polygon in board units, fly-out offset/rotation and its delay. */
internal class ReportShard(
    val points: FloatArray,
    val centroid: Offset,
    val dx: Float,
    val dy: Float,
    val rotation: Float,
    val delay: Float,
    val solid: Boolean,
    val darkInner: Boolean,
)

/** One debris chip flying out and staying at its offset. */
internal class ReportDebris(
    val dx: Float,
    val dy: Float,
    val rotation: Float,
    val opacity: Float,
    val delay: Float,
    val size: Float,
    val bone: Boolean,
)

internal class ReportShatterShape(val shards: List<ReportShard>, val debris: List<ReportDebris>)

/**
 * The Report board's seeded shatter: 11 wedges between an outer rim (the Architect's diamond
 * lattice on victory, the round Core on defeat) and a ragged inner ring, flying 16–56 px out.
 * Deterministic for a given outcome (seed 7 victory, 19 defeat).
 */
internal fun reportShatterShape(victory: Boolean): ReportShatterShape {
    var seed = if (victory) 7L else 19L
    fun rnd(): Float {
        seed = (seed * 16_807L) % 2_147_483_647L
        return ((seed - 1).toDouble() / 2_147_483_646.0).toFloat()
    }
    val tau = (PI * 2).toFloat()
    val r = 168f
    val ri = 44f
    val n = 11
    fun rad(t: Float) = if (victory) r / (abs(cos(t)) + abs(sin(t))) else r
    fun pt(t: Float, radius: Float) = floatArrayOf(ShatterCenter + cos(t) * radius, ShatterCenter + sin(t) * radius)
    class Bound(val t: Float, val ri: Float)
    val bounds = List(n) { index ->
        val t = (index + (rnd() - 0.5f) * 0.55f) / n * tau - (PI / 2).toFloat()
        Bound(t, ri * (0.7f + rnd() * 0.6f))
    }
    val corners = if (victory) listOf(0f, (PI / 2).toFloat(), PI.toFloat(), (PI * 1.5).toFloat()) else emptyList()
    val shards = bounds.mapIndexed { index, bound ->
        val next = bounds[(index + 1) % n]
        val t0 = bound.t
        var t1 = next.t
        if (t1 <= t0) t1 += tau
        val outer = mutableListOf(pt(t0, rad(t0)))
        if (victory) {
            corners.forEach { c -> listOf(c, c + tau, c - tau).forEach { cc -> if (cc > t0 && cc < t1) outer += pt(cc, r) } }
        } else {
            for (k in 1 until 3) {
                val t = t0 + (t1 - t0) * k / 3f
                outer += pt(t, r * (0.96f + rnd() * 0.08f))
            }
        }
        outer += pt(t1, rad(t1))
        if (victory) {
            outer.sortBy { p -> ((atan2(p[1] - ShatterCenter, p[0] - ShatterCenter) - t0 + tau * 2) % tau) }
        }
        val inner = listOf(pt(t1, next.ri), pt((t0 + t1) / 2f, (bound.ri + next.ri) / 2f * (0.8f + rnd() * 0.4f)), pt(t0, bound.ri))
        val polygon = outer + inner
        val cx = polygon.sumOf { it[0].toDouble() }.toFloat() / polygon.size
        val cy = polygon.sumOf { it[1].toDouble() }.toFloat() / polygon.size
        val tm = (t0 + t1) / 2f
        val dist = (if (victory) 16f else 22f) + rnd() * (if (victory) 22f else 34f)
        val solid = rnd() < 0.3f
        val rotation = (rnd() - 0.5f) * (if (victory) 16f else 26f)
        val delay = 0.32f + rnd() * 0.12f
        val darkInner = if (solid) false else rnd() < 0.5f
        ReportShard(
            points = FloatArray(polygon.size * 2) { i -> polygon[i / 2][i % 2] },
            centroid = Offset(cx, cy),
            dx = cos(tm) * dist,
            dy = sin(tm) * dist,
            rotation = rotation,
            delay = delay,
            solid = solid,
            darkInner = darkInner,
        )
    }
    val debris = List(18) { index ->
        val t = rnd() * tau
        val d = 190f + rnd() * 90f
        ReportDebris(
            dx = cos(t) * d,
            dy = sin(t) * d,
            rotation = rnd() * 360f,
            opacity = 0.35f + rnd() * 0.55f,
            delay = 0.34f + rnd() * 0.2f,
            size = 4f + (rnd() * 6f).roundToInt(),
            bone = index % 3 == 0,
        )
    }
    return ReportShatterShape(shards, debris)
}

/** A shard's polygon as a path in board units (built once per shape). */
internal fun ReportShard.path(): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    close()
}

/** Fly-out amount 0..1 (with the 8 % recoil) and rotation share at [t] seconds after [delay]. */
internal fun shardFlight(t: Float, delay: Float): Float {
    val f = ((t - delay) / 1.25f).coerceIn(0f, 1f)
    return if (f < 0.08f) -0.04f * KkEase.Out.transform(f / 0.08f) else -0.04f + 1.04f * KkEase.Out.transform((f - 0.08f) / 0.92f)
}

/** Slow float after landing (6 s ease-in-out, alternating), 0..1. */
internal fun shardFloat(t: Float, delay: Float): Float {
    val u = t - delay - 1.25f
    if (u <= 0f) return 0f
    val cycle = floor(u / 6f).toInt()
    val frac = (u - cycle * 6f) / 6f
    val phase = if (cycle % 2 == 0) frac else 1f - frac
    return (1f - cos(phase * PI.toFloat())) * 0.5f
}

/**
 * Draws the shattered rim: every shard is the [accent] polygon with an inset ink face (solid
 * shards stay filled). [paths] are the shards' board-unit paths; [t] seconds since the shatter
 * started; [scale] board units to px around [center]; [spread] multiplies the fly-out distance;
 * [alpha] fades the whole rim. Allocation-free (transforms only).
 */
internal fun DrawScope.drawShatterShards(
    shape: ReportShatterShape,
    paths: List<Path>,
    center: Offset,
    scale: Float,
    t: Float,
    accent: Color,
    spread: Float = 1f,
    alpha: Float = 1f,
    floating: Boolean = true,
) {
    translate(center.x - ShatterCenter, center.y - ShatterCenter) {
        scale(scale, scale, Offset(ShatterCenter, ShatterCenter)) {
            for (index in shape.shards.indices) {
                val shard = shape.shards[index]
                val flight = shardFlight(t, shard.delay)
                val float = if (floating) shardFloat(t, shard.delay) else 0f
                val move = flight * spread * (1f + 0.08f * float)
                val turn = shard.rotation * flight.coerceAtLeast(0f) * (1f + 0.25f * float)
                translate(shard.dx * move, shard.dy * move) {
                    rotate(turn, shard.centroid) {
                        val path = paths[index]
                        drawPath(path, accent, alpha)
                        if (!shard.solid) {
                            scale(0.86f, 0.86f, shard.centroid) {
                                drawPath(path, if (shard.darkInner) Kk.Ink2 else Kk.Ink1, alpha)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Debris chips flying out 190–280 board units and staying (opacity per chip). */
internal fun DrawScope.drawShatterDebris(
    shape: ReportShatterShape,
    center: Offset,
    scale: Float,
    t: Float,
    accent: Color,
    count: Int = shape.debris.size,
    spread: Float = 1f,
    alpha: Float = 1f,
) {
    for (index in 0 until count.coerceAtMost(shape.debris.size)) {
        val chip = shape.debris[index]
        val f = ((t - chip.delay) / 1.6f).coerceIn(0f, 1f)
        if (f <= 0f) continue
        val eased = KkEase.Out.transform(f)
        val opacity = if (f < 0.06f) f / 0.06f else kkLerp(1f, chip.opacity, (f - 0.06f) / 0.94f)
        val x = center.x + chip.dx * eased * scale * spread
        val y = center.y + chip.dy * eased * scale * spread
        val half = chip.size * scale * 0.5f
        rotate(45f + chip.rotation * eased, Offset(x, y)) {
            drawRect(if (chip.bone) Kk.Bone else accent, Offset(x - half, y - half), Size(half * 2f, half * 2f), alpha = opacity * alpha)
        }
    }
}
