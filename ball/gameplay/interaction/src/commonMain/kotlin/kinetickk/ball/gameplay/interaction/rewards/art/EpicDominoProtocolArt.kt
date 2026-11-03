// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin

// Domino Protocol.

private const val DominoGround = 422f
private const val DominoW = 26f
private const val DominoH = 54f
private const val DominoDepthX = 6f
private const val DominoDepthY = -5f
private const val DominoTiles = 6
private val DominoAngles = floatArrayOf(64f, 47f, 31f, 16f, 0f, 0f)

/** Pips of each tile: the falling one shows two and two, the five more each show five over another value. */
private val DominoPips = intArrayOf(2, 2, 5, 1, 5, 3, 5, 4, 5, 6, 5, 2)

/** Pivot x (each tile turns about its bottom right corner) of every tile, resting on its neighbour. */
private fun dominoPivots(into: FloatArray) {
    var pivot = 0f
    into[0] = 0f
    for (index in 0 until DominoTiles - 1) {
        val a = DominoAngles[index] * Deg
        val next = DominoAngles[index + 1] * Deg
        pivot = if (DominoAngles[index] == 0f) {
            pivot + DominoW + 16f
        } else {
            val tipX = pivot + DominoH * sin(a)
            val tipY = DominoGround - DominoH * cos(a)
            val along = (DominoGround - DominoW * sin(next) - tipY) / cos(next)
            tipX + DominoW * cos(next) - along * sin(next)
        }
        into[index + 1] = pivot
    }
    // Centre the row: from the first tile's lifted back corner to the last tile's depth.
    val left = into[0] - DominoW * cos(DominoAngles[0] * Deg)
    val right = into[DominoTiles - 1] + DominoDepthX
    val shift = 150f - (left + right) * 0.5f
    for (index in 0 until DominoTiles) into[index] += shift
}

/** Board point of tile [index] at local ([u] across from the back edge, [v] up from the base). */
private fun dominoX(pivots: FloatArray, index: Int, u: Float, v: Float): Float {
    val a = DominoAngles[index] * Deg
    return pivots[index] + (u - DominoW) * cos(a) + v * sin(a)
}

private fun dominoY(index: Int, u: Float, v: Float): Float {
    val a = DominoAngles[index] * Deg
    return DominoGround + (u - DominoW) * sin(a) - v * cos(a)
}

private fun Path.dominoFace(pivots: FloatArray, index: Int, dx: Float, dy: Float) {
    moveB(dominoX(pivots, index, 0f, 0f) + dx, dominoY(index, 0f, 0f) + dy)
    lineB(dominoX(pivots, index, DominoW, 0f) + dx, dominoY(index, DominoW, 0f) + dy)
    lineB(dominoX(pivots, index, DominoW, DominoH) + dx, dominoY(index, DominoW, DominoH) + dy)
    lineB(dominoX(pivots, index, 0f, DominoH) + dx, dominoY(index, 0f, DominoH) + dy)
    close()
}

/** The tile's whole box (its face and the face moved back by the depth) as one outline. */
private fun Path.dominoBody(pivots: FloatArray, index: Int) {
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    for (corner in 0 until 4) {
        val u = if (corner == 1 || corner == 2) DominoW else 0f
        val v = if (corner >= 2) DominoH else 0f
        xs[corner] = dominoX(pivots, index, u, v)
        ys[corner] = dominoY(index, u, v)
        xs[corner + 4] = xs[corner] + DominoDepthX
        ys[corner + 4] = ys[corner] + DominoDepthY
    }
    hullB(xs, ys)
}

/** The convex hull of the board points as one closed contour (gift wrapping, at build time only). */
private fun Path.hullB(xs: FloatArray, ys: FloatArray) {
    val count = xs.size
    var start = 0
    for (index in 1 until count) if (xs[index] < xs[start] || (xs[index] == xs[start] && ys[index] < ys[start])) start = index
    var current = start
    var steps = 0
    do {
        if (steps == 0) moveB(xs[current], ys[current]) else lineB(xs[current], ys[current])
        var next = (current + 1) % count
        for (index in 0 until count) {
            val cross = (xs[next] - xs[current]) * (ys[index] - ys[current]) - (ys[next] - ys[current]) * (xs[index] - xs[current])
            if (cross < 0f) next = index
        }
        current = next
        steps++
    } while (current != start && steps <= count)
    close()
}

