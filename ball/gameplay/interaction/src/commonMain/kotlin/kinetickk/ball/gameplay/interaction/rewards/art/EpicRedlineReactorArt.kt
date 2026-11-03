// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk

// Redline Reactor.

private const val ReactorX = 176f
private const val ReactorY = 398f
private const val ReactorRods = 8
private const val ReactorRodOrbit = 50f
private const val ReactorRod = 9f
private const val ReactorVents = 12

/**
 * Redline Reactor: a reactor core seen from straight above, as a schematic of its vessel: the heavy
 * containment ring with coolant vents cut into it, vent ticks round it and the hot redline arc, an
 * inner shield ring with its dashed track, a ring of eight fuel rods seen end-on round the bright
 * core disc, and control spokes between the rods. Round and flat, never a stack.
 */
internal fun DrawScope.drawRedlineReactorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 404f - 150f * 0.5f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val hot = cache.stroke(b.l(3.4f), StrokeCap.Round)
    val vent = cache.stroke(b.l(4.2f), StrokeCap.Round)
    val x = ReactorX
    val y = ReactorY
    fadedArt(frame, x, 404f, 190f, 150f, 0.5f) {
        ovalB(Kk.Ink, x, y, 114f, 114f, Knockout)
        // Containment: a heavy ring lit from the upper left, its rims, coolant vents cut into it.
        drawPath(cache.path(0) {
            fillType = PathFillType.EvenOdd
            ovalB(x, y, 104f, 104f)
            ovalB(x, y, 90f, 90f)
        }, cache.brush(0) { lightBrush(hue, x - 104f, y - 104f, x + 72f, y + 72f, 0.16f, 0.03f) })
        ovalB(hue, x, y, 104f, 104f, 0.2f, line)
        ovalB(hue, x, y, 90f, 90f, 0.12f, hair)
        for (slot in 0 until ReactorVents) arcB(Kk.Ink, x, y, 97f, 97f, slot * 30f + 8f, 14f, 0.9f, vent)
        drawPath(cache.path(1) {
            for (index in 0 until 48) {
                val a = index * 7.5f
                val outer = if (index % 4 == 0) 117f else 111f
                segB(polarX(x, 107f, a), polarY(y, 107f, a), polarX(x, outer, a), polarY(y, outer, a))
            }
        }, hue, alpha = 0.14f, style = hair)
        // The redline: the hot sector of the containment ring, upper right.
        arcB(hue, x, y, 104f, 104f, 290f, 58f, RewardArtAlpha.HIGH, hot)
        // The shield ring and its dashed inner track.
        ovalB(hue, x, y, 74f, 74f, 0.16f, line)
        drawPath(cache.path(2) { for (index in 0 until 36) arcB(x, y, 67f, 67f, index * 10f, 5f, true) }, hue, alpha = 0.12f, style = hair)
        // Control spokes between the rods.
        drawPath(cache.path(3) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f + 22.5f
                segB(polarX(x, 30f, a), polarY(y, 30f, a), polarX(x, 66f, a), polarY(y, 66f, a))
            }
        }, hue, alpha = 0.1f, style = hair)
        // The fuel rods end-on: dark wells, a lit rim and a hot pellet in each.
        val rods = cache.path(4) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f
                ovalB(polarX(x, ReactorRodOrbit, a), polarY(y, ReactorRodOrbit, a), ReactorRod, ReactorRod)
            }
        }
        drawPath(rods, Kk.Ink, alpha = 0.9f)
        drawPath(rods, hue, alpha = 0.2f, style = line)
        drawPath(cache.path(5) {
            for (index in 0 until ReactorRods) {
                val a = index * 45f
                ovalB(polarX(x, ReactorRodOrbit, a), polarY(y, ReactorRodOrbit, a), 3.4f, 3.4f)
            }
        }, hue, alpha = 0.2f)
        // The core: a bright disc in its own glow.
        glowB(cache.brush(1) { glowBrush(hue, x, y, 62f, 0.22f) }, x, y, 62f, 1f)
        ovalB(hue, x, y, 24f, 24f, 0.14f)
        ovalB(hue, x, y, 24f, 24f, 0.22f, line)
        ovalB(hue, x, y, 11f, 11f, 0.22f)
    }
}
