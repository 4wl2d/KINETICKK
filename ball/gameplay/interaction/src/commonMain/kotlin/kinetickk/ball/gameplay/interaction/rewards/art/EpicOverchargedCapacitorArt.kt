// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Overcharged Capacitor.

private const val CapAxis = 372f
private const val CapPlateTop = 312f
private const val CapPlateBottom = 436f
private const val CapPlateDepth = 28f
private const val CapPlateRise = 18f
private val CapPlateX = floatArrayOf(74f, 190f)
private const val CapCoilLeft = 108f
private const val CapCoilTurns = 7
private const val CapCoilPitch = 10.6f
private const val CapCoilRadius = 24f
private const val CapCoilLean = 4f
private const val CapCoilSamples = 10

/** The coil's front ([front]) or back half-turns as polylines round the rod. */
private fun Path.capacitorCoil(front: Boolean) {
    for (turn in 0 until CapCoilTurns) {
        val from = turn * 2f * PI.toFloat() + if (front) 0f else PI.toFloat()
        for (sample in 0..CapCoilSamples) {
            val theta = from + sample * PI.toFloat() / CapCoilSamples
            val px = CapCoilLeft + CapCoilPitch * theta / (2f * PI.toFloat()) + CapCoilLean * sin(theta)
            val py = CapAxis - CapCoilRadius * cos(theta)
            if (sample == 0) moveB(px, py) else lineB(px, py)
        }
    }
}

private fun Path.capacitorPlate(a: Float) {
    moveB(a, CapPlateTop)
    lineB(a + CapPlateDepth, CapPlateTop - CapPlateRise)
    lineB(a + CapPlateDepth, CapPlateBottom - CapPlateRise)
    lineB(a, CapPlateBottom)
    close()
}

/**
 * Overcharged Capacitor: two charged plates seen at an angle with the field running straight
 * between them and fringing over their tops, a core rod through both and a tight copper-style coil
 * wound round it, glowing with more charge than it can hold.
 */
internal fun DrawScope.drawOverchargedCapacitorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 372f - 118f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val wire = cache.stroke(b.l(1.8f), StrokeCap.Round, StrokeJoin.Round)
    val gap = cache.stroke(b.l(3.8f), StrokeCap.Round, StrokeJoin.Round)
    val mark = cache.stroke(b.l(1.7f))
    fadedArt(frame, 146f, 372f, 158f, 118f, 0.5f) {
        glowB(cache.brush(0) { glowBrush(hue, 146f, CapAxis, 70f, 0.24f) }, 146f, CapAxis, 70f, 1f)
        // The field: straight between the plates, fringing over and under them.
        drawPath(cache.path(0) {
            for (row in 0 until 3) {
                segB(104f, 320f + row * 11f, 188f, 320f + row * 11f)
                segB(104f, 402f + row * 11f, 188f, 402f + row * 11f)
            }
            moveB(96f, 296f)
            quadB(146f, 272f, 202f, 296f)
            moveB(90f, 302f)
            quadB(146f, 256f, 208f, 302f)
            moveB(98f, 432f)
            quadB(146f, 446f, 194f, 432f)
        }, hue, alpha = 0.15f, style = hair)
        drawPath(cache.path(1) { capacitorCoil(front = false) }, hue, alpha = 0.11f, style = hair)
        // The core rod with its terminals.
        val rod = cache.path(2) {
            addRect(boardRect(44f, CapAxis - 8f, 246f, CapAxis + 8f))
            ovalB(44f, CapAxis, 4f, 11f)
            ovalB(246f, CapAxis, 4f, 11f)
        }
        drawPath(rod, Kk.Ink, alpha = Knockout)
        drawPath(rod, cache.brush(1) { lightBrush(hue, 0f, CapAxis - 10f, 0f, CapAxis + 10f, 0.16f, 0.03f) })
        drawPath(cache.path(3) {
            segB(44f, CapAxis - 8f, 246f, CapAxis - 8f)
            segB(44f, CapAxis + 8f, 246f, CapAxis + 8f)
            ovalB(44f, CapAxis, 4f, 11f)
            ovalB(246f, CapAxis, 4f, 11f)
            ovalB(52f, CapAxis, 3f, 13f)
            ovalB(238f, CapAxis, 3f, 13f)
        }, hue, alpha = 0.18f, style = hair)
        val coil = cache.path(4) { capacitorCoil(front = true) }
        drawPath(coil, Kk.Ink, alpha = 0.8f, style = gap)
        drawPath(coil, hue, alpha = 0.3f, style = wire)
        // The plates, each a lit face and a bright leading edge.
        for (plate in 0 until 2) {
            val a = CapPlateX[plate]
            val face = cache.path(5 + plate) { capacitorPlate(a) }
            drawPath(face, Kk.Ink, alpha = Knockout)
            drawPath(face, cache.brush(2) { lightBrush(hue, 0f, CapPlateTop - CapPlateRise, 0f, CapPlateBottom, 0.17f, 0.03f) })
            drawPath(face, hue, alpha = 0.22f, style = line)
            lineB(hue, a, CapPlateTop, a, CapPlateBottom, b.l(2.6f), 0.2f)
            lineB(hue, a + CapPlateDepth, CapPlateTop - CapPlateRise, a, CapPlateTop, b.l(1.6f), 0.3f)
        }
        drawPath(cache.path(7) {
            for (plate in 0 until 2) {
                val a = CapPlateX[plate]
                for (rib in 1..3) {
                    val yy = CapPlateTop + rib * 31f
                    segB(a + 3f, yy - 2f, a + CapPlateDepth - 3f, yy - CapPlateRise + 2f)
                }
            }
        }, hue, alpha = 0.08f, style = hair)
        // The charge on the plates: plus marks down the left plate, minus marks down the right.
        drawPath(cache.path(8) {
            for (plate in 0 until 2) {
                val a = CapPlateX[plate]
                for (row in 0 until 5) {
                    val v = 0.14f + row * 0.18f
                    val cx = a + CapPlateDepth * 0.5f
                    val cy = CapPlateTop + (CapPlateBottom - CapPlateTop) * v - CapPlateRise * 0.5f
                    segB(cx - 3.5f, cy + 1.2f, cx + 3.5f, cy - 1.2f)
                    if (plate == 0) segB(cx, cy - 3.6f, cx, cy + 3.6f)
                }
            }
        }, hue, alpha = RewardArtAlpha.HIGH, style = mark)
        // More charge than it can hold: the light spills over the coil.
        glowB(cache.brush(3) { glowBrush(hue, 146f, CapAxis, 46f, 0.14f) }, 146f, CapAxis, 46f, 0.8f)
    }
}