private val PipU = floatArrayOf(0.28f, 0.5f, 0.72f)
private val PipV = floatArrayOf(0.25f, 0.5f, 0.75f)

/** Grid cells (column, row) of the pips of [value] on one half of a tile. */
private fun pipCells(value: Int): Int = when (value) {
    1 -> 0b000_010_000
    2 -> 0b100_000_001
    3 -> 0b100_010_001
    4 -> 0b101_000_101
    5 -> 0b101_010_101
    else -> 0b101_101_101
}

private fun Path.dominoPips(pivots: FloatArray) {
    for (index in 0 until DominoTiles) {
        for (half in 0 until 2) {
            val cells = pipCells(DominoPips[index * 2 + half])
            for (cell in 0 until 9) {
                if (cells and (1 shl (8 - cell)) == 0) continue
                val u = DominoW * PipU[cell % 3]
                val v = DominoH * 0.5f * (half + PipV[2 - cell / 3])
                ovalB(dominoX(pivots, index, u, v), dominoY(index, u, v), 2.3f, 2.3f)
            }
        }
    }
}

/**
 * Domino Protocol: six tiles on a ground line, the first one falling onto the next and the fall
 * running down the row, each resting on its neighbour, the first tile's swing traced as a dashed
 * arc: the falling one shows two and two, the five more each show five.
 */
internal fun DrawScope.drawDominoProtocolArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 128f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val dash = cache.stroke(b.l(1.8f), StrokeCap.Round)
    val pivots = cache.floats(0, DominoTiles) { dominoPivots(it) }
    fadedArt(frame, 150f, 404f, 186f, 128f, 0.55f) {
        drawRect(Kk.Ink, b.at(20f, DominoGround), Size(b.l(260f), b.l(10f)), alpha = 0.5f)
        lineB(hue, 22f, DominoGround, 278f, DominoGround, b.hair, 0.14f)
        drawPath(cache.path(0) {
            val pivot = pivots[0]
            for (index in 0 until 7) {
                val start = 270f + index * 9f
                arcB(pivot, DominoGround, DominoH, DominoH, start, 5f, true)
            }
        }, hue, alpha = 0.2f, style = dash)
        for (index in 0 until DominoTiles) {
            val body = cache.path(1 + index * 2) { dominoBody(pivots, index) }
            val face = cache.path(2 + index * 2) { dominoFace(pivots, index, 0f, 0f) }
            drawPath(body, Kk.Ink, alpha = 0.92f)
            drawPath(body, hue, alpha = 0.05f)
            drawPath(face, Kk.Ink, alpha = 0.9f)
            drawPath(face, cache.brush(index) {
                lightBrush(hue, dominoX(pivots, index, 0f, DominoH), dominoY(index, 0f, DominoH), dominoX(pivots, index, DominoW, 0f), dominoY(index, DominoW, 0f),
                    if (index == 0) 0.18f else 0.14f, 0.035f)
            })
            drawPath(face, hue, alpha = if (index == 0) 0.3f else 0.22f, style = line)
        }
        drawPath(cache.path(14) {
            for (index in 0 until DominoTiles) {
                segB(dominoX(pivots, index, 4f, DominoH * 0.5f), dominoY(index, 4f, DominoH * 0.5f),
                    dominoX(pivots, index, DominoW - 4f, DominoH * 0.5f), dominoY(index, DominoW - 4f, DominoH * 0.5f))
            }
        }, hue, alpha = 0.16f, style = hair)
        drawPath(cache.path(15) { dominoPips(pivots) }, hue, alpha = 0.3f)
    }
}
