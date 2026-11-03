// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.sqrt

/** Storm Lattice: a lightning-filled triangular lattice. */
internal fun DrawScope.drawStormLatticeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.55f, 0.56f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val pitch = r * 0.44f
    val nodes = cache.floats(0, LatticeCols * LatticeRows * 2) { out ->
        for (j in 0 until LatticeRows) for (i in 0 until LatticeCols) {
            val row = j - LatticeRows / 2
            val base = (j * LatticeCols + i) * 2
            out[base] = cx + (i - LatticeCols / 2 + 0.5f * (row and 1)) * pitch
            out[base + 1] = cy + row * pitch * SQRT3_HALF
        }
    }
    val edges = cache.path(0) {
        for (j in 0 until LatticeRows) for (i in 0 until LatticeCols) {
            val row = j - LatticeRows / 2
            if (i + 1 < LatticeCols) segment(nodes, i, j, i + 1, j)
            if (j + 1 < LatticeRows) {
                val left = if (row and 1 == 0) i - 1 else i
                if (left in 0 until LatticeCols) segment(nodes, i, j, left, j + 1)
                if (left + 1 in 0 until LatticeCols) segment(nodes, i, j, left + 1, j + 1)
            }
        }
    }
    val dots = cache.path(1) {
        for (index in 0 until LatticeCols * LatticeRows) {
            val half = pitch * 0.045f
            diamond(nodes[index * 2], nodes[index * 2 + 1], half)
        }
    }
    val cells = cache.path(2) {
        for (index in LatticeCells.indices step 6) {
            moveTo(node(nodes, LatticeCells[index], LatticeCells[index + 1], 0), node(nodes, LatticeCells[index], LatticeCells[index + 1], 1))
            lineTo(node(nodes, LatticeCells[index + 2], LatticeCells[index + 3], 0), node(nodes, LatticeCells[index + 2], LatticeCells[index + 3], 1))
            lineTo(node(nodes, LatticeCells[index + 4], LatticeCells[index + 5], 0), node(nodes, LatticeCells[index + 4], LatticeCells[index + 5], 1))
            close()
        }
    }
    val bolt = cache.path(3) {
        bolt(nodes, LatticeBolt, pitch, frame, 0)
        bolt(nodes, LatticeForkA, pitch, frame, 20)
        bolt(nodes, LatticeForkB, pitch, frame, 40)
        bolt(nodes, LatticeForkC, pitch, frame, 60)
    }
    val joints = cache.path(4) {
        for (index in LatticeBolt.indices step 2) {
            diamond(node(nodes, LatticeBolt[index], LatticeBolt[index + 1], 0), node(nodes, LatticeBolt[index], LatticeBolt[index + 1], 1), pitch * 0.09f)
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy, r * 1.6f, 0.4f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy, r * 1.35f, 0.4f) }
    val hubX = node(nodes, LatticeBolt[6], LatticeBolt[7], 0)
    val hubY = node(nodes, LatticeBolt[6], LatticeBolt[7], 1)
    val charge = cache.brush(2) { glowBrush(frame.hue, hubX, hubY, pitch * 1.3f) }
    drawCircle(charge, pitch * 1.3f, Offset(hubX, hubY), alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(cells, hue, alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(edges, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f)))
    drawPath(dots, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(bolt, hue, alpha = alphaOf(RewardArtAlpha.LOW, g), style = cache.stroke(frame.u(6f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(bolt, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.2f), StrokeCap.Round, StrokeJoin.Miter))
    drawPath(bolt, bone, alpha = RewardArtAlpha.MID, style = cache.stroke(frame.u(0.8f), StrokeCap.Round, StrokeJoin.Miter))
    drawPath(joints, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

// region Storm Lattice

private const val LatticeCols = 9
private const val LatticeRows = 7

/** Lattice nodes as (column, row) pairs: the main bolt, upper left to lower right through the center. */
private val LatticeBolt = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 5, 6, 6)
private val LatticeForkA = intArrayOf(3, 3, 4, 3, 4, 2, 5, 2)
private val LatticeForkB = intArrayOf(5, 5, 4, 6)
private val LatticeForkC = intArrayOf(4, 4, 5, 4, 6, 4, 7, 3)

/** Charged cells (three nodes each), filled faintly along the bolt. */
private val LatticeCells = intArrayOf(
    1, 2, 2, 2, 2, 3,
    3, 4, 4, 4, 4, 3,
    5, 5, 6, 5, 5, 6,
    3, 3, 4, 3, 3, 2,
    5, 4, 6, 4, 6, 5,
)

private fun node(nodes: FloatArray, i: Int, j: Int, axis: Int): Float = nodes[(j * LatticeCols + i) * 2 + axis]

private fun Path.segment(nodes: FloatArray, i0: Int, j0: Int, i1: Int, j1: Int) {
    moveTo(node(nodes, i0, j0, 0), node(nodes, i0, j0, 1))
    lineTo(node(nodes, i1, j1, 0), node(nodes, i1, j1, 1))
}

/** A lightning bolt along lattice edges: every edge kinks twice across its line. */
private fun Path.bolt(nodes: FloatArray, route: IntArray, pitch: Float, frame: RewardArtFrame, salt: Int) {
    var x0 = node(nodes, route[0], route[1], 0)
    var y0 = node(nodes, route[0], route[1], 1)
    moveTo(x0, y0)
    for (index in 2 until route.size step 2) {
        val x1 = node(nodes, route[index], route[index + 1], 0)
        val y1 = node(nodes, route[index], route[index + 1], 1)
        val dx = x1 - x0
        val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val nx = -dy / length
        val ny = dx / length
        val kick = pitch * (0.1f + 0.08f * frame.random(salt + index))
        val sign = if ((index / 2 + salt) % 2 == 0) 1f else -1f
        lineTo(x0 + dx * 0.36f + nx * kick * sign, y0 + dy * 0.36f + ny * kick * sign)
        lineTo(x0 + dx * 0.64f - nx * kick * sign, y0 + dy * 0.64f - ny * kick * sign)
        lineTo(x1, y1)
        x0 = x1
        y0 = y1
    }
}

// endregion
