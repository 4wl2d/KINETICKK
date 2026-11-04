// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Phoenix Coil: phoenix wing feathers spiralling into a coil. Short rounded coverts lap round the
 * coil's outer turn, and from its end a wing of long primaries opens over a 120° sweep, longest at
 * the leading edge; each feather has a shaft and a rounded, slightly split tip. Embers rise past the
 * wing tips.
 */
internal fun DrawScope.drawPhoenixCoilArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.5f, 0.56f, 1.0f)
    val r = spot[2]
    val g = spot[3]
    // The coil sits left of and below the spot; the wing opens to its right.
    val cx = spot[0] - r * 0.5f
    val cy = spot[1] + r * 0.22f
    val cache = frame.cache
    val feathers = cache.floats(0, PhoenixAll * 6) { phoenixFeatherFrames(it, cx, cy, r) }
    val coverts = cache.path(0) { phoenixFeathers(feathers, 0, PhoenixCoverts) }
    val primaries = cache.path(1) { phoenixFeathers(feathers, PhoenixCoverts, PhoenixAll) }
    val shafts = cache.path(2) { phoenixQuills(feathers) }
    val leading = cache.path(3) { phoenixLeadingEdges(feathers) }
    val coil = cache.path(4) { phoenixCoil(cx, cy, r, 1f) }
    val rail = cache.path(5) { phoenixCoil(cx, cy, r, 0.72f) }
    val embers = cache.path(6) {
        // Rising past the wing tips, smaller as they climb.
        for (index in 0 until PhoenixEmbers) {
            val x = cx + r * (1.0f + 0.2f * index + 0.1f * frame.random(index))
            val y = cy - r * (0.7f + 0.16f * index + 0.08f * frame.random(10 + index))
            diamond(x, y, r * (0.04f - index * 0.006f))
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx + r * 0.5f, cy, r * 1.9f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx + r * 0.4f, cy, r * 1.6f, 0.3f) }
    val heart = cache.brush(2) { glowBrush(frame.hue, cx, cy, r * 0.42f) }
    val edge = cache.stroke(frame.u(1.1f))
    drawCircle(heart, r * 0.42f, Offset(cx, cy), alpha = alphaOf(RewardArtAlpha.MID, g))
    // Back to front: the primaries, then the coverts lapping over their roots.
    drawPath(primaries, hue, alpha = alphaOf(RewardArtAlpha.LOW * 1.1f, g))
    drawPath(primaries, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = edge)
    drawPath(coverts, Kk.Ink2, alpha = 0.55f)
    drawPath(coverts, hue, alpha = alphaOf(RewardArtAlpha.LOW * 1.3f, g))
    drawPath(coverts, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = edge)
    drawPath(shafts, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(0.9f), StrokeCap.Round))
    drawPath(leading, bone, alpha = RewardArtAlpha.MID, style = cache.stroke(frame.u(1.2f), StrokeCap.Round))
    drawPath(rail, hue, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1.2f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(coil, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.6f), StrokeCap.Round, StrokeJoin.Round))
    drawCircle(hue, r * 0.11f, Offset(cx, cy), alpha = alphaOf(RewardArtAlpha.LOW, g))
    drawCircle(bone, r * 0.04f, Offset(cx, cy), alpha = RewardArtAlpha.HIGH)
    drawPath(embers, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

// region Phoenix Coil

/** Coverts along the coil's outer turn, then the primaries of the wing. */
private const val PhoenixCoverts = 7
private const val PhoenixPrimaries = 7
private const val PhoenixAll = PhoenixCoverts + PhoenixPrimaries
private const val PhoenixEmbers = 4

/** The coil turns clockwise and ends at its upper right, heading down and right, where the wing opens. */
private const val PhoenixTurn = 1.6f * 3.1415927f
private const val PhoenixEnd = -0.25f * 3.1415927f

/** How far a covert turns out from the coil's tangent (radians): low, so they overlap like scales. */
private const val PhoenixLean = 0.55f

/** The wing's sweep (radians) and the heading of its leading primary (up and to the right). */
private const val WingSweep = 2.0943951f
private const val WingLead = -0.62f

/** Primary lengths (share of the reach), leading edge first: longest near the lead, short at the trailing end. */
private val PrimaryLength = floatArrayOf(0.86f, 1.02f, 1.06f, 0.98f, 0.86f, 0.72f, 0.58f)

private fun phoenixRho(t: Float, r: Float): Float = r * 0.16f * exp(1.5f * t)

private fun phoenixTheta(t: Float): Float = PhoenixEnd - PhoenixTurn * (1f - t)

/** Per feather: base x, y, cos and sin of its heading, length and width (coverts first, then primaries). */
private fun phoenixFeatherFrames(out: FloatArray, cx: Float, cy: Float, r: Float) {
    for (index in 0 until PhoenixCoverts) {
        // Short, broad and rounded, growing along the coil's outer turn.
        val t = 0.25f + 0.7f * index / (PhoenixCoverts - 1f)
        val rho = phoenixRho(t, r)
        val theta = phoenixTheta(t)
        val heading = theta + HALF_PI - PhoenixLean
        val length = r * 0.14f + rho * 0.42f
        val base = index * 6
        out[base] = cx + rho * cos(theta)
        out[base + 1] = cy + rho * sin(theta)
        out[base + 2] = cos(heading)
        out[base + 3] = sin(heading)
        out[base + 4] = length
        out[base + 5] = length * 0.42f
    }
    // The wing's root: the coil's outer end, the roots spread a little along the wing's arm.
    val endRho = phoenixRho(1f, r)
    val rootX = cx + endRho * cos(PhoenixEnd)
    val rootY = cy + endRho * sin(PhoenixEnd)
    for (index in 0 until PhoenixPrimaries) {
        val share = index / (PhoenixPrimaries - 1f)
        val heading = WingLead + WingSweep * share
        val length = r * PrimaryLength[index]
        val base = (PhoenixCoverts + index) * 6
        out[base] = rootX + r * (0.04f + 0.16f * share)
        out[base + 1] = rootY + r * 0.16f * share
        out[base + 2] = cos(heading)
        out[base + 3] = sin(heading)
        out[base + 4] = length
        // Long primaries are slender; the short trailing ones a little broader.
        out[base + 5] = length * (0.15f + 0.07f * share)
    }
}

/** The lit leading edge of every primary. */
private fun Path.phoenixLeadingEdges(f: FloatArray) {
    for (index in PhoenixCoverts until PhoenixAll) {
        val b = index * 6
        local(true, f[b], f[b + 1], f[b + 2], f[b + 3], 0.3f * f[b + 4], -0.48f * f[b + 5])
        local(false, f[b], f[b + 1], f[b + 2], f[b + 3], 0.86f * f[b + 4], -0.4f * f[b + 5])
    }
}

/**
 * Feathers [from] until [to]: a vane swelling from the quill to a rounded tip with a small split in it
 * (never a point, which would read as an arrowhead).
 */
private fun Path.phoenixFeathers(f: FloatArray, from: Int, to: Int) {
    for (index in from until to) {
        val b = index * 6
        val ox = f[b]
        val oy = f[b + 1]
        val c = f[b + 2]
        val s = f[b + 3]
        val l = f[b + 4]
        val w = f[b + 5]
        local(true, ox, oy, c, s, 0f, -0.14f * w)
        localQuad(ox, oy, c, s, 0.22f * l, -0.56f * w, 0.62f * l, -0.5f * w)
        localQuad(ox, oy, c, s, 1.02f * l, -0.42f * w, 0.99f * l, -0.04f * w)
        // The split: a small notch just behind the tip's middle.
        local(false, ox, oy, c, s, 0.9f * l, 0.04f * w)
        localQuad(ox, oy, c, s, 0.95f * l, 0.5f * w, 0.6f * l, 0.5f * w)
        localQuad(ox, oy, c, s, 0.2f * l, 0.52f * w, 0f, 0.14f * w)
        close()
    }
}

/** The shaft of every feather, and barbs angling from it across the larger vanes. */
private fun Path.phoenixQuills(f: FloatArray) {
    for (index in 0 until PhoenixAll) {
        val b = index * 6
        val ox = f[b]
        val oy = f[b + 1]
        val c = f[b + 2]
        val s = f[b + 3]
        val l = f[b + 4]
        val w = f[b + 5]
        local(true, ox, oy, c, s, 0.02f * l, 0f)
        local(false, ox, oy, c, s, 0.86f * l, 0.02f * w)
        if (index >= PhoenixCoverts) {
            for (barb in 0 until 4) {
                val at = 0.28f + barb * 0.15f
                local(true, ox, oy, c, s, at * l, 0.02f * w)
                local(false, ox, oy, c, s, (at + 0.08f) * l, 0.4f * w)
                local(true, ox, oy, c, s, (at + 0.05f) * l, -0.02f * w)
                local(false, ox, oy, c, s, (at + 0.12f) * l, -0.38f * w)
            }
        }
    }
}

/** The coil the feathers sit on, wound tighter inward ([scale] draws a second rail inside it). */
private fun Path.phoenixCoil(cx: Float, cy: Float, r: Float, scale: Float) {
    val steps = 84
    for (step in 0..steps) {
        val t = -1f + 2f * step / steps
        val rho = phoenixRho(t, r) * scale
        val theta = phoenixTheta(t)
        val x = cx + rho * cos(theta)
        val y = cy + rho * sin(theta)
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
}

// endregion
