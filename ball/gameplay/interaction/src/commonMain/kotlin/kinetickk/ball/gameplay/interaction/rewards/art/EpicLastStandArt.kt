// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin

// Last Stand.

private const val StandGround = 428f
private const val StandLean = 6f
private const val StandCut = 12f
private val StandX = floatArrayOf(32f, 76f, 123f, 177f, 221f)
private val StandWidth = floatArrayOf(48f, 48f, 54f, 48f, 48f)
private val StandHeight = floatArrayOf(96f, 106f, 120f, 106f, 96f)

/** Back to front: the outer shields, the inner ones, the cracked centre last. */
private val StandOrder = intArrayOf(0, 4, 1, 3, 2)

private fun standX(index: Int, u: Float, v: Float): Float = StandX[index] + u + StandLean * v / StandHeight[index]
private fun standY(v: Float): Float = StandGround - v

/** Shield [index]'s outline inset by [inset] (local u across, v up); the centre one has its top corner broken off. */
private fun Path.standShield(index: Int, inset: Float) {
    val w = StandWidth[index]
    val h = StandHeight[index]
    val c = StandCut
    val d = inset
    fun at(u: Float, v: Float, first: Boolean = false) = if (first) moveB(standX(index, u, v), standY(v)) else lineB(standX(index, u, v), standY(v))
    at(d + 3f, d, first = true)
    at(w - d - 3f, d)
    at(w - d, d + 3f)
    if (index == 2 && d == 0f) {
        at(w, h - c - 12f)
        at(w - 7f, h - c - 6f)
        at(w - 5f, h - c + 1f)
        at(w - 13f, h - 5f)
        at(w - c - 10f, h)
    } else {
        at(w - d, h - c - d * 0.4f)
        at(w - c - d * 0.4f, h - d)
    }
    at(c + d * 0.4f, h - d)
    at(d, h - c - d * 0.4f)
    at(d, d + 3f)
    close()
}

/**
 * Last Stand: a wall of five tower shields standing on the ground line, the tall one in the
 * middle in front, struck, cracked from a dent and missing a corner, still standing.
 */
internal fun DrawScope.drawLastStandArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 410f - 140f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val crack = cache.stroke(b.l(1.3f), StrokeCap.Round, StrokeJoin.Round)
    val light = cache.brush(0) { lightBrush(hue, 0f, StandGround - 120f, 0f, StandGround, 0.12f, 0.025f) }
    val struck = cache.brush(1) { lightBrush(hue, 0f, StandGround - 120f, 0f, StandGround, 0.18f, 0.035f) }
    fadedArt(frame, 150f, 410f, 186f, 140f, 0.55f) {
        drawRect(Kk.Ink, b.at(24f, StandGround), Size(b.l(252f), b.l(12f)), alpha = 0.5f)
        lineB(hue, 22f, StandGround, 278f, StandGround, b.hair, 0.14f)
        for (order in 0 until 5) {
            val index = StandOrder[order]
            val shield = cache.path(index * 3) { standShield(index, 0f) }
            drawPath(shield, Kk.Ink, alpha = 0.92f)
            val outer = index == 0 || index == 4
            if (index == 2) drawPath(shield, struck) else drawPath(shield, light, alpha = if (outer) 0.6f else 0.85f)
            drawPath(shield, hue, alpha = if (index == 2) RewardArtAlpha.HIGH else if (outer) 0.15f else 0.19f, style = line)
            drawPath(cache.path(index * 3 + 1) {
                standShield(index, 5f)
                val w = StandWidth[index]
                val h = StandHeight[index]
                segB(standX(index, w * 0.5f, 9f), standY(9f), standX(index, w * 0.5f, h - 9f), standY(h - 9f))
                segB(standX(index, 5f, h * 0.62f), standY(h * 0.62f), standX(index, w - 5f, h * 0.62f), standY(h * 0.62f))
                for (corner in 0 until 4) {
                    val u = if (corner % 2 == 0) 9f else w - 9f
                    val v = if (corner < 2) 11f else h - 15f
                    ovalB(standX(index, u, v), standY(v), 1.7f, 1.7f)
                }
            }, hue, alpha = 0.12f, style = hair)
        }
        // The strike on the centre shield: a dent and cracks running out of it.
        val impactU = 23f
        val impactV = 70f
        val ix = standX(2, impactU, impactV)
        val iy = standY(impactV)
        clipPath(cache.path(2 * 3) { standShield(2, 0f) }) {
            val cracks = cache.path(15) {
                for (ray in 0 until 7) {
                    var angle = ray * 51f + 14f + (frame.random(300 + ray) - 0.5f) * 24f
                    var px = ix
                    var py = iy
                    moveB(px, py)
                    for (step in 0 until 3) {
                        val length = (9f + step * 8f) * (0.8f + frame.random(320 + ray * 3 + step) * 0.6f)
                        angle += (frame.random(360 + ray * 3 + step) - 0.5f) * 34f
                        px += length * cos(angle * Deg)
                        py += length * sin(angle * Deg)
                        lineB(px, py)
                        if (step == 1 && ray % 3 == 0) {
                            val side = angle + 38f
                            lineB(px + 12f * cos(side * Deg), py + 12f * sin(side * Deg))
                            moveB(px, py)
                        }
                    }
                }
            }
            drawPath(cracks, Kk.Ink, alpha = 0.8f, style = cache.stroke(b.l(3f), StrokeCap.Round, StrokeJoin.Round))
            drawPath(cracks, hue, alpha = RewardArtAlpha.HIGH, style = crack)
        }
        ovalB(Kk.Ink, ix, iy, 5f, 5f, 0.7f)
        ovalB(hue, ix, iy, 5f, 5f, 0.2f, hair)
        ovalB(hue, ix, iy, 9.5f, 9.5f, 0.1f, hair)
        // The broken-off corner at the foot of the wall.
        drawPath(cache.path(16) {
            moveB(182f, StandGround - 1f)
            lineB(189f, StandGround - 7f)
            lineB(195f, StandGround - 4f)
            lineB(193f, StandGround - 1f)
            close()
            moveB(199f, StandGround - 1f)
            lineB(203f, StandGround - 4f)
            lineB(207f, StandGround - 1f)
            close()
        }, hue, alpha = 0.18f)
    }
}
