// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Shatterglass: frozen shards bursting outward (straight cut ends). */
internal fun DrawScope.drawShatterglassArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.6f, 0.6f, 1.05f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val crack = cache.floats(0, ShatterCracks * ShatterJoints * 2) { shatterCrackPoints(it, frame, cx, cy, r) }
    val pieces = cache.floats(1, ShatterPieces * 9) { shatterPieces(it, crack, frame, cx, cy, r) }
    val lit = cache.path(0) { shatterPieceFills(pieces, 0) }
    val mid = cache.path(1) { shatterPieceFills(pieces, 1) }
    val dim = cache.path(2) { shatterPieceFills(pieces, 2) }
    val edges = cache.path(3) { shatterPieceFills(pieces, -1) }
    val facets = cache.path(4) {
        for (piece in 0 until ShatterPieces) {
            val b = piece * 9
            moveTo(pieces[b + 2], pieces[b + 3])
            lineTo((pieces[b + 4] + pieces[b + 6]) * 0.5f, (pieces[b + 5] + pieces[b + 7]) * 0.5f)
        }
    }
    val runs = cache.path(5) {
        // The cracks run on past the burst, kinking once more.
        for (k in 0 until ShatterCracks) {
            val end = (k * ShatterJoints + ShatterJoints - 1) * 2
            val x = crack[end]
            val y = crack[end + 1]
            val dx = x - cx
            val dy = y - cy
            val turn = (if (k % 2 == 0) 0.12f else -0.12f)
            moveTo(x, y)
            lineTo(x + dx * 0.22f - dy * turn * 0.3f, y + dy * 0.22f + dx * turn * 0.3f)
            lineTo(x + dx * 0.42f - dy * turn * 0.1f, y + dy * 0.42f + dx * turn * 0.1f)
        }
    }
    val chips = cache.path(6) {
        for (index in 0 until 10) {
            val angle = (index * 36f + frame.random(30 + index) * 20f) * DEG
            val reach = r * (1.12f + 0.32f * frame.random(40 + index))
            val size = r * (0.026f + 0.026f * frame.random(50 + index))
            val c = cos(angle + 0.7f)
            val s = sin(angle + 0.7f)
            val ox = cx + reach * cos(angle)
            val oy = cy + reach * sin(angle)
            local(true, ox, oy, c, s, -size, -size * 0.6f)
            local(false, ox, oy, c, s, size, -size * 0.3f)
            local(false, ox, oy, c, s, size * 0.7f, size * 0.6f)
            local(false, ox, oy, c, s, -size * 0.8f, size * 0.4f)
            close()
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy, r * 1.55f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy, r * 1.3f, 0.35f) }
    drawPath(dim, hue, alpha = alphaOf(RewardArtAlpha.LOW * 0.7f, g))
    drawPath(mid, hue, alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(lit, hue, alpha = alphaOf(RewardArtAlpha.MID * 1.15f, g))
    drawPath(lit, bone, alpha = RewardArtAlpha.LOW * 0.6f)
    drawPath(facets, bone, alpha = RewardArtAlpha.LOW, style = cache.stroke(frame.u(0.8f)))
    drawPath(edges, hue, alpha = alphaOf(RewardArtAlpha.MID * 1.2f, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(runs, hue, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1f)))
    drawPath(chips, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawCircle(bone, r * 0.05f, Offset(cx, cy), alpha = RewardArtAlpha.HIGH)
}

// region Shatterglass

private const val ShatterCracks = 8
private const val ShatterJoints = 4
private val ShatterAngles = floatArrayOf(-168f, -124f, -80f, -36f, 6f, 46f, 88f, 130f)
private val ShatterReach = floatArrayOf(1.0f, 0.8f, 1.1f, 0.9f, 1.18f, 0.94f, 1.06f, 0.84f)
private val ShatterJointAt = floatArrayOf(0.06f, 0.34f, 0.62f, 1f)
private val ShatterWobble = floatArrayOf(0f, 4f, -5f, 3f)

/** Per crack, its joints from the impact outward (x, y each). */
private fun shatterCrackPoints(out: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, r: Float) {
    for (k in 0 until ShatterCracks) {
        val base = ShatterAngles[k] + (frame.random(k) - 0.5f) * 10f
        for (j in 0 until ShatterJoints) {
            val angle = (base + ShatterWobble[j] * (if (k % 2 == 0) 1f else -1f)) * DEG
            val reach = r * ShatterReach[k] * ShatterJointAt[j] * (if (j in 1..2) 0.94f + 0.12f * frame.random(10 + k * 4 + j) else 1f)
            val index = (k * ShatterJoints + j) * 2
            out[index] = cx + reach * cos(angle)
            out[index + 1] = cy + reach * sin(angle)
        }
    }
}

private const val ShatterPieces = ShatterCracks * (ShatterJoints - 1)

/**
 * Per piece of the burst (between two cracks and two rings): its four corners, pulled apart from
 * its neighbours and pushed outward the further out it sits (some outer pieces fly loose and turn),
 * and its tone (0 lit, 1 mid, 2 dim facet).
 */
private fun shatterPieces(out: FloatArray, crack: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, r: Float) {
    for (k in 0 until ShatterCracks) for (band in 0 until ShatterJoints - 1) {
        val next = (k + 1) % ShatterCracks
        val a = (k * ShatterJoints + band) * 2
        val b = (next * ShatterJoints + band) * 2
        val c = (next * ShatterJoints + band + 1) * 2
        val d = (k * ShatterJoints + band + 1) * 2
        val mx = (crack[a] + crack[b] + crack[c] + crack[d]) * 0.25f
        val my = (crack[a + 1] + crack[b + 1] + crack[c + 1] + crack[d + 1]) * 0.25f
        val dx = mx - cx
        val dy = my - cy
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val loose = band == ShatterJoints - 2 && frame.random(300 + k) < 0.35f
        val push = r * (0.015f + 0.03f * band + (if (loose) 0.14f else 0f))
        val spin = if (loose) (frame.random(310 + k) - 0.5f) * 0.6f else 0f
        val cs = cos(spin)
        val sn = sin(spin)
        val ox = mx + dx / length * push
        val oy = my + dy / length * push
        val base = (k * (ShatterJoints - 1) + band) * 9
        var slot = 0
        for (corner in intArrayOf(a, b, c, d)) {
            val vx = (crack[corner] - mx) * 0.88f
            val vy = (crack[corner + 1] - my) * 0.88f
            out[base + slot] = ox + vx * cs - vy * sn
            out[base + slot + 1] = oy + vx * sn + vy * cs
            slot += 2
        }
        out[base + 8] = (frame.random(320 + k * 3 + band) * 2.999f).toInt().toFloat()
    }
}

/** The pieces of [tone] (all of them for −1). */
private fun Path.shatterPieceFills(f: FloatArray, tone: Int) {
    for (piece in 0 until ShatterPieces) {
        val b = piece * 9
        if (tone >= 0 && f[b + 8].toInt() != tone) continue
        moveTo(f[b], f[b + 1])
        lineTo(f[b + 2], f[b + 3])
        lineTo(f[b + 4], f[b + 5])
        lineTo(f[b + 6], f[b + 7])
        close()
    }
}

// endregion
