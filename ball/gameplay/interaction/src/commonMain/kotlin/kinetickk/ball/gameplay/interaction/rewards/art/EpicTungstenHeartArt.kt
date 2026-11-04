// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk

// Tungsten Heart.

private const val TungstenX = 186f
private const val TungstenTilt = 0.34f
private val TungstenRx = floatArrayOf(100f, 82f, 64f)
private val TungstenTop = floatArrayOf(400f, 377f, 356f)
private val TungstenDepth = floatArrayOf(25f, 23f, 21f)
private const val TungstenCollarRx = 14f
private const val TungstenCollarTop = 339f

/**
 * Tungsten Heart: three heavy weight plates stacked on a collar, seen from above at the lower right,
 * cropped by the card as if too heavy for it: turned metal flanks, a raised rim and grooved sides,
 * grip slots and a faint warm heart at the hub.
 */
internal fun DrawScope.drawTungstenHeartArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 418f - 150f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val glint = cache.stroke(b.l(2.2f), StrokeCap.Round)
    val grip = cache.stroke(b.l(3.6f), StrokeCap.Round)
    val x = TungstenX
    fadedArt(frame, x, 418f, 186f, 150f, 0.5f) {
        for (plate in 0 until 3) {
            val rx = TungstenRx[plate]
            val ry = rx * TungstenTilt
            val top = TungstenTop[plate]
            val bottom = top + TungstenDepth[plate]
            drawPath(cache.path(plate * 3) { cylinderSide(x, rx, ry, top, bottom); ovalB(x, top, rx, ry) }, Kk.Ink, alpha = Knockout)
            drawPath(cache.path(plate * 3 + 1) { cylinderSide(x, rx, ry, top, bottom) }, cache.brush(plate) { metalAcross(hue, x - rx, x + rx, 1.3f) })
            drawPath(cache.path(plate * 3 + 2) { cylinderEdges(x, rx, ry, top, bottom, groove = true) }, hue, alpha = 0.15f, style = hair)
            ovalB(cache.brush(3 + plate) { lightBrush(hue, x - rx, top - ry, x + rx * 0.6f, top + ry, 0.16f, 0.03f) }, x, top, rx, ry)
            ovalB(hue, x, top, rx, ry, 0.24f, line)
            ovalB(hue, x, top, rx * 0.84f, ry * 0.84f, 0.12f, hair)
            arcB(hue, x, top, rx, ry, 112f, 52f, 0.22f, glint)
            lineB(hue, x - rx * 0.6f, top + ry * 0.8f + 2f, x - rx * 0.6f, bottom + ry * 0.8f - 2f, b.l(1.6f), 0.16f)
        }
        // The top plate's face: a recess, two grip slots and the hub with its warm heart.
        val rx = TungstenRx[2]
        val ry = rx * TungstenTilt
        val top = TungstenTop[2]
        ovalB(Kk.Ink, x, top, rx * 0.62f, ry * 0.62f, 0.5f)
        ovalB(hue, x, top, rx * 0.62f, ry * 0.62f, 0.1f, hair)
        arcB(Kk.Ink, x, top, rx * 0.73f, ry * 0.73f, 158f, 44f, 0.85f, grip)
        arcB(Kk.Ink, x, top, rx * 0.73f, ry * 0.73f, 338f, 44f, 0.85f, grip)
        arcB(hue, x, top, rx * 0.73f, ry * 0.73f, 162f, 36f, 0.12f, hair)
        arcB(hue, x, top, rx * 0.73f, ry * 0.73f, 342f, 36f, 0.12f, hair)
        glowB(cache.brush(6) { glowBrush(hue, x, top, 40f, 0.2f) }, x, top, 40f, TungstenTilt)
        ovalB(hue, x, top, 24f, 24f * TungstenTilt, 0.16f, hair)
        // The collar through the stack, its bore dark.
        val cr = TungstenCollarRx
        val cry = cr * TungstenTilt
        val collarBottom = top
        drawPath(cache.path(9) { cylinderSide(x, cr, cry, TungstenCollarTop, collarBottom); ovalB(x, TungstenCollarTop, cr, cry) }, Kk.Ink, alpha = Knockout)
        drawPath(cache.path(10) { cylinderSide(x, cr, cry, TungstenCollarTop, collarBottom) }, cache.brush(7) { metalAcross(hue, x - cr, x + cr, 1.3f) })
        drawPath(cache.path(11) { cylinderEdges(x, cr, cry, TungstenCollarTop, collarBottom, groove = false) }, hue, alpha = 0.18f, style = hair)
        ovalB(hue, x, TungstenCollarTop, cr, cry, 0.14f)
        ovalB(Kk.Ink, x, TungstenCollarTop, cr * 0.5f, cry * 0.5f, 0.9f)
        ovalB(hue, x, TungstenCollarTop, cr, cry, 0.26f, hair)
    }
}
